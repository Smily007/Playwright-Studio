package com.automation.studio.ui;

import com.automation.studio.browser.BrowserConfiguration;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Browser / execution settings dialog. Edits the given configuration in place when OK is pressed. */
public final class SettingsView {
    public record AppearancePreferences(String theme, String textSize) {}

    private SettingsView() {}

    public static AppearancePreferences showAppearance(Window owner, String currentTheme, String currentTextSize) {
        Dialog<AppearancePreferences> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Appearance and accessibility");
        StudioTheme.install(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().addAll("Light", "Dark", "High contrast");
        theme.setValue("dark".equals(currentTheme) ? "Dark"
                : "high-contrast".equals(currentTheme) ? "High contrast" : "Light");
        theme.setAccessibleText("Application color theme");

        ComboBox<String> textSize = new ComboBox<>();
        textSize.getItems().addAll("Default", "Large", "Larger");
        textSize.setValue("large".equals(currentTextSize) ? "Large"
                : "larger".equals(currentTextSize) ? "Larger" : "Default");
        textSize.setAccessibleText("Application text size");

        Label themeHelp = new Label("Choose a color theme. High contrast improves visibility.");
        Label textHelp = new Label("Increase text size throughout the application.");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Theme"), theme);
        grid.addRow(1, new Label("Text size"), textSize);
        grid.add(themeHelp, 1, 2);
        grid.add(textHelp, 1, 3);
        GridPane.setHgrow(theme, Priority.ALWAYS);
        GridPane.setHgrow(textSize, Priority.ALWAYS);
        theme.setMaxWidth(Double.MAX_VALUE);
        textSize.setMaxWidth(Double.MAX_VALUE);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new AppearancePreferences(theme.getValue(), textSize.getValue())
                : null);
        return dialog.showAndWait().orElse(null);
    }

    public static boolean show(Window owner, BrowserConfiguration c) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Settings");
        StudioTheme.install(dlg.getDialogPane());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> browser = new ComboBox<>();
        browser.getItems().addAll(BrowserConfiguration.BROWSERS);
        browser.setValue(c.browser);
        CheckBox headless = new CheckBox();
        headless.setSelected(c.headless);
        TextField exe = new TextField(c.executablePath);
        TextField w = new TextField(String.valueOf(c.viewportWidth));
        TextField h = new TextField(String.valueOf(c.viewportHeight));
        TextField timeout = new TextField(String.valueOf(c.timeoutMs));
        TextField slow = new TextField(String.valueOf(c.slowMoMs));
        CheckBox video = new CheckBox();
        video.setSelected(c.recordVideo);
        ComboBox<String> shot = new ComboBox<>();
        shot.getItems().addAll("off", "failure", "every-step");
        shot.setValue(c.screenshotMode);
        ComboBox<String> trace = new ComboBox<>();
        trace.getItems().addAll("off", "on-failure", "on");
        trace.setValue(c.trace);
        TextField retry = new TextField(String.valueOf(c.retryCount));
        TextField threads = new TextField(String.valueOf(c.threadCount));

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(12));
        String[] labels = {"Browser", "Headless", "Executable path", "Viewport width", "Viewport height", "Timeout (ms)", "Slow motion (ms)",
                "Record video", "Screenshots", "Trace", "Retry failed tests", "Parallel threads"};
        javafx.scene.Node[] nodes = {browser, headless, exe, w, h, timeout, slow, video, shot, trace, retry, threads};
        for (int i = 0; i < labels.length; i++) {
            Label label = new Label(labels[i]);
            label.setLabelFor(nodes[i]);
            nodes[i].setAccessibleText(labels[i]);
            g.add(label, 0, i);
            g.add(nodes[i], 1, i);
        }
        GridPane.setHgrow(exe, Priority.ALWAYS);
        for (javafx.scene.Node node : nodes)
            if (node instanceof Control control) control.setMaxWidth(Double.MAX_VALUE);
        var size = WindowSizing.fit(owner, 560, 600);
        ScrollPane scroll = new ScrollPane(g);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(size.getHeight() * 0.75);
        dlg.getDialogPane().setContent(scroll);
        dlg.getDialogPane().setPrefWidth(size.getWidth());

        dlg.setResultConverter(b -> b == ButtonType.OK);
        if (!dlg.showAndWait().orElse(false)) return false;
        c.browser = browser.getValue();
        c.headless = headless.isSelected();
        c.executablePath = exe.getText().trim();
        c.viewportWidth = num(w.getText(), c.viewportWidth);
        c.viewportHeight = num(h.getText(), c.viewportHeight);
        c.timeoutMs = num(timeout.getText(), c.timeoutMs);
        c.slowMoMs = num(slow.getText(), c.slowMoMs);
        c.recordVideo = video.isSelected();
        c.screenshotMode = shot.getValue();
        c.trace = trace.getValue();
        c.retryCount = num(retry.getText(), c.retryCount);
        c.threadCount = Math.max(1, num(threads.getText(), c.threadCount));
        return true;
    }

    private static int num(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
