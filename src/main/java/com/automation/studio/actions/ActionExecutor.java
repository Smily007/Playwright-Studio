package com.automation.studio.actions;

import com.automation.studio.model.TestStep;

/** Runs one step against a live browser (used by "Run step" in the studio). */
@FunctionalInterface
public interface ActionExecutor {
    void execute(ExecutionContext ctx, TestStep step) throws Exception;
}
