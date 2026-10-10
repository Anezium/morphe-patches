/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Constructor hooks retain native service ownership; the official app owns authentication. */
public final class NativeBindings {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Object> SERVICES = new HashMap<>();
    private static final List<WeakReference<Object>> ACCOUNT_GRAPHS = new ArrayList<>();
    private static final java.util.Set<Runnable> OBSERVERS = new java.util.LinkedHashSet<>();

    private static Object latestGraph;
    private static Object latestOwner;
    private static Object popularGraph;
    private static Object popularOwner;

    public static void resetLatest() { latestGraph = null; latestOwner = null; }

    private static Object latest(Object graph) throws ReflectiveOperationException {
        if (latestGraph != graph || latestOwner == null) {
            latestOwner = NativeLatest.create(graph);
            latestGraph = graph;
        }
        return latestOwner;
    }

    public static void capture(Object service) {
        if (service == null) return;
        synchronized (SERVICES) {
            SERVICES.put(service.getClass().getName(), service);
            if (service.getClass().getName().equals("hc1.t1")) {
                ACCOUNT_GRAPHS.removeIf(reference -> reference.get() == null || reference.get() == service);
                ACCOUNT_GRAPHS.add(new WeakReference<>(service));
            }
        }
        MAIN.post(() -> {
            for (Runnable listener : new java.util.ArrayList<>(OBSERVERS)) listener.run();
        });
    }

    public static Runnable observe(Runnable listener) {
        OBSERVERS.add(listener);
        return () -> OBSERVERS.remove(listener);
    }

    public static Object service(String name) {
        String provider;
        switch (name) {
            case "com.reddit.feeds.latest.impl.data.a": provider = "#latest"; break;
            case NativePopular.SERVICE: provider = "#popular"; break;
            case "com.reddit.search.remote.c": provider = "ii"; break;
            case "com.reddit.data.remote.p": provider = "Gb"; break;
            case "com.reddit.screens.listing.compose.usecase.a": provider = "Yl"; break;
            case "com.reddit.graphql.d0": provider = "C"; break;
            case "com.reddit.comment.data.repository.b": provider = "V9"; break;
            case "com.reddit.link.impl.data.repository.l": provider = "E6"; break;
            case "com.reddit.data.remote.m": provider = "@T2"; break;
            case "com.reddit.notification.impl.data.remote.g": provider = "@y3"; break;
            case "com.reddit.comment.data.datasource.c": provider = "@u3"; break;
            default: provider = null;
        }
        if (provider != null) {
            try {
                Object graph = accountGraph();
                Object current = currentSession();
                Object value;
                if (provider.equals("#latest")) value = latest(graph);
                else if (provider.equals("#popular")) {
                    value = popularGraph == graph && popularOwner != null
                            ? popularOwner : NativePopular.create(graph);
                } else value = provider.startsWith("@")
                        ? graph.getClass().getMethod(provider.substring(1)).invoke(graph)
                        : NativeCalls.read(graph.getClass().getField(provider).get(graph), "get");
                if (!sameSession(current, currentSession()) || !graphMatches(graph, current)) {
                    throw new IllegalStateException("Native account changed");
                }
                if (!Class.forName(name, false, graph.getClass().getClassLoader()).isInstance(value)) {
                    throw new IllegalStateException("Native service type changed");
                }
                if (provider.equals("#popular")) {
                    popularGraph = graph;
                    popularOwner = value;
                }
                return value;
            } catch (ReflectiveOperationException unavailable) {
                throw new IllegalStateException("Native service is unavailable", unavailable);
            }
        }
        synchronized (SERVICES) {
            Object value = SERVICES.get(name);
            if (value == null) throw new IllegalStateException("Native service is not initialized");
            return value;
        }
    }

    public static Object accountGraph() throws ReflectiveOperationException {
        Object session = currentSession();
        List<WeakReference<Object>> candidates;
        synchronized (SERVICES) { candidates = new ArrayList<>(ACCOUNT_GRAPHS); }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            Object graph = candidates.get(i).get();
            if (graph != null && graphMatches(graph, session)) return graph;
        }
        throw new IllegalStateException("Native account graph is not initialized");
    }

    private static Object currentSession() throws ReflectiveOperationException {
        synchronized (SERVICES) {
            Object owner = SERVICES.get("com.reddit.session.o");
            if (owner == null) throw new IllegalStateException("Native session is not initialized");
            return NativeCalls.read(owner, "t");
        }
    }

    private static boolean graphMatches(Object graph, Object session) throws ReflectiveOperationException {
        Object provider = graph.getClass().getField("h").get(graph);
        return sameSession(NativeCalls.read(provider, "get"), session);
    }

    private static boolean sameSession(Object first, Object second) throws ReflectiveOperationException {
        if (first == null || second == null
                || Boolean.TRUE.equals(NativeCalls.read(first, "isTokenInvalid"))
                || Boolean.TRUE.equals(NativeCalls.read(second, "isTokenInvalid"))) return false;
        if (first == second) return true;
        Object firstId = NativeCalls.read(first, "getId");
        return firstId != null && Objects.equals(firstId, NativeCalls.read(second, "getId"))
                && Objects.equals(NativeCalls.read(first, "isLoggedIn"), NativeCalls.read(second, "isLoggedIn"))
                && NativeCalls.text(first, "getUsername").equalsIgnoreCase(NativeCalls.text(second, "getUsername"));
    }

    public static boolean ready(String name) {
        synchronized (SERVICES) { return SERVICES.containsKey(name); }
    }

    public static String account() throws ReflectiveOperationException {
        Object session = NativeCalls.read(service("com.reddit.session.o"), "t");
        if (!Boolean.TRUE.equals(NativeCalls.read(session, "isLoggedIn"))
                || Boolean.TRUE.equals(NativeCalls.read(session, "isTokenInvalid"))) return "";
        String username = NativeCalls.text(session, "getUsername");
        return username.isEmpty() ? "" : username.toLowerCase(Locale.ROOT);
    }
}
