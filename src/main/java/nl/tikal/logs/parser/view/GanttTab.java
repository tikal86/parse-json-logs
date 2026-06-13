package nl.tikal.logs.parser.view;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import nl.tikal.logs.parser.JsonLogParserApp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GanttTab extends Tab {
    private ScrollPane ganttScrollPane;
    private Pane ganttPane;
    private ObservableList<JsonNode> allData;

    public GanttTab() {
        this.setClosable(false);
        ganttPane = new Pane();
        ganttScrollPane = new ScrollPane(ganttPane);
        ganttScrollPane.setFitToWidth(true);
        ganttScrollPane.setStyle("-fx-background-color: white;");
        this.setContent(ganttScrollPane);
    }

    public void buildGanttChart() {
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

}
