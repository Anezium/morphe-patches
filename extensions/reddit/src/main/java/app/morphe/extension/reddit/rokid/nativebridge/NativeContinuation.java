/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

/** The patch binds this class to the stock APK's R8-renamed coroutine ABI. */
public final class NativeContinuation {
    public NativeContinuation(Object completion) {
        throw new IllegalStateException("Native continuation binding missing");
    }

    public Object invokeSuspend(Object result) {
        return result;
    }
}
