# PR-26 — Join Outcome Feedback & Global Card Cleanup

**Branch:** `feature/26-join-outcome-feedback`

> Per project Rule 8, this plan now lives in-repo (moved from the plan-mode scratch path). Per project Rule 9, divided into 2 independently buildable/testable steps. Both the plan and the implementation have passed independent Codex + Antigravity cross-review — see "Cross-Review Log" below. **Status: implemented, both steps green (`./gradlew assembleDebug test`), ready for commit/PR.**

## Context

PR-25 (shareable join links, merged) left two small, related gaps that the user now wants closed:

1. **No feedback on join outcome.** Today, whether a user joins a brand-new group or types/taps a code for a group they already belong to, `JoinGroupScreen` just silently navigates back — Firestore's `arrayUnion` is a no-op for an existing member, so nothing visibly happens either way. This was flagged as a known low-priority limitation during PR-25's plan review and explicitly deferred. The user now wants both outcomes to show distinguishable visual feedback.
2. **The global default group's invite code is still visible in one place.** PR-25 already hid the invite-code/copy-button affordance in `GroupHeaderBar.kt` (used inside a group's session screens) for the global group, since every user auto-joins it and the code is functionally inert there. It missed `GroupsScreen.kt`'s `GroupCard` (the list-view card on the Groups tab), which has its own, separate invite-code row (the "second row" of the card, between the group name and the member count) — this is the one remaining spot showing it.

## Confirmed Facts (verified directly against current code, do not re-derive)

