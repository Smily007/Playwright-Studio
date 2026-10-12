package com.automation.studio.browser;

import com.automation.studio.actions.ExecutionContext;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.MouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Embedded browser view (Chromium only): streams the active page with CDP Page.startScreencast and forwards
 * mouse / keyboard input back through Playwright's real input API, so the recorder sees genuine events.
 * It follows new tabs/popups. All Playwright calls go through the BrowserManager worker thread.
 */
public class LiveView {
    private static final Logger log = LoggerFactory.getLogger(LiveView.class);

    @FunctionalInterface
    private interface PageAction { void run(Page p) throws Exception; }

    private final BrowserManager manager;
    private final ExecutionContext ec;
    private final Consumer<byte[]> sink;
    private final int width, height;
    private final AtomicBoolean moveQueued = new AtomicBoolean();
    private volatile Page active;
    private volatile boolean inspectMode;
    private CDPSession cdp;

    LiveView(BrowserManager manager, ExecutionContext ec, Consumer<byte[]> sink, int width, int height) {
        this.manager = manager;
        this.ec = ec;
        this.sink = sink;
        this.width = width;
        this.height = height;
    }

    /** Call before the first page is created so that it is followed from the start. */
    void attach(BrowserContext context) {
        context.onPage(this::follow);
    }

    private void follow(Page p) {
        stopScreencast();
        active = p;
        ec.page = p;
        startScreencast(p);
        if (inspectMode) setInspectModeOn(p, true);
        p.onClose(closed -> {
            if (active != closed) return;
            stopScreencast();
            active = null;
            List<Page> rest = closed.context().pages();
            if (!rest.isEmpty()) follow(rest.get(rest.size() - 1));
        });
    }

    private void startScreencast(Page p) {
        try {
            CDPSession s = p.context().newCDPSession(p);
            s.on("Page.screencastFrame", params -> {
                try {
                    sink.accept(Base64.getDecoder().decode(params.get("data").getAsString()));
                    JsonObject ack = new JsonObject();
                    ack.addProperty("sessionId", params.get("sessionId").getAsInt());
                    s.send("Page.screencastFrameAck", ack);
                } catch (Throwable t) {
                    log.debug("Frame handling failed: {}", t.toString());
                }
            });
            JsonObject o = new JsonObject();
            o.addProperty("format", "jpeg");
            o.addProperty("quality", 70);
            o.addProperty("maxWidth", width);
            o.addProperty("maxHeight", height);
            o.addProperty("everyNthFrame", 1);
            s.send("Page.startScreencast", o);
            cdp = s;
        } catch (Throwable t) {
            log.warn("Live view is not available for this browser: {}", t.toString());
        }
    }

    private void stopScreencast() {
        CDPSession s = cdp;
        cdp = null;
        if (s == null) return;
        try { s.send("Page.stopScreencast"); } catch (Throwable ignored) { }
        try { s.detach(); } catch (Throwable ignored) { }
    }

    void shutdown() { stopScreencast(); active = null; }

    // ------------------------------------------------------------------ input (callable from any thread)

    private java.util.concurrent.CompletableFuture<Void> run(PageAction a) {
        return manager.submit(() -> {
            Page p = active;
            if (p != null && !p.isClosed()) a.run(p);
            return (Void) null;
        }).exceptionally(t -> { log.debug("Input failed: {}", t.toString()); return null; });
    }

    public void mouseMove(double x, double y) {
        if (!moveQueued.compareAndSet(false, true)) return;
        run(p -> p.mouse().move(x, y)).whenComplete((v, t) -> moveQueued.set(false));
    }

    public void mouseDown(double x, double y, String button, int clicks) {
        run(p -> {
            p.mouse().move(x, y);
            p.mouse().down(new Mouse.DownOptions().setButton(MouseButton.valueOf(button)).setClickCount(Math.max(1, clicks)));
        });
    }

    public void mouseUp(double x, double y, String button, int clicks) {
        run(p -> {
            p.mouse().move(x, y);
            p.mouse().up(new Mouse.UpOptions().setButton(MouseButton.valueOf(button)).setClickCount(Math.max(1, clicks)));
        });
    }

    public void wheel(double dx, double dy) { run(p -> p.mouse().wheel(dx, dy)); }

    public void type(String text) { run(p -> p.keyboard().type(text)); }

    public void insertText(String text) { run(p -> p.keyboard().insertText(text)); }

    public void press(String key) { run(p -> p.keyboard().press(key)); }

    public void setInspectMode(boolean enabled) {
        inspectMode = enabled;
        run(p -> setInspectModeOn(p, enabled));
    }

    public CompletableFuture<Integer> highlightXPath(String expression) {
        return manager.submit(() -> {
            Page page = active;
            if (page == null || page.isClosed()) throw new IllegalStateException("Browser page is not available.");
            int matches = 0;
            for (var frame : page.frames()) {
                Object result = frame.evaluate("expression => window.__studioHighlightXPath ? window.__studioHighlightXPath(expression) : 0", expression);
                if (result instanceof Number count) matches += count.intValue();
            }
            return matches;
        });
    }

    private static void setInspectModeOn(Page page, boolean enabled) {
        page.evaluate("enabled => { if (window.__studioSetInspectMode) window.__studioSetInspectMode(enabled); }", enabled);
    }
}
