package com.softeen.nflocospicks.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.stringResource
import com.softeen.nflocospicks.R
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.softeen.nflocospicks.presentation.leaderboard.LeaderboardScreen
import com.softeen.nflocospicks.presentation.picks.PickScreen
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

sealed class BottomNavItem(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Picks       : BottomNavItem("picks_content/{groupId}",       R.string.nav_my_picks,    Icons.AutoMirrored.Filled.List)
    object Leaderboard : BottomNavItem("leaderboard_content/{groupId}", R.string.nav_leaderboard, Icons.Default.Star)
}

@Composable
fun GroupSessionScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (String) -> Unit
) {
    val navController = rememberNavController()
    val appColors = LocalAppColors.current

    val items = listOf(
        BottomNavItem.Picks,
        BottomNavItem.Leaderboard
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = appColors.header,
                contentColor = appColors.primary
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { item ->
                    val routeWithId = item.route.replace("{groupId}", groupId)
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                        label = { Text(stringResource(item.titleRes)) },
                        selected = selected,
                        onClick = {
                            navController.navigate(routeWithId) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = appColors.primary,
                            selectedTextColor = appColors.primary,
                            unselectedIconColor = appColors.secondary,
                            unselectedTextColor = appColors.secondary,
                            indicatorColor = appColors.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "picks_content/$groupId",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = BottomNavItem.Picks.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) {
                PickScreen(
                    onNavigateBack = onNavigateBack
                )
            }
            composable(
                route = BottomNavItem.Leaderboard.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) {
                LeaderboardScreen(
                    onNavigateBack = onNavigateBack,
                    onNavigateToHistory = onNavigateToHistory
                )
            }
        }
    }
}
