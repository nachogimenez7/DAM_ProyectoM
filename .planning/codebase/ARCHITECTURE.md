# Architecture

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Overall Shape

- Native Android single-module app.
- Package is mostly flat: `app/src/main/java/com/traidores/juego/`.
- UI is Activity/XML driven, with helper classes for renderers, animators, catalogs, and local engine logic.
- The app is not using MVVM, Navigation Component, Compose, dependency injection, coroutines, Room, Retrofit, or repositories.

## Main Layers

### UI Activities

- `MainActivity.kt`: initial menu and audio quick controls.
- `JugarActivity.kt`, `LocalModeActivity.kt`, `OnlineModeActivity.kt`: mode selection.
- `LobbyBrowserActivity.kt`, `LobbyActivity.kt`: simulated lobby browsing and local lobby configuration.
- `AssigningRolesActivity.kt`: role assignment/card-dealing transition.
- `GameplayMockActivity.kt`: main local gameplay screen and orchestration.
- `RolesActivity.kt`, `AyudaActivity.kt`, `OpcionesActivity.kt`: reference/help/options surfaces.
- `ProfileActivity.kt`, `ProfileSelectionActivity.kt`: local profile and selectors.

### Game Domain

- `GameModels.kt`: `GameSession`, `GamePlayer`, roles, phases, map/theme data, timing, chat, and result fields.
- `GameEngine.kt`: pure-ish local rules for night actions, debate, voting, tie voting, alcalde handling, role actions, results, timeouts, and win progression.
- `LocalBotAi.kt`: local bot voting/chat heuristics and debug vote behavior.
- `RoleCatalog.kt`, `ProfileRoleCatalog.kt`, `ProfileCustomizationCatalog.kt`: role/profile data catalogs.

### Presentation Helpers

- `GameplayTableUi.kt`: phase text, public events, feedback, and gameplay-facing copy/state.
- `GameTableLayout.kt`: table seat placement helpers.
- `WinnerResultsRenderer.kt`: final result list/rendering.
- `RoleDetailDialog.kt`, adapters, and item models for list/detail surfaces.

### Animation And Audio

- `VoteResultAnimator.kt`: vote recount, tie result, expulsion, no-expulsion presentation, boot animation.
- `DayNightTransitionAnimator.kt`, `DeathRevealAnimator.kt`, `SilenceRevealAnimator.kt`, `WinnerRevealAnimator.kt`, `TraitorRevealAnimator.kt`, `RolePreviewAnimator.kt`, `JesterVictoryAnimator.kt`: blocking or transitional overlays.
- `MusicManager.kt`, `GameplaySoundEffects.kt`, `GameplayEffects.kt`, `AudioPreferences.kt`: sound/music/vibration/preferences.

## Gameplay Data Flow

1. Lobby/options build a local `GameSession`.
2. `AssigningRolesActivity` presents role assignment and opens `GameplayMockActivity`.
3. `GameplayMockActivity` owns the current `GameSession` instance.
4. Human actions call `GameEngine.resolveHumanTargetAction()` or phase-specific engine methods.
5. Bot actions are resolved by `GameEngine` and `LocalBotAi`.
6. UI state is rerendered from the updated session through `renderGame()` and specialized overlay methods.
7. Blocking overlays pause countdown/music and call back into `resumeGameFlowAfterBlockingUi()`.

## Navigation Pattern

- Navigation is manual through `Intent`.
- Back behavior is Activity-specific and handled inside each screen.
- Gameplay has many transient states: role preview, chat, event log, vote result, tie vote, reveals, winner/jester/oracle overlays, and day-night transitions.
- There is no central route graph or navigation test harness.

## Current Architectural Strengths

- `GameEngine.kt` centralizes most local match rules instead of spreading rule decisions across XML/UI.
- Tests exist for engine and table UI behavior.
- Animations are increasingly extracted away from `GameplayMockActivity`.
- Role/catalog data is separated from many Activity details.
- Audio preferences have a dedicated helper.

## Current Architectural Risks

- `GameplayMockActivity.kt` is very large at about 3,479 lines and remains the highest-risk coordination point.
- `activity_gameplay_mock.xml` is also very large at about 1,946 lines.
- The package is flat, so ownership boundaries are implicit.
- UI state and game state are often coordinated manually through booleans.
- Programmatic lobby dialogs and fixed dimensions create visual fragility on compact portrait screens.
- There is no instrumentation layer to verify Activity recreation, keyboard, and navigation behavior.

## Recommended Boundary For Next Work

- Keep bug fixes scoped to existing surfaces.
- Do not introduce Firebase/online architecture during stabilization.
- Prefer extracting small helpers only when they reduce risk in `GameplayMockActivity.kt` or `LobbyActivity.kt`.
- Treat `GameEngine.kt` as the source of truth for local rules.
- Treat `GameplayTableUi.kt` as the copy/state adapter for gameplay presentation.
