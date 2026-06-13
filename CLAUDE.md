<!-- GSD:project-start source:PROJECT.md -->

## Project

**App Traidores**

App Traidores es un juego móvil Android de deducción social con ambientación histórica y medieval. Actualmente permite recorrer menús, configurar partidas, usar perfiles personalizables y jugar una simulación local del flujo de una partida; las superficies online todavía funcionan con datos simulados.

Este ciclo de trabajo se concentra en pulir las pantallas de gameplay, lobby, perfil y chat antes de incorporar nuevos roles o servicios reales.

**Core Value:** El jugador debe poder recorrer y utilizar las pantallas principales sin contenido cortado, controles confusos, rutas rotas ni pérdida inesperada de estado.

### Constraints

- **Alcance**: Gameplay, lobby, perfil y chat — son las superficies prioritarias antes de sumar roles.
- **Producto**: No agregar funciones nuevas — el objetivo es estabilización visual y de navegación.
- **Dispositivos**: Priorizar teléfonos — las tablets quedan fuera de esta etapa.
- **Orientación**: Mantener la orientación actual de cada pantalla — no se amplía la matriz de compatibilidad.
- **Diseño**: Conservar la identidad medieval y dorada — las correcciones deben integrarse con el estilo existente.
- **Implementación**: Permitir solo refactorizaciones pequeñas y justificadas — se evita transformar la corrección en una reescritura.
- **Verificación**: No ejecutar compilaciones — el usuario validará compilación y apariencia en Android Studio.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Kotlin 1.9.22 - Android Activities, game rules, presentation state, adapters, animation coordinators, and local persistence.
- XML - Android layouts, themes, colors, drawables, and manifest declarations under `app/src/main/res/`.
- Groovy Gradle DSL - Project and application build configuration in `build.gradle` and `app/build.gradle`.

## Runtime

- Android runtime, minimum SDK 24 and target/compile SDK 34.
- Java 8 bytecode target through `sourceCompatibility`, `targetCompatibility`, and `jvmTarget`.
- Portrait orientation for menu/profile/reference screens; landscape orientation for lobby, role assignment, and gameplay in `app/src/main/AndroidManifest.xml`.
- Gradle wrapper 8.5, configured in `gradle/wrapper/gradle-wrapper.properties`.
- Android Gradle Plugin 8.1.4.
- No dependency lockfile or version catalog.

## Frameworks

- AndroidX AppCompat 1.6.1 - Activity and compatibility foundation.
- AndroidX Core KTX 1.12.0 - Kotlin-friendly Android APIs.
- Material Components 1.11.0 - Dialog and UI primitives.
- ConstraintLayout 2.1.4 - Available for responsive layout work, although most current screens use `RelativeLayout` and nested `LinearLayout`.
- RecyclerView 1.3.2 and CardView 1.0.0 - Role and profile selection lists.
- JUnit 4.13.2 - Local JVM tests in `app/src/test/java/com/traidores/juego/`.
- No Android instrumentation, screenshot, accessibility, or end-to-end test framework is configured.
- Android Studio is the intended development environment.
- Gradle wrapper scripts are `gradlew` and `gradlew.bat`.
- Kotlin official style is enabled in `gradle.properties`.

## Key Dependencies

- `androidx.appcompat:appcompat:1.6.1` - Base Activity implementation and dialogs.
- `com.google.android.material:material:1.11.0` - Material-compatible controls.
- `androidx.recyclerview:recyclerview:1.3.2` - Dynamic role/profile lists.
- `androidx.constraintlayout:constraintlayout:2.1.4` - Responsive positioning option for future layout corrections.
- `androidx.cardview:cardview:1.0.0` - Card presentation.
- Custom fonts in `app/src/main/res/font/`.
- Large map, role, profile, and background images in `app/src/main/res/drawable/` and `drawable-nodpi/`.
- Music and sound effects in `app/src/main/res/raw/`.

## Configuration

