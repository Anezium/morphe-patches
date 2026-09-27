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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.widget.EditText
import java.lang.ref.WeakReference

/** Mirrors the native editor and suggestions; input and IME ownership stay in YouTube. */
internal class RokidSearchView(context: Context) : View(context) {
    private var editor = WeakReference<EditText>(null)
    private var suggestions = WeakReference<View>(null)
    private val density = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    private val panel = Paint(stroke).apply {
        color = 0xFF444444.toInt()
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16 * resources.displayMetrics.scaledDensity
    }
    private val dim = TextPaint(text).apply {
        color = 0xFFA3A3A3.toInt()
        textSize = 13 * resources.displayMetrics.scaledDensity
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun showSearch(field: EditText, results: View?) {
        editor = WeakReference(field)
        suggestions = WeakReference(results)
        visibility = VISIBLE
        invalidate()
    }

    fun hideSearch() {
        visibility = GONE
        editor.clear()
        suggestions.clear()
    }

    override fun onDraw(canvas: Canvas) {
        val field = editor.get()?.takeIf { it.isShown && it.isAttachedToWindow } ?: return
        val side = 12 * density
        val fieldTop = 40 * density
        canvas.drawRoundRect(RectF(side, fieldTop, width - side, fieldTop + 44 * density),
            8 * density, 8 * density, stroke)
        canvas.drawCircle(27 * density, fieldTop + 20 * density, 7 * density, stroke)
        canvas.drawLine(32 * density, fieldTop + 25 * density, 38 * density, fieldTop + 31 * density, stroke)
        canvas.save()
        canvas.clipRect(46 * density, fieldTop + 2 * density, width - 18 * density, fieldTop + 42 * density)
        canvas.translate(46 * density, fieldTop + (44 * density - field.height) / 2)
        field.draw(canvas)
        canvas.restore()

        val panelTop = 104 * density
        canvas.drawRoundRect(RectF(side, panelTop, width - side, panelTop + 82 * density),
            8 * density, 8 * density, panel)
        canvas.drawRoundRect(RectF(26 * density, panelTop + 22 * density,
            44 * density, panelTop + 58 * density), 3 * density, 3 * density, stroke)
        canvas.drawLine(32 * density, panelTop + 52 * density, 38 * density, panelTop + 52 * density, stroke)
        canvas.drawText("Type on your phone", 58 * density, panelTop + 24 * density, text)
        val message = "Open the Nexus keyboard on your phone, then press Search."
        val label = StaticLayout.Builder.obtain(message, 0, message.length, dim,
            (width - 76 * density).toInt().coerceAtLeast(1)).setIncludePad(false).build()
        canvas.save()
        canvas.translate(58 * density, panelTop + 33 * density)
        label.draw(canvas)
        canvas.restore()

        suggestions.get()?.takeIf { it.isShown && it.height > 0 }?.let {
            val top = 204 * density
            canvas.save()
            canvas.clipRect(0f, top, width.toFloat(), height - 36 * density)
            canvas.translate(0f, top)
            it.draw(canvas)
            canvas.restore()
        }
    }
}
