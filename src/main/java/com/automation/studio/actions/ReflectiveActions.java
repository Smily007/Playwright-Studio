package com.automation.studio.actions;

import com.automation.studio.model.TestStep;
import com.automation.studio.utilities.CodeUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Builds actions straight from the Playwright Java API: every public method of Locator, Page,
 * BrowserContext, Keyboard, Mouse, Clock, LocatorAssertions and PageAssertions whose parameters are
 * simple types (String, numbers, booleans, enums) plus an optional trailing *Options object. All option
 * setters with simple types become form fields, so the studio exposes the full option surface.
 */
public final class ReflectiveActions {
    enum Target { LOCATOR, PAGE, CONTEXT, KEYBOARD, MOUSE, CLOCK, ASSERT_LOCATOR, ASSERT_PAGE }

    private static final Set<String> RECORDABLE = Set.of("CLICK", "DBLCLICK", "FILL", "SELECT_OPTION", "CHECK",
            "UNCHECK", "PRESS", "HOVER", "NAVIGATE", "RELOAD");

    /** Page methods that take a selector string: superseded by Locator-based actions. */
    private static final Set<String> PAGE_SELECTOR_METHODS = Set.of("click", "dblclick", "tap", "hover", "focus", "fill",
            "type", "press", "check", "uncheck", "setChecked", "selectOption", "setInputFiles", "dispatchEvent",
            "dragAndDrop", "getAttribute", "innerHTML", "innerText", "textContent", "inputValue", "isChecked",
            "isDisabled", "isEditable", "isEnabled", "isHidden", "isVisible", "querySelector", "querySelectorAll",
            "locator", "waitForSelector", "evalOnSelector", "evalOnSelectorAll");

    private static final Set<String> SKIP_NAMES = Set.of("not", "setTestIdAttribute", "getByRole");

    private static final Map<String, String[]> NAMES = Map.ofEntries(
            Map.entry("navigate", new String[]{"url"}),
            Map.entry("fill", new String[]{"value"}),
            Map.entry("press", new String[]{"key"}),
            Map.entry("selectOption", new String[]{"value"}),
            Map.entry("type", new String[]{"text"}),
            Map.entry("pressSequentially", new String[]{"text"}),
            Map.entry("insertText", new String[]{"text"}),
            Map.entry("down", new String[]{"key"}),
            Map.entry("up", new String[]{"key"}),
            Map.entry("hasText", new String[]{"expected"}),
            Map.entry("containsText", new String[]{"expected"}),
            Map.entry("hasValue", new String[]{"value"}),
            Map.entry("hasAttribute", new String[]{"name", "value"}),
            Map.entry("hasCSS", new String[]{"name", "value"}),
            Map.entry("hasClass", new String[]{"expected"}),
            Map.entry("containsClass", new String[]{"expected"}),
            Map.entry("hasId", new String[]{"id"}),
            Map.entry("hasCount", new String[]{"count"}),
            Map.entry("hasAccessibleName", new String[]{"name"}),
            Map.entry("hasAccessibleDescription", new String[]{"description"}),
            Map.entry("hasURL", new String[]{"url"}),
            Map.entry("hasTitle", new String[]{"title"}),
            Map.entry("matchesAriaSnapshot", new String[]{"expected"}),
            Map.entry("getAttribute", new String[]{"name"}),
            Map.entry("waitForTimeout", new String[]{"ms"}),
            Map.entry("waitForURL", new String[]{"url"}),
            Map.entry("waitForLoadState", new String[]{"state"}),
            Map.entry("waitForFunction", new String[]{"expression"}),
            Map.entry("setViewportSize", new String[]{"width", "height"}),
            Map.entry("evaluate", new String[]{"expression"}),
            Map.entry("setContent", new String[]{"html"}),
            Map.entry("setChecked", new String[]{"checked"}),
            Map.entry("dispatchEvent", new String[]{"type"}),
            Map.entry("setOffline", new String[]{"offline"}),
            Map.entry("setDefaultTimeout", new String[]{"timeout"}),
            Map.entry("setDefaultNavigationTimeout", new String[]{"timeout"}),
            Map.entry("move", new String[]{"x", "y"}),
            Map.entry("click", new String[]{"x", "y"}),
            Map.entry("dblclick", new String[]{"x", "y"}),
            Map.entry("wheel", new String[]{"deltaX", "deltaY"}),
            Map.entry("fastForward", new String[]{"ticks"}),
            Map.entry("runFor", new String[]{"ticks"}),
            Map.entry("setFixedTime", new String[]{"time"}),
            Map.entry("setSystemTime", new String[]{"time"}));

