# PR-25 — Join Groups via Shareable Link

> Standalone implementation plan, self-contained for `codex exec -s read-only` / `agy.exe --mode plan` review. Every file path, current behavior, and proposed change is described inline — no external conversation context required.
>
> **Location note:** this plan was originally drafted at `C:\Users\brici\.claude\plans\refactored-coalescing-flask.md` (outside the repo) during Claude Code's plan-mode workflow, then moved here per project Rule 8 (`CLAUDE.md`) — plans must live in `docs/plans/` so Codex and Antigravity have direct filesystem access to review them. This file is now the canonical version; the original is stale.
>
> **Structure note:** per project Rule 9, this plan is divided into numbered Steps. Each Step lists its own Files, Implementation, and "Done when" criteria, and is meant to be independently buildable/testable and resumable cold — if a session hits a usage limit mid-PR, the next session should be able to pick up at the first not-yet-done Step without re-reading anything but that Step (plus the shared Context/Confirmed Facts below, which every Step assumes).

## Context

Today, joining a group in NFLocosPick only works by manually typing a 6-character invite code into `JoinGroupScreen` (`app/src/main/java/com/softeen/nflocospicks/presentation/groups/JoinGroupScreen.kt`). The group admin can copy or share that raw code from `GroupHeaderBar`, but there's no real, tappable link — a recipient must open the app, find "Unirse a Grupo," and retype the code by hand.

This PR adds joining via a **verified Android App Link** (HTTPS deep link over Firebase Hosting), chosen explicitly over a bare custom URI scheme (e.g. `nflocospicks://join`) because the product owner wants the link to degrade gracefully for people who don't have the app installed yet: tapping it opens the app straight into a pre-filled join flow if installed, or a web fallback page if not. Firebase Hosting is the natural host since the project already uses Firebase (Auth/Firestore/Functions) and gets `<project>.web.app`/`<project>.firebaseapp.com` for free the moment Hosting is enabled — no new infrastructure vendor.

The user still must tap "Unirme" to confirm the join — this PR does not introduce silent auto-join.

## Confirmed Facts (do not re-derive — shared context for every Step below)

- Invite codes: `FirebaseGroupDataSource.kt:20-23` generates 6 chars from `"ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"`, no uniqueness check. `joinGroup` (lines 44-60) queries `groups` by `inviteCode`, throws `NoSuchElementException` if not found, then does a non-transactional `arrayUnion` update.
- `JoinGroupUseCase.invoke(inviteCode, userId)` normalizes with `.trim().uppercase()` before calling the repository. No `source` parameter.
- `GroupViewModel.joinGroup(inviteCode: String)` resolves the current `userId`, drives `GroupActionUiState`, and on success logs `AppEvent.GroupJoined(group.id, group.name)` — this event has no `source` param today. **Verified convention** (`AppEvent.kt:19`, `GroupViewModel.kt:150-153`, `GroupsScreen.kt:249,269`): `AppEvent.GroupOpened(groupId, groupName, source)` is logged from `GroupViewModel.onGroupClicked(groupId: String, source: String = "group_list")`, a default-valued parameter populated by each Composable call site (`"group_list"` vs `"global_feed_panel"`). This PR's `source` param on `joinGroup`/`GroupJoined` follows this exact, already-established pattern.
- `GroupViewModel` is Hilt-scoped to the `Screen.Groups` back-stack entry (verified `NavGraph.kt:140-143,150-154,189-192,211-214`: every one of `CreateGroup`/`JoinGroup`/`History`/`GroupSession` does `hiltViewModel(navController.getBackStackEntry(Screen.Groups.route))`). **This means `Screen.Groups` must already be in the back stack before navigating to `Screen.JoinGroup`**, or `getBackStackEntry` throws.
- `firestore.rules`' `groups` `update` rule already has a self-join carve-out (a non-member may add only their own uid to `memberIds`) — confirmed no rules change is needed; link-based join reuses the exact same `GroupViewModel.joinGroup(code)` path as manual entry.
- `MainActivity.kt` is `launchMode="singleTask"` with an existing `onNewIntent`/`onCreate` pair that both call `handleEmailLinkIntent(intent)` — a sibling check that early-returns unless `userRepository.isSignInWithEmailLink(link)` is true. A join-link handler is a second, independent sibling check at the same two call sites.
- **Verified exact auth-gating logic** (`NavGraph.kt:246-273`, reproduced in full since Step 4 extends this block):
  ```kotlin
  LaunchedEffect(authState) {
      val state = authState
      when {
          state is AuthUiState.Authenticated && state.isProfileSynced &&
              navController.currentDestination?.route == Screen.Login.route -> {
              val destination = if (state.user.isProfileComplete) Screen.Groups.route else Screen.Account.route
              navController.navigate(destination) { popUpTo(Screen.Login.route) { inclusive = true } }
          }
          state is AuthUiState.Idle &&
              navController.currentDestination?.route != Screen.Login.route -> {
              navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
          }
      }
  }
  ```
  This is keyed **only on `authState`**. A brand-new user with an incomplete profile is routed `Login → Account`; `AccountScreen`'s `onSetupComplete` callback (`NavGraph.kt:111-115`) then navigates `Account → Groups` **directly**, via its own `navController.navigate(...)` call — this does **not** go through the `LaunchedEffect(authState)` block at all, since `authState` itself doesn't change during that hop. Step 4 accounts for this.
