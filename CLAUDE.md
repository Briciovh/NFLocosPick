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

All 26 roadmap PRs (PR-1 … PR-26) have shipped and merged into `main` — **PR-24 (Group Membership Subcollection Migration) is frozen indefinitely** by explicit decision (2026-09-04; revisit only if the user base approaches the ~25-30k-member per-document ceiling). The full historical roadmap, with per-PR detail on *why* the code looks the way it does, lives in [`docs/roadmap-shipped.md`](docs/roadmap-shipped.md).

New work is no longer appended to the roadmap: each feature/fix/upgrade is planned per-request in `docs/plans/<descriptive-kebab-name>.md` per Rules 8–10 below. Post-roadmap work already shipped this way (e.g. group settings/deletion, group-member block/remove) also lives under `docs/plans/`.

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

8. **Plans live in the repo, at `docs/plans/<descriptive-kebab-name>.md` — never in a local Claude directory.** Plan-mode tooling defaults to writing plan files outside the project (e.g. under a user-home `.claude/plans/` directory). Before running Rule 10's cross-review step, move or rewrite the plan into `docs/plans/` inside this repository, and reference that in-repo path in every review command from then on. Reason: Antigravity needs direct filesystem access to read the plan, and a path outside the repo isn't reliably reachable by it the way an in-repo file is. Name the file descriptively (matching existing examples like `docs/plans/global-default-group.md`, `docs/plans/analytics-enrichment.md`), not after the PR number alone.

9. **Plans must be divided into logical, completable, testable, self-sufficient steps — never written as one monolithic block of changes.** Each step in a plan must: (a) be independently buildable and testable — its own `./gradlew assembleDebug`/`./gradlew test` pass, not dependent on a later step to compile or pass; (b) be self-sufficient to resume cold — carry enough context (current state, target state, exact files) that a different agent, or the same agent in a fresh session, can pick up at exactly that step without re-reading or re-deriving the rest of the plan; (c) map to a natural commit checkpoint. Reason: if an agent's session hits a usage limit mid-implementation, only the in-progress step needs re-evaluation — not the entire PR re-planned or re-implemented from scratch.

