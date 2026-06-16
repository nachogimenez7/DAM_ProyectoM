---
phase: 3
plan: 03-02
title: "Clarify room availability and empty states"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - LOBBY-02
  - LOBBY-04
---

# Execution Summary

## What Changed

- Centralized room-browser helpers for status color, join enablement, and action labels.
- Kept full rooms labeled as `LLENA`.
- Added an explicit `EN PARTIDA` button label for blocked rooms that are unavailable without being full.
- Added a visible empty-state message to the room browser layout and wired it from the Activity.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- No Gradle build was run because the user explicitly said they would compile later.
