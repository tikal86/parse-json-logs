# Filter System Documentation

## Overview

The JSON Log Parser implements an Excel-style column filtering system that allows users to show/hide rows based on specific values in each column. Multiple filters can be active simultaneously, using AND logic (rows must match ALL active filters).

## Architecture

### Data Layer

The application uses JavaFX's `FilteredList` pattern:

```java
// In JsonLogParserApp
private ObservableList<JsonNode> allData;           // Original unfiltered data
private FilteredList<JsonNode> filteredData;        // Filtered view
private Map<String, Set<String>> columnFilters;     // Active filter state
```

- **`allData`**: Immutable source data, never modified by filters
- **`filteredData`**: Wrapper around `allData` with a predicate function
- **`columnFilters`**: Maps column name → set of allowed values

### UI Components

Each table column header contains:
1. A `Label` with the field name
2. A `Button` with "▼" that opens the filter dialog

```java
HBox headerBox = new HBox(5);
Label headerLabel = new Label(fieldName);
Button filterButton = new Button("▼");
filterButton.setOnAction(e -> showFilterDialog(fieldName));
headerBox.getChildren().addAll(headerLabel, filterButton);
column.setGraphic(headerBox);
```

## Filter Workflow

### 1. Opening the Filter Dialog

When a user clicks a column's filter button:

```java
showFilterDialog(String fieldName)
```

**Steps:**
1. Iterate through `allData` to collect all unique values for the field
2. Use `TreeSet` for automatic sorting of string values
3. Handle missing fields as empty string (`""`)
4. Convert non-text JSON nodes to string via `toString()`

```java
Set<String> uniqueValues = new TreeSet<>();
for (JsonNode node : allData) {
    JsonNode fieldNode = node.get(fieldName);
    String value;
    if (fieldNode == null) {
        value = "";
    } else if (fieldNode.isTextual()) {
        value = fieldNode.asText();
    } else {
        value = fieldNode.toString();
    }
    uniqueValues.add(value);
}
```

### 2. Displaying Current Filter State

The dialog shows checkboxes for each unique value:

- If **no filter exists** for this column → all checkboxes are checked
- If **filter exists** → only values in `columnFilters.get(fieldName)` are checked

```java
Set<String> currentFilter = columnFilters.get(fieldName);
for (String value : uniqueValues) {
    CheckBox checkBox = new CheckBox(displayValue);
    checkBox.setSelected(currentFilter == null || currentFilter.contains(value));
}
```

**Display logic:**
- Empty strings show as `"(empty)"` in the UI
- Stored internally as `""` in the `columnFilters` map

### 3. Applying the Filter

When the user clicks OK:

```java
// Collect selected values
Set<String> selectedValues = new HashSet<>();
for (Map.Entry<String, CheckBox> entry : checkBoxMap.entrySet()) {
    if (entry.getValue().isSelected()) {
        selectedValues.add(entry.getKey());
    }
}

// Update columnFilters map
if (selectedValues.size() == uniqueValues.size()) {
    // Optimization: all selected = no filter needed
    columnFilters.remove(fieldName);
} else {
    columnFilters.put(fieldName, selectedValues);
}

applyFilters();
```

**Key optimization:** If all values are selected, the filter is removed from the map entirely. This avoids unnecessary predicate checks.

### 4. Filter Predicate Evaluation

```java
private void applyFilters() {
    filteredData.setPredicate(node -> {
        // Check each active filter
        for (Map.Entry<String, Set<String>> filterEntry : columnFilters.entrySet()) {
            String fieldName = filterEntry.getKey();
            Set<String> allowedValues = filterEntry.getValue();
            
            // Extract value from this row
            JsonNode fieldNode = node.get(fieldName);
            String value;
            if (fieldNode == null) {
                value = "";
            } else if (fieldNode.isTextual()) {
                value = fieldNode.asText();
            } else {
                value = fieldNode.toString();
            }
            
            // If this column's filter rejects the value, reject the row
            if (!allowedValues.contains(value)) {
                return false;  // Short-circuit: row is hidden
            }
        }
        return true;  // Row passes all filters
    });
}
```

