package com.cts.plugin.intellij.loc.service;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.util.ProjectEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * GenAiLocProjectService
 *
 * Project-scoped service that owns the EventDispatcher and ProjectEventLogger.
 * One instance per open project, disposed when the project closes.
 */
@Service(Service.Level.PROJECT)
public final class GenAiLocProjectService implements Disposable {

    private static final Logger LOG = Logger.getInstance(GenAiLocProjectService.class);

    private final EventDispatcher      dispatcher;
    private final ProjectEventLogger   projectLogger;
    private final String               sessionId = java.util.UUID.randomUUID().toString();

    /** IntelliJ injects the Project automatically via constructor injection. */
    public GenAiLocProjectService(Project project) {
        String basePath = project.getBasePath() != null ? project.getBasePath() : System.getProperty("user.home");
        this.projectLogger = new ProjectEventLogger(basePath, sessionId);
        this.dispatcher    = new EventDispatcher(projectLogger);

        LOG.info("GenAiLocProjectService initialized"
                + " — project=" + project.getName()
                + " sessionId=" + sessionId
                + " csv=" + projectLogger.getCsvFilePath()
                + " log=" + projectLogger.getActivityLogPath());

        projectLogger.logActivity("Plugin started — sessionId=" + sessionId
                + " | project=" + project.getName());
    }

    public void enqueue(LOCRequestPayload event) {
        LOG.debug("GenAiLocProjectService.enqueue: " + event);
        dispatcher.enqueue(event);
    }

    /** Triggers an immediate flush of all queued events to the backend. */
    public void flushNow() {
        LOG.debug("GenAiLocProjectService.flushNow: triggered");
        dispatcher.flush();
    }

    public String getSessionId() {
        return sessionId;
    }

    public ProjectEventLogger getProjectLogger() {
        return projectLogger;
    }

    public String getStats() {
        String stats = dispatcher.getStats();
        LOG.debug("GenAiLocProjectService.getStats: " + stats);
        return stats;
    }

    @Override
    public void dispose() {
        String stats = dispatcher.getStats();
        LOG.info("GenAiLocProjectService disposing — final stats: " + stats + " sessionId=" + sessionId);
        projectLogger.logActivity("Plugin stopped — final stats: " + stats);
        dispatcher.dispose();
    }
}

