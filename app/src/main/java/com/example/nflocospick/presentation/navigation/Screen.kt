package com.example.nflocospick.presentation.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Groups : Screen("groups")
    data object Schedule : Screen("schedule/{groupId}")
    data object Picks : Screen("picks/{groupId}")
    data object Leaderboard : Screen("leaderboard/{groupId}")
    data object History : Screen("history/{groupId}")
}
