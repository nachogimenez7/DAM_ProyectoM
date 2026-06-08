---
last_mapped: 2026-06-03
focus: arch
---

# Structure

## Summary
The repository is a compact Android project with one Gradle application module. Source is concentrated in `app/src/main/java/com/traidores/juego`, resources in `app/src/main/res`, local unit tests in `app/src/test`, and planning docs in `.planning/codebase`.

## Root Layout
- `settings.gradle` names the root project `TraidoresMenu` and includes `:app`.
- `build.gradle` declares Android Gradle Plugin and Kotlin plugin versions.
- `app/build.gradle` configures namespace, SDK levels, app version, dependencies, and JUnit tests.
- `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` provide the Gradle wrapper.
- `README.md` describes the menu/navigation prototype.
- `.planning/codebase` contains codebase reference docs.
- `.idea`, `.gradle`, and `app/build` are local/generated directories.

## App Source Directory
Package root: `app/src/main/java/com/traidores/juego`.

Activities:
- `MainActivity.kt` - launcher menu, sound toggle, top-level navigation.
- `JugarActivity.kt` - local/online mode selection.
- `LocalModeActivity.kt` - local room create/join mock entry.
- `LobbyActivity.kt` - map selection, player list management, role assignment handoff.
- `AssigningRolesActivity.kt` - timed transition into gameplay.
- `GameplayMockActivity.kt` - local match renderer/controller.
- `OnlineModeActivity.kt` - mocked online actions that open gameplay.
- `RolesActivity.kt` - role catalog and detail dialog.
- `AyudaActivity.kt` - help screen.
- `OpcionesActivity.kt` - options, language/account/volume UI, and preferences.

Shared and domain classes:
- `BaseActivity.kt` - common music lifecycle hook.
- `MusicManager.kt` - singleton `MediaPlayer` controller.
- `GameModels.kt` - session, player, role, chat, phase, map, and local factory models.
- `GameEngine.kt` - local game state machine and rule resolution.
- `Role.kt`, `RoleListItem.kt`, `MapInfo.kt` - role catalog models.
- `RoleAdapter.kt` - RecyclerView adapter for map headers, sections, and role cards.

## Manifest
- File: `app/src/main/AndroidManifest.xml`.
- Declares `android.permission.INTERNET`.
- Registers all Activity classes in package-relative form.
- Launcher: `.MainActivity`.
- Landscape: `.LobbyActivity`, `.AssigningRolesActivity`, `.GameplayMockActivity`.
- Portrait: main/menu/setup/reference/options screens.

## Layout Resources
Activity layouts:
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_jugar.xml`
- `app/src/main/res/layout/activity_local_mode.xml`
- `app/src/main/res/layout/activity_online_mode.xml`
- `app/src/main/res/layout/activity_lobby.xml`
- `app/src/main/res/layout/activity_assigning_roles.xml`
- `app/src/main/res/layout/activity_gameplay_mock.xml`
- `app/src/main/res/layout/activity_roles.xml`
- `app/src/main/res/layout/activity_ayuda.xml`
- `app/src/main/res/layout/activity_opciones.xml`

Repeated/detail layouts:
- `app/src/main/res/layout/item_lobby_player.xml`
- `app/src/main/res/layout/item_role.xml`
- `app/src/main/res/layout/item_map.xml`
- `app/src/main/res/layout/item_section_header.xml`
- `app/src/main/res/layout/item_mock_player_left.xml`
- `app/src/main/res/layout/item_mock_player_right.xml`
- `app/src/main/res/layout/dialog_role_detail.xml`

## Drawable And Media Resources
- UI chrome: `bg_btn_dark.xml`, `bg_btn_gold.xml`, `bg_game_panel.xml`, `bg_hud_parchment.xml`, `bg_chat_panel.xml`, `bg_chat_input.xml`, `bg_chat_fab.xml`, `bg_player_seat.xml`, `bg_player_avatar.xml`, `bg_player_chip.xml`, `bg_role_card.xml`, `bg_card_back.xml`, `bg_center_event.xml`.
- Menu/mode assets: `fondo_menu.png`, `logo_traidores.png`, `logo_traidores_transparente.png`, `modo_juego_local.png`, `modo_jugar_online.png`.
- Map assets: `mapa_pampa.png`, `mapa_pampa_noche.png`, `mapa_grecia.png`, `mapa_grecia_noche.png`, `mapa_medieval.png`, `mapa_medieval_noche.png`.
- Role assets: `rol_<role>.jpg` legacy/base images plus `rol_<role>_<map-suffix>.png` variants.
- Audio: `app/src/main/res/raw/menu_music.mp3`.
- Fonts: `app/src/main/res/font/*.ttf`.

## Values And Theme
- `app/src/main/res/values/colors.xml` - app palette and semantic colors.
- `app/src/main/res/values/strings.xml` - app strings.
- `app/src/main/res/values/themes.xml` - app theme.

## Tests
- `app/src/test/java/com/traidores/juego/GameEngineTest.kt` contains local JUnit tests for game rules.
- No `app/src/androidTest` directory was observed.

## Naming Conventions
- Activity classes end with `Activity`.
- Activity layouts use `activity_<screen>.xml`.
- Repeated rows and cards use `item_<thing>.xml`.
- Dialog layouts use `dialog_<thing>.xml`.
- Drawable backgrounds use `bg_<component>.xml`.
- Map keys are lowercase strings such as `pampa`, `grecia`, and `medieval`.
- Role image names follow `rol_<role>_<suffix>` where suffix is `gaucho`, `griego`, or `medieval`.

## Non-App Artifacts In The Workspace
- Top-level folders `roles_gauchos`, `roles_griegos`, and `roles_medievales` contain role art outside the Android resource tree.
- Top-level screenshots such as `traidores-current.png`, `traidores-gameplay-new.png`, and `traidores-chat-visible.png` appear to be QA/reference captures.
- Top-level `lobby.xml` and `window.xml` appear to be exported or inspection artifacts, not Android source files.
