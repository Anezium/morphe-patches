/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.shared.NavigationBar

/** Native destinations remain inside the patched package, including URL fallbacks. */
object RokidSectionNavigation {
    fun open(activity: Activity, section: RokidSection): Boolean = when (section) {
        RokidSection.HOME -> clickTab(activity, "home") || openUrl(activity, "https://www.youtube.com/")
        RokidSection.SUBSCRIPTIONS -> clickTab(activity, "subscriptions") ||
            openUrl(activity, "https://www.youtube.com/feed/subscriptions")
        RokidSection.YOU -> clickYouTab(activity) || openUrl(activity, "https://www.youtube.com/feed/you")
        RokidSection.HISTORY -> openUrl(activity, "https://www.youtube.com/feed/history")
        RokidSection.WATCH_LATER -> openUrl(activity, "https://www.youtube.com/playlist?list=WL")
        RokidSection.SEARCH -> openSearch(activity)
    }

    fun isRootFeed(): Boolean = !NavigationBar.isBackButtonVisible() && !NavigationBar.isSearchBarActive()

    fun find(activity: Activity, name: String): View? {
        val id = ResourceUtils.getIdentifier(activity, ResourceType.ID, name)
        return if (id == 0) null else activity.findViewById(id)
    }

    private fun tabs(activity: Activity): List<View> {
        val bar = find(activity, "pivot_bar") ?: return emptyList()
        val tabs = mutableListOf<View>()
        fun walk(view: View) {
            if (view.isClickable) {
                tabs.add(view)
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        if (bar is ViewGroup) for (i in 0 until bar.childCount) walk(bar.getChildAt(i))
        return tabs
    }

    private fun string(activity: Activity, name: String): String? {
        val id = ResourceUtils.getIdentifier(activity, ResourceType.STRING, name)
        return if (id == 0) null else activity.getString(id)
    }

    private fun containsLabel(view: View, label: String): Boolean {
        if (view is TextView && view.text.toString() == label) return true
        if (view.contentDescription?.toString() == label) return true
        return view is ViewGroup && (0 until view.childCount).any { containsLabel(view.getChildAt(it), label) }
    }

    private fun clickTab(activity: Activity, labelName: String): Boolean {
        val label = string(activity, labelName) ?: return false
        return tabs(activity).firstOrNull { containsLabel(it, label) }?.performClick() == true
    }

    private fun clickYouTab(activity: Activity): Boolean {
        val borderId = ResourceUtils.getIdentifier(activity, ResourceType.ID, "you_tab_border")
        if (borderId == 0) return false
        return tabs(activity).firstOrNull { it.findViewById<View>(borderId) != null }?.performClick() == true
    }

    private fun openSearch(activity: Activity): Boolean {
        for (name in listOf("menu_item_search", "search_box")) {
            val view = find(activity, name) ?: continue
            if (view.performClick()) return true
        }
        val toolbar = find(activity, "toolbar")
        val label = string(activity, "search")
        fun clickSearch(view: View): Boolean {
            if (view.isClickable && label != null && view.contentDescription?.toString() == label) {
                return view.performClick()
            }
            return view is ViewGroup && (0 until view.childCount).any { clickSearch(view.getChildAt(it)) }
        }
        return (toolbar != null && clickSearch(toolbar)) || activity.onSearchRequested()
    }

    private fun openUrl(activity: Activity, url: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(activity.packageName))
        true
    } catch (ex: Exception) {
        Logger.printException({ "Rokid section navigation failed" }, ex)
        false
    }
}
