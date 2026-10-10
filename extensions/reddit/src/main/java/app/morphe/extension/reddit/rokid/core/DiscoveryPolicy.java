/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

/** Input and result boundaries shared by the pinned native discovery adapters. */
public final class DiscoveryPolicy {
    private DiscoveryPolicy() { }

    public static String query(String value) {
        String query = value == null ? "" : value.trim();
        if (query.isEmpty() || query.length() > 200 || hasControl(query)) {
            throw new IllegalArgumentException("Enter a search query of 1 to 200 characters");
        }
        return query;
    }

    public static String cursor(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.length() > 2048 || hasControl(value) || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Invalid native discovery cursor");
        }
        return value;
    }

    public static String nextCursor(String value, String previous) {
        String next = cursor(value);
        if (next != null && next.equals(previous)) {
            throw new IllegalStateException("Native discovery cursor did not advance");
        }
        return next;
    }

    public static String nextCursor(String value, String previous, boolean hasNextPage) {
        if (!hasNextPage) return null;
        String next = nextCursor(value, previous);
        if (next == null) throw new IllegalStateException("Native community cursor is missing");
        return next;
    }

    public static String communityId(String rawId, String fullId, String name) {
        if (rawId == null || !rawId.matches("[a-z0-9]+")
                || name == null || !name.matches("[A-Za-z0-9_]{1,21}")
                || !("t5_" + rawId).equals(fullId)) {
            throw new IllegalStateException("Native community identity is invalid");
        }
        return fullId;
    }

    public static boolean organicSearchPost(String type, Boolean promoted) {
        return ("Default".equals(type) || "Hero".equals(type)) && Boolean.FALSE.equals(promoted);
    }

    public static boolean sameAccount(Object session, String account, Object currentSession, String currentAccount) {
        return session != null && session == currentSession && account != null && !account.isEmpty()
                && account.equals(currentAccount);
    }

    /** Public feeds allow anonymous sessions, but never another graph or session owner. */
    public static boolean sameFeedOwner(Object graph, Object session, String account,
                                        Object currentGraph, Object currentSession, String currentAccount) {
        return graph != null && graph == currentGraph && session != null && session == currentSession
                && account != null && account.equals(currentAccount);
    }

    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }
}