---
last_mapped: 2026-06-03
focus: arch
---

# Architecture

## Summary
`TraidoresMenu` is a single-module Android app built as an Activity/XML prototype. The current shape is still screen-driven, but local match rules have been separated into a small Kotlin domain layer: Activities own Android lifecycle, navigation, and rendering, while `GameEngine.kt` resolves game phases and `GameModels.kt` holds immutable serializable state.

## Runtime Pattern
- Android application module: `app`.
- Package and namespace: `com.traidores.juego`.
- UI controllers are `AppCompatActivity` subclasses under `app/src/main/java/com/traidores/juego`.
- Layouts and visual assets are XML/PNG resources under `app/src/main/res`.
- There is no Compose, ViewModel, repository, Room database, dependency injection, or navigation component.

## Entry Points
- Launcher screen: `app/src/main/java/com/traidores/juego/MainActivity.kt`.
- Activity registry and orientation policy: `app/src/main/AndroidManifest.xml`.
- Main menu routes to play mode selection, roles catalog, help, and options.
- All Activities extend `app/src/main/java/com/traidores/juego/BaseActivity.kt`, which delegates global music lifecycle to `MusicManager.kt`.

## Screen Flow
- `MainActivity.kt` -> `JugarActivity.kt` for play mode selection.
- `JugarActivity.kt` -> `LocalModeActivity.kt` or `OnlineModeActivity.kt`.
- Local path: `LocalModeActivity.kt` creates or joins a mock local session, then opens `LobbyActivity.kt`.
- Lobby path: `LobbyActivity.kt` selects map, changes mock player count, assigns roles, then opens `AssigningRolesActivity.kt`.
- Transition path: `AssigningRolesActivity.kt` waits briefly with a `Handler`, then opens `GameplayMockActivity.kt`.
- Gameplay path: `GameplayMockActivity.kt` renders the table, role card, chat, phase status, and player actions.
- Reference/catalog path: `RolesActivity.kt` uses `RoleAdapter.kt` plus role/map data classes to render map-specific role descriptions.

## Domain Layer
- `app/src/main/java/com/traidores/juego/GameModels.kt` defines `GameSession`, `GamePlayer`, `GameRole`, `GameChatMessage`, `GamePhase`, `GameMap`, and `LocalGameFactory`.
- `LocalGameFactory` owns demo setup: maps, default players, player limits, map selection, player removal, and role assignment.
- `app/src/main/java/com/traidores/juego/GameEngine.kt` owns rule transitions: night start, assassin/police/medic resolution, dawn, debate, voting, results, chat permissions, auto-advance timing, and winner checks.
- The engine mostly returns copied `GameSession` values rather than mutating Android objects, which keeps rule behavior testable outside Activities.

## UI State And Data Flow
- `GameSession` is passed between screens as a `Serializable` extra using `LobbyActivity.EXTRA_SESSION`.
- `GameplayMockActivity` keeps the active session in a private field, calls `GameEngine`, then re-renders all relevant views manually.
- Rendering uses imperative Android APIs: `findViewById`, `TextView.text`, `ImageView.setImageResource`, visibility, alpha, and view animations.
- Player seats in gameplay are generated programmatically inside `GameplayMockActivity` instead of inflated from a repeated XML row.
- Chat history, public history, private hints, votes, and winner state are stored in `GameSession`; there is no persistence after process death.

## Resource Architecture
- Screen layout XML files live in `app/src/main/res/layout/activity_*.xml`.
- Repeated rows/cards use `item_*.xml`; role detail uses `dialog_role_detail.xml`.
- UI backgrounds and selectors are drawable XML resources such as `bg_btn_gold.xml`, `bg_game_panel.xml`, `bg_chat_panel.xml`, and `bg_player_seat.xml`.
- Map and role art lives in `app/src/main/res/drawable`, with source/reference art also present in top-level `roles_gauchos`, `roles_griegos`, and `roles_medievales`.
- Role images are resolved by name convention, for example `rol_asesino_gaucho` or `rol_medico_medieval`.

## Orientation Boundary
- Menu, setup, roles, help, options, and online screens are portrait.
- Lobby, assigning roles, and gameplay are landscape.
- This makes the match flow a clear presentation boundary, but state still depends on Activity memory and serialized extras.

## Testing Boundary
- `app/src/test/java/com/traidores/juego/GameEngineTest.kt` tests the engine/domain layer with plain JUnit.
- Coverage focuses on role assignment, night actions, voting, chat permissions, auto-advance behavior, target action labels, and win conditions.
- There are no Android instrumentation tests or UI interaction tests in the observed tree.

## Architectural Constraints
- Navigation is manual `Intent` chaining, so back stack/session behavior is encoded per Activity.
- `Serializable` extras are acceptable for a prototype but are fragile for larger sessions and Android lifecycle recovery.
- `GameEngine` is testable, but role catalogs and many text strings remain hardcoded in Activities.
- The manifest requests `INTERNET`, but the online flow is currently mocked and does not define a network/service boundary.
