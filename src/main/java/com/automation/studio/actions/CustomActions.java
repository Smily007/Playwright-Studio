package com.automation.studio.actions;

import com.automation.studio.model.TestStep;
import com.automation.studio.runtime.TestData;
import com.automation.studio.utilities.CodeUtil;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.Geolocation;
import com.microsoft.playwright.options.RequestOptions;
import org.testng.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Hand-written actions that need more than a single API call (routing, dialogs, tabs, downloads, API calls...). */
final class CustomActions {
    private CustomActions() {}

    private static void reg(ActionCatalog c, String id, String cat, String name, boolean loc, ParamDef[] params,
                            ActionDefinition.CodeGen gen, ActionExecutor exec) {
        ActionDefinition d = new ActionDefinition(id, cat, name, loc);
        d.params.addAll(Arrays.asList(params));
        d.codeGen = gen;
        d.executor = exec;
        d.recordable = id.equals("SET_INPUT_FILES");
        c.add(d);
    }

    private static ParamDef[] P(ParamDef... p) { return p; }

    private static String loc(TestStep s) { return s.locator == null ? "page.locator(\"body\")" : s.locator.toJava(); }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    static void registerAll(ActionCatalog c) {
        reg(c, "SET_INPUT_FILES", "Files", "Upload file(s)", true, P(ParamDef.str("path")),
                (s, d) -> {
                    List<String> paths = split(s.param("path"));
                    if (paths.size() == 1) return loc(s) + ".setInputFiles(Paths.get(" + CodeUtil.expr(paths.get(0)) + "));";
                    return loc(s) + ".setInputFiles(new Path[]{" + paths.stream().map(p -> "Paths.get(" + CodeUtil.expr(p) + ")")
                            .collect(Collectors.joining(", ")) + "});";
                },
                (x, s) -> s.locator.resolve(x.page, x.vars).setInputFiles(split(s.param("path")).stream()
                        .map(p -> Paths.get(TestData.resolve(p, x.vars))).toArray(Path[]::new)));

        reg(c, "DRAG_TO", "Interaction", "Drag to element", true, P(ParamDef.str("targetSelector")),
                (s, d) -> loc(s) + ".dragTo(page.locator(" + CodeUtil.expr(s.param("targetSelector")) + "));",
                (x, s) -> s.locator.resolve(x.page, x.vars).dragTo(x.page.locator(TestData.resolve(s.param("targetSelector"), x.vars))));

        reg(c, "DIALOG_ACCEPT", "Dialogs", "Accept next dialog", false, P(ParamDef.str("promptText")),
                (s, d) -> "page.onceDialog(dialog -> dialog.accept(" + (s.param("promptText").isBlank() ? "" : CodeUtil.expr(s.param("promptText"))) + "));",
                (x, s) -> x.page.onceDialog(dlg -> {
                    if (s.param("promptText").isBlank()) dlg.accept(); else dlg.accept(TestData.resolve(s.param("promptText"), x.vars));
                }));

        reg(c, "DIALOG_DISMISS", "Dialogs", "Dismiss next dialog", false, P(),
                (s, d) -> "page.onceDialog(dialog -> dialog.dismiss());",
                (x, s) -> x.page.onceDialog(dlg -> dlg.dismiss()));

        reg(c, "MOCK_ROUTE", "Network", "Mock response for URL", false,
                P(ParamDef.str("urlPattern"), ParamDef.integer("status"), ParamDef.str("body"), ParamDef.str("contentType")),
                (s, d) -> "page.route(" + CodeUtil.expr(s.param("urlPattern")) + ", route -> route.fulfill(new Route.FulfillOptions()"
                        + ".setStatus(" + (s.param("status").isBlank() ? "200" : s.param("status").trim()) + ")"
                        + ".setContentType(" + CodeUtil.literal(s.param("contentType").isBlank() ? "application/json" : s.param("contentType")) + ")"
                        + ".setBody(" + CodeUtil.expr(s.param("body")) + ")));",
                (x, s) -> x.page.route(TestData.resolve(s.param("urlPattern"), x.vars), route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(s.param("status").isBlank() ? 200 : Integer.parseInt(s.param("status").trim()))
                        .setContentType(s.param("contentType").isBlank() ? "application/json" : s.param("contentType"))
                        .setBody(TestData.resolve(s.param("body"), x.vars)))));

        reg(c, "ABORT_ROUTE", "Network", "Block requests for URL", false, P(ParamDef.str("urlPattern")),
                (s, d) -> "page.route(" + CodeUtil.expr(s.param("urlPattern")) + ", route -> route.abort());",
                (x, s) -> x.page.route(TestData.resolve(s.param("urlPattern"), x.vars), route -> route.abort()));

        reg(c, "UNROUTE_ALL", "Network", "Remove all routes", false, P(),
                (s, d) -> "page.unrouteAll();",
                (x, s) -> x.page.unrouteAll());

        reg(c, "SET_GEOLOCATION", "Context", "Set geolocation", false, P(ParamDef.number("latitude"), ParamDef.number("longitude")),
                (s, d) -> "context.grantPermissions(Arrays.asList(\"geolocation\"));\ncontext.setGeolocation(new Geolocation("
                        + Double.parseDouble(num(s.param("latitude"))) + ", " + Double.parseDouble(num(s.param("longitude"))) + "));",
                (x, s) -> {
                    x.context.grantPermissions(Arrays.asList("geolocation"));
                    x.context.setGeolocation(new Geolocation(Double.parseDouble(num(s.param("latitude"))), Double.parseDouble(num(s.param("longitude")))));
                });

