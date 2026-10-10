/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.nativebridge;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Resolves the inspected stock media models without a separate Reddit client. */
public final class NativeMedia {
    public static final class Item {
        public final boolean video;
        public final boolean loop;
        public final List<String> sources;
        private Item(boolean video, boolean loop, List<String> sources) {
            this.video = video;
            this.loop = loop;
            this.sources = Collections.unmodifiableList(sources);
        }
    }

    public static final class Thumbnail {
        public final String source;
        public final String label;
        public final int count;

        private Thumbnail(String source, String label, int count) {
            this.source = source;
            this.label = label;
            this.count = count;
        }
    }

    /** Uses static stock previews, never a playable video URL, for feed and post images. */
    public static Thumbnail thumbnail(Object post) throws ReflectiveOperationException {
        Object media = NativeCalls.read(post, "getMedia");
        Object preview = NativeCalls.read(post, "getPreview");
        Object video = media == null ? null : NativeCalls.read(media, "getRedditVideo");
        if (video == null && preview != null) video = NativeCalls.read(preview, "getRedditVideoPreview");
        Object gallery = NativeCalls.read(post, "getGallery");
        List<?> galleryItems = gallery == null ? Collections.emptyList() : list(NativeCalls.read(gallery, "getItems"));
        String source = "";
        if (!galleryItems.isEmpty()) {
            Object first = galleryItems.get(0);
            Map<?, ?> metadata = (Map<?, ?>) NativeCalls.read(post, "getMediaMetadata");
            Object entry = metadata == null ? null : metadata.get(NativeCalls.text(first, "getMediaId"));
            if (entry != null) {
                source = descriptorPreview(list(NativeCalls.read(entry, "getPreviewImageDescriptor")));
                Object descriptor = NativeCalls.read(entry, "getSourceImageDescriptor");
                if (source.isEmpty() && descriptor != null) source = firstStatic(
                        NativeCalls.text(descriptor, "getPreviewUrl"), NativeCalls.text(descriptor, "getImageUrl"),
                        NativeCalls.text(descriptor, "getGifUrl"));
            }
            if (source.isEmpty()) source = resolutionPreview(list(NativeCalls.read(first, "getResolutions")));
            if (source.isEmpty()) source = firstStatic(NativeCalls.text(first, "getUrl"));
        }
        List<?> previewImages = preview == null ? Collections.emptyList() : list(NativeCalls.read(preview, "getImages"));
        if (source.isEmpty() && !previewImages.isEmpty()) {
            Object first = previewImages.get(0);
            source = resolutionPreview(list(NativeCalls.read(first, "getResolutions")));
            Object original = NativeCalls.read(first, "getSource");
            if (source.isEmpty() && original != null) source = firstStatic(NativeCalls.text(original, "getUrl"));
        }
        if (source.isEmpty()) {
            Object thumbnailImage = NativeCalls.read(post, "getThumbnailImage");
            if (thumbnailImage != null) source = firstStatic(NativeCalls.text(thumbnailImage, "getUrl"));
            if (source.isEmpty()) source = firstStatic(NativeCalls.text(post, "getThumbnail"));
        }
        String direct = NativeCalls.text(post, "getUrl");
        boolean directImage = imagePath(direct);
        if (source.isEmpty() && directImage) source = firstStatic(direct);
        if (video != null) {
            boolean gif = Boolean.TRUE.equals(NativeCalls.read(video, "isGif"));
            Object duration = NativeCalls.read(video, "getDuration");
            int seconds = duration instanceof Number ? Math.max(0, ((Number) duration).intValue()) : 0;
            String label = gif ? "GIF" : "VIDEO";
            if (!gif && seconds > 0) label += " \u00b7 " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            return new Thumbnail(source, label, 1);
        }
        if (!galleryItems.isEmpty()) return new Thumbnail(source, "GALLERY \u00b7 " + galleryItems.size(), galleryItems.size());
        if (source.isEmpty()) return null;
        boolean gif = gifPath(direct);
        if (!previewImages.isEmpty()) {
            Object variants = NativeCalls.read(previewImages.get(0), "getVariants");
            if (variants != null) gif |= NativeCalls.read(variants, "getGif") != null || NativeCalls.read(variants, "getMp4") != null;
        }
        return new Thumbnail(source, gif ? "GIF" : "IMAGE", 1);
    }

    private static String resolutionPreview(List<?> resolutions) throws ReflectiveOperationException {
        String best = "";
        int bestWidth = 0;
        for (Object resolution : resolutions) {
            String candidate = firstStatic(NativeCalls.text(resolution, "getUrl"));
            int width = ((Number) NativeCalls.read(resolution, "getWidth")).intValue();
            if (!candidate.isEmpty() && preferWidth(width, bestWidth)) { best = candidate; bestWidth = width; }
        }
        return best;
    }

    private static String descriptorPreview(List<?> descriptors) throws ReflectiveOperationException {
        String best = "";
        int bestWidth = 0;
        for (Object descriptor : descriptors) {
            String candidate = firstStatic(NativeCalls.text(descriptor, "getPreviewUrl"), NativeCalls.text(descriptor, "getImageUrl"));
            Object widthValue = NativeCalls.read(descriptor, "getPreviewWidth");
            int width = widthValue instanceof Number ? ((Number) widthValue).intValue() : 0;
            if (!candidate.isEmpty() && (best.isEmpty() || preferWidth(width, bestWidth))) { best = candidate; bestWidth = width; }
        }
        return best;
    }

