# Architecture — NFLocosPick

Mirrors CLAUDE.md's "Project Overview" / "Build & Test Commands" / "Target Architecture" sections. Source of truth is CLAUDE.md; update both together.

## Project Overview

**NFLocosPick** is a private-group NFL pick'em Android app. Each week, members of a group pick teams from NFL matchups. Results are auto-scored after games end and tracked on a season-long leaderboard.

**Stack:**
- **Android** (Kotlin, Jetpack Compose, Material 3)
- **Firebase** — Auth (Google Sign-In), Firestore (data), Cloud Functions (auto-scoring)
- **ESPN unofficial API** — free, no key required, provides weekly schedule + live scores
- **Hilt** — dependency injection
- **Retrofit** — HTTP client for ESPN API
- **Navigation Compose** — screen routing

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
