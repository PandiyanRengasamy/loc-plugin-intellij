package com.cts.plugin.intellij.loc.service;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.CsvFallbackStore;
import com.cts.plugin.intellij.loc.util.ProjectEventLogger;
import com.google.gson.Gson;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EventDispatcher — thread-safe event queue.
 * Writes all events to the per-project CSV/log via ProjectEventLogger,
 * and POSTs them to the backend REST API.
 */
public class EventDispatcher {

    private static final Logger LOG  = Logger.getInstance(EventDispatcher.class);
    private static final Gson   GSON = new Gson();

    private final List<LOCRequestPayload>   queue     = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient               httpClient;
    private final AtomicInteger            sentTotal = new AtomicInteger(0);
    private final AtomicInteger            failTotal = new AtomicInteger(0);
    private final ProjectEventLogger       projectLogger;

    private volatile boolean serviceDown = false;

    public EventDispatcher(){
        this.projectLogger = new ProjectEventLogger(System.getProperty("user.home"), java.util.UUID.randomUUID().toString());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        LOG.info("EventDispatcher created (no-arg) — projectLogger initialised with home dir");
    }

    public EventDispatcher(ProjectEventLogger projectLogger) {
        this.projectLogger = projectLogger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        GenAiLocSettings settings = GenAiLocSettings.getInstance();
        // No periodic timer — flushing is driven by batch threshold and CommandListener drain
        LOG.info("EventDispatcher initialised — batchSize=" + settings.getBatchSize()
                + "  backendUrl=" + settings.getBackendUrl());
        projectLogger.logActivity("EventDispatcher started — batchSize=" + settings.getBatchSize());
    }

    public void enqueue(LOCRequestPayload event) {
        GenAiLocSettings s = GenAiLocSettings.getInstance();
        if (!s.isEnabled()) {
            LOG.info("EventDispatcher.enqueue — plugin disabled, event dropped for file=" + event.getFileName());
            return;
        }

        queue.add(event);
        LOG.info("EventDispatcher.enqueue — queued file=" + event.getFileName()
                + " tool=" + event.getGenAiTool()
                + " +lines=" + event.getLinesAdded()
                + " ~lines=" + event.getLinesModified()
                + " genAi=" + event.isGenAiGenerated()
                + " queueSize=" + queue.size());

        if (queue.size() >= s.getBatchSize()) {
            LOG.info("EventDispatcher.enqueue — batch threshold reached (size=" + queue.size()
                    + "), triggering immediate flush");
            scheduler.execute(this::flush);
        }
    }

    public synchronized void flush() {
        if (queue.isEmpty()) {
            LOG.info("EventDispatcher.flush — queue is empty, nothing to send");
            return;
        }

        GenAiLocSettings gas = GenAiLocSettings.getInstance();
        List<LOCRequestPayload> batch = new ArrayList<>(queue);
        queue.clear();

        // Always POST each event individually to /events using the standard JSON template.
        String singleUrl = gas.getBackendUrl();
        LOG.info("EventDispatcher.flush — START: flushing " + batch.size()
                + " event(gas) to " + singleUrl);

        int okCount   = 0;
        int failCount = 0;
        List<LOCRequestPayload> failed = new ArrayList<>();

        for (LOCRequestPayload e : batch) {
            LOG.info("EventDispatcher.flush — processing event: file=" + e.getFileName()
                    + " dev=" + e.getDeveloperId()
                    + " tool=" + e.getGenAiTool()
                    + " +lines=" + e.getLinesAdded()
                    + " genAi=" + e.isGenAiGenerated()
                    + " session=" + e.getSessionId());
            String json = buildStandardPayload(e);
            try {
                int status = post(singleUrl, json);
                if (status >= 200 && status < 300) {
                    okCount++;
                    sentTotal.incrementAndGet();
                    projectLogger.logEvent(e, "SENT");
                    LOG.info("EventDispatcher.flush — POST OK: file=" + e.getFileName()
                            + " httpStatus=" + status + " totalSent=" + sentTotal.get());
                } else {
                    failCount++;
                    failed.add(e);
                    projectLogger.logEvent(e, "FALLBACK(HTTP-" + status + ")");
                    LOG.warn("EventDispatcher.flush — POST FAILED: file=" + e.getFileName()
                            + " httpStatus=" + status + " → saved to fallback");
                }
            } catch (Exception ex) {
                failCount++;
                failed.add(e);
                projectLogger.logEvent(e, "FALLBACK(ERROR)");
                LOG.warn("EventDispatcher.flush — POST ERROR: file=" + e.getFileName()
                        + " error=" + ex.getMessage() + " → saved to fallback");
            }
        }

        LOG.info("EventDispatcher.flush — END: ok=" + okCount + " failed=" + failCount
                + " totalSent=" + sentTotal.get() + " serviceDown=" + serviceDown);

        if (okCount > 0) {
            notifyUser("GenAI LOC: ✅ " + okCount + " event(gas) sent to backend.", NotificationType.INFORMATION);
            projectLogger.logActivity("Flush OK — " + okCount + " event(gas) → " + singleUrl
                    + " | total=" + sentTotal.get());
            if (serviceDown && failCount == 0) {
                serviceDown = false;
                LOG.info("EventDispatcher.flush — backend recovered, scheduling fallback replay");
                projectLogger.logActivity("Backend recovered — replaying fallback CSV");
                scheduler.execute(this::replayFallback);
            }
        }

        if (failCount > 0) {
            serviceDown = true;
            CsvFallbackStore.write(failed);
            LOG.info("EventDispatcher.flush — " + failCount
                    + " event(gas) written to CSV fallback, serviceDown=true");
            // Silent fallback — no popup to avoid client-side noise
            LOG.warn("GenAI LOC: " + failCount + " event(s) failed. Saved to CSV fallback.");
            projectLogger.logActivity("Flush partial failure — " + failCount
                    + " event(gas) saved to fallback, " + okCount + " sent OK");
            for (LOCRequestPayload fe : failed) {
                String json = buildStandardPayload(fe);
                LOG.info("EventDispatcher.flush — scheduling retry for file=" + fe.getFileName());
                retryAfterDelay(singleUrl, json, java.util.List.of(fe));
            }
        }
    }

