/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

import java.util.Objects;

/** Immutable destination retained from selecting Reply through the native send. */
public final class ReplyTarget {
    public enum Kind { POST, COMMENT }

    public final Kind kind;
    public final String postId;
    public final String parentId;
    public final String accountId;
    public final String author;
    public final String context;

    public ReplyTarget(Kind kind, String postId, String parentId, String accountId,
                       String author, String context) {
        this.kind = Objects.requireNonNull(kind);
        if (postId == null || !postId.matches("t3_[a-z0-9]+")) {
            throw new IllegalArgumentException("Invalid post identity");
        }
        if (kind == Kind.POST ? !postId.equals(parentId)
                : parentId == null || !parentId.matches("t1_[a-z0-9]+")) {
            throw new IllegalArgumentException("Invalid reply parent identity");
        }
        if (accountId == null || accountId.isEmpty()) {
            throw new IllegalArgumentException("An authenticated account is required");
        }
        this.postId = postId;
        this.parentId = parentId;
        this.accountId = accountId;
        this.author = author == null ? "" : author;
        this.context = context == null ? "" : context;
    }

    public boolean sameDestination(ReplyTarget other) {
        return other != null && kind == other.kind && postId.equals(other.postId)
                && parentId.equals(other.parentId) && accountId.equals(other.accountId);
    }
}
