package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.cts.plugin.intellij.loc.util.PreSnapshotStore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CopilotKeepAllMouseListener
 *
 * Registers a global AWT mouse listener that fires ONLY when the user clicks
 * a "Keep All", "Keep", "Accept", "Apply", or similar button inside the GitHub
 * Copilot Chat diff panel OR the code editor inline diff/suggestion area.
 *
 * Strict matching policy
 * ──────────────────────
 * Exact match on known button texts ("keep all", "keep", "accept", "apply", etc.)
 * plus partial match for editor-area buttons with extra context (e.g.
 * "Accept Copilot Suggestion (Tab)"). Only these trigger LOC capture.
 *
 * LOC calculation strategy — Local History approach (zero background polling)
 * ─────────────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: Copilot Chat writes its generated code to the editor as a LIVE
 *  PREVIEW *before* the user clicks "Keep All". At MOUSE_PRESSED the document
 *  already reflects the new code → PRE == POST → diff = 0.
 *
 *  FIX: IntelliJ's built-in Local History records every document change with
 *  zero cost to us. When Copilot writes its preview it creates a history entry.
 *  On MOUSE_RELEASED we query Local History for the revision that existed
 *  BEFORE Copilot's change (index = 1, index 0 = current/post-preview).
 *  That gives us the true PRE line/char count with no background polling.
 *
 *  Flow:
 *    MOUSE_PRESSED  → arm (just record button text, no snapshot needed)
 *    MOUSE_RELEASED → schedule POST snapshot + Local History PRE lookup → diff → send
 */
public class CopilotKeepAllMouseListener implements AWTEventListener {

