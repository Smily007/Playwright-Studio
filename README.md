# Playwright Test Studio

A Katalon-style desktop studio: open a browser, record your actions, get Playwright Java + TestNG code,
run it, and read an HTML report. Java 25+, Maven, Playwright for Java, TestNG, JavaFX, SLF4J/Logback, Jackson.

> **Status:** this source tree was written without a compiler available. Run `mvn clean test` first and fix any
> compile error it reports (see "First build" below) before relying on it.

## Requirements
* **JDK 25 or newer** (a JDK, not a JRE: the studio compiles generated tests with `javax.tools`). Check: `java -version`, `javac -version`
* **Maven 3.8+**. Check: `mvn -version`
* Internet access on first build (Maven dependencies and Playwright browser download)

## Setup
```bash
# 1. Build + run the unit tests
mvn clean test

# 2. Download the Playwright browsers once (Chromium, Firefox, WebKit)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
#    Linux only, if the browser fails to start because of missing libraries:
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install-deps"

# 3. Start the desktop app
mvn javafx:run          # or ./run.sh  /  run.bat
```
You can also install browsers from the app: **Browser > Install Playwright browsers**.

## First build
`mvn clean test` runs the action catalog, locator engine, code generator, project storage, and generated-action
compilation tests. `AllActionsCompileTest` generates one step for every action and compiles it. If it fails, its message
shows the generated line that does not compile; that is the quickest way to find a problem in a specific action.

## Quick start (about 2 minutes)
1. Use **ADD PROJECT** to create a project folder; after naming the project and setting up environments, choose the parent folder where the new project should be created. The last selected location is used as the starting folder next time. The setup dialog lets you add named environments and their base URLs.
   The first environment URL is the recording/source URL used to map recorded navigation to other environments.
   Use **Project > Manage environments...** to edit them later. Before every test run, select the environment to use.
   Recorded navigation URLs from the project's first environment are mapped to the selected environment while
   preserving the path and query; external URLs are left unchanged.
   Or use **IMPORT PROJECT FOLDER** to open an existing Studio project
   containing `project.json`. You can also import `sample-projects/ExampleProject` (contains `ExampleTest`, which passes,
   and `ExampleFailingTest`, which fails on purpose). Switch between recent folders with the **Project** selector.
2. Select a test case on the left. Press **RUN TEST**. The console streams compile + TestNG output, the result bar shows
   PASSED/FAILED, and **OPEN REPORT** opens the HTML report.
3. To debug a test, press **START DEBUGGER**, choose a test case, then toggle **Break** on its steps in the
   automatically opened Debugger Steps window. Press **START DEBUGGING** in that window to close it and run
   the test in a visible browser.
   **STEP FORWARD** executes one step at a time; **CONTINUE** runs until the next marked breakpoint, and
   **STOP DEBUGGER** ends the debug run. Debug runs use a visible browser.
4. Run `ExampleFailingTest` to see a failed step, the exception, stack trace, screenshot and trace in the report.

## One window: browser + steps + code
The studio opens maximized and adapts as its window is resized. The test-case sidebar and
toolbar controls size or wrap to the available width; below 1050 pixels the live browser moves below the test editor
instead of compressing it. Step-table columns resize to available space. The step editor switches to a stacked layout
on narrow displays, and settings, environment, and history views size to fit the active display.
The interface uses a VS Code-inspired workbench layout with an activity rail, project/test explorer, central test
workspace, and bottom utility launchers for test steps, generated Java, and execution output. Test Steps are available in a separate window from the bottom launcher rather than occupying space in the main workspace. The original Playwright Studio
brand mark appears in the title bar and application windows. Styling is bundled locally; the UI does not require
network access to load external CSS.
Action buttons show their icon above the label with a hover tooltip and accessible label; duplicate shortcut buttons have been removed from the left activity rail.
The upper ribbon groups all project, browser, recording, test, and Excel actions in one row, with URL and selection controls in the row below.
Use the chevron in the title bar to collapse or expand the ribbon.
Interface text scales with the window dimensions within readable minimum and maximum limits; resizing the main or
utility windows updates text size without zooming or clipping the layout.
Keyboard focus has a high-contrast indicator across interactive controls, form labels are associated with their inputs,
and step-form numeric values are validated with a visible error instead of being silently discarded. Control hit areas
have readable minimum sizing. These are accessibility improvements aligned with WCAG 2.2 AA; assistive-technology and
platform-specific behavior still requires manual verification on each supported desktop.

