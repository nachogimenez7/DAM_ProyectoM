---
last_mapped: 2026-06-03
focus: tech
---

# Stack

## Summary
Traidores is a single-module Android application built with Kotlin and XML layouts. The current project is an Android Studio style Gradle project with classic `Activity` navigation, AppCompat, Material Components, RecyclerView, CardView, and ConstraintLayout dependencies.

## Build System
- Root project: `settings.gradle`
- Root plugin versions: `build.gradle`
- Android app module: `app/build.gradle`
- Module namespace and app id: `com.traidores.juego`
- Android Gradle Plugin: `com.android.application` version `8.1.4`
- Kotlin Android plugin: `org.jetbrains.kotlin.android` version `1.9.22`
- Compile SDK: `34`
- Target SDK: `34`
- Min SDK: `24`
- App version: `0.1.0-alpha`

## Runtime and Language
- Kotlin is the only application language under `app/src/main/java/com/traidores/juego`.
- XML is used for all UI screens under `app/src/main/res/layout`.
- The app uses standard Android Activities rather than Jetpack Compose.
- The project targets Java 8 bytecode via `sourceCompatibility JavaVersion.VERSION_1_8` and `kotlinOptions.jvmTarget = '1.8'`.

## Dependencies
- `androidx.core:core-ktx:1.12.0` for Kotlin Android extensions.
- `androidx.appcompat:appcompat:1.6.1` for `AppCompatActivity` and dialogs.
- `com.google.android.material:material:1.11.0` for Material widgets and theme compatibility.
- `androidx.constraintlayout:constraintlayout:2.1.4`, although current key layouts mostly use `RelativeLayout` and `LinearLayout`.
- `androidx.recyclerview:recyclerview:1.3.2` for the roles catalog in `RolesActivity.kt`.
- `androidx.cardview:cardview:1.0.0`, likely for card-like visual surfaces.

## UI Assets
- Main visual assets live in `app/src/main/res/drawable`.
- Map images include `mapa_pampa.png`, `mapa_grecia.png`, `mapa_medieval.png`, plus night variants such as `mapa_pampa_noche.png`.
- Role illustrations are stored by map suffix, for example `rol_asesino_gaucho.png`, `rol_detective_griego.png`, and `rol_medico_medieval.png`.
- Fonts are bundled in `app/src/main/res/font`, including `grenze.ttf`, `cormorant_garamond.ttf`, `cinzel.ttf`, `metamorphous.ttf`, and `rye.ttf`.
- Menu music is bundled as `app/src/main/res/raw/menu_music.mp3`.

## Theming
- Theme definition is in `app/src/main/res/values/themes.xml`.
- Color tokens are in `app/src/main/res/values/colors.xml`.
- Shared button and panel drawables include `bg_btn_gold.xml`, `bg_btn_dark.xml`, `bg_game_panel.xml`, `bg_player_chip.xml`, `bg_role_card.xml`, and `bg_card_back.xml`.

## Current Build Notes
- The repository contains `gradle/wrapper/gradle-wrapper.properties`, but no checked-in `gradlew.bat` or wrapper jar was observed.
- Successful local builds have used Gradle 8.5 from the user's Gradle cache and Android Studio's JBR at `C:\Program Files\Android\Android Studio\jbr`.
- Generated APK location after debug build: `app/build/outputs/apk/debug/app-debug.apk`.

