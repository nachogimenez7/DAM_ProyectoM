---
last_mapped: 2026-06-03
focus: tech
---

# Integrations

## Summary
The app currently has no real backend, database, authentication provider, matchmaking service, analytics, ads, push notifications, or external API client. Integrations are Android-platform local services plus mocked online/game flows.

## Android Manifest
- Permission declared: `android.permission.INTERNET`.
- No network client dependency or network call was observed, so this appears reserved for future online play.
- Launcher activity: `.MainActivity`.
- App theme: `@style/Theme.Traidores`.
- Backup is enabled with `android:allowBackup="true"`.
- Activities are explicitly registered and screen orientation is fixed per screen.

## Activity Navigation
- Navigation uses explicit `Intent` launches.
- Main flow: `MainActivity` -> `JugarActivity` -> local/online mode screens.
- Local flow: `LocalModeActivity` -> `LobbyActivity` -> `AssigningRolesActivity` -> `GameplayMockActivity`.
- Online buttons currently show `Toast` feedback and open `GameplayMockActivity`.
- `RolesActivity`, `AyudaActivity`, and `OpcionesActivity` are standalone menu destinations.

## Game State Passing
- `GameSession`, `GamePlayer`, `GameRole`, and `GameChatMessage` implement `Serializable`.
- `LobbyActivity.EXTRA_SESSION` is the primary intent extra key for local game session transfer.
- `LocalGameFactory` creates local sessions, selects maps, changes mock players, and assigns roles.
- There is no persistent game-state store; session state is passed in memory between activities.

## Local Preferences
- Preferences file: `TraidoresPrefs`.
- `MainActivity` reads/writes `sound_on`.
- `MusicManager` reads `sound_on` and `music_volume`.
- `OpcionesActivity` manages `music_volume`, `voice_volume`, `language`, and simulated account form values.
- Preferences use Android `SharedPreferences`; no Room, SQLite wrapper, DataStore, or remote sync is present.

## Media
- `MusicManager` uses Android `MediaPlayer` to loop `R.raw.menu_music`.
- `BaseActivity` calls `MusicManager.onActivityStarted` and `onActivityStopped` for all screens that inherit from it.
- Playback is controlled by `SharedPreferences` and delayed pause handling via `Handler`.

## Resource Lookup
- The app uses generated `R` references for most layouts, drawables, raw assets, strings, colors, and fonts.
- Some role/map images are resolved dynamically from resource names with `Resources.getIdentifier`.
- Dynamic lookup is used in roles and gameplay rendering, so missing drawable names may fail at runtime rather than compile time.

## Mocked Online and Auth
- `OnlineModeActivity` has quick match, search, and create actions, but all are local mocks.
- `OpcionesActivity` includes simulated login/register UI.
- No credentials are stored or transmitted.
- No Firebase, OAuth, REST, websocket, or local-network integration was observed.

## External Service Surface
- No API keys, tokens, service config files, analytics SDKs, crash reporters, ads, payments, cloud storage, or push notification setup were observed.
- Security-sensitive external integration surface is currently minimal, with `INTERNET` and `allowBackup` as the main production-readiness review points.
