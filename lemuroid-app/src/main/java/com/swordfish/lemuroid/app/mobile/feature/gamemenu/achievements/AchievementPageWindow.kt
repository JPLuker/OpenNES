package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

/**
 * Keep long achievement sets responsive inside the scrolling pause panel.
 * Filtering and completion are always calculated from the complete official
 * achievement list, not just the currently displayed window.
 */
internal object AchievementPageWindow {
    const val PAGE_SIZE = 20

    fun limit(total: Int, requested: Int): Int = requested.coerceIn(0, total.coerceAtLeast(0))

    fun more(total: Int, displayed: Int): Int =
        (total.coerceAtLeast(0) - displayed.coerceAtLeast(0)).coerceIn(0, PAGE_SIZE)

    fun next(total: Int, displayed: Int): Int =
        limit(total, displayed.coerceAtLeast(0) + PAGE_SIZE)
}
