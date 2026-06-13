# External Integrations

**Analysis Date:** 2026-06-13

## APIs & External Services

**Current State:**
- No HTTP client, REST API, WebSocket, Firebase SDK, or external service client exists in `app/build.gradle`.
- The apparent online flows in `OnlineModeActivity.kt`, `LobbyBrowserActivity.kt`, and `LobbyActivity.kt` are local simulations.
- Mock lobbies are hardcoded in `app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt`.

## Data Storage

**Databases:**
- None.
- Game sessions live in memory as serializable `GameSession` objects from `app/src/main/java/com/traidores/juego/GameModels.kt`.

**Local Preferences:**
- Android `SharedPreferences` stores sound, language, player name, profile fields, display scale, and other options.
- Main access points include `MainActivity.kt`, `OpcionesActivity.kt`, `ProfileActivity.kt`, `MusicManager.kt`, and `GameplayMockActivity.kt`.
- There is no schema versioning or migration layer for preference keys.

**File Storage:**
- No user file storage or gallery integration.
- Avatars, banners, role images, and map artwork are packaged application resources.

**Caching:**
- None beyond in-memory Activity/session state and Android resource caching.

## Authentication & Identity

**Current Implementation:**
- Login and registration controls in `OpcionesActivity.kt` are simulated.
- No credentials are sent or stored by a backend provider.
- The current player identity is a locally stored display name.

**Planned but Not Integrated:**
- Firebase/Google account concepts are documented product direction, not runtime dependencies.
- They must remain out of the current visual/navigation stabilization milestone.

## Monitoring & Observability

**Error Tracking:**
- No Crashlytics, Sentry, or equivalent integration.

**Analytics:**
- None.

**Logs:**
- No structured logging layer.
- User-visible failures are primarily communicated with `Toast` or dialog messages.

## CI/CD & Deployment

**Hosting:**
- Not applicable; this is an Android APK.

**CI Pipeline:**
- No `.github/workflows/` pipeline is present.
- Builds, tests, and manual device checks currently depend on local Android Studio execution.

## Environment Configuration

**Development:**
- Android SDK location is machine-specific in gitignored `local.properties`.
- No API keys or service secrets are required.
- Mock online state requires no network connection.

**Staging:**
- No staging environment.

**Production:**
- No production backend or remote configuration.

## Webhooks & Callbacks

**Incoming:**
- None.

**Outgoing:**
- None.

## Stabilization Implications

- Route and empty-state testing can be deterministic because current data is local.
- Online labels must clearly remain coherent even though data is simulated.
- No new service integration should be introduced while fixing visual and navigation bugs.

---
*Integration audit: 2026-06-13*
*Update when a real backend, authentication provider, or telemetry service is added*
