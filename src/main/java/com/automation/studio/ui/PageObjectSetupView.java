package com.automation.studio.ui;

import com.automation.studio.model.Project;
import com.automation.studio.model.ProjectPageObject;
import com.automation.studio.model.TestCase;
import com.automation.studio.utilities.CodeUtil;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.net.URI;
import java.util.List;
import java.util.Optional;

final class PageObjectSetupView {
    private PageObjectSetupView() {}

    static Optional<ProjectPageObject> show(Window owner, Project project, TestCase current) {
        Dialog<ProjectPageObject> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Add reusable Page Object");
        dialog.setHeaderText("Create a class for an application origin and select related tests that should reuse its locators.");
        StudioTheme.install(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        String defaultName = CodeUtil.className(current == null ? "Application" : current.name) + "Page";
        TextField className = new TextField(defaultName);
        TextField origin = new TextField(project.baseUrl);
        ListView<TestCase> tests = new ListView<>(FXCollections.observableArrayList(project.tests));
        tests.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tests.setPrefHeight(190);
        if (current != null) tests.getSelectionModel().select(current);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(12));
        Label classNameLabel = new Label("Java class name");
        classNameLabel.setLabelFor(className);
        className.setAccessibleText("Java class name");
        Label originLabel = new Label("Application URL");
        originLabel.setLabelFor(origin);
        origin.setAccessibleText("Application URL");
        form.addRow(0, classNameLabel, className);
        form.addRow(1, originLabel, origin);
        form.add(new Label("Related test cases"), 0, 2);
        form.add(tests, 1, 2);
        tests.setAccessibleText("Select related test cases");
        GridPane.setHgrow(className, Priority.ALWAYS);
        GridPane.setHgrow(origin, Priority.ALWAYS);
        GridPane.setVgrow(tests, Priority.ALWAYS);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(WindowSizing.fit(owner, 640, 460).getWidth());

        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String problem = validate(className.getText(), origin.getText(), tests.getSelectionModel().getSelectedItems(), project);
            if (problem != null) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING, problem);
                alert.initOwner(owner);
                alert.setHeaderText("Check Page Object settings");
                StudioTheme.install(alert.getDialogPane());
                alert.showAndWait();
            }
        });
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            ProjectPageObject pageObject = new ProjectPageObject();
            pageObject.className = className.getText().trim();
            pageObject.origin = normalizeOrigin(origin.getText());
            for (TestCase test : tests.getSelectionModel().getSelectedItems())
                pageObject.testNames.add(test.name);
            return pageObject;
        });
        return dialog.showAndWait();
    }

    private static String validate(String className, String origin, List<TestCase> tests, Project project) {
        String name = className == null ? "" : className.trim();
        if (!name.matches("[A-Z][A-Za-z0-9]*")) return "Use a valid Java class name starting with an uppercase letter.";
        if (project.pageObjects.stream().anyMatch(page -> page.className.equals(name)))
            return "A Page Object with this class name already exists.";
        try {
            URI uri = URI.create(origin.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null)
                return "Enter a valid http(s) application URL.";
        } catch (IllegalArgumentException e) {
            return "Enter a valid http(s) application URL.";
        }
        if (tests.isEmpty()) return "Select at least one related test case.";
        return null;
    }

    private static String normalizeOrigin(String url) {
        URI uri = URI.create(url.trim());
        try {
            return new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(),
                    uri.getPort(), null, null, null).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid application URL.", e);
        }
    }
}
