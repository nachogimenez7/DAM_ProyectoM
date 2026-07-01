# Spec — Saltar noche sin acción pendiente + reveal inmersivo de "nadie murió"

> Handoff Claude (diseño/review) → Codex (implementación). Código = fuente de verdad. Codex arranca en frío: leer todo antes de tocar nada. Diff acotado por fase; el usuario valida en Android Studio (no se compila en este handoff).

**Contexto:** ciclo de estabilización de gameplay (ver `CLAUDE.md`). Dos pedidos del usuario tras jugar una partida como Aldeano (rol sin acción nocturna):

1. La noche se siente "invisible" — en realidad no se salta, pero el jugador queda mirando un botón apagado durante minutos sin sentir que algo pasa.
2. Cuando nadie muere al amanecer, hoy solo queda un mensaje de texto en el chat (antes había un cartel que se quitó por pedido del usuario en un handoff previo). El usuario quiere una animación inmersiva equivalente en peso a la del reveal de muerte, pero con contenido propio (sin carta ni sangre).

Ambos comparten el mismo punto de enganche en el código (`resumeGameFlowAfterBlockingUi()` y la familia de *RevealAnimator), así que se documentan juntos, pero son dos cambios independientes — pueden implementarse y mergearse por separado.

---

## PARTE 1 — Botón "SALTAR NOCHE" cuando no hay decisión pendiente

### Diagnóstico (con evidencia)

El código **no** salta la noche para un jugador sin acción — la renderiza igual que para todos, pero de forma pasiva y, en partidas con pocos roles especiales, muy larga:

1. `GameEngine.startNight()` (`GameEngine.kt:7-32`) recorre **todas** las sub-fases `NOCHE_ASESINO → NOCHE_MERCENARIO → NOCHE_POLICIA → NOCHE_MEDICO → NOCHE_ORACULO` en secuencia, exista o no ese rol entre los jugadores vivos (`resolveMercenary`/`resolvePolice`/`resolveMedic` en `GameEngine.kt:206-300` llaman a `advanceNight(...)` igual aunque nadie tenga ese rol).
2. Para cada una de esas sub-fases, si el humano no tiene el turno (`isHumanRoleTurn == false`), `phaseText(...)` (`GameplayMockActivity.kt:3348-3417`) le pone el label `"ESPERAR"` al botón de acción.
3. `renderAdvanceButton()` (`GameplayMockActivity.kt:2626-2683`) calcula `mustWaitForPhaseTimer()` (`GameplayMockActivity.kt:4246-4263`) = `true` para ese caso (no es `quickTestMode`, `requiresHumanInput()` es `false`, hay `activePhaseSeconds()` configurado) → `btnAction.isEnabled = false`.
4. La cuenta regresiva (`GameplayCountdown`, arrancada en `ensureCountdownForCurrentPhase()` → `GameplayMockActivity.kt:3419-3451`) corre **40 segundos reales por sub-fase** (`timing.nightSeconds`, default en `GameModels.kt:205,232`). Solo al llegar a 0, `onCountdownExpired()` (`GameplayMockActivity.kt:3527-3618`) resuelve y avanza.
5. El subtítulo del narrador (`nightSubtitle()`, `GameplayMockActivity.kt:3321-3346`) es una línea **estática** durante toda la espera — no hay señal de que "algo está pasando".

**Resultado medible:** un Aldeano en una partida con pocos roles especiales puede atravesar hasta 5 cuentas regresivas de 40s seguidas (~3+ min) con un botón apagado y texto fijo. De ahí la percepción de "no vi la noche".

**El único auto-skip real que existe hoy** es `quickTestMode` (Lobby → Opciones avanzadas, `LobbyActivity.kt:1729-1748`), que usa `GameEngine.shouldAutoAdvance()` (`GameEngine.kt:1222-1228`) para saltar instantáneo. Es opt-in, pensado para testing, no para jugar — no se debe activar por default, pero confirma que el motor ya sabe distinguir "sin acción" sin ambigüedad.

### Decisión de diseño (confirmada con el usuario)

El botón **no debe activarse apenas el jugador termina su propia acción** — eso le quita peso al momento de jugar tu rol. La regla acordada:

