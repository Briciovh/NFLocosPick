# Hide pre-kickoff "0" scores on Picks screen

## Context

Group members noticed that every game card on the Picks screen shows a "0" score under each team even before the game has started (see screenshot: "Detroit Lions (1-0) 0" / "Buffalo Bills (1-0) 0"). Before kickoff, both teams have trivially scored 0 points against each other, so displaying it is meaningless noise. The desired behavior: hide the score entirely while a game is `SCHEDULED`, and start showing it (and keep updating it) once the game is `IN_PROGRESS` or `FINAL`.

**Root cause**: ESPN's scoreboard API returns `score = "0"` (a non-null string) for games that haven't started, not `null`. `EspnMapper.toGame()` (`app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnMapper.kt:75-76`) parses this with `home.score?.toIntOrNull()` / `away.score?.toIntOrNull()`, which happily turns `"0"` into the `Int` `0` — never `null`. This violates the domain model's own documented contract on `Game.kt:11`: `val homeScore: Int?, // null mientras el juego no ha comenzado`. Downstream, `TeamPickButton` in `PickScreen.kt` already does the right thing — it only renders the score `Text` `if (score != null)` (`PickScreen.kt:502-510`) — so the bug is entirely in the mapper not honoring the contract, not in the UI's gating logic.

This also means **no UI change is needed**. `GameStatus` (`SCHEDULED` / `IN_PROGRESS` / `FINAL`) already drives `GameStatusChip`, the result checkmark badge, and `isLocked` elsewhere in `PickScreen.kt`/`PickViewModel.kt` — reusing it here (by fixing the mapper) is consistent with how the rest of the screen already treats game status, and keeps the fix to one file plus its test.

## Step 1 (single step — self-contained, one commit checkpoint)

**Files:**
- `app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnMapper.kt`
- `app/src/test/java/com/softeen/nflocospicks/data/remote/espn/EspnMapperTest.kt`

**Change in `EspnMapper.kt` (`toGame()` + `toGameStatus()`, lines ~66-88):**

Compute `status` first, then null out both scores when the game hasn't started. Also fix `toGameStatus()`'s fallback: today it maps *anything* that isn't `completed` or exactly `STATUS_IN_PROGRESS` to `SCHEDULED`, which — once scores are nulled for `SCHEDULED` — would make live scores vanish during halftime, delays, or other in-between ESPN statuses (`STATUS_HALFTIME`, `STATUS_END_PERIOD`, `STATUS_DELAYED`, `STATUS_SUSPENDED`, etc., all real ESPN values not currently in this `when`). Flip the fallback so only the exact `STATUS_SCHEDULED` name counts as not-yet-started, and every other non-final status is treated as in-progress:

```kotlin
private fun EspnStatusType.toGameStatus(): GameStatus = when {
    completed                  -> GameStatus.FINAL
    name == "STATUS_SCHEDULED" -> GameStatus.SCHEDULED
    else                       -> GameStatus.IN_PROGRESS
}
```

And in `toGame()`:

```kotlin
val status = competition.status.type.toGameStatus()

return Game(
    id             = id,
    weekId         = weekId,
    seasonType     = seasonType,
    homeTeam       = home.team.displayName,
    awayTeam       = away.team.displayName,
    homeTeamAbbr   = home.team.abbreviation,
    awayTeamAbbr   = away.team.abbreviation,
    kickoffTime    = kickoffMillis,
    homeScore      = if (status == GameStatus.SCHEDULED) null else home.score?.toIntOrNull(),
    awayScore      = if (status == GameStatus.SCHEDULED) null else away.score?.toIntOrNull(),
    status         = status,
    homeTeamRecord = home.records?.firstOrNull { it.name == "overall" }?.summary,
    awayTeamRecord = away.records?.firstOrNull { it.name == "overall" }?.summary,
    weekNumber     = weekNumber
)
```

(Requires importing/qualifying `GameStatus` if not already imported in this file — check current imports.)

No changes needed to `PickScreen.kt`, `PickViewModel.kt`, `Game.kt`, or `GameStatus.kt` — they already treat `homeScore`/`awayScore == null` as "don't show a score" (`TeamPickButton`, `PickScreen.kt:502`) and only read scores under a status/null guard elsewhere (`PickViewModel.kt:238-240`). `MockDataProvider.kt` and `PreviewData.kt` already model scheduled games with `homeScore = null, awayScore = null`, confirming this is the existing intended contract, not a new one.

**Backend (`functions/src/espn.ts`) — reviewed, not applicable:** this file has the identical mapper shape and the same "score defaults to 0, not null" behavior, but it is only consumed by `computeWinners()`, which filters to `status === "FINAL"` games before ever reading `homeScore`/`awayScore` (`espn.ts:124-126`). A `SCHEDULED` game's score value there has no observable effect today, so changing it is out of this fix's scope per CLAUDE.md Rule 1 (Strict PR Boundaries) — the Android display bug is fully fixed by the client-side `EspnMapper.kt` change alone, since `ScheduleRepositoryImpl` reads directly from `EspnApiService`/`EspnMapper`, not from this Cloud Function.

**Test coverage (`EspnMapperTest.kt`):**

