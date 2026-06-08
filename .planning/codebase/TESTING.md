---
last_mapped: 2026-06-03
focus: quality
---

# Testing

## Summary
The project now has a JVM unit-test source set with JUnit 4 coverage for the local game engine. There are still no instrumented UI tests, so Activity navigation, XML rendering, media lifecycle, and device-specific behavior remain manually verified.

## Test Setup
- Unit tests live in `app/src/test/java/com/traidores/juego`.
- `app/build.gradle` includes `testImplementation 'junit:junit:4.13.2'`.
- No `app/src/androidTest` directory was observed.
- No Espresso, Robolectric, Mockito, Compose test, or AndroidX test dependencies are configured.
- The Gradle wrapper is present, so the normal local command is `.\gradlew.bat test`.

## Existing Coverage
- `GameEngineTest.kt` contains 17 JUnit tests.
- Current tests cover role assignment, assassin kills, medic protection, private police hints, muted-player restrictions, bot debate privacy, auto-advance gating, target action labels, voting resolution, chat permissions, and win conditions.
- Tests exercise pure Kotlin/domain behavior through `GameEngine`, `LocalGameFactory`, and `GameSession` fixtures.

## Current Verification Commands
- Run JVM unit tests:
  - `.\gradlew.bat test`
- Build a debug APK:
  - `.\gradlew.bat assembleDebug`
- Useful output paths:
  - Unit test reports: `app/build/reports/tests/testDebugUnitTest/index.html`
  - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Manual Scenarios Still Needed
- Main menu routes to play, roles, help, options, and back navigation.
- Sound toggle and options sliders update `TraidoresPrefs` and affect `MusicManager`.
- Local flow from `MainActivity` to lobby to role assignment to gameplay.
- Lobby map selector updates visible map, selected map key, and role image suffix.
- Add/remove mock players, including host removal protection and min/max player limits.
- Gameplay renders readable player seats across 5 to 15 players.
- Chat panel opens/closes, respects muted/read-only states, and does not overlap critical gameplay controls.
- Role card reveal/hide behavior works across phase transitions.

## High-Value Next Tests
- Add unit tests for `LocalGameFactory.selectMap`, `addMockPlayer`, `removeLastPlayer`, and `removePlayer`.
- Add deterministic role-assignment testing by injecting or controlling shuffle behavior if rules become more complex.
- Add instrumented tests for Activity navigation and lobby/gameplay smoke flows.
- Add UI assertions for chat visibility, card action buttons, and muted-player controls.
- Add regression tests around dynamic drawable names for each map/role suffix.

## Known Gaps
- Activity code, XML layout behavior, timers, Toast messages, and `SharedPreferences` integration are not covered by automated tests.
- Media playback lifecycle in `MusicManager` is untested.
- No coverage report or minimum coverage threshold is configured.
- No CI workflow was observed in the workspace.
