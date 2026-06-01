package com.softeen.nflocospicks.presentation.navigation

sealed class Screen(val route: String) {
    data object Login       : Screen("login")
    data object Groups      : Screen("groups")
    data object CreateGroup : Screen("create_group")
    data object JoinGroup   : Screen("join_group")
    data object Schedule    : Screen("schedule/{groupId}")
    data object Picks       : Screen("picks/{groupId}")
    data object Leaderboard : Screen("leaderboard/{groupId}")
    data object History     : Screen("history/{groupId}")
    data object Settings        : Screen("settings")
    data object TeamSelection   : Screen("team_selection")
    data object UserManagement  : Screen("user_management")

    // Proposals (UI design reference — no eliminar hasta PR final)
    data object Proposal1 : Screen("proposal1")
    data object Proposal2 : Screen("proposal2")
    data object Proposal3 : Screen("proposal3")
}
