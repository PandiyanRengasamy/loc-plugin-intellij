package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.FileChange;
import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.cts.plugin.intellij.loc.util.PreSnapshotStore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CopilotActionListener — intercepts IntelliJ action executions to detect
 * Keep/Keep All/Accept/Apply clicks from ANY AI tool (Copilot, Claude, etc.).
 *
 * This is the correct architectural approach because:
 *  - AWT mouse listeners CANNOT detect clicks inside JCEF (embedded Chromium)
 *  - AnActionListener fires for ALL action executions regardless of how the
 *    action was triggered (toolbar, keyboard, JCEF button, menu, etc.)
 *
 * The listener captures PRE snapshots in {@link #beforeActionPerformed} and
 * computes diffs + sends events in {@link #afterActionPerformed}.
 */
public class CopilotActionListener implements AnActionListener {
    // Guard to prevent duplicate processing for the same action
    private volatile boolean isProcessingLocEvent = false;

    private static final Logger LOG = Logger.getInstance(CopilotActionListener.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Delay after action completes before taking POST snapshot (ms). */
    private static final int POST_SNAPSHOT_DELAY_MS = 800;

    // ── Action ID / class name patterns that indicate AI accept/keep/apply ────
    private static final String[] ACTION_ID_KEYWORDS = {
            "copilot",
            "github.copilot",
            "keep",
            "keepall",
            "accept",
            "acceptall",
            "apply",
            "applyall",
            "applySuggestion",
            "acceptSuggestion",
            "insertAtCursor",
            "insertCode",
            "applyInEditor",
            "inlay",
            "suggestion",
            "editorAcceptInlay",
            "editorTab",
            "EditorChooseLookupItem",
            "EditorChooseLookupItemReplace",
            "inlineSuggest",
            "codeCompletion",
    };

    private static final String[] ACTION_CLASS_KEYWORDS = {
            "copilot",
            "github",
            "keep",
            "accept",
            "apply",
            "suggestion",
            "inlay",
            "inline",
    };

    /** Action text patterns (from Presentation#getText). */
    private static final Set<String> SAFE_ACTION_TEXTS = new HashSet<>(Arrays.asList(
            "keep all", "keep", "accept", "accept all",
            "apply", "apply all", "apply in editor",
            "apply suggestion", "accept suggestion",
            "accept solution", "insert at cursor", "insert code"
    ));

    private static final String[] PARTIAL_ACTION_TEXTS = {
            "accept copilot", "apply copilot", "copilot: accept",
            "copilot: apply", "copilot: keep", "keep all",
            "accept suggestion", "apply suggestion",
    };

    /** PRE snapshots captured in beforeActionPerformed. Key = file path. */
    private final ConcurrentHashMap<String, int[]> preSnapshots = new ConcurrentHashMap<>();

    /** Tracks whether beforeActionPerformed detected a relevant AI action. */
    private volatile boolean armed = false;
    private volatile String armedActionId = null;
    private volatile String armedActionText = null;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GenAI-LOC-ActionListener");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        if (!isRelevantAiAction(action, event)) {
            armed = false;
            return;
        }

        String actionId = event.getActionManager().getId(action);
        String actionText = action.getTemplatePresentation().getText();
        String actionClass = action.getClass().getName();

        LOG.info("GenAI-LOC | [ActionListener] ▶ BEFORE action detected:"
                + " id=" + actionId
                + " text='" + actionText + "'"
                + " class=" + actionClass);

        // Capture PRE snapshots for all open editors
        preSnapshots.clear();
        try {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) continue;
                for (com.intellij.openapi.fileEditor.FileEditor fe :
                        FileEditorManager.getInstance(project).getAllEditors()) {
                    if (!(fe instanceof TextEditor)) continue;
                    Editor ed = ((TextEditor) fe).getEditor();
                    VirtualFile vf = ed.getVirtualFile();
                    if (vf == null || !vf.isInLocalFileSystem()) continue;
                    String path = vf.getPath();
                    if (preSnapshots.containsKey(path)) continue;
                    Document doc = ed.getDocument();
                    preSnapshots.put(path, new int[]{doc.getLineCount(), doc.getTextLength()});
                    LOG.info("GenAI-LOC | [ActionListener]   PRE: " + vf.getName()
                            + " lines=" + doc.getLineCount() + " chars=" + doc.getTextLength());
                    // Store PRE snapshot in PreSnapshotStore for MouseListener
                    try {
                        PreSnapshotStore.capture(path, doc.getLineCount(), doc.getTextLength());
                    } catch (Exception ex) {
                        LOG.warn("GenAI-LOC | [ActionListener] PreSnapshotStore.capture error: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("GenAI-LOC | [ActionListener] PRE snapshot error: " + ex.getMessage());
        }

        armed = true;
        armedActionId = actionId;
        armedActionText = actionText;
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull com.intellij.openapi.actionSystem.AnActionResult result) {
        if (!armed) return;
        armed = false;

        final String actionId = armedActionId;
        final String actionText = armedActionText;
        final Map<String, int[]> capturedPre = new HashMap<>(preSnapshots);
        preSnapshots.clear();

        LOG.info("GenAI-LOC | [ActionListener] ◀ AFTER action: id=" + actionId
                + " text='" + actionText + "' — scheduling POST snapshot in " + POST_SNAPSHOT_DELAY_MS + "ms");

        // Delay to let the document settle after action execution
        scheduler.schedule(() -> {
            try {
                computeAndSendDiff(capturedPre, actionId, actionText);
            } catch (Exception ex) {
                LOG.error("GenAI-LOC | [ActionListener] diff/send error: " + ex.getMessage(), ex);
            }
        }, POST_SNAPSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // ── Core diff + send logic ────────────────────────────────────────────────

    private void computeAndSendDiff(Map<String, int[]> capturedPre,
                                     String actionId, String actionText) {
        // Guard: prevent duplicate processing for the same action
        if (isProcessingLocEvent) {
            LOG.warn("GenAI-LOC | [ActionListener] LOC event processing is already in progress. Skipping duplicate trigger.");
            return;
        }
        final Set<String> pendingPaths = new HashSet<>();
        final List<LOCRequestPayload> pendingEvents = new ArrayList<>();
        isProcessingLocEvent = true;
        try {
            // Also drain any PendingLocEvents that the DocumentListener may have stored
            for (int attempt = 0; attempt < 10; attempt++) {
                LOCRequestPayload evt = PendingLocEvent.consumeLatest();
                while (evt != null) {
                    String path = evt.getFilePath();
                    int total = Math.abs(evt.getLinesAdded()) + Math.abs(evt.getLinesModified()) + Math.abs(evt.getLinesDeleted());
                    if (path != null && total > 0) {
                        pendingPaths.add(path);
                        pendingEvents.add(evt);
                    }
                    evt = PendingLocEvent.consumeLatest();
                }
                if (!pendingEvents.isEmpty()) break;
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
            // Ensure all pending events are cleared after processing
            com.cts.plugin.intellij.loc.util.PendingLocEvent.clearAll();

            LOG.info("GenAI-LOC | [ActionListener] PendingLocEvent drained: " + pendingEvents.size());
        } finally {
            isProcessingLocEvent = false;
        }

        // POST snapshots
        final Map<String, int[]> postSnapshots = new LinkedHashMap<>();
        try {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                    if (project.isDisposed()) continue;
                    for (com.intellij.openapi.fileEditor.FileEditor fe :
                            FileEditorManager.getInstance(project).getAllEditors()) {
                        if (!(fe instanceof TextEditor)) continue;
                        Editor ed = ((TextEditor) fe).getEditor();
                        VirtualFile vf = ed.getVirtualFile();
                        if (vf == null || !vf.isInLocalFileSystem()) continue;
                        String path = vf.getPath();
                        if (postSnapshots.containsKey(path)) continue;
                        Document doc = ed.getDocument();
                        postSnapshots.put(path, new int[]{doc.getLineCount(), doc.getTextLength()});
                    }
                }
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
            throw pce;
        } catch (Exception ex) {
            LOG.warn("GenAI-LOC | [ActionListener] POST snapshot error: " + ex.getMessage());
        }

        LOG.info("GenAI-LOC | [ActionListener] POST snapshots: " + postSnapshots.size() + " files");

        // Compute diffs
        List<FileChange> fileChanges = new ArrayList<>();
        Set<String> allPaths = new LinkedHashSet<>(postSnapshots.keySet());
        allPaths.addAll(capturedPre.keySet());

        for (String path : allPaths) {
            if (pendingPaths.contains(path)) continue; // already counted by PendingLocEvent

            int[] pre = capturedPre.get(path);
            int[] post = postSnapshots.get(path);
            if (pre == null || post == null) continue;

            int preLines = pre[0], preChars = pre[1];
            int postLines = post[0], postChars = post[1];
            int netLines = postLines - preLines;
            int charDelta = postChars - preChars;

            int linesAdded = Math.max(0, netLines);
            int linesDeleted = Math.max(0, -netLines);

            // Hidden deletions in replace operations
            double avgCharsPerLine = preLines > 0 ? (double) preChars / preLines : 40.0;
            if (linesAdded > 0 && charDelta < (int)(linesAdded * avgCharsPerLine)) {
                int charDeficit = (int)(linesAdded * avgCharsPerLine) - charDelta;
                int hiddenDeleted = (int)(charDeficit / avgCharsPerLine);
                if (hiddenDeleted > 0) linesDeleted = Math.max(linesDeleted, hiddenDeleted);
            }

            int linesModified = 0; // Not tracked in this logic

            // Extract fileName and className from path
            String fileName = path.substring(path.lastIndexOf("/") + 1);
            String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

            if (linesAdded > 0 || linesDeleted > 0) {
                fileChanges.add(new FileChange(path, fileName, className, linesAdded, linesModified, linesDeleted));
            }
        }

        // Compose and send event if any changes, but only for Copilot/Claude
        int totalAdded = 0, totalModified = 0, totalDeleted = 0;
        for (FileChange fc : fileChanges) {
            totalAdded += fc.getLinesAdded();
            totalModified += fc.getLinesModified();
            totalDeleted += fc.getLinesDeleted();
        }
        for (LOCRequestPayload evt : pendingEvents) {
            totalAdded += evt.getLinesAdded();
            totalModified += evt.getLinesModified();
            totalDeleted += evt.getLinesDeleted();
        }

        // Final guard: skip if no file changes at all
        if (fileChanges.isEmpty() && pendingEvents.isEmpty()) {
            LOG.info("GenAI-LOC | [ActionListener] Skipped event: no file changes or pending events");
            return;
        }
        if (totalAdded == 0 && totalModified == 0 && totalDeleted == 0) {
            LOG.info("GenAI-LOC | [ActionListener] Skipped event: all LOC counts are zero");
            return;
        }

        Project project = findOpenProject();
        if (project == null) return;
        String tool = inferTool(actionId, actionText);
        if (!"COPILOT".equals(tool) && !"CLAUDE".equals(tool)) {
            // Do not trigger LOC service or show popup for non-Copilot/Claude actions
            LOG.info("GenAI-LOC | [ActionListener] Skipped event: not Copilot/Claude tool (tool=" + tool + ")");
            return;
        }
        GenAiLocProjectService svc = project.getService(GenAiLocProjectService.class);
        if (svc == null) { LOG.warn("GenAI-LOC | [ActionListener] Service not found"); return; }

        // Use multi-file constructor, fill required fields, use defaults/nulls for others
        LOCRequestPayload req = new LOCRequestPayload(
            null, // developerId
            null, // developerName
            null, // projectId
            null, // sprintId
            null, // filePath
            null, // fileName
            null, // className
            null, // ideType
            tool, // genAiTool
            null, // developmentMode
            totalAdded,
            totalModified,
            totalDeleted,
            true, // genAiGenerated
            null, // genAiConfidenceScore
            null, // eventTimestamp
            null, // sessionId
            new ArrayList<>(fileChanges)
        );

        // Set accepted location, agent, model, file counts
        String location = inferAcceptedLocation(actionId, actionText);
        req.setAcceptedLocation(location);
        req.setAgentName(tool);
        req.setLlmModel(inferLlmModel(tool));

        int filesUpdated = 0, filesAdded = 0, filesDeleted = 0;
        for (FileChange fc : fileChanges) {
            if (fc.getLinesAdded() > 0 && fc.getLinesModified() == 0 && fc.getLinesDeleted() == 0) {
                filesAdded++;
            } else if (fc.getLinesDeleted() > 0 && fc.getLinesAdded() == 0 && fc.getLinesModified() == 0) {
                filesDeleted++;
            } else {
                filesUpdated++;
            }
        }
        req.setTotalFilesUpdated(filesUpdated);
        req.setTotalFilesAdded(filesAdded);
        req.setTotalFilesDeleted(filesDeleted);

        LOG.info("GenAI-LOC | [ActionListener] ENQUEUE: files=" + fileChanges.size()
                + " +=" + totalAdded + " ~=" + totalModified + " -=" + totalDeleted
                + " tool=" + tool + " location=" + location
                + " filesUpdated=" + filesUpdated + " filesAdded=" + filesAdded + " filesDeleted=" + filesDeleted);

        svc.enqueue(req);
        svc.flushNow();

        // Clear PreSnapshotStore for processed files
        for (FileChange fc : fileChanges) {
            PreSnapshotStore.consume(fc.getFilePath());
        }

        // Show notification popup for LOC/service event (GenAI tool action)
        String msg = "GenAI LOC: ✅ " + tool + " action detected — "
                + fileChanges.size() + " file(s) | +" + totalAdded + " ~" + totalModified
                + " -" + totalDeleted + " lines | Sent";
        showNotification(msg, project);
    }

    // ── Action relevance detection ────────────────────────────────────────────
    private boolean isRelevantAiAction(AnAction action, AnActionEvent event) {
        // SAFETY: Never trigger on single/double click or caret/selection/editor actions
        // Editor action classes (e.g., EditorAction, EditorTab, EditorChooseLookupItem, caret, selection, etc.) must be ignored
        // This ensures that clicks in the code editor window are always ignored

        // STRICT allow-list for GenAI tool actions ONLY (Copilot/Claude accept/keep/apply)
        String actionId = event.getActionManager().getId(action);
        String actionClass = action.getClass().getName().toLowerCase();
        String actionText = action.getTemplatePresentation().getText();
        String textLower = actionText != null ? actionText.trim().toLowerCase() : "";

        // Explicitly skip known editor/caret/selection actions
        if (actionClass.contains("editoraction") || actionClass.contains("editorimpl") ||
            actionClass.contains("editorcomponent") || actionClass.contains("caret") ||
            actionClass.contains("selection") || actionClass.contains("mouseevent") ||
            actionClass.contains("editorchooselookupitem") || actionClass.contains("editortab")) {
            LOG.info("GenAI-LOC | [ActionListener] Skipped editor/caret/selection action: class=" + actionClass);
            return false;
        }

        // 1. Allow-list of action IDs (only known GenAI tool actions)
        Set<String> allowedActionIds = new HashSet<>(Arrays.asList(
                "github.copilot.accept", "github.copilot.keep", "github.copilot.keepall",
                "com.github.copilot.accept", "com.github.copilot.keep", "com.github.copilot.keepall",
                "com.github.copilot.actions.AcceptAction", "com.github.copilot.actions.KeepAction", "com.github.copilot.actions.KeepAllAction",
                // Claude/Anthropic (add if known)
                "com.anthropic.claude.accept", "com.anthropic.claude.keep", "com.anthropic.claude.keepall"
        ));
        if (actionId != null && allowedActionIds.contains(actionId)) {
            LOG.info("GenAI-LOC | [ActionListener] Matched strict actionId: " + actionId);
            return true;
        }

        // 2. Allow-list of class names (must contain copilot/claude AND accept/keep/apply)
        if ((actionClass.contains("copilot") || actionClass.contains("claude")) &&
                (actionClass.contains("accept") || actionClass.contains("keep") || actionClass.contains("apply"))) {
            LOG.info("GenAI-LOC | [ActionListener] Matched strict actionClass: " + actionClass);
            return true;
        }

        // 3. Allow-list of button texts (exact match, lowercased, unique to GenAI tools)
        Set<String> allowedTexts = new HashSet<>(Arrays.asList(
                "accept", "keep", "keep all", "apply", "apply all", "accept suggestion", "apply suggestion", "accept solution"
        ));
        if (!textLower.isEmpty() && allowedTexts.contains(textLower)) {
            LOG.info("GenAI-LOC | [ActionListener] Matched strict actionText: '" + textLower + "'");
            return true;
        }

        // 4. REMOVE all partial/loose matching logic to avoid false positives from generic UI/editor actions
        // (No partial text/class matching allowed)

        // Otherwise, not relevant — skip LOC/service trigger
        LOG.info("GenAI-LOC | [ActionListener] Skipped action: id=" + actionId + ", class=" + actionClass + ", text='" + textLower + "'");
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String inferTool(String actionId, String actionText) {
        String combined = ((actionId != null ? actionId : "") + " " + (actionText != null ? actionText : "")).toLowerCase();
        if (combined.contains("copilot") || combined.contains("github")) return "COPILOT";
        if (combined.contains("claude") || combined.contains("anthropic")) return "CLAUDE";
        if (combined.contains("gemini") || combined.contains("google")) return "GEMINI";
        if (combined.contains("chatgpt") || combined.contains("openai")) return "CHATGPT";
        if (combined.contains("amazon") || combined.contains("codewhisperer")) return "AMAZON_Q";
        return "COPILOT"; // default
    }

    private static String inferAcceptedLocation(String actionId, String actionText) {
        String combined = ((actionId != null ? actionId : "") + " " + (actionText != null ? actionText : "")).toLowerCase();
        if (combined.contains("chat")) return "COPILOT_CHAT";
        if (combined.contains("inline") || combined.contains("inlay") || combined.contains("suggestion")) return "INLINE_SUGGESTION";
        if (combined.contains("editor") || combined.contains("apply in editor")) return "CODE_EDITOR";
        if (combined.contains("keep")) return "CODE_EDITOR";
        return "CODE_EDITOR";
    }

    private static String inferLlmModel(String tool) {
        switch (tool) {
            case "COPILOT": return "gpt-4o";
            case "CLAUDE": return "claude-3.5-sonnet";
            case "GEMINI": return "gemini-pro";
            case "CHATGPT": return "gpt-4o";
            default: return "unknown";
        }
    }

    private static Project findOpenProject() {
        Project[] p = ProjectManager.getInstance().getOpenProjects();
        return p.length > 0 ? p[0] : null;
    }

    private void showNotification(String msg, Project project) {
        try {
            com.intellij.notification.NotificationGroup group =
                    com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("GenAI LOC Tracker");
            com.intellij.notification.Notification n = (group != null)
                    ? group.createNotification(msg, com.intellij.notification.NotificationType.INFORMATION)
                    : new com.intellij.notification.Notification("GenAI LOC Tracker",
                    "GenAI LOC Tracker", msg, com.intellij.notification.NotificationType.INFORMATION);
            com.intellij.notification.Notifications.Bus.notify(n, project);
        } catch (Exception ex) {
            LOG.warn("GenAI-LOC | [ActionListener] notification error: " + ex.getMessage());
        }
    }
}

