/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import app.morphe.extension.reddit.rokid.core.PostNavigation;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A measured HUD viewport with one selection shared by keys, R08 and activation. */
public final class HudView extends LinearLayout {
    public static final int SELECT_ACTION = 0x01020001;
    public static final int ACCENT = 0xff70ff8b;
    private static final int SECONDARY = 0xff4daf60;
    private static final int DIM = 0xff327b40;

    public static final class Row {
        public final String id;
        public final String title;
        public final String detail;
        public final Runnable action;

        public Row(String id, String title, String detail, Runnable action) {
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.action = action;
        }
    }

    private final Activity activity;
    private final TextView header;
    private final TextView context;
    private final TextView position;
    private final TextView hint;
    private final ScrollView viewport;
    private final LinearLayout rowsHost;
    private final List<Row> rows = new ArrayList<>();
    private final List<View> rowViews = new ArrayList<>();
    private int selected;
    private long focusRequest;
    private EditText editor;
    public static final class FeedRow {
        public final Row row;
        public final String metadata;
        public final String previewUrl;
        public final String mediaLabel;
        public FeedRow(Row row, String metadata, String previewUrl, String mediaLabel) {
            this.row = row;
            this.metadata = metadata;
            this.previewUrl = previewUrl;
            this.mediaLabel = mediaLabel;
        }
    }

    private final List<FeedRow> feedRows = new ArrayList<>();
    private final List<TextView> feedTitles = new ArrayList<>();
    private final List<LinearLayout> feedDetails = new ArrayList<>();
    private java.util.function.IntConsumer selectionChanged;
    private MediaThumbnail thumbnail;
    private final List<CharSequence> pages = new ArrayList<>();
    private final PostNavigation postNavigation = new PostNavigation();
    private LinearLayout readingHost;
    private LinearLayout previewHost;
    private String previewSource;
    private String previewLabel;
    private Runnable openPreview;
    private boolean postReader;
    private TextView reader;
    private int readingPage;
    private Runnable readingAction;
    private boolean securedEditor;
    private ViewGroup nativeRoot;
    private final Map<View, Integer> nativeAccessibility = new IdentityHashMap<>();
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener nativeLayout = this::hideNativeSiblings;

