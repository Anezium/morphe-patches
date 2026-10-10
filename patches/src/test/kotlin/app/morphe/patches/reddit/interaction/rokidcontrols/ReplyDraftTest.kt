/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.extension.reddit.rokid.core.ReplyDraft
import app.morphe.extension.reddit.rokid.core.ReplyTarget
import org.junit.Assert.*
import org.junit.Test

class ReplyDraftTest {
    private fun target(parent: String = "t1_parent", account: String = "account-a") =
        ReplyTarget(ReplyTarget.Kind.COMMENT, "t3_post", parent, account, "alice", "Parent text")

    private fun draft() = ReplyDraft(target()).apply { edit("A reply\nwith another line") }

    @Test fun finishingEditingCannotPublish() {
        val draft = draft()
        assertEquals(0L, draft.beginSend(target(), "account-a", true))
        assertEquals(ReplyDraft.Phase.EDITING, draft.phase())
        assertTrue(draft.preview())
        draft.returnToEditor()
        assertEquals("A reply\nwith another line", draft.text())
    }

    @Test fun duplicateSendAndStaleCompletionAreRejected() {
        val draft = draft()
        assertTrue(draft.preview())
        val token = draft.beginSend(target(), "account-a", true)
        assertTrue(token > 0)
        assertEquals(0L, draft.beginSend(target(), "account-a", true))
        assertFalse(draft.complete(token + 1, "t1_reply", "t1_parent", "t3_post"))
        assertTrue(draft.complete(token, "t1_reply", "t1_parent", "t3_post"))
    }

    @Test fun changingParentOrAccountBlocksSendAndRetainsDraft() {
        for (changed in listOf(target("t1_other"), target(account = "account-b"))) {
            val draft = draft()
            draft.preview()
            assertEquals(0L, draft.beginSend(changed, changed.accountId, true))
            assertEquals(ReplyDraft.Phase.BLOCKED, draft.phase())
            assertEquals("A reply\nwith another line", draft.text())
        }
    }

    @Test fun lockedParentCannotBeSubmitted() {
        val draft = draft()
        draft.preview()
        assertEquals(0L, draft.beginSend(target(), "account-a", false))
        assertEquals(ReplyDraft.Phase.BLOCKED, draft.phase())
    }

    @Test fun nestedReplyCannotCompleteAsTopLevelComment() {
        val draft = draft()
        draft.preview()
        val token = draft.beginSend(target(), "account-a", true)
        assertFalse(draft.complete(token, "t1_reply", "t3_post", "t3_post"))
        assertEquals(ReplyDraft.Phase.UNKNOWN, draft.phase())
        assertEquals(0L, draft.beginSend(target(), "account-a", true))
    }

    @Test fun unknownNetworkOutcomeRequiresReconciliation() {
        val draft = draft()
        draft.preview()
        val token = draft.beginSend(target(), "account-a", true)
        assertTrue(draft.fail(token, "Network disconnected", false))
        assertFalse(draft.preview())
        draft.edit("Replacement text")
        assertEquals("A reply\nwith another line", draft.text())
        assertFalse(draft.reconcile("t1_reply", "t1_other", "t3_post"))
        assertTrue(draft.reconcile("t1_reply", "t1_parent", "t3_post"))
    }

    @Test fun explicitRejectionPreservesDraftAndAllowsReviewAgain() {
        val draft = draft()
        draft.preview()
        val token = draft.beginSend(target(), "account-a", true)
        draft.fail(token, "Rate limited", true)
        assertEquals(ReplyDraft.Phase.FAILED, draft.phase())
        assertTrue(draft.preview())
    }

    @Test fun emptyAndOversizedRepliesCannotReachPreview() {
        val draft = draft()
        draft.edit(" \n ")
        assertFalse(draft.preview())
        draft.edit("a".repeat(10001))
        assertFalse(draft.preview())
    }

    @Test fun editingDuringParentRefreshInvalidatesTheEarlierSendApproval() {
        val draft = draft()
        draft.preview()
        val approved = draft.revision()
        assertTrue(draft.isApprovedPreview(approved))
        draft.returnToEditor()
        draft.edit("Another reply")
        draft.preview()
        assertFalse(draft.isApprovedPreview(approved))
        assertTrue(draft.isApprovedPreview(draft.revision()))
    }
}
