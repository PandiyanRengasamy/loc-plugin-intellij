package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.cts.plugin.intellij.loc.util.AiActionConstants;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocCaptureDocumentListener implements BulkAwareDocumentListener, CommandListener {

    private final Project project;
    private final AtomicBoolean isAiActionActive = new AtomicBoolean(false);

    // Accepts any command containing these keywords (case-insensitive)
    private static final List<String> TARGET_COMMANDS = AiActionConstants.ACTION_KEYWORDS;
    private static final Logger LOG = LoggerFactory.getLogger(LocCaptureDocumentListener.class);

    public LocCaptureDocumentListener(Project project) {
        this.project = project;
    }

    @Override
    public void commandStarted(@NotNull CommandEvent event) {
        String cmd = event.getCommandName();
        if (cmd != null) {
            String cmdLower = cmd.toLowerCase();
            if (TARGET_COMMANDS.stream().anyMatch(cmdLower::contains)) {
                isAiActionActive.set(true);
                LOG.info("Command started: {} (matched keyword in list)", event.getCommandName());
            }
        }
    }

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
        isAiActionActive.set(false);
        LOG.info("Command finished: {}", event.getCommandName());
    }

    @Override
    public void documentChangedNonBulk(@NotNull DocumentEvent event) {
        if (isAiActionActive.get()) {
            storePendingLocEvent(event);
        }
    }

    /**
     * Stores a pending LOC event for the current file, to be drained by the mouse listener.
     */
    private void storePendingLocEvent(DocumentEvent event) {
        VirtualFile vf = FileDocumentManager.getInstance().getFile(event.getDocument());
        if (vf == null || !vf.isInLocalFileSystem()) return;
        String filePath = vf.getPath();
        String fileName = vf.getName();
        String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        int linesAdded = countLines(event.getNewFragment().toString());
        int linesRemoved = countLines(event.getOldFragment().toString());
        int netChange = linesAdded - linesRemoved;
        if (netChange == 0) return;
        LOCRequestPayload req = new LOCRequestPayload(
                System.getProperty("user.name", "unknown"), // developerId
                System.getProperty("user.name", "unknown"), // developerName
                project != null ? project.getName() : "unknown", // projectId
                null, // sprintId
                filePath, fileName, className,
                "INTELLIJ", "COPILOT", "BROWNFIELD",
                linesAdded, 0, linesRemoved,
                true, 0.90,
                java.time.LocalDateTime.now().toString(),
                null, new java.util.ArrayList<>()
        );
        PendingLocEvent.store(filePath, req);
        LOG.info("[LOC Service] Pending event stored for Keep All: {} netChange={}", fileName, netChange);
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Count newline characters
        return (int) text.chars().filter(ch -> ch == '\n').count() + (text.endsWith("\n") ? 0 : 1);
    }
}
