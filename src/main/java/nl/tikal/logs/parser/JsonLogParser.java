package nl.tikal.logs.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonLogParser {
    private final ObjectMapper objectMapper;

    public JsonLogParser() {
        this.objectMapper = new ObjectMapper();
    }

    public List<JsonNode> parseLogFile(String filePath) throws IOException {
        List<JsonNode> jsonObjects = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty()) {
                    continue;
                }
                
                try {
                    JsonNode jsonNode = objectMapper.readTree(line);
                    jsonObjects.add(jsonNode);
                } catch (IOException e) {
                    System.err.println("Failed to parse JSON on line " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        
        return jsonObjects;
    }

    public void processLogFile(String filePath) throws IOException {
        System.out.println("Parsing log file: " + filePath);
        List<JsonNode> jsonObjects = parseLogFile(filePath);
        
        System.out.println("Successfully parsed " + jsonObjects.size() + " JSON objects");
        
        for (int i = 0; i < jsonObjects.size(); i++) {
            JsonNode node = jsonObjects.get(i);
            System.out.println("\n--- Entry " + (i + 1) + " ---");
            System.out.println(node.toPrettyString());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar parse-json-logs.jar <log-file-path>");
            System.exit(1);
        }

        String logFilePath = args[0];
        JsonLogParser parser = new JsonLogParser();

        try {
            parser.processLogFile(logFilePath);
        } catch (IOException e) {
            System.err.println("Error processing log file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
