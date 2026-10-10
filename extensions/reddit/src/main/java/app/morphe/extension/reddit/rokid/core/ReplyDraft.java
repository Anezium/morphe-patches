/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.core;

import java.util.Objects;

/** One draft owns one exact parent and at most one in-flight native operation. */
public final class ReplyDraft {
    public enum Phase { EDITING, PREVIEW, SENDING, SENT, FAILED, UNKNOWN, BLOCKED }

    public final ReplyTarget target;
    private String text = "";
    private Phase phase = Phase.EDITING;
    private long operation;
    private long revision;
    private String sentId;
    private String message = "";

    public ReplyDraft(ReplyTarget target) {
        this.target = Objects.requireNonNull(target);
    }

    public String text() { return text; }
    public Phase phase() { return phase; }
    public String message() { return message; }
    public String sentId() { return sentId; }
    public long revision() { return revision; }

    public void edit(String value) {
        if (phase == Phase.SENDING || phase == Phase.SENT || phase == Phase.UNKNOWN) return;
        text = value == null ? "" : value;
        phase = Phase.EDITING;
        revision++;
        message = "";
    }

    public boolean preview() {
        if (phase != Phase.EDITING && phase != Phase.FAILED && phase != Phase.BLOCKED) return false;
        if (text.trim().isEmpty()) {
            message = "Write a reply first.";
            return false;
        }
        if (text.length() > 10000) {
            message = "Your reply exceeds 10,000 characters.";
            return false;
        }
        phase = Phase.PREVIEW;
        revision++;
        message = "";
        return true;
    }

    public void returnToEditor() {
        if (phase == Phase.PREVIEW || phase == Phase.FAILED || phase == Phase.BLOCKED) {
            phase = Phase.EDITING;
            revision++;
        }
    }

    /** Called only by the explicit Send action after refreshing the native parent. */
    public long beginSend(ReplyTarget currentTarget, String activeAccountId, boolean parentAllowsReply) {
        if (phase != Phase.PREVIEW) return 0;
        if (!target.sameDestination(currentTarget) || !target.accountId.equals(activeAccountId)) {
            phase = Phase.BLOCKED;
            message = "The account or reply destination changed. Reopen the thread.";
            return 0;
        }
        if (!parentAllowsReply) {
            phase = Phase.BLOCKED;
            message = "This parent no longer accepts replies.";
            return 0;
        }
        phase = Phase.SENDING;
        message = "";
        return ++operation;
    }

    public boolean isApprovedPreview(long approvedRevision) {
        return phase == Phase.PREVIEW && revision == approvedRevision;
    }

    public boolean complete(long token, String replyId, String parentId, String postId) {
        if (phase != Phase.SENDING || token != operation) return false;
        if (replyId == null || !replyId.matches("t1_[a-z0-9]+")
                || !target.parentId.equals(parentId) || !target.postId.equals(postId)) {
            phase = Phase.UNKNOWN;
            message = "Check the thread before sending again.";
            return false;
        }
        phase = Phase.SENT;
        sentId = replyId;
        return true;
    }

    public boolean fail(long token, String reason, boolean definitelyRejected) {
        if (phase != Phase.SENDING || token != operation) return false;
        phase = definitelyRejected ? Phase.FAILED : Phase.UNKNOWN;
        message = definitelyRejected ? reason : "Sending outcome is unknown. Check the thread.";
        return true;
    }

    /** An uncertain write must be reconciled, never retried automatically. */
    public boolean reconcile(String replyId, String parentId, String postId) {
        if (phase != Phase.UNKNOWN || replyId == null || !replyId.matches("t1_[a-z0-9]+")
                || !target.parentId.equals(parentId) || !target.postId.equals(postId)) return false;
        sentId = replyId;
        phase = Phase.SENT;
        message = "";
        return true;
    }
}
