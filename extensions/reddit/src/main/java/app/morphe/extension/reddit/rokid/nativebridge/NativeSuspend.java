/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

/** Binding for native methods whose R8 signature narrowed the continuation to SuspendLambda. */
public final class NativeSuspend {
    public NativeSuspend(Object completion) {
        throw new IllegalStateException("Native suspend binding missing");
    }
    public Object invokeSuspend(Object result) { return result; }
}
