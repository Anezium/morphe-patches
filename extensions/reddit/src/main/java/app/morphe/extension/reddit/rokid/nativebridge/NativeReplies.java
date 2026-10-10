/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import app.morphe.extension.reddit.rokid.core.ReplyDraft;
import app.morphe.extension.reddit.rokid.core.ReplyTarget;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Revalidate the original parent and account before the one explicit native create call. */
public final class NativeReplies {
    private static final String REPOSITORY = "com.reddit.comment.data.repository.b";
    private static final String SORT = "com.reddit.listing.model.sort.CommentSortType";
    private final NativeCalls calls;
    private boolean validating;
    private Runnable sending;

    public NativeReplies(NativeCalls calls) { this.calls = calls; }
    public boolean isValidating() { return validating; }

    public void send(ReplyDraft draft, Runnable sending, NativeCalls.Callback completion) {
        if (validating || draft.phase() != ReplyDraft.Phase.PREVIEW) return;
        validating = true;
        this.sending = sending;
        long revision = draft.revision();
        try {
            if (!draft.target.accountId.equals(NativeBindings.account())) {
                block(draft, completion);
                return;
            }
            if (draft.target.kind == ReplyTarget.Kind.POST) refreshPost(draft, revision, completion);
            else refreshComment(draft, revision, completion);
        } catch (ReflectiveOperationException | RuntimeException error) {
            validating = false;
            completion.complete(null, error);
        }
    }

    private void refreshPost(ReplyDraft draft, long revision, NativeCalls.Callback completion) throws ReflectiveOperationException {
        Object owner = NativeBindings.service("com.reddit.link.impl.data.repository.l");
        Method method = owner.getClass().getMethod("v", String.class, calls.continuationType());
        calls.invoke(owner, method, new Object[]{draft.target.postId}, (value, error) -> {
            if (error != null) failValidation(completion, error);
            else {
                try {
                    Object post = NativeCalls.success(value);
                    String postId = NativeCalls.text(post, "getKindWithId");
                    if (!postId.equals(draft.target.postId)) {
                        block(draft, completion);
                        return;
                    }
                    submit(draft, revision, allows(post), completion);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    failValidation(completion, failure);
                }
            }
        });
    }

    private void refreshComment(ReplyDraft draft, long revision, NativeCalls.Callback completion) throws ReflectiveOperationException {
        Object owner = NativeBindings.service(REPOSITORY);
        Method method = owner.getClass().getMethod("l", String.class, Integer.class, calls.type(SORT),
                Integer.class, boolean.class, Boolean.class, boolean.class,
                calls.type("com.reddit.comment.domain.usecase.q"));
        Object flow = method.invoke(owner, draft.target.parentId, 100, calls.constant(SORT, "CONFIDENCE"),
                0, false, null, false, null);
        AtomicBoolean fresh = new AtomicBoolean();
        calls.collect(flow, (value, error) -> {
            if (fresh.get()) return;
            try {
                Object result = NativeCalls.success(value);
                if (Boolean.TRUE.equals(NativeCalls.read(result, "isCache"))) return;
                fresh.set(true);
                Object parent = find((List<?>) NativeCalls.read(result, "getComments"), draft.target.parentId);
                boolean matches = parent != null
                        && draft.target.postId.equals(NativeCalls.text(parent, "getLinkKindWithId"));
                if (!matches) {
                    block(draft, completion);
                    return;
                }
                submit(draft, revision, !Boolean.TRUE.equals(NativeCalls.read(result, "isPostLocked"))
                        && allows(parent) && !Boolean.TRUE.equals(NativeCalls.read(parent, "isDeletedByRedditor")),
                        completion);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                fresh.set(true);
                failValidation(completion, failure);
            }
        }, (value, error) -> {
            if (fresh.compareAndSet(false, true)) {
                failValidation(completion, error != null ? error : new IllegalStateException("Parent could not be refreshed"));
            }
        });
    }

    private void submit(ReplyDraft draft, long revision, boolean allows, NativeCalls.Callback completion) throws ReflectiveOperationException {
        if (!draft.isApprovedPreview(revision)) {
            failValidation(completion, new IllegalStateException("Reply review changed; select Send again"));
            return;
        }
        Object owner = NativeBindings.service(REPOSITORY);
        Method method = owner.getClass().getMethod("f",
                calls.type("com.reddit.domain.model.comment.CreateCommentParentType"),
                String.class, String.class, calls.type(SORT), calls.type("sg3.x71"), calls.continuationImplType());
        Object kind = calls.constant("com.reddit.domain.model.comment.CreateCommentParentType",
                draft.target.kind == ReplyTarget.Kind.POST ? "LINK" : "COMMENT");
        Object sort = calls.constant(SORT, "CONFIDENCE");
        String account = NativeBindings.account();
        long token = draft.beginSend(draft.target, account, allows);
        validating = false;
        if (token == 0) {
            completion.complete(null, new IllegalStateException(draft.message()));
            return;
        }
        sending.run();
        calls.invoke(owner, method, new Object[]{kind, draft.target.parentId, draft.text(), sort, null},
                (value, error) -> {
                    try {
                        if (error != null) throw new IllegalStateException("Native send outcome is unknown", error);
                        Object comment = NativeCalls.success(value);
                        if (!draft.complete(token, NativeCalls.text(comment, "getKindWithId"),
                                NativeCalls.text(comment, "getParentKindWithId"),
                                NativeCalls.text(comment, "getLinkKindWithId"))) {
                            throw new IllegalStateException("Native reply destination did not match");
                        }
                        completion.complete(comment, null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        draft.fail(token, "Sending outcome is unknown.", false);
                        completion.complete(null, failure);
                    }
                });
    }

    public static Object find(List<?> comments, String id) throws ReflectiveOperationException {
        for (Object comment : comments) {
            if (!comment.getClass().getName().equals("com.reddit.domain.model.Comment")) continue;
            if (id.equals(NativeCalls.text(comment, "getKindWithId"))) return comment;
            Object replies = NativeCalls.read(comment, "getInternalCommentReplies");
            if (replies instanceof List<?>) {
                Object nested = find((List<?>) replies, id);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    public static boolean allows(Object parent) throws ReflectiveOperationException {
        return !Boolean.TRUE.equals(NativeCalls.read(parent, "getLocked"))
                && !Boolean.TRUE.equals(NativeCalls.read(parent, "getArchived"))
                && NativeCalls.text(parent, "getUnrepliableReason").isEmpty();
    }

    private void block(ReplyDraft draft, NativeCalls.Callback completion) {
        draft.beginSend(null, "", false);
        validating = false;
        completion.complete(null, new IllegalStateException(draft.message()));
    }

    private void failValidation(NativeCalls.Callback completion, Throwable error) {
        validating = false;
        completion.complete(null, error);
    }
}
