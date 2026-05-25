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
./gradlew test --tests "com.example.nflocospick.ExampleUnitTest"

# Run a single test method
./gradlew test --tests "com.example.nflocospick.ExampleUnitTest.addition_isCorrect"

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
app/src/main/java/com/example/nflocospick/
│
├── domain/                         # Pure Kotlin — no Android/Firebase/Retrofit imports
│   ├── model/                      # Entity classes (User, Group, Game, Pick, Standing)
│   ├── repository/                 # Repository interfaces (GroupRepository, PickRepository…)
│   └── usecase/                    # One class per use case (ScoreWeekPicksUseCase, SubmitPickUseCase…)
│
├── data/                           # Implements domain interfaces
│   ├── remote/
│   │   ├── espn/                   # Retrofit service + ESPN DTOs + mappers → domain models
│   │   └── firebase/               # Firestore data sources + mappers → domain models
│   ├── repository/                 # Concrete repository implementations (injected via Hilt)
│   └── worker/                     # WorkManager Workers (e.g. ScoringWorker)
│
├── presentation/                   # Android / Compose layer
│   ├── theme/                      # Color, Type, Theme (existing, move here from ui/theme)
│   ├── navigation/                 # NavGraph, Screen sealed class, NavHost wiring
│   ├── auth/                       # LoginScreen + AuthViewModel + AuthUiState
│   ├── groups/                     # GroupScreen (create/join) + GroupViewModel + GroupUiState
│   ├── schedule/                   # ScheduleScreen + ScheduleViewModel + ScheduleUiState
│   ├── picks/                      # PickScreen + PickViewModel + PickUiState
│   ├── leaderboard/                # LeaderboardScreen + LeaderboardViewModel + LeaderboardUiState
│   └── history/                    # HistoryScreen + HistoryViewModel + HistoryUiState
│
├── di/                             # Hilt modules (NetworkModule, FirebaseModule, RepositoryModule)
├── NFLocosPickApp.kt               # @HiltAndroidApp
└── MainActivity.kt                 # Single Activity; hosts NavHost
```

### MVVM → MVI migration notes

- Each feature already exposes a `*UiState` data class and a `StateFlow` — keep this pattern so MVI's `State` fits in without restructuring.
- Side-effects (navigation, toasts) must go through a `Channel<UiEffect>` from day one; avoid calling nav callbacks directly from ViewModels.
- Use cases must remain pure and side-effect-free so they work identically under both patterns.

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

---

## Rules

These rules apply to every change made in this repository. There are no exceptions unless a rule explicitly says so.

1. **Never downgrade a dependency.** If a situation arises where a downgrade seems necessary, stop, explain the problem clearly, and ask for explicit permission before making the change. Prefer fixing the root cause (API incompatibility, missing migration step) over a version rollback.

2. **Build before every commit.** After each code change, run `./gradlew assembleDebug` (and `./gradlew test` if logic changed) before staging anything. Fix all errors and warnings introduced by the change before committing. Never commit a broken build.

---

## Key Constraints

- `minSdk 24` — no API below Android 7.0
- Dynamic color (Material You) is enabled by default on Android 12+; custom color scheme kicks in on older devices (see `Theme.kt`)
- All Firestore writes must use transactions or batched writes when updating both a pick and a standing simultaneously (PR-6)
- `google-services.json` is never committed — add a real one from the Firebase Console to `app/` to enable Firebase at runtime. The `google-services` plugin is applied conditionally in `app/build.gradle.kts` so the project builds without it.

## AGP 9 / Dependency Compatibility Notes

- **Hilt requires ≥ 2.59** with AGP 9.x (versions ≤ 2.58 use the removed `BaseExtension` API). Hilt 2.59 also requires Gradle ≥ 9.1.
- **KSP on Kotlin 2.2.x + AGP 9** needs `android.disallowKotlinSourceSets=false` in `gradle.properties` because KSP adds sources via the old `kotlin.sourceSets` DSL. This flag can be removed when the project upgrades to Kotlin 2.3.x (where KSP ≥ 2.3.6 handles it natively).
- **Firebase `-ktx` artifacts were merged** into their base counterparts as of BOM 33+. Use `firebase-auth` and `firebase-firestore` (without the `-ktx` suffix).
