package com.automation.studio.configuration;

import com.automation.studio.utilities.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Application-level settings (recent projects, default projects folder) in ~/.playwright-test-studio/settings.json. */
public class ConfigurationManager {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        public List<String> recentProjects = new ArrayList<>();
        public String projectsDir = Paths.get(System.getProperty("user.home"), "PlaywrightTestStudio", "projects").toString();
        public String theme = "light";
        public String textSize = "default";
    }

    private final Path file = Paths.get(System.getProperty("user.home"), ".playwright-test-studio", "settings.json");
    private Settings settings = new Settings();

    public ConfigurationManager() {
        try {
            if (Files.exists(file)) settings = JsonUtils.read(file, Settings.class);
        } catch (IOException e) {
            settings = new Settings();
        }
    }

    public Settings get() { return settings; }

    public void addRecent(Path projectDir) {
        String p = projectDir.toAbsolutePath().toString();
        settings.recentProjects.remove(p);
        settings.recentProjects.add(0, p);
        while (settings.recentProjects.size() > 8) settings.recentProjects.remove(settings.recentProjects.size() - 1);
        save();
    }

    public void save() {
        try { JsonUtils.write(file, settings); } catch (IOException ignored) { /* settings are best effort */ }
    }
}
