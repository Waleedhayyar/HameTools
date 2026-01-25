package com.ham.tools.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.ham.tools.R

/**
 * Sealed class representing all navigation destinations in the app
 */
sealed class NavDestination(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    /**
     * Logbook - Main screen for QSO (contact) records
     */
    data object Logbook : NavDestination(
        route = "logbook",
        titleResId = R.string.nav_logbook,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    /**
     * Tools - Contains QSL generator, CW practice, SSTV, etc.
     */
    data object Tools : NavDestination(
        route = "tools",
        titleResId = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build
    )

    /**
     * Profile - User statistics and settings
     */
    data object Profile : NavDestination(
        route = "profile",
        titleResId = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )

    companion object {
        /**
         * List of all bottom navigation destinations
         */
        val bottomNavItems = listOf(Logbook, Tools, Profile)
        
        // Tool sub-routes (not in bottom nav)
        const val QSL_EDITOR = "tools/qsl_editor"
        const val QSL_EDITOR_WITH_QSO = "tools/qsl_editor/{qsoLogId}"
        const val CW_PRACTICE = "tools/cw_practice"
        const val Q_CODES = "tools/q_codes"
        const val SSTV = "tools/sstv"
        const val PROPAGATION = "tools/propagation"
        const val GRID_LOCATOR = "tools/grid_locator"
        
        /**
         * Create QSL Editor route with QSO Log ID
         */
        fun qslEditorWithQso(qsoLogId: Long): String = "tools/qsl_editor/$qsoLogId"
    }
}
