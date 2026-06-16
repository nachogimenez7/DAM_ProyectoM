# Conventions

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Kotlin Style

- Kotlin official style is enabled in `gradle.properties`.
- Code uses a pragmatic Android Activity style rather than strict layered architecture.
- Immutable session updates are preferred in engine/model paths through `copy()`.
- UI code commonly uses `findViewById`, direct listeners, and imperative rendering.
- Many local helper methods are private inside large Activity classes.

## State Management

- `GameSession` is the main state container for local match rules.
- `GameplayMockActivity` stores UI/transient state with many booleans for overlays and flow locks.
- Countdown state is delegated to `GameplayCountdown`.
- Feedback queue/state is delegated to `GameplayFeedbackState`.
- Profile and options state rely on `SharedPreferences`.

## Rendering Pattern

- Activities initialize views in `onCreate`.
- Main gameplay rerendering flows through `renderGame()` and specialized rendering methods.
- Blocking overlays pause countdown/music and resume through callback methods.
- Many UI strings are still hardcoded in Kotlin/XML instead of `strings.xml`.
- Autosizing is used in some recently touched text/buttons, but not consistently across the app.

## Game Rule Pattern

- `GameEngine.kt` owns phase transitions, human target validation, timeout resolution, voting, tie rules, and winner progression.
- `LocalBotAi.kt` owns bot chat/vote heuristics and debug/testing behavior.
- UI should ask `GameEngine` for action legality instead of reimplementing rules.
- Tests should be added or updated when changing `GameEngine.kt`, `GameplayTableUi.kt`, or role catalogs.

## Resource Pattern

- Historical/role/map images are resource-based and selected through catalogs or theme keys.
- Large assets should use `drawable-nodpi` when density scaling would be undesirable.
- Small UI shapes remain XML drawables.
- One-off generated assets should be committed only when referenced by the app.
- Temporary generation files should stay in `tmp/` and remain untracked.

## Audio Pattern

- Background music goes through `MusicManager`.
- Short effects go through `GameplaySoundEffects` or animator-specific `MediaPlayer` handling.
- Audio preference checks should respect `AudioPreferences`.
- New effects should avoid duplicating preference logic in each caller.

## Navigation Pattern

- Navigation is manual with `Intent`.
- Several Activities call `finish()` to keep the back stack shallow.
- Gameplay back behavior must prioritize transient UI before leaving the match.
- There is no centralized navigation contract yet.

## Documentation Pattern

- GSD planning lives in `.planning/`.
- Product and stabilization scope are defined in `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, and `.planning/ROADMAP.md`.
- Codebase maps should be refreshed after major gameplay/profile/lobby changes.

## Editing Guidelines For This Repo

- Keep fixes local to gameplay/lobby/profile/chat unless a shared helper is clearly needed.
- Avoid broad package reorganization during stabilization.
- Prefer small XML/Kotlin corrections with manual visual validation.
- Keep `tmp/` out of commits.
- Use Android Studio/device screenshots as the source of truth for visual acceptance.