- **The "already a member" check must happen inside the data source, before the write** — `FirebaseGroupDataSource.joinGroup` (`data/remote/firebase/FirebaseGroupDataSource.kt:64-80`) fetches `doc` (pre-join state) via the invite-code query, *then* does `FieldValue.arrayUnion(userId)`, then re-reads the doc into `updated` (post-join state) to return. By the time a `Group` reaches `GroupViewModel`/`JoinGroupUseCase`, membership already includes the joiner — there's no way to tell the two cases apart from the returned `Group` alone. The check has to compare `userId` against `doc`'s pre-write `memberIds` (`doc.get("memberIds") as? List<*>`), inside the data source.
- **No existing type carries this distinction anywhere** — `GroupRepository.joinGroup`/`JoinGroupUseCase`/`FirebaseGroupDataSource.joinGroup` all return a plain `Group` (`domain/repository/GroupRepository.kt:19`, `domain/usecase/JoinGroupUseCase.kt:10-11`). `GroupActionUiState.Success(val group: Group)` (`presentation/groups/GroupUiState.kt:18`) is shared verbatim between `createGroup` and `joinGroup` flows.
- **The established pattern for "show feedback after an action, landing back on GroupsScreen" already exists and should be reused**: `GroupUiEffect` (`presentation/groups/GroupUiState.kt:24-29`) is a 4-case sealed class (`NavigateToGroupSession`, `NavigateToLogin`, `ScoringResult`, `ScoringError`) sent through a `Channel` on `GroupViewModel` and collected in `GroupsScreen.kt`'s `LaunchedEffect(Unit)` (lines ~92-107), which shows a `SnackbarHostState` snackbar. `JoinGroupScreen.kt`'s `LaunchedEffect(actionState)` already calls `onNavigateBack()` unconditionally on `Success` — that stays unchanged; the effect fires from the shared `GroupViewModel` and is picked up once the user is back on `GroupsScreen` (the channel buffers until a consumer is collecting, which is exactly how this already has to work for scoring feedback triggered while the user isn't necessarily looking at `GroupsScreen`).
- **`GroupViewModel.joinGroup(inviteCode: String, source: String = "manual_code")`** (`presentation/groups/GroupViewModel.kt:129-143`, confirmed current signature unchanged since PR-25): resolves `userId`, sets `Loading`, calls `joinGroupUseCase(inviteCode, userId)`, on success sets `Success(group)` + logs `AppEvent.GroupJoined`; catches `NoSuchElementException` → `Error("Código de invitación inválido")`.
- **`GroupRepositoryImpl.joinGroup`** (`data/repository/GroupRepositoryImpl.kt:17-18`) is a one-line pass-through to the data source — needs updating in lockstep with the interface.
- **Test scaffolding to extend, not invent**: `FirebaseGroupDataSourceTest.kt` (added in PR-25) already mocks `FirebaseFirestore`/`CollectionReference`/`Query`/`QuerySnapshot`/`DocumentReference` with MockK and `Tasks.forResult(...)`/`returnsMany` for sequential Firestore calls — the same shape applies here. `JoinGroupUseCaseTest.kt` uses a hand-written fake `CapturingJoinRepository : GroupRepository` (not MockK). `GroupViewModelTest.kt` uses MockK (`joinGroupUseCase = mockk<JoinGroupUseCase>()`, `logger = mockk<AppLogger>(relaxed = true)`), `coEvery { joinGroupUseCase(any(), any()) } returns ...`, and asserts via `vm.actionState.value as GroupActionUiState.Success` / `verify { logger.logEvent(match { ... }) }`. **`FirebaseGroupDataSource.joinGroup` itself currently has zero test coverage** — this plan adds the first tests for it, not just extends existing ones.
- **No third display location exists**: grepped `inviteCode` across all of `presentation/` — only `GroupsScreen.kt:354` (`GroupCard`, this plan's Step 2 target) and `GroupHeaderBar.kt` (already fixed in PR-25) ever render it. `GlobalGroupConstants.GROUP_ID` (`domain/model/GlobalGroupConstants.kt:9`) is already imported in `GroupsScreen.kt` (used for the avatar's `localIconRes` at line 322).
- **No Spanish-copy precedent exists yet** for "joined"/"already a member" messages — this is new copy. Existing tone reference (`values/strings.xml:62-71`): `group_code_copied` → `"Código copiado"`, `scoring_result` → `"%1$d pick(s) puntuados 🏆"`, `scoring_none` → `"No hay picks nuevos que puntuar"` — short, neutral tuteo, sentence-case, no terminal period, occasional emoji for positive outcomes.
- **`Group` domain model** (`domain/model/Group.kt:3-11`): `id, name, inviteCode, createdBy, memberIds: List<String>, photoUrl?, iconId?` — no timestamp field, nothing else relevant needed here.

## Decision (confirmed with user)

Feedback surfaces as a **snackbar on `GroupsScreen`** after the auto-navigate-back from `JoinGroupScreen` completes — reusing the exact existing `GroupUiEffect`/`SnackbarHostState` mechanism already used for scoring feedback. `JoinGroupScreen`'s current auto-navigate-on-`Success` behavior is unchanged.

## Step 1 — Join Outcome Feedback (domain → data → presentation, end to end)

**Files:**
- `app/src/main/java/com/softeen/nflocospicks/domain/model/JoinGroupResult.kt` (new)
- `app/src/main/java/com/softeen/nflocospicks/domain/repository/GroupRepository.kt`
- `app/src/main/java/com/softeen/nflocospicks/data/repository/GroupRepositoryImpl.kt`
- `app/src/main/java/com/softeen/nflocospicks/data/remote/firebase/FirebaseGroupDataSource.kt`
- `app/src/main/java/com/softeen/nflocospicks/domain/usecase/JoinGroupUseCase.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/groups/GroupUiState.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/groups/GroupViewModel.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/groups/GroupsScreen.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- Tests: `FirebaseGroupDataSourceTest.kt`, `JoinGroupUseCaseTest.kt`, `GroupViewModelTest.kt`, plus three more surfaced by cross-review (see "Test updates" below) — `CreateGroupUseCaseTest.kt`, `UploadGroupPhotoUseCaseTest.kt`, `GroupViewModelIntegrationTest.kt`

**Depends on:** none (independent of Step 2).

**Corrected after cross-review (Codex + Antigravity, both independently found this) — the `GroupRepository.joinGroup` signature change breaks three more test files than the first draft listed**, because each has its own hand-written or MockK fake implementing the whole `GroupRepository` interface, not just the files that test join behavior directly:
- `CreateGroupUseCaseTest.kt:22` — `private class CapturingGroupRepository : GroupRepository` has `override suspend fun joinGroup(...): Group = throw NotImplementedError()`. Change the return type to `JoinGroupResult` (the throw body is unaffected — this test never calls `joinGroup`, it just needs to compile).
- `UploadGroupPhotoUseCaseTest.kt:21` — `private class CapturingPhotoRepository : GroupRepository` has the identical `override suspend fun joinGroup(...): Group = throw NotImplementedError()`. Same fix.
- `GroupViewModelIntegrationTest.kt:102-113` — this one **does** exercise `joinGroup` for real (test: `` `joinGroup passes trimmed and uppercased code to repository through the real use case` ``): `coEvery { groupRepo.joinGroup(any(), any()) } returns stubGroup` → change to `returns JoinGroupResult(stubGroup, alreadyMember = false)`; `assertEquals(GroupActionUiState.Success(stubGroup), vm.actionState.value)` → `assertEquals(GroupActionUiState.Success(stubGroup, alreadyMember = false), vm.actionState.value)`. This is a real behavioral assertion, not just a compile fix — keep it passing, don't just silence it.

### 1a. New domain model

```kotlin
package com.softeen.nflocospicks.domain.model

/** Outcome of [GroupRepository.joinGroup]: the resulting [Group] plus whether the
 *  user was already a member before this call (vs. newly added). */
data class JoinGroupResult(val group: Group, val alreadyMember: Boolean)
```

### 1b. `GroupRepository` / `GroupRepositoryImpl` / `JoinGroupUseCase`

```kotlin
// GroupRepository.kt
suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult
```
```kotlin
// GroupRepositoryImpl.kt
override suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult =
    dataSource.joinGroup(inviteCode, userId)
```
```kotlin
// JoinGroupUseCase.kt
suspend operator fun invoke(inviteCode: String, userId: String): JoinGroupResult =
    groupRepository.joinGroup(inviteCode.trim().uppercase(), userId)
```

### 1c. `FirebaseGroupDataSource.joinGroup` — detect membership before writing

```kotlin
suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult {
    val snapshot = firestore.collection(COLLECTION)
        .whereEqualTo("inviteCode", inviteCode)
        .get()
        .await()

    val doc = snapshot.documents.firstOrNull()
        ?: throw NoSuchElementException("No group found for invite code: $inviteCode")

    val alreadyMember = (doc.get("memberIds") as? List<*>)?.contains(userId) == true
    if (alreadyMember) {
        return JoinGroupResult(doc.toGroup(), alreadyMember = true)
    }

    firestore.collection(COLLECTION).document(doc.id)
        .update("memberIds", FieldValue.arrayUnion(userId))
        .await()

    // Re-leemos el documento tras el update para retornar el estado fresco.
    val updated = firestore.collection(COLLECTION).document(doc.id).get().await()
    return JoinGroupResult(updated.toGroup(), alreadyMember = false)
}
```
Skipping the write entirely when already a member (rather than still calling `arrayUnion` — which would be a harmless no-op — and re-reading) saves a Firestore write and a redundant read, and lets the existing `doc` snapshot serve directly as the returned `Group` with no second round-trip.

**Two accepted limitations, both raised by Codex's review and deliberately not engineered around** (matching this project's existing precedent — PR-25's invite-code-uniqueness check-and-retry made the identical trade-off explicitly, for the identical reason: disproportionate effort at this app's actual scale):
1. **Non-atomic read-then-write.** The query → check → conditional-write flow isn't transactional. Two concurrent join attempts for the same user (e.g. a rapid double-tap, or retrying after a dropped network response) could both read "not yet a member" and both report `alreadyMember = false` — cosmetically wrong (a duplicate "you joined!" snackbar) but not data-unsafe, since `arrayUnion` itself is idempotent regardless of how many times it's called. A fully atomic fix needs a Firestore transaction wrapping the read+write, which is disproportionate for what's purely user-facing copy at this app's scale (a few dozen users, group joins are not a hot path). Accepted as-is.
2. **Weaker freshness guarantee for the already-member early return.** Today's code always does update-then-reread; the early return skips both for an existing member, so a group deleted between the initial query and this check would be reported as a successful "already a member" for a group that no longer exists (vs. today's code, which would fail differently against a deleted doc). Given group deletion mid-join is astronomically rare, this is accepted as a documented trade-off, not fixed.

### 1d. `GroupUiState.kt` — richer `Success`, new effect

```kotlin
data class Success(val group: Group, val alreadyMember: Boolean = false) : GroupActionUiState()
```
(Default `false` keeps `createGroup`'s existing `Success(group)`-style call sites — check whichever ones exist — compiling unchanged; only `joinGroup` sets it explicitly.)

```kotlin
data class GroupJoined(val groupName: String, val alreadyMember: Boolean) : GroupUiEffect()
```

### 1e. `GroupViewModel.joinGroup`

```kotlin
fun joinGroup(inviteCode: String, source: String = "manual_code") {
    val userId = userRepository.getCurrentUser()?.uid ?: return
    viewModelScope.launch {
        _actionState.value = GroupActionUiState.Loading
        try {
            val result = joinGroupUseCase(inviteCode, userId)
            _actionState.value = GroupActionUiState.Success(result.group, result.alreadyMember)
            if (!result.alreadyMember) {
                logger.logEvent(AppEvent.GroupJoined(result.group.id, result.group.name, source))
            }
            effects.send(GroupUiEffect.GroupJoined(result.group.name, result.alreadyMember))
        } catch (e: NoSuchElementException) {
            _actionState.value = GroupActionUiState.Error("Código de invitación inválido")
        } catch (e: Exception) {
            _actionState.value = GroupActionUiState.Error(e.message ?: "Error al unirse al grupo")
        }
    }
}
```
**Resolved after Codex review — analytics semantics were left unresolved in the first draft.** Codex correctly flagged that logging `group_joined` on every already-a-member no-op would inflate conversion-style metrics built on that event and make the event name misleading ("joined" implies a new membership, not a re-tap of an existing one). Resolved by **not logging `AppEvent.GroupJoined` at all when `alreadyMember == true`** — no new event param, no new Custom Dimension registration needed (avoids touching the Analytics Custom Dimension cap discussed in `CLAUDE.md`'s Analytics section), and `group_joined` keeps meaning exactly what its name says. The UI-feedback `GroupUiEffect.GroupJoined` effect still fires either way — the snackbar always shows, only the analytics event is conditional.

### 1f. `GroupsScreen.kt` — consume the new effect

Add a case to the existing `when` in the effect-collecting `LaunchedEffect(Unit)`:
```kotlin
is GroupUiEffect.GroupJoined -> {
    val msg = if (effect.alreadyMember)
        String.format(alreadyMemberPattern, effect.groupName)
    else
        String.format(joinedNewPattern, effect.groupName)
    snackbarHostState.showSnackbar(msg)
}
```
with `val joinedNewPattern = stringResource(R.string.group_joined_new)` / `val alreadyMemberPattern = stringResource(R.string.group_already_member)` declared alongside the existing `scoringNoneMsg`/`scoringResultPattern` at the top of `GroupsScreen`.

### 1g. New strings (neutral Mexican tuteo)

| Key | es | en |
|---|---|---|
| `group_joined_new` | `"Te uniste a %1$s 🎉"` | `"You joined %1$s 🎉"` |
| `group_already_member` | `"Ya eras miembro de %1$s"` | `"You were already a member of %1$s"` |

### Test updates

- **`FirebaseGroupDataSourceTest.kt`** — **corrected after Antigravity review**: the first draft's case (3) claimed an "existing 'no group found' `NoSuchElementException` case still passes unchanged" — verified false. This file (added in PR-25) currently has **only** `createGroup` retry-logic tests; there is zero existing `joinGroup` coverage here (the `NoSuchElementException` case that does exist today lives in `GroupViewModelTest.kt`, mocking `JoinGroupUseCase`, not real Firestore). All three cases below are new: (1) user not in the fetched doc's `memberIds` → `arrayUnion` update is called, re-read happens, `JoinGroupResult.alreadyMember == false`; (2) user already in `memberIds` → `update`/re-read `get()` are **never** called (`verify(exactly = 0)`), `JoinGroupResult.alreadyMember == true` and the returned `Group` matches the original doc; (3) new "no group found" test *for this data source specifically* (not duplicating `GroupViewModelTest`'s existing one, which tests a different layer) — mock an empty query snapshot, assert `NoSuchElementException` is thrown.
- **`JoinGroupUseCaseTest.kt`**: update `CapturingJoinRepository.joinGroup` to return `JoinGroupResult(stub, alreadyMember = false)` (or parameterize it) so the file compiles; the two existing tests (trim/uppercase behavior) need no logical changes, just the fake's return type.
- **`GroupViewModelTest.kt`**: update `coEvery { joinGroupUseCase(...) } returns stubGroup` call sites to return `JoinGroupResult(stubGroup, alreadyMember = false)` (or `true` where relevant); add a new test asserting `GroupActionUiState.Success.alreadyMember == true` and that the ViewModel still doesn't throw/misbehave when `alreadyMember` is true; assert the new `GroupUiEffect.GroupJoined` effect is sent with the right `groupName`/`alreadyMember` in at least one new-join and one already-member case; **add a test asserting `logger.logEvent` is NOT called when `alreadyMember == true`** (`verify(exactly = 0) { logger.logEvent(match { it.name == "group_joined" }) }`) per the resolved analytics semantics above — this is the one behavioral assertion most likely to regress silently if skipped.
- **Three more test files need their `GroupRepository` fake/mock updated to compile** (surfaced by cross-review, not part of "new coverage" — see the note under Step 1's file list above): `CreateGroupUseCaseTest.kt`, `UploadGroupPhotoUseCaseTest.kt`, `GroupViewModelIntegrationTest.kt`.

### Done when

`./gradlew assembleDebug` and `./gradlew test` both pass, including the new/updated cases above.

---

## Step 2 — Hide Invite Code Row on `GroupCard` for the Global Group

**Files:** `app/src/main/java/com/softeen/nflocospicks/presentation/groups/GroupsScreen.kt`

**Depends on:** none (independent of Step 1).

In `GroupCard` (`GroupsScreen.kt:290-389`), wrap the invite-code `Row` (lines 352-379 — the code text + copy-button `Row`, sitting between the group-name `Text` and the member-count `Text`) in a global-group check, mirroring the same condition already used for `GroupHeaderBar` in PR-25:

```kotlin
if (group.id != GlobalGroupConstants.GROUP_ID) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.group_code_label, group.inviteCode),
            color = appColors.secondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(4.dp))
        Box(/* ...copy button, unchanged... */) { /* ... */ }
    }
    Spacer(Modifier.height(4.dp))
}
Text(
    text = stringResource(R.string.group_member_count, group.memberIds.size),
    color = appColors.secondary,
    style = MaterialTheme.typography.bodyMedium
)
```
The trailing `Spacer(Modifier.height(4.dp))` between the code row and the member-count row moves *inside* the conditional (it's only needed as a gap after the code row when that row is actually shown). `GlobalGroupConstants` is already imported in this file (used at line 322 for the avatar).

**Corrected after Antigravity review**: the first draft claimed this prevents "a stray leading gap" for the member-count text on the global card — checked against the actual layout and that's not quite right. There's a *separate*, pre-existing `Spacer(Modifier.height(8.dp))` immediately **before** the invite-code row (between it and the group name), which this change does not touch and stays unconditional. So for the global group, the member-count text ends up directly below that existing 8dp spacer — i.e. an 8dp gap between the group name and the member count, not a zero-gap or "stray" gap. That's a perfectly reasonable spacing outcome (consistent with the app's existing 8dp rhythm elsewhere on this card), so no further change is needed — just correcting the plan's own reasoning about *why* it looks fine, rather than leaving an inaccurate claim in place.

### Done when

`./gradlew assembleDebug` **and `./gradlew test`** both pass (added per Codex review — the first draft only listed `assembleDebug`, which doesn't meet Rule 9/10's "independently testable" bar even though this step adds no new test cases itself; running the full suite confirms no regression). Existing `GroupsScreen` `@Preview` functions still compile. Per this project's established convention (no Compose UI unit tests exist anywhere in the codebase, including for the identical `GroupHeaderBar` global-group-hide logic shipped in PR-25), the visual outcome itself is not covered by an automated test — verify via Compose Preview or, if you want real device confirmation, that's a manual ask-first step per Rule 7, not part of this step's automated "done."

---

## Cross-Review Log (per Rule 10 — completed)

Both reviewers run against this plan (at its scratch-path draft, prior to being moved into `docs/plans/`), 2026-09-05:

- `codex exec -s read-only "Review the plan at C:\Users\brici\.claude\plans\refactored-coalescing-flask.md..."` — 7 findings. All verified against the actual current code before applying:
  - **High** (non-atomic read-then-write race) — verified real, accepted as a documented limitation (Section 1c) rather than engineered around, matching PR-25's identical precedent for the same reason (disproportionate at this app's scale).
  - **High** (3 more test files break compilation: `CreateGroupUseCaseTest.kt`, `UploadGroupPhotoUseCaseTest.kt`, `GroupViewModelIntegrationTest.kt`) — verified true by reading all three files directly; added to Step 1's file list and Test updates.
  - **Medium** (analytics semantics for the already-member no-op unresolved) — resolved: `AppEvent.GroupJoined` is now only logged when `alreadyMember == false` (Section 1e).
  - **Medium** (Step 2 missing `./gradlew test` per Rule 9/10) — fixed (Step 2 Done-when).
  - **Medium** (`FirebaseGroupDataSourceTest.kt` description falsely implied an existing "no group found" case for `joinGroup`) — verified false, corrected.
  - **Medium** (stale/deleted-group freshness weakened by the early-return optimization) — verified real, accepted as a documented limitation (Section 1c).
  - **Low** (PR/scope governance underspecified) — resolved: this is now explicitly PR-26, not a PR-25 follow-up (see top of this document).
  - **Low** (scratch-plan location correctly flagged as needing to move before formal review) — acknowledged; moved to `docs/plans/join-outcome-feedback.md`.
- `agy.exe --mode plan --dangerously-skip-permissions -p "Review the plan at C:\Users\brici\.claude\plans\refactored-coalescing-flask.md..." --model gemini-3.1-pro-high --effort high` — 4 findings:
  - **High** (same 3 missing test files as Codex found, independently) — corroborates the fix above; also specifically named the exact `coEvery`/`assertEquals` lines in `GroupViewModelIntegrationTest.kt` that need updating, which the plan now quotes directly.
  - **Medium** (same false "existing test" claim as Codex found, independently, plus correctly identified where that test actually lives — `GroupViewModelTest.kt`) — corroborates the fix above.
  - **Low** (Step 2's spacer reasoning was slightly inaccurate about which `Spacer` mattered) — verified real, corrected (Step 2).
  - Verified-clean, no action: `GroupActionUiState`'s new `alreadyMember: Boolean = false` default is backward-compatible with `CreateGroupScreen.kt`/`JoinGroupScreen.kt`'s existing property-based (not positional) access.

Both reviewers independently converged on the same two highest-priority findings (the 3 missing test files, and the false test-coverage claim) — strong signal those were real, not reviewer noise. No findings were rejected as stale/inapplicable this round; every one was verified true and fixed or resolved above.

### Implementation Cross-Review (Rule 10 step 5 — completed 2026-09-05)

Both steps implemented, `./gradlew assembleDebug test` green locally (including the 3 new `FirebaseGroupDataSourceTest.kt` `joinGroup` cases). Ran against the full uncommitted diff:

- `codex exec review --uncommitted` — **no actionable correctness, security, or maintainability regressions identified.**
- `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high` — reviewed the diff, confirmed it aligns with this plan: correct state management with no dropped `GroupUiEffect`s, analytics correctly skipped for the already-member no-op, clean Firestore query handling, and adequate unit test coverage. Independently ran `./gradlew assembleDebug` and `./gradlew test` itself and confirmed both pass.

Neither reviewer raised a finding requiring code changes — the implementation matches the reviewed plan as written. No further action needed.

## Critical Files

- `domain/model/JoinGroupResult.kt` (new), `domain/repository/GroupRepository.kt`, `domain/usecase/JoinGroupUseCase.kt`
- `data/repository/GroupRepositoryImpl.kt`, `data/remote/firebase/FirebaseGroupDataSource.kt`
- `presentation/groups/GroupUiState.kt`, `presentation/groups/GroupViewModel.kt`, `presentation/groups/GroupsScreen.kt`
- `values/strings.xml`, `values-en/strings.xml`
- Tests: `FirebaseGroupDataSourceTest.kt`, `JoinGroupUseCaseTest.kt`, `GroupViewModelTest.kt`, `CreateGroupUseCaseTest.kt`, `UploadGroupPhotoUseCaseTest.kt`, `GroupViewModelIntegrationTest.kt`
