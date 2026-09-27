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
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Transparent outline player rail for 480x640 glasses HUDs. No filled panels.
 */
class RokidPlayerRailView(context: Context) : LinearLayout(context) {
    private val buttons = mutableListOf<RailButton>()

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Color.TRANSPARENT)
        val pad = dp(8f).toInt()
        setPadding(pad, pad, pad, pad)
        RokidRailItem.entries.forEach { item ->
            val button = RailButton(context).also { it.item = item }
            val params = LayoutParams(0, dp(44f).toInt(), 1f)
            params.marginStart = dp(4f).toInt()
            params.marginEnd = dp(4f).toInt()
            addView(button, params)
            buttons.add(button)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        visibility = GONE
    }

    fun showAt(
        index: Int,
        playPauseLabel: String,
        playPauseAvailable: Boolean,
        seekBackLabel: String,
        seekForwardLabel: String,
        seekAvailable: Boolean,
    ) {
        visibility = VISIBLE
        buttons.forEachIndexed { i, button ->
            button.highlighted = i == index
            when (button.item) {
                RokidRailItem.PLAY_PAUSE -> {
                    button.setEnabled(playPauseAvailable)
                    button.caption = playPauseLabel
                }
                RokidRailItem.SEEK_BACK -> {
                    button.setEnabled(seekAvailable)
                    button.caption = seekBackLabel
                }
                RokidRailItem.SEEK_FORWARD -> {
                    button.setEnabled(seekAvailable)
                    button.caption = seekForwardLabel
                }
                RokidRailItem.FULLSCREEN -> {
                    button.setEnabled(true)
                    button.caption = "Full"
                }
                RokidRailItem.BACK -> {
                    button.setEnabled(true)
                    button.caption = "Back"
                }
            }
            button.setTextColor(if (button.isEnabled) Color.WHITE else 0x80FFFFFF.toInt())
            button.invalidate()
        }
    }

    fun hideRail() {
        visibility = GONE
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
    }

    private class RailButton(context: Context) : TextView(context) {
        var item: RokidRailItem = RokidRailItem.PLAY_PAUSE
        // Must not be named selected/isSelected: that emits setSelected(Z)V and
        // accidentally overrides View.setSelected.
        var highlighted: Boolean = false
        var caption: String = ""
            set(value) {
                field = value
                text = value
            }

        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val bounds = RectF()

        init {
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val stroke = if (highlighted) dp(2f) else dp(1f)
            outline.strokeWidth = stroke
            outline.color = when {
                !isEnabled() -> 0x66FFFFFF.toInt()
                highlighted -> Color.WHITE
                else -> 0xB3FFFFFF.toInt()
            }
            val inset = stroke / 2f + dp(1f)
            bounds.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(bounds, dp(4f), dp(4f), outline)
            super.onDraw(canvas)
        }

        private fun dp(value: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                resources.displayMetrics,
            )
        }
    }
}
