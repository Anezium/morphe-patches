/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.app.Activity;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewGroup;

import app.morphe.extension.reddit.rokid.core.SingleAxisInput;

/** The official account and secondary activities retain their own native navigation. */
public final class NativeScreen {
    private final HudView hud;
    private final NativeSurface surface;
    private final SingleAxisInput input = new SingleAxisInput();

    public NativeScreen(Activity activity, ViewGroup root) {
        hud = new HudView(activity);
        root.addView(hud, new ViewGroup.LayoutParams(-1, -1));
        surface = new NativeSurface(activity, root, hud);
        String title = activity.getClass().getName().contains("auth.") ? "ACCOUNT" : "REDDIT";
        surface.open(title, activity::onBackPressed);
    }

    public boolean handle(KeyEvent event) {
        if (hud.isEditing()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) surface.back();
                return true;
            }
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return SingleAxisInput.classify(event.getKeyCode()) != SingleAxisInput.Action.NONE;
        }
        switch (input.map(event.getAction(), event.getKeyCode(), event.getRepeatCount(), SystemClock.uptimeMillis())) {
            case NEXT: if (!hud.moveReading(1)) hud.move(1); return true;
            case PREVIOUS: if (!hud.moveReading(-1)) hud.move(-1); return true;
            case SELECT: hud.activate(); return true;
            case BACK: surface.back(); return true;
            case DUPLICATE: return true;
            default: return false;
        }
    }

    public void close() {
        surface.close();
        hud.finishEditing();
        if (hud.getParent() instanceof ViewGroup) ((ViewGroup) hud.getParent()).removeView(hud);
    }
}
