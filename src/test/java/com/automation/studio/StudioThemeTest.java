package com.automation.studio;

import com.automation.studio.ui.StudioTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;

public class StudioThemeTest {

    @BeforeClass
    public void initJavaFX() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    @Test
    public void testStylesheetResourceExists() {
        InputStream stream = getClass().getResourceAsStream("/styles/studio.css");
        Assert.assertNotNull(stream, "studio.css resource must exist on classpath");
    }

    @Test
    public void testInstallScene() {
        Scene scene = new Scene(new Pane());
        StudioTheme.install(scene);
        Assert.assertFalse(scene.getStylesheets().isEmpty());
        Assert.assertTrue(scene.getStylesheets().get(0).contains("studio.css"));
    }

    @Test
    public void testInstallDialogPane() {
        DialogPane pane = new DialogPane();
        StudioTheme.install(pane);
        Assert.assertFalse(pane.getStylesheets().isEmpty());
        Assert.assertTrue(pane.getStylesheets().get(0).contains("studio.css"));
    }

    @Test
    public void testAppearancePreferencesApplyToInstalledRoots() {
        StackPane root = new StackPane();
        Scene scene = new Scene(root);
        StudioTheme.install(scene);
        StudioTheme.configure("high-contrast", "larger");
        Assert.assertTrue(root.getStyleClass().contains("theme-high-contrast"));
        Assert.assertTrue(root.getStyleClass().contains("text-size-larger"));
        Assert.assertEquals(StudioTheme.textScale(), 1.3);

        StudioTheme.configure("dark", "large");
        Assert.assertFalse(root.getStyleClass().contains("theme-high-contrast"));
        Assert.assertTrue(root.getStyleClass().contains("theme-dark"));
        Assert.assertTrue(root.getStyleClass().contains("text-size-large"));
        StudioTheme.configure("light", "default");
    }
}
