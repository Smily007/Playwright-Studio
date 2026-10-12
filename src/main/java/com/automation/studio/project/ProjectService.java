package com.automation.studio.project;

import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectEnvironment;
import com.automation.studio.model.TestCase;
import com.automation.studio.generator.PageObjectGenerator;
import com.automation.studio.utilities.FileUtils;
import com.automation.studio.utilities.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Create / open / save / save-as of projects stored as a folder with project.json. */
public class ProjectService {
    private static final String[] DIRS = {
            "tests", "testdata", "screenshots", "videos", "reports", "logs", "generated",
            "src/main/java/pages", "src/main/resources", "src/test/java/tests", "src/test/resources"
    };

    public Project create(Path parentDir, String name) throws IOException {
        Path root = parentDir.resolve(FileUtils.sanitize(name));
        if (Files.exists(root)) throw new IOException("The project folder already exists: " + root);
        Files.createDirectories(parentDir);
        Project p = new Project();
        p.projectName = name;
        p.root = root;
        for (String d : DIRS) Files.createDirectories(root.resolve(d));
        JsonUtils.write(root.resolve("project.json"), p);
        return p;
    }

    public Project create(Path parentDir, String name, java.util.List<ProjectEnvironment> environments) throws IOException {
        if (environments == null || environments.isEmpty())
            throw new IOException("Add at least one project environment.");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (ProjectEnvironment environment : environments) {
            if (environment == null || environment.name == null || environment.name.isBlank()
                    || environment.url == null || environment.url.isBlank())
                throw new IOException("Every project environment must have a name and URL.");
            if (!names.add(environment.name.trim().toLowerCase(java.util.Locale.ROOT)))
                throw new IOException("Project environment names must be unique.");
        }
        Project p = create(parentDir, name);
        p.environments = new java.util.ArrayList<>(environments);
        p.baseUrl = environments.get(0).url;
        save(p);
        return p;
    }

    public Project open(Path root) throws IOException {
        Path f = root.resolve("project.json");
        if (!Files.exists(f)) throw new IOException("Not a Playwright Test Studio project (project.json missing): " + root);
        Project p = JsonUtils.read(f, Project.class);
        p.root = root;
        if (p.environments == null) p.environments = new java.util.ArrayList<>();
        if (p.pageObjects == null) p.pageObjects = new java.util.ArrayList<>();
        if (p.environments.isEmpty())
            p.environments.add(new ProjectEnvironment("Default", p.baseUrl == null ? "" : p.baseUrl));
        if (p.baseUrl == null || p.baseUrl.isBlank()) p.baseUrl = p.environments.get(0).url;
        for (String d : DIRS) Files.createDirectories(root.resolve(d));
        for (TestCase t : p.tests) t.renumber();
        persistPageObjects(p);
        return p;
    }

    public void save(Project p) throws IOException {
        if (p.root == null) throw new IOException("Project has no location");
        if (p.environments == null || p.environments.isEmpty())
            throw new IOException("Project must have at least one environment with a URL.");
        for (ProjectEnvironment environment : p.environments) {
            if (environment.name == null || environment.name.isBlank() || environment.url == null || environment.url.isBlank())
                throw new IOException("Every project environment must have a name and URL.");
        }
        for (TestCase t : p.tests) t.renumber();
        persistPageObjects(p);
        JsonUtils.write(p.root.resolve("project.json"), p);
    }

    public void persistPageObjects(Project project) throws IOException {
        PageObjectGenerator.GeneratedPages pages = new PageObjectGenerator().generate(project);
        Path sourceRoot = project.root.resolve("src/main/java/pages");
        Files.createDirectories(sourceRoot);
        for (var source : pages.sources().entrySet())
            FileUtils.writeGeneratedSource(sourceRoot.resolve(source.getKey() + ".java"), source.getValue());
    }

    public Project saveAs(Project p, Path newRoot) throws IOException {
        Files.createDirectories(newRoot);
        if (p.root != null && Files.exists(p.root)) FileUtils.copyDir(p.root, newRoot);
        p.root = newRoot;
        save(p);
        return p;
    }
}