The toolbar has a **View** selector:
* **Embedded (one window)** (default, Chromium/Chrome/Edge): the browser is shown in the *LIVE BROWSER* panel on the right of
  the studio, next to the steps and code. The real browser runs without its own window; the panel streams it (CDP screencast)
  and sends your mouse and keyboard input back through Playwright, so recording works exactly as with a normal window.
  The panel has Back / Forward / Reload and Inspect controls (Back and Forward are recorded as steps). Inspect highlights
  the hovered element; click it to capture its absolute and relative XPath. Enter an XPath in the panel and choose
  Highlight to highlight every matching element in the live page and its frames. When a locator-based test step is
  selected, the captured absolute XPath is applied to that step (including the iframe selector when inspecting inside
  a frame). Otherwise both XPath expressions remain available for copying. Press Escape or Cancel Inspect to exit
  without selecting an element.
  Limits: native dialogs (alert/confirm, OS file chooser, print) are not shown; use ADD STEP > Upload file / Accept dialog.
  Playwright auto-dismisses dialogs it has no handler for. Image streaming is about 30 fps, so it is a little less smooth than a real window.
* **Side by side**: the studio takes the left half of the screen and the real Chromium window opens on the right half.
* **Separate window**: the browser opens as its own window (also used automatically for Firefox and WebKit).

## Recording a test
1. Create a project and a test case (the app asks if you have none).
2. Enter the URL, pick the browser, press **START RECORDING**. The browser opens and the Navigate step is recorded.
3. Click, type, choose dropdown options, tick boxes in the browser. Steps appear in the table, and the Java code updates.
4. Press **STOP RECORDING**. The step table shows locator type and value. If a recorded locator does not work,
   select the step and click **EDIT STEP / LOCATOR**, choose **XPATH**, and enter an XPath expression
   (for example, `//button[@data-testid='save']`). The code preview and generated Java update from that locator.
   You can also double-click a step to edit it. Use ADD STEP / DELETE / DUPLICATE / MOVE / ENABLE-DISABLE for other edits.
   In the embedded browser, enter an XPath and press **HIGHLIGHT** to highlight matching elements. Use **INSPECT**,
   hover the target, then click it to populate the selected locator-based step with its absolute XPath and show its
   relative XPath. If no compatible step is selected, use **COPY XPATH** or **COPY RELATIVE**.
5. **ADD STEP** opens the action palette (search by name, e.g. `assert`, `route`, `wait`) and a form built from the
   action's parameters, with a live code preview. Assertions are in the *Assertions* category.
6. **RUN STEP** runs the selected step against the open browser (START BROWSER first).

Locators are chosen from the DOM, in this order: role, label, placeholder, alt text, test id, text, title, stable id,
CSS, XPath. Coordinates are never used. Password values are stored as `${secret:key}` and the real value goes to
`testdata/secrets.json` (git-ignored); generated code only contains `TestData.resolve("${secret:...}", vars)`.

## Value references inside any text field
`${env:NAME}` environment variable, `${secret:key}` (env `STUDIO_SECRET_KEY` or `testdata/secrets.json`),
`${var:name}` a variable set by SET_VARIABLE or by "storeAs" on a data-extraction step.
Use **IMPORT EXCEL DATA** to add an `.xlsx` or `.xls` workbook to the project, select a worksheet, and use the first
row as variable headers. The selected test runs once per non-empty spreadsheet row. Reference a column in any step
value as `${var:HeaderName}` (for example `${var:LoginName}`); use **CLEAR** to disable the workbook for that project.
The workbook is copied under `testdata/excel/` and remains project-specific (Excel data folders are Git-ignored).
Keep sensitive workbook values protected locally; use the existing secret references for credentials where possible.
`TestData.loadCsv(...)` / `loadJson(...)` also read external data files from your own code.