- `firebase.json` has only `functions`/`firestore`/`storage` keys today — no `hosting` key. `.firebaserc` → `"default": "nflocospicks"`. No `public/`/`hosting/` folder exists.
- `AndroidManifest.xml` already has one `autoVerify="true"` intent-filter for Firebase's email-link sign-in, host `nflocospicks.firebaseapp.com`, `pathPrefix="/__/auth/links"` — flagged in-code as an *unverified guess* specifically about Firebase Auth's "Authorized domains" list. That flag is unrelated to Hosting/App Links (which just needs Hosting enabled). To keep the two concerns visually distinct, this PR uses `nflocospicks.web.app` (Hosting's canonical domain) as the App Links host, not `firebaseapp.com`.
- `keystore.properties` (gitignored) backs `signingConfigs.release` in `app/build.gradle.kts:43-50` and must not be read directly. `./gradlew signingReport` prints SHA1/SHA256 for debug and release variants to console without exposing the keystore file itself.
- **Corrected during implementation (2026-09-05): the app is already published** at `https://play.google.com/store/apps/details?id=com.softeen.nflocospicks` (user-confirmed) — the "pre-launch" assumption throughout the original plan draft was wrong. This means the app is being installed by real users through Play App Signing's re-signing today, so `assetlinks.json` needs Play App Signing's certificate SHA256 — not just the local debug/upload-key entries — to be genuinely release-ready, not "later, after the first publish" as originally filed. Whether that fingerprint is already registered anywhere (e.g. already added to Firebase for Google Sign-In per `CLAUDE.md`'s Release Checklist) is unconfirmed — ask the user before assuming either way. App Links' `assetlinks.json` is a distinct mechanism/file from that Google Sign-In registration even if the underlying cert is the same.
- `ScreenNames.kt:7` maps `Screen.JoinGroup.route -> "join_group"` by comparing against the `Screen.JoinGroup.route` **symbol**, not a literal — confirmed this still matches correctly once `Screen.JoinGroup.route` becomes the template string `"join_group?code={code}"`, since `NavBackStackEntry.destination.route` always reports the registered template, not the filled-in instance. No change needed there.
- Existing pure-Kotlin, no-Android-import top-level functions already live in `presentation/navigation` and `presentation/common` (e.g. `EspnLogoUrl.kt`) and are unit-tested today without Robolectric. This PR follows the same pattern rather than introducing anything in `domain/` (a URL/deep-link is a navigation/routing concern, not a business rule).
- `Screen.kt` routes are plain strings; only `Screen.GroupSession` has a `createRoute(...)` helper today.
- Both `values/strings.xml` and `values-en/strings.xml` exist and are kept in parallel (all in-app Spanish strings must be neutral Mexican tuteo).

## Decisions Already Made (resolved by the user, 2026-09-04 — do not re-litigate)

1. **PR-24 (membership subcollection migration) is frozen indefinitely.** Expected scale is a few dozen users, well under the scale concerns that motivated it. This PR proceeds against today's `memberIds[]` array model, no dependency on PR-24.
2. **Invite-code uniqueness gets fixed inside this PR** (not deferred) — see Step 8.
3. **Invite links ship as indefinite, non-revocable bearer credentials**, same as today's manual code. No rotation/revocation mechanism in this PR.
4. **The global default group ("NFLocos de Corazón") hides its invite affordance entirely** (copy-code and share, not just the new link) — see Step 6.

## Steps Overview

| Step | What | New/changed files | Depends on |
|---|---|---|---|
| 1 | Firebase Hosting + App Link manifest entry | `firebase.json`, `public/.well-known/assetlinks.json` (new), `public/join/index.html` (new), `AndroidManifest.xml` | none |
| 2 | Pure invite-code link parser | `JoinLinkParser.kt` (new), `JoinLinkParserTest.kt` (new) | none |
| 3 | `MainActivity` intent handling | `MainActivity.kt` | Step 2 |
| 4 | `NavGraph` wiring + pure post-auth decision fn | `Screen.kt`, `NavGraph.kt`, `PostAuthNavActionTest.kt` (new) | Step 3 (for full wiring; the pure function/tests have no dependency) |
| 5 | Analytics `source` param | `AppEvent.kt`, `GroupViewModel.kt`, `GroupViewModelTest.kt` | none — can be done any time, even first |
| 6 | Shareable link builder + `GroupHeaderBar` UI | `GroupInviteLink.kt` (new), `GroupInviteLinkTest.kt` (new), `GroupHeaderBar.kt` | none structurally |
| 7 | `JoinGroupScreen` prefilled-code UI + strings | `JoinGroupScreen.kt`, `strings.xml`, `strings-en.xml` | Step 4 (for prefilled code to actually arrive end-to-end) |
| 8 | Invite-code uniqueness fix | `FirebaseGroupDataSource.kt`, `FirebaseGroupDataSourceTest.kt` (new) | none — fully independent |
| 9 | Docs wrap-up | `CLAUDE.md`, `.agents/rules/roadmap-active-3.md` | all prior steps (describes what shipped) |

Suggested implementation order: 5, 8, 2, 1, 6 (all independent, can go in any order or in parallel across sessions) → 3 → 4 → 7 → 9. Steps 5, 8, 2, 1, and 6 have no dependencies on each other or on anything not yet built, so they're the safest place to resume if picking this up cold with no other context than this file.

---

## Step 1 — Firebase Hosting Infrastructure + App Link Manifest Entry

**Files:** `firebase.json`, `public/.well-known/assetlinks.json` (new), `public/join/index.html` (new), `app/src/main/AndroidManifest.xml`

**Depends on:** none.

### 1a. `firebase.json` — add a `hosting` block

```json
{
  "functions": [ /* unchanged */ ],
  "firestore": { "rules": "firestore.rules" },
  "storage": [ /* unchanged */ ],
  "hosting": {
    "public": "public",
    "ignore": ["firebase.json", "**/node_modules/**"],
    "rewrites": [
      { "source": "/join",   "destination": "/join/index.html" },
      { "source": "/join/**", "destination": "/join/index.html" }
    ]
  }
}
```

The `ignore` array deliberately **omits** the commonly-generated `**/.*` dotfile-exclusion glob. Relying on Hosting's `.well-known/` special-case *and* a dotfile-ignore glob at the same time is a common reason teams ship a working build with a 404'ing `assetlinks.json`. Omitting the glob removes the ambiguity (no other dotfiles are planned under `public/`). **Manual verification required**: after the first `firebase deploy --only hosting`, curl `https://nflocospicks.web.app/.well-known/assetlinks.json` directly to confirm it's actually served.

### 1b. New directory: `public/`

```
public/
├── .well-known/
│   └── assetlinks.json
└── join/
    └── index.html
```

**`public/.well-known/assetlinks.json`** (SHA256 values are placeholders filled in during implementation via `./gradlew signingReport`):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.softeen.nflocospicks",
      "sha256_cert_fingerprints": [
        "REPLACE_WITH_DEBUG_SHA256",
        "REPLACE_WITH_RELEASE_SHA256"
      ]
    }
  }
]
```

Both the debug variant's SHA256 (so `adb`-installed debug builds pass Android's App Links handshake during development/verification) and the release/upload variant's SHA256 are included as separate array entries — a third entry for Play App Signing's production cert gets appended later (see Step 1's Manual Follow-up below — this is release-blocking, not optional).

**`public/join/index.html`** — minimal fallback page shown to anyone who taps the link without the app installed (or opens it from a desktop browser). Illustrative, not final copy:

```html
<!DOCTYPE html>
<html lang="es-MX">
<head><meta charset="utf-8"><title>NFLocos Picks</title></head>
<body>
  <h1>Te invitaron a un grupo en NFLocos Picks</h1>
  <p>Descarga la app para unirte:</p>
  <p><a href="https://play.google.com/store/apps/details?id=com.softeen.nflocospicks">NFLocos Picks en Google Play</a></p>
</body>
</html>
```

**Corrected after Codex review**: the first draft echoed the invite code back onto the page (`?code=... -> "Código de invitación: ABC123"` in visible text). Codex correctly flagged this as amplifying an already-weak credential — the code is a permanent, non-expiring, non-revocable bearer token (see Decisions Already Made above), and rendering it as plain page text adds a second place it leaks (screenshots, shoulder-surfing, browser history *of the rendered page*, not just the URL) for zero functional benefit — the installed app reads the code from the URL itself via `extractInviteCodeFromJoinLink` (Step 2), it never needs the fallback page to display it. The fallback page now only confirms "you were invited," with no code, no JS.

**Corrected during implementation**: the app turned out to already be published (see the corrected Confirmed Facts note above) — the real Play Store URL is wired in directly, rather than the placeholder "not published yet" copy the plan originally called for. No root `public/index.html` is added — out of scope for this PR.

### 1c. `AndroidManifest.xml` — new `autoVerify` intent-filter

Inside `<activity android:name=".MainActivity">`, added as a **third** intent-filter, after the existing email-link one:

```xml
<!-- Android App Link for joining a group via shareable invite link (PR-25).
     Host is Firebase Hosting's canonical domain (nflocospicks.web.app), kept
     deliberately distinct from the email-link intent-filter's
     nflocospicks.firebaseapp.com above — that host is flagged pending
     verification against Firebase Auth's Authorized domains list, an
     unrelated concern to Hosting/App Links.
     Requires public/.well-known/assetlinks.json to be deployed with the
     correct SHA256 cert fingerprint(s) for autoVerify to actually pass. -->
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="nflocospicks.web.app"
        android:pathPrefix="/join" />
