package com.automation.studio;

import com.automation.studio.locator.LocatorEngine;
import com.automation.studio.locator.LocatorInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class LocatorEngineTest {
    private final LocatorEngine engine = new LocatorEngine();

    @Test
    public void buttonWithNameBecomesRoleLocator() {
        LocatorInfo l = engine.build(Map.of("tag", "button", "role", "button", "name", "Login", "id", "loginBtn", "text", "Login"));
        Assert.assertEquals(l.locatorType, LocatorInfo.Type.ROLE);
        Assert.assertEquals(l.toJava(), "page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Login\"))");
    }

    @Test
    public void passwordFieldUsesLabel() {
        LocatorInfo l = engine.build(Map.of("tag", "input", "type", "password", "label", "Password", "css", "input"));
        Assert.assertEquals(l.toJava(), "page.getByLabel(\"Password\")");
    }

    @Test
    public void placeholderThenTestIdThenId() {
        Assert.assertEquals(engine.build(Map.of("tag", "input", "placeholder", "Search")).toJava(), "page.getByPlaceholder(\"Search\")");
        Assert.assertEquals(engine.build(Map.of("tag", "div", "testid", "card")).toJava(), "page.getByTestId(\"card\")");
        Assert.assertEquals(engine.build(Map.of("tag", "div", "id", "main-box", "css", "div")).toJava(), "page.locator(\"#main-box\")");
    }

    @Test
    public void generatedIdsAreRejectedInFavourOfCss() {
        LocatorInfo l = engine.build(Map.of("tag", "div", "id", "ember123456", "css", "div > span", "xpath", "/html[1]/body[1]"));
        Assert.assertEquals(l.locatorType, LocatorInfo.Type.CSS);
    }

    @Test
    public void xpathIsTheLastResort() {
        LocatorInfo l = engine.build(Map.of("tag", "div", "xpath", "/html[1]/body[1]/div[1]"));
        Assert.assertEquals(l.locatorType, LocatorInfo.Type.XPATH);
    }

    @Test
    public void customXPathLocatorIsRenderedAsExplicitPlaywrightXPath() {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = LocatorInfo.Type.XPATH;
        l.locatorValue = "//button[@data-testid='save']";

        Assert.assertEquals(l.toJava(), "page.locator(\"xpath=//button[@data-testid='save']\")");
    }
}
