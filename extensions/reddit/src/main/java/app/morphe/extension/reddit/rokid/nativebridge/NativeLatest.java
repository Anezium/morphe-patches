/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The inspected Reddit 2026.14.0 Latest factory without a stock screen. */
public final class NativeLatest {
    public static final String SERVICE = "com.reddit.feeds.latest.impl.data.a";

    private NativeLatest() { }

    /** The caller must validate this account graph against the active session before and after creation. */
    public static Object create(Object validatedAccountGraph) throws ReflectiveOperationException {
        if (validatedAccountGraph == null) throw new IllegalStateException("Native account graph is unavailable");
        ClassLoader loader = validatedAccountGraph.getClass().getClassLoader();
        Class<?> accountType = type(loader, "hc1.t1");
        if (validatedAccountGraph.getClass() != accountType) throw new IllegalStateException("Unexpected native account graph");
        Class<?> appType = type(loader, "hc1.u0");
        Object app = field(validatedAccountGraph, "c", appType);
        if (app == null || app.getClass() != appType) throw new IllegalStateException("Unexpected native app graph");

        Class<?> listingType = type(loader, "com.reddit.listing.repository.a");
        Object listing = provider(validatedAccountGraph, "P4", type(loader, "cl3.c"), listingType);
        Class<?> correlationType = type(loader, "ek1.a");
        Object correlation = correlationType.getConstructor().newInstance();
        Class<?> layoutType = type(loader, "com.reddit.feeds.latest.impl.ui.b");
        Object layout = layoutType.getConstructor(listingType).newInstance(listing);
        Class<?> scopeType = type(loader, "eo.a");
        Object scope = type(loader, "eo.d").getConstructor(String.class).newInstance("latest");
        if (!scopeType.isInstance(scope)) throw new IllegalStateException("Unexpected native analytics scope");
        Class<?> eventType = type(loader, "com.reddit.eventkit.b");
        Object events = provider(validatedAccountGraph, "k", type(loader, "cl3.a"), eventType);
        Class<?> analyticsType = type(loader, "com.reddit.feeds.impl.analytics.e");
        Object analytics = analyticsType.getConstructor(scopeType, eventType, listingType).newInstance(scope, events, listing);
        Class<?> unitsType = type(loader, "qt3.h");
        Object units = unitsType.getConstructor().newInstance();
        Class<?> feedType = type(loader, "com.reddit.feeds.data.FeedType");
        if (!feedType.isEnum()) throw new IllegalStateException("Unexpected native feed type");
        Object latest = feedType.getField("LATEST").get(null);
        if (latest == null || latest.getClass() != feedType) throw new IllegalStateException("Native Latest scope is unavailable");

        // hc1.d1.f0 uses this public constructor with these account/app graph dependencies.
        // It has no synthetic flags; the native Latest scope is eo.d("latest") and FeedType.LATEST.
        Class<?>[] signature = new Class<?>[]{
                type(loader, "com.reddit.feeds.impl.domain.paging.d"),
                type(loader, "com.reddit.graphql.d0"), type(loader, "zl1.e"),
                type(loader, "a81.d"), type(loader, "zk1.f"), correlationType, layoutType,
                type(loader, "com.reddit.ads.impl.sessionslots.b"), type(loader, "uj.a"),
                type(loader, "ix1.c"), analyticsType, type(loader, "d62.a"),
                type(loader, "com.reddit.graphql.b1"), feedType, unitsType
        };
        Class<?> cachedProvider = type(loader, "cl3.c");
        Object[] arguments = new Object[]{
                read(validatedAccountGraph, "v1", signature[0]),
                provider(validatedAccountGraph, "C", type(loader, "cl3.a"), signature[1]),
                provider(validatedAccountGraph, "f9", cachedProvider, signature[2]),
                provider(validatedAccountGraph, "j2", cachedProvider, signature[3]),
                provider(validatedAccountGraph, "C0", cachedProvider, signature[4]),
                correlation, layout,
                provider(validatedAccountGraph, "T4", cachedProvider, signature[7]),
                provider(validatedAccountGraph, "S4", cachedProvider, signature[8]),
                provider(app, "e", type(loader, "hc1.t0"), signature[9]), analytics,
                provider(validatedAccountGraph, "c4", cachedProvider, signature[11]),
                read(validatedAccountGraph, "x2", signature[12]), latest, units
        };
        return type(loader, SERVICE).getConstructor(signature).newInstance(arguments);
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static Object field(Object owner, String name, Class<?> expected) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        if (field.getType() != expected) throw new IllegalStateException("Unexpected native Latest graph field");
        return field.get(owner);
    }

    private static Object provider(Object owner, String name, Class<?> providerType, Class<?> expected)
            throws ReflectiveOperationException {
        Object provider = field(owner, name, providerType);
        if (provider == null) throw new IllegalStateException("Native Latest dependency is unavailable");
        Method get = providerType.getMethod("get");
        if (get.getReturnType() != Object.class) throw new IllegalStateException("Unexpected native Latest provider");
        Object result = get.invoke(provider);
        if (!expected.isInstance(result)) throw new IllegalStateException("Unexpected native Latest dependency");
        return result;
    }

    private static Object read(Object graph, String name, Class<?> expected) throws ReflectiveOperationException {
        Method getter = graph.getClass().getMethod(name);
        if (getter.getReturnType() != expected) throw new IllegalStateException("Unexpected native Latest graph method");
        Object result = getter.invoke(graph);
        if (!expected.isInstance(result)) throw new IllegalStateException("Native Latest dependency is unavailable");
        return result;
    }
}
