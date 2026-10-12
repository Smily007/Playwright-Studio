package com.automation.studio.execution;

import com.automation.studio.browser.BrowserConfiguration;

import java.nio.file.Path;
import java.util.Map;

/** What to run: generated sources by class name, plus an optional suite XML. */
public class ExecutionRequest {
    public Path projectDir;
    public Path reportDir;
    public Map<String, String> sources;
    public Map<String, String> pageObjectSources;
    public String suiteXml;
    public BrowserConfiguration config;
    public Path excelDataFile;
    public String excelSheetName;
    public String environmentName;
    public String environmentUrl;
    public String sourceBaseUrl;
    public boolean debugMode;
    public DebuggerController debugger;
}
