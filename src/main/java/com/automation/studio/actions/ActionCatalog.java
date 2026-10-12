package com.automation.studio.actions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of every supported action. Custom actions are hand-written; the rest are discovered from the
 * Playwright Java API by reflection, so the catalog follows the Playwright version on the classpath.
 * To add an action: create an ActionDefinition (codeGen + executor) and call {@link #add}.
 */
public class ActionCatalog {
    private final Map<String, ActionDefinition> actions = new LinkedHashMap<>();

    public ActionCatalog() {
        CustomActions.registerAll(this);
        ReflectiveActions.registerAll(this);
    }

    public void add(ActionDefinition d) { actions.put(d.id, d); }

    public ActionDefinition get(String id) { return actions.get(id); }

    public Collection<ActionDefinition> all() { return actions.values(); }

    public List<String> categories() {
        return actions.values().stream().map(a -> a.category).distinct().sorted().collect(Collectors.toList());
    }

    public List<ActionDefinition> search(String text) {
        String t = text == null ? "" : text.trim().toLowerCase();
        return actions.values().stream()
                .filter(a -> t.isEmpty() || a.displayName.toLowerCase().contains(t) || a.id.toLowerCase().contains(t)
                        || a.category.toLowerCase().contains(t))
                .sorted(Comparator.comparing((ActionDefinition a) -> a.category).thenComparing(a -> a.displayName))
                .collect(Collectors.toList());
    }
}