    private ReflectiveActions() {}

    public static void registerAll(ActionCatalog cat) {
        scan(cat, Target.LOCATOR, Locator.class);
        scan(cat, Target.ASSERT_LOCATOR, LocatorAssertions.class);
        scan(cat, Target.ASSERT_PAGE, PageAssertions.class);
        scan(cat, Target.PAGE, Page.class);
        scan(cat, Target.CONTEXT, BrowserContext.class);
        scan(cat, Target.KEYBOARD, Keyboard.class);
        scan(cat, Target.MOUSE, Mouse.class);
        scan(cat, Target.CLOCK, Clock.class);
    }

    // ------------------------------------------------------------------ discovery

    private static void scan(ActionCatalog cat, Target t, Class<?> owner) {
        Map<String, Method> best = new TreeMap<>();
        Map<String, Integer> scores = new HashMap<>();
        for (Method m : owner.getMethods()) {
            if (!allowed(t, m)) continue;
            Class<?>[] pt = m.getParameterTypes();
            int n = pt.length;
            Class<?> opts = null;
            if (n > 0 && isOptions(pt[n - 1], owner)) { opts = pt[n - 1]; n--; }
            boolean ok = true;
            for (int i = 0; i < n; i++) if (!ParamDef.supported(pt[i])) ok = false;
            if (!ok) continue;
            int score = n * 2 + (opts != null ? 1 : 0);
            Integer prev = scores.get(m.getName());
            if (prev == null || score > prev) { scores.put(m.getName(), score); best.put(m.getName(), m); }
        }
        for (Method m : best.values()) register(cat, t, owner, m);
    }

    private static boolean allowed(Target t, Method m) {
        if (Modifier.isStatic(m.getModifiers())) return false;
        String n = m.getName();
        if (SKIP_NAMES.contains(n) || n.startsWith("on") && n.length() > 2 && Character.isUpperCase(n.charAt(2))) return false;
        if (t == Target.PAGE && PAGE_SELECTOR_METHODS.contains(n)) return false;
        return okReturn(m.getReturnType());
    }

    private static boolean okReturn(Class<?> r) {
        return r == void.class || r.isPrimitive() || r == String.class || r == Object.class || r == List.class
                || r == byte[].class || r == Boolean.class || r == Integer.class || r == Double.class || r == Long.class
                || r == Response.class;
    }

    private static boolean storeable(Class<?> r) {
        return r != void.class && r != byte[].class && r != Response.class;
    }

    private static boolean isOptions(Class<?> c, Class<?> owner) {
        return c.getSimpleName().endsWith("Options") && c.getEnclosingClass() == owner;
    }

    private static List<Method> setters(Class<?> opts) {
        Map<String, Method> out = new TreeMap<>();
        for (Method s : opts.getMethods()) {
            if (!s.getName().startsWith("set") || s.getParameterCount() != 1) continue;
            Class<?> p = s.getParameterTypes()[0];
            if (!ParamDef.supported(p)) continue;
            Method existing = out.get(s.getName());
            if (existing == null || p == String.class) out.put(s.getName(), s);
        }
        return new ArrayList<>(out.values());
    }

