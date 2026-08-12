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

---

## Rules

These rules apply to every change made in this repository. There are no exceptions unless a rule explicitly says so.

1. **Never downgrade a dependency.** If a situation arises where a downgrade seems necessary, stop, explain the problem clearly, and ask for explicit permission before making the change. Prefer fixing the root cause (API incompatibility, missing migration step) over a version rollback.

2. **Sync and build before every commit.** After each code change:
   - If any Gradle file was modified (`libs.versions.toml`, any `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`), run `./gradlew dependencies` first to sync and resolve dependencies before building.
   - Always run `./gradlew assembleDebug` (and `./gradlew test` if logic changed) before staging anything.
   - Fix all errors and warnings introduced by the change before committing. Never commit a broken build.

3. **Spanish output must be neutral Mexican Spanish (tuteo) — never voseo/Rioplatense.** This applies to chat replies, in-app strings, comments, and any generated document — including casual one-liners, which is exactly where this has slipped before (e.g. "decime" instead of "dime"). Never: vos, tenés/podés/sos/decís, decime/contame/fijate/mirá/andá. Always: tú (usually omitted), tienes/puedes/eres/dices, dime/cuéntame/fíjate/mira/anda.

4. **Deploy `firestore.rules`/`storage.rules` immediately after any change to them.** Editing these files locally has no effect on the live app — Firestore/Storage keep enforcing whatever was last deployed, so a rules change that isn't deployed silently leaves the old (often more restrictive) behavior in place, breaking the exact feature the change was meant to enable. After every edit to either file, run `firebase deploy --only firestore:rules,storage` (both together, even if only one changed) before considering the change complete.

5. **Never launch an emulator/device, install or run the app, or otherwise perform manual runtime verification (adb, screenshots, UI walkthroughs) on your own — ask for explicit authorization first, every time.** This has been requested before; doing it unprompted burns a large amount of tokens and time. `./gradlew assembleDebug` and `./gradlew test` (Rule 2) are always expected and don't need to be asked about — this rule is specifically about running the real app (emulator/device) to eyeball a change. If manual verification would materially de-risk a change, offer it and wait for a yes before running anything.

---

## Key Constraints

- `minSdk 24` — no API below Android 7.0
- Dynamic color (Material You) is **disabled** — the app uses a fixed Blue Steel dark theme. Do not re-enable it.
- All Firestore writes must use transactions or batched writes when updating both a pick and a standing simultaneously (PR-6)
- User preferences (favorite team, font-size preference) are stored in **Jetpack DataStore** on-device, not in Firestore.
- `google-services.json` is never committed — add a real one from the Firebase Console to `app/` to enable Firebase at runtime. The `google-services` plugin is applied conditionally in `app/build.gradle.kts` so the project builds without it.

## Release Checklist

- **Play App Signing re-signs the app with its own certificate — that certificate's SHA-1/SHA-256 must be registered in Firebase.** The upload/local keystore (`keystore.properties`) is only used to sign the AAB you upload; Google Play then re-signs it for distribution with a separate management key. Google Sign-In validates the installed app's certificate against the fingerprints registered on the Firebase Android OAuth client — if only the upload key's SHA-1 is registered, Sign-In breaks on every production install even though it works fine locally.
  - Before (or right after) the **first** production publish, get the "App signing key certificate" SHA-1 and SHA-256 from Play Console → app → Protegido con Play → Firma de apps → Descargar certificados, and register both with `firebase apps:android:sha:create <appId> <hash> --project nflocospicks`. Verify with `firebase apps:android:sha:list <appId> --project nflocospicks`.
  - After adding a fingerprint, refresh the local `app/google-services.json` via `firebase apps:sdkconfig ANDROID <appId> --project nflocospicks -o app/google-services.json.new && mv -f app/google-services.json.new app/google-services.json` so local/CI builds stay in sync (the file is gitignored, so this only affects your machine).
  - This is a one-time step per app — Play App Signing's certificate doesn't change between releases, so once it's registered it stays fixed.

## AGP 9 / Dependency Compatibility Notes

- **Hilt requires ≥ 2.59** with AGP 9.x (versions ≤ 2.58 use the removed `BaseExtension` API). Hilt 2.59 also requires Gradle ≥ 9.1.
- **KSP on Kotlin 2.2.x + AGP 9** needs `android.disallowKotlinSourceSets=false` in `gradle.properties` because KSP adds sources via the old `kotlin.sourceSets` DSL. This flag can be removed when the project upgrades to Kotlin 2.3.x (where KSP ≥ 2.3.6 handles it natively).
- **Firebase `-ktx` artifacts were merged** into their base counterparts as of BOM 33+. Use `firebase-auth` and `firebase-firestore` (without the `-ktx` suffix).
