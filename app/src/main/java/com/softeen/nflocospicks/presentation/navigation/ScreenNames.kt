package com.softeen.nflocospicks.presentation.navigation

fun routeToScreenName(route: String?): String? = when (route) {
    Screen.Login.route            -> "login"
    Screen.Groups.route           -> "groups"
    Screen.CreateGroup.route      -> "create_group"
    Screen.JoinGroup.route        -> "join_group"
    Screen.Settings.route         -> "settings"
    Screen.Account.route          -> "account"
    Screen.ChangePassword.route   -> "change_password"
    Screen.TeamSelection.route    -> "team_selection"
    Screen.UserManagement.route   -> "user_management"
    Screen.History.route          -> "pick_history"
    Screen.GroupSession.route     -> "group_session"
    BottomNavItem.Picks.route       -> "picks"
    BottomNavItem.Leaderboard.route -> "leaderboard"
    BottomNavItem.Board.route       -> "board"
    null                           -> null
    else                           -> route  // fallback: never lose an unmapped route
}
