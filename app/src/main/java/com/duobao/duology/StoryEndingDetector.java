package com.duobao.duology;

/**
 * Evidence-driven text DISPLAY detector for the ending of 夺宝奇缘 1.
 *
 * IMPORTANT: only call onTextDisplayed() for text that the emulator has
 * ACTUALLY displayed in the game frame/dialogue, NOT for raw MRP file reads.
 * A file-read callback would fire when map assets are loaded, long before
 * the actual ending is reached.
 *
 * Verified source: 夺宝奇缘1, entry #154 "14.map" (gzip inflated)
 * UTF-16BE byte offsets: 0x1494 for ——剧终—— and 0x14a2 for the first
 * sentence of its epilogue, 0x14e2 for the 妖之国 sentence.
 */
public final class StoryEndingDetector {
    private static final String[] MARKERS = {
            "剧终",
            "虽然制止了敖蛟的阴谋",
            "但是他的野心并没破灭",
            "如今逃往了妖之国",
            "又将会引发什么变故"
    };
    private static final int MAX_HISTORY = 8192;
    private final StringBuilder visibleHistory = new StringBuilder();
    private int nextMarker;
    private boolean pendingEndOfEpilogue;
    private boolean delivered;

    public synchronized void reset() {
        visibleHistory.setLength(0);
        nextMarker = 0;
        pendingEndOfEpilogue = false;
        delivered = false;
    }

    /** Call from runtime text render hook, never on asset loading. */
    public synchronized void onTextDisplayed(String displayed) {
        if (delivered || displayed == null || displayed.isEmpty()) return;
        // Screen OCR often omits decorative dashes, spaces and punctuation.
        // Keep only Chinese/letter/digit characters, but still require ALL
        // five semantically distinctive fragments in their original order.
        StringBuilder normalized = new StringBuilder(displayed.length());
        for (int i = 0; i < displayed.length(); i++) {
            char c = displayed.charAt(i);
            if (Character.isLetterOrDigit(c)) normalized.append(c);
        }
        visibleHistory.append(normalized);
        if (visibleHistory.length() > MAX_HISTORY) {
            visibleHistory.delete(0, visibleHistory.length() - MAX_HISTORY);
        }
        // Ordered markers: don't mistake a boss's repeated dialogue for a win.
        while (nextMarker < MARKERS.length) {
            String m = MARKERS[nextMarker];
            String history = visibleHistory.toString();
            int found = history.indexOf(m);
            if (found < 0) break;
            nextMarker++;
            // Consume the completed marker while keeping content following it.
            visibleHistory.delete(0, found + m.length());
        }
        pendingEndOfEpilogue = nextMarker == MARKERS.length;
    }

    /**
     * Invoke ONLY on a player's 'continue/confirm' input AFTER the last
     * epilogue line has actually been displayed (i.e., not the key press
     * used to reveal a slowly-scrolling text animation).
     */
    public synchronized boolean onEpilogueAcknowledged() {
        if (!pendingEndOfEpilogue || delivered) return false;
        delivered = true;
        return true;
    }

    /** Reenable the acknowledgment only when the commit of the save/transition failed. */
    public synchronized void retryAfterSaveFailure() {
        if (pendingEndOfEpilogue) delivered = false;
    }

    public synchronized int markersSeen() { return nextMarker; }
    public synchronized boolean awaitingAcknowledgement() { return pendingEndOfEpilogue && !delivered; }
}
