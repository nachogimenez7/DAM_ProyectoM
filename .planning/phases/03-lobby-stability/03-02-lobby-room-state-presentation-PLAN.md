---
phase: 3
plan: 03-02
title: "Clarify room availability and empty states"
type: implementation
wave: 2
depends_on:
  - 03-01
files_modified:
  - app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt
  - app/src/main/res/layout/activity_lobby_browser.xml
autonomous: true
requirements:
  - LOBBY-02
  - LOBBY-04
---

<objective>
Make the online room browser communicate available, full, in-progress, and empty states with unambiguous labels and behavior.
</objective>

<truths>
- Do not add networking or real backend state.
- Keep the current mock-lobby demo behavior.
- Improve labels, availability rules, and empty presentation only.
</truths>

<tasks>

## Task 1: Centralize room action rules

type: implementation
files:
  - app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt
action:
  - Use helpers for join enablement, button label, and status color.
  - Keep full rooms blocked and labeled clearly.
acceptance_criteria:
  - Room rows do not rely on scattered inline conditionals for availability.

## Task 2: Add a real empty-state placeholder

type: implementation
files:
  - app/src/main/res/layout/activity_lobby_browser.xml
  - app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt
action:
  - Add a hidden empty-state text block and toggle it from the Activity.
acceptance_criteria:
  - A no-room scenario has a visible explanation instead of an empty panel.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Review waiting, almost-full, and blocked room rows.
- Confirm the empty state appears when the room list is empty.

</verification>
