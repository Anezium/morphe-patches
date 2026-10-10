/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import java.lang.reflect.Method;

public final class NativeActions {
    private final NativeCalls calls;
    private boolean busy;
    public NativeActions(NativeCalls calls) { this.calls = calls; }

    public void vote(Object item, boolean comment, String direction, NativeCalls.Callback completion) {
        try {
            Object owner;
            Method method;
            String id = NativeCalls.text(item, "getKindWithId");
            Class<?> voteType = calls.type("com.reddit.domain.model.vote.VoteDirection");
            if (comment) {
                Object repository = NativeBindings.service("com.reddit.comment.data.repository.b");
                owner = repository.getClass().getField("a").get(repository);
                method = owner.getClass().getMethod("B", String.class, voteType, calls.continuationImplType());
            } else {
                owner = NativeBindings.service("com.reddit.link.impl.data.repository.l");
                method = owner.getClass().getMethod("j0", String.class, voteType,
                        calls.type("kotlin.coroutines.jvm.internal.SuspendLambda"));
            }
            mutate(owner, method, new Object[]{id, calls.constant(voteType.getName(), direction)}, completion);
        } catch (ReflectiveOperationException | RuntimeException error) {
            completion.complete(null, error);
        }
    }

    public void savePost(Object post, boolean saved, NativeCalls.Callback completion) {
        try {
            Object owner = NativeBindings.service("com.reddit.link.impl.data.repository.l");
            Method method = owner.getClass().getMethod(saved ? "U" : "c0", String.class, calls.continuationImplType());
            mutate(owner, method, new Object[]{NativeCalls.text(post, "getKindWithId")}, completion);
        } catch (ReflectiveOperationException | RuntimeException error) {
            completion.complete(null, error);
        }
    }

    public void saveComment(Object comment, boolean saved, NativeCalls.Callback completion) {
        try {
            Object repository = NativeBindings.service("com.reddit.comment.data.repository.b");
            Object owner = repository.getClass().getField("a").get(repository);
            Method method = owner.getClass().getMethod(saved ? "u" : "y", String.class,
                    saved ? calls.continuationType() : calls.continuationImplType());
            mutate(owner, method, new Object[]{NativeCalls.text(comment, "getKindWithId")}, completion);
        } catch (ReflectiveOperationException | RuntimeException error) {
            completion.complete(null, error);
        }
    }

    private void mutate(Object owner, Method method, Object[] arguments, NativeCalls.Callback completion)
            throws ReflectiveOperationException {
        if (busy) return;
        if (NativeBindings.account().isEmpty()) throw new IllegalStateException("Sign in first");
        busy = true;
        calls.invoke(owner, method, arguments, (value, error) -> {
            busy = false;
            if (error != null) completion.complete(null, error);
            else {
                try {
                    if (value != null && value.getClass().getName().equals("com.reddit.domain.model.UpdateResponse")) {
                        if (!Boolean.TRUE.equals(NativeCalls.read(value, "getSuccess"))) throw new IllegalStateException("Native action rejected");
                        completion.complete(value, null);
                    } else completion.complete(NativeCalls.success(value), null);
                }
                catch (ReflectiveOperationException | RuntimeException failure) { completion.complete(null, failure); }
            }
        });
    }
}
