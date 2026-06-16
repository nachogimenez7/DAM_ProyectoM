---
phase: 1
plan: 01-01
title: "Audit and fix main gameplay visual structure"
type: implementation
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/java/com/traidores/juego/GameplayTableUi.kt
  - app/src/test/java/com/traidores/juego/GameplayTableUiTest.kt
autonomous: true
requirements:
  - GAME-01
  - GAME-02
---

<objective>
Audit and correct the main gameplay layout so the header, event summary, map center, player seats, bottom role panel, and primary action controls remain readable and usable on compact landscape phones.
</objective>

<truths>
- The event log starts collapsed by product decision.
- Do not add gameplay features.
- Do not redesign chat keyboard behavior in this plan.
- Preserve current visual identity: dark panels, gold accents, historical map backgrounds.
- Keep changes tightly scoped to visible gameplay structure and copy/size behavior.
</truths>

<tasks>

## Task 1: Inventory current gameplay visual states

type: audit
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
action:
  - List the gameplay states that affect the main HUD: fresh start, night, day, voting, eliminated, silenced, protected, event log collapsed/expanded, bottom panel actions.
  - Identify which views use fixed heights/widths in the header, event log, player seats, and bottom panel.
  - Record concrete likely clipping/overlap targets in the plan notes or implementation summary.
verify:
  - No code change is required for this task unless an obvious one-line cleanup is discovered.
acceptance_criteria:
  - Main HUD risk areas are known before editing.

## Task 2: Stabilize event log collapsed baseline

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
action:
  - Verify the XML default and Kotlin state both start collapsed.
  - Ensure render code does not expand the log during fresh match setup or routine phase render.
  - Keep manual expand/collapse behavior intact.
verify:
  - Fresh match starts with the map center visible.
  - The collapsed row still shows the latest event summary.
acceptance_criteria:
  - GAME-01: event log does not cover the center map by default.

## Task 3: Correct bottom panel text and controls where needed

type: implementation
files:
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/GameplayTableUi.kt
action:
  - Review current player name, status chip, role name, role hint, reveal button, and action button sizing.
  - Add max lines, ellipsize, autosize, or slightly adjusted dimensions only where text can be cut or overlap.
  - Keep important buttons visible and understandable.
verify:
  - Long player names and role/action labels do not hide primary actions.
  - Eliminated/silenced/protected states remain readable.
acceptance_criteria:
  - GAME-02: role/name/help text stays readable within available panel space.

## Task 4: Verify player seats and header do not compete with modal-free gameplay

type: implementation
files:
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Check player side seats, header controls, timer, chat icon, settings icon, and phase title for overlap.
  - Make minimal spacing or text-size corrections if the current layout leaves known collisions.
verify:
  - Header controls remain tappable.
  - Player names/status indicators remain readable enough for gameplay.
acceptance_criteria:
  - GAME-01: core gameplay HUD remains visible without modal overlays.

</tasks>

<verification>

Automated:

- Run `git diff --check`.
- If Kotlin or pure helper behavior changes, run:
  - `.\gradlew.bat :app:testDebugUnitTest`

Manual:

- Start a fresh local match.
- Confirm the event log is collapsed at match start.
- Check night/day/voting states in landscape.
- Check bottom panel with long names and blocked/completed action states.
- Confirm no primary button is pushed off-screen.

</verification>

<success_criteria>

- GAME-01 and GAME-02 are covered by concrete layout/copy fixes or documented as already acceptable.
- Fresh gameplay opens with the map center visible.
- Main HUD can be used without opening chat.
- No new features or broad refactors are introduced.
- Changes are small enough for manual Android Studio validation.

</success_criteria>
