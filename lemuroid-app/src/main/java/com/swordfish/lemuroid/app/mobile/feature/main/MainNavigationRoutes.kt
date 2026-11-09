package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.swordfish.lemuroid.R

fun NavGraphBuilder.composable(
    route: MainRoute,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    this.composable(route = route.route, arguments = route.arguments, content = content)
}

fun NavController.navigateToRoute(route: MainRoute) {
    this.navigate(route.route)
}

enum class MainRoute(
    val route: String,
    @StringRes val titleId: Int,
    val parent: MainRoute? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
) {
    HOME(
        route = "home",
        titleId = R.string.title_home,
    ),
    SEARCH(
        route = "search",
        titleId = R.string.title_search,
    ),
    SETTINGS(
        route = "settings/home",
        titleId = R.string.title_settings,
    ),
    SETTINGS_LIBRARY(
        route = "settings/library",
        titleId = R.string.opennes_library_maintenance,
        parent = SETTINGS,
    ),
    SETTINGS_ADVANCED(
        route = "settings/advanced",
        titleId = R.string.settings_title_advanced_settings,
        parent = SETTINGS,
    ),
    SETTINGS_INPUT_DEVICES(
        route = "settings/inputdevices",
        titleId = R.string.settings_title_gamepad_settings,
        parent = SETTINGS,
    ),
    SETTINGS_CONTROLLER_APPEARANCE(
        route = "settings/controller-appearance",
        titleId = R.string.opennes_controller_appearance_title,
        parent = SETTINGS,
    ),
    SETTINGS_SAVE_SYNC(
        route = "settings/savesync",
        titleId = R.string.settings_title_save_sync,
        parent = SETTINGS,
    ),
    SETTINGS_ROMM(
        route = "settings/romm",
        titleId = R.string.opennes_romm_title,
        parent = SETTINGS,
    ),
    ;

    companion object {
        fun findByRoute(route: String): MainRoute {
            return values().first { it.route == route }
        }
    }
}
