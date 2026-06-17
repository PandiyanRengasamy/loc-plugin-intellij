package com.cts.plugin.intellij.loc.settings;

import com.cts.plugin.intellij.loc.util.GenAiToolDetector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import com.intellij.openapi.diagnostic.Logger;
/**
 * GenAiLocSettings
 *
 * Persists plugin configuration using IntelliJ's PersistentStateComponent API.
 * Settings are stored in ~/Library/Application Support/JetBrains/<product>/genai-loc.xml
 * (or equivalent on Windows/Linux).
 *
 * Access via: GenAiLocSettings.getInstance()
 */
@State(
        name   = "GenAiLocSettings",
        storages = { @Storage("genai-loc.xml") }
)
public class GenAiLocSettings implements PersistentStateComponent<GenAiLocSettings.State> {
    private static final Logger LOG = Logger.getInstance(GenAiLocSettings.class);
    // ── Properties file at project root ──────────────────────────────────
    private static final String PROPS_FILE = "genai-loc.properties";

    /**
     * Loads defaults from genai-loc.properties.
     * Search order:
     *   1. ${user.home}/genai-loc.properties   — user-level override (easiest to deploy)
     *   2. ${user.dir}/genai-loc.properties    — current working directory (project root in dev)
     * Falls back to hard-coded defaults if neither file is found.
     */
    private static Properties loadProperties() {
        LOG.info("Loading GenAiLocSettings defaults from " + PROPS_FILE);
        Properties props = new Properties();

        // 1. User home — most reliable location when plugin is installed in real IntelliJ
        java.io.File homeFile = Paths.get(System.getProperty("user.home", ""), PROPS_FILE).toFile();
        // 2. Current working directory (useful during development / sandbox runs)
        java.io.File cwdFile  = Paths.get(System.getProperty("user.dir",  ""), PROPS_FILE).toFile();

        java.io.File[] candidates = { homeFile, cwdFile };
        for (java.io.File f : candidates) {
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    props.load(in);
                    LOG.info("GenAiLocSettings: loaded properties from " + f.getAbsolutePath());
                    return props;   // first match wins
                } catch (IOException e) {
                    LOG.warn("GenAiLocSettings: failed to read " + f.getAbsolutePath() + " — " + e.getMessage());
                }
            }
        }
        LOG.warn("GenAiLocSettings: " + PROPS_FILE + " not found in user.home or user.dir — using hard-coded defaults");
        return props;
    }

    /** Mutable inner state class — must have public fields for XML serialization. */
    public static class State {
        private static final Properties DEFAULTS = loadProperties();

        public String  backendUrl           = DEFAULTS.getProperty("backend.url",            "http://localhost:8080/api/v1/genai-loc/events");
        public String  developerId          = DEFAULTS.getProperty("developer.id",           System.getProperty("user.name", ""));
        public String  developerName        = DEFAULTS.getProperty("developer.name",         "");
        public String  projectId            = DEFAULTS.getProperty("project.id",             "");
        public String  sprintId             = DEFAULTS.getProperty("sprint.id",              "");
        public String  defaultGenAiTool     = DEFAULTS.getProperty("default.genai.tool",     GenAiToolDetector.detectPrimary());
        public boolean enabled              = Boolean.parseBoolean(DEFAULTS.getProperty("plugin.enabled",           "true"));
        public int     batchSize            = Integer.parseInt(DEFAULTS.getProperty("batch.size",                   "1"));
        public int     flushIntervalSeconds = Integer.parseInt(DEFAULTS.getProperty("flush.interval.seconds",       "10"));
        public int     genAiLineThreshold   = Integer.parseInt(DEFAULTS.getProperty("genai.line.threshold",         "3"));
        @Override
        public String toString() {
            return "State{" +
                    "backendUrl='" + backendUrl + '\'' +
                    ", developerId='" + developerId + '\'' +
                    ", developerName='" + developerName + '\'' +
                    ", projectId='" + projectId + '\'' +
                    ", sprintId='" + sprintId + '\'' +
                    ", defaultGenAiTool='" + defaultGenAiTool + '\'' +
                    ", enabled=" + enabled +
                    ", batchSize=" + batchSize +
                    ", flushIntervalSeconds=" + flushIntervalSeconds +
                    ", genAiLineThreshold=" + genAiLineThreshold +
                    '}';
        }
    }

    private State state = new State();

    /** Thread-safe counter to track how many times getInstance() has been called. */
    private static final AtomicInteger ACCESS_COUNT = new AtomicInteger(0);

    /** Singleton access via IntelliJ service mechanism */
    public static GenAiLocSettings getInstance() {
        int count = ACCESS_COUNT.incrementAndGet();
        LOG.debug("GenAiLocSettings.getInstance() called - total access count: " + count);
        return ApplicationManager.getApplication().getService(GenAiLocSettings.class);
    }

    /** Returns the total number of times getInstance() has been called. */
    public static int getAccessCount() {
        return ACCESS_COUNT.get();
    }

    /** Resets the access counter back to zero. */
    public static void resetAccessCount() {
        int previous = ACCESS_COUNT.getAndSet(0);
        LOG.info("GenAiLocSettings: access counter reset (was " + previous + ")");
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loadedState) {
        this.state = loadedState;
    }

    // ── Convenience accessors ─────────────────────────────────────────────

    public String  getBackendUrl()           { return state.backendUrl; }
    public String  getDeveloperId()          { return state.developerId; }
    public String  getDeveloperName()        { return state.developerName; }
    public String  getProjectId()            { return state.projectId; }
    public String  getSprintId()             { return state.sprintId; }
    public String  getDefaultGenAiTool()     { return state.defaultGenAiTool; }
    public boolean isEnabled()               { return state.enabled; }
    public int     getBatchSize()            { return state.batchSize; }
    public int     getFlushIntervalSeconds() { return state.flushIntervalSeconds; }
    public int     getGenAiLineThreshold()   { return state.genAiLineThreshold; }

    public void setBackendUrl(String v)           { state.backendUrl = v; }
    public void setDeveloperId(String v)          { state.developerId = v; }
    public void setDeveloperName(String v)        { state.developerName = v; }
    public void setProjectId(String v)            { state.projectId = v; }
    public void setSprintId(String v)             { state.sprintId = v; }
    public void setDefaultGenAiTool(String v)     { state.defaultGenAiTool = v; }
    public void setEnabled(boolean v)             { state.enabled = v; }
    public void setBatchSize(int v)               { state.batchSize = v; }
    public void setFlushIntervalSeconds(int v)    { state.flushIntervalSeconds = v; }
    public void setGenAiLineThreshold(int v)      { state.genAiLineThreshold = v; }
}