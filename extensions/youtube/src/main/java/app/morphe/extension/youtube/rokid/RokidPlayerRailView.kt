/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.animation.ValueAnimator
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
 * Glasses player overlay: six icon keys above the hints strip, a seek bar and
 * time on the bottom edge of the video, and in fullscreen a transient pill.
 * Only the focused key is filled and labelled.
 *
 * Geometry follows the mockup, whose frames are device pixels of the
 * 480 x 640, 240 dpi canvas: an 80 px key is 53.3 dp. Five 80 dp keys would
 * need 400 dp on a 320 dp wide screen. The options key made six, so keys
 * shrank to 68 px to keep both side margins.
 */
class RokidPlayerRailView(context: Context) : View(context) {
    private enum class Mode {
        HIDDEN,
        RAIL,
        FULLSCREEN,
    }

    private var mode = Mode.HIDDEN
    private var railIndex = 0
    private var playing = false
    private var playPauseLabel = ""
    private var playPauseAvailable = false
    private var seekAvailable = false
    private var fullscreenLabel = "Fullscreen"
    private var videoBox: RokidBox? = null
    private var metadataBottom: Int? = null
    private var timeMs = -1L
    private var lengthMs = -1L

    private var keysAlpha = 0f
    private var keysShown = false
    private var fader: ValueAnimator? = null

    private val keySize = px(68f)
    private val keyRadius = px(14f)
    private val sideMargin = px(16f)
    private val iconSize = px(30f)
    private val keyStroke = dp(1.5f)
    private val hintsHeight = dp(RokidHudView.HINTS_DP)

