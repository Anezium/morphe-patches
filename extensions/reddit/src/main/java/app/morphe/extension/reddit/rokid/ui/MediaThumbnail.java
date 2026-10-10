/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.LruCache;
import app.morphe.extension.reddit.rokid.nativebridge.NativeMedia;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** An attachment-scoped static preview. Animated sources decode to their first frame. */
public final class MediaThumbnail extends FrameLayout implements AutoCloseable {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Activity activity;
    private final String source;
    private final String label;
    private final ImageView image;
    private final TextView notice;
    private Future<?> pending;
    private boolean loaded;
    private volatile boolean attached;
    private volatile boolean closed;
    private volatile long generation;
    private volatile HttpURLConnection connection;

    public MediaThumbnail(Activity activity, String source, String label) {
        super(activity);
        this.activity = activity;
        this.source = source == null ? "" : source;
        this.label = label == null ? "MEDIA" : label;
        setBackgroundColor(android.graphics.Color.BLACK);
        setContentDescription(this.label + " preview");
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setColorFilter(MonochromeMedia.filter());
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(-1, -1));
        notice = new TextView(activity);
        notice.setTextColor(MonochromeMedia.ACCENT);
        notice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                13f * activity.getResources().getDisplayMetrics().widthPixels / 480f);
        notice.setMaxLines(1);
        notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notice.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        notice.setBackgroundColor(android.graphics.Color.BLACK);
        addView(notice, new LayoutParams(-1, -2, Gravity.BOTTOM));
        notice.setText(this.label);
        Bitmap cached = CACHE.get(this.source);
        if (cached != null) { image.setImageBitmap(cached); loaded = true; }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (!closed && !loaded) load();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        cancel();
        super.onDetachedFromWindow();
    }

    private boolean current(long operation) {
        return !closed && attached && operation == generation && !Thread.currentThread().isInterrupted();
    }

    private void load() {
        if (!NativeMedia.staticAllowed(source)) { notice.setText(label + " \u00b7 preview unavailable"); return; }
        Bitmap cached = CACHE.get(source);
        if (cached != null) { image.setImageBitmap(cached); loaded = true; notice.setText(label); return; }
        long operation = ++generation;
        notice.setText(label + " \u00b7 loading");
        pending = WORKERS.submit(() -> {
            Bitmap decoded = null;
            try {
                byte[] bytes = download(operation);
                if (!current(operation)) return;
                decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, origin) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float ratio = Math.min(1f, Math.min(320f / width, 240f / height));
                    decoder.setTargetSize(Math.max(1, Math.round(width * ratio)), Math.max(1, Math.round(height * ratio)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                if (!current(operation)) return;
                CACHE.put(source, decoded);
            } catch (Exception unavailable) {
                // A failed or oversized preview stays a labelled placeholder; URLs never enter logs.
            }
            Bitmap loadedImage = decoded;
            activity.runOnUiThread(() -> {
                if (!current(operation)) return;
                pending = null;
                loaded = loadedImage != null;
                image.setImageBitmap(loadedImage);
                notice.setText(loaded ? label : label + " \u00b7 preview unavailable");
            });
        });
    }

    private byte[] download(long operation) throws Exception {
        URL next = new URL(source.replace("&amp;", "&"));
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!current(operation) || !NativeMedia.staticAllowed(next.toString())) throw new IllegalStateException("Preview unavailable");
            HttpURLConnection request = (HttpURLConnection) next.openConnection();
            connection = request;
            request.setConnectTimeout(12000);
            request.setReadTimeout(12000);
            request.setInstanceFollowRedirects(false);
            try {
                int response = request.getResponseCode();
                if (response == 301 || response == 302 || response == 303 || response == 307 || response == 308) {
                    String location = request.getHeaderField("Location");
                    if (redirects == 3 || location == null) throw new IllegalStateException("Preview unavailable");
                    next = new URL(next, location);
                    continue;
                }
                String type = request.getContentType();
                if (response != 200 || type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")
                        || request.getContentLengthLong() > MAX_BYTES) throw new IllegalStateException("Preview unavailable");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = request.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        if (!current(operation) || bytes.size() + read > MAX_BYTES) throw new IllegalStateException("Preview cancelled");
                        bytes.write(buffer, 0, read);
                    }
                }
                return bytes.toByteArray();
            } finally {
                request.disconnect();
                if (connection == request) connection = null;
            }
        }
        throw new IllegalStateException("Preview unavailable");
    }

    private void cancel() {
        generation++;
        if (pending != null) { pending.cancel(true); pending = null; }
        if (connection != null) connection.disconnect();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        attached = false;
        cancel();
        image.setImageDrawable(null);
    }
}