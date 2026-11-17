package com.example.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoiceTranslatorScreen
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object VoiceRoute

@Serializable
data class StudioRoute(val projectId: Long)

@Composable
fun AppNavigation(
    bayanViewModel: BayanViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isInStudio = currentDestination?.hasRoute<StudioRoute>() == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MidnightVoid,
        contentWindowInsets = if (isInStudio) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        bottomBar = {
            if (!isInStudio) {
                NavigationBar(
                    containerColor = MidnightSurface,
                    contentColor = SilverPure,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val isVideoSelected = currentDestination?.hasRoute<HomeRoute>() == true ||
                        currentDestination == null
                    val isVoiceSelected = currentDestination?.hasRoute<VoiceRoute>() == true

                    NavigationBarItem(
                        selected = isVideoSelected,
                        onClick = {
                            navController.navigate(HomeRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isVideoSelected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                contentDescription = stringResource(R.string.tab_video_studio)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_video_studio)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SilverPure,
                            selectedTextColor = SilverPure,
                            indicatorColor = MidnightSurfaceHigh,
                            unselectedIconColor = SilverMuted,
                            unselectedTextColor = SilverMuted
                        ),
                        modifier = Modifier.testTag("nav_video_studio")
                    )

                    NavigationBarItem(
                        selected = isVoiceSelected,
                        onClick = {
                            navController.navigate(VoiceRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isVoiceSelected) Icons.Filled.Mic else Icons.Outlined.MicNone,
                                contentDescription = stringResource(R.string.tab_voice_translator)
                            )
                        },
                        label = { Text(stringResource(R.string.tab_voice_translator)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IceSapphire,
                            selectedTextColor = SilverPure,
                            indicatorColor = MidnightSurfaceHigh,
                            unselectedIconColor = SilverMuted,
                            unselectedTextColor = SilverMuted
                        ),
                        modifier = Modifier.testTag("nav_voice_translator")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = bayanViewModel,
                    outerPadding = innerPadding,
                    onOpenProject = { projectId ->
                        navController.navigate(StudioRoute(projectId))
                    }
                )
            }

            composable<VoiceRoute> {
                VoiceTranslatorScreen(
                    viewModel = bayanViewModel,
                    outerPadding = innerPadding
                )
            }

            composable<StudioRoute> { backStack ->
                val route: StudioRoute = backStack.toRoute()
                StudioScreen(
                    projectId = route.projectId,
                    viewModel = bayanViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
