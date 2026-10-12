package com.automation.studio.recorder;

import com.automation.studio.locator.LocatorEngine;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.model.TestStep;
import com.automation.studio.utilities.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns DOM events reported by recorder.js into TestSteps. Locators are chosen by the LocatorEngine from the
 * element's semantics (role, label, placeholder, test id, text, id, CSS, XPath), never from coordinates.
 */
public class ActionRecorder {
    private static final Logger log = LoggerFactory.getLogger(ActionRecorder.class);

    private final LocatorEngine engine;
    private final BrowserEventListener listener;
    private final Function<String, String> secretStore;
    private volatile boolean recording;
    private volatile long lastActionMs;
    private String lastNavUrl = "";
    private String lastKey = "";
    private int lastCount;

    /** @param secretStore stores a sensitive value and returns the reference to put in the step, e.g. ${secret:key} */
    public ActionRecorder(LocatorEngine engine, BrowserEventListener listener, Function<String, String> secretStore) {
        this.engine = engine;
        this.listener = listener;
        this.secretStore = secretStore;
    }

    public void setRecording(boolean on) {
        recording = on;
        if (on) { lastKey = ""; lastCount = 0; }
    }

    public boolean isRecording() { return recording; }

    /** Marks "the user just acted", so the resulting navigation is not recorded as a separate Navigate step. */
    public void touch() { lastActionMs = System.currentTimeMillis(); }

    /** Must be called before pages are created in the context. */
    public void attach(BrowserContext context) {
        context.exposeBinding("__studioRecord", (source, args) -> {
            try {
                if (recording && args.length > 0) handle(source.frame(), source.page(), String.valueOf(args[0]));
            } catch (Throwable t) {
                log.warn("Recorder event failed: {}", t.toString());
            }
            return null;
        });
        context.addInitScript(DomInspector.script());
        context.onPage(this::watchNavigation);
    }

    private void watchNavigation(Page page) {
        page.onFrameNavigated((Frame f) -> {
            if (f.parentFrame() != null) return;
            String url = f.url();
            boolean userDriven = System.currentTimeMillis() - lastActionMs < 2500;
            if (recording && !userDriven && !url.equals(lastNavUrl) && !url.startsWith("about:")) {
                TestStep s = new TestStep("NAVIGATE").with("url", url);
                finish(s, "NAVIGATE|" + url, false);
            }
            lastNavUrl = url;
        });
    }

    private void handle(Frame frame, Page page, String json) throws Exception {
        Map<String, Object> m = JsonUtils.mapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        String action = str(m.get("action"));
        String value = str(m.get("value"));
        String frameSel = str(m.get("frameSel"));
        boolean sensitive = Boolean.TRUE.equals(m.get("sensitive"));
        lastActionMs = System.currentTimeMillis();
        if (frameSel.isEmpty() && frame != null && frame != page.mainFrame() && !frame.name().isEmpty())
            frameSel = "iframe[name=\"" + frame.name() + "\"]";

        LocatorInfo loc = null;
        Object el = m.get("el");
        if (el instanceof Map<?, ?> em) {
            Map<String, String> dom = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : em.entrySet()) dom.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            loc = engine.build(dom);
            loc.frameSelector = frameSel;
        }

        TestStep s;
        switch (action) {
            case "CLICK_RIGHT" -> { s = new TestStep("CLICK").with("button", "RIGHT"); }
            case "FILL" -> s = new TestStep("FILL").with("value", sensitive ? secretStore.apply(value) : value);
            case "SELECT_OPTION" -> s = new TestStep("SELECT_OPTION").with("value", value);
            case "PRESS" -> s = new TestStep("PRESS").with("key", value);
            case "SET_INPUT_FILES" -> s = new TestStep("SET_INPUT_FILES").with("path", value);
            default -> s = new TestStep(action);
        }
        s.locator = loc;
        String key = s.action + "|" + (loc == null ? "" : loc.toJava());

        if (action.equals("DBLCLICK") && lastKey.equals("CLICK|" + (loc == null ? "" : loc.toJava())) && lastCount > 0) {
            int replace = Math.min(2, lastCount);
            finish(s, key, true, replace);
            return;
        }
        if (action.equals("FILL") && lastKey.equals(key)) { finish(s, key, true, 1); return; }
        finish(s, key, false);
    }

    private void finish(TestStep s, String key, boolean replace) { finish(s, key, replace, 1); }

    private void finish(TestStep s, String key, boolean replace, int count) {
        s.timestamp = Instant.now().toString();
        if (replace) { listener.onReplaceLast(count, s); lastCount = 1; }
        else { listener.onStep(s); lastCount = key.equals(lastKey) ? lastCount + 1 : 1; }
        lastKey = key;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
