package nl.tikal.logs.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonLogParserTest {

    @Test
    void testParseValidJsonLog(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test.log");
        String content = """
                {"level":"INFO","message":"Application started","timestamp":"2024-01-01T10:00:00Z"}
                {"level":"DEBUG","message":"Processing request","timestamp":"2024-01-01T10:01:00Z"}
                {"level":"ERROR","message":"Connection failed","timestamp":"2024-01-01T10:02:00Z"}
                """;
        Files.writeString(logFile, content);

        JsonLogParser parser = new JsonLogParser();
        List<JsonNode> result = parser.parseLogFile(logFile.toString());

        assertEquals(3, result.size());
        assertEquals("INFO", result.get(0).get("level").asText());
        assertEquals("Application started", result.get(0).get("message").asText());
    }

    @Test
    void testParseEmptyLines(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test.log");
        String content = """
                {"level":"INFO","message":"First"}
                
                {"level":"INFO","message":"Second"}
                """;
        Files.writeString(logFile, content);

        JsonLogParser parser = new JsonLogParser();
        List<JsonNode> result = parser.parseLogFile(logFile.toString());

        assertEquals(2, result.size());
    }

    @Test
    void testParseInvalidJson(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("test.log");
        String content = """
                {"level":"INFO","message":"Valid"}
                {invalid json}
                {"level":"INFO","message":"Another valid"}
                """;
        Files.writeString(logFile, content);

        JsonLogParser parser = new JsonLogParser();
        List<JsonNode> result = parser.parseLogFile(logFile.toString());

        assertEquals(2, result.size());
    }
    @Test

    void testParseFile() throws IOException {
        Path logFile = Path.of("src/test/resources/sample.log");

        JsonLogParser parser = new JsonLogParser();
        List<JsonNode> result = parser.parseLogFile(logFile.toString());

        assertEquals(5, result.size());
    }
}
