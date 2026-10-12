package com.automation.studio.ui;

import com.automation.studio.reporting.ReportManager;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Test history: date, test, browser, duration, status, failure reason; open an old report. */
public final class HistoryView {
    private HistoryView() {}

    public static void show(Window owner, Path projectRoot, ReportManager reports, HostServices hosts) {
        Stage st = new Stage();
        st.initOwner(owner);
        st.setTitle("Test History");
        TableView<ReportManager.HistoryRow> table = new TableView<>();
        table.setAccessibleText("Test execution history");
        table.setItems(FXCollections.observableArrayList(reports.history(projectRoot)));
        table.setPlaceholder(new Label("No test history is available for this project."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(List.of(
                col("Date", 90, r -> r.date), col("Test", 160, r -> r.test), col("Browser", 140, r -> r.browser),
                col("Duration (ms)", 100, r -> String.valueOf(r.durationMs)), col("Status", 80, r -> r.status),
                col("Failure reason", 380, r -> r.failure)));
        Button open = IconButton.create("Open report", "↗", () -> {
            ReportManager.HistoryRow row = table.getSelectionModel().getSelectedItem();
            if (row != null) hosts.showDocument(Path.of(row.reportPath).toUri().toString());
        });
        Button clear = IconButton.create("Clear history and delete report files", "⌫", () -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "This permanently deletes all history and generated report files for this project.");
            confirmation.initOwner(st);
            confirmation.setTitle("Clear test history");
            confirmation.setHeaderText("Delete all test history and reports?");
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
            try {
                reports.clearHistory(projectRoot);
                table.getItems().clear();
                open.setDisable(true);
            } catch (IOException e) {
                Alert error = new Alert(Alert.AlertType.ERROR, "Could not clear test history: " + e.getMessage());
                error.initOwner(st);
                error.setTitle("Could not clear history");
                error.setHeaderText("History was not fully cleared");
                error.showAndWait();
            }
        });
        clear.getStyleClass().add("button-quiet");
        clear.setDisable(table.getItems().isEmpty());
        open.setDisable(table.getItems().isEmpty());
        HBox bar = new HBox(8, open, clear);
        bar.setPadding(new Insets(8));
        BorderPane root = new BorderPane(table);
        root.setBottom(bar);
        var size = WindowSizing.fit(owner, 1000, 500);
        Scene scene = new Scene(root, size.getWidth(), size.getHeight());
        StudioTheme.install(scene);
        st.setScene(scene);
        st.show();
    }

    private static TableColumn<ReportManager.HistoryRow, String> col(String title, double w, java.util.function.Function<ReportManager.HistoryRow, String> f) {
        TableColumn<ReportManager.HistoryRow, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }
}
