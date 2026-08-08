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
    data object Account         : Screen("account")
    data object ChangePassword  : Screen("change_password")
    data object TeamSelection   : Screen("team_selection")
    data object UserManagement  : Screen("user_management")
    data object GroupSession    : Screen("group_session/{groupId}") {
        fun createRoute(groupId: String) = "group_session/$groupId"
    }
}
