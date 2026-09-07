package com.softeen.nflocospicks.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks the wire contract of every [AppEvent]: the GA4 event `name` and the
 * exact `params` map. Special attention to the `buildMap` events, which must
 * OMIT (not null-fill) an absent optional param — Firebase drops null values
 * anyway, but an explicit absent key keeps assertions and the GA4 Custom
 * Dimension registration unambiguous.
 */
class AppEventTest {

    @Test
    fun `simple method events carry name and single param`() {
        assertThat(AppEvent.SignIn("google").name).isEqualTo("sign_in")
        assertThat(AppEvent.SignIn("google").params).containsExactly("method", "google")
        assertThat(AppEvent.SignUp("password").params).containsExactly("method", "password")
    }

    @Test
    fun `object events have no params`() {
        assertThat(AppEvent.SignOut.name).isEqualTo("sign_out")
        assertThat(AppEvent.SignOut.params).isEmpty()
        assertThat(AppEvent.AccountDeleted.params).isEmpty()
        assertThat(AppEvent.GlobalGroupAutoJoined.name).isEqualTo("global_group_auto_joined")
        assertThat(AppEvent.ProfileSaved.params).isEmpty()
        assertThat(AppEvent.PasswordChanged.name).isEqualTo("password_changed")
    }

    @Test
    fun `GroupCreated and GroupJoined carry their full param set`() {
        assertThat(AppEvent.GroupCreated("g1", "Los Locos").params)
            .containsExactly("group_id", "g1", "group_name", "Los Locos")
        assertThat(AppEvent.GroupJoined("g1", "Los Locos", "invite_link").params)
            .containsExactly("group_id", "g1", "group_name", "Los Locos", "source", "invite_link")
    }

    @Test
    fun `GroupOpened includes group_name only when non-null`() {
        assertThat(AppEvent.GroupOpened("g1", "Los Locos", "list").params)
            .containsExactly("group_id", "g1", "group_name", "Los Locos", "source", "list")

        val withoutName = AppEvent.GroupOpened("g1", null, "list")
        assertThat(withoutName.params).containsExactly("group_id", "g1", "source", "list")
        assertThat(withoutName.params).doesNotContainKey("group_name")
    }

    @Test
    fun `PickSubmitted includes team_name only when non-null and keeps week_number as Int`() {
        val full = AppEvent.PickSubmitted(
            groupId = "g1", weekId = "2025-week-01", gameId = "401",
            teamAbbr = "KC", teamName = "Chiefs", seasonType = "REGULAR", weekNumber = 1
        )
        assertThat(full.name).isEqualTo("pick_submitted")
        assertThat(full.params).containsExactly(
            "group_id", "g1",
            "week_id", "2025-week-01",
            "game_id", "401",
            "team_abbr", "KC",
            "team_name", "Chiefs",
            "season_type", "REGULAR",
            "week_number", 1
        )
        assertThat(full.params["week_number"]).isInstanceOf(Int::class.javaObjectType)

        val noName = full.copy(teamName = null)
        assertThat(noName.params).doesNotContainKey("team_name")
        assertThat(noName.params).containsKey("team_abbr")
    }

    @Test
    fun `FavoriteTeamSet omits team_name when null`() {
        assertThat(AppEvent.FavoriteTeamSet("KC", "Chiefs").params)
            .containsExactly("team_abbr", "KC", "team_name", "Chiefs")
        assertThat(AppEvent.FavoriteTeamSet("KC", null).params)
            .containsExactly("team_abbr", "KC")
    }

    @Test
    fun `LeaderboardViewed and PickHistoryViewed omit group_name when null`() {
        assertThat(AppEvent.LeaderboardViewed("g1", null).params).containsExactly("group_id", "g1")
        assertThat(AppEvent.LeaderboardViewed("g1", "Los Locos").params)
            .containsExactly("group_id", "g1", "group_name", "Los Locos")
        assertThat(AppEvent.PickHistoryViewed("g1", null).params).containsExactly("group_id", "g1")
    }

