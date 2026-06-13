# Stack Research

**Domain:** Stabilization of an existing Android Views game UI
**Researched:** 2026-06-13
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | Existing 1.9.22 | Activity and UI behavior | Preserve the current language and avoid migration risk |
| Android Views and XML | Existing platform stack | Layouts and screen structure | The application is already View-based; Compose migration is outside scope |
| AndroidX Activity/AppCompat | Existing compatible versions | Lifecycle and back handling | Provides lifecycle-aware back dispatch and Activity integration |
| ConstraintLayout and scrolling containers | Existing dependencies | Responsive placement | Replace selected rigid dimensions without redesigning the app |

### Supporting Libraries

| Library | Purpose | When to Use |
|---------|---------|-------------|
| WindowInsetsCompat | System bar and IME visibility/insets | Gameplay chat and any screen obscured by keyboard or system UI |
| OnBackPressedDispatcher | Ordered back behavior | Gameplay overlays, chat, dialogs, and active-match exit |
| ViewModel or saved instance state | Preserve transient UI state | Profile edit drafts and lightweight recreation-sensitive state |
| AndroidX Test ActivityScenario/Espresso | Activity, lifecycle, navigation, and UI tests | Add tests without changing production architecture |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Layout Inspector | Inspect clipping and bounds | Run manually on the user's phone or emulator |
| Accessibility Scanner | Detect labels and touch target issues | Supplement, not replace, manual checks |
| Android Studio device matrix | Compare screen and font configurations | Prioritize compact phones and current orientations |

## Installation

No new production dependency is required by default. Add AndroidX Test dependencies only when the instrumentation-test phase begins.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Incremental Views/XML fixes | Rewrite in Compose | Only in a separate future migration milestone |
| ActivityScenario and Espresso | UI Automator | Use UI Automator only for system-level interactions Espresso cannot cover |
| Small state extraction | Full MVVM rewrite | Only after stabilization if maintenance cost remains high |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Global UI scaling or shrinking | Hides symptoms and damages readability | Correct constraints, scrolling, wrapping, and state-specific sizing |
| New navigation framework migration | Expands scope and regression surface | Clarify existing Intent and back-stack behavior first |
| Hardcoded keyboard heights | IME size varies by device and mode | WindowInsetsCompat and measured available space |
| Broad package reorganization | Creates unrelated churn | Small local helpers or state holders |

## Version Compatibility

- The project currently targets API 34. Edge-to-edge becomes enforced when targeting API 35 on Android 15, so inset handling should be corrected before a future target upgrade.
- `OnBackPressedDispatcher` is the compatible AndroidX path for custom back behavior and predictive-back readiness.
- Instrumented tests require a device or emulator when they are eventually executed.

## Sources

- https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
- https://developer.android.com/develop/ui/views/layout/sw-keyboard
- https://developer.android.com/guide/navigation/custom-back
- https://developer.android.com/guide/components/activities/testing

---
*Stack research for: App Traidores stabilization*
*Researched: 2026-06-13*
