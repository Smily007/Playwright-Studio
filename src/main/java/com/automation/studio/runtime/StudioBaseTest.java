package com.automation.studio.runtime;

import com.automation.studio.model.TestResult;
import com.automation.studio.utilities.JsonUtils;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Base class of every generated test. Owns the Playwright lifecycle (one browser per test method),
 * wraps each step for logging / screenshots / failure capture and writes a TestResult JSON that the
 * reporter turns into the HTML report. Settings arrive as -Dstudio.* system properties.
 */
public abstract class StudioBaseTest {
    private static final BufferedReader DEBUG_INPUT = new BufferedReader(new InputStreamReader(System.in));
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;
    protected final Map<String, String> vars = new LinkedHashMap<>();

    private final List<TestResult.StepResult> steps = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private Path reportDir;
    private String testName;
    private long startMs;
    private Instant start;
    private boolean softFailure;
    private TestResult.StepResult firstFailure;
    private boolean tracing;
    private Video video;
    private boolean debugStepMode = true;

    private static String prop(String k, String d) { return System.getProperty(k, d); }

    @BeforeMethod(alwaysRun = true)
    public void studioSetUp(ITestResult result) {
        testName = getClass().getSimpleName();
        Object[] parameters = result.getParameters();
        if (parameters.length > 0 && parameters[0] instanceof Map<?, ?> row) {
            Object rowNumber = row.get("__studio_excel_row");
            if (rowNumber != null) testName += " - row " + rowNumber;
        }
        reportDir = Paths.get(prop("studio.reportDir", "target/studio-report"));
        start = Instant.now();
        startMs = System.currentTimeMillis();
        steps.clear();
        logs.clear();
        softFailure = false;
        info("Starting test: " + testName);

        playwright = Playwright.create();
        BrowserType bt = switch (prop("studio.engine", "chromium")) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
        BrowserType.LaunchOptions lo = new BrowserType.LaunchOptions()
                .setHeadless(Boolean.parseBoolean(prop("studio.headless", "false")))
                .setSlowMo(Double.parseDouble(prop("studio.slowMo", "0")));
        if (!prop("studio.channel", "").isBlank()) lo.setChannel(prop("studio.channel", ""));
        if (!prop("studio.executablePath", "").isBlank()) lo.setExecutablePath(Paths.get(prop("studio.executablePath", "")));
        browser = bt.launch(lo);

        Browser.NewContextOptions co = new Browser.NewContextOptions()
                .setViewportSize(Integer.parseInt(prop("studio.viewportWidth", "1280")), Integer.parseInt(prop("studio.viewportHeight", "720")));
        if (Boolean.parseBoolean(prop("studio.recordVideo", "false")))
            co.setRecordVideoDir(reportDir.resolve("videos").resolve(testName));
        context = browser.newContext(co);
        context.setDefaultTimeout(Double.parseDouble(prop("studio.timeout", "30000")));
        if (!prop("studio.trace", "on-failure").equals("off")) {
            context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
            tracing = true;
        }
        page = context.newPage();
        video = page.video();
    }

