---
phase: 5
plan: 05-01
title: "Normalize navigation routes and back priority"
type: implementation
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt
  - app/src/main/java/com/traidores/juego/AssigningRolesActivity.kt
  - app/src/main/java/com/traidores/juego/OnlineModeActivity.kt
  - app/src/main/java/com/traidores/juego/LocalModeActivity.kt
autonomous: true
requirements:
  - NAV-01
  - NAV-02
  - NAV-03
---

<objective>
Make menu, lobby, profile, and gameplay navigation behave predictably, with one explicit back-priority model and no accidental route duplication.
</objective>

<truths>
- Do not add new features or new destinations in this phase.
- Preserve the current Activity-based architecture and existing screen entry points.
- Treat gameplay overlays as either blocking modals or dismissible layers; each visible layer must belong to only one category.
</truths>

<tasks>

## Task 1: Define and enforce gameplay back priority

type: implementation
files:
  - app/src/main/java/com/traidores/juego/GameplayMockActivity.kt
action:
  - Audit every gameplay overlay and transient surface already mounted from `GameplayMockActivity`.
  - Make the back behavior explicit for each case: blocking reveal/result layers, terminal winner return, and dismissible layers such as role preview, chat, and event log.
  - Remove ambiguous fallthrough paths where the visible UI state and the back result do not clearly match.
acceptance_criteria:
  - Gameplay has one documented back priority from topmost transient layer to Activity exit.
  - Pressing back in gameplay never closes the wrong layer first.

## Task 2: Align system back with visible back buttons

type: implementation
files:
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyActivity.kt
  - app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt
  - app/src/main/java/com/traidores/juego/AssigningRolesActivity.kt
  - app/src/main/java/com/traidores/juego/OnlineModeActivity.kt
  - app/src/main/java/com/traidores/juego/LocalModeActivity.kt
action:
  - Verify that the top-left back button and the Android system back action produce the same result on each primary route.
  - Keep profile discard-confirmation behavior intact while ensuring it is reached consistently from both entry points.
  - Normalize lobby-related exits so browser, local, and online entry screens return to the expected previous screen instead of leaving duplicate navigation states.
acceptance_criteria:
  - Back button and system back are behaviorally equivalent on menu-adjacent screens.
  - Returning from lobby/profile flows does not reopen or duplicate an unexpected screen.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Traverse `Main -> Jugar -> Local/Online -> Lobby -> AssigningRoles -> Gameplay` and back out step by step.
- Open gameplay chat, role preview, and event log in different combinations and verify back resolves the topmost intended layer.
- Edit the profile, trigger discard confirmation, and verify both back affordances behave the same.

</verification>
