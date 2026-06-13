# Codebase Structure

**Analysis Date:** 2026-06-13

## Directory Layout

```text
App Traidores/
|-- app/
|   |-- build.gradle                 # Android module configuration
|   `-- src/
|       |-- main/
|       |   |-- AndroidManifest.xml  # Activities, orientation, theme
|       |   |-- java/com/traidores/juego/
|       |   `-- res/                 # Layouts, drawables, fonts, audio, strings
|       `-- test/java/com/traidores/juego/
|-- docs/                            # Product/role behavior notes
|-- gradle/wrapper/                  # Gradle wrapper runtime
|-- roles_gauchos/                   # Source artwork outside Android resources
|-- roles_griegos/                   # Source artwork outside Android resources
|-- roles_medievales/                # Source artwork outside Android resources
|-- .planning/codebase/              # GSD codebase map
|-- build.gradle                     # Root plugin versions
|-- settings.gradle                  # Repositories and module list
`-- README.md                        # Basic project instructions
```

## Directory Purposes

**`app/src/main/java/com/traidores/juego/`:**
- Purpose: All production Kotlin code in a flat package.
- Contains: Activities, models, game engine, bots, catalogs, adapters, animation coordinators, and audio helpers.
- Largest files: `GameplayMockActivity.kt`, `GameEngine.kt`, `LobbyActivity.kt`, `LocalBotAi.kt`, and `GameplayTableUi.kt`.
- No feature subpackages currently separate menu, profile, lobby, or gameplay concerns.

**`app/src/main/res/layout/`:**
- Purpose: Activity, dialog, and item XML layouts.
- Key files: `activity_gameplay_mock.xml`, `activity_profile.xml`, `activity_lobby.xml`, and `activity_opciones.xml`.
- `activity_gameplay_mock.xml` is the largest and most dimension-heavy layout.

**`app/src/main/res/drawable*/`:**
- Purpose: UI backgrounds, icons, map art, role images, profile banners, and animation elements.
- `drawable-nodpi/` contains artwork that should not be density-scaled automatically.

**`app/src/main/res/raw/`:**
- Purpose: Music and gameplay sound effects.

**`app/src/test/java/com/traidores/juego/`:**
- Purpose: JVM unit tests for domain and presentation helpers.
- Key files: `GameEngineTest.kt` and `GameplayTableUiTest.kt`.
- No `app/src/androidTest/` tree exists.

**`docs/`:**
- Purpose: Rules and future integration decisions.
- Current files: `lan-role-readiness.md` and `map-exclusive-roles.md`.

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/traidores/juego/MainActivity.kt` - Launcher menu.
- `app/src/main/java/com/traidores/juego/LobbyActivity.kt` - Local/simulated online room.
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` - Match UI.

**Configuration:**
- `app/src/main/AndroidManifest.xml` - Activities and orientation locks.
- `app/build.gradle` - SDK and dependencies.
- `app/src/main/res/values/themes.xml` - Theme and shared button styles.
- `app/src/main/res/values/colors.xml` - Core palette.

**Core Logic:**
- `app/src/main/java/com/traidores/juego/GameEngine.kt` - Game rules.
- `app/src/main/java/com/traidores/juego/GameModels.kt` - Session/model definitions.
- `app/src/main/java/com/traidores/juego/GameplayTableUi.kt` - Presentation derivation.
- `app/src/main/java/com/traidores/juego/RoleCatalog.kt` - Role metadata.

**Testing:**
- `app/src/test/java/com/traidores/juego/GameEngineTest.kt` - Domain regression coverage.
- `app/src/test/java/com/traidores/juego/GameplayTableUiTest.kt` - Presentation rules.
- `app/src/test/java/com/traidores/juego/GameTableLayoutTest.kt` - Pure table geometry.

## Naming Conventions

**Files:**
- PascalCase Kotlin files matching primary class/object names.
- Android resource files use lowercase snake_case.
- Activity layouts use `activity_<screen>.xml`; rows use `item_<entity>.xml`; dialogs use `dialog_<purpose>.xml`.
- Unit tests use `<Subject>Test.kt`.

**Resources:**
- Backgrounds use `bg_` prefixes.
- Icons use `ic_` prefixes.
- Role images use `rol_<role>_<map suffix>`.
- Map-specific suffixes are `gaucho`, `griego`, and `medieval`.

## Where to Add Correction Work

**Visual Layout Fix:**
- XML: `app/src/main/res/layout/`.
- Shared control styling: `app/src/main/res/values/themes.xml`.
- Colors/drawables: `app/src/main/res/values/` and `app/src/main/res/drawable/`.
- Prefer dimensions based on constraints/weights over adding more fixed sizes.

**Navigation Fix:**
- Activity click/back handling in `app/src/main/java/com/traidores/juego/*Activity.kt`.
- Manifest orientation/window behavior in `app/src/main/AndroidManifest.xml`.

**Regression Test:**
- Pure logic test in `app/src/test/java/com/traidores/juego/`.
- Activity/layout/navigation verification requires creating `app/src/androidTest/`.

## Special Directories and Root Artifacts

**`.gradle/`, `.idea/`, `app/build/`:**
- Generated tooling/build output.
- Gitignored and not source.

**`roles_*`:**
- Original role artwork source folders.
- Separate from packaged Android resources.

**Root screenshots and XML captures:**
- Files matching `traidores-*.png`, `window.xml`, and `lobby.xml` are debug artifacts and gitignored.
- They are useful for visual comparison but are not production inputs.

---
*Structure analysis: 2026-06-13*
*Update when modules or feature packages are introduced*
