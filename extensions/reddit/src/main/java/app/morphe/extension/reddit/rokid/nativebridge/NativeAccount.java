/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only account routes use the official app's account graph and typed queries. */
public final class NativeAccount {
    public enum PostScope { PROFILE, SAVED }
    public enum CommentScope { PROFILE, SAVED }

    public static final class AccountEntry {
        public final String accountId;
        public final String id;
        public final String title;
        public final String body;
        public final String subreddit;
        public final String postId;
        public final String commentId;
        public final boolean unread;
        public final Object nativeComment;

        private AccountEntry(String accountId, String id, String title, String body, String subreddit,
                             String postId, String commentId, boolean unread, Object nativeComment) {
            this.accountId = accountId;
            this.id = id;
            this.title = title;
            this.body = body;
            this.subreddit = subreddit;
            this.postId = postId;
            this.commentId = commentId;
            this.unread = unread;
            this.nativeComment = nativeComment;
        }
    }

    public static final class EntryPage {
        public final List<AccountEntry> entries;
        public final String next;

        private EntryPage(List<AccountEntry> entries, String next) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.next = next;
        }
    }

    public static final class Preference {
        public final String label;
        public final String value;

        private Preference(String label, String value) { this.label = label; this.value = value; }
    }

    private final NativeCalls calls;
    private final NativeFeed feeds;

    public NativeAccount(NativeCalls calls) {
        this.calls = calls;
        feeds = new NativeFeed(calls);
    }

    public void posts(PostScope scope, String cursor, int feedSize, NativeCalls.Callback completion) {
        try {
            Snapshot snapshot = snapshot();
            Object query = scope == PostScope.PROFILE ? profilePostsQuery(snapshot.account, cursor)
                    : savedPostsQuery(cursor);
            query(query, snapshot, (value, error) -> {
                if (error != null) completion.complete(null, error);
                else {
                    try {
                        Object listing = scope == PostScope.PROFILE
                                ? path(value, "a", "b", "a") : path(value, "a", "a");
                        if (listing == null) {
                            completion.complete(new NativeFeed.Page(Collections.emptyList(), null), null);
                            return;
                        }
                        Object info = field(listing, "b");
                        String next = scope == PostScope.PROFILE ? pageCursor(field(info, "b"))
                                : stringField(info, "a");
                        List<?> edges = list(field(listing, "c"));
                        Object mapper = provider(snapshot.graph, "f9", "zl1.e");
                        Class<?> contextType = calls.type("gk1.c");
                        Object context = contextType.getConstructor(calls.type("com.reddit.feeds.caching.data.DataSourceType"),
                                Instant.class, calls.type("com.reddit.feeds.data.FeedType"))
                                .newInstance(null, null, calls.constant("com.reddit.feeds.data.FeedType",
                                        scope == PostScope.PROFILE ? "SUBMITTED_POSTS" : "SAVED_POSTS"));
                        Method map = calls.type("zl1.e").getMethod("a", calls.type("ep1.a50"), contextType);
                        List<Object> units = new ArrayList<>();
                        for (Object edge : edges) {
                            Object fragment = field(edge, "b");
                            if (fragment != null) {
                                Object unit = map.invoke(mapper, fragment, context);
                                if (unit != null) units.add(unit);
                            }
                        }
                        Object page = calls.type("lk1.b").getConstructor(List.class, String.class,
                                calls.type("sw1.b"), Integer.class, Integer.class)
                                .newInstance(units, next, null, null, null);
                        snapshot.validate();
                        feeds.resolvePage(page, snapshot.account, (posts, failure) -> {
                            try {
                                snapshot.validate();
                                completion.complete(posts, failure);
                            } catch (ReflectiveOperationException | RuntimeException changed) {
                                completion.complete(null, changed);
                            }
                        });
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        completion.complete(null, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            completion.complete(null, failure);
        }
    }

    public void comments(CommentScope scope, String cursor, NativeCalls.Callback completion) {
        if (scope == CommentScope.SAVED) {
            savedComments(cursor, completion);
            return;
        }
        try {
            Snapshot snapshot = snapshot();
            Class<?> input = calls.type("j9.x0");
            Object query = calls.type("wz2.xv1").getConstructor(String.class, input, input,
                    calls.type("j9.y0"), input, input, input).newInstance(snapshot.account,
                    present(calls.constant("com.reddit.type.ProfileFeedSort", "NEW")),
                    present(calls.constant("com.reddit.type.CommentRange", "ALL")), optional(cursor),
                    present(false), present(false), present(false));
            query(query, snapshot, (value, error) -> {
                if (error != null) completion.complete(null, error);
                else {
                    try {
                        Object listing = path(value, "a", "b", "a");
                        List<AccountEntry> entries = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        String next = listing == null ? null : pageCursor(path(listing, "a", "b"));
                        if (listing != null) {
                            for (Object edge : list(field(listing, "b"))) {
                                Object node = field(edge, "a");
                                Object comment = field(node, "c");
                                Object post = field(node, "b");
                                if (comment == null || post == null) continue;
                                String id = nativeId(stringField(comment, "a"), "t1");
                                String postId = nativeId(stringField(post, "b"), "t3");
                                if (id.isEmpty() || postId.isEmpty() || !unique.add(id)) continue;
                                String subreddit = stringField(path(post, "d", "a"), "a");
                                entries.add(new AccountEntry(snapshot.account, id, stringField(post, "c"),
                                        stringField(field(comment, "d"), "a"), subreddit, postId, id, false, null));
                            }
                        }
                        snapshot.validate();
                        completion.complete(new EntryPage(entries, next), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        completion.complete(null, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            completion.complete(null, failure);
        }
    }

    private void savedComments(String cursor, NativeCalls.Callback completion) {
        try {
            Snapshot snapshot = snapshot();
            Object owner = NativeBindings.service("com.reddit.comment.data.datasource.c");
            Method method = calls.type("com.reddit.comment.data.datasource.c")
                    .getMethod("p", String.class, calls.continuationImplType());
            calls.invoke(owner, method, new Object[]{cursor}, (result, error) -> {
                if (error != null) completion.complete(null, error);
                else {
                    try {
                        snapshot.validate();
                        Object listing = NativeCalls.success(result);
                        List<AccountEntry> entries = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        for (Object comment : list(NativeCalls.read(listing, "getChildren"))) {
                            if (!calls.type("com.reddit.domain.model.Comment").isInstance(comment)) continue;
                            String id = nativeId(NativeCalls.text(comment, "getKindWithId"), "t1");
                            String postId = nativeId(NativeCalls.text(comment, "getLinkKindWithId"), "t3");
                            if (id.isEmpty() || postId.isEmpty() || !unique.add(id)) continue;
                            entries.add(new AccountEntry(snapshot.account, id, NativeCalls.text(comment, "getLinkTitle"),
                                    NativeCalls.text(comment, "getBody"), NativeCalls.text(comment, "getSubredditNamePrefixed"),
                                    postId, id, false, comment));
                        }
                        completion.complete(new EntryPage(entries,
                                nullableText(NativeCalls.read(listing, "getAfter"))), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        completion.complete(null, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            completion.complete(null, failure);
        }
    }

    public void inbox(String cursor, NativeCalls.Callback completion) {
        try {
            Snapshot snapshot = snapshot();
            Object owner = NativeBindings.service("com.reddit.notification.impl.data.remote.g");
            Class<?> reasonType = calls.type("com.reddit.notification.analytics.InboxFetchReason");
            Method method = calls.type("com.reddit.notification.impl.data.remote.g").getMethod("c",
                    int.class, reasonType, String.class, calls.continuationImplType());
            Object reason = calls.constant(reasonType.getName(), cursor == null ? "INITIAL_LOAD" : "FETCH_NEXT_PAGE");
            calls.invoke(owner, method, new Object[]{25, reason, cursor}, (page, error) -> {
                if (error != null) completion.complete(null, error);
                else {
                    try {
                        snapshot.validate();
                        if (!calls.type("hk2.r").isInstance(page)) throw new IllegalStateException("Inbox unavailable");
                        List<AccountEntry> entries = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        for (Object notification : list(field(page, "a"))) {
                            if (!calls.type("hk2.q").isInstance(notification)) continue;
                            String id = stringField(notification, "a");
                            if (id.isEmpty() || !unique.add(id)) continue;
                            Object nativeComment = field(notification, "A");
                            String postId = nativeId(stringField(field(notification, "i"), "a"), "t3");
                            String commentId = knownId(stringField(notification, "p"), "t1");
                            if (nativeComment != null && calls.type("com.reddit.domain.model.Comment").isInstance(nativeComment)) {
                                String parent = nativeId(NativeCalls.text(nativeComment, "getLinkKindWithId"), "t3");
                                if (postId.isEmpty() || postId.equals(parent)) {
                                    postId = parent;
                                    commentId = nativeId(NativeCalls.text(nativeComment, "getKindWithId"), "t1");
                                } else {
                                    nativeComment = null;
                                    commentId = "";
                                }
                            }
                            boolean unread = field(notification, "f") == null
                                    && !Boolean.TRUE.equals(field(notification, "u"));
                            entries.add(new AccountEntry(snapshot.account, id, stringField(notification, "b"),
                                    stringField(notification, "c"), stringField(notification, "s"),
                                    postId, commentId, unread, nativeComment));
                        }
                        String next = Boolean.TRUE.equals(field(page, "b")) ? nullableText(field(page, "c")) : null;
                        completion.complete(new EntryPage(entries, next), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        completion.complete(null, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            completion.complete(null, failure);
        }
    }

    public void preferences(NativeCalls.Callback completion) {
        try {
            Snapshot snapshot = snapshot();
            Object owner = NativeBindings.service("com.reddit.data.remote.m");
            Method method = calls.type("com.reddit.data.remote.m").getMethod("b", calls.continuationImplType());
            calls.invoke(owner, method, new Object[]{}, (preferences, error) -> {
                if (error != null) completion.complete(null, error);
                else {
                    try {
                        snapshot.validate();
                        if (!calls.type("com.reddit.domain.model.AccountPreferences").isInstance(preferences)) {
                            throw new IllegalStateException("Account preferences unavailable");
                        }
                        List<Preference> rows = new ArrayList<>();
                        rows.add(toggle(preferences, "Feed recommendations", "getFeedRecommendationsEnabled"));
                        rows.add(toggle(preferences, "Allow followers", "getEnableFollowers"));
                        rows.add(toggle(preferences, "Online status", "getShowPresence"));
                        rows.add(toggle(preferences, "Mature content", "getOver18"));
                        rows.add(toggle(preferences, "Mature search results", "getSearchIncludeOver18"));
                        String sort = NativeCalls.text(preferences, "getDefaultCommentSort");
                        rows.add(new Preference("Default comment sort", sort.isEmpty() ? "Default" : sort));
                        rows.add(toggle(preferences, "Ignore suggested comment sort", "getIgnoreSuggestedSort"));
                        completion.complete(Collections.unmodifiableList(rows), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        completion.complete(null, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            completion.complete(null, failure);
        }
    }

    public void readComment(AccountEntry entry, NativeCalls.Callback completion) {
        AtomicBoolean emitted = new AtomicBoolean();
        try {
            Snapshot snapshot = snapshot();
            if (!snapshot.account.equals(entry.accountId) || entry.commentId.isEmpty() || entry.postId.isEmpty()) {
                throw new IllegalStateException("Comment target unavailable");
            }
            Object owner = NativeBindings.service("com.reddit.comment.data.repository.b");
            Class<?> sort = calls.type("com.reddit.listing.model.sort.CommentSortType");
            Method method = owner.getClass().getMethod("l", String.class, Integer.class, sort,
                    Integer.class, boolean.class, Boolean.class, boolean.class,
                    calls.type("com.reddit.comment.domain.usecase.q"));
            Object flow = method.invoke(owner, entry.commentId, 100, calls.constant(sort.getName(), "CONFIDENCE"),
                    0, false, null, false, null);
            calls.collect(flow, (value, error) -> {
                if (emitted.get()) return;
                try {
                    snapshot.validate();
                    Object result = NativeCalls.success(value);
                    if (Boolean.TRUE.equals(NativeCalls.read(result, "isCache"))) return;
                    List<Object> comments = new ArrayList<>();
                    NativeComments.flatten(list(NativeCalls.read(result, "getComments")), comments);
                    Object found = null;
                    for (Object comment : comments) {
                        if (entry.commentId.equals(NativeCalls.text(comment, "getKindWithId"))
                                && entry.postId.equals(NativeCalls.text(comment, "getLinkKindWithId"))) {
                            found = comment;
                            break;
                        }
                    }
                    if (found == null) throw new IllegalStateException("Comment target changed");
                    if (emitted.compareAndSet(false, true)) completion.complete(found, null);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    if (emitted.compareAndSet(false, true)) completion.complete(null, failure);
                }
            }, (value, error) -> {
                if (emitted.compareAndSet(false, true)) completion.complete(null,
                        error == null ? new IllegalStateException("Comment unavailable") : error);
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (emitted.compareAndSet(false, true)) completion.complete(null, failure);
        }
    }

    private Object profilePostsQuery(String account, String cursor) throws ReflectiveOperationException {
        Class<?> optional = calls.type("j9.y0");
        Class<?> input = calls.type("j9.x0");
        return calls.type("wz2.ew1").getConstructor(String.class, optional, optional, optional, optional,
                input, input, input, input, input, input).newInstance(account,
                present(calls.constant("com.reddit.type.PostFeedSort", "NEW")), absent(), optional(cursor), absent(),
                present(true), present(true), present(false), present(false), present(false), present(false));
    }

    private Object savedPostsQuery(String cursor) throws ReflectiveOperationException {
        Class<?> input = calls.type("j9.x0");
        Class<?> optional = calls.type("j9.y0");
        Object context = calls.type("sg3.wp").getConstructor(calls.type("j9.y0"), calls.type("j9.y0"),
                calls.type("j9.y0"), calls.type("j9.y0"), calls.type("j9.y0"), calls.type("j9.y0"),
                calls.type("j9.y0"), calls.type("j9.y0"), calls.type("j9.x0"), int.class)
                .newInstance(null, absent(), absent(), absent(), null, absent(), null, null, null, 32055);
        return calls.type("wz2.fq1").getConstructor(input, optional, input, input, input, input,
                input, input, input, input, input, input).newInstance(present(context), optional(cursor),
                present(false), present(false), present(true), present(false), present(false), present(true),
                present(false), present(false), present(false), present(false));
    }

    private void query(Object query, Snapshot snapshot, NativeCalls.Callback completion)
            throws ReflectiveOperationException {
        Object owner = NativeBindings.service("com.reddit.graphql.d0");
        Method method = calls.type("com.reddit.graphql.d0").getMethod("execute", calls.type("j9.t0"),
                java.util.Map.class, calls.type("okhttp3.OkHttpClient"), calls.type("com.reddit.network.common.RetryAlgo"),
                Set.class, calls.type("com.reddit.graphql.FetchPolicy"), calls.type("com.reddit.graphql.x0"),
                calls.type("com.reddit.network.n"), calls.type("com.reddit.network.orchestrator.RequestSchedulerPriority"),
                calls.type("com.reddit.network.orchestrator.DeferPolicy"), calls.continuationType());
        Object network = calls.type("com.reddit.network.n").getConstructor().newInstance();
        snapshot.validate();
        calls.invoke(owner, method, new Object[]{query, null, null, calls.constant("com.reddit.network.common.RetryAlgo", "FULL_JITTER"), null,
                calls.constant("com.reddit.graphql.FetchPolicy", "NetworkOnly"), null, network,
                calls.constant("com.reddit.network.orchestrator.RequestSchedulerPriority", "NORMAL"),
                calls.constant("com.reddit.network.orchestrator.DeferPolicy", "NONE")}, (result, error) -> {
            if (error != null) completion.complete(null, error);
            else {
                try {
                    snapshot.validate();
                    completion.complete(NativeCalls.success(result), null);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    completion.complete(null, failure);
                }
            }
        });
    }

    private final class Snapshot {
        private final String account;
        private final Object graph;

        private Snapshot(String account, Object graph) { this.account = account; this.graph = graph; }

        private void validate() throws ReflectiveOperationException {
            if (!account.equals(NativeBindings.account()) || graph != NativeBindings.accountGraph()) {
                throw new IllegalStateException("Account changed");
            }
        }
    }

    private Snapshot snapshot() throws ReflectiveOperationException {
        String account = NativeBindings.account();
        if (account.isEmpty()) throw new IllegalStateException("Sign in to Reddit");
        Snapshot snapshot = new Snapshot(account, NativeBindings.accountGraph());
        snapshot.validate();
        return snapshot;
    }

    private Object provider(Object graph, String field, String type) throws ReflectiveOperationException {
        Object value = NativeCalls.read(field(graph, field), "get");
        if (!calls.type(type).isInstance(value)) throw new IllegalStateException("Native service type changed");
        return value;
    }

    private Object present(Object value) throws ReflectiveOperationException {
        return calls.type("j9.x0").getConstructor(Object.class).newInstance(value);
    }

    private Object absent() throws ReflectiveOperationException { return calls.constant("j9.v0", "b"); }
    private Object optional(String value) throws ReflectiveOperationException { return value == null ? absent() : present(value); }

    private String nativeId(String value, String kind) throws ReflectiveOperationException {
        if (value.isEmpty()) return "";
        String normalized = (String) calls.type("com.reddit.common.identity.b")
                .getMethod(kind.equals("t1") ? "d" : "e", String.class).invoke(null, value);
        return knownId(normalized, kind);
    }

    private static String knownId(String value, String kind) {
        if (value.matches("[a-z0-9]+")) value = kind + "_" + value;
        return value.matches(kind + "_[a-z0-9]+") ? value : "";
    }

    private static Object field(Object owner, String field) throws ReflectiveOperationException {
        return owner == null ? null : owner.getClass().getField(field).get(owner);
    }

    private static Object path(Object owner, String... fields) throws ReflectiveOperationException {
        for (String field : fields) owner = field(owner, field);
        return owner;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?>)) throw new IllegalStateException("Native list unavailable");
        return (List<?>) value;
    }

    private static String stringField(Object owner, String field) throws ReflectiveOperationException {
        Object value = field(owner, field);
        if (value == null) return "";
        if (!(value instanceof String)) throw new IllegalStateException("Native text type changed");
        return (String) value;
    }

    private static String nullableText(Object value) {
        if (value == null) return null;
        if (!(value instanceof String)) throw new IllegalStateException("Native cursor type changed");
        String cursor = (String) value;
        return cursor.isEmpty() ? null : cursor;
    }

    private static String pageCursor(Object pageInfo) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(field(pageInfo, "a")) ? nullableText(field(pageInfo, "b")) : null;
    }

    private static Preference toggle(Object preferences, String label, String getter) throws ReflectiveOperationException {
        Object value = NativeCalls.read(preferences, getter);
        if (!(value instanceof Boolean)) throw new IllegalStateException("Native preference type changed");
        return new Preference(label, Boolean.TRUE.equals(value) ? "On" : "Off");
    }
}