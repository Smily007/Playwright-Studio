package com.automation.studio.cli;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.execution.ExecutionManager;
import com.automation.studio.execution.TestNGExecutor;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectEnvironment;
import com.automation.studio.model.TestCase;
import com.automation.studio.project.ProjectService;
import com.automation.studio.reporting.ReportManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(name = "playwright-studio", mixinStandardHelpOptions = true,
        description = "Run Playwright Test Studio tests headlessly with JUnit XML output")
public class StudioCli implements Callable<Integer> {
    @Option(names = {"--project"}, required = true, description = "Project directory containing project.json")
    private Path projectDir;

    @Option(names = {"--test"}, description = "Specific test case name(s), comma separated")
    private String tests;

    @Option(names = {"--env"}, description = "Environment name to run against")
    private String env;

    @Option(names = {"--browser"}, description = "Browser: chromium|chrome|msedge|firefox|webkit")
    private String browser;

    @Option(names = {"--headless"}, description = "Run headless (default true in CLI)", defaultValue = "true")
    private boolean headless;

    @Option(names = {"--junit"}, description = "Output JUnit XML to this path", defaultValue = "target/studio-junit.xml")
    private Path junit;

    @Option(names = {"--report-dir"}, description = "Report directory (defaults to reports/...)")
    private Path reportDir;

    @Option(names = {"--threads"}, description = "Parallel threads")
    private Integer threads;

    @Override
    public Integer call() throws Exception {
        ProjectService ps = new ProjectService();
        Project project = ps.open(projectDir);
        if (browser != null && !browser.isBlank()) project.browserConfig.browser = browser;
        if (threads != null) project.browserConfig.threadCount = threads;
        project.browserConfig.headless = headless;

        ProjectEnvironment environment = null;
        if (env != null && !env.isBlank()) {
            for (ProjectEnvironment e : project.environments) {
                if (env.equalsIgnoreCase(e.name)) { environment = e; break; }
            }
            if (environment == null) throw new IllegalArgumentException("Environment not found: " + env);
        } else if (!project.environments.isEmpty()) {
            environment = project.environments.get(0);
        } else {
            throw new IllegalArgumentException("No environments configured in project");
        }

        List<TestCase> toRun = new ArrayList<>();
        if (tests != null && !tests.isBlank()) {
            String[] names = tests.split(",");
            for (String name : names) {
                String n = name.trim();
                for (TestCase tc : project.tests) {
                    if (n.equals(tc.name) || n.equals(tc.name + ".java")) { toRun.add(tc); break; }
                }
            }
        } else {
            toRun.addAll(project.tests);
        }
        if (toRun.isEmpty()) throw new IllegalArgumentException("No test cases to run");

        ReportManager rm = new ReportManager();
        ExecutionManager em = new ExecutionManager(new JavaTestGenerator(new ActionCatalog()), new TestNGExecutor(), rm);
        AtomicBoolean failed = new AtomicBoolean(false);
        var result = em.run(project, toRun, Map.of(), toRun.size() > 1 || project.browserConfig.threadCount > 1, environment, System.out::println).join();
        try {
            generateJUnitXml(result, junit);
            System.out.println("JUnit XML: " + junit.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write JUnit XML: " + e.getMessage());
        }
        if (!result.allPassed()) failed.set(true);
        return failed.get() ? 1 : 0;
    }

    private void generateJUnitXml(com.automation.studio.execution.ExecutionResult result, Path out) throws Exception {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        int total = result.results.size();
        int failures = 0, errors = 0;
        for (var r : result.results) {
            if ("FAILED".equals(r.status)) failures++;
            if (r.exception != null && !r.exception.isBlank()) errors++;
        }
        double time = result.durationMs / 1000.0;
        sb.append("<testsuite name=\"PlaywrightStudio\" tests=\"").append(total).append("\" failures=\"").append(failures).append("\" errors=\"").append(errors).append("\" time=\"").append(String.format("%.3f", time)).append("\">\n");
        for (var r : result.results) {
            double t2 = r.durationMs / 1000.0;
            sb.append("  <testcase classname=\"").append(escape(r.testName)).append("\" name=\"").append(escape(r.testName)).append("\" time=\"").append(String.format("%.3f", t2)).append("\">\n");
            if ("FAILED".equals(r.status)) {
                String msg = r.exception == null ? "" : r.exception.replace("\"", "&quot;");
                String trace = r.stackTrace == null ? "" : escape(r.stackTrace);
                sb.append("    <failure message=\"").append(msg).append("\" type=\"AssertionError\">\n").append(trace).append("\n    </failure>\n");
            }
            sb.append("  </testcase>\n");
        }
        sb.append("</testsuite>\n");
        Files.writeString(out, sb.toString());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new StudioCli()).execute(args);
        System.exit(exit);
    }
}
