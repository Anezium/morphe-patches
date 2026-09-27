/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.lang.ref.WeakReference

/** A single full-width video card, including when the backing list is a grid. */
internal class RokidFeedCardView(context: Context) : View(context) {
    private var thumbnail = WeakReference<View>(null)
    private var peek = WeakReference<View>(null)
    private var lines = emptyList<String>()
    private var title: StaticLayout? = null
    private var metadata: StaticLayout? = null
    private var textWidth = 0
    private var duration: String? = null
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 21 * scaledDensity
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA3A3A3.toInt()
        textSize = 13 * scaledDensity
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12 * scaledDensity
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun showCard(item: View, next: View?): Boolean {
        val image = RokidFeedCardContent.thumbnail(item) ?: return false
        val text = RokidFeedCardContent.textOutsideThumbnail(item, image)
        if (text.isEmpty()) return false
        thumbnail = WeakReference(image)
        duration = RokidFeedCardContent.duration(image)
        peek = WeakReference<View>(next?.let { RokidFeedCardContent.thumbnail(it) })
        if (lines != text) {
            lines = text
            textWidth = 0
        }
        visibility = VISIBLE
        invalidate()
        return true
    }

    fun hideCard() {
        visibility = GONE
        thumbnail.clear()
        peek.clear()
        lines = emptyList()
        title = null
        metadata = null
        textWidth = 0
        duration = null
    }

    override fun onDraw(canvas: Canvas) {
        val image = thumbnail.get()?.takeIf { it.isShown && it.isAttachedToWindow } ?: return
        val inset = 16 * density
        val top = 42 * density
        val cardWidth = width - 2 * inset
        if (cardWidth <= 0) return
        val imageHeight = cardWidth * 9 / 16
        drawThumbnail(canvas, image, inset, top, cardWidth)
        duration?.let {
            val right = width - inset - 6 * density
            val bottom = top + imageHeight - 6 * density
            val left = right - durationPaint.measureText(it) - 8 * density
            canvas.drawRoundRect(RectF(left, bottom - 21 * density, right, bottom),
                3 * density, 3 * density, badgePaint)
            canvas.drawText(it, left + 4 * density, bottom - 5 * density, durationPaint)
        }
        val ring = RectF(inset - 3 * density, top - 3 * density,
            width - inset + 3 * density, top + imageHeight + 3 * density)
        canvas.drawRoundRect(ring, 8 * density, 8 * density, stroke)
        if (textWidth != cardWidth.toInt()) {
            textWidth = cardWidth.toInt()
            title = layout(lines.firstOrNull().orEmpty(), titlePaint, 2)
            metadata = layout(lines.drop(1).joinToString(" · "), metaPaint, 2)
        }
        var y = top + imageHeight + 14 * density
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height - 36 * density)
        title?.let {
            canvas.save()
            canvas.translate(inset, y)
            it.draw(canvas)
            canvas.restore()
            y += it.height + 8 * density
        }
        metadata?.let {
            canvas.save()
            canvas.translate(inset, y)
            it.draw(canvas)
            canvas.restore()
            y += it.height + 24 * density
        }
        peek.get()?.takeIf { it.isShown && it.isAttachedToWindow }?.let {
            canvas.saveLayerAlpha(0f, y, width.toFloat(), height.toFloat(), 89)
            drawThumbnail(canvas, it, inset, y, cardWidth)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun layout(text: String, paint: TextPaint, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).build()

    private fun drawThumbnail(canvas: Canvas, image: View, left: Float, top: Float, targetWidth: Float) {
        if (image.width <= 0 || image.height <= 0) return
        canvas.save()
        canvas.translate(left, top)
        val scale = targetWidth / image.width
        canvas.scale(scale, scale)
        image.draw(canvas)
        canvas.restore()
    }
}
