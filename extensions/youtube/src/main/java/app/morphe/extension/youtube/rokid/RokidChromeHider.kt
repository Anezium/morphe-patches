/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Sets YouTube's top bar, tab bar and filter chips GONE while the patch is
 * attached and puts every touched view back on [restore]. Ids repeat across
 * fragments (a stale second app bar stays in the tree), so the whole window is
 * walked, and walked again on every global layout because YouTube re-shows
 * its chrome when fragments change.
 */
class RokidChromeHider(private val ids: Set<Int>) {
    private val previous = WeakHashMap<View, Int>()
    private var root: WeakReference<View> = WeakReference(null)
    private var observer: ViewTreeObserver? = null
    private var applying = false

    /** Views that must stay visible for now, e.g. the toolbar while search is open. */
    var exempt: Set<Int> = emptySet()
        set(value) {
            if (field != value) {
                field = value
                apply()
            }
        }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { apply() }

    fun bind(decor: View) {
        if (root.get() === decor && observer?.isAlive == true) {
            apply()
            return
        }
        unbindObserver()
        root = WeakReference(decor)
        observer = decor.viewTreeObserver.also { it.addOnGlobalLayoutListener(layoutListener) }
        apply()
    }

    fun apply() {
        val decor = root.get() ?: return
        if (applying || ids.isEmpty()) {
            return
        }
        applying = true
        try {
            walk(decor)
        } finally {
            applying = false
        }
    }

    fun restore() {
        unbindObserver()
        for ((view, visibility) in previous) {
            if (view != null && view.visibility != visibility) {
                view.visibility = visibility
            }
        }
        previous.clear()
        root = WeakReference(null)
    }

    private fun walk(view: View) {
        val id = view.id
        if (id != View.NO_ID && id in ids) {
            if (id in exempt) {
                previous.remove(view)?.let { original ->
                    if (view.visibility != original) {
                        view.visibility = original
                    }
                }
            } else {
                if (!previous.containsKey(view)) {
                    previous[view] = view.visibility
                }
                if (view.visibility != View.GONE) {
                    view.visibility = View.GONE
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i) ?: continue)
            }
        }
    }

    private fun unbindObserver() {
        observer?.let {
            if (it.isAlive) {
                it.removeOnGlobalLayoutListener(layoutListener)
            }
        }
        observer = null
    }
}
