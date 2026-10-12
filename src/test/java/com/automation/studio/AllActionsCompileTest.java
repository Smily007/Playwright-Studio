package com.automation.studio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.actions.ActionDefinition;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.model.Project;
import com.automation.studio.model.TestCase;
import com.automation.studio.model.TestStep;

/** Generates one step for EVERY action and compiles the result with the JDK compiler. */
public class AllActionsCompileTest {

    @Test
    public void generatedCodeForEveryActionCompiles() throws Exception {
        ActionCatalog catalog = new ActionCatalog();
        JavaTestGenerator gen = new JavaTestGenerator(catalog);
        TestCase tc = new TestCase("AllActions");
        for (ActionDefinition d : catalog.all()) {
            TestStep s = new TestStep(d.id);
            if (d.needsLocator) s.locator = LocatorInfo.css("#x");
            if (d.id.equals("FILL")) s.params.put("value", "${var:LoginName}");
            if (d.id.equals("WAIT_FOR_TIMEOUT")) s.params.put("ms", "${var:DelayMs}");
            tc.steps.add(s);
        }
        String source = gen.generate(tc, new Project());

        Path dir = Files.createTempDirectory("studio-compile");
        Path src = dir.resolve("AllActions.java");
        Files.writeString(src, source);
        Path out = Files.createDirectory(dir.resolve("classes"));

        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull(jc, "tests must run on a JDK");
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(out.toFile()));
            boolean ok = jc.getTask(null, fm, diags, List.of("-classpath", System.getProperty("java.class.path"), "-proc:none"), null,
                    fm.getJavaFileObjectsFromFiles(List.of(src.toFile()))).call();
            StringBuilder sb = new StringBuilder();
            String[] lines = source.split("\n");
            for (Diagnostic<? extends JavaFileObject> dg : diags.getDiagnostics())
                if (dg.getKind() == Diagnostic.Kind.ERROR)
                    sb.append("line ").append(dg.getLineNumber()).append(": ").append(dg.getMessage(null)).append("\n   ")
                            .append(dg.getLineNumber() > 0 && dg.getLineNumber() <= lines.length ? lines[(int) dg.getLineNumber() - 1].trim() : "").append('\n');
            Assert.assertTrue(ok, "Generated code does not compile:\n" + sb);
        }
    }
}
