# Architecture

**Analysis Date:** 2026-06-13

## Pattern Overview

**Overall:** Single-module Android application with Activity-driven navigation, XML views, in-memory game state, and extracted pure game/presentation helpers.

**Key Characteristics:**
- One Android `:app` module and one package, `com.traidores.juego`.
- Explicit `Intent` navigation between Activities rather than Navigation Component routes.
- Portrait menu/reference flow and landscape game flow are separated by manifest orientation locks.
- Local game simulation passes a serializable `GameSession` through Activity extras.
- Business rules have meaningful test coverage; visual and navigation behavior does not.

## Layers

**Activity/UI Layer:**
- Purpose: Inflate XML, bind views, handle clicks, show dialogs, and render state.
- Contains: `MainActivity.kt`, `LobbyActivity.kt`, `GameplayMockActivity.kt`, `ProfileActivity.kt`, and other Activities.
- Depends on: Android framework, resource files, game/presentation helpers.
- Risk: `GameplayMockActivity.kt` and `LobbyActivity.kt` combine navigation, rendering, animation, timers, and state mutation.

**Presentation Helper Layer:**
- Purpose: Convert session state into UI-friendly data and isolate animations.
- Contains: `GameplayTableUi.kt`, `GameTableLayout.kt`, `WinnerResultsRenderer.kt`, and the `*Animator.kt` classes.
- Depends on: Models and some Android view APIs.
- Used by: Gameplay and role/result presentation.

**Domain Layer:**
- Purpose: Model players, phases, actions, roles, and resolve game rules.
- Contains: `GameModels.kt`, `GameEngine.kt`, `RoleCatalog.kt`, and `LocalBotAi.kt`.
- Depends on: Kotlin/JVM and Android resource identifiers in the local factory/catalog.
- Used by: Lobby, role assignment, gameplay, and tests.

**Persistence/Platform Services:**
- Purpose: Retain user options and coordinate audio/effects.
- Contains: `MusicManager.kt`, `GameplayEffects.kt`, and direct `SharedPreferences` calls.
- Depends on: Android context and packaged resources.

## Data Flow

**Menu to Gameplay:**
1. `MainActivity.kt` opens `JugarActivity.kt`.
2. The user selects local or simulated online mode.
3. `LocalGameFactory` creates a `GameSession`.
4. `LobbyActivity.kt` modifies players, map, and configuration.
5. `AssigningRolesActivity.kt` displays role-reading state and starts gameplay.
6. `GameplayMockActivity.kt` resolves phases through `GameEngine.kt`.
7. The final overlay uses `GameplayTableUi.winnerPresentation()` and `WinnerResultsRenderer.kt`.

**Profile Editing:**
1. `ProfileActivity.kt` loads a saved profile from `SharedPreferences`.
2. It maintains a mutable draft while editing.
3. `ProfileSelectionActivity.kt` returns avatar/banner/role choices through Activity results.
4. Save writes preferences; cancel restores the in-memory saved profile.

**State Management:**
- `GameSession` is immutable data copied after actions.
- Activity-level flags track overlays, transitions, chat state, and animations.
- Gameplay state is saved in `onSaveInstanceState`; lobby/profile state preservation is less complete.
- User preferences are local and synchronous through `SharedPreferences`.

## Key Abstractions

**GameSession:**
- Purpose: Complete local match state.
- Location: `app/src/main/java/com/traidores/juego/GameModels.kt`.
- Pattern: Serializable immutable data object passed through Intents and Bundles.

**GameEngine:**
- Purpose: Authoritative local phase and action resolver.
- Location: `app/src/main/java/com/traidores/juego/GameEngine.kt`.
- Pattern: Stateless object with pure or copy-based transformations.

**RoleCatalog:**
- Purpose: Central role names, teams, map availability, images, descriptions, and requirements.
- Location: `app/src/main/java/com/traidores/juego/RoleCatalog.kt`.

**Presentation Coordinators:**
- Purpose: Keep complex animation and result rendering outside the main Activity.
- Examples: `DayNightTransitionAnimator.kt`, `DeathRevealAnimator.kt`, `WinnerRevealAnimator.kt`.

## Entry Points

**Application Entry:**
- Location: `app/src/main/java/com/traidores/juego/MainActivity.kt`.
- Trigger: Launcher intent declared in `app/src/main/AndroidManifest.xml`.
- Responsibilities: Main navigation, sound toggle, and profile entry.

**Game Entry:**
- Location: `app/src/main/java/com/traidores/juego/LobbyActivity.kt`.
- Trigger: Local or simulated online mode.
- Responsibilities: Session configuration and transition to role assignment.

**Gameplay Entry:**
- Location: `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt`.
- Trigger: Assigned session from `AssigningRolesActivity.kt`.
- Responsibilities: Full game table rendering and interaction lifecycle.

## Error Handling

**Strategy:** Guard invalid user actions and show a `Toast`; use fallback data/resource values when possible.

**Patterns:**
- Guard clauses prevent invalid targets or phases.
- Dialogs confirm destructive profile/lobby actions.
- Resource lookup uses `resources.getIdentifier()` with fallback placeholders.
- There is no global exception handler, error screen, or structured diagnostic logging.

## Cross-Cutting Concerns

**Navigation:**
- Direct Activity Intents and `finish()` calls.
- Back behavior is implemented independently per Activity.
- Gameplay intercepts back presses for overlays and expanded panels before allowing Activity exit.

**Validation:**
- Domain actions are validated in `GameEngine`.
- Profile text is validated in `ProfileActivity`.
- Layout fit and route continuity are not automatically validated.

**Audio:**
- `BaseActivity.kt` reports Activity lifecycle to `MusicManager.kt`.
- Gameplay transition coordinators pause/resume phase music.

---
*Architecture analysis: 2026-06-13*
*Update when navigation or state ownership changes*
