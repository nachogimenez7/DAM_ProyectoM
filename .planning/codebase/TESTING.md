# Testing

**Analysis Date:** 2026-06-16
**Mapped Commit:** e6a3bcd

## Current Test Framework

- Local JVM tests use JUnit 4.13.2.
- Tests live in `app/src/test/java/com/traidores/juego/`.
- There are no Android instrumentation tests under `app/src/androidTest/`.
- There is no screenshot, golden image, accessibility, UI Automator, Espresso, Robolectric, or Compose test setup.

## Existing Test Files

- `GameEngineTest.kt`: broad local rule coverage for phases, roles, voting, ties, jester, oracle, mayor, desertor, chat/debug behavior, and win/result scenarios.
- `GameplayTableUiTest.kt`: public events, role/phase text, gameplay copy, and presentation behavior.
- `GameTableLayoutTest.kt`: seat/layout helper behavior.
- `GameplayCountdownTest.kt`: countdown and transition lock behavior.
- `GameplayFeedbackStateTest.kt`: feedback queue/private/banner state behavior.
- `RoleCatalogTest.kt`: catalog/map-role consistency.

## Useful Command

On this Windows setup, the reliable unit-test command is:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest
```

## Coverage Strengths

- `GameEngine.kt` has meaningful local rule coverage.
- Voting, tie, mayor, jester, oracle, payador, bot/debug behavior, and result progression are represented in unit tests.
- Some presentation copy and event-log behavior is covered through pure helper tests.
- Countdown and feedback state are tested outside the Activity.

## Coverage Gaps

- No automated coverage for `Activity` lifecycle recreation.
- No keyboard/IME tests for portrait chat.
- No screenshot tests for compact portrait gameplay, lobby dialogs, profile, or vote overlays.
- No navigation/back-stack smoke tests.
- No resource-size or asset-loading regression checks.
- No tests for actual `MediaPlayer` behavior or audio preference integration on device.

## Manual Validation Needed

- Compact portrait gameplay on at least one smaller phone.
- Chat with real keyboard open, including receiving bot messages while typing.
- Vote recount, tie vote, second tie, no-expulsion, expulsion boot animation, and final result windows.
- Role preview timing and continue button.
- Lobby timing/options dialogs on small screens.
- Profile edit flow, selectors, banner/avatar changes, and Activity recreation if possible.
- Audio mute/music/effects behavior from menu/options/gameplay.

## Recommended Next Test Work

- Keep adding local unit tests for rule changes in `GameEngine.kt`.
- Extract Activity-independent layout/state decisions into testable helpers when fixing visual bugs.
- Add a small manual QA matrix in `.planning` for APK validation.
- Consider Robolectric or instrumentation only after the UI stabilizes enough to make those tests worth maintaining.