</intent-filter>
```

Two separate `autoVerify` intent-filters (different hosts) on the same activity is valid and matches the existing pattern already established by the email-link filter.

**Flagged during Antigravity review, not resolved analytically**: whether adding this second `autoVerify="true"` host could affect verification of the existing `nflocospicks.firebaseapp.com` host (or vice versa) on some Android versions isn't something confirmed against this app's `minSdk 24` range or the current Domain Verification API — see the device-verification checklist below.

### Done when

- `./gradlew assembleDebug` passes (manifest change alone doesn't affect Kotlin compilation, but confirms no XML errors).
- Manual (ask-first, Rule 7 / Rule 10 step 4/5 discipline applies once code exists to test against): after `firebase deploy --only hosting`, `https://nflocospicks.web.app/join?code=test` loads the fallback page, and `https://nflocospicks.web.app/.well-known/assetlinks.json` returns JSON (not 404).
- Later, after `./gradlew signingReport`: real SHA256 values replace the two placeholders in `assetlinks.json`, redeployed.
- Run `adb shell pm get-app-links com.softeen.nflocospicks` and confirm **both** `nflocospicks.web.app` and `nflocospicks.firebaseapp.com` show as verified.

**Status: fully done, including deploy.** `firebase.json` hosting block, `public/.well-known/assetlinks.json` (3 real fingerprints — debug `C5:E6:C9:74:...9B:FC`, release/upload `25:C2:E2:9B:...91:64` via `./gradlew signingReport`, Play App Signing `0D:86:61:3E:...CF:0E` via Play Console → Protegido con Play → Administrar la firma de apps de Play, "Clave clásica"), `public/join/index.html` (real Play Store link), and the manifest intent-filter are all in place. `firebase deploy --only hosting` completed (first-ever Hosting deploy for this project). Verified live: `https://nflocospicks.web.app/.well-known/assetlinks.json` returns HTTP 200 with all 3 fingerprints; `https://nflocospicks.web.app/join?code=test` returns a 301 to `/join/?code=test` (Firebase Hosting's own clean-URL redirect for a directory-backed route) which then serves the fallback page correctly with the query param intact. **This 301 is a real-world confirmation that Step 2's trailing-slash tolerance in the parser was necessary, not just a hypothetical** — Firebase's own redirect produces exactly the `/join/` form the parser had to be taught to accept; it's harmless for the actual Android App Link path since OS-level intent-filter matching happens on the tapped URI directly, before any network request or redirect. `./gradlew assembleDebug`/`./gradlew test` both green throughout.

---

## Step 2 — Pure Invite-Code Link Parser

**Files:** `app/src/main/java/com/softeen/nflocospicks/presentation/navigation/JoinLinkParser.kt` (new), `app/src/test/java/com/softeen/nflocospicks/presentation/navigation/JoinLinkParserTest.kt` (new)

**Depends on:** none. Fully self-contained, pure Kotlin.

**Location: `presentation/navigation/`, not `domain/`.** Extracting a query parameter from an incoming deep-link string is a navigation/routing concern, not a business rule — the domain layer's use cases operate on an already-clean invite code string and never touch URLs/intents. `presentation/navigation` already hosts this kind of pure, directly-JVM-unit-testable top-level function.

```kotlin
package com.softeen.nflocospicks.presentation.navigation

private const val JOIN_LINK_SCHEME = "https"
private const val JOIN_LINK_HOST = "nflocospicks.web.app"
private const val JOIN_LINK_PATH = "/join"
private val INVITE_CODE_PATTERN = Regex("^[A-Z0-9]{6}$")

/**
 * Extracts and validates the "code" query parameter from an incoming
 * join-link URL string (e.g. "https://nflocospicks.web.app/join?code=ABC123").
 * Pure string parsing — no android.net.Uri — so this is directly
 * JVM-unit-testable. Returns null unless the link is EXACTLY
 * scheme=https, host=nflocospicks.web.app, path=/join (or /join/), and
 * "code" is a 6-char [A-Z0-9] value — this is a trust boundary
 * (MainActivity's VIEW intent-filter is exported, so any app can send an
 * arbitrary Intent at it), not just a convenience parser, so matching is
 * exact, not substring.
 */
fun extractInviteCodeFromJoinLink(link: String): String? {
    val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return null
    if (uri.scheme != JOIN_LINK_SCHEME) return null
    if (uri.host != JOIN_LINK_HOST) return null
    if (uri.path != JOIN_LINK_PATH && uri.path != "$JOIN_LINK_PATH/") return null

    val rawCode = (uri.query ?: return null)
        .split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "code" }
        ?.get(1)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        ?.trim()
        ?.uppercase()
        ?: return null

    return rawCode.takeIf { INVITE_CODE_PATTERN.matches(it) }
}
```

`java.net.URI` is a plain JDK class (not `android.net.Uri`), so this stays directly unit-testable on the JVM without Robolectric.

**Correction history** (kept for context — do not re-litigate; verified against this current version already):
- *Codex review*: the very first draft used `contains("/join")` substring matching, which would have accepted `/not/join`, `/joining`, or any host/scheme at all. Fixed to exact `java.net.URI`-based matching plus output validation, as shown above.
- *Antigravity review, round 1*: added trailing-slash tolerance (`/join` or `/join/`) — the manifest's `pathPrefix="/join"` already routes a trailing-slash link to the app; without this the parser would silently reject a link Android already decided belonged to this app.
- *Antigravity review, round 2, two findings evaluated and rejected as stale*: "fragment (`#`) bleeding into the code value" and "no URL-decoding, doesn't use `java.net.URI`" — both checked directly against the code above; `java.net.URI.getQuery()` correctly excludes `#fragment` per RFC 3986, and `java.net.URLDecoder.decode(...)` is already present. These findings described the pre-Codex-fix first draft, not this version.

### Test cases (`JoinLinkParserTest.kt`)

Valid `https://nflocospicks.web.app/join?code=ABC123`; valid with trailing slash (`.../join/?code=ABC123`); wrong host (`https://evil.example/join?code=ABC123`); wrong scheme (`http://...`); path that merely contains but isn't exactly `/join` (`/joining`, `/not/join`, `/join/extra`); missing `code` param; empty `code` value; percent-encoded `code` value (decodes correctly); repeated `code` params (first match wins — assert which, don't leave it implicit); extra unrelated query params alongside a valid `code`; code failing `[A-Z0-9]{6}` (wrong length, lowercase before uppercasing is applied vs. after, non-alphanumeric characters); malformed/garbage input string (no exception, returns null).

### Done when

`./gradlew test` passes, including the new `JoinLinkParserTest` covering every case above (implemented: 15 cases, all passing — one extra case beyond the list above was added to concretely regression-guard the "fragment bleeding into the code" concern raised, and rejected as stale, during the round-2 Antigravity review).

**Status: done.**

---

## Step 3 — MainActivity Intent Handling

**Files:** `app/src/main/java/com/softeen/nflocospicks/MainActivity.kt`

**Depends on:** Step 2 (`extractInviteCodeFromJoinLink`).

Add a Compose `State` holder for the pending code, and a sibling handler function alongside `handleEmailLinkIntent`:

```kotlin
private companion object {
    const val KEY_PENDING_INVITE_CODE = "pending_invite_code"
}

private val pendingInviteCode = mutableStateOf<String?>(null)

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing splash/locale setup ...
    pendingInviteCode.value = savedInstanceState?.getString(KEY_PENDING_INVITE_CODE)
    handleEmailLinkIntent(intent)
    if (savedInstanceState == null) {
        // Only process the launching Intent's join-link data on a genuinely fresh
        // start — NOT on a recreation (rotation, theme change, config change). See
        // the "spurious re-trigger" note below for why this guard is required.
        handleJoinLinkIntent(intent)
    }

    setContent {
        NFLocosPickTheme {
            NavGraph(
                pendingInviteCode = pendingInviteCode.value,
                onPendingInviteConsumed = { pendingInviteCode.value = null }
            )
        }
    }
}

override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    pendingInviteCode.value?.let { outState.putString(KEY_PENDING_INVITE_CODE, it) }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleEmailLinkIntent(intent)
    handleJoinLinkIntent(intent)
}

private fun handleJoinLinkIntent(intent: Intent?) {
    val link = intent?.data?.toString() ?: return
    val code = extractInviteCodeFromJoinLink(link) ?: return
    pendingInviteCode.value = code
}
```