    public HudView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);
        setPadding(px(18), px(18), px(18), px(12));
        header = text(25, ACCENT, true);
        context = text(18, SECONDARY, false);
        position = text(17, SECONDARY, false);
        hint = text(15, SECONDARY, false);
        header.setMaxLines(1);
        header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        context.setMaxLines(2);
        context.setEllipsize(android.text.TextUtils.TruncateAt.END);
        position.setMaxLines(1);
        position.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hint.setMinLines(2);
        hint.setMaxLines(2);
        addView(header);
        addView(context);
        addView(position);
        viewport = new ScrollView(activity);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setHorizontalScrollBarEnabled(false);
        viewport.setFillViewport(false);
        viewport.setClipChildren(true);
        viewport.setClipToPadding(true);
        viewport.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rowsHost = new LinearLayout(activity);
        rowsHost.setOrientation(VERTICAL);
        viewport.addView(rowsHost, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(viewport, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        addView(hint);
        setFocusableInTouchMode(true);
        // Selection uses an outline; Android's default focus wash lights black AR pixels.
        setDefaultFocusHighlightEnabled(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof ViewGroup) {
            nativeRoot = (ViewGroup) getParent();
            nativeRoot.getViewTreeObserver().addOnGlobalLayoutListener(nativeLayout);
            hideNativeSiblings();
        }
    }

    private void hideNativeSiblings() {
        if (nativeRoot == null) return;
        for (int i = 0; i < nativeRoot.getChildCount(); i++) {
            View nativeView = nativeRoot.getChildAt(i);
            if (nativeView == this) continue;
            // The native projection reads nodes directly; R08 must see only the HUD.
            if (!nativeAccessibility.containsKey(nativeView)) {
                nativeAccessibility.put(nativeView, nativeView.getImportantForAccessibility());
                nativeView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        // Native screens can append a scrim after the HUD has attached.
        nativeRoot.setForeground(null);
        if (nativeRoot.indexOfChild(this) != nativeRoot.getChildCount() - 1) bringToFront();
    }

    @Override protected void onDetachedFromWindow() {
        if (nativeRoot != null && nativeRoot.getViewTreeObserver().isAlive()) {
            nativeRoot.getViewTreeObserver().removeOnGlobalLayoutListener(nativeLayout);
        }
        for (Map.Entry<View, Integer> entry : nativeAccessibility.entrySet()) {
            entry.getKey().setImportantForAccessibility(entry.getValue());
        }
        nativeAccessibility.clear();
        nativeRoot = null;
        closeThumbnail();
        super.onDetachedFromWindow();
    }

    public void show(String title, String parentContext, List<Row> items, String selectedId) {
        finishEditing();
        focusRequest++;
        closeThumbnail();
        feedRows.clear();
        feedTitles.clear();
        feedDetails.clear();
        selectionChanged = null;
        reader = null;
        readingHost = null;
        previewHost = null;
        postReader = false;
        openPreview = null;
        readingAction = null;
        pages.clear();
        rows.clear();
        rows.addAll(items);
        rowsHost.removeAllViews();
        rowViews.clear();
        header.setText(title);
        context.setText(parentContext);
        context.setVisibility(parentContext.isEmpty() ? GONE : VISIBLE);
        hint.setText("Swipe · tap · back");
        selected = 0;
        for (int i = 0; i < items.size(); i++) {
            Row row = items.get(i);
            if (row.id.equals(selectedId)) selected = i;
            LinearLayout host = new LinearLayout(activity);
            host.setOrientation(VERTICAL);
            host.setPadding(px(12), px(12), px(12), px(12));
            TextView label = text(24, ACCENT, true);
            label.setText(row.title);
            host.addView(label);
            if (!row.detail.isEmpty()) {
                TextView detail = text(19, SECONDARY, false);
                detail.setText(row.detail);
                host.addView(detail);
            }
            host.setContentDescription(row.title + (row.detail.isEmpty() ? "" : ". " + row.detail));
            host.setFocusable(true);
            host.setDefaultFocusHighlightEnabled(false);
            host.setClickable(true);
            final int clickIndex = i;
            host.setOnClickListener(view -> { select(clickIndex); activate(); });
            final int index = i;
            host.setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo node) {
                    super.onInitializeAccessibilityNodeInfo(view, node);
                    node.setSelected(index == selected);
                    node.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION, "Select"));
                }
                @Override public boolean performAccessibilityAction(View view, int action, android.os.Bundle args) {
                    if (action == SELECT_ACTION || action == AccessibilityNodeInfo.ACTION_SELECT) {
                        select(index);
                        return true;
                    }
                    if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) select(index);
                    return super.performAccessibilityAction(view, action, args);
                }
            });
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = px(8);
            rowsHost.addView(host, params);
            rowViews.add(host);
        }
        select(selected);
    }

    public void show(String title, String context, List<Row> items, String selectedId, int scroll) {
        show(title, context, items, selectedId);
        followSelection(scroll);
    }

    public void move(int direction) {
        if (rows.isEmpty() || editor != null) return;
        select((selected + direction + rows.size()) % rows.size());
    }

    public void activate() {
        if (readingAction != null) {
            readingAction.run();
            return;
        }
        if (editor == null && selected < rows.size()) rows.get(selected).action.run();
    }

    public void showFeed(String title, List<FeedRow> items, String selectedId, int scroll,
                         java.util.function.IntConsumer onSelection) {
        show(title, "", new ArrayList<>(), "");
        feedRows.addAll(items);
        selectionChanged = onSelection;
        selected = 0;
        for (int i = 0; i < items.size(); i++) {
            FeedRow item = items.get(i);
            rows.add(item.row);
            if (item.row.id.equals(selectedId)) selected = i;
            LinearLayout host = new LinearLayout(activity);
            host.setOrientation(VERTICAL);
            host.setPadding(px(12), px(10), px(12), px(10));
            TextView meta = text(16, SECONDARY, false);
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            meta.setText(item.metadata);
            host.addView(meta);
            TextView label = text(23, ACCENT, true);
            label.setText(item.row.title);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            host.addView(label);
            feedTitles.add(label);
            LinearLayout detail = new LinearLayout(activity);
            detail.setOrientation(HORIZONTAL);
            host.addView(detail);
            feedDetails.add(detail);
            host.setContentDescription(item.metadata + ". " + item.row.title);
            installRowSelection(host, i);
            LayoutParams params = new LayoutParams(-1, -2);
            params.bottomMargin = px(6);
            rowsHost.addView(host, params);
            rowViews.add(host);
        }
        select(selected);
        hint.setText("Swipe: posts \u00b7 Tap: open \u00b7 Back: menu");
        followSelection(scroll);
    }

    private void installRowSelection(View host, int index) {
        host.setFocusable(true);
        host.setDefaultFocusHighlightEnabled(false);
        host.setClickable(true);
        host.setOnClickListener(view -> { select(index); activate(); });
        host.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo node) {
                super.onInitializeAccessibilityNodeInfo(view, node);
                node.setSelected(index == selected);
                node.addAction(new AccessibilityNodeInfo.AccessibilityAction(SELECT_ACTION, "Select"));
            }
            @Override public boolean performAccessibilityAction(View view, int action, android.os.Bundle args) {
                if (action == SELECT_ACTION || action == AccessibilityNodeInfo.ACTION_SELECT) {
                    select(index);
                    return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) select(index);
                return super.performAccessibilityAction(view, action, args);
            }
        });
    }

    private void expandFeedSelection() {
        closeThumbnail();
        for (int i = 0; i < feedRows.size(); i++) {
            boolean active = i == selected;
            feedTitles.get(i).setMaxLines(active ? 3 : 2);
            LinearLayout detail = feedDetails.get(i);
            detail.removeAllViews();
            detail.setVisibility(active ? VISIBLE : GONE);
            if (!active) continue;
            FeedRow item = feedRows.get(i);
            if (!item.mediaLabel.isEmpty()) {
                thumbnail = new MediaThumbnail(activity, item.previewUrl, item.mediaLabel);
                LayoutParams imageParams = new LayoutParams(px(112), px(100));
                imageParams.rightMargin = px(12);
                detail.addView(thumbnail, imageParams);
            }
            TextView summary = text(18, SECONDARY, false);
            summary.setMaxLines(3);
            summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            summary.setText(item.row.detail);
            detail.addView(summary, new LayoutParams(0, -2, 1));
        }
    }

    public int scrollPosition() { return viewport.getScrollY(); }

    private void closeThumbnail() {
        if (thumbnail != null) { thumbnail.close(); thumbnail = null; }
    }

    public void read(String title, String parentContext, String body, int page, Runnable action) {
        beginReading(title, parentContext, body, page, action, null, null, null);
    }

    public void readPost(String parentContext, String title, String body, int page, String source,
                         String mediaLabel, Runnable actions, Runnable openMedia) {
        SpannableString content = new SpannableString(title + (body.isEmpty() ? "" : "\n\n" + body));
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new RelativeSizeSpan(1.18f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        beginReading("POST", parentContext, content, page, actions, source, mediaLabel, openMedia);
    }

    private void beginReading(String title, String parentContext, CharSequence body, int page, Runnable action,
                              String source, String mediaLabel, Runnable openMedia) {
        show(title, parentContext, new ArrayList<>(), "");
        readingPage = Math.max(0, page);
        postReader = openMedia != null;
        previewSource = source;
        previewLabel = mediaLabel;
        openPreview = openMedia;
        readingHost = new LinearLayout(activity);
        readingHost.setOrientation(VERTICAL);
        reader = text(22, ACCENT, false);
        reader.setLineSpacing(px(3), 1f);
        readingHost.addView(reader, new LayoutParams(-1, -2));
        previewHost = new LinearLayout(activity);
        previewHost.setOrientation(VERTICAL);
        readingHost.addView(previewHost, new LayoutParams(-1, -2));
        rowsHost.addView(readingHost, new LayoutParams(-1, -2));
        readingAction = action;
        readingHost.setContentDescription(parentContext);
        readingHost.setFocusable(true);
        readingHost.setClickable(true);
        readingHost.setOnClickListener(view -> activate());
        hint.setText("Swipe: text \u00b7 Tap: actions ? Back");
        TextView current = reader;
        afterLayout(() -> {
            if (reader != current || viewport.getHeight() <= 0) return;
            int width = Math.max(1, viewport.getWidth());
            int height = Math.max(1, viewport.getHeight() - px(8));
            pages.clear();
            int start = 0;
            while (start < body.length()) {
                CharSequence remaining = body.subSequence(start, body.length());
                StaticLayout layout = layout(remaining, width);
                int line = 0;
                while (line + 1 < layout.getLineCount() && layout.getLineBottom(line + 1) <= height) line++;
                int end = Math.max(1, layout.getLineEnd(line));
                pages.add(remaining.subSequence(0, end));
                start += end;
            }
            if (pages.isEmpty()) pages.add("");
            if (postReader && height - layout(pages.get(pages.size() - 1), width).getHeight()
                    < px(170) + previewChromeHeight(width) + reader.getPaddingBottom()) {
                pages.add("");
            }
            readingPage = Math.max(0, Math.min(page, pages.size() - 1));
            postNavigation.configure(pages.size(), postReader, readingPage);
            renderReadingPage();
        });
    }

    private StaticLayout layout(CharSequence text, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), reader.getPaint(), width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
                .setLineSpacing(px(3), 1f).build();
    }

    public boolean moveReading(int direction) {
        if (reader == null || pages.isEmpty()) return false;
        PostNavigation.Move result = postNavigation.move(direction);
        if (result == PostNavigation.Move.OPEN_MEDIA) {
            openPreview.run();
        } else if (result == PostNavigation.Move.PAGE) {
            readingPage = postNavigation.page();
            renderReadingPage();
        }
        return true;
    }

    public int readingPage() { return readingPage; }

    public void updateMediaTitle(String title) { header.setText(title); }
    public void updateMediaHint(String text) { hint.setText(text); }

    public void media(String title, String parentContext, View content, Runnable action) {
        show(title, parentContext, new ArrayList<>(), "");
        if (content.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) content.getParent()).removeView(content);
        }
        rowsHost.addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        afterLayout(() -> {
            if (content.getParent() == rowsHost) {
                content.getLayoutParams().height = viewport.getHeight();
                content.requestLayout();
            }
        });
        readingAction = action;
        content.setOnClickListener(view -> action.run());
        hint.setText("Swipe through media · tap for actions · back");
    }

    private void renderReadingPage() {
        closeThumbnail();
        reader.setText(pages.get(readingPage));
        reader.setVisibility(pages.get(readingPage).length() == 0 ? GONE : VISIBLE);
        previewHost.removeAllViews();
        boolean preview = postReader && readingPage == pages.size() - 1;
        previewHost.setVisibility(preview ? VISIBLE : GONE);
        position.setText("Page " + (readingPage + 1) + " / " + pages.size()
                + (postReader ? "  [ " + previewLabel + " ]" : ""));
        hint.setText(preview ? "Swipe \u25b6: full screen \u00b7 Tap: actions \u00b7 Back: feed"
                : "Swipe: text \u00b7 Tap: actions ? Back");
        if (preview) {
            previewHost.setBackground(outline(ACCENT));
            previewHost.setPadding(px(6), px(6), px(6), px(6));
            int textHeight = pages.get(readingPage).length() == 0 ? 0
                    : layout(pages.get(readingPage), Math.max(1, viewport.getWidth())).getHeight() + reader.getPaddingBottom();
            TextView cue = previewCue();
            cue.measure(MeasureSpec.makeMeasureSpec(Math.max(1, viewport.getWidth() - px(12)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int remaining = viewport.getHeight() - textHeight - cue.getMeasuredHeight()
                    - previewHost.getPaddingTop() - previewHost.getPaddingBottom() - px(4);
            thumbnail = new MediaThumbnail(activity, previewSource, previewLabel);
            previewHost.addView(thumbnail, new LayoutParams(-1, Math.max(px(1), Math.min(px(300), remaining))));
            previewHost.addView(cue);
        }
        viewport.scrollTo(0, 0);
    }

    private void afterLayout(Runnable action) {
        viewport.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (viewport.getViewTreeObserver().isAlive()) {
                    viewport.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                action.run();
            }
        });
        viewport.requestLayout();
    }

    private TextView previewCue() {
        TextView cue = text(17, ACCENT, true);
        cue.setText("Swipe \u25b6 full screen");
        cue.setGravity(Gravity.CENTER);
        cue.setPadding(0, 0, 0, 0);
        return cue;
    }

    private int previewChromeHeight(int width) {
        TextView cue = previewCue();
        cue.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width - px(12)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return cue.getMeasuredHeight() + px(16);
    }

    public String selectedId() { return rows.isEmpty() ? "" : rows.get(selected).id; }
    public boolean isEditing() { return editor != null; }

    public EditText edit(String title, String parentContext, String initial, Runnable done) {
        show(title, parentContext, new ArrayList<>(), "");
        editor = new EditText(activity);
        editor.setLayerType(View.LAYER_TYPE_HARDWARE, MonochromeMedia.paint());
        editor.setTextColor(ACCENT);
        editor.setHintTextColor(SECONDARY);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24) * textScale());
        editor.setBackground(outline(ACCENT));
        editor.setDefaultFocusHighlightEnabled(false);
        editor.setPadding(px(12), px(12), px(12), px(12));
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        editor.setText(initial);
        editor.setSelection(initial.length());
        editor.setContentDescription("Reply draft. Phone keyboard input.");
        editor.setOnEditorActionListener((field, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE && event == null) {
                done.run();
                return true;
            }
            return false;
        });
        rowsHost.addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        hint.setText("Type on phone · back to review");
        editor.requestFocus();
        editor.post(() -> {
            if (editor != null) ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
        return editor;
    }

    public String editingText() { return editor == null ? "" : editor.getText().toString(); }

    public void editorError(String message) {
        if (editor != null) editor.setError(message);
    }

    public void markSensitiveEditor() {
        if (editor != null) {
            if ((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                securedEditor = true;
            }
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setContentDescription("Password. Phone keyboard input.");
        }
    }

    public void finishEditing() {
        if (editor == null) return;
        ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editor.clearFocus();
        editor = null;
        if (securedEditor) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            securedEditor = false;
        }
        requestFocus();
    }

    private void select(int index) {
        if (rows.isEmpty()) {
            position.setText("");
            return;
        }
        selected = Math.max(0, Math.min(index, rows.size() - 1));
        if (!feedRows.isEmpty()) expandFeedSelection();
        if (selectionChanged != null) selectionChanged.accept(selected);
        for (int i = 0; i < rowViews.size(); i++) {
            rowViews.get(i).setSelected(i == selected);
            rowViews.get(i).setBackground(outline(i == selected ? ACCENT : DIM));
        }
        position.setText((selected + 1) + " / " + rows.size());
        followSelection(-1);
    }

    private void followSelection(int restoredScroll) {
        if (rows.isEmpty()) return;
        View row = rowViews.get(selected);
        long operation = ++focusRequest;
        viewport.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (viewport.getViewTreeObserver().isAlive()) {
                    viewport.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                if (operation != focusRequest || selected >= rowViews.size() || row != rowViews.get(selected)) return;
                if (restoredScroll >= 0) viewport.scrollTo(0, restoredScroll);
                row.requestRectangleOnScreen(new Rect(0, 0, row.getWidth(), row.getHeight()), true);
            }
        });
        viewport.requestLayout();
    }

    private GradientDrawable outline(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(px(8));
        drawable.setStroke(Math.max(1, px(2)), color);
        return drawable;
    }

    private TextView text(int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        // System emoji glyphs keep their own colors unless the rendered text is filtered.
        view.setLayerType(View.LAYER_TYPE_HARDWARE, MonochromeMedia.paint());
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size) * textScale());
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        view.setPadding(0, 0, 0, px(8));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int px(int reference) {
        return Math.round(reference * activity.getResources().getDisplayMetrics().widthPixels / 480f);
    }

    private float textScale() {
        float scale = activity.getSharedPreferences("rokid_hud", Activity.MODE_PRIVATE).getFloat("text_scale", 1f);
        return scale == 1.2f || scale == 1.4f ? scale : 1f;
    }
}
