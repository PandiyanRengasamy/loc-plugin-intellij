package com.cts.plugin.intellij.loc.util;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ProjectEventLogger
 *
 * Writes two local files under  <projectRoot>/.genai-loc/
 * so developers can instantly verify the plugin is working
 * without looking at idea.log:
 *
 *   events-YYYYMMDD.csv   — one row per event (all events, not just fallback)
 *   plugin-activity.log   — human-readable chronological activity log
 *
 * One instance per project, created by GenAiLocProjectService.
 */
public class ProjectEventLogger {

    private static final Logger LOG = Logger.getInstance(ProjectEventLogger.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String DIR_NAME     = ".genai-loc";
    private static final String ACTIVITY_LOG = "plugin-activity.log";

    // CSV header — must match toCsvRow() column order
    private static final String CSV_HEADER =
            "timestamp,developerId,developerName,projectId,sprintId," +
            "fileName,filePath,ideType,genAiTool,developmentMode," +
            "linesAdded,linesModified,linesDeleted," +
            "genAiGenerated,confidenceScore,sessionId,status";

    private final Path logDir;
    private final Path activityLogFile;

    /**
     * @param projectBasePath  absolute path to the project root (e.g. C:/Users/me/MyProject)
     * @param sessionId        UUID of the current plugin session
     */
    public ProjectEventLogger(String projectBasePath, String sessionId) {
        this.logDir          = Paths.get(projectBasePath, DIR_NAME);
        this.activityLogFile = logDir.resolve(ACTIVITY_LOG);
        init(projectBasePath, sessionId);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Writes an event to the project CSV and the activity log.
     *
     * @param event   the event that was processed
     * @param status  "SENT" if successfully posted to backend, "FALLBACK" if saved to CSV fallback
     */
    public void logEvent(LOCRequestPayload event, String status) {
        String ts       = LocalDateTime.now().format(TS_FMT);
        String csvFile  = csvFilePath();

        // ── CSV ──────────────────────────────────────────────────────────
        try {
            Files.createDirectories(logDir);
            boolean exists = Files.exists(Paths.get(csvFile));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile, true))) {
                if (!exists) {
                    bw.write(CSV_HEADER);
                    bw.newLine();
                }
                bw.write(toCsvRow(event, ts, status));
                bw.newLine();
            }
        } catch (IOException ex) {
            LOG.warn("ProjectEventLogger: failed to write CSV — " + ex.getMessage());
        }

        // ── Activity log ─────────────────────────────────────────────────
        String genAiFlag = event.isGenAiGenerated() ? "✅ AI" : "✏️  manual";
        String line = ts
                + " | " + status
                + " | " + genAiFlag
                + " | tool=" + event.getGenAiTool()
                + " | +lines=" + event.getLinesAdded()
                + " ~lines=" + event.getLinesModified()
                + " | file=" + event.getFileName();
        appendActivity(line);
    }

    /**
     * Logs a plain-text message to the activity log (no CSV row).
     * Use for plugin lifecycle events (startup, shutdown, backend status).
     */
    public void logActivity(String message) {
        String ts   = LocalDateTime.now().format(TS_FMT);
        String line = ts + " | " + message;
        appendActivity(line);
    }

    /** Returns the path to today's CSV file (for display purposes). */
    public String getCsvFilePath() {
        return csvFilePath();
    }

    /** Returns the path to the activity log file. */
    public String getActivityLogPath() {
        return activityLogFile.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void init(String projectBasePath, String sessionId) {
        try {
            Files.createDirectories(logDir);
            String ts = LocalDateTime.now().format(TS_FMT);
            String header = "=".repeat(70) + "\n"
                    + ts + " | GenAI LOC Tracker STARTED\n"
                    + ts + " | Project     : " + projectBasePath + "\n"
                    + ts + " | Session ID  : " + sessionId + "\n"
                    + ts + " | CSV file    : " + csvFilePath() + "\n"
                    + "=".repeat(70);
            appendActivity(header);
            LOG.info("ProjectEventLogger: initialized — dir=" + logDir);
        } catch (Exception ex) {
            LOG.warn("ProjectEventLogger: init failed — " + ex.getMessage());
        }
    }

    private void appendActivity(String line) {
        try {
            Files.createDirectories(logDir);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(activityLogFile.toFile(), true))) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException ex) {
            LOG.warn("ProjectEventLogger: failed to write activity log — " + ex.getMessage());
        }
    }

    private String csvFilePath() {
        String date = DATE_FMT.format(LocalDateTime.now());
        return logDir.resolve("events-" + date + ".csv").toString();
    }

    private static String toCsvRow(LOCRequestPayload e, String ts, String status) {
        return escape(ts)                          + "," +
               escape(e.getDeveloperId())          + "," +
               escape(e.getDeveloperName())        + "," +
               escape(e.getProjectId())            + "," +
               escape(e.getSprintId())             + "," +
               escape(e.getFileName())             + "," +
               escape(e.getFilePath())             + "," +
               escape(e.getIdeType())              + "," +
               escape(e.getGenAiTool())            + "," +
               escape(e.getDevelopmentMode())      + "," +
               e.getLinesAdded()                   + "," +
               e.getLinesModified()                + "," +
               e.getLinesDeleted()                 + "," +
               e.isGenAiGenerated()                + "," +
               (e.getGenAiConfidenceScore() != null ? e.getGenAiConfidenceScore() : "") + "," +
               escape(e.getSessionId())            + "," +
               escape(status);
    }

    private static String escape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

