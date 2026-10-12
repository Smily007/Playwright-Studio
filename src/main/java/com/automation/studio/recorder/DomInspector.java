package com.automation.studio.recorder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the JavaScript injected into every page/frame to describe interacted elements. */
public final class DomInspector {
    private static String script;

    private DomInspector() {}

    public static synchronized String script() {
        if (script == null) {
            try (InputStream in = DomInspector.class.getResourceAsStream("/recorder.js")) {
                if (in == null) throw new IllegalStateException("recorder.js missing from classpath");
                script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load recorder.js", e);
            }
        }
        return script;
    }
}
