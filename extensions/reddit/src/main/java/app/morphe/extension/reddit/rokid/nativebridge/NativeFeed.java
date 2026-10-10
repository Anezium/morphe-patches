/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import app.morphe.extension.reddit.rokid.core.DiscoveryPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NativeFeed {
    public enum Scope {
        HOME("com.reddit.feeds.home.impl.data.paging.e", "Home"),
        POPULAR("com.reddit.feeds.popular.impl.data.paging.a", "Popular"),
        LATEST("com.reddit.feeds.latest.impl.data.a", "Latest");
        public final String service;
        public final String title;
        Scope(String service, String title) { this.service = service; this.title = title; }
    }

    public static final class Page {
        public final List<Object> posts;
        public final String next;
        Page(List<Object> posts, String next) { this.posts = posts; this.next = next; }
    }

    private final NativeCalls calls;
    public NativeFeed(NativeCalls calls) { this.calls = calls; }

    public void load(Scope scope, String cursor, int feedSize, NativeCalls.Callback completion) {
        try {
            Snapshot snapshot = new Snapshot();
            Object owner = NativeBindings.service(scope.service);
            snapshot.check();
            Class<?> paramsType = calls.type("com.reddit.feeds.data.paging.f");
            Object params = paramsType.getConstructor(String.class, boolean.class, Integer.class, int.class)
                    .newInstance(cursor, cursor == null, null, feedSize);
            Method load = calls.type("com.reddit.feeds.data.paging.g")
                    .getMethod("i", paramsType, calls.continuationType());
            snapshot.check();
            calls.invoke(owner, load, new Object[]{params}, (result, error) -> {
                if (error != null) fail(completion, error);
                else {
                    try {
                        snapshot.check();
                        if (failedResult(result, completion)) return;
                        Object page = NativeCalls.success(result);
                        resolvePage(page, snapshot, completion);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        fail(completion, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    public void post(String id, NativeCalls.Callback callback) {
        try {
            if (id == null || !id.matches("t3_[a-z0-9]+")) throw new IllegalArgumentException("Invalid post");
            resolve(java.util.Collections.singletonList(id), null, new Snapshot(), callback);
        } catch (ReflectiveOperationException | RuntimeException error) { fail(callback, error); }
    }

    void resolvePage(Object page, String account, NativeCalls.Callback completion) {
        try {
            resolvePage(page, snapshot(account), completion);
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(completion, failure); }
    }

    private void resolvePage(Object page, Snapshot snapshot, NativeCalls.Callback completion) {
        try {
            snapshot.check();
            List<?> units = (List<?>) page.getClass().getField("a").get(page);
            String next = (String) page.getClass().getField("b").get(page);
            List<String> ids = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            Class<?> unitType = calls.type("ym1.g0");
            Method promoted = unitType.getMethod("b");
            Method linkId = unitType.getMethod("getLinkId");
            for (Object unit : units) {
                // Resolving a link by id can discard the feed unit's promoted context.
                if (!unitType.isInstance(unit) || !Boolean.FALSE.equals(promoted.invoke(unit))) continue;
                String id = (String) linkId.invoke(unit);
                if (id == null) continue;
                if (id.matches("[a-z0-9]+")) id = "t3_" + id;
                if (id.matches("t3_[a-z0-9]+") && unique.add(id)) ids.add(id);
            }
            if (ids.isEmpty()) {
                snapshot.check();
                completion.complete(new Page(new ArrayList<>(), next), null);
                return;
            }
            resolve(ids, next, snapshot, completion);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    private static Snapshot snapshot(String account) throws ReflectiveOperationException {
        Snapshot snapshot = new Snapshot();
        if (!snapshot.account.equals(account)) throw new IllegalStateException("Native account changed");
        return snapshot;
    }

    private static final class Snapshot {
        final Object graph;
        final Object session;
        final String account;

        Snapshot() throws ReflectiveOperationException {
            graph = NativeBindings.accountGraph();
            session = NativeCalls.read(NativeBindings.service("com.reddit.session.o"), "t");
            account = NativeBindings.account();
            check();
        }

        void check() throws ReflectiveOperationException {
            Object current = NativeCalls.read(NativeBindings.service("com.reddit.session.o"), "t");
            if (!DiscoveryPolicy.sameFeedOwner(graph, session, account, NativeBindings.accountGraph(),
                    current, NativeBindings.account())) throw new IllegalStateException("Native feed owner changed");
        }
    }

    private static boolean failedResult(Object result, NativeCalls.Callback completion)
            throws ReflectiveOperationException {
        if (result == null || !result.getClass().getName().equals("hx.b")) return false;
        Object cause = NativeCalls.read(result, "a");
        if (!(cause instanceof Throwable)) return false;
        fail(completion, (Throwable) cause);
        return true;
    }

    private static void fail(NativeCalls.Callback completion, Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null
                ? failure.getCause() : failure;
        // Native exception messages and stack traces can contain account data or request URLs.
        NativeCalls.logFailure("RedditGlassesFeed", cause);
        completion.complete(null, cause);
    }

    void resolve(List<String> ids, String next, String account, NativeCalls.Callback completion)
            throws ReflectiveOperationException {
        resolve(ids, next, snapshot(account), completion);
    }

    private void resolve(List<String> ids, String next, Snapshot snapshot, NativeCalls.Callback completion)
            throws ReflectiveOperationException {
        snapshot.check();
        Object repository = NativeBindings.service("com.reddit.link.impl.data.repository.l");
        snapshot.check();
        Method fetch = repository.getClass().getMethod("G", List.class, calls.continuationImplType());
        calls.invoke(repository, fetch, new Object[]{ids}, (value, error) -> {
            if (error != null) fail(completion, error);
            else {
                try {
                    snapshot.check();
                    if (failedResult(value, completion)) return;
                    Object listing = NativeCalls.success(value);
                    List<?> links = (List<?>) NativeCalls.read(listing, "getChildren");
                    List<Object> posts = new ArrayList<>();
                    for (String id : ids) {
                        for (Object link : links) {
                            if (id.equals(NativeCalls.text(link, "getKindWithId"))) {
                                if (!Boolean.TRUE.equals(NativeCalls.read(link, "getPromoted"))) posts.add(link);
                                break;
                            }
                        }
                    }
                    snapshot.check();
                    completion.complete(new Page(posts, next), null);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    fail(completion, failure);
                }
            }
        });
    }
}
