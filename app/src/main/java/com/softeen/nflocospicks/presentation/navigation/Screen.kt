package com.softeen.nflocospicks.presentation.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Groups : Screen("groups")
    data object Schedule : Screen("schedule/{groupId}")
    data object Picks : Screen("picks/{groupId}")
    data object Leaderboard : Screen("leaderboard/{groupId}")
    data object History : Screen("history/{groupId}")

    // Proposals
    data object Proposal1 : Screen("proposal1")
    data object Proposal2 : Screen("proposal2")
    data object Proposal3 : Screen("proposal3")
}
