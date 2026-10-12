package com.automation.studio.ui;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.actions.ActionDefinition;
import com.automation.studio.actions.StepDescriber;
import com.automation.studio.browser.BrowserConfiguration;
import com.automation.studio.browser.BrowserManager;
import com.automation.studio.browser.LiveView;
import com.automation.studio.browser.ViewOptions;
import com.automation.studio.configuration.ConfigurationManager;
import com.automation.studio.execution.DebuggerController;
import com.automation.studio.execution.ExecutionManager;
import com.automation.studio.execution.ExecutionResult;
import com.automation.studio.execution.ProcessRunner;
import com.automation.studio.execution.TestNGExecutor;
import com.automation.studio.generator.JavaTestGenerator;
import com.automation.studio.locator.LocatorEngine;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.logging.LogManager;
import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectEnvironment;
import com.automation.studio.model.ProjectPageObject;
import com.automation.studio.model.TestCase;
import com.automation.studio.model.TestResult;
import com.automation.studio.model.TestStep;
import com.automation.studio.project.ProjectService;
import com.automation.studio.recorder.ActionRecorder;
import com.automation.studio.recorder.BrowserEventListener;
import com.automation.studio.reporting.ReportManager;
import com.automation.studio.runtime.TestData;
import com.automation.studio.utilities.FileUtils;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.css.PseudoClass;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The "Playwright Test Studio" main window. All Playwright work is delegated to background threads. */
public class MainWindow {
    private static final PseudoClass STEP_PASSED = PseudoClass.getPseudoClass("step-passed");
    private static final PseudoClass STEP_FAILED = PseudoClass.getPseudoClass("step-failed");

    private final Stage stage;
    private final HostServices hosts;
    private final VBox activityBar = new VBox(8);
    private Region testSidebar;
    private final Label titleProject = new Label("No project open");
    private VBox toolbarPanel;
    private Button toolbarToggle;
    private ToggleButton explorerToggle;
    private Node stepsPane;

