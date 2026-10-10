/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewGroup;

import app.morphe.extension.reddit.rokid.core.ReplyDraft;
import app.morphe.extension.reddit.rokid.core.ReplyTarget;
import app.morphe.extension.reddit.rokid.core.SingleAxisInput;
import app.morphe.extension.reddit.rokid.core.PostNavigation;
import app.morphe.extension.reddit.rokid.nativebridge.NativeMedia;
import app.morphe.extension.reddit.rokid.nativebridge.NativeAccount;
import app.morphe.extension.reddit.rokid.nativebridge.NativeActions;
import app.morphe.extension.reddit.rokid.nativebridge.NativeBindings;
import app.morphe.extension.reddit.rokid.nativebridge.NativeCalls;
import app.morphe.extension.reddit.rokid.nativebridge.NativeComments;
import app.morphe.extension.reddit.rokid.nativebridge.NativeFeed;
import app.morphe.extension.reddit.rokid.nativebridge.NativeReplies;
import app.morphe.extension.reddit.rokid.ui.HudView;
import app.morphe.extension.reddit.rokid.ui.NativeSurface;
import app.morphe.extension.reddit.rokid.ui.MediaView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RedditController {
    private enum Screen { MENU, FEED, POST, COMMENTS, COMMENT, ACTIONS, EDITOR, DRAFT, PREVIEW, STATUS, NATIVE, MEDIA, SECONDARY }
    private final Activity activity;
    private final HudView hud;
    private final NativeSurface nativeSurface;
    private final NativeCalls calls;
    private final NativeFeed feeds;
    private final NativeComments threads;
    private final NativeReplies replies;
    private final NativeActions actions;
    private final Runnable stopObserving;
    private final SingleAxisInput input = new SingleAxisInput();
    private final Map<String, ReplyDraft> drafts = new HashMap<>();
    private final List<Object> posts = new ArrayList<>();
    private final List<Object> comments = new ArrayList<>();
    private final List<Integer> visibleComments = new ArrayList<>();
    private static final class FeedState {
        final List<Object> posts;
        final String next;
        final int index;
        final int scroll;
        FeedState(List<Object> posts, String next, int index, int scroll) {
            this.posts = new ArrayList<>(posts);
            this.next = next;
            this.index = index;
            this.scroll = scroll;
        }
    }
    private final Map<NativeFeed.Scope, FeedState> feedStates = new java.util.EnumMap<>(NativeFeed.Scope.class);
    private final Map<String, FeedState> routeStates = new HashMap<>();
    private PostRoute postRoute;
    private Runnable commentBack = this::commentList;
    private Runnable postBack = this::feed;
    private final SecondaryRoutes secondary;
    private final NativeAccount accountRoutes;
    private String feedAccount = "";
    private NativeFeed.Scope scope = NativeFeed.Scope.HOME;
    private Screen screen = Screen.MENU;
    private Screen actionOrigin;
    private Screen replyOrigin = Screen.POST;
    private int postIndex;
    private int commentIndex;
    private int readingPage;
    private int postPage;
    private int feedScroll;
    private long mediaOpenedAt;
    private final java.util.Set<String> revealed = new java.util.HashSet<>();
    private String next;
    private String commentSort = "CONFIDENCE";
    private final java.util.Set<String> collapsed = new java.util.HashSet<>();
    private ReplyDraft draft;
    private boolean loading;
    private boolean feedListing;
    private boolean closed;
    private long request;
    private Runnable finishEditor;
    private MediaView media;
    private boolean nativeAuthRequested;
    private boolean nativeAuthOpened;

    public RedditController(Activity activity, ViewGroup root) throws ReflectiveOperationException {
        this.activity = activity;
        calls = new NativeCalls(activity.getClassLoader());
        feeds = new NativeFeed(calls);
        threads = new NativeComments(calls);
        replies = new NativeReplies(calls);
        actions = new NativeActions(calls);
        hud = new HudView(activity);
        root.addView(hud, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nativeSurface = new NativeSurface(activity, root, hud);
        stopObserving = NativeBindings.observe(() -> {
            if (!closed && !account().equals(feedAccount) && !hud.isEditing()) {
                checkFeedAccount();
                menu();
            }
        });
        accountRoutes = new NativeAccount(calls);
        secondary = new SecondaryRoutes(hud, new SecondaryRoutes.Host() {
            public void enter() {
                request++;
                loading = false;
                closeMedia();
                nativeSurface.close();
                screen = Screen.SECONDARY;
            }
            public boolean active() { return !closed && screen == Screen.SECONDARY; }
            public String account() { return RedditController.this.account(); }
            public void root(String selected) { menu(selected); }
            public void feed(PostRoute route) { openFeed(route); }
            public void accountEntry(NativeAccount.AccountEntry entry, boolean comment, Runnable back) {
                openAccountEntry(entry, comment, back);
            }
            public void signIn() { nativeAuth(); }
            public void readability() { settings(); }
        }, calls);
        menu();
    }

    public void close() {
        if (closed) return;
        closed = true;
        secondary.leave();
        for (ReplyDraft item : drafts.values()) item.returnToEditor();
        closeMedia();
        request++;
        stopObserving.run();
        nativeSurface.close();
        if (hud.getParent() instanceof ViewGroup) ((ViewGroup) hud.getParent()).removeView(hud);
    }

    public void onNativeAuthOpened() {
        if (!closed && screen == Screen.NATIVE && nativeAuthRequested) nativeAuthOpened = true;
    }

    public void onResume() {
        if (closed) return;
        boolean returningFromAuth = nativeAuthOpened;
        nativeAuthOpened = false;
        if (hud.isEditing()) return;
        if (returningFromAuth && screen == Screen.NATIVE) menu();
        else if (screen == Screen.MENU) menu(hud.selectedId());
    }

    public boolean isEditing() { return hud.isEditing(); }

    public boolean handle(KeyEvent event) {
        if (closed) return false;
        if (hud.isEditing()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (nativeSurface.isActive()) nativeSurface.back();
                else if (!secondary.finishEditor() && finishEditor != null) finishEditor.run();
                return true;
            }
            // Enter and Space remain ordinary editor input; neither can publish.
            return false;
        }
        SingleAxisInput.Action action = input.map(event.getAction(), event.getKeyCode(),
                event.getRepeatCount(), SystemClock.uptimeMillis());
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return SingleAxisInput.classify(event.getKeyCode()) != SingleAxisInput.Action.NONE;
        }
        switch (action) {
            case DUPLICATE: return true;
            case NEXT: move(1); return true;
            case PREVIOUS: move(-1); return true;
            case SELECT: hud.activate(); return true;
            case BACK: back(); return true;
            default: return false;
        }
    }

    private void move(int direction) {
        if (screen == Screen.MEDIA && media != null) {
            if (PostNavigation.acceptsMediaSwipe(mediaOpenedAt, SystemClock.uptimeMillis())) media.step(direction);
            return;
        }
        if (screen == Screen.FEED && feedListing && !posts.isEmpty()) {
            if ("feed-options".equals(hud.selectedId())) {
                if (direction > 0) hud.move(direction);
                return;
            }
            int candidate = postIndex + direction;
            if (candidate >= posts.size() && next != null && !loading) loadFeed(next);
            else if (candidate < 0 && postIndex == 0) hud.move(-1);
            else if (candidate >= 0 && candidate < posts.size()) hud.move(direction);
            return;
        }
        if (hud.moveReading(direction)) return;
        hud.move(direction);
    }

    private void menu() {
        menu("HOME");
    }

    private void menu(String selectedId) {
        checkFeedAccount();
        rememberFeed();
        postRoute = null;
        posts.clear();
        next = null;
        secondary.leave();
        request++;
        loading = false;
        closeMedia();
        nativeSurface.close();
        nativeAuthRequested = false;
        nativeAuthOpened = false;
        screen = Screen.MENU;
        List<HudView.Row> rows = new ArrayList<>();
        for (NativeFeed.Scope item : NativeFeed.Scope.values()) {
            rows.add(row(item.name(), item.title, "", () -> chooseFeed(item)));
        }
        rows.add(row("communities", "Communities", "Your joined communities", secondary::communities));
        rows.add(row("search", "Search", "Posts and communities", secondary::search));
        rows.add(row("inbox", "Inbox", "Notifications and replies", secondary::inbox));
        rows.add(row("profile", "Profile", "Posts and comments", () -> secondary.group(false)));
        rows.add(row("saved", "Saved", "Posts and comments", () -> secondary.group(true)));
        rows.add(row("create", "Create post", "Review before posting", () -> nativeRoute("Create post", "/submit/")));
        rows.add(row("settings", "Settings", "Text size and Reddit preferences", this::settings));
        rows.add(row("account", account().isEmpty() ? "Sign in" : "u/" + account(), "Official Reddit account", () -> nativeRoute("Account", "/login/")));
        rows.add(row("exit", "Exit Reddit", "Return to launcher", this::exit));
        hud.show("REDDIT", "", rows, selectedId);
    }

    private void checkFeedAccount() {
        String current = account();
        if (current.equals(feedAccount)) return;
        request++;
        loading = false;
        feedStates.clear();
        routeStates.clear();
        secondary.clear();
        postRoute = null;
        posts.clear();
        comments.clear();
        collapsed.clear();
        revealed.clear();
        next = null;
        postIndex = commentIndex = readingPage = postPage = feedScroll = 0;
        feedAccount = current;
    }

    private void rememberFeed() {
        if (posts.isEmpty()) return;
        if (screen == Screen.FEED && feedListing) feedScroll = hud.scrollPosition();
        FeedState state = new FeedState(posts, next, postIndex, feedScroll);
        if (postRoute == null) feedStates.put(scope, state); else routeStates.put(postRoute.key, state);
    }

    private void chooseFeed(NativeFeed.Scope selectedScope) {
        checkFeedAccount();
        rememberFeed();
        request++;
        loading = false;
        postRoute = null;
        secondary.leave();
        commentBack = this::commentList;
        scope = selectedScope;
        posts.clear();
        comments.clear();
        collapsed.clear();
        commentIndex = readingPage = postPage = feedScroll = postIndex = 0;
        next = null;
        FeedState cached = feedStates.get(scope);
        if (cached != null) {
            posts.addAll(cached.posts);
            next = cached.next;
            postIndex = cached.index;
            feedScroll = cached.scroll;
            feed();
        } else loadFeed(null);
    }

    private void openFeed(PostRoute route) {
        checkFeedAccount();
        rememberFeed();
        request++;
        loading = false;
        postRoute = route;
        secondary.leave();
        comments.clear();
        collapsed.clear();
        commentBack = this::commentList;
        posts.clear();
        next = null;
        commentIndex = readingPage = postPage = feedScroll = postIndex = 0;
        FeedState cached = routeStates.get(route.key);
        if (cached != null) {
            posts.addAll(cached.posts);
            next = cached.next;
            postIndex = cached.index;
            feedScroll = cached.scroll;
            feed();
        } else loadFeed(null);
    }

    private String feedTitle() { return postRoute == null ? scope.title : postRoute.title; }
    private void feedBack() {
        rememberFeed();
        if (postRoute == null) menu(scope.name()); else postRoute.back.run();
    }

    private void loadFeed(String cursor) {
        if (loading) return;
        if (screen == Screen.FEED && feedListing) feedScroll = hud.scrollPosition();
        loading = true;
        feedListing = false;
        long operation = ++request;
        NativeFeed.Scope requestedScope = scope;
        PostRoute route = postRoute;
        String requestedAccount = account();
        screen = Screen.FEED;
        hud.show("Loading " + feedTitle() + "\u2026", "", Arrays.asList(row("back", "Back", "", this::feedBack)), "back");
        NativeCalls.Callback completion = (value, error) -> {
            if (closed || operation != request || requestedScope != scope || route != postRoute) return;
            if (!requestedAccount.equals(account())) { checkFeedAccount(); menu(); return; }
            loading = false;
            if (error != null) {
                NativeCalls.logFailure("RedditGlassesFeed", error);
                screen = Screen.FEED;
                List<HudView.Row> rows = Arrays.asList(
                        row("retry", "Retry", "", () -> loadFeed(cursor)),
                        row("back", "Back", "", this::feedBack));
                hud.show(feedTitle(), "Feed unavailable. Check your connection.", rows, "retry");
                return;
            }
            NativeFeed.Page page = (NativeFeed.Page) value;
            int firstNew = cursor == null ? 0 : posts.size();
            if (cursor == null) posts.clear();
            for (Object post : page.posts) {
                try {
                    String id = NativeCalls.text(post, "getKindWithId");
                    boolean duplicate = false;
                    for (Object old : posts) if (id.equals(NativeCalls.text(old, "getKindWithId"))) duplicate = true;
                    if (!duplicate) posts.add(post);
                } catch (ReflectiveOperationException invalid) { /* Invalid native entries are skipped. */ }
            }
            next = java.util.Objects.equals(cursor, page.next) ? null : page.next;
            postIndex = Math.min(firstNew, Math.max(0, posts.size() - 1));
            readingPage = postPage = feedScroll = 0;
            feed();
        };
        if (route == null) feeds.load(requestedScope, cursor, posts.size(), completion);
        else route.loader.load(cursor, posts.size(), completion);
    }

    private void feed() {
        closeMedia();
        screen = Screen.FEED;
        feedListing = !posts.isEmpty();
        if (posts.isEmpty()) {
            List<HudView.Row> empty = new ArrayList<>();
            if (next != null) empty.add(row("more", "Load more", "", () -> loadFeed(next)));
            empty.add(row("refresh", "Refresh", "", () -> loadFeed(null)));
            empty.add(row("back", "Back", "", this::feedBack));
            hud.show(feedTitle(), "No posts in this listing.", empty, "refresh");
            return;
        }
        safe(() -> {
            List<HudView.FeedRow> items = new ArrayList<>();
            for (int i = 0; i < posts.size(); i++) {
                Object post = posts.get(i);
                final int index = i;
                boolean hidden = sensitive(post) && !revealed.contains(NativeCalls.text(post, "getKindWithId"));
                NativeMedia.Thumbnail preview = hidden ? null : NativeMedia.thumbnail(post);
                String label = hidden ? "Sensitive" : preview == null ? "Text" : preview.label;
                String metadata = NativeCalls.text(post, "getSubredditNamePrefixed") + " \u00b7 " + label
                        + " \u00b7 " + NativeCalls.text(post, "getNumComments") + " comments";
                String excerpt = hidden ? "Content hidden. Open for Reveal."
                        : clip(NativeCalls.text(post, "getSelftext").replaceAll("\\s+", " "), 150);
                String detail = excerpt + (excerpt.isEmpty() ? "" : "\n") + "u/"
                        + NativeCalls.text(post, "getAuthor") + " \u00b7 \u25b2 " + NativeCalls.text(post, "getScore");
                HudView.Row row = row(NativeCalls.text(post, "getKindWithId"), NativeCalls.text(post, "getTitle"), detail, () -> {
                    postIndex = index;
                    postBack = this::feed;
                    comments.clear();
                    commentBack = this::commentList;
                    collapsed.clear();
                    commentIndex = readingPage = 0;
                    feedScroll = hud.scrollPosition();
                    postPage = 0;
                    screen = Screen.POST;
                    readPost(true);
                });
                items.add(new HudView.FeedRow(row, metadata, preview == null ? "" : preview.source,
                        hidden ? "Reveal" : preview == null ? "" : preview.label));
            }
            items.add(0, new HudView.FeedRow(row("feed-options", "Feed options", "Refresh and navigation", this::feedOptions), "", "", ""));
            hud.showFeed(feedTitle().toUpperCase(java.util.Locale.ROOT), items, NativeCalls.text(posts.get(postIndex), "getKindWithId"),
                    feedScroll, index -> { if (index > 0 && index <= posts.size()) postIndex = index - 1; });
        });
    }

    private void feedOptions() {
        feedScroll = hud.scrollPosition();
        screen = Screen.ACTIONS;
        actionOrigin = Screen.FEED;
        hud.show("FEED OPTIONS", feedTitle(), Arrays.asList(
                row("refresh", "Refresh", "Load current Reddit results", () -> {
                    if (postRoute == null && scope == NativeFeed.Scope.LATEST) NativeBindings.resetLatest();
                    loadFeed(null);
                }),
                row("feed", "Back to feed", "", this::feed),
                row("back", "Back", "", this::feedBack),
                row("menu", "Main menu", "", this::menu)), "refresh");
    }

    private boolean sensitive(Object post) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(NativeCalls.read(post, "getOver18"))
                || Boolean.TRUE.equals(NativeCalls.read(post, "getSpoiler"));
    }

    private void readPost(boolean detail) {
        screen = Screen.POST;
        safe(() -> {
            Object post = posts.get(postIndex);
            boolean hidden = sensitive(post) && !revealed.contains(NativeCalls.text(post, "getKindWithId"));
            NativeMedia.Thumbnail preview = hidden ? null : NativeMedia.thumbnail(post);
            String body = hidden ? "Sensitive or spoiler content. Tap for Reveal."
                    : NativeCalls.text(post, "getSelftext");
            String context = NativeCalls.text(post, "getSubredditNamePrefixed") + " \u00b7 u/" + NativeCalls.text(post, "getAuthor");
            hud.readPost(context, NativeCalls.text(post, "getTitle"), body, postPage,
                    preview == null ? "" : preview.source, preview == null ? "" : preview.label,
                    () -> postActions(true), preview == null ? null : () -> {
                        postPage = hud.readingPage();
                        viewMedia(post);
                    });
        });
    }

    private void postActions(boolean detail) {
        postPage = hud.readingPage();
        actionOrigin = Screen.POST;
        screen = Screen.ACTIONS;
        safe(() -> {
            Object post = posts.get(postIndex);
            Boolean vote = (Boolean) NativeCalls.read(post, "getVoteState");
            boolean saved = Boolean.TRUE.equals(NativeCalls.read(post, "getSaved"));
            boolean reveal = sensitive(post) && !revealed.contains(NativeCalls.text(post, "getKindWithId"));
            List<HudView.Row> rows = new ArrayList<>();
            if (reveal) rows.add(row("reveal", "Reveal content", "Sensitive or spoiler content", () -> safe(() -> {
                revealed.add(NativeCalls.text(post, "getKindWithId"));
                readPost(true);
            })));
            if (reveal) {
                rows.add(row("back", "Back", "", this::returnToPost));
                hud.show("REVEAL CONTENT?", clip(NativeCalls.text(post, "getTitle"), 90), rows, "back");
                return;
            }
            rows.add(row("comments", "Comments", "", this::loadComments));
            rows.add(row("reply", "Reply to post", "Review the exact destination", () -> compose(post, false)));
            NativeMedia.Thumbnail preview = NativeMedia.thumbnail(post);
            if (preview != null) rows.add(row("media", "Open media", preview.label, () -> viewMedia(post)));
            rows.add(row("up", Boolean.TRUE.equals(vote) ? "Remove upvote" : "Upvote", "", () ->
                    actions.vote(post, false, Boolean.TRUE.equals(vote) ? "NONE" : "UP", this::mutationResult)));
            rows.add(row("down", Boolean.FALSE.equals(vote) ? "Remove downvote" : "Downvote", "", () ->
                    actions.vote(post, false, Boolean.FALSE.equals(vote) ? "NONE" : "DOWN", this::mutationResult)));
            rows.add(row("save", saved ? "Unsave" : "Save", "", () -> actions.savePost(post, !saved, this::mutationResult)));
            rows.add(row("native", "Media and more", "Gallery · video · link · report", () -> openPostNative(post)));
            rows.add(row("back", "Back", "", this::returnToPost));
            hud.show("POST ACTIONS", clip(NativeCalls.text(post, "getTitle"), 90), rows, "comments");
        });
    }

    private void loadComments() {
        if (loading) return;
        safe(() -> {
            String postId = NativeCalls.text(posts.get(postIndex), "getKindWithId");
            loading = true;
            long operation = ++request;
            status("Loading comments…", "", this::returnToPost);
            threads.load(postId, null, commentSort, (value, error) -> {
                if (closed || operation != request) return;
                loading = false;
                if (error != null) status("Comments unavailable", "Your drafts are kept.", this::loadComments);
                else safe(() -> {
                    comments.clear();
                    NativeComments.flatten((List<?>) NativeCalls.read(value, "getComments"), comments);
                    commentIndex = Math.min(commentIndex, Math.max(0, comments.size() - 1));
                    commentList();
                });
            });
        });
    }

    private void commentList() {
        commentBack = this::commentList;
        screen = Screen.COMMENTS;
        safe(() -> {
            List<HudView.Row> rows = new ArrayList<>();
            int hiddenDepth = -1;
            visibleComments.clear();
            for (int i = 0; i < comments.size(); i++) {
                Object comment = comments.get(i);
                String id = NativeCalls.text(comment, "getKindWithId");
                int depth = ((Number) NativeCalls.read(comment, "getDepth")).intValue();
                if (hiddenDepth >= 0 && depth > hiddenDepth) continue;
                visibleComments.add(i);
                hiddenDepth = collapsed.contains(id) ? depth : -1;
                final int index = i;
                rows.add(row(id, "u/" + NativeCalls.text(comment, "getAuthor") + (hiddenDepth >= 0 ? " · replies hidden" : ""),
                        (depth > 0 ? "Depth " + depth + " · " : "") + clip(NativeCalls.text(comment, "getBody"), 120), () -> {
                            commentIndex = index;
                            readingPage = 0;
                            comment();
                        }));
            }
            rows.add(row("reply", "Reply to post", "", () -> compose(posts.get(postIndex), false)));
            rows.add(row("refresh", "Refresh comments", "", this::loadComments));
            rows.add(row("sort", "Sort comments", commentSort, this::sortComments));
            rows.add(row("more", "More comments", "Open the full native thread", () -> openPostNative(posts.get(postIndex))));
            rows.add(row("back", "Back to post", "", this::returnToPost));
            hud.show("COMMENTS", comments.isEmpty() ? "No replies yet." : "Nested parents stay attached.", rows,
                    comments.isEmpty() ? "reply" : NativeCalls.text(comments.get(commentIndex), "getKindWithId"));
        });
    }

    private void comment() {
        screen = Screen.COMMENT;
        safe(() -> {
            Object item = comments.get(commentIndex);
            String parent = NativeCalls.text(item, "getParentKindWithId");
            hud.read("COMMENT \u00b7 " + (commentIndex + 1) + "/" + comments.size(),
                    "u/" + NativeCalls.text(item, "getAuthor") + "\n" + parentContext(parent),
                    NativeCalls.text(item, "getBody"), readingPage, this::commentActions);
        });
    }

    private String parentContext(String parentId) throws ReflectiveOperationException {
        for (Object parent : comments) {
            if (parentId.equals(NativeCalls.text(parent, "getKindWithId"))) {
                return "Reply to u/" + NativeCalls.text(parent, "getAuthor") + ": "
                        + clip(NativeCalls.text(parent, "getBody").replaceAll("\\s+", " "), 75);
            }
        }
        return "On: " + clip(NativeCalls.text(posts.get(postIndex), "getTitle"), 85);
    }

    private void commentActions() {
        readingPage = hud.readingPage();
        actionOrigin = Screen.COMMENT;
        screen = Screen.ACTIONS;
        safe(() -> {
            Object item = comments.get(commentIndex);
            Boolean vote = (Boolean) NativeCalls.read(item, "getVoteState");
            boolean saved = Boolean.TRUE.equals(NativeCalls.read(item, "getSaved"));
            List<HudView.Row> rows = Arrays.asList(
                    row("reply", "Reply to this comment", clip(NativeCalls.text(item, "getBody"), 80), () -> compose(item, true)),
                    row("up", Boolean.TRUE.equals(vote) ? "Remove upvote" : "Upvote", "", () ->
                            actions.vote(item, true, Boolean.TRUE.equals(vote) ? "NONE" : "UP", this::mutationResult)),
                    row("down", Boolean.FALSE.equals(vote) ? "Remove downvote" : "Downvote", "", () ->
                            actions.vote(item, true, Boolean.FALSE.equals(vote) ? "NONE" : "DOWN", this::mutationResult)),
                    row("save", saved ? "Unsave" : "Save", "", () -> actions.saveComment(item, !saved, this::mutationResult)),
                    row("collapse", collapsed.contains(NativeCalls.text(item, "getKindWithId")) ? "Show replies" : "Hide replies", "", () -> safe(() -> {
                        String id = NativeCalls.text(item, "getKindWithId");
                        if (!collapsed.remove(id)) collapsed.add(id);
                        commentList();
                    })),
                    row("more", "More actions", "Edit · delete · report", () -> openCommentNative(item)),
                    row("back", "Back", "", this::comment));
            hud.show("COMMENT ACTIONS", "u/" + NativeCalls.text(item, "getAuthor"), rows, "reply");
        });
    }

    private void compose(Object parent, boolean comment) {
        replyOrigin = screen == Screen.ACTIONS ? actionOrigin : screen;
        if (replyOrigin == Screen.MEDIA) replyOrigin = Screen.POST;
        safe(() -> {
            String account = NativeBindings.account();
            if (account.isEmpty()) { nativeRoute("Sign in", "/login/"); return; }
            if (!NativeReplies.allows(parent)) { status("Replies are closed", "This parent cannot receive replies.", this::returnFromDraft); return; }
            ReplyTarget target = new ReplyTarget(comment ? ReplyTarget.Kind.COMMENT : ReplyTarget.Kind.POST,
                    comment ? NativeCalls.text(parent, "getLinkKindWithId") : NativeCalls.text(parent, "getKindWithId"),
                    NativeCalls.text(parent, "getKindWithId"), account, NativeCalls.text(parent, "getAuthor"),
                    comment ? NativeCalls.text(parent, "getBody") : NativeCalls.text(parent, "getTitle"));
            String key = account + ":" + target.postId + ":" + target.parentId;
            draft = drafts.get(key);
            if (draft == null || draft.phase() == ReplyDraft.Phase.SENT) {
                draft = new ReplyDraft(target);
                drafts.put(key, draft);
            }
            showDraft();
        });
    }

    private void returnFromDraft() {
        if (replyOrigin == Screen.COMMENT && !comments.isEmpty()) comment();
        else if (replyOrigin == Screen.COMMENTS) commentList();
        else returnToPost();
    }

    private String destination() {
        return (draft.target.kind == ReplyTarget.Kind.POST ? "Reply to post by " : "Reply to comment by ")
                + "u/" + draft.target.author + "\n" + clip(draft.target.context, 100);
    }

    private void editReply() {
        draft.returnToEditor();
        screen = Screen.EDITOR;
        finishEditor = this::finishReplyEditing;
        hud.edit("REPLY", destination(), draft.text(), finishEditor);
    }

    private void finishReplyEditing() {
        draft.edit(hud.editingText());
        hud.finishEditing();
        showDraft();
    }

    private void showDraft() {
        screen = Screen.DRAFT;
        ReplyDraft current = draft;
        if (current.phase() == ReplyDraft.Phase.SENDING) {
            status("Sending…", "The draft is kept. Sending again is disabled.", this::returnFromDraft);
            return;
        }
        if (current.phase() == ReplyDraft.Phase.UNKNOWN) {
            hud.show("CHECK THREAD", destination(), Arrays.asList(
                    row("check", "Check the thread", "Outcome unknown. No automatic retry.", this::reconcile),
                    row("back", "Back with draft", "", this::returnFromDraft)), "check");
            return;
        }
        if (current.phase() == ReplyDraft.Phase.SENT) {
            status("Reply sent", current.sentId(), this::openSentReply);
            return;
        }
        List<HudView.Row> rows = Arrays.asList(
                row("edit", current.text().isEmpty() ? "Write reply" : "Edit draft", "Phone keyboard", this::editReply),
                row("preview", "Preview", clip(current.text(), 100), () -> {
                    if (current.preview()) preview(); else showDraft();
                }),
                row("keep", "Back with draft", "", this::returnFromDraft),
                row("discard", "Discard draft", "Requires confirmation", () -> hud.show("DISCARD DRAFT?", destination(), Arrays.asList(
                        row("keep", "Keep draft", "", this::showDraft), row("discard", "Discard", "", () -> {
                            drafts.values().remove(current); draft = null; returnFromDraft();
                        })), "keep")));
        hud.show("REPLY DRAFT", destination() + (current.message().isEmpty() ? "" : "\n" + current.message()), rows, "edit");
    }

    private void preview() {
        screen = Screen.PREVIEW;
        hud.show("PREVIEW", destination(), Arrays.asList(
                row("read", "Read your reply", clip(draft.text(), 120), () -> hud.read("YOUR REPLY", destination(), draft.text(), 0, this::preview)),
                row("send", "Send reply", "As u/" + draft.target.accountId, this::send),
                row("edit", "Edit", "", this::editReply),
                row("back", "Back with draft", "", this::showDraft)), "read");
    }

    private void send() {
        ReplyDraft submitted = draft;
        replies.send(submitted, () -> {
            if (!closed && draft == submitted && screen == Screen.STATUS) {
                status("Sending…", "Your draft is kept. Sending again is disabled.", this::showDraft);
            }
        }, (value, error) -> {
            if (closed) return;
            if (submitted.phase() == ReplyDraft.Phase.SENT && value != null) {
                safe(() -> {
                    if (posts.isEmpty() || !submitted.target.postId.equals(NativeCalls.text(posts.get(postIndex), "getKindWithId"))) return;
                    int parent = -1;
                    for (int i = 0; i < comments.size(); i++) {
                        if (submitted.target.parentId.equals(NativeCalls.text(comments.get(i), "getKindWithId"))) parent = i;
                    }
                    comments.add(parent + 1, value);
                    commentIndex = parent + 1;
                    readingPage = 0;
                });
            }
            if (draft == submitted && (screen == Screen.PREVIEW || screen == Screen.DRAFT || screen == Screen.STATUS)) {
                if (error != null && submitted.phase() == ReplyDraft.Phase.PREVIEW) {
                    status("Could not check parent", "Your draft is kept. Review and send again.", this::preview);
                } else showDraft();
            }
        });
        if (replies.isValidating() || draft.phase() == ReplyDraft.Phase.SENDING) {
            status("Checking destination…", "No duplicate send.", this::showDraft);
        }
    }

    private void reconcile() {
        ReplyDraft checked = draft;
        threads.load(checked.target.postId, null, "NEW", (value, error) -> {
            if (closed || draft != checked) return;
            if (error != null) { showDraft(); return; }
            safe(() -> {
                List<Object> refreshed = new ArrayList<>();
                NativeComments.flatten((List<?>) NativeCalls.read(value, "getComments"), refreshed);
                List<Object> matching = new ArrayList<>();
                for (Object item : refreshed) {
                    if (checked.target.parentId.equals(NativeCalls.text(item, "getParentKindWithId"))
                            && checked.target.postId.equals(NativeCalls.text(item, "getLinkKindWithId"))
                            && checked.target.accountId.equalsIgnoreCase(NativeCalls.text(item, "getAuthor"))
                            && checked.text().equals(NativeCalls.text(item, "getBody"))) matching.add(item);
                }
                if (matching.size() == 1) {
                    Object sent = matching.get(0);
                    checked.reconcile(NativeCalls.text(sent, "getKindWithId"), NativeCalls.text(sent, "getParentKindWithId"),
                            NativeCalls.text(sent, "getLinkKindWithId"));
                }
                comments.clear();
                comments.addAll(refreshed);
                showDraft();
            });
        });
    }

    private void sortComments() {
        screen = Screen.ACTIONS;
        actionOrigin = Screen.COMMENTS;
        List<HudView.Row> rows = new ArrayList<>();
        for (String sort : new String[]{"CONFIDENCE", "NEW", "TOP", "OLD", "CONTROVERSIAL", "QA"}) {
            rows.add(row(sort, sort.equals("CONFIDENCE") ? "Best" : sort, "", () -> {
                commentSort = sort;
                commentIndex = 0;
                loadComments();
            }));
        }
        rows.add(row("back", "Back", "", this::commentList));
        hud.show("COMMENT SORT", "", rows, commentSort);
    }

    private void settings() {
        screen = Screen.ACTIONS;
        actionOrigin = Screen.MENU;
        List<HudView.Row> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            float scale = new float[]{1f, 1.2f, 1.4f}[i];
            rows.add(row("scale-" + i, new String[]{"Standard text", "Large text", "Extra large text"}[i], "", () -> {
                activity.getSharedPreferences("rokid_hud", Activity.MODE_PRIVATE).edit().putFloat("text_scale", scale).apply();
                settings();
            }));
        }
        rows.add(row("reddit", "Reddit preferences", "Current account values", secondary::preferences));
        rows.add(row("back", "Back", "", this::menu));
        hud.show("READABILITY", "", rows, "scale-0");
    }

    private void nativeRoute(String title, String path) {
        if ("/login/".equals(path)) {
            nativeAuth();
            return;
        }
        screen = Screen.NATIVE;
        nativeAuthRequested = false;
        nativeAuthOpened = false;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com" + path));
        intent.setPackage(activity.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            activity.startActivity(intent);
            nativeSurface.open(title, this::menu);
        } catch (RuntimeException error) {
            status("Screen unavailable", "Return to the menu.", this::menu);
        }
    }

    private void nativeAuth() {
        try {
            Parcelable login = (Parcelable) calls.constant("cr.f1", "a");
            Parcelable signup = (Parcelable) calls.constant("cr.v1", "a");
            Intent intent = new Intent();
            intent.setClassName(activity, "com.reddit.auth.login.screen.AuthActivityKt");
            intent.putExtra("com.reddit.login", login);
            intent.putExtra("com.reddit.signup", signup);
            intent.putExtra("com.reddit.force_incognito_after_auth", false);
            intent.putExtra("com.reddit.should_hide_sso_section", false);
            nativeSurface.close();
            screen = Screen.NATIVE;
            nativeAuthRequested = true;
            nativeAuthOpened = false;
            // The stock navigator uses request 42; its activity result observer owns account switching.
            activity.startActivityForResult(intent, 42);
        } catch (ReflectiveOperationException | RuntimeException error) {
            nativeAuthRequested = false;
            nativeAuthOpened = false;
            status("Account screen unavailable", "Return to the menu.", this::menu);
        }
    }

    private void openAccountEntry(NativeAccount.AccountEntry entry, boolean comment, Runnable origin) {
        checkFeedAccount();
        rememberFeed();
        secondary.prepareThread(origin);
        long operation = ++request;
        String owner = account();
        loading = true;
        screen = Screen.SECONDARY;
        hud.show("Loading thread\u2026", "", Arrays.asList(row("back", "Back", "", origin)), "back");
        feeds.post(entry.postId, (value, error) -> {
            if (closed || operation != request) return;
            if (!owner.equals(account()) || !owner.equals(entry.accountId)) { checkFeedAccount(); menu(); return; }
            loading = false;
            NativeFeed.Page page = error == null ? (NativeFeed.Page) value : null;
            if (page == null || page.posts.isEmpty()) {
                hud.show("Thread unavailable", "The list is kept.", Arrays.asList(
                        row("retry", "Retry", "", () -> openAccountEntry(entry, comment, origin)),
                        row("back", "Back", "", origin)), "retry");
                return;
            }
            Object post = page.posts.get(0);
            posts.clear();
            posts.add(post);
            comments.clear();
            collapsed.clear();
            next = null;
            postIndex = commentIndex = readingPage = postPage = feedScroll = 0;
            postRoute = new PostRoute("thread:" + entry.postId, "Thread", (cursor, size, cb) -> feeds.post(entry.postId, cb), origin);
            postBack = origin;
            if (!comment) { readPost(true); return; }
            loading = true;
            accountRoutes.readComment(entry, (nativeComment, failure) -> {
                if (closed || operation != request) return;
                loading = false;
                if (!owner.equals(account())) { checkFeedAccount(); menu(); return; }
                if (failure != null) {
                    NativeCalls.logFailure("RedditGlassesRoutes", failure);
                    hud.show("Comment unavailable", "The list is kept.", Arrays.asList(
                            row("retry", "Retry", "", () -> openAccountEntry(entry, true, origin)),
                            row("back", "Back", "", origin)), "retry");
                    return;
                }
                comments.add(nativeComment);
                commentBack = origin;
                comment();
            });
        });
    }

    private void openPostNative(Object post) {
        safe(() -> nativeRoute("POST", NativeCalls.text(post, "getPermalink")));
    }

    private void openCommentNative(Object comment) {
        safe(() -> {
            Object post = posts.get(postIndex);
            String id = NativeCalls.text(comment, "getKindWithId");
            if (!id.matches("t1_[a-z0-9]+") || !NativeCalls.text(post, "getKindWithId")
                    .equals(NativeCalls.text(comment, "getLinkKindWithId"))) {
                throw new IllegalStateException("Comment destination changed");
            }
            String path = NativeCalls.text(post, "getPermalink");
            nativeRoute("COMMENT", path + (path.endsWith("/") ? "" : "/") + id.substring(3) + "/");
        });
    }

    private void mutationResult(Object value, Throwable error) {
        if (closed) return;
        if (error == null) status("Action confirmed", "", this::returnToPost);
        else status("Action not confirmed", "Check the thread before trying again.", this::returnToPost);
    }

    private void returnToPost() {
        request++;
        loading = false;
        closeMedia();
        screen = Screen.POST;
        readPost(true);
    }

    private void exit() {
        close();
        activity.finishAndRemoveTask();
    }

    private void back() {
        if (nativeSurface.back()) {
            if (!nativeSurface.isActive()) screen = Screen.MENU;
            return;
        }
        request++;
        loading = false;
        switch (screen) {
            case MENU: exit(); break;
            case FEED: feedBack(); break;
            case POST: postBack.run(); break;
            case COMMENT: commentBack.run(); break;
            case SECONDARY: secondary.back(); break;
            case MEDIA: returnToPost(); break;
            case COMMENTS: returnToPost(); break;
            case ACTIONS:
                if (actionOrigin == Screen.COMMENT) comment();
                else if (actionOrigin == Screen.COMMENTS) commentList();
                else if (actionOrigin == Screen.MENU) menu();
                else if (actionOrigin == Screen.MEDIA) showMedia();
                else if (actionOrigin == Screen.FEED) feed();
                else returnToPost();
                break;
            case DRAFT: case PREVIEW: returnFromDraft(); break;
            default: menu(); break;
        }
    }

    private void status(String title, String detail, Runnable nextAction) {
        screen = Screen.STATUS;
        hud.show(title, detail, Arrays.asList(row("continue", "Continue", "", nextAction),
                row("menu", "Menu", "", this::menu)), "continue");
    }

    private String account() {
        try { return NativeBindings.account(); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return ""; }
    }

    private void viewMedia(Object post) {
        safe(() -> {
            closeMedia();
            mediaOpenedAt = SystemClock.uptimeMillis();
            MediaView opened = new MediaView(activity, post);
            media = opened;
            opened.setOnPositionChanged(() -> {
                if (!closed && screen == Screen.MEDIA && media == opened) {
                    hud.updateMediaTitle(opened.position());
                    hud.updateMediaHint(opened.navigationHint());
                }
            });
            showMedia();
        });
    }

    private void showMedia() {
        screen = Screen.MEDIA;
        hud.media(media.position(), "", media, this::mediaActions);
        hud.updateMediaHint(media.navigationHint());
    }

    private void mediaActions() {
        screen = Screen.ACTIONS;
        actionOrigin = Screen.MEDIA;
        List<HudView.Row> rows = new ArrayList<>();
        if (media.isUnavailable()) {
            rows.add(row("retry", "Retry media", "", () -> { media.retry(); showMedia(); }));
        }
        if (media.isVideo() || media.isAnimated()) {
            rows.add(row("play", media.isPlaying() ? "Pause" : "Play", "", () -> { media.togglePlayback(); showMedia(); }));
        }
        if (media.canMute()) {
            rows.add(row("mute", media.isMuted() ? "Unmute" : "Mute", "", () -> { media.toggleMute(); showMedia(); }));
        }
        if (media.isVideo() || media.isAnimated()) {
            rows.add(row("replay", "Replay", "", () -> { media.replay(); showMedia(); }));
        }
        if (media.isVideo()) {
            rows.add(row("ahead", "Forward 10 seconds", "", () -> { media.seek(10); showMedia(); }));
            rows.add(row("behind", "Back 10 seconds", "", () -> { media.seek(-10); showMedia(); }));
        }
        if (media.itemCount() > 1) {
            rows.add(row("next", "Next image", "", () -> { media.step(1); showMedia(); }));
            rows.add(row("previous", "Previous image", "", () -> { media.step(-1); showMedia(); }));
        }
        rows.add(row("view", "Back to media", "", this::showMedia));
        rows.add(row("post", "Back to post", "", this::returnToPost));
        rows.add(row("comments", "Comments", "", () -> { closeMedia(); loadComments(); }));
        rows.add(row("reply", "Reply to post", "Review the exact destination", () -> {
            closeMedia(); compose(posts.get(postIndex), false);
        }));
        safe(() -> {
            Object post = posts.get(postIndex);
            Boolean vote = (Boolean) NativeCalls.read(post, "getVoteState");
            boolean saved = Boolean.TRUE.equals(NativeCalls.read(post, "getSaved"));
            rows.add(row("up", Boolean.TRUE.equals(vote) ? "Remove upvote" : "Upvote", "", () ->
                    actions.vote(post, false, Boolean.TRUE.equals(vote) ? "NONE" : "UP", this::mutationResult)));
            rows.add(row("down", Boolean.FALSE.equals(vote) ? "Remove downvote" : "Downvote", "", () ->
                    actions.vote(post, false, Boolean.FALSE.equals(vote) ? "NONE" : "DOWN", this::mutationResult)));
            rows.add(row("save", saved ? "Unsave" : "Save", "", () -> actions.savePost(post, !saved, this::mutationResult)));
            media.prepareForDetach();
            hud.show("MEDIA ACTIONS", media.position(), rows, media.isUnavailable() ? "retry" : media.isVideo() || media.isAnimated() ? "play" : "post");
        });
    }

    private void closeMedia() {
        if (media != null) { media.close(); media = null; }
    }

    public void pauseMedia() {
        if (media != null) media.pause();
    }

    private void openSentReply() {
        ReplyDraft sent = draft;
        safe(() -> {
            Object repository = NativeBindings.service("com.reddit.link.impl.data.repository.l");
            calls.invoke(repository, repository.getClass().getMethod("v", String.class, calls.continuationType()),
                    new Object[]{sent.target.postId}, (value, error) -> {
                        if (closed) return;
                        if (error != null) { showDraft(); return; }
                        safe(() -> {
                            Object post = NativeCalls.success(value);
                            if (!sent.target.postId.equals(NativeCalls.text(post, "getKindWithId"))) throw new IllegalStateException("Post changed");
                            posts.add(post);
                            postIndex = posts.size() - 1;
                            threads.load(sent.target.postId, null, "NEW", (result, failure) -> {
                                if (closed) return;
                                if (failure != null) { showDraft(); return; }
                                safe(() -> {
                                    comments.clear();
                                    collapsed.clear();
                                    commentIndex = 0;
                                    NativeComments.flatten((List<?>) NativeCalls.read(result, "getComments"), comments);
                                    for (int i = 0; i < comments.size(); i++) {
                                        if (sent.sentId().equals(NativeCalls.text(comments.get(i), "getKindWithId"))) commentIndex = i;
                                    }
                                    readingPage = 0;
                                    commentList();
                                });
                            });
                        });
                    });
        });
    }

    private interface CheckedAction { void run() throws ReflectiveOperationException; }
    private void safe(CheckedAction action) {
        try { action.run(); }
        catch (ReflectiveOperationException | RuntimeException error) {
            if (!closed) status("Content unavailable", "Return to the menu or retry.", this::menu);
        }
    }

    private static HudView.Row row(String id, String title, String detail, Runnable action) {
        return new HudView.Row(id, title, detail, action);
    }
    private static String clip(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
}
