---
phase: 4
plan: 04-01
title: "Stabilize profile layout and editing flow"
status: executed_pending_visual_validation
executed_at: 2026-06-16
requirements:
  - PROF-01
  - PROF-02
---

# Execution Summary

## What Changed

- Added ellipsize and autosize to the profile name, public ID, and favorite role title.
- Replaced fake-looking hardcoded stats with explicit placeholder values and a hint explaining that progress is not registered yet.
- Fixed the third stat label to `Porcentaje`.
- Added tighter text handling for the profile selection title and option cards.

## Verification

- `git diff --check` passed. Git only reported expected LF-to-CRLF working-copy warnings.
- No Gradle build was run because the user said they would compile later.

## Local Gameplay Follow-up (2026-08-06)

- The gallery photo was visible in profile screens but compact gameplay avatars still rendered only the yellow initial fallback.
- Added a shared compact avatar that shows the saved photo only for the human profile on this device and preserves initials for bots, missing photos, and non-local identities.
- Applied it to individual vote seals, recount candidates, expulsion cards, and tie-vote cards.
- After device review, recount-candidate avatars were moved off the card artwork into a compact identity row directly below it, beside the player name.
- The human name in the bottom gameplay panel now opens the same mini profile, allowing the local photo to be checked without leaving the match.
