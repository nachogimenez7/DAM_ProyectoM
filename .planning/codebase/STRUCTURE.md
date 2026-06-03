---
last_mapped: 2026-06-03
focus: arch
---

# Structure

## Summary
The repository is a small Android app with one Gradle module, app code under `app/src/main/java/com/traidores/juego`, and all UI/resources under `app/src/main/res`. There are also top-level role art folders outside the Android resource tree.

## Root Layout
- `settings.gradle` defines root project `TraidoresMenu` and includes `:app`.
- `build.gradle` defines Android and Kotlin plugin versions.
- `app/build.gradle` defines module configuration and dependencies.
- `README.md` describes the project as a Traidores menu/navigation prototype.
- `gradle/wrapper/gradle-wrapper.properties` exists, but wrapper scripts/jar were not observed.
- Top-level folders `roles_gauchos`, `roles_griegos`, and `roles_medievales` contain role art source assets outside `app/src/main/res`.

## Kotlin Source Layout
- Source package root: `app/src/main/java/com/traidores/juego`.
- Activities:
  - `MainActivity.kt`
  - `JugarActivity.kt`
  - `LocalModeActivity.kt`
  - `LobbyActivity.kt`
  - `AssigningRolesActivity.kt`
  - `GameplayMockActivity.kt`
  - `OnlineModeActivity.kt`
  - `RolesActivity.kt`
  - `AyudaActivity.kt`
  - `OpcionesActivity.kt`
- Shared classes:
  - `BaseActivity.kt`
  - `MusicManager.kt`
  - `GameModels.kt`
  - `Role.kt`
  - `RoleAdapter.kt`
  - `RoleListItem.kt`
  - `MapInfo.kt`

## Layout Resources
- Main menu: `app/src/main/res/layout/activity_main.xml`
- Play mode selection: `app/src/main/res/layout/activity_jugar.xml`
- Local mode: `app/src/main/res/layout/activity_local_mode.xml`
- Online mode: `app/src/main/res/layout/activity_online_mode.xml`
- Lobby: `app/src/main/res/layout/activity_lobby.xml`
- Assigning roles: `app/src/main/res/layout/activity_assigning_roles.xml`
- Gameplay: `app/src/main/res/layout/activity_gameplay_mock.xml`
- Roles catalog: `app/src/main/res/layout/activity_roles.xml`, `item_role.xml`, `item_map.xml`, `item_section_header.xml`, `dialog_role_detail.xml`
- Gameplay side chips: `item_mock_player_left.xml`, `item_mock_player_right.xml`
- Lobby player row: `item_lobby_player.xml`

## Drawable Resources
- Shared UI backgrounds:
  - `bg_btn_dark.xml`
  - `bg_btn_gold.xml`
  - `bg_game_panel.xml`
  - `bg_player_chip.xml`
  - `bg_player_avatar.xml`
  - `bg_role_card.xml`
  - `bg_card_back.xml`
- Menu and mode images:
  - `fondo_menu.png`
  - `logo_traidores.png`
  - `logo_traidores_transparente.png`
  - `modo_juego_local.png`
  - `modo_jugar_online.png`
- Maps:
  - `mapa_pampa.png`
  - `mapa_grecia.png`
  - `mapa_medieval.png`
  - night variants for each map.
- Role cards follow `rol_<role>_<map-suffix>.png`.

## Values Resources
- Colors: `app/src/main/res/values/colors.xml`
- Strings: `app/src/main/res/values/strings.xml`
- Theme/styles: `app/src/main/res/values/themes.xml`

## Naming Conventions
- Activity classes end with `Activity`.
- Activity layouts use `activity_<screen>.xml`.
- Repeated row/card layouts use `item_<thing>.xml`.
- Dialog layout uses `dialog_role_detail.xml`.
- Role image resources follow map suffixes: `gaucho`, `griego`, `medieval`.
- Map keys in local gameplay use lowercase strings such as `pampa`, `grecia`, and `medieval`.

## Generated and IDE Files
- `.gradle/`, `.idea/`, and `app/build/` are generated/local environment directories.
- The working tree currently shows `.idea` changes unrelated to app code.
- Debug APK output is under `app/build/outputs/apk/debug`.

