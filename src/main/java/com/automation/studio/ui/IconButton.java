package com.automation.studio.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public final class IconButton {
    private IconButton() {}

    public static Button create(String label, String icon, Runnable action) {
        Button button = new Button();
        configure(button, label, icon);
        button.setOnAction(event -> action.run());
        return button;
    }

    public static void configure(Button button, String label, String icon) {
        Label glyph = new Label(icon);
        glyph.getStyleClass().add("icon-button-glyph");
        button.setText(label);
        button.setGraphic(glyph);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(6);
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().add("icon-button");

        // Semantic tagging for Google Material Design styling
        String lower = label.toLowerCase();
        if (lower.contains("run test") || lower.contains("run step")) {
            button.getStyleClass().add("btn-action-run");
            glyph.getStyleClass().add("glyph-run");
        } else if (lower.contains("start recording")) {
            button.getStyleClass().add("btn-action-record");
            glyph.getStyleClass().add("glyph-record");
        } else if (lower.contains("stop recording")) {
            button.getStyleClass().add("btn-action-stop-rec");
            glyph.getStyleClass().add("glyph-stop-rec");
        } else if (lower.contains("start browser")) {
            button.getStyleClass().add("btn-action-browser");
            glyph.getStyleClass().add("glyph-browser");
        } else if (lower.contains("stop browser")) {
            button.getStyleClass().add("btn-action-stop-browser");
            glyph.getStyleClass().add("glyph-stop-browser");
        } else if (lower.contains("excel")) {
            button.getStyleClass().add("btn-action-excel");
            glyph.getStyleClass().add("glyph-excel");
        } else if (lower.contains("report")) {
            button.getStyleClass().add("btn-action-report");
            glyph.getStyleClass().add("glyph-report");
        } else if (lower.contains("steps") || lower.contains("step")) {
            glyph.getStyleClass().add("glyph-steps");
        } else if (lower.contains("java") || lower.contains("code")) {
            glyph.getStyleClass().add("glyph-code");
        } else if (lower.contains("console")) {
            glyph.getStyleClass().add("glyph-console");
        } else if (lower.contains("back") || lower.contains("forward") || lower.contains("reload")) {
            button.getStyleClass().add("browser-nav-btn");
            glyph.getStyleClass().add("glyph-nav");
        }
    }
}

