package com.automation.studio.browser;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** Browser + execution settings stored in project.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrowserConfiguration {
    public static final List<String> BROWSERS = List.of("chromium", "chrome", "msedge", "firefox", "webkit");

    public String browser = "chromium";
    public boolean headless = false;
    public String executablePath = "";
    public int viewportWidth = 1280;
    public int viewportHeight = 720;
    public int timeoutMs = 30000;
    public int slowMoMs = 0;
    public boolean recordVideo = false;
    /** off | failure | every-step */
    public String screenshotMode = "failure";
    /** off | on | on-failure */
    public String trace = "on-failure";
    public int retryCount = 0;
    public int threadCount = 1;
    /** embedded (browser inside the studio window), side-by-side, separate */
    public String viewMode = "embedded";

    /** Playwright engine: chromium, firefox or webkit. */
    @JsonIgnore
    public String engine() {
        return switch (browser) {
            case "firefox" -> "firefox";
            case "webkit" -> "webkit";
            default -> "chromium";
        };
    }

    /** Chromium channel (chrome, msedge) or empty. */
    @JsonIgnore
    public String channel() {
        return browser.equals("chrome") || browser.equals("msedge") ? browser : "";
    }

    public BrowserConfiguration copy() {
        BrowserConfiguration c = new BrowserConfiguration();
        c.browser = browser; c.headless = headless; c.executablePath = executablePath;
        c.viewportWidth = viewportWidth; c.viewportHeight = viewportHeight; c.timeoutMs = timeoutMs;
        c.slowMoMs = slowMoMs; c.recordVideo = recordVideo; c.screenshotMode = screenshotMode;
        c.trace = trace; c.retryCount = retryCount; c.threadCount = threadCount; c.viewMode = viewMode;
        return c;
    }

    /** JVM system properties handed to the test JVM. */
    public List<String> toSystemProps() {
        List<String> p = new ArrayList<>();
        p.add("-Dstudio.engine=" + engine());
        p.add("-Dstudio.channel=" + channel());
        p.add("-Dstudio.browserName=" + browser);
        p.add("-Dstudio.headless=" + headless);
        p.add("-Dstudio.executablePath=" + executablePath);
        p.add("-Dstudio.viewportWidth=" + viewportWidth);
        p.add("-Dstudio.viewportHeight=" + viewportHeight);
        p.add("-Dstudio.timeout=" + timeoutMs);
        p.add("-Dstudio.slowMo=" + slowMoMs);
        p.add("-Dstudio.recordVideo=" + recordVideo);
        p.add("-Dstudio.screenshotMode=" + screenshotMode);
        p.add("-Dstudio.trace=" + trace);
        p.add("-Dstudio.retry=" + retryCount);
        p.add("-Dstudio.threads=" + threadCount);
        return p;
    }
}
