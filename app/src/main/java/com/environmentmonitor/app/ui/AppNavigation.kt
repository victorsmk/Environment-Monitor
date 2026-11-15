package com.environmentmonitor.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.environmentmonitor.app.ui.dashboard.DashboardScreen
import com.environmentmonitor.app.ui.history.HistoryScreen
import com.environmentmonitor.app.ui.history.SessionDetailScreen
import com.environmentmonitor.app.ui.profiles.NEW_PROFILE_ID
import com.environmentmonitor.app.ui.profiles.ProfileEditorScreen
import com.environmentmonitor.app.ui.profiles.ProfilesScreen
import com.environmentmonitor.app.ui.settings.SettingsScreen

/** Screens reachable from the bottom navigation bar. */
enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Default.Home),
    Profiles("profiles", "Profiles", Icons.Default.Person),
    History("history", "History", Icons.AutoMirrored.Filled.List),
    Settings("settings", "Settings", Icons.Default.Settings),
}

private const val PROFILE_EDITOR_ROUTE = "profile/{$PROFILE_ID_ARG}"

private fun profileEditorRoute(id: Long) = "profile/$id"

private const val SESSION_ROUTE = "session/{$SESSION_ID_ARG}"

private fun sessionRoute(id: Long) = "session/$id"

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevel,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(viewModel = viewModel(factory = AppViewModelProvider.Factory))
            }
            composable(TopLevelDestination.Profiles.route) {
                ProfilesScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.Factory),
                    onOpenProfile = { id -> navController.navigate(profileEditorRoute(id)) },
                    onNewProfile = { navController.navigate(profileEditorRoute(NEW_PROFILE_ID)) },
                )
            }
            composable(TopLevelDestination.History.route) {
                HistoryScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.Factory),
                    onOpenSession = { id -> navController.navigate(sessionRoute(id)) },
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(viewModel = viewModel(factory = AppViewModelProvider.Factory))
            }
            composable(
                route = SESSION_ROUTE,
                arguments = listOf(navArgument(SESSION_ID_ARG) { type = NavType.LongType }),
            ) {
                SessionDetailScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.Factory),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = PROFILE_EDITOR_ROUTE,
                arguments = listOf(navArgument(PROFILE_ID_ARG) { type = NavType.LongType }),
            ) {
                ProfileEditorScreen(
                    viewModel = viewModel(factory = AppViewModelProvider.Factory),
                    onBack = { navController.popBackStack() },
                    onDuplicated = { id ->
                        navController.navigate(profileEditorRoute(id)) {
                            popUpTo(PROFILE_EDITOR_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
