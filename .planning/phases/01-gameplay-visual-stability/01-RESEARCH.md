# Phase 1: Gameplay Visual Stability - Research

**Date:** 2026-06-16
**Status:** Complete

## Current Situation

Gameplay is the most complex screen in the app. The current implementation is a large Activity plus a large XML layout:

- `GameplayMockActivity.kt`: about 3,479 lines.
- `activity_gameplay_mock.xml`: about 1,946 lines.
- `VoteResultAnimator.kt`: owns the richest vote/result overlay behavior.
- `GameplayTableUi.kt`: centralizes phase text, events, and feedback copy.

Recent work improved vote windows, tie vote layout, expulsion boot art, event log default state, audio effects, and banner cleanup. That means Phase 1 should not start by redesigning the whole screen. It should first lock down the states that are already close to acceptable.

## Risk Areas

### Fixed Dimensions

The gameplay XML still uses many fixed dp values. This is acceptable for a controlled landscape target, but fragile on compact phones, display zoom, and larger font settings.

Research conclusion: avoid a full responsive rewrite now. Instead, identify high-risk fixed areas and correct only the ones tied to visible clipping or blocked controls.

### Overlay State

Several modal states can compete:

- Initial role preview.
- Chat panel.
- Event log.
- Vote result.
- Tie vote.
- Death reveal.
- Silence reveal.
- Oracle reveal.
- Jester victory.
- Winner reveal.
- Day/night transition.

Research conclusion: Phase 1 needs an overlay-state audit before more visual edits. The dangerous failures are stale banners, invisible views blocking touches, and hidden panels reserving space.

### Bottom Panel

The bottom gameplay panel has role art, player name, role name, status chip, hint text, and action buttons. It is a common place for text clipping.

Research conclusion: handle this as part of the main layout audit, not as a separate feature.

### Event Log

The product decision is that the log starts collapsed so the center of the map remains visible. It can be manually expanded.

Research conclusion: verify fresh match default, phase changes, chat open/close, and overlay returns do not accidentally expand it.

### Vote And Result Windows

The newer vote/tie windows are visually acceptable, but need state hygiene:

- No stale vote-registered banner.
- No cut voter tokens.
- No overflow in two-player and multi-player tie cases.
- No hidden overlay intercepting gameplay after dismissal.

Research conclusion: keep the current visual family and audit behavior.

## Recommended Plan Shape

Phase 1 should be two plans, matching ROADMAP:

1. Audit and fix the main gameplay visual structure.
2. Consolidate overlay/action state so hidden/blocked/completed states behave consistently.

This keeps work small enough to test manually and reduces risk before Phase 2 chat-keyboard work.

## Validation Strategy

Automated:

- Run `.\gradlew.bat :app:testDebugUnitTest` when logic/helper code changes.
- Add/update local unit tests only for extracted pure helpers or engine/table UI changes.

Manual:

- Fresh match start.
- Night phase with event log collapsed.
- Day phase with bottom panel actions.
- Vote registered -> recount window.
- Tie -> desempate window.
- Second tie/no expulsion.
- Expulsion boot.
- Death/silence/oracle/jester/winner overlays if reachable.

## Research Complete

The phase should proceed to planning with no new research needed.
