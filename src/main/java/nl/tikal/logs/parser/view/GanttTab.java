package nl.tikal.logs.parser.view;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GanttTab extends Tab {
    private static final int EVENTHEIGHT = 20;
    private static final int MIN_FILTER_LENGTH = 3;
    private final ScrollPane ganttScrollPane;
    private final Pane ganttPane;
    private final TextField categoryFilterField;
    private final ContextMenu categorySuggestions;
    private ObservableList<JsonNode> allData;

    public GanttTab(final String name) {
        super(name);
        this.setClosable(false);
        ganttPane = new Pane();
        ganttScrollPane = new ScrollPane(ganttPane);
        ganttScrollPane.setFitToWidth(true);
        ganttScrollPane.setStyle("-fx-background-color: white;");

        categoryFilterField = new TextField();
        categoryFilterField.setPromptText("Type at least " + MIN_FILTER_LENGTH + " characters...");
        categoryFilterField.setPrefColumnCount(30);
        categorySuggestions = new ContextMenu();
        categoryFilterField.textProperty().addListener((obs, oldText, newText) -> {
            showCategorySuggestions(newText);
            renderGanttChart();
        });

        final var clearButton = new Button("Clear");
        clearButton.setOnAction(e -> categoryFilterField.clear());

        final var filterBar = new HBox(10, new Label("Category filter:"), categoryFilterField, clearButton);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(8));

        final var root = new BorderPane();
        root.setTop(filterBar);
        root.setCenter(ganttScrollPane);
        this.setContent(root);
    }

    /**
     * Shows the categories matching the typed text, once enough characters are typed.
     */
    private void showCategorySuggestions(final String text) {
        categorySuggestions.hide();
        if (text == null || text.trim().length() < MIN_FILTER_LENGTH || allData == null) {
            return;
        }

        final var search = text.trim().toLowerCase();
        final var matches = availableCategories().stream()
                                                 .filter(category -> category.toLowerCase().contains(search))
                                                 .limit(20)
                                                 .toList();
        if (matches.isEmpty()) {
            return;
        }

        categorySuggestions.getItems().clear();
        for (final var category : matches) {
            final var item = new MenuItem(category);
            item.setOnAction(e -> categoryFilterField.setText(category));
            categorySuggestions.getItems().add(item);
        }
        categorySuggestions.show(categoryFilterField, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private Set<String> availableCategories() {
        final Set<String> categories = new TreeSet<>();
        if (allData != null) {
            for (final var node : allData) {
                categories.add(extractCategory(node));
            }
        }
        return categories;
    }

    private boolean matchesCategoryFilter(final String category) {
        final var text = categoryFilterField.getText();
        if (text == null || text.trim().length() < MIN_FILTER_LENGTH) {
            return true;
        }
        return category.toLowerCase().contains(text.trim().toLowerCase());
    }

    public void buildGanttChart(final List<JsonNode> jsonObjects) {
        allData = FXCollections.observableArrayList(jsonObjects);
        categoryFilterField.clear();
        renderGanttChart();
    }

    private void renderGanttChart() {
        ganttPane.getChildren().clear();

        if (allData == null || allData.isEmpty()) {
            return;
        }

        // Extract timestamps and categorize by logger_name or level
        final List<GanttEntry> entries = new ArrayList<>();

        for (final var node : allData) {
            final var timestamp = extractTimestamp(node);
            if (timestamp == null) {
                continue;
            }

            final var category = extractCategory(node);
            if (!matchesCategoryFilter(category)) {
                continue;
            }
            final var level = node.has("level") ? node.get("level").asText() : "UNKNOWN";
            final var message = node.has("message") ? node.get("message").asText() : "";

            try {
                final var instant = parseTimestamp(timestamp);
                entries.add(new GanttEntry(category, instant, level, message));
            } catch (final Exception e) {
                // Skip entries with unparseable timestamps
            }
        }

        if (entries.isEmpty()) {
            final var filterText = categoryFilterField.getText() == null ? "" : categoryFilterField.getText().trim();
            final var message = filterText.length() < MIN_FILTER_LENGTH
                    ? "No timeline data available (no valid @timestamp fields found)"
                    : "No categories match \"" + filterText + "\"";
            final var noData = new Text(50, 50, message);
            noData.setStyle("-fx-font-size: 14px;");
            ganttPane.getChildren().add(noData);
            return;
        }

        // Sort by timestamp
        entries.sort(Comparator.comparing(GanttEntry::getInstant));

        // Find time range
        final var minTime = entries.getFirst().getInstant();
        final var maxTime = entries.getLast().getInstant();
        var timeRangeMillis = maxTime.toEpochMilli() - minTime.toEpochMilli();

        if (timeRangeMillis == 0) {
            timeRangeMillis = 1; // Avoid division by zero
        }

        // Group by category
        final var groupedEntries = entries.stream()
                                          .collect(Collectors.groupingBy(GanttEntry::getCategory));

        // Layout constants
        final var leftMargin = 240;
        final var topMargin = 50;
        final var rowHeight = 30;
        final var chartWidth = 900;

        // Draw timeline axis
        drawTimelineAxis(minTime, maxTime, leftMargin, topMargin, chartWidth);

        // Draw category rows
        var rowIndex = 0;
        final var comparator = new Comparator<List<GanttEntry>>() {

            @Override
            public int compare(final List<GanttEntry> o1, final List<GanttEntry> o2) {
                return o1.getFirst().getInstant().compareTo(o2.getFirst().getInstant());
            }
        };

        var sorted =
                groupedEntries.entrySet().stream()
                   .sorted(Map.Entry.comparingByValue(comparator)).collect(ArrayList::new, (map, entry) -> map.add(entry.getKey()), ArrayList::addAll);
//        final List<String> categories = new ArrayList<String>(sorted.keySet());
//        categories.sort(category -> category.);

        for (final var category : sorted) {
            final var y = topMargin + 30 + (rowIndex * rowHeight);

            // Category label
            final var label = new Text(10, y + 15, (String) category);
            label.setStyle("-fx-font-size: 11px; -fx-font-family: monospace;");
            ganttPane.getChildren().add(label);

            // Category row background
            final var rowBg = new Rectangle(leftMargin, y, chartWidth, rowHeight);
            rowBg.setFill(rowIndex % 2 == 0 ? Color.rgb(245, 245, 245) : Color.WHITE);
            rowBg.setStroke(Color.rgb(220, 220, 220));
            ganttPane.getChildren().add(rowBg);

            // Draw events for this category
//            System.out.println("leftMargin: " + leftMargin + ", chartWidth: " + chartWidth);
            final var categoryEntries = groupedEntries.get(category);
//            System.out.println("categoryEntries size: " + categoryEntries.size() + ", chartWidth: " + chartWidth);
            final var first = categoryEntries.getFirst();
            final var offset = first.getInstant().toEpochMilli() - minTime.toEpochMilli();
            final var x = leftMargin + (chartWidth * offset / (double) timeRangeMillis);
            if (categoryEntries.size() > 1 ) {
                System.out.println("Entry: " + first.getCategory() + ", Time: " + first.getInstant());
                System.out.println("Entry: " + first.getCategory() + ", Offset: " + offset);
                System.out.println("Entry: " + first.getCategory() + ", Offset: " + offset);
                System.out.println("Entry timerange factor: " + offset / (double) timeRangeMillis + ", X: " + x);
                final var last = categoryEntries.getLast();
                final var lastEntryInstant = last.getInstant();
                final var lastEntryOffset = lastEntryInstant.toEpochMilli() - minTime.toEpochMilli();
                final var lastEntryX = leftMargin + (chartWidth * lastEntryOffset / (double) timeRangeMillis);
                final var proportionalWidth = lastEntryX - x;
                System.out.println("LastEntry: " + last.getCategory() + ", Time: " + last.getInstant() );
                System.out.println("LastEntry: " + last.getCategory() + ", Offset: " + lastEntryOffset);
                System.out.println("Entry timerange factor: " + lastEntryOffset / (double) timeRangeMillis + ", lastEntryX: " + lastEntryX);
                System.out.println("LastEntry: " + last.getCategory() + ", proportionalWidth: " + proportionalWidth);
                drawEventRect(x, y, proportionalWidth, first);
//                for (final var entry : categoryEntries) {
//                    //                System.out.println("Entry: " + entry.getCategory() + ", Time: " + entry.getInstant());
//                    final var offset = entry.getInstant().toEpochMilli() - minTime.toEpochMilli();
//                    //                System.out.println("Entry: " + entry.getCategory() + ", Offset: " + offset);
//                    final var x = leftMargin + (chartWidth * offset / (double) timeRangeMillis);
//                    //                System.out.println("Entry timerange factor: " + offset / (double) timeRangeMillis + ", X: " + x);
//
//                    final var width = 40;
//                    // make width proportional to instant of last entry in group
//                    final var lastEntryInstant = categoryEntries.get(categoryEntries.size() - 1).getInstant();
//                    //                System.out.println("LastEntry: " + entry.getCategory() + ", Number: " + (categoryEntries.size() - 1) );
//                    final var lastEntryOffset = lastEntryInstant.toEpochMilli() - minTime.toEpochMilli();
//                    //                System.out.println("LastEntry: " + entry.getCategory() + ", Offset: " + lastEntryOffset);
//                    final var lastEntryX = leftMargin + (chartWidth * lastEntryOffset / (double) timeRangeMillis);
//                    //                System.out.println("Entry timerange factor: " + lastEntryOffset / (double) timeRangeMillis + ", lastEntryX: " + lastEntryX);
//                    final var proportionalWidth = lastEntryX - x;
//                    //                System.out.println("LastEntry: " + entry.getCategory() + ", proportionalWidth: " + proportionalWidth);
//                }
            } else {
                drawEventRect(x, y, 20, first);

            }

            rowIndex++;
        }

        // Set pane height
        ganttPane.setPrefHeight(topMargin + 60 + (rowIndex * rowHeight));
    }

    private void drawEventRect(final double x, final double y, final double proportionalWidth, final GanttEntry entry) {
        // Event marker (small rectangle)
        var width = proportionalWidth;
        if (proportionalWidth < 10) {
            width = 100;
        }
        final var eventRect = new Rectangle(x - 2, y + 5, width, EVENTHEIGHT);
        eventRect.setFill(getColorForLevel(entry.getLevel()));
        eventRect.setStroke(Color.BLACK);
        eventRect.setStrokeWidth(0.5);

        // Tooltip
        final var tooltip = new Tooltip(entry.getLevel() +
                                        " at " +
                                        DateTimeFormatter.ISO_INSTANT.format(entry.getInstant()) +
                                        "\n" +
                                        entry.getMessage());
        Tooltip.install(eventRect, tooltip);

        ganttPane.getChildren().add(eventRect);

    }
    private void drawTimelineAxis(final Instant minTime, final Instant maxTime, final int leftMargin, final int topMargin, final int chartWidth) {
        // Draw horizontal line
        final var axis = new Rectangle(leftMargin, topMargin, chartWidth, 2);
        axis.setFill(Color.BLACK);
        ganttPane.getChildren().add(axis);

        // Draw time markers
        final var numMarkers = 5;
        final var timeRangeMillis = maxTime.toEpochMilli() - minTime.toEpochMilli();

        for (var i = 0; i <= numMarkers; i++) {
            final var x = leftMargin + (chartWidth * i / (double) numMarkers);
            final var markerTimeMillis = minTime.toEpochMilli() + (timeRangeMillis * i / numMarkers);
            final var markerTime = Instant.ofEpochMilli(markerTimeMillis);

            // Marker line
            final var marker = new Rectangle(x, topMargin, 1, 10);
            marker.setFill(Color.BLACK);
            ganttPane.getChildren().add(marker);

            // Time label
            final var ldt = LocalDateTime.ofInstant(markerTime, ZoneId.systemDefault());
            final var timeLabel = ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            final var text = new Text(x - 20, topMargin + 25, timeLabel);
            text.setStyle("-fx-font-size: 10px;");
            ganttPane.getChildren().add(text);
        }
    }

    private String extractTimestamp(final JsonNode node) {
        // Try common timestamp field names
        final String[] timestampFields = {"@timestamp", "timestamp", "time", "date", "datetime"};
        for (final var field : timestampFields) {
            if (node.has(field)) {
                final var fieldNode = node.get(field);
                if (fieldNode.isTextual()) {
                    return fieldNode.asText();
                }
            }
        }
        return null;
    }

    private String extractCategory(final JsonNode node) {
        // Try to use logger_name, otherwise use level
        if (node.has("trace_id")) {
            var traceId = node.get("trace_id").asText();
            // Shorten long traceId names
            if (traceId.length() > 40) {
                final var lastDot = traceId.lastIndexOf('.');
                if (lastDot > 0) {
                    traceId = "..." + traceId.substring(lastDot);
                }
            }
            return traceId;
        } else if (node.has("level")) {
            return node.get("level").asText();
        }
        return "UNKNOWN";
    }

    private Instant parseTimestamp(final String timestamp) throws DateTimeParseException {
        // Try ISO instant format first
        try {
            return Instant.parse(timestamp);
        } catch (final DateTimeParseException e) {
            // Try other common formats
            try {
                final var ldt = LocalDateTime.parse(timestamp);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (final DateTimeParseException e2) {
                throw e; // Give up
            }
        }
    }

    private Color getColorForLevel(final String level) {
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

        public GanttEntry(final String category, final Instant instant, final String level, final String message) {
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

}
