---
phase: 1
plan: 01-02
title: "Consolidate gameplay overlay and action states"
type: implementation
wave: 2
depends_on:
  - 01-01
files_modified:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/VoteResultAnimator.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/java/com/traidores/juego/GameEngine.kt
  - app/src/test/java/com/traidores/juego/GameEngineTest.kt
autonomous: true
requirements:
  - GAME-03
  - GAME-04
---

<objective>
Make gameplay overlays and action states predictable: visible overlays should own input, hidden overlays should not block or reserve space, stale banners should disappear before modal windows, and unavailable actions should not execute.
</objective>

<truths>
- Do not redesign the vote windows; audit and harden the current implementation.
- Do not change role rules unless a UI state exposes a clear engine mismatch.
- Chat keyboard behavior is Phase 2 unless it directly blocks a gameplay overlay.
- Preserve current animations and audio unless they cause stale state or blocked controls.
</truths>

<tasks>

## Task 1: Build overlay priority audit

type: audit
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Trace `resumeGameFlowAfterBlockingUi()`, vote result opening, tie vote opening, reveal opening, chat opening, and cancellation paths.
  - Document the intended priority order of blocking UI states in the implementation summary.
  - Identify any path where a hidden overlay could remain clickable, visible, or reserve space.
verify:
  - Priority order is explicit before editing.
acceptance_criteria:
  - GAME-04 risks are known before changes.

## Task 2: Harden banner cleanup before modal gameplay windows

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Ensure action feedback banners are dismissed before vote result, no-expulsion, tie vote, death/silence/oracle/jester/winner overlays when relevant.
  - Avoid duplicate cleanup logic if a small helper improves safety.
verify:
  - Vote-registered or similar banners do not appear over modal vote/reveal windows.
acceptance_criteria:
  - GAME-04: opening a modal layer does not leave stale UI from the previous action.

## Task 3: Audit vote, tie, expulsion, and no-expulsion windows

type: implementation
files:
  - app/src/main/java/com/traidores/juego/VoteResultAnimator.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
action:
  - Verify recount cards, voter tokens, tie vote cards, buttons, and expulsion/no-expulsion states do not clip in expected player counts.
  - Keep the current compact design family.
  - Fix only concrete spacing/visibility/touch issues found.
verify:
  - Two-candidate tie, repeated tie, majority reached, no-expulsion, and expulsion animation remain visually coherent.
acceptance_criteria:
  - GAME-03 and GAME-04: vote states are clear and modal windows are self-contained.

## Task 4: Verify action availability and blocked states

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/GameEngine.kt
  - app/src/test/java/com/traidores/juego/GameEngineTest.kt
action:
  - Check that unavailable actions are disabled or rejected consistently.
  - Check that eliminated, silenced, protected, already-used, and invalid-target states do not execute hidden actions.
  - Add or update unit tests only when engine behavior changes.
verify:
  - Existing unit tests pass if touched.
  - Manual gameplay shows disabled states clearly enough.
acceptance_criteria:
  - GAME-03: blocked or completed controls do not execute actions.

</tasks>

<verification>

Automated:

- Run `git diff --check`.
- Run `.\gradlew.bat :app:testDebugUnitTest` if Kotlin logic changes.

Manual:

- Vote as human and verify the confirmation banner disappears before recount.
- Force tie and verify tie vote window owns input.
- Trigger no-expulsion and expulsion result states.
- Open/close event log and chat around gameplay phases and confirm no hidden overlay blocks controls.
- Check death/silence/oracle/jester/winner overlays if reachable.

</verification>

<success_criteria>

- GAME-03 and GAME-04 are covered by concrete overlay/action-state fixes or documented as already acceptable.
- Stale banners do not appear on top of vote/reveal/result windows.
- Hidden overlays do not intercept touches after dismissal.
- Disabled or unavailable gameplay controls do not execute actions.
- No new role or online behavior is introduced.

</success_criteria>
