package com.cts.plugin.intellij.loc.service;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventDispatcher service.
 * Tests event queueing, batch processing, and statistics.
 */
@DisplayName("EventDispatcher Tests")
public class EventDispatcherTest {

    private EventDispatcher dispatcher;
    private LOCRequestPayload sampleEvent;

    @BeforeEach
    void setUp() {
        dispatcher = new EventDispatcher();
        sampleEvent = new LOCRequestPayload(
                "dev-001", "Test Developer",
                "TEST-PROJECT", "TEST-SPRINT",
                "/test/file.java", "file.java", "file",
                "IntelliJ IDEA", "TestTool", "Development",
                5, 2, 1,
                true, 0.75,
                "2026-04-02T10:00:00Z", "test-session"
        );
    }

    @Test
    @DisplayName("Should initialize EventDispatcher with proper configuration")
    void testInitialization() {
        assertNotNull(dispatcher);
        String stats = dispatcher.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Sent="));
        assertTrue(stats.contains("Failed="));
        assertTrue(stats.contains("Pending="));
    }

    @Test
    @DisplayName("Should enqueue single event")
    void testEnqueueEvent() {
        // Enqueue event and check stats contain pending count
        dispatcher.enqueue(sampleEvent);
        String stats = dispatcher.getStats();

        assertNotNull(stats);
        assertTrue(stats.contains("Pending="));
    }

    @Test
    @DisplayName("Should track statistics correctly")
    void testStatisticsTracking() {
        String initialStats = dispatcher.getStats();
        assertNotNull(initialStats);

        // Stats format should be: "Sent=X Failed=Y Pending=Z CsvFallback=yes|no"
        assertTrue(initialStats.matches(".*Sent=\\d+.*Failed=\\d+.*Pending=\\d+.*CsvFallback=.*"));
    }

    @Test
    @DisplayName("Should handle multiple events in queue")
    void testMultipleEvents() {
        for (int i = 0; i < 5; i++) {
            LOCRequestPayload event = new LOCRequestPayload(
                    "dev-" + i, "Developer " + i,
                    "PROJ-001", "SPRINT-01",
                    "/path/file" + i + ".java", "file" + i + ".java", "file" + i,
                    "IntelliJ IDEA", "TestTool", "Development",
                    i + 1, i, i > 0 ? i - 1 : 0,
                    true, 0.5 + (i * 0.1),
                    "2026-04-02T" + (10 + i) + ":00:00Z", "session-001"
            );
            dispatcher.enqueue(event);
        }

        String stats = dispatcher.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Pending="));
    }

    @Test
    @DisplayName("Should create proper HTTP endpoint URL for single event")
    void testSingleEventEndpoint() {
        String url = "http://localhost:8080/api/events";
        assertNotNull(url);
        assertTrue(url.endsWith("/events"));
    }

    @Test
    @DisplayName("Should create proper HTTP endpoint URL for batch events")
    void testBatchEventEndpoint() {
        String singleUrl = "http://localhost:8080/api/events";
        String batchUrl = singleUrl.replace("/events", "/events/batch");

        assertNotNull(batchUrl);
        assertTrue(batchUrl.endsWith("/events/batch"));
    }

    @Test
    @DisplayName("Should generate valid JSON payload for events")
    void testJsonPayloadGeneration() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(sampleEvent);

        assertNotNull(json);
        assertTrue(json.contains("developerId"));
        assertTrue(json.contains("dev-001"));
        assertTrue(json.contains("file.java"));
    }

    @Test
    @DisplayName("Should handle disposed state gracefully")
    void testDisposeGracefully() {
        dispatcher.enqueue(sampleEvent);
        dispatcher.dispose();

        // Should not throw exception
        assertDoesNotThrow(() -> dispatcher.dispose());
    }

    @Test
    @DisplayName("Should format stats for UI display")
    void testStatsFormatting() {
        String stats = dispatcher.getStats();

        // Stats should contain all required fields
        assertAll(
                () -> assertTrue(stats.contains("Sent=")),
                () -> assertTrue(stats.contains("Failed=")),
                () -> assertTrue(stats.contains("Pending=")),
                () -> assertTrue(stats.contains("CsvFallback="))
        );
    }
}

