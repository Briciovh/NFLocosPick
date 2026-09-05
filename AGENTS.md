# AGENTS.md

This file provides guidance to any AGENTS.md-compatible coding agent (Antigravity CLI, Codex, Cursor, etc.) working in this repository.

**`CLAUDE.md` is the source of truth for this project.** It is a superset of everything below and is kept current first; this file and `.agents/rules/*.md` are mirrors of it, split up because most agent tools cap a single rules file at ~12,000 characters (`CLAUDE.md` itself is ~43,000). If anything here conflicts with `CLAUDE.md`, `CLAUDE.md` wins — flag the conflict rather than silently picking one.

**NFLocosPick** is a private-group NFL pick'em Android app (Kotlin, Jetpack Compose, Material 3, Firebase, Hilt, Retrofit, ESPN unofficial API). See `.agents/rules/architecture.md` for the full stack, build/test commands, Clean Architecture layers, package structure, and Firestore data model.

## Read these before making any change

- [`.agents/rules/rules.md`](.agents/rules/rules.md) — **binding rules, read first.** Strict PR boundaries, never downgrade a dependency, build+test before every commit, mandatory tests, neutral Mexican Spanish (tuteo, never voseo), deploy `firestore.rules`/`storage.rules` immediately after editing them, never run the app/emulator without explicit authorization.
- [`.agents/rules/architecture.md`](.agents/rules/architecture.md) — project overview, stack, build & test commands, Clean Architecture layers, package structure, team-theming system, Firestore data model, ESPN API.
- [`.agents/rules/roadmap-shipped.md`](.agents/rules/roadmap-shipped.md) — PR Roadmap, PR-1 through PR-13 (already merged; historical context for *why* the code looks the way it does).
- [`.agents/rules/roadmap-active-1.md`](.agents/rules/roadmap-active-1.md) — PR Roadmap, PR-14 through PR-17.
- [`.agents/rules/roadmap-active-2.md`](.agents/rules/roadmap-active-2.md) — PR Roadmap, PR-18 through PR-23.
- [`.agents/rules/roadmap-active-3.md`](.agents/rules/roadmap-active-3.md) — PR Roadmap, PR-24 (frozen indefinitely, not implemented) and PR-25 (join groups via shareable link — most recent).
- [`.agents/rules/constraints-analytics.md`](.agents/rules/constraints-analytics.md) — key constraints (minSdk, disabled dynamic color, DataStore vs Firestore, etc.) and the full Analytics event inventory.
- [`.agents/rules/release-and-compat.md`](.agents/rules/release-and-compat.md) — Play App Signing / release checklist and AGP 9 dependency compatibility notes.

Note: there is no separate `Roadmap.md` file — the roadmap lives inside `CLAUDE.md` (and is mirrored in the `roadmap-*.md` files above).

## Before proposing or making a change

1. Identify which PR (if any) the request belongs to per the roadmap files above, and stay strictly inside that PR's scope (Rule 1 in `rules.md`).
2. Check `constraints-analytics.md` and `release-and-compat.md` for any constraint that applies to the files you're touching.
3. After the change: build, run tests, and if `firestore.rules`/`storage.rules` changed, deploy them immediately (Rules 3, 4, 6 in `rules.md`).
