# Integrations

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## External Services

- No real network, Firebase, database, analytics, ads, push notifications, or auth provider is currently wired in the Android app.
- `OnlineModeActivity.kt` and `LobbyBrowserActivity.kt` simulate online/lobby behavior locally.
- Account/profile online design exists conceptually, but the app currently persists only local mock profile state.

## Android Platform Integrations

- `AndroidManifest.xml` declares `android.permission.VIBRATE`.
- `BaseActivity.kt` provides shared AppCompat setup and is inherited by app screens.
- Navigation uses Android `Intent` and Activity transitions directly from each Activity.
- Landscape gameplay uses `adjustResize` to respond to soft keyboard insets.

## Media And Audio

- `MusicManager.kt` owns background music and victory music through `MediaPlayer`.
- `GameplaySoundEffects.kt` plays short one-shot gameplay effects through `MediaPlayer`.
- Animation classes with sound include:
  - `AssigningRolesActivity.kt` for card shuffle/deal sound.
  - `DayNightTransitionAnimator.kt` for day/night transition sounds.
  - `DeathRevealAnimator.kt` for death reveal.
  - `SilenceRevealAnimator.kt` for silence reveal.
  - `GameplayMockActivity.kt` for vote, oracle, payador, and jester cues.
- Audio preferences are split between music and effects in `AudioPreferences.kt`.

## Local Storage

- `SharedPreferences` is used for:
  - Audio settings.
  - Local player/profile data.
  - Lobby/gameplay option values.
  - Initial role-reading delay and other gameplay preferences.
- There is no Room, SQLite wrapper, file database, or remote persistence layer.

## Resource Integrations

- Fonts in `app/src/main/res/font/`: `grenze.ttf`, `cormorant_garamond.ttf`, `metamorphous.ttf`, `rye.ttf`.
- Map backgrounds and logs are resource-driven, selected by map/theme keys.
- Role details and map-specific art are resolved through catalog classes rather than remote content.

## Build/Repo Integrations

- Git remote is GitHub repository `nachogimenez7/DAM_ProyectoM`.
- No CI workflow was observed in the repo snapshot.
- No secrets or API keys are expected for local build/test.

## Future Integration Boundaries

- Firebase/auth/backend authority should not be mixed into `GameEngine.kt` directly.
- Online match authority should live behind a dedicated service/repository boundary when introduced.
- Profile/account persistence should separate public profile data from private auth identity.
- The current local engine can remain the rules reference, but online validation must be server-authoritative later.
