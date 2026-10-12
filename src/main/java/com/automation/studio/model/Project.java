package com.automation.studio.model;

import com.automation.studio.browser.BrowserConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {
    public String projectName = "NewProject";
    public String baseUrl = "";
    public String excelDataFile = "";
    public String excelSheetName = "";
    public List<ProjectEnvironment> environments = new ArrayList<>();
    public List<ProjectPageObject> pageObjects = new ArrayList<>();
    public BrowserConfiguration browserConfig = new BrowserConfiguration();
    public List<TestCase> tests = new ArrayList<>();

    /** Project directory (not serialized). */
    @JsonIgnore
    public Path root;
}
