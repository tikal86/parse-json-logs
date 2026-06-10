# JSON Log Parser

A Maven-based Java application for parsing log files containing JSON objects (one per line).

## Features

- Parses log files with one JSON object per line
- Handles malformed JSON gracefully by skipping invalid lines
- JavaFX GUI with table view displaying JSON fields as columns
- **Column visibility selector** - show/hide columns as needed
- Dynamically adapts columns based on JSON structure
- Handles missing fields - shows empty cells for missing values
- Supports nested JSON - displays as string representation
- Error handling - gracefully handles malformed JSON
- Command-line interface also available
- Includes unit tests

## GUI Features

1. **Load Log File** - Open and parse JSON log files
2. **Select Columns** - Choose which columns to display:
   - Shows all available columns with checkboxes
   - Select All / Deselect All buttons for convenience
   - Hides unchecked columns from the table view
   - Status bar shows count of visible columns
3. **Excel-Style Column Filters** - Filter data by column values:
   - Each column header has a filter button (▼)
   - Click to see all unique values in that column
   - Check/uncheck values to show/hide rows
   - Multiple filters can be active simultaneously
   - Select All / Deselect All for quick selection
   - Status bar shows filtered row count and active filter count
4. **Clear** - Reset the table view and all filters

## Building the Project

```bash
mvn clean package
```

## Running the GUI Application

```bash
mvn javafx:run
```

Or run the compiled JAR:
```bash
java -jar target/parse-json-logs-1.0-SNAPSHOT.jar
```

## Running the Command-Line Application

```bash
java -cp target/parse-json-logs-1.0-SNAPSHOT.jar nl.tikal.logs.parser.JsonLogParser <path-to-log-file>
```

## Example Log File Format

```
{"level":"INFO","message":"Application started","timestamp":"2024-01-01T10:00:00Z"}
{"level":"DEBUG","message":"Processing request","timestamp":"2024-01-01T10:01:00Z"}
{"level":"ERROR","message":"Connection failed","timestamp":"2024-01-01T10:02:00Z"}
```

## Running Tests

```bash
mvn test
```

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
