---
phase: 2
plan: 02-01
title: "Measure keyboard viewport and adapt chat panel"
type: implementation
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
autonomous: true
requirements:
  - CHAT-01
  - CHAT-02
---

<objective>
Make the chat panel react to the real visible gameplay area and keyboard state instead of only switching between a fixed open/closed layout.
</objective>

<truths>
- Do not redesign the gameplay screen.
- Keep the chat inside the existing gameplay Activity and visual style.
- The user's final verification requires a real Android keyboard/device.
- Avoid changing bot AI, role rules, voting, or online behavior.
</truths>

<tasks>

## Task 1: Audit current keyboard handling

type: audit
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Review current `WindowInsetsCompat.Type.ime()` usage.
  - Identify whether chat layout depends only on a boolean compact state.
acceptance_criteria:
  - The implementation summary states the remaining device-dependent risks.

## Task 2: Track keyboard visibility and inset size

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Store whether the IME is visible and the bottom inset reported by Android.
  - Reapply chat dimensions when the inset changes.
acceptance_criteria:
  - Different keyboards can trigger different chat panel measurements.

## Task 3: Adapt panel size to the center gameplay column

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Base chat width on the center column when available, not only the full root width.
  - Keep the compact chat panel large enough to read messages but clear of core controls.
acceptance_criteria:
  - The chat panel is less likely to cover side player cards or shrink unpredictably.

</tasks>

<verification>

Automated:
- Run `git diff --check`.
- Run `.\gradlew.bat :app:testDebugUnitTest` only if Kotlin logic changes and the environment allows it.

Manual:
- Open gameplay, open chat, focus input.
- Confirm recent messages remain visible above the keyboard.
- Confirm no large empty band appears inside the app layout.
- Close and reopen chat, then send a message.

</verification>