`pendingInviteCode` is a plain `androidx.compose.runtime.mutableStateOf` field on the Activity (not itself a Composable) — reading `.value` inside `setContent { }` still participates in recomposition when the Activity later mutates it from `onNewIntent`, the standard idiom for threading Activity-level Intent data into a Compose tree without a ViewModel. `handleEmailLinkIntent` and `handleJoinLinkIntent` are mutually exclusive in practice (different hosts/paths) but both are safe to call unconditionally, matching the existing "silently no-op on non-matching links" pattern.

**Correction history:**
- *Antigravity review, round 1*: added `onSaveInstanceState`/restore-from-`savedInstanceState`. Without it, a real scenario silently drops the invite: user taps a join link (unauthenticated) → gets routed to sign-in → leaves the app to check a verification code/email-link in another app → Android kills the process under memory pressure while backgrounded → user returns → recreation doesn't reliably redeliver the original Intent in every process-death path — a plain in-memory field has no protection at all there.
- *Antigravity review, round 2 — critical bug in the round-1 fix*: `launchMode="singleTask"` retains the Activity's `Intent` across its lifetime, including plain configuration changes (rotation, theme switch), not just process death. The round-1 version called `handleJoinLinkIntent(intent)` unconditionally on every `onCreate`, including recreations. Sequence that broke: tap link → `pendingInviteCode` set → reach `JoinGroupScreen`, join or navigate away, `onPendingInviteConsumed()` sets it back to `null` → **rotate the screen** → `onCreate` runs again → the Intent's URI was never cleared, so `handleJoinLinkIntent` re-extracts the same code, overwriting the just-restored `null` → user gets redirected to `JoinGroupScreen` again, unprompted. **Fix**: the `if (savedInstanceState == null)` guard above — the standard idiom for "only process launch-Intent extras once, not on every recreation." Composes correctly with the round-1 fix: on process death, `savedInstanceState` is non-null (even holding no key, if the code was already consumed before death), so extraction is correctly skipped in favor of the restored value; on a genuinely fresh launch, extraction runs normally. `onNewIntent` is unaffected — it only fires for a genuinely new Intent delivery to an already-running `singleTask` Activity, never for a mere recreation.

### Done when

`./gradlew assembleDebug` passes. (No new unit tests here — Android-framework lifecycle code isn't unit-testable without Robolectric per this project's existing conventions; correctness of the rotation/process-death fixes is verified per the device-verification checklist in Step 1/9's Manual Follow-up.)

**Corrected during implementation — real Step 3/4 ordering issue, not just a soft dependency.** The plan's original Step 3 code sample already called `NavGraph(pendingInviteCode = ..., onPendingInviteConsumed = ...)`, but `NavGraph`'s signature doesn't gain those parameters until Step 4 — that call wouldn't compile with only Step 3 done, contradicting Step 3's own "independently buildable" requirement (Rule 9). **Fix**: implemented Step 3 exactly as shown above (the `pendingInviteCode` field, `handleJoinLinkIntent`, `onSaveInstanceState`, the `savedInstanceState == null` guard) but left the `setContent { NavGraph() }` call site unchanged — still zero arguments — since `NavGraph` doesn't accept them yet. `MainActivity` now correctly captures the pending code into `pendingInviteCode` and is ready for Step 4, which is what will actually change the `NavGraph()` call site to `NavGraph(pendingInviteCode = pendingInviteCode.value, onPendingInviteConsumed = { pendingInviteCode.value = null })` once `NavGraph` itself supports those parameters.

**Status: done.** `./gradlew assembleDebug` and `./gradlew test` both green.

---

## Step 4 — NavGraph Wiring: Route Args + Pure Post-Auth Decision Function

**Files:** `app/src/main/java/com/softeen/nflocospicks/presentation/navigation/Screen.kt`, `app/src/main/java/com/softeen/nflocospicks/presentation/navigation/NavGraph.kt`, `app/src/test/java/com/softeen/nflocospicks/presentation/navigation/PostAuthNavActionTest.kt` (new)

**Depends on:** Step 3 for full end-to-end wiring (`NavGraph` needs the `pendingInviteCode`/`onPendingInviteConsumed` params `MainActivity` passes in). The pure `decidePostAuthNavigation` function and its unit tests have no such dependency and can be written/tested standalone.

### 4a. `Screen.kt` — optional route argument

```kotlin
data object JoinGroup : Screen("join_group?code={code}") {
    fun createRoute(code: String? = null): String =
        if (code != null) "join_group?code=${java.net.URLEncoder.encode(code, "UTF-8")}" else "join_group"
}
```

**Corrected after Codex review**: the code is URI-encoded here rather than interpolated raw. It's a no-op today (invite codes are always `[A-Z0-9]{6}` per `INVITE_CODE_PATTERN`/`generateInviteCode`'s charset), but `createRoute` shouldn't rely on every future caller upholding that invariant.

### 4b. `NavGraph.kt` signature and composable registration

```kotlin
@Composable
fun NavGraph(
    pendingInviteCode: String? = null,
    onPendingInviteConsumed: () -> Unit = {}
) {
    // ... unchanged navController/authViewModel/settingsViewModel setup ...
```

