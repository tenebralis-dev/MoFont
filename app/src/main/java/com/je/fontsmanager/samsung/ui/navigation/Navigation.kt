package com.je.fontsmanager.samsung.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    data object Library : Screen(
        route = "library",
        title = "Library",
        icon = Icons.AutoMirrored.Filled.List
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Library,
    Screen.Settings
)