    private final ActionCatalog catalog = new ActionCatalog();
    private final ConfigurationManager config = new ConfigurationManager();
    private final ProjectService projects = new ProjectService();
    private final ReportManager reports = new ReportManager();
    private final LocatorEngine locatorEngine = new LocatorEngine();
    private final JavaTestGenerator generator = new JavaTestGenerator(catalog);
    private final BrowserManager browser = new BrowserManager();
    private final ExecutionManager execution = new ExecutionManager(generator, new TestNGExecutor(), reports);
    private final DebuggerController debugger = new DebuggerController();
    private final ExecutorService bg = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "studio-bg"); t.setDaemon(true); return t; });
    private final ActionRecorder recorder = new ActionRecorder(locatorEngine, new BrowserEventListener() {
        @Override public void onStep(TestStep step) { Platform.runLater(() -> addRecorded(step)); }
        @Override public void onReplaceLast(int count, TestStep step) { Platform.runLater(() -> replaceRecorded(count, step)); }
    }, this::storeSecret);
    private LogManager logManager;

    private volatile Project project;
    private volatile TestCase current;
    private int secretCounter;
    private Path lastReport;
    private boolean codeDirty;
    private boolean programmatic;

    private final TextField urlField = new TextField();
    private final ComboBox<String> browserBox = new ComboBox<>();
    private final CheckBox headless = new CheckBox("Headless");
    private final ListView<TestCase> testList = new ListView<>();
    private final ObservableList<TestStep> stepItems = FXCollections.observableArrayList();
    private final TableView<TestStep> table = new TableView<>(stepItems);
    private final TextArea code = new TextArea();
    private final TextArea console = new TextArea();
    private Stage codeWindow;
    private Stage consoleWindow;
    private Stage stepsWindow;
    private final Label result = new Label("RESULT: -");
    private final Button openReport = new Button();
    private final Button startRec = new Button();
    private final Button stopRec = new Button();
    private final Button runBtn = new Button();
    private final Button debugBtn = new Button();
    private final Button debugForwardBtn = new Button();
    private final Button debugContinueBtn = new Button();
    private final Button debugStopBtn = new Button();
    private Button debugStepsStartBtn;
    private final LiveBrowserPanel livePanel = new LiveBrowserPanel();
    private final ComboBox<String> viewBox = new ComboBox<>();
    private final ComboBox<String> projectBox = new ComboBox<>();
    private final Label excelDataStatus = new Label("No Excel test data");
    private final Menu recentMenu = new Menu("Recent projects");
    private boolean updatingProjectChoices;
    private boolean restoringTestSelection;
    private TestCase pendingDebugTest;

    public MainWindow(Stage stage, HostServices hosts) {
        this.stage = stage;
        this.hosts = hosts;
    }

    public void show() {
        logManager = new LogManager(this::log);
        stage.setTitle("Playwright Test Studio");
        stage.getIcons().setAll(StudioBrand.windowIcon());
        Rectangle2D screen = WindowSizing.boundsFor(null);
        double initialWidth = Math.min(1600, screen.getWidth() * 0.96);
        double initialHeight = Math.min(900, screen.getHeight() * 0.94);
        SplitPane main = new SplitPane();
        BorderPane root = buildRoot(main);
        Scene scene = new Scene(root, initialWidth, initialHeight);
        StudioTheme.install(scene);
        stage.setScene(scene);
        scene.widthProperty().addListener((o, oldWidth, newWidth) ->
                adaptLayout(newWidth.doubleValue(), scene.getHeight(), main, root));
        scene.heightProperty().addListener((o, oldHeight, newHeight) ->
                adaptLayout(scene.getWidth(), newHeight.doubleValue(), main, root));
        adaptLayout(initialWidth, initialHeight, main, root);
        stage.setOnCloseRequest(e -> { recorder.setRecording(false); debugger.stop(); browser.close(); logManager.close(); });
        stage.setMaximized(true);
        stage.show();
        browser.onBrowserClosed(msg -> Platform.runLater(() -> { log(msg); livePanel.clear(); setRecordingUi(false); recorder.setRecording(false); }));
        browser.onElementInspected((xpath, relativeXPath, frameSelector) ->
                livePanel.setInspectResult(xpath, relativeXPath, frameSelector));
        log("Welcome. Create or open a project (Project menu), enter a URL and press START RECORDING.");
    }

    // ------------------------------------------------------------------ UI construction

    private BorderPane buildRoot(SplitPane main) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("studio-root");
        toolbarPanel = buildToolbar();
        VBox top = new VBox(buildTitleBar(), buildMenu(), toolbarPanel);
        top.getStyleClass().add("top-shell");
        root.setTop(top);

        testList.setPrefWidth(180);
        testList.setMinWidth(0);
        testList.setMaxWidth(Double.MAX_VALUE);
        testList.setAccessibleText("Test Explorer");
        testList.setPlaceholder(new Label("No test cases yet. Use New test to add one."));
        testList.getStyleClass().add("explorer-list");
        testList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TestCase item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label dot = new Label("•");
                    dot.setStyle("-fx-text-fill: #1a73e8; -fx-font-size: 15px; -fx-font-weight: bold;");
                    Label name = new Label(item.name);
                    name.setStyle("-fx-font-weight: 500; -fx-text-fill: #202124;");
                    HBox.setHgrow(name, Priority.ALWAYS);
                    name.setMaxWidth(Double.MAX_VALUE);
                    SVGPath trashIcon = new SVGPath();
                    trashIcon.setContent("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
                    trashIcon.getStyleClass().add("delete-test-icon");
                    Button delete = new Button();
                    delete.setGraphic(trashIcon);
                    delete.getStyleClass().add("action-delete-test");
                    delete.setAccessibleText("Delete test " + item.name);
                    delete.setTooltip(new Tooltip("Delete test"));
                    delete.setFocusTraversable(true);
                    delete.setOnAction(e -> deleteTest(item));
                    HBox row = new HBox(6, dot, name, delete);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        testList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null && !restoringTestSelection) {
                if (pendingDebugTest != null && b != pendingDebugTest) clearPendingDebugger();
                loadTest(b);
            }
        });
        Label explorerTitle = new Label("TEST EXPLORER");
        Button newTest = new Button("+");
        newTest.getStyleClass().addAll("button-secondary", "action-new-test");
        newTest.setAccessibleText("New test");
        newTest.setTooltip(new Tooltip("New test"));
        newTest.setOnAction(e -> newTest());
        Region headerSpacer = new Region();
        HBox explorerHeader = new HBox(explorerTitle, headerSpacer, newTest);
        explorerHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        VBox left = new VBox(6, explorerHeader, testList);
        left.getStyleClass().add("test-sidebar");
        explorerTitle.getStyleClass().add("section-title");
        left.setPadding(new Insets(8));
        left.setMinWidth(0);
        left.setMaxWidth(Double.MAX_VALUE);
        explorerHeader.setMinWidth(0);
        VBox.setVgrow(testList, Priority.ALWAYS);
        testSidebar = left;
        HBox sidebarDock = new HBox(buildActivityBar(), left);
        sidebarDock.getStyleClass().add("sidebar-dock");
        sidebarDock.setMinWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);

        stepsPane = buildStepsPane();
        if (stepsPane instanceof Region region) region.setMinSize(0, 0);
        livePanel.setMinSize(0, 0);
        main.getItems().setAll(livePanel);
        main.setMinSize(0, 0);
        livePanel.setNavHandler(this::liveNav);
        livePanel.setInspectHandler(this::applyInspectedXPath);
        SplitPane workspace = new SplitPane(sidebarDock, main);
        workspace.setOrientation(Orientation.HORIZONTAL);
        workspace.setDividerPositions(0.24);
        workspace.setMinSize(0, 0);
        root.setCenter(workspace);

        result.getStyleClass().add("result-idle");
        openReport.setDisable(true);
        openReport.getStyleClass().add("button-secondary");
        IconButton.configure(openReport, "Open report", "↗");
        openReport.setOnAction(e -> { if (lastReport != null) hosts.showDocument(lastReport.toUri().toString()); });
        Button javaWindow = IconButton.create("Generated Java", "{}", this::showCodeWindow);
        javaWindow.getStyleClass().addAll("utility-tab", "button-quiet");
        Button stepsWindowButton = IconButton.create("Test Steps", "☷", this::showStepsWindow);
        stepsWindowButton.getStyleClass().addAll("utility-tab", "button-quiet");
        Button consoleWindowButton = IconButton.create("Execution console", "≡", this::showConsoleWindow);
        consoleWindowButton.getStyleClass().addAll("utility-tab", "button-quiet");
        FlowPane bottom = new FlowPane(8, 6, result, openReport, stepsWindowButton, javaWindow, consoleWindowButton);
        bottom.getStyleClass().add("status-bar");
        bottom.setPadding(new Insets(8));
        root.setBottom(bottom);
        return root;
    }

    private void adaptLayout(double width, double height, SplitPane main, BorderPane root) {
        if (width <= 0 || height <= 0) return;
        applyResponsiveFontSize(root, width, height);
        boolean narrow = width < 1050;
        boolean compact = width < 760;
        Orientation desired = narrow ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        if (main.getOrientation() != desired) {
            main.setOrientation(desired);
            main.setDividerPositions(narrow ? 0.72 : 0.68);
        }
        livePanel.setMinSize(0, narrow ? Math.min(150, height * 0.22) : 0);

        testList.setPrefWidth(compact ? 120 : Math.max(160, Math.min(260, width * 0.14)));
        if (testSidebar != null) testSidebar.setMinWidth(0);
    }

    private void applyResponsiveFontSize(Region root, double width, double height) {
        double scale = Math.max(0.85, Math.min(1.25, Math.min(width / 1280.0, height / 800.0)));
        double fontSize = Math.round(12.0 * scale * 10.0) / 10.0;
        Object previous = root.getProperties().get("studio.fontSize");
        if (!(previous instanceof Double value) || Double.compare(fontSize, value) != 0) {
            root.setStyle("-fx-font-size: " + fontSize + "px;");
            root.getProperties().put("studio.fontSize", fontSize);
        }
    }

    private Node buildTitleBar() {
        Label title = new Label("PLAYWRIGHT STUDIO");
        title.getStyleClass().add("brand-title");
        titleProject.getStyleClass().add("brand-project");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbarToggle = new Button("▾");
        toolbarToggle.getStyleClass().add("toolbar-collapse-button");
        toolbarToggle.setAccessibleText("Collapse top toolbar");
        toolbarToggle.setTooltip(new Tooltip("Collapse top toolbar"));
        toolbarToggle.setFocusTraversable(true);
        toolbarToggle.setOnAction(e -> toggleToolbar());
        HBox bar = new HBox(10, StudioBrand.mark(), title, spacer, titleProject, toolbarToggle);
        bar.getStyleClass().add("title-bar");
        return bar;
    }

    private void toggleToolbar() {
        boolean show = !toolbarPanel.isVisible();
        toolbarPanel.setVisible(show);
        toolbarPanel.setManaged(show);
        toolbarToggle.setText(show ? "▾" : "▸");
        toolbarToggle.setAccessibleText(show ? "Collapse top toolbar" : "Expand top toolbar");
        toolbarToggle.setTooltip(new Tooltip(show ? "Collapse top toolbar" : "Expand top toolbar"));
    }

    private Node buildActivityBar() {
        activityBar.getStyleClass().add("activity-bar");
        activityBar.setAlignment(Pos.TOP_CENTER);
        explorerToggle = new ToggleButton("▤");
        explorerToggle.getStyleClass().add("sidebar-toggle");
        explorerToggle.setSelected(true);
        explorerToggle.setAccessibleText("Hide Test Explorer");
        explorerToggle.setTooltip(new Tooltip("Hide Test Explorer"));
        explorerToggle.setFocusTraversable(true);
        explorerToggle.setOnAction(e -> setTestSidebarVisible(explorerToggle.isSelected()));
        activityBar.getChildren().setAll(StudioBrand.mark(), explorerToggle);
        return activityBar;
    }

    private void setTestSidebarVisible(boolean visible) {
        testSidebar.setVisible(visible);
        testSidebar.setManaged(visible);
        String action = visible ? "Hide" : "Show";
        explorerToggle.setAccessibleText(action + " Test Explorer");
        explorerToggle.setTooltip(new Tooltip(action + " Test Explorer"));
    }

    private MenuBar buildMenu() {
        MenuItem newP = item("Add project...", this::newProject);
        MenuItem openP = item("Import project folder...", this::openProject);
        MenuItem saveP = item("Save", () -> saveProject(true));
        MenuItem saveAs = item("Save As...", this::saveProjectAs);
        MenuItem environments = item("Manage environments...", this::manageEnvironments);
        MenuItem closeP = item("Close project", this::closeProject);
        Menu project = new Menu("Project", null, newP, openP, environments,
                item("Add Page Object class...", this::addPageObject), saveP, saveAs, closeP,
                new SeparatorMenuItem(), recentMenu);
        refreshRecent();

        Menu tests = new Menu("Test Cases", null, item("New test case...", this::newTest),
                item("Delete selected test case", () -> deleteTest(testList.getSelectionModel().getSelectedItem())),
                item("Run all tests (suite)", () -> runTests(true)));
        Menu browserM = new Menu("Browser", null, item("Start browser", () -> launchBrowser(false)), item("Stop browser", this::stopBrowser),
                item("Install Playwright browsers", () -> playwrightCli(List.of("install"))),
                item("Open Playwright Codegen / Inspector", this::openCodegen),
                item("Open trace file in Trace Viewer...", this::openTrace));
        Menu settings = new Menu("Settings", null, item("Browser & execution settings...", this::openSettings));
        Menu rep = new Menu("Reports", null, item("Open last report", () -> { if (lastReport != null) hosts.showDocument(lastReport.toUri().toString()); }),
                item("Test history...", this::openHistory));
        MenuBar menuBar = new MenuBar(project, tests, browserM, settings, rep);
        menuBar.getStyleClass().add("studio-menu");
        return menuBar;
    }

    private static MenuItem item(String text, Runnable r) {
        MenuItem m = new MenuItem(text);
        m.setOnAction(e -> r.run());
        return m;
    }

    private VBox buildToolbar() {
        urlField.setPromptText("https://example.com");
        urlField.getStyleClass().add("toolbar-control");
        HBox.setHgrow(urlField, Priority.ALWAYS);
        browserBox.getItems().addAll(BrowserConfiguration.BROWSERS);
        browserBox.setValue("chromium");
        browserBox.setPrefWidth(125);
        browserBox.setMinWidth(110);
        browserBox.setMaxWidth(150);
        browserBox.getStyleClass().add("toolbar-control");
        viewBox.getItems().addAll("Embedded (one window)", "Side by side", "Separate window");
        viewBox.setValue("Embedded (one window)");
        viewBox.setPrefWidth(190);
        viewBox.setMinWidth(155);
        viewBox.setMaxWidth(220);
        viewBox.getStyleClass().add("toolbar-control");
        Button start = new Button();
        IconButton.configure(start, "Start browser", "▶");
        start.getStyleClass().addAll("button-secondary", "toolbar-button");
        start.setOnAction(e -> launchBrowser(false));
        Button stop = new Button();
        IconButton.configure(stop, "Stop browser", "■");
        stop.getStyleClass().addAll("button-quiet", "toolbar-button");
        stop.setOnAction(e -> stopBrowser());
        urlField.setPrefWidth(320);
        urlField.setMinWidth(100);

        IconButton.configure(startRec, "Start recording", "●");
        startRec.setOnAction(e -> startRecording());
        startRec.getStyleClass().addAll("button-primary", "toolbar-button");
        IconButton.configure(stopRec, "Stop recording", "■");
        stopRec.setOnAction(e -> stopRecording());
        stopRec.getStyleClass().addAll("button-quiet", "toolbar-button");
        stopRec.setDisable(true);
        IconButton.configure(runBtn, "Run test", "▶");
        runBtn.setOnAction(e -> runTests(false));
        runBtn.getStyleClass().addAll("button-primary", "toolbar-button");
        IconButton.configure(debugBtn, "Start debugger", "DBG");
        debugBtn.setOnAction(e -> prepareOrRunDebugger());
        debugBtn.getStyleClass().addAll("button-secondary", "toolbar-button");
        IconButton.configure(debugForwardBtn, "Step forward", "⏭");
        debugForwardBtn.setOnAction(e -> forwardDebugger());
        debugForwardBtn.getStyleClass().addAll("button-secondary", "toolbar-button");
        debugForwardBtn.setDisable(true);
        IconButton.configure(debugContinueBtn, "Continue to next breakpoint", "⏩");
        debugContinueBtn.setOnAction(e -> continueDebugger());
        debugContinueBtn.getStyleClass().addAll("button-secondary", "toolbar-button");
        debugContinueBtn.setDisable(true);
        IconButton.configure(debugStopBtn, "Stop debugger", "■");
        debugStopBtn.setOnAction(e -> stopDebugger());
        debugStopBtn.getStyleClass().addAll("button-quiet", "toolbar-button");
        debugStopBtn.setDisable(true);
        projectBox.setPromptText("Select project folder");
        projectBox.setPrefWidth(170);
        projectBox.setMinWidth(100);
        projectBox.setMaxWidth(220);
        projectBox.getStyleClass().add("toolbar-control");
        projectBox.setOnAction(e -> {
            String selected = projectBox.getValue();
            if (!updatingProjectChoices && selected != null
                    && (project == null || !project.root.toAbsolutePath().toString().equals(selected))) {
                openProjectAt(Path.of(selected));
            }
        });
        Button importExcel = new Button();
        IconButton.configure(importExcel, "Import Excel data", "▦");
        importExcel.getStyleClass().addAll("button-secondary", "toolbar-button");
        importExcel.setOnAction(e -> importExcelData());
        Button clearExcel = new Button();
        IconButton.configure(clearExcel, "Clear Excel data", "×");
        clearExcel.getStyleClass().addAll("button-quiet", "toolbar-button");
        clearExcel.setOnAction(e -> clearExcelData());
        HBox actions = new HBox(8, start, stop, startRec, stopRec, runBtn, debugBtn,
                debugForwardBtn, debugContinueBtn, debugStopBtn, importExcel, clearExcel);
        actions.getStyleClass().add("toolbar-action-row");
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 6, 4, 6));
        ScrollPane actionScroll = toolbarRowScroll(actions, 44);

        urlField.setMaxWidth(Double.MAX_VALUE);
        Label urlLabel = new Label("URL:");
        urlLabel.setLabelFor(urlField);
        urlField.setAccessibleText("Base URL for recording");
        HBox urlInput = new HBox(6, urlLabel, urlField);
        urlInput.getStyleClass().add("toolbar-input");
        HBox.setHgrow(urlField, Priority.ALWAYS);
        Label browserLabel = new Label("Browser:");
        browserLabel.setLabelFor(browserBox);
        browserBox.setAccessibleText("Browser engine");
        headless.setAccessibleText("Run browser headless");
        HBox browserInput = new HBox(6, browserLabel, browserBox, headless);
        browserInput.getStyleClass().add("toolbar-input");
        Label viewLabel = new Label("View:");
        viewLabel.setLabelFor(viewBox);
        viewBox.setAccessibleText("Browser window view mode");
        HBox viewInput = new HBox(6, viewLabel, viewBox);
        viewInput.getStyleClass().add("toolbar-input");
        excelDataStatus.setWrapText(true);
        excelDataStatus.setPrefWidth(170);
        excelDataStatus.setMinWidth(80);
        Label projectLabel = new Label("Project:");
        projectLabel.setLabelFor(projectBox);
        projectBox.setAccessibleText("Recent project folders");
        excelDataStatus.setAccessibleText("Excel data status");
        excelDataStatus.getStyleClass().add("excel-data-badge");
        HBox selectors = new HBox(10, urlInput, projectLabel, projectBox, browserInput, viewInput, excelDataStatus);
        selectors.getStyleClass().add("toolbar-dropdown-row");
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.setPadding(new Insets(2, 6, 4, 6));
        HBox.setHgrow(urlInput, Priority.ALWAYS);
        ScrollPane selectorScroll = toolbarRowScroll(selectors, 44);
        VBox toolbar = new VBox(actionScroll, selectorScroll);
        toolbar.getStyleClass().add("toolbar-surface");
        return toolbar;
    }

    private ScrollPane toolbarRowScroll(HBox row, double height) {
        ScrollPane scroll = new ScrollPane(row);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.setPrefViewportHeight(height);
        scroll.setMinHeight(height);
        scroll.setMaxHeight(height);
        scroll.getStyleClass().add("toolbar-row-scroll");
        return scroll;
    }

    private Node buildStepsPane() {
        table.getStyleClass().add("step-table");
        table.setAccessibleText("Test steps and execution results");
        table.setPlaceholder(new Label("No steps yet. Use Add Step or record browser actions."));
        table.getColumns().add(col("#", 40, s -> String.valueOf(s.stepNumber)));
        TableColumn<TestStep, Boolean> breakpointColumn = new TableColumn<>("Break");
        breakpointColumn.setPrefWidth(58);
        breakpointColumn.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().breakpoint));
        breakpointColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setAccessibleText("Toggle breakpoint");
                check.setTooltip(new Tooltip("Pause before this step while debugging"));
                check.setOnAction(e -> {
                    int row = getIndex();
                    if (row >= 0 && row < stepItems.size()) {
                        stepItems.get(row).breakpoint = check.isSelected();
                        syncSteps(true);
                    }
                });
            }
            @Override
            protected void updateItem(Boolean enabled, boolean empty) {
                super.updateItem(enabled, empty);
                setGraphic(empty ? null : check);
                if (!empty) {
                    check.setSelected(Boolean.TRUE.equals(enabled));
                    check.setAccessibleText("Toggle breakpoint for step " + (getIndex() + 1));
                }
            }
        });
        table.getColumns().add(breakpointColumn);
        table.getColumns().add(col("Action", 190, s -> { ActionDefinition d = catalog.get(s.action); return (s.enabled ? "" : "(off) ") + (d == null ? s.action : d.displayName); }));
        table.getColumns().add(col("Locator type", 115, s -> s.locator == null ? "" : s.locator.locatorType.name()));
        table.getColumns().add(col("Locator value", 300, s -> s.locator == null ? "" : s.locator.locatorValue));
        table.getColumns().add(col("Value", 300, StepDescriber::values));
        table.getColumns().add(col("Status", 110, s -> s.status));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> {
            return new TableRow<>() {
                @Override
                protected void updateItem(TestStep step, boolean empty) {
                    super.updateItem(step, empty);
                    pseudoClassStateChanged(STEP_PASSED, !empty && "PASSED".equalsIgnoreCase(step.status));
                    pseudoClassStateChanged(STEP_FAILED, !empty && "FAILED".equalsIgnoreCase(step.status));
                }
                {
                    setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !isEmpty()) editStep(); });
                }
            };
        });
        Button add = btn("ADD STEP", this::addStep), edit = btn("EDIT STEP / LOCATOR", this::editStep), del = btn("DELETE", this::deleteStep),
                dup = btn("DUPLICATE", this::duplicateStep), up = btn("MOVE UP", () -> move(-1)), down = btn("MOVE DOWN", () -> move(1)),
                toggle = btn("ENABLE/DISABLE", this::toggleStep), breakpoint = btn("TOGGLE BREAK", this::toggleBreakpoint),
                runStep = btn("RUN STEP", this::runSelectedStep);
        debugStepsStartBtn = IconButton.create("START DEBUGGING", "▶", this::prepareOrRunDebugger);
        debugStepsStartBtn.getStyleClass().addAll("button-primary", "action-button");
        debugStepsStartBtn.setVisible(false);
        debugStepsStartBtn.setManaged(false);
        add.getStyleClass().add("button-primary");
        edit.getStyleClass().add("button-secondary");
        del.getStyleClass().add("button-quiet");
        runStep.getStyleClass().add("button-secondary");
        FlowPane bar = new FlowPane(6, 6, add, edit, del, dup, up, down, toggle, breakpoint, runStep,
                debugStepsStartBtn);
        bar.getStyleClass().add("action-strip");
        bar.setPadding(new Insets(6, 0, 0, 0));
        Label locatorHelp = new Label("To fix a recorded locator: select the step, choose EDIT STEP / LOCATOR, set Locator type to XPATH, and enter its XPath expression.");
        locatorHelp.setWrapText(true);
        VBox box = new VBox(4, new Label("TEST STEPS"), table, locatorHelp, bar);
        box.getStyleClass().add("workspace-panel");
        box.getChildren().get(0).getStyleClass().add("section-title");
        locatorHelp.getStyleClass().add("helper-text");
        box.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private Node buildCodePane() {
        code.getStyleClass().add("code-editor");
        code.setAccessibleText("Generated Java code editor");
        code.textProperty().addListener((o, a, b) -> { if (!programmatic) codeDirty = true; });
        TextField find = new TextField();
        find.setPromptText("Search in code");
        find.setAccessibleText("Search generated code");
        find.setOnAction(e -> findNext(find.getText()));
        Button findBtn = btn("FIND", () -> findNext(find.getText()));
        Button regen = btn("REGENERATE", () -> refreshCode(true));
        Button save = btn("SAVE CODE", this::saveCode);
        Button copy = btn("COPY", () -> { ClipboardContent c = new ClipboardContent(); c.putString(code.getText()); Clipboard.getSystemClipboard().setContent(c); });
        regen.getStyleClass().add("button-secondary");
        save.getStyleClass().add("button-primary");
        copy.getStyleClass().add("button-quiet");
        FlowPane bar = new FlowPane(6, 6, find, findBtn, regen, save, copy);
        bar.getStyleClass().add("action-strip");
        find.setPrefWidth(200);
        VBox box = new VBox(4, new Label("GENERATED JAVA CODE (editable)"), code, bar);
        box.getStyleClass().add("workspace-panel");
        box.getChildren().get(0).getStyleClass().add("section-title");
        box.setPadding(new Insets(8));
        VBox.setVgrow(code, Priority.ALWAYS);
        return box;
    }

    private Node buildConsolePane() {
        console.setEditable(false);
        console.setAccessibleText("Execution output");
        console.getStyleClass().add("console-editor");
        VBox box = new VBox(4, new Label("EXECUTION CONSOLE"), console);
        box.getStyleClass().add("workspace-panel");
        box.getChildren().get(0).getStyleClass().add("section-title");
        box.setPadding(new Insets(8));
        VBox.setVgrow(console, Priority.ALWAYS);
        return box;
    }

    private void showCodeWindow() {
        if (codeWindow == null) {
            codeWindow = createUtilityWindow("Generated Java Code", buildCodePane(), 1050, 680);
        }
        codeWindow.show();
        codeWindow.toFront();
    }

    private void showStepsWindow() {
        if (stepsWindow != null && stepsWindow.isShowing()) {
            stepsWindow.toFront();
            return;
        }
        if (stepsWindow == null) {
            stepsWindow = createUtilityWindow("Test Steps", stepsPane, 1050, 680);
        }
        stepsWindow.show();
        stepsWindow.toFront();
    }

    private void showConsoleWindow() {
        if (consoleWindow == null) {
            consoleWindow = createUtilityWindow("Execution Console", buildConsolePane(), 900, 560);
        }
        consoleWindow.show();
        consoleWindow.toFront();
    }

    private Stage createUtilityWindow(String title, Node content, double width, double height) {
        return createUtilityWindow(title, content, width, height, () -> {});
    }

    private Stage createUtilityWindow(String title, Node content, double width, double height, Runnable onClose) {
        Stage window = new Stage();
        window.initOwner(stage);
        window.setTitle(title);
        window.getIcons().setAll(StudioBrand.windowIcon());
        var size = WindowSizing.fit(stage, width, height);
        BorderPane root = new BorderPane(content);
        Scene scene = new Scene(root, size.getWidth(), size.getHeight());
        StudioTheme.install(scene);
        applyResponsiveFontSize(root, size.getWidth(), size.getHeight());
        scene.widthProperty().addListener((o, oldWidth, newWidth) ->
                applyResponsiveFontSize(root, newWidth.doubleValue(), scene.getHeight()));
        scene.heightProperty().addListener((o, oldHeight, newHeight) ->
                applyResponsiveFontSize(root, scene.getWidth(), newHeight.doubleValue()));
        window.setScene(scene);
        window.setOnCloseRequest(event -> {
            event.consume();
            window.hide();
            onClose.run();
        });
        return window;
    }

    private static Button btn(String text, Runnable r) {
        String icon = switch (text) {
            case "ADD STEP" -> "+";
            case "EDIT STEP / LOCATOR" -> "✎";
            case "DELETE" -> "×";
            case "DUPLICATE" -> "⧉";
            case "MOVE UP" -> "↑";
            case "MOVE DOWN" -> "↓";
            case "ENABLE/DISABLE" -> "✓";
            case "TOGGLE BREAK" -> "●";
            case "RUN STEP" -> "▷";
            case "FIND" -> "⌕";
            case "REGENERATE" -> "↻";
            case "SAVE CODE" -> "▣";
            case "COPY" -> "⧉";
            default -> throw new IllegalArgumentException("No icon configured for button: " + text);
        };
        Button b = IconButton.create(text, icon, r);
        b.getStyleClass().add("action-button");
        return b;
    }

    private static TableColumn<TestStep, String> col(String title, double w, java.util.function.Function<TestStep, String> f) {
        TableColumn<TestStep, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    // ------------------------------------------------------------------ logging / dialogs

    private void log(String line) {
        Platform.runLater(() -> console.appendText(line + "\n"));
    }

    private void showError(String title, Throwable t) {
        Throwable c = t;
        while ((c instanceof CompletionException || c instanceof java.util.concurrent.ExecutionException) && c.getCause() != null) c = c.getCause();
        String msg = String.valueOf(c.getMessage());
        if (msg.contains("Executable doesn't exist") || msg.contains("playwright install"))
            msg += "\n\nThe Playwright browsers are not installed. Use Browser > Install Playwright browsers.";
        String shown = msg.length() > 1800 ? msg.substring(0, 1800) + "..." : msg;
        log("ERROR " + title + ": " + msg.split("\n")[0]);
        Alert a = new Alert(Alert.AlertType.ERROR, shown);
        a.initOwner(stage);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.initOwner(stage);
        a.showAndWait();
    }

    private Optional<String> ask(String title, String def) {
        TextInputDialog d = new TextInputDialog(def);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(title);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    // ------------------------------------------------------------------ projects

    private void newProject() {
        Optional<String> name = ask("New project name", "MyProject");
        if (name.isEmpty()) return;
        List<ProjectEnvironment> defaults = List.of(new ProjectEnvironment("Development", "https://example.com"));
        Optional<List<ProjectEnvironment>> environments = EnvironmentSetupView.show(stage, defaults);
        if (environments.isEmpty()) return;
        Optional<Path> parent = chooseProjectParentDirectory();
        if (parent.isEmpty() || !prepareProjectSwitch()) return;
        try {
            Project created = createProject(parent.get(), name.get(), environments.get());
            applyProject(created);
            log("Created project at " + project.root);
        } catch (Exception e) { showError("Cannot create project", e); }
    }

    private Optional<Path> chooseProjectParentDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose where to create the project");
        Path initialDirectory = Path.of(config.get().projectsDir);
        if (!java.nio.file.Files.isDirectory(initialDirectory))
            initialDirectory = Path.of(System.getProperty("user.home"));
        if (java.nio.file.Files.isDirectory(initialDirectory))
            chooser.setInitialDirectory(initialDirectory.toFile());
        return Optional.ofNullable(chooser.showDialog(stage)).map(File::toPath);
    }

    private Project createProject(Path parent, String name, List<ProjectEnvironment> environments) throws IOException {
        Project created = projects.create(parent, name, environments);
        config.get().projectsDir = parent.toAbsolutePath().toString();
        config.save();
        return created;
    }

    private void manageEnvironments() {
        if (project == null) { info("Create or open a project first."); return; }
        EnvironmentSetupView.show(stage, project.environments).ifPresent(environments -> {
            project.environments = environments;
            urlField.setText(project.baseUrl);
            if (saveProject(false)) log("Project environments saved.");
        });
    }

    private void addPageObject() {
        if (project == null) {
            info("Create or open a project before adding a Page Object.");
            return;
        }
        if (project.tests.isEmpty()) {
            info("Add at least one test case before creating a Page Object.");
            return;
        }
        Optional<ProjectPageObject> created = PageObjectSetupView.show(stage, project, current);
        if (created.isEmpty()) return;
        project.pageObjects.add(created.get());
        if (saveProject(false)) {
            log("Added reusable Page Object " + created.get().className
                    + " for " + created.get().testNames.size() + " related test(s).");
            if (current != null) refreshCode(false);
        } else {
            project.pageObjects.remove(created.get());
        }
    }

    private void openProject() {
        DirectoryChooser ch = new DirectoryChooser();
        ch.setTitle("Open project folder (contains project.json)");
        Path initialDirectory = project == null
                ? Path.of(config.get().projectsDir)
                : project.root.getParent();
        if (initialDirectory != null && java.nio.file.Files.isDirectory(initialDirectory))
            ch.setInitialDirectory(initialDirectory.toFile());
        File f = ch.showDialog(stage);
        if (f != null) openProjectAt(f.toPath());
    }

    private void openProjectAt(Path dir) {
        if (!prepareProjectSwitch()) return;
        try { applyProject(projects.open(dir)); log("Opened project " + project.root); }
        catch (Exception e) {
            refreshProjectChoices();
            showError("Cannot open project", e);
        }
    }

    private void applyProject(Project p) {
        clearPendingDebugger();
        current = null;
        codeDirty = false;
        stepItems.clear();
        setCode("");
        project = p;
        System.setProperty("studio.projectDir", p.root.toAbsolutePath().toString());
        config.addRecent(p.root);
        refreshRecent();
        refreshProjectChoices();
        refreshExcelDataStatus();
        browserBox.setValue(p.browserConfig.browser);
        headless.setSelected(p.browserConfig.headless);
        setViewBox(p.browserConfig.viewMode);
        urlField.setText(p.baseUrl == null || p.baseUrl.isBlank()
                ? (p.environments.isEmpty() ? "" : p.environments.get(0).url)
                : p.baseUrl);
        testList.setItems(FXCollections.observableArrayList(p.tests));
        stage.setTitle("Playwright Test Studio - " + p.projectName);
        titleProject.setText(p.projectName);
        if (!p.tests.isEmpty()) testList.getSelectionModel().select(0);
        else { current = null; stepItems.clear(); setCode(""); }
    }

    private boolean saveProject(boolean message) {
        if (project == null) { if (message) info("No project is open."); return true; }
        try {
            syncConfig();
            if (current != null) syncSteps(false);
            project.tests = new java.util.ArrayList<>(testList.getItems());
            projects.save(project);
            if (message) log("Project saved.");
            return true;
        } catch (Exception e) {
            showError("Cannot save project", e);
            return false;
        }
    }

    private void saveProjectAs() {
        if (project == null) return;
        DirectoryChooser ch = new DirectoryChooser();
        ch.setTitle("Choose the new project folder");
        File f = ch.showDialog(stage);
        if (f == null) return;
        try {
            if (!saveProject(false) || !saveCurrentCode()) return;
            applyProject(projects.saveAs(project, f.toPath()));
            log("Saved as " + project.root);
        }
        catch (Exception e) { showError("Save As failed", e); }
    }

    private void closeProject() {
        if (!saveProject(false) || !saveCurrentCode()) return;
        project = null;
        current = null;
        testList.getItems().clear();
        stepItems.clear();
        setCode("");
        refreshProjectChoices();
        stage.setTitle("Playwright Test Studio");
        titleProject.setText("No project open");
    }

    private void refreshRecent() {
        recentMenu.getItems().clear();
        for (String p : config.get().recentProjects) recentMenu.getItems().add(item(p, () -> openProjectAt(Path.of(p))));
        refreshProjectChoices();
    }

    private void refreshProjectChoices() {
        updatingProjectChoices = true;
        projectBox.getItems().setAll(config.get().recentProjects);
        projectBox.setValue(project == null ? null : project.root.toAbsolutePath().toString());
        updatingProjectChoices = false;
    }

    private boolean prepareProjectSwitch() {
        return saveProject(false) && saveCurrentCode();
    }

    private boolean saveCurrentCode() {
        if (!codeDirty || project == null || current == null) return true;
        return writeCurrentCode();
    }

    private boolean writeCurrentCode() {
        if (project == null || current == null) return false;
        try {
            Path file = project.root.resolve("generated").resolve(generator.className(current) + ".java");
            FileUtils.write(file, code.getText());
            log("Saved project code to " + file);
            return true;
        } catch (Exception e) {
            showError("Cannot save project code", e);
            return false;
        }
    }

    private void syncConfig() {
        if (project == null) return;
        project.browserConfig.browser = browserBox.getValue();
        project.browserConfig.headless = headless.isSelected();
        project.browserConfig.viewMode = viewModeValue();
    }

    private void importExcelData() {
        if (project == null) {
            info("Create or open a project before importing Excel test data.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Excel test data");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbooks (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;

        try {
            List<String> sheets = TestData.excelSheets(selected.toPath());
            if (sheets.isEmpty()) throw new IOException("The workbook does not contain any worksheets.");
            ChoiceDialog<String> dialog = new ChoiceDialog<>(sheets.get(0), sheets);
            dialog.initOwner(stage);
            dialog.setTitle("Choose Excel worksheet");
            dialog.setHeaderText("The first row of the selected worksheet is used as the test-data headers.");
            dialog.setContentText("Worksheet:");
            Optional<String> selectedSheet = dialog.showAndWait();
            if (selectedSheet.isEmpty()) return;

            List<Map<String, String>> rows = TestData.loadExcel(selected.toPath(), selectedSheet.get());
            String headers = String.join(", ", rows.get(0).keySet());
            Alert preview = new Alert(Alert.AlertType.INFORMATION);
            preview.initOwner(stage);
            preview.setTitle("Excel test data");
            preview.setHeaderText(rows.size() + " data row(s) found");
            preview.setContentText("Header names become test variables. Use them in step values as ${var:HeaderName}.\n\nHeaders: " + headers);
            preview.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (preview.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;

            Path destinationDir = project.root.resolve("testdata").resolve("excel");
            java.nio.file.Files.createDirectories(destinationDir);
            Path destination = destinationDir.resolve(selected.toPath().getFileName());
            if (!selected.toPath().toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize()))
                java.nio.file.Files.copy(selected.toPath(), destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            project.excelDataFile = project.root.relativize(destination).toString().replace('\\', '/');
            project.excelSheetName = selectedSheet.get();
            if (!saveProject(false)) return;
            refreshExcelDataStatus();
            log("Imported Excel worksheet '" + selectedSheet.get() + "' with " + rows.size() + " data row(s).");
        } catch (Exception e) {
            showError("Cannot import Excel data", e);
        }
    }

    private void clearExcelData() {
        if (project == null || project.excelDataFile == null || project.excelDataFile.isBlank()) return;
        project.excelDataFile = "";
        project.excelSheetName = "";
        if (saveProject(false)) {
            refreshExcelDataStatus();
            log("Excel data disabled for this project.");
        }
    }

    private Path excelDataPath() throws IOException {
        if (project == null || project.excelDataFile == null || project.excelDataFile.isBlank()) return null;
        Path projectRoot = project.root.toAbsolutePath().normalize();
        Path file = projectRoot.resolve(project.excelDataFile).normalize();
        if (!file.startsWith(projectRoot) || !java.nio.file.Files.isRegularFile(file))
            throw new IOException("The configured Excel data file is missing or outside the project folder.");
        return file;
    }

    private void refreshExcelDataStatus() {
        if (project == null || project.excelDataFile == null || project.excelDataFile.isBlank()) {
            excelDataStatus.setText("No Excel test data");
            return;
        }
        try {
            Path file = excelDataPath();
            List<Map<String, String>> rows = TestData.loadExcel(file, project.excelSheetName);
            excelDataStatus.setText(file.getFileName() + " / " + project.excelSheetName + " (" + rows.size() + " rows)");
        } catch (Exception e) {
            excelDataStatus.setText("Excel data unavailable");
            log("ERROR Excel data: " + e.getMessage());
        }
    }

    private boolean ensureProjectAndTest() {
        if (project == null) {
            Optional<String> name = ask("No project is open. Create one named:", "MyProject");
            if (name.isEmpty()) return false;
            List<ProjectEnvironment> defaults = List.of(new ProjectEnvironment("Development", "https://example.com"));
            Optional<List<ProjectEnvironment>> environments = EnvironmentSetupView.show(stage, defaults);
            if (environments.isEmpty()) return false;
            Optional<Path> parent = chooseProjectParentDirectory();
            if (parent.isEmpty()) return false;
            try {
                applyProject(createProject(parent.get(), name.get(), environments.get()));
            } catch (Exception e) { showError("Cannot create project", e); return false; }
        }
        if (current == null) {
            Optional<String> name = ask("New test case name", "ExampleTest");
            if (name.isEmpty()) return false;
            createTest(name.get());
        }
        return true;
    }

    // ------------------------------------------------------------------ test cases and steps

    private void newTest() {
        if (project == null) { info("Create or open a project first."); return; }
        ask("New test case name", "ExampleTest").ifPresent(this::createTest);
    }

    private void createTest(String name) {
        TestCase tc = new TestCase(name);
        testList.getItems().add(tc);
        project.tests.add(tc);
        testList.getSelectionModel().select(tc);
    }

    private void deleteTest(TestCase tc) {
        if (tc == null || project == null || !testList.getItems().contains(tc)) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(stage);
        a.setTitle("Delete test case");
        a.setHeaderText("Delete test case '" + tc.name + "'?");
        a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        if (a.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) return;
        testList.getItems().remove(tc);
        project.tests.remove(tc);
        if (pendingDebugTest == tc) clearPendingDebugger();
        if (current == tc) { current = null; stepItems.clear(); setCode(""); }
    }

    private void loadTest(TestCase tc) {
        if (!saveCurrentCode()) {
            restoringTestSelection = true;
            try { testList.getSelectionModel().select(current); }
            finally { restoringTestSelection = false; }
            return;
        }
        current = tc;
        stepItems.setAll(tc.steps);
        tc.renumber();
        table.refresh();
        Path savedCode = project.root.resolve("generated").resolve(generator.className(tc) + ".java");
        try {
            if (java.nio.file.Files.isRegularFile(savedCode)) {
                String text = migrateGeneratedPackage(java.nio.file.Files.readString(savedCode));
                String generated = generator.generate(tc, project);
                setCode(text);
                codeDirty = !text.equals(generated);
            } else {
                codeDirty = false;
                refreshCode(false);
            }
        } catch (Exception e) {
            showError("Cannot load project code", e);
            codeDirty = false;
            refreshCode(false);
        }
    }

    private static String migrateGeneratedPackage(String source) {
        return source.replaceFirst("(?m)^package generated;", "package tests;");
    }

    /** Writes the table back into the current test case and regenerates the code. */
    private void syncSteps(boolean regenerate) {
        if (current == null) return;
        current.steps.clear();
        current.steps.addAll(stepItems);
        current.renumber();
        table.refresh();
        if (project != null) {
            try {
                projects.persistPageObjects(project);
            } catch (IOException e) {
                showError("Cannot update generated page objects", e);
            }
        }
        if (regenerate) refreshCode(false);
    }

    private TestStep selected() {
        TestStep s = table.getSelectionModel().getSelectedItem();
        if (s == null) info("Select a step first.");
        return s;
    }

    private void addStep() {
        if (!ensureProjectAndTest()) return;
        StepEditorDialog.show(stage, catalog, null).ifPresent(s -> {
            int idx = table.getSelectionModel().getSelectedIndex();
            if (idx >= 0) stepItems.add(idx + 1, s); else stepItems.add(s);
            syncSteps(true);
        });
    }

    private void editStep() {
        TestStep s = selected();
        if (s == null) return;
        StepEditorDialog.show(stage, catalog, s).ifPresent(n -> { stepItems.set(stepItems.indexOf(s), n); syncSteps(true); });
    }

    private void deleteStep() {
        TestStep s = selected();
        if (s == null) return;
        stepItems.remove(s);
        syncSteps(true);
    }

    private void duplicateStep() {
        TestStep s = selected();
        if (s == null) return;
        stepItems.add(stepItems.indexOf(s) + 1, s.copy());
        syncSteps(true);
    }

    private void move(int delta) {
        int i = table.getSelectionModel().getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= stepItems.size()) return;
        TestStep s = stepItems.remove(i);
        stepItems.add(j, s);
        table.getSelectionModel().select(j);
        syncSteps(true);
    }

    private void toggleStep() {
        TestStep s = selected();
        if (s == null) return;
        s.enabled = !s.enabled;
        syncSteps(true);
    }

    private void toggleBreakpoint() {
        TestStep s = selected();
        if (s == null) return;
        s.breakpoint = !s.breakpoint;
        syncSteps(true);
    }

    private void runSelectedStep() {
        TestStep s = selected();
        if (s == null) return;
        if (!browser.isRunning()) { info("Start the browser first (START BROWSER)."); return; }
        s.status = "Running";
        table.refresh();
        browser.runStep(catalog, s).whenComplete((v, e) -> Platform.runLater(() -> {
            s.status = e == null ? "PASSED" : "FAILED";
            table.refresh();
            if (e != null) showError("Step failed", e);
        }));
    }

    // ------------------------------------------------------------------ recording

    private String storeSecret(String value) {
        try {
            String key = (current == null ? "test" : FileUtils.sanitize(current.name).toLowerCase()) + "_password_" + (++secretCounter);
            TestData.putSecret(project.root, key, value);
            return "${secret:" + key + "}";
        } catch (Exception e) {
            log("Could not store secret: " + e.getMessage());
            return "";
        }
    }

    private void addRecorded(TestStep s) {
        if (current == null) return;
        s.status = "Recorded";
        stepItems.add(s);
        syncSteps(true);
        table.scrollTo(stepItems.size() - 1);
    }

    private void replaceRecorded(int count, TestStep s) {
        if (current == null) return;
        for (int i = 0; i < count && !stepItems.isEmpty(); i++) stepItems.remove(stepItems.size() - 1);
        addRecorded(s);
    }

    private static String normalizeUrl(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return null;
        if (!t.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) t = "https://" + t;
        try {
            URI u = new URI(t);
            if (u.getHost() == null || !List.of("http", "https").contains(u.getScheme().toLowerCase())) return null;
            return t;
        } catch (Exception e) { return null; }
    }

    private void setRecordingUi(boolean on) {
        startRec.setDisable(on);
        stopRec.setDisable(!on);
    }

    private void startRecording() {
        if (!ensureProjectAndTest()) return;
        String url = normalizeUrl(urlField.getText());
        if (url == null) { info("Enter a valid http(s) URL, e.g. https://example.com"); return; }
        recorder.setRecording(true);
        setRecordingUi(true);
        log("Recording started");
        if (!browser.isRunning()) launchBrowser(true);
        else if (stepItems.isEmpty()) browser.currentUrl().thenAccept(u -> Platform.runLater(() -> addRecorded(new TestStep("NAVIGATE").with("url", u))));
    }

    private void stopRecording() {
        recorder.setRecording(false);
        setRecordingUi(false);
        saveProject(false);
        refreshCode(false);
        log("Recording stopped: " + stepItems.size() + " step(s)");
    }

    private String viewModeValue() {
        String v = viewBox.getValue();
        return v == null ? ViewOptions.EMBEDDED : v.startsWith("Side") ? ViewOptions.SIDE_BY_SIDE : v.startsWith("Separate") ? ViewOptions.SEPARATE : ViewOptions.EMBEDDED;
    }

    private void setViewBox(String mode) {
        viewBox.setValue(ViewOptions.SIDE_BY_SIDE.equals(mode) ? "Side by side" : ViewOptions.SEPARATE.equals(mode) ? "Separate window" : "Embedded (one window)");
    }

    private void launchBrowser(boolean recording) {
        String url = normalizeUrl(urlField.getText());
        if (url == null) { info("Enter a valid http(s) URL, e.g. https://example.com"); return; }
        BrowserConfiguration cfg = project != null ? project.browserConfig.copy() : new BrowserConfiguration();
        cfg.browser = browserBox.getValue();
        cfg.headless = headless.isSelected();
        syncConfig();

        ViewOptions view = new ViewOptions();
        view.mode = viewModeValue();
        boolean chromium = cfg.engine().equals("chromium");
        if (!chromium && !ViewOptions.SEPARATE.equals(view.mode)) {
            log("Embedded / side-by-side views need Chromium, Chrome or Edge. Using a separate window for " + cfg.browser + ".");
            view.mode = ViewOptions.SEPARATE;
        }
        if (ViewOptions.EMBEDDED.equals(view.mode)) {
            view.frameSink = livePanel::showFrame;
            if (cfg.headless) log("Embedded view always runs the browser without its own window.");
        } else if (ViewOptions.SIDE_BY_SIDE.equals(view.mode)) {
            Rectangle2D b = WindowSizing.boundsFor(stage);
            stage.setMaximized(false);
            stage.setX(b.getMinX());
            stage.setY(b.getMinY());
            stage.setWidth(b.getWidth() / 2);
            stage.setHeight(b.getHeight());
            view.x = (int) (b.getMinX() + b.getWidth() / 2);
            view.y = (int) b.getMinY();
            view.width = (int) (b.getWidth() / 2);
            view.height = (int) b.getHeight();
        }
        Path videoDir = project == null ? null : project.root.resolve("videos");
        log("Launching " + cfg.browser + " (" + view.mode + ") at " + url);
        browser.launch(url, cfg, recorder, videoDir, view).whenComplete((live, e) -> Platform.runLater(() -> {
            if (e != null) { recorder.setRecording(false); setRecordingUi(false); livePanel.clear(); showError("Browser launch failed", e); }
            else {
                if (live != null) livePanel.attach(live, cfg.viewportWidth, cfg.viewportHeight);
                log("Browser started");
            }
        }));
    }

    /** Back / Forward / Reload buttons of the embedded view. Back and Forward are recorded as steps. */
    private void liveNav(String actionId) {
        if (!browser.isRunning()) return;
        livePanel.cancelInspectMode();
        recorder.touch();
        TestStep s = new TestStep(actionId);
        browser.runStep(catalog, s).whenComplete((v, e) -> Platform.runLater(() -> {
            if (e != null) log("Navigation failed: " + e.getMessage());
            else if (recorder.isRecording() && !actionId.equals("RELOAD")) addRecorded(new TestStep(actionId));
        }));
    }

    private void applyInspectedXPath(String xpath, String frameSelector) {
        TestStep step = table.getSelectionModel().getSelectedItem();
        ActionDefinition definition = step == null ? null : catalog.get(step.action);
        if (step == null || definition == null || !definition.needsLocator) {
            log("Inspected XPath is ready to copy. Select a test step that uses a locator to apply it.");
            return;
        }
        step.locator = LocatorInfo.xpath(xpath);
        step.locator.frameSelector = frameSelector;
        syncSteps(true);
        log("Applied inspected XPath to selected step: " + xpath);
    }

    private void stopBrowser() {
        recorder.setRecording(false);
        setRecordingUi(false);
        livePanel.clear();
        browser.stop().whenComplete((v, e) -> log("Browser stopped"));
    }

    // ------------------------------------------------------------------ code

    private void setCode(String text) {
        programmatic = true;
        code.setText(text);
        programmatic = false;
        codeDirty = false;
    }

    private void refreshCode(boolean ask) {
        if (current == null) { setCode(""); return; }
        if (codeDirty) {
            if (!ask) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Regenerating the code from the recorded steps will overwrite your manual code changes. Continue?");
            a.initOwner(stage);
            if (a.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        }
        setCode(generator.generate(current, project));
    }

    private void saveCode() {
        if (project == null || current == null) return;
        writeCurrentCode();
    }

    private void findNext(String q) {
        if (q == null || q.isEmpty()) return;
        String t = code.getText();
        int idx = t.indexOf(q, code.getCaretPosition());
        if (idx < 0) idx = t.indexOf(q);
        if (idx >= 0) { code.requestFocus(); code.selectRange(idx, idx + q.length()); }
    }

    // ------------------------------------------------------------------ execution

    private void runTests(boolean all) {
        runTests(all, false);
    }

    private void prepareOrRunDebugger() {
        if (pendingDebugTest != null && pendingDebugTest == current) {
            runTests(false, true);
            return;
        }
        if (project == null || project.tests.isEmpty()) {
            info("Open a project with at least one test case before starting the debugger.");
            return;
        }

        TestCase initial = current != null && project.tests.contains(current) ? current : project.tests.get(0);
        ChoiceDialog<TestCase> dialog = new ChoiceDialog<>(initial, project.tests);
        dialog.initOwner(stage);
        dialog.setTitle("Choose test to debug");
        dialog.setHeaderText("Select the test whose steps you want to debug.");
        dialog.setContentText("Test case:");
        Optional<TestCase> selectedTest = dialog.showAndWait();
        if (selectedTest.isEmpty()) return;

        TestCase test = selectedTest.get();
        testList.getSelectionModel().select(test);
        if (current != test) {
            info("Could not load the selected test. Save or resolve the current code edits, then try again.");
            return;
        }

        pendingDebugTest = test;
        setDebuggerButtonReady(true);
        result.setText("DEBUGGER: Mark breakpoints, then start debugging");
        setResultStyle("result-idle");
        log("Selected test for debugging: " + test.name
                + ". Toggle Break on the desired steps, then press START DEBUGGING.");
        showStepsWindow();
        stepsWindow.setTitle("Debugger Steps - " + test.name);
    }

    private void setDebuggerButtonReady(boolean ready) {
        debugBtn.setVisible(!ready);
        debugBtn.setManaged(!ready);
        if (debugStepsStartBtn != null) {
            debugStepsStartBtn.setVisible(ready);
            debugStepsStartBtn.setManaged(ready);
        }
        if (!ready && stepsWindow != null) stepsWindow.setTitle("Test Steps");
    }

    private void clearPendingDebugger() {
        pendingDebugTest = null;
        setDebuggerButtonReady(false);
    }

    private void runTests(boolean all, boolean debugMode) {
        if (!ensureProjectAndTest()) return;
        if (debugMode && codeDirty) {
            info("Save or discard manual code edits before debugging; the debugger runs the steps in the Test Steps editor.");
            return;
        }
        if (project.environments == null || project.environments.isEmpty()) {
            showError("No project environments", new IllegalStateException("Add an environment with a base URL before running tests."));
            return;
        }
        ChoiceDialog<ProjectEnvironment> environmentDialog = new ChoiceDialog<>(project.environments.get(0), project.environments);
        environmentDialog.initOwner(stage);
        environmentDialog.setTitle("Select test environment");
        environmentDialog.setHeaderText("Tests run against the selected environment URL.");
        environmentDialog.setContentText("Environment:");
        Optional<ProjectEnvironment> selectedEnvironment = environmentDialog.showAndWait();
        if (selectedEnvironment.isEmpty()) return;
        try {
            TestData.validateEnvironmentUrl(selectedEnvironment.get().url);
        } catch (IllegalArgumentException e) {
            showError("Invalid test environment", e);
            return;
        }
        if (!saveProject(false)) return;
        Path excelFile;
        try {
            excelFile = excelDataPath();
            if (excelFile != null) {
                List<Map<String, String>> rows = TestData.loadExcel(excelFile, project.excelSheetName);
                log("Using Excel test data: " + rows.size() + " row(s), headers " + String.join(", ", rows.get(0).keySet()));
            }
        } catch (Exception e) {
            showError("Excel test data is invalid", e);
            return;
        }
        List<TestCase> tests = all ? List.copyOf(project.tests) : List.of(current);
        Map<String, String> overrides = (!all && codeDirty) ? Map.of(generator.className(current), code.getText()) : Map.of();
        for (TestCase t : tests) if (t.steps.isEmpty()) { info("Test '" + t.name + "' has no steps."); return; }
        result.setText("RESULT: RUNNING...");
        setResultStyle("result-running");
        runBtn.setDisable(true);
        debugBtn.setDisable(true);
        debugForwardBtn.setDisable(true);
        debugContinueBtn.setDisable(true);
        debugStopBtn.setDisable(true);
        console.clear();
        ProjectEnvironment environment = selectedEnvironment.get();
        log("Running against environment " + environment.name + " (" + environment.url + ")");
        if (debugMode) {
            debugger.setPauseListener(pause -> Platform.runLater(() -> {
                if (pause.stepNumber() > 0 && pause.stepNumber() <= stepItems.size()) {
                    table.getSelectionModel().select(pause.stepNumber() - 1);
                    table.scrollTo(pause.stepNumber() - 1);
                    stepItems.get(pause.stepNumber() - 1).status = "PAUSED";
                    table.refresh();
                }
                result.setText("DEBUGGER: PAUSED AT STEP " + pause.stepNumber());
                debugForwardBtn.setDisable(false);
                debugContinueBtn.setDisable(false);
                debugStopBtn.setDisable(false);
                log("Debugger paused before step " + pause.stepNumber() + ": " + pause.description());
            }));
            clearPendingDebugger();
            if (stepsWindow != null) stepsWindow.hide();
        }
        execution.run(project, tests, overrides, all, environment, debugMode, debugMode ? debugger : null, this::log)
                .whenComplete((r, e) -> Platform.runLater(() -> {
            runBtn.setDisable(false);
            debugBtn.setDisable(false);
            debugForwardBtn.setDisable(true);
            debugContinueBtn.setDisable(true);
            debugStopBtn.setDisable(true);
            debugger.setPauseListener(null);
            if (debugMode && debugger.isStopped()) {
                result.setText("DEBUGGER: STOPPED");
                setResultStyle("result-idle");
                log("Debugger stopped.");
            } else if (e != null) {
                result.setText("RESULT: ERROR");
                setResultStyle("result-failed");
                showError("Execution failed", e);
            } else showResult(r);
        }));
    }

    private void forwardDebugger() {
        try {
            debugForwardBtn.setDisable(true);
            debugContinueBtn.setDisable(true);
            debugger.stepForward();
        } catch (IOException e) {
            showError("Could not step the debugger", e);
        }
    }

    private void continueDebugger() {
        try {
            debugForwardBtn.setDisable(true);
            debugContinueBtn.setDisable(true);
            debugger.continueExecution();
        } catch (IOException e) {
            showError("Could not continue the debugger", e);
        }
    }

    private void stopDebugger() {
        debugForwardBtn.setDisable(true);
        debugContinueBtn.setDisable(true);
        debugStopBtn.setDisable(true);
        debugger.stop();
    }

    private void showResult(ExecutionResult r) {
        lastReport = r.indexHtml;
        openReport.setDisable(lastReport == null);
        boolean ok = r.allPassed();
        result.setText("RESULT: " + (ok ? "PASSED" : "FAILED") + "  (" + r.results.size() + " test(s), " + r.durationMs + " ms)");
        setResultStyle(ok ? "result-passed" : "result-failed");
        for (TestResult tr : r.results) {
            if (current != null && tr.testName.startsWith(generator.className(current)))
                for (TestResult.StepResult sr : tr.steps)
                    if (sr.number >= 1 && sr.number <= stepItems.size()) stepItems.get(sr.number - 1).status = sr.status;
            if ("FAILED".equals(tr.status)) log("TEST FAILED: " + tr.testName + " | step " + tr.failedStep + " | " + tr.exception + " | url " + tr.url + " | screenshot " + tr.screenshot);
        }
        table.refresh();
    }

    private void setResultStyle(String style) {
        result.getStyleClass().removeAll("result-idle", "result-running", "result-passed", "result-failed");
        result.getStyleClass().add(style);
    }

    // ------------------------------------------------------------------ tools

    private void playwrightCli(List<String> args) {
        List<String> cmd = new java.util.ArrayList<>(List.of(ProcessRunner.javaBin(), "-cp", ProcessRunner.currentClasspath(), "com.microsoft.playwright.CLI"));
        cmd.addAll(args);
        log("Running: playwright " + String.join(" ", args));
        bg.submit(() -> {
            try {
                int code = ProcessRunner.run(cmd, null, this::log);
                log("playwright " + args.get(0) + " finished with exit code " + code);
            } catch (Exception e) { Platform.runLater(() -> showError("Playwright CLI failed", e)); }
        });
    }

    private void openCodegen() {
        String url = normalizeUrl(urlField.getText());
        playwrightCli(url == null ? List.of("codegen") : List.of("codegen", url));
    }

    private void openTrace() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select trace.zip");
        File f = fc.showOpenDialog(stage);
        if (f != null) playwrightCli(List.of("show-trace", f.getAbsolutePath()));
    }

    private void openSettings() {
        if (project == null) { info("Open a project first."); return; }
        syncConfig();
        if (SettingsView.show(stage, project.browserConfig)) {
            browserBox.setValue(project.browserConfig.browser);
            headless.setSelected(project.browserConfig.headless);
            saveProject(false);
        }
    }

    private void openHistory() {
        if (project == null) { info("Open a project first."); return; }
        HistoryView.show(stage, project.root, reports, hosts);
    }
}
