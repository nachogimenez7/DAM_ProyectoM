# Technology Stack

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Languages

- Kotlin is the primary implementation language for Activities, game state, local rules, render helpers, adapters, animation coordinators, audio helpers, and profile/lobby logic.
- XML is used for Android layouts, drawables, menus, themes, fonts, and the manifest.
- Groovy Gradle DSL is used in `build.gradle` and `app/build.gradle`.

## Android Runtime

- Single Android app module: `:app`.
- Namespace and application id: `com.traidores.juego`.
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`.
- Java/Kotlin bytecode target: JVM 1.8.
- App version: `0.1.0-alpha`.
- Every Activity is fixed to portrait in `app/src/main/AndroidManifest.xml`; landscape resource variants are not maintained.
- `GameplayMockActivity` uses `android:windowSoftInputMode="adjustResize"` to support the portrait chat keyboard flow.

## Frameworks And Dependencies

- Android Gradle Plugin 8.1.4.
- Kotlin Android plugin 1.9.22.
- AndroidX Core KTX 1.12.0.
- AndroidX AppCompat 1.6.1.
- Material Components 1.11.0.
- ConstraintLayout 2.1.4.
- RecyclerView 1.3.2.
- CardView 1.0.0.
- JUnit 4.13.2 for JVM unit tests.

## Build And Tooling

- Gradle wrapper scripts: `gradlew` and `gradlew.bat`.
- Settings live in `settings.gradle`; project plugins in root `build.gradle`; app configuration in `app/build.gradle`.
- The current Codex environment did not expose `node` in PATH, so GSD shim commands cannot be queried directly from this shell without adding Node.
- Previous working Gradle command on Windows uses Android Studio JBR:
  - `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`
  - `.\gradlew.bat :app:testDebugUnitTest`

## Assets

- Large visual assets live mostly in `app/src/main/res/drawable/` and `app/src/main/res/drawable-nodpi/`.
- Role art exists per map family: base, gaucho, medieval, and Greek variants.
- Recent profile/gameplay assets include historical portraits, profile banners, `card_back_traidores.webp`, `oracle_return_portal.webp`, `jester_horn_illustrated.webp`, and `ic_kicking_boot.png`.
- Audio assets in `app/src/main/res/raw/` include phase music, victory music, card dealing, transition sounds, vote cast, death/silence reveals, oracle, payador, and jester victory effects.

## Persistence

- Local state is stored with `SharedPreferences`, mainly using the `TraidoresPrefs` namespace.
- `AudioPreferences.kt` centralizes music/effects preferences and migration.
- `ProfileActivity.kt`, `LobbyActivity.kt`, `LocalModeActivity.kt`, `OnlineModeActivity.kt`, and `GameplayMockActivity.kt` read/write preference state directly in several places.

## Testing Stack

- Tests are local JVM tests in `app/src/test/java/com/traidores/juego/`.
- Current test files cover `GameEngine`, `GameplayTableUi`, `GameTableLayout`, `GameplayCountdown`, `GameplayFeedbackState`, and `RoleCatalog`.
- No instrumentation, screenshot, accessibility, device-matrix, or UI-navigation test framework is configured.

## Production Shape

- The app is still local/mock-first.
- Online screens exist, but Firebase/auth/network services are not implemented in the app module.
- Real multiplayer, accounts, backend authority, and database persistence remain future work.
