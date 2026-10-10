/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

import java.util.Locale;

/** Raw R08 slides and counted taps, after the bridge yields the ring to Reddit. */
public final class RingInput {
    public enum Action { NONE, NEXT, PREVIOUS, SELECT, BACK }
    public static final long TAP_WINDOW_MS = 350;
    private int taps;
    private long lastTap;
    private boolean tappedInEditor;

    public static boolean accepts(int key) {
        return key == 87 || key == 88 || key == 85 || key == 126 || key == 127;
    }

    public static boolean isRingDevice(String name) {
        return name != null && name.toUpperCase(Locale.ROOT).contains("R08");
    }

    public Action onKey(int action, int key, int repeat, long now, boolean editing) {
        if (action != 0 || repeat != 0) return Action.NONE;
        if (key == 87 || key == 88) {
            reset();
            return editing ? Action.NONE : key == 87 ? Action.NEXT : Action.PREVIOUS;
        }
        if (!accepts(key)) return Action.NONE;
        // A new isolated tap cannot combine with an expired sequence if the UI thread was busy.
        if (taps > 0 && now - lastTap >= TAP_WINDOW_MS) reset();
        taps++;
        lastTap = now;
        tappedInEditor |= editing;
        return Action.NONE;
    }

    public Action resolve(long now, boolean editing) {
        if (taps == 0 || now - lastTap < TAP_WINDOW_MS) return Action.NONE;
        Action result = taps == 2 ? Action.BACK
                : taps == 1 && !editing && !tappedInEditor ? Action.SELECT : Action.NONE;
        reset();
        return result;
    }

    public boolean pending() { return taps > 0; }

    public void reset() {
        taps = 0;
        lastTap = 0;
        tappedInEditor = false;
    }

    /** Only Back may leave the editor; ring actions never inject text into its field. */
    public static boolean mayDispatch(Action action, boolean foreground, boolean editing) {
        return foreground && action != Action.NONE && (!editing || action == Action.BACK);
    }
}
