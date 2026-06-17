package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GenAiCommandListener — listens to IntelliJ command completions.
 *
 * This is the alternative to the removed auto-send timer. When a command
 * finishes (e.g. "Editor Tab", "Choose Lookup Item"), we check after a short
 * delay whether any PendingLocEvent entries remain unclaimed. If so, we send
 * them — covering inline Copilot Tab-accepts in the code editor that don't
 * trigger a "Keep All" click.
 *
 * This does NOT fire on timer — it fires ONLY after a command execution,
 * which means it won't duplicate-send if the user hasn't done anything.
 */
public class GenAiCommandListener implements CommandListener {

    private static final Logger LOG = Logger.getInstance(GenAiCommandListener.class);

    /** Delay after command finishes before draining pending events. */
    private static final int DRAIN_DELAY_MS = 2000;

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GenAI-LOC-CmdListener");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
        String cmdName = event.getCommandName() != null ? event.getCommandName().toLowerCase() : "";

        // Only trigger for commands that could be AI accepts
        if (!isRelevantCommand(cmdName)) return;

        LOG.info("GenAI-LOC | [CommandListener] command finished: '" + cmdName + "' — scheduling pending drain in " + DRAIN_DELAY_MS + "ms");

        SCHEDULER.schedule(() -> drainPending(), DRAIN_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private boolean isRelevantCommand(String cmd) {
        // Empty command name = Copilot silent insert (Keep All, Apply in Editor)
        if (cmd.isBlank()) return true;
        // Tab-accept, lookup, inline suggestion
        return cmd.contains("tab") || cmd.contains("lookup") || cmd.contains("choose")
                || cmd.contains("copilot") || cmd.contains("accept") || cmd.contains("keep")
                || cmd.contains("apply") || cmd.contains("insert") || cmd.contains("suggestion")
                || cmd.contains("inlay") || cmd.contains("completion");
    }

    private void drainPending() {
        if (PendingLocEvent.size() == 0) return;

        GenAiLocSettings settings = GenAiLocSettings.getInstance();
        if (!settings.isEnabled()) return;

        // Drain all pending
        LOCRequestPayload evt = PendingLocEvent.consumeLatest();
        if (evt == null) return;

        LOG.info("GenAI-LOC | [CommandListener] draining pending event: file=" + evt.getFileName()
                + " +=" + evt.getLinesAdded() + " ~=" + evt.getLinesModified()
                + " tool=" + evt.getGenAiTool());

        Project project = findOpenProject();
        if (project == null) return;

        GenAiLocProjectService svc = project.getService(GenAiLocProjectService.class);
        if (svc == null) return;

        svc.enqueue(evt);

        // Drain remaining
        LOCRequestPayload next = PendingLocEvent.consumeLatest();
        while (next != null) {
            LOG.info("GenAI-LOC | [CommandListener] draining additional: file=" + next.getFileName());
            svc.enqueue(next);
            next = PendingLocEvent.consumeLatest();
        }

        svc.flushNow();
    }

    private static Project findOpenProject() {
        Project[] p = ProjectManager.getInstance().getOpenProjects();
        return p.length > 0 ? p[0] : null;
    }
}

