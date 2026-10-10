/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

public final class SingleAxisInput {
    public enum Action { NONE, DUPLICATE, PREVIOUS, NEXT, SELECT, BACK }
    private long lastDirection = Long.MIN_VALUE;

    public Action map(int eventAction, int key, int repeat, long now) {
        if (eventAction != 0) return Action.NONE;
        Action action = classify(key);
        if (action == Action.NONE) return action;
        if (repeat > 0) return Action.DUPLICATE;
        if (action == Action.PREVIOUS || action == Action.NEXT) {
            if (lastDirection != Long.MIN_VALUE && now - lastDirection < 250) return Action.DUPLICATE;
            lastDirection = now;
        }
        return action;
    }

    public static Action classify(int key) {
        switch (key) {
            case 19: case 21: case 184: return Action.PREVIOUS;
            case 20: case 22: case 183: return Action.NEXT;
            case 23: case 62: case 66: case 160: case 202: return Action.SELECT;
            case 4: return Action.BACK;
            default: return Action.NONE;
        }
    }
}
