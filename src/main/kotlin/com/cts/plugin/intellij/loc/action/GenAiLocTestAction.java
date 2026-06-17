package com.cts.plugin.intellij.loc.action;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.GenAiToolDetector;
import com.cts.plugin.intellij.loc.util.Icons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GenAiLocTestAction
 *
 * Appears under  Tools › GenAI LOC Tracker › Test Backend Connection
 *
 * When invoked it:
 *  1. Sends a real test event to the backend  (genAiGenerated=false, tool=TEST)
 *  2. Shows a balloon with the HTTP result so you can confirm the service is reachable
 *  3. Also shows what AI tools are currently detected
 */
public class GenAiLocTestAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(GenAiLocTestAction.class);

    public GenAiLocTestAction() {
        super("Test Backend Connection", "Sends a test event to the backend and shows the HTTP result", Icons.LOC_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        GenAiLocSettings s = GenAiLocSettings.getInstance();

        LOG.info("GenAiLocTestAction: triggered manually by user");

        // ── 1. Detect tools ───────────────────────────────────────────────
        String toolsSummary = String.join(", ", GenAiToolDetector.detectAll());
        if (toolsSummary.isBlank()) toolsSummary = "none detected";

        // ── 2. Send test event via the project service ────────────────────
        String backendResult = "skipped (no project service)";
        if (project != null) {
            GenAiLocProjectService svc = project.getService(GenAiLocProjectService.class);
            if (svc != null) {
                LOCRequestPayload testReq = new LOCRequestPayload(
                        s.getDeveloperId().isBlank() ? System.getenv("USERNAME") : s.getDeveloperId(),
                        s.getDeveloperName().isBlank() ? "Test User" : s.getDeveloperName(),
                        s.getProjectId().isBlank() ? project.getName() : s.getProjectId(),
                        s.getSprintId().isBlank() ? null : s.getSprintId(),
                        "TEST_FILE.java", "TEST_FILE.java", "TEST_FILE",
                        "INTELLIJ",
                        "TEST",      // not a real AI tool — just a diagnostic marker
                        "BROWNFIELD",
                        0, 0, 0,
                        false, null,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        svc.getSessionId()
                );
                svc.enqueue(testReq);
                svc.flushNow();
                backendResult = "test event sent — check notification for HTTP result";
                LOG.info("GenAiLocTestAction: test event enqueued and flushed");
            }
        }

        // ── 3. Direct HTTP ping for immediate feedback ────────────────────
        String pingResult = pingBackend(s.getBackendUrl());

        // ── 4. Show summary notification ─────────────────────────────────
        String msg = "<b>GenAI LOC Tracker — Diagnostic</b><br>"
                + "🌐 Backend URL   : " + s.getBackendUrl() + "<br>"
                + "🔌 Ping result   : " + pingResult + "<br>"
                + "🤖 AI tools      : " + toolsSummary + "<br>"
                + "👤 Developer ID  : " + s.getDeveloperId() + "<br>"
                + "📋 Plugin enabled: " + s.isEnabled() + "<br>"
                + "📤 Event send    : " + backendResult;

        boolean ok = pingResult.contains("HTTP 2") || pingResult.contains("HTTP 4");
        NotificationType type = ok ? NotificationType.INFORMATION : NotificationType.ERROR;

        Notification notif = NotificationGroupManager.getInstance()
                .getNotificationGroup("GenAI LOC Tracker")
                .createNotification(msg, type);
        Notifications.Bus.notify(notif, project);
    }

    private String pingBackend(String eventsUrl) {
        try {
            URL url = URI.create(eventsUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.disconnect();
            LOG.info("GenAiLocTestAction: ping " + eventsUrl + " → HTTP " + code);
            return "HTTP " + code + " (" + (code < 500 ? "backend is UP" : "server error") + ")";
        } catch (Exception ex) {
            LOG.warn("GenAiLocTestAction: ping failed — " + ex.getMessage());
            return "FAILED — " + ex.getMessage();
        }
    }
}

