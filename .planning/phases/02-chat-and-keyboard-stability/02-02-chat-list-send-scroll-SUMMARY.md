---
phase: 2
plan: 02-02
title: "Stabilize chat list, send, and scroll behavior"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - CHAT-03
  - CHAT-04
---

# Execution Summary

## What Changed

- Reduced compact chat header, composer, input, button, status row, and padding heights slightly to recover vertical space while typing.
- Kept the existing durable composer cleanup after send.
- Scrolls to the newest message after a successful send.
- Makes chat bubble width adapt to the current chat panel width instead of using a fixed 250dp width.

## Preserved Behavior

- New-message notice remains available while typing.
- Bot reactions and chat rules were not changed.
- No online, database, role, vote, or engine behavior was added.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- `.\gradlew.bat :app:testDebugUnitTest` passed with `BUILD SUCCESSFUL`.
