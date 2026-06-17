---
phase: 5
plan: 05-01
title: "Normalize navigation routes and back priority"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - NAV-01
  - NAV-02
  - NAV-03
---

# Execution Summary

## What Changed

- Unified `AssigningRolesActivity` back handling so both the visible back button and the Android system back cancel the pending auto-open into gameplay.
- Replaced the deprecated gameplay `onBackPressed()` path with `OnBackPressedDispatcher`.
- Defined a single gameplay back order:
  - winner reveal -> return to lobby
  - role preview -> close preview
  - chat -> close chat
  - action feedback banner -> hide banner
  - expanded event log -> collapse log
  - blocking reveal/transition overlays -> ignore back until the layer ends
- Fixed profile back behavior so choosing `Descartar` while editing restores the saved draft and exits the profile instead of leaving the user stranded on the same screen.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- No Gradle build was run because this project is still being visually validated by the user in Android Studio/device.
