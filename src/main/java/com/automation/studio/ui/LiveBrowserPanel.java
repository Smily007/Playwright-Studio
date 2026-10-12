package com.automation.studio.ui;

import com.automation.studio.browser.LiveView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Shows the Playwright browser inside the studio window and forwards mouse / keyboard input to it. */
public class LiveBrowserPanel extends BorderPane {
    private final ImageView view = new ImageView();
    private final StackPane stack = new StackPane();
    private final Label hint = new Label("🌐 Embedded Browser View\nPress START RECORDING or START BROWSER to launch");
    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Button inspect = new Button("INSPECT");
    private final TextField xpath = new TextField();
    private final TextField relativeXpath = new TextField();
    private final Label inspectStatus = new Label();
    private volatile LiveView live;
    private volatile int vw = 1280, vh = 720;
    private Consumer<String> navHandler = s -> { };
    private BiConsumer<String, String> inspectHandler = (selector, frame) -> { };
    private long lastMove;

    public LiveBrowserPanel() {
        Button back = navigationButton("‹", "Back", "GO_BACK");
        Button fwd = navigationButton("›", "Forward", "GO_FORWARD");
        Button reload = navigationButton("↻", "Refresh", "RELOAD");
        inspect.setTooltip(new Tooltip("Inspect a page element and capture its XPath"));
        inspect.setDisable(true);
        inspect.getStyleClass().addAll("button-secondary", "browser-inspect-btn");
        inspect.setOnAction(e -> {
            LiveView l = live;
            if (l == null) return;
            boolean enabled = !inspect.getText().equals("CANCEL");
            inspect.setText(enabled ? "CANCEL" : "INSPECT");
            if (enabled) {
                if (!inspect.getStyleClass().contains("inspect-active")) inspect.getStyleClass().add("inspect-active");
                xpath.clear();
            } else {
                inspect.getStyleClass().remove("inspect-active");
            }
            l.setInspectMode(enabled);
        });
        xpath.setPromptText("Enter XPath to highlight");
        xpath.setAccessibleText("XPath to inspect or highlight in the live browser");
        xpath.setPrefColumnCount(20);
        xpath.setMinWidth(100);
        xpath.getStyleClass().add("browser-xpath-field");
        HBox.setHgrow(xpath, Priority.ALWAYS);
        Button highlight = new Button("HIGHLIGHT");
        highlight.setTooltip(new Tooltip("Highlight elements matching the entered XPath"));
        highlight.getStyleClass().addAll("button-secondary", "btn-highlight");
        highlight.setOnAction(e -> highlightXPath());
        xpath.setOnAction(e -> highlightXPath());
        relativeXpath.setPromptText("Relative XPath");
        relativeXpath.setAccessibleText("Relative XPath for the inspected element");
        relativeXpath.setPrefColumnCount(18);
        relativeXpath.setEditable(false);
        relativeXpath.setMinWidth(100);
        relativeXpath.getStyleClass().add("browser-xpath-field");
        HBox.setHgrow(relativeXpath, Priority.ALWAYS);
        Button copyXpath = new Button("COPY XPATH");
        copyXpath.getStyleClass().addAll("button-quiet", "btn-copy-xpath");
        copyXpath.setOnAction(e -> {
            if (!xpath.getText().isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(xpath.getText());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        Button copyRelativeXpath = new Button("COPY RELATIVE");
        copyRelativeXpath.getStyleClass().addAll("button-quiet", "btn-copy-xpath");
        copyRelativeXpath.setOnAction(e -> {
            if (!relativeXpath.getText().isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(relativeXpath.getText());
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        inspectStatus.setAccessibleText("XPath highlight status");
        inspectStatus.getStyleClass().add("inspect-status-label");
        Label title = new Label("LIVE BROWSER");
        title.getStyleClass().add("section-title");
        HBox header = new HBox(6, title, back, fwd, reload, inspect, xpath, highlight, copyXpath,
                relativeXpath, copyRelativeXpath, inspectStatus);
        header.getStyleClass().add("browser-toolbar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6));
        setTop(header);

        view.setPreserveRatio(true);
        view.setPickOnBounds(true);
        stack.setAlignment(Pos.TOP_LEFT);
        stack.getStyleClass().add("browser-viewport");
        stack.setFocusTraversable(true);
        stack.getChildren().addAll(view, hint);
        hint.getStyleClass().add("browser-empty-hint");
        StackPane.setAlignment(hint, Pos.CENTER);
        view.fitWidthProperty().bind(stack.widthProperty());
        view.fitHeightProperty().bind(stack.heightProperty());
        setCenter(stack);
        setMinWidth(220);

        view.setOnMousePressed(e -> { stack.requestFocus(); LiveView l = live; if (l != null) l.mouseDown(px(e), py(e), button(e), e.getClickCount()); });
        view.setOnMouseReleased(e -> { LiveView l = live; if (l != null) l.mouseUp(px(e), py(e), button(e), e.getClickCount()); });
        view.setOnMouseMoved(this::moved);
        view.setOnMouseDragged(this::moved);
        view.setOnScroll(e -> { LiveView l = live; if (l != null) l.wheel(-e.getDeltaX(), -e.getDeltaY()); });

        stack.setOnKeyPressed(e -> {
            LiveView l = live;
            if (l == null) return;
            String name = keyName(e.getCode());
            if (name != null) { l.press(name); e.consume(); }
            else if (e.isShortcutDown() && e.getCode() == KeyCode.V) {
                String text = Clipboard.getSystemClipboard().getString();
                if (text != null) l.insertText(text);
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.A) { l.press("Control+A"); e.consume(); }
        });
        stack.setOnKeyTyped(e -> {
            LiveView l = live;
            String ch = e.getCharacter();
            if (l == null || ch == null || ch.isEmpty() || e.isShortcutDown() || e.isControlDown()) return;
            char c = ch.charAt(0);
            if (c >= 32 && c != 127) { l.type(ch); e.consume(); }
        });
    }

    private Button navigationButton(String icon, String label, String action) {
        Button button = new Button(icon);
        button.getStyleClass().addAll("button-quiet", "browser-nav-btn");
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.setFocusTraversable(true);
        button.setOnAction(event -> navHandler.accept(action));
        return button;
    }

    private void moved(MouseEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastMove < 40) return;
        lastMove = now;
        LiveView l = live;
        if (l != null) l.mouseMove(px(e), py(e));
    }

    private double px(MouseEvent e) { return e.getX() * vw / Math.max(1, view.getLayoutBounds().getWidth()); }

    private double py(MouseEvent e) { return e.getY() * vh / Math.max(1, view.getLayoutBounds().getHeight()); }

    private static String button(MouseEvent e) {
        return e.getButton() == MouseButton.SECONDARY ? "RIGHT" : e.getButton() == MouseButton.MIDDLE ? "MIDDLE" : "LEFT";
    }

    private static String keyName(KeyCode c) {
        return switch (c) {
            case ENTER -> "Enter";
            case BACK_SPACE -> "Backspace";
            case TAB -> "Tab";
            case ESCAPE -> "Escape";
            case DELETE -> "Delete";
            case LEFT -> "ArrowLeft";
            case RIGHT -> "ArrowRight";
            case UP -> "ArrowUp";
            case DOWN -> "ArrowDown";
            case HOME -> "Home";
            case END -> "End";
            case PAGE_UP -> "PageUp";
            case PAGE_DOWN -> "PageDown";
            default -> null;
        };
    }

    public void setNavHandler(Consumer<String> handler) { this.navHandler = handler; }
    public void setInspectHandler(BiConsumer<String, String> handler) { this.inspectHandler = handler; }

    public void setInspectResult(String selector, String relativeSelector, String frameSelector) {
        Platform.runLater(() -> {
            inspect.setText("INSPECT");
            inspect.getStyleClass().remove("inspect-active");
            if (selector.isBlank()) return;
            xpath.setText(selector);
            relativeXpath.setText(relativeSelector);
            inspectStatus.setText("Element inspected");
            inspectHandler.accept(selector, frameSelector);
        });
    }

    private void highlightXPath() {
        LiveView l = live;
        String selector = xpath.getText().trim();
        if (l == null || selector.isBlank()) {
            inspectStatus.setText(l == null ? "Start the embedded browser first" : "Enter an XPath");
            return;
        }
        cancelInspectMode();
        inspectStatus.setText("Searching...");
        l.highlightXPath(selector).whenComplete((count, error) -> Platform.runLater(() -> {
            if (error != null) {
                inspectStatus.setText("Invalid XPath: " + error.getMessage());
                return;
            }
            inspectStatus.setText(count == 0 ? "No matches" : count + " match(es) highlighted");
        }));
    }

    public void cancelInspectMode() {
        LiveView l = live;
        if (l != null) l.setInspectMode(false);
        Platform.runLater(() -> {
            inspect.setText("INSPECT");
            inspect.getStyleClass().remove("inspect-active");
        });
    }

    /** Connects input to a running embedded session; viewport size is needed to map mouse coordinates. */
    public void attach(LiveView lv, int viewportWidth, int viewportHeight) {
        this.vw = viewportWidth;
        this.vh = viewportHeight;
        this.live = lv;
        Platform.runLater(() -> inspect.setDisable(false));
    }

    public void clear() {
        live = null;
        Platform.runLater(() -> {
            view.setImage(null);
            hint.setVisible(true);
            inspect.setDisable(true);
            inspect.setText("INSPECT");
            inspect.getStyleClass().remove("inspect-active");
            xpath.clear();
            relativeXpath.clear();
            inspectStatus.setText("");
        });
    }

    /** Thread-safe; called with JPEG bytes from the Playwright thread. Frames are coalesced. */
    public void showFrame(byte[] jpeg) {
        latest.set(jpeg);
        if (scheduled.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                scheduled.set(false);
                byte[] d = latest.getAndSet(null);
                if (d != null) { view.setImage(new Image(new ByteArrayInputStream(d))); hint.setVisible(false); }
            });
        }
    }
}