- **Rol sin acción nunca en la noche actual (ej. Aldeano):** el botón "SALTAR NOCHE" aparece desde el arranque de la noche.
- **Rol con acción (asesino, médico, policía, mercenario, oráculo):** durante tu propia sub-fase ves tu UI de acción normal (matar / curar / investigar / pasar). El juego **nunca avanza solo** al confirmar tu jugada. Recién **después** de que confirmás tu acción (o decidís explícitamente no actuar) aparece el botón "SALTAR NOCHE" para el resto de la noche.
- Si el jugador está AFK o el bot/IA resuelve su parte, eso no debe bloquear el salto — la condición es únicamente "¿el humano tiene ahora mismo una decisión pendiente?", usando `GameEngine.requiresHumanInput(session)` (`GameEngine.kt:1204-1220`) como única fuente de verdad.
- Tocar "SALTAR NOCHE" lleva **de una** hasta el amanecer (no sub-fase por sub-fase) — una vez que no hay nada más que decidir, no tiene sentido reconfirmar en cada sub-fase restante.

### Qué tocar

**No hace falta tocar `GameEngine.kt`** — la lógica de "tengo o no tengo acción" ya existe y es correcta (`requiresHumanInput`). Es un cambio de presentación + control de avance en `GameplayMockActivity.kt`:

1. **Label y habilitación del botón** — en `renderAdvanceButton()` (`GameplayMockActivity.kt:2626-2683`): cuando `!GameEngine.requiresHumanInput(session)` y la fase actual es nocturna y no hay ninguna UI bloqueante activa (ver guard de `scheduleAutoAdvanceIfNeeded()`, `GameplayMockActivity.kt:3419-3437`, mismo set de banderas: `isDayNightTransitionRunning`, `isDeathRevealRunning`, `isSilenceRevealRunning`, etc.), mostrar label `"SALTAR NOCHE"` y `btnAction.isEnabled = true` en vez del actual `"ESPERAR"` deshabilitado.
2. **Handler del tap** — en `handleCurrentPhase()` (cerca de `GameplayMockActivity.kt:1041-1046`, donde hoy `mustWaitForPhaseTimer()` bloquea con un Toast tipo "La fase avanza sola..."), agregar la rama: si se tocó estando habilitado el salto, limpiar el countdown (`clearCountdown()`/equivalente) y avanzar usando el mismo camino que ya usa `onCountdownExpired()` al expirar naturalmente (`advanceSessionWithoutRendering()` y sucesivos), **en loop** mientras la siguiente sub-fase siga sin requerir input humano y siga siendo de noche — para que un solo tap cruce todas las sub-fases restantes sin acción hasta `AMANECER`.
3. **No tocar "tu acción confirmada" para que avance solo** — el flujo de confirmar tu propia jugada (asesino elige víctima, médico cura, etc.) sigue exactamente igual que hoy: termina, te deja en pantalla, y **recién ahí** pasa a calificar como "sin decisión pendiente" para mostrar el botón de salto.
4. **Excluir modo online** — `isOnlineGameplay()` ya gatea muchos caminos de avance porque ahí la fase la resuelve el host vía Firestore (`OnlinePhaseGate.canAdvanceLocally`, `blockOnlineGuestLocalPhaseAdvance`). El botón de salto debe quedar **deshabilitado/oculto en online** en esta primera entrega — saltar localmente podría desincronizar la partida compartida. Si se quiere online más adelante, es un cambio aparte que vive en el host.
5. **Opcional / baja prioridad — variar el subtítulo durante la espera:** si en algún momento sigue habiendo espera real (ej. la sub-fase de tu propio rol, donde no aplica skip), rotar `nightSubtitle()` entre 2-3 líneas con un `Handler`/`Runnable` (mismo patrón que ya usa `clearPhaseAdviceRunnable` en el archivo) para que no se sienta congelado. No es necesario para resolver el pedido principal — es pulido aparte, hacerlo solo si sobra tiempo.

### Puntos de guardia a respetar (no romper)

El salto debe quedar bloqueado exactamente en los mismos casos donde hoy el timer natural también queda pausado — reusar la lista de banderas de `scheduleAutoAdvanceIfNeeded()` (`GameplayMockActivity.kt:3419-3437`): transición día/noche en curso, reveals de muerte/silencio/oráculo, resultado de votación, jester/winner reveal, preview de rol, feedback bloqueante, diálogo de desertor. Si alguna de esas está activa, el botón no debe ofrecer saltar.

---

## PARTE 2 — Reveal inmersivo de "nadie murió"

### Arquitectura existente a reutilizar

El proyecto ya tiene dos reveals de amanecer con el mismo patrón estructural pero contenido distinto — son la plantilla a seguir:

