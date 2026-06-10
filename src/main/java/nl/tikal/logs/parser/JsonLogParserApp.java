package nl.tikal.logs.parser;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.beans.property.SimpleStringProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class JsonLogParserApp extends Application {
    
    private TableView<JsonNode> tableView;
    private ScrollPane ganttScrollPane;
    private Pane ganttPane;
    private Label statusLabel;
    private JsonLogParser parser;
    private ObservableList<JsonNode> allData;
    private FilteredList<JsonNode> filteredData;
    private Map<String, Set<String>> columnFilters;

    @Override
    public void start(final Stage primaryStage) {
        parser = new JsonLogParser();
        columnFilters = new HashMap<>();
        
        final var root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Top: toolbar with load button
        final var toolbar = createToolbar(primaryStage);
        root.setTop(toolbar);
        
        // Center: TabPane with table and gantt chart
        TabPane tabPane = new TabPane();
        
        // Tab 1: Table View
        Tab tableTab = new Tab("Table View");
        tableTab.setClosable(false);
        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
        tableTab.setContent(tableView);
        
        // Tab 2: Gantt Chart
        Tab ganttTab = new Tab("Gantt Chart");
        ganttTab.setClosable(false);
        ganttPane = new Pane();
        ganttScrollPane = new ScrollPane(ganttPane);
        ganttScrollPane.setFitToWidth(true);
        ganttScrollPane.setStyle("-fx-background-color: white;");
        ganttTab.setContent(ganttScrollPane);
        
        tabPane.getTabs().addAll(tableTab, ganttTab);
        root.setCenter(tabPane);
        
        // Bottom: status bar
        statusLabel = new Label("Ready. Click 'Load Log File' to start.");
        statusLabel.setPadding(new Insets(5));
        root.setBottom(statusLabel);
        
        final var scene = new Scene(root, 1200, 700);
        primaryStage.setTitle("JSON Log Parser");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private HBox createToolbar(final Stage stage) {
        final var toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        
        final var loadButton = new Button("Load Log File");
        loadButton.setOnAction(e -> loadLogFile(stage));
        
        final var columnsButton = new Button("Select Columns");
        columnsButton.setOnAction(e -> showColumnSelector());
        
        final var clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearTable());
        
        toolbar.getChildren().addAll(loadButton, columnsButton, clearButton);
        return toolbar;
    }
    
    private void loadLogFile(final Stage stage) {
        final var fileChooser = new FileChooser();
        fileChooser.setTitle("Select JSON Log File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        final var file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            loadFile(file);
        }
    }
    
    private void loadFile(final File file) {
        try {
            statusLabel.setText("Loading file: " + file.getName() + "...");
            
            final var jsonObjects = parser.parseLogFile(file.getAbsolutePath());
            
            if (jsonObjects.isEmpty()) {
                statusLabel.setText("No valid JSON objects found in file.");
                showAlert("No Data", "No valid JSON objects found in the selected file.");
                return;
            }
            
            // Collect all unique field names from all JSON objects
            final Set<String> allFields = new LinkedHashSet<>();
            for (final var node : jsonObjects) {
                final var fieldNames = node.fieldNames();
                while (fieldNames.hasNext()) {
                    allFields.add(fieldNames.next());
                }
            }
            
            // Clear existing columns
            tableView.getColumns().clear();
            
            // Create columns dynamically based on fields
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
            
            final var columnStatus = visibleCount > 0 ?
                " (showing " + visibleCount + " of " + tableView.getColumns().size() + " columns)" : "";
            statusLabel.setText("Loaded " + jsonObjects.size() + " entries from " + file.getName() + columnStatus);
            
            // Build Gantt chart
            buildGanttChart();
            
        } catch (final IOException ex) {
            statusLabel.setText("Error loading file: " + ex.getMessage());
            showAlert("Error", "Failed to load file: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    private void clearTable() {
        tableView.getColumns().clear();
        tableView.getItems().clear();
        columnFilters.clear();
        allData = null;
        filteredData = null;
        ganttPane.getChildren().clear();
        statusLabel.setText("Table cleared. Load a new file to continue.");
    }
    
    private void showColumnSelector() {
        if (tableView.getColumns().isEmpty()) {
            showAlert("No Columns", "Please load a log file first.");
            return;
        }
        
        final var dialog = new Dialog<Void>();
        dialog.setTitle("Select Columns");
        dialog.setHeaderText("Choose which columns to display:");
        
        // Create checkbox for each column
        final var vbox = new javafx.scene.layout.VBox(10);
        vbox.setPadding(new Insets(15));
        
        final Map<CheckBox, TableColumn<JsonNode, ?>> checkBoxMap = new HashMap<>();
        
        for (final var column : tableView.getColumns()) {
            // Get column name from userData
            var columnName = (String) column.getUserData();
            if (columnName == null) {
                columnName = column.getText(); // fallback
            }
            final var checkBox = new CheckBox(columnName);
            checkBox.setSelected(column.isVisible());
            checkBoxMap.put(checkBox, column);
            vbox.getChildren().add(checkBox);
        }
        
        // Add Select All / Deselect All buttons
        final var buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        final var selectAllBtn = new Button("Select All");
        selectAllBtn.setOnAction(e -> {
            for (final var cb : checkBoxMap.keySet()) {
                cb.setSelected(true);
            }
        });
        
        final var deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setOnAction(e -> {
            for (final var cb : checkBoxMap.keySet()) {
                cb.setSelected(false);
            }
        });
        
        buttonBox.getChildren().addAll(selectAllBtn, deselectAllBtn);
        vbox.getChildren().add(buttonBox);
        
        final var scrollPane = new ScrollPane(vbox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Handle OK button click
        final var okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setOnAction(event -> {
            // Apply column visibility
            for (final var entry : checkBoxMap.entrySet()) {
                entry.getValue().setVisible(entry.getKey().isSelected());
            }
            
            final var visibleCount = checkBoxMap.values().stream()
                                                .filter(TableColumn::isVisible)
                                                .count();
            statusLabel.setText("Showing " + visibleCount + " of " + tableView.getColumns().size() + " columns");
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
        statusLabel.setText("Showing " + filteredData.size() + " of " + allData.size() + " entries" + filterStatus);
    }
    
    private void buildGanttChart() {
        ganttPane.getChildren().clear();
        
        if (allData == null || allData.isEmpty()) {
            return;
        }
        
        // Extract timestamps and categorize by logger_name or level
        List<GanttEntry> entries = new ArrayList<>();
        
        for (JsonNode node : allData) {
            String timestamp = extractTimestamp(node);
            if (timestamp == null) {
                continue;
            }
            
            String category = extractCategory(node);
            String level = node.has("level") ? node.get("level").asText() : "UNKNOWN";
            String message = node.has("message") ? node.get("message").asText() : "";
            
            try {
                Instant instant = parseTimestamp(timestamp);
                entries.add(new GanttEntry(category, instant, level, message));
            } catch (Exception e) {
                // Skip entries with unparseable timestamps
            }
        }
        
        if (entries.isEmpty()) {
            Text noData = new Text(50, 50, "No timeline data available (no valid @timestamp fields found)");
            noData.setStyle("-fx-font-size: 14px;");
            ganttPane.getChildren().add(noData);
            return;
        }
        
        // Sort by timestamp
        entries.sort(Comparator.comparing(GanttEntry::getInstant));
        
        // Find time range
        Instant minTime = entries.get(0).getInstant();
        Instant maxTime = entries.get(entries.size() - 1).getInstant();
        long timeRangeMillis = maxTime.toEpochMilli() - minTime.toEpochMilli();
        
        if (timeRangeMillis == 0) {
            timeRangeMillis = 1; // Avoid division by zero
        }
        
        // Group by category
        Map<String, List<GanttEntry>> groupedEntries = entries.stream()
            .collect(Collectors.groupingBy(GanttEntry::getCategory));
        
        // Layout constants
        final int leftMargin = 200;
        final int topMargin = 50;
        final int rowHeight = 30;
        final int chartWidth = 900;
        final int eventHeight = 20;
        
        // Draw timeline axis
        drawTimelineAxis(minTime, maxTime, leftMargin, topMargin, chartWidth);
        
        // Draw category rows
        int rowIndex = 0;
        List<String> categories = new ArrayList<>(groupedEntries.keySet());
        categories.sort(String::compareTo);
        
        for (String category : categories) {
            int y = topMargin + 30 + (rowIndex * rowHeight);
            
            // Category label
            Text label = new Text(10, y + 15, category);
            label.setStyle("-fx-font-size: 11px; -fx-font-family: monospace;");
            ganttPane.getChildren().add(label);
            
            // Category row background
            Rectangle rowBg = new Rectangle(leftMargin, y, chartWidth, rowHeight);
            rowBg.setFill(rowIndex % 2 == 0 ? Color.rgb(245, 245, 245) : Color.WHITE);
            rowBg.setStroke(Color.rgb(220, 220, 220));
            ganttPane.getChildren().add(rowBg);
            
            // Draw events for this category
            List<GanttEntry> categoryEntries = groupedEntries.get(category);
            for (GanttEntry entry : categoryEntries) {
                long offset = entry.getInstant().toEpochMilli() - minTime.toEpochMilli();
                double x = leftMargin + (chartWidth * offset / (double) timeRangeMillis);
                
                // Event marker (small rectangle)
                Rectangle eventRect = new Rectangle(x - 2, y + 5, 4, eventHeight);
                eventRect.setFill(getColorForLevel(entry.getLevel()));
                eventRect.setStroke(Color.BLACK);
                eventRect.setStrokeWidth(0.5);
                
                // Tooltip
                Tooltip tooltip = new Tooltip(
                    entry.getLevel() + " at " + 
                    DateTimeFormatter.ISO_INSTANT.format(entry.getInstant()) +
                    "\n" + entry.getMessage()
                );
                Tooltip.install(eventRect, tooltip);
                
                ganttPane.getChildren().add(eventRect);
            }
            
            rowIndex++;
        }
        
        // Set pane height
        ganttPane.setPrefHeight(topMargin + 60 + (rowIndex * rowHeight));
    }
    
    private void drawTimelineAxis(Instant minTime, Instant maxTime, int leftMargin, int topMargin, int chartWidth) {
        // Draw horizontal line
        Rectangle axis = new Rectangle(leftMargin, topMargin, chartWidth, 2);
        axis.setFill(Color.BLACK);
        ganttPane.getChildren().add(axis);
        
        // Draw time markers
        int numMarkers = 5;
        long timeRangeMillis = maxTime.toEpochMilli() - minTime.toEpochMilli();
        
        for (int i = 0; i <= numMarkers; i++) {
            double x = leftMargin + (chartWidth * i / (double) numMarkers);
            long markerTimeMillis = minTime.toEpochMilli() + (timeRangeMillis * i / numMarkers);
            Instant markerTime = Instant.ofEpochMilli(markerTimeMillis);
            
            // Marker line
            Rectangle marker = new Rectangle(x, topMargin, 1, 10);
            marker.setFill(Color.BLACK);
            ganttPane.getChildren().add(marker);
            
            // Time label
            LocalDateTime ldt = LocalDateTime.ofInstant(markerTime, ZoneId.systemDefault());
            String timeLabel = ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            Text text = new Text(x - 20, topMargin + 25, timeLabel);
            text.setStyle("-fx-font-size: 10px;");
            ganttPane.getChildren().add(text);
        }
    }
    
    private String extractTimestamp(JsonNode node) {
        // Try common timestamp field names
        String[] timestampFields = {"@timestamp", "timestamp", "time", "date", "datetime"};
        for (String field : timestampFields) {
            if (node.has(field)) {
                JsonNode fieldNode = node.get(field);
                if (fieldNode.isTextual()) {
                    return fieldNode.asText();
                }
            }
        }
        return null;
    }
    
    private String extractCategory(JsonNode node) {
        // Try to use logger_name, otherwise use level
        if (node.has("logger_name")) {
            String logger = node.get("logger_name").asText();
            // Shorten long logger names
            if (logger.length() > 40) {
                int lastDot = logger.lastIndexOf('.');
                if (lastDot > 0) {
                    logger = "..." + logger.substring(lastDot);
                }
            }
            return logger;
        } else if (node.has("level")) {
            return node.get("level").asText();
        }
        return "UNKNOWN";
    }
    
    private Instant parseTimestamp(String timestamp) throws DateTimeParseException {
        // Try ISO instant format first
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            // Try other common formats
            try {
                LocalDateTime ldt = LocalDateTime.parse(timestamp);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                throw e; // Give up
            }
        }
    }
    
    private Color getColorForLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR", "FATAL" -> Color.rgb(220, 53, 69);
            case "WARN", "WARNING" -> Color.rgb(255, 193, 7);
            case "INFO" -> Color.rgb(23, 162, 184);
            case "DEBUG" -> Color.rgb(108, 117, 125);
            case "TRACE" -> Color.rgb(200, 200, 200);
            default -> Color.rgb(100, 100, 100);
        };
    }
    
    // Inner class for Gantt entries
    private static class GanttEntry {
        private final String category;
        private final Instant instant;
        private final String level;
        private final String message;
        
        public GanttEntry(String category, Instant instant, String level, String message) {
            this.category = category;
            this.instant = instant;
            this.level = level;
            this.message = message;
        }
        
        public String getCategory() {
            return category;
        }
        
        public Instant getInstant() {
            return instant;
        }
        
        public String getLevel() {
            return level;
        }
        
        public String getMessage() {
            return message.length() > 100 ? message.substring(0, 97) + "..." : message;
        }
    }
    
    private void showAlert(final String title, final String message) {
        final var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(final String[] args) {
        launch(args);
    }
}
