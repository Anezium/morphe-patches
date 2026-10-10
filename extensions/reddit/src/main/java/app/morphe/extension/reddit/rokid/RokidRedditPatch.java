/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;

import app.morphe.extension.reddit.rokid.nativebridge.NativeBindings;
import app.morphe.extension.reddit.rokid.ui.NativeScreen;

import java.util.WeakHashMap;

/** Version-pinned bytecode entry points. Stock Reddit remains the native service owner. */
public final class RokidRedditPatch {
    private static final WeakHashMap<Activity, RedditController> CONTROLLERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, NativeScreen> NATIVE_SCREENS = new WeakHashMap<>();
    private static boolean registered;

    public static boolean isPatchIncluded() { return false; }

    public static void capture(Object service) {
        if (isPatchIncluded()) NativeBindings.capture(service);
    }

    public static void register(Application application) {
        if (!isPatchIncluded() || registered) return;
        registered = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { onCreate(activity); }
            @Override public void onActivityResumed(Activity activity) { onResume(activity); }
            @Override public void onActivityDestroyed(Activity activity) { onDestroy(activity); }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {
                RokidRingController.pause(activity);
                RedditController controller = CONTROLLERS.get(activity);
                if (controller != null) controller.pauseMedia();
            }
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    public static void onCreate(Activity activity) {
        if (!isPatchIncluded() || !activity.getClass().getName().startsWith("com.reddit.")) return;
        if (activity.getClass().getName().equals("com.reddit.auth.login.screen.AuthActivityKt")) {
            for (RedditController controller : CONTROLLERS.values()) controller.onNativeAuthOpened();
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        activity.getWindow().setStatusBarColor(Color.BLACK);
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().getDecorView().post(() -> attach(activity));
    }

    private static void attach(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || CONTROLLERS.containsKey(activity)
                || NATIVE_SCREENS.containsKey(activity)) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        try {
            if (activity.getClass().getName().equals("com.reddit.launch.main.MainActivity")) {
                CONTROLLERS.put(activity, new RedditController(activity, root));
            } else if (activity.getClass().getName().startsWith("com.reddit.")) {
                NATIVE_SCREENS.put(activity, new NativeScreen(activity, root));
            }
        }
        catch (ReflectiveOperationException | RuntimeException incompatible) {
            // An incompatible native ABI cannot expose a working-looking Send button.
            NATIVE_SCREENS.put(activity, new NativeScreen(activity, root));
        }
        RokidRingController.attached(activity);
    }

    public static void onResume(Activity activity) {
        if (!isPatchIncluded()) return;
        RokidRingController.resume(activity);
        RedditController controller = CONTROLLERS.get(activity);
        if (controller != null) controller.onResume();
        activity.getWindow().getDecorView().post(() -> attach(activity));
    }

    public static void onDestroy(Activity activity) {
        RokidRingController.destroy(activity);
        RedditController controller = CONTROLLERS.remove(activity);
        if (controller != null) controller.close();
        NativeScreen nativeScreen = NATIVE_SCREENS.remove(activity);
        if (nativeScreen != null) nativeScreen.close();
    }

    public static boolean handleKeyEvent(Activity activity, KeyEvent event) {
        if (!isPatchIncluded()) return false;
        if (RokidRingController.handle(activity, event)) return true;
        return dispatchControllerKey(activity, event);
    }

    static boolean hasController(Activity activity) {
        return CONTROLLERS.containsKey(activity) || NATIVE_SCREENS.containsKey(activity);
    }

    static boolean isEditing(Activity activity) {
        RedditController controller = CONTROLLERS.get(activity);
        if (controller != null) return controller.isEditing();
        NativeScreen screen = NATIVE_SCREENS.get(activity);
        return screen != null && screen.isEditing();
    }

    static boolean dispatchControllerKey(Activity activity, KeyEvent event) {
        RedditController controller = CONTROLLERS.get(activity);
        if (controller != null) return controller.handle(event);
        NativeScreen nativeScreen = NATIVE_SCREENS.get(activity);
        return nativeScreen != null && nativeScreen.handle(event);
    }
}