- No environment variables are required.
- `local.properties` contains machine-specific Android SDK configuration and is gitignored.
- User preferences and profile mock data use `SharedPreferences` under the `TraidoresPrefs` namespace.
- `settings.gradle` defines repositories and the single `:app` module.
- `app/build.gradle` defines SDK levels, version `0.1.0-alpha`, dependencies, and release settings.
- Release minification is disabled.

## Platform Requirements

- Windows is the active development environment.
- Android Studio/JDK and Android SDK 34 are required.
- The project has no command-line Node.js dependency.
- Single Android APK application with application ID `com.traidores.juego`.
- Current implementation is local/mock-first and does not require network access.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- PascalCase for Kotlin classes, objects, enums, and data classes.
- Activities end in `Activity`; adapters end in `Adapter`; animation coordinators end in `Animator`.
- Resource names are lowercase snake_case.
- camelCase for functions.
- UI event orchestration commonly uses `show*`, `render*`, `update*`, `handle*`, `toggle*`, and `resolve*`.
- Guard/query helpers commonly use `is*`, `can*`, `should*`, and `needs*`.
- camelCase for fields and locals.
- `lateinit var` is common for Activity-bound views.
- Constants use `UPPER_SNAKE_CASE` in companion objects.

## Code Style

- Kotlin official style is enabled in `gradle.properties`.
- Four-space indentation and trailing commas in multiline Kotlin declarations are common.
- XML attributes are generally one per line.
- No standalone formatter or lint configuration beyond Android/Kotlin defaults is committed.
- Kotlin and XML contain many hardcoded Spanish strings.
- `app/src/main/res/values/strings.xml` contains only a small subset of visible copy.
- New correction work should avoid expanding hardcoded duplication and should move repeated/accessibility text to resources when touching a screen.

## Import Organization

- Android framework imports first.
- AndroidX imports next.
- Kotlin/Java standard library imports last.
- No path aliases or barrel modules exist.

## Error Handling

- Use early returns for invalid state or actions.
- Use `Toast` for recoverable user feedback.
- Use `AlertDialog` for confirmation and editable values.
- Use safe fallbacks when an Intent extra or drawable resource is missing.
- Avoid non-null assertions; production code primarily relies on guards and `lateinit`.
- There is no central route abstraction or route validation.
- Every Activity owns its own click and back behavior.

## Logging

- No application logging framework is used.
- No consistent `Log.d/e` diagnostic strategy is present.
- Visual fixes should be verified through repeatable test cases or screenshots rather than temporary production logs.

## Comments

- Comments are sparse and usually identify a UI section or explain a business exception.
- Most domain behavior is expressed through named functions and tests.
- Comment only non-obvious layout calculations, lifecycle workarounds, and state ordering.
- Do not narrate basic view binding or assignments.

## Function Design

- Guard clauses followed by immutable `copy()` state updates.
- Extract pure rules into `GameEngine`, `GameplayTableUi`, or geometry helpers.
- Keep dynamic Android view construction in renderer/adapter classes when practical.
- `GameplayMockActivity.kt` and `LobbyActivity.kt` contain long methods and many responsibilities.
- Programmatic dialog construction in `LobbyActivity.kt` makes visual consistency harder to review than XML-based components.

## Module Design

- Kotlin top-level package visibility is simple; most helpers are objects or classes.
- `internal` is used for testable implementation details such as `GameplayFeedbackState`.
- Prefer immutable model transformations for game state.
- Activity UI flags are mutable and lifecycle-sensitive.
- Any visual/navigation correction should preserve state ordering around animations and callbacks.

## XML/UI Conventions

- Shared primary/dark buttons use `BtnGold` and `BtnDark` from `themes.xml`.
- The visual system uses dark brown panels, gold borders/accents, custom fonts, and map artwork.
- Touch targets are commonly 44dp; new work should meet or exceed 48dp where layout permits.
- Avoid adding fixed widths/heights to `activity_gameplay_mock.xml`; it already contains 99 fixed dimensions.
- Use `ScrollView`/RecyclerView or responsive constraints for screens that can overflow with large fonts or small displays.

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## Pattern Overview

