# Codebase Concerns

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Priority Summary

1. `GameplayMockActivity.kt` and `activity_gameplay_mock.xml` are still the main fragility points.
2. Visual behavior depends heavily on fixed portrait dimensions and manual overlay state.
3. Chat/keyboard behavior requires real-device validation.
4. Lobby/profile/navigation stability remain behind gameplay in the roadmap.
5. Asset size and resource growth should be watched before APK sharing.

## High-Risk Files

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt`: about 3,479 lines; coordinates gameplay state, countdown, chat, event log, overlays, audio, voting, reveals, and navigation.
- `app/src/main/res/layout/activity_gameplay_mock.xml`: about 1,946 lines; contains HUD, chat, event log, bottom panel, vote result, tie vote, role preview, jester/oracle/winner/reveal overlays.
- `app/src/main/java/com/traidores/juego/GameEngine.kt`: about 1,561 lines; central local rules and timeout behavior.
- `app/src/main/java/com/traidores/juego/LobbyActivity.kt`: about 810 lines; lobby configuration and programmatic dialogs.
- `app/src/main/java/com/traidores/juego/LocalBotAi.kt`: about 647 lines; bot chat/vote behavior can affect perceived quality quickly.
- `app/src/main/java/com/traidores/juego/ProfileActivity.kt`: about 482 lines; edit state and local persistence.

## Visual/Layout Concerns

- Gameplay has improved recently, but still uses many fixed widths/heights.
- Vote/tie/reveal overlays are visually richer but increase state coordination complexity.
- Event log default collapsed/open behavior is product-sensitive and should be verified from a fresh match.
- Bottom gameplay panel competes with role art, player name, role name, hints, status chips, and buttons.
- Lobby/options dialogs may still need compact portrait validation.
- Profile layout has many image/text/edit affordances and should be checked on short portrait devices.

## State Coordination Concerns

- Many overlay flags can interact: vote result, tie vote, chat, event log, death reveal, silence reveal, oracle reveal, jester victory, winner reveal, role preview, day/night transition.
- `resumeGameFlowAfterBlockingUi()` is central and should be treated carefully.
- Banner feedback such as vote confirmation must not leak into modal overlays.
- Music pause/resume across blocking overlays is easy to regress.
- Countdown transition locks must stay aligned with manually dismissed overlays.

## Navigation Concerns

- Back behavior is not centrally specified.
- Gameplay should close transient UI before leaving the match.
- Profile selection Activities return results to profile editing and need consistent cancellation behavior.
- Lobby and browser screens mix simulated online/local flows and can confuse route expectations.

## Asset/Performance Concerns

- Several role/historical images are multi-megabyte PNGs in `drawable-nodpi`.
- Raw music assets include several multi-megabyte MP3/MPEG files.
- Repeated `MediaPlayer` creation for short effects is simple but should be watched if effects become frequent.
- APK size may grow quickly as more generated banners/portraits/sounds are added.
- There is no automated resource-size budget.

## Testing Concerns

- Local unit tests cover logic well, but not Android UI rendering.
- No test guards for text clipping, keyboard layout, or overlay stacking.
- No Activity recreation tests for profile draft or gameplay state.
- No manual QA artifact is currently tracked for the final APK pass.

## Recommended Stabilization Focus

- Continue with GSD Phase 1: Gameplay Visual Stability.
- Audit only current behavior before adding roles/features.
- Prefer visual bug fixes over new content.
- Create a manual device checklist for APK sharing.
- Defer online/Firebase and new-role expansion until the existing surfaces are stable.
