package com.cts.plugin.intellij.loc.testutil;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;

/**
 * TestEventBuilder - Fluent builder for creating test CodeEventRequest objects.
 * 
 * Simplifies test setup by providing sensible defaults and a fluent API.
 * 
 * Example:
 * <pre>
 * CodeEventRequest event = new TestEventBuilder()
 *     .developerId("dev-001")
 *     .projectId("PROJ-A")
 *     .fileName("Test.java")
 *     .linesAdded(10)
 *     .build();
 * </pre>
 */
public class TestEventBuilder {

    // Defaults
    private String developerId = "dev-default";
    private String developerName = "Default Developer";
    private String projectId = "PROJECT-DEFAULT";
    private String sprintId = "SPRINT-DEFAULT";
    private String filePath = "/default/path/File.java";
    private String fileName = "File.java";
    private String className = "File";
    private String ideType = "IntelliJ IDEA";
    private String genAiTool = "GitHub Copilot";
    private String developmentMode = "Development";
    private int linesAdded = 0;
    private int linesModified = 0;
    private int linesDeleted = 0;
    private boolean genAiGenerated = true;
    private Double genAiConfidenceScore = 0.85;
    private String eventTimestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    private String sessionId = java.util.UUID.randomUUID().toString();

    public TestEventBuilder developerId(String developerId) {
        this.developerId = developerId;
        return this;
    }

    public TestEventBuilder developerName(String developerName) {
        this.developerName = developerName;
        return this;
    }

    public TestEventBuilder projectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public TestEventBuilder sprintId(String sprintId) {
        this.sprintId = sprintId;
        return this;
    }

    public TestEventBuilder filePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public TestEventBuilder fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public TestEventBuilder ideType(String ideType) {
        this.ideType = ideType;
        return this;
    }

    public TestEventBuilder genAiTool(String genAiTool) {
        this.genAiTool = genAiTool;
        return this;
    }

    public TestEventBuilder developmentMode(String developmentMode) {
        this.developmentMode = developmentMode;
        return this;
    }

    public TestEventBuilder linesAdded(int linesAdded) {
        this.linesAdded = linesAdded;
        return this;
    }

    public TestEventBuilder linesModified(int linesModified) {
        this.linesModified = linesModified;
        return this;
    }

    public TestEventBuilder linesDeleted(int linesDeleted) {
        this.linesDeleted = linesDeleted;
        return this;
    }

    public TestEventBuilder genAiGenerated(boolean genAiGenerated) {
        this.genAiGenerated = genAiGenerated;
        return this;
    }

    public TestEventBuilder genAiConfidenceScore(Double confidenceScore) {
        this.genAiConfidenceScore = confidenceScore;
        return this;
    }

    public TestEventBuilder eventTimestamp(String timestamp) {
        this.eventTimestamp = timestamp;
        return this;
    }

    public TestEventBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public LOCRequestPayload build() {
        return new LOCRequestPayload(
                developerId, developerName,
                projectId, sprintId,
                filePath, fileName, className,
                ideType, genAiTool, developmentMode,
                linesAdded, linesModified, linesDeleted,
                genAiGenerated, genAiConfidenceScore,
                eventTimestamp, sessionId
        );
    }

    /**
     * Creates an event representing GenAI generated code.
     */
    public static LOCRequestPayload genAiEvent() {
        return new TestEventBuilder()
                .genAiGenerated(true)
                .genAiConfidenceScore(0.90)
                .build();
    }

    /**
     * Creates an event representing non-GenAI code.
     */
    public static LOCRequestPayload regularEvent() {
        return new TestEventBuilder()
                .genAiGenerated(false)
                .genAiConfidenceScore(null)
                .build();
    }

    /**
     * Creates an event with large number of lines changed.
     */
    public static LOCRequestPayload largeChangeEvent() {
        return new TestEventBuilder()
                .linesAdded(100)
                .linesModified(50)
                .linesDeleted(20)
                .build();
    }

    /**
     * Creates an event with minimal changes.
     */
    public static LOCRequestPayload minimalChangeEvent() {
        return new TestEventBuilder()
                .linesAdded(1)
                .linesModified(0)
                .linesDeleted(0)
                .build();
    }

    /**
     * Creates a Copilot-generated event.
     */
    public static LOCRequestPayload copilotEvent() {
        return new TestEventBuilder()
                .genAiTool("GitHub Copilot")
                .genAiGenerated(true)
                .genAiConfidenceScore(0.92)
                .build();
    }

    /**
     * Creates a Claude-generated event.
     */
    public static LOCRequestPayload claudeEvent() {
        return new TestEventBuilder()
                .genAiTool("Claude")
                .genAiGenerated(true)
                .genAiConfidenceScore(0.88)
                .build();
    }

    /**
     * Creates a ChatGPT-generated event.
     */
    public static LOCRequestPayload chatGptEvent() {
        return new TestEventBuilder()
                .genAiTool("ChatGPT")
                .genAiGenerated(true)
                .genAiConfidenceScore(0.85)
                .build();
    }

    /**
     * Creates an event with special characters in file path.
     */
    public static LOCRequestPayload eventWithSpecialChars() {
        return new TestEventBuilder()
                .fileName("file,with,commas.java")
                .filePath("/path/with \"quotes\"/file.java")
                .build();
    }
}

