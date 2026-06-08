---
last_mapped: 2026-06-03
focus: tech
---

# Stack

## Summary
Traidores is a single-module Android app built with Kotlin, XML layouts, and the classic Android `Activity` model. It is a local-first game prototype: screens, resources, music, lobby setup, role assignment, and mock gameplay all live in the app module.

## Build System
- Root project: `settings.gradle`
- Root plugins: Android Gradle Plugin `8.1.4`, Kotlin Android plugin `1.9.22`
- App module: `app/build.gradle`
- Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties` points to Gradle `8.5`; `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are present.
- Repositories: `google()`, `mavenCentral()`, and `gradlePluginPortal()` for plugin resolution.
- Repository policy: `RepositoriesMode.FAIL_ON_PROJECT_REPOS`

## Android Configuration
- Namespace and app id: `com.traidores.juego`
- Compile SDK: `34`
- Target SDK: `34`
- Min SDK: `24`
- Version: `0.1.0-alpha`, version code `1`
- Java/Kotlin bytecode target: Java 8 / JVM `1.8`
- Release minification: disabled
- AndroidX enabled; non-transitive `R` enabled.

## Runtime Architecture
- Kotlin application code is under `app/src/main/java/com/traidores/juego`.
- XML resources drive the UI under `app/src/main/res`.
- `BaseActivity` extends `AppCompatActivity` and centralizes music lifecycle hooks.
- `GameModels.kt` defines serializable game state models and `LocalGameFactory`.
- `GameEngine.kt` contains deterministic game phase logic and is covered by local JVM tests.
- Navigation is imperative via `Intent` and activity declarations in `AndroidManifest.xml`.

## UI Stack
- Screens use XML layouts, mostly `RelativeLayout` and `LinearLayout`.
- No Jetpack Compose is used.
- `RolesActivity` and `RoleAdapter` use `RecyclerView` for the roles catalog.
- `LobbyActivity` and `GameplayMockActivity` build some repeated UI rows/cards programmatically from XML resources and game state.
- The app locks portrait for menu/configuration screens and landscape for lobby, role assignment, and gameplay screens.

## Dependencies
- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.cardview:cardview:1.0.0`
- `junit:junit:4.13.2` for local JVM tests

## Assets and Resources
- Main image assets live in `app/src/main/res/drawable`.
- Role art is stored by theme suffix: gaucho, griego, and medieval.
- Map art includes pampa, grecia, medieval, and night variants.
- Fonts are bundled in `app/src/main/res/font`.
- Menu music is bundled at `app/src/main/res/raw/menu_music.mp3`.
- Shared styling lives in `app/src/main/res/values/colors.xml`, `themes.xml`, and reusable drawable backgrounds.

## Test Stack
- Unit tests live under `app/src/test/java/com/traidores/juego`.
- Current tests focus on `GameEngine` and `LocalGameFactory` behavior.
- No instrumentation, Compose, UI automation, or backend integration tests were observed.
