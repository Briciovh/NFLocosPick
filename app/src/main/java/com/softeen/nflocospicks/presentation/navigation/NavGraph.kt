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
import com.softeen.nflocospicks.domain.model.isProfileComplete
import com.softeen.nflocospicks.presentation.account.AccountScreen
import com.softeen.nflocospicks.presentation.account.ChangePasswordScreen
import com.softeen.nflocospicks.presentation.auth.AuthUiState
import com.softeen.nflocospicks.presentation.auth.AuthViewModel
import com.softeen.nflocospicks.presentation.auth.LoginScreen
import com.softeen.nflocospicks.presentation.common.defaultTeamColors
import com.softeen.nflocospicks.presentation.common.nflTeamColorMap
import com.softeen.nflocospicks.presentation.common.toAppColors
import com.softeen.nflocospicks.presentation.groups.CreateGroupScreen
import com.softeen.nflocospicks.presentation.groups.GroupListUiState
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
import com.softeen.nflocospicks.presentation.usermanagement.UserManagementScreen
import com.softeen.nflocospicks.presentation.theme.FontScaleOption
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
    val fontScale  = FontScaleOption.fromKey(prefs.fontScalePreference)

    CompositionLocalProvider(LocalAppColors provides appColors) {
        NFLocosPickTheme(appColors = appColors, fontScale = fontScale) {
            NavHost(
                navController    = navController,
                startDestination = Screen.Login.route
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(viewModel = authViewModel)
                }

                composable(Screen.Groups.route) {
                    GroupsScreen(
                        onNavigateToCreateGroup = {
                            navController.navigate(Screen.CreateGroup.route)
                        },
                        onNavigateToJoinGroup = {
                            navController.navigate(Screen.JoinGroup.route)
                        },
                        onNavigateToGroup = { groupId ->
                            navController.navigate(Screen.GroupSession.createRoute(groupId))
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
                    val deleteAccountError by authViewModel.deleteAccountError.collectAsStateWithLifecycle()
                    if (user != null) {
                        SettingsScreen(
                            user                         = user,
                            viewModel                    = settingsViewModel,
                            onSignOut                    = { authViewModel.signOut() },
                            onDeleteAccount              = { authViewModel.deleteAccount() },
                            deleteAccountError           = deleteAccountError,
                            onDismissDeleteAccountError  = { authViewModel.clearDeleteAccountError() },
                            onNavigateBack               = { navController.popBackStack() },
                            onNavigateToTeamSelection    = { navController.navigate(Screen.TeamSelection.route) },
                            onNavigateToUserManagement   = { navController.navigate(Screen.UserManagement.route) },
                            onNavigateToAccount          = { navController.navigate(Screen.Account.route) }
                        )
                    }
                }

                composable(Screen.Account.route) {
                    val user = (authState as? AuthUiState.Authenticated)?.user
                    if (user != null) {
                        AccountScreen(
                            user                      = user,
                            favoriteTeamAbbr          = prefs.favoriteTeamAbbr,
                            onSetupComplete           = {
                                navController.navigate(Screen.Groups.route) {
                                    popUpTo(Screen.Account.route) { inclusive = true }
                                }
                            },
                            onNavigateBack            = { navController.popBackStack() },
                            onNavigateToTeamSelection = { navController.navigate(Screen.TeamSelection.route) },
                            onNavigateToChangePassword = { navController.navigate(Screen.ChangePassword.route) }
                        )
                    }
                }

                composable(Screen.ChangePassword.route) {
                    ChangePasswordScreen(onNavigateBack = { navController.popBackStack() })
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
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable

                    // Mismo patrón que Screen.GroupSession: compartimos el GroupViewModel de
                    // GroupsScreen para mostrar el nombre/código del grupo en el header, sin
                    // hacer un fetch aparte.
                    val groupsEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    val groupListState by groupViewModel.groupListState.collectAsStateWithLifecycle()
                    val group = (groupListState as? GroupListUiState.Success)?.groups?.find { it.id == groupId }

                    HistoryScreen(
                        group          = group,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route     = Screen.GroupSession.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable

                    // Compartimos la instancia de GroupViewModel con GroupsScreen para leer el
                    // Group actual (nombre/foto/ícono) del mismo listener en tiempo real, sin
                    // hacer un fetch aparte.
                    val groupsEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    val groupListState by groupViewModel.groupListState.collectAsStateWithLifecycle()
                    val photoUiState by groupViewModel.photoUiState.collectAsStateWithLifecycle()
                    val group = (groupListState as? GroupListUiState.Success)?.groups?.find { it.id == groupId }
                    val currentUserId = groupViewModel.currentUserId

                    GroupSessionScreen(
                        groupId             = groupId,
                        group               = group,
                        currentUserId       = currentUserId,
                        photoUiState        = photoUiState,
                        onNavigateBack      = { navController.popBackStack() },
                        onNavigateToHistory = { gId -> navController.navigate("history/$gId") },
                        onUploadPhoto       = { uri -> group?.let { g -> currentUserId?.let { groupViewModel.uploadGroupPhoto(g, it, uri) } } },
                        onSetIcon           = { iconId -> group?.let { g -> currentUserId?.let { groupViewModel.setGroupIcon(g, it, iconId) } } },
                        onDismissPhotoPicker = { groupViewModel.resetPhotoUiState() }
                    )
                }

                composable(Screen.UserManagement.route) {
                    val currentUid = (authState as? AuthUiState.Authenticated)?.user?.uid
                        ?: return@composable
                    UserManagementScreen(
                        currentUserUid = currentUid,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    // Único punto de redirección tras autenticarse (login recién hecho o sesión restaurada)
    // y al cerrar sesión. Un usuario con perfil incompleto (sin username, o sin ningún medio
    // de contacto) es forzado a Account antes de poder entrar a Groups — ver Screen.Account
    // más arriba. Una vez que isProfileComplete es true para una cuenta, nunca debe volver
    // a mandarse a Account.
    LaunchedEffect(authState) {
        val state = authState
        when {
            state is AuthUiState.Authenticated && state.isProfileSynced &&
                navController.currentDestination?.route == Screen.Login.route -> {
                val destination = if (state.user.isProfileComplete) {
                    Screen.Groups.route
                } else {
                    Screen.Account.route
                }
                navController.navigate(destination) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            state is AuthUiState.Idle &&
                navController.currentDestination?.route != Screen.Login.route -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
}
