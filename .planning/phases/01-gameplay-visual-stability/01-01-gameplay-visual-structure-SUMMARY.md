---
phase: 1
plan: 01-01
title: "Audit and fix main gameplay visual structure"
status: executed_pending_visual_validation
executed_at: 2026-06-16
commits:
  - 0568d2f
requirements:
  - GAME-01
  - GAME-02
---

# Execution Summary

## What Changed

- Tightened the bottom gameplay HUD so the role card, player name, status, role name, hint, and action buttons fit better on compact landscape screens.
- Added autosizing and ellipsizing to the player name, status, role name, and current-player hint.
- Gave the hint a weighted area inside the bottom panel so it uses available vertical space without pushing controls out.
- Reduced the action-control width and primary button widths slightly to preserve button visibility.

## Audit Notes

- Main HUD risk areas are the fixed-height header, collapsed event row, side player seats, and bottom player/action panel.
- The event log remains a deliberate collapsed default so the center of the map stays visible at match start.
- Player seats and header were not changed in this pass because the concrete clipping risk was in the bottom panel shown in recent screenshots.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- `.\gradlew.bat :app:testDebugUnitTest` was run with Android Studio JBR and returned exit code 0.

## Remaining Validation

- User should visually verify on Android Studio/device:
  - Fresh match starts with the event log collapsed.
  - Bottom role hint no longer disappears under the panel edge.
  - Long names and action labels remain readable enough on compact landscape phones.
