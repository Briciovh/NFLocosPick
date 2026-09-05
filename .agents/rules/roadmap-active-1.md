# PR Roadmap — Active, part 1 (PR-14 to PR-17)

Mirrors CLAUDE.md's "PR Roadmap" section, PR-14 through PR-17. Source of truth is CLAUDE.md; update both together. See `roadmap-shipped.md` for PR-1 through PR-13 and `roadmap-active-2.md` for PR-18 onward.

### PR-14 — Size & Space Correction
**Branch:** `feature/14-size-space-correction`

A corrective PR, not new scope: PR-11, PR-12, and PR-13 all shipped, but none of them addressed the user's three original complaints — fonts still read as small, layouts still felt cramped with large wasted space, and team logos/icons never actually got bigger. Root cause for the layout piece: PR-12's "adaptive" column/sizing logic gated on `currentWindowAdaptiveInfo().windowSizeClass`, which buckets by dp width — every phone in portrait, regardless of physical screen size, lands in `COMPACT` (<600dp), so PR-12's "more columns on wider screens" logic never activated on any phone tested. Separately, icon/logo sizing was simply never in scope of any of the three prior PRs — a planning gap, not a regression. This PR fixes both, directly, with concrete dp changes rather than another abstraction layer.

- `TeamSelectionScreen.kt` — column count now derives from `BoxWithConstraints`-measured actual available width instead of `WindowSizeClass` (`columns = floor(availableWidth / CELL_SIZE)`), so it responds correctly to real screen size instead of being stuck at a hardcoded phone-bucket value; `CELL_SIZE` increased 72dp → 104dp, internal `TeamLogo` 44dp → 76dp
- `TeamLogo` call sites resized app-wide: `ScheduleScreen` 48→72dp, `PickScreen` 40→64dp (plus a `Button` `contentPadding` reduction to make room), `SettingsScreen`/`AccountScreen` favorite-team rows unified to 52dp, `HistoryScreen`'s dense inline logos 20→28dp (kept modest — most space-constrained context in the app)
- `GroupsScreen.kt` `GroupCard` — new `Row` layout with a 96dp rounded placeholder avatar (group name's first letter on an accent-tinted background — presentation-only, no domain/Firestore changes, a deliberate seam for a future real group-photo feature that is explicitly out of scope here); card padding 16→20dp, `LazyColumn` spacing 8→20dp
- `GroupSessionScreen.kt` bottom `NavigationBar` icons 24dp (M3 default) → 30dp
- Note on `WindowSizeClass`: `Modifier.responsiveCardWidth()` (`presentation/common/AdaptiveLayout.kt`) is untouched — capping card width on genuinely wide tablet screens is exactly what `WindowSizeClass` is for. The PR-12 mistake was specifically using it to gate *phone-scale* sizing decisions (column count, cell size), where it can't distinguish "small phone" from "huge phone" since both stay `COMPACT`.

### PR-15 — Week Tabs & Live Refresh
**Branch:** `feature/15-week-tabs-refresh`

Adds an ESPN-style horizontally scrollable week-tab row to `PickScreen` so users can browse and submit picks for any week of the season (preseason, regular, postseason), not just whatever ESPN's API currently considers "the current week." Also closes a gap discovered while building this: `PickScreen` is the screen actually wired to the "My Picks" bottom-nav destination, but a previous change had added pull-to-refresh + a 5-minute auto-refresh loop to `ScheduleScreen`/`ScheduleViewModel` instead — a route that was defined (`Screen.kt`, `NavGraph.kt`) but never navigated to anywhere in the app. That dead screen is removed; the refresh work is ported to `PickViewModel`, where it actually runs.

- `domain/model/Game.kt` gains `weekNumber: Int` (ESPN's raw API week number; defaulted so existing call sites don't need updates) — preseason 1=Hall of Fame Game, 2-4=the three real preseason weeks (ESPN's site splits the HOF game into its own tab, offset by +1 from "PRE WK 1-3"); regular season 1-18; postseason 1,2,3,5 (4 is the empty Pro Bowl bye week, intentionally absent as a tab)
- `domain/model/SeasonWeek.kt` (new) + `NflSeasonCalendar` — the static 26-entry season structure the tab row renders; deliberately never constructs a `weekId` itself (postseason `weekId`'s year comes from each game's own Jan/Feb kickoff, an existing documented ambiguity) — `weekId` always comes from fetched `Game` objects
- `ScheduleRepository.getGamesForWeek(seasonType, weekNumber)` (new, alongside the existing `getCurrentWeekGames`) backed by ESPN's `?seasontype=&week=` params — unlike `getCurrentWeekGames`, intentionally skips both the Firestore cache (browsing an arbitrary week would otherwise pollute `HistoryScreen`, which surfaces every `weeks/{weekId}` doc with a non-empty `games[]`) and the debug kickoff-time offset (would make every future week falsely appear to kick off tomorrow)
- `PickViewModel` — adds per-tab-index in-memory caching, a `selectedWeekIndex`/`currentWeekIndex` state pair (the latter drives disabling the manual sync icon off the current-week tab, since the scoring Cloud Function only ever scores the actual current week), and the ported `isRefreshing`/`refresh()`/5-min auto-refresh loop (`Dispatchers.Default`, not `viewModelScope`'s default `Main.immediate` — the shared `TestDispatcher` used by `PickViewModelTest`/`PickViewModelIntegrationTest` would otherwise hang `runTest`'s cleanup forever on the infinite loop)
- `PickScreen.kt` — new `WeekTabRow` (`PrimaryScrollableTabRow`) and `PullToRefreshBox` wrapping the games list

### PR-16 — Global Group Foundation
**Branch:** `feature/16-global-group-foundation`

First of a 5-PR series adding a default, always-present group ("NFLocos de Corazón") every user belongs to — pinned first in the group list, with a fixed admin and its own feed panel on `GroupsScreen` (PR-17 adds auto-membership/standings, PR-18 verifies/tightens board-admin rules, PR-19 adds the feed panel, PR-20 adds inactivity-based deactivation). No prior art exists for a pinned/system group, a non-`createdBy`-derived admin, or auto-membership — this series builds all three on top of existing per-group primitives rather than introducing a parallel group type.

- One-time Admin SDK seed (not a permanent Cloud Function) creates `groups/{GLOBAL_GROUP_ID}` with `name = "NFLocos de Corazón"`, `createdBy` = the UID of `nezaboost@gmail.com` (resolved via the existing `usernames/saulbrisniega` → `userId` lookup, not hardcoded by email), `memberIds = [that uid]`. `GLOBAL_GROUP_ID` is a fixed, reserved id shared between client code and `firestore.rules`.
- `GroupAvatar.kt` — fallback chain (`photoUrl` → `iconId` → letter) gains a `localIconRes: Int?` tier ahead of `iconId`/letter, to render the bundled `nflocos_picks_icon.png` (already in `app/src/main/res/drawable/`) instead of a remote photo or `Icons.Filled.*` vector.
- `GroupViewModel.observeGroups` — sorts the real Firestore-backed group list so `id == GLOBAL_GROUP_ID` always comes first (extends the existing "prepend a synthetic group" pattern already used for `MockDataProvider.MOCK_GROUP`, but for a real doc instead of a mock one).
- `firestore.rules` — new clause on `groups/{groupId}` `delete` denying deletion outright when `groupId == GLOBAL_GROUP_ID`, regardless of `createdBy` (today `delete` only checks `createdBy`, which would otherwise let the admin delete the global group). Deploy immediately per the rules-deploy rule below.

### PR-17 — Global Group Auto-Membership & Standings Seeding
**Branch:** `feature/17-global-group-membership`

- `UserRepositoryImpl.upsertAndResolveRole` — in the `isNewUser` branch, self-add the user to the global group's `memberIds` via `arrayUnion` (already permitted by the existing `groups/{groupId}` `update` rule's self-join carve-out — no rule change needed for this part).
- New Cloud Function `onCall` (e.g. `ensureGlobalStanding`, `functions/src/`, same shape as `scoreGroupWeek`) seeds `standings/{GLOBAL_GROUP_ID}/members/{userId}` as `{ totalPoints: 0, weeklyBreakdown: {} }` if missing — called by the client right after the auto-join in `upsertAndResolveRole`, since `standings` writes are `allow write: if false` for clients.
- One-time Admin SDK backfill script adds every existing `users/{uid}` doc's uid to the global group's `memberIds` and seeds their zero-point standing, so pre-existing users aren't left out.
