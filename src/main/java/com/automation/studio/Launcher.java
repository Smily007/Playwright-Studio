package com.automation.studio;

/** Plain main class (does not extend Application) so JavaFX also starts from the classpath. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        javafx.application.Application.launch(StudioApp.class, args);
    }
}
