package com.automation.studio.actions;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live Playwright objects + test variables. Must only be used on the Playwright worker thread. */
public class ExecutionContext {
    public Playwright playwright;
    public Browser browser;
    public BrowserContext context;
    public Page page;
    public final Map<String, String> vars = new LinkedHashMap<>();
}
