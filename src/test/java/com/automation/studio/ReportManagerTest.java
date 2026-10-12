package com.automation.studio;

import com.automation.studio.reporting.ReportManager;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReportManagerTest {
    @Test
    public void clearsProjectReportsWithoutRemovingReportsDirectoryOrOtherProjectFiles() throws Exception {
        Path project = Files.createTempDirectory("studio-report-history");
        try {
            Path execution = Files.createDirectories(project.resolve("reports/2026-10-09/execution-001"));
            Files.writeString(execution.resolve("summary.json"), "{}");
            Files.writeString(execution.resolve("index.html"), "report");
            Path otherFile = Files.writeString(project.resolve("project.json"), "{}");

            new ReportManager().clearHistory(project);

            Assert.assertTrue(Files.isDirectory(project.resolve("reports")));
            Assert.assertEquals(new ReportManager().history(project).size(), 0);
            Assert.assertTrue(Files.exists(otherFile));
            Assert.assertFalse(Files.exists(execution));
        } finally {
            com.automation.studio.utilities.FileUtils.deleteDir(project);
        }
    }
}
