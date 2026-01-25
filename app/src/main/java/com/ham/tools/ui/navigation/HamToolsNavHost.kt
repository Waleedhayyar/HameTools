package com.ham.tools.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ham.tools.ui.screens.logbook.LogbookScreen
import com.ham.tools.ui.screens.profile.ProfileScreen
import com.ham.tools.ui.screens.tools.ToolsScreen
import com.ham.tools.ui.screens.tools.cw.CwPracticeScreen
import com.ham.tools.ui.screens.tools.gridlocator.GridLocatorScreen
import com.ham.tools.ui.screens.tools.qcodes.QCodesScreen
import com.ham.tools.ui.screens.tools.qsl.QslEditorScreen
import com.ham.tools.ui.screens.tools.propagation.PropagationScreen
import com.ham.tools.ui.screens.tools.sstv.SstvReceiverScreen

/**
 * Main navigation host for the HamTools app
 * 
 * @param navController The navigation controller to use
 * @param modifier Modifier to apply to the NavHost
 */
@Composable
fun HamToolsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavDestination.Logbook.route,
        modifier = modifier
    ) {
        // Logbook Screen - QSO records
        composable(route = NavDestination.Logbook.route) {
            LogbookScreen(
                onGenerateQsl = { qsoLog ->
                    // Navigate to QSL editor with the QSO log ID
                    navController.navigate(NavDestination.qslEditorWithQso(qsoLog.id))
                }
            )
        }

        // Tools Screen - QSL, CW, SSTV, Propagation, etc.
        composable(route = NavDestination.Tools.route) {
            ToolsScreen(
                onNavigateToQslEditor = {
                    navController.navigate(NavDestination.QSL_EDITOR)
                },
                onNavigateToCwPractice = {
                    navController.navigate(NavDestination.CW_PRACTICE)
                },
                onNavigateToQCodes = {
                    navController.navigate(NavDestination.Q_CODES)
                },
                onNavigateToSstv = {
                    navController.navigate(NavDestination.SSTV)
                },
                onNavigateToPropagation = {
                    navController.navigate(NavDestination.PROPAGATION)
                },
                onNavigateToGridLocator = {
                    navController.navigate(NavDestination.GRID_LOCATOR)
                }
            )
        }

        // Profile Screen - User stats and settings
        composable(route = NavDestination.Profile.route) {
            ProfileScreen()
        }
        
        // QSL Editor Screen (without QSO data - template editing mode)
        composable(route = NavDestination.QSL_EDITOR) {
            QslEditorScreen(
                qsoLogId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // QSL Editor Screen (with QSO data - card generation mode)
        composable(
            route = NavDestination.QSL_EDITOR_WITH_QSO,
            arguments = listOf(
                navArgument("qsoLogId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val qsoLogId = backStackEntry.arguments?.getLong("qsoLogId")
            QslEditorScreen(
                qsoLogId = qsoLogId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // CW Practice Screen
        composable(route = NavDestination.CW_PRACTICE) {
            CwPracticeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Q Codes Dictionary Screen
        composable(route = NavDestination.Q_CODES) {
            QCodesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // SSTV Receiver Screen
        composable(route = NavDestination.SSTV) {
            SstvReceiverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Propagation Prediction Screen
        composable(route = NavDestination.PROPAGATION) {
            PropagationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Grid Locator Screen
        composable(route = NavDestination.GRID_LOCATOR) {
            GridLocatorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
