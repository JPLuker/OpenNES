package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementPageWindowTest {
    @Test
    fun showsStablePagesWithoutInflatingCounts() {
        assertEquals(20, AchievementPageWindow.limit(88, AchievementPageWindow.PAGE_SIZE))
        assertEquals(20, AchievementPageWindow.more(88, 20))
        assertEquals(40, AchievementPageWindow.next(88, 20))
        assertEquals(8, AchievementPageWindow.more(88, 80))
        assertEquals(88, AchievementPageWindow.next(88, 80))
        assertEquals(0, AchievementPageWindow.more(88, 88))
    }

    @Test
    fun emptyAndMalformedCountsNeverProduceNegativeWindows() {
        assertEquals(0, AchievementPageWindow.limit(0, 20))
        assertEquals(0, AchievementPageWindow.more(0, 0))
        assertEquals(0, AchievementPageWindow.next(0, 0))
        assertEquals(0, AchievementPageWindow.more(-4, -2))
    }
}
