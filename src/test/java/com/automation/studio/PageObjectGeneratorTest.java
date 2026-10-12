package com.automation.studio;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.generator.PageObjectGenerator;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectPageObject;
import com.automation.studio.model.TestCase;
import com.automation.studio.model.TestStep;
import com.automation.studio.utilities.FileUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PageObjectGeneratorTest {
    private static TestStep navigate(String url) {
        return new TestStep("NAVIGATE").with("url", url);
    }

    private static TestStep fill(LocatorInfo locator, String value) {
        TestStep step = new TestStep("FILL");
        step.locator = locator;
        step.with("value", value);
        return step;
    }

    private static LocatorInfo label(String text) {
        LocatorInfo locator = new LocatorInfo();
        locator.locatorType = LocatorInfo.Type.LABEL;
        locator.locatorValue = text;
        return locator;
    }

    @Test
    public void buildsReusablePageObjectForRelatedTestsOnSameOrigin() {
        Project project = new Project();
        project.baseUrl = "https://app.example.com";
        TestCase login = new TestCase("Login");
        login.steps.add(navigate("https://app.example.com/login"));
        login.steps.add(fill(label("Email"), "${var:Email}"));
        TestCase profile = new TestCase("Profile");
        profile.steps.add(navigate("https://app.example.com/profile"));
        profile.steps.add(fill(label("Email"), "${var:Email}"));
        project.tests.add(login);
        project.tests.add(profile);

        PageObjectGenerator.GeneratedPages pages = new PageObjectGenerator().generate(project);
        String pageSource = pages.sources().values().iterator().next();
        String testSource = new JavaTestGenerator(new ActionCatalog()).generate(login, project, pages);
        var loginRef = pages.reference(login, 1);
        var profileRef = pages.reference(profile, 1);

        Assert.assertEquals(pages.sources().size(), 1);
        Assert.assertEquals(loginRef.className(), profileRef.className());
        Assert.assertEquals(loginRef.methodName(), profileRef.methodName());
        Assert.assertTrue(pageSource.contains("public Locator " + loginRef.methodName() + "(Map<String, String> vars)"));
        Assert.assertTrue(pageSource.contains("page.getByLabel(\"Email\")"));
        Assert.assertTrue(testSource.contains("import pages.*;"));
        Assert.assertTrue(testSource.contains(loginRef.variableName() + "." + loginRef.methodName()
                + "(vars).fill(TestData.resolve(\"${var:Email}\", vars));"));
    }

    @Test
    public void separatesPageObjectsByApplicationOrigin() {
        Project project = new Project();
        project.baseUrl = "https://app.example.com";
        TestCase test = new TestCase("Cross domain");
        test.steps.add(navigate("https://app.example.com"));
        test.steps.add(fill(label("Email"), "first"));
        test.steps.add(navigate("https://accounts.example.net"));
        test.steps.add(fill(label("Email"), "second"));
        project.tests.add(test);

        PageObjectGenerator.GeneratedPages pages = new PageObjectGenerator().generate(project);

        Assert.assertEquals(pages.sources().size(), 2);
        Assert.assertNotEquals(pages.reference(test, 1).className(), pages.reference(test, 3).className());
    }

    @Test
    public void usesUserDefinedPageClassForLinkedTests() {
        Project project = new Project();
        project.baseUrl = "https://app.example.com";
        TestCase test = new TestCase("Checkout");
        test.steps.add(navigate("https://app.example.com/checkout"));
        test.steps.add(fill(label("Coupon"), "SAVE10"));
        project.tests.add(test);
        ProjectPageObject pageObject = new ProjectPageObject();
        pageObject.className = "CheckoutPage";
        pageObject.origin = "https://app.example.com";
        pageObject.testNames.add(test.name);
        project.pageObjects.add(pageObject);

        PageObjectGenerator.GeneratedPages pages = new PageObjectGenerator().generate(project);

        Assert.assertTrue(pages.sources().containsKey("CheckoutPage"));
        Assert.assertEquals(pages.reference(test, 1).className(), "CheckoutPage");
    }

    @Test
    public void generatedPageObjectAndTestSourcesCompileTogether() throws Exception {
        Project project = new Project();
        project.baseUrl = "https://app.example.com";
        TestCase test = new TestCase("Login");
        test.steps.add(navigate("https://app.example.com/login"));
        test.steps.add(fill(label("Email"), "${var:Email}"));
        project.tests.add(test);
        PageObjectGenerator.GeneratedPages pages = new PageObjectGenerator().generate(project);
        String testSource = new JavaTestGenerator(new ActionCatalog()).generate(test, project, pages);

        Path root = Files.createTempDirectory("studio-pom-compile");
        try {
            Path pageFile = root.resolve("pages").resolve(pages.sources().keySet().iterator().next() + ".java");
            Path testFile = root.resolve("tests").resolve("Login.java");
            Path classes = Files.createDirectory(root.resolve("classes"));
            Files.createDirectories(pageFile.getParent());
            Files.createDirectories(testFile.getParent());
            Files.writeString(pageFile, pages.sources().values().iterator().next());
            Files.writeString(testFile, testSource);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            Assert.assertNotNull(compiler);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                manager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classes.toFile()));
                boolean compiled = compiler.getTask(null, manager, diagnostics,
                        List.of("-classpath", System.getProperty("java.class.path"), "-proc:none"), null,
                        manager.getJavaFileObjectsFromFiles(List.of(pageFile.toFile(), testFile.toFile()))).call();
                String errors = diagnostics.getDiagnostics().stream()
                        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                        .map(diagnostic -> diagnostic.getMessage(null))
                        .reduce("", (left, right) -> left + right + "\n");
                Assert.assertTrue(compiled, errors);
            }
        } finally {
            FileUtils.deleteDir(root);
        }
    }
}
