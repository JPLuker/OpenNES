package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors

/** The pause menu follows the active controller color palette. */
internal object GameMenuStyle {
    val background: Color
        @Composable get() = LocalOpenNesAppColors.current.background
    val surface: Color
        @Composable get() = LocalOpenNesAppColors.current.panel
    val surfaceRaised: Color
        @Composable get() = LocalOpenNesAppColors.current.surface
    val border: Color
        @Composable get() = LocalOpenNesAppColors.current.border
    val foreground: Color
        @Composable get() = LocalOpenNesAppColors.current.foreground
    val muted: Color
        @Composable get() = LocalOpenNesAppColors.current.muted
    val red: Color
        @Composable get() = LocalOpenNesAppColors.current.red
}
