package com.automation.studio.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

public final class StudioTheme {
    private static final String STYLESHEET = StudioTheme.class.getResource("/styles/studio.css").toExternalForm();

    private StudioTheme() {}

    public static void install(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
    }

    public static void install(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(STYLESHEET);
    }
}
