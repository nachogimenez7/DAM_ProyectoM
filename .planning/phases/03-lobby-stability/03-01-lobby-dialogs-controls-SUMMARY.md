---
phase: 3
plan: 03-01
title: "Make lobby dialogs and controls responsive"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - LOBBY-01
  - LOBBY-03
---

# Execution Summary

## What Changed

- Moved the start button out of the scroll-dependent configuration stack and pinned it to the top of the right lobby panel.
- Kept the rest of the right-side content scrollable below the primary action.
- Added autosizing to compact dialog buttons and dialog footer buttons.
- Clamped lobby dialog widths to the available display width instead of using rigid widths only.
- Added autosizing to the lobby title and the add/remove player controls.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- No Gradle build was run because the user explicitly said they would compile later.

## Device Validation Follow-up (2026-08-06)

- The practice-role control was still a cyclic button, forcing the tester to traverse the full list after passing the desired role.
- The lobby practice summary and the advanced-options control now open a direct, scrollable role grid.
- Every choice shows its role name plus team/map and minimum-player context; the active choice is highlighted and can be replaced with one tap.
