package com.automation.studio.ui;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.actions.ActionDefinition;
import com.automation.studio.actions.ParamDef;
import com.automation.studio.locator.LocatorInfo;
import com.automation.studio.model.TestStep;
import javafx.geometry.Dimension2D;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Add/edit step dialog: searchable action palette on the left, a form rendered dynamically from the
 * selected ActionDefinition on the right, and a live Java code preview at the bottom.
 */
public final class StepEditorDialog {
    private final ActionCatalog catalog;
    private final TestStep existing;
    private ActionDefinition def;

    private final ListView<ActionDefinition> palette = new ListView<>();
    private final VBox form = new VBox(8);
    private final TextArea preview = new TextArea();
    private final ComboBox<LocatorInfo.Type> locType = new ComboBox<>();
    private final TextField locValue = new TextField();
    private final TextField locRole = new TextField();
    private final TextField locNth = new TextField();
    private final TextField locFrame = new TextField();
    private final CheckBox exact = new CheckBox("Exact match");
    private final TextField description = new TextField();
    private final TextField timeout = new TextField();
    private final CheckBox cont = new CheckBox("Continue on failure");
    private final CheckBox enabled = new CheckBox("Enabled");
    private final CheckBox breakpoint = new CheckBox("Breakpoint");
    private final Map<String, Control> paramControls = new LinkedHashMap<>();
    private final Map<String, ParamDef> paramDefinitions = new LinkedHashMap<>();

    private StepEditorDialog(ActionCatalog catalog, TestStep existing) {
        this.catalog = catalog;
        this.existing = existing;
    }

    public static Optional<TestStep> show(Window owner, ActionCatalog catalog, TestStep existing) {
        return new StepEditorDialog(catalog, existing).open(owner);
    }

    private Optional<TestStep> open(Window owner) {
        Dialog<TestStep> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle(existing == null ? "Add step" : "Edit step");
        dlg.setResizable(true);
        StudioTheme.install(dlg.getDialogPane());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField search = new TextField();
        search.setPromptText("Search actions (e.g. click, assert, route)");
        search.setAccessibleText("Search actions by name or category");
        Label paletteLabel = new Label("Action palette");
        paletteLabel.setLabelFor(search);
        palette.getItems().setAll(catalog.search(""));
        palette.setAccessibleText("Available actions");
        palette.setPlaceholder(new Label("No matching actions."));
        palette.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ActionDefinition item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label name = new Label(item.displayName);
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: #202124; -fx-font-size: 0.95em;");
                    Label cat = new Label(item.category);
                    cat.setStyle("-fx-font-size: 0.78em; -fx-text-fill: #1a73e8; -fx-background-color: #e8f0fe; -fx-background-radius: 10; -fx-padding: 1 7 1 7; -fx-font-weight: bold;");
                    HBox row = new HBox(8, name, cat);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        Dimension2D size = WindowSizing.fit(owner, 980, 640);
        palette.setPrefWidth(Math.min(300, size.getWidth() * 0.3));
        search.textProperty().addListener((o, a, b) -> palette.getItems().setAll(catalog.search(b)));
        VBox left = new VBox(6, paletteLabel, search, palette);
        VBox.setVgrow(palette, Priority.ALWAYS);

        locType.getItems().addAll(LocatorInfo.Type.values());
        locType.setValue(LocatorInfo.Type.ROLE);
        locType.valueProperty().addListener((o, a, b) -> updateLocatorPrompt(b));
        updateLocatorPrompt(locType.getValue());
        enabled.setSelected(true);
        description.setPromptText("Description (optional)");
        timeout.setPromptText("Timeout ms (optional)");
        preview.setEditable(false);
        preview.setAccessibleText("Generated code preview for the selected action");
        preview.setPrefRowCount(5);
        preview.getStyleClass().add("code-editor");
        Label previewLabel = new Label("Code preview");
        previewLabel.setLabelFor(preview);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(420, size.getHeight() * 0.6));
        VBox right = new VBox(6, scroll, previewLabel, preview);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        left.setMinSize(0, 0);
        right.setMinSize(0, 0);
        SplitPane root = new SplitPane(left, right);
        root.setMinSize(0, 0);
        root.setPrefSize(size.getWidth(), size.getHeight());
        root.setOrientation(size.getWidth() < 780 ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        root.setDividerPositions(0.32);
        root.widthProperty().addListener((o, oldWidth, newWidth) -> {
            Orientation orientation = newWidth.doubleValue() < 780 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            if (root.getOrientation() != orientation) {
                root.setOrientation(orientation);
                root.setDividerPositions(0.32);
            }
        });
        dlg.getDialogPane().setContent(root);

        palette.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> { if (b != null) select(b, null); });
        if (existing != null) {
            ActionDefinition d = catalog.get(existing.action);
            if (d != null) { palette.getItems().setAll(catalog.search("")); palette.getSelectionModel().select(d); select(d, existing); }
        }

