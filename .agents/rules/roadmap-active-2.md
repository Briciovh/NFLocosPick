# PR Roadmap — Active, part 2 (PR-18 to PR-23)

Mirrors CLAUDE.md's "PR Roadmap" section, PR-18 through PR-23. Source of truth is CLAUDE.md; update both together. See `roadmap-active-1.md` for PR-14 through PR-17.

### PR-18 — Global Board Admin Verification & Rule Tightening
**Branch:** `feature/18-global-board-admin`

- Verify `BoardViewModel.isGroupAdmin` (already `Group.createdBy == currentUserId`) works unmodified for the global group's board, once `createdBy` is nezaboost's uid from PR-16 — add explicit test coverage for this group specifically.
- Tighten `firestore.rules` `board/{messageId}` `update` to gate the `isAnnouncement` toggle with `diff(resource.data).affectedKeys()` (same pattern already used for `groups/{groupId}`'s `photoUrl`/`iconId`), instead of allowing any field update from the author or `createdBy` without distinguishing which field changed — closes a pre-existing gap that would otherwise let a non-admin author flip `isAnnouncement` on their own message via a generic update call.

### PR-19 — Global Feed Panel on GroupsScreen
**Branch:** `feature/19-global-feed-panel`

- New composable (e.g. `GlobalGroupFeedPanel`, `presentation/groups/`) — fixed, read-only, non-scrolling panel showing the latest messages/announcements from the global group's board via the existing `WatchBoardMessagesUseCase` (unchanged); tapping navigates into the full board (`GroupSessionScreen` with `groupId = GLOBAL_GROUP_ID`).
- `GroupsScreenContent` — restructures the body `Column` under the `Scaffold`: the groups `LazyColumn` keeps ~2/3 of the available height (`weight`), the feed panel takes the bottom ~1/3, with enough bottom padding that the create/join FABs never overlap it.

### PR-20 — Account Inactivity Deactivation
**Branch:** `feature/20-inactivity-deactivation`

- `User` (domain) + `users/{uid}` gain `lastActive` (timestamp), self-stamped by the owner on every sign-in inside `upsertAndResolveRole` (already permitted by the existing `users/{userId}` write rule — no rule change for this field). This also implements automatic reactivation: any sign-in refreshes `lastActive`.
- `users/{uid}` gains `isActive`/`disabledAt` — unlike `lastActive`, this field **must** be locked against client writes (new `firestore.rules` clause on `users/{userId}` using `diff().affectedKeys()` to exclude it from the owner's otherwise-unrestricted self-write), since a client could otherwise self-reactivate by writing the field directly.
- New Cloud Function `onSchedule` (`functions/src/inactivity.ts`, same cron-scheduling pattern as `scheduledScoring`, daily) scans `users` for `lastActive` older than one year, sets `isActive = false`, and removes/hides that user's `standings/{groupId}/members/{userId}` entry across every group they belong to (not just the global one) — same per-group iteration pattern as `accountDeletion.ts`'s `groups.where("memberIds", "array-contains", uid)`. No separate reactivation logic is needed: the next scheduled run simply re-includes anyone whose `lastActive` was refreshed by a sign-in.

### PR-21 — Analytics Infrastructure, User-ID & Event Enrichment
**Branch:** `feature/21-analytics-infrastructure`

Full parameter-level specification, exact file/function references, and event signatures for this 3-PR series live in `docs/plans/analytics-enrichment.md` — read that file before implementing any of PR-21/22/23.

First of a 3-PR series closing the gap between what the app logs to Firebase Analytics and what the owner can actually read on the dashboard (475 unattributable `screen_view` events, 231 unattributable `pick_submitted` events, no group/team names anywhere). Root cause: all 16 existing events (defined in `analytics/AppEvent.kt`, logged through the single `AppLogger.logEvent()` choke point) carry almost no context, and `AppEvent.ScreenViewed` — defined from the start — has never actually been called, so the app relies on GA4's near-useless automatic `screen_view` under a single-Activity Compose app. Per policy, no event may ever carry `display_name`/`email` (Google prohibits PII in event params/user properties); user attribution instead goes through Firebase's native Analytics User-ID.

- `AppLogger.setUserId(uid: String?)` — new method, same Timber-then-Firebase pattern as `logEvent`. Called once per session from `AuthViewModel.watchRole(uid)` (the single choke point every sign-in/session-restore path already funnels through), and cleared (`setUserId(null)`) in `signOut()`/`deleteAccount()`. No event needs a repeated `user_id` param — dashboard filtering by user comes from the native User-ID/User Explorer feature.
- New `ScreenTrackingViewModel` + reusable `TrackScreenView` composable + `routeToScreenName()` mapping (`presentation/navigation/`), wired into both `NavGraph.kt`'s NavHost and `GroupSessionScreen.kt`'s nested NavHost, keyed on the route pattern (not the backstack entry) to avoid duplicate firing on recomposition. Activates the previously dead `screen_viewed` event.
- Enrichment of all 16 existing events with non-PII context already in scope at each call site: `group_name` on `GroupCreated`/`GroupJoined`/`GroupOpened`/`LeaderboardViewed`/`PickHistoryViewed`/board events; `source` on `GroupOpened` and `ScoringCompleted`; `team_name`/`season_type`/`week_number` on `PickSubmitted`; `team_name` on `FavoriteTeamSet`; `is_group_admin`/`is_own_message` on board events. New `nflTeamNameByAbbr` map in `NflTeams.kt`; `GroupRepository` newly injected into `LeaderboardViewModel`/`HistoryViewModel` for `group_name` resolution.

### PR-22 — Expanded Analytics Coverage for Untracked Actions
**Branch:** `feature/22-analytics-coverage`

Second of the series — instruments the ~14 user actions that have zero analytics tracking today, following the non-PII conventions PR-21 established. Excludes the inactivity Cloud Function (server-side, no client Analytics path) and deliberately avoids logging every tick of the 5-minute pick auto-refresh loop, to keep the dashboard from drowning in a periodic background event.

- `AppLogger` newly injected into `UserManagementViewModel`, `AccountViewModel`, `ChangePasswordViewModel` (none of the three have it today).
- New events: `week_tab_selected`, `pick_refresh` (manual only), `pick_auto_refresh_failed` (failures only), `leaderboard_tab_selected`, `history_week_toggled`, `font_scale_changed`, `icon_scale_changed`, `group_photo_uploaded`, `group_icon_set`, `user_role_changed`, `profile_saved`, `profile_photo_uploaded`, `account_email_link_sent`, `phone_link_verified`, `password_changed`, `global_group_auto_joined`.
- `board_message_sent` gains an `action` param (`"sent"` vs `"edited"`) and now also fires from the previously-silent edit branch of `BoardViewModel.sendOrSaveMessage()`.
- `scoring_completed` reused (not duplicated) from `PickViewModel.triggerSync()` with `source="pick_manual_sync"`, distinguishing manual sync from the group-card scoring trigger.

### PR-23 — GA4 Console Custom Dimensions & Analytics Documentation
**Branch:** `feature/23-ga4-console-setup`

Third and final PR of the series — mostly a console/ops step plus documentation, not app code: registering a new event param in `AppEvent.kt` does not make it filterable in the GA4 console until it's separately registered as a Custom Dimension (Admin → Custom definitions), and the free tier caps event-scoped dimensions at 50 / user-scoped at 25.

- Manual step (Firebase console): register a prioritized subset (~10) of the new/enriched params as event-scoped Custom Dimensions — `group_name`, `team_name`, `source`, `screen_name`, `season_type`, `week_number`, `is_group_admin`, `is_own_message`, `action`/`message_type`, `group_id`. `user_id` needs no registration — it's natively usable via User Explorer as soon as `setUserId` starts firing in PR-21.
- Recommendation (not implemented): enable BigQuery Export (Admin → Project Settings → Integrations) once the 50-dimension cap becomes limiting, for raw SQL access to every param.
- `CLAUDE.md` "Key Constraints" gains an "Analytics" subsection listing the full event inventory, so future PRs adding events don't reintroduce the same enrichment gap.
