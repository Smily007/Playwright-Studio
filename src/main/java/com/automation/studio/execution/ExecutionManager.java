package com.automation.studio.execution;

import com.automation.studio.generator.CodeGenerator;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.generator.PageObjectGenerator;
import com.automation.studio.generator.TestNGGenerator;
import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectEnvironment;
import com.automation.studio.model.TestCase;
import com.automation.studio.model.TestResult;
import com.automation.studio.reporting.ReportManager;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Orchestrates generate -> compile -> TestNG -> report on a background thread (never the JavaFX thread). */
public class ExecutionManager {
    private final CodeGenerator generator;
    private final TestNGGenerator suiteGenerator = new TestNGGenerator();
    private final TestExecutor executor;
    private final ReportManager reports;
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "execution-manager");
        t.setDaemon(true);
        return t;
    });

    public ExecutionManager(CodeGenerator generator, TestExecutor executor, ReportManager reports) {
        this.generator = generator;
        this.executor = executor;
        this.reports = reports;
    }

    /**
     * @param codeOverrides class name -> source typed in the code editor (used instead of regenerating)
     * @param asSuite       run through a generated TestNG suite XML
     */
    public CompletableFuture<ExecutionResult> run(Project project, List<TestCase> tests, Map<String, String> codeOverrides,
                                                  boolean asSuite, ProjectEnvironment environment, Consumer<String> out) {
        return run(project, tests, codeOverrides, asSuite, environment, false, null, out);
    }

    public CompletableFuture<ExecutionResult> run(Project project, List<TestCase> tests, Map<String, String> codeOverrides,
                                                  boolean asSuite, ProjectEnvironment environment, boolean debugMode,
                                                  DebuggerController debugger, Consumer<String> out) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (environment == null || environment.name == null || environment.name.isBlank()
                        || environment.url == null || environment.url.isBlank())
                    throw new IllegalArgumentException("Select a project environment with a valid base URL before running tests.");
                Path execDir = reports.nextExecutionDir(project.root);
                Map<String, String> sources = new LinkedHashMap<>();
                PageObjectGenerator.GeneratedPages pageObjects = new PageObjectGenerator().generate(project);
                for (TestCase tc : tests) {
                    String cls = generator.className(tc);
                    String generated = generator instanceof JavaTestGenerator javaGenerator
                            ? javaGenerator.generate(tc, project, pageObjects)
                            : generator.generate(tc, project);
                    sources.put(cls, codeOverrides.getOrDefault(cls, generated));
                }
                ExecutionRequest req = new ExecutionRequest();
                req.projectDir = project.root;
                req.reportDir = execDir;
                req.sources = sources;
                req.pageObjectSources = pageObjects.sources();
                req.config = project.browserConfig;
                req.environmentName = environment.name;
                req.environmentUrl = environment.url;
                req.sourceBaseUrl = project.baseUrl;
                req.debugMode = debugMode;
                req.debugger = debugger;
                if (project.excelDataFile != null && !project.excelDataFile.isBlank()) {
                    req.excelDataFile = project.root.resolve(project.excelDataFile).normalize();
                    if (!req.excelDataFile.startsWith(project.root.normalize()))
                        throw new IllegalArgumentException("Excel data file must be inside the project folder.");
                    req.excelSheetName = project.excelSheetName;
                }
                if (asSuite || project.browserConfig.threadCount > 1)
                    req.suiteXml = suiteGenerator.suiteXml(project.projectName, List.copyOf(sources.keySet()), project.browserConfig.threadCount);
                out.accept("Report folder: " + execDir);
                ExecutionResult r = executor.execute(req, out);
                List<TestResult> results = ReportManager.loadResults(execDir);
                r.results = results;
                r.indexHtml = reports.generate(execDir, results, project.browserConfig.browser, r.durationMs);
                return r;
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, pool);
    }
}
