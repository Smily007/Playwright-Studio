package com.automation.studio;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.automation.studio.ui.IconButton;

import javafx.application.Platform;
import javafx.scene.control.Button;

public class IconButtonTest {

    @BeforeClass
    public void initJavaFX() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    @Test
    public void testRunButtonConfiguration() {
        Button b = IconButton.create("Run test", "▶", () -> {});
        Assert.assertEquals(b.getText(), "Run test");
        Assert.assertEquals(b.getAccessibleText(), "Run test");
        Assert.assertEquals(b.getContentDisplay(), javafx.scene.control.ContentDisplay.LEFT);
        Assert.assertEquals(b.getGraphicTextGap(), 6.0);
        Assert.assertTrue(b.getStyleClass().contains("icon-button"));
        Assert.assertTrue(b.getStyleClass().contains("btn-action-run"));
        Assert.assertNotNull(b.getGraphic());
        Assert.assertTrue(b.getGraphic().getStyleClass().contains("glyph-run"));
    }

    @Test
    public void testRecordButtonConfiguration() {
        Button b = IconButton.create("Start recording", "●", () -> {});
        Assert.assertTrue(b.getStyleClass().contains("btn-action-record"));
        Assert.assertTrue(b.getGraphic().getStyleClass().contains("glyph-record"));
    }

    @Test
    public void testStopRecordingButtonConfiguration() {
        Button b = IconButton.create("Stop recording", "■", () -> {});
        Assert.assertTrue(b.getStyleClass().contains("btn-action-stop-rec"));
        Assert.assertTrue(b.getGraphic().getStyleClass().contains("glyph-stop-rec"));
    }

    @Test
    public void testExcelButtonConfiguration() {
        Button b = IconButton.create("Import Excel data", "▦", () -> {});
        Assert.assertTrue(b.getStyleClass().contains("btn-action-excel"));
        Assert.assertTrue(b.getGraphic().getStyleClass().contains("glyph-excel"));
    }
}

