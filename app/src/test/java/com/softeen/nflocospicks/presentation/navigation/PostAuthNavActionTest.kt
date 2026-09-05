package com.softeen.nflocospicks.presentation.navigation

import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.presentation.auth.AuthUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PostAuthNavActionTest {

    private val completeUser = User(
        uid = "u1",
        displayName = "Bricio",
        email = "bricio@example.com",
        photoUrl = null,
        username = "bricio"
    )

    private val incompleteUser = User(
        uid = "u2",
        displayName = "",
        email = "",
        photoUrl = null,
        username = null
    )

    @Test
    fun `Idle and not on Login navigates to Login`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Idle,
            currentRoute = Screen.Groups.route,
            pendingInviteCode = null
        )
        assertEquals(
            PostAuthNavAction.Navigate(Screen.Login.route, popUpToRoute = null, popUpToInclusive = true),
            action
        )
    }

    @Test
    fun `Idle while already on Login takes no action`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Idle,
            currentRoute = Screen.Login.route,
            pendingInviteCode = null
        )
        assertEquals(PostAuthNavAction.None, action)
    }

    @Test
    fun `Authenticated synced complete profile on Login navigates to Groups`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = Screen.Login.route,
            pendingInviteCode = null
        )
        assertEquals(
            PostAuthNavAction.Navigate(Screen.Groups.route, popUpToRoute = Screen.Login.route, popUpToInclusive = true),
            action
        )
    }

    @Test
    fun `Authenticated synced incomplete profile on Login with a pending code navigates to Account not JoinGroup`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(incompleteUser, isProfileSynced = true),
            currentRoute = Screen.Login.route,
            pendingInviteCode = "ABC123"
        )
        assertEquals(
            PostAuthNavAction.Navigate(Screen.Account.route, popUpToRoute = Screen.Login.route, popUpToInclusive = true),
            action
        )
    }

    @Test
    fun `Authenticated synced complete profile on Groups with a pending code navigates to JoinGroup`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = Screen.Groups.route,
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.Navigate(Screen.JoinGroup.createRoute("ABC123")), action)
    }

    @Test
    fun `Authenticated synced complete profile on a non-Groups screen with a pending code also navigates to JoinGroup`() {
        // Regression guard: before the round-2 Antigravity fix, this branch was gated on
        // currentRoute == Groups specifically, so a user reading PickScreen/GroupSession who
        // tapped a link would see nothing happen. Groups is always in the back stack once
        // authenticated with a complete profile, regardless of which screen is on top.
        val pickScreenAction = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = "picks/{groupId}",
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.Navigate(Screen.JoinGroup.createRoute("ABC123")), pickScreenAction)

        val groupSessionAction = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = "group_session/{groupId}",
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.Navigate(Screen.JoinGroup.createRoute("ABC123")), groupSessionAction)
    }

    @Test
    fun `a pending code never hijacks navigation while still on Login`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = Screen.Login.route,
            pendingInviteCode = "ABC123"
        )
        // Falls into the Login->Groups branch, not the join-link branch — action is Navigate to
        // Groups, not JoinGroup, even though a code is pending.
        assertEquals(
            PostAuthNavAction.Navigate(Screen.Groups.route, popUpToRoute = Screen.Login.route, popUpToInclusive = true),
            action
        )
    }

    @Test
    fun `a pending code never hijacks navigation while still on Account`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = Screen.Account.route,
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.None, action)
    }

    @Test
    fun `Authenticated but not yet profile-synced takes no action even with a pending code`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = false),
            currentRoute = Screen.Groups.route,
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.None, action)
    }

    @Test
    fun `a null currentRoute never triggers the join-link branch, even with everything else matching`() {
        // Regression guard for a real crash found in implementation review: null != Login.route
        // and null != Account.route are both true, so without an explicit null check this
        // branch could fire before NavHost's currentDestination resolves (e.g. the very first
        // composition), navigating to JoinGroup before Groups is guaranteed to be in the back
        // stack — Screen.JoinGroup's getBackStackEntry(Groups) would then throw.
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = null,
            pendingInviteCode = "ABC123"
        )
        assertEquals(PostAuthNavAction.None, action)
    }

    @Test
    fun `no pending code on Groups takes no action`() {
        val action = decidePostAuthNavigation(
            authState = AuthUiState.Authenticated(completeUser, isProfileSynced = true),
            currentRoute = Screen.Groups.route,
            pendingInviteCode = null
        )
        assertEquals(PostAuthNavAction.None, action)
    }
}
