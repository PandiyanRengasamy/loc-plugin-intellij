package com.cts.plugin.intellij.loc.util;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CsvFallbackStore
 *
 * When the backend service is unreachable, unsaved events are written to a
 * local CSV file under:  <user.home>/.genai-loc/fallback-<date>.csv
 *
 * Once the backend recovers, EventDispatcher calls replayAndClear() which
 * reads all pending rows, returns them as CodeEventRequest objects, and
 * deletes the file on success.
 */
public class CsvFallbackStore {

    private static final Logger LOG = Logger.getInstance(CsvFallbackStore.class);
    //if the .genai-loc directory doesn't exist, it will be created when writing the first fallback CSV file. Each day's file is named with a date stamp to prevent overwriting previous data and to allow for historical analysis if needed.
    private static final String STORE_DIR   = System.getProperty("user.home") + File.separator + ".genai-loc";
    private static final String DATE_STAMP  = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
    private static final String CSV_FILE    = STORE_DIR + File.separator + "fallback-" + DATE_STAMP + ".csv";

    // CSV column headers — must match write order below
    private static final String HEADER =
            "developerId,developerName,projectId,sprintId,filePath,fileName," +
            "ideType,genAiTool,developmentMode," +
            "linesAdded,linesModified,linesDeleted," +
            "genAiGenerated,genAiConfidenceScore,eventTimestamp,sessionId";

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Appends a list of failed events to the CSV fallback file.
     * Creates the file (and parent directories) if they don't exist.
     */
    public static void write(List<LOCRequestPayload> events) {
        try {
            Files.createDirectories(Paths.get(STORE_DIR));
            boolean fileExists = Files.exists(Paths.get(CSV_FILE));

            try (BufferedWriter bw = new BufferedWriter(
                    new FileWriter(CSV_FILE, true))) {   // append mode

                // Write header only on first creation
                if (!fileExists) {
                    bw.write(HEADER);
                    bw.newLine();
                    LOG.info("CsvFallbackStore: created new fallback file -> " + CSV_FILE);
                }

                for (LOCRequestPayload e : events) {
                    bw.write(toCsvRow(e));
                    bw.newLine();
                }
                LOG.warn("CsvFallbackStore: wrote " + events.size() + " events to fallback CSV -> " + CSV_FILE);
            }
        } catch (IOException ex) {
            LOG.error("CsvFallbackStore: failed to write fallback CSV -> " + ex.getMessage());
        }
    }

    // ── Read & Replay ─────────────────────────────────────────────────────

    /**
     * Returns true if a fallback CSV file exists with unsent events.
     */
    public static boolean hasPending() {
        boolean exists = Files.exists(Paths.get(CSV_FILE));
        LOG.debug("CsvFallbackStore: hasPending=" + exists + " file=" + CSV_FILE);
        return exists;
    }

    /**
     * Reads all rows from the fallback CSV and returns them as CodeEventRequest objects.
     * Skips the header row and any malformed rows.
     */
    public static List<LOCRequestPayload> readAll() {
        List<LOCRequestPayload> events = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }  // skip header
                if (line.isBlank()) continue;
                try {
                    events.add(fromCsvRow(line));
                } catch (Exception e) {
                    LOG.warn("CsvFallbackStore: skipping malformed row -> " + line);
                }
            }
            LOG.info("CsvFallbackStore: read " + events.size() + " pending events from " + CSV_FILE);
        } catch (IOException ex) {
            LOG.error("CsvFallbackStore: failed to read fallback CSV -> " + ex.getMessage());
        }
        return events;
    }

    /**
     * Deletes the fallback CSV file after all events have been successfully replayed.
     */
    public static void clear() {
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(CSV_FILE));
            if (deleted) {
                LOG.info("CsvFallbackStore: fallback CSV cleared -> " + CSV_FILE);
            }
        } catch (IOException ex) {
            LOG.error("CsvFallbackStore: failed to delete fallback CSV -> " + ex.getMessage());
        }
    }

    // ── CSV helpers ───────────────────────────────────────────────────────

    private static String toCsvRow(LOCRequestPayload e) {
        return escape(e.getDeveloperId())        + "," +
               escape(e.getDeveloperName())      + "," +
               escape(e.getProjectId())          + "," +
               escape(e.getSprintId())           + "," +
               escape(e.getFilePath())           + "," +
               escape(e.getFileName())           + "," +
               escape(e.getClassName())          + "," +
               escape(e.getIdeType())            + "," +
               escape(e.getGenAiTool())          + "," +
               escape(e.getDevelopmentMode())    + "," +
               e.getLinesAdded()                 + "," +
               e.getLinesModified()              + "," +
               e.getLinesDeleted()               + "," +
               e.isGenAiGenerated()              + "," +
               (e.getGenAiConfidenceScore() != null ? e.getGenAiConfidenceScore() : "") + "," +
               escape(e.getEventTimestamp())     + "," +
               escape(e.getSessionId());
    }

    private static LOCRequestPayload fromCsvRow(String row) {
        String[] c = row.split(",", -1);
        return new LOCRequestPayload(
                unescape(c[0]),  unescape(c[1]),
                unescape(c[2]),  unescape(c[3]),
                unescape(c[4]),  unescape(c[5]),  unescape(c[6]),
                unescape(c[7]),  unescape(c[8]),  unescape(c[9]),
                Integer.parseInt(c[10]),
                Integer.parseInt(c[11]),
                Integer.parseInt(c[12]),
                Boolean.parseBoolean(c[13]),
                c[14].isBlank() ? null : Double.parseDouble(c[14]),
                unescape(c[15]), unescape(c[16])
        );
    }

    /** Wrap values containing commas or quotes in double-quotes. */
    private static String escape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private static String unescape(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
        }
        return val;
    }
}

