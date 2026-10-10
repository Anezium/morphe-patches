/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import app.morphe.extension.reddit.rokid.core.DiscoveryPolicy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only discovery through the inspected Reddit 2026.14.0 native services. */
public final class NativeDiscovery {
    public static final String SEARCH_SERVICE = "com.reddit.search.remote.c";
    public static final String COMMUNITY_SERVICE = "com.reddit.data.remote.p";
    public static final String COMMUNITY_FEED_SERVICE = "com.reddit.screens.listing.compose.usecase.a";
    private static final int PAGE_SIZE = 25;

    public enum SearchSort { RELEVANCE, TOP, NEW }

    public static final class Community {
        public final String id;
        public final String name;
        public final String title;
        public final boolean favorite;
        public final boolean nsfw;
        public final boolean moderator;
        public final boolean subscribed;
        public final String description;
        public final Long subscribers;

        private Community(String id, String name, String title, boolean favorite, boolean nsfw, boolean moderator,
                boolean subscribed, String description, Long subscribers) {
            this.id = id;
            this.name = name;
            this.title = title;
            this.favorite = favorite;
            this.nsfw = nsfw;
            this.moderator = moderator;
            this.subscribed = subscribed;
            this.description = description;
            this.subscribers = subscribers;
        }
    }

    public static final class CommunityPage {
        public final List<Community> communities;
        public final String next;

        private CommunityPage(List<Community> communities, String next) {
            this.communities = Collections.unmodifiableList(new ArrayList<>(communities));
            this.next = next;
        }
    }

    private final NativeCalls calls;

    public NativeDiscovery(NativeCalls calls) { this.calls = calls; }

    public void searchPosts(String input, String cursor, NativeCalls.Callback completion) {
        searchPosts(input, SearchSort.RELEVANCE, cursor, completion);
    }

