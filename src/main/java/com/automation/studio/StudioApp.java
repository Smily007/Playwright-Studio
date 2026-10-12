package com.automation.studio;

import com.automation.studio.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class StudioApp extends Application {
    @Override
    public void start(Stage stage) {
        new MainWindow(stage, getHostServices()).show();
    }
}
