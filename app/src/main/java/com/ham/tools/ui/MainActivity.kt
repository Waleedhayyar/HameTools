package com.ham.tools.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ham.tools.HamToolsApplication
import com.ham.tools.ui.navigation.HamToolsNavHost
import com.ham.tools.ui.navigation.NavDestination
import com.ham.tools.ui.screens.onboarding.OnboardingScreen
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.util.AppLanguage
import com.ham.tools.util.LocaleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Main Activity for HamTools app
 * 
 * This is the single activity that hosts all composable screens
 * using Navigation Compose for navigation between screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        // 应用保存的语言设置
        val language = HamToolsApplication.currentLanguage.value
        val context = LocaleManager.applyLanguage(newBase, language)
        super.attachBaseContext(context)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        setContent {
            // 监听语言变化
            val currentLanguage by HamToolsApplication.currentLanguage.collectAsState()
            
            // 当语言变化时重建 Activity
            LaunchedEffect(currentLanguage) {
                // 语言已经在 ViewModel 中更新了，这里只是触发重组
            }
            
            HamToolsTheme {
                HamToolsApp()
            }
        }
    }
    
    /**
     * 重新创建 Activity 以应用新的语言设置
     */
    fun recreateForLanguageChange() {
        recreate()
    }
}

/**
 * Main composable for the HamTools app
 * 
 * Sets up the Scaffold with bottom navigation bar and
 * hosts the navigation graph.
 * 首次使用时显示引导页面（仅首次打开应用时）。
 */
@Composable
fun HamToolsApp(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 判断是否显示引导页 - 只在配置加载完成且未完成引导时显示
    val showOnboarding = !isLoading && !userProfile.isOnboardingComplete

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 加载中时显示空白（避免闪烁）
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
        
        // 引导页面
        AnimatedVisibility(
            visible = showOnboarding,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OnboardingScreen(
                onComplete = { callsign ->
                    scope.launch {
                        viewModel.completeOnboarding(callsign)
                    }
                }
            )
        }
        
        // 主应用界面
        AnimatedVisibility(
            visible = !isLoading && !showOnboarding,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Scaffold(
                bottomBar = {
                    // 只在主页面显示底部导航
                    val showBottomBar = currentDestination?.route in NavDestination.bottomNavItems.map { it.route }
                    
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        HamToolsBottomNavigationBar(
                            currentDestination = currentDestination,
                            onNavigate = { destination ->
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                HamToolsNavHost(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

/**
 * Bottom navigation bar following Material 3 design guidelines
 * 
 * @param currentDestination The currently active navigation destination
 * @param onNavigate Callback when a navigation item is clicked
 */
@Composable
private fun HamToolsBottomNavigationBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (NavDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavDestination.bottomNavItems.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { 
                it.route == destination.route 
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = stringResource(destination.titleResId)
                    )
                },
                label = {
                    Text(text = stringResource(destination.titleResId))
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
