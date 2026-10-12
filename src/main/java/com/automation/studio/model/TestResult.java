package com.automation.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** Result of one executed test (written by StudioBaseTest, read by the reporter). Paths are relative to the execution dir. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResult {
    public String testName = "", className = "", environment = "", status = "", browser = "", url = "", title = "";
    public String exception = "", stackTrace = "", screenshot = "", video = "", trace = "", failedStep = "";
    public String startTime = "", endTime = "";
    public long durationMs;
    public List<StepResult> steps = new ArrayList<>();
    public List<String> logs = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepResult {
        public int number;
        public String description = "", status = "", error = "", screenshot = "";
        public long durationMs;
    }
}