**Logic:**
- **AND semantics**: A row is visible only if it passes ALL active filters
- **Short-circuit evaluation**: As soon as one filter fails, return `false`
- **Empty `columnFilters`**: All rows pass (no filters active)

## Examples

### Example 1: Single Filter

**Scenario:** Filter the "level" column to show only "ERROR" and "WARN" entries.

**State after OK:**
```java
columnFilters = {
    "level": ["ERROR", "WARN"]
}
```

**Predicate evaluation for a row with `{"level": "INFO"}`:**
1. Check filter for "level"
2. Extract value: `"INFO"`
3. Is `"INFO"` in `["ERROR", "WARN"]`? → No
4. Return `false` → Row is hidden

### Example 2: Multiple Filters (AND Logic)

**Scenario:** 
- Filter "level" to show only "ERROR"
- Filter "message" to show only rows containing "timeout" or "failed"

**State:**
```java
columnFilters = {
    "level": ["ERROR"],
    "message": ["Connection timeout", "Request failed"]
}
```

**For a row `{"level": "ERROR", "message": "Connection timeout"}`:**
1. Check "level": `"ERROR"` in `["ERROR"]` → ✓ Pass
2. Check "message": `"Connection timeout"` in `["Connection timeout", "Request failed"]` → ✓ Pass
3. Return `true` → Row is visible

**For a row `{"level": "ERROR", "message": "Unknown error"}`:**
1. Check "level": `"ERROR"` in `["ERROR"]` → ✓ Pass
2. Check "message": `"Unknown error"` in `["Connection timeout", "Request failed"]` → ✗ Fail
3. Return `false` → Row is hidden

### Example 3: Handling Missing Fields

**Scenario:** Filter "optional_field" to show only empty values and "value1"

**State:**
```java
columnFilters = {
    "optional_field": ["", "value1"]
}
```

**For a row `{"level": "INFO"}` (missing "optional_field"):**
1. Extract value: `fieldNode = null` → `value = ""`
2. Is `""` in `["", "value1"]`? → ✓ Yes
3. Return `true` → Row is visible

## Status Bar Updates

The status bar shows real-time filter statistics:

```java
int activeFilters = columnFilters.size();
String filterStatus = activeFilters > 0 ? " (" + activeFilters + " filter(s) active)" : "";
statusLabel.setText("Showing " + filteredData.size() + " of " + allData.size() + " entries" + filterStatus);
```

**Example output:** `"Showing 42 of 2151 entries (2 filter(s) active)"`

## Clearing Filters

The "Clear" button resets everything:

```java
private void clearTable() {
    tableView.getColumns().clear();
    tableView.getItems().clear();
    columnFilters.clear();           // Remove all filters
    allData = null;
    filteredData = null;
    statusLabel.setText("Table cleared. Load a new file to continue.");
}
```

## Performance Characteristics

- **Filter application**: O(R × F × V) where:
  - R = number of rows in `allData`
  - F = number of active filters (`columnFilters.size()`)
  - V = average values per filter set (typically small)

- **UI collection of unique values**: O(R) - scans all rows once per filter dialog open

- **Memory**: Filters store only value sets, not row indices. For a column with 10 unique values across 10,000 rows, only 10 strings are stored in the filter.

## Design Decisions

### Why `FilteredList` instead of copying data?

- **Reactive**: UI updates automatically when predicate changes
- **Memory efficient**: No duplicate data structures
- **Composable**: Can wrap with `SortedList` for sorting without breaking filters

### Why store allowed values instead of excluded values?

- **User model**: "Show these values" is more intuitive than "Hide these values"
- **Default behavior**: No filter = all allowed (simpler than "no filter = none excluded")
- **Explicit state**: Selected checkboxes directly map to stored values

### Why remove filters when all values are selected?

- **Performance**: Avoid unnecessary predicate checks when filter is effectively a no-op
- **Clarity**: `columnFilters.isEmpty()` means "no filtering active"
- **Invariant**: `columnFilters` only contains filters that actually restrict rows
