package com.automation.studio.browser;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.actions.ActionDefinition;
import com.automation.studio.actions.ExecutionContext;
import com.automation.studio.model.TestStep;
import com.automation.studio.recorder.ActionRecorder;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Owns Playwright on ONE dedicated worker thread (Playwright Java is not thread safe). The worker also pumps
 * Playwright's event loop while idle (page.waitForTimeout) so recorder bindings, screencast frames and listeners fire.
 * Never call Playwright objects from the JavaFX thread: use {@link #submit}.
 */
public class BrowserManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BrowserManager.class);

    @FunctionalInterface
    public interface InspectListener {
        void onElementInspected(String xpath, String relativeXPath, String frameSelector);
    }

    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;
    private volatile ExecutionContext ctx;
    private volatile LiveView live;
    private Consumer<String> closedListener = s -> { };
    private InspectListener inspectListener = (xpath, relativeXPath, frame) -> { };

    public BrowserManager() {
        worker = new Thread(this::loop, "playwright-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public void onBrowserClosed(Consumer<String> listener) { this.closedListener = listener; }
    public void onElementInspected(InspectListener listener) { this.inspectListener = listener; }

    public boolean isRunning() { return ctx != null; }

    private void loop() {
        while (running) {
            try {
                Runnable r = queue.poll();
                if (r != null) { r.run(); continue; }
                ExecutionContext c = ctx;
                if (c != null && c.page != null && !c.page.isClosed()) c.page.waitForTimeout(live != null ? 12 : 40);
                else Thread.sleep(40);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                log.debug("Worker loop: {}", t.toString());
                if (ctx != null && (ctx.page == null || ctx.page.isClosed())) { /* browser window closed by user */
                    cleanup();
                    closedListener.accept("Browser was closed");
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
        }
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        queue.add(() -> {
            try { f.complete(task.call()); } catch (Throwable e) { f.completeExceptionally(e); }
        });
        return f;
    }

    /**
     * Launches the browser, installs the recorder and opens the URL.
     * @return the LiveView in embedded mode, otherwise null
     */
    public CompletableFuture<LiveView> launch(String url, BrowserConfiguration cfg, ActionRecorder recorder, Path videoDir, ViewOptions view) {
        return submit(() -> {
            if (ctx != null) cleanup();
            boolean embedded = ViewOptions.EMBEDDED.equals(view.mode);
            boolean sideBySide = ViewOptions.SIDE_BY_SIDE.equals(view.mode);
            ExecutionContext c = new ExecutionContext();
            c.playwright = Playwright.create();
            BrowserType bt = switch (cfg.engine()) {
                case "firefox" -> c.playwright.firefox();
                case "webkit" -> c.playwright.webkit();
                default -> c.playwright.chromium();
            };
            BrowserType.LaunchOptions lo = new BrowserType.LaunchOptions()
                    .setHeadless(embedded || cfg.headless).setSlowMo(cfg.slowMoMs);
            if (sideBySide)
                lo.setArgs(List.of("--window-position=" + view.x + "," + view.y, "--window-size=" + view.width + "," + view.height));
            if (!cfg.channel().isEmpty()) lo.setChannel(cfg.channel());
            if (!cfg.executablePath.isBlank()) lo.setExecutablePath(Paths.get(cfg.executablePath));
            c.browser = bt.launch(lo);
            c.browser.onDisconnected(b -> { cleanup(); closedListener.accept("Browser disconnected"); });
            Browser.NewContextOptions co = new Browser.NewContextOptions();
            if (sideBySide) co.setViewportSize(null);
            else co.setViewportSize(cfg.viewportWidth, cfg.viewportHeight);
            if (cfg.recordVideo && videoDir != null) co.setRecordVideoDir(videoDir);
            c.context = c.browser.newContext(co);
            c.context.setDefaultTimeout(cfg.timeoutMs);
            recorder.attach(c.context);
            c.context.exposeBinding("__studioInspect", (source, args) -> {
                if (args.length >= 3) {
                    String frameSelector = String.valueOf(args[2]);
                    if (frameSelector.isBlank() && source.frame() != source.page().mainFrame() && !source.frame().name().isEmpty())
                        frameSelector = "iframe[name=\"" + source.frame().name() + "\"]";
                    LiveView currentView = live;
                    if (currentView != null) currentView.setInspectMode(false);
                    inspectListener.onElementInspected(String.valueOf(args[0]), String.valueOf(args[1]), frameSelector);
                }
                return null;
            });
            LiveView lv = null;
            if (embedded) {
                lv = new LiveView(this, c, view.frameSink, cfg.viewportWidth, cfg.viewportHeight);
                lv.attach(c.context);
            }
            c.page = c.context.newPage();
            c.page.onClose(p -> { if (c.context.pages().isEmpty()) { cleanup(); closedListener.accept("Browser window closed"); } });
            ctx = c;
            live = lv;
            c.page.navigate(url);
            return lv;
        });
    }

    /** Runs one step on the live page and returns once finished. Errors surface through the future. */
    public CompletableFuture<Void> runStep(ActionCatalog catalog, TestStep step) {
        return submit(() -> {
            if (ctx == null) throw new IllegalStateException("Browser is not running. Click START BROWSER first.");
            ActionDefinition d = catalog.get(step.action);
            if (d == null) throw new IllegalArgumentException("Unknown action: " + step.action);
            d.execute(ctx, step);
            return null;
        });
    }

    public CompletableFuture<String> currentUrl() {
        return submit(() -> ctx == null ? "" : ctx.page.url());
    }

    public ExecutionContext ctx() {
        return ctx;
    }

    public CompletableFuture<Void> stop() {
        return submit(() -> { cleanup(); return null; });
    }

    private void cleanup() {
        ExecutionContext c = ctx;
        ctx = null;
        LiveView lv = live;
        live = null;
        if (lv != null) lv.shutdown();
        if (c == null) return;
        try { if (c.context != null) c.context.close(); } catch (Throwable ignored) { }
        try { if (c.browser != null) c.browser.close(); } catch (Throwable ignored) { }
        try { if (c.playwright != null) c.playwright.close(); } catch (Throwable ignored) { }
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        try { stop().get(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
        running = false;
        worker.interrupt();
    }
}
