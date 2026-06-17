---
phase: 5
plan: 05-02
title: "Audit critical controls and navigation matrix"
type: implementation
wave: 2
depends_on:
  - 05-01
files_modified:
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/res/layout/activity_lobby.xml
  - app/src/main/res/layout/activity_profile.xml
  - app/src/main/res/layout/activity_jugar.xml
  - app/src/main/res/layout/activity_online_mode.xml
  - app/src/main/res/layout/activity_local_mode.xml
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
  - .planning/phases/05-navigation-usability-guard/05-NAVIGATION-MATRIX.md
autonomous: true
requirements:
  - USE-01
  - NAV-03
---

<objective>
Finish the stability pass with a focused usability audit: clearer controls, usable touch targets, coherent disabled states, and a manual navigation matrix that captures what must still be checked on device.
</objective>

<truths>
- This phase is a polish and guardrail pass, not a redesign.
- Mobile portrait readability and touch usability take priority over squeezing more content on screen.
- Empty, disabled, and unavailable states must read as intentional states instead of broken UI.
</truths>

<tasks>

## Task 1: Audit the highest-risk controls

type: implementation
files:
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/res/layout/activity_lobby.xml
  - app/src/main/res/layout/activity_profile.xml
  - app/src/main/res/layout/activity_jugar.xml
  - app/src/main/res/layout/activity_online_mode.xml
  - app/src/main/res/layout/activity_local_mode.xml
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
action:
  - Review the most-used controls across gameplay, lobby, profile, and play-entry screens.
  - Tighten spacing, touch area, contrast, and wording where a control currently looks disabled by mistake, feels too small, or does not explain its state.
  - Add or correct content descriptions on important controls that act as back, close, chat, settings, edit, or primary progression actions.
acceptance_criteria:
  - Important controls look tappable when enabled and clearly unavailable when disabled.
  - The primary controls on the main four surfaces have understandable labels or content descriptions.

## Task 2: Capture a manual navigation and empty-state matrix

type: implementation
files:
  - .planning/phases/05-navigation-usability-guard/05-NAVIGATION-MATRIX.md
action:
  - Write a compact QA matrix for the user to run later on device.
  - Cover the main routes, back outcomes, empty states, blocked states, and gameplay overlays that cannot be fully trusted from desktop-only inspection.
  - Keep the checklist aligned with the actual stabilized routes from `05-01`.
acceptance_criteria:
  - The phase directory contains a reusable manual matrix for menu, lobby, profile, and gameplay navigation checks.
  - The matrix highlights which checks still depend on the user's phone, IME, or screen size.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Recheck the highest-frequency controls on compact portrait layouts.
- Execute the generated `05-NAVIGATION-MATRIX.md` checklist on Android Studio/device when available.

</verification>
