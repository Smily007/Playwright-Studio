package com.automation.studio.browser;

import java.util.function.Consumer;

/** How the controlled browser is shown. */
public class ViewOptions {
    public static final String EMBEDDED = "embedded";
    public static final String SIDE_BY_SIDE = "side-by-side";
    public static final String SEPARATE = "separate";

    public String mode = SEPARATE;
    /** Screen rectangle for the browser window in side-by-side mode. */
    public int x, y, width, height;
    /** Receives JPEG frames in embedded mode (called on the Playwright thread). */
    public Consumer<byte[]> frameSink;
}
