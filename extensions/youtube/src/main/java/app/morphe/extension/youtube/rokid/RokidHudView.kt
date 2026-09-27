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
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * Glasses HUD bands: a header with the section tag and the "n / N" counter,
 * and the hints strip at the bottom. Bands are opaque black, which is
 * transparent on the glasses optics.
 */
class RokidHudView(context: Context) : View(context) {
    private var header = false
    private var faded = false
    private var tag: String? = null
    private var counter: String? = null
    private var hints: List<RokidHint> = emptyList()

    val headerHeight: Int = dp(HEADER_DP).toInt()
    val hintsHeight: Int = dp(HINTS_DP).toInt()

    private val bandPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val tagPaint = monoPaint(12f, DIM).apply { letterSpacing = 0.18f }
    private val counterPaint = monoPaint(12f, DIM).apply {
        letterSpacing = 0.1f
        textAlign = Paint.Align.RIGHT
    }
    private val glyphPaint = monoPaint(11.5f, Color.WHITE)
    private val hintPaint = monoPaint(11.5f, DIM).apply { letterSpacing = 0.04f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ACCENT
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun show(header: Boolean, tag: String?, counter: String?, hints: List<RokidHint>) {
        val changed = header != this.header || tag != this.tag || counter != this.counter ||
            hints != this.hints
        this.header = header
        this.tag = tag
        this.counter = counter
        this.hints = hints
        val shouldShow = header || hints.isNotEmpty()
        visibility = if (shouldShow) VISIBLE else GONE
        if (changed) {
            invalidate()
        }
    }

    fun hideHud() {
        visibility = GONE
    }

    /** Fades out with the player rail; comes back at once with the next key. */
    fun setFaded(faded: Boolean) {
        if (faded == this.faded) {
            return
        }
        this.faded = faded
        animate().cancel()
        if (faded) {
            animate().alpha(0f).setDuration(FADE_MS).start()
        } else {
            alpha = 1f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val side = dp(16f)
        if (header) {
            canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), bandPaint)
            val baseline = headerHeight / 2f - (tagPaint.ascent() + tagPaint.descent()) / 2f
            tag?.let { text ->
                val radius = dp(3f)
                canvas.drawCircle(side + radius, headerHeight / 2f, radius, dotPaint)
                canvas.drawText(text.uppercase(), side + radius * 2 + dp(6f), baseline, tagPaint)
            }
            counter?.let { text ->
                canvas.drawText(text, width - side, baseline, counterPaint)
            }
        }
        if (hints.isNotEmpty()) {
            val top = (height - hintsHeight).toFloat()
            canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), bandPaint)
            val gap = dp(14f)
            val glyphGap = dp(4f)
            var total = 0f
            hints.forEachIndexed { i, hint ->
                total += glyphPaint.measureText(hint.glyph) + glyphGap + hintPaint.measureText(hint.label)
                if (i > 0) total += gap
            }
            var x = (width - total) / 2f
            val baseline = top + hintsHeight / 2f - (hintPaint.ascent() + hintPaint.descent()) / 2f
            hints.forEach { hint ->
                canvas.drawText(hint.glyph, x, baseline, glyphPaint)
                x += glyphPaint.measureText(hint.glyph) + glyphGap
                canvas.drawText(hint.label, x, baseline, hintPaint)
                x += hintPaint.measureText(hint.label) + gap
            }
        }
    }

    private fun monoPaint(sp: Float, color: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            this.color = color
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    companion object {
        const val HEADER_DP = 36f
        const val HINTS_DP = 36f
        const val FADE_MS = 300L
        const val DIM = 0xFFA3A3A3.toInt()
        const val ACCENT = 0xFFFF0033.toInt()
    }
}
