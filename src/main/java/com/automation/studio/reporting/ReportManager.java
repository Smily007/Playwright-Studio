package com.automation.studio.reporting;

import com.automation.studio.model.TestResult;
import com.automation.studio.utilities.FileUtils;
import com.automation.studio.utilities.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Creates execution folders, loads results, writes index.html + summary.json and exposes history. */
public class ReportManager {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        public String date = "", browser = "", started = "";
        public int total, passed, failed, skipped;
        public long durationMs;
        public List<TestResult> results = new ArrayList<>();
    }

    /** One row of the history table. */
    public static class HistoryRow {
        public String date, test, browser, status, failure, reportPath;
        public long durationMs;
    }

    public Path nextExecutionDir(Path projectRoot) throws IOException {
        Path day = projectRoot.resolve("reports").resolve(LocalDate.now().toString());
        Files.createDirectories(day);
        int n = 1;
        Path d;
        do { d = day.resolve(String.format("execution-%03d", n++)); } while (Files.exists(d));
        Files.createDirectories(d);
        return d;
    }

    public static List<TestResult> loadResults(Path execDir) throws IOException {
        Path dir = execDir.resolve("results");
        List<TestResult> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".json")).sorted()::iterator)
                out.add(JsonUtils.read(p, TestResult.class));
        }
        return out;
    }

    public Path generate(Path execDir, List<TestResult> results, String browser, long totalMs) throws IOException {
        Summary s = new Summary();
        s.date = execDir.getParent() == null ? "" : execDir.getParent().getFileName().toString();
        s.browser = browser;
        s.started = java.time.Instant.now().toString();
        s.durationMs = totalMs;
        s.results = results;
        s.total = results.size();
        for (TestResult r : results) {
            switch (r.status) {
                case "PASSED" -> s.passed++;
                case "FAILED" -> s.failed++;
                default -> s.skipped++;
            }
        }
        JsonUtils.write(execDir.resolve("summary.json"), s);
        Path index = execDir.resolve("index.html");
        FileUtils.write(index, html(s, execDir.getFileName().toString()));
        return index;
    }

    public List<HistoryRow> history(Path projectRoot) {
        List<HistoryRow> rows = new ArrayList<>();
        Path reports = projectRoot.resolve("reports");
        if (!Files.isDirectory(reports)) return rows;
        try (Stream<Path> s = Files.walk(reports, 3)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.getFileName().toString().equals("summary.json"))::iterator) {
                Summary sum = JsonUtils.read(p, Summary.class);
                for (TestResult r : sum.results) {
                    HistoryRow h = new HistoryRow();
                    h.date = sum.date; h.test = r.testName; h.browser = r.browser.isEmpty() ? sum.browser : r.browser;
                    h.status = r.status; h.durationMs = r.durationMs;
                    h.failure = r.exception.isEmpty() ? "" : r.exception.split("\n")[0];
                    h.reportPath = p.getParent().resolve("index.html").toString();
                    rows.add(h);
                }
            }
        } catch (IOException e) {
            // unreadable history entries are skipped
        }
        rows.sort(Comparator.comparing((HistoryRow h) -> h.reportPath).reversed());
        return rows;
    }

    public void clearHistory(Path projectRoot) throws IOException {
        Path reports = projectRoot.resolve("reports");
        if (!Files.isDirectory(reports)) return;
        List<Path> entries;
        try (Stream<Path> children = Files.list(reports)) {
            entries = children.toList();
        }
        for (Path entry : entries) FileUtils.deleteDir(entry);
    }

    // ------------------------------------------------------------------ HTML

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String dur(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private String html(Summary s, String title) {
        StringBuilder h = new StringBuilder();
        int pct = s.total == 0 ? 0 : Math.round(s.passed * 100f / s.total);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Playwright Test Studio - ").append(esc(title)).append("</title><style>")
                .append("body{font-family:Segoe UI,Arial,sans-serif;margin:24px;background:#f5f6f8;color:#222}")
                .append("h1{margin:0 0 12px}.cards{display:flex;gap:12px;margin-bottom:20px}")
                .append(".card{background:#fff;border-radius:8px;padding:12px 18px;box-shadow:0 1px 3px #0002}")
                .append(".card b{display:block;font-size:24px}.PASSED{color:#188038}.FAILED{color:#c5221f}.SKIPPED{color:#b06000}")
                .append("details{background:#fff;border-radius:8px;margin:8px 0;padding:10px 14px;box-shadow:0 1px 3px #0002}")
                .append("summary{cursor:pointer;font-weight:600}table{border-collapse:collapse;width:100%;margin:8px 0}")
                .append("td,th{border-bottom:1px solid #eee;padding:4px 8px;text-align:left;font-size:13px}")
                .append("pre{background:#1e1e1e;color:#ddd;padding:10px;overflow:auto;font-size:12px;border-radius:6px}")
                .append("img{max-width:100%;border:1px solid #ccc;margin:6px 0}</style></head><body>");
        h.append("<h1>Playwright Test Studio report</h1><div>").append(esc(s.date)).append(" / ").append(esc(title)).append("</div><br>");
        h.append("<div class=\"cards\">")
                .append(card("Total tests", String.valueOf(s.total), "")).append(card("Passed", String.valueOf(s.passed), "PASSED"))
                .append(card("Failed", String.valueOf(s.failed), "FAILED")).append(card("Skipped", String.valueOf(s.skipped), "SKIPPED"))
                .append(card("Pass percentage", pct + "%", "")).append(card("Execution time", dur(s.durationMs), "")).append("</div>");
        for (TestResult r : s.results) {
            long ok = r.steps.stream().filter(x -> "PASSED".equals(x.status)).count();
            long bad = r.steps.stream().filter(x -> "FAILED".equals(x.status)).count();
            h.append("<details").append("FAILED".equals(r.status) ? " open" : "").append("><summary><span class=\"").append(r.status).append("\">")
                    .append(esc(r.status)).append("</span> &nbsp;").append(esc(r.testName)).append(" &nbsp;<small>(").append(r.durationMs).append(" ms)</small></summary>");
            h.append("<table><tr><th>Browser</th><td>").append(esc(r.browser)).append("</td><th>URL</th><td>").append(esc(r.url)).append("</td></tr>")
                    .append("<tr><th>Start</th><td>").append(esc(r.startTime)).append("</td><th>End</th><td>").append(esc(r.endTime)).append("</td></tr>")
                    .append("<tr><th>Page title</th><td>").append(esc(r.title)).append("</td><th>Steps</th><td>").append(r.steps.size())
                    .append(" (passed ").append(ok).append(", failed ").append(bad).append(")</td></tr></table>");
            h.append("<table><tr><th>#</th><th>Step</th><th>Status</th><th>ms</th><th>Screenshot</th></tr>");
            for (TestResult.StepResult st : r.steps) {
                h.append("<tr><td>").append(st.number).append("</td><td>").append(esc(st.description));
                if (!st.error.isEmpty()) h.append("<br><small class=\"FAILED\">").append(esc(st.error)).append("</small>");
                h.append("</td><td class=\"").append(st.status).append("\">").append(esc(st.status)).append("</td><td>").append(st.durationMs).append("</td><td>")
                        .append(st.screenshot.isEmpty() ? "" : "<a href=\"" + esc(st.screenshot) + "\">image</a>").append("</td></tr>");
            }
            h.append("</table>");
            if (!r.exception.isEmpty()) {
                h.append("<h4>Failure</h4><p><b>Step:</b> ").append(esc(r.failedStep)).append("<br><b>Exception:</b> ").append(esc(r.exception)).append("</p>");
                h.append("<pre>").append(esc(r.stackTrace)).append("</pre>");
            }
            if (!r.screenshot.isEmpty()) h.append("<h4>Screenshot</h4><img src=\"").append(esc(r.screenshot)).append("\">");
            if (!r.video.isEmpty()) h.append("<h4>Video</h4><video controls width=\"640\" src=\"").append(esc(r.video)).append("\"></video>");
            if (!r.trace.isEmpty()) h.append("<p>Trace: <a href=\"").append(esc(r.trace)).append("\">").append(esc(r.trace))
                    .append("</a> (open with <code>playwright show-trace</code>)</p>");
            h.append("<h4>Logs</h4><pre>").append(esc(String.join("\n", r.logs))).append("</pre></details>");
        }
        return h.append("</body></html>").toString();
    }

    private static String card(String label, String value, String cls) {
        return "<div class=\"card\">" + label + "<b class=\"" + cls + "\">" + value + "</b></div>";
    }
}
