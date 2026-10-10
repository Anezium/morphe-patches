/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;

import app.morphe.extension.reddit.rokid.core.RingInput;

import java.lang.ref.WeakReference;

/** Uses the existing YouTube/Nexus focus contract; no accessibility navigation fallback. */
final class RokidRingController {
    private static final String FOCUS_ACTION = "com.anezium.r08accessbridge.action.NEXUS_RING_FOCUS";
    private static final String BRIDGE_PACKAGE = "com.anezium.r08accessbridge";
    private static final long KEEPALIVE_MS = 60_000;
    private static final long REFRESH_MIN_MS = 5_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final RingInput INPUT = new RingInput();
    private static WeakReference<Activity> target = new WeakReference<>(null);
    private static boolean resumed;
    private static boolean claimed;
    private static long lastClaim;

    private static final Runnable RESOLVE = () -> {
        Activity activity = target.get();
        if (!foreground(activity)) {
            INPUT.reset();
            return;
        }
        dispatch(activity, INPUT.resolve(SystemClock.uptimeMillis(), RokidRedditPatch.isEditing(activity)));
    };

    private static final Runnable KEEPALIVE = new Runnable() {
        @Override public void run() {
            Activity activity = target.get();
            if (!foreground(activity)) {
                release();
                return;
            }
            publish(true);
            MAIN.postDelayed(this, KEEPALIVE_MS);
        }
    };

    private static final ViewTreeObserver.OnWindowFocusChangeListener FOCUS = hasFocus -> {
        if (hasFocus) refresh();
        else release();
    };

    static void resume(Activity activity) {
        if (target.get() != activity) {
            Activity old = target.get();
            release();
            if (old != null && old.getWindow().getDecorView().getViewTreeObserver().isAlive()) {
                old.getWindow().getDecorView().getViewTreeObserver().removeOnWindowFocusChangeListener(FOCUS);
            }
            target = new WeakReference<>(activity);
            activity.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(FOCUS);
        }
        resumed = true;
        refresh();
    }

    static void attached(Activity activity) {
        if (target.get() == activity) refresh();
    }

    static void pause(Activity activity) {
        if (target.get() != activity) return;
        resumed = false;
        release();
    }

    static void destroy(Activity activity) {
        if (target.get() != activity) return;
        pause(activity);
        if (activity.getWindow().getDecorView().getViewTreeObserver().isAlive()) {
            activity.getWindow().getDecorView().getViewTreeObserver().removeOnWindowFocusChangeListener(FOCUS);
        }
        target = new WeakReference<>(null);
    }

    static boolean handle(Activity activity, KeyEvent event) {
        if (!RingInput.accepts(event.getKeyCode())
                || !RingInput.isRingDevice(event.getDevice() == null ? null : event.getDevice().getName())) return false;
        if (!foreground(activity)) return false;
        refresh();
        RingInput.Action action = INPUT.onKey(event.getAction(), event.getKeyCode(), event.getRepeatCount(),
                SystemClock.uptimeMillis(), RokidRedditPatch.isEditing(activity));
        dispatch(activity, action);
        // UP and repeats are consumed without changing the existing tap deadline.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            MAIN.removeCallbacks(RESOLVE);
            if (INPUT.pending()) MAIN.postDelayed(RESOLVE, RingInput.TAP_WINDOW_MS + 20);
        }
        return true;
    }

    private static boolean foreground(Activity activity) {
        return activity != null && target.get() == activity && resumed && activity.hasWindowFocus()
                && !activity.isFinishing() && !activity.isDestroyed() && RokidRedditPatch.hasController(activity);
    }

    private static void refresh() {
        if (!foreground(target.get())) return;
        if (!claimed || SystemClock.uptimeMillis() - lastClaim >= REFRESH_MIN_MS) {
            claimed = true;
            publish(true);
            MAIN.removeCallbacks(KEEPALIVE);
            MAIN.postDelayed(KEEPALIVE, KEEPALIVE_MS);
        }
    }

    private static void release() {
        MAIN.removeCallbacks(KEEPALIVE);
        MAIN.removeCallbacks(RESOLVE);
        INPUT.reset();
        if (claimed) {
            claimed = false;
            publish(false);
        }
    }

    private static void publish(boolean focused) {
        Activity activity = target.get();
        if (activity == null) return;
        lastClaim = SystemClock.uptimeMillis();
        try {
            activity.getApplicationContext().sendBroadcast(new Intent(FOCUS_ACTION).setPackage(BRIDGE_PACKAGE)
                    .putExtra("focused", focused).putExtra("ts", System.currentTimeMillis()));
        } catch (RuntimeException unavailable) {
            // The standalone temple path remains usable when the bridge is absent.
        }
    }

    private static void dispatch(Activity activity, RingInput.Action action) {
        if (!RingInput.mayDispatch(action, foreground(activity), RokidRedditPatch.isEditing(activity))) return;
        int key;
        switch (action) {
            case NEXT: key = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case PREVIOUS: key = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case SELECT: key = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case BACK: key = KeyEvent.KEYCODE_BACK; break;
            default: return;
        }
        long now = SystemClock.uptimeMillis();
        // Directly invoke the same canonical controller; never fall through to stock text input.
        RokidRedditPatch.dispatchControllerKey(activity, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0));
        RokidRedditPatch.dispatchControllerKey(activity,
                new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0));
    }
}