    @DataProvider(name = "studioExcelRows")
    public Object[][] studioExcelRows() throws java.io.IOException {
        String dataFile = prop("studio.excelDataFile", "");
        if (dataFile.isBlank()) return new Object[][]{{Map.of()}};
        String sheetName = prop("studio.excelSheetName", "");
        List<Map<String, String>> rows = TestData.loadExcel(Paths.get(dataFile), sheetName);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = new LinkedHashMap<>(rows.get(i));
            row.put("__studio_excel_row", String.valueOf(i + 1));
            data[i][0] = row;
        }
        return data;
    }

    protected void step(int number, String description, ThrowingRunnable body) { step(number, description, false, body); }

    protected void step(int number, String description, boolean continueOnFailure, ThrowingRunnable body) {
        step(number, description, continueOnFailure, false, body);
    }

    protected void step(int number, String description, boolean continueOnFailure, boolean breakpoint, ThrowingRunnable body) {
        if (Boolean.getBoolean("studio.debug") && (debugStepMode || breakpoint))
            awaitDebugger(number, description);
        TestResult.StepResult sr = new TestResult.StepResult();
        sr.number = number;
        sr.description = description;
        long t0 = System.currentTimeMillis();
        info("Step " + number + ": " + description);
        try {
            body.run();
            sr.status = "PASSED";
            if (prop("studio.screenshotMode", "failure").equals("every-step"))
                sr.screenshot = shot(String.format("step-%02d", number));
        } catch (Throwable e) {
            sr.status = "FAILED";
            sr.error = e.toString();
            sr.screenshot = shot(String.format("step-%02d-failure", number));
            if (firstFailure == null) firstFailure = sr;
            error("Step " + number + " FAILED: " + e);
            sr.durationMs = System.currentTimeMillis() - t0;
            steps.add(sr);
            if (continueOnFailure) { softFailure = true; return; }
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error er) throw er;
            throw new RuntimeException(e);
        }
        sr.durationMs = System.currentTimeMillis() - t0;
        steps.add(sr);
    }

    private void awaitDebugger(int number, String description) {
        System.out.println("STUDIO_DEBUG_PAUSED:" + number + ":" + description);
        try {
            String command = DEBUG_INPUT.readLine();
            if (command == null) throw new IllegalStateException("Debugger input closed while execution was paused.");
            debugStepMode = command.equals("step");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read debugger command.", e);
        }
    }

    /** Fails the test if a step marked continue-on-failure failed. Generated tests call this last. */
    protected void assertNoSoftFailures() {
        if (softFailure) throw new AssertionError("Step " + (firstFailure == null ? "?" : firstFailure.number) + " failed (continue-on-failure)");
    }

    /** Full-page screenshot to screenshots/{test}/{name}.png, returns the path relative to the report dir. */
    protected String shot(String name) {
        try {
            Path rel = Paths.get("screenshots", testName, name + ".png");
            Path file = reportDir.resolve(rel);
            Files.createDirectories(file.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true));
            return rel.toString().replace('\\', '/');
        } catch (Throwable t) {
            error("Screenshot failed: " + t.getMessage());
            return "";
        }
    }

    @AfterMethod(alwaysRun = true)
    public void studioTearDown(ITestResult r) {
        TestResult res = new TestResult();
        res.testName = testName;
        res.environment = prop("studio.environment", "");
        res.className = getClass().getName();
        res.steps = new ArrayList<>(steps);
        boolean failed = r.getStatus() == ITestResult.FAILURE;
        res.status = r.getStatus() == ITestResult.SUCCESS ? "PASSED" : failed ? "FAILED" : "SKIPPED";
        try { res.url = page.url(); res.title = page.title(); } catch (Throwable ignored) { }
        try { res.browser = browser.browserType().name() + " " + browser.version(); } catch (Throwable ignored) { }
        if (failed) {
            Throwable t = r.getThrowable();
            if (t != null) {
                res.exception = t.toString();
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                res.stackTrace = sw.toString();
            }
            if (firstFailure != null) {
                res.failedStep = firstFailure.number + ": " + firstFailure.description;
                res.screenshot = firstFailure.screenshot;
            } else {
                res.screenshot = shot("failure");
            }
            error("Test FAILED: " + res.exception);
        } else {
            info("Test " + res.status);
        }
        Path vid = null;
        try {
            if (tracing) {
                String mode = prop("studio.trace", "on-failure");
                if (mode.equals("on") || failed) {
                    Path rel = Paths.get("traces", testName + ".zip");
                    Files.createDirectories(reportDir.resolve(rel).getParent());
                    context.tracing().stop(new Tracing.StopOptions().setPath(reportDir.resolve(rel)));
                    res.trace = rel.toString().replace('\\', '/');
                } else {
                    context.tracing().stop();
                }
            }
        } catch (Throwable t) { error("Trace failed: " + t.getMessage()); }
        try { context.close(); if (video != null) vid = video.path(); } catch (Throwable t) { error("Close failed: " + t.getMessage()); }
        try { browser.close(); } catch (Throwable ignored) { }
        try { playwright.close(); } catch (Throwable ignored) { }
        if (vid != null) {
            Path abs = vid.toAbsolutePath().normalize();
            Path base = reportDir.toAbsolutePath().normalize();
            res.video = abs.startsWith(base) ? base.relativize(abs).toString().replace('\\', '/') : "";
        }
        res.startTime = start.toString();
        res.endTime = Instant.now().toString();
        res.durationMs = System.currentTimeMillis() - startMs;
        res.logs = new ArrayList<>(logs);
        try {
            JsonUtils.write(reportDir.resolve("results").resolve(testName + ".json"), res);
        } catch (Exception e) {
            log.error("Could not write result JSON", e);
        }
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private void info(String msg) { logs.add(LocalTime.now().format(TS) + " INFO " + msg); log.info(msg); }
    private void error(String msg) { logs.add(LocalTime.now().format(TS) + " ERROR " + msg); log.error(msg); }
}
