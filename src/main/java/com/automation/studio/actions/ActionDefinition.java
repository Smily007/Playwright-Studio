package com.automation.studio.actions;

import com.automation.studio.model.TestStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for one action: metadata for the UI (category, parameters), the Java code
 * generator and the live executor all hang off this object.
 */
public class ActionDefinition {
    @FunctionalInterface
    public interface CodeGen {
        String generate(TestStep step, ActionDefinition def);
    }

    public final String id;
    public final String category;
    public final String displayName;
    public final boolean needsLocator;
    public final List<ParamDef> params = new ArrayList<>();
    public boolean recordable;
    public CodeGen codeGen;
    public ActionExecutor executor;

    public ActionDefinition(String id, String category, String displayName, boolean needsLocator) {
        this.id = id;
        this.category = category;
        this.displayName = displayName;
        this.needsLocator = needsLocator;
    }

    public String generate(TestStep step) { return codeGen.generate(step, this); }

    public void execute(ExecutionContext ctx, TestStep step) throws Exception { executor.execute(ctx, step); }

    public ParamDef param(String name) {
        for (ParamDef p : params) if (p.name.equals(name)) return p;
        return null;
    }

    @Override public String toString() { return "[" + category + "] " + displayName; }
}
