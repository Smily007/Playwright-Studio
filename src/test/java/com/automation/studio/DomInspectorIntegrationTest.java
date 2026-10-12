package com.automation.studio;

import com.automation.studio.recorder.DomInspector;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

public class DomInspectorIntegrationTest {
    @Test
    public void inspectorReturnsBothXpathsAndHighlightsEnteredXPath() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser;
            try {
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            } catch (PlaywrightException e) {
                throw new SkipException("Chromium is not installed or could not start: " + e.getMessage());
            }
            try (BrowserContext context = browser.newContext()) {
                AtomicReference<String> xpath = new AtomicReference<>();
                AtomicReference<String> relativeXpath = new AtomicReference<>();
                context.exposeBinding("__studioInspect", (source, args) -> {
                    xpath.set(String.valueOf(args[0]));
                    relativeXpath.set(String.valueOf(args[1]));
                    return null;
                });
                Page page = context.newPage();
                page.setContent("<main><button id=\"save\">Save</button></main>");
                page.evaluate(DomInspector.script());
                page.evaluate("window.__studioSetInspectMode(true)");
                page.locator("#save").click();
                page.waitForTimeout(50);

                Assert.assertEquals(xpath.get(), "/html[1]/body[1]/main[1]/button[1]");
                Assert.assertEquals(relativeXpath.get(), "//*[@id='save']");

                Object matches = page.evaluate("expression => window.__studioHighlightXPath(expression)",
                        "//button[@id='save']");
                Assert.assertEquals(((Number) matches).intValue(), 1);
                Assert.assertFalse(page.locator("#save").evaluate("(el) => el.style.boxShadow").toString().isBlank());
            } finally {
                browser.close();
            }
        }
    }
}
