package com.softeen.nflocospicks.analytics

sealed class AppEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    // ── Auth ──────────────────────────────────────────────────────────────────
    data class SignIn(val method: String) : AppEvent("sign_in", mapOf("method" to method))
    data class SignUp(val method: String) : AppEvent("sign_up", mapOf("method" to method))
    object SignOut : AppEvent("sign_out")
    object AccountDeleted : AppEvent("account_deleted")
    object GlobalGroupAutoJoined : AppEvent("global_group_auto_joined")

    // ── Groups ────────────────────────────────────────────────────────────────
    data class GroupCreated(val groupId: String, val groupName: String) :
        AppEvent("group_created", mapOf("group_id" to groupId, "group_name" to groupName))

    data class GroupJoined(val groupId: String, val groupName: String, val source: String) :
        AppEvent("group_joined", mapOf("group_id" to groupId, "group_name" to groupName, "source" to source))

    data class GroupOpened(val groupId: String, val groupName: String?, val source: String) :
        AppEvent("group_opened", buildMap {
            put("group_id", groupId)
            groupName?.let { put("group_name", it) }
            put("source", source)
        })

    data class ScoringCompleted(val groupId: String, val scoredCount: Int, val source: String) :
        AppEvent("scoring_completed", mapOf(
            "group_id" to groupId,
            "scored_count" to scoredCount,
            "source" to source
        ))

    data class GroupPhotoUploaded(val groupId: String) :
        AppEvent("group_photo_uploaded", mapOf("group_id" to groupId))

    data class GroupIconSet(val groupId: String, val iconId: String) :
        AppEvent("group_icon_set", mapOf("group_id" to groupId, "icon_id" to iconId))

    data class GroupRenamed(val groupId: String) :
        AppEvent("group_renamed", mapOf("group_id" to groupId))

    data class GroupDeleted(val groupId: String) :
        AppEvent("group_deleted", mapOf("group_id" to groupId))

    data class GroupMemberRemoved(val groupId: String, val targetUserId: String, val blocked: Boolean) :
        AppEvent("group_member_removed",
            mapOf("group_id" to groupId, "target_user_id" to targetUserId, "blocked" to blocked))

    data class GroupMemberUnblocked(val groupId: String, val targetUserId: String) :
        AppEvent("group_member_unblocked",
            mapOf("group_id" to groupId, "target_user_id" to targetUserId))

    // ── Picks ─────────────────────────────────────────────────────────────────
    data class PickSubmitted(
        val groupId: String,
        val weekId: String,
        val gameId: String,
        val teamAbbr: String,
        val teamName: String?,
        val seasonType: String,
        val weekNumber: Int
    ) : AppEvent(
        "pick_submitted",
        buildMap {
            put("group_id", groupId)
            put("week_id", weekId)
            put("game_id", gameId)
            put("team_abbr", teamAbbr)
            teamName?.let { put("team_name", it) }
            put("season_type", seasonType)
            put("week_number", weekNumber)
        }
    )

    data class WeekTabSelected(val groupId: String, val seasonType: String, val weekNumber: Int) :
        AppEvent("week_tab_selected", mapOf(
            "group_id" to groupId,
            "season_type" to seasonType,
            "week_number" to weekNumber
        ))

    data class PickRefresh(val groupId: String) :
        AppEvent("pick_refresh", mapOf("group_id" to groupId, "trigger" to "manual"))

    data class PickAutoRefreshFailed(val groupId: String) :
        AppEvent("pick_auto_refresh_failed", mapOf("group_id" to groupId))

    // ── Leaderboard / History ─────────────────────────────────────────────────
    data class LeaderboardViewed(val groupId: String, val groupName: String?) :
        AppEvent("leaderboard_viewed", buildMap {
            put("group_id", groupId)
            groupName?.let { put("group_name", it) }
        })

    data class LeaderboardTabSelected(val groupId: String, val seasonType: String) :
        AppEvent("leaderboard_tab_selected", mapOf("group_id" to groupId, "season_type" to seasonType))

    data class PickHistoryViewed(val groupId: String, val groupName: String?) :
        AppEvent("pick_history_viewed", buildMap {
            put("group_id", groupId)
            groupName?.let { put("group_name", it) }
        })

    data class HistoryWeekToggled(val groupId: String, val weekId: String, val expanded: Boolean) :
        AppEvent("history_week_toggled", mapOf(
            "group_id" to groupId,
            "week_id" to weekId,
            "expanded" to expanded
        ))

    // ── Settings ──────────────────────────────────────────────────────────────
    data class FavoriteTeamSet(val teamAbbr: String, val teamName: String?) :
        AppEvent("favorite_team_set", buildMap {
            put("team_abbr", teamAbbr)
            teamName?.let { put("team_name", it) }
        })

    data class LanguageChanged(val languageTag: String) :
        AppEvent("language_changed", mapOf("language_tag" to languageTag))

    data class FontScaleChanged(val scale: String) :
        AppEvent("font_scale_changed", mapOf("scale" to scale))

    data class IconScaleChanged(val scale: String) :
        AppEvent("icon_scale_changed", mapOf("scale" to scale))

    // ── Account / User Management ─────────────────────────────────────────────
    data class UserRoleChanged(val targetUserId: String, val newRole: String) :
        AppEvent("user_role_changed", mapOf("target_user_id" to targetUserId, "new_role" to newRole))

    object ProfileSaved : AppEvent("profile_saved")
    object ProfilePhotoUploaded : AppEvent("profile_photo_uploaded")
    object AccountEmailLinkSent : AppEvent("account_email_link_sent")
    object PhoneLinkVerified : AppEvent("phone_link_verified")
    object PasswordChanged : AppEvent("password_changed")

    // ── Errors & Performance ──────────────────────────────────────────────────
    data class ErrorOccurred(val feature: String, val message: String) :
        AppEvent("error_occurred", mapOf("feature" to feature, "message" to message))

    data class ApiLatency(val endpoint: String, val latencyMs: Long) :
        AppEvent("api_latency", mapOf("endpoint" to endpoint, "latency_ms" to latencyMs))

    // ── Screen Tracking ───────────────────────────────────────────────────────
    data class ScreenViewed(val screenName: String) :
        AppEvent("screen_viewed", mapOf("screen_name" to screenName))

    // ── Board ─────────────────────────────────────────────────────────────────
    data class BoardMessageSent(
        val groupId: String,
        val messageType: String,
        val action: String,
        val isGroupAdmin: Boolean,
        val groupName: String?
    ) : AppEvent("board_message_sent", buildMap {
        put("group_id", groupId)
        put("message_type", messageType)
        put("action", action)
        put("is_group_admin", isGroupAdmin)
        groupName?.let { put("group_name", it) }
    })

    data class BoardMessageDeleted(
        val groupId: String,
        val isGroupAdmin: Boolean,
        val isOwnMessage: Boolean,
        val groupName: String?
    ) : AppEvent("board_message_deleted", buildMap {
        put("group_id", groupId)
        put("is_group_admin", isGroupAdmin)
        put("is_own_message", isOwnMessage)
        groupName?.let { put("group_name", it) }
    })

    data class BoardAnnouncementToggled(
        val groupId: String,
        val isAnnouncement: Boolean,
        val groupName: String?
    ) : AppEvent("board_announcement_toggled", buildMap {
        put("group_id", groupId)
        put("is_announcement", isAnnouncement)
        groupName?.let { put("group_name", it) }
    })
}
