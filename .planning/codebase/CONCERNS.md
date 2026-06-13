# Codebase Concerns

**Analysis Date:** 2026-06-13

## Priority Summary

1. Mobile layout resilience and font scaling.
2. Back-stack and exit behavior through lobby/gameplay/profile.
3. Empty, disabled, and loading-like states in simulated online/profile surfaces.
4. Instrumented coverage for Activities, keyboard, dialogs, and orientation.
5. Reduction of visual-state coupling in gameplay and lobby.

## Tech Debt

**Oversized gameplay Activity and layout:**
- Issue: `GameplayMockActivity.kt` has about 2,730 lines and `activity_gameplay_mock.xml` about 1,504 lines with 99 fixed width/height values.
- Impact: Small visual changes can affect overlays, keyboard behavior, transitions, chat, cards, and the bottom panel simultaneously.
- Fix approach: Stabilize behavior first, then extract cohesive renderers/controllers and replace rigid dimensions with measured constraints.

**Lobby presentation constructed partly in Kotlin:**
- Issue: `LobbyActivity.kt` builds timing and advanced-option dialogs programmatically.
- Impact: Text wrapping, dialog width, touch targets, and styles are difficult to validate consistently across devices.
- Fix approach: Move stable dialog structure to XML or shared builders after visual behavior is locked.

**Hardcoded UI copy:**
- Issue: At least 172 layout text/hint/content-description values are hardcoded; more strings exist in Activities.
- Impact: Encoding inconsistencies, inaccessible labels, duplicated wording, and unreliable future localization.
- Fix approach: Migrate strings screen-by-screen while correcting the affected UI, starting with navigation labels and accessibility descriptions.

**Flat production package:**
- Issue: Menu, profile, lobby, gameplay, domain, and renderer code all live in `com.traidores.juego`.
- Impact: Ownership and safe modification boundaries are unclear.
- Fix approach: Do not reorganize during bug fixing; document boundaries now and defer package moves until stabilization is complete.

## Known and Likely Visual Bugs

**Small-screen and large-font clipping:**
- Evidence: Portrait menu/mode screens use centered non-scroll containers; gameplay has many fixed dimensions.
- Files: `activity_main.xml`, `activity_local_mode.xml`, `activity_online_mode.xml`, `activity_gameplay_mock.xml`.
- Trigger: Short displays, display zoom, or increased font scale.
- Fix approach: Add scroll/responsive constraints, autosizing only where appropriate, and test at multiple configurations.

**Gameplay chat/insets fragility:**
- Evidence: Chat dimensions are changed programmatically based on IME visibility while gameplay also uses `adjustResize`.
- Files: `GameplayMockActivity.kt`, `activity_gameplay_mock.xml`, `AndroidManifest.xml`.
- Trigger: Open keyboard in landscape, rotate/recreate, receive messages while typing, or use a split keyboard.
- Fix approach: Treat chat+IME as one tested state machine and verify content remains visible above the keyboard.

**Bottom gameplay information competes for vertical space:**
- Evidence: The bottom panel contains role art, three text rows, and multiple actions with fixed heights.
- Files: `activity_gameplay_mock.xml`.
- Trigger: Long role hints or increased text scale.
- Fix approach: Define minimum/maximum text behavior and reserve panel height using constraints rather than repeated fixed values.

**Dialogs may overflow compact landscape:**
- Evidence: Lobby dialogs receive fixed dp widths through `showLandscapeDialog()`.
- Files: `LobbyActivity.kt`.
- Trigger: Small landscape devices or large text.
- Fix approach: Cap width against available window bounds and ensure dialog content scrolls independently from actions.

## Navigation Risks

**Gameplay back behavior is implicit and multi-step:**
- Symptoms: Back first closes reveal/chat/event-log states; with the event log expanded by default, the first back may appear not to leave gameplay.
- File: `GameplayMockActivity.kt`.
- Risk: Users may interpret back as broken or accidentally leave the match after dismissing overlays.
- Fix approach: Define explicit priority and add an exit confirmation for an active match without changing game features.

