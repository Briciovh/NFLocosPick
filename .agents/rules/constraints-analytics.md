# Key Constraints & Analytics — NFLocosPick

Mirrors CLAUDE.md's "Key Constraints" section, including the Analytics subsection and event inventory. Source of truth is CLAUDE.md; update both together.

## Key Constraints

- `minSdk 24` — no API below Android 7.0
- Dynamic color (Material You) is **disabled** — the app uses a fixed Blue Steel dark theme. Do not re-enable it.
- All Firestore writes must use transactions or batched writes when updating both a pick and a standing simultaneously (PR-6)
- User preferences (favorite team, font-size preference) are stored in **Jetpack DataStore** on-device, not in Firestore.
- `google-services.json` is never committed — add a real one from the Firebase Console to `app/` to enable Firebase at runtime. The `google-services` plugin is applied conditionally in `app/build.gradle.kts` so the project builds without it.

## Analytics

All Firebase Analytics logging goes through the single choke point `AppLogger.logEvent(event: AppEvent)` (`analytics/AppLogger.kt`), injected into ViewModels only — never into Composables or Repositories. Every event is a case of the sealed class `analytics/AppEvent.kt`; add new events there, not as ad-hoc `firebaseAnalytics.logEvent(...)` calls elsewhere. **Never pass `display_name`/`email`/any PII as an event param or user property** — Google's Firebase Analytics/GA4 terms prohibit it and doing so risks the Analytics property being suspended. User attribution goes through Firebase's native Analytics User-ID (`AppLogger.setUserId(uid)`, set once per session, not per event) instead. `group.name` and team nicknames are not PII and are safe to log directly. A new event param is not filterable in the GA4 console until it is separately registered as a Custom Dimension (Admin → Custom definitions); the free tier caps these at 50 event-scoped / 25 user-scoped, so register new params deliberately rather than by default.

### Event Inventory (Current)

| # | Event | Params | Source |
|---|---|---|---|
| 1 | `sign_in` | `method` | Auth |
| 2 | `sign_up` | `method` | Auth |
| 3 | `sign_out` | — (triggers `setUserId(null)`) | Auth |
| 4 | `account_deleted` | — (triggers `setUserId(null)`) | Auth |
| 5 | `group_created` | `group_id`, `group_name` | Groups |
| 6 | `group_joined` | `group_id`, `group_name`, `source` | Groups |
| 7 | `group_opened` | `group_id`, `group_name?`, `source` | Groups |
| 8 | `scoring_completed` | `group_id`, `scored_count`, `source` | Groups/Picks |
| 9 | `pick_submitted` | `group_id`, `week_id`, `game_id`, `team_abbr`, `team_name?`, `season_type`, `week_number` | Picks |
| 10 | `leaderboard_viewed` | `group_id`, `group_name?` | Leaderboard |
| 11 | `pick_history_viewed` | `group_id`, `group_name?` | History |
| 12 | `favorite_team_set` | `team_abbr`, `team_name?` | Settings |
| 13 | `language_changed` | `language_tag` | Settings |
| 14 | `board_message_sent` | `group_id`, `message_type`, `action`, `is_group_admin`, `group_name?` | Board |
| 15 | `board_message_deleted` | `group_id`, `is_group_admin`, `is_own_message`, `group_name?` | Board |
| 16 | `board_announcement_toggled` | `group_id`, `is_announcement`, `group_name?` | Board |
| 17 | `screen_viewed` | `screen_name` | Navigation |
| 18 | `week_tab_selected` | `group_id`, `season_type`, `week_number` | Picks |
| 19 | `pick_refresh` | `group_id`, `trigger="manual"` | Picks |
| 20 | `pick_auto_refresh_failed` | `group_id` | Picks |
| 21 | `leaderboard_tab_selected` | `group_id`, `season_type` | Leaderboard |
| 22 | `history_week_toggled` | `group_id`, `week_id`, `expanded` | History |
| 23 | `font_scale_changed` | `scale` | Settings |
| 24 | `icon_scale_changed` | `scale` | Settings |
| 25 | `group_photo_uploaded` | `group_id` | Groups |
| 26 | `group_icon_set` | `group_id`, `icon_id` | Groups |
| 27 | `user_role_changed` | `target_user_id`, `new_role` | User Mgmt |
| 28 | `profile_saved` | — | Account |
| 29 | `profile_photo_uploaded` | — | Account |
| 30 | `account_email_link_sent` | — | Account |
| 31 | `phone_link_verified` | — | Account |
| 32 | `password_changed` | — | Account |
| 33 | `global_group_auto_joined` | — | Auth |
| 34 | `group_renamed` | `group_id` | Groups |
| 35 | `group_deleted` | `group_id` | Groups |
| 36 | `group_member_removed` | `group_id`, `target_user_id`, `blocked` | Groups |
| 37 | `group_member_unblocked` | `group_id`, `target_user_id` | Groups |
