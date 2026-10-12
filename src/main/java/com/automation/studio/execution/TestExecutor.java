package com.automation.studio.execution;

import java.util.function.Consumer;

public interface TestExecutor {
    ExecutionResult execute(ExecutionRequest request, Consumer<String> out) throws Exception;
}
