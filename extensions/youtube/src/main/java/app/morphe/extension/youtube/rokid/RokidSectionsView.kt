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
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * Sections list that replaces the tab bar in glasses mode: one row per
 * section, the focused row ringed. Opaque black, so the feed underneath is
 * invisible on the optics. Geometry in mockup pixels (480 x 640, 240 dpi).
 */
class RokidSectionsView(context: Context) : View(context) {
    private var focusedIndex = 0

    private val rowHeight = dp(72f)
    private val rowGap = dp(6f)
    private val side = px(16f)
    private val listTop = dp(44f)
    private var listOffset = 0
    private val radius = px(10f)
    private val iconSize = px(24f)
    private val ringStroke = dp(3f)
    private val rowStroke = dp(1.5f)

    private val rowRect = RectF()
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
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        visibility = GONE
    }

    fun showAt(index: Int) {
        if (index != focusedIndex || visibility != VISIBLE) {
            focusedIndex = index
            visibility = VISIBLE
            invalidate()
        }
    }

    fun hideSections() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val viewport = (height - listTop - dp(RokidHudView.HINTS_DP)).toInt()
        listOffset = RokidSectionsState.scrollOffset(
            focusedIndex, (rowHeight + rowGap).toInt(), rowHeight.toInt(), viewport, listOffset,
        )
        canvas.save()
        canvas.clipRect(0f, listTop, width.toFloat(), listTop + viewport)
        RokidSection.entries.forEachIndexed { i, section ->
            val top = listTop + i * (rowHeight + rowGap) - listOffset
            rowRect.set(side, top, width - side, top + rowHeight)
            val focused = i == focusedIndex
            if (focused) {
                val inset = ringStroke / 2f
                canvas.drawRoundRect(
                    rowRect.left + inset,
                    rowRect.top + inset,
                    rowRect.right - inset,
                    rowRect.bottom - inset,
                    radius,
                    radius,
                    ringPaint,
                )
            } else {
                val inset = rowStroke / 2f
                canvas.drawRoundRect(
                    rowRect.left + inset,
                    rowRect.top + inset,
                    rowRect.right - inset,
                    rowRect.bottom - inset,
                    radius,
                    radius,
                    rowPaint,
                )
            }
            iconPaint.color = if (focused) Color.WHITE else ICON
            val iconLeft = rowRect.left + px(18f)
            canvas.save()
            canvas.translate(iconLeft, rowRect.centerY() - iconSize / 2f)
            canvas.scale(iconSize / 24f, iconSize / 24f)
            canvas.drawPath(iconFor(section), iconPaint)
            canvas.restore()
            labelPaint.color = if (focused) Color.WHITE else LABEL
            canvas.drawText(
                section.label,
                iconLeft + iconSize + px(16f),
                rowRect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint,
            )
        }
        canvas.restore()
    }

    private fun iconFor(section: RokidSection): Path {
        return when (section) {
            RokidSection.HOME -> HOME_ICON
            RokidSection.SUBSCRIPTIONS -> SUBSCRIPTIONS_ICON
            RokidSection.SEARCH -> SEARCH_ICON
            RokidSection.HISTORY -> HISTORY_ICON
            RokidSection.WATCH_LATER -> WATCH_LATER_ICON
            RokidSection.YOU -> YOU_ICON
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun px(value: Float): Float = dp(value / 1.5f)

    private companion object {
        const val ROW_OUTLINE = 0xFF3A3A3A.toInt()
        const val ICON = 0xFFBDBDBD.toInt()
        const val LABEL = 0xFFD6D6D6.toInt()

        // 24 x 24 icon paths from the mockup's SVGs.
        val HOME_ICON = Path().apply {
            moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f); lineTo(21f, 21f)
            lineTo(15f, 21f); lineTo(15f, 14f); lineTo(9f, 14f); lineTo(9f, 21f)
            lineTo(3f, 21f); close()
        }
        val SUBSCRIPTIONS_ICON = Path().apply {
            addRoundRect(RectF(3f, 7f, 21f, 21f), 2f, 2f, Path.Direction.CW)
            moveTo(7f, 3f); lineTo(12f, 7f); lineTo(17f, 3f)
            moveTo(10f, 12f); lineTo(15f, 15f); lineTo(10f, 18f); close()
        }
        val SEARCH_ICON = Path().apply {
            addCircle(11f, 11f, 7f, Path.Direction.CW)
            moveTo(20f, 20f); lineTo(16f, 16f)
        }
        val HISTORY_ICON = Path().apply {
            addCircle(12f, 12f, 9f, Path.Direction.CW)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
        val WATCH_LATER_ICON = Path().apply {
            moveTo(4f, 5f); lineTo(20f, 5f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 19f); lineTo(14f, 19f)
        }
        val YOU_ICON = Path().apply {
            addCircle(12f, 8f, 4f, Path.Direction.CW)
            moveTo(4f, 21f)
            arcTo(RectF(4f, 13f, 20f, 29f), 180f, 180f, false)
        }
    }
}
