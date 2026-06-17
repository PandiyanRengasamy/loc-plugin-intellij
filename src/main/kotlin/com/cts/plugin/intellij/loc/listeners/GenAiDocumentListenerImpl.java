package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.GenAiToolDetector;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.cts.plugin.intellij.loc.util.PreSnapshotStore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GenAiDocumentListenerImpl
 *
 * Listens to every document change and decides whether it was produced by a
 * GenAI coding assistant (GitHub Copilot, Claude, ChatGPT, Gemini, …).
 *
 * Detection strategy
 * ──────────────────
 * Three tiers, evaluated in order:
 *
 *  Tier-1  EXPLICIT COMMAND  — the IntelliJ command name unambiguously names the AI tool
 *          (e.g. "copilot", "apply suggestion", "insert at cursor", "keep all").
 *          Threshold: ≥ 1 inserted newline.
 *
 *  Tier-2  KEEP / ACCEPT with Copilot active — "Keep All" / "Accept" in Copilot Chat
 *          inline-diff.  Only fires when the Copilot plugin is installed AND the block
 *          is large enough to be AI-generated (≥ 3 newlines).
 *
 *  Tier-3  PLUGIN ACTIVE + LARGE BLOCK — no AI command name but Copilot/Claude/etc.
 *          is installed and a significant block (≥ 5 newlines) was inserted atomically.
 *          Catches "Apply in Editor" and other programmatic insertions.
 *
 * Normal typing (Enter key = 1 newline, small pastes) never reaches Tier-2/3,
 * preventing the most common false-positive.
 */
public class GenAiDocumentListenerImpl implements DocumentListener {

    private static final Logger LOG = Logger.getInstance(GenAiDocumentListenerImpl.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Thresholds ────────────────────────────────────────────────────────────
    /** Tier-1: explicit AI command → even 1 inserted newline counts. */
    private static final int THRESHOLD_EXPLICIT   = 1;
    /** Tier-2: "keep/accept" command with Copilot active → ≥ 1 line. */
    private static final int THRESHOLD_KEEP       = 1;
    /**
     * Tier-2.5: no command name, Copilot installed, significant chars inserted.
     * Lowered to 5 to capture small single-line "Keep All" snippets from Copilot Chat.
     */
    private static final int THRESHOLD_SILENT_CHARS = 5;
    /** Tier-3: plugin active, no AI command → ≥ 3 lines to avoid false positives. */
    private static final int THRESHOLD_PASSIVE    = 3;
    /** Claude/Amazon Q passive threshold — typically dumps larger blocks. */
    private static final int THRESHOLD_CLAUDE_PASSIVE = 5;
    /** JetBrains AI Assistant passive threshold. */
    private static final int THRESHOLD_AIASST_PASSIVE = 5;
    /**
     * Minimum chars for early-exit bypass.
     * Lowered to 5 so that even small "Keep All" single-line AI replacements are NOT skipped.
     * The previous condition also required insertedChars != removedChars which
     * blocked same-size AI replacements — that check is now removed.
     */
    private static final int MIN_CHARS_BYPASS_EARLY_EXIT = 5;

    // ── Command-name keywords (all lowercase) ─────────────────────────────────
    // Copilot — Tier-1 explicit matches
    private static final String[] CMD_COPILOT_EXPLICIT = {
            "copilot",          // any "GitHub Copilot: …" command
            "apply suggestion", // Copilot Chat → Apply suggestion
            "insert at cursor", // Copilot Chat → Insert at Cursor
            "accept solution",  // Copilot Chat → Accept solution
            "apply copilot",    // Copilot Chat → Apply in Editor
            "apply in editor",  // Copilot Chat → Apply in Editor (alternate label)
            "insert suggestion",// Copilot inline suggestion accept
    };
    // Copilot — Tier-2 "keep / accept" patterns (need Copilot plugin + ≥ 1 line)
    private static final String[] CMD_COPILOT_KEEP = {
            "keep",             // Copilot Chat → Keep All / Keep
            "keep all",         // Copilot Chat → Keep All (explicit)
            "accept",           // Copilot Chat → Accept All
            "accept all",       // Copilot Chat → Accept All (explicit)
            "apply",            // Copilot Chat → Apply (generic)
            "replace",          // Copilot Chat → Replace Editor Content
            "update editor",    // Copilot Chat → Update Editor
            "insert code",      // Copilot Chat → Insert Code
            "discard",          // inverse — sometimes paired with keep in diff view
    };
    // Other tools — Tier-1 explicit matches
    private static final String CMD_CLAUDE   = "claude";
    private static final String CMD_AMAZONQ  = "amazon q";
    private static final String CMD_GEMINI   = "gemini";
    private static final String CMD_AI_ASST  = "ai action";   // JetBrains AI Assistant
    private static final String CMD_CHATGPT  = "chatgpt";

    // ── Copilot plugin IDs (checked in order) ─────────────────────────────────
    private static final String[] COPILOT_PLUGIN_IDS = {
            "com.github.copilot",
            "GitHub.copilot",
            "com.github.github-copilot",
            "com.github.copilot-intellij",
            "com.github.copilot.intellij",
            "github-copilot",
            "copilot",
            "com.github.copilot.jetbrains",   // newer packaging
            "com.github.copilot-chat",         // Copilot Chat standalone
    };

    // Auto-send timer REMOVED — CopilotActionListener handles all dispatching.

    private final Editor editor;

    /** Per-file snapshot of line count before each change. */
    private final Map<String, Integer> priorLineCounts = new ConcurrentHashMap<>();

    /** Cached resolved tool name (set once per listener instance). */
    private volatile String cachedTool = null;

    /** Cached [developerId, developerName] (set once per listener instance). */
    private volatile String[] cachedIdentity = null;

    public GenAiDocumentListenerImpl(Editor editor) {
        this.editor = editor;
    }

    // ── DocumentListener callbacks ────────────────────────────────────────────

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
        VirtualFile vf = FileDocumentManager.getInstance().getFile(e.getDocument());
        if (vf != null) {
            int lineCount = e.getDocument().getLineCount();
            int charCount = e.getDocument().getTextLength();
            priorLineCounts.put(vf.getPath(), lineCount);
            // Capture into the global PreSnapshotStore so CopilotKeepAllMouseListener
            // can retrieve the TRUE pre-change state even after Copilot has applied
            // its live preview (which makes the document already contain the new code
            // at mouse-press time, causing PRE == POST in the naive snapshot approach).
            PreSnapshotStore.capture(vf.getPath(), lineCount, charCount);
            LOG.info("GenAI-LOC | beforeDocumentChange: captured PRE snapshot for "
                    + vf.getName() + " lines=" + lineCount + " chars=" + charCount);
        }
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
        processChange(e);
    }

