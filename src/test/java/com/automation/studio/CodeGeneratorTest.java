package com.automation.studio;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.model.Project;
import com.automation.studio.model.TestCase;
import com.automation.studio.model.TestStep;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Golden-line tests for the code generator. */
public class CodeGeneratorTest {
    private final ActionCatalog catalog = new ActionCatalog();
    private final JavaTestGenerator gen = new JavaTestGenerator(catalog);

    private static LocatorInfo role(String role, String name) {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = LocatorInfo.Type.ROLE;
        l.role = role;
        l.locatorValue = name;
        return l;
    }

    private static LocatorInfo label(String v) {
        LocatorInfo l = new LocatorInfo();
        l.locatorType = LocatorInfo.Type.LABEL;
        l.locatorValue = v;
        return l;
    }

    private String code(TestStep... steps) {
        TestCase tc = new TestCase("Login test");
        for (TestStep s : steps) tc.steps.add(s);
        return gen.generate(tc, new Project());
    }

    private static TestStep step(String action, LocatorInfo l, String... kv) {
        TestStep s = new TestStep(action);
        s.locator = l;
        for (int i = 0; i < kv.length; i += 2) s.params.put(kv[i], kv[i + 1]);
        return s;
    }

    @Test
    public void classStructure() {
        String c = code(step("NAVIGATE", null, "url", "https://example.com"));
        Assert.assertTrue(c.contains("package tests;"));
        Assert.assertTrue(c.contains("public class LoginTest extends StudioBaseTest"));
        Assert.assertTrue(c.contains("public void loginTest(Map<String, String> excelRow)"));
        Assert.assertTrue(c.contains("page.navigate(TestData.resolveEnvironmentUrl(\"https://example.com\", System.getProperty(\"studio.sourceBaseUrl\", \"\"), System.getProperty(\"studio.baseUrl\", \"\")));"));
    }

    @Test
    public void interactionsAndOptions() {
        String c = code(step("CLICK", role("button", "Login")),
                step("FILL", label("Username"), "value", "admin"),
                step("CLICK", label("Menu"), "button", "RIGHT", "force", "true"),
                step("CHECK", label("Remember me")));
        Assert.assertTrue(c.contains("page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Login\")).click();"));
        Assert.assertTrue(c.contains("page.getByLabel(\"Username\").fill(\"admin\");"));
        Assert.assertTrue(c.contains(".click(new Locator.ClickOptions().setButton(MouseButton.RIGHT).setForce(true));"));
        Assert.assertTrue(c.contains("page.getByLabel(\"Remember me\").check();"));
    }

    @Test
    public void assertions() {
        TestStep url = step("ASSERT_PAGE_HAS_URL", null, "url", "https://example.com/dashboard");
        TestStep hidden = step("ASSERT_IS_VISIBLE", label("Spinner"), "negate", "true");
        String c = code(step("ASSERT_HAS_TEXT", role("heading", "Welcome"), "expected", "Welcome"), url, hidden);
        Assert.assertTrue(c.contains("assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(\"Welcome\"))).hasText(\"Welcome\");"));
        Assert.assertTrue(c.contains("assertThat(page).hasURL(\"https://example.com/dashboard\");"));
        Assert.assertTrue(c.contains("assertThat(page.getByLabel(\"Spinner\")).not().isVisible();"));
    }

    @Test
    public void secretsNeverAppearInSource() {
        String c = code(step("FILL", label("Password"), "value", "${secret:login_password_1}"));
        Assert.assertTrue(c.contains("TestData.resolve(\"${secret:login_password_1}\", vars)"));
    }

    @Test
    public void excelHeaderVariablesAreResolvedAtRuntime() {
        String c = code(step("FILL", label("Username"), "value", "${var:LoginName}"));
        Assert.assertTrue(c.contains("@Test(dataProvider = \"studioExcelRows\")"));
        Assert.assertTrue(c.contains("vars.putAll(excelRow);"));
        Assert.assertTrue(c.contains("TestData.resolve(\"${var:LoginName}\", vars)"));
    }

    @Test
    public void navigationUsesSelectedEnvironmentAndSupportsDataDrivenUrls() {
        String c = code(step("NAVIGATE", null, "url", "${var:PageUrl}"));
        Assert.assertTrue(c.contains("TestData.resolveEnvironmentUrl(TestData.resolve(\"${var:PageUrl}\", vars)"));
    }

    @Test
    public void disabledStepsAreCommentedOutAndSoftFailuresAreAsserted() {
        TestStep off = step("CLICK", label("X"));
        off.enabled = false;
        TestStep soft = step("CLICK", label("Y"));
        soft.continueOnFailure = true;
        String c = code(off, soft);
        Assert.assertTrue(c.contains("// [disabled] step 1"));
        Assert.assertTrue(c.contains("assertNoSoftFailures();"));
    }

    @Test
    public void breakpointIsEmittedAsAnEnabledDebuggerPause() {
        TestStep breakpoint = step("NAVIGATE", null, "url", "https://example.com");
        breakpoint.breakpoint = true;
        String generated = code(breakpoint);
        Assert.assertTrue(generated.contains(", false, true, () -> {"), generated);

        breakpoint.breakpoint = false;
        Assert.assertFalse(code(breakpoint).contains(", false, true, () -> {"));
    }

    @Test
    public void copiedStepsKeepBreakpointState() {
        TestStep original = step("CLICK", label("Save"));
        original.breakpoint = true;
        Assert.assertTrue(original.copy().breakpoint);
    }

    @Test
    public void storeValueIntoVariable() {
        String c = code(step("TEXT_CONTENT", label("Total"), "storeAs", "total"));
        Assert.assertTrue(c.contains("vars.put(\"total\", String.valueOf(page.getByLabel(\"Total\").textContent()));"));
    }
}
