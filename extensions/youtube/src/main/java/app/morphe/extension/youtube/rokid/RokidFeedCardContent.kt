/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** Read the mounted card without changing Litho's layout or its click target. */
internal object RokidFeedCardContent {
    private val hostType by lazy { Class.forName("com.facebook.litho.ComponentHost") }
    private val textContent by lazy { hostType.getMethod("getTextContent") }
    private val textItems by lazy {
        Class.forName("com.facebook.litho.TextContent").getMethod("getTextItems")
    }
    private val durationPattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")

    private fun text(view: View): List<String> = try {
        val values = when {
            view is TextView -> listOf(view.text)
            hostType.isInstance(view) -> textItems.invoke(textContent.invoke(view)) as? List<*>
            else -> null
        }
        values?.filterIsInstance<CharSequence>()?.map { it.toString().trim() }
            ?.filter { it.isNotEmpty() }.orEmpty()
    } catch (_: ReflectiveOperationException) {
        emptyList()
    }

    fun duration(image: View): String? {
        val host = (image.parent as? ViewGroup)?.takeIf {
            it.width == image.width && it.height == image.height
        } ?: return null
        fun find(view: View): String? {
            text(view).firstOrNull { durationPattern.matches(it) }?.let { return it }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return find(host)
    }

    fun thumbnail(view: View): ImageView? {
        if (!view.isShown) return null
        if (view is ImageView && view.drawable != null &&
            RokidFeedGeometry.isVideoThumbnail(view.width, view.height)
        ) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) thumbnail(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    fun textOutsideThumbnail(item: View, thumbnail: View): List<String> {
        val location = IntArray(2)
        thumbnail.getLocationInWindow(location)
        val imageLeft = location[0]
        val imageRight = location[0] + thumbnail.width
        val below = location[1] + thumbnail.height
        val result = linkedSetOf<String>()
        fun visit(view: View) {
            if (!view.isShown || view === thumbnail) return
            view.getLocationInWindow(location)
            if (location[1] >= below || location[0] >= imageRight ||
                location[0] + view.width <= imageLeft
            ) {
                result.addAll(text(view))
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(item)
        return result.toList()
    }
}