- **`DeathRevealAnimator.kt`** (carta + sangre + flip de rol): tiene una pausa intermedia con botón "CONTINUAR" (`onReadyToContinue`/`continueAndFinish`) porque el jugador necesita asimilar quién murió y qué rol tenía.
- **`SilenceRevealAnimator.kt`** (jaula que se cierra): **se auto-resuelve de punta a punta**, sin botón de continuar — entra, arma el efecto, sostiene un momento (`hold`, un `ValueAnimator` puramente temporal), sale sola y llama a `onFinished()`.

**"Nadie murió" no tiene un jugador ni un rol que revelar — es un evento del pueblo entero.** Por eso el patrón correcto a copiar es el de `SilenceRevealAnimator` (automático), no el de `DeathRevealAnimator` (con pausa).

### Punto único de enganche

Todos los reveals de amanecer se encolan y se drenan desde el mismo router central, `resumeGameFlowAfterBlockingUi()` (`GameplayMockActivity.kt:4480-4523`):

```kotlin
private fun resumeGameFlowAfterBlockingUi() {
    if ( isDayNightTransitionRunning || isDeathRevealRunning || isSilenceRevealRunning ||
         isOracleRevealVisible || isVoteResultVisible || isTieVoteVisible ||
         isJesterVictoryVisible || isWinnerRevealVisible || isRolePreviewOpen ||
         feedbackState.privateVisible ) { return }
    if (feedbackState.pending?.blocksGameplay == true) { showPendingPrivateFeedback(); return }
    if (voteNoExpulsionPresented && ...) { ...; return }
    if (maybeShowNextDeathReveal()) return
    if (maybeShowNextSilenceReveal()) return
    if (maybeShowOracleReveal()) return
    // ... etc
}
```

Un `if (maybeShowNoDeathReveal()) return` se suma a esta lista, lógicamente junto a los otros dos chequeos de amanecer (muerte/silencio), y `isNoDeathRevealRunning` se suma al guard del principio de la función.

### Señal de "nadie murió"

`GameEngine.resolveDawn()` (`GameEngine.kt:354-401`) ya produce el string literal `"Amanecer: no murio nadie."` cuando no hubo víctima o estaba protegida (líneas 359-360), guardado en `session.publicAnnouncement`. Es exactamente el mismo mecanismo de texto que ya usan los detectores de muerte (`"amanecer: murio {name}"`) y silencio (`"{name} no puede hablar ni votar hoy"`) en `GameplayTableUi.newlyKilledAtDawn`/`newlySilencedAtDawn` (`GameplayTableUi.kt:442-467`). Se agrega un helper análogo, `GameplayTableUi.wasNoDeathAtDawn(session): Boolean`, chequeando ese substring en minúsculas.

**Diferencia importante con los otros dos detectores:** muerte/silencio dedupean por nombre de jugador (`knownDeadPlayers`/`knownMutedPlayers: Set<String>`). "Nadie murió" no tiene jugador para usar como clave — el dedupe tiene que ser por ronda/fase (ej. recordar el último `session.round` o `phaseIndex` ya mostrado) para no re-encolarlo en cada re-render del mismo amanecer.

### Qué crear

1. **`NoDeathRevealAnimator.kt`** (archivo nuevo) — mismo shape de constructor que `SilenceRevealAnimator`: `overlay`, `content`, las vistas del efecto visual elegido, `dp`, `onFinished`. **Sin `playerName`** (no es un evento por jugador). Secuencia: entrada (fade + scale, igual que los otros dos) → efecto central → sostenido (`hold`) → salida → `onFinished()`.
2. **Bloque XML `noDeathRevealOverlay`** en `activity_gameplay_mock.xml`, ubicado junto a `silenceRevealOverlay` (~línea 587). Mismo root `FrameLayout` con scrim `#B8000000`, mismo `bg_reveal_text_shade` para el texto, mismo marco ornamental por mapa (`revealPanelBackgroundForMap(session.mapKey)`, reutilizando la función ya existente en `applyRevealOverlayTheme()`).
3. **Estado en `GameplayMockActivity.kt`:** flag `isNoDeathRevealRunning`, campo `noDeathRevealAnimator`, y en vez de un `ArrayDeque` (no hay cola de jugadores) un simple `pendingNoDeathReveal: Boolean` + el dedupe por ronda mencionado arriba.
4. **Detección** en el mismo punto donde hoy se llama `collectNewlyDeadPlayers()`/`collectNewlyMutedPlayers()` (`GameplayMockActivity.kt:1238-1239`, dentro de `renderGame()`): agregar `collectNoDeathEvent()` análoga, usando `GameplayTableUi.wasNoDeathAtDawn(session)`.
5. **Extender los guards existentes** que hoy listan `isDeathRevealRunning`/`isSilenceRevealRunning` juntos, para que reconozcan también `isNoDeathRevealRunning`: el guard de `resumeGameFlowAfterBlockingUi()` ya mencionado, el de `handleGameplayBack()` (~`GameplayMockActivity.kt:928-935`, intercepta el botón atrás del sistema mientras hay un overlay bloqueante), el de resume-on-focus (~`880-888`), y los pares de cancelación en `onDestroy`/restore (~`781-785`, `822-824`, donde hoy se llama `cancelDeathReveal(...)` y `cancelSilenceReveal(...)` siempre juntos — agregar `cancelNoDeathReveal(...)` al lado).
6. **Ajustar el resume de música:** `finishDeathReveal()`/`finishSilenceReveal()` hoy solo retoman la música de fase cuando **ambas** colas (`pendingDeathReveals`, `pendingSilenceReveals`) están vacías (`GameplayMockActivity.kt:4585`). Sumar la nueva bandera a ese chequeo para que no se reanude la música a mitad de una secuencia de reveals encadenados.

