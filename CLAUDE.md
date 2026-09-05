# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NFLocosPick** is a private-group NFL pick'em Android app. Each week, members of a group pick teams from NFL matchups. Results are auto-scored after games end and tracked on a season-long leaderboard.

**Stack:**
- **Android** (Kotlin, Jetpack Compose, Material 3)
- **Firebase** — Auth (Google Sign-In), Firestore (data), Cloud Functions (auto-scoring)
- **ESPN unofficial API** — free, no key required, provides weekly schedule + live scores
- **Hilt** — dependency injection
- **Retrofit** — HTTP client for ESPN API
- **Navigation Compose** — screen routing

---

## Build & Test Commands

All commands run from the repo root. On Windows use `gradlew.bat`; on Mac/Linux use `./gradlew`.

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.softeen.nflocospicks.ExampleUnitTest"

# Run a single test method
./gradlew test --tests "com.softeen.nflocospicks.ExampleUnitTest.addition_isCorrect"

# Run instrumented tests (requires connected device or emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Clean
./gradlew clean
```

---

## Target Architecture

The app follows **Clean Architecture** with three explicit layers, and **MVVM** as the presentation pattern (planned migration to **MVI** in a future refactor — design ViewModels to be state-holder-friendly so the transition is low friction).

### Layer responsibilities

| Layer | Package | Rule |
|---|---|---|
| **Presentation** | `presentation/` | Composables, ViewModels, UI state classes. No direct data-source access. |
| **Domain** | `domain/` | Pure Kotlin. No Android framework imports. Use cases own all business logic. Defines repository interfaces. |
| **Data** | `data/` | Implements domain interfaces. Owns all I/O: Firestore, ESPN API, WorkManager scheduling. |

Dependencies flow **inward only**: `presentation → domain ← data`. The domain layer knows nothing about Firebase or Retrofit.

### Package structure

```
app/src/main/java/com/softeen/nflocospicks/
│
├── domain/                         # Pure Kotlin — no Android/Firebase/Retrofit imports
│   ├── model/                      # Entity classes (User, Group, Game, Pick, Standing, UserPreferences…)
│   ├── repository/                 # Repository interfaces (GroupRepository, PickRepository…)
│   └── usecase/                    # One class per use case (ScoreWeekPicksUseCase, SubmitPickUseCase…)
│
├── data/                           # Implements domain interfaces
│   ├── remote/
│   │   ├── espn/                   # Retrofit service + ESPN DTOs + mappers → domain models
│   │   └── firebase/               # Firestore data sources + mappers → domain models
│   ├── repository/                 # Concrete repository implementations (injected via Hilt)
│   │   └── UserPreferencesRepositoryImpl  # DataStore-backed; NOT Firestore
│   └── worker/                     # WorkManager Workers (e.g. ScoringWorker)
│
├── presentation/                   # Android / Compose layer
│   ├── theme/                      # Color.kt (Blue Steel palette), Type.kt, Theme.kt, AppColors.kt
│   ├── navigation/                 # NavGraph, Screen sealed class, NavHost wiring
│   ├── common/                     # Shared UI utilities: TeamLogo, NflTeams, NflTeamColors, EspnLogoUrl
│   ├── preview/                    # PreviewData.kt + PreviewWrapper composable (internal, preview-only)
│   ├── auth/                       # LoginScreen + AuthViewModel + AuthUiState
│   ├── groups/                     # GroupsScreen, CreateGroupScreen, JoinGroupScreen + ViewModel + UiState
│   ├── picks/                      # PickScreen + PickViewModel + PickUiState — also owns the week-tab row (WeekTabLabels.kt)
│   ├── leaderboard/                # LeaderboardScreen + LeaderboardViewModel + LeaderboardUiState
│   ├── history/                    # HistoryScreen + HistoryViewModel + HistoryUiState
│   ├── settings/                   # SettingsScreen + SettingsViewModel (DataStore prefs)
│   ├── teamselection/              # TeamSelectionScreen (picks favorite NFL team)
│   ├── welcome/                    # WelcomeScreen (onboarding stub)
│   └── proposals/                  # UI design proposals — keep until PR-10 lands
│
├── di/                             # Hilt modules (NetworkModule, FirebaseModule, RepositoryModule,
│                                   #   DataStoreModule, WorkerModule)
├── NFLocosPickApp.kt               # @HiltAndroidApp
└── MainActivity.kt                 # Single Activity; hosts NavHost
```

### MVVM → MVI migration notes

- Each feature already exposes a `*UiState` data class and a `StateFlow` — keep this pattern so MVI's `State` fits in without restructuring.
- Side-effects (navigation, toasts) must go through a `Channel<UiEffect>` from day one; avoid calling nav callbacks directly from ViewModels.
- Use cases must remain pure and side-effect-free so they work identically under both patterns.

### Team-theming system (PR-10)

The app uses a fixed "Blue Steel" `MaterialTheme` (dynamic color is intentionally disabled). On top of that, two accent colors shift per the user's favorite NFL team:

- `AppColors(accent, header)` in `presentation/theme/AppColors.kt` — holds the two active colors.
- `LocalAppColors` — `CompositionLocal` defaulting to Blue Steel gold/header.
- `nflTeamColorMap` in `presentation/common/NflTeamColors.kt` — maps 32 team abbreviations to their `NflTeamColors`.
- `SettingsViewModel` (scoped to `NavGraph`) reads `UserPreferences.favoriteTeamAbbr` from DataStore, derives the `AppColors`, and provides them via `CompositionLocalProvider` at the `NavGraph` level so every screen inherits the active theme.
- All screens read colors via `LocalAppColors.current` — never hardcode `BSGold`/`BSHeader` in new UI code.

`SettingsViewModel` is instantiated once at `NavGraph` scope and shared into `SettingsScreen` and `TeamSelectionScreen` to avoid duplicate DataStore reads.

### Firestore Data Model

```
groups/{groupId}
  ├── name, inviteCode, createdBy, memberIds[]
  └── weeks/{weekId}           # e.g. "2025-week-01"
        ├── games[]            # ESPN game IDs + teams for this week
        └── picks/{userId}
              ├── gameId, pickedTeam, isCorrect, scoredAt

