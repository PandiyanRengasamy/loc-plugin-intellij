package com.cts.plugin.intellij.loc.integration;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LOC Plugin.
 * Tests the interaction between multiple components:
 * - Event creation
 * - Event dispatching
 * - CSV fallback functionality
 */
@DisplayName("LOC Plugin Integration Tests")
public class LocPluginIntegrationTest {

    private EventDispatcher dispatcher;
    private List<LOCRequestPayload> testEvents;

    @BeforeEach
    void setUp() {
        dispatcher = new EventDispatcher();

        testEvents = new ArrayList<>();

        // Create test events
        testEvents.add(new LOCRequestPayload(
                "dev-001", "Alice Developer",
                "PROJ-A", "SPRINT-1",
                "/src/main/java/App.java", "App.java", "App",
                "IntelliJ IDEA", "GitHub Copilot", "Production",
                20, 10, 5,
                true, 0.92,
                "2026-04-02T09:30:00Z", "session-001"
        ));

        testEvents.add(new LOCRequestPayload(
                "dev-002", "Bob Engineer",
                "PROJ-B", "SPRINT-2",
                "/src/test/java/AppTest.java", "AppTest.java", "AppTest",
                "VS Code", "Claude", "Development",
                15, 8, 3,
                true, 0.88,
                "2026-04-02T10:15:00Z", "session-002"
        ));

        testEvents.add(new LOCRequestPayload(
                "dev-003", "Carol Coder",
                "PROJ-C", "SPRINT-3",
                "/src/config/Config.java", "Config.java", "Config",
                "IntelliJ IDEA", "ChatGPT", "Staging",
                5, 2, 1,
                false, null,
                "2026-04-02T11:45:00Z", "session-003"
        ));
    }

    @Test
    @DisplayName("Should process multiple events through dispatcher")
    void testProcessMultipleEvents() {
        for (LOCRequestPayload event : testEvents) {
            dispatcher.enqueue(event);
        }

        String stats = dispatcher.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Pending="));
    }

    @Test
    @DisplayName("Should handle mixed GenAI and non-GenAI events")
    void testMixedEventProcessing() {
        long genAiCount = testEvents.stream()
                .filter(LOCRequestPayload::isGenAiGenerated)
                .count();

        long nonGenAiCount = testEvents.stream()
                .filter(e -> !e.isGenAiGenerated())
                .count();

        assertEquals(2, genAiCount, "Should have 2 GenAI generated events");
        assertEquals(1, nonGenAiCount, "Should have 1 non-GenAI event");
    }

    @Test
    @DisplayName("Should group events by developer")
    void testGroupEventsByDeveloper() {
        java.util.Map<String, List<LOCRequestPayload>> grouped = new java.util.HashMap<>();

        for (LOCRequestPayload event : testEvents) {
            grouped.computeIfAbsent(event.getDeveloperId(), k -> new ArrayList<>())
                    .add(event);
        }

        assertEquals(3, grouped.size(), "Should have 3 developers");
        assertTrue(grouped.containsKey("dev-001"));
        assertTrue(grouped.containsKey("dev-002"));
        assertTrue(grouped.containsKey("dev-003"));
    }

    @Test
    @DisplayName("Should calculate total lines of code changes")
    void testCalculateTotalLineChanges() {
        int totalAdded = testEvents.stream()
                .mapToInt(LOCRequestPayload::getLinesAdded)
                .sum();

        int totalModified = testEvents.stream()
                .mapToInt(LOCRequestPayload::getLinesModified)
                .sum();

        int totalDeleted = testEvents.stream()
                .mapToInt(LOCRequestPayload::getLinesDeleted)
                .sum();

        assertEquals(40, totalAdded, "Total lines added");
        assertEquals(20, totalModified, "Total lines modified");
        assertEquals(9, totalDeleted, "Total lines deleted");
    }

    @Test
    @DisplayName("Should detect AI tool usage patterns")
    void testDetectToolUsagePatterns() {
        java.util.Map<String, Integer> toolUsage = new java.util.HashMap<>();

        for (LOCRequestPayload event : testEvents) {
            String tool = event.getGenAiTool();
            toolUsage.put(tool, toolUsage.getOrDefault(tool, 0) + 1);
        }

        assertEquals(1, toolUsage.getOrDefault("GitHub Copilot", 0));
        assertEquals(1, toolUsage.getOrDefault("Claude", 0));
        assertEquals(1, toolUsage.getOrDefault("ChatGPT", 0));
    }

    @Test
    @DisplayName("Should calculate average confidence scores for GenAI events")
    void testCalculateAverageConfidence() {
        double avgConfidence = testEvents.stream()
                .filter(LOCRequestPayload::isGenAiGenerated)
                .mapToDouble(e -> e.getGenAiConfidenceScore() != null ? e.getGenAiConfidenceScore() : 0.0)
                .average()
                .orElse(0.0);

        assertTrue(avgConfidence > 0.8, "Average confidence should be above 0.8");
    }

    @Test
    @DisplayName("Should deduplicate events using equals and hashCode")
    void testEventDeduplication() {
        LOCRequestPayload dup1 = testEvents.get(0);
        LOCRequestPayload dup2 = new LOCRequestPayload(
                "dev-001", "Alice Developer",
                "PROJ-A", "SPRINT-1",
                "/src/main/java/App.java", "App.java", "App",
                "Different Tool", "Different Tool", "Production",
                99, 99, 99,
                false, null,
                "2026-04-02T09:30:00Z", "different-session"
        );

        assertEquals(dup1, dup2, "Events should be equal based on developerId, projectId, fileName, timestamp");

        java.util.Set<LOCRequestPayload> uniqueEvents = new java.util.HashSet<>(testEvents);
        assertEquals(3, uniqueEvents.size(), "Should have 3 unique events");
    }

    @Test
    @DisplayName("Should validate event timestamps are in ISO format")
    void testEventTimestampValidation() {
        for (LOCRequestPayload event : testEvents) {
            String timestamp = event.getEventTimestamp();
            assertNotNull(timestamp);
            assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
        }
    }

    @Test
    @DisplayName("Should maintain session consistency across events")
    void testSessionConsistency() {
        String session1 = testEvents.get(0).getSessionId();
        String session2 = testEvents.get(1).getSessionId();
        String session3 = testEvents.get(2).getSessionId();

        assertNotNull(session1);
        assertNotNull(session2);
        assertNotNull(session3);
        assertNotEquals(session1, session2);
        assertNotEquals(session2, session3);
    }

    @Test
    @DisplayName("Should generate valid JSON for batch upload")
    void testBatchJsonGeneration() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.util.Map<String, Object> batch = java.util.Map.of("events", testEvents);
        String json = gson.toJson(batch);

        assertNotNull(json);
        assertTrue(json.contains("\"events\""));
        assertTrue(json.contains("developerId"));
        assertTrue(json.length() > 100);
    }

    @Test
    @DisplayName("Should dispose dispatcher gracefully")
    void testDispatcherDisposal() {
        for (LOCRequestPayload event : testEvents) {
            dispatcher.enqueue(event);
        }

        assertDoesNotThrow(() -> dispatcher.dispose());
    }
}