1. Update the shared `singleEvent()` test helper to pass `score = "0"` (matching ESPN's real scheduled-game payload) instead of `score = null`, so the tests reflect reality rather than masking the bug. The existing preseason/postseason weekId tests don't assert on scores, so this is safe.
2. Add a test for a scheduled game (`STATUS_SCHEDULED`, `score = "0"` both sides) asserting `game.homeScore`/`game.awayScore` are `null` and `game.status == GameStatus.SCHEDULED`.
3. Add a test for `STATUS_IN_PROGRESS` with non-zero scores (e.g. `"7"`/`"3"`) asserting they pass through unchanged — locks in that a real 0-0 in-progress game must still show "0", not disappear.
4. Add a test for a non-standard live status (e.g. `STATUS_HALFTIME`, `completed = false`) with a non-zero score, asserting `game.status == GameStatus.IN_PROGRESS` and the score is preserved — covers the halftime/delay regression risk found in review.

**Verification:**
1. `./gradlew assembleDebug` — must build clean.
2. `./gradlew test --tests "com.softeen.nflocospicks.data.remote.espn.EspnMapperTest"` — new + existing tests pass.
3. `./gradlew test` — full unit suite green.

## Process notes (per CLAUDE.md Rules 8-10)

- This is a single-step plan (Rule 9) — one file changed, one test file updated, one build/test checkpoint, one commit.
- User has confirmed (2026-09-16) this goes through the **full cross-review flow**, not the trivial-fix exemption.
- Plan review (Antigravity) completed 2026-09-16 — see Cross-Review Log below. Findings synthesized and folded into Step 1 above.
- After implementing Step 1 and before considering the fix done, run the implementation review against the uncommitted diff (Antigravity), apply warranted fixes once, no further review round after that (Rule 10 step 5).

## Cross-Review Log

**Plan Review (Antigravity) - Findings & Risks:**

1. **Critical Bug (Halftime/Delay Scores Hidden):** The current `EspnStatusType.toGameStatus()` mapper defaults any status that isn't `completed == true` or `name == "STATUS_IN_PROGRESS"` to `GameStatus.SCHEDULED`. The ESPN API uses statuses like `STATUS_HALFTIME`, `STATUS_END_PERIOD`, `STATUS_DELAYED`, and `STATUS_SUSPENDED`. Under the current logic, all these live states map to `SCHEDULED`. If the plan is implemented as-is, the live scores of games at halftime or during a delay will map to `SCHEDULED`, causing the new logic to incorrectly null out and hide the score. 
   **Recommendation:** `toGameStatus()` must be updated to properly map halftime, end-of-period, suspended, and delayed states to `IN_PROGRESS` to protect their scores.

2. **Backend Divergence (`espn.ts`):** The plan ignores `functions/src/espn.ts`, which contains an identical `eventToGame` parsing function and is meant to stay in sync with the Android client's domain model.
   **Recommendation:** Apply the same nulling logic and status mapping fixes to `functions/src/espn.ts` and add tests for it in `functions/test/espn.test.ts`.

3. **Test Mock Inconsistency:** The existing `singleEvent` helper in `EspnMapperTest.kt` passes `score = null` for scheduled games, which contradicts the core issue (ESPN returning `"0"`).
   **Recommendation:** Update the `singleEvent` test helper in `EspnMapperTest.kt` to pass `"0"` for scores, ensuring all tests properly reflect the real ESPN payload, instead of just adding a new test next to the flawed helper.

**Synthesis (Claude, verified against codebase):**

1. **Applied.** Confirmed real: `EspnDtos.kt:52`'s own comment claims only `STATUS_SCHEDULED | STATUS_IN_PROGRESS | STATUS_FINAL` exist, but ESPN's actual API sends additional live-game statuses (halftime, delays, suspensions) that the old `toGameStatus()` fallback silently mapped to `SCHEDULED`. That was a latent, invisible bug before this change (only cost a missing LIVE chip); this change would have turned it into a visible one (score vanishing mid-game). Fixed by flipping the fallback in `toGameStatus()` to treat only the exact `STATUS_SCHEDULED` name as not-started, everything else non-final as `IN_PROGRESS`. Added a dedicated test for a non-standard live status.
2. **Not applied — verified not applicable to this fix's scope.** Traced `functions/src/espn.ts` usage: `computeWinners()` (`espn.ts:124`) filters to `status === "FINAL"` before ever reading `homeScore`/`awayScore`, so the backend's 0-vs-null distinction for scheduled games has no observable effect. Also confirmed the Android Picks screen reads games directly via `ScheduleRepositoryImpl` → `EspnApiService` → `EspnMapper.kt` (`ScheduleRepositoryImpl.kt:34-35,51`), never through this Cloud Function, so the display bug is fully fixed client-side alone. Touching the backend mapper would be an unrequested, unrelated-scope change per CLAUDE.md Rule 1.
3. **Applied.** Confirmed `singleEvent()` in `EspnMapperTest.kt` passes `score = null`, not ESPN's real `"0"`. Updated it to `"0"`; confirmed the two tests that reuse it (preseason/postseason weekId) don't assert on scores, so this is safe.
