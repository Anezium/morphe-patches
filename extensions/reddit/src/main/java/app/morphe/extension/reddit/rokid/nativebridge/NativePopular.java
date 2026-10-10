/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The inspected Reddit 2026.14.0 Popular factory without a stock screen. */
public final class NativePopular {
    public static final String SERVICE = "com.reddit.feeds.popular.impl.data.paging.a";

    private NativePopular() { }

    /** The caller must validate this account graph against the active session before and after creation. */
    public static Object create(Object validatedAccountGraph) throws ReflectiveOperationException {
        if (validatedAccountGraph == null) throw new IllegalStateException("Native account graph is unavailable");
        ClassLoader loader = validatedAccountGraph.getClass().getClassLoader();
        Class<?> accountType = type(loader, "hc1.t1");
        if (validatedAccountGraph.getClass() != accountType) throw new IllegalStateException("Unexpected native account graph");
        Class<?> appType = type(loader, "hc1.u0");
        Object app = field(validatedAccountGraph, "c", appType);
        if (app == null || app.getClass() != appType) throw new IllegalStateException("Unexpected native app graph");

        Class<?> cachedProvider = type(loader, "cl3.c");
        Class<?> listingType = type(loader, "com.reddit.listing.repository.a");
        Object listing = provider(validatedAccountGraph, "P4", cachedProvider, listingType);
        Class<?> scopeType = type(loader, "eo.a");
        Object scope = type(loader, "eo.d").getConstructor(String.class).newInstance("popular");
        if (!scopeType.isInstance(scope)) throw new IllegalStateException("Unexpected native analytics scope");
        Class<?> sessionsType = type(loader, "uj.a");
        Object sessions = provider(validatedAccountGraph, "S4", cachedProvider, sessionsType);
        Class<?> settingsType = type(loader, "zk1.f");
        Object settings = provider(validatedAccountGraph, "C0", cachedProvider, settingsType);

        Class<?> trendingType = type(loader, "com.reddit.feeds.popular.impl.data.b");
        Class<?>[] trendingSignature = new Class<?>[]{
                type(loader, "bx.b"), type(loader, "q71.c"), scopeType, sessionsType,
                type(loader, "ia3.d"), type(loader, "ia3.b"), type(loader, "ia3.a"),
                type(loader, "ul.a"), settingsType
        };
        Object trending = trendingType.getConstructor(trendingSignature).newInstance(
                provider(app, "c", type(loader, "hc1.t0"), trendingSignature[0]),
                provider(validatedAccountGraph, "ii", cachedProvider, trendingSignature[1]),
                scope, sessions,
                provider(validatedAccountGraph, "Kg", cachedProvider, trendingSignature[4]),
                provider(validatedAccountGraph, "da", cachedProvider, trendingSignature[5]),
                provider(validatedAccountGraph, "ea", cachedProvider, trendingSignature[6]),
                provider(validatedAccountGraph, "h6", cachedProvider, trendingSignature[7]), settings);
        Class<?> eventType = type(loader, "com.reddit.eventkit.b");
        Object events = provider(validatedAccountGraph, "k", type(loader, "cl3.a"), eventType);
        Class<?> analyticsType = type(loader, "com.reddit.feeds.impl.analytics.e");
        Object analytics = analyticsType.getConstructor(scopeType, eventType, listingType).newInstance(scope, events, listing);
        Class<?> correlationType = type(loader, "ek1.a");
        Object correlation = correlationType.getConstructor().newInstance();
        Class<?> unitsType = type(loader, "qt3.h");
        Object units = unitsType.getConstructor().newInstance();
        Class<?> feedType = type(loader, "com.reddit.feeds.data.FeedType");
        if (!feedType.isEnum()) throw new IllegalStateException("Unexpected native feed type");
        Object popular = feedType.getField("POPULAR").get(null);
        if (popular == null || popular.getClass() != feedType) throw new IllegalStateException("Native Popular scope is unavailable");

        // hc1.a1.L2 uses this constructor; its scope is eo.d("popular").
        // Reuse the resulting owner for pagination, which retains native correlation state.
        Class<?>[] signature = new Class<?>[]{
                type(loader, "com.reddit.feeds.impl.domain.paging.d"),
                type(loader, "com.reddit.graphql.d0"), type(loader, "zl1.e"),
                type(loader, "zl1.g"), type(loader, "jn1.a"), type(loader, "ek1.h"),
                listingType, correlationType, settingsType, type(loader, "ix1.c"),
                trendingType, analyticsType, type(loader, "com.reddit.ads.impl.sessionslots.b"),
                sessionsType, type(loader, "com.reddit.graphql.b1"), feedType, unitsType
        };
        Object[] arguments = new Object[]{
                read(validatedAccountGraph, "v1", signature[0]),
                provider(validatedAccountGraph, "C", type(loader, "cl3.a"), signature[1]),
                provider(validatedAccountGraph, "f9", cachedProvider, signature[2]),
                provider(validatedAccountGraph, "n9", cachedProvider, signature[3]),
                provider(validatedAccountGraph, "o9", cachedProvider, signature[4]),
                provider(validatedAccountGraph, "p9", cachedProvider, signature[5]),
                listing, correlation, settings,
                provider(app, "e", type(loader, "hc1.t0"), signature[9]), trending, analytics,
                provider(validatedAccountGraph, "T4", cachedProvider, signature[12]), sessions,
                read(validatedAccountGraph, "x2", signature[14]), popular, units
        };
        return type(loader, SERVICE).getConstructor(signature).newInstance(arguments);
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static Object field(Object owner, String name, Class<?> expected) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        if (field.getType() != expected) throw new IllegalStateException("Unexpected native Popular graph field");
        return field.get(owner);
    }

    private static Object provider(Object owner, String name, Class<?> providerType, Class<?> expected)
            throws ReflectiveOperationException {
        Object provider = field(owner, name, providerType);
        if (provider == null) throw new IllegalStateException("Native Popular dependency is unavailable");
        Method get = providerType.getMethod("get");
        if (get.getReturnType() != Object.class) throw new IllegalStateException("Unexpected native Popular provider");
        Object result = get.invoke(provider);
        if (!expected.isInstance(result)) throw new IllegalStateException("Unexpected native Popular dependency");
        return result;
    }

    private static Object read(Object graph, String name, Class<?> expected) throws ReflectiveOperationException {
        Method getter = graph.getClass().getMethod(name);
        if (getter.getReturnType() != expected) throw new IllegalStateException("Unexpected native Popular graph method");
        Object result = getter.invoke(graph);
        if (!expected.isInstance(result)) throw new IllegalStateException("Native Popular dependency is unavailable");
        return result;
    }
}
