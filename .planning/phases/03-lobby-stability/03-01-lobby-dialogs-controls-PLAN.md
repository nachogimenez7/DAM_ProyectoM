---
phase: 3
plan: 03-01
title: "Make lobby dialogs and controls responsive"
type: implementation
wave: 1
depends_on: []
files_modified:
  - app/src/main/res/layout/activity_lobby.xml
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
autonomous: true
requirements:
  - LOBBY-01
  - LOBBY-03
---

<objective>
Keep the lobby creation screen usable on compact landscape devices by making the start action visible and the timing/advanced dialogs less rigid.
</objective>

<truths>
- Do not add new lobby features.
- Preserve the current visual language and Activity/XML structure.
- The user will compile and visually validate later.
- Keep changes scoped to layout clarity, text fitting, and dialog sizing.
</truths>

<tasks>

## Task 1: Keep the primary action visible

type: implementation
files:
  - app/src/main/res/layout/activity_lobby.xml
action:
  - Remove the start button from the scroll-dependent section.
  - Keep configuration content scrollable below the primary action.
acceptance_criteria:
  - The host action is visible without scrolling the whole right panel.

## Task 2: Reduce rigid dialog sizing

type: implementation
files:
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
action:
  - Clamp dialog width to the available display width.
  - Make compact dialog buttons and footer actions autosize when space gets tight.
acceptance_criteria:
  - Timing and advanced dialogs degrade more gracefully on narrow landscape screens.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Open lobby on a compact landscape device.
- Confirm `INICIAR PARTIDA` stays visible.
- Open `TIEMPOS` and `OPCIONES AVANZADAS`.
- Check that footer actions remain readable.

</verification>