    private static boolean preferWidth(int width, int current) {
        return width > 0 && (current <= 0 || (width >= 320 && (current < 320 || width < current))
                || (width < 320 && current < 320 && width > current));
    }

    private static String firstStatic(String... candidates) {
        for (String value : candidates) if (staticAllowed(value)) return value.replace("&amp;", "&");
        return "";
    }

    private static boolean imagePath(String value) {
        String path = Uri.parse(value).getPath();
        return path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(gif|png|jpe?g|webp)$");
    }

    private static boolean gifPath(String value) {
        String path = Uri.parse(value).getPath();
        return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
    }

    public static boolean staticAllowed(String value) {
        if (!allowed(value)) return false;
        Uri uri = Uri.parse(value);
        String path = uri.getPath();
        return !"v.redd.it".equals(uri.getHost()) && (path == null
                || !path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m3u8|mpd|webm)$"));
    }
    private NativeMedia() {}

    public static List<Item> resolve(Object post) throws ReflectiveOperationException {
        List<Item> items = new ArrayList<>();
        Object media = NativeCalls.read(post, "getMedia");
        Object preview = NativeCalls.read(post, "getPreview");
        Object video = media == null ? null : NativeCalls.read(media, "getRedditVideo");
        if (video == null && preview != null) video = NativeCalls.read(preview, "getRedditVideoPreview");
        if (video != null) {
            addVideo(items, Boolean.TRUE.equals(NativeCalls.read(video, "isGif")),
                    NativeCalls.text(video, "getHlsUrl"), NativeCalls.text(video, "getFallBackUrl"),
                    NativeCalls.text(video, "getPackagedMp4Url"));
            if (!items.isEmpty()) return items;
        }
        Object gallery = NativeCalls.read(post, "getGallery");
        if (gallery != null) {
            Map<?, ?> metadata = (Map<?, ?>) NativeCalls.read(post, "getMediaMetadata");
            for (Object item : list(NativeCalls.read(gallery, "getItems"))) {
                Object entry = metadata == null ? null : metadata.get(NativeCalls.text(item, "getMediaId"));
                Object source = entry == null ? null : NativeCalls.read(entry, "getSourceImageDescriptor");
                if (source != null) {
                    int before = items.size();
                    addVideo(items, Boolean.TRUE.equals(NativeCalls.read(entry, "isGif")),
                            NativeCalls.text(source, "getMp4Url"));
                    if (items.size() > before) continue;
                    addImage(items, NativeCalls.text(source, "getGifUrl"), NativeCalls.text(source, "getImageUrl"),
                            NativeCalls.text(source, "getPreviewUrl"), NativeCalls.text(item, "getUrl"));
                } else addImage(items, NativeCalls.text(item, "getUrl"));
            }
            if (!items.isEmpty()) return items;
        }
        String direct = NativeCalls.text(post, "getUrl");
        String path = Uri.parse(direct).getPath();
        if (path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(gif|png|jpe?g|webp)$")) {
            addImage(items, direct);
            if (!items.isEmpty()) return items;
        }
        if (preview != null) for (Object image : list(NativeCalls.read(preview, "getImages"))) {
            Object variants = NativeCalls.read(image, "getVariants");
            Object gif = variants == null ? null : NativeCalls.read(variants, "getGif");
            Object gifSource = gif == null ? null : NativeCalls.read(gif, "getSource");
            Object mp4 = variants == null ? null : NativeCalls.read(variants, "getMp4");
            Object mp4Source = mp4 == null ? null : NativeCalls.read(mp4, "getSource");
            Object source = NativeCalls.read(image, "getSource");
            int before = items.size();
            if (gifSource != null) addImage(items, NativeCalls.text(gifSource, "getUrl"));
            if (items.size() == before && mp4Source != null) addVideo(items, true, NativeCalls.text(mp4Source, "getUrl"));
            if (items.size() == before && source != null) addImage(items, NativeCalls.text(source, "getUrl"));
        }
        return items;
    }

    private static List<?> list(Object value) {
        return value == null ? Collections.emptyList() : (List<?>) value;
    }

    private static void addVideo(List<Item> items, boolean loop, String... candidates) {
        List<String> sources = sources(candidates);
        if (!sources.isEmpty()) items.add(new Item(true, loop, sources));
    }

    private static void addImage(List<Item> items, String... candidates) {
        List<String> sources = sources(candidates);
        if (!sources.isEmpty()) items.add(new Item(false, false, sources));
    }

    private static List<String> sources(String... candidates) {
        List<String> sources = new ArrayList<>();
        for (String value : candidates) if (allowed(value)) {
            String url = value.replace("&amp;", "&");
            if (!sources.contains(url)) sources.add(url);
        }
        return sources;
    }

    public static boolean allowed(String value) {
        if (value == null || value.isEmpty()) return false;
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && host != null
                && (host.equals("i.redd.it") || host.equals("preview.redd.it")
                || host.equals("external-preview.redd.it") || host.equals("v.redd.it")
                || host.equals("i.imgur.com") || host.endsWith(".redditmedia.com"));
    }
}
