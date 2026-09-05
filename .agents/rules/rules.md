# Rules — NFLocosPick

Mirrors CLAUDE.md's "Rules" section verbatim. These apply to every change made in this repository. There are no exceptions unless a rule explicitly says so. Source of truth is CLAUDE.md; update both together.

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
