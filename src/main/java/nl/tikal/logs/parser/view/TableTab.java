package nl.tikal.logs.parser.view;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

public class TableTab extends Tab {
    private static final String STACK_TRACE_FILTER = "nl.mendesgans";
    private static final int PREF_COLUMN_COUNT = 80;
    private static final int PREF_ROW_COUNT = 30;
    private static final int FILTER_DIALOG_HEIGHT = 400;
    private static final int FILTER_DIALOG_WIDTH = 300;
    private static final int MIN_SEARCH_LENGTH = 3;
    private static final String DEFAULT_SEARCH_FIELD = "message";

    private final TableView<JsonNode> tableView;
    private final TextField searchField;
    private final ComboBox<String> searchFieldSelector;
    private ObservableList<JsonNode> allData;
    private FilteredList<JsonNode> filteredData;
    private final Map<String, Set<String>> columnFilters;
    private String columnStatus;

    public TableTab(final String name) {
        super(name);
        this.setClosable(false);
        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
        tableView.setRowFactory(tv -> {
            final var row = new TableRow<JsonNode>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    showJsonDialog(row.getItem());
                }
            });
            return row;
        });
        searchField = new TextField();
        searchField.setPromptText("Search (min " + MIN_SEARCH_LENGTH + " characters)");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchFieldSelector = new ComboBox<>();
        searchFieldSelector.setPromptText(DEFAULT_SEARCH_FIELD);

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        searchFieldSelector.valueProperty().addListener((obs, oldValue, newValue) -> {
            showColumn(newValue);
            applyFilters();
        });

        final var searchBox = new HBox(5, new Label("Search:"), searchField, searchFieldSelector);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(5));

        final var content = new BorderPane(tableView);
        content.setTop(searchBox);
        this.setContent(content);
        columnFilters = new HashMap<>();
    }

    public void clearTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        columnFilters.clear();
        searchFieldSelector.getItems().clear();
        searchField.clear();
        allData = null;
        filteredData = null;
    }

    public void clearColums() {
        tableView.getColumns().clear();

    }

    public void createColumns(final Set<String> allFields, final List<JsonNode> jsonObjects) {
        columnFilters.clear();
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

            column.setCellValueFactory(cellData ->
                    new SimpleStringProperty(valueOf(cellData.getValue(), fieldName)));

            column.setCellFactory(col -> {
                final var cell = new TableCell<JsonNode, String>() {
                    @Override
                    protected void updateItem(final String item, final boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : item);
                    }
                };

                final var addItem = new MenuItem();
                addItem.setOnAction(e -> updateFilter(fieldName, cell.getItem(), true));
                final var removeItem = new MenuItem();
                removeItem.setOnAction(e -> updateFilter(fieldName, cell.getItem(), false));
                final var copyItem = new MenuItem();
                copyItem.setOnAction(e -> copyToClipboard(cell.getItem()));
                final var contextMenu = new ContextMenu(addItem, removeItem, copyItem);

                cell.setOnContextMenuRequested(e -> {
                    if (cell.isEmpty()) {
                        return;
                    }
                    final var value = cell.getItem() == null ? "" : cell.getItem();
                    final var display = value.isEmpty() ? "(empty)" : value;
                    addItem.setText("Add \"" + display + "\" to filter");
                    removeItem.setText("Remove \"" + display + "\" from filter");
                    copyItem.setText("Copy \"" + display + "\"");
                    contextMenu.show(cell, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
                return cell;
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
                150, 0, FILTER_DIALOG_WIDTH, 50, 350, 0
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

        searchFieldSelector.getItems().setAll(allFields);
        searchFieldSelector.setValue(allFields.contains(DEFAULT_SEARCH_FIELD)
                ? DEFAULT_SEARCH_FIELD
                : allFields.stream().findFirst().orElse(null));
        applyFilters();
    }

    /** Makes the column for the given field visible, so search results are readable. */
    private void showColumn(final String fieldName) {
        if (fieldName == null) {
            return;
        }
        for (final var column : tableView.getColumns()) {
            if (fieldName.equals(column.getUserData()) && !column.isVisible()) {
                column.setVisible(true);
            }
        }
    }

    private static void copyToClipboard(final String value) {
        final var content = new ClipboardContent();
        content.putString(value == null ? "" : value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static String valueOf(final JsonNode node, final String fieldName) {        final var fieldNode = node.get(fieldName);
        if (fieldNode == null) {
            return "";
        }
        return fieldNode.isTextual() ? fieldNode.asText() : fieldNode.toString();
    }

    private Set<String> uniqueValuesOf(final String fieldName) {
        final Set<String> uniqueValues = new TreeSet<>();
        for (final var node : allData) {
            uniqueValues.add(valueOf(node, fieldName));
        }
        return uniqueValues;
    }

    /**
     * Adds or removes a single value from the filter of a column. When no filter is
     * active yet, adding narrows the filter to just that value and removing keeps
     * every other value.
     */
    private void updateFilter(final String fieldName, final String rawValue, final boolean add) {
        if (allData == null || allData.isEmpty()) {
            return;
        }
        final var value = rawValue == null ? "" : rawValue;
        final var uniqueValues = uniqueValuesOf(fieldName);

        var selected = columnFilters.get(fieldName);
        selected = selected == null ? new HashSet<>(add ? Set.of() : uniqueValues) : new HashSet<>(selected);

        if (add) {
            selected.add(value);
        } else {
            selected.remove(value);
        }

        if (selected.size() == uniqueValues.size()) {
            columnFilters.remove(fieldName);
        } else {
            columnFilters.put(fieldName, selected);
        }
        applyFilters();
    }

    /**
     * Pretty prints the JSON, expanding the escaped "\n\t" separators inside the
     * stack_trace field onto indented lines. When {@code stackTraceFilter} is not
     * null, only the extra stack trace lines containing that text are kept.
     */
    private static String formatJson(final JsonNode node, final String stackTraceFilter) {
        final var lines = node.toPrettyString().split("\n", -1);
        final var result = new StringBuilder();
        for (var i = 0; i < lines.length; i++) {
            final var line = lines[i];
            if (line.trim().startsWith("\"stack_trace\"") && line.contains("\\n\\t")) {
                final var indent = " ".repeat(line.length() - line.stripLeading().length() + 4);
                final var parts = line.split("\\\\n\\\\t", -1);
                result.append(parts[0]);
                for (var p = 1; p < parts.length; p++) {
                    if (stackTraceFilter == null || parts[p].contains(stackTraceFilter)) {
                        result.append('\n').append(indent).append(parts[p]);
                    }
                }
            } else {
                result.append(line);
            }
            if (i < lines.length - 1) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private void showJsonDialog(final JsonNode node) {
        if (node == null) {
            return;
        }

        final var dialog = new Dialog<Void>();
        dialog.setTitle("Original JSON");
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        final var textArea = new TextArea(formatJson(node, null));
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setStyle("-fx-font-family: monospace;");
        textArea.setPrefColumnCount(PREF_COLUMN_COUNT);
        textArea.setPrefRowCount(PREF_ROW_COUNT);

        final var filterButtonType = new ButtonType("Show " + STACK_TRACE_FILTER, ButtonBar.ButtonData.OTHER);
        final var clearButtonType = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        final var copyButtonType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes()
                .addAll(filterButtonType, clearButtonType, copyButtonType, ButtonType.CLOSE);

        final var filterButton = (Button) dialog.getDialogPane().lookupButton(filterButtonType);
        filterButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            textArea.setText(formatJson(node, STACK_TRACE_FILTER));
            event.consume();
        });

        final var clearButton = (Button) dialog.getDialogPane().lookupButton(clearButtonType);
        clearButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            textArea.setText(formatJson(node, null));
            event.consume();
        });

        final var copyButton = (Button) dialog.getDialogPane().lookupButton(copyButtonType);
        copyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            final var content = new ClipboardContent();
            content.putString(textArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
            event.consume();
        });

        dialog.showAndWait();
    }

    private void showFilterDialog(final String fieldName) {
        if (allData == null || allData.isEmpty()) {
            return;
        }

        final var dialog = new Dialog<Void>();
        dialog.setTitle("Filter: " + fieldName);
        dialog.setHeaderText("Select values to display:");
        dialog.setResizable(true);

        // Collect all unique values for this field
        final Set<String> uniqueValues = uniqueValuesOf(fieldName);

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
        vbox.getChildren().addFirst(buttonBox);

        final var scrollPane = new ScrollPane(vbox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(FILTER_DIALOG_HEIGHT);
        scrollPane.setPrefWidth(FILTER_DIALOG_WIDTH);
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        scrollPane.setMaxHeight(Double.MAX_VALUE);

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
                if (!filterEntry.getValue().contains(valueOf(node, filterEntry.getKey()))) {
                    return false;
                }
            }
            return matchesSearch(node);
        });

        final var activeFilters = columnFilters.size();
        final var filterStatus = activeFilters > 0 ? " (" + activeFilters + " filter(s) active)" : "";
        final var searchStatus = isSearchActive()
                ? " (" + filteredData.size() + " of " + allData.size() + " rows match \"" + searchField.getText() + "\")"
                : "";
        StatusPanel.changeStatus(filterStatus + searchStatus);
    }

    private boolean isSearchActive() {
        final var text = searchField.getText();
        return text != null
                && text.trim().length() >= MIN_SEARCH_LENGTH
                && searchFieldSelector.getValue() != null;
    }

    private boolean matchesSearch(final JsonNode node) {
        if (!isSearchActive()) {
            return true;
        }
        final var text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        return valueOf(node, searchFieldSelector.getValue()).toLowerCase(Locale.ROOT).contains(text);
    }

    public String getColumnStatus() {
        return columnStatus;
    }

    public ObservableList<TableColumn<JsonNode,?>> getColumns() {
        return tableView.getColumns();
    }
}