    // ── Core processing ───────────────────────────────────────────────────────

    private void processChange(@NotNull DocumentEvent e) {
        GenAiLocSettings settings = GenAiLocSettings.getInstance();
        if (!settings.isEnabled()) return;

        VirtualFile vf = FileDocumentManager.getInstance().getFile(e.getDocument());
        if (vf == null || !vf.isInLocalFileSystem()) return;

        String filePath  = vf.getPath();
        int priorLines   = priorLineCounts.getOrDefault(filePath, e.getDocument().getLineCount());
        int currentLines = e.getDocument().getLineCount();

        String newFragment       = e.getNewFragment().toString();
        String oldFragment       = e.getOldFragment().toString();
        int    insertedNewlines  = countNewlines(newFragment);
        int    removedNewlines   = countNewlines(oldFragment);

        int linesAdded    = Math.max(0, currentLines - priorLines);
        int linesDeleted  = Math.max(0, priorLines - currentLines);
        int linesModified = (removedNewlines > 0 && insertedNewlines > 0)
                ? Math.min(removedNewlines, insertedNewlines) : 0;

        int insertedChars = newFragment.length();
        int removedChars  = oldFragment.length();

        // Nothing new was written → skip, UNLESS a significant char-level replacement
        // happened (e.g. Copilot "Keep All" replaces lines in-place with same line count).
        // NOTE: We intentionally do NOT require insertedChars != removedChars here, because
        // "Keep All" in Copilot Chat replaces old code with AI code of similar length —
        // that is a valid GenAI event even when the character count is equal.
        boolean significantCharChange = insertedChars >= MIN_CHARS_BYPASS_EARLY_EXIT;
        if (linesAdded == 0 && linesModified == 0 && !significantCharChange) {
            priorLineCounts.put(filePath, currentLines);
            return;
        }

        String commandName = currentCommandName();

        LOG.info("GenAI-LOC | RAW-CHANGE file=" + vf.getName()
                + " cmd='" + commandName + "'"
                + " +nl=" + insertedNewlines + " +chars=" + insertedChars
                + " linesAdded=" + linesAdded + " linesMod=" + linesModified
                + " copilotInstalled=" + isCopilotInstalled());

        DetectionResult det = detectGenAi(
                resolveActiveTool(settings),
                commandName,
                insertedNewlines,
                insertedChars,
                newFragment,
                settings.getGenAiLineThreshold()
        );

        LOG.info("GenAI-LOC | file=" + vf.getName()
                + " +lines=" + linesAdded
                + " ~lines=" + linesModified
                + " -lines=" + linesDeleted
                + " +chars=" + insertedChars
                + " -chars=" + removedChars
                + " | tool=" + det.tool
                + " genAi=" + det.isGenAi
                + " conf=" + String.format("%.2f", det.confidence)
                + " cmd='" + commandName + "'"
                + " insertedNL=" + insertedNewlines);

        // ── Build & enqueue event ─────────────────────────────────────────────
        String[]               identity     = resolveDeveloperIdentity(settings);
        Project                project      = editor.getProject();
        GenAiLocProjectService svc          = resolveService(project);

        if (svc == null) {
            LOG.warn("GenAI-LOC | no GenAiLocProjectService — event dropped for " + vf.getName());
            priorLineCounts.put(filePath, currentLines);
            return;
        }

        String projectId = settings.getProjectId().isBlank()
                ? (project != null ? project.getName() : "unknown")
                : settings.getProjectId();
        String sprintId  = settings.getSprintId().isBlank() ? null : settings.getSprintId();
        String devMode   = priorLines <= 1 ? "GREENFIELD" : "BROWNFIELD";

        // For "Keep All" / in-place AI replacements where net linesAdded == 0,
        // report linesModified as the AI-accepted lines so the backend sees real work.
        int reportedLinesAdded    = linesAdded;
        int reportedLinesModified = linesModified;
        if (det.isGenAi && linesAdded == 0 && linesModified == 0 && insertedNewlines > 0) {
            // All accepted lines were replacements with same line count — treat as modified
            reportedLinesModified = insertedNewlines;
        }

        LOCRequestPayload req = new LOCRequestPayload(
                identity[0], identity[1],
                projectId, sprintId,
                filePath, vf.getName(),
                vf.getName().contains(".") ? vf.getName().substring(0, vf.getName().lastIndexOf('.')) : vf.getName(),
                "INTELLIJ",
                det.isGenAi ? det.tool : "NONE",
                devMode,
                reportedLinesAdded, reportedLinesModified, linesDeleted,
                det.isGenAi, det.isGenAi ? det.confidence : null,
                LocalDateTime.now().format(TS_FMT),
                svc.getSessionId()
        );

        // Set totalFiles counts for single-file event
        if (reportedLinesAdded > 0 && reportedLinesModified == 0 && linesDeleted == 0) {
            req.setTotalFilesAdded(1);
        } else if (linesDeleted > 0 && reportedLinesAdded == 0 && reportedLinesModified == 0) {
            req.setTotalFilesDeleted(1);
        } else {
            req.setTotalFilesUpdated(1);
        }

        // ── Dispatch / store event ────────────────────────────────────────────
        // For COPILOT events (whether explicit command or silent), always park in
        // PendingLocEvent so CopilotKeepAllMouseListener can pick it up when the
        // user clicks "Keep All". This avoids the race condition where the document
        // listener fires immediately and the mouse listener then sends a zero-count
        // duplicate event.
        //
        // For non-Copilot explicit commands (Claude, Gemini, ChatGPT), still enqueue
        // and flush immediately since there is no "Keep All" mouse click for those tools.
        boolean isCopilotEvent = det.isGenAi && "COPILOT".equals(det.tool);
        if (isCopilotEvent) {
            // Always park Copilot events — both silent AND explicit — for mouse listener
            PendingLocEvent.store(filePath, req);
            int effectiveLines = linesAdded > 0 ? linesAdded
                    : (linesModified > 0 ? linesModified : insertedNewlines);
            LOG.info("GenAI-LOC | Copilot change parked as PENDING (awaiting Keep-All confirmation)"
                    + " tool=" + det.tool
                    + " cmd='" + commandName + "'"
                    + " file=" + vf.getName()
                    + " effectiveLines=" + effectiveLines);
            showDetectionNotification(det.tool, vf.getName(), linesAdded, linesModified, insertedNewlines, project);
        } else if (det.isGenAi && commandName.isBlank()) {
            PendingLocEvent.store(filePath, req);
            int effectiveLines = linesAdded > 0 ? linesAdded
                    : (linesModified > 0 ? linesModified : insertedNewlines);
            LOG.info("GenAI-LOC | Silent AI change parked as PENDING (awaiting Keep-All confirmation)"
                    + " tool=" + det.tool
                    + " file=" + vf.getName()
                    + " effectiveLines=" + effectiveLines);
            showDetectionNotification(det.tool, vf.getName(), linesAdded, linesModified, insertedNewlines, project);
        } else {
            svc.enqueue(req);
            if (det.isGenAi) {
                int effectiveAiLines = linesAdded > 0
                        ? linesAdded
                        : (linesModified > 0 ? linesModified : insertedNewlines);
                LOG.info("GenAI-LOC | AI event (explicit cmd, non-Copilot) — flushing immediately"
                        + " (effectiveAiLines=" + effectiveAiLines + ")");
                showDetectionNotification(det.tool, vf.getName(), linesAdded, linesModified, insertedNewlines, project);
                svc.flushNow();
            }
        }

        priorLineCounts.put(filePath, currentLines);
        // After a confirmed non-AI change, reset the PreSnapshotStore baseline so the
        // next AI action compares against the correct (post-user-edit) state.
        if (!det.isGenAi) {
            PreSnapshotStore.forceCapture(filePath, currentLines, e.getDocument().getTextLength());
        }
    }


