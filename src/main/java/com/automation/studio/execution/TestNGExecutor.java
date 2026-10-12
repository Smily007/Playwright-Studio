package com.automation.studio.execution;

import com.automation.studio.utilities.FileUtils;

import javax.tools.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compiles the generated sources with the JDK compiler, then runs them with TestNG in a separate JVM
 * (isolated from the JavaFX app) and streams the output.
 */
public class TestNGExecutor implements TestExecutor {

    @Override
    public ExecutionResult execute(ExecutionRequest req, Consumer<String> out) throws Exception {
        long t0 = System.currentTimeMillis();
        Path gen = req.projectDir.resolve("generated");
        Path classes = gen.resolve("classes");
        FileUtils.deleteDir(classes);
        FileUtils.ensureDirs(classes, req.reportDir);

        List<File> files = new ArrayList<>();
        Path pageSourceRoot = req.projectDir.resolve("src/main/java/pages");
        java.util.Map<String, String> pageObjectSources = req.pageObjectSources == null ? java.util.Map.of() : req.pageObjectSources;
        for (var e : pageObjectSources.entrySet()) {
            Path f = pageSourceRoot.resolve(e.getKey() + ".java");
            FileUtils.writeGeneratedSource(f, e.getValue());
            files.add(f.toFile());
        }
        Path testSourceRoot = req.projectDir.resolve("src/test/java/tests");
        for (var e : req.sources.entrySet()) {
            Path f = gen.resolve(e.getKey() + ".java");
            FileUtils.write(f, e.getValue());
            Path frameworkSource = testSourceRoot.resolve(e.getKey() + ".java");
            FileUtils.write(frameworkSource, e.getValue());
            files.add(frameworkSource.toFile());
        }
        String cp = ProcessRunner.currentClasspath();
        out.accept("Compiling " + pageObjectSources.size() + " page object(s) and " + req.sources.size() + " generated test class(es)...");
        compile(files, classes, cp);
        out.accept("Compilation OK");

        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessRunner.javaBin());
        List<String> browserProperties = req.config.toSystemProps();
        if (req.debugMode) {
            browserProperties.removeIf(property -> property.startsWith("-Dstudio.headless="));
            browserProperties.add("-Dstudio.headless=false");
        }
        cmd.addAll(browserProperties);
        cmd.add("-Dstudio.reportDir=" + req.reportDir.toAbsolutePath());
        cmd.add("-Dstudio.projectDir=" + req.projectDir.toAbsolutePath());
        cmd.add("-Dstudio.environment=" + req.environmentName);
        cmd.add("-Dstudio.baseUrl=" + req.environmentUrl);
        cmd.add("-Dstudio.sourceBaseUrl=" + req.sourceBaseUrl);
        if (req.debugMode) cmd.add("-Dstudio.debug=true");
        if (req.excelDataFile != null) {
            cmd.add("-Dstudio.excelDataFile=" + req.excelDataFile.toAbsolutePath());
            cmd.add("-Dstudio.excelSheetName=" + req.excelSheetName);
        }
        cmd.add("-cp");
        cmd.add(classes.toAbsolutePath() + File.pathSeparator + cp);
        cmd.add(RunnerMain.class.getName());
        if (req.suiteXml != null) {
            Path suite = gen.resolve("suite.xml");
            FileUtils.write(suite, req.suiteXml);
            cmd.add("--suite=" + suite.toAbsolutePath());
        } else {
            cmd.add("--classes=" + String.join(",", req.sources.keySet().stream().map(c -> "tests." + c).toList()));
        }
        out.accept("Running TestNG...");
        ExecutionResult r = new ExecutionResult();
        r.exitCode = ProcessRunner.run(cmd, req.projectDir, out, req.debugger);
        r.durationMs = System.currentTimeMillis() - t0;
        r.reportDir = req.reportDir;
        return r;
    }

    private void compile(List<File> files, Path classesDir, String classpath) throws Exception {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) throw new IllegalStateException("No Java compiler available. Run Playwright Test Studio with a JDK (not a JRE).");
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir.toFile()));
            boolean ok = jc.getTask(null, fm, diags, List.of("-classpath", classpath, "-proc:none"), null,
                    fm.getJavaFileObjectsFromFiles(files)).call();
            if (!ok) {
                StringBuilder sb = new StringBuilder("Java compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics())
                    if (d.getKind() == Diagnostic.Kind.ERROR)
                        sb.append(d.getSource() == null ? "" : new File(d.getSource().toUri()).getName()).append(':').append(d.getLineNumber())
                                .append(": ").append(d.getMessage(null)).append('\n');
                throw new CompilationException(sb.toString());
            }
        }
    }
}
