# Structure

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Repository Root

- `settings.gradle`: single-module project declaration.
- `build.gradle`: Android/Kotlin plugin versions.
- `app/build.gradle`: Android app configuration and dependencies.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: Gradle wrapper.
- `README.md`, `CLAUDE.md`, `docs/`: project notes and role/map documentation.
- `.planning/`: GSD project planning, roadmap, requirements, and codebase map.
- `tmp/`: local temporary files; currently untracked and should not be committed.

## App Source

- `app/src/main/AndroidManifest.xml`: permissions, Activity declarations, orientation, and soft-input mode.
- `app/src/main/java/com/traidores/juego/`: Kotlin source package.
- `app/src/main/res/layout/`: Activity, dialog, and item XML layouts.
- `app/src/main/res/drawable/`: XML drawables plus many webp/png visual resources.
- `app/src/main/res/drawable-nodpi/`: larger bitmap assets that should not be density-scaled.
- `app/src/main/res/raw/`: music and sound effects.
- `app/src/main/res/font/`: custom fonts.

## Kotlin Source Groups

### Entry And Navigation

- `BaseActivity.kt`
- `MainActivity.kt`
- `JugarActivity.kt`
- `LocalModeActivity.kt`
- `OnlineModeActivity.kt`

### Lobby And Match Setup

- `LobbyBrowserActivity.kt`
- `LobbyActivity.kt`
- `AssigningRolesActivity.kt`
- `MapInfo.kt`

### Gameplay

- `GameplayMockActivity.kt`
- `GameEngine.kt`
- `GameModels.kt`
- `LocalBotAi.kt`
- `GameplayTableUi.kt`
- `GameTableLayout.kt`
- `GameplayCountdown.kt`
- `GameplayFeedbackState.kt`

### Gameplay Animation/Presentation

- `VoteResultAnimator.kt`
- `DayNightTransitionAnimator.kt`
- `DeathRevealAnimator.kt`
- `SilenceRevealAnimator.kt`
- `WinnerRevealAnimator.kt`
- `WinnerResultsRenderer.kt`
- `TraitorRevealAnimator.kt`
- `RolePreviewAnimator.kt`
- `JesterVictoryAnimator.kt`

### Roles And Help

- `RoleCatalog.kt`
- `Role.kt`
- `RoleListItem.kt`
- `RoleAdapter.kt`
- `RoleDetailDialog.kt`
- `RolesActivity.kt`
- `AyudaActivity.kt`

### Profile

- `ProfileActivity.kt`
- `ProfileSelectionActivity.kt`
- `ProfileSelectionAdapter.kt`
- `ProfileRoleCatalog.kt`
- `ProfileCustomizationCatalog.kt`

### Audio And Options

- `AudioPreferences.kt`
- `MusicManager.kt`
- `GameplaySoundEffects.kt`
- `GameplayEffects.kt`
- `OpcionesActivity.kt`

## Layout Surface Map

- `activity_gameplay_mock.xml`: main gameplay HUD, chat, event log, vote/tie/result overlays, reveals, and bottom panel.
- `activity_lobby.xml`: landscape lobby screen.
- `activity_lobby_browser.xml`: available lobby list.
- `activity_profile.xml`, `activity_profile_selection.xml`: profile and selectors.
- `activity_opciones.xml`: options.
- `activity_ayuda.xml`: help and rules.
- `activity_roles.xml`, `dialog_role_detail.xml`, `item_role.xml`: role reference UI.
- `activity_assigning_roles.xml`: dealing animation.

## Test Structure

- `app/src/test/java/com/traidores/juego/GameEngineTest.kt`
- `app/src/test/java/com/traidores/juego/GameplayTableUiTest.kt`
- `app/src/test/java/com/traidores/juego/GameTableLayoutTest.kt`
- `app/src/test/java/com/traidores/juego/GameplayCountdownTest.kt`
- `app/src/test/java/com/traidores/juego/GameplayFeedbackStateTest.kt`
- `app/src/test/java/com/traidores/juego/RoleCatalogTest.kt`

## Naming Conventions

- Activity classes use `*Activity`.
- Animator classes use `*Animator`.
- UI/model helpers use descriptive names such as `GameplayTableUi`, `GameTableLayout`, and `WinnerResultsRenderer`.
- Layout files use `activity_`, `dialog_`, and `item_` prefixes.
- Drawable names generally use `bg_`, `ic_`, `rol_`, `mapa_`, `profile_`, and theme-specific suffixes.

## High-Change Areas

- `GameplayMockActivity.kt` and `activity_gameplay_mock.xml` are the highest-change/highest-risk files.
- `GameEngine.kt` is the central rules file and must stay aligned with tests.
- `LobbyActivity.kt` mixes setup logic and programmatic dialog layout.
- `ProfileActivity.kt` owns several local edit/persistence paths.