    private static final Logger LOG = Logger.getInstance(CopilotKeepAllMouseListener.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── STRICT matching: ONLY "keep" and "keep all" ───────────────────────────
    /**
     * The EXACT button texts (all lowercase, trimmed, HTML-stripped) that will
     * trigger LOC capture. Covers both the Copilot Chat panel ("keep", "keep all")
     * and code editor area buttons ("accept", "apply", "apply in editor", etc.).
     */
    /**
     * SAFE exact-match button texts — these are unambiguously Copilot/AI actions
     * and will NOT produce false positives from unrelated IDE dialogs.
     */
    private static final Set<String> KEEP_BUTTON_SAFE = new HashSet<>(Arrays.asList(
            "keep all",
            "keep",
            "apply in editor",
            "apply suggestion",
            "accept suggestion",
            "accept solution",
            "insert at cursor",
            "insert code"
    ));

    /**
     * CONTEXT-SENSITIVE exact-match button texts — generic labels like "Accept"
     * and "Apply" appear in many IDE dialogs (Settings, Merge, etc.).
     * These ONLY trigger LOC capture when the clicked component's ancestor hierarchy
     * contains a Copilot/AI-related component (diff view, chat panel, inline suggestion).
     */
    private static final Set<String> KEEP_BUTTON_CONTEXTUAL = new HashSet<>(Arrays.asList(
            "accept",
            "accept all",
            "apply",
            "apply all"
    ));

    /** ms to wait after MOUSE_RELEASED before taking POST snapshot. */
    private static final int SNAPSHOT_DELAY_MS = 800;

    /**
     * Additional keywords for partial/contains matching — needed for code editor area
     * buttons where the text may include extra context like "Accept Copilot Suggestion (Tab)"
     * or "Apply Changes" etc.
     */
    private static final String[] KEEP_BUTTON_PARTIAL = {
            "accept copilot",
            "apply copilot",
            "accept suggestion",
            "apply suggestion",
            "accept solution",
            "apply in editor",
            "insert at cursor",
            "copilot: accept",
            "copilot: apply",
            "copilot: keep",
    };

    /**
     * Component hierarchy keywords that indicate the button is inside a
     * Copilot / AI-related panel (diff view, chat panel, inline suggestion gutter).
     * Used to gate the CONTEXTUAL button matches and avoid false positives from
     * unrelated IDE dialogs like Settings → Apply, Merge → Accept, etc.
     */
    private static final String[] AI_CONTEXT_KEYWORDS = {
            "copilot", "github", "diff", "inlay", "inline",
            "suggestion", "chat", "aicode", "genai", "codeedit",
            "editorinline", "gutter",
            // Copilot inline diff in code editor area
            "inlaycomponent", "inlaypanel", "ghosttext",
            "codecompletion", "editorhint"
    };

    /**
     * Returns true if the lowercased text matches a known Keep/Accept button.
     * <ul>
     *   <li>SAFE texts ("keep all", "apply suggestion", etc.) → always match</li>
     *   <li>CONTEXTUAL texts ("accept", "apply") → only match when the clicked
     *       component is inside a Copilot/AI-related component hierarchy</li>
     *   <li>PARTIAL keywords ("accept copilot", etc.) → contains-match, always triggers</li>
     * </ul>
     *
     * @param lower   lowercased, trimmed button text
     * @param clicked the AWT component that was clicked (for hierarchy inspection)
     */
    private static boolean isKeepButtonMatch(String lower, Component clicked) {
        // 1. Safe exact matches — always trigger
        if (KEEP_BUTTON_SAFE.contains(lower)) return true;

        // 2. Partial/contains matches — always trigger
        for (String partial : KEEP_BUTTON_PARTIAL) {
            if (lower.contains(partial)) return true;
        }

        // 3. Contextual exact matches — only if inside a Copilot/AI component
        if (KEEP_BUTTON_CONTEXTUAL.contains(lower)) {
            if (isInAiContext(clicked)) {
                LOG.info("GenAI-LOC | contextual match '" + lower + "' ACCEPTED (AI context found)");
                return true;
            }
            LOG.debug("GenAI-LOC | contextual match '" + lower + "' REJECTED (no AI context)");
            return false;
        }

        return false;
    }

    /**
     * Walks up the component hierarchy (up to 20 levels) and checks if any
     * ancestor's class name contains a Copilot/AI-related keyword.
     * This distinguishes a "Accept" button inside a Copilot diff panel from
     * an "Accept" button inside an unrelated IDE dialog.
     */
    private static boolean isInAiContext(Component c) {
        boolean foundEditor = false;
        Component cur = c;
        for (int depth = 0; depth < 20 && cur != null; depth++) {
            String clsName = cur.getClass().getName().toLowerCase();

            // Track if we're inside an editor component
            if (clsName.contains("editorcomponent") || clsName.contains("editorimpl")
                    || clsName.contains("editorpanel")) {
                foundEditor = true;
            }

            for (String kw : AI_CONTEXT_KEYWORDS) {
                if (clsName.contains(kw)) return true;
            }

            // Check accessible name for context clues
            try {
                javax.accessibility.AccessibleContext ac = cur.getAccessibleContext();
                if (ac != null) {
                    String accName = ac.getAccessibleName();
                    if (accName != null) {
                        String accLower = accName.toLowerCase();
                        for (String kw : AI_CONTEXT_KEYWORDS) {
                            if (accLower.contains(kw)) return true;
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Check component name property (set via setName())
            try {
                String compName = cur.getName();
                if (compName != null) {
                    String compLower = compName.toLowerCase();
                    for (String kw : AI_CONTEXT_KEYWORDS) {
                        if (compLower.contains(kw)) return true;
                    }
                }
            } catch (Exception ignored) {}

            // Check ActionButton's action class name — Copilot actions have
            // "copilot" or "github" in their action class even when the Swing
            // component hierarchy uses generic IntelliJ editor classes.
            try {
                String actionClassName = getActionClassName(cur);
                if (actionClassName != null) {
                    String actionLower = actionClassName.toLowerCase();
                    for (String kw : AI_CONTEXT_KEYWORDS) {
                        if (actionLower.contains(kw)) return true;
                    }
                }
            } catch (Exception ignored) {}

            // Check tooltip text for Copilot references
            if (cur instanceof javax.swing.JComponent) {
                try {
                    String tip = ((javax.swing.JComponent) cur).getToolTipText();
                    if (tip != null) {
                        String tipLower = tip.toLowerCase();
                        for (String kw : AI_CONTEXT_KEYWORDS) {
                            if (tipLower.contains(kw)) return true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            cur = cur.getParent();
        }

        // Fallback: if the clicked component itself is an ActionButton whose
        // action class name contains a Copilot/AI keyword, treat it as AI context
        // even if none of the parent components matched.
        try {
            String actionCls = getActionClassName(c);
            if (actionCls != null) {
                String al = actionCls.toLowerCase();
                if (al.contains("copilot") || al.contains("github")
                        || al.contains("suggestion") || al.contains("inlay")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // If we detected an editor and the clicked component is an ActionButton
        // whose action text matches keep/accept patterns, it's likely a Copilot inline action.
        // Copilot inline diff buttons sit directly inside the editor component.
        // NOTE: We do NOT blindly return true for foundEditor — that would cause
        // every click inside the code editor to trigger the API call (false positive).
        if (foundEditor) {
            String actionText = getActionButtonText(c);
            if (actionText != null) {
                String atLower = actionText.trim().toLowerCase();
                if (KEEP_BUTTON_SAFE.contains(atLower)) return true;
                for (String partial : KEEP_BUTTON_PARTIAL) {
                    if (atLower.contains(partial)) return true;
                }
            }
            // Only treat as AI context if the clicked component is actually a button
            String clsName = c.getClass().getName().toLowerCase();
            if (clsName.contains("actionbutton") || clsName.contains("button")) {
                LOG.info("GenAI-LOC | isInAiContext: found editor context + button component, treating as AI");
                return true;
            }
            LOG.debug("GenAI-LOC | isInAiContext: found editor but clicked component is NOT a button ("
                    + c.getClass().getSimpleName() + "), not treating as AI context");
            return false;
        }

        return false;
    }

    /**
     * Extracts the action class name from an ActionButton component via reflection.
     * Returns the fully qualified class name of the action, or null.
     */
    private static String getActionClassName(Component c) {
        try {
            // Try getAction() method
            try {
                java.lang.reflect.Method getAction = c.getClass().getMethod("getAction");
                Object action = getAction.invoke(c);
                if (action != null) return action.getClass().getName();
            } catch (NoSuchMethodException ignored) {}
            // Try myAction field
            java.lang.reflect.Field f = findField(c.getClass(), "myAction");
            if (f != null) {
                f.setAccessible(true);
                Object action = f.get(c);
                if (action != null) return action.getClass().getName();
            }
        } catch (Exception ignored) {}
        return null;
    }
    /**
     * Infers a button label from the action class name (lowercase).
     * Used for icon-only ActionButtons where no text is available.
     */
    private static String inferLabelFromActionClass(String actionLower) {
        if (actionLower.contains("keepall")) return "keep all";
        if (actionLower.contains("keep")) return "keep";
        if (actionLower.contains("acceptall")) return "accept all";
        if (actionLower.contains("applyall")) return "apply all";
        if (actionLower.contains("accept")) return "accept";
        if (actionLower.contains("apply")) return "apply";
        if (actionLower.contains("suggestion")) return "accept suggestion";
        if (actionLower.contains("inlay")) return "accept";
        return "accept";
    }

    /** Retry window for pending Copilot events after Keep/Keep-All release. */
    private static final int PENDING_DRAIN_RETRIES = 8;
    private static final int PENDING_DRAIN_RETRY_DELAY_MS = 150;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GenAI-LOC-MouseListener");
                t.setDaemon(true);
                return t;
            });

    /**
     * PRE state cache — populated by our own DocumentListener (registered via EditorFactory).
     * Key   = absolute VirtualFile path
     * Value = int[2]{lineCount, charCount} captured in beforeDocumentChange()
     *
     * Because this listener fires BEFORE every document write (including Copilot's
     * live-preview write), the map always holds the true pre-change state for each file.
     * No background polling required.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, int[]> PRE_STATE_CACHE
            = new java.util.concurrent.ConcurrentHashMap<>();


    /**
     * Set when MOUSE_PRESSED detects a Keep/Keep-All button.
     * Consumed on matching MOUSE_RELEASED.
     */
    private final AtomicReference<String> armedButtonText = new AtomicReference<>(null);

    // ── AWTEventListener ─────────────────────────────────────────────────────

    @Override
    public void eventDispatched(AWTEvent event) {

        if (!(event instanceof MouseEvent)) return;
        MouseEvent me = (MouseEvent) event;

        Component clicked = me.getComponent();
        if (clicked == null) return;

        // Ignore clicks directly on the code editor component (not a button)
        String clsName = clicked.getClass().getName().toLowerCase();
        boolean isEditorComponent = clsName.contains("editorcomponent") || clsName.contains("editorimpl") || clsName.contains("editorpanel");
        boolean isButtonComponent = clsName.contains("button") || clsName.contains("actionbutton");
        if (isEditorComponent && !isButtonComponent) {
            // This is a click in the code editor, not on a button—ignore
            return;
        }

        // ── MOUSE_PRESSED: arm only — no snapshot taken here ─────────────────
        // (Copilot's live preview may not have been applied yet at this moment,
        //  but we do NOT snapshot here because it could already be post-preview.
        //  PRE will be read from Local History on MOUSE_RELEASED.)
        if (me.getID() == MouseEvent.MOUSE_PRESSED) {
            String rawText = extractKeepButtonText(clicked);
            if (rawText == null) {
                // No text found — but if this is a button/ActionButton inside an AI context
                // (e.g. icon-only Copilot inline diff buttons in the code editor), arm it anyway.
                String btnClsName = clicked.getClass().getName().toLowerCase();
                if (btnClsName.contains("actionbutton") || btnClsName.contains("button")) {
                    // Check if action class name suggests a Copilot/keep/accept action
                    String actionCls = getActionClassName(clicked);
                    String actionLower = actionCls != null ? actionCls.toLowerCase() : "";
                    boolean isCopilotAction = actionLower.contains("copilot")
                            || actionLower.contains("keep") || actionLower.contains("accept")
                            || actionLower.contains("apply") || actionLower.contains("suggestion")
                            || actionLower.contains("inlay") || actionLower.contains("github");

                    if (isCopilotAction || isInAiContext(clicked)) {
                        // Icon-only Copilot button in code editor — arm it
                        String label = isCopilotAction ? inferLabelFromActionClass(actionLower) : "accept";
                        armedButtonText.set(label);
                        LOG.info("GenAI-LOC | ⬇ MOUSE_PRESSED armed (icon-only button): inferred='" + label + "'"
                                + "  component=" + clicked.getClass().getName()
                                + "  actionClass=" + actionCls
                                + "  hierarchy=" + buildComponentHierarchy(clicked, 8));
                        return;
                    }
                    LOG.info("GenAI-LOC | MOUSE_PRESSED: no text found for BUTTON component="
                            + clicked.getClass().getName()
                            + " hierarchy=" + buildComponentHierarchy(clicked, 10)
                            + " actionClass=" + actionCls);
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("GenAI-LOC | MOUSE_PRESSED: no text found for component="
                            + clicked.getClass().getName()
                            + " hierarchy=" + buildComponentHierarchy(clicked, 6));
                }
                return;
            }
            String lower = rawText.trim().toLowerCase();
            if (!isKeepButtonMatch(lower, clicked)) {
                LOG.debug("GenAI-LOC | MOUSE_PRESSED: text='" + rawText + "' not a keep button, skip");
                return;
            }
            armedButtonText.set(rawText);
            LOG.info("GenAI-LOC | ⬇ MOUSE_PRESSED armed: text='" + rawText + "'"
                    + "  component=" + clicked.getClass().getName());
            return;
        }

        // ── MOUSE_RELEASED: compute diff using Local History + send ───────────
        if (me.getID() != MouseEvent.MOUSE_RELEASED) return;

        String armed = armedButtonText.getAndSet(null);
        if (armed == null) return;

        // Re-check released component (may differ if mouse drifted slightly).
        // For inline diff editor buttons the mouse may be released over a slightly
        // different Swing component (e.g. the icon inside an ActionButton), so we
        // only ABORT if the released component clearly belongs to a DIFFERENT,
        // non-keep button.  If extractKeepButtonText returns null (component has no
        // text / unknown) we let the armed event proceed — the PRESSED check already
        // validated the intent.
        if (clicked != null) {
            String rel = extractKeepButtonText(clicked);
            if (rel != null) {
                String relLower = rel.trim().toLowerCase();
                if (!isKeepButtonMatch(relLower, clicked)) {
                    // Only abort if the released text is clearly something else (not just empty/unknown)
                    LOG.info("GenAI-LOC | RELEASED mismatch — text='" + rel + "', abort");
                    return;
                }
            }
            // rel == null → unknown component; trust the PRESSED arming
        }

        LOG.info("GenAI-LOC | ⬆ MOUSE_RELEASED — scheduling POST + Local History diff");

        scheduler.schedule(() -> {
            final Set<String>             pendingCountedPaths = new HashSet<>();
            final List<LOCRequestPayload> pendingEvents = drainPendingEventsWithRetry(pendingCountedPaths);
            LOG.info("GenAI-LOC | PendingLocEvent drained: " + pendingEvents.size() + " event(s)");

            // POST snapshot — fresh current document state
            Map<String, EditorSnapshot> postSnapshots = snapshotAllEditors();
            LOG.info("GenAI-LOC | POST snapshots: " + postSnapshots.size() + " file(s)");
            for (EditorSnapshot s : postSnapshots.values()) {
                LOG.info("GenAI-LOC |   POST file=" + s.fileName
                        + "  lines=" + s.lineCount + "  chars=" + s.charCount);
            }

            // Per-file diff using Local History for PRE
            final List<FileDiff> diffs = new ArrayList<>();
            Set<String> allPaths = new LinkedHashSet<>(postSnapshots.keySet());
            allPaths.addAll(PreSnapshotStore.getAllPaths());

            for (String filePath : allPaths) {
                if (pendingCountedPaths.contains(filePath)) continue;

                EditorSnapshot post = postSnapshots.get(filePath);
                if (post == null) continue;

                // PRE: from DocumentListener cache (captured before every doc write)
                int preLines, preChars;
                int[] hist = getPreCountFromCache(filePath, post.fileName);
                if (hist != null) {
                    preLines = hist[0];
                    preChars = hist[1];
                    LOG.info("GenAI-LOC |   PRE(LocalHistory) file=" + post.fileName
                            + "  lines=" + preLines + "  chars=" + preChars);
                } else {
                    // Fallback: PreSnapshotStore (set in beforeDocumentChange)
                    PreSnapshotStore.Snapshot stored = PreSnapshotStore.get(filePath);
                    if (stored != null) {
                        preLines = stored.lineCount;
                        preChars = stored.charCount;
                        LOG.info("GenAI-LOC |   PRE(PreSnapshotStore) file=" + post.fileName
                                + "  lines=" + preLines + "  chars=" + preChars);
                    } else {
                        LOG.info("GenAI-LOC |   SKIP (no PRE source) file=" + post.fileName);
                        continue;
                    }
                }

                double avgCharsPerLine = preLines > 0 ? (double) preChars / preLines : 40.0;
                int    charDelta       = post.charCount - preChars;
                int    netLines        = post.lineCount - preLines;

                // ── Net added / deleted from line count change ─────────────────
                int linesAdded   = Math.max(0, netLines);
                int linesDeleted = Math.max(0, -netLines);

                // ── Detect hidden deletions in replace operations ───────────────
                // When Copilot replaces existing lines with new ones, the net line
                // count may increase (more added than deleted), hiding the deletions.
                // If actual char gain < expected char gain for the added lines,
                // some existing lines were deleted/replaced.
                //   e.g. PRE 23/614 → POST 26/520
                //        expectedGain for +3 lines = 3×26.7 = 80
                //        actualGain = −94  → deficit = 174 → ~4 hidden deletions
                if (linesAdded > 0 && charDelta < (int)(linesAdded * avgCharsPerLine)) {
                    int charDeficit   = (int)(linesAdded * avgCharsPerLine) - charDelta;
                    int hiddenDeleted = (int)(charDeficit / avgCharsPerLine);
                    if (hiddenDeleted > 0) {
                        linesDeleted = Math.max(linesDeleted, hiddenDeleted);
                        LOG.info("GenAI-LOC |   hidden deletions detected: charDeficit=" + charDeficit
                                + "  hiddenDeleted=" + hiddenDeleted);
                    }
                }

                // ── Modified lines heuristic ───────────────────────────────────
                // Case 1: Pure in-place edit — same line count, chars changed
                // Case 2: Replace with line change — excess char delta beyond what
                //         the net add/delete explains → estimate modified lines
                int linesMod = 0;
                if (post.charCount != preChars) {
                    if (linesAdded == 0 && linesDeleted == 0) {
                        linesMod = Math.max(1, Math.abs(charDelta) / 40);
                    } else {
                        int expectedCharDelta = (int)((linesAdded - linesDeleted) * avgCharsPerLine);
                        int excessCharDelta   = Math.abs(charDelta - expectedCharDelta);
                        if (excessCharDelta > (int)(avgCharsPerLine * 2)) {
                            linesMod = Math.max(1, excessCharDelta / 40);
                        }
                    }
                }
                LOG.info("GenAI-LOC |   linesMod heuristic: preChars=" + preChars
                        + "  postChars=" + post.charCount
                        + "  charDelta=" + charDelta
                        + "  avgCharsPerLine=" + String.format("%.1f", avgCharsPerLine)
                        + "  linesAdded=" + linesAdded
                        + "  linesDeleted=" + linesDeleted
                        + "  linesMod=" + linesMod);

                LOG.info("GenAI-LOC |   DIFF file=" + post.fileName
                        + "  preLines=" + preLines + "  postLines=" + post.lineCount
                        + "  preChars=" + preChars + "  postChars=" + post.charCount
                        + "  +lines=" + linesAdded + "  -lines=" + linesDeleted
                        + "  ~lines=" + linesMod);

                if (linesAdded == 0 && linesDeleted == 0 && linesMod == 0) {
                    LOG.info("GenAI-LOC |   SKIP (no change) file=" + post.fileName);
                    continue;
                }
                diffs.add(new FileDiff(post, linesAdded, linesMod, linesDeleted));
            }

            // ── Send bundled event ─────────────────────────────────────────────
            int totalChanged = pendingEvents.size() + diffs.size();
            LOG.info("GenAI-LOC | Keep-All result: " + totalChanged + " file(s) changed"
                    + "  (" + pendingEvents.size() + " PendingLocEvent"
                    + "  + " + diffs.size() + " PRE/POST diff)");

            if (totalChanged == 0) {
                LOG.info("GenAI-LOC | No file changes detected — skipping service call and popup.");
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    GenAiLocSettings s = GenAiLocSettings.getInstance();
                    if (!s.isEnabled()) { LOG.info("GenAI-LOC | Plugin disabled"); return; }

                    Project project = findOpenProject();
                    if (project == null) { LOG.warn("GenAI-LOC | No open project"); return; }

                    GenAiLocProjectService svc = project.getService(GenAiLocProjectService.class);
                    if (svc == null) { LOG.warn("GenAI-LOC | Service not found"); return; }

                    String developerId   = s.getDeveloperId().isBlank()
                            ? System.getProperty("user.name", "unknown") : s.getDeveloperId();
                    String developerName = s.getDeveloperName().isBlank() ? developerId : s.getDeveloperName();
                    String projectId     = s.getProjectId().isBlank() ? project.getName() : s.getProjectId();
                    String sprintId      = s.getSprintId().isBlank() ? null : s.getSprintId();
                    String tool          = "COPILOT";
                    String sessionId     = svc.getSessionId();
                    String timestamp     = LocalDateTime.now().format(TS_FMT);

                    List<com.cts.plugin.intellij.loc.model.FileChange> allFileChanges = new java.util.ArrayList<>();

                    for (LOCRequestPayload req : pendingEvents) {
                        if (!req.getFileChanges().isEmpty()) {
                            allFileChanges.addAll(req.getFileChanges());
                        } else {
                            allFileChanges.add(com.cts.plugin.intellij.loc.model.FileChange.of(
                                    req.getFilePath(), req.getFileName(),
                                    req.getLinesAdded(), req.getLinesModified(), req.getLinesDeleted()));
                        }
                        LOG.info("GenAI-LOC | Bundled(Pending) file=" + req.getFileName()
                                + "  +=" + req.getLinesAdded() + "  ~=" + req.getLinesModified());
                    }

                    for (FileDiff diff : diffs) {
                        String cls = diff.snap.fileName.contains(".")
                                ? diff.snap.fileName.substring(0, diff.snap.fileName.lastIndexOf('.'))
                                : diff.snap.fileName;
                        allFileChanges.add(new com.cts.plugin.intellij.loc.model.FileChange(
                                diff.snap.filePath, diff.snap.fileName, cls,
                                diff.linesAdded, diff.linesModified, diff.linesDeleted));
                        LOG.info("GenAI-LOC | Bundled(diff) file=" + diff.snap.fileName
                                + "  +=" + diff.linesAdded + "  ~=" + diff.linesModified
                                + "  -=" + diff.linesDeleted);
                    }

                    int totalAdded    = allFileChanges.stream().mapToInt(com.cts.plugin.intellij.loc.model.FileChange::getLinesAdded).sum();
                    int totalModified = allFileChanges.stream().mapToInt(com.cts.plugin.intellij.loc.model.FileChange::getLinesModified).sum();
                    int totalDeleted  = allFileChanges.stream().mapToInt(com.cts.plugin.intellij.loc.model.FileChange::getLinesDeleted).sum();

                    String primaryPath, primaryFile, primaryClass;
                    if (!allFileChanges.isEmpty()) {
                        com.cts.plugin.intellij.loc.model.FileChange first = allFileChanges.get(0);
                        primaryPath  = first.getFilePath();
                        primaryFile  = first.getFileName();
                        primaryClass = first.getClassName();
                    } else {
                        primaryPath  = project.getBasePath() != null ? project.getBasePath() : "unknown";
                        primaryFile  = project.getName();
                        primaryClass = project.getName();
                    }

                    LOCRequestPayload bundledReq = new LOCRequestPayload(
                            developerId, developerName, projectId, sprintId,
                            primaryPath, primaryFile, primaryClass,
                            "INTELLIJ", tool, "BROWNFIELD",
                            totalAdded, totalModified, totalDeleted,
                            true, 0.95, timestamp, sessionId, allFileChanges);

                    // Compute total files updated / added / deleted
                    int filesUpdated = 0, filesAdded = 0, filesDeleted = 0;
                    for (com.cts.plugin.intellij.loc.model.FileChange fc : allFileChanges) {
                        if (fc.getLinesAdded() > 0 && fc.getLinesModified() == 0 && fc.getLinesDeleted() == 0) {
                            filesAdded++;
                        } else if (fc.getLinesDeleted() > 0 && fc.getLinesAdded() == 0 && fc.getLinesModified() == 0) {
                            filesDeleted++;
                        } else {
                            filesUpdated++;
                        }
                    }
                    bundledReq.setTotalFilesUpdated(filesUpdated);
                    bundledReq.setTotalFilesAdded(filesAdded);
                    bundledReq.setTotalFilesDeleted(filesDeleted);

                    LOG.info("GenAI-LOC | ENQUEUE bundled event: files=" + allFileChanges.size()
                            + "  totalAdded=" + totalAdded
                            + "  totalModified=" + totalModified
                            + "  totalDeleted=" + totalDeleted);
                    svc.enqueue(bundledReq);
                    svc.flushNow();

                    for (String fp : postSnapshots.keySet()) PreSnapshotStore.consume(fp);
                    showSummaryNotification(tool, Math.max(totalChanged, 1), pendingEvents, diffs, project);

                } catch (Exception ex) {
                    if (ex instanceof com.intellij.openapi.progress.ProcessCanceledException)
                        throw (RuntimeException) ex;
                    LOG.error("GenAI-LOC | Keep-All processing failed: " + ex.getMessage(), ex);
                }
            });

        }, SNAPSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // ── Local History PRE lookup ──────────────────────────────────────────────

    /**
     * Returns the PRE line/char count for a file from the DocumentListener cache.
     * The cache is populated in {@code beforeDocumentChange()} — i.e. BEFORE every
     * write to the document, including Copilot's live-preview write.
     *
     * @return int[2] = {lineCount, charCount}, or {@code null} if not cached yet.
     */
    private static int[] getPreCountFromCache(String filePath, String fileName) {
        int[] cached = PRE_STATE_CACHE.get(filePath);
        if (cached != null) {
            LOG.info("GenAI-LOC | PRE_STATE_CACHE hit for " + fileName
                    + "  lines=" + cached[0] + "  chars=" + cached[1]);
        } else {
            LOG.info("GenAI-LOC | PRE_STATE_CACHE miss for " + fileName);
        }
        return cached;
    }

    // ── Text extraction ───────────────────────────────────────────────────────

    /**
     * Attempts to extract the label text from the clicked component (or its ancestors).
     * The search is widened to 10 levels to accommodate deeply-nested Copilot inline-diff
     * action buttons in the code editor window.
     *
     * In addition to standard Swing text properties this method also checks:
     *  - Action#getText() / Presentation#getText() via ActionButton reflection
     *  - ClientProperty "actionButton.action" (IntelliJ ActionButton)
     *  - The component class name for known Copilot keep-button patterns
     */
    private static String extractKeepButtonText(Component clicked) {
        Component current = clicked;
        for (int depth = 0; depth < 10 && current != null; depth++) {
            // 1. Standard text / accessible text
            String text = getComponentText(current);
            if (text != null) {
                String clean = text.replaceAll("<[^>]+>", "").trim();
                if (!clean.isEmpty()) return clean;
            }
            String accessible = getAccessibleText(current);
            if (accessible != null && !accessible.isBlank()) return accessible;

            // 2. IntelliJ ActionButton — the "Keep All" / "Keep" buttons in inline diff
            //    are rendered as ActionButton instances; their text lives in the
            //    AnAction's Presentation object.
            String actionText = getActionButtonText(current);
            if (actionText != null && !actionText.isBlank()) return actionText;

            // 3. Class-name heuristic for Copilot inline diff buttons
            //    Copilot uses custom component classes that may not expose text through
            //    standard Swing APIs.  Match on class/simple-name patterns.
            String classHint = getClassNameHint(current);
            if (classHint != null) return classHint;

            current = current.getParent();
        }
        return null;
    }

    /**
     * Reads the AnAction Presentation text from an IntelliJ ActionButton component
     * via reflection (avoids a hard compile-time dependency on ActionButton).
     */
    private static String getActionButtonText(Component c) {
        try {
            // IntelliJ's ActionButton stores 'myAction' or 'myPresentation'
            String[] fields = { "myAction", "myPresentation", "action", "presentation" };
            for (String fieldName : fields) {
                try {
                    java.lang.reflect.Field f = findField(c.getClass(), fieldName);
                    if (f == null) continue;
                    f.setAccessible(true);
                    Object val = f.get(c);
                    if (val == null) continue;
                    // Try getText() on the object (works for both AnAction and Presentation)
                    try {
                        java.lang.reflect.Method getText = val.getClass().getMethod("getText");
                        Object txt = getText.invoke(val);
                        if (txt instanceof String && !((String) txt).isBlank()) return (String) txt;
                    } catch (NoSuchMethodException ignored) {}
                    // Try getTemplateText() (AnAction API)
                    try {
                        java.lang.reflect.Method getTpl = val.getClass().getMethod("getTemplateText");
                        Object txt = getTpl.invoke(val);
                        if (txt instanceof String && !((String) txt).isBlank()) return (String) txt;
                    } catch (NoSuchMethodException ignored) {}
                    // Try getTemplatePresentation().getText() (AnAction API)
                    try {
                        java.lang.reflect.Method getTplPres = val.getClass().getMethod("getTemplatePresentation");
                        Object pres = getTplPres.invoke(val);
                        if (pres != null) {
                            java.lang.reflect.Method getText2 = pres.getClass().getMethod("getText");
                            Object txt = getText2.invoke(pres);
                            if (txt instanceof String && !((String) txt).isBlank()) return (String) txt;
                        }
                    } catch (NoSuchMethodException ignored) {}
                } catch (Exception ignored) {}
            }
            // Also try getAction() method (some wrappers expose it)
            try {
                java.lang.reflect.Method getAction = c.getClass().getMethod("getAction");
                Object action = getAction.invoke(c);
                if (action != null) {
                    try {
                        java.lang.reflect.Method getText = action.getClass().getMethod("getText");
                        Object txt = getText.invoke(action);
                        if (txt instanceof String && !((String) txt).isBlank()) return (String) txt;
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        java.lang.reflect.Method getTpl = action.getClass().getMethod("getTemplateText");
                        Object txt = getTpl.invoke(action);
                        if (txt instanceof String && !((String) txt).isBlank()) return (String) txt;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ex) {
                LOG.debug("GenAI-LOC | getAction reflective: " + ex.getMessage());
            }
        } catch (Exception ex) {
            LOG.debug("GenAI-LOC | getActionButtonText: " + ex.getMessage());
        }
        return null;
    }

    /** Walks the class hierarchy to find a declared field with the given name. */
    private static java.lang.reflect.Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try { return cls.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Returns a keep-button label ("keep all" or "keep") if the component's class
     * name matches known Copilot inline-diff button patterns; otherwise null.
     *
     * Copilot / JetBrains diff editor uses class names such as:
     *   - *KeepAllAction*, *KeepAction*, *AcceptAction*
     *   - *DiffEditorToolbarAction*, *InlineKeep*
     * The simple-name check is intentionally broad so we catch future renames.
     */
    private static String getClassNameHint(Component c) {
        String cls = c.getClass().getName().toLowerCase();
        String simple = c.getClass().getSimpleName().toLowerCase();
        if (cls.contains("keepall") || simple.contains("keepall")) return "keep all";
        if (cls.contains("keep") || simple.contains("keep")) return "keep";
        // Copilot inline diff / editor buttons use Accept/Apply action classes
        if (cls.contains("acceptall") || simple.contains("acceptall")) return "accept all";
        if (cls.contains("applyall") || simple.contains("applyall")) return "apply all";
        if (cls.contains("acceptaction") || simple.contains("acceptaction")) return "accept";
        if (cls.contains("applyaction") || simple.contains("applyaction")) return "apply";
        if (cls.contains("applyin") || simple.contains("applyin")) return "apply in editor";
        if (cls.contains("applysuggestion") || simple.contains("applysuggestion")) return "apply suggestion";
        if (cls.contains("acceptsuggestion") || simple.contains("acceptsuggestion")) return "accept suggestion";
        if (cls.contains("insertatcursor") || simple.contains("insertatcursor")) return "insert at cursor";
        // Copilot inline completion accept (e.g. copilot.applyInlays)
        if (cls.contains("copilot") && (cls.contains("apply") || cls.contains("accept") || cls.contains("inlay"))) return "accept";
        return null;
    }

    private static String getComponentText(Component c) {
        try {
            if (c instanceof javax.swing.AbstractButton) return ((javax.swing.AbstractButton) c).getText();
            if (c instanceof javax.swing.JLabel) return ((javax.swing.JLabel) c).getText();
            // Do NOT extract text from JTextComponent / editor areas — that's code content,
            // not button text. Extracting it causes false positives on every editor click.
            // if (c instanceof javax.swing.text.JTextComponent) return ((javax.swing.text.JTextComponent) c).getText();
            try {
                java.lang.reflect.Method m = c.getClass().getMethod("getText");
                Object r = m.invoke(c);
                if (r instanceof String) return (String) r;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ex) {
                LOG.warn("GenAI-LOC | getComponentText reflective: " + ex.getMessage());
            }
        } catch (Exception ex) {
            LOG.warn("GenAI-LOC | getComponentText: " + ex.getMessage());
        }
        return null;
    }

    private static String getAccessibleText(Component c) {
        try {
            javax.accessibility.AccessibleContext ac = c.getAccessibleContext();
            if (ac != null) {
                String name = ac.getAccessibleName();
                if (name != null && !name.isBlank()) return name;
                String desc = ac.getAccessibleDescription();
                if (desc != null && !desc.isBlank()) return desc;
            }
            if (c instanceof javax.swing.JComponent) {
                String tip = ((javax.swing.JComponent) c).getToolTipText();
                if (tip != null && !tip.isBlank()) return tip;
            }
        } catch (Exception ex) {
            LOG.warn("GenAI-LOC | getAccessibleText: " + ex.getMessage());
        }
        return null;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static class EditorSnapshot {
        final String fileName, filePath;
        final int lineCount, charCount;
        EditorSnapshot(String fileName, String filePath, int lineCount, int charCount) {
            this.fileName = fileName; this.filePath = filePath;
            this.lineCount = lineCount; this.charCount = charCount;
        }
    }

    private static class FileDiff {
        final EditorSnapshot snap;
        final int linesAdded, linesModified, linesDeleted;
        FileDiff(EditorSnapshot snap, int linesAdded, int linesModified, int linesDeleted) {
            this.snap = snap;
            this.linesAdded = linesAdded; this.linesModified = linesModified; this.linesDeleted = linesDeleted;
        }
    }

    // ── Editor snapshot ───────────────────────────────────────────────────────

    private Map<String, EditorSnapshot> snapshotAllEditors() {
        AtomicReference<Map<String, EditorSnapshot>> result = new AtomicReference<>(Collections.emptyMap());
        try {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    Map<String, EditorSnapshot> map = new LinkedHashMap<>();
                    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                        if (project.isDisposed()) continue;
                        com.intellij.openapi.fileEditor.FileEditor[] editors =
                                FileEditorManager.getInstance(project).getAllEditors();
                        for (com.intellij.openapi.fileEditor.FileEditor fe : editors) {
                            if (!(fe instanceof TextEditor)) continue;
                            Editor ed = ((TextEditor) fe).getEditor();
                            VirtualFile vf = ed.getVirtualFile();
                            if (vf == null || !vf.isInLocalFileSystem()) continue;
                            String path = vf.getPath();
                            if (map.containsKey(path)) continue;
                            map.put(path, new EditorSnapshot(vf.getName(), path,
                                    ed.getDocument().getLineCount(), ed.getDocument().getTextLength()));
                        }
                    }
                    // Also include files tracked in PreSnapshotStore (may be in diff view)
                    for (String preStorePath : PreSnapshotStore.getAllPaths()) {
                        if (map.containsKey(preStorePath)) continue;
                        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl("file://" + preStorePath);
                        if (vf == null) vf = VirtualFileManager.getInstance().findFileByUrl(preStorePath);
                        if (vf == null || !vf.isInLocalFileSystem()) continue;
                        Document doc = FileDocumentManager.getInstance().getDocument(vf);
                        if (doc == null) continue;
                        map.put(preStorePath, new EditorSnapshot(vf.getName(), preStorePath,
                                doc.getLineCount(), doc.getTextLength()));
                    }
                    result.set(map);
                } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
                    throw pce;
                } catch (Exception ex) {
                    LOG.warn("GenAI-LOC | snapshotAllEditors inner: " + ex.getMessage());
                }
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
            throw pce;
        } catch (Exception ex) {
            LOG.error("GenAI-LOC | snapshotAllEditors: " + ex.getMessage(), ex);
        }
        return result.get();
    }

    // ── PendingLocEvent drain ─────────────────────────────────────────────────

    private List<LOCRequestPayload> drainPendingEventsWithRetry(Set<String> pendingFilePaths) {
        List<LOCRequestPayload> pendingEvents = new ArrayList<>();
        for (int attempt = 0; attempt < PENDING_DRAIN_RETRIES; attempt++) {
            LOCRequestPayload latest = PendingLocEvent.consumeLatest();
            while (latest != null) {
                String path = latest.getFilePath();
                int total = Math.abs(latest.getLinesAdded())
                        + Math.abs(latest.getLinesModified())
                        + Math.abs(latest.getLinesDeleted());
                if (path != null && total > 0) {
                    pendingFilePaths.add(path);
                    pendingEvents.add(latest);
                    LOG.info("GenAI-LOC | Consumed PendingLocEvent: " + latest.getFileName()
                            + "  +=" + latest.getLinesAdded() + "  ~=" + latest.getLinesModified());
                }
                latest = PendingLocEvent.consumeLatest();
            }
            if (!pendingEvents.isEmpty()) return pendingEvents;
            try { Thread.sleep(PENDING_DRAIN_RETRY_DELAY_MS); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return pendingEvents; }
        }
        return pendingEvents;
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void showSummaryNotification(String tool, int totalFiles,
                                          List<LOCRequestPayload> pending,
                                          List<FileDiff> diffs, Project project) {
        int totalAdded = 0, totalModified = 0;
        for (LOCRequestPayload r : pending) { totalAdded += r.getLinesAdded(); totalModified += r.getLinesModified(); }
        for (FileDiff d : diffs) { totalAdded += d.linesAdded; totalModified += d.linesModified; }
        String fileList = buildFileList(pending, diffs);
        String msg = "GenAI LOC: 🖱 Keep/Keep-All — " + tool
                + " | " + totalFiles + " file(s) | +" + totalAdded + " added  ~" + totalModified
                + " modified | " + fileList + " | Sent ✅";
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
            LOG.warn("GenAI-LOC | notification: " + ex.getMessage());
        }
    }

    private static String buildFileList(List<LOCRequestPayload> pending, List<FileDiff> diffs) {
        StringBuilder sb = new StringBuilder();
        for (LOCRequestPayload r : pending) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.getFileName()).append("(+").append(r.getLinesAdded()).append(")");
        }
        for (FileDiff d : diffs) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.snap.fileName).append("(+").append(d.linesAdded).append(")");
        }
        return sb.length() > 0 ? sb.toString() : "none";
    }

    private static Project findOpenProject() {
        Project[] p = ProjectManager.getInstance().getOpenProjects();
        return p.length > 0 ? p[0] : null;
    }


    /**
     * Builds a human-readable component hierarchy string for debugging.
     * e.g. "ActionButton > NonOpaquePanel > EditorImpl$MyPanel > ..."
     */
    private static String buildComponentHierarchy(Component c, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        Component cur = c;
        for (int i = 0; i < maxDepth && cur != null; i++) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(cur.getClass().getSimpleName());
            // Also append accessible name if available
            try {
                javax.accessibility.AccessibleContext ac = cur.getAccessibleContext();
                if (ac != null) {
                    String name = ac.getAccessibleName();
                    if (name != null && !name.isBlank()) sb.append("[\"").append(name).append("\"]");
                }
            } catch (Exception ignored) {}
            cur = cur.getParent();
        }
        return sb.toString();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /** Call once at startup to register this listener globally. */
    public static void register() {
        LOG.info("GenAI-LOC | register() called — scheduling on EDT");

        Runnable registration = () -> {
            try {
                LOG.info("GenAI-LOC | register() executing on EDT");
                CopilotKeepAllMouseListener listener = new CopilotKeepAllMouseListener();

                // ── Register DocumentListener via EditorFactory (public API) ─────────
                // beforeDocumentChange fires BEFORE every write to any open document,
                // including Copilot's live-preview write. We store the pre-change
                // line/char count into PRE_STATE_CACHE so the mouse listener can
                // always find the true PRE state regardless of when Keep All is clicked.
                EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                        new DocumentListener() {
                            @Override
                            public void beforeDocumentChange(@NotNull DocumentEvent e) {
                                VirtualFile vf = FileDocumentManager.getInstance().getFile(e.getDocument());
                                if (vf != null && vf.isInLocalFileSystem()) {
                                    int lines = e.getDocument().getLineCount();
                                    int chars = e.getDocument().getTextLength();
                                    PRE_STATE_CACHE.put(vf.getPath(), new int[]{lines, chars});
                                    LOG.info("GenAI-LOC | [DocListener] PRE captured: "
                                            + vf.getName() + "  lines=" + lines + "  chars=" + chars);
                                }
                            }

                            @Override
                            public void documentChanged(@NotNull DocumentEvent e) {
                                // Store PendingLocEvent for significant silent changes
                                // so GenAiCommandListener can drain them after Keep All
                                VirtualFile vf = FileDocumentManager.getInstance().getFile(e.getDocument());
                                if (vf == null || !vf.isInLocalFileSystem()) return;

                                String filePath = vf.getPath();
                                int[] pre = PRE_STATE_CACHE.get(filePath);
                                if (pre == null) return;

                                int preLines = pre[0];
                                int preChars = pre[1];
                                int postLines = e.getDocument().getLineCount();
                                int postChars = e.getDocument().getTextLength();

                                String newFrag = e.getNewFragment().toString();
                                int insertedChars = newFrag.length();
                                int insertedNewlines = (int) newFrag.chars().filter(ch -> ch == '\n').count();

                                // Only store for significant changes (Copilot Keep All pattern)
                                // Empty command + ≥5 chars inserted
                                if (insertedChars < 5) return;

                                int linesAdded = Math.max(0, postLines - preLines);
                                int linesModified = 0;
                                int linesDeleted = Math.max(0, preLines - postLines);

                                // In-place modification detection
                                if (linesAdded == 0 && linesDeleted == 0 && Math.abs(postChars - preChars) > 5) {
                                    linesModified = Math.max(1, Math.abs(postChars - preChars) / 40);
                                }

                                if (linesAdded == 0 && linesModified == 0 && linesDeleted == 0) return;

                                // Check if Copilot is installed
                                boolean copilotInstalled = com.intellij.ide.plugins.PluginManagerCore.getPlugins() != null
                                        && java.util.Arrays.stream(com.intellij.ide.plugins.PluginManagerCore.getPlugins())
                                        .anyMatch(p -> p.getPluginId().getIdString().toLowerCase().contains("copilot")
                                                && p.isEnabled());
                                if (!copilotInstalled) return;

                                String fileName = vf.getName();
                                String className = fileName.contains(".")
                                        ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

                                GenAiLocSettings s = GenAiLocSettings.getInstance();
                                String developerId = s.getDeveloperId().isBlank()
                                        ? System.getProperty("user.name", "unknown") : s.getDeveloperId();
                                String developerName = s.getDeveloperName().isBlank() ? developerId : s.getDeveloperName();
                                String projectId = s.getProjectId().isBlank() ? "unknown" : s.getProjectId();

                                LOCRequestPayload req = new LOCRequestPayload(
                                        developerId, developerName, projectId, null,
                                        filePath, fileName, className,
                                        "INTELLIJ", "COPILOT", "BROWNFIELD",
                                        linesAdded, linesModified, linesDeleted,
                                        true, 0.90,
                                        LocalDateTime.now().format(TS_FMT),
                                        null, new java.util.ArrayList<>());

                                PendingLocEvent.store(filePath, req);
                                LOG.info("GenAI-LOC | [DocListener] PENDING stored: " + fileName
                                        + "  +lines=" + linesAdded + "  ~lines=" + linesModified
                                        + "  -lines=" + linesDeleted + "  chars=" + insertedChars);
                            }
                        }
                );
                LOG.info("GenAI-LOC | DocumentListener registered via EditorFactory ✅");

                Toolkit.getDefaultToolkit().addAWTEventListener(
                        listener, AWTEvent.MOUSE_EVENT_MASK);
                LOG.info("GenAI-LOC | CopilotKeepAllMouseListener registered ✅"
                        + "  (PRE from DocumentListener cache, no background polling)"
                        + "  matching(safe): " + KEEP_BUTTON_SAFE
                        + "  matching(contextual): " + KEEP_BUTTON_CONTEXTUAL);
            } catch (Exception ex) {
                LOG.error("GenAI-LOC | register failed: " + ex.getMessage(), ex);
            }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            registration.run();
        } else {
            ApplicationManager.getApplication().invokeLater(registration);
        }
    }
}
