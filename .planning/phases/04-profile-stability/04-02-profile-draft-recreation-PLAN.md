---
phase: 4
plan: 04-02
title: "Preserve profile draft across recreation"
type: implementation
wave: 2
depends_on:
  - 04-01
files_modified:
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
autonomous: true
requirements:
  - PROF-03
  - PROF-04
---

<objective>
Keep unsaved profile edits alive across normal Activity recreation and ensure empty fields degrade into explicit placeholders instead of confusing blanks.
</objective>

<truths>
- Preserve the existing save/discard model.
- No database writes beyond the current SharedPreferences profile storage.
- A recreation-safe draft matters more here than dialog persistence.
</truths>

<tasks>

## Task 1: Save editing state and draft fields

type: implementation
files:
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
action:
  - Save `isEditing` plus the current draft fields into `savedInstanceState`.
  - Restore the draft when the screen comes back while editing.
acceptance_criteria:
  - Unsaved edits survive a normal Activity recreation.

## Task 2: Keep empty fields explicit

type: implementation
files:
  - app/src/main/java/com/traidores/juego/ProfileActivity.kt
action:
  - Render a clear bio placeholder when the user leaves the phrase empty.
acceptance_criteria:
  - Empty profile content does not collapse into misleading blank UI.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Enter edit mode, change several fields, recreate the Activity, and confirm the draft remains.
- Discard changes and confirm the saved profile returns.

</verification>
