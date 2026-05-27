package com.softeen.nflocospicks.presentation.navigation

import androidx.compose.runtime.Composable
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
import com.softeen.nflocospicks.presentation.groups.CreateGroupScreen
import com.softeen.nflocospicks.presentation.groups.GroupViewModel
import com.softeen.nflocospicks.presentation.groups.GroupsScreen
import com.softeen.nflocospicks.presentation.groups.JoinGroupScreen
import com.softeen.nflocospicks.presentation.picks.PickScreen
import com.softeen.nflocospicks.presentation.schedule.ScheduleScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    // AuthViewModel vive en el scope del NavGraph para sobrevivir cambios de destino.
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Siempre arrancamos en Login. Si hay sesión activa (restaurada síncronamente en
    // AuthViewModel.init), el LaunchedEffect redirige a Groups antes del primer frame.
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
                onSignedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Groups.route) { inclusive = true }
                    }
                }
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
            // groupId lo lee ScheduleViewModel vía SavedStateHandle — no se pasa explícitamente
            ScheduleScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route     = Screen.Picks.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            // groupId lo lee PickViewModel vía SavedStateHandle — no se pasa explícitamente
            PickScreen(onNavigateBack = { navController.popBackStack() })
        }
    }

    // Redirección por restauración de sesión (cold start con sesión existente).
    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated &&
            navController.currentDestination?.route == Screen.Login.route
        ) {
            navController.navigate(Screen.Groups.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }
}
