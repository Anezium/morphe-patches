/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Projects the official screen's actual semantic actions into the glasses menu. */
public final class NativeSurface {
    private static final class Node {
        final View view;
        final AccessibilityNodeProvider provider;
        final int id;
        final String label;
        final String initial;
        final boolean editable;
        final boolean password;
        final boolean clickable;
        final boolean nativeAuthTouch;
        final boolean scrollable;
        final int scrollForward;
        final int scrollBackward;
        final boolean checkable;
        final boolean checked;
        final int inputType;
        final Node clickOwner;
        Node(View view, AccessibilityNodeProvider provider, int id, AccessibilityNodeInfo info, Node clickOwner) {
            this.view = view;
            this.provider = provider;
            this.id = id;
            CharSequence text = info.getText();
            CharSequence description = info.getContentDescription();
            password = info.isPassword();
            editable = info.isEditable();
            nativeAuthTouch = isNativeAuthContinue(view, info);
            this.clickOwner = info.isClickable() || nativeAuthTouch ? null : clickOwner;
            clickable = info.isClickable() || nativeAuthTouch || this.clickOwner != null;
            scrollable = info.isScrollable();
            String nativeClass = String.valueOf(info.getClassName());
            boolean pager = nativeClass.contains("ViewPager") || nativeClass.endsWith("Pager");
            boolean horizontal = nativeClass.contains("Horizontal")
                    || supports(info, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId())
                    || supports(info, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId());
            scrollForward = pager || horizontal ? 0 : supports(info, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId())
                    ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()
                    : supports(info, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : 0;
            scrollBackward = pager || horizontal ? 0 : supports(info, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())
                    ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()
                    : supports(info, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : 0;
            checkable = info.isCheckable();
            checked = info.isChecked();
            inputType = info.getInputType();
            initial = !password && text != null && !info.isShowingHintText() ? text.toString() : "";
            String resource = info.getViewIdResourceName();
            label = password ? "Password" : text != null && text.length() > 0 ? text.toString()
                    : description != null && description.length() > 0 ? description.toString() : editable ? "Text field"
                    : checkable && resource != null && resource.endsWith("onboarding_email_subscription") ? "Email notifications" : "";
        }
        AccessibilityNodeInfo fresh() {
            return provider == null ? view.createAccessibilityNodeInfo() : provider.createAccessibilityNodeInfo(id);
        }
        private static boolean supports(AccessibilityNodeInfo info, int action) {
            for (AccessibilityNodeInfo.AccessibilityAction available : info.getActionList()) {
                if (available.getId() == action) return true;
            }
            return false;
        }
        boolean perform(int action, Bundle args) {
            AccessibilityNodeInfo info = fresh();
            if (info == null || !info.isEnabled()) return false;
            Node current = new Node(view, provider, id, info, clickOwner);
            // A reused virtual id must not redirect an action to a different native item.
            if (!label.equals(current.label) || editable != current.editable || password != current.password
                    || nativeAuthTouch != current.nativeAuthTouch) return false;
            if ((action == scrollForward || action == scrollBackward) && !supports(info, action)) return false;
            if (action == AccessibilityNodeInfo.ACTION_CLICK && clickOwner != null) return clickOwner.perform(action, args);
            if (action == AccessibilityNodeInfo.ACTION_CLICK && nativeAuthTouch) return touchNativeAuth(info);
            return provider == null ? view.performAccessibilityAction(action, args) : provider.performAction(id, action, args);
        }

        private static boolean isNativeAuthContinue(View view, AccessibilityNodeInfo info) {
            String resource = info.getViewIdResourceName();
            if (resource == null || !(resource.equals("continue_animated_button")
                    || resource.endsWith("/continue_animated_button"))) return false;
            Context context = view.getContext();
            for (int depth = 0; depth < 16; depth++) {
                if (context.getClass().getName().equals("com.reddit.auth.login.screen.AuthActivityKt")) return true;
                if (!(context instanceof ContextWrapper)) break;
                Context base = ((ContextWrapper) context).getBaseContext();
                if (base == context) break;
                context = base;
            }
            return false;
        }

        private boolean touchNativeAuth(AccessibilityNodeInfo info) {
            // This pinned APK's Compose auth button exposes a pointer gesture without ACTION_CLICK.
            Rect bounds = new Rect();
            Rect visible = new Rect();
            info.getBoundsInScreen(bounds);
            if (bounds.isEmpty() || !view.getGlobalVisibleRect(visible)
                    || !visible.contains(bounds.centerX(), bounds.centerY())) return false;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            long now = SystemClock.uptimeMillis();
            float x = bounds.exactCenterX() - location[0];
            float y = bounds.exactCenterY() - location[1];
            MotionEvent down = nativeTouch(now, now, MotionEvent.ACTION_DOWN, x, y);
            MotionEvent up = nativeTouch(now, now + 80, MotionEvent.ACTION_UP, x, y);
            try {
                boolean accepted = view.dispatchTouchEvent(down);
                return view.dispatchTouchEvent(up) || accepted;
            } finally {
                down.recycle();
                up.recycle();
            }
        }

        private static MotionEvent nativeTouch(long start, long time, int action, float x, float y) {
            MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
            pointer.id = 0;
            pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
            coordinates.x = x;
            coordinates.y = y;
            coordinates.pressure = 1;
            coordinates.size = 1;
            return MotionEvent.obtain(start, time, action, 1, new MotionEvent.PointerProperties[]{pointer},
                    new MotionEvent.PointerCoords[]{coordinates}, 0, 0, 1, 1, 0, 0,
                    InputDevice.SOURCE_TOUCHSCREEN, 0);
        }
    }

    private final Activity activity;
    private final ViewGroup root;
    private final HudView hud;
    private Runnable back;
    private String title;
    private String signature = "";
    private boolean active;
    private boolean editing;
    private boolean queued;
    private Runnable finishEditor;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener layout = this::schedule;

    public NativeSurface(Activity activity, ViewGroup root, HudView hud) {
        this.activity = activity;
        this.root = root;
        this.hud = hud;
        HiddenApiBypass.addHiddenApiExemptions("Landroid/view/accessibility/AccessibilityNodeInfo;");
    }

    public void open(String title, Runnable back) {
        close();
        this.title = title;
        this.back = back;
        active = true;
        signature = "";
        root.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        schedule();
        root.postDelayed(this::schedule, 500);
    }

    public boolean back() {
        if (!active) return false;
        if (editing) {
            if (finishEditor != null) {
                finishEditor.run();
                return true;
            }
            hud.finishEditing();
            editing = false;
            signature = "";
            schedule();
        } else {
            close();
            back.run();
        }
        return true;
    }

    public boolean isActive() { return active; }
    public boolean isEditing() { return editing; }

    public void close() {
        active = false;
        editing = false;
        finishEditor = null;
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
    }

    private void schedule() {
        if (!active || editing || queued) return;
        queued = true;
        root.postDelayed(() -> {
            queued = false;
            if (active && !editing) render();
        }, 80);
    }

    private List<Node> snapshot() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != hud) scan(child, nodes, 0, null);
        }
        return nodes;
    }

    private String identity(List<Node> nodes) {
        StringBuilder identity = new StringBuilder(title);
        for (Node node : nodes) identity.append('\n').append(node.id).append(':').append(node.label)
                .append(':').append(node.editable).append(':').append(node.clickable)
                .append(':').append(node.checkable).append(':').append(node.checked)
                .append(':').append(node.scrollForward).append(':').append(node.scrollBackward)
                .append(':').append(System.identityHashCode(node.view));
        return identity.toString();
    }

    private void render() {
        List<Node> nodes = snapshot();
        String next = identity(nodes);
        if (next.equals(signature)) return;
        signature = next;
        List<HudView.Row> rows = new ArrayList<>();
        Node forward = null;
        Node backward = null;
        Set<String> duplicate = new HashSet<>();
        for (Node node : nodes) {
            if (!node.label.isEmpty() && duplicate.add(node.id + ":" + node.label + ":" + System.identityHashCode(node.view))) {
                String id = "native-" + System.identityHashCode(node.view) + "-" + node.id;
                String label = node.label.length() > 160 ? node.label.substring(0, 157) + "…" : node.label;
                rows.add(new HudView.Row(id, label, node.editable ? "Type on phone"
                        : node.checkable ? node.checked ? "On" : "Off" : "", () -> activate(node)));
            }
            if (node.scrollable && node.scrollForward != 0 && (forward == null
                    || node.scrollForward == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId())) forward = node;
            if (node.scrollable && node.scrollBackward != 0 && (backward == null
                    || node.scrollBackward == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())) backward = node;
        }
        if (forward != null) {
            Node target = forward;
            rows.add(new HudView.Row("more", "More", "Scroll down", () -> scroll(target, target.scrollForward)));
        }
        if (backward != null) {
            Node target = backward;
            rows.add(new HudView.Row("previous", "Previous", "Scroll up", () -> scroll(target, target.scrollBackward)));
        }
        if (rows.isEmpty()) rows.add(new HudView.Row("loading", "Loading…", "Tap to check again", () -> {
            signature = "";
            schedule();
        }));
        rows.add(new HudView.Row("back", "Back", "", () -> back()));
        hud.show(title, "", rows, hud.selectedId());
    }

    private void scroll(Node node, int action) {
        node.perform(action, null);
        signature = "";
        schedule();
    }

    private void activate(Node node) {
        if (node.editable) {
            editing = true;
            finishEditor = () -> {
                String text = hud.editingText();
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                if (!node.perform(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    hud.editorError("The native field changed. Your text is kept here.");
                    return;
                }
                hud.finishEditing();
                finishEditor = null;
                editing = false;
                signature = "";
                schedule();
            };
            android.widget.EditText editor = hud.edit(title, node.label, node.initial, finishEditor);
            if (node.inputType != 0) editor.setInputType(node.inputType);
            if (node.password) hud.markSensitiveEditor();
        } else if (node.clickable) {
            List<Node> current = snapshot();
            String label = node.label.toLowerCase(java.util.Locale.ROOT);
            boolean review = current.stream().anyMatch(item -> item.editable && !item.password)
                    || label.matches(".*(post|publish|submit|send|delete|report|block|publier|envoyer|supprimer|signaler|bloquer).*" );
            if (review) {
                confirm(node, current);
                return;
            }
            node.perform(AccessibilityNodeInfo.ACTION_CLICK, null);
            signature = "";
            schedule();
        } else {
            editing = true;
            hud.read(title, "", node.label, 0, () -> {
                editing = false;
                signature = "";
                schedule();
            });
        }
    }

    private void confirm(Node node, List<Node> current) {
        editing = true;
        String approved = identity(current);
        StringBuilder context = new StringBuilder();
        for (Node item : current) {
            if (item.editable && !item.password) context.append(item.label).append('\n');
        }
        List<HudView.Row> rows = new ArrayList<>();
        rows.add(new HudView.Row("confirm", node.label, "Confirm this native action", () -> {
            if (!approved.equals(identity(snapshot()))) {
                hud.show("ACTION CHANGED", "Review the current native screen again.",
                        java.util.Collections.singletonList(new HudView.Row("back", "Back", "", this::back)), "back");
                return;
            }
            if (!node.perform(AccessibilityNodeInfo.ACTION_CLICK, null)) {
                hud.show("ACTION UNAVAILABLE", "Reddit did not accept this action. Return to the form.",
                        java.util.Collections.singletonList(new HudView.Row("back", "Back", "", this::back)), "back");
                return;
            }
            editing = false;
            signature = "";
            schedule();
        }));
        rows.add(new HudView.Row("back", "Back to edit", "", this::back));
        hud.show("REVIEW", context.toString(), rows, "back");
    }

    private void scan(View view, List<Node> nodes, int depth, Node clickOwner) {
        if (depth > 20 || nodes.size() >= 256 || view.getVisibility() != View.VISIBLE) return;
        if (!view.getGlobalVisibleRect(new Rect())) return;
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        if (provider != null) {
            virtual(view, provider, -1, nodes, new HashSet<>(), 0, clickOwner);
            return;
        }
        AccessibilityNodeInfo info = view.createAccessibilityNodeInfo();
        Node current = info == null ? null : add(view, null, -1, info, nodes, clickOwner);
        Node owner = current != null && (info.isClickable() || current.nativeAuthTouch) ? current : clickOwner;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), nodes, depth + 1, owner);
        }
    }

    private void virtual(View view, AccessibilityNodeProvider provider, int id, List<Node> nodes,
                         Set<Integer> seen, int depth, Node clickOwner) {
        if (depth > 24 || nodes.size() >= 256 || !seen.add(id)) return;
        AccessibilityNodeInfo info = provider.createAccessibilityNodeInfo(id);
        if (info == null) return;
        Node current = add(view, provider, id, info, nodes, clickOwner);
        Node owner = current != null && (info.isClickable() || current.nativeAuthTouch) ? current : clickOwner;
        for (int i = 0; i < info.getChildCount(); i++) {
            try {
                long child = (Long) HiddenApiBypass.invoke(AccessibilityNodeInfo.class, info, "getChildId", i);
                virtual(view, provider, (int) (child >> 32), nodes, seen, depth + 1, owner);
            } catch (ReflectiveOperationException | RuntimeException inaccessible) {
                return;
            }
        }
    }

    private Node add(View view, AccessibilityNodeProvider provider, int id, AccessibilityNodeInfo info, List<Node> nodes, Node clickOwner) {
        if (!info.isVisibleToUser() || !info.isEnabled()) return null;
        Rect bounds = new Rect();
        Rect visible = new Rect();
        info.getBoundsInScreen(bounds);
        // Auth retains an off-screen Compose page whose nodes still claim visibility.
        if (!view.getGlobalVisibleRect(visible) || !Rect.intersects(bounds, visible)) return null;
        Node node = new Node(view, provider, id, info, clickOwner);
        if (!node.label.isEmpty() || node.scrollable) nodes.add(node);
        return node;
    }
}