users/{userId}
  ├── displayName, email, photoUrl

standings/{groupId}/members/{userId}
  ├── totalPoints, weeklyBreakdown{}
```

### ESPN API Base URLs

- Schedule/scores: `https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard`
- Specific week: append `?dates=YYYYMMDD` or `?seasontype=2&week=N`

---

## PR Roadmap

Each PR has its own branch. Merge into `main` in order.

### PR-1 — Project Foundation
**Branch:** `feature/01-project-foundation`

- Add dependencies to `libs.versions.toml` + `app/build.gradle.kts`: Firebase BOM, Firebase Auth, Firebase Firestore, Hilt, Navigation Compose, Retrofit, OkHttp logging interceptor
- Apply plugins: `com.google.gms.google-services`, `com.google.dagger.hilt.android`
- Create package skeleton (`data/`, `domain/`, `ui/`, `di/`)
- Add `NFLocosPickApp.kt` (`@HiltAndroidApp`)
- Wire `NavHost` in `MainActivity`
- Add `google-services.json` (Firebase project config — **not committed to git**; add to `.gitignore`)

### PR-2 — Authentication
**Branch:** `feature/02-auth`

- Google Sign-In via Firebase Auth
- `LoginScreen` with "Sign in with Google" button
- `AuthViewModel` exposing `authState: StateFlow<AuthState>`
- `UserRepository` (wraps `FirebaseAuth` + writes user doc to Firestore `users/{uid}`)
- Nav: unauthenticated users land on `LoginScreen`; authenticated go to `GroupScreen`

### PR-3 — Groups & Invite System
**Branch:** `feature/03-groups`

- `CreateGroupScreen` — enter group name → generates a random 6-char `inviteCode`, writes `groups/{id}` to Firestore
- `JoinGroupScreen` — enter invite code → looks up group, adds `userId` to `memberIds[]`
- `GroupViewModel` with `createGroup()` / `joinGroup()` use cases
- Home screen stub listing the user's groups (real-time Firestore listener)

### PR-4 — NFL Schedule (ESPN API)
**Branch:** `feature/04-nfl-schedule`

- Retrofit `EspnApiService` interface + DTOs mapped to domain `Game` model
- `ScheduleRepository` — fetches current week's games; caches in Firestore `weeks/{weekId}/games[]`
- `ScheduleScreen` — displays matchups as cards (home vs away, date/time)
- `ScheduleViewModel` exposing `games: StateFlow<List<Game>>`

### PR-5 — Pick Submission
**Branch:** `feature/05-picks`

- `PickScreen` — shows this week's games; user taps a team to pick it; picked team is highlighted
- Picks lock automatically when the game's kickoff time passes (compare `System.currentTimeMillis()` vs `game.kickoffTime`)
- `PickRepository` — writes/reads `groups/{groupId}/weeks/{weekId}/picks/{userId}` in Firestore
- `PickViewModel` with `submitPick()` / `getPick()` / `hasPicked()` helpers
- Nav: accessible from the group home screen; scoped to a specific `groupId`

### PR-6 — Auto-Scoring
**Branch:** `feature/06-auto-scoring`

- `ScoringRepository` — polls ESPN API for final scores; compares each pick's `pickedTeam` against the actual winner; writes `isCorrect` + `scoredAt` back to each pick doc
- Update `standings/{groupId}/members/{userId}.totalPoints` via Firestore transaction
- Scoring trigger: a `WorkManager` periodic task runs every 30 min on game days (Sunday, Monday, Thursday), or manually triggered from the group screen
- Scoring logic lives in `domain/usecase/ScoreWeekPicksUseCase`

### PR-7 — Leaderboard
**Branch:** `feature/07-leaderboard`

- `LeaderboardScreen` — real-time ranked list of members with `totalPoints`; tapping a member shows their weekly breakdown
- `LeaderboardViewModel` with a Firestore `snapshotListener` on `standings/{groupId}/members`
- Animate rank changes with `animateItemPlacement()` in `LazyColumn`

### PR-8 — Pick History
**Branch:** `feature/08-pick-history`

- `HistoryScreen` — week-by-week accordion; each row shows the game, picked team, actual winner, and ✅/❌
- `HistoryViewModel` loads all past `weeks/{weekId}/picks/{userId}` docs for the current user in the selected group
- Accessible from the leaderboard (tap own name) or a profile menu

### PR-9 — Settings, DataStore & Team Logos
**Branch:** `feature/09-teams_logos`

- `SettingsScreen` — shows signed-in user (avatar, name, email), favorite team row, sign-out button
- `UserPreferences` domain model + `UserPreferencesRepository` interface; `UserPreferencesRepositoryImpl` backed by Jetpack DataStore (Proto or Preferences)
- `DataStoreModule` Hilt module; `SettingsViewModel` exposes `preferences: StateFlow<UserPreferences>`
- `TeamLogo` composable uses Coil + ESPN logo CDN (`EspnLogoUrl.kt`)
- `NflTeams.kt` — `NflTeam(abbr, name)` data class + complete 32-team list

### PR-10 — Team Theming
**Branch:** `feature/10-team-theming`

- `NflTeamColors.kt` — `nflTeamColorMap` mapping all 32 team abbreviations to `NflTeamColors(accent, header)`
- `AppColors.kt` + `LocalAppColors` — `CompositionLocal` theming layer on top of the fixed Blue Steel `MaterialTheme`
- `NavGraph` derives `AppColors` from the saved favorite team and wraps the entire nav host in `CompositionLocalProvider`
- `TeamSelectionScreen` — 4-column grid of all 32 team logos; tapping selects/deselects the favorite
- All screens updated to read colors from `LocalAppColors.current` instead of hardcoded Blue Steel constants
- `PreviewData.kt` + `PreviewWrapper` extracted to `presentation/preview/` for clean Compose preview setup

### PR-11 — Accessibility & Legibility Pass
**Branch:** `feature/11-accessibility-legibility`

First of a 3-PR redesign series addressing tester feedback that text/controls were too small and screens wasted space (PR-12 adds Material 3 Adaptive width-aware layouts, PR-13 adopts Material 3 Expressive shapes/motion; the app logo/brand identity stays untouched pending client approval).

- `Type.kt` — fully-specified Material3 `Typography` covering all 15 roles (previously only `bodyLarge` was overridden), sized larger and weighted bolder than stock M3 defaults
- `Typography.scaledBy(factor)` extension + `FontScaleOption` enum (`PEQUENO`/`NORMAL`/`GRANDE`) — an in-app font-size override layered on top of (not replacing) the Android system accessibility font scale
- `UserPreferences.fontScalePreference` + DataStore key, `SettingsViewModel.setFontScale()`, and a Pequeño/Normal/Grande selector in `SettingsScreen` next to the language selector
- `NFLocosPickTheme`/`NavGraph` derive the scaled `Typography` once per preference change via `remember(fontScale)`, mirroring the existing `AppColors`/`LocalAppColors` pattern
- Fixed `ScheduleScreen`'s `LazyColumn` missing `fillMaxSize()` — root cause of the empty-space-at-bottom reports on short game weeks
- App-wide 48dp touch-target audit (documented in `Type.kt`) confirming interactive rows/buttons clear the minimum with the new type scale, while intentionally leaving decorative small team logos (History screen) untouched

### PR-12 — Adaptive Layouts
**Branch:** `feature/12-adaptive-layouts`

Second of the 3-PR redesign series — adds Material 3 Adaptive support so the app uses available width intelligently on tablets/large-screen devices instead of stretching phone-sized layouts unboundedly (PR-13 closes the series with Material 3 Expressive visual polish; the logo/brand identity is untouched pending client approval).

- `material3-adaptive` (BOM-managed) added to `libs.versions.toml` / `app/build.gradle.kts`; window width read via `currentWindowAdaptiveInfo().windowSizeClass` (no `Activity`/`CompositionLocal` plumbing needed — also respects `@Preview(widthDp = ...)`, unlike the legacy `material3-window-size-class` artifact)
- `TeamSelectionScreen` — column count now derives from `WindowSizeClass.isWidthAtLeastBreakpoint(...)` (COMPACT=4 / MEDIUM=6 / EXPANDED=8); cell size stays fixed at 72dp on every breakpoint (bold, legible logos take priority over cramming more small cells); the grid itself is capped at 900dp and centered on wide screens
- `Modifier.responsiveCardWidth()` — new reusable modifier in `presentation/common/AdaptiveLayout.kt`; caps list-card content at 600dp, centered (via `horizontalAlignment` on the parent `LazyColumn`), on MEDIUM/EXPANDED screens; a no-op on COMPACT. Applied to `ScheduleScreen`, `PickScreen`, `LeaderboardScreen`, `HistoryScreen`, `GroupsScreen`'s card lists

### PR-13 — Bolder Shapes & Motion Polish
**Branch:** `feature/13-expressive-polish`

Third and final PR of the redesign series. Originally scoped as full Material 3 Expressive adoption (`MaterialExpressiveTheme`/`MotionScheme`/`MaterialShapes`), but a real compile against the resolved `androidx.compose.material3:material3-android:1.4.0` artifact showed those APIs are Kotlin-`internal`/unresolved in this version — not usable from app code despite appearing public when decompiled with `javap` (which can't see Kotlin's `internal` visibility). PR-13 ships the same visual goal with stable APIs instead: bolder shapes via a themed `Shapes` object and `spring()`-based motion. The logo/brand identity remains untouched, still pending client approval.

- `presentation/theme/Shapes.kt` (new) — themed `Shapes` (`extraSmall`=4dp, `small`=8dp, `medium`=16dp [bolder than the prior de-facto 12dp card radius], `large`=20dp, `extraLarge`=24dp), wired into `NFLocosPickTheme`'s existing `MaterialTheme(...)` call via its `shapes` param
- ~26 hardcoded `RoundedCornerShape(N.dp)` call sites across 14 screens migrated to `MaterialTheme.shapes.*` theme references
- Game-status badges (`ScheduleScreen`'s `StatusChip`, `PickScreen`'s `GameStatusChip`) now use `CircleShape` for a pill/scoreboard look; administrative badges (Board announcement tag, UserManagement role tag) intentionally stay rectangular
- Decorative `CircleShape` backdrop behind the Login screen logo (logo image itself untouched); `HistoryScreen`'s bare-emoji pick-result indicator replaced with a themed `CircleShape` badge + `Icon` (green/red tint, pending state unchanged)
- `spring()`-tuned `AnimatedVisibility` on `HistoryScreen`/`LeaderboardScreen`'s expand/collapse toggles, plus a new `animateFloatAsState` selection-scale animation on `PickScreen`'s `TeamPickButton` (previously zero motion on the app's core tap interaction)
- Closes the 3-PR redesign series (PR-11 typography/legibility → PR-12 adaptive layouts → PR-13 shapes/motion polish)

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

### PR-24 — Group Membership Subcollection Migration
**Branch:** `feature/24-membership-subcollection`

**Status: frozen indefinitely (decided 2026-09-04).** Expected app scale is a few dozen users — well under the ~25-30k UID per-document ceiling and ~1 write/sec contention concern below that motivated this PR. Not worth the migration risk/effort at this scale; revisit only if usage grows enough to approach those limits. PR-25 (join-via-link) and any other work proceed against today's `memberIds[]` array model with no dependency on this PR landing first.

Originates from hallazgo #6 of the Global Group security review (Codex + Antigravity + Claude, sept 2026 — see `docs/plans/global-default-group.md`): storing every app user's UID in a single `memberIds[]` array field on one document (`groups/global_nflocos_de_corazon`) collides with Firestore's ~1 MiB per-document cap (~25-30k UIDs) and its recommended ~1 write/sec-per-document contention limit as the user base grows. Not urgent at today's scale (a private-group app), but `memberIds` is load-bearing across the **entire** app, not just the global group — this PR was scoped out of the immediate security fix precisely because of that blast radius, documented here instead of implemented ad hoc. Full inventory of what this touches (confirmed by grep across the codebase, sept 2026): 15 separate `get(...).data.memberIds` checks in `firestore.rules`, the `whereArrayContains("memberIds", ...)` query that powers "which groups am I in" (`FirebaseGroupDataSource`), 3 Cloud Functions (`scoring.ts`, `inactivity.ts`, `accountDeletion.ts`) that iterate or query `memberIds` directly, 2 one-time backfill scripts, and 6 Kotlin test suites that construct `Group` objects with `memberIds` directly.

- New shape: `groups/{groupId}/members/{uid}` subcollection, each doc `{ uid, joinedAt }` — replaces the `memberIds: List<String>` array field on the group doc itself.
- "Which groups am I in" query becomes a `collectionGroup("members").whereEqualTo("uid", ...)` query (Firestore collection-group queries support real-time listeners, so `GroupViewModel.observeGroups` keeps working, but resolving parent `Group` docs from the returned member docs — `doc.ref.parent.parent`/a batch-get — is a real behavior change from today's single `whereArrayContains` snapshot listener, not a drop-in swap).
- New denormalized `memberCount: Int` field directly on the group doc (updated transactionally alongside creating/deleting a `members/{uid}` doc) so `GroupsScreen`'s member-count display doesn't need to fetch the full member subcollection just to show a number.
- `firestore.rules` — all 15 `get(...).data.memberIds` membership checks become `exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid))` (cheaper than the current `get()` of the whole group doc); new `allow read/create/delete` rules on `groups/{groupId}/members/{memberId}` itself (self-join/self-leave, creator can add/remove others).
- `scoring.ts`/`accountDeletion.ts`/`inactivity.ts` — rewritten to query the `members` subcollection (or a `collectionGroup` query keyed on `uid`) instead of reading/filtering the `memberIds` array.
- One-time Admin SDK migration script backfills a `members/{uid}` doc for every entry in every existing group's `memberIds` array — this is the highest-risk step (irreversible in practice once the old array-based code path is retired) and should run against a backup/staging copy first.
- Given the project currently has **zero repository-layer or Cloud Functions unit tests** (a pre-existing gap, not introduced by this PR — see `docs/plans/global-default-group.md`), this PR should budget time to add at least minimal test coverage for the new membership read/write paths before/alongside the migration, rather than migrating the app's core membership model with no automated safety net at all.

### PR-25 — Join Groups via Shareable Link
**Branch:** `feature/join-link`

Adds joining a group via a real, tappable Android App Link (`https://nflocospicks.web.app/join?code=XXXXXX`), instead of only a manually-typed 6-char invite code. Chose a verified HTTPS App Link over a bare custom URI scheme specifically so the link degrades gracefully for recipients who don't have the app installed yet (falls back to a web page instead of doing nothing). Full plan, three rounds of cross-review (Codex + two Antigravity passes) with every finding verified/incorporated/rejected-with-reason, and a per-step build/test log live at `docs/plans/join-via-link.md` — this is also the first PR built under Rules 8–10 (in-repo plan location, step-divided plan, mandatory cross-review), and the first to discover mid-implementation that the app was already live on Play Store (`https://play.google.com/store/apps/details?id=com.softeen.nflocospicks`), which changed several "pre-launch" assumptions along the way.