- One Android `:app` module and one package, `com.traidores.juego`.
- Explicit `Intent` navigation between Activities rather than Navigation Component routes.
- Portrait menu/reference flow and landscape game flow are separated by manifest orientation locks.
- Local game simulation passes a serializable `GameSession` through Activity extras.
- Business rules have meaningful test coverage; visual and navigation behavior does not.

## Layers

- Purpose: Inflate XML, bind views, handle clicks, show dialogs, and render state.
- Contains: `MainActivity.kt`, `LobbyActivity.kt`, `GameplayMockActivity.kt`, `ProfileActivity.kt`, and other Activities.
- Depends on: Android framework, resource files, game/presentation helpers.
- Risk: `GameplayMockActivity.kt` and `LobbyActivity.kt` combine navigation, rendering, animation, timers, and state mutation.
- Purpose: Convert session state into UI-friendly data and isolate animations.
- Contains: `GameplayTableUi.kt`, `GameTableLayout.kt`, `WinnerResultsRenderer.kt`, and the `*Animator.kt` classes.
- Depends on: Models and some Android view APIs.
- Used by: Gameplay and role/result presentation.
- Purpose: Model players, phases, actions, roles, and resolve game rules.
- Contains: `GameModels.kt`, `GameEngine.kt`, `RoleCatalog.kt`, and `LocalBotAi.kt`.
- Depends on: Kotlin/JVM and Android resource identifiers in the local factory/catalog.
- Used by: Lobby, role assignment, gameplay, and tests.
- Purpose: Retain user options and coordinate audio/effects.
- Contains: `MusicManager.kt`, `GameplayEffects.kt`, and direct `SharedPreferences` calls.
- Depends on: Android context and packaged resources.

## Data Flow

- `GameSession` is immutable data copied after actions.
- Activity-level flags track overlays, transitions, chat state, and animations.
- Gameplay state is saved in `onSaveInstanceState`; lobby/profile state preservation is less complete.
- User preferences are local and synchronous through `SharedPreferences`.

## Key Abstractions

- Purpose: Complete local match state.
- Location: `app/src/main/java/com/traidores/juego/GameModels.kt`.
- Pattern: Serializable immutable data object passed through Intents and Bundles.
- Purpose: Authoritative local phase and action resolver.
- Location: `app/src/main/java/com/traidores/juego/GameEngine.kt`.
- Pattern: Stateless object with pure or copy-based transformations.
- Purpose: Central role names, teams, map availability, images, descriptions, and requirements.
- Location: `app/src/main/java/com/traidores/juego/RoleCatalog.kt`.
- Purpose: Keep complex animation and result rendering outside the main Activity.
- Examples: `DayNightTransitionAnimator.kt`, `DeathRevealAnimator.kt`, `WinnerRevealAnimator.kt`.

## Entry Points

- Location: `app/src/main/java/com/traidores/juego/MainActivity.kt`.
- Trigger: Launcher intent declared in `app/src/main/AndroidManifest.xml`.
- Responsibilities: Main navigation, sound toggle, and profile entry.
- Location: `app/src/main/java/com/traidores/juego/LobbyActivity.kt`.
- Trigger: Local or simulated online mode.
- Responsibilities: Session configuration and transition to role assignment.
- Location: `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt`.
- Trigger: Assigned session from `AssigningRolesActivity.kt`.
- Responsibilities: Full game table rendering and interaction lifecycle.

## Error Handling

- Guard clauses prevent invalid targets or phases.
- Dialogs confirm destructive profile/lobby actions.
- Resource lookup uses `resources.getIdentifier()` with fallback placeholders.
- There is no global exception handler, error screen, or structured diagnostic logging.

## Cross-Cutting Concerns

- Direct Activity Intents and `finish()` calls.
- Back behavior is implemented independently per Activity.
- Gameplay intercepts back presses for overlays and expanded panels before allowing Activity exit.
- Domain actions are validated in `GameEngine`.
- Profile text is validated in `ProfileActivity`.
- Layout fit and route continuity are not automatically validated.
- `BaseActivity.kt` reports Activity lifecycle to `MusicManager.kt`.
- Gameplay transition coordinators pause/resume phase music.

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