    private static String fileName(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    // ── Detection logic ───────────────────────────────────────────────────────

    private static class DetectionResult {
        final boolean isGenAi;
        final String  tool;
        final double  confidence;
        DetectionResult(boolean isGenAi, String tool, double confidence) {
            this.isGenAi    = isGenAi;
            this.tool       = tool;
            this.confidence = confidence;
        }
    }

    /**
     * Multi-tier detection. Returns a DetectionResult indicating whether the
     * change is AI-generated, which tool, and a confidence score.
     */
    private DetectionResult detectGenAi(
            String activeTool,
            String cmd,              // already lowercased
            int    insertedNewlines,
            int    insertedChars,
            String newFragment,
            int    configuredThreshold) {

        // ════════════════════════════════════════════════════════════════════
        // TIER-1 : Explicit AI command name → low threshold (≥ 1 newline)
        // ════════════════════════════════════════════════════════════════════

        // GitHub Copilot — explicit command
        if (containsAny(cmd, CMD_COPILOT_EXPLICIT) && insertedNewlines >= THRESHOLD_EXPLICIT) {
            return result(true, "COPILOT", insertedNewlines, THRESHOLD_EXPLICIT);
        }

        // Claude / Amazon Q — explicit command
        if ((cmd.contains(CMD_CLAUDE) || cmd.contains(CMD_AMAZONQ))
                && insertedNewlines >= THRESHOLD_EXPLICIT) {
            return result(true, "CLAUDE", insertedNewlines, THRESHOLD_EXPLICIT);
        }

        // JetBrains AI Assistant / ChatGPT — explicit command
        if ((cmd.contains(CMD_AI_ASST) || cmd.contains(CMD_CHATGPT))
                && insertedNewlines >= THRESHOLD_EXPLICIT) {
            return result(true, "CHATGPT", insertedNewlines, THRESHOLD_EXPLICIT);
        }

        // Gemini — explicit command
        if (cmd.contains(CMD_GEMINI) && insertedNewlines >= THRESHOLD_EXPLICIT) {
            return result(true, "GEMINI", insertedNewlines, THRESHOLD_EXPLICIT);
        }

        // ════════════════════════════════════════════════════════════════════
        // TIER-2 : "Keep All" / "Accept" from Copilot Chat inline diff
        //          Fires when Copilot plugin IS installed AND command name
        //          matches keep/accept/apply/replace AND either:
        //           - ≥ 1 inserted newline, OR
        //           - ≥ 20 chars inserted (handles single-line replacements)
        // ════════════════════════════════════════════════════════════════════
        if (isCopilotInstalled()
                && containsAny(cmd, CMD_COPILOT_KEEP)
                && (insertedNewlines >= THRESHOLD_KEEP || insertedChars >= 20)) {
            return result(true, "COPILOT", Math.max(insertedNewlines, 1), THRESHOLD_KEEP);
        }

        // ════════════════════════════════════════════════════════════════════
        // TIER-2.5 : Copilot installed, NO recognised command (empty cmd),
        //            but a significant amount of text was inserted atomically.
        //            This is the primary path for "Keep All" / "Apply in Editor"
        //            from Copilot Chat — those actions do NOT register an IntelliJ
        //            command name, so cmd == "" here.
        //            Condition A: ≥ 1 newline AND ≥ THRESHOLD_SILENT_CHARS chars
        //            Condition B: significant char change only (single-line "Keep All")
        // ════════════════════════════════════════════════════════════════════
        if (isCopilotInstalled() && cmd.isBlank()) {
            boolean condA = insertedNewlines >= THRESHOLD_KEEP && insertedChars >= THRESHOLD_SILENT_CHARS;
            boolean condB = insertedChars >= MIN_CHARS_BYPASS_EARLY_EXIT; // single-line replacement
            if (condA || condB) {
                LOG.info("GenAI-LOC | Tier-2.5 SILENT Copilot 'Keep All' detected: nl="
                        + insertedNewlines + " chars=" + insertedChars
                        + " reason=" + (condA ? "multi-line" : "single-line-replace"));
                return result(true, "COPILOT", Math.max(insertedNewlines, 1), THRESHOLD_KEEP);
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // TIER-2.6 : Any GenAI plugin installed, NO command name, any meaningful
        //            text inserted. This catches "Keep All" from Claude, Gemini,
        //            AI Assistant, and other tools that don't register commands.
        // ════════════════════════════════════════════════════════════════════
        if (cmd.isBlank() && insertedChars >= MIN_CHARS_BYPASS_EARLY_EXIT) {
            String anyTool = resolveAnyInstalledTool();
            if (anyTool != null) {
                LOG.info("GenAI-LOC | Tier-2.6 SILENT GenAI change detected: tool=" + anyTool
                        + " nl=" + insertedNewlines + " chars=" + insertedChars);
                return result(true, anyTool, Math.max(insertedNewlines, 1), THRESHOLD_KEEP);
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // TIER-3 : Plugin active, no recognisable AI command name.
        //          Catches "Apply in Editor" and other programmatic inserts.
        //          Threshold reduced to 3 to be more sensitive.
        // ════════════════════════════════════════════════════════════════════

        // Copilot passive (includes Copilot Chat "Apply in Editor" with no cmd)
        if (isCopilotInstalled() && insertedNewlines >= THRESHOLD_PASSIVE) {
            return result(true, "COPILOT", insertedNewlines, THRESHOLD_PASSIVE);
        }

        // Claude / Amazon Q passive
        if (isClaudeInstalled() && insertedNewlines >= THRESHOLD_CLAUDE_PASSIVE) {
            return result(true, "CLAUDE", insertedNewlines, THRESHOLD_CLAUDE_PASSIVE);
        }

        // JetBrains AI Assistant passive
        if (isAiAssistantInstalled() && insertedNewlines >= THRESHOLD_AIASST_PASSIVE) {
            return result(true, "CHATGPT", insertedNewlines, THRESHOLD_AIASST_PASSIVE);
        }

        // Gemini passive
        if (isGeminiInstalled() && insertedNewlines >= THRESHOLD_PASSIVE) {
            return result(true, "GEMINI", insertedNewlines, THRESHOLD_PASSIVE);
        }

        // ════════════════════════════════════════════════════════════════════
        // FALLBACK : use the configured active tool + user-defined threshold
        // ════════════════════════════════════════════════════════════════════
        if (!"NONE".equals(activeTool) && !"OTHER".equals(activeTool)
                && insertedNewlines >= configuredThreshold) {
            return result(true, activeTool, insertedNewlines, configuredThreshold);
        }

        return new DetectionResult(false, activeTool, 0.0);
    }

    // ── Plugin presence helpers ───────────────────────────────────────────────

    private boolean isCopilotInstalled() {
        for (String id : COPILOT_PLUGIN_IDS) {
            if (GenAiToolDetector.isPluginEnabled(id)) return true;
        }
        return false;
    }

    private boolean isClaudeInstalled() {
        return GenAiToolDetector.isPluginEnabled("com.anthropic.claude")
                || GenAiToolDetector.isPluginEnabled("amazon.q");
    }

    private boolean isAiAssistantInstalled() {
        return GenAiToolDetector.isPluginEnabled("com.intellij.ml.llm")
                || GenAiToolDetector.isPluginEnabled("com.openai.chatgpt");
    }

    private boolean isGeminiInstalled() {
        return GenAiToolDetector.isPluginEnabled("com.google.ide-plugin")
                || GenAiToolDetector.isPluginEnabled("com.google.gemini");
    }

    /**
     * Returns the name of the first GenAI tool detected (any tool),
     * or null if no GenAI plugin is installed. Used for Tier-2.6 fallback.
     */
    @Nullable
    private String resolveAnyInstalledTool() {
        if (isCopilotInstalled())      return "COPILOT";
        if (isClaudeInstalled())       return "CLAUDE";
        if (isAiAssistantInstalled())  return "CHATGPT";
        if (isGeminiInstalled())       return "GEMINI";
        return null;
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private static boolean containsAny(String cmd, String[] keywords) {
        for (String kw : keywords) {
            if (cmd.contains(kw)) return true;
        }
        return false;
    }

    private static DetectionResult result(boolean isAi, String tool,
                                          int insertedNewlines, int threshold) {
        double conf = Math.min(0.97, 0.70 + (insertedNewlines - threshold) * 0.02);
        return new DetectionResult(isAi, tool, conf);
    }

    @NotNull
    private String currentCommandName() {
        String name = CommandProcessor.getInstance().getCurrentCommandName();
        return name != null ? name.toLowerCase() : "";
    }

    private String resolveActiveTool(GenAiLocSettings settings) {
        if (cachedTool == null) {
            String configured = settings.getDefaultGenAiTool();
            cachedTool = (configured != null && !configured.isBlank())
                    ? configured
                    : GenAiToolDetector.detectPrimary();
            LOG.debug("GenAI-LOC | resolvedTool=" + cachedTool);
        }
        return cachedTool;
    }

    private String[] resolveDeveloperIdentity(GenAiLocSettings s) {
        if (cachedIdentity != null) return cachedIdentity;

        if (!s.getDeveloperId().isBlank()) {
            String name = s.getDeveloperName().isBlank() ? s.getDeveloperId() : s.getDeveloperName();
            cachedIdentity = new String[]{ s.getDeveloperId(), name };
            LOG.info("GenAI-LOC | identity from settings: id=" + cachedIdentity[0]);
            return cachedIdentity;
        }

        // Fall back to OS login
        String osUser     = System.getProperty("user.name", "unknown");
        String domainUser = System.getenv("USERNAME");
        String domain     = System.getenv("USERDOMAIN");
        String resolvedId = (domainUser != null && !domainUser.isBlank()) ? domainUser : osUser;
        String resolvedName = (domain != null && !domain.isBlank()) ? domain + "/" + osUser : osUser;

        cachedIdentity = new String[]{ resolvedId, resolvedName };
        LOG.info("GenAI-LOC | identity from OS: id=" + resolvedId + " name=" + resolvedName);
        return cachedIdentity;
    }

    @Nullable
    private GenAiLocProjectService resolveService(@Nullable Project project) {
        if (project != null) {
            return project.getService(GenAiLocProjectService.class);
        }
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        return open.length > 0 ? open[0].getService(GenAiLocProjectService.class) : null;
    }

    private int countNewlines(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }

    private void showDetectionNotification(String tool, String fileName,
                                           int linesAdded, int linesModified,
                                           int insertedNewlines, @Nullable Project project) {
        // Compute the effective AI lines for display:
        //   - "Keep All" that adds lines       → linesAdded > 0
        //   - "Keep All" that modifies lines   → linesModified > 0
        //   - "Keep" replacing same-count code → fall back to insertedNewlines
        int effectiveLines = linesAdded > 0
                ? linesAdded
                : (linesModified > 0 ? linesModified : insertedNewlines);

        String action = linesAdded > 0 ? "added" : "kept/modified";
        String msg = "GenAI LOC: 🤖 Detected " + tool + " code in " + fileName
                + " (" + effectiveLines + " lines " + action + "). Sending to backend...";
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                com.intellij.notification.NotificationGroup group =
                        com.intellij.notification.NotificationGroupManager.getInstance()
                                .getNotificationGroup("GenAI LOC Tracker");
                com.intellij.notification.Notification n = (group != null)
                        ? group.createNotification(msg,
                                com.intellij.notification.NotificationType.INFORMATION)
                        : new com.intellij.notification.Notification(
                                "GenAI LOC Tracker", "GenAI LOC Tracker", msg,
                                com.intellij.notification.NotificationType.INFORMATION);
                com.intellij.notification.Notifications.Bus.notify(n, project);
            } catch (Exception ex) {
                LOG.warn("GenAI-LOC | notification failed: " + ex.getMessage());
            }
        });
    }
}
