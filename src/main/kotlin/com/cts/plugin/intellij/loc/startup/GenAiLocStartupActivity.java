package com.cts.plugin.intellij.loc.startup;

import com.cts.plugin.intellij.loc.listeners.CopilotKeepAllMouseListener;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.GenAiToolDetector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * GenAiLocStartupActivity
 *
 * Runs when a project is opened. It:
 *  1. Logs the active configuration (backend URL, developer, tools detected)
 *  2. Shows a startup balloon telling the user the plugin is alive
 *  3. Pings the backend and shows whether it is reachable
 *
 * Registered in plugin.xml as a <postStartupActivity>.
 */
public class GenAiLocStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(GenAiLocStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        GenAiLocSettings s = GenAiLocSettings.getInstance();

        // ── 1. Detect installed AI tools ──────────────────────────────────
        List<String> detectedTools = GenAiToolDetector.detectAll();
        String toolsSummary = detectedTools.isEmpty() ? "none detected" : String.join(", ", detectedTools);

        // ── 1b. Register global mouse listener for "Keep All" button detection ──
        CopilotKeepAllMouseListener.register();
        LOG.info("GenAI-LOC | CopilotKeepAllMouseListener registered at startup");

        // ── 2. Get project logger paths ───────────────────────────────────
        GenAiLocProjectService svc = project.getService(GenAiLocProjectService.class);
        String csvPath     = svc != null ? svc.getProjectLogger().getCsvFilePath()     : "n/a";
        String logPath     = svc != null ? svc.getProjectLogger().getActivityLogPath() : "n/a";

        // ── 3. Log everything ─────────────────────────────────────────────
        LOG.info("╔══════════════════════════════════════════════════════════╗");
        LOG.info("║  GenAI LOC Tracker — PLUGIN STARTED                     ║");
        LOG.info("╠══════════════════════════════════════════════════════════╣");
        LOG.info("║  Backend URL   : " + s.getBackendUrl());
        LOG.info("║  Developer ID  : " + s.getDeveloperId());
        LOG.info("║  Developer Name: " + s.getDeveloperName());
        LOG.info("║  Project ID    : " + s.getProjectId());
        LOG.info("║  Sprint ID     : " + s.getSprintId());
        LOG.info("║  AI Tools      : " + toolsSummary);
        LOG.info("║  Plugin Enabled: " + s.isEnabled());
        LOG.info("║  Batch Size    : " + s.getBatchSize());
        LOG.info("║  CSV file      : " + csvPath);
        LOG.info("║  Activity log  : " + logPath);
        LOG.info("╚══════════════════════════════════════════════════════════╝");

        // ── 4. Ping backend and show startup notification ─────────────────
        boolean backendReachable = pingBackend(s.getBackendUrl());

        String statusIcon = backendReachable ? "✅" : "❌";
        String statusText = backendReachable ? "Backend REACHABLE" : "Backend UNREACHABLE";
        String toolText   = detectedTools.isEmpty()
                ? "⚠️ No AI plugin detected"
                : "🤖 AI tools: " + toolsSummary;

        String msg = "<b>GenAI LOC Tracker is active</b><br>"
                + statusIcon + " " + statusText + ": " + s.getBackendUrl() + "<br>"
                + toolText + "<br>"
                + "👤 Developer: " + s.getDeveloperId()
                + (s.getDeveloperName().isBlank() ? "" : " (" + s.getDeveloperName() + ")") + "<br>"
                + "📁 Project: " + s.getProjectId() + "<br>"
                + "📊 Events CSV: <code>" + csvPath + "</code><br>"
                + "📋 Activity log: <code>" + logPath + "</code>";

        NotificationType notifType = backendReachable
                ? NotificationType.INFORMATION
                : NotificationType.WARNING;

        if (svc != null) {
            svc.getProjectLogger().logActivity("Startup ping → backend "
                    + (backendReachable ? "REACHABLE" : "UNREACHABLE") + " | tools=" + toolsSummary);
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Notification notif = NotificationGroupManager.getInstance()
                        .getNotificationGroup("GenAI LOC Tracker")
                        .createNotification(msg, notifType);
                Notifications.Bus.notify(notif, project);
            } catch (Exception ex) {
                LOG.warn("GenAiLocStartupActivity: could not show startup notification: " + ex.getMessage());
            }
        });

        return null;
    }

    /**
     * Does a quick HTTP GET to the backend health endpoint or the events URL
     * to confirm it is up. Returns true if HTTP 2xx or 4xx (server is running;
     * 4xx just means the exact path differs). Returns false on connection failure.
     */
    private boolean pingBackend(String eventsUrl) {
        try {
            // Derive a health-check URL: replace /events with /actuator/health or just ping root
            String healthUrl = eventsUrl
                    .replaceAll("/api/v1/genai-loc/events.*", "/actuator/health");
            URL url = URI.create(healthUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            LOG.info("GenAiLocStartupActivity: backend ping " + healthUrl + " → HTTP " + code);
            conn.disconnect();
            return code < 500;
        } catch (Exception ex) {
            LOG.warn("GenAiLocStartupActivity: backend ping failed — " + ex.getMessage());
            return false;
        }
    }
}

