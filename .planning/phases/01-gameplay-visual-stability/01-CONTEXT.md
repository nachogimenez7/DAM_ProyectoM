# Phase 1: Gameplay Visual Stability - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning
**Source:** GSD plan-phase inline context

<domain>
## Phase Boundary

This phase stabilizes only the current gameplay screen in landscape. It does not add roles, online behavior, new game mechanics, new profile features, or a new visual direction.

The goal is that the player can use the existing gameplay flow without important content being cut, hidden, overlapped, or blocked by stale overlays.
</domain>

<decisions>
## Implementation Decisions

### Scope
- D-01: Focus on `GameplayMockActivity.kt`, `activity_gameplay_mock.xml`, and small helper classes only when directly required.
- D-02: Do not redesign lobby, profile, chat keyboard behavior, or navigation in this phase except where gameplay overlays directly depend on them.
- D-03: Keep the existing Kotlin/XML Activity architecture.
- D-04: Avoid broad refactors; extract or adjust only when it reduces immediate risk.

### Product Behavior
- D-05: The event log must start collapsed so the center of the map remains visible at match start.
- D-06: Modal vote/recount/tie/result windows must not show stale action banners such as vote confirmation.
- D-07: Hidden overlays must not reserve space or block visible controls.
- D-08: Disabled or unavailable gameplay actions must look disabled and must not execute.

### Verification
- D-09: The user performs final visual validation in Android Studio/device.
- D-10: Automated work can run local JVM tests when useful, but this phase should not depend on instrumentation or screenshot tooling.
</decisions>

<canonical_refs>
## Canonical References

### Planning
- `.planning/ROADMAP.md` - Phase 1 goal, success criteria, and plan names.
- `.planning/REQUIREMENTS.md` - GAME-01 through GAME-04.
- `.planning/codebase/CONCERNS.md` - Current high-risk gameplay files and state concerns.
- `.planning/codebase/ARCHITECTURE.md` - Gameplay data flow and Activity/engine boundaries.
- `.planning/codebase/TESTING.md` - Existing test coverage and gaps.

### Gameplay Code
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` - Gameplay orchestration and overlay state.
- `app/src/main/res/layout/activity_gameplay_mock.xml` - Main gameplay layout and overlay definitions.
- `app/src/main/java/com/traidores/juego/VoteResultAnimator.kt` - Vote result, tie result, no-expulsion, expulsion animation.
- `app/src/main/java/com/traidores/juego/GameplayTableUi.kt` - Gameplay copy, public events, and presentation state.
- `app/src/main/java/com/traidores/juego/GameEngine.kt` - Local rules and phase transitions.
</canonical_refs>

<specifics>
## Specific Ideas

- Fresh match starts with event log collapsed.
- Gameplay HUD should keep header, event summary, player seats, bottom panel, and primary action visible.
- Vote/tie/result windows should remain compact and centered.
- Banners must be dismissed before blocking overlays appear.
- Role hints and bottom-panel text need predictable max lines, autosize, or truncation behavior.
- Overlay closing/opening should be audited against `resumeGameFlowAfterBlockingUi()`.
</specifics>

<deferred>
## Deferred Ideas

- Chat keyboard stabilization belongs to Phase 2.
- Lobby options/dialog stability belongs to Phase 3.
- Profile draft and selector stability belongs to Phase 4.
- Global navigation/back behavior belongs to Phase 5.
- New roles, Firebase, and online services remain out of scope.
</deferred>

---

*Phase: 01-gameplay-visual-stability*
*Context gathered: 2026-06-16*