    private static void register(ActionCatalog cat, Target t, Class<?> owner, Method m) {
        Class<?>[] pt = m.getParameterTypes();
        int n = pt.length;
        Class<?> opts = null;
        if (n > 0 && isOptions(pt[n - 1], owner)) { opts = pt[n - 1]; n--; }
        String[] names = NAMES.get(m.getName());
        ActionDefinition d = new ActionDefinition(makeId(cat, t, m.getName()), category(t, m.getName()),
                display(t, m.getName()), t == Target.LOCATOR || t == Target.ASSERT_LOCATOR);
        for (int i = 0; i < n; i++) {
            String pn = names != null && i < names.length ? names[i]
                    : pt[i].isEnum() ? CodeUtil.decap(pt[i].getSimpleName()) : "arg" + (i + 1);
            d.params.add(new ParamDef(pn, pt[i], false, false, null));
        }
        if (opts != null)
            for (Method s : setters(opts))
                d.params.add(new ParamDef(CodeUtil.decap(s.getName().substring(3)), s.getParameterTypes()[0], true, false, s));
        if (t == Target.ASSERT_LOCATOR || t == Target.ASSERT_PAGE)
            d.params.add(ParamDef.meta("negate", ParamDef.Kind.BOOLEAN));
        if (storeable(m.getReturnType()))
            d.params.add(ParamDef.meta("storeAs", ParamDef.Kind.STRING));
        final Class<?> o = opts;
        d.codeGen = (s, def) -> generate(t, m, o, s, def);
        d.executor = (c, s) -> invoke(t, m, o, c, s, d);
        d.recordable = RECORDABLE.contains(d.id);
        cat.add(d);
    }

    private static String makeId(ActionCatalog cat, Target t, String method) {
        String base = CodeUtil.snake(method);
        String id = switch (t) {
            case ASSERT_LOCATOR -> "ASSERT_" + base;
            case ASSERT_PAGE -> "ASSERT_PAGE_" + base;
            case KEYBOARD -> "KEYBOARD_" + base;
            case MOUSE -> "MOUSE_" + base;
            case CLOCK -> "CLOCK_" + base;
            case CONTEXT -> "CONTEXT_" + base;
            default -> base;
        };
        return cat.get(id) != null ? t.name() + "_" + id : id;
    }