10. **Cross-review every plan and every implementation with the *other* model — never the one that authored it — before calling a feature/bugfix/upgrade done.** The two agents are Claude and Antigravity (`agy.exe`). Reviewer ≠ author, per artifact: the model that wrote the plan does not review its own plan, and the model that wrote the diff does not review its own diff — the other model does. There is no second, same-model review pass layered on top, and applying a reviewer's findings does **not** trigger a fresh review round.

   - **Who authors is the user's call.** The user decides which model is put on planning and which on implementing (same model for both, or split). If it wasn't stated, the model the user handed the task to is the author and the other model reviews.
   - **One review pass per artifact.** Plan reviewed once, implementation reviewed once. The authoring model synthesizes the findings, applies what holds up, and proceeds — even when the resulting fixes are non-trivial. Re-running a full review on minor post-review edits is exactly what wasted a large amount of tokens before; don't.
   - **Trivial one-line fixes** skip the whole flow.

   Flow, end to end:
   1. Define the scope of the feature/bugfix/upgrade.
   2. The **planning model** drafts the plan **in `docs/plans/<descriptive-kebab-name>.md` (Rule 8), divided into logical, self-sufficient steps (Rule 9)** — not a scratch file outside the repo, not one monolithic block.
   3. **The other model reviews the plan** before any code is written — read-only, findings only:
      - **Antigravity reviewing** (Claude planned): `agy.exe --mode plan --dangerously-skip-permissions -p "Review the plan at <in-repo path>. Look for gaps, risks, missing edge cases, and omissions given this codebase. Do not write code, only report findings." --model gemini-3.1-pro-high --effort high` — binary at `C:\Users\brici\AppData\Local\agy\bin\agy.exe` on this machine; `--mode plan` keeps it read-only, and `--dangerously-skip-permissions` is required for headless (non-interactive) operation since it can't otherwise prompt for tool-call approval. Claude Code's own auto-mode classifier will refuse to run this flag or self-add a permission rule for it, so the user needs to add the allow-rule to `.claude/settings.local.json` once (already present in this repo; ask them for it on a fresh machine).
      - **Claude reviewing** (Antigravity planned): `claude -p "Review the plan at <in-repo path>. Look for gaps, risks, missing edge cases, and omissions given this codebase. Do not write code, only report findings." --model opus --permission-mode plan` (full model id `claude-opus-5`). `--permission-mode plan` keeps it read-only — no edits or writes. If a fully non-interactive run still stalls waiting on a read/git approval, add `--dangerously-skip-permissions` (safe here because plan mode blocks every write); that flag may need the same one-time `.claude/settings.local.json` allow-rule as the AGY command. Runnable by the user directly, or headlessly by Antigravity.
      The **authoring model** then synthesizes the reviewer's findings against its own judgment and the codebase — don't apply a suggestion just because the reviewer made it; verify it first, and explicitly note in the plan when a finding turns out to already be resolved or not applicable, not just when one gets applied. Update the plan if warranted, and get the user's go-ahead on any resulting scope change before implementing.
   4. The **implementing model** implements the (possibly updated) plan, one step at a time per Rule 9 — build and test each step before moving to the next, so the plan file's own step boundaries stay meaningful as commit checkpoints.
   5. **The other model reviews the implementation**, once, against the diff:
      - **Antigravity reviewing**: `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`
      - **Claude reviewing**: `claude -p "Review the current uncommitted git diff for correctness, security, and design issues" --model opus --permission-mode plan`
      The implementing model applies fixes where warranted, verifying each finding rather than applying it blindly. No further review round after these fixes.
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
| 34 | `group_renamed` | `group_id` | Groups |
| 35 | `group_deleted` | `group_id` | Groups |
| 36 | `group_member_removed` | `group_id`, `target_user_id`, `blocked` | Groups |
| 37 | `group_member_unblocked` | `group_id`, `target_user_id` | Groups |

## Release Checklist

- **Play App Signing re-signs the app with its own certificate — that certificate's SHA-1/SHA-256 must be registered in Firebase.** The upload/local keystore (`keystore.properties`) is only used to sign the AAB you upload; Google Play then re-signs it for distribution with a separate management key. Google Sign-In validates the installed app's certificate against the fingerprints registered on the Firebase Android OAuth client — if only the upload key's SHA-1 is registered, Sign-In breaks on every production install even though it works fine locally.
  - Before (or right after) the **first** production publish, get the "App signing key certificate" SHA-1 and SHA-256 from Play Console → app → Protegido con Play → Firma de apps → Descargar certificados, and register both with `firebase apps:android:sha:create <appId> <hash> --project nflocospicks`. Verify with `firebase apps:android:sha:list <appId> --project nflocospicks`.
  - After adding a fingerprint, refresh the local `app/google-services.json` via `firebase apps:sdkconfig ANDROID <appId> --project nflocospicks -o app/google-services.json.new && mv -f app/google-services.json.new app/google-services.json` so local/CI builds stay in sync (the file is gitignored, so this only affects your machine).
  - This is a one-time step per app — Play App Signing's certificate doesn't change between releases, so once it's registered it stays fixed.

## AGP 9 / Dependency Compatibility Notes

- **Hilt requires ≥ 2.59** with AGP 9.x (versions ≤ 2.58 use the removed `BaseExtension` API). Hilt 2.59 also requires Gradle ≥ 9.1.
- **KSP on Kotlin 2.2.x + AGP 9** needs `android.disallowKotlinSourceSets=false` in `gradle.properties` because KSP adds sources via the old `kotlin.sourceSets` DSL. This flag can be removed when the project upgrades to Kotlin 2.3.x (where KSP ≥ 2.3.6 handles it natively).
- **Firebase `-ktx` artifacts were merged** into their base counterparts as of BOM 33+. Use `firebase-auth` and `firebase-firestore` (without the `-ktx` suffix).
