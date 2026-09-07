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
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.softeen.nflocospicks.presentation.groups.GroupSettingsScreen
import com.softeen.nflocospicks.presentation.groups.GroupViewModel
import com.softeen.nflocospicks.presentation.groups.GroupsScreen
import com.softeen.nflocospicks.presentation.groups.JoinGroupScreen
import com.softeen.nflocospicks.presentation.history.HistoryScreen
import com.softeen.nflocospicks.presentation.leaderboard.LeaderboardScreen
import com.softeen.nflocospicks.presentation.picks.PickScreen
import com.softeen.nflocospicks.presentation.settings.SettingsScreen
import com.softeen.nflocospicks.presentation.settings.SettingsViewModel
import com.softeen.nflocospicks.presentation.teamselection.TeamSelectionScreen
import com.softeen.nflocospicks.presentation.usermanagement.UserManagementScreen
import com.softeen.nflocospicks.presentation.theme.FontScaleOption
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme

@Composable
fun NavGraph(
    pendingInviteCode: String? = null,
    onPendingInviteConsumed: () -> Unit = {}
) {
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
                            navController.navigate(Screen.JoinGroup.createRoute())
                        },
                        onNavigateToGroup = { groupId ->
                            navController.navigate(Screen.GroupSession.createRoute(groupId))
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onNavigateToGroupSettings = { groupId ->
                            navController.navigate(Screen.GroupSettings.createRoute(groupId))
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
                            user                         = user,
                            viewModel                    = settingsViewModel,
                            onSignOut                    = { authViewModel.signOut() },
                            onNavigateBack               = { navController.popBackStack() },
                            onNavigateToTeamSelection    = { navController.navigate(Screen.TeamSelection.route) },
                            onNavigateToUserManagement   = { navController.navigate(Screen.UserManagement.route) },
                            onNavigateToAccount          = { navController.navigate(Screen.Account.route) }
                        )
                    }
                }

                composable(Screen.Account.route) {
                    val user = (authState as? AuthUiState.Authenticated)?.user
                    val deleteAccountError by authViewModel.deleteAccountError.collectAsStateWithLifecycle()
                    if (user != null) {
                        AccountScreen(
                            user                      = user,
                            favoriteTeamAbbr          = prefs.favoriteTeamAbbr,
                            onSetupComplete           = {
                                navController.navigate(Screen.Groups.route) {
                                    popUpTo(Screen.Account.route) { inclusive = true }
                                }
                            },
                            onDeleteAccount              = { authViewModel.deleteAccount() },
                            deleteAccountError           = deleteAccountError,
                            onDismissDeleteAccountError  = { authViewModel.clearDeleteAccountError() },
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

                composable(
                    route     = Screen.JoinGroup.route,
                    arguments = listOf(navArgument("code") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { navBackStackEntry ->
                    val groupsEntry = remember(navBackStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    val prefilledCode = navBackStackEntry.arguments?.getString("code")
                    JoinGroupScreen(
                        prefilledCode  = prefilledCode,
                        onNavigateBack = { navController.popBackStack() },
                        viewModel      = groupViewModel
                    )
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

                    // Si el grupo se borra mientras el usuario está viendo su historial, salir
                    // a Groups en vez de dejar una pantalla "fantasma" (mismo guard que
                    // Screen.GroupSession). Gate en Success para no reaccionar durante Loading.
                    LaunchedEffect(groupListState, group) {
                        if (groupListState is GroupListUiState.Success && group == null) {
                            navController.navigate(Screen.Groups.route) {
                                popUpTo(Screen.Groups.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }

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

                    // Si el grupo se borra (por este usuario desde GroupSettings, o por otro
                    // admin/dispositivo), el listener en vivo deja de devolverlo y `group`
                    // pasa a null mientras el miembro sigue en la sesión. Salimos solos a
                    // Groups para no dejar un GroupSessionScreen "fantasma". Gate en Success
                    // para no reaccionar durante Loading, donde `group` siempre es null.
                    LaunchedEffect(groupListState, group) {
                        if (groupListState is GroupListUiState.Success && group == null) {
                            navController.navigate(Screen.Groups.route) {
                                popUpTo(Screen.Groups.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }

                    GroupSessionScreen(
                        groupId             = groupId,
                        group               = group,
                        currentUserId       = currentUserId,
                        photoUiState        = photoUiState,
                        onNavigateBack      = { navController.popBackStack() },
                        onNavigateToHistory = { gId -> navController.navigate("history/$gId") },
                        onNavigateToGroupSettings = { gId -> navController.navigate(Screen.GroupSettings.createRoute(gId)) },
                        onUploadPhoto       = { uri -> group?.let { g -> currentUserId?.let { groupViewModel.uploadGroupPhoto(g, it, uri) } } },
                        onSetIcon           = { iconId -> group?.let { g -> currentUserId?.let { groupViewModel.setGroupIcon(g, it, iconId) } } },
                        onDismissPhotoPicker = { groupViewModel.resetPhotoUiState() }
                    )
                }

                composable(
                    route     = Screen.GroupSettings.route,
                    arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable

                    // Mismo patrón que Screen.GroupSession/History: compartimos el GroupViewModel
                    // de GroupsScreen para leer el Group actual del listener en vivo.
                    val groupsEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(Screen.Groups.route)
                    }
                    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
                    val groupListState by groupViewModel.groupListState.collectAsStateWithLifecycle()
                    val photoUiState by groupViewModel.photoUiState.collectAsStateWithLifecycle()
                    val settingsState by groupViewModel.groupSettingsState.collectAsStateWithLifecycle()
                    val group = (groupListState as? GroupListUiState.Success)?.groups?.find { it.id == groupId }
                    val currentUserId = groupViewModel.currentUserId

                    // Limpia cualquier Error de una visita previa a esta pantalla.
                    LaunchedEffect(Unit) { groupViewModel.resetGroupSettingsState() }

                    GroupSettingsScreen(
                        groupListState       = groupListState,
                        group                = group,
                        currentUserId        = currentUserId,
                        photoUiState         = photoUiState,
                        settingsState        = settingsState,
                        onRename             = { name -> group?.let { g -> currentUserId?.let { groupViewModel.renameGroup(g, it, name) } } },
                        onUploadPhoto        = { uri -> group?.let { g -> currentUserId?.let { groupViewModel.uploadGroupPhoto(g, it, uri) } } },
                        onSetIcon            = { iconId -> group?.let { g -> currentUserId?.let { groupViewModel.setGroupIcon(g, it, iconId) } } },
                        onDeleteGroup        = { group?.let { g -> currentUserId?.let { groupViewModel.deleteGroup(g, it) } } },
                        onDismissPhotoPicker = { groupViewModel.resetPhotoUiState() },
                        onNavigateBack       = { navController.popBackStack() },
                        onExitToGroups       = {
                            navController.navigate(Screen.Groups.route) {
                                popUpTo(Screen.Groups.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
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
            TrackScreenView(navController)
        }
    }

    // Único punto de redirección tras autenticarse (login recién hecho o sesión restaurada),
    // al cerrar sesión, y al consumir un código de invitación pendiente (PR-25). Un usuario
    // con perfil incompleto (sin username, o sin ningún medio de contacto) es forzado a
    // Account antes de poder entrar a Groups — ver Screen.Account más arriba. Una vez que
    // isProfileComplete es true para una cuenta, nunca debe volver a mandarse a Account.
    //
    // Keyeamos también en currentBackStackEntry (no solo authState/pendingInviteCode) porque
    // AccountScreen.onSetupComplete navega Account → Groups directamente, sin pasar por este
    // efecto ni cambiar authState — sin esta key, un código pendiente para un usuario nuevo
    // que recién completa su perfil se quedaría atorado sin consumirse. Ver docs/plans/
    // join-via-link.md Paso 4 (5c) para el detalle completo.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(authState, pendingInviteCode, currentBackStackEntry) {
        when (val action = decidePostAuthNavigation(
            authState, navController.currentDestination?.route, pendingInviteCode
        )) {
            is PostAuthNavAction.Navigate -> {
                navController.navigate(action.route) {
                    if (action.popUpToRoute != null) {
                        popUpTo(action.popUpToRoute) { inclusive = action.popUpToInclusive }
                    } else if (action.popUpToInclusive) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                if (action.route.startsWith("join_group")) onPendingInviteConsumed()
            }
            PostAuthNavAction.None -> Unit
        }
    }
}

sealed class PostAuthNavAction {
    data class Navigate(
        val route: String,
        val popUpToRoute: String? = null,
        val popUpToInclusive: Boolean = false
    ) : PostAuthNavAction()
    object None : PostAuthNavAction()
}

/**
 * Dada la sesión actual, la ruta actual del back stack, y si hay un código de invitación
 * pendiente (llegado por un join link, PR-25), decide la única acción de navegación (si la
 * hay) que el efecto de auth-gating de NavGraph debe tomar. Pura — sin NavController — por
 * lo tanto directamente testeable con JUnit.
 */
fun decidePostAuthNavigation(
    authState: AuthUiState,
    currentRoute: String?,
    pendingInviteCode: String?
): PostAuthNavAction = when {
    authState is AuthUiState.Idle && currentRoute != Screen.Login.route ->
        PostAuthNavAction.Navigate(Screen.Login.route, popUpToRoute = null, popUpToInclusive = true) // popUpTo(0)

    authState is AuthUiState.Authenticated && authState.isProfileSynced && currentRoute == Screen.Login.route -> {
        val destination = if (authState.user.isProfileComplete) Screen.Groups.route else Screen.Account.route
        PostAuthNavAction.Navigate(destination, popUpToRoute = Screen.Login.route, popUpToInclusive = true)
    }

    // Se dispara desde cualquier pantalla, no solo Groups: una vez autenticado con perfil
    // completo, Groups siempre está en el back stack (es la raíz de la app tras el login),
    // así que Screen.JoinGroup's getBackStackEntry(Groups) nunca lanza excepción sin
    // importar qué pantalla esté al frente. Restringido a "no Login/Account" para no
    // interrumpir el flujo de onboarding a medio terminar.
    authState is AuthUiState.Authenticated && authState.isProfileSynced && authState.user.isProfileComplete &&
        pendingInviteCode != null &&
        currentRoute != null && currentRoute != Screen.Login.route && currentRoute != Screen.Account.route ->
        PostAuthNavAction.Navigate(Screen.JoinGroup.createRoute(pendingInviteCode))

    else -> PostAuthNavAction.None
}