    // ── Standard JSON payload builder ─────────────────────────────────────────

    /**
     * Builds the standard LOC event JSON string that matches the backend
     * {@code LocEventRequest} contract exactly. All fields are included so
     * the service can validate and store every attribute.
     *
     * <pre>
     * Standard JSON template:
     * {
     *   "developerId"         : "john_doe",
     *   "developerName"       : "John Doe",
     *   "projectId"           : "MyProject",
     *   "sprintId"            : "Sprint-1",
     *   "filePath"            : "/path/to/MyClass.java",
     *   "fileName"            : "MyClass.java",
     *   "ideType"             : "INTELLIJ",
     *   "genAiTool"           : "COPILOT",
     *   "developmentMode"     : "BROWNFIELD",
     *   "linesAdded"          : 10,
     *   "linesModified"       : 2,
     *   "linesDeleted"        : 0,
     *   "genAiGenerated"      : true,
     *   "genAiConfidenceScore": 0.92,
     *   "eventTimestamp"      : "2026-04-17T10:30:00",
     *   "sessionId"           : "uuid-session-id"
     * }
     * </pre>
     */
    private String buildStandardPayload(LOCRequestPayload e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("developerId",          e.getDeveloperId());
        payload.put("developerName",        e.getDeveloperName());
        payload.put("projectId",            e.getProjectId());
        payload.put("sprintId",             e.getSprintId());
        payload.put("filePath",             e.getFilePath());
        payload.put("fileName",             e.getFileName());
        payload.put("className",            e.getClassName());
        payload.put("ideType",              e.getIdeType()  != null ? e.getIdeType()  : "INTELLIJ");
        payload.put("genAiTool",            e.getGenAiTool() != null ? e.getGenAiTool() : "NONE");
        payload.put("developmentMode",      e.getDevelopmentMode() != null ? e.getDevelopmentMode() : "BROWNFIELD");
        payload.put("linesAdded",           e.getLinesAdded());
        payload.put("linesModified",        e.getLinesModified());
        payload.put("linesDeleted",         e.getLinesDeleted());
        payload.put("genAiGenerated",       e.isGenAiGenerated());
        payload.put("genAiConfidenceScore", e.getGenAiConfidenceScore());
        payload.put("eventTimestamp",       e.getEventTimestamp());
        payload.put("sessionId",            e.getSessionId());
        // New fields: LLM model, agent, accepted location
        payload.put("llmModel",             e.getLlmModel());
        payload.put("agentName",            e.getAgentName());
        payload.put("acceptedLocation",     e.getAcceptedLocation());
        // Include per-file breakdown array
        payload.put("fileChanges",          e.getFileChanges());
        // Total file counts
        payload.put("totalFilesUpdated",    e.getTotalFilesUpdated());
        payload.put("totalFilesAdded",      e.getTotalFilesAdded());
        payload.put("totalFilesDeleted",    e.getTotalFilesDeleted());
        // LLM token usage
        payload.put("inputTokens",          e.getInputTokens());
        payload.put("outputTokens",         e.getOutputTokens());
        String json = GSON.toJson(payload);
        LOG.info("GenAI-LOC | build Standard Payload — file=" + e.getFileName()
                + " payloadSize=" + json.length() + " chars"
                + " payload=" + json);
        return json;
    }

