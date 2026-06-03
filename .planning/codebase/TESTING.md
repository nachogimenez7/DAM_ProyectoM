---
last_mapped: 2026-06-03
focus: quality
---

# Testing

## Summary
No automated test framework or test source set is currently configured beyond default Android build support. Validation has been done manually and through `assembleDebug`.

## Test Directories
- No `app/src/test` directory was observed.
- No `app/src/androidTest` directory was observed.
- No unit test files were found under `app/src/main/java`.

## Test Dependencies
- `app/build.gradle` has no `testImplementation` dependencies.
- `app/build.gradle` has no `androidTestImplementation` dependencies.
- No JUnit, Espresso, Mockito, Robolectric, or Compose test dependencies are configured.

## Current Verification Command
- Debug build command used in this environment:
  - Set `JAVA_HOME` to Android Studio JBR.
  - Run cached Gradle 8.5 `gradle.bat assembleDebug`.
- Build output confirms compile, resource processing, dexing, and APK packaging.
- APK output path: `app/build/outputs/apk/debug/app-debug.apk`.

## Manual Test Scenarios
- Main menu opens and routes to play, roles, help, and options.
- Sound toggle updates `TraidoresPrefs.sound_on` and `MusicManager`.
- Local flow:
  - `MainActivity` -> `JugarActivity` -> `LocalModeActivity` -> `LobbyActivity`
  - Choose map in lobby.
  - Add/remove mock players.
  - Expel non-host participant.
  - Start role assignment.
  - Transition through `AssigningRolesActivity` to `GameplayMockActivity`.
- Gameplay:
  - Reveal/hide human card.
  - Resolve AI phases.
  - Select valid active targets.
  - Handle muted/dead players.
  - Advance dawn/day/voting/result.
  - Detect Pueblo or Traidores winner.

## High-Value Future Unit Tests
- `LocalGameFactory.assignRoles` should always assign exactly one assassin, one police role, one medic, and remaining aldeanos.
- `assignRoles` should preserve one human player and reset `alive/muted` flags.
- `removePlayer` should not remove host index `0`.
- `addMockPlayer` should not duplicate player names.
- Game winner logic should handle zero assassins, assassin parity, and ongoing states.

## High-Value Future Instrumented Tests
- Activity navigation from menu to local game.
- Lobby map selector updates displayed map and session map key.
- Gameplay blocks selection of muted players.
- Public announcement does not reveal protected player or vote breakdown.
- Police private hint is visible only in the lower HUD text.

## Risks Without Tests
- Gameplay state machine lives in Activity code, making it hard to unit test directly.
- Random role assignment can make manual verification inconsistent unless seedable logic is introduced.
- Dynamic resource lookup failures may only appear during manual runtime tests.

