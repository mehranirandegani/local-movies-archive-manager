package com.moviearchive.ui;

import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the user narrow the grid by year/rating/runtime ranges plus country
 * and certification (MPAA-style rating) - dimensions that don't fit neatly
 * into the simple text-search or single-tag sidebar filter.
 */
public class AdvancedFilterDialog {

    public static void show(FilterCriteria criteria, List<String> availableCountries,
                             List<String> availableCertifications, Consumer<FilterCriteria> onApply) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("فیلتر پیشرفته");

        TextField yearFrom = new TextField(criteria.yearFrom == null ? "" : String.valueOf(criteria.yearFrom));
        TextField yearTo = new TextField(criteria.yearTo == null ? "" : String.valueOf(criteria.yearTo));
        TextField ratingFrom = new TextField(criteria.ratingFrom == null ? "" : String.valueOf(criteria.ratingFrom));
        TextField ratingTo = new TextField(criteria.ratingTo == null ? "" : String.valueOf(criteria.ratingTo));
        TextField runtimeFrom = new TextField(criteria.runtimeFrom == null ? "" : String.valueOf(criteria.runtimeFrom));
        TextField runtimeTo = new TextField(criteria.runtimeTo == null ? "" : String.valueOf(criteria.runtimeTo));

        ComboBox<String> countryBox = new ComboBox<>();
        countryBox.getItems().add("(همه)");
        countryBox.getItems().addAll(availableCountries);
        countryBox.setValue(criteria.country == null ? "(همه)" : criteria.country);
        countryBox.getStyleClass().add("sort-box");

        ComboBox<String> certBox = new ComboBox<>();
        certBox.getItems().add("(همه)");
        certBox.getItems().addAll(availableCertifications);
        certBox.setValue(criteria.certification == null ? "(همه)" : criteria.certification);
        certBox.getStyleClass().add("sort-box");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);

        int row = 0;
        grid.addRow(row++, new Label("سال از:"), yearFrom, new Label("تا:"), yearTo);
        grid.addRow(row++, new Label("امتیاز از:"), ratingFrom, new Label("تا:"), ratingTo);
        grid.addRow(row++, new Label("مدت (دقیقه) از:"), runtimeFrom, new Label("تا:"), runtimeTo);
        grid.addRow(row++, new Label("کشور:"), countryBox);
        grid.addRow(row++, new Label("رده‌بندی سنی:"), certBox);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e05252;");

        Button applyBtn = new Button("اعمال فیلتر");
        applyBtn.getStyleClass().add("btn-primary");
        Button clearBtn = new Button("پاک‌کردن فیلترها");
        clearBtn.getStyleClass().add("btn-secondary");
        Button cancelBtn = new Button("انصراف");
        cancelBtn.getStyleClass().add("btn-secondary");

        applyBtn.setOnAction(e -> {
            try {
                FilterCriteria updated = new FilterCriteria();
                updated.yearFrom = parseIntOrNull(yearFrom.getText());
                updated.yearTo = parseIntOrNull(yearTo.getText());
                updated.ratingFrom = parseDoubleOrNull(ratingFrom.getText());
                updated.ratingTo = parseDoubleOrNull(ratingTo.getText());
                updated.runtimeFrom = parseIntOrNull(runtimeFrom.getText());
                updated.runtimeTo = parseIntOrNull(runtimeTo.getText());
                updated.country = "(همه)".equals(countryBox.getValue()) ? null : countryBox.getValue();
                updated.certification = "(همه)".equals(certBox.getValue()) ? null : certBox.getValue();
                onApply.accept(updated);
                stage.close();
            } catch (NumberFormatException ex) {
                errorLabel.setText("لطفاً فقط عدد وارد کنید.");
            }
        });

        clearBtn.setOnAction(e -> {
            onApply.accept(new FilterCriteria());
            stage.close();
        });

        cancelBtn.setOnAction(e -> stage.close());

        HBox actions = new HBox(8, applyBtn, clearBtn, cancelBtn);
        VBox root = new VBox(14, grid, errorLabel, actions);
        root.setPadding(new Insets(16));

        stage.setScene(new Scene(root, 420, 320));
        stage.getScene().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        stage.getScene().getStylesheets().add(AdvancedFilterDialog.class.getResource("/styles.css").toExternalForm());
        stage.showAndWait();
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        return Integer.parseInt(s.trim());
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        return Double.parseDouble(s.trim());
    }
}