    /**
     * Reads all pending events from the CSV fallback, POSTs them to the backend,
     * and clears the CSV if successful. This is called automatically when the backend
     * is detected to have recovered after a failure, and can also be triggered manually from the UI for testing.
     */
    public synchronized void replayFallback() {
        if (!CsvFallbackStore.hasPending()) {
            LOG.info("EventDispatcher.replayFallback — no pending CSV events, skipping replay");
            return;
        }
        List<LOCRequestPayload> pending = CsvFallbackStore.readAll();
        if (pending.isEmpty()) {
            LOG.info("EventDispatcher.replayFallback — CSV was present but empty, clearing");
            CsvFallbackStore.clear();
            return;
        }

        LOG.info("EventDispatcher.replayFallback — START: replaying " + pending.size() + " event(s)");
        projectLogger.logActivity("Replaying " + pending.size() + " fallback events...");

        GenAiLocSettings s = GenAiLocSettings.getInstance();
        String url     = pending.size() == 1
                ? s.getBackendUrl()
                : s.getBackendUrl().replace("/events", "/events/batch");
        Object payload = pending.size() == 1 ? pending.get(0) : Map.of("events", pending);
        String json    = GSON.toJson(payload);
        LOG.info("EventDispatcher.replayFallback — posting to " + url
                + " payloadSize=" + json.length() + " chars");

        try {
            int status = post(url, json);
            if (status >= 200 && status < 300) {
                sentTotal.addAndGet(pending.size());
                serviceDown = false;
                CsvFallbackStore.clear();
                for (LOCRequestPayload e : pending) projectLogger.logEvent(e, "REPLAYED");
                projectLogger.logActivity("Replay OK — " + pending.size() + " events delivered, fallback CSV cleared");
                LOG.info("EventDispatcher.replayFallback — OK: " + pending.size()
                        + " event(s) delivered, CSV cleared, totalSent=" + sentTotal.get());
            } else {
                projectLogger.logActivity("Replay HTTP " + status + " — fallback retained");
                LOG.warn("EventDispatcher.replayFallback — FAILED httpStatus=" + status
                        + " — CSV retained for next retry");
            }
        } catch (Exception ex) {
            projectLogger.logActivity("Replay ERROR — " + ex.getMessage());
            LOG.warn("EventDispatcher.replayFallback — ERROR: " + ex.getMessage()
                    + " — CSV retained for next retry");
        }
    }

    public String getStats() {
        return "Sent=" + sentTotal.get()
                + " Failed=" + failTotal.get()
                + " Pending=" + queue.size()
                + " CsvFallback=" + (CsvFallbackStore.hasPending() ? "yes" : "no");
    }

    public void dispose() {
        LOG.info("EventDispatcher.dispose — shutting down scheduler, flushing remaining queue (size="
                + queue.size() + ")");
        scheduler.shutdown();
        flush();
        LOG.info("EventDispatcher.dispose — DONE. finalStats=" + getStats());
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void notifyUser(String message, NotificationType type) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    com.intellij.notification.NotificationGroup group =
                            NotificationGroupManager.getInstance().getNotificationGroup("GenAI LOC Tracker");
                    Notification notification = (group != null)
                            ? group.createNotification(message, type)
                            : new Notification("GenAI LOC Tracker", "GenAI LOC Tracker", message, type);
                    Notifications.Bus.notify(notification);
                } catch (Exception ex) {
                    LOG.warn("notifyUser failed: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            LOG.warn("notifyUser: " + ex.getMessage());
        }
    }

    private int post(String url, String json) throws Exception {
        LOG.info("GenAI_LOC: EventDispatcher.post — POST URL  : " + url);
        LOG.info("GenAI_LOC: EventDispatcher.post — Payload   : " + json);
        LOG.info("GenAI_LOC: EventDispatcher.post — PayloadLen: " + json.length() + " chars");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        LOG.info("EventDispatcher.post — Sending HTTP POST: " + request);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int    statusCode  = response.statusCode();
        String responseBody = response.body() != null ? response.body() : "(empty)";

        if (statusCode >= 200 && statusCode < 300) {
            LOG.info("EventDispatcher.post — Response OK   : status=" + statusCode
                    + " body=" + responseBody);
        } else {
            LOG.warn("EventDispatcher.post — Response ERROR: status=" + statusCode
                    + " body=" + responseBody);
        }

        return statusCode;
    }

    private void retryAfterDelay(String url, String json, List<LOCRequestPayload> batch) {
        LOG.info("EventDispatcher.retryAfterDelay — scheduling retry in 5s for "
                + batch.size() + " event(s) → " + url);
        scheduler.schedule(() -> {
            LOG.info("EventDispatcher.retryAfterDelay — executing retry for " + batch.size() + " event(s)");
            try {
                int status = post(url, json);
                if (status >= 200 && status < 300) {
                    sentTotal.addAndGet(batch.size());
                    for (LOCRequestPayload e : batch) projectLogger.logEvent(e, "RETRY-OK");
                    projectLogger.logActivity("Retry OK — " + batch.size() + " events delivered");
                    LOG.info("EventDispatcher.retryAfterDelay — RETRY OK: " + batch.size()
                            + " event(s) delivered, totalSent=" + sentTotal.get());
                } else {
                    failTotal.addAndGet(batch.size());
                    projectLogger.logActivity("Retry FAILED HTTP " + status);
                    LOG.error("EventDispatcher.retryAfterDelay — RETRY FAILED httpStatus=" + status);
                }
            } catch (Exception ex) {
                failTotal.addAndGet(batch.size());
                projectLogger.logActivity("Retry ERROR — " + ex.getMessage());
                LOG.error("EventDispatcher.retryAfterDelay — RETRY ERROR: " + ex.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }
}
