package com.automation.studio;

import com.automation.studio.ui.StudioBrand;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StudioBrandTest {

    @BeforeClass
    public void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    public void markNodeProperties() {
        Node mark1 = StudioBrand.mark();
        Assert.assertNotNull(mark1);
        Assert.assertEquals(mark1.getAccessibleText(), "Playwright Test Studio logo");
        Assert.assertTrue(mark1.getStyleClass().contains("brand-mark-root"));

        Node mark2 = StudioBrand.mark(48);
        Assert.assertNotNull(mark2);
        Assert.assertNotSame(mark1, mark2, "mark() must return independent Node instances");
    }

    @Test
    public void windowIconDimensionsAndColors() {
        WritableImage icon = StudioBrand.windowIcon();
        Assert.assertNotNull(icon);
        Assert.assertEquals(icon.getWidth(), 48.0);
        Assert.assertEquals(icon.getHeight(), 48.0);

        boolean hasOpaquePixel = false;
        boolean hasTransparentPixel = false;
        boolean hasAntiAliasedPixel = false;
        boolean hasGoogleBlue = false;
        boolean hasGoogleGreen = false;

        for (int y = 0; y < (int) icon.getHeight(); y++) {
            for (int x = 0; x < (int) icon.getWidth(); x++) {
                Color c = icon.getPixelReader().getColor(x, y);
                if (c.getOpacity() == 0.0) {
                    hasTransparentPixel = true;
                } else if (c.getOpacity() == 1.0) {
                    hasOpaquePixel = true;
                } else {
                    hasAntiAliasedPixel = true;
                }

                if (c.getBlue() > 0.7 && c.getRed() < 0.3) {
                    hasGoogleBlue = true;
                }
                if (c.getGreen() > 0.5 && c.getRed() < 0.35) {
                    hasGoogleGreen = true;
                }
            }
        }

        Assert.assertTrue(hasOpaquePixel, "Icon must have solid pixels");
        Assert.assertTrue(hasTransparentPixel, "Icon corners outside squircle must be transparent");
        Assert.assertTrue(hasAntiAliasedPixel, "Icon edges must be anti-aliased");
        Assert.assertTrue(hasGoogleBlue, "Icon must feature Google Blue base");
        Assert.assertTrue(hasGoogleGreen, "Icon must feature Google Green test checkmark badge");
    }
}