- **Firebase Hosting** (first-ever deploy for this project): `public/.well-known/assetlinks.json` (Digital Asset Links, all 3 real `sha256_cert_fingerprints` — debug, release/upload-key, and Play App Signing's own cert, since the app is store-installed and Play re-signs it) and `public/join/index.html` (fallback page linking to the real Play Store listing), both served live; new `AndroidManifest.xml` `autoVerify="true"` intent-filter for the `nflocospicks.web.app`/`/join` host, kept deliberately separate from the existing email-link intent-filter's `nflocospicks.firebaseapp.com` host.
- `presentation/navigation/JoinLinkParser.kt` (new) — pure-Kotlin (`java.net.URI`, no `android.net.Uri`) exact scheme/host/path matching plus invite-code shape validation; a real trust boundary since `MainActivity`'s `VIEW` intent-filter is exported. `MainActivity.kt` gains `pendingInviteCode` state, a `handleJoinLinkIntent` sibling to the existing `handleEmailLinkIntent`, and `onSaveInstanceState`/`savedInstanceState == null` guarding — the latter fixes a real bug found in review where `launchMode="singleTask"`'s retained `Intent` would silently re-trigger the join flow on every screen rotation, even after the invite was already consumed.
- `NavGraph.kt`'s auth-gating `LaunchedEffect` extracted into a pure, unit-tested `decidePostAuthNavigation` function (`PostAuthNavActionTest.kt`, 10 cases) — also fixes a real bug found in review where the join-link branch was incorrectly gated on the user being *on* `Screen.Groups` specifically, rather than `Groups` merely being in the back stack (always true once authenticated with a complete profile); it now fires from anywhere in the app, not just the Groups screen.
- `GroupViewModel.joinGroup`/`AppEvent.GroupJoined` gain a `source` param (`"manual_code"` default vs `"invite_link"`, attributed at submit time so an edited prefilled code correctly falls back to `"manual_code"`) — follows the existing `GroupOpened`/`onGroupClicked(source=...)` convention. `presentation/common/GroupInviteLink.kt` (new) builds the real shareable link for `GroupHeaderBar`'s share button; the entire copy/share affordance is hidden for the global default group ("NFLocos de Corazón"), since every user already auto-joins it and the invite mechanism is inert there.
- `JoinGroupScreen` accepts an optional `prefilledCode`, shows a "código detectado" notice, and still requires an explicit "Unirme" tap — no silent auto-join.
- `FirebaseGroupDataSource.createGroup()` gains check-and-retry invite-code uniqueness (`generateUniqueInviteCode`, 5 attempts) — a pre-existing gap unrelated to the link mechanism itself, bundled into this PR by explicit user decision since a shareable link raises the stakes of a collision; deliberately not made fully race-proof (a reservation-document transaction) since that's disproportionate at this app's scale.
- Also decided during this PR: PR-24 (membership subcollection migration) is frozen indefinitely (see its entry above); invite links ship indefinite/non-revocable, same as the manual code today.
- 157 total unit tests passing (`./gradlew test`) across the whole suite as of this PR; `./gradlew assembleDebug` green throughout.

---

## Rules

These rules apply to every change made in this repository. There are no exceptions unless a rule explicitly says so.

1. **Strict PR Boundaries.** Never implement changes belonging to a future PR or a different scope than currently requested, even if you are already touching the same files. Stop and wait for explicit approval before proceeding to the next PR in the roadmap. This ensures proper version control hygiene and avoids potential conflicts with other developers' assignments.

2. **Never downgrade a dependency.** If a situation arises where a downgrade seems necessary, stop, explain the problem clearly, and ask for explicit permission before making the change. Prefer fixing the root cause (API incompatibility, missing migration step) over a version rollback.

3. **Sync and build before every commit.** After each code change:
   - If any Gradle file was modified (`libs.versions.toml`, any `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`), run `./gradlew dependencies` first to sync and resolve dependencies before building.
   - Always run `./gradlew assembleDebug` (and `./gradlew test` if logic changed) before staging anything.
   - Fix all errors and warnings introduced by the change before committing. Never commit a broken build.

4. **Mandatory Testing.** Every new feature or logic change MUST be accompanied by comprehensive unit tests. If existing tests are affected, they must be updated and verified. Never consider a task complete without confirming that all tests pass (`./gradlew test`).

5. **Spanish output must be neutral Mexican Spanish (tuteo) — never voseo/Rioplatense.** This applies to chat replies, in-app strings, comments, and any generated document — including casual one-liners, which is exactly where this has slipped before (e.g. "decime" instead of "dime"). Never: vos, tenés/podés/sos/decís, decime/contame/fijate/mirá/andá. Always: tú (usually omitted), tienes/puedes/eres/dices, dime/cuéntame/fíjate/mira/anda.

6. **Deploy `firestore.rules`/`storage.rules` immediately after any change to them.** Editing these files locally has no effect on the live app — Firestore/Storage keep enforcing whatever was last deployed, so a rules change that isn't deployed silently leaves the old (often more restrictive) behavior in place, breaking the exact feature the change was meant to enable. After every edit to either file, run `firebase deploy --only firestore:rules,storage` (both together, even if only one changed) before considering the change complete.

7. **Never launch an emulator/device, install or run the app, or otherwise perform manual runtime verification (adb, screenshots, UI walkthroughs) on your own — ask for explicit authorization first, every time.** This has been requested before; doing it unprompted burns a large amount of tokens and time. `./gradlew assembleDebug` and `./gradlew test` (Rule 3) are always expected and don't need to be asked about — this rule is specifically about running the real app (emulator/device) to eyeball a change. If manual verification would materially de-risk a change, offer it and wait for a yes before running anything.

8. **Plans live in the repo, at `docs/plans/<descriptive-kebab-name>.md` — never in a local Claude directory.** Plan-mode tooling defaults to writing plan files outside the project (e.g. under a user-home `.claude/plans/` directory). Before running Rule 10's cross-review step, move or rewrite the plan into `docs/plans/` inside this repository, and reference that in-repo path in every review command from then on. Reason: Codex and Antigravity need direct filesystem access to read the plan, and a path outside the repo isn't reliably reachable by them the way an in-repo file is. Name the file descriptively (matching existing examples like `docs/plans/global-default-group.md`, `docs/plans/analytics-enrichment.md`), not after the PR number alone.

9. **Plans must be divided into logical, completable, testable, self-sufficient steps — never written as one monolithic block of changes.** Each step in a plan must: (a) be independently buildable and testable — its own `./gradlew assembleDebug`/`./gradlew test` pass, not dependent on a later step to compile or pass; (b) be self-sufficient to resume cold — carry enough context (current state, target state, exact files) that a different agent, or the same agent in a fresh session, can pick up at exactly that step without re-reading or re-deriving the rest of the plan; (c) map to a natural commit checkpoint. Reason: if an agent's session hits a usage limit mid-implementation, only the in-progress step needs re-evaluation — not the entire PR re-planned or re-implemented from scratch.

10. **Cross-review both the plan and the implementation with independent CLI reviewers before calling a feature/bugfix/upgrade done.** For anything beyond a trivial one-line fix, follow this flow end to end:
   1. Define the scope of the feature/bugfix/upgrade.
   2. Draft an implementation plan **in `docs/plans/<descriptive-kebab-name>.md` (Rule 8), divided into logical, self-sufficient steps (Rule 9)** — not a scratch file outside the repo, not one monolithic block.
   3. **Cross-review the plan** with two independent reviewers before writing code — don't just self-review:
      - Codex: `codex exec -s read-only "Review the plan at <in-repo path>. Look for gaps, risks, missing edge cases, and omissions given this codebase. Do not write code, only report findings."`
      - Antigravity (AGY): `agy.exe --mode plan --dangerously-skip-permissions -p "<same review prompt, in-repo path>" --model gemini-3.1-pro-high --effort high` — binary at `C:\Users\brici\AppData\Local\agy\bin\agy.exe` on this machine; `--mode plan` keeps it read-only, and `--dangerously-skip-permissions` is required for headless (non-interactive) operation since it can't otherwise prompt for tool-call approval — Claude Code's own auto-mode classifier will refuse to run this flag or self-add a permission rule for it, so the user needs to add the allow-rule to `.claude/settings.local.json` once (ask them for it if it's not already there).
      Synthesize both sets of findings against your own judgment and the codebase — don't apply a suggestion just because a reviewer made it; verify it first, and explicitly note in the plan when a finding turns out to already be resolved or not applicable, not just when one gets applied. Update the plan if warranted, and get the user's go-ahead on any resulting scope change before implementing.
   4. Implement the (possibly updated) plan, one step at a time per Rule 9 — build and test each step before moving to the next, so the plan file's own step boundaries stay meaningful as commit checkpoints.
   5. **Cross-review the implementation** the same way, this time against the diff:
      - Codex: `codex exec review --uncommitted` (verified 2026-09-05: `--uncommitted` cannot be combined with a custom `[PROMPT]` argument in this CLI version — `codex exec review --uncommitted "..."` errors with "the argument '--uncommitted' cannot be used with '[PROMPT]'". Run it plain; Codex picks what to focus on itself)
      - AGY: `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`
      Apply fixes where warranted, again verifying each finding rather than applying it blindly.
   6. Add/update test coverage (Rule 4).
   7. Sync Gradle only if a Gradle file changed (Rule 3), then run `./gradlew test` — never instrumented/UI tests locally; those run in the GitHub Actions CI pipeline. Fix any failures and re-run.
   8. Once tests pass, the change is ready for a PR. Creating commits and pushing always remains the user's responsibility — never commit or push without being explicitly asked to, every time, regardless of how this workflow went.

---

## Key Constraints

- `minSdk 24` — no API below Android 7.0
- Dynamic color (Material You) is **disabled** — the app uses a fixed Blue Steel dark theme. Do not re-enable it.
- All Firestore writes must use transactions or batched writes when updating both a pick and a standing simultaneously (PR-6)
- User preferences (favorite team, font-size preference) are stored in **Jetpack DataStore** on-device, not in Firestore.
- `google-services.json` is never committed — add a real one from the Firebase Console to `app/` to enable Firebase at runtime. The `google-services` plugin is applied conditionally in `app/build.gradle.kts` so the project builds without it.

### Analytics

All Firebase Analytics logging goes through the single choke point `AppLogger.logEvent(event: AppEvent)` (`analytics/AppLogger.kt`), injected into ViewModels only — never into Composables or Repositories. Every event is a case of the sealed class `analytics/AppEvent.kt`; add new events there, not as ad-hoc `firebaseAnalytics.logEvent(...)` calls elsewhere. **Never pass `display_name`/`email`/any PII as an event param or user property** — Google's Firebase Analytics/GA4 terms prohibit it and doing so risks the Analytics property being suspended. User attribution goes through Firebase's native Analytics User-ID (`AppLogger.setUserId(uid)`, set once per session, not per event) instead. `group.name` and team nicknames are not PII and are safe to log directly. A new event param is not filterable in the GA4 console until it is separately registered as a Custom Dimension (Admin → Custom definitions); the free tier caps these at 50 event-scoped / 25 user-scoped, so register new params deliberately rather than by default.

#### Event Inventory (Current)

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

## Release Checklist

- **Play App Signing re-signs the app with its own certificate — that certificate's SHA-1/SHA-256 must be registered in Firebase.** The upload/local keystore (`keystore.properties`) is only used to sign the AAB you upload; Google Play then re-signs it for distribution with a separate management key. Google Sign-In validates the installed app's certificate against the fingerprints registered on the Firebase Android OAuth client — if only the upload key's SHA-1 is registered, Sign-In breaks on every production install even though it works fine locally.
  - Before (or right after) the **first** production publish, get the "App signing key certificate" SHA-1 and SHA-256 from Play Console → app → Protegido con Play → Firma de apps → Descargar certificados, and register both with `firebase apps:android:sha:create <appId> <hash> --project nflocospicks`. Verify with `firebase apps:android:sha:list <appId> --project nflocospicks`.
  - After adding a fingerprint, refresh the local `app/google-services.json` via `firebase apps:sdkconfig ANDROID <appId> --project nflocospicks -o app/google-services.json.new && mv -f app/google-services.json.new app/google-services.json` so local/CI builds stay in sync (the file is gitignored, so this only affects your machine).
  - This is a one-time step per app — Play App Signing's certificate doesn't change between releases, so once it's registered it stays fixed.

## AGP 9 / Dependency Compatibility Notes

- **Hilt requires ≥ 2.59** with AGP 9.x (versions ≤ 2.58 use the removed `BaseExtension` API). Hilt 2.59 also requires Gradle ≥ 9.1.
- **KSP on Kotlin 2.2.x + AGP 9** needs `android.disallowKotlinSourceSets=false` in `gradle.properties` because KSP adds sources via the old `kotlin.sourceSets` DSL. This flag can be removed when the project upgrades to Kotlin 2.3.x (where KSP ≥ 2.3.6 handles it natively).
- **Firebase `-ktx` artifacts were merged** into their base counterparts as of BOM 33+. Use `firebase-auth` and `firebase-firestore` (without the `-ktx` suffix).
