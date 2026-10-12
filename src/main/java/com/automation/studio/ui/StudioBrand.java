package com.automation.studio.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.*;

public final class StudioBrand {
    // Google Material Design 3 Palette
    public static final Color GOOGLE_BLUE = Color.web("#1a73e8");
    public static final Color GOOGLE_BLUE_DARK = Color.web("#1557b0");
    public static final Color GOOGLE_RED = Color.web("#ea4335");
    public static final Color GOOGLE_YELLOW = Color.web("#fbbc04");
    public static final Color GOOGLE_GREEN = Color.web("#34a853");

    private StudioBrand() {}

    public static Node mark() {
        return mark(32);
    }

    public static Node mark(double size) {
        StackPane root = new StackPane();
        root.setMinSize(size, size);
        root.setPrefSize(size, size);
        root.setMaxSize(size, size);
        root.getStyleClass().add("brand-mark-root");
        root.setAccessibleText("Playwright Test Studio logo");

        // 1. Google Material Squircle with subtle depth
        Rectangle base = new Rectangle(size, size);
        base.setArcWidth(size * 0.38);
        base.setArcHeight(size * 0.38);
        LinearGradient baseGradient = new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, GOOGLE_BLUE),
                new Stop(1.0, GOOGLE_BLUE_DARK)
        );
        base.setFill(baseGradient);
        base.setStroke(Color.rgb(255, 255, 255, 0.28));
        base.setStrokeWidth(Math.max(1.0, size * 0.03));
        base.setEffect(new DropShadow(size * 0.14, 0, size * 0.04, Color.rgb(26, 115, 232, 0.38)));

        // 2. Playwright "P" Monogram container
        Pane glyphLayer = new Pane();
        glyphLayer.setMinSize(size, size);
        glyphLayer.setPrefSize(size, size);
        glyphLayer.setMaxSize(size, size);

        // Vertical stem of "P"
        double stemX = size * 0.22;
        double stemY = size * 0.18;
        double stemW = size * 0.14;
        double stemH = size * 0.58;
        Rectangle stem = new Rectangle(stemX, stemY, stemW, stemH);
        stem.setArcWidth(size * 0.07);
        stem.setArcHeight(size * 0.07);
        stem.setFill(Color.WHITE);

        // Loop of "P" (Top bar + Right curve + Bottom bar)
        double loopX = stemX + stemW - 0.5;
        double loopY = stemY;
        double loopW = size * 0.38;
        double loopH = size * 0.36;
        Rectangle loopOuter = new Rectangle(loopX, loopY, loopW, loopH);
        loopOuter.setArcWidth(loopH);
        loopOuter.setArcHeight(loopH);
        loopOuter.setFill(Color.WHITE);

        // Play Triangle inside the loop (Google Yellow / Amber)
        double triX1 = stemX + stemW + size * 0.07;
        double triY1 = stemY + size * 0.07;
        double triX2 = stemX + stemW + size * 0.07;
        double triY2 = stemY + loopH - size * 0.07;
        double triX3 = stemX + stemW + size * 0.26;
        double triY3 = stemY + (loopH * 0.5);
        Polygon playTriangle = new Polygon(
                triX1, triY1,
                triX2, triY2,
                triX3, triY3
        );
        playTriangle.setFill(GOOGLE_YELLOW);

        // Google Red accent arc at top
        Rectangle redAccent = new Rectangle(loopX + size * 0.06, loopY + 0.5, size * 0.18, size * 0.07);
        redAccent.setArcWidth(size * 0.06);
        redAccent.setArcHeight(size * 0.06);
        redAccent.setFill(GOOGLE_RED);

        glyphLayer.getChildren().addAll(stem, loopOuter, playTriangle, redAccent);

        // 3. Verification Checkmark Badge in Google Green at bottom-right
        double badgeRadius = size * 0.23;
        Circle badge = new Circle(badgeRadius);
        badge.setFill(GOOGLE_GREEN);
        badge.setStroke(Color.WHITE);
        badge.setStrokeWidth(Math.max(1.5, size * 0.05));
        badge.setEffect(new DropShadow(size * 0.10, 0, size * 0.03, Color.rgb(0, 0, 0, 0.28)));

        Polyline checkMark = new Polyline(
                -badgeRadius * 0.45, -badgeRadius * 0.05,
                -badgeRadius * 0.10, badgeRadius * 0.35,
                badgeRadius * 0.50, -badgeRadius * 0.35
        );
        checkMark.setStroke(Color.WHITE);
        checkMark.setStrokeWidth(Math.max(1.8, size * 0.065));
        checkMark.setStrokeLineCap(StrokeLineCap.ROUND);
        checkMark.setStrokeLineJoin(StrokeLineJoin.ROUND);
        checkMark.setFill(null);

        StackPane badgePane = new StackPane(badge, checkMark);
        badgePane.setMinSize(badgeRadius * 2, badgeRadius * 2);
        badgePane.setPrefSize(badgeRadius * 2, badgeRadius * 2);
        badgePane.setMaxSize(badgeRadius * 2, badgeRadius * 2);

        root.getChildren().addAll(base, glyphLayer, badgePane);
        StackPane.setAlignment(badgePane, Pos.BOTTOM_RIGHT);
        return root;
    }

    public static WritableImage windowIcon() {
        int size = 48;
        WritableImage image = new WritableImage(size, size);
        PixelWriter pw = image.getPixelWriter();

        double squircleRadius = size * 0.36;
        double badgeCx = size * 0.76;
        double badgeCy = size * 0.76;
        double badgeR = size * 0.23;

        // Supersampling 3x3 per pixel for anti-aliasing
        int subSamples = 3;
        double subStep = 1.0 / subSamples;

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double rSum = 0, gSum = 0, bSum = 0, aSum = 0;

                for (int sy = 0; sy < subSamples; sy++) {
                    double y = py + (sy + 0.5) * subStep;
                    for (int sx = 0; sx < subSamples; sx++) {
                        double x = px + (sx + 0.5) * subStep;

                        // Check badge first (on top)
                        double dBadge = Math.hypot(x - badgeCx, y - badgeCy);
                        if (dBadge <= badgeR) {
                            // Check white checkmark lines inside badge
                            // Line 1: (badgeCx - 4.5, badgeCy) to (badgeCx - 1, badgeCy + 4)
                            // Line 2: (badgeCx - 1, badgeCy + 4) to (badgeCx + 5.5, badgeCy - 4.5)
                            double dLine1 = distToSegment(x, y, badgeCx - size * 0.10, badgeCy - size * 0.01, badgeCx - size * 0.02, badgeCy + size * 0.08);
                            double dLine2 = distToSegment(x, y, badgeCx - size * 0.02, badgeCy + size * 0.08, badgeCx + size * 0.11, badgeCy - size * 0.09);
                            double strokeThick = size * 0.038;

                            if (dLine1 <= strokeThick || dLine2 <= strokeThick) {
                                rSum += 1.0; gSum += 1.0; bSum += 1.0; aSum += 1.0;
                            } else if (dBadge >= badgeR - size * 0.035) {
                                // White rim of badge
                                rSum += 1.0; gSum += 1.0; bSum += 1.0; aSum += 1.0;
                            } else {
                                // Google Green badge body
                                rSum += GOOGLE_GREEN.getRed();
                                gSum += GOOGLE_GREEN.getGreen();
                                bSum += GOOGLE_GREEN.getBlue();
                                aSum += 1.0;
                            }
                            continue;
                        }

                        // Check squircle body
                        boolean inSquircle = inRoundedRect(x, y, size * 0.05, size * 0.05, size * 0.90, size * 0.90, squircleRadius);
                        if (inSquircle) {
                            // Check "P" monogram geometry
                            double stemX = size * 0.21;
                            double stemY = size * 0.18;
                            double stemW = size * 0.14;
                            double stemH = size * 0.58;

                            double loopX = stemX + stemW - 0.5;
                            double loopY = stemY;
                            double loopW = size * 0.38;
                            double loopH = size * 0.36;

                            // Stem
                            boolean inStem = inRoundedRect(x, y, stemX, stemY, stemW, stemH, size * 0.06);
                            // Outer loop
                            boolean inLoop = inRoundedRect(x, y, loopX, loopY, loopW, loopH, loopH * 0.5);

                            // Inner play triangle
                            double triX1 = stemX + stemW + size * 0.07;
                            double triY1 = stemY + size * 0.07;
                            double triX2 = stemX + stemW + size * 0.07;
                            double triY2 = stemY + loopH - size * 0.07;
                            double triX3 = stemX + stemW + size * 0.25;
                            double triY3 = stemY + (loopH * 0.5);
                            boolean inPlayTriangle = inTriangle(x, y, triX1, triY1, triX2, triY2, triX3, triY3);

                            // Red accent on top of loop
                            boolean inRedAccent = x >= loopX + size * 0.07 && x <= loopX + size * 0.24 && y >= loopY && y <= loopY + size * 0.07;

                            if (inPlayTriangle) {
                                rSum += GOOGLE_YELLOW.getRed();
                                gSum += GOOGLE_YELLOW.getGreen();
                                bSum += GOOGLE_YELLOW.getBlue();
                                aSum += 1.0;
                            } else if (inRedAccent) {
                                rSum += GOOGLE_RED.getRed();
                                gSum += GOOGLE_RED.getGreen();
                                bSum += GOOGLE_RED.getBlue();
                                aSum += 1.0;
                            } else if (inStem || inLoop) {
                                rSum += 1.0; gSum += 1.0; bSum += 1.0; aSum += 1.0;
                            } else {
                                // Squircle blue gradient
                                double t = (x + y) / (size * 2.0);
                                double r = GOOGLE_BLUE.getRed() * (1 - t) + GOOGLE_BLUE_DARK.getRed() * t;
                                double g = GOOGLE_BLUE.getGreen() * (1 - t) + GOOGLE_BLUE_DARK.getGreen() * t;
                                double b = GOOGLE_BLUE.getBlue() * (1 - t) + GOOGLE_BLUE_DARK.getBlue() * t;
                                rSum += r; gSum += g; bSum += b; aSum += 1.0;
                            }
                        }
                    }
                }

                double totalSamples = subSamples * subSamples;
                double alpha = aSum / totalSamples;
                if (alpha > 0.01) {
                    Color c = Color.color(
                            Math.min(1.0, rSum / totalSamples),
                            Math.min(1.0, gSum / totalSamples),
                            Math.min(1.0, bSum / totalSamples),
                            alpha
                    );
                    pw.setColor(px, py, c);
                } else {
                    pw.setColor(px, py, Color.TRANSPARENT);
                }
            }
        }
        return image;
    }

    private static boolean inRoundedRect(double x, double y, double rx, double ry, double rw, double rh, double r) {
        if (x < rx || x > rx + rw || y < ry || y > ry + rh) return false;
        double innerX1 = rx + r;
        double innerX2 = rx + rw - r;
        double innerY1 = ry + r;
        double innerY2 = ry + rh - r;

        if (x >= innerX1 && x <= innerX2) return true;
        if (y >= innerY1 && y <= innerY2) return true;

        double cx = x < innerX1 ? innerX1 : innerX2;
        double cy = y < innerY1 ? innerY1 : innerY2;
        return Math.hypot(x - cx, y - cy) <= r;
    }

    private static boolean inTriangle(double px, double py, double x1, double y1, double x2, double y2, double x3, double y3) {
        double d1 = sign(px, py, x1, y1, x2, y2);
        double d2 = sign(px, py, x2, y2, x3, y3);
        double d3 = sign(px, py, x3, y3, x1, y1);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static double sign(double p1x, double p1y, double p2x, double p2y, double p3x, double p3y) {
        return (p1x - p3x) * (p2y - p3y) - (p2x - p3x) * (p1y - p3y);
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);
        return Math.hypot(px - projX, py - projY);
    }
}

