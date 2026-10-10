/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

/** Swiping continues a post into media only after the final preview page. */
public final class PostNavigation {
    public enum Move { PAGE, OPEN_MEDIA, EDGE }
    private int page;
    private int pageCount = 1;
    private boolean media;

    public void configure(int count, boolean hasMedia, int restoredPage) {
        pageCount = Math.max(1, count);
        media = hasMedia;
        page = Math.max(0, Math.min(restoredPage, pageCount - 1));
    }

    public int page() { return page; }

    public Move move(int direction) {
        if (direction > 0 && page == pageCount - 1) return media ? Move.OPEN_MEDIA : Move.EDGE;
        int next = Math.max(0, Math.min(page + Integer.signum(direction), pageCount - 1));
        if (next == page) return Move.EDGE;
        page = next;
        return Move.PAGE;
    }

    public static boolean acceptsMediaSwipe(long openedAt, long now) {
        return now - openedAt >= 300;
    }
}
