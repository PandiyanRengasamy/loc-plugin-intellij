package com.cts.plugin.intellij.loc.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CodeEventRequest model class.
 * Tests construction, getters, equals, and hashCode methods.
 */
@DisplayName("CodeEventRequest Tests")
public class CodeEventRequestTest {

    private LOCRequestPayload event1;
    private LOCRequestPayload event2;
    private LOCRequestPayload event3;

    @BeforeEach
    void setUp() {
        event1 = new LOCRequestPayload(
                "dev-123", "John Doe",
                "PROJ-001", "SPRINT-01",
                "/home/user/MyProject/src/Main.java", "Main.java", "Main",
                "IntelliJ IDEA", "GitHub Copilot", "Production",
                10, 5, 2,
                true, 0.95,
                "2026-04-02T10:30:00Z", "session-abc123"
        );

        event2 = new LOCRequestPayload(
                "dev-123", "John Doe",
                "PROJ-001", "SPRINT-01",
                "/home/user/MyProject/src/Main.java", "Main.java", "Main",
                "IntelliJ IDEA", "Claude", "Production",
                10, 5, 2,
                true, 0.92,
                "2026-04-02T10:30:00Z", "session-xyz789"
        );

        event3 = new LOCRequestPayload(
                "dev-456", "Jane Smith",
                "PROJ-002", "SPRINT-02",
                "/home/user/OtherProject/Utils.java", "Utils.java", "Utils",
                "VS Code", "ChatGPT", "Development",
                15, 8, 3,
                false, null,
                "2026-04-02T11:45:00Z", "session-def456"
        );
    }

    @Test
    @DisplayName("Should construct CodeEventRequest with all fields")
    void testConstructor() {
        assertNotNull(event1);
        assertEquals("dev-123", event1.getDeveloperId());
        assertEquals("John Doe", event1.getDeveloperName());
        assertEquals("PROJ-001", event1.getProjectId());
        assertEquals("SPRINT-01", event1.getSprintId());
        assertEquals("Main.java", event1.getFileName());
        assertEquals("GitHub Copilot", event1.getGenAiTool());
        assertEquals(10, event1.getLinesAdded());
        assertEquals(5, event1.getLinesModified());
        assertEquals(2, event1.getLinesDeleted());
        assertTrue(event1.isGenAiGenerated());
        assertEquals(0.95, event1.getGenAiConfidenceScore());
    }

    @Test
    @DisplayName("Should generate correct toString representation")
    void testToString() {
        String str = event1.toString();
        assertNotNull(str);
        assertTrue(str.contains("Main.java"));
        assertTrue(str.contains("dev-123"));
        assertTrue(str.contains("GitHub Copilot"));
    }

    @Test
    @DisplayName("Should consider two events equal if they have same developerId, projectId, fileName, and timestamp")
    void testEquals() {
        // event1 and event2 have same developerId, projectId, fileName, timestamp
        // but different genAiTool and sessionId — they should be equal
        assertEquals(event1, event2);

        // event1 and event3 have different developerId and projectId — not equal
        assertNotEquals(event1, event3);
    }

    @Test
    @DisplayName("Should generate same hashCode for events with same developerId, projectId, fileName, and timestamp")
    void testHashCode() {
        // event1 and event2 should have the same hash code
        assertEquals(event1.hashCode(), event2.hashCode());

        // event1 and event3 should have different hash codes
        assertNotEquals(event1.hashCode(), event3.hashCode());
    }

    @Test
    @DisplayName("Should use hashCode and equals in HashSet deduplication")
    void testHashSetDeduplication() {
        java.util.Set<LOCRequestPayload> set = new java.util.HashSet<>();
        set.add(event1);
        set.add(event2);  // Same hash/equals as event1, should not be added
        set.add(event3);

        assertEquals(2, set.size(), "Set should contain only 2 unique events");
        assertTrue(set.contains(event1));
        assertTrue(set.contains(event3));
    }

    @Test
    @DisplayName("Should handle null confidence score")
    void testNullConfidenceScore() {
        assertNull(event3.getGenAiConfidenceScore());
    }

    @Test
    @DisplayName("Should not be equal to null or different class")
    void testEqualsWithNull() {
        assertNotEquals(event1, null);
        assertNotEquals(event1, "not an event");
        assertNotEquals(event1, new Object());
    }

    @Test
    @DisplayName("Should be equal to itself")
    void testEqualsSelf() {
        assertEquals(event1, event1);
    }
}