    @Test
    fun `board events keep boolean params and omit group_name when null`() {
        val sent = AppEvent.BoardMessageSent("g1", "announcement", "sent", isGroupAdmin = true, groupName = null)
        assertThat(sent.name).isEqualTo("board_message_sent")
        assertThat(sent.params).containsExactly(
            "group_id", "g1", "message_type", "announcement", "action", "sent", "is_group_admin", true
        )
        assertThat(sent.params["is_group_admin"]).isInstanceOf(Boolean::class.javaObjectType)

        val deleted = AppEvent.BoardMessageDeleted("g1", isGroupAdmin = false, isOwnMessage = true, groupName = "Los Locos")
        assertThat(deleted.params).containsExactly(
            "group_id", "g1", "is_group_admin", false, "is_own_message", true, "group_name", "Los Locos"
        )

        val toggled = AppEvent.BoardAnnouncementToggled("g1", isAnnouncement = true, groupName = null)
        assertThat(toggled.params).containsExactly("group_id", "g1", "is_announcement", true)
    }

    @Test
    fun `numeric and misc events carry correctly typed params`() {
        assertThat(AppEvent.ScoringCompleted("g1", 7, "pick_manual_sync").params)
            .containsExactly("group_id", "g1", "scored_count", 7, "source", "pick_manual_sync")
        assertThat(AppEvent.PickRefresh("g1").params).containsExactly("group_id", "g1", "trigger", "manual")
        assertThat(AppEvent.ApiLatency("scoreboard", 1234L).params)
            .containsExactly("endpoint", "scoreboard", "latency_ms", 1234L)
        assertThat(AppEvent.WeekTabSelected("g1", "PRESEASON", 3).params)
            .containsExactly("group_id", "g1", "season_type", "PRESEASON", "week_number", 3)
        assertThat(AppEvent.ScreenViewed("groups").params).containsExactly("screen_name", "groups")
    }

    @Test
    fun `event names are unique across the sealed hierarchy`() {
        assertThat(allEventSamples.map { it.name }).containsNoDuplicates()
    }

    @Test
    fun `sample list covers every distinct AppEvent type`() {
        // Guard against a new event being added to the sealed class without a
        // sample here (which would silently skip it from the uniqueness / param
        // checks). Reflection (`sealedSubclasses`) is intentionally avoided to
        // keep this suite free of a kotlin-reflect dependency — bump
        // EXPECTED_EVENT_TYPES when you add an AppEvent subclass and give it a
        // sample above.
        assertThat(allEventSamples.map { it::class }.toSet()).hasSize(EXPECTED_EVENT_TYPES)
        assertThat(allEventSamples).hasSize(EXPECTED_EVENT_TYPES)
    }

    private companion object {
        const val EXPECTED_EVENT_TYPES = 37

        val allEventSamples: List<AppEvent> = listOf(
            AppEvent.SignIn("x"), AppEvent.SignUp("x"), AppEvent.SignOut, AppEvent.AccountDeleted,
            AppEvent.GlobalGroupAutoJoined, AppEvent.GroupCreated("g", "n"),
            AppEvent.GroupJoined("g", "n", "s"), AppEvent.GroupOpened("g", null, "s"),
            AppEvent.ScoringCompleted("g", 0, "s"), AppEvent.GroupPhotoUploaded("g"),
            AppEvent.GroupIconSet("g", "i"), AppEvent.GroupRenamed("g"), AppEvent.GroupDeleted("g"),
            AppEvent.PickSubmitted("g", "w", "ga", "KC", null, "REGULAR", 1),
            AppEvent.WeekTabSelected("g", "REGULAR", 1), AppEvent.PickRefresh("g"),
            AppEvent.PickAutoRefreshFailed("g"), AppEvent.LeaderboardViewed("g", null),
            AppEvent.LeaderboardTabSelected("g", "REGULAR"), AppEvent.PickHistoryViewed("g", null),
            AppEvent.HistoryWeekToggled("g", "w", true), AppEvent.FavoriteTeamSet("KC", null),
            AppEvent.LanguageChanged("es"), AppEvent.FontScaleChanged("grande"),
            AppEvent.IconScaleChanged("grande"), AppEvent.UserRoleChanged("u", "ADMIN"),
            AppEvent.ProfileSaved, AppEvent.ProfilePhotoUploaded, AppEvent.AccountEmailLinkSent,
            AppEvent.PhoneLinkVerified, AppEvent.PasswordChanged, AppEvent.ErrorOccurred("f", "m"),
            AppEvent.ApiLatency("e", 0L), AppEvent.ScreenViewed("s"),
            AppEvent.BoardMessageSent("g", "chat", "sent", false, null),
            AppEvent.BoardMessageDeleted("g", false, false, null),
            AppEvent.BoardAnnouncementToggled("g", false, null)
        )
    }
}
