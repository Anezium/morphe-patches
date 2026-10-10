/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/** Calls only the inspected native coroutine ABI. No session token crosses this bridge. */
public final class NativeCalls {
    public interface Callback {
        void complete(Object value, Throwable error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final ClassLoader loader;
    private final Class<?> continuation;
    private final Class<?> continuationImpl;
    private final Object emptyContext;
    private final Object suspended;

    public NativeCalls(ClassLoader loader) throws ReflectiveOperationException {
        this.loader = loader;
        continuation = type("ul3.a");
        continuationImpl = type("kotlin.coroutines.jvm.internal.ContinuationImpl");
        emptyContext = type("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
        suspended = type("kotlin.coroutines.intrinsics.CoroutineSingletons")
                .getField("COROUTINE_SUSPENDED").get(null);
        if (!continuationImpl.isAssignableFrom(NativeContinuation.class)) {
            throw new IllegalStateException("Reddit coroutine binding is not installed");
        }
    }

    public Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    public Class<?> continuationType() { return continuation; }
    public Class<?> continuationImplType() { return continuationImpl; }

    public Object constant(String className, String field) throws ReflectiveOperationException {
        return type(className).getField(field).get(null);
    }

    public void invoke(Object owner, Method method, Object[] arguments, Callback callback) {
        AtomicBoolean completed = new AtomicBoolean();
        Callback once = (value, error) -> {
            if (completed.compareAndSet(false, true)) {
                MAIN.post(() -> callback.complete(value, error));
            }
        };
        try {
            Object completion = Proxy.newProxyInstance(loader, new Class<?>[]{continuation},
                    (proxy, called, args) -> {
                        switch (called.getName()) {
                            case "getContext": return emptyContext;
                            case "resumeWith":
                                deliver(args[0], once);
                                return null;
                            case "equals": return proxy == args[0];
                            case "hashCode": return System.identityHashCode(proxy);
                            case "toString": return "Reddit glasses completion";
                            default: throw new UnsupportedOperationException(called.getName());
                        }
                    });
            Object[] actual = new Object[arguments.length + 1];
            System.arraycopy(arguments, 0, actual, 0, arguments.length);
            Class<?> expected = method.getParameterTypes()[arguments.length];
            actual[arguments.length] = expected.getName().equals("kotlin.coroutines.jvm.internal.SuspendLambda")
                    ? new NativeSuspend(completion) : new NativeContinuation(completion);
            Object result = method.invoke(owner, actual);
            if (result != suspended) deliver(result, once);
        } catch (InvocationTargetException error) {
            once.complete(null, error.getCause());
        } catch (ReflectiveOperationException | RuntimeException error) {
            once.complete(null, error);
        }
    }

    public void collect(Object flow, Callback onValue, Callback onComplete) throws ReflectiveOperationException {
        Class<?> collectorType = type("kotlinx.coroutines.flow.l");
        Object unit = constant("kotlin.Unit", "a");
        Object collector = Proxy.newProxyInstance(loader, new Class<?>[]{collectorType},
                (proxy, called, args) -> {
                    if (called.getName().equals("emit")) {
                        Object value = args[0];
                        MAIN.post(() -> onValue.complete(value, null));
                        return unit;
                    }
                    if (called.getName().equals("equals")) return proxy == args[0];
                    if (called.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (called.getName().equals("toString")) return "Reddit glasses collector";
                    throw new UnsupportedOperationException(called.getName());
                });
        Method collect = type("kotlinx.coroutines.flow.k").getMethod("d", collectorType, continuation);
        invoke(flow, collect, new Object[]{collector}, onComplete);
    }

    private void deliver(Object value, Callback callback) {
        try {
            if (value != null && value.getClass().getName().equals("kotlin.Result$Failure")) {
                callback.complete(null, (Throwable) value.getClass().getField("exception").get(value));
            } else {
                callback.complete(value, null);
            }
        } catch (ReflectiveOperationException error) {
            callback.complete(null, error);
        }
    }

    public static void logFailure(String tag, Throwable failure) {
        StringBuilder detail = new StringBuilder();
        for (int depth = 0; failure != null && depth < 4; depth++, failure = failure.getCause()) {
            detail.append(failure.getClass().getName());
            StackTraceElement[] frames = failure.getStackTrace();
            for (int i = 0; i < Math.min(4, frames.length); i++) detail.append(" at ").append(frames[i]);
            detail.append("; ");
        }
        // Only class and method locations are logged, never exception messages or native values.
        android.util.Log.w(tag, detail.toString());
    }

    public static Object success(Object result) throws ReflectiveOperationException {
        if (result != null && result.getClass().getName().equals("hx.b")) {
            Object cause = read(result, "a");
            if (cause instanceof Throwable) throw new IllegalStateException("Native request failed", (Throwable) cause);
        }
        if (result == null || !result.getClass().getName().equals("hx.g")) {
            throw new IllegalStateException("Native request did not confirm success");
        }
        return result.getClass().getMethod("a").invoke(result);
    }

    public static Object read(Object owner, String getter) throws ReflectiveOperationException {
        if (owner == null) throw new IllegalStateException("Native object unavailable");
        return owner.getClass().getMethod(getter).invoke(owner);
    }

    public static String text(Object owner, String getter) throws ReflectiveOperationException {
        Object value = read(owner, getter);
        return value == null ? "" : value.toString();
    }
}
