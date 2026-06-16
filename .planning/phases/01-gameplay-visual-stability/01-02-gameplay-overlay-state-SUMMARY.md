---
phase: 1
plan: 01-02
title: "Consolidate gameplay overlay and action states"
status: executed_pending_visual_validation
executed_at: 2026-06-16
commits:
  - 0568d2f
requirements:
  - GAME-03
  - GAME-04
---

# Execution Summary

## What Changed

- Dismissed action-feedback banners before opening blocking gameplay windows:
  - role preview
  - death reveal
  - silence reveal
  - oracle reveal
  - jester victory
  - winner reveal
  - traitor reveal
- Preserved the existing vote, tie, expulsion, no-expulsion, animation, and audio flows.
- Did not change role rules, engine behavior, or online behavior.

## Overlay Priority Notes

- Blocking windows pause the countdown and own the interaction until dismissed.
- Stale action banners should never remain visible above modal vote/reveal/result windows.
- Existing cleanup paths already covered several vote-result and tie paths; this pass broadened the same cleanup rule to other modal reveals.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- `.\gradlew.bat :app:testDebugUnitTest` was run with Android Studio JBR and returned exit code 0.

## Remaining Validation

- User should visually verify:
  - A vote/action confirmation banner disappears before a modal window opens.
  - Reveal/victory/result overlays do not appear with stale banners on top.
  - Hidden overlays do not block the next gameplay action after dismissal.