    public void searchPosts(String input, SearchSort sort, String cursor, NativeCalls.Callback completion) {
        try {
            if (sort == null) throw new IllegalArgumentException("Choose a search sort");
            String query = DiscoveryPolicy.query(input);
            String after = DiscoveryPolicy.cursor(cursor);
            Account request = new Account();
            Object owner = service(SEARCH_SERVICE);
            Class<?> paramsType = calls.type("sa3.b");
            Object params = params(query, "posts", sort);
            Object tracking = tracking();
            Method search = method(calls.type(SEARCH_SERVICE), "n", Object.class, String.class,
                    calls.type("ia3.f"), paramsType, String.class, Integer.class, calls.continuationImplType());
            request.check();
            calls.invoke(owner, search, new Object[]{query, tracking, params, after, PAGE_SIZE}, (result, error) -> {
                if (error != null) fail(completion, error);
                else {
                    try {
                        request.check();
                        Object page = success(result);
                        exact(page, "sa3.i");
                        List<?> entries = list(field(page, "a", List.class));
                        String next = DiscoveryPolicy.nextCursor((String) field(page, "b", String.class), after);
                        Class<?> entryType = calls.type("com.reddit.domain.model.SearchPost");
                        Class<?> postType = calls.type("com.reddit.domain.model.Link");
                        Method link = method(entryType, "getLink", postType);
                        Method kind = method(entryType, "getType", calls.type("com.reddit.domain.model.SearchPost$Type"));
                        Method promoted = method(postType, "getPromoted", boolean.class);
                        Method id = method(postType, "getKindWithId", String.class);
                        List<Object> posts = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        for (Object entry : entries) {
                            if (!entryType.isInstance(entry)) throw new IllegalStateException("Unexpected native search entry");
                            Object post = link.invoke(entry);
                            exact(post, "com.reddit.domain.model.Link");
                            Object searchKind = kind.invoke(entry);
                            if (!(searchKind instanceof Enum)) throw new IllegalStateException("Unexpected native search type");
                            if (!DiscoveryPolicy.organicSearchPost(((Enum<?>) searchKind).name(), (Boolean) promoted.invoke(post))) continue;
                            String fullId = (String) id.invoke(post);
                            if (fullId != null && fullId.matches("t3_[a-z0-9]+") && unique.add(fullId)) posts.add(post);
                        }
                        request.check();
                        completion.complete(new NativeFeed.Page(Collections.unmodifiableList(posts), next), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        fail(completion, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    public void searchCommunities(String input, String cursor, NativeCalls.Callback completion) {
        try {
            String query = DiscoveryPolicy.query(input);
            String after = DiscoveryPolicy.cursor(cursor);
            Account request = new Account();
            Object owner = service(SEARCH_SERVICE);
            Class<?> paramsType = calls.type("sa3.b");
            Method search = method(calls.type(SEARCH_SERVICE), "b", Object.class, String.class,
                    calls.type("ia3.f"), paramsType, String.class, calls.continuationImplType());
            Object params = params(query, "communities", null);
            Object tracking = tracking();
            request.check();
            calls.invoke(owner, search, new Object[]{query, tracking, params, after}, (result, error) -> {
                if (error != null) fail(completion, error);
                else {
                    try {
                        request.check();
                        Object page = success(result);
                        exact(page, "sa3.i");
                        String next = DiscoveryPolicy.nextCursor((String) field(page, "b", String.class), after);
                        Class<?> listType = calls.type("ta3.u");
                        Class<?> rowType = calls.type("ta3.l0");
                        List<Community> communities = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        for (Object component : list(field(page, "a", List.class))) {
                            if (component == null || component.getClass() != listType) continue;
                            for (Object row : list(field(component, "b", ArrayList.class))) {
                                if (row == null || row.getClass() != rowType) continue;
                                Object nativeCommunity = field(row, "e", calls.type("sa3.f"));
                                exact(nativeCommunity, "sa3.f");
                                String fullId = (String) field(nativeCommunity, "j", String.class);
                                String name = (String) field(nativeCommunity, "k", String.class);
                                String rawId = fullId != null && fullId.startsWith("t5_") ? fullId.substring(3) : null;
                                DiscoveryPolicy.communityId(rawId, fullId, name);
                                if (!fullId.equals(field(nativeCommunity, "b", String.class))) {
                                    throw new IllegalStateException("Native search community identity is invalid");
                                }
                                if (!unique.add(fullId)) continue;
                                String title = (String) field(nativeCommunity, "d", String.class);
                                if (!("r/" + name).equals(title)) throw new IllegalStateException("Native community label is invalid");
                                communities.add(new Community(fullId, name, title, false,
                                        Boolean.TRUE.equals(field(nativeCommunity, "i", Boolean.class)), false,
                                        Boolean.TRUE.equals(field(nativeCommunity, "e", Boolean.class)),
                                        (String) field(nativeCommunity, "h", String.class),
                                        (Long) field(nativeCommunity, "f", Long.class)));
                            }
                        }
                        request.check();
                        completion.complete(new CommunityPage(communities, next), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        fail(completion, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    public void communities(String cursor, NativeCalls.Callback completion) {
        try {
            String after = DiscoveryPolicy.cursor(cursor);
            Account request = new Account();
            Object owner = service(COMMUNITY_SERVICE);
            Method fetch = method(calls.type(COMMUNITY_SERVICE), "t", Object.class,
                    boolean.class, String.class, boolean.class, String.class,
                    boolean.class, String.class, int.class, calls.continuationImplType());
            request.check();
            calls.invoke(owner, fetch, new Object[]{true, after, false, null, false, null, PAGE_SIZE}, (result, error) -> {
                if (error != null) fail(completion, error);
                else {
                    try {
                        request.check();
                        exact(result, "com.reddit.domain.model.UserSubredditPaginatedListing");
                        Object page = read(result, "getSubscribed", calls.type("com.reddit.domain.model.PaginatedListing"));
                        exact(page, "com.reddit.domain.model.PaginatedListing");
                        List<?> rows = list(read(page, "getItems", List.class));
                        Object hasNext = read(page, "getHasNextPage", boolean.class);
                        if (!(hasNext instanceof Boolean)) throw new IllegalStateException("Unexpected native community pagination");
                        String next = DiscoveryPolicy.nextCursor((String) read(page, "getEndCursor", String.class),
                                after, Boolean.TRUE.equals(hasNext));
                        List<Community> communities = new ArrayList<>();
                        Set<String> unique = new HashSet<>();
                        for (Object row : rows) {
                            exact(row, "com.reddit.domain.model.SubredditListItem");
                            if (!Boolean.FALSE.equals(read(row, "isUser", boolean.class))) continue;
                            if (!Boolean.TRUE.equals(read(row, "getUserIsSubscriber", boolean.class))) continue;
                            String name = (String) read(row, "getDisplayName", String.class);
                            String fullId = DiscoveryPolicy.communityId((String) read(row, "getId", String.class),
                                    (String) read(row, "getKindWithId", String.class), name);
                            if (!unique.add(fullId)) continue;
                            String title = (String) read(row, "getDisplayNamePrefixed", String.class);
                            if (!title.equals("r/" + name)) throw new IllegalStateException("Native community label is invalid");
                            communities.add(new Community(fullId, name, title,
                                    Boolean.TRUE.equals(read(row, "getUserHasFavorited", boolean.class)),
                                    Boolean.TRUE.equals(read(row, "getOver18", boolean.class)),
                                    Boolean.TRUE.equals(read(row, "getUserIsModerator", boolean.class)), true, "", null));
                        }
                        request.check();
                        completion.complete(new CommunityPage(communities, next), null);
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        fail(completion, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    public void communityFeed(Community community, String cursor, NativeCalls.Callback completion) {
        try {
            if (community == null) throw new IllegalArgumentException("Choose a community");
            DiscoveryPolicy.communityId(community.id.substring(3), community.id, community.name);
            String after = DiscoveryPolicy.cursor(cursor);
            Account request = new Account();
            Object owner = service(COMMUNITY_FEED_SERVICE);
            Class<?> sortType = calls.type("sw1.b");
            Object sort = sortType.getConstructor(calls.type("com.reddit.listing.model.sort.SortType"),
                    calls.type("com.reddit.listing.model.sort.SortTimeFrame"))
                    .newInstance(calls.constant("com.reddit.listing.model.sort.SortType", "HOT"), null);
            Class<?> requestType = calls.type("com.reddit.screens.listing.compose.usecase.b");
            Object params = requestType.getConstructor(String.class, String.class, String.class,
                    String.class, String.class, sortType, String.class, calls.type("com.reddit.type.FeedLayout"),
                    calls.type("sg3.q1"), List.class, List.class, calls.type("com.reddit.feeds.data.FeedType"))
                    .newInstance(after, community.name, community.id, null, UUID.randomUUID().toString(), sort, null,
                            calls.constant("com.reddit.type.FeedLayout", "CLASSIC"), adContext(),
                            Collections.emptyList(), Collections.emptyList(),
                            calls.constant("com.reddit.feeds.data.FeedType", "SUBREDDIT"));
            Method fetch = method(calls.type(COMMUNITY_FEED_SERVICE), "b", Object.class,
                    requestType, calls.continuationImplType());
            request.check();
            calls.invoke(owner, fetch, new Object[]{params}, (result, error) -> {
                if (error != null) fail(completion, error);
                else {
                    try {
                        request.check();
                        exact(result, "lk1.b");
                        DiscoveryPolicy.nextCursor((String) field(result, "b", String.class), after);
                        new NativeFeed(calls).resolvePage(result, request.account, (page, failure) -> {
                            if (failure != null) fail(completion, failure);
                            else {
                                try {
                                    request.check();
                                    completion.complete(page, null);
                                } catch (ReflectiveOperationException | RuntimeException stale) {
                                    fail(completion, stale);
                                }
                            }
                        });
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        fail(completion, failure);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(completion, failure);
        }
    }

    private Object params(String query, String category, SearchSort sort) throws ReflectiveOperationException {
        Class<?> queryType = calls.type("com.reddit.domain.model.search.Query");
        Object nativeQuery = method(queryType, "from", queryType, String.class).invoke(null, query);
        return calls.type("sa3.b").getConstructor(queryType,
                calls.type("com.reddit.search.domain.model.SearchSortType"),
                calls.type("com.reddit.search.domain.model.SearchSortTimeFrame"), boolean.class,
                List.class, List.class, String.class, java.util.Map.class, List.class)
                .newInstance(nativeQuery, sort == null ? null
                                : calls.constant("com.reddit.search.domain.model.SearchSortType", sort.name()),
                        null, false, null, null, category, Collections.emptyMap(), Collections.emptyList());
    }

    private Object tracking() throws ReflectiveOperationException {
        Class<?> correlationType = calls.type("com.reddit.domain.model.search.SearchCorrelation");
        Object correlation = correlationType.getConstructor(String.class,
                calls.type("com.reddit.domain.model.search.OriginElement"),
                calls.type("com.reddit.domain.model.search.OriginPageType"),
                calls.type("com.reddit.domain.model.search.SearchSource"), String.class, String.class, String.class)
                .newInstance(UUID.randomUUID().toString(),
                        calls.constant("com.reddit.domain.model.search.OriginElement", "SEARCH_BAR"),
                        calls.constant("com.reddit.domain.model.search.OriginPageType", "HOME"),
                        calls.constant("com.reddit.domain.model.search.SearchSource", "DEFAULT"), null, null, null);
        return calls.type("ia3.f").getConstructor(String.class, String.class, String.class, Boolean.class,
                String.class, String.class, String.class, Boolean.class, Boolean.class, Boolean.class,
                calls.type("com.reddit.search.analytics.SearchStructureType"), correlationType,
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class)
                .newInstance(null, null, null, null, null, null, null, null, null, null,
                        calls.constant("com.reddit.search.analytics.SearchStructureType", "SEARCH"), correlation,
                        "search_results", null, null, null, null, null, null, null, null);
    }

    private Object adContext() throws ReflectiveOperationException {
        Class<?> optional = calls.type("j9.y0");
        Class<?> layout = calls.type("com.reddit.type.AdLayout");
        Class<?>[] signature = new Class<?>[17];
        Arrays.fill(signature, optional);
        signature[1] = layout;
        Object[] values = new Object[17];
        Arrays.fill(values, calls.constant("j9.v0", "b"));
        values[1] = calls.constant("com.reddit.type.AdLayout", "CLASSIC");
        return calls.type("sg3.q1").getConstructor(signature).newInstance(values);
    }

    private Object service(String name) throws ReflectiveOperationException {
        Object owner = NativeBindings.service(name);
        exact(owner, name);
        return owner;
    }

    private void exact(Object value, String name) throws ReflectiveOperationException {
        if (value == null || value.getClass() != calls.type(name)) {
            throw new IllegalStateException("Unexpected native discovery binding");
        }
    }

    private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... params)
            throws ReflectiveOperationException {
        Method method = owner.getMethod(name, params);
        if (method.getReturnType() != returns) throw new IllegalStateException("Unexpected native discovery method");
        return method;
    }

    private static Object field(Object owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        if (field.getType() != type) throw new IllegalStateException("Unexpected native discovery field");
        return field.get(owner);
    }

    private static Object read(Object owner, String getter, Class<?> returns) throws ReflectiveOperationException {
        return method(owner.getClass(), getter, returns).invoke(owner);
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List)) throw new IllegalStateException("Unexpected native discovery list");
        return (List<?>) value;
    }

    private static Object success(Object value) throws ReflectiveOperationException {
        if (value != null && value.getClass().getName().equals("hx.b")) {
            throw new IllegalStateException("Native search failed");
        }
        return NativeCalls.success(value);
    }

    private static void fail(NativeCalls.Callback completion, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
        completion.complete(null, cause);
    }

    private static final class Account {
        final Object session;
        final String account;

        Account() throws ReflectiveOperationException {
            session = NativeCalls.read(NativeBindings.service("com.reddit.session.o"), "t");
            account = NativeBindings.account();
            check();
        }

        void check() throws ReflectiveOperationException {
            Object current = NativeCalls.read(NativeBindings.service("com.reddit.session.o"), "t");
            if (!DiscoveryPolicy.sameAccount(session, account, current, NativeBindings.account())) {
                throw new IllegalStateException("Account changed or is not signed in");
            }
        }
    }
}