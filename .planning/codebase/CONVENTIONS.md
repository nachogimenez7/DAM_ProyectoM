# Coding Conventions

**Analysis Date:** 2026-06-13

## Naming Patterns

**Files and Types:**
- PascalCase for Kotlin classes, objects, enums, and data classes.
- Activities end in `Activity`; adapters end in `Adapter`; animation coordinators end in `Animator`.
- Resource names are lowercase snake_case.

**Functions:**
- camelCase for functions.
- UI event orchestration commonly uses `show*`, `render*`, `update*`, `handle*`, `toggle*`, and `resolve*`.
- Guard/query helpers commonly use `is*`, `can*`, `should*`, and `needs*`.

**Variables:**
- camelCase for fields and locals.
- `lateinit var` is common for Activity-bound views.
- Constants use `UPPER_SNAKE_CASE` in companion objects.

## Code Style

**Formatting:**
- Kotlin official style is enabled in `gradle.properties`.
- Four-space indentation and trailing commas in multiline Kotlin declarations are common.
- XML attributes are generally one per line.
- No standalone formatter or lint configuration beyond Android/Kotlin defaults is committed.

**Strings:**
- Kotlin and XML contain many hardcoded Spanish strings.
- `app/src/main/res/values/strings.xml` contains only a small subset of visible copy.
- New correction work should avoid expanding hardcoded duplication and should move repeated/accessibility text to resources when touching a screen.

## Import Organization

**Order:**
- Android framework imports first.
- AndroidX imports next.
- Kotlin/Java standard library imports last.
- No path aliases or barrel modules exist.

## Error Handling

**Patterns:**
- Use early returns for invalid state or actions.
- Use `Toast` for recoverable user feedback.
- Use `AlertDialog` for confirmation and editable values.
- Use safe fallbacks when an Intent extra or drawable resource is missing.
- Avoid non-null assertions; production code primarily relies on guards and `lateinit`.

**Navigation Errors:**
- There is no central route abstraction or route validation.
- Every Activity owns its own click and back behavior.

## Logging

**Framework:**
- No application logging framework is used.
- No consistent `Log.d/e` diagnostic strategy is present.

**Correction Guidance:**
- Visual fixes should be verified through repeatable test cases or screenshots rather than temporary production logs.

## Comments

**Current Pattern:**
- Comments are sparse and usually identify a UI section or explain a business exception.
- Most domain behavior is expressed through named functions and tests.

**Guidance:**
- Comment only non-obvious layout calculations, lifecycle workarounds, and state ordering.
- Do not narrate basic view binding or assignments.

## Function Design

**Preferred Existing Pattern:**
- Guard clauses followed by immutable `copy()` state updates.
- Extract pure rules into `GameEngine`, `GameplayTableUi`, or geometry helpers.
- Keep dynamic Android view construction in renderer/adapter classes when practical.

**Current Deviations:**
- `GameplayMockActivity.kt` and `LobbyActivity.kt` contain long methods and many responsibilities.
- Programmatic dialog construction in `LobbyActivity.kt` makes visual consistency harder to review than XML-based components.

## Module Design

**Exports:**
- Kotlin top-level package visibility is simple; most helpers are objects or classes.
- `internal` is used for testable implementation details such as `GameplayFeedbackState`.

**State:**
- Prefer immutable model transformations for game state.
- Activity UI flags are mutable and lifecycle-sensitive.
- Any visual/navigation correction should preserve state ordering around animations and callbacks.

## XML/UI Conventions

- Shared primary/dark buttons use `BtnGold` and `BtnDark` from `themes.xml`.
- The visual system uses dark brown panels, gold borders/accents, custom fonts, and map artwork.
- Touch targets are commonly 44dp; new work should meet or exceed 48dp where layout permits.
- Avoid adding fixed widths/heights to `activity_gameplay_mock.xml`; it already contains 99 fixed dimensions.
- Use `ScrollView`/RecyclerView or responsive constraints for screens that can overflow with large fonts or small displays.

---
*Convention analysis: 2026-06-13*
*Update when linting, resources, or navigation standards are formalized*
