package nl.tikal.logs.parser.view;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;

public class TableTab extends Tab {
    private TableView<JsonNode> tableView;
    private ObservableList<JsonNode> allData;
    private FilteredList<JsonNode> filteredData;
    private Map<String, Set<String>> columnFilters;
    private String columnStatus;

    public TableTab(String name) {
        super(name);
        this.setClosable(false);
        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
        this.setContent(tableView);
        columnFilters = new HashMap<>();
    }

    public void clearTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        columnFilters.clear();
        allData = null;
        filteredData = null;
    }

    public void clearColums() {
        tableView.getColumns().clear();

    }

    public void createColumns(Set<String> allFields, List<JsonNode> jsonObjects) {
        for (final var fieldName : allFields) {
            final var column = new TableColumn<JsonNode, String>();

            // Store field name in userData for later retrieval
            column.setUserData(fieldName);

            // Create custom header with filter button
            final var headerBox = new HBox(5);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            final var headerLabel = new Label(fieldName);
            final var filterButton = new Button("▼");
            filterButton.setStyle("-fx-font-size: 8px; -fx-padding: 2px 4px;");
            filterButton.setOnAction(e -> showFilterDialog(fieldName));
            headerBox.getChildren().addAll(headerLabel, filterButton);
            column.setGraphic(headerBox);

            column.setCellValueFactory(cellData -> {
                final var node = cellData.getValue();
                final var fieldNode = node.get(fieldName);
                if (fieldNode == null) {
                    return new SimpleStringProperty("");
                } else if (fieldNode.isTextual()) {
                    return new SimpleStringProperty(fieldNode.asText());
                } else {
                    return new SimpleStringProperty(fieldNode.toString());
                }
            });
            tableView.getColumns().add(column);
        }

        // Set default column visibility
        final var defaultVisibleColumns = Set.of(
                "@timestamp", "message", "logger_name", "level", "X-ING-Response-ID"
        );

        final var defaultColumnWidths = List.of(
                120, 0, 250, 50, 350, 0
        );

        final var maxColumnWidths = List.of(
                150, 0, 300, 50, 350, 0
        );

        long visibleCount = 0;
        for (final var column : tableView.getColumns()) {
            final var fieldName = (String) column.getUserData();
            final var shouldBeVisible = defaultVisibleColumns.contains(fieldName);
            column.setVisible(shouldBeVisible);
            final int columnWidth = defaultColumnWidths.get(Long.valueOf(visibleCount).intValue());
            final int maxWidth = maxColumnWidths.get(Long.valueOf(visibleCount).intValue());
            if (columnWidth != 0) {
                column.setPrefWidth(columnWidth);
                column.setMaxWidth(maxWidth);
            }
            if (shouldBeVisible) {
                visibleCount++;
            }
        }

        // Load data into table with filtering support
        allData = FXCollections.observableArrayList(jsonObjects);
        filteredData = new FilteredList<>(allData, p -> true);
        tableView.setItems(filteredData);

        columnStatus = visibleCount > 0 ?
                " (showing " + visibleCount + " of " + tableView.getColumns().size() + " columns)" : "";

    }

    private void showFilterDialog(final String fieldName) {
        if (allData == null || allData.isEmpty()) {
            return;
        }

        final var dialog = new Dialog<Void>();
        dialog.setTitle("Filter: " + fieldName);
        dialog.setHeaderText("Select values to display:");

        // Collect all unique values for this field
        final Set<String> uniqueValues = new TreeSet<>();
        for (final var node : allData) {
            final var fieldNode = node.get(fieldName);
            final String value;
            if (fieldNode == null) {
                value = "";
            } else if (fieldNode.isTextual()) {
                value = fieldNode.asText();
            } else {
                value = fieldNode.toString();
            }
            uniqueValues.add(value);
        }

        // Create checkbox for each unique value
        final var vbox = new VBox(10);
        vbox.setPadding(new Insets(15));

        // Get current filter state for this field
        final var currentFilter = columnFilters.get(fieldName);

        final Map<String, CheckBox> checkBoxMap = new LinkedHashMap<>();
        for (final var value : uniqueValues) {
            final var displayValue = value.isEmpty() ? "(empty)" : value;
            final var checkBox = new CheckBox(displayValue);
            // If no filter exists, all are selected; otherwise check current filter
            checkBox.setSelected(currentFilter == null || currentFilter.contains(value));
            checkBoxMap.put(value, checkBox);
            vbox.getChildren().add(checkBox);
        }

        // Add Select All / Deselect All buttons
        final var buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        final var selectAllBtn = new Button("Select All");
        selectAllBtn.setOnAction(e -> {
            for (final var cb : checkBoxMap.values()) {
                cb.setSelected(true);
            }
        });

        final var deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setOnAction(e -> {
            for (final var cb : checkBoxMap.values()) {
                cb.setSelected(false);
            }
        });

        buttonBox.getChildren().addAll(selectAllBtn, deselectAllBtn);
        vbox.getChildren().add(0, buttonBox);

        final var scrollPane = new ScrollPane(vbox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setPrefWidth(300);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Handle OK button click
        final var okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setOnAction(event -> {
            // Collect selected values
            final Set<String> selectedValues = new HashSet<>();
            for (final var entry : checkBoxMap.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selectedValues.add(entry.getKey());
                }
            }

            // Update filter for this field
            if (selectedValues.size() == uniqueValues.size()) {
                // All selected = no filter
                columnFilters.remove(fieldName);
            } else {
                columnFilters.put(fieldName, selectedValues);
            }

            // Apply all filters
            applyFilters();
        });

        dialog.showAndWait();
    }

    private void applyFilters() {
        if (filteredData == null) {
            return;
        }

        filteredData.setPredicate(node -> {
            // Apply all column filters
            for (final var filterEntry : columnFilters.entrySet()) {
                final var fieldName = filterEntry.getKey();
                final var allowedValues = filterEntry.getValue();

                final var fieldNode = node.get(fieldName);
                final String value;
                if (fieldNode == null) {
                    value = "";
                } else if (fieldNode.isTextual()) {
                    value = fieldNode.asText();
                } else {
                    value = fieldNode.toString();
                }

                if (!allowedValues.contains(value)) {
                    return false;
                }
            }
            return true;
        });

        final var activeFilters = columnFilters.size();
        final var filterStatus = activeFilters > 0 ? " (" + activeFilters + " filter(s) active)" : "";
//        statusLabel.setText("Showing " + filteredData.size() + " of " + allData.size() + " entries" + filterStatus);
    }

    public String getColumnStatus() {
        return columnStatus;
    }

    public ObservableList<TableColumn<JsonNode,?>> getColumns() {
        return tableView.getColumns();
    }
}
