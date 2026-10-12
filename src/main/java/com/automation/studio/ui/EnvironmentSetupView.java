package com.automation.studio.ui;

import com.automation.studio.model.ProjectEnvironment;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class EnvironmentSetupView {
    private record EnvironmentFields(TextField name, TextField url, FlowPane row) {}

    private final VBox rows = new VBox(8);
    private final List<EnvironmentFields> fields = new ArrayList<>();

    static Optional<List<ProjectEnvironment>> show(Window owner, List<ProjectEnvironment> initial) {
        return new EnvironmentSetupView().open(owner, initial);
    }

    private Optional<List<ProjectEnvironment>> open(Window owner, List<ProjectEnvironment> initial) {
        Dialog<List<ProjectEnvironment>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Project environments");
        dialog.setHeaderText("Set up each environment with the base URL used when its tests run. The first URL anchors recorded navigation.");
        dialog.setResizable(true);
        StudioTheme.install(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var size = WindowSizing.fit(owner, 620, 460);
        FlowPane header = new FlowPane(16, 6,
                new Label("Environment name"), new Label("Base URL"));
        VBox content = new VBox(10, header, rows);
        content.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(300, size.getHeight() * 0.65));

        Button add = IconButton.create("Add environment", "+", () -> addRow("", ""));
        VBox root = new VBox(8, scroll, add);
        root.setPadding(new Insets(8));
        root.setPrefWidth(size.getWidth());
        dialog.getDialogPane().setContent(root);

        if (initial != null && !initial.isEmpty()) {
            for (ProjectEnvironment environment : initial) addRow(environment.name, environment.url);
        } else {
            addRow("Development", "https://example.com");
        }

        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validate();
            if (error != null) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING, error);
                alert.initOwner(owner);
                alert.setHeaderText("Check project environments");
                alert.showAndWait();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? collect() : null);
        return dialog.showAndWait();
    }

    private void addRow(String name, String url) {
        TextField environmentName = new TextField(name);
        environmentName.setPromptText("e.g. Development");
        environmentName.setAccessibleText("Environment name");
        TextField baseUrl = new TextField(url);
        baseUrl.setPromptText("https://example.com");
        baseUrl.setAccessibleText("Environment base URL");
        Button remove = new Button();
        IconButton.configure(remove, "Remove environment", "×");
        FlowPane row = new FlowPane(8, 6, environmentName, baseUrl, remove);
        environmentName.setPrefColumnCount(16);
        baseUrl.setPrefColumnCount(36);
        environmentName.setMinWidth(110);
        environmentName.setPrefWidth(150);
        baseUrl.setMinWidth(180);
        baseUrl.setPrefWidth(300);
        EnvironmentFields entry = new EnvironmentFields(environmentName, baseUrl, row);
        fields.add(entry);
        rows.getChildren().add(row);
        remove.setOnAction(e -> {
            if (fields.size() <= 1) return;
            fields.remove(entry);
            rows.getChildren().remove(row);
        });
    }

    private String validate() {
        if (fields.isEmpty()) return "Add at least one environment.";
        Set<String> names = new HashSet<>();
        for (EnvironmentFields entry : fields) {
            String name = entry.name().getText().trim();
            if (name.isEmpty()) return "Every environment needs a name.";
            if (!names.add(name.toLowerCase(Locale.ROOT))) return "Environment names must be unique: " + name;
            String url = entry.url().getText().trim();
            try {
                URI uri = URI.create(url);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null)
                    return "Enter a valid http(s) base URL for environment '" + name + "'.";
            } catch (IllegalArgumentException e) {
                return "Enter a valid http(s) base URL for environment '" + name + "'.";
            }
        }
        return null;
    }

    private List<ProjectEnvironment> collect() {
        List<ProjectEnvironment> environments = new ArrayList<>();
        for (EnvironmentFields entry : fields)
            environments.add(new ProjectEnvironment(entry.name().getText().trim(), entry.url().getText().trim()));
        return environments;
    }
}
