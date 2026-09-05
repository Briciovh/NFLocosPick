# PR Roadmap — Shipped (PR-1 to PR-13)

Mirrors CLAUDE.md's "PR Roadmap" section, PR-1 through PR-13. These have already merged into `main` — kept here for historical context on *why* the code looks the way it does. Source of truth is CLAUDE.md; update both together. See `roadmap-active.md` for PR-14 onward.

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
