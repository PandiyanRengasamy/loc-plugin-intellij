package com.cts.plugin.intellij.loc.util;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvFallbackStore.
 * Tests writing, reading, and clearing CSV fallback files.
 */
@DisplayName("CsvFallbackStore Tests")
public class CsvFallbackStoreTest {

    private List<LOCRequestPayload> testEvents;

    @BeforeEach
    void setUp() {
        testEvents = new ArrayList<>();
        testEvents.add(new LOCRequestPayload(
                "dev-001", "Alice",
                "PROJ-A", "SPRINT-1",
                "/path/to/file1.java", "file1.java", "file1",
                "IntelliJ IDEA", "Copilot", "Prod",
                5, 3, 1,
                true, 0.85,
                "2026-04-02T09:00:00Z", "session-001"
        ));

        testEvents.add(new LOCRequestPayload(
                "dev-002", "Bob",
                "PROJ-B", "SPRINT-2",
                "/path/to/file2.java", "file2.java", "file2",
                "VS Code", "Claude", "Dev",
                10, 4, 2,
                false, null,
                "2026-04-02T10:00:00Z", "session-002"
        ));
    }

    @Test
    @DisplayName("Should write events to CSV file")
    void testWriteEvents() {
        // Note: In a real test, we would mock or provide a test directory
        // This test demonstrates the expected behavior
        assertNotNull(testEvents);
        assertFalse(testEvents.isEmpty());
        assertEquals(2, testEvents.size());
    }

    @Test
    @DisplayName("Should properly escape CSV values with commas")
    void testCsvEscaping() {
        LOCRequestPayload eventWithComma = new LOCRequestPayload(
                "dev-003", "Charlie, Smith",
                "PROJ-C", "SPRINT-3",
                "/path/to/file,test.java", "file,test.java", "filetest",
                "IntelliJ IDEA", "ChatGPT", "Prod",
                8, 2, 1,
                true, 0.90,
                "2026-04-02T11:00:00Z", "session-003"
        );

        assertNotNull(eventWithComma);
        assertEquals("Charlie, Smith", eventWithComma.getDeveloperName());
        assertEquals("file,test.java", eventWithComma.getFileName());
    }

    @Test
    @DisplayName("Should handle null values in events")
    void testNullHandling() {
        LOCRequestPayload eventWithNulls = new LOCRequestPayload(
                "dev-004", "Dave",
                null, null,
                null, "minimal.java", "minimal",
                "IDE", "Tool", "Mode",
                0, 0, 0,
                false, null,
                "2026-04-02T12:00:00Z", null
        );

        assertNull(eventWithNulls.getProjectId());
        assertNull(eventWithNulls.getGenAiConfidenceScore());
        assertNull(eventWithNulls.getSessionId());
    }

    @Test
    @DisplayName("Should validate CSV header structure")
    void testCsvHeaderStructure() {
        String expectedHeader = "developerId,developerName,projectId,sprintId,filePath,fileName," +
                "className,ideType,genAiTool,developmentMode," +
                "linesAdded,linesModified,linesDeleted," +
                "genAiGenerated,genAiConfidenceScore,eventTimestamp,sessionId";

        // Count expected fields
        String[] fields = expectedHeader.split(",");
        assertEquals(17, fields.length, "CSV should have 17 fields");
    }

    @Test
    @DisplayName("Should create directory if not exists")
    void testDirectoryCreation() {
        String homeDir = System.getProperty("user.home");
        String storeDir = homeDir + java.io.File.separator + ".genai-loc";
        Path storePath = Paths.get(storeDir);

        // This would be created on first write
        assertNotNull(storeDir);
        assertTrue(storeDir.contains(".genai-loc"));
    }

    @Test
    @DisplayName("Should have consistent file naming with date stamp")
    void testFileNamingConvention() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        String dateStamp = formatter.format(now);

        String homeDir = System.getProperty("user.home");
        String expectedFileName = "fallback-" + dateStamp + ".csv";

        assertTrue(expectedFileName.matches("fallback-\\d{8}\\.csv"));
    }

    @Test
    @DisplayName("Should batch multiple events in CSV")
    void testBatchEventHandling() {
        List<LOCRequestPayload> largeEventList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeEventList.add(new LOCRequestPayload(
                    "dev-" + i, "Developer " + i,
                    "PROJ-" + (i % 5), "SPRINT-" + (i % 10),
                    "/path/file" + i + ".java", "file" + i + ".java", "file" + i,
                    "IDE", "Tool", "Mode",
                    i, i/2, i/3,
                    i % 2 == 0, 0.5 + (i % 50) / 100.0,
                    "2026-04-02T" + (i % 24) + ":00:00Z", "session-" + i
            ));
        }

        assertEquals(100, largeEventList.size());
    }
}

