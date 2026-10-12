package com.automation.studio.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.Parent;

import java.util.Map;
import java.util.WeakHashMap;

public final class StudioTheme {
    private static final String STYLESHEET = StudioTheme.class.getResource("/styles/studio.css").toExternalForm();
    private static final Map<Parent, Boolean> ROOTS = new WeakHashMap<>();
    private static String theme = "light";
    private static String textSize = "default";
    private static final String BASE_FONT_SIZE = "studio.baseFontSize";

    private StudioTheme() {}

    public static void install(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
        register(scene.getRoot());
    }

    public static void install(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(STYLESHEET);
        register(dialogPane);
    }

    public static void configure(String selectedTheme, String selectedTextSize) {
        theme = validTheme(selectedTheme);
        textSize = validTextSize(selectedTextSize);
        for (Parent root : ROOTS.keySet()) apply(root);
    }

    public static double textScale() {
        return switch (textSize) {
            case "large" -> 1.15;
            case "larger" -> 1.3;
            default -> 1.0;
        };
    }

    public static void setResponsiveFontSize(Parent root, double baseSize) {
        root.getProperties().put(BASE_FONT_SIZE, baseSize);
        applyFontSize(root, baseSize);
    }

    private static void register(Parent root) {
        ROOTS.put(root, Boolean.TRUE);
        apply(root);
    }

    private static void apply(Parent root) {
        root.getStyleClass().removeAll("theme-dark", "theme-high-contrast", "text-size-large", "text-size-larger");
        if ("dark".equals(theme)) root.getStyleClass().add("theme-dark");
        if ("high-contrast".equals(theme)) root.getStyleClass().add("theme-high-contrast");
        if ("large".equals(textSize)) root.getStyleClass().add("text-size-large");
        if ("larger".equals(textSize)) root.getStyleClass().add("text-size-larger");
        Object baseSize = root.getProperties().get(BASE_FONT_SIZE);
        if (baseSize instanceof Double size) applyFontSize(root, size);
    }

    private static void applyFontSize(Parent root, double baseSize) {
        double fontSize = Math.round(baseSize * textScale() * 10.0) / 10.0;
        Object previous = root.getProperties().get("studio.fontSize");
        if (!(previous instanceof Double value) || Double.compare(fontSize, value) != 0) {
            root.setStyle("-fx-font-size: " + fontSize + "px;");
            root.getProperties().put("studio.fontSize", fontSize);
        }
    }

    private static String validTheme(String value) {
        return switch (value == null ? "" : value) {
            case "dark", "high-contrast" -> value;
            default -> "light";
        };
    }

    private static String validTextSize(String value) {
        return switch (value == null ? "" : value) {
            case "large", "larger" -> value;
            default -> "default";
        };
    }
}
