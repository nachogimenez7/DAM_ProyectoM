---
last_mapped: 2026-06-03
focus: concerns
scope: Android app source, Gradle config, manifest, unit tests
---

# Concerns

## Summary
The app has moved past a pure mock: `GameEngine` and `GameEngineTest` now cover core local-game rules. The remaining risks are concentrated around prototype infrastructure, Activity-heavy runtime flow, simulated online/account features, and release/security defaults.

## High Priority

### Prototype online and account flows
- `OnlineModeActivity.kt` still routes quick play, lobby search, and lobby creation into `GameplayMockActivity` with Toasts that explicitly describe mock behavior.
- `LocalModeActivity.kt` accepts a mock join code and creates local sessions without validation or persistence.
- `OpcionesActivity.kt` simulates login/register messages locally; there is no backend, credential persistence, or authenticated online state.
- Risk: users can mistake UI affordances for real multiplayer/account support.

### Activity-owned game runtime
- `GameplayMockActivity.kt` still owns rendering, target selection, chat UI, timers, auto-advance scheduling, and session mutation around the extracted `GameEngine`.
- Handler-based auto-advance is lifecycle-sensitive and only in-memory; leaving/recreating the Activity can reset or lose game state.
- Risk: UI regressions or lifecycle events can affect game progression even when engine rules are correct.

### Intent-passed Serializable session state
- `GameSession`, players, roles, chat messages, and phases implement `Serializable`; Activities pass sessions with deprecated `getSerializableExtra`.
- This is acceptable for a small prototype, but the session now contains growing lists and gameplay history.
- Risk: fragile navigation contracts, possible performance cost, and no durable recovery after process death.

## Medium Priority

### Release and security defaults need review
- `AndroidManifest.xml` declares `INTERNET` while current app behavior is still local/mock.
- `android:allowBackup="true"` is enabled.
- `app/build.gradle` has release `minifyEnabled false`.
- Risk: release builds may expose unnecessary platform surface or ship without shrink/obfuscation review.

### Test coverage is useful but narrow
- `GameEngineTest.kt` covers role assignment, night actions, voting, chat permissions, mute behavior, and win conditions.
- Coverage does not yet exercise Activity navigation, lifecycle/timer behavior, XML layout rendering, account/options behavior, or online-mode handoffs.
- Verification in this environment is blocked because `JAVA_HOME` is not set and `java` is missing from `PATH`.

### Random role assignment is nondeterministic
- `LocalGameFactory.assignRoles` uses `roles.shuffled()` directly.
- Existing tests validate role counts but cannot reproduce a specific assignment order without controlling randomness.
- Risk: future tests for role-specific UI and private information may become flaky or require brittle workarounds.

## Lower Priority

### Dynamic drawable lookup
- Role/map images still use resource-name strings and `resources.getIdentifier` in role-related UI paths.
- Missing or renamed assets silently fall back to generic gallery icons in several places.
- Risk: asset regressions are detected late instead of at compile time.

### Hardcoded copy and manual language handling
- Gameplay, lobby, options, and navigation strings are largely hardcoded in Kotlin/XML.
- `OpcionesActivity.kt` manually changes visible labels instead of using Android locale resources.
- Risk: localization will require broad UI rewrites and manual copy synchronization.

### Shared music singleton lifecycle
- `MusicManager.kt` owns a singleton `MediaPlayer` and delayed pause behavior.
- There is no explicit release path for long idle sessions or app shutdown.
- Risk: low for prototype, but worth monitoring before release or broader device testing.

## Watchlist
- `GameEngine.humanPlayer` falls back to `session.players.first()`, so empty sessions can crash if a future flow creates one.
- `GameSession.phaseIndex` exists but is not part of observed phase progression.
- The working tree contains many modified/untracked IDE, wrapper, screenshot, layout, and Kotlin files; treat source-control cleanup separately from app concerns.
