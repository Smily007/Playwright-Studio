package com.automation.studio.locator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the most reliable locator for a DOM element description produced by the recorder script.
 * Priority: role, label, placeholder, alt, test id, text, title, stable id, CSS, XPath.
 */
public class LocatorEngine {
    private final List<LocatorStrategy> strategies = List.of(
            dom -> {
                String name = s(dom, "name");
                if (LocatorInfo.validRole(s(dom, "role")) && !name.isBlank() && name.length() <= 80)
                    return Optional.of(make(LocatorInfo.Type.ROLE, name, 0.95, dom).role(s(dom, "role")));
                return Optional.empty();
            },
            dom -> opt(LocatorInfo.Type.LABEL, s(dom, "label"), 0.90, dom),
            dom -> opt(LocatorInfo.Type.PLACEHOLDER, s(dom, "placeholder"), 0.85, dom),
            dom -> opt(LocatorInfo.Type.ALT_TEXT, s(dom, "alt"), 0.80, dom),
            dom -> opt(LocatorInfo.Type.TESTID, s(dom, "testid"), 0.98, dom),
            dom -> {
                String tag = s(dom, "tag");
                String t = s(dom, "text");
                if (!t.isBlank() && t.length() <= 60 && !List.of("input", "textarea", "select").contains(tag))
                    return Optional.of(make(LocatorInfo.Type.TEXT, t, 0.60, dom));
                return Optional.empty();
            },
            dom -> opt(LocatorInfo.Type.TITLE, s(dom, "title"), 0.55, dom),
            dom -> {
                String id = s(dom, "id");
                boolean stable = !id.isBlank() && !id.matches(".*\\d{4,}.*") && !id.matches("(?i)[a-f0-9-]{16,}");
                return stable ? Optional.of(make(LocatorInfo.Type.ID, id, 0.55, dom)) : Optional.empty();
            },
            dom -> opt(LocatorInfo.Type.CSS, s(dom, "css"), 0.40, dom),
            dom -> opt(LocatorInfo.Type.XPATH, s(dom, "xpath"), 0.20, dom));

    public LocatorInfo build(Map<String, String> dom) {
        for (LocatorStrategy st : strategies) {
            Optional<LocatorInfo> r = st.tryBuild(dom);
            if (r.isPresent()) return r.get();
        }
        return LocatorInfo.css("body");
    }

    private static Optional<LocatorInfo> opt(LocatorInfo.Type t, String value, double conf, Map<String, String> dom) {
        return value.isBlank() ? Optional.empty() : Optional.of(make(t, value, conf, dom));
    }

    private static LocatorInfo make(LocatorInfo.Type t, String value, double conf, Map<String, String> dom) {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = t;
        l.locatorValue = value;
        l.confidence = conf;
        l.elementTag = s(dom, "tag");
        l.elementText = s(dom, "text");
        Map<String, String> attrs = new LinkedHashMap<>();
        for (String k : List.of("id", "type", "placeholder", "testid", "title", "alt"))
            if (!s(dom, k).isBlank()) attrs.put(k, s(dom, k));
        l.attributes = attrs;
        return l;
    }

    private static String s(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v;
    }
}
