package com.softeen.nflocospicks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.softeen.nflocospicks.presentation.auth.AuthUiState
import com.softeen.nflocospicks.presentation.auth.AuthViewModel
import com.softeen.nflocospicks.presentation.auth.LoginScreen
import com.softeen.nflocospicks.presentation.common.defaultTeamColors
import com.softeen.nflocospicks.presentation.common.nflTeamColorMap
import com.softeen.nflocospicks.presentation.common.toAppColors
import com.softeen.nflocospicks.presentation.groups.CreateGroupScreen
import com.softeen.nflocospicks.presentation.groups.GroupViewModel
import com.softeen.nflocospicks.presentation.groups.GroupsScreen
import com.softeen.nflocospicks.presentation.groups.JoinGroupScreen
import com.softeen.nflocospicks.presentation.history.HistoryScreen
import com.softeen.nflocospicks.presentation.leaderboard.LeaderboardScreen
import com.softeen.nflocospicks.presentation.picks.PickScreen
import com.softeen.nflocospicks.presentation.schedule.ScheduleScreen
import com.softeen.nflocospicks.presentation.settings.SettingsScreen
import com.softeen.nflocospicks.presentation.settings.SettingsViewModel
import com.softeen.nflocospicks.presentation.teamselection.TeamSelectionScreen
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    // ViewModels a nivel NavGraph — sobreviven cambios de destino.
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // SettingsViewModel en scope NavGraph para leer favoriteTeamAbbr y derivar AppColors.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()

    // Colores activos: Blue Steel por defecto, colores del equipo favorito si hay uno.
    val teamColors = nflTeamColorMap[prefs.favoriteTeamAbbr] ?: defaultTeamColors
    val appColors  = teamColors.toAppColors()

    CompositionLocalProvider(LocalAppColors provides appColors) {
        NFLocosPickTheme(appColors = appColors) {
            NavHost(
                navController    = navController,
                startDestination = Screen.Login.route
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onAuthenticated = {
                            navController.navigate(Screen.Groups.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                        viewModel = authViewModel
                    )
                }

                composable(Screen.Groups.route) {
                    GroupsScreen(
                        onNavigateToCreateGroup = {
                            navController.navigate(Screen.CreateGroup.route)
                        },
                        onNavigateToJoinGroup = {
                            navController.navigate(Screen.JoinGroup.route)
                        },
                        onNavigateToSchedule = { groupId ->
                            navController.navigate("schedule/$groupId")
                        },
                        onNavigateToPicks = { groupId ->
                            navController.navigate("picks/$groupId")
                        },
                        onNavigateToLeaderboard = { groupId ->
                            navController.navigate("leaderboard/$groupId")
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onSignedOut = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Groups.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    val user = (authState as? AuthUiState.Authenticated)?.user
                    if (user != null) {
                        SettingsScreen(
                            user                      = user,
                            viewModel                 = settingsViewModel,
                            onSignOut                 = { authViewModel.signOut() },
                            onNavigateBack            = { navController.popBackStack() },
                            onNavigateToTeamSelection = { navController.navigate(Screen.TeamSelection.route) }
                        )
                    }
                }

                composable(Screen.TeamSelection.route) {
                    TeamSelectionScreen(
                        viewModel      = settingsViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.CreateGroup.route) { navBackStackEntry ->
                    // Compartimos la instancia de GroupViewModel con GroupsScreen
                    // para que el listener en tiempo real ya esté activo.
                    val groupsEntry = remember(navBackStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    CreateGroupScreen(
                        onNavigateBack = { navController.popBackStack() },
                        viewModel      = groupViewModel
                    )
                }

                composable(Screen.JoinGroup.route) { navBackStackEntry ->
                    val groupsEntry = remember(navBackStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    JoinGroupScreen(
                        onNavigateBack = { navController.popBackStack() },
                        viewModel      = groupViewModel
                    )
                }

                composable(
                    route     = Screen.Schedule.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) {
                    ScheduleScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(
                    route     = Screen.Picks.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) {
                    PickScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(
                    route     = Screen.Leaderboard.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) {
                    LeaderboardScreen(
                        onNavigateBack      = { navController.popBackStack() },
                        onNavigateToHistory = { groupId ->
                            navController.navigate("history/$groupId")
                        }
                    )
                }

                composable(
                    route     = Screen.History.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) {
                    HistoryScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }

    // Redirección por restauración de sesión y cierre de sesión.
    LaunchedEffect(authState) {
        when {
            authState is AuthUiState.Authenticated &&
                navController.currentDestination?.route == Screen.Login.route -> {
                navController.navigate(Screen.Groups.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            authState is AuthUiState.Idle &&
                navController.currentDestination?.route != Screen.Login.route -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
}
