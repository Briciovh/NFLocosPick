package com.example.nflocospick.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nflocospick.presentation.proposals.Proposal1
import com.example.nflocospick.presentation.proposals.Proposal2
import com.example.nflocospick.presentation.proposals.Proposal3
import com.example.nflocospick.presentation.welcome.WelcomeScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            WelcomeScreen(
                onNavigateToProposal1 = { navController.navigate(Screen.Proposal1.route) },
                onNavigateToProposal2 = { navController.navigate(Screen.Proposal2.route) },
                onNavigateToProposal3 = { navController.navigate(Screen.Proposal3.route) }
            )
        }
        composable(Screen.Proposal1.route) {
            Proposal1(onBack = { navController.popBackStack() })
        }
        composable(Screen.Proposal2.route) {
            Proposal2(onBack = { navController.popBackStack() })
        }
        composable(Screen.Proposal3.route) {
            Proposal3(onBack = { navController.popBackStack() })
        }
    }
}