        Node ok = dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String problem = validate();
            if (problem != null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, problem);
                alert.initOwner(dlg.getDialogPane().getScene().getWindow());
                alert.setTitle("Check step settings");
                alert.setHeaderText("Some step details need attention");
                StudioTheme.install(alert.getDialogPane());
                alert.showAndWait();
                ev.consume();
            }
        });
        dlg.setResultConverter(b -> b == ButtonType.OK && def != null ? build() : null);
        return dlg.showAndWait();
    }

    private String validate() {
        if (def == null) return "Choose an action from the palette.";
        if (def.needsLocator) {
            if (locType.getValue() == LocatorInfo.Type.ROLE && !LocatorInfo.validRole(locRole.getText()))
                return "ROLE locators need a valid ARIA role, for example: button, link, textbox, heading.";
            if (locType.getValue() != LocatorInfo.Type.ROLE && locValue.getText().isBlank()) return "Enter a locator value.";
            if (!locNth.getText().isBlank()) {
                try { Integer.parseInt(locNth.getText().trim()); }
                catch (NumberFormatException e) { return "Locator index must be a whole number."; }
            }
        }
        if (!timeout.getText().isBlank()) {
            try {
                if (Long.parseLong(timeout.getText().trim()) <= 0) return "Step timeout must be a positive whole number.";
            } catch (NumberFormatException e) {
                return "Step timeout must be a positive whole number.";
            }
        }
        for (var entry : paramDefinitions.entrySet()) {
            ParamDef parameter = entry.getValue();
            Control control = paramControls.get(entry.getKey());
            String raw = value(control);
            if (raw.isBlank() || raw.contains("${")) continue;
            try {
                switch (parameter.kind) {
                    case INT -> Integer.parseInt(raw);
                    case LONG -> Long.parseLong(raw);
                    case DOUBLE -> {
                        if (!Double.isFinite(Double.parseDouble(raw)))
                            return "'" + parameter.name + "' must be a finite number.";
                    }
                    default -> { }
                }
            } catch (NumberFormatException e) {
                return "'" + parameter.name + "' must be a valid "
                        + (parameter.kind == ParamDef.Kind.INT || parameter.kind == ParamDef.Kind.LONG
                        ? "whole number." : "number.");
            }
        }
        return null;
    }

    private void select(ActionDefinition d, TestStep from) {
        def = d;
        paramControls.clear();
        paramDefinitions.clear();
        form.getChildren().clear();
        Label title = new Label(d.displayName + "  (" + d.category + ")");
        title.getStyleClass().add("step-editor-title");
        form.getChildren().add(title);

        if (d.needsLocator) {
            GridPane g = new GridPane();
            g.setHgap(6);
            g.setVgap(6);
            locValue.setPromptText("Locator value");
            locRole.setPromptText("ARIA role, e.g. button");
            locNth.setPromptText("Index: 0 = first, -1 = last (optional)");
            locFrame.setPromptText("iframe CSS selector (optional)");
            locType.setAccessibleText("Locator strategy");
            locRole.setAccessibleText("ARIA role");
            locValue.setAccessibleText("Locator value");
            locNth.setAccessibleText("Locator index");
            locFrame.setAccessibleText("Locator frame selector");
            g.addRow(0, labelFor("Locator type", locType), locType);
            g.addRow(1, labelFor("Role", locRole), locRole);
            g.addRow(2, labelFor("Value", locValue), locValue);
            g.addRow(3, labelFor("Index", locNth), locNth);
            g.addRow(4, labelFor("Frame", locFrame), locFrame);
            g.add(exact, 1, 5);
            GridPane.setHgrow(locValue, Priority.ALWAYS);
            TitledPane tp = new TitledPane("Locator", g);
            tp.setCollapsible(false);
            form.getChildren().add(tp);
            for (Control c : new Control[]{locType, locValue, locRole, locNth, locFrame, exact}) listen(c);
        }

        GridPane main = new GridPane();
        main.setHgap(6);
        main.setVgap(6);
        GridPane opts = new GridPane();
        opts.setHgap(6);
        opts.setVgap(6);
        int mr = 0, or = 0;
        for (ParamDef p : d.params) {
            Control c = controlFor(p);
            paramControls.put(p.name, c);
            paramDefinitions.put(p.name, p);
            listen(c);
            Label parameterLabel = labelFor(p.name, c);
            c.setAccessibleText(p.name);
            if (p.option || p.meta) opts.addRow(or++, parameterLabel, c);
            else main.addRow(mr++, parameterLabel, c);
        }
        if (mr > 0) { TitledPane tp = new TitledPane("Parameters", main); tp.setCollapsible(false); form.getChildren().add(tp); }
        if (or > 0) { TitledPane tp = new TitledPane("Options", opts); tp.setExpanded(false); form.getChildren().add(tp); }

        GridPane common = new GridPane();
        common.setHgap(6);
        common.setVgap(6);
        common.addRow(0, labelFor("Description", description), description);
        common.addRow(1, labelFor("Step timeout (ms)", timeout), timeout);
        common.add(cont, 1, 2);
        common.add(enabled, 1, 3);
        common.add(breakpoint, 1, 4);
        GridPane.setHgrow(description, Priority.ALWAYS);
        form.getChildren().add(new TitledPane("Step settings", common));
        for (Control c : new Control[]{description, timeout, cont, enabled, breakpoint}) listen(c);

        if (from != null) fill(from);
        updatePreview();
    }

    private Control controlFor(ParamDef p) {
        if (p.kind == ParamDef.Kind.BOOLEAN) {
            ComboBox<String> cb = new ComboBox<>();
            cb.getItems().addAll("", "true", "false");
            return cb;
        }
        if (p.kind == ParamDef.Kind.ENUM) {
            ComboBox<String> cb = new ComboBox<>();
            cb.getItems().add("");
            cb.getItems().addAll(p.enumValues);
            return cb;
        }
        TextField tf = new TextField();
        tf.setPromptText(p.kind == ParamDef.Kind.STRING ? "text (supports ${env:X} ${secret:k} ${var:v})" : "number");
        return tf;
    }

    private static Label labelFor(String text, Control control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        return label;
    }

    private void listen(Control c) {
        if (c instanceof TextField tf) tf.textProperty().addListener((o, a, b) -> updatePreview());
        else if (c instanceof ComboBox<?> cb) cb.valueProperty().addListener((o, a, b) -> updatePreview());
        else if (c instanceof CheckBox ck) ck.selectedProperty().addListener((o, a, b) -> updatePreview());
    }

    private void updateLocatorPrompt(LocatorInfo.Type type) {
        if (type == LocatorInfo.Type.XPATH) locValue.setPromptText("//button[@type='submit']");
        else if (type == LocatorInfo.Type.CSS) locValue.setPromptText("CSS selector, e.g. #submit");
        else if (type == LocatorInfo.Type.ROLE) locValue.setPromptText("Accessible name (ARIA role is entered below)");
        else locValue.setPromptText("Locator value");
    }

    private void fill(TestStep s) {
        if (s.locator != null) {
            locType.setValue(s.locator.locatorType);
            locValue.setText(s.locator.locatorValue);
            locRole.setText(s.locator.role);
            locNth.setText(s.locator.nth == null ? "" : String.valueOf(s.locator.nth));
            locFrame.setText(s.locator.frameSelector);
            exact.setSelected(s.locator.exact);
        }
        for (var e : s.params.entrySet()) {
            Control c = paramControls.get(e.getKey());
            if (c instanceof TextField tf) tf.setText(e.getValue());
            else if (c instanceof ComboBox<?> cb) { @SuppressWarnings("unchecked") ComboBox<String> sc = (ComboBox<String>) cb; sc.setValue(e.getValue()); }
        }
        description.setText(s.description);
        timeout.setText(s.timeoutMs == null ? "" : String.valueOf(s.timeoutMs));
        cont.setSelected(s.continueOnFailure);
        enabled.setSelected(s.enabled);
        breakpoint.setSelected(s.breakpoint);
    }

    private static String value(Control c) {
        if (c instanceof TextField tf) return tf.getText().trim();
        if (c instanceof ComboBox<?> cb) return cb.getValue() == null ? "" : String.valueOf(cb.getValue());
        return "";
    }

    private TestStep build() {
        TestStep s = new TestStep(def.id);
        if (existing != null) { s.timestamp = existing.timestamp; s.screenshotPath = existing.screenshotPath; s.stepNumber = existing.stepNumber; }
        if (def.needsLocator) {
            LocatorInfo l = new LocatorInfo();
            l.locatorType = locType.getValue();
            l.locatorValue = locValue.getText().trim();
            l.role = locRole.getText().trim().toLowerCase();
            l.exact = exact.isSelected();
            l.frameSelector = locFrame.getText().trim();
            if (!locNth.getText().isBlank()) l.nth = Integer.parseInt(locNth.getText().trim());
            l.confidence = existing != null && existing.locator != null ? existing.locator.confidence : 1.0;
            s.locator = l;
        }
        for (var e : paramControls.entrySet()) {
            String v = value(e.getValue());
            if (!v.isBlank()) s.params.put(e.getKey(), v);
        }
        s.description = description.getText().trim();
        if (!timeout.getText().isBlank()) s.timeoutMs = Long.parseLong(timeout.getText().trim());
        s.continueOnFailure = cont.isSelected();
        s.enabled = enabled.isSelected();
        s.breakpoint = breakpoint.isSelected();
        return s;
    }

    private void updatePreview() {
        if (def == null) return;
        try {
            preview.setText(def.generate(build()));
        } catch (Exception e) {
            preview.setText("// " + e.getMessage());
        }
    }
}
