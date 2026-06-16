---
phase: 2
plan: 02-01
title: "Measure keyboard viewport and adapt chat panel"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - CHAT-01
  - CHAT-02
---

# Execution Summary

## What Changed

- Replaced the chat keyboard layout switch with a state that tracks both IME visibility and the reported bottom inset.
- Reapplies chat dimensions when the keyboard inset changes, so different Android keyboards can trigger different measurements.
- Bases chat width on the center gameplay column when available instead of the full gameplay root.
- Enlarged compact chat width while keeping it inside the gameplay center area.

## Remaining Device Risk

- Android keyboards can render differently by manufacturer and settings. The app can react to IME visibility/insets, but the keyboard surface itself remains owned by Android.
- Final confirmation still requires a real device with the user's keyboard.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- `.\gradlew.bat :app:testDebugUnitTest` passed with `BUILD SUCCESSFUL`.