**Route behavior is duplicated:**
- Issue: Each Activity manually starts the next Activity and calls `finish()` independently.
- Files: All `*Activity.kt` navigation handlers.
- Impact: Back-stack regressions can be introduced without compiler or unit-test feedback.
- Fix approach: Add route smoke tests before centralizing navigation decisions.

**Profile edit draft is not saved across recreation:**
- Evidence: `ProfileActivity.kt` creates `draftProfile` in `onCreate` but has no `onSaveInstanceState`.
- Trigger: Process recreation or configuration change while editing.
- Impact: Unsaved edits and editing mode can reset unexpectedly.
- Fix approach: Save draft/edit mode or intentionally lock orientation with a documented discard flow.

## Empty and Disabled States

**Lobby browser has no real empty state:**
- Evidence: `LobbyBrowserActivity.kt` always renders a hardcoded list.
- Risk: Future remote empty/error results have no designed container.
- Current-scope fix: Add a reusable empty-state presentation without connecting a backend.

**Online mode labels describe simulated behavior as real:**
- Evidence: Simulated lobbies and local factories are used behind online actions.
- Risk: Disabled/full/unavailable state semantics can become inconsistent.
- Current-scope fix: Ensure buttons, status labels, and unreachable actions are visually and verbally coherent; do not add networking.

**Profile statistics and achievements are placeholder-driven:**
- Risk: Empty/locked/no-selection states may be visually ambiguous.
- Files: `ProfileActivity.kt`, `activity_profile.xml`.
- Current-scope fix: Define stable placeholder and no-data presentation only.

## Accessibility and Usability

**Touch targets:**
- Several icon buttons use 44dp dimensions.
- Recommendation: Move important navigation/actions toward 48dp minimum where layout permits.

**Content descriptions:**
- Many descriptions are hardcoded and decorative images use mixed conventions.
- Recommendation: Audit interactive images/buttons; use `@null` only for genuinely decorative artwork.

**Contrast and disabled states:**
- Disabled controls often rely mainly on alpha.
- Recommendation: Verify label contrast and add semantic disabled copy where state is not obvious.

## Performance and Lifecycle

**Dynamic view recreation:**
- Gameplay rebuilds chat bubbles and player/card views during renders.
- Risk: Extra allocations and animation churn on lower-end devices.
- Improvement path: Measure before optimizing; reuse adapters/holders only after correctness.

**Handlers and animations:**
- Gameplay coordinates several `Handler` callbacks and animators.
- Risk: Ordering bugs around pause/resume and overlay dismissal.
- Mitigation: Existing cleanup is substantial, but Activity-level instrumentation is missing.

**Large packaged artwork/audio:**
- Risk: APK size and memory pressure, especially with full-resolution drawable images.
- Improvement path: Audit resource dimensions and move non-density-scaled art deliberately; avoid visual quality regressions.

## Test Coverage Gaps

**No Android instrumentation tests:**
- What's not tested: Activity startup, view binding, click routes, back stack, dialogs, keyboard, orientation, and process recreation.
- Priority: Critical for the requested stabilization milestone.

**No visual regression baseline:**
- What's not tested: Clipping, overlap, typography, spacing, and empty states.
- Priority: High.

**No CI:**
- What's not tested automatically on push: build health and unit regressions.
- Priority: Medium during stabilization; a simple Gradle test workflow would reduce accidental breakage.

## Scope Guard

- Do not add new roles, Firebase, real online networking, account systems, purchases, or gallery uploads in this milestone.
- Avoid broad architecture rewrites while correcting bugs.
- Preserve the established medieval/gold visual identity while improving responsive behavior.

---
*Concerns audit: 2026-06-13*
*Update as stabilization issues are verified and resolved*