**Also required as part of this step (deferred here from Step 3 — see that step's implementation note)**: `MainActivity.kt`'s `setContent { NFLocosPickTheme { NavGraph() } }` call site changes to:
```kotlin
setContent {
    NFLocosPickTheme {
        NavGraph(
            pendingInviteCode = pendingInviteCode.value,
            onPendingInviteConsumed = { pendingInviteCode.value = null }
        )
    }
}
```
Step 3 already added the `pendingInviteCode` field and everything that populates it; this is the one-line change that actually threads it into the Compose tree, which could only happen once `NavGraph` accepts the parameter (i.e. once this step's signature change above lands).

`Groups`' manual "Unirse" entry point (`NavGraph.kt:72-74`) changes from `navController.navigate(Screen.JoinGroup.route)` to `navController.navigate(Screen.JoinGroup.createRoute())` — `Screen.JoinGroup.route` is now a template string, not a navigable literal.

The `JoinGroup` composable registration (`NavGraph.kt:150-159`) gains an optional nav argument:

```kotlin
composable(
    route = Screen.JoinGroup.route,
    arguments = listOf(navArgument("code") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    })
) { navBackStackEntry ->
    val groupsEntry = remember(navBackStackEntry) {
        navController.getBackStackEntry(Screen.Groups.route)
    }
    val groupViewModel: GroupViewModel = hiltViewModel(groupsEntry)
    val prefilledCode = navBackStackEntry.arguments?.getString("code")
    JoinGroupScreen(
        prefilledCode  = prefilledCode,
        onNavigateBack = { navController.popBackStack() },
        viewModel      = groupViewModel
    )
}
```

### 4c. The pure decision function

The existing `LaunchedEffect(authState)` block is Compose-embedded and untestable as-is. Extract the decision into a pure function:

```kotlin
sealed class PostAuthNavAction {
    data class Navigate(
        val route: String,
        val popUpToRoute: String? = null,
        val popUpToInclusive: Boolean = false
    ) : PostAuthNavAction()
    object None : PostAuthNavAction()
}

/**
 * Given the current auth state, the current back-stack route, and whether a
 * join-link invite code is pending, decides the single navigation action (if
 * any) NavGraph's auth-gating effect should take. Pure — no NavController,
 * directly unit-testable.
 */
fun decidePostAuthNavigation(
    authState: AuthUiState,
    currentRoute: String?,
    pendingInviteCode: String?
): PostAuthNavAction = when {
    authState is AuthUiState.Idle && currentRoute != Screen.Login.route ->
        PostAuthNavAction.Navigate(Screen.Login.route, popUpToRoute = null, popUpToInclusive = true) // popUpTo(0)

    authState is AuthUiState.Authenticated && authState.isProfileSynced && currentRoute == Screen.Login.route -> {
        val destination = if (authState.user.isProfileComplete) Screen.Groups.route else Screen.Account.route
        PostAuthNavAction.Navigate(destination, popUpToRoute = Screen.Login.route, popUpToInclusive = true)
    }

    authState is AuthUiState.Authenticated && authState.isProfileSynced && authState.user.isProfileComplete &&
        pendingInviteCode != null &&
        currentRoute != Screen.Login.route && currentRoute != Screen.Account.route ->
        PostAuthNavAction.Navigate(Screen.JoinGroup.createRoute(pendingInviteCode))

    else -> PostAuthNavAction.None
}
```

And the Composable wrapper:

```kotlin
val currentBackStackEntry by navController.currentBackStackEntryAsState()
LaunchedEffect(authState, pendingInviteCode, currentBackStackEntry) {
    when (val action = decidePostAuthNavigation(
        authState, navController.currentDestination?.route, pendingInviteCode
    )) {
        is PostAuthNavAction.Navigate -> {
            navController.navigate(action.route) {
                if (action.popUpToRoute != null) {
                    popUpTo(action.popUpToRoute) { inclusive = action.popUpToInclusive }
                } else if (action.popUpToInclusive) {
                    popUpTo(0) { inclusive = true }
                }
            }
            if (action.route.startsWith("join_group")) onPendingInviteConsumed()
        }
        PostAuthNavAction.None -> Unit
    }
}
```

**Correction history:**
- *Antigravity review, round 2 — the join-link branch's original gate was wrong, not just narrow.* The first version required `currentRoute == Screen.Groups.route` specifically, reasoning that `Screen.JoinGroup`'s composable does `navController.getBackStackEntry(Screen.Groups.route)`, which throws if `Groups` isn't in the back stack. That conflated two different things: "`Groups` exists somewhere in the back stack" (all `getBackStackEntry` actually needs) vs. "`Groups` is the current top-of-stack screen" (what the condition actually checked). Traced through `NavGraph.kt`: every screen reachable after authentication is pushed on top of `Groups` with no `popUpTo` that ever removes it while `authState` stays `Authenticated`+complete-profile. So `Groups` is *always* in the back stack once authenticated with a complete profile, regardless of which screen is on top — meaning the original restriction was unnecessarily narrow: a user reading `PickScreen`/`GroupSession` who tapped a link would previously see nothing happen until they happened to navigate back to `Groups` themselves. **Fix**: the branch now fires from any screen except `Login`/`Account`.
- *Found during the first plan review round*: `AccountScreen.onSetupComplete` navigates `Account → Groups` via a **direct** `navController.navigate(...)` call, independent of this effect — it doesn't change `authState`. A `LaunchedEffect` keyed only on `(authState, pendingInviteCode)` would never re-evaluate when a new user finishes account setup and lands on `Groups`, silently dropping the pending invite. **Fix**: also key the effect on `navController.currentBackStackEntryAsState()`, so it re-evaluates on every navigation — this remains necessary even with the broadened branch above, since the branch still needs `currentRoute` to be re-read at the right moment.
- **Residual, accepted edge case**: if a user is already sitting on `JoinGroupScreen` (manually) and taps a link with a *different* code, the effect re-fires and navigates to a fresh `join_group` route instance — Compose Navigation treats this as a new back-stack entry rather than updating in place, pushing a duplicate entry (an extra "back" press to leave). Cosmetic, not a correctness bug; not worth the added complexity of collapsing same-route re-navigations for a rare double-tap scenario.

### Test cases (`PostAuthNavActionTest.kt`)

`Idle` + not on Login → navigate to Login; `Authenticated`+synced+complete profile+on Login → navigate to Groups (regression guard); `Authenticated`+synced+incomplete profile+on Login+pending code present → navigates to Account, **not** JoinGroup (deferred-consumption case); `Authenticated`+synced+complete profile+on Groups+pending code → navigates to JoinGroup with the code; **`Authenticated`+synced+complete profile+on a non-Groups screen (e.g. `"picks/{groupId}"`, `"group_session/{groupId}"`)+pending code → also navigates to JoinGroup** (the case that was broken before the round-2 Antigravity fix — assert explicitly, not just implicitly via the Groups case); `Authenticated`+synced+complete profile+on Login or Account+pending code → no action (must not fire mid-auth-flow); `Authenticated` but `isProfileSynced == false` → no action; pending code `null` on any route → no action.

### Done when

`./gradlew test` passes including the new `PostAuthNavActionTest`; `./gradlew assembleDebug` passes.

**Status: done.** `MainActivity.kt`'s `setContent { NavGraph() }` call site updated to pass `pendingInviteCode`/`onPendingInviteConsumed` (deferred here from Step 3 as planned). `Screen.JoinGroup`'s composable registration gains the `code` nav argument (declared but not yet read/passed to `JoinGroupScreen` — that's Step 7, once that composable accepts a `prefilledCode` param; reading it now with nowhere to pass it would just be an unused local). `PostAuthNavActionTest` — 10 cases, all passing, including an explicit regression guard for the `PickScreen`/`GroupSession` broadened-branch fix. `./gradlew assembleDebug` and `./gradlew test` (full suite) both green.

---

## Step 5 — Analytics: `source` Parameter on `GroupJoined`

**Files:** `app/src/main/java/com/softeen/nflocospicks/analytics/AppEvent.kt`, `app/src/main/java/com/softeen/nflocospicks/presentation/groups/GroupViewModel.kt`, `app/src/test/java/com/softeen/nflocospicks/presentation/groups/GroupViewModelTest.kt`

**Depends on:** none — fully independent, safe to implement first or in parallel with any other step.

`AppEvent.kt` — add `source` to `GroupJoined`:

```kotlin
data class GroupJoined(val groupId: String, val groupName: String, val source: String) :
    AppEvent("group_joined", mapOf("group_id" to groupId, "group_name" to groupName, "source" to source))
```

`GroupViewModel.kt` — add a default-valued `source` parameter, following the verified `onGroupClicked(groupId, source: String = "group_list")` precedent:

```kotlin
fun joinGroup(inviteCode: String, source: String = "manual_code") {
    val userId = userRepository.getCurrentUser()?.uid ?: return
    viewModelScope.launch {
        _actionState.value = GroupActionUiState.Loading
        try {
            val group = joinGroupUseCase(inviteCode, userId)
            _actionState.value = GroupActionUiState.Success(group)
            logger.logEvent(AppEvent.GroupJoined(group.id, group.name, source))
        } catch (e: NoSuchElementException) {
            _actionState.value = GroupActionUiState.Error("Código de invitación inválido")
        } catch (e: Exception) {
            _actionState.value = GroupActionUiState.Error(e.message ?: "Error al unirse al grupo")
        }
    }
}
```

**`JoinGroupUseCase` is unchanged.** `source` is an analytics/attribution concept about how the UI was reached, not a business rule — the same reasoning that already keeps `source` out of the use case layer for `GroupOpened`. Keeping it ViewModel-only avoids polluting the domain layer and keeps `JoinGroupUseCaseTest.kt` untouched.

### Test cases (`GroupViewModelTest.kt`)

Existing `joinGroup("ABC123")` success test additionally asserts `it.params["source"] == "manual_code"` (default); new test calls `joinGroup("ABC123", source = "invite_link")` and asserts the logged event's `source` param. `GroupViewModel.joinGroup(code, source)` only needs to verify it forwards whatever `source` it's given — the "edited code falls back to manual_code" comparison logic lives in `JoinGroupScreen` (Step 7), not here.

### Done when

`./gradlew test` passes including the updated `GroupViewModelTest`. `JoinGroupUseCaseTest.kt` needs no changes; run it to confirm no regression.

**Status: done.** `./gradlew assembleDebug` and `./gradlew test` both green; new `joinGroup logs the given source instead of the manual_code default` test passing alongside the updated existing test (9/9 in `GroupViewModelTest`).

Also update `CLAUDE.md`'s Event Inventory table (`group_joined` row) from `group_id, group_name` to `group_id, group_name, source` as part of this step — small doc edit, easy to do alongside the code change while it's fresh.

---

## Step 6 — Shareable Link Builder + GroupHeaderBar UI

**Files:** `app/src/main/java/com/softeen/nflocospicks/presentation/common/GroupInviteLink.kt` (new), `app/src/test/java/com/softeen/nflocospicks/presentation/common/GroupInviteLinkTest.kt` (new), `app/src/main/java/com/softeen/nflocospicks/presentation/common/GroupHeaderBar.kt`

**Depends on:** none structurally (uses a literal host constant, doesn't need Step 1's Hosting deploy to compile or unit-test).

```kotlin
package com.softeen.nflocospicks.presentation.common

private const val APP_LINK_HOST = "nflocospicks.web.app"

/** Builds the shareable App Link a group admin sends to invite someone. Must
 *  match AndroidManifest.xml's join intent-filter host/pathPrefix exactly. */
fun buildGroupInviteLink(inviteCode: String): String =
    "https://$APP_LINK_HOST/join?code=$inviteCode"
```

`GroupHeaderBar.kt` — the share button's message embeds the link. The copy button is left as-is (copies the raw code only) — pasting a bare code into `JoinGroupScreen`'s manual field is a distinct, still-valid use case from sharing a tappable link:

```kotlin
val shareMessage = stringResource(
    R.string.group_invite_share_text,
    group.inviteCode,
    buildGroupInviteLink(group.inviteCode)
)
```

**Corrected during implementation**: `group_invite_share_text` gains a second `%2$s` placeholder in both `values/strings.xml` and `values-en/strings.xml` — done as part of *this* step, not Step 7 as originally filed, since Step 6's own code (the call above) needs it to actually compile a meaningful message. Step 7's strings table below now covers only the notice string it actually introduces.

**Per Decisions Already Made #4 — hide the invite affordance entirely for the global group.** Every user already auto-joins `GlobalGroupConstants.GROUP_ID` ("NFLocos de Corazón") at signup (PR-17), so a shareable link — and, by the same logic, the existing copy-code button — is functionally inert there. `GroupHeaderBar.kt` already imports `GlobalGroupConstants` (used at line 70 for the avatar). The existing `if (group != null)` gate at line 78, which wraps both the copy-code and share buttons, becomes:

```kotlin
if (group != null && group.id != GlobalGroupConstants.GROUP_ID) {
    // ... existing copy-code Box and share Box, unchanged ...
}
```

This hides both affordances (not just the new link) for the global group — if the invite mechanism is inert there, the pre-existing copy-code button is equally inert. Non-global groups are unaffected.

### Test cases

`GroupInviteLinkTest.kt` (new): `buildGroupInviteLink("ABC123")` produces the exact expected URL string. `GroupHeaderBar` doesn't currently have a dedicated Compose test file per this codebase's existing layout — if the global-group hide logic needs coverage beyond manual verification, that's an instrumented/Compose UI test (CI, not local), consistent with this project's existing testing conventions.

### Done when

`./gradlew test` passes including the new `GroupInviteLinkTest`; `./gradlew assembleDebug` passes.

**Status: done.** Both green.

---

## Step 7 — JoinGroupScreen Prefilled-Code UI + Strings

**Files:** `app/src/main/java/com/softeen/nflocospicks/presentation/groups/JoinGroupScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Depends on:** Step 4 for the prefilled code to actually arrive end-to-end via navigation; the screen-level code changes below compile and preview independently.

```kotlin
@Composable
fun JoinGroupScreen(
    prefilledCode: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: GroupViewModel
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    LaunchedEffect(actionState) {
        if (actionState is GroupActionUiState.Success) {
            viewModel.resetActionState()
            onNavigateBack()
        }
    }

    JoinGroupScreenContent(
        actionState    = actionState,
        initialCode    = prefilledCode.orEmpty(),
        onNavigateBack = onNavigateBack,
        onJoinGroup    = { code ->
            // Attribute at submit time, not composition time — see correction
            // history below for why.
            val source = if (code == prefilledCode) "invite_link" else "manual_code"
            viewModel.joinGroup(code, source)
        }
    )
}

@Composable
internal fun JoinGroupScreenContent(
    actionState: GroupActionUiState,
    initialCode: String = "",
    onNavigateBack: () -> Unit,
    onJoinGroup: (String) -> Unit
) {
    // Normalize exactly like manual onValueChange does, so a lowercase/oddly-cased
    // prefilled code from a link renders consistently with what typing would produce.
    var inviteCode by remember(initialCode) {
        mutableStateOf(initialCode.uppercase().filter { it.isLetterOrDigit() }.take(6))
    }
    val appColors = LocalAppColors.current
    // ... existing Column/Text/Spacer scaffolding unchanged ...

    if (initialCode.isNotEmpty()) {
        Text(
            text = stringResource(R.string.join_group_prefilled_notice),
            color = appColors.secondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = inviteCode,
        onValueChange = { inviteCode = it.uppercase().filter { char -> char.isLetterOrDigit() }.take(6) },
        // ... label/placeholder/singleLine/modifier/colors unchanged ...
    )
    // ... rest (error text, Button, TextButton) unchanged ...
}
```

The user still must tap "Unirme" — `onJoinGroup` is only invoked by the existing `Button`'s `onClick`, unchanged. Both `@Preview` functions gain the new `initialCode` default parameter for free (defaults to `""`).

**Correction history:**
- *Codex review*: `join_group_prefilled_notice` was declared as a string resource but never actually rendered in the first draft's code sample. Also added `.filter { it.isLetterOrDigit() }` to the initial-state normalization.
- *Antigravity review, round 1*: the character filter was applied only to the initial prefilled state, leaving the manual `onValueChange` path (`JoinGroupScreen.kt:87` today: `{ inviteCode = it.uppercase().take(6) }`, confirmed no filter) inconsistent — a developer implementing verbatim would filter a link's code but not hand-typed input. Fixed both paths to match.
- *Antigravity review, round 2, two more issues*: (1) `remember` was missing its key (`remember(initialCode) { ... }` now, not bare `remember { ... }`) — idiomatic-correct any time a remembered initial value derives from a parameter. (2) Source attribution broke if a user arrived via a link then edited the code by hand — the first draft computed `source` once at composition time from whether `prefilledCode` was non-null, so an edited submission was still misattributed as `"invite_link"`. Fixed by comparing the submitted code against `prefilledCode` at submit time (shown above).

### New/changed strings (neutral Mexican tuteo)

| Key | `values/strings.xml` (es) | `values-en/strings.xml` (en) | Change |
|---|---|---|---|
| `join_group_prefilled_notice` | `"Código de invitación detectado"` | `"Invite code detected"` | New |

(`group_invite_share_text`'s `%2$s` addition already landed in Step 6, since that step's own code needed it.)

Both files must be updated together per project convention.

### Done when

`./gradlew assembleDebug` passes; existing `@Preview` functions still compile. Manual/device verification (Step 1/9's checklist) covers the end-to-end prefill and source-attribution behavior, since it's Compose UI not covered by a JVM unit test.

**Status: done — this also closes out the wiring deferred from Step 4.** `NavGraph.kt`'s `JoinGroup` composable now reads `navBackStackEntry.arguments?.getString("code")` and passes it as `prefilledCode` (the read/pass-through that Step 4 explicitly deferred until `JoinGroupScreen` could accept the parameter). `./gradlew assembleDebug` and `./gradlew test` (full suite, 157 tests) both green.

---

## Step 8 — Invite-Code Uniqueness Fix

**Files:** `app/src/main/java/com/softeen/nflocospicks/data/remote/firebase/FirebaseGroupDataSource.kt`, `app/src/test/java/com/softeen/nflocospicks/data/remote/firebase/FirebaseGroupDataSourceTest.kt` (new — see Test cases note below for why this differs from the original plan)

**Depends on:** none — fully independent of the rest of this PR; could even ship as its own tiny PR, but per user decision it's bundled into PR-25 since the shareable link raises the stakes of a collision.

Today (`FirebaseGroupDataSource.kt:26-42`), `createGroup` calls `generateInviteCode()` once and writes it with no collision check. Fix: check-and-retry before writing, bounded to a small number of attempts:

```kotlin
suspend fun createGroup(name: String, creatorUserId: String): Group {
    val code = generateUniqueInviteCode()
    val doc = mapOf(
        "name"       to name,
        "inviteCode" to code,
        "createdBy"  to creatorUserId,
        "memberIds"  to listOf(creatorUserId)
    )
    val ref = firestore.collection(COLLECTION).add(doc).await()
    return Group(
        id         = ref.id,
        name       = name,
        inviteCode = code,
        createdBy  = creatorUserId,
        memberIds  = listOf(creatorUserId)
    )
}

private suspend fun generateUniqueInviteCode(maxAttempts: Int = 5): String {
    repeat(maxAttempts) {
        val candidate = generateInviteCode()
        val collision = firestore.collection(COLLECTION)
            .whereEqualTo("inviteCode", candidate)
            .limit(1)
            .get()
            .await()
        if (collision.isEmpty) return candidate
    }
    throw IllegalStateException("No se pudo generar un código de invitación único tras $maxAttempts intentos")
}
```

**Deliberate scope limit, stated explicitly rather than silently accepted**: this is a check-then-write, not a transaction — there's still a narrow race window if two `createGroup` calls happen to generate the exact same code at the exact same moment. A fully race-proof fix would need a deterministic reservation document (e.g. `inviteCodes/{code}` written inside a Firestore transaction) instead of a query-then-`add()`. That's disproportionate to this app's expected scale (a few dozen users, per the PR-24 freeze decision) and concurrent group-creation is already vanishingly rare (a deliberate, infrequent admin action, not a hot path). The check-and-retry above closes the overwhelmingly common case (sequential creates never collide) and leaves only a negligible simultaneous-creation race — an acceptable, documented trade-off.

### Test cases

**Corrected during implementation**: the plan originally suggested updating `CreateGroupUseCaseTest.kt`, but that file fakes `GroupRepository` directly (`CapturingGroupRepository`), one layer above `FirebaseGroupDataSource` — it never calls through to the data source, so it can't exercise `generateUniqueInviteCode()`'s retry logic at all. Wrote `app/src/test/java/com/softeen/nflocospicks/data/remote/firebase/FirebaseGroupDataSourceTest.kt` instead (new file — the first Firestore-data-source unit test in this project). Mocks `FirebaseFirestore`/`CollectionReference`/`Query`/`QuerySnapshot`/`DocumentReference` with MockK (works out of the box for these final Play Services/Firestore classes, no Robolectric needed) and uses `com.google.android.gms.tasks.Tasks.forResult(...)` to produce completed `Task`s for `.get()`/`.add()`, since `kotlinx.coroutines.tasks.await()` needs a real `Task`, not a mock of one. Three cases: succeeds on the first try when there's no collision; retries once and succeeds when the first candidate collides (`query.get()` stubbed with `returnsMany` to return a non-empty snapshot then an empty one); throws `IllegalStateException` and calls `collection.add(...)` zero times when every one of the 5 attempts collides.

### Done when

`./gradlew test` passes including the new `FirebaseGroupDataSourceTest` (3 cases, all passing as of implementation).

**Status: done.**

---

## Step 9 — Docs Wrap-Up

**Files:** `CLAUDE.md`, `.agents/rules/roadmap-active-3.md`

**Depends on:** all prior steps — this documents what was actually shipped, so do it last (or update incrementally as each step lands, but reconcile once at the end).

- `CLAUDE.md`'s Event Inventory table: `group_joined` row updated to `group_id, group_name, source` (can be done alongside Step 5 instead of held to the end, since that's when the code change lands).
- Add a `### PR-25 — Join Groups via Shareable Link` entry to `CLAUDE.md`'s PR Roadmap section, following the format of PR-16 through PR-24 (branch name, bullet list of what shipped, referencing this plan file), and mirror the roadmap addition into `.agents/rules/roadmap-active-3.md` per the existing convention (that file already carries PR-24's frozen-status note from the same 2026-09-04 session).

### Done when

The roadmap entry accurately reflects what was actually built (write it from the finished diff, not from this plan's aspirations, in case anything changed during implementation).

---

## Testing Summary (cross-reference)

| Test file | Step | New/updated |
|---|---|---|
| `presentation/navigation/JoinLinkParserTest.kt` | 2 | New |
| `presentation/navigation/PostAuthNavActionTest.kt` | 4 | New |
| `presentation/groups/GroupViewModelTest.kt` | 5 | Updated |
| `presentation/common/GroupInviteLinkTest.kt` | 6 | New |
| `data/remote/firebase/FirebaseGroupDataSourceTest.kt` | 8 | New (corrected during implementation — see Step 8's Test cases note; `CreateGroupUseCaseTest.kt` can't exercise this logic) |
| `domain/usecase/JoinGroupUseCaseTest.kt` | 5 | Unchanged — run to confirm no regression |

**Explicitly out of scope for automated coverage**: Compose Navigation / instrumented UI tests (run in GitHub Actions CI, not locally, per project convention). End-to-end Android App Links verification genuinely requires a real/emulated device and cannot be verified by JVM unit tests.

---

## Manual Follow-up Steps (ask-first per Rule 7 — not part of "done" via `./gradlew test`)

1. ~~Enable Firebase Hosting for real~~ — **done.** `firebase deploy --only hosting` completed; both the fallback page and `assetlinks.json` confirmed served live (see Step 1's Status note).
2. ~~Get real certificate fingerprints~~ — **done.** All 3 real SHA256 values (debug, release/upload, Play App Signing) are in the deployed `assetlinks.json`.
3. **Verify the Digital Asset Links statement** independently via Google's Statement List API before spending a device-verification cycle debugging the app side — still worth doing before the device-verification pass (item 4 below), even though the file is confirmed served.
4. **Real-device/emulator verification** (Steps 3, 4, 7):
   ```
   adb shell am start -a android.intent.action.VIEW -d "https://nflocospicks.web.app/join?code=ABC123" com.softeen.nflocospicks
   adb shell pm get-app-links com.softeen.nflocospicks
   ```
   Confirm the app opens directly into a pre-filled `JoinGroupScreen`. **Add these specific scenarios**:
   - A brand-new account, signed up via the invite link, that has to complete `AccountScreen` setup first — confirm it still lands on a prefilled `JoinGroupScreen` afterward.
   - Both `nflocospicks.web.app` and `nflocospicks.firebaseapp.com` show as verified in `pm get-app-links` (Step 1's multi-domain concern).
   - Rotate the screen (or toggle theme) after already consuming a pending invite, while on a screen other than `JoinGroupScreen` — confirm you are *not* redirected back into the join flow (Step 3's regression guard).
   - Arrive via a link, edit the prefilled code to a different valid code, submit, confirm the logged `group_joined` event has `source = "manual_code"` (Step 7 — not covered by a `GroupViewModel` unit test since the comparison logic lives in the screen).
5. ~~Get Play App Signing's SHA256 before the next Hosting deploy~~ — **done during implementation.** Play Console moved this page (now `Protegido con Play → Administrar la firma de apps de Play`, not the old `App integrity` location `CLAUDE.md`'s Release Checklist describes — worth updating that doc separately). The button that reveals the "Clave clásica" SHA-256 is copy-to-clipboard only, never rendered as visible text (unlike the upload-key certificate section on the same page, which does show plaintext) — the user copied and pasted it directly. Added as the third `sha256_cert_fingerprints` entry in `public/.well-known/assetlinks.json`, alongside the still-placeholder debug/release-upload entries: `0D:86:61:3E:FA:89:92:4D:C5:9B:D6:B5:23:7C:E8:F1:0D:E4:24:04:3F:36:F8:25:D4:BB:30:9A:F9:82:CF:0E`. Still pending: the debug and release/upload-key placeholders (via `./gradlew signingReport`) and the actual `firebase deploy --only hosting`.
6. ~~Revisit `public/join/index.html` copy once a real Play Store listing URL exists~~ — **done during implementation**: the real Play Store URL (`https://play.google.com/store/apps/details?id=com.softeen.nflocospicks`) is already wired into the fallback page (Step 1).

---

## Cross-Review Log (per Rule 10)

Both independent reviewers have run against this plan (three passes total, prior to the move into `docs/plans/`); every finding across all three is either incorporated above, resolved as a Decision Already Made, or documented here as evaluated-and-not-applied with a reason.

- `codex exec -s read-only` (round 1): findings incorporated in Steps 1, 2, 4, 7, plus the Testing/Manual sections.
- `agy.exe --mode plan --dangerously-skip-permissions -p ... --model gemini-3.1-pro-high --effort high` (round 1, CLI): 4 findings, incorporated in Steps 2, 3, 7, and the Testing section.
- Second Antigravity pass, run directly by the user (round 2, 7 prioritized findings): four real bugs incorporated (Steps 3, 4, 7 — the spurious re-trigger on config change, the `currentRoute == Groups` gate, the missing `remember` key, the submit-time source attribution). Three evaluated and **not** applied:
  - Parser fragment (`#`) handling / missing URL-decoding — already fixed in the current Step 2 parser (verified directly against the code); the finding described the pre-Codex-fix draft.
  - Multi-domain `autoVerify` interaction risk — genuinely uncertain rather than a confirmed bug; added as a device-verification checklist item (Step 1 / Manual Follow-up #4) instead of a code change.
  - No feedback/redirect for an already-a-member user tapping their own group's link — real but low-priority UX polish; logged as an accepted limitation rather than built out (would need new membership-detection logic, a scope addition not a bugfix).
- "Únete" flagged as a typo by Codex's round-1 review — checked directly against the plan text, already correctly accented; very likely a console/codepage mojibake artifact from how the reviewer's tool rendered the file, not a real defect. No change made.

**Before implementing further**, re-run both reviewers against this file at its new `docs/plans/join-via-link.md` path if picking this plan back up after a significant gap, to catch anything from a codebase drift since 2026-09-04.

### Implementation review (post-Step-9, against the full uncommitted diff, 2026-09-05)

With all 9 steps implemented, ran both reviewers against the actual code diff (not just the plan) per Rule 10 step 5:

- `codex exec review --uncommitted` — 2 findings, both applied: (1) `.claude/` (machine-specific permission grants, including unrestricted `adb`) and `.firebase/` (Firebase CLI's local deploy cache, regenerated every deploy with local timestamps/hashes) were untracked but not gitignored — both added to `.gitignore` so they can never get swept into a commit. Note: the CLI syntax documented in Rule 10 (`codex exec review --uncommitted "<custom prompt>"`) turned out to be invalid in the installed version — `--uncommitted` cannot be combined with a `[PROMPT]` argument (`error: the argument '--uncommitted' cannot be used with '[PROMPT]'`); corrected in `CLAUDE.md`/`.agents/rules/rules.md`/memory to run it plain.
- `agy.exe --mode plan --dangerously-skip-permissions -p ... --model gemini-3.1-pro-high --effort high` — 4 findings, 3 applied, 1 flagged as a separate follow-up (out of PR-25's scope):
  1. **Critical, applied**: `JoinLinkParser.kt`'s `java.net.URLDecoder.decode(...)` call was outside the `runCatching` block that only wrapped `java.net.URI(link)` — a malformed percent-encoding sequence (e.g. `?code=%2`) throws `IllegalArgumentException` uncaught, and since `MainActivity`'s `VIEW` intent-filter is exported, any app could send such an Intent and crash NFLocosPick — a real DoS. Fixed: decode wrapped in its own `runCatching`, decoding happens after extracting the raw param value.
  2. **Applied**: scheme/host comparison in the same parser was case-sensitive (`!=`), but Android's OS-level Intent/App-Link resolution is case-insensitive for scheme and host (path stays case-sensitive) — an Intent the OS already routed here could get silently rejected. Fixed: `.equals(..., ignoreCase = true)` for scheme and host (path comparison intentionally left case-sensitive — correct per URL semantics).
  3. **Applied**: `decidePostAuthNavigation`'s join-link branch condition (`currentRoute != Screen.Login.route && currentRoute != Screen.Account.route`) is vacuously true when `currentRoute` is `null` (possible momentarily before Compose Navigation resolves a current destination), which could navigate to `JoinGroup` before `Groups` is guaranteed to be in the back stack, crashing `getBackStackEntry(Groups)`. Fixed: added an explicit `currentRoute != null` guard.
  4. **Not applied here, flagged as a separate follow-up task**: `handleEmailLinkIntent(intent)` in `MainActivity.onCreate` still runs unconditionally on every recreation, unlike the new `handleJoinLinkIntent` (guarded by `if (savedInstanceState == null)` as part of this PR) — explicitly pre-existing code, unrelated to the join-link feature, so left alone per Rule 1's strict PR boundaries rather than fixed inline.
  5. Also explicitly confirmed clean: `FirebaseGroupDataSource.kt`'s check-and-retry logic — "behaves correctly as a non-transactional best-effort guard... aligns perfectly with the accepted constraints and scale outlined in the plan."

All fixes verified against the actual current code before applying (per Rule 8/10's verify-before-accepting discipline) — none were stale. Added regression-guard unit tests for all 3 applied findings (`JoinLinkParserTest`: malformed percent-encoding, mixed-case scheme/host — now 17 cases; `PostAuthNavActionTest`: null `currentRoute` — now 11 cases). `./gradlew assembleDebug` and `./gradlew test` (full suite) both green after these fixes.
