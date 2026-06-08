---
last_mapped: 2026-06-03
focus: quality
---

# Conventions

## Summary
The app is a Kotlin Android prototype built around XML layouts, Activity-driven screens, and a small extracted game-domain layer. UI code remains imperative and direct, while local game rules now live mostly in `GameEngine` and immutable model copies.

## Kotlin and Activity Style
- Activities live under `app/src/main/java/com/traidores/juego`.
- Screen classes extend `BaseActivity`, which centralizes music lifecycle hooks.
- `onCreate` usually calls `setContentView`, resolves views with `findViewById`, then wires inline `setOnClickListener` handlers.
- Navigation uses explicit `Intent(this, TargetActivity::class.java)`.
- Long-running UI timers use `Handler(Looper.getMainLooper())` and remove callbacks in lifecycle cleanup.
- There is no ViewBinding, DataBinding, Jetpack Compose, dependency injection, or MVVM layer.

## Domain and State
- Core local game state is represented by immutable `data class` models: `GameSession`, `GamePlayer`, `GameRole`, `GameChatMessage`, and `GameMap`.
- Game progression is expressed with the `GamePhase` enum.
- `GameEngine` owns phase resolution, winner checks, target validation, chat permissions, auto-advance decisions, and private/public announcement logic.
- `LocalGameFactory` owns mock session creation, map selection, player add/remove, and random role assignment.
- Activity state is still stored in mutable Activity fields, especially in `GameplayMockActivity`.
- Cross-screen session transfer uses `Serializable` extras through `LobbyActivity.EXTRA_SESSION`.

## UI and Resources
- Layouts are XML-first and mostly use `RelativeLayout` with nested `LinearLayout`; gameplay also builds player cards programmatically in a `FrameLayout`.
- Shared visual identity comes from XML drawable backgrounds, custom fonts, image assets, and centralized colors in `res/values/colors.xml`.
- Button and theme styling is in `res/values/themes.xml` and drawable shape resources such as `bg_btn_gold.xml`.
- Role/map image lookup is partly dynamic via `resources.getIdentifier`, with Android fallback drawables when a resource name is missing.
- Text is predominantly Spanish, but many strings are hardcoded in Kotlin or layout XML rather than centralized in `strings.xml`.

## Persistence and Integrations
- Sound/music settings use `SharedPreferences` named `TraidoresPrefs` with keys such as `sound_on`, `music_volume`, and `voices_volume`.
- `MusicManager` owns menu music playback and observes Activity start/stop events through `BaseActivity`.
- Online mode and login/register flows are currently mock UI flows; no remote API, database, or auth provider is wired.

## Error Handling and Validation
- Invalid local-game actions are usually blocked by guards and surfaced with `Toast` messages.
- Engine methods return the unchanged `GameSession` for invalid target/action inputs.
- Dynamic image lookup falls back to a generic gallery drawable.
- There is no structured logging, crash reporting, or app-wide error boundary.

## Quality Risks
- `GameplayMockActivity` is large and mixes rendering, timers, input routing, and state mutation.
- `LocalGameFactory.assignRoles` uses unseeded `shuffled()`, so role assignment is intentionally random and not deterministic.
- Hardcoded user-facing strings make localization and copy review difficult.
- `Serializable` is simple for the prototype but can become fragile as model shape changes.
- No lint/style configuration beyond Android/Gradle defaults is visible.