    private static String display(Target t, String method) {
        String words = String.join(" ", method.split("(?<=[a-z0-9])(?=[A-Z])"));
        words = words.equals("dblclick") ? "double click" : words;
        String prefix = switch (t) {
            case ASSERT_LOCATOR -> "Assert ";
            case ASSERT_PAGE -> "Assert page ";
            case KEYBOARD -> "Keyboard ";
            case MOUSE -> "Mouse ";
            case CLOCK -> "Clock ";
            case CONTEXT -> "Context ";
            default -> "";
        };
        String s = prefix + words;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String category(Target t, String n) {
        switch (t) {
            case ASSERT_LOCATOR: case ASSERT_PAGE: return "Assertions";
            case KEYBOARD: case MOUSE: return "Interaction";
            case CONTEXT: return "Context";
            case CLOCK: return "Emulation";
            default: break;
        }
        if (n.startsWith("waitFor")) return "Waits";
        if (n.matches("is[A-Z].*|textContent|innerText|innerHTML|inputValue|getAttribute|allTextContents|allInnerTexts|boundingBox|count|url|title|content|ariaSnapshot|all")) return "Data Extraction";
        if (n.matches("navigate|goBack|goForward|reload|setContent|bringToFront|close|setDefault.*")) return "Navigation";
        if (n.matches("fill|clear|type|press.*|selectOption|selectText|check|uncheck|setChecked")) return "Input";
        if (n.matches("click|dblclick|tap|hover|focus|blur|dragTo|scrollIntoViewIfNeeded|dispatchEvent|highlight")) return "Interaction";
        if (n.matches("evaluate.*|pause|addInitScript|.*Snapshot")) return "Debug";
        if (n.matches("screenshot|pdf|setInputFiles")) return "Files";
        if (n.matches("emulateMedia|setViewportSize|setExtraHTTPHeaders")) return "Emulation";
        return "Advanced";
    }

    // ------------------------------------------------------------------ code generation

    private static Map<String, String> effective(TestStep s, ActionDefinition d) {
        Map<String, String> p = new LinkedHashMap<>(s.params);
        ParamDef timeout = d.param("timeout");
        if (s.timeoutMs != null && timeout != null && timeout.option && (p.get("timeout") == null || p.get("timeout").isBlank()))
            p.put("timeout", String.valueOf(s.timeoutMs));
        return p;
    }

    static String generate(Target t, Method m, Class<?> optsType, TestStep s, ActionDefinition d) {
        Map<String, String> p = effective(s, d);
        List<String> args = new ArrayList<>();
        for (ParamDef pd : d.params)
            if (!pd.option && !pd.meta) args.add(pd.javaLiteral(p.getOrDefault(pd.name, "")));
        if (t == Target.PAGE && m.getName().equals("navigate") && !args.isEmpty())
            args.set(0, "TestData.resolveEnvironmentUrl(" + args.get(0)
                    + ", System.getProperty(\"studio.sourceBaseUrl\", \"\")"
                    + ", System.getProperty(\"studio.baseUrl\", \"\"))");
        if (optsType != null) {
            StringBuilder o = new StringBuilder();
            for (ParamDef pd : d.params) {
                String v = p.get(pd.name);
                if (pd.option && v != null && !v.isBlank())
                    o.append('.').append(pd.setter.getName()).append('(').append(pd.javaLiteral(v)).append(')');
            }
            if (o.length() > 0)
                args.add("new " + optsType.getEnclosingClass().getSimpleName() + "." + optsType.getSimpleName() + "()" + o);
        }
        String call = m.getName() + "(" + String.join(", ", args) + ")";
        String loc = s.locator == null ? "page.locator(\"body\")" : s.locator.toJava();
        boolean negate = Boolean.parseBoolean(p.getOrDefault("negate", "false"));
        String not = negate ? ".not()" : "";
        String expr = switch (t) {
            case LOCATOR -> loc + "." + call;
            case PAGE -> "page." + call;
            case CONTEXT -> "context." + call;
            case KEYBOARD -> "page.keyboard()." + call;
            case MOUSE -> "page.mouse()." + call;
            case CLOCK -> "page.clock()." + call;
            case ASSERT_LOCATOR -> "assertThat(" + loc + ")" + not + "." + call;
            case ASSERT_PAGE -> "assertThat(page)" + not + "." + call;
        };
        String store = p.get("storeAs");
        if (store != null && !store.isBlank() && storeable(m.getReturnType()))
            return "vars.put(" + CodeUtil.literal(store) + ", String.valueOf(" + expr + "));";
        return expr + ";";
    }

    // ------------------------------------------------------------------ live execution

    static void invoke(Target t, Method m, Class<?> optsType, ExecutionContext c, TestStep s, ActionDefinition d) throws Exception {
        Map<String, String> p = effective(s, d);
        List<Object> args = new ArrayList<>();
        for (ParamDef pd : d.params)
            if (!pd.option && !pd.meta) args.add(pd.toRuntime(p.getOrDefault(pd.name, ""), c.vars));
        if (optsType != null) {
            Object o = optsType.getDeclaredConstructor().newInstance();
            for (ParamDef pd : d.params) {
                String v = p.get(pd.name);
                if (pd.option && v != null && !v.isBlank()) pd.setter.invoke(o, pd.toRuntime(v, c.vars));
            }
            args.add(o);
        }
        boolean negate = Boolean.parseBoolean(p.getOrDefault("negate", "false"));
        Object target;
        switch (t) {
            case LOCATOR: target = s.locator.resolve(c.page, c.vars); break;
            case PAGE: target = c.page; break;
            case CONTEXT: target = c.context; break;
            case KEYBOARD: target = c.page.keyboard(); break;
            case MOUSE: target = c.page.mouse(); break;
            case CLOCK: target = c.page.clock(); break;
            case ASSERT_LOCATOR: {
                LocatorAssertions a = PlaywrightAssertions.assertThat(s.locator.resolve(c.page, c.vars));
                target = negate ? a.not() : a;
                break;
            }
            default: {
                PageAssertions a = PlaywrightAssertions.assertThat(c.page);
                target = negate ? a.not() : a;
            }
        }
        Object result;
        try {
            result = m.invoke(target, args.toArray());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            if (cause instanceof Error er) throw er;
            throw e;
        }
        String store = p.get("storeAs");
        if (store != null && !store.isBlank()) c.vars.put(store, String.valueOf(result));
    }
}
