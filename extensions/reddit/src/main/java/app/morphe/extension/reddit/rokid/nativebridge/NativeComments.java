/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeComments {
    private final NativeCalls calls;
    public NativeComments(NativeCalls calls) { this.calls = calls; }

    public void load(String postId, String cursor, String sort, NativeCalls.Callback completion) {
        AtomicBoolean emitted = new AtomicBoolean();
        try {
            Object owner = NativeBindings.service("com.reddit.comment.data.repository.b");
            Class<?> sortType = calls.type("com.reddit.listing.model.sort.CommentSortType");
            Object loadType = calls.constant("com.reddit.comment.domain.usecase."
                    + (cursor == null ? "l" : "k"), "a");
            Method method = owner.getClass().getMethod("k", String.class, String.class, Integer.class,
                    sortType, boolean.class, String.class, boolean.class, boolean.class, int.class,
                    calls.type("com.reddit.type.CommentTreeFilter"), boolean.class,
                    calls.type("com.reddit.comment.domain.usecase.p"), String.class, Boolean.class,
                    boolean.class, calls.type("com.reddit.comment.domain.usecase.q"));
            Object flow = method.invoke(owner, postId, cursor, 100, calls.constant(sortType.getName(), sort),
                    false, "", false, false, 0, null, false, loadType, null, null, false, null);
            String account = NativeBindings.account();
            calls.collect(flow, (value, error) -> {
                try {
                    Object result = NativeCalls.success(value);
                    if (Boolean.TRUE.equals(NativeCalls.read(result, "isCache"))) return;
                    if (!account.equals(NativeBindings.account())) throw new IllegalStateException("Account changed");
                    if (emitted.compareAndSet(false, true)) completion.complete(result, null);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    if (emitted.compareAndSet(false, true)) completion.complete(null, failure);
                }
            }, (value, error) -> {
                if (emitted.compareAndSet(false, true)) {
                    completion.complete(null, error != null ? error : new IllegalStateException("Comments unavailable"));
                }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (emitted.compareAndSet(false, true)) completion.complete(null, error);
        }
    }

    public static void flatten(List<?> tree, List<Object> rows) throws ReflectiveOperationException {
        for (Object comment : tree) {
            if (!comment.getClass().getName().equals("com.reddit.domain.model.Comment")) continue;
            rows.add(comment);
            Object replies = NativeCalls.read(comment, "getInternalCommentReplies");
            if (replies instanceof List<?>) flatten((List<?>) replies, rows);
        }
    }
}
