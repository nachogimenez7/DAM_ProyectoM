---
last_mapped: 2026-06-03
focus: concerns
---

# Concerns

## Summary
The project is a functional prototype with strong visual assets, but core gameplay and navigation are still implemented directly inside Activities. The largest risks are maintainability of `GameplayMockActivity.kt`, lack of automated tests, and mocked online/account functionality that can be mistaken for real behavior.

## Gameplay Logic Concentration
- `GameplayMockActivity.kt` contains UI wiring, state transitions, AI choices, voting, winner checks, and rendering.
- This makes local rules harder to test or reuse.
- A future `GameEngine` or reducer class would make state transitions easier to unit test.

## Random Role Assignment
- `LocalGameFactory.assignRoles` uses `roles.shuffled()`.
- This is good for gameplay variety but makes repeatable tests harder.
- A seedable random source or injectable shuffler would help test deterministic scenarios.

## Serializable State Passing
- `GameSession` is passed as `Serializable` through intents.
- This is simple but can become fragile as session state grows.
- Large or complex game state may eventually need a shared repository, parcelable model, or persistence layer.

## Dynamic Resource Lookup
- `getIdentifier` is used in `RolesActivity.kt`, `RoleAdapter.kt`, and `GameplayMockActivity.kt`.
- Misspelled resource names degrade to generic gallery icons.
- Compile-time resource references would catch more mistakes.

## Missing Tests
- There are no unit or instrumentation tests.
- Current gameplay rules are not protected against regression.
- Role assignment, muted players, public/private information separation, and winner checks are high-priority test targets.

## Mocked Online and Account Features
- `OnlineModeActivity.kt` opens mocked gameplay after Toasts.
- `OpcionesActivity.kt` simulates login/register without backend persistence.
- These flows should be clearly marked as mock until real networking/auth exists.

## Permissions and Security
- `INTERNET` is declared in `app/src/main/AndroidManifest.xml` despite no current network code.
- `android:allowBackup="true"` is enabled.
- Neither is necessarily wrong for a prototype, but both should be reviewed before release.

## Music Lifecycle
- `MusicManager.kt` maintains a singleton `MediaPlayer`.
- It pauses after a delay when all screens stop.
- There is no explicit release path for the player.
- Long-running sessions should be monitored for lifecycle or resource leaks.

## Localization
- Many strings are hardcoded in Kotlin/XML.
- `OpcionesActivity.kt` simulates language changes manually.
- Real localization would require moving copy to resource files and using locale-specific `values-*` folders.

## Git/Project Hygiene
- The current working tree includes `.idea` changes and new untracked files.
- Generated IDE state should be reviewed separately from app changes.
- The Gradle wrapper appears incomplete because `gradlew.bat` and wrapper jar were not observed.

