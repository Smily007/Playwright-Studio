package com.automation.studio;

import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectEnvironment;
import com.automation.studio.model.ProjectPageObject;
import com.automation.studio.model.TestCase;
import com.automation.studio.project.ProjectService;
import com.automation.studio.utilities.JsonUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectServiceTest {
    private final ProjectService projects = new ProjectService();

    @Test
    public void createsAndReopensFolderBasedProject() throws Exception {
        Path parent = Files.createTempDirectory("studio-projects");
        try {
            Project created = projects.create(parent, "Login Project", java.util.List.of(
                    new ProjectEnvironment("Development", "https://dev.example.com"),
                    new ProjectEnvironment("Staging", "https://stage.example.com")));
            created.excelDataFile = "testdata/excel/users.xlsx";
            created.excelSheetName = "Users";
            TestCase login = new TestCase("Login");
            login.steps.add(new com.automation.studio.model.TestStep("NAVIGATE").with("url", "https://dev.example.com/login"));
            com.automation.studio.model.TestStep email = new com.automation.studio.model.TestStep("FILL");
            email.locator = com.automation.studio.locator.LocatorInfo.css("#email");
            email.with("value", "user@example.com");
            login.steps.add(email);
            created.tests.add(login);
            ProjectPageObject pageObject = new ProjectPageObject();
            pageObject.className = "LoginPage";
            pageObject.origin = "https://dev.example.com";
            pageObject.testNames.add("Login");
            created.pageObjects.add(pageObject);
            projects.save(created);

            Project reopened = projects.open(created.root);

            Assert.assertEquals(reopened.projectName, "Login Project");
            Assert.assertEquals(reopened.tests.size(), 1);
            Assert.assertEquals(reopened.tests.get(0).name, "Login");
            Assert.assertEquals(reopened.root, created.root);
            Assert.assertEquals(reopened.environments.size(), 2);
            Assert.assertEquals(reopened.environments.get(1).url, "https://stage.example.com");
            Assert.assertEquals(reopened.excelDataFile, "testdata/excel/users.xlsx");
            Assert.assertEquals(reopened.excelSheetName, "Users");
            Assert.assertEquals(reopened.pageObjects.size(), 1);
            Assert.assertEquals(reopened.pageObjects.get(0).className, "LoginPage");
            Assert.assertTrue(Files.isRegularFile(reopened.root.resolve("src/main/java/pages/LoginPage.java")));
            Assert.assertTrue(Files.isDirectory(reopened.root.resolve("generated")));
            Assert.assertTrue(Files.isDirectory(reopened.root.resolve("src/main/java/pages")));
            Assert.assertTrue(Files.isDirectory(reopened.root.resolve("src/test/java/tests")));
            Assert.assertTrue(Files.isDirectory(reopened.root.resolve("src/test/resources")));
        } finally {
            com.automation.studio.utilities.FileUtils.deleteDir(parent);
        }
    }

    @Test
    public void refusesToCreateProjectInsideAnExistingFolder() throws Exception {
        Path parent = Files.createTempDirectory("studio-project-existing");
        try {
            Path existing = Files.createDirectory(parent.resolve("Existing"));
            Path marker = Files.writeString(existing.resolve("keep.txt"), "preserve");

            Assert.expectThrows(IOException.class, () -> projects.create(parent, "Existing"));

            Assert.assertEquals(Files.readString(marker), "preserve");
            Assert.assertFalse(Files.exists(existing.resolve("project.json")));
        } finally {
            com.automation.studio.utilities.FileUtils.deleteDir(parent);
        }
    }

    @Test
    public void preservesExistingHandWrittenPageClassInsteadOfReplacingIt() throws Exception {
        Path parent = Files.createTempDirectory("studio-page-object-conflict");
        try {
            Project project = projects.create(parent, "Page Conflict",
                    java.util.List.of(new ProjectEnvironment("Development", "https://dev.example.com")));
            TestCase test = new TestCase("Login");
            test.steps.add(new com.automation.studio.model.TestStep("NAVIGATE").with("url", "https://dev.example.com/login"));
            com.automation.studio.model.TestStep fill = new com.automation.studio.model.TestStep("FILL");
            fill.locator = com.automation.studio.locator.LocatorInfo.css("#email");
            test.steps.add(fill);
            project.tests.add(test);
            String className = new com.automation.studio.generator.PageObjectGenerator()
                    .generate(project).sources().keySet().iterator().next();
            Path handwritten = project.root.resolve("src/main/java/pages").resolve(className + ".java");
            String content = "public class " + className + " { }";
            Files.writeString(handwritten, content);

            Assert.expectThrows(IOException.class, () -> projects.save(project));
            Assert.assertEquals(Files.readString(handwritten), content);
        } finally {
            com.automation.studio.utilities.FileUtils.deleteDir(parent);
        }
    }

    @Test
    public void upgradesLegacyBaseUrlToDefaultEnvironmentWhenOpeningProject() throws Exception {
        Path root = Files.createTempDirectory("studio-legacy-project");
        try {
            Project legacy = new Project();
            legacy.projectName = "Legacy";
            legacy.baseUrl = "https://legacy.example.com";
            JsonUtils.write(root.resolve("project.json"), legacy);

            Project opened = projects.open(root);

            Assert.assertEquals(opened.environments.size(), 1);
            Assert.assertEquals(opened.environments.get(0).name, "Default");
            Assert.assertEquals(opened.environments.get(0).url, "https://legacy.example.com");
        } finally {
            com.automation.studio.utilities.FileUtils.deleteDir(root);
        }
    }
}
