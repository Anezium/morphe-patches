/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.reddit.rokid.ui;

import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/** Maps media luminance to the HUD accent, preserving transparent and black pixels. */
final class MonochromeMedia {
    static final int ACCENT = 0xff70ff8b;
    private MonochromeMedia() {}

    static ColorMatrixColorFilter filter() {
        float red = 112f / 255f;
        float blue = 139f / 255f;
        return new ColorMatrixColorFilter(new float[]{
                .2126f * red, .7152f * red, .0722f * red, 0, 0,
                .2126f, .7152f, .0722f, 0, 0,
                .2126f * blue, .7152f * blue, .0722f * blue, 0, 0,
                0, 0, 0, 1, 0
        });
    }

    static Paint paint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(filter());
        return paint;
    }
}