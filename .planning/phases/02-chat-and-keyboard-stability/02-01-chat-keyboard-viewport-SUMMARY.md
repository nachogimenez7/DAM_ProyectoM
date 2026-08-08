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

## Device Validation Follow-up (2026-08-06)

- A real-device trailer test showed that the composer could still remain below the keyboard.
- Root cause: the controller tracked the IME inset but continued sizing the panel from the full `screenHeightDp`.
- The panel now caps itself to the actual visible gameplay viewport, anchors above an overlaid keyboard, and follows IME animation progress.
- Both Android behaviors are handled: a root already resized by `adjustResize`, or a full root with the IME drawn on top.
- A second device check found a brief oversized state while the IME was closing. The chat now keeps its normal target height and centers inside the progressively changing visible viewport, so opening and closing use the same reversible motion without a final size jump.
- Status remains `executed_pending_visual_validation` until the corrected APK is checked on the same device/keyboard combination.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- `.\gradlew.bat :app:testDebugUnitTest` passed with `BUILD SUCCESSFUL`.
