/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid;

import android.util.Log;
import app.morphe.extension.reddit.rokid.nativebridge.NativeAccount;
import app.morphe.extension.reddit.rokid.nativebridge.NativeCalls;
import app.morphe.extension.reddit.rokid.nativebridge.NativeDiscovery;
import app.morphe.extension.reddit.rokid.ui.HudView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Discovery and read-only account navigation stay in the same monochrome HUD. */
final class SecondaryRoutes {
    interface Host {
        void enter();
        boolean active();
        String account();
        void root(String selected);
        void feed(PostRoute route);
        void accountEntry(NativeAccount.AccountEntry entry, boolean comment, Runnable back);
        void signIn();
        void readability();
    }
    private static final class Listing<T> {
        final List<T> items = new ArrayList<>();
        String next;
        String selected = "";
        int scroll;
        boolean loaded;
        void remember(HudView hud) { selected = hud.selectedId(); scroll = hud.scrollPosition(); }
    }
    private final HudView hud;
    private final Host host;
    private final NativeDiscovery discovery;
    private final NativeAccount account;
    private final Map<String, Listing<NativeDiscovery.Community>> communityLists = new HashMap<>();
    private final Map<String, Listing<NativeAccount.AccountEntry>> entryLists = new HashMap<>();
    private String query = "";
    private NativeDiscovery.SearchSort searchSort = NativeDiscovery.SearchSort.RELEVANCE;
    private Runnable back = () -> {};
    private long operation;
    private boolean editing;

