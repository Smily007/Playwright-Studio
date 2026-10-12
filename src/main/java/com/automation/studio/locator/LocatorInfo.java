package com.automation.studio.locator;

import com.automation.studio.runtime.TestData;
import com.automation.studio.utilities.CodeUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.LinkedHashMap;
import java.util.Map;

/** Describes how to find an element; renders to Playwright Java code and resolves to a live Locator. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocatorInfo {
    public enum Type { ROLE, LABEL, PLACEHOLDER, TESTID, TEXT, ALT_TEXT, TITLE, ID, CSS, XPATH }

    public Type locatorType = Type.CSS;
    public String locatorValue = "";
    /** ARIA role name for ROLE locators (e.g. "button"). */
    public String role = "";
    public boolean exact;
    public double confidence;
    public String elementTag = "";
    public String elementText = "";
    public Map<String, String> attributes = new LinkedHashMap<>();
    /** null = no index, 0 = first(), -1 = last(), n = nth(n). */
    public Integer nth;
    /** CSS selector of an enclosing iframe, empty if the element is in the main frame. */
    public String frameSelector = "";

    public static LocatorInfo css(String css) {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = Type.CSS;
        l.locatorValue = css;
        return l;
    }

    public static LocatorInfo xpath(String xpath) {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = Type.XPATH;
        l.locatorValue = xpath;
        return l;
    }

    public LocatorInfo role(String r) { this.role = r; return this; }

    public LocatorInfo copy() {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = locatorType; l.locatorValue = locatorValue; l.role = role; l.exact = exact;
        l.confidence = confidence; l.elementTag = elementTag; l.elementText = elementText;
        l.attributes = new LinkedHashMap<>(attributes); l.nth = nth; l.frameSelector = frameSelector;
        return l;
    }

    public static boolean validRole(String r) {
        if (r == null || r.isBlank()) return false;
        try { AriaRole.valueOf(r.toUpperCase().replace('-', '_')); return true; } catch (IllegalArgumentException e) { return false; }
    }

    private AriaRole ariaRole() { return AriaRole.valueOf(roleEnum()); }
    private String roleEnum() { return role.toUpperCase().replace('-', '_'); }
    private boolean inFrame() { return frameSelector != null && !frameSelector.isBlank(); }

    @JsonIgnore
    public String summary() {
        String s = locatorType + ": " + locatorValue;
        if (locatorType == Type.ROLE) s = "ROLE " + role + (locatorValue.isBlank() ? "" : " \"" + locatorValue + "\"");
        if (inFrame()) s = "[" + frameSelector + "] " + s;
        if (nth != null) s += " #" + nth;
        return s;
    }

    /** Java source expression, e.g. {@code page.getByLabel("Username")}. */
    public String toJava() {
        String b = inFrame() ? "page.frameLocator(" + CodeUtil.literal(frameSelector) + ")" : "page";
        String o = inFrame() ? "FrameLocator" : "Page";
        String v = javaValue(locatorValue);
        String s;
        switch (locatorType) {
            case ROLE:
                s = b + ".getByRole(AriaRole." + roleEnum()
                        + (locatorValue.isBlank() ? "" : ", new " + o + ".GetByRoleOptions().setName(" + v + ")" + (exact ? ".setExact(true)" : "")) + ")";
                break;
            case LABEL: s = b + ".getByLabel(" + v + exactOpt(o, "GetByLabelOptions") + ")"; break;
            case PLACEHOLDER: s = b + ".getByPlaceholder(" + v + exactOpt(o, "GetByPlaceholderOptions") + ")"; break;
            case TEXT: s = b + ".getByText(" + v + exactOpt(o, "GetByTextOptions") + ")"; break;
            case TESTID: s = b + ".getByTestId(" + v + ")"; break;
            case ALT_TEXT: s = b + ".getByAltText(" + v + ")"; break;
            case TITLE: s = b + ".getByTitle(" + v + ")"; break;
            case ID: s = b + ".locator(" + javaValue(idSelector(locatorValue)) + ")"; break;
            case XPATH: s = b + ".locator(" + javaValue("xpath=" + locatorValue) + ")"; break;
            default: s = b + ".locator(" + v + ")";
        }
        if (nth != null) s += nth == 0 ? ".first()" : nth == -1 ? ".last()" : ".nth(" + nth + ")";
        return s;
    }

    private String exactOpt(String owner, String optClass) {
        return exact ? ", new " + owner + "." + optClass + "().setExact(true)" : "";
    }

    private static String javaValue(String value) {
        return CodeUtil.expr(value);
    }

    private static String idSelector(String id) {
        return id.matches("[A-Za-z][\\w-]*") ? "#" + id : "[id=\"" + id + "\"]";
    }

    /** Resolves to a live Playwright Locator. */
    public Locator resolve(Page page, Map<String, String> vars) {
        String v = TestData.resolve(locatorValue, vars);
        Locator loc = inFrame() ? onFrame(page.frameLocator(TestData.resolve(frameSelector, vars)), v) : onPage(page, v);
        if (nth != null) loc = nth == 0 ? loc.first() : nth == -1 ? loc.last() : loc.nth(nth);
        return loc;
    }

    private Locator onPage(Page p, String v) {
        switch (locatorType) {
            case ROLE:
                return v.isBlank() ? p.getByRole(ariaRole())
                        : p.getByRole(ariaRole(), new Page.GetByRoleOptions().setName(v).setExact(exact));
            case LABEL: return p.getByLabel(v, new Page.GetByLabelOptions().setExact(exact));
            case PLACEHOLDER: return p.getByPlaceholder(v, new Page.GetByPlaceholderOptions().setExact(exact));
            case TEXT: return p.getByText(v, new Page.GetByTextOptions().setExact(exact));
            case TESTID: return p.getByTestId(v);
            case ALT_TEXT: return p.getByAltText(v);
            case TITLE: return p.getByTitle(v);
            case ID: return p.locator(idSelector(v));
            case XPATH: return p.locator("xpath=" + v);
            default: return p.locator(v);
        }
    }

    private Locator onFrame(FrameLocator f, String v) {
        switch (locatorType) {
            case ROLE:
                return v.isBlank() ? f.getByRole(ariaRole())
                        : f.getByRole(ariaRole(), new FrameLocator.GetByRoleOptions().setName(v).setExact(exact));
            case LABEL: return f.getByLabel(v, new FrameLocator.GetByLabelOptions().setExact(exact));
            case PLACEHOLDER: return f.getByPlaceholder(v, new FrameLocator.GetByPlaceholderOptions().setExact(exact));
            case TEXT: return f.getByText(v, new FrameLocator.GetByTextOptions().setExact(exact));
            case TESTID: return f.getByTestId(v);
            case ALT_TEXT: return f.getByAltText(v);
            case TITLE: return f.getByTitle(v);
            case ID: return f.locator(idSelector(v));
            case XPATH: return f.locator("xpath=" + v);
            default: return f.locator(v);
        }
    }
}
