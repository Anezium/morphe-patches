/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.app.Activity;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import app.morphe.extension.reddit.rokid.nativebridge.NativeMedia;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Keeps playback intent and position while the HUD temporarily opens its action list. */
public final class MediaView extends FrameLayout implements AutoCloseable {
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<NativeMedia.Item> items;
    private final ImageView image;
    private final TextView notice;
    private final TextureView video;
    private MediaPlayer player;
    private Surface videoSurface;
    private AnimatedImageDrawable animation;
    private Future<?> imageRequest;
    private Runnable positionChanged;
    private int itemIndex;
    private int sourceIndex;
    private int videoPosition;
    private int videoDuration;
    private int videoWidth;
    private int videoHeight;
    private boolean prepared;
    private boolean playing;
    private boolean seeking;
    private boolean muted;
    private boolean unavailable;
    private long videoGeneration;
    private volatile long generation;
    private volatile boolean closed;
    private volatile HttpURLConnection connection;

    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (closed || !isAttachedToWindow() || !prepared || !isPlaying() || !canMute()) return;
            rememberPosition();
            notifyPositionChanged();
            postDelayed(this, 1000);
        }
    };

    private void schedulePlaybackTick() {
        removeCallbacks(playbackTick);
        if (!closed && prepared && isAttachedToWindow() && isPlaying() && canMute()) {
            postDelayed(playbackTick, 1000);
        }
    }

    public MediaView(Activity activity, Object post) throws ReflectiveOperationException {
        super(activity);
        this.activity = activity;
        items = NativeMedia.resolve(post);
        setBackgroundColor(android.graphics.Color.BLACK);
        video = new TextureView(activity);
        video.setOpaque(false);
        // TextureView composites its video frames through this paint; SurfaceView cannot.
        video.setLayerPaint(MonochromeMedia.paint());
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                if (!closed && isVideo()) openVideo();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { fitVideo(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                rememberPosition();
                videoGeneration++;
                releaseVideo();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        addView(video, new LayoutParams(-1, -1));
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setColorFilter(MonochromeMedia.filter());
        addView(image, new LayoutParams(-1, -1));
        notice = new TextView(activity);
        notice.setTextColor(MonochromeMedia.ACCENT);
        notice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                20f * activity.getResources().getDisplayMetrics().widthPixels / 480f);
        notice.setGravity(Gravity.CENTER);
        addView(notice, new LayoutParams(-1, -1));
        showItem();
    }

    public void setOnPositionChanged(Runnable listener) { positionChanged = listener; }

    private void notifyPositionChanged() {
        if (!closed && positionChanged != null) positionChanged.run();
    }

    public boolean isVideo() { return !items.isEmpty() && items.get(itemIndex).video; }
    public boolean isAnimated() { return animation != null; }
    public boolean isPlaying() { return playing && (isVideo() || isAnimated()); }
    public int itemCount() { return items.size(); }
    public boolean isUnavailable() { return unavailable; }
    public boolean canMute() { return isVideo() && !items.get(itemIndex).loop; }
    public boolean isMuted() { return isVideo() && (items.get(itemIndex).loop || muted); }
    public void toggleMute() {
        if (!canMute()) return;
        muted = !muted;
        if (prepared && player != null) player.setVolume(muted ? 0f : 1f, muted ? 0f : 1f);
        notifyPositionChanged();
    }
    public void replay() {
        if (!isVideo() && animation == null) return;
        videoPosition = 0;
        playing = true;
        if (prepared && player != null) { seeking = true; player.seekTo(0); if (isAttachedToWindow()) player.start(); }
        if (animation != null) {
            animation.stop();
            animation = null;
            image.setImageDrawable(null);
            generation++;
            cancelImageRequest();
            notice.setText("Loading image\u2026");
            if (isAttachedToWindow()) loadImage();
        } else notice.setText("");
        notifyPositionChanged();
    }
    public void retry() {
        if (closed || items.isEmpty()) return;
        unavailable = false;
        sourceIndex = 0;
        generation++;
        cancelImageRequest();
        if (isVideo()) {
            videoGeneration++;
            releaseVideo();
            notice.setText("Loading video\u2026");
            openVideo();
        } else {
            if (animation != null) animation.stop();
            animation = null;
            image.setImageDrawable(null);
            notice.setText("Loading image\u2026");
            if (isAttachedToWindow()) loadImage();
        }
        notifyPositionChanged();
    }
    public String position() {
        if (items.isEmpty()) return "MEDIA";
        return (isVideo() ? items.get(itemIndex).loop ? "GIF" : "VIDEO" : isAnimated() ? "GIF" : "IMAGE")
                + " \u00b7 " + (itemIndex + 1) + " / " + items.size();
    }
    public String navigationHint() {
        if (isVideo() && !items.get(itemIndex).loop) {
            String time = clock(videoPosition) + " / " + (videoDuration > 0 ? clock(videoDuration) : "--:--");
            return (isPlaying() ? "Playing" : "Paused") + " \u00b7 " + time
                    + (isMuted() ? " \u00b7 muted" : " \u00b7 sound on")
                    + "\nSwipe \u00b110 s \u00b7 Tap: actions \u00b7 Back: post";
        }
        String swipe = "";
        if (items.size() > 1) swipe = "Swipe \u00b7 " + (itemIndex == 0 ? "next" : itemIndex == items.size() - 1 ? "previous" : "previous / next") + "   ";
        return swipe + "Tap \u00b7 actions   Back \u00b7 post";
    }

    private static String clock(int milliseconds) {
        int seconds = Math.max(0, milliseconds / 1000);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
    public void togglePlayback() {
        if (!isVideo() && animation == null) return;
        playing = !playing;
        notice.setText("");
        if (isVideo() && prepared && player != null) {
            if (playing && isAttachedToWindow()) player.start(); else if (player.isPlaying()) player.pause();
        }
        if (animation != null) { if (playing && isAttachedToWindow()) animation.start(); else animation.stop(); }
        schedulePlaybackTick();
        notifyPositionChanged();
    }
    public void pause() {
        removeCallbacks(playbackTick);
        playing = false;
        rememberPosition();
        if (prepared && player != null && player.isPlaying()) player.pause();
        if (animation != null) animation.stop();
        notifyPositionChanged();
    }
    public void seek(int seconds) {
        if (!isVideo()) return;
        rememberPosition();
        long next = (long) videoPosition + seconds * 1000L;
        videoPosition = (int) Math.max(0L, Math.min(next, videoDuration > 0 ? videoDuration - 1L : Integer.MAX_VALUE));
        if (prepared && player != null) { seeking = true; player.seekTo(videoPosition); }
        notifyPositionChanged();
    }
    public void step(int direction) {
        if (direction == 0) return;
        if (items.size() <= 1) {
            if (isVideo() && !items.get(itemIndex).loop) seek(direction > 0 ? 10 : -10);
            return;
        }
        int next = Math.max(0, Math.min(items.size() - 1, itemIndex + (direction > 0 ? 1 : -1)));
        if (next == itemIndex) return;
        itemIndex = next;
        showItem();
    }

    private void showItem() {
        generation++;
        cancelImageRequest();
        if (animation != null) animation.stop();
        animation = null;
        image.setImageDrawable(null);
        videoGeneration++;
        releaseVideo();
        playing = !items.isEmpty() && (!isVideo() || items.get(itemIndex).loop);
        unavailable = items.isEmpty();
        sourceIndex = videoPosition = videoDuration = videoWidth = videoHeight = 0;
        video.setTransform(new Matrix());
        image.setVisibility(isVideo() ? View.GONE : View.VISIBLE);
        video.setVisibility(isVideo() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) notice.setText("Media unavailable. Back to post.");
        else if (isVideo()) { notice.setText("Loading video\u2026"); openVideo(); }
        else if (isAttachedToWindow()) loadImage();
        else notice.setText("Loading image\u2026");
        notifyPositionChanged();
    }

    private void openVideo() {
        if (closed || !isVideo() || !isAttachedToWindow() || !video.isAvailable() || player != null) return;
        if (!video.isHardwareAccelerated()) { unavailable = true; notice.setText("Video unavailable. Back to post."); notifyPositionChanged(); return; }
        long operation = ++videoGeneration;
        prepared = false;
        seeking = false;
        notice.setText("Loading video\u2026");
        MediaPlayer opened = new MediaPlayer();
        player = opened;
        try {
            videoSurface = new Surface(video.getSurfaceTexture());
            opened.setSurface(videoSurface);
            opened.setOnPreparedListener(ready -> {
                if (!currentVideo(operation, ready)) return;
                prepared = true;
                videoDuration = Math.max(0, ready.getDuration());
                videoWidth = ready.getVideoWidth();
                videoHeight = ready.getVideoHeight();
                fitVideo();
                boolean gif = items.get(itemIndex).loop;
                ready.setLooping(gif);
                ready.setVolume(isMuted() ? 0f : 1f, isMuted() ? 0f : 1f);
                ready.setOnSeekCompleteListener(seeked -> { if (currentVideo(operation, seeked)) seeking = false; });
                videoPosition = Math.max(0, Math.min(videoPosition, videoDuration > 0 ? videoDuration - 1 : Integer.MAX_VALUE));
                seeking = true;
                ready.seekTo(videoPosition);
                if (playing) ready.start();
                unavailable = false;
                notice.setText("");
                schedulePlaybackTick();
                notifyPositionChanged();
            });
            opened.setOnVideoSizeChangedListener((resized, width, height) -> {
                if (!currentVideo(operation, resized)) return;
                videoWidth = width;
                videoHeight = height;
                fitVideo();
            });
            opened.setOnCompletionListener(completed -> {
                if (!currentVideo(operation, completed)) return;
                playing = false;
                videoPosition = 0;
                seeking = true;
                completed.seekTo(0);
                notice.setText("Tap for playback actions");
                notifyPositionChanged();
            });
            opened.setOnErrorListener((failed, what, extra) -> {
                if (currentVideo(operation, failed)) videoFailed(operation);
                return true;
            });
            opened.setDataSource(activity, Uri.parse(items.get(itemIndex).sources.get(sourceIndex)));
            opened.prepareAsync();
        } catch (Exception unavailable) {
            if (currentVideo(operation, opened)) videoFailed(operation);
        }
    }

    private boolean currentVideo(long operation, MediaPlayer candidate) {
        return !closed && operation == videoGeneration && player == candidate && isVideo() && isAttachedToWindow();
    }

    private void videoFailed(long operation) {
        releaseVideo();
        if (sourceIndex + 1 < items.get(itemIndex).sources.size()) {
            sourceIndex++;
            post(() -> { if (!closed && operation == videoGeneration && isVideo() && isAttachedToWindow()) openVideo(); });
        } else {
            playing = false;
            unavailable = true;
            notice.setText("Video unavailable. Back to post.");
            notifyPositionChanged();
        }
    }

    private void fitVideo() {
        int width = video.getWidth();
        int height = video.getHeight();
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return;
        float scale = Math.min((float) width / videoWidth, (float) height / videoHeight);
        Matrix transform = new Matrix();
        transform.setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f);
        video.setTransform(transform);
    }

    private void rememberPosition() {
        // The requested position stays authoritative until an asynchronous seek completes.
        if (prepared && isVideo() && player != null && !seeking) {
            try { videoPosition = Math.max(0, player.getCurrentPosition()); }
            catch (IllegalStateException unavailable) { /* Preserve the last confirmed or requested position. */ }
        }
    }

    private void releaseVideo() {
        removeCallbacks(playbackTick);
        MediaPlayer released = player;
        player = null;
        prepared = false;
        seeking = false;
        if (released != null) released.release();
        if (videoSurface != null) { videoSurface.release(); videoSurface = null; }
    }

    public void prepareForDetach() {
        if (closed) return;
        rememberPosition();
        videoGeneration++;
        releaseVideo();
        generation++;
        cancelImageRequest();
        if (animation != null) animation.stop();
    }

    @Override protected void onDetachedFromWindow() {
        prepareForDetach();
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        long operation = generation;
        long attachment = videoGeneration;
        post(() -> {
            if (closed || operation != generation || attachment != videoGeneration || !isAttachedToWindow()) return;
            if (isVideo()) openVideo();
            else if (animation != null) { if (playing) animation.start(); }
            else if (!items.isEmpty() && image.getDrawable() == null) loadImage();
        });
    }

    private void loadImage() {
        if (closed || items.isEmpty() || imageRequest != null || !isAttachedToWindow()) return;
        notice.setText("Loading image " + (itemIndex + 1) + " / " + items.size() + "\u2026");
        long operation = generation;
        List<String> sources = items.get(itemIndex).sources;
        imageRequest = worker.submit(() -> {
            Drawable decoded = null;
            for (String source : sources) {
                if (!currentImage(operation)) return;
                try {
                    byte[] data = download(source, operation);
                    if (!currentImage(operation)) return;
                    decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)), (decoder, info, origin) -> {
                        int width = info.getSize().getWidth();
                        int height = info.getSize().getHeight();
                        float ratio = Math.min(1f, Math.min(960f / width, 1280f / height));
                        decoder.setTargetSize(Math.max(1, Math.round(width * ratio)), Math.max(1, Math.round(height * ratio)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                    break;
                } catch (Exception unavailable) {
                    // Try only the selected native item's bounded CDN candidates, without logging URLs.
                }
            }
            Drawable loaded = decoded;
            activity.runOnUiThread(() -> {
                if (!currentImage(operation)) { if (loaded instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) loaded).stop(); return; }
                imageRequest = null;
                image.setImageDrawable(loaded);
                animation = loaded instanceof AnimatedImageDrawable ? (AnimatedImageDrawable) loaded : null;
                if (animation != null && playing && isAttachedToWindow()) animation.start();
                unavailable = loaded == null;
                notice.setText(unavailable ? "Image unavailable. Back to post." : "");
                notifyPositionChanged();
            });
        });
    }

    private boolean currentImage(long operation) {
        return !closed && operation == generation && !Thread.currentThread().isInterrupted();
    }

    private byte[] download(String source, long operation) throws Exception {
        URL next = new URL(source);
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!currentImage(operation) || !NativeMedia.allowed(next.toString())) throw new IllegalStateException("Media unavailable");
            HttpURLConnection request = (HttpURLConnection) next.openConnection();
            connection = request;
            request.setConnectTimeout(12000);
            request.setReadTimeout(12000);
            request.setInstanceFollowRedirects(false);
            try {
                int response = request.getResponseCode();
                if (response == 301 || response == 302 || response == 303 || response == 307 || response == 308) {
                    String location = request.getHeaderField("Location");
                    if (redirects == 3 || location == null) throw new IllegalStateException("Media unavailable");
                    next = new URL(next, location);
                    continue;
                }
                if (response != 200 || request.getContentLengthLong() > 12 * 1024 * 1024) throw new IllegalStateException("Media unavailable");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = request.getInputStream()) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        if (!currentImage(operation)) throw new IllegalStateException("Media closed");
                        if (bytes.size() + read > 12 * 1024 * 1024) throw new IllegalStateException("Image too large");
                        bytes.write(buffer, 0, read);
                    }
                }
                return bytes.toByteArray();
            } finally {
                request.disconnect();
                if (connection == request) connection = null;
            }
        }
        throw new IllegalStateException("Media unavailable");
    }

    private void cancelImageRequest() {
        if (imageRequest != null) { imageRequest.cancel(true); imageRequest = null; }
        if (connection != null) connection.disconnect();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        positionChanged = null;
        generation++;
        videoGeneration++;
        cancelImageRequest();
        worker.shutdownNow();
        playing = false;
        releaseVideo();
        if (animation != null) animation.stop();
        animation = null;
        image.setImageDrawable(null);
    }
}