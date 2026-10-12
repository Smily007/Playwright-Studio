package com.automation.studio.actions;

import com.automation.studio.model.TestStep;

import java.util.ArrayList;
import java.util.List;

/** Human readable text for steps (table, logs, generated step descriptions). Secrets are masked. */
public final class StepDescriber {
    private StepDescriber() {}

    public static String mask(String value) {
        return value != null && value.contains("${secret:") ? "******" : value;
    }

    /** Values of all non-blank params, masked. */
    public static String values(TestStep s) {
        List<String> parts = new ArrayList<>();
        boolean single = s.params.values().stream().filter(v -> v != null && !v.isBlank()).count() == 1;
        for (var e : s.params.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            parts.add(single ? mask(e.getValue()) : e.getKey() + "=" + mask(e.getValue()));
        }
        return String.join(", ", parts);
    }

    public static String describe(TestStep s, ActionDefinition d) {
        if (s.description != null && !s.description.isBlank()) return s.description;
        String name = d == null ? s.action : d.displayName;
        String loc = s.locator == null ? "" : " " + s.locator.summary();
        String v = values(s);
        return name + loc + (v.isEmpty() ? "" : " (" + v + ")");
    }
}
