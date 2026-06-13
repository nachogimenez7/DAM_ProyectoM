# Technology Stack

**Analysis Date:** 2026-06-13

## Languages

**Primary:**
- Kotlin 1.9.22 - Android Activities, game rules, presentation state, adapters, animation coordinators, and local persistence.

**Secondary:**
- XML - Android layouts, themes, colors, drawables, and manifest declarations under `app/src/main/res/`.
- Groovy Gradle DSL - Project and application build configuration in `build.gradle` and `app/build.gradle`.

## Runtime

**Environment:**
- Android runtime, minimum SDK 24 and target/compile SDK 34.
- Java 8 bytecode target through `sourceCompatibility`, `targetCompatibility`, and `jvmTarget`.
- Portrait orientation for menu/profile/reference screens; landscape orientation for lobby, role assignment, and gameplay in `app/src/main/AndroidManifest.xml`.

**Package Manager:**
- Gradle wrapper 8.5, configured in `gradle/wrapper/gradle-wrapper.properties`.
- Android Gradle Plugin 8.1.4.
- No dependency lockfile or version catalog.

## Frameworks

**Core:**
- AndroidX AppCompat 1.6.1 - Activity and compatibility foundation.
- AndroidX Core KTX 1.12.0 - Kotlin-friendly Android APIs.
- Material Components 1.11.0 - Dialog and UI primitives.
- ConstraintLayout 2.1.4 - Available for responsive layout work, although most current screens use `RelativeLayout` and nested `LinearLayout`.
- RecyclerView 1.3.2 and CardView 1.0.0 - Role and profile selection lists.

**Testing:**
- JUnit 4.13.2 - Local JVM tests in `app/src/test/java/com/traidores/juego/`.
- No Android instrumentation, screenshot, accessibility, or end-to-end test framework is configured.

**Build/Dev:**
- Android Studio is the intended development environment.
- Gradle wrapper scripts are `gradlew` and `gradlew.bat`.
- Kotlin official style is enabled in `gradle.properties`.

## Key Dependencies

**Critical:**
- `androidx.appcompat:appcompat:1.6.1` - Base Activity implementation and dialogs.
- `com.google.android.material:material:1.11.0` - Material-compatible controls.
- `androidx.recyclerview:recyclerview:1.3.2` - Dynamic role/profile lists.
- `androidx.constraintlayout:constraintlayout:2.1.4` - Responsive positioning option for future layout corrections.
- `androidx.cardview:cardview:1.0.0` - Card presentation.

**Local Assets:**
- Custom fonts in `app/src/main/res/font/`.
- Large map, role, profile, and background images in `app/src/main/res/drawable/` and `drawable-nodpi/`.
- Music and sound effects in `app/src/main/res/raw/`.

## Configuration

**Environment:**
- No environment variables are required.
- `local.properties` contains machine-specific Android SDK configuration and is gitignored.
- User preferences and profile mock data use `SharedPreferences` under the `TraidoresPrefs` namespace.

**Build:**
- `settings.gradle` defines repositories and the single `:app` module.
- `app/build.gradle` defines SDK levels, version `0.1.0-alpha`, dependencies, and release settings.
- Release minification is disabled.

## Platform Requirements

**Development:**
- Windows is the active development environment.
- Android Studio/JDK and Android SDK 34 are required.
- The project has no command-line Node.js dependency.

**Production:**
- Single Android APK application with application ID `com.traidores.juego`.
- Current implementation is local/mock-first and does not require network access.

---
*Stack analysis: 2026-06-13*
*Update after major dependency or SDK changes*
