---
last_mapped: 2026-06-03
focus: arch
---

# Architecture

## Summary
The app is a classic Android Activity-based prototype. It uses Kotlin classes as screen controllers, XML layouts for presentation, and a local in-memory `GameSession` model passed between screens. There is no repository layer, persistence layer, service layer, or navigation framework.

## Entry Point
- Launcher activity: `MainActivity.kt`.
- Manifest declaration: `app/src/main/AndroidManifest.xml`.
- `MainActivity` is portrait and exported with `MAIN`/`LAUNCHER`.
- `MainActivity` routes to play, roles, help, and options.

## Screen Flow
- Main menu: `MainActivity.kt` with `activity_main.xml`.
- Mode select: `JugarActivity.kt` with `activity_jugar.xml`.
- Local setup: `LocalModeActivity.kt` with `activity_local_mode.xml`.
- Local lobby: `LobbyActivity.kt` with `activity_lobby.xml`.
- Assigning roles transition: `AssigningRolesActivity.kt` with `activity_assigning_roles.xml`.
- Gameplay: `GameplayMockActivity.kt` with `activity_gameplay_mock.xml`.
- Roles catalog: `RolesActivity.kt`, `RoleAdapter.kt`, `RoleListItem.kt`, `Role.kt`, `MapInfo.kt`.
- Help/options: `AyudaActivity.kt`, `OpcionesActivity.kt`.
- Online mode: `OnlineModeActivity.kt`, currently mocked.

## Orientation Boundary
- Menu and setup screens are portrait in `app/src/main/AndroidManifest.xml`.
- Lobby, assigning roles, and gameplay are landscape.
- This boundary is important: local match flow becomes landscape once the player enters the lobby.

## Game Domain Model
- Domain models live in `GameModels.kt`.
- `GameSession` stores map, players, phase, round, night actions, investigation data, votes, public/private messaging, history, and winner.
- `GamePlayer` stores name, initial, role, alive/muted state, and whether the player is human.
- `GameRole` stores key, display name, team, and drawable resource name.
- `GamePhase` enumerates local match phases.
- `GameMap` maps a map key to display name, image resource, and role image suffix.

## Local Game State Flow
- `LocalGameFactory.createSession()` creates a deterministic player list and default map.
- `LobbyActivity` can select map, add/remove mock players, and expel participants.
- `LocalGameFactory.assignRoles(session)` creates one police role, one assassin, one medic, and aldeanos, then shuffles them.
- `GameplayMockActivity` resolves the local state machine from `GamePhase.REPARTO` through night, dawn, day debate, voting, and result.

## UI State Pattern
- Screen state is managed directly in each Activity.
- Views are stored as Activity fields only where needed, for example `GameplayMockActivity`.
- UI updates are manual through `TextView.text`, `ImageView.setImageResource`, and visibility changes.
- There is no ViewModel, LiveData, Flow, or lifecycle-aware state holder.

## Shared Behavior
- `BaseActivity.kt` centralizes music lifecycle behavior for all screens.
- Every Activity extends `BaseActivity`, so music behavior is applied globally.
- `MusicManager.kt` is a singleton object controlling one `MediaPlayer`.

## Roles Catalog Architecture
- `RolesActivity.kt` builds map-specific role lists in code.
- `RoleAdapter.kt` renders map, section, and role card item types.
- Role descriptions and map stories are hardcoded in Kotlin maps inside `RolesActivity.kt`.
- Role images are chosen through naming convention suffixes.

## Data Flow Concerns
- `Serializable` intent extras carry full game session state between activities.
- Once `GameplayMockActivity` starts, state is in-memory only and not persisted across process death.
- Orientation is fixed for gameplay, reducing configuration change churn but not eliminating lifecycle risk.

