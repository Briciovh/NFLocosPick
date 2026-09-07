package com.softeen.nflocospicks.presentation.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteToScreenNameTest {

    @Test
    fun `null route maps to null`() {
        assertThat(routeToScreenName(null)).isNull()
    }

    @Test
    fun `each top-level Screen route maps to its analytics name`() {
        assertThat(routeToScreenName(Screen.Login.route)).isEqualTo("login")
        assertThat(routeToScreenName(Screen.Groups.route)).isEqualTo("groups")
        assertThat(routeToScreenName(Screen.CreateGroup.route)).isEqualTo("create_group")
        assertThat(routeToScreenName(Screen.JoinGroup.route)).isEqualTo("join_group")
        assertThat(routeToScreenName(Screen.Settings.route)).isEqualTo("settings")
        assertThat(routeToScreenName(Screen.Account.route)).isEqualTo("account")
        assertThat(routeToScreenName(Screen.ChangePassword.route)).isEqualTo("change_password")
        assertThat(routeToScreenName(Screen.TeamSelection.route)).isEqualTo("team_selection")
        assertThat(routeToScreenName(Screen.UserManagement.route)).isEqualTo("user_management")
        assertThat(routeToScreenName(Screen.History.route)).isEqualTo("pick_history")
        assertThat(routeToScreenName(Screen.GroupSession.route)).isEqualTo("group_session")
        assertThat(routeToScreenName(Screen.GroupSettings.route)).isEqualTo("group_settings")
    }

    @Test
    fun `each bottom-nav route maps to its analytics name`() {
        assertThat(routeToScreenName(BottomNavItem.Picks.route)).isEqualTo("picks")
        assertThat(routeToScreenName(BottomNavItem.Leaderboard.route)).isEqualTo("leaderboard")
        assertThat(routeToScreenName(BottomNavItem.Board.route)).isEqualTo("board")
    }

    @Test
    fun `an unmapped route is passed through unchanged rather than dropped`() {
        assertThat(routeToScreenName("some/unknown/route")).isEqualTo("some/unknown/route")
        assertThat(routeToScreenName("")).isEqualTo("")
    }

    /**
     * Contract: callers pass the Navigation route *pattern* (with `{placeholder}`
     * segments, e.g. `Screen.GroupSession.route`), never a resolved path like
     * `group_session/g123`. A resolved path does NOT match any `when` branch and
     * falls through to passthrough — which would send a high-cardinality string to
     * analytics. This test pins that behavior so a regression in the caller (passing
     * `navBackStackEntry.destination.route` vs. the resolved id) is caught here.
     */
    @Test
    fun `a resolved path with arguments is not mapped and falls through to passthrough`() {
        assertThat(routeToScreenName("group_session/g123")).isEqualTo("group_session/g123")
        assertThat(routeToScreenName("picks/g123")).isEqualTo("picks/g123")
        assertThat(routeToScreenName("join_group?code=ABC123")).isEqualTo("join_group?code=ABC123")
    }
}