    private val keyRect = RectF()
    private val pillRect = RectF()
    private val barRect = RectF()

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = keyStroke
        color = OUTLINE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // Mockup icons: stroke 2.2 in a 24 unit box.
        strokeWidth = 2.2f
    }
    private val labelPaint = monoPaint(11f, Color.WHITE).apply {
        letterSpacing = 0.12f
        textAlign = Paint.Align.CENTER
    }
    private val numberPaint = monoPaint(9f, ICON).apply { textAlign = Paint.Align.CENTER }
    private val timePaint = monoPaint(11f, Color.WHITE)
    private val ghostPaint = monoPaint(11f, MUTE).apply { letterSpacing = 0.14f }
    private val trackPaint = Paint().apply {
        style = Paint.Style.FILL
        color = TRACK
    }
    private val playedPaint = Paint().apply {
        style = Paint.Style.FILL
        color = RokidHudView.ACCENT
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = PILL_BACKGROUND
    }
    private val backgroundPaint = Paint().apply { color = Color.BLACK }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        visibility = GONE
    }

    /**
     * Watch page. [videoBox] is the player's rect in this view's coordinates,
     * null while it is not laid out.
     */
    @Suppress("LongParameterList")
    fun showRail(
        index: Int,
        keysVisible: Boolean,
        playing: Boolean,
        playPauseLabel: String,
        playPauseAvailable: Boolean,
        seekAvailable: Boolean,
        fullscreenLabel: String,
        videoBox: RokidBox?,
        metadataBottom: Int?,
        timeMs: Long,
        lengthMs: Long,
    ) {
        mode = Mode.RAIL
        railIndex = index
        this.playing = playing
        this.playPauseLabel = playPauseLabel
        this.playPauseAvailable = playPauseAvailable
        this.seekAvailable = seekAvailable
        this.fullscreenLabel = fullscreenLabel
        this.videoBox = videoBox
        this.metadataBottom = metadataBottom
        this.timeMs = timeMs
        this.lengthMs = lengthMs
        setKeysShown(keysVisible)
        visibility = VISIBLE
        invalidate()
    }

    /** Fullscreen: no rail, only the pill for a moment after input. */
    fun showFullscreen(pillVisible: Boolean, playing: Boolean, timeMs: Long, lengthMs: Long) {
        mode = Mode.FULLSCREEN
        this.playing = playing
        this.timeMs = timeMs
        this.lengthMs = lengthMs
        videoBox = null
        setKeysShown(pillVisible)
        visibility = VISIBLE
        invalidate()
    }

    fun hideRail() {
        mode = Mode.HIDDEN
        fader?.cancel()
        keysShown = false
        keysAlpha = 0f
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        fader?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        when (mode) {
            Mode.RAIL -> {
                val top = metadataBottom ?: videoBox?.bottom
                if (top != null) {
                    canvas.drawRect(0f, top.toFloat(), width.toFloat(), height.toFloat(), backgroundPaint)
                }
                drawSeekBar(canvas)
                if (keysAlpha > 0f) {
                    drawKeys(canvas)
                }
            }
            Mode.FULLSCREEN -> if (keysAlpha > 0f) {
                drawPill(canvas)
            }
            Mode.HIDDEN -> Unit
        }
    }

    /** Fades out, appears at once: a key press must show the rail immediately. */
    private fun setKeysShown(shown: Boolean) {
        if (shown == keysShown) {
            return
        }
        keysShown = shown
        fader?.cancel()
        if (shown) {
            keysAlpha = 1f
            return
        }
        fader = ValueAnimator.ofFloat(keysAlpha, 0f).apply {
            duration = FADE_MS
            addUpdateListener {
                keysAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun drawKeys(canvas: Canvas) {
        val count = RokidRailItem.entries.size
        val gap = (width - sideMargin * 2 - keySize * count) / (count - 1)
        val labelSpace = px(26f)
        val bottom = height - hintsHeight - labelSpace
        val top = bottom - keySize
        RokidRailItem.entries.forEachIndexed { i, item ->
            val left = sideMargin + i * (keySize + gap)
            keyRect.set(left, top, left + keySize, bottom)
            val focused = i == railIndex
            val available = when (item) {
                RokidRailItem.PLAY_PAUSE -> playPauseAvailable
                RokidRailItem.SEEK_BACK, RokidRailItem.SEEK_FORWARD -> seekAvailable
                else -> true
            }
            val alpha = keysAlpha * if (available) 1f else UNAVAILABLE_ALPHA
            drawKey(canvas, item, focused, alpha)
            if (focused) {
                labelPaint.alpha = (255 * keysAlpha).toInt()
                val text = labelFor(item).uppercase()
                val half = labelPaint.measureText(text) / 2f
                val x = keyRect.centerX().coerceIn(half + px(4f), width - half - px(4f))
                canvas.drawText(text, x, bottom + px(8f) - labelPaint.ascent(), labelPaint)
            }
        }
    }

    private fun drawKey(canvas: Canvas, item: RokidRailItem, focused: Boolean, alpha: Float) {
        val a = (255 * alpha).toInt()
        val inset = keyStroke / 2f
        if (focused) {
            fillPaint.alpha = a
            canvas.drawRoundRect(keyRect, keyRadius, keyRadius, fillPaint)
        } else {
            outlinePaint.alpha = a
            canvas.drawRoundRect(
                keyRect.left + inset,
                keyRect.top + inset,
                keyRect.right - inset,
                keyRect.bottom - inset,
                keyRadius,
                keyRadius,
                outlinePaint,
            )
        }
        val iconColor = if (focused) Color.BLACK else ICON
        iconPaint.color = iconColor
        iconPaint.alpha = a
        val hasNumber = item == RokidRailItem.SEEK_BACK || item == RokidRailItem.SEEK_FORWARD
        val numberHeight = if (hasNumber) px(6f) + numberPaint.textSize else 0f
        val iconTop = keyRect.centerY() - (iconSize + numberHeight) / 2f
        drawIcon(canvas, iconFor(item), keyRect.centerX() - iconSize / 2f, iconTop, iconSize)
        if (hasNumber) {
            numberPaint.color = iconColor
            numberPaint.alpha = a
            canvas.drawText(
                "10",
                keyRect.centerX(),
                iconTop + iconSize + px(6f) - numberPaint.ascent(),
                numberPaint,
            )
        }
    }

    private fun drawSeekBar(canvas: Canvas) {
        val box = videoBox ?: return
        if (box.isEmpty) {
            return
        }
        val barHeight = dp(5f)
        barRect.set(box.left.toFloat(), box.bottom - barHeight, box.right.toFloat(), box.bottom.toFloat())
        canvas.drawRect(barRect, trackPaint)
        val played = barRect.left + barRect.width() * RokidRailLabels.playedFraction(timeMs, lengthMs)
        canvas.drawRect(barRect.left, barRect.top, played, barRect.bottom, playedPaint)
        canvas.drawCircle(played.coerceAtLeast(barRect.left + px(6.5f)), barRect.centerY(), px(6.5f), knobPaint)

        val text = RokidRailLabels.timeLabel(timeMs, lengthMs)
        val padX = px(8f)
        val padY = px(3f)
        val textWidth = timePaint.measureText(text)
        val textHeight = timePaint.descent() - timePaint.ascent()
        val right = box.right - px(12f)
        val bottom = barRect.top - px(8f)
        pillRect.set(right - textWidth - padX * 2, bottom - textHeight - padY * 2, right, bottom)
        canvas.drawRoundRect(pillRect, px(4f), px(4f), scrimPaint)
        canvas.drawText(text, pillRect.left + padX, pillRect.top + padY - timePaint.ascent(), timePaint)
    }

    private fun drawPill(canvas: Canvas) {
        val a = (255 * keysAlpha).toInt()
        ghostPaint.alpha = a
        canvas.drawText("FULLSCREEN", px(16f), px(14f) - ghostPaint.ascent(), ghostPaint)

        val icon = px(22f)
        val barWidth = px(180f)
        val gap = px(14f)
        val text = RokidRailLabels.clock(timeMs)
        timePaint.alpha = a
        val textWidth = timePaint.measureText(text)
        val pillWidth = px(14f) + icon + gap + barWidth + gap + textWidth + px(18f)
        val pillHeight = icon + px(20f)
        val left = (width - pillWidth) / 2f
        val bottom = height - hintsHeight - px(60f)
        pillRect.set(left, bottom - pillHeight, left + pillWidth, bottom)
        scrimPaint.alpha = (Color.alpha(PILL_BACKGROUND) * keysAlpha).toInt()
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, scrimPaint)
        scrimPaint.alpha = Color.alpha(PILL_BACKGROUND)
        outlinePaint.alpha = a
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, outlinePaint)

        iconPaint.color = Color.WHITE
        iconPaint.alpha = a
        var x = left + px(14f)
        drawIcon(canvas, if (playing) PAUSE_ICON else PLAY_ICON, x, pillRect.centerY() - icon / 2f, icon)
        x += icon + gap
        val barHeight = px(4f)
        barRect.set(x, pillRect.centerY() - barHeight / 2f, x + barWidth, pillRect.centerY() + barHeight / 2f)
        trackPaint.alpha = (Color.alpha(TRACK) * keysAlpha).toInt()
        canvas.drawRoundRect(barRect, barHeight / 2f, barHeight / 2f, trackPaint)
        trackPaint.alpha = Color.alpha(TRACK)
        playedPaint.alpha = a
        val played = barRect.left + barRect.width() * RokidRailLabels.playedFraction(timeMs, lengthMs)
        canvas.drawRect(barRect.left, barRect.top, played, barRect.bottom, playedPaint)
        playedPaint.alpha = 255
        x += barWidth + gap
        canvas.drawText(text, x, pillRect.centerY() - (timePaint.ascent() + timePaint.descent()) / 2f, timePaint)
        timePaint.alpha = 255
    }

    private fun drawIcon(canvas: Canvas, icon: Path, left: Float, top: Float, size: Float) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / 24f, size / 24f)
        canvas.drawPath(icon, iconPaint)
        canvas.restore()
    }

    private fun iconFor(item: RokidRailItem): Path {
        return when (item) {
            RokidRailItem.PLAY_PAUSE -> if (playing) PAUSE_ICON else PLAY_ICON
            RokidRailItem.SEEK_BACK -> SEEK_BACK_ICON
            RokidRailItem.SEEK_FORWARD -> SEEK_FORWARD_ICON
            RokidRailItem.FULLSCREEN -> FULLSCREEN_ICON
            RokidRailItem.OPTIONS -> OPTIONS_ICON
            RokidRailItem.BACK -> CLOSE_ICON
        }
    }

    private fun labelFor(item: RokidRailItem): String {
        return when (item) {
            RokidRailItem.PLAY_PAUSE -> playPauseLabel
            RokidRailItem.SEEK_BACK -> RokidRailLabels.seek(backward = true, available = seekAvailable)
            RokidRailItem.SEEK_FORWARD -> RokidRailLabels.seek(backward = false, available = seekAvailable)
            RokidRailItem.FULLSCREEN -> fullscreenLabel
            RokidRailItem.OPTIONS -> "Options"
            RokidRailItem.BACK -> "Close"
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

    /** Mockup pixel: device pixel of the 240 dpi glasses canvas. */
    private fun px(value: Float): Float = dp(value / MOCKUP_DENSITY)

    private companion object {
        const val MOCKUP_DENSITY = 1.5f
        const val FADE_MS = 300L
        const val UNAVAILABLE_ALPHA = 0.3f
        const val OUTLINE = 0xFF5E5E5E.toInt()
        const val ICON = 0xFFD0D0D0.toInt()
        const val MUTE = 0xFF5E5E5E.toInt()
        const val TRACK = 0x38FFFFFF
        const val PILL_BACKGROUND = 0xB8000000.toInt()

        // Icon paths in a 24 x 24 box, from the mockup's SVGs.
        val PAUSE_ICON = Path().apply {
            moveTo(7f, 5f)
            lineTo(7f, 19f)
            moveTo(17f, 5f)
            lineTo(17f, 19f)
        }
        val PLAY_ICON = Path().apply {
            moveTo(8f, 5f)
            lineTo(8f, 19f)
            lineTo(19f, 12f)
            close()
        }
        val SEEK_BACK_ICON = Path().apply {
            // a8 8 0 1 0 from (4,12) to (7,5.8): counter-clockwise the long way.
            arcTo(RectF(4f, 4f, 20f, 20f), 180f, -308.9f, true)
            moveTo(4f, 4f)
            lineTo(4f, 9f)
            lineTo(9f, 9f)
        }
        val SEEK_FORWARD_ICON = Path().apply {
            arcTo(RectF(4f, 4f, 20f, 20f), 0f, 308.9f, true)
            moveTo(20f, 4f)
            lineTo(20f, 9f)
            lineTo(15f, 9f)
        }
        val FULLSCREEN_ICON = Path().apply {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(20f, 9f); lineTo(20f, 4f); lineTo(15f, 4f)
            moveTo(4f, 15f); lineTo(4f, 20f); lineTo(9f, 20f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        }
        val OPTIONS_ICON = Path().apply {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 18f); lineTo(20f, 18f)
            addCircle(9f, 6f, 2.2f, Path.Direction.CW)
            addCircle(15f, 12f, 2.2f, Path.Direction.CW)
            addCircle(8f, 18f, 2.2f, Path.Direction.CW)
        }
        val CLOSE_ICON = Path().apply {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }
}
