---
last_mapped: 2026-06-03
focus: tech
---

# Integrations

## Summary
The current app has no real backend, database, authentication provider, webhooks, or external API integration. The codebase is primarily a local Android game prototype with mocked local and online flows.

## Android Platform Integrations
- The app declares `android.permission.INTERNET` in `app/src/main/AndroidManifest.xml`.
- No HTTP client dependency is present in `app/build.gradle`.
- No network code was observed in `app/src/main/java/com/traidores/juego`.
- The `INTERNET` permission appears reserved for future online play rather than active use.

## Media Integration
- `MusicManager.kt` uses Android `MediaPlayer`.
- The music asset is `app/src/main/res/raw/menu_music.mp3`.
- `BaseActivity.kt` starts/stops music lifecycle tracking for every screen by calling `MusicManager.onActivityStarted(this)` and `MusicManager.onActivityStopped()`.
- `MusicManager.refresh(context)` reads preferences and starts/pauses looping menu music.

## Local Preferences
- Preferences file: `TraidoresPrefs`.
- `MainActivity.kt` reads and writes `sound_on`.
- `MusicManager.kt` reads `sound_on` and `music_volume`.
- `OpcionesActivity.kt` manages `music_volume`, `voice_volume`, `language`, and simulated account form interactions.
- Preferences are stored with Android `SharedPreferences`, not a database.

## Game Session Passing
- Local game state is passed between activities using `Serializable` extras.
- `LobbyActivity.EXTRA_SESSION` is used as the intent key.
- `LocalModeActivity.kt` creates a `GameSession` and sends it to `LobbyActivity.kt`.
- `LobbyActivity.kt` assigns roles and sends the session to `AssigningRolesActivity.kt`.
- `AssigningRolesActivity.kt` forwards the session to `GameplayMockActivity.kt`.

## Mocked Online Flow
- `OnlineModeActivity.kt` contains buttons for quick match, search, and create.
- Each online action currently displays a `Toast` and opens `GameplayMockActivity`.
- There is no matchmaking service, websocket, REST API, Firebase, or local network integration yet.

## Authentication
- `OpcionesActivity.kt` includes simulated login/register UI.
- No auth backend exists.
- Username/password fields only trigger local Toast messages.
- No credentials are persisted or transmitted.

## Resource Lookup Integration
- Several components use `Resources.getIdentifier`.
- `RolesActivity.kt`, `RoleAdapter.kt`, and `GameplayMockActivity.kt` resolve drawable names dynamically from role/map metadata.
- This reduces explicit resource references but can hide missing-resource errors until runtime.

## External Services
- None currently integrated.
- No analytics, crash reporting, ads, payment, push notification, or cloud storage integrations were observed.

## Security Notes
- Because no external API keys or tokens are present, no secret-bearing integration surface was observed.
- `android:allowBackup="true"` is enabled in `app/src/main/AndroidManifest.xml`; that may be acceptable for a prototype but should be reviewed before production.

