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
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/**
 * Focus ring over the focused feed card. Reads the card's rect from the window
 * and draws on top; YouTube's own views are never touched. The rest of the
 * results list is dimmed so the next card only peeks.
 */
class RokidFocusRingView(context: Context) : View(context) {
    private var ringTarget: WeakReference<View> = WeakReference(null)
    private var card: WeakReference<View> = WeakReference(null)
    private var scope: WeakReference<View> = WeakReference(null)
    private var scrollObserver: ViewTreeObserver? = null

    private val ringRect = RectF()
    private val cardRect = RectF()
    private val scopeRect = RectF()
    private var hasGeometry = false

    private val ringStroke = dp(3f)
    private val ringOutset = dp(3f)
    private val ringRadius = dp(8f)
    private val location = IntArray(2)
    private val ownLocation = IntArray(2)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = ringStroke
    }
    private val scrimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = SCRIM_COLOR
    }

    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateGeometry()
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        updateGeometry()
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    /** Ring around [target], dim everything of [container] outside [cardView]. */
    fun follow(target: View, cardView: View, container: View) {
        if (ringTarget.get() !== target || card.get() !== cardView || scope.get() !== container) {
            unbind()
            ringTarget = WeakReference(target)
            card = WeakReference(cardView)
            scope = WeakReference(container)
            target.addOnLayoutChangeListener(layoutListener)
            if (cardView !== target) {
                cardView.addOnLayoutChangeListener(layoutListener)
            }
            scrollObserver = viewTreeObserver.also { it.addOnScrollChangedListener(scrollListener) }
        }
        visibility = VISIBLE
        updateGeometry()
    }

    fun hide() {
        unbind()
        hasGeometry = false
        visibility = GONE
    }

    fun isFollowing(view: View): Boolean = card.get() === view && visibility == VISIBLE

    override fun onDetachedFromWindow() {
        unbind()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasGeometry) {
            return
        }
        canvas.save()
        canvas.clipOutRect(cardRect)
        canvas.drawRect(scopeRect, scrimPaint)
        canvas.restore()
        canvas.drawRoundRect(ringRect, ringRadius, ringRadius, ringPaint)
    }

    private fun unbind() {
        ringTarget.get()?.removeOnLayoutChangeListener(layoutListener)
        card.get()?.removeOnLayoutChangeListener(layoutListener)
        scrollObserver?.let { observer ->
            if (observer.isAlive) {
                observer.removeOnScrollChangedListener(scrollListener)
            }
        }
        scrollObserver = null
        ringTarget = WeakReference(null)
        card = WeakReference(null)
        scope = WeakReference(null)
    }

    /**
     * Only reads geometry and invalidates. Never changes layout from a layout
     * callback: that re-enters the traversal that is running.
     */
    private fun updateGeometry() {
        val target = ringTarget.get()
        val cardView = card.get()
        val container = scope.get()
        if (
            target == null || cardView == null || container == null ||
            !target.isAttachedToWindow || !target.isShown ||
            cardView.parent !== container || width == 0
        ) {
            if (hasGeometry) {
                hasGeometry = false
                invalidate()
            }
            return
        }
        getLocationInWindow(ownLocation)
        val targetBox = boxOf(target)
        val cardBox = boxOf(cardView)
        val scopeBox = boxOf(container)
        val bounds = RokidBox(0, scopeBox.top.coerceAtLeast(0), width, height)
        val ring = RokidFeedGeometry.ringBox(
            targetBox,
            ringOutset.toInt(),
            ringStroke.toInt(),
            bounds,
        )
        ringRect.set(ring.left.toFloat(), ring.top.toFloat(), ring.right.toFloat(), ring.bottom.toFloat())
        cardRect.set(
            cardBox.left.toFloat(),
            (cardBox.top - ringOutset - ringStroke).coerceAtLeast(0f),
            cardBox.right.toFloat(),
            cardBox.bottom.toFloat(),
        )
        scopeRect.set(
            scopeBox.left.toFloat(),
            scopeBox.top.toFloat(),
            scopeBox.right.toFloat(),
            scopeBox.bottom.toFloat(),
        )
        hasGeometry = !ring.isEmpty
        invalidate()
    }

    private fun boxOf(view: View): RokidBox {
        view.getLocationInWindow(location)
        val left = location[0] - ownLocation[0]
        val top = location[1] - ownLocation[1]
        return RokidBox(left, top, left + view.width, top + view.height)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private companion object {
        /** Black at 65 %: the peeking card keeps 35 % of its light. */
        const val SCRIM_COLOR = 0xA6000000.toInt()
    }
}
