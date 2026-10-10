/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid;

import app.morphe.extension.reddit.rokid.nativebridge.NativeCalls;

/** A native post listing with an explicit return destination. */
final class PostRoute {
    interface Loader { void load(String cursor, int size, NativeCalls.Callback callback); }
    final String key;
    final String title;
    final Loader loader;
    final Runnable back;
    PostRoute(String key, String title, Loader loader, Runnable back) {
        this.key = key;
        this.title = title;
        this.loader = loader;
        this.back = back;
    }
}
