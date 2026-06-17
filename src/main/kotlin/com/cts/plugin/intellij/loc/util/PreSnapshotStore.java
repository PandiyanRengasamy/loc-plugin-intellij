package com.cts.plugin.intellij.loc.util;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PreSnapshotStore — captures the TRUE pre-change document state.
 *
 * Problem solved
 * ──────────────
 * GitHub Copilot Chat applies AI-generated code to the editor as a LIVE PREVIEW
 * before the user clicks "Keep All". This means:
 *   • GenAiDocumentListenerImpl.beforeDocumentChange() fires with the real PRE state.
 *   • CopilotKeepAllMouseListener fires at click time — but by then the document
 *     already contains the new code, so snapshotAllEditors() captures POST == PRE → diff = 0.
 *
 * Solution
 * ─────────
 * 1. GenAiDocumentListenerImpl.beforeDocumentChange() calls {@link #capture} to record
 *    the exact line count and char count BEFORE any AI change is applied.
 * 2. CopilotKeepAllMouseListener reads from {@link #get} instead of snapshotting the
 *    current document. This gives the genuine PRE values.
 * 3. Snapshots expire after {@link #TTL_MS} ms to prevent stale data.
 *
 * This works correctly even when:
 *   • Copilot applies its diff before the click (live preview mode).
 *   • Multiple files are changed in one "Keep All" session.
 *   • The user delays clicking "Keep All" for up to {@link #TTL_MS} ms.
 */
public final class PreSnapshotStore {

    private static final Logger LOG = Logger.getInstance(PreSnapshotStore.class);

    /** How long a snapshot stays valid. User should click Keep All within 5 minutes. */
    public static final long TTL_MS = 300_000L; // 5 minutes

    /** Key = absolute file path. */
    private static final Map<String, Snapshot> STORE = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Capture (or update) the pre-change state for a file.
     * Call this from {@code beforeDocumentChange()} — before any edit is applied.
     *
     * @param filePath  absolute path of the file being changed
     * @param lineCount document line count BEFORE the change
     * @param charCount document character count BEFORE the change
     */
    public static void capture(String filePath, int lineCount, int charCount) {
        // Only update if there is no existing (non-expired) snapshot.
        // This ensures we keep the FIRST (oldest) pre-change state, not the
        // intermediate states produced by Copilot streaming its diff in chunks.
        Snapshot existing = STORE.get(filePath);
        if (existing != null && !existing.isExpired()) {
            LOG.info("PreSnapshotStore.capture: KEEP existing pre-snapshot for "
                    + fileName(filePath) + " lines=" + existing.lineCount
                    + " (ignoring intermediate change: lines=" + lineCount + ")");
            return;
        }
        STORE.put(filePath, new Snapshot(lineCount, charCount));
        LOG.info("PreSnapshotStore.capture: STORED pre-snapshot for "
                + fileName(filePath) + " lines=" + lineCount + " chars=" + charCount);
    }

    /**
     * Force-update the snapshot even if one already exists.
     * Use this when you KNOW a genuine user edit (non-AI) has just completed
     * and you need to reset the baseline.
     *
     * @param filePath  absolute path of the file
     * @param lineCount new baseline line count
     * @param charCount new baseline char count
     */
    public static void forceCapture(String filePath, int lineCount, int charCount) {
        STORE.put(filePath, new Snapshot(lineCount, charCount));
        LOG.info("PreSnapshotStore.forceCapture: RESET pre-snapshot for "
                + fileName(filePath) + " lines=" + lineCount + " chars=" + charCount);
    }

    /**
     * Retrieve (without removing) the stored pre-snapshot for a file.
     *
     * @param filePath absolute path of the file
     * @return the stored {@link Snapshot}, or {@code null} if not present or expired
     */
    public static Snapshot get(String filePath) {
        Snapshot s = STORE.get(filePath);
        if (s == null) {
            LOG.info("PreSnapshotStore.get: NO pre-snapshot for " + fileName(filePath));
            return null;
        }
        if (s.isExpired()) {
            STORE.remove(filePath);
            LOG.info("PreSnapshotStore.get: EXPIRED pre-snapshot discarded for " + fileName(filePath));
            return null;
        }
        LOG.info("PreSnapshotStore.get: found pre-snapshot for " + fileName(filePath)
                + " lines=" + s.lineCount + " chars=" + s.charCount
                + " age=" + (System.currentTimeMillis() - s.capturedAt) + "ms");
        return s;
    }

    /**
     * Retrieve AND remove the stored pre-snapshot for a file.
     * Call this after the LOC diff has been calculated so the entry is cleared.
     *
     * @param filePath absolute path of the file
     * @return the stored {@link Snapshot}, or {@code null} if not present or expired
     */
    public static Snapshot consume(String filePath) {
        Snapshot s = STORE.remove(filePath);
        if (s == null) {
            LOG.info("PreSnapshotStore.consume: NOTHING to consume for " + fileName(filePath));
            return null;
        }
        if (s.isExpired()) {
            LOG.info("PreSnapshotStore.consume: EXPIRED entry discarded for " + fileName(filePath));
            return null;
        }
        LOG.info("PreSnapshotStore.consume: consumed pre-snapshot for " + fileName(filePath)
                + " lines=" + s.lineCount + " chars=" + s.charCount
                + " age=" + (System.currentTimeMillis() - s.capturedAt) + "ms");
        return s;
    }

    /**
     * Returns {@code true} if a valid (non-expired) pre-snapshot exists for the file.
     *
     * @param filePath absolute path of the file
     */
    public static boolean has(String filePath) {
        Snapshot s = STORE.get(filePath);
        if (s == null) return false;
        if (s.isExpired()) { STORE.remove(filePath); return false; }
        return true;
    }

    /** Returns the number of entries currently in the store (including possibly expired ones). */
    public static int size() {
        return STORE.size();
    }

    /**
     * Returns all file paths that currently have a valid (non-expired) pre-snapshot.
     * Used by CopilotKeepAllMouseListener to locate files that may not be in any
     * TextEditor (e.g. shown only in a Copilot Chat diff panel).
     *
     * @return set of absolute file paths with valid snapshots
     */
    public static java.util.Set<String> getAllPaths() {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Snapshot> e : STORE.entrySet()) {
            if (!e.getValue().isExpired()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private PreSnapshotStore() { /* utility class */ }

    private static String fileName(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /** Immutable snapshot of a document's state at a point in time. */
    public static final class Snapshot {
        /** Line count BEFORE the document change. */
        public final int  lineCount;
        /** Character count BEFORE the document change. */
        public final int  charCount;
        /** Wall-clock timestamp when this snapshot was created. */
        public final long capturedAt;

        Snapshot(int lineCount, int charCount) {
            this.lineCount  = lineCount;
            this.charCount  = charCount;
            this.capturedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - capturedAt > TTL_MS;
        }

        @Override
        public String toString() {
            return "Snapshot{lines=" + lineCount + " chars=" + charCount
                    + " age=" + (System.currentTimeMillis() - capturedAt) + "ms}";
        }
    }
}

