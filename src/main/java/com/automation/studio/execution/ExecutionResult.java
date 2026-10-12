package com.automation.studio.execution;

import com.automation.studio.model.TestResult;

import java.nio.file.Path;
import java.util.List;

public class ExecutionResult {
    public int exitCode;
    public long durationMs;
    public Path reportDir;
    public Path indexHtml;
    public List<TestResult> results;

    public boolean allPassed() {
        return results != null && !results.isEmpty() && results.stream().allMatch(r -> "PASSED".equals(r.status));
    }
}
