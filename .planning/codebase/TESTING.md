# Testing Patterns

**Analysis Date:** 2026-06-13

## Test Framework

**Runner:**
- JUnit 4.13.2 local JVM tests.
- Android Gradle default unit-test task; no custom test configuration.

**Assertion Library:**
- `org.junit.Assert` with `assertEquals`, `assertTrue`, `assertFalse`, and `assertNull`.

**Run Commands:**
```powershell
.\gradlew.bat test
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testDebugUnitTest --tests "com.traidores.juego.GameEngineTest"
```

## Test File Organization

**Location:**
- All tests are under `app/src/test/java/com/traidores/juego/`.
- Tests mirror the production package.

**Naming:**
- `<Subject>Test.kt`.
- Method names describe expected behavior in camelCase.

**Current Inventory:**
- `GameEngineTest.kt` - 72 tests.
- `GameplayTableUiTest.kt` - 24 tests.
- `GameplayCountdownTest.kt` - 4 tests.
- `GameplayFeedbackStateTest.kt` - 3 tests.
- `RoleCatalogTest.kt` - 3 tests.
- `GameTableLayoutTest.kt` - 2 tests.

## Test Structure

**Suite Organization:**
```kotlin
class GameplayCountdownTest {
    @Test
    fun countdownPausesAndResumesWithoutLosingRemainingTime() {
        val countdown = GameplayCountdown()
        countdown.ensurePhase(3, 5_000L)

        val result = countdown.start(1_000L)

        assertEquals(GameplayCountdown.StartResult.STARTED, result)
    }
}
```

**Patterns:**
- Direct arrange/act/assert flow without explicit section comments.
- Test models are created inline or through helper functions at the bottom of a test class.
- Tests favor pure domain/presentation logic and avoid Android framework dependencies.

## Mocking

**Framework:**
- No mocking library.
- No Robolectric.

**Current Approach:**
- Construct real `GameSession`, `GamePlayer`, and helper objects.
- Keep production logic pure enough to execute on the JVM.

## Fixtures and Factories

**Test Data:**
- Private factory helpers inside large test classes.
- Inline roles and sessions for narrow cases.
- No shared fixture directory.

## Coverage

**Requirements:**
- No enforced line or branch coverage target.
- No coverage report task is documented.
- CI does not enforce test execution.

**Strong Areas:**
- Phase progression, voting, AFK handling, role behavior, winner presentation, countdown state, and pure table geometry.

**Weak Areas:**
- Activity navigation and back-stack behavior.
- XML layout fit on small screens, tablets, and large font scales.
- Keyboard/insets/chat interaction.
- Dialog size and accessibility.
- Profile edit cancellation across rotation/process recreation.
- Music behavior across Activity transitions.

## Test Types

**Unit Tests:**
- Present and substantial for domain logic.
- Fast and Android-independent.

**Integration Tests:**
- No Activity-to-Activity navigation tests.
- No tests that inflate layouts or assert view visibility/enabled state.

**E2E/Visual Tests:**
- None.
- Existing root screenshots are manual artifacts, not automated baselines.

## Recommended Stabilization Verification

**First Additions:**
- Instrumented smoke tests for every manifest Activity opening successfully.
- Navigation tests for menu -> mode -> lobby -> role assignment -> gameplay and back behavior.
- Layout checks at compact portrait, common portrait, compact landscape, and large font scale.
- Screenshot/manual checklist for profile, lobby dialogs, gameplay overlays, keyboard-open chat, and winner screen.

**Regression Rule:**
- Every fixed navigation bug should get an Activity/instrumentation test where feasible.
- Every fixed pure state bug should get a JVM test in the existing style.

---
*Testing analysis: 2026-06-13*
*Update after adding instrumentation or screenshot testing*
