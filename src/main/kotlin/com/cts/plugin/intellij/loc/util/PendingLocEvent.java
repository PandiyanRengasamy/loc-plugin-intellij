package com.cts.plugin.intellij.loc.util;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * PendingLocEvent — Thread-safe registry for "pending" GenAI LOC events.
 *
 * Problem solved
 * ──────────────
 * GitHub Copilot Chat (and similar tools) apply the AI-generated code to the
 * editor document AS A LIVE PREVIEW before the user clicks "Keep All".
 * This means:
 *   • GenAiDocumentListenerImpl fires when the preview is shown (the correct LOC diff).
 *   • CopilotKeepAllMouseListener fires at Keep-All click time — but by then the
 *     document already contains the new code, so PRE == POST → no diff → skipped.
 *
 * Solution
 * ─────────
 * 1. GenAiDocumentListenerImpl detects a silent (no command-name) Copilot insertion.
 *    Instead of enqueuing immediately, it calls {@link #store} to park the event here.
 * 2. CopilotKeepAllMouseListener handles the Keep-All click and calls {@link #consume}.
 *    If a pending event exists for the active file, it sends THAT event to the backend.
 * 3. If the user clicks "Discard" or ignores the suggestion, the entry expires after
 *    {@link #TTL_MS} ms and is silently dropped.
 *
 * This ensures:
 *   • Events are only sent after the user CONFIRMS the code (no premature sends).
 *   • No double-counting between the document listener and the mouse listener.
 *   • Fallback still works: if no pending event exists, the mouse listener falls back
 *     to the PRE/POST snapshot comparison.
 */
public final class PendingLocEvent {

    private static final Logger LOG = Logger.getInstance(PendingLocEvent.class);

    /** How long a pending event stays valid (ms). User should click Keep All within 60 s. */
    public static final long TTL_MS = 60_000L;

    // ── Storage ───────────────────────────────────────────────────────────────

    /** Key = absolute file path. */
    private static final Map<String, Entry> PENDING = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Store a pending LOC event for the given file.
     * If a previous entry already exists for the file it is replaced.
     *
     * @param filePath absolute path of the edited file
     * @param req      the pre-built CodeEventRequest to hold
     */
    public static void store(String filePath, LOCRequestPayload req) {
        boolean alreadyPending = PENDING.containsKey(filePath);
        PENDING.put(filePath, new Entry(req));
        LOG.info("GenAI-LOC | PendingLocEvent.store: file=" + fileName(filePath)
                + " +lines=" + req.getLinesAdded()
                + " ~lines=" + req.getLinesModified()
                + " tool=" + req.getGenAiTool()
                + (alreadyPending ? " [OVERWRITE: previous pending event existed]" : ""));
        if (PENDING.size() > 0) {
            LOG.info("GenAI-LOC | PendingLocEvent.store: total pending events after store: " + PENDING.size());
        }
    }

    /**
     * Consume (retrieve and remove) the pending event for the given file.
     * Returns {@code null} if no valid (non-expired) entry exists.
     *
     * @param filePath absolute path of the edited file
     * @return the stored {@link LOCRequestPayload}, or {@code null}
     */
    public static LOCRequestPayload consume(String filePath) {
        Entry entry = PENDING.remove(filePath);
        if (entry == null) {
            LOG.info("");
            return null;
        }
        if (entry.isExpired()) {
            LOG.info("GenAI-LOC | PendingLocEvent.consume: expired entry discarded for " + fileName(filePath));
            return null;
        }
        LOG.info("GenAI-LOC | PendingLocEvent.consume: returning pending event for " + fileName(filePath)
                + " age=" + (System.currentTimeMillis() - entry.createdAt) + "ms");
        return entry.request;
    }

    /**
     * Consume the most-recently stored pending event across ALL files.
     * Used when the active file path is unknown at Keep-All click time.
     * Returns {@code null} if the map is empty or all entries are expired.
     */
    public static LOCRequestPayload consumeLatest() {
        if (PENDING.isEmpty()) return null;

        String latestKey = null;
        long   latestTs  = Long.MIN_VALUE;
        for (Map.Entry<String, Entry> e : PENDING.entrySet()) {
            if (!e.getValue().isExpired() && e.getValue().createdAt > latestTs) {
                latestTs  = e.getValue().createdAt;
                latestKey = e.getKey();
            }
        }
        if (latestKey == null) { PENDING.clear(); return null; }
        return consume(latestKey);
    }

    /**
     * Discard any pending event for the given file without sending it.
     * Call this when the user clicks "Discard" / "Reject".
     *
     * @param filePath absolute path of the edited file
     */
    public static void discard(String filePath) {
        Entry removed = PENDING.remove(filePath);
        if (removed != null) {
            LOG.info("GenAI-LOC | PendingLocEvent.discard: pending event for "
                    + fileName(filePath) + " dropped");
        }
    }

    /**
     * Returns {@code true} if a non-expired pending event exists for the file.
     *
     * @param filePath absolute path of the edited file
     */
    public static boolean hasPending(String filePath) {
        Entry e = PENDING.get(filePath);
        if (e == null) return false;
        if (e.isExpired()) { PENDING.remove(filePath); return false; }
        return true;
    }

    /** Returns the total number of pending (possibly expired) entries. */
    public static int size() {
        return PENDING.size();
    }

    /**
     * Clear all pending events. Use with caution.
     */
    public static void clearAll() {
        PENDING.clear();
        LOG.info("GenAI-LOC | PendingLocEvent.clearAll: all pending events cleared");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private PendingLocEvent() { /* utility class */ }

    private static String fileName(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    /** Immutable holder for a pending event and its creation timestamp. */
    private static final class Entry {
        final LOCRequestPayload request;
        final long             createdAt;

        Entry(LOCRequestPayload request) {
            this.request   = request;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TTL_MS;
        }
    }
}

