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
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.View

/**
 * Player options panel: a titled list under the video, the focused row
 * ringed, the active choice of a sub-list marked with the accent dot. Opaque
 * black from [top] down, so the video above stays visible while choosing.
 */
class RokidOptionsView(context: Context) : View(context) {
    private var title = ""
    private var rows: List<RokidOptionRow> = emptyList()
    private var focusedIndex = 0
    private var panelTop = 0
    private var listOffset = 0

    private val rowHeight = dp(44f)
    private val rowGap = dp(4f)
    private val side = px(16f)
    private val radius = px(10f)
    private val ringStroke = dp(3f)
    private val rowStroke = dp(1.5f)

    private val rowRect = RectF()
    private val backgroundPaint = Paint().apply { color = Color.BLACK }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rowStroke
        color = ROW_OUTLINE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        color = Color.WHITE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = RokidHudView.ACCENT
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(11f)
        letterSpacing = 0.14f
        color = DIM
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = sp(16f)
    }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(13f)
        textAlign = Paint.Align.RIGHT
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        visibility = GONE
    }

    /** [top] is the panel's top edge in this view, usually the video's bottom. */
    fun showOptions(title: String, rows: List<RokidOptionRow>, focused: Int, top: Int) {
        if (title != this.title) listOffset = 0
        this.title = title
        this.rows = rows
        focusedIndex = focused
        panelTop = top.coerceIn(0, height.coerceAtLeast(0))
        visibility = VISIBLE
        invalidate()
    }

    fun hideOptions() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = panelTop.toFloat()
        val bottom = height - dp(RokidHudView.HINTS_DP)
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), backgroundPaint)
        val titleTop = top + px(12f)
        canvas.drawText(title.uppercase(), side, titleTop - titlePaint.ascent(), titlePaint)
        val listTop = titleTop + titlePaint.descent() - titlePaint.ascent() + px(10f)
        val viewport = (bottom - listTop).toInt()
        listOffset = RokidSectionsState.scrollOffset(
            focusedIndex, (rowHeight + rowGap).toInt(), rowHeight.toInt(), viewport, listOffset, rows.lastIndex,
        )
        canvas.save()
        canvas.clipRect(0f, listTop, width.toFloat(), bottom)
        rows.forEachIndexed { i, row ->
            val rowTop = listTop + i * (rowHeight + rowGap) - listOffset
            rowRect.set(side, rowTop, width - side, rowTop + rowHeight)
            drawRow(canvas, row, i == focusedIndex)
        }
        canvas.restore()
    }

    private fun drawRow(canvas: Canvas, row: RokidOptionRow, focused: Boolean) {
        val stroke = if (focused) ringStroke else rowStroke
        val inset = stroke / 2f
        canvas.drawRoundRect(
            rowRect.left + inset,
            rowRect.top + inset,
            rowRect.right - inset,
            rowRect.bottom - inset,
            radius,
            radius,
            if (focused) ringPaint else rowPaint,
        )
        val alpha = if (row.enabled) 255 else UNAVAILABLE_ALPHA
        var textLeft = rowRect.left + px(18f)
        if (row.current) {
            canvas.drawCircle(textLeft + px(5f), rowRect.centerY(), px(5f), dotPaint)
            textLeft += px(20f)
        }
        val textRight = rowRect.right - px(18f)
        val baseline = { paint: Paint -> rowRect.centerY() - (paint.ascent() + paint.descent()) / 2f }
        var labelRight = textRight
        row.value?.let { value ->
            valuePaint.color = if (focused) Color.WHITE else DIM
            valuePaint.alpha = alpha
            val shown = TextUtils.ellipsize(value, valuePaint, (textRight - textLeft) * 0.55f, TextUtils.TruncateAt.END)
            canvas.drawText(shown, 0, shown.length, textRight, baseline(valuePaint), valuePaint)
            labelRight = textRight - valuePaint.measureText(shown, 0, shown.length) - px(12f)
        }
        labelPaint.color = if (focused) Color.WHITE else LABEL
        labelPaint.alpha = alpha
        val label = TextUtils.ellipsize(row.label, labelPaint, labelRight - textLeft, TextUtils.TruncateAt.END)
        canvas.drawText(label, 0, label.length, textLeft, baseline(labelPaint), labelPaint)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun px(value: Float): Float = dp(value / 1.5f)

    private companion object {
        const val ROW_OUTLINE = 0xFF3A3A3A.toInt()
        const val LABEL = 0xFFD6D6D6.toInt()
        const val DIM = 0xFFA3A3A3.toInt()
        const val UNAVAILABLE_ALPHA = 77
    }
}