### Dirección creativa elegida: "Amanecer dorado"

Confirmada con el usuario sobre 3 opciones (amanecer dorado / vela / campanas). Se eligió **amanecer dorado** por ser la más universal entre los 3 mapas (grecia/medieval/pampa — solo cambia el tono del color, no la forma) y la más simple de construir bien con assets propios (no hay arte reutilizable hoy: no existen drawables de paloma/paz/sol-reveal en el repo, hay que crearlos).

**Composición sugerida** (mismo lenguaje visual que los otros reveals — marco ornamental por mapa, fondo `bg_reveal_text_shade`, headline en `accent_gold`):

- Centro: un resplandor/sol que se expande desde un punto (radial gradient dorado, opcional 4-6 rayos finos rotando levemente hacia afuera), análogo en espíritu al "shine sweep" que ya existe en `centralPublicEventShine` pero llevado a la pieza central en vez de un detalle de fondo.
- Headline: `"AL AMANECER..."` (consistente con el reveal de muerte) seguido de un título corto tipo `"EL PUEBLO RESPIRA"` o similar (texto final a definir, tono: alivio, no celebración exagerada).
- Línea inferior: `"Nadie murió esta noche."`
- Sin botón "CONTINUAR" — se cierra sola tras un `hold` de ~1.5-2s (mismo orden de magnitud que `SilenceRevealAnimator`).
- Sin nombre de jugador (es un evento colectivo).

**Assets a crear** (no existen hoy, confirmado por búsqueda en `drawable*`/`raw`):
- Vector(es) drawable para el resplandor/rayos (puede ser geometría simple: círculo con gradiente radial + líneas finas, no requiere ilustración compleja).
- Sonido opcional: o silencio (dejar que hable la música ambiente), o un chime suave nuevo — decisión de producto, no bloquea la implementación si se deja sin SFX en una primera entrega.

### Constraints del ciclo (igual que en specs previas)

- No agregar funciones nuevas más allá de lo descripto — esto es estabilización/pulido de una superficie ya existente (el momento de amanecer), no una mecánica nueva.
- Mantener identidad medieval/dorada y el patrón visual ya usado por muerte/silencio (mismo marco por mapa, mismo scrim, misma tipografía).
- Gameplay es landscape-only — no se amplía a portrait.
- Refactors chicos y justificados; no reescribir `GameplayMockActivity.kt` más allá de los puntos de extensión ya señalados.
- No compilar — el usuario valida en Android Studio.

---

## Resumen de archivos a tocar/crear

**Parte 1 (saltar noche):**
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — `renderAdvanceButton()`, `handleCurrentPhase()` (solamente; no toca `GameEngine.kt`).

**Parte 2 (reveal sin muertes):**
- `app/src/main/java/com/traidores/juego/NoDeathRevealAnimator.kt` (nuevo)
- `app/src/main/res/layout/activity_gameplay_mock.xml` (nuevo bloque `noDeathRevealOverlay`)
- `app/src/main/res/drawable/` (nuevo arte vectorial para el resplandor)
- `app/src/main/java/com/traidores/juego/GameplayTableUi.kt` — `wasNoDeathAtDawn(session)`
- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — estado, detección, wiring al router, extensión de guards existentes.