        reg(c, "GRANT_PERMISSIONS", "Context", "Grant permissions (comma separated)", false, P(ParamDef.str("permissions")),
                (s, d) -> "context.grantPermissions(Arrays.asList(" + split(s.param("permissions")).stream()
                        .map(CodeUtil::literal).collect(Collectors.joining(", ")) + "));",
                (x, s) -> x.context.grantPermissions(split(s.param("permissions"))));

        reg(c, "ADD_COOKIE", "Context", "Add cookie", false, P(ParamDef.str("name"), ParamDef.str("value"), ParamDef.str("url")),
                (s, d) -> "context.addCookies(Arrays.asList(new Cookie(" + CodeUtil.expr(s.param("name")) + ", " + CodeUtil.expr(s.param("value"))
                        + ").setUrl(" + CodeUtil.expr(s.param("url")) + ")));",
                (x, s) -> x.context.addCookies(Arrays.asList(new Cookie(TestData.resolve(s.param("name"), x.vars), TestData.resolve(s.param("value"), x.vars))
                        .setUrl(TestData.resolve(s.param("url"), x.vars)))));

        reg(c, "SWITCH_TAB", "Context", "Switch to tab by index", false, P(ParamDef.integer("index")),
                (s, d) -> "page = context.pages().get(" + (int) Double.parseDouble(num(s.param("index"))) + ");\npage.bringToFront();",
                (x, s) -> {
                    x.page = x.context.pages().get((int) Double.parseDouble(num(s.param("index"))));
                    x.page.bringToFront();
                });

        reg(c, "CLICK_OPEN_POPUP", "Context", "Click and switch to new tab/popup", true, P(),
                (s, d) -> "page = page.waitForPopup(() -> " + loc(s) + ".click());",
                (x, s) -> x.page = x.page.waitForPopup(() -> s.locator.resolve(x.page, x.vars).click()));

        reg(c, "DOWNLOAD_FILE", "Files", "Click and save download", true, P(ParamDef.str("fileName")),
                (s, d) -> "Download download = page.waitForDownload(() -> " + loc(s) + ".click());\n"
                        + "download.saveAs(Paths.get(\"downloads\", " + CodeUtil.expr(s.param("fileName").isBlank() ? "download.bin" : s.param("fileName")) + "));",
                (x, s) -> {
                    Download dl = x.page.waitForDownload(() -> s.locator.resolve(x.page, x.vars).click());
                    dl.saveAs(Paths.get("downloads", TestData.resolve(s.param("fileName").isBlank() ? "download.bin" : s.param("fileName"), x.vars)));
                });

        reg(c, "API_REQUEST", "API", "API request (assert status)", false,
                P(ParamDef.str("method"), ParamDef.str("url"), ParamDef.str("body"), ParamDef.integer("expectedStatus"), ParamDef.meta("storeAs", ParamDef.Kind.STRING)),
                (s, d) -> {
                    StringBuilder b = new StringBuilder();
                    String method = s.param("method").isBlank() ? "GET" : s.param("method").toUpperCase();
                    b.append("APIResponse apiResponse = playwright.request().newContext().fetch(").append(CodeUtil.expr(s.param("url")))
                            .append(", RequestOptions.create().setMethod(").append(CodeUtil.literal(method)).append(")");
                    if (!s.param("body").isBlank()) b.append(".setData(").append(CodeUtil.expr(s.param("body"))).append(")");
                    b.append(");");
                    if (!s.param("expectedStatus").isBlank())
                        b.append("\nAssert.assertEquals(apiResponse.status(), ").append((int) Double.parseDouble(s.param("expectedStatus"))).append(", \"API status\");");
                    if (!s.param("storeAs").isBlank())
                        b.append("\nvars.put(").append(CodeUtil.literal(s.param("storeAs"))).append(", apiResponse.text());");
                    return b.toString();
                },
                (x, s) -> {
                    String method = s.param("method").isBlank() ? "GET" : s.param("method").toUpperCase();
                    RequestOptions ro = RequestOptions.create().setMethod(method);
                    if (!s.param("body").isBlank()) ro.setData(TestData.resolve(s.param("body"), x.vars));
                    APIResponse r = x.playwright.request().newContext().fetch(TestData.resolve(s.param("url"), x.vars), ro);
                    if (!s.param("expectedStatus").isBlank()) Assert.assertEquals(r.status(), (int) Double.parseDouble(s.param("expectedStatus")), "API status");
                    if (!s.param("storeAs").isBlank()) x.vars.put(s.param("storeAs"), r.text());
                });

        reg(c, "SET_VARIABLE", "Control Flow", "Set variable", false, P(ParamDef.str("name"), ParamDef.str("value")),
                (s, d) -> "vars.put(" + CodeUtil.literal(s.param("name")) + ", " + CodeUtil.expr(s.param("value")) + ");",
                (x, s) -> x.vars.put(s.param("name"), TestData.resolve(s.param("value"), x.vars)));

        reg(c, "COMMENT", "Control Flow", "Comment", false, P(ParamDef.str("text")),
                (s, d) -> "// " + s.param("text").replace('\n', ' '),
                (x, s) -> { });

        reg(c, "TAKE_SCREENSHOT", "Files", "Take screenshot", false, P(ParamDef.str("name")),
                (s, d) -> "shot(" + CodeUtil.literal(s.param("name").isBlank() ? "manual" : s.param("name")) + ");",
                (x, s) -> x.page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                        .setPath(Paths.get("screenshots", (s.param("name").isBlank() ? "manual" : s.param("name")) + ".png")).setFullPage(true)));
    }

    private static String num(String v) { return v == null || v.isBlank() ? "0" : v.trim(); }
}
