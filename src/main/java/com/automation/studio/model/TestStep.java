package com.automation.studio.model;

import com.automation.studio.locator.LocatorInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** One step. {@code action} is an ActionCatalog id; {@code params} are keyed by the action's ParamDef names. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestStep {
    public int stepNumber;
    public String action = "";
    public LocatorInfo locator;
    public Map<String, String> params = new LinkedHashMap<>();
    public String description = "";
    public boolean enabled = true;
    public boolean breakpoint;
    public boolean continueOnFailure;
    public Long timeoutMs;
    public String timestamp;
    public String screenshotPath;
    public String status = "Ready";

    public TestStep() {}
    public TestStep(String action) { this.action = action; }

    public String param(String key) {
        String v = params.get(key);
        return v == null ? "" : v;
    }

    public TestStep with(String key, String value) { params.put(key, value); return this; }

    public TestStep copy() {
        TestStep s = new TestStep(action);
        s.stepNumber = stepNumber;
        s.locator = locator == null ? null : locator.copy();
        s.params = new LinkedHashMap<>(params);
        s.description = description;
        s.enabled = enabled;
        s.breakpoint = breakpoint;
        s.continueOnFailure = continueOnFailure;
        s.timeoutMs = timeoutMs;
        s.timestamp = timestamp;
        s.screenshotPath = screenshotPath;
        s.status = status;
        return s;
    }
}
