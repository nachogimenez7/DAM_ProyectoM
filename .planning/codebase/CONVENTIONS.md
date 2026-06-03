---
last_mapped: 2026-06-03
focus: quality
---

# Conventions

## Summary
The codebase follows pragmatic Android Activity conventions with direct view binding via `findViewById`, hardcoded local data, and XML-driven UI. The style is simple and prototype-oriented.

## Activity Pattern
- Activities extend `BaseActivity`, not directly `AppCompatActivity`.
- Activity setup happens in `onCreate`.
- Layouts are assigned using `setContentView(R.layout.activity_...)`.
- Click handlers are attached inline with `setOnClickListener`.
- Navigation uses explicit `Intent(this, TargetActivity::class.java)`.

## View Access
- Views are accessed with `findViewById`.
- There is no Android ViewBinding or DataBinding.
- Local one-off views are often `val` variables in `onCreate`.
- Reused gameplay views are Activity fields in `GameplayMockActivity.kt`.

## State Management
- Simple screen state is stored in Activity fields.
- Game state is represented by immutable Kotlin data classes in `GameModels.kt`.
- State transitions are implemented by `session = session.copy(...)`.
- There is no separate state reducer or domain service yet.

## Data Modeling
- `GameSession`, `GamePlayer`, `GameRole`, and `GameMap` are data classes.
- `GamePhase` is an enum.
- Intent payloads use `Serializable`.
- Role identities use string keys such as `asesino`, `policia`, `medico`, and `aldeano`.

## Resource Conventions
- UI colors are centralized in `app/src/main/res/values/colors.xml`.
- Button styles are in `app/src/main/res/values/themes.xml`.
- Drawable backgrounds are XML shapes under `app/src/main/res/drawable`.
- Image resources are referenced directly in XML or dynamically by resource name.

## Text and Localization
- Some global strings live in `app/src/main/res/values/strings.xml`.
- Many gameplay, roles, and Toast strings are hardcoded in Kotlin or layout XML.
- `OpcionesActivity.kt` simulates language selection but does not use Android resource localization.
- Current copy generally uses Spanish text, often ASCII-only without accents.

## Error Handling
- Missing dynamic drawables fall back to `android.R.drawable.ic_menu_gallery`.
- Invalid local game selections show Toast messages.
- There is no structured logging or exception handling strategy.
- No runtime validation exists for every possible impossible `GameSession` state.

## UI Layout Style
- Layouts mostly use `RelativeLayout` and nested `LinearLayout`.
- Landscape gameplay uses side player columns, a top status banner, and a compact bottom HUD.
- Menu/setup screens use full-screen background images plus overlays.
- Shared visual identity is implemented through fonts, drawable backgrounds, and image assets.

## Code Organization
- Domain models and game factory are grouped in `GameModels.kt`.
- Gameplay logic is currently concentrated in `GameplayMockActivity.kt`.
- Role catalog content is concentrated in `RolesActivity.kt`.
- This organization is acceptable for prototype scale but creates large files as behavior grows.

