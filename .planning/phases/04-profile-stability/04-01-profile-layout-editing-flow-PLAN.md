---
phase: 4
plan: 04-01
title: "Stabilize profile layout and editing flow"
type: implementation
wave: 1
depends_on: []
files_modified:
  - app/src/main/res/layout/activity_profile.xml
  - app/src/main/res/layout/activity_profile_selection.xml
  - app/src/main/res/layout/item_profile_role_selection.xml
  - app/src/main/res/layout/item_profile_banner_selection.xml
autonomous: true
requirements:
  - PROF-01
  - PROF-02
---

<objective>
Keep the profile, achievements, and current selector screens readable and coherent without adding backend or online behavior.
</objective>

<truths>
- Do not implement Firebase, login, or gallery upload in this phase.
- Preserve the current visual style and editing model.
- Treat stats as placeholders unless they are backed by real stored progress.
</truths>

<tasks>

## Task 1: Tighten profile composition

type: implementation
files:
  - app/src/main/res/layout/activity_profile.xml
action:
  - Prevent long name, public ID, and favorite-role labels from clipping badly.
  - Replace fake-looking stat values with explicit placeholder presentation.
acceptance_criteria:
  - The profile no longer presents hardcoded demo stats as if they were real.

## Task 2: Make current selectors degrade better

type: implementation
files:
  - app/src/main/res/layout/activity_profile_selection.xml
  - app/src/main/res/layout/item_profile_role_selection.xml
  - app/src/main/res/layout/item_profile_banner_selection.xml
action:
  - Add ellipsize and autosize where long titles or subtitles can get cut.
acceptance_criteria:
  - Selector titles and options remain readable on smaller portrait phones.

</tasks>

<verification>

Automated:
- Run `git diff --check`.

Manual:
- Open profile and enter edit mode.
- Open avatar, banner, and favorite-role selectors.
- Check that stats clearly look unavailable when no real data exists.

</verification>
