# Pitfalls Research

**Domain:** Android game UI stabilization
**Researched:** 2026-06-13
**Confidence:** HIGH

## Critical Pitfalls

### 1. Shrinking Instead of Making Layouts Responsive

**What goes wrong:** Text becomes unreadable while clipping still appears on other devices.

**Why it happens:** A screenshot-specific fix changes font or view size without addressing constraints and available space.

**How to avoid:** Prefer `wrap_content`, constraints, weights only where appropriate, bounded scrolling, and minimum/maximum behavior.

**Warning signs:** Repeated device-specific dp changes and progressively smaller typography.

**Phase to address:** Gameplay/chat, lobby, and profile layout phases.

### 2. Treating the Keyboard as a Fixed Rectangle

**What goes wrong:** Chat jumps, leaves black gaps, or hides messages on split keyboards and different devices.

**Why it happens:** The layout guesses keyboard height or mixes `adjustResize` with unrelated manual dimensions.

**How to avoid:** Read IME visibility and insets, then render chat from the measured viewport.

**Warning signs:** Magic height constants and different results between emulator and phone.

**Phase to address:** Gameplay and chat phase.

### 3. Multiple Independent Back Handlers

**What goes wrong:** Back closes the wrong layer, appears broken, or exits the match unexpectedly.

**Why it happens:** Each overlay and Activity intercepts back without a single priority order.

**How to avoid:** Define and test one ordered back policy using `OnBackPressedDispatcher`.

**Warning signs:** Repeated back overrides, direct `finish()` calls, and boolean combinations without a state table.

**Phase to address:** Navigation stabilization phase.

### 4. Visual and Behavioral State Divergence

**What goes wrong:** A button says full but remains clickable, or looks disabled while still performing an action.

**Why it happens:** Label, style, enabled flag, and handler are updated in separate branches.

**How to avoid:** Render every property from one availability state.

**Warning signs:** Repeated conditionals for the same lobby or action status.

**Phase to address:** Lobby and gameplay state phases.

### 5. Losing Draft State on Recreation

**What goes wrong:** Profile edits disappear after a system recreation.

**Why it happens:** Draft data exists only in Activity fields.

**How to avoid:** Persist lightweight draft/edit state with saved state or a ViewModel plus saved-state support.

**Warning signs:** `onCreate` always rebuilds the draft from defaults.

**Phase to address:** Profile phase.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Fixed dialog width | Fast visual match | Overflow on compact landscape | Only if bounded by available width |
| Hardcoded strings in layouts | Fast iteration | Inconsistent copy and accessibility | Migrate touched strings during fixes |
| More booleans for overlays | Small local change | Impossible state combinations | Only for isolated binary state |
| Rebuilding all views on every render | Simple code | Allocation and scroll-position churn | Accept until measured, except chat position bugs |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Disabled state shown only with alpha | Reason is unclear | Combine style, label, enabled state, and context |
| Icon button below 48 dp target | Difficult to tap | Keep visual icon small inside a larger target |
| Empty list shown as blank panel | Looks broken | Show concise state and available next action |
| Keyboard covers latest messages | Conversation becomes unusable | Reserve visible chat area and keep latest content reachable |

## "Looks Done But Isn't" Checklist

- [ ] Text fits with larger font settings, not only default font scale.
- [ ] Dialog actions remain visible on compact landscape.
- [ ] System back and visible back buttons produce coherent results.
- [ ] Full and disabled buttons reject taps as well as looking disabled.
- [ ] Profile draft survives Activity recreation.
- [ ] Chat shows recent messages while the IME is open.
- [ ] Important icon controls have usable touch areas and descriptions.
- [ ] Empty states are reachable and not just mocked in XML.

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Rigid layout fixes | Each screen phase | User screenshots on target phone and layout review |
| Fixed keyboard assumptions | Gameplay/chat | Open IME, send/receive messages, close and reopen |
| Back-handler conflict | Navigation | Route and overlay back matrix |
| State divergence | Lobby/gameplay | Compare text, style, enabled state, and action |
| Lost profile draft | Profile | Recreate Activity while editing |

## Sources

- https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views
- https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility
- https://developer.android.com/guide/navigation/navigation-custom-back
- https://developer.android.com/topic/libraries/architecture/views/saving-states-views
- https://developer.android.com/guide/topics/ui/accessibility/views/apps-views

---
*Pitfalls research for: App Traidores stabilization*
*Researched: 2026-06-13*
