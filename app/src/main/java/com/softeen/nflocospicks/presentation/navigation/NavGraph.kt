package com.softeen.nflocospicks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.softeen.nflocospicks.presentation.auth.AuthUiState
import com.softeen.nflocospicks.presentation.auth.AuthViewModel
import com.softeen.nflocospicks.presentation.auth.LoginScreen
import com.softeen.nflocospicks.presentation.groups.GroupsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    // AuthViewModel lives at NavGraph scope so it survives destination changes.
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Always start at Login. If the user is already authenticated (session restored
    // synchronously in AuthViewModel.init), the LaunchedEffect below redirects them
    // to Groups before the first frame is visible — no login flash in practice.
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
                onSignedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Groups.route) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
    }

    // Handle session-restore redirect (cold start with existing Firebase session).
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