    SecondaryRoutes(HudView hud, Host host, NativeCalls calls) {
        this.hud = hud;
        this.host = host;
        discovery = new NativeDiscovery(calls);
        account = new NativeAccount(calls);
    }
    void clear() {
        operation++;
        communityLists.clear();
        entryLists.clear();
        query = "";
    }
    void leave() { operation++; editing = false; }
    void prepareThread(Runnable origin) { enter(origin); }
    void back() { operation++; back.run(); }
    boolean finishEditor() {
        if (!editing) return false;
        String value = hud.editingText().replaceAll("\\s+", " ").trim();
        hud.finishEditing();
        editing = false;
        query = value;
        if (query.isEmpty()) searchMenu(); else searchPosts();
        return true;
    }
    void search() {
        enter(() -> host.root("search"));
        editing = true;
        hud.edit("SEARCH", "Posts and communities \u00b7 phone keyboard", query, this::finishEditor);
    }
    private void searchMenu() {
        show("SEARCH", query.isEmpty() ? "Enter a query" : query, Arrays.asList(
                row("edit", "Edit search", "Phone keyboard", this::search),
                row("posts", "Posts", searchSort.name(), this::searchPosts),
                row("communities", "Communities", "", () -> communities(true)),
                row("sort", "Sort posts", searchSort.name(), this::searchSort),
                row("back", "Back", "", () -> host.root("search"))), "posts", 0, () -> host.root("search"));
    }
    private void searchSort() {
        List<HudView.Row> rows = new ArrayList<>();
        for (NativeDiscovery.SearchSort sort : NativeDiscovery.SearchSort.values()) {
            rows.add(row(sort.name(), sort == NativeDiscovery.SearchSort.RELEVANCE ? "Relevance"
                    : sort == NativeDiscovery.SearchSort.TOP ? "Top" : "New", "", () -> {
                searchSort = sort;
                searchMenu();
            }));
        }
        rows.add(row("back", "Back", "", this::searchMenu));
        show("SEARCH SORT", query, rows, searchSort.name(), 0, this::searchMenu);
    }
    private void searchPosts() {
        if (query.isEmpty()) { search(); return; }
        final String value = query;
        final NativeDiscovery.SearchSort sort = searchSort;
        leave();
        host.feed(new PostRoute("search:" + value + ":" + sort, "Search \u00b7 " + value,
                (cursor, size, cb) -> discovery.searchPosts(value, sort, cursor, cb), this::searchMenu));
    }
    void communities() { communities(false); }
    private void communities(boolean search) {
        if (search && query.isEmpty()) { search(); return; }
        if (!search && requireSignIn("communities")) return;
        String key = search ? "search:" + query : "joined";
        Listing<NativeDiscovery.Community> list = communityLists.computeIfAbsent(key, ignored -> new Listing<>());
        if (list.loaded) communityRows(search, list);
        else loadCommunities(search, list, null);
    }
    private void loadCommunities(boolean search, Listing<NativeDiscovery.Community> list, String cursor) {
        String value = query;
        String owner = host.account();
        Runnable origin = search ? this::searchMenu : () -> host.root("communities");
        long request = loading(search ? "Searching communities\u2026" : "Loading communities\u2026", origin);
        NativeCalls.Callback cb = (result, error) -> {
            if (!valid(request, owner)) return;
            if (error != null) { failure("Communities unavailable", error, () -> loadCommunities(search, list, cursor), origin); return; }
            NativeDiscovery.CommunityPage page = (NativeDiscovery.CommunityPage) result;
            if (cursor == null) list.items.clear();
            int firstNew = list.items.size();
            for (NativeDiscovery.Community community : page.communities) {
                if (list.items.stream().noneMatch(item -> item.id.equals(community.id))) list.items.add(community);
            }
            if (cursor != null && firstNew < list.items.size()) { list.selected = list.items.get(firstNew).id; list.scroll = 0; }
            list.next = page.next;
            list.loaded = true;
            communityRows(search, list);
        };
        if (search) discovery.searchCommunities(value, cursor, cb); else discovery.communities(cursor, cb);
    }
    private void communityRows(boolean search, Listing<NativeDiscovery.Community> list) {
        List<HudView.Row> rows = new ArrayList<>();
        for (NativeDiscovery.Community community : list.items) {
            String detail = clip(community.description, 85);
            if (community.favorite) detail = "Favorite \u00b7 " + detail;
            if (community.nsfw) detail = "Mature community \u00b7 " + detail;
            rows.add(row(community.id, community.title, detail, () -> {
                list.remember(hud);
                leave();
                host.feed(new PostRoute("community:" + community.id, community.title,
                        (cursor, size, cb) -> discovery.communityFeed(community, cursor, cb),
                        () -> communityRows(search, list)));
            }));
        }
        if (list.next != null) rows.add(row("more", "Load more", "", () -> {
            list.remember(hud);
            loadCommunities(search, list, list.next);
        }));
        rows.add(row("refresh", "Refresh", "", () -> loadCommunities(search, list, null)));
        Runnable origin = search ? this::searchMenu : () -> host.root("communities");
        rows.add(row("back", "Back", "", origin));
        show(search ? "COMMUNITY RESULTS" : "COMMUNITIES", list.items.isEmpty() ? "No communities found"
                : search ? query : "Your joined communities", rows, list.selected, list.scroll, origin);
    }
    void group(boolean saved) { group(saved, "posts"); }
    private void group(boolean saved, String selected) {
        String key = saved ? "saved" : "profile";
        if (requireSignIn(key)) return;
        show(saved ? "SAVED" : "PROFILE", "Your posts and comments", Arrays.asList(
                row("posts", "Posts", "", () -> accountPosts(saved)),
                row("comments", "Comments", "", () -> entries(saved ? "saved" : "profile")),
                row("back", "Back", "", () -> host.root(key))), selected, 0, () -> host.root(key));
    }
    private void accountPosts(boolean saved) {
        leave();
        host.feed(new PostRoute(saved ? "saved-posts" : "profile-posts", saved ? "Saved posts" : "Profile posts",
                (cursor, size, cb) -> account.posts(saved ? NativeAccount.PostScope.SAVED : NativeAccount.PostScope.PROFILE,
                        cursor, size, cb), () -> group(saved)));
    }
    void inbox() {
        if (!requireSignIn("inbox")) entries("inbox");
    }
    private Runnable entryOrigin(String kind) {
        return kind.equals("inbox") ? () -> host.root("inbox") : () -> group(kind.equals("saved"), "comments");
    }
    private void entries(String kind) {
        Listing<NativeAccount.AccountEntry> list = entryLists.computeIfAbsent(kind, ignored -> new Listing<>());
        if (list.loaded) entryRows(kind, list); else loadEntries(kind, list, null);
    }
    private void loadEntries(String kind, Listing<NativeAccount.AccountEntry> list, String cursor) {
        String owner = host.account();
        Runnable origin = entryOrigin(kind);
        long request = loading("Loading " + entryTitle(kind) + "\u2026", origin);
        NativeCalls.Callback cb = (value, error) -> {
            if (!valid(request, owner)) return;
            if (error != null) { failure(entryTitle(kind) + " unavailable", error,
                    () -> loadEntries(kind, list, cursor), origin); return; }
            NativeAccount.EntryPage page = (NativeAccount.EntryPage) value;
            if (cursor == null) list.items.clear();
            int firstNew = list.items.size();
            for (NativeAccount.AccountEntry entry : page.entries) {
                if (list.items.stream().noneMatch(item -> item.id.equals(entry.id))) list.items.add(entry);
            }
            if (cursor != null && firstNew < list.items.size()) { list.selected = list.items.get(firstNew).id; list.scroll = 0; }
            list.next = page.next;
            list.loaded = true;
            entryRows(kind, list);
        };
        if (kind.equals("inbox")) account.inbox(cursor, cb);
        else account.comments(kind.equals("saved") ? NativeAccount.CommentScope.SAVED : NativeAccount.CommentScope.PROFILE, cursor, cb);
    }
    private void entryRows(String kind, Listing<NativeAccount.AccountEntry> list) {
        List<HudView.Row> rows = new ArrayList<>();
        for (NativeAccount.AccountEntry entry : list.items) {
            rows.add(row(entry.id, entry.title.isEmpty() ? "Comment" : clip(entry.title, 100),
                    (entry.unread ? "Unread \u00b7 " : "") + clip(entry.body.replaceAll("\\s+", " "), 110), () -> {
                list.remember(hud);
                entryRead(kind, list, entry, 0);
            }));
        }
        if (list.next != null) rows.add(row("more", "Load more", "", () -> {
            list.remember(hud);
            loadEntries(kind, list, list.next);
        }));
        rows.add(row("refresh", "Refresh", "", () -> loadEntries(kind, list, null)));
        Runnable origin = entryOrigin(kind);
        rows.add(row("back", "Back", "", origin));
        show(entryTitle(kind), list.items.isEmpty() ? "No items" : kind.equals("inbox")
                ? "Notifications and replies" : "Your comments", rows, list.selected, list.scroll, origin);
    }
    private void entryRead(String kind, Listing<NativeAccount.AccountEntry> list,
                           NativeAccount.AccountEntry entry, int page) {
        enter(() -> entryRows(kind, list));
        hud.read(entryTitle(kind), entry.subreddit + (entry.title.isEmpty() ? "" : " \u00b7 " + entry.title),
                entry.body, page, () -> entryActions(kind, list, entry, hud.readingPage()));
    }
    private void entryActions(String kind, Listing<NativeAccount.AccountEntry> list,
                              NativeAccount.AccountEntry entry, int page) {
        List<HudView.Row> rows = new ArrayList<>();
        Runnable origin = () -> entryRows(kind, list);
        if (!entry.commentId.isEmpty() && !entry.postId.isEmpty()) rows.add(row("comment", "Open full comment",
                "Read and reply in its exact thread", () -> { leave(); host.accountEntry(entry, true, origin); }));
        if (!entry.postId.isEmpty()) rows.add(row("post", "Open post", "", () -> { leave(); host.accountEntry(entry, false, origin); }));
        rows.add(row("read", "Back to reading", "", () -> entryRead(kind, list, entry, page)));
        rows.add(row("back", "Back to list", "", origin));
        show("ITEM ACTIONS", clip(entry.title, 85), rows, rows.get(0).id, 0, () -> entryRead(kind, list, entry, page));
    }
    void preferences() {
        if (requireSignIn("settings")) return;
        String owner = host.account();
        long request = loading("Loading preferences\u2026", host::readability);
        account.preferences((value, error) -> {
            if (!valid(request, owner)) return;
            if (error != null) { failure("Preferences unavailable", error, this::preferences, host::readability); return; }
            List<HudView.Row> rows = new ArrayList<>();
            for (Object item : (List<?>) value) {
                NativeAccount.Preference preference = (NativeAccount.Preference) item;
                rows.add(row(preference.label, preference.label, preference.value, () -> {}));
            }
            rows.add(row("refresh", "Refresh", "", this::preferences));
            rows.add(row("back", "Back", "", host::readability));
            show("REDDIT PREFERENCES", "Current account \u00b7 read only", rows, "", 0, host::readability);
        });
    }
    private boolean requireSignIn(String selected) {
        if (!host.account().isEmpty()) return false;
        show("SIGN IN REQUIRED", "Use the official Reddit account screen", Arrays.asList(
                row("signin", "Sign in", "", host::signIn),
                row("back", "Back", "", () -> host.root(selected))), "signin", 0, () -> host.root(selected));
        return true;
    }
    private long loading(String title, Runnable origin) {
        enter(origin);
        hud.show(title, "", Arrays.asList(row("back", "Back", "", origin)), "back");
        return operation;
    }
    private boolean valid(long request, String owner) {
        if (request != operation || !host.active()) return false;
        if (!owner.equals(host.account())) { clear(); host.root("account"); return false; }
        return true;
    }
    private void failure(String title, Throwable error, Runnable retry, Runnable origin) {
        NativeCalls.logFailure("RedditGlassesRoutes", error);
        show(title, "Check your connection or retry.", Arrays.asList(row("retry", "Retry", "", retry),
                row("back", "Back", "", origin)), "retry", 0, origin);
    }
    private void enter(Runnable origin) {
        operation++;
        back = origin;
        host.enter();
    }
    private void show(String title, String context, List<HudView.Row> rows, String selected, int scroll, Runnable origin) {
        enter(origin);
        hud.show(title, context, rows, selected, scroll);
    }
    private static String entryTitle(String kind) { return kind.equals("inbox") ? "INBOX" : kind.equals("saved") ? "SAVED COMMENTS" : "PROFILE COMMENTS"; }
    private static HudView.Row row(String id, String label, String detail, Runnable action) { return new HudView.Row(id, label, detail, action); }
    private static String clip(String text, int size) { return text.length() <= size ? text : text.substring(0, size - 1) + "\u2026"; }
}
