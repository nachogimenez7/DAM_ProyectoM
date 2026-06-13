# Architecture Research

**Domain:** Incremental stabilization of an Android Views application
**Researched:** 2026-06-13
**Confidence:** HIGH

## Recommended Architecture

The current Activity-based structure should remain. Stabilization should introduce only small ownership boundaries where a screen currently mixes state decisions, layout mutation, and navigation.

```text
User input
  -> Activity event handler
  -> explicit screen/overlay state
  -> render function
  -> XML Views

System event
  -> insets / lifecycle / back dispatcher
  -> explicit state transition
  -> render function
```

### Component Responsibilities

| Component | Responsibility | Recommended Treatment |
|-----------|----------------|-----------------------|
| Activity | Lifecycle, navigation, event registration | Keep, but reduce duplicated state decisions |
| Screen state | Current overlay, edit, keyboard, or availability state | Represent explicitly with small enums/data classes where useful |
| Renderer/helper | Apply one state consistently to Views | Extract only cohesive repeated blocks |
| XML layout | Structure, constraints, scrolling, minimum sizes | Remove fragile fixed dimensions selectively |
| Saved state | Recreation-sensitive transient data | Store only lightweight profile/edit state |

## Architectural Patterns

### Ordered Back Policy

Back should evaluate states in a fixed order, for example: close modal, close expanded event log, close chat, confirm active-match exit, then finish Activity. Use `OnBackPressedDispatcher` so this behavior is lifecycle-aware.

### State-Driven Rendering

For controls such as full lobby buttons, blocked timers, role actions, and chat visibility, derive text, enabled state, alpha/background, and click handler from the same state. This prevents visual and behavioral disagreement.

### Measured Insets

Use window and IME insets as input to layout decisions. Do not infer keyboard state from a fixed pixel threshold or assign a universal keyboard height.

### Minimal Saved State

Persist profile draft fields and editing mode across recreation. Avoid storing large drawables or domain objects in a Bundle.

## Data Flows

### Chat and Keyboard

```text
IME visibility/insets
  -> available gameplay viewport
  -> chat panel height/position
  -> message list scrolls to latest visible item
```

### Profile Editing

```text
Saved profile
  -> editable draft
  -> render fields
  -> save or discard
  -> recreation restores draft and edit mode
```

### Lobby Availability

```text
Lobby model
  -> availability state (joinable/full/in progress)
  -> button label + enabled state + style + click behavior
```

## Build Order

1. Establish shared verification criteria and screen-state inventories.
2. Correct gameplay and chat because they have the highest layout and state coupling.
3. Correct lobby dialogs and availability states.
4. Correct profile layout and draft restoration.
5. Normalize navigation/back behavior and add focused regression coverage.

## Anti-Patterns

### Layout Fixes That Mutate Unrelated States

Changing shared panel heights in many event handlers creates hidden dependencies. Compute layout from current state in one place instead.

### Full Architecture Rewrite During Stabilization

Moving every Activity to a new framework would obscure whether a change fixes a bug or introduces one. Extract only when it directly reduces the risk of the current correction.

### Visual-Only Disabled States

Alpha alone is not a state model. Disabled controls must also reject actions and communicate the reason through text or surrounding context.

## Sources

- https://developer.android.com/guide/navigation/custom-back
- https://developer.android.com/develop/ui/views/layout/sw-keyboard
- https://developer.android.com/topic/libraries/architecture/views/saving-states-views
- https://developer.android.com/topic/libraries/architecture/views/viewmodel
- https://developer.android.com/guide/components/activities/testing

---
*Architecture research for: App Traidores stabilization*
*Researched: 2026-06-13*
