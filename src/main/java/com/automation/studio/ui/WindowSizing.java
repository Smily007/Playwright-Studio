package com.automation.studio.ui;

import javafx.geometry.Dimension2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;

final class WindowSizing {
    private WindowSizing() {}

    static Dimension2D fit(Window owner, double preferredWidth, double preferredHeight) {
        Rectangle2D bounds = boundsFor(owner);
        return new Dimension2D(
                Math.min(preferredWidth, bounds.getWidth() * 0.95),
                Math.min(preferredHeight, bounds.getHeight() * 0.9));
    }

    static Rectangle2D boundsFor(Window owner) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        if (owner != null) {
            var screens = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
            if (!screens.isEmpty()) bounds = screens.get(0).getVisualBounds();
        }
        return bounds;
    }
}
