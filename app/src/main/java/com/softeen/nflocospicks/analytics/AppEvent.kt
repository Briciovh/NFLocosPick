package com.softeen.nflocospicks.analytics

sealed class AppEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    // ── Auth ──────────────────────────────────────────────────────────────────
    object SignIn  : AppEvent("sign_in",  mapOf("method" to "google"))
    object SignUp  : AppEvent("sign_up",  mapOf("method" to "google"))
    object SignOut : AppEvent("sign_out")

    // ── Groups ────────────────────────────────────────────────────────────────
    data class GroupCreated(val groupId: String) :
        AppEvent("group_created", mapOf("group_id" to groupId))

    data class GroupJoined(val groupId: String) :
        AppEvent("group_joined", mapOf("group_id" to groupId))

    data class GroupOpened(val groupId: String) :
        AppEvent("group_opened", mapOf("group_id" to groupId))

    data class ScoringCompleted(val groupId: String, val scoredCount: Int) :
        AppEvent("scoring_completed", mapOf("group_id" to groupId, "scored_count" to scoredCount))

    // ── Picks ─────────────────────────────────────────────────────────────────
    data class PickSubmitted(
        val groupId: String,
        val weekId: String,
        val gameId: String,
        val teamAbbr: String
    ) : AppEvent(
        "pick_submitted",
        mapOf("group_id" to groupId, "week_id" to weekId, "game_id" to gameId, "team_abbr" to teamAbbr)
    )

    // ── Leaderboard / History ─────────────────────────────────────────────────
    data class LeaderboardViewed(val groupId: String) :
        AppEvent("leaderboard_viewed", mapOf("group_id" to groupId))

    data class PickHistoryViewed(val groupId: String) :
        AppEvent("pick_history_viewed", mapOf("group_id" to groupId))

    // ── Settings ──────────────────────────────────────────────────────────────
    data class FavoriteTeamSet(val teamAbbr: String) :
        AppEvent("favorite_team_set", mapOf("team_abbr" to teamAbbr))

    data class LanguageChanged(val languageTag: String) :
        AppEvent("language_changed", mapOf("language_tag" to languageTag))

    // ── Board ─────────────────────────────────────────────────────────────────
    data class BoardMessageSent(val groupId: String, val messageType: String) :
        AppEvent("board_message_sent", mapOf("group_id" to groupId, "message_type" to messageType))

    data class BoardMessageDeleted(val groupId: String) :
        AppEvent("board_message_deleted", mapOf("group_id" to groupId))

    data class BoardAnnouncementToggled(val groupId: String, val isAnnouncement: Boolean) :
        AppEvent("board_announcement_toggled", mapOf("group_id" to groupId, "is_announcement" to isAnnouncement))
}
