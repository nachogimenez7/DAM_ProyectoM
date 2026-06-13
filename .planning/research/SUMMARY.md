# Project Research Summary

**Project:** App Traidores
**Domain:** Existing Android Views game stabilization
**Researched:** 2026-06-13
**Confidence:** HIGH

## Executive Summary

App Traidores should be stabilized incrementally with its current Kotlin, Activity, and XML stack. The research does not justify a new UI framework, navigation migration, or broad architecture rewrite. The effective approach is to remove selected rigid dimensions, make screen state explicit, and use AndroidX lifecycle, back, inset, and saved-state APIs where the current behavior is fragile.

The highest-risk area is gameplay chat because keyboard visibility, overlays, event logs, bottom role information, and fixed landscape dimensions compete for the same viewport. Navigation and profile recreation are the next systemic risks. Every correction should preserve the medieval visual identity and be verified against compact-phone bounds, current orientations, clear disabled states, and predictable back behavior.

## Key Findings

### Recommended Stack

- Keep Kotlin, Activities, Android Views, XML, and existing visual resources.
- Use `WindowInsetsCompat` for keyboard/system UI measurements.
- Use `OnBackPressedDispatcher` for ordered back behavior.
- Use saved state or a small ViewModel for recreation-sensitive profile drafts.
- Add focused ActivityScenario/Espresso coverage when the testing phase begins.

### Required Behaviors

- Content and actions fit compact phones without overlap.
- Keyboard-open chat remains readable and usable.
- Back closes transient layers before leaving the screen or match.
- Full, blocked, disabled, and empty states are coherent.
- Profile edit state survives normal Activity recreation.
- Important controls have at least 48 dp touch targets where layout permits.

### Architecture Approach

Keep Activities as screen owners, but introduce small screen-state models and cohesive render helpers when a fix otherwise duplicates conditional UI mutation. XML owns structure and responsive sizing; Activities own lifecycle and navigation; saved state owns lightweight drafts.

### Critical Pitfalls

1. **Shrinking to fit** - correct constraints and scrolling instead.
2. **Fixed keyboard assumptions** - use measured IME insets.
3. **Competing back handlers** - define one ordered policy.
4. **Visual-only disabled states** - derive appearance and behavior from one state.
5. **Activity-only drafts** - preserve profile edit state across recreation.

## Implications for Roadmap

### Phase 1: Verification Baseline and State Inventory
Define screen matrices, expected back behavior, target compact-phone bounds, and manual acceptance checks.

### Phase 2: Gameplay and Chat Stability
Correct keyboard/inset behavior, visible conversation area, overlapping panels, and gameplay overlay state.

### Phase 3: Lobby Stability
Correct compact-landscape dialogs, button availability states, and empty presentations.

### Phase 4: Profile Stability
Correct layout fit, edit affordances, selected item presentation, and recreation-safe drafts.

### Phase 5: Navigation and Regression Guard
Normalize back/route outcomes across the four surfaces and add focused test scaffolding or checks without executing compilation.

### Phase Ordering Rationale

- Baseline criteria come first so fixes are not screenshot-specific.
- Gameplay/chat comes next because it has the greatest state and layout coupling.
- Lobby and profile can then be stabilized independently.
- Cross-screen navigation is finalized after individual screen states are reliable.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Existing stack and official Android guidance align |
| Behaviors | HIGH | Directly derived from current defects and platform expectations |
| Architecture | HIGH | Incremental patterns fit the existing Activity-based code |
| Pitfalls | HIGH | Confirmed by codebase map and official lifecycle/layout guidance |

### Gaps to Validate During Implementation

- Exact clipping and spacing must be confirmed using the user's target phone screenshots or a connected device/emulator.
- Keyboard behavior varies by IME; the user's real keyboard remains the most valuable manual check.
- Some navigation problems may only become evident when each current route is exercised.

## Sources

### Primary

- https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
- https://developer.android.com/develop/ui/views/layout/sw-keyboard
- https://developer.android.com/guide/navigation/custom-back
- https://developer.android.com/topic/libraries/architecture/views/saving-states-views
- https://developer.android.com/guide/components/activities/testing
- https://developer.android.com/guide/topics/ui/accessibility/views/apps-views

---
*Research completed: 2026-06-13*
*Ready for roadmap: yes*
