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

8. **Plans live in the repo, at `docs/plans/<descriptive-kebab-name>.md` — never in a local Claude directory.** Plan-mode tooling defaults to writing plan files outside the project (e.g. under a user-home `.claude/plans/` directory). Before running Rule 10's cross-review step, move or rewrite the plan into `docs/plans/` inside this repository, and reference that in-repo path in every review command from then on. Reason: Antigravity needs direct filesystem access to read the plan, and a path outside the repo isn't reliably reachable by it the way an in-repo file is. Name the file descriptively (matching existing examples like `docs/plans/global-default-group.md`, `docs/plans/analytics-enrichment.md`), not after the PR number alone.

9. **Plans must be divided into logical, completable, testable, self-sufficient steps — never written as one monolithic block of changes.** Each step in a plan must: (a) be independently buildable and testable — its own `./gradlew assembleDebug`/`./gradlew test` pass, not dependent on a later step to compile or pass; (b) be self-sufficient to resume cold — carry enough context (current state, target state, exact files) that a different agent, or the same agent in a fresh session, can pick up at exactly that step without re-reading or re-deriving the rest of the plan; (c) map to a natural commit checkpoint. Reason: if an agent's session hits a usage limit mid-implementation, only the in-progress step needs re-evaluation — not the entire PR re-planned or re-implemented from scratch.

10. **Cross-review both the plan and the implementation with the independent Antigravity CLI reviewer before calling a feature/bugfix/upgrade done.** For anything beyond a trivial one-line fix, follow this flow end to end:
   1. Define the scope of the feature/bugfix/upgrade.
   2. Draft an implementation plan **in `docs/plans/<descriptive-kebab-name>.md` (Rule 8), divided into logical, self-sufficient steps (Rule 9)** — not a scratch file outside the repo, not one monolithic block.
   3. **Cross-review the plan** with Antigravity before writing code — don't just self-review:
      - Antigravity (AGY): `agy.exe --mode plan --dangerously-skip-permissions -p "Review the plan at <in-repo path>. Look for gaps, risks, missing edge cases, and omissions given this codebase. Do not write code, only report findings." --model gemini-3.1-pro-high --effort high` — binary at `C:\Users\brici\AppData\Local\agy\bin\agy.exe` on this machine; `--mode plan` keeps it read-only, and `--dangerously-skip-permissions` is required for headless (non-interactive) operation since it can't otherwise prompt for tool-call approval — Claude Code's own auto-mode classifier will refuse to run this flag or self-add a permission rule for it, so the user needs to add the allow-rule to `.claude/settings.local.json` once (ask them for it if it's not already there).
      Synthesize AGY's findings against your own judgment and the codebase — don't apply a suggestion just because the reviewer made it; verify it first, and explicitly note in the plan when a finding turns out to already be resolved or not applicable, not just when one gets applied. Update the plan if warranted, and get the user's go-ahead on any resulting scope change before implementing.
   4. Implement the (possibly updated) plan, one step at a time per Rule 9 — build and test each step before moving to the next, so the plan file's own step boundaries stay meaningful as commit checkpoints.
   5. **Cross-review the implementation** the same way, this time against the diff:
      - AGY: `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`
      Apply fixes where warranted, again verifying each finding rather than applying it blindly.
   6. Add/update test coverage (Rule 4).
   7. Sync Gradle only if a Gradle file changed (Rule 3), then run `./gradlew test` — never instrumented/UI tests locally; those run in the GitHub Actions CI pipeline. Fix any failures and re-run.
   8. Once tests pass, the change is ready for a PR. Creating commits and pushing always remains the user's responsibility — never commit or push without being explicitly asked to, every time, regardless of how this workflow went.
