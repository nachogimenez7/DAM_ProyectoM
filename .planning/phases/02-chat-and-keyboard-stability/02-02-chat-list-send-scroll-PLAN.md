---
phase: 2
plan: 02-02
title: "Stabilize chat list, send, and scroll behavior"
type: implementation
wave: 2
depends_on:
  - 02-01
files_modified:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/res/layout/activity_gameplay_mock.xml
autonomous: true
requirements:
  - CHAT-03
  - CHAT-04
---

<objective>
Keep chat usable while writing: sending should clear reliably, recent messages should stay reachable, and the new-message notice should help instead of hiding important content.
</objective>

<truths>
- Preserve the existing chat panel concept.
- Do not add new multiplayer or database behavior.
- Do not redesign the keyboard itself; Android/IME owns that surface.
- Keep changes small enough for manual APK verification.
</truths>

<tasks>

## Task 1: Improve compact chat density

type: implementation
files:
  - app/src/main/res/layout/activity_gameplay_mock.xml
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Reduce only the compact-mode heights and padding that waste vertical space.
  - Preserve readable tap targets.
acceptance_criteria:
  - The chat can show input plus multiple recent messages in landscape.

## Task 2: Stabilize post-send scroll

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Keep the existing durable input cleanup.
  - Ensure successful sends return the list to the newest visible message.
acceptance_criteria:
  - After sending, the composer is empty and the latest message is reachable.

## Task 3: Preserve new-message affordance while typing

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Keep the new-message counter visible only when useful.
  - Do not auto-yank scroll away from the user while typing unless they acknowledge it.
acceptance_criteria:
  - A player can type and still know if new messages arrived.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Type while bots send messages.
- Press the new-message notice.
- Send a message and verify the composer clears.
- Reopen chat after closing the keyboard.

</verification>