## Code editor
Use the **GENERATED JAVA** tab at the bottom to open the editable generated-code window. REGENERATE rebuilds it from the steps and warns before overwriting manual edits.
If you edited the code, RUN TEST runs your edited code. SAVE CODE writes `generated/<Class>.java` inside the active
project folder; saved per-test code is restored when you reopen that test or switch back to its project.
Use the **EXECUTION CONSOLE** tab at the bottom to open the live build and test output in its own window.

## Settings
**Settings > Browser & execution settings**: browser (chromium, chrome, msedge, firefox, webkit), headless, executable
path, viewport, timeout, slow motion, video, screenshots (off / failure / every-step), trace (off / on-failure / on),
retry count, parallel threads. Stored in `project.json`. Parallel runs use a TestNG suite with `parallel="classes"`.

## Project layout
```
MyProject/
  project.json  tests/  testdata/  screenshots/  videos/  reports/  logs/
  src/main/java/pages/<Origin>Page.java     # generated shared Page Objects
  src/test/java/tests/<Test>.java           # generated TestNG tests
  src/main/resources/  src/test/resources/  generated/<Test>.java (editable copies)
  reports/<date>/execution-001/  index.html  summary.json  results/  screenshots/  videos/  traces/
```
Recorded locators are generated into page-object locator methods. The default page class is shared by tests that
navigate to the same application origin; navigation to another origin selects its own page class. The built-in runner
compiles these page objects with the generated tests. Use **Project > Add Page Object class...** to name a class and
associate related tests with its application URL; those tests reuse that class's generated locators.
**Reports > Test history** lists all past runs and opens old reports. Use **Clear history** to permanently delete the project's generated report files and history.

## Action catalog
`ActionCatalog` = hand-written actions (upload, drag, dialogs, route mocking/blocking, geolocation, permissions,
cookies, tabs/popups, downloads, API requests, variables, screenshots) + actions **discovered by reflection** from the
Playwright API: every simple-typed method of `Locator`, `Page`, `BrowserContext`, `Keyboard`, `Mouse`, `Clock`,
`LocatorAssertions`, `PageAssertions`, with every simple-typed option as a form field. Upgrading the Playwright version in
`pom.xml` upgrades the catalog. To add an action: build an `ActionDefinition` (params, `codeGen`, `executor`) and call
`ActionCatalog.add`; no UI or generator change is needed.

## Not included yet (be aware)
* Syntax highlighting and line numbers in the code editor (plain `TextArea`; RichTextFX/CodeArea would be the upgrade).
* Point-and-click locator picker, Locator Playground, live Network/Console panels.
* Control-flow steps IF / LOOP / FOR_EACH / CALL_TEST / TRY_CATCH (SET_VARIABLE, COMMENT and per-step *continue on failure* exist).
* Soft assertions as a separate mode, HAR record/replay, `route.fulfill` from files, request/response/WebSocket listeners,
  API-object parameters (`Pattern`, `Map`, `List`, callbacks): reachable only by editing the generated Java code.
* Back/forward recording (reload is recorded; use ADD STEP for Go back/forward).
* The HTML report is a built-in writer, not ExtentReports.
* Running through Maven: `MavenExecutor` exists but the UI runs tests through TestNG directly.

## Troubleshooting
| Problem | Fix |
|---|---|
| `Executable doesn't exist ... playwright install` | Install browsers (see Setup) |
| "JavaFX runtime components are missing" | Start with `mvn javafx:run`, not by running `StudioApp` directly (use `Launcher`) |
| "No Java compiler available" | Start the studio with a JDK, not a JRE |
| Compile error in generated test | The console lists file:line; fix the code in the editor, or the step values |
| Test JVM cannot find classes (`ClassNotFoundException` for `StudioBaseTest`) | The studio passes its own classpath to the test JVM; start it through Maven or an IDE run configuration that uses a normal classpath |
| Live panel stays black | Wait for the page to load; if it stays black, switch View to *Side by side* and report the console message |
| Browser opens but no steps are recorded | Recording must be ON before you act; pages with strict iframes from other origins record only top-level actions |
| Linux: browser fails to start | `install-deps` command above |
| Corporate proxy blocks browser download | Set `HTTPS_PROXY`, or point *Executable path* to an installed Chrome/Edge |
