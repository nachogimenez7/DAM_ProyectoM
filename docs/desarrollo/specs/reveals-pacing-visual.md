# Spec — Pacing de reveals (URGENTE) + caracterización visual por mapa

> Handoff Claude (diseño) → Codex. Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio. Motivo: review del profesor — "las cosas pasan tan rápido que un jugador nuevo se pierde".

Decisiones tomadas: **tap para continuar** en los momentos dramáticos (con auto-avance de seguridad), **MODO TEST RÁPIDO apagado por defecto**, **caracterización temática por mapa** de los overlays (NO la pantalla final de datos).

---

## PARTE A — Pacing (URGENTE)

### Diagnóstico
- `DeathRevealAnimator` (`:84`): la secuencia es entrada(280) + impacto(420) + giro(~490) + **hold(2300)** + salida(320) ≈ 3,8s y **se auto-cierra** (`onAnimationEnd → finish → onFinished`). 2,3s para leer "quién murió + rol" es muy poco para un novato.
- La **expulsión ya tiene "CONTINUAR"** (tap); la **muerte y la "VÍCTIMA ELEGIDA" se cierran solas** → incoherente.
- `quickTestMode = true` por defecto en `LocalGameFactory.createSession` (`GameModels.kt`) → acelera fases (README "MODO TEST RÁPIDO"). Probable mayor culpable del "todo muy rápido".
- Varios auto-cierres por constante: `CENTRAL_PUBLIC_EVENT_DURATION_MS`, `INFORMATION_FEEDBACK_DURATION_MS`, `PHASE_ADVICE_DURATION_MS`.

### A.1 — Tap para continuar en momentos dramáticos
Aplicar a: **death reveal**, **víctima elegida** (info privada del asesino), y mantener en **expulsión** (ya lo tiene). Patrón unificado:
- El overlay llega a un estado "en espera" (reemplaza el `hold` fijo de 2300ms) con un botón/zona **"Continuar"** (o "tocá para continuar").
- **Auto-avance de seguridad** a los **~9s** si nadie toca (evita que el juego se trabe; importante también para online).
- Concreto en `DeathRevealAnimator`: cortar la secuencia antes del `hold`/`exit`; al terminar el giro, quedarse visible y exponer un callback `onReadyToContinue`; el Activity muestra "Continuar" y arma el timeout de 9s; al tocar (o timeout) se corre `exit` + `onFinished`. Mantener `cancel()` para ciclo de vida.
- Reusar el patrón del botón "CONTINUAR" de la expulsión para consistencia visual.

### A.2 — MODO TEST RÁPIDO apagado por defecto
- `LocalGameFactory.createSession(...)`: `quickTestMode = false` por defecto (ritmo de presentación). Mantener el **toggle en el lobby** (opt-in para pruebas rápidas). Revisar también `debugBotsObeyVoteCommands = true` ahí — apagarlo para la experiencia real (dejar como opción debug).
- **Cuidado:** `createSession` se usa en tests y en `createOnlineLobby` (que ya fuerza `quickTestMode=false`). Verificar/ajustar tests que asuman el default anterior.

### A.3 — Holds de banners (legibilidad)
- Subir las duraciones de auto-cierre de los banners no-tap (p. ej. `CENTRAL_PUBLIC_EVENT_DURATION_MS`, `INFORMATION_FEEDBACK_DURATION_MS`) a valores cómodos para leer (afinar de a poco; objetivo: que un novato alcance a leer). Los momentos dramáticos van por tap (A.1), no por este timer.

### A.4 — Online (no romper)
- El tap-para-continuar es **presentación local por cliente**; el avance autoritativo lo sigue gobernando el host/timer. El auto-avance de seguridad (~9s) garantiza que ningún cliente trabe la partida. No bloquear el flujo online esperando un tap.

**➡️ Revisión de Claude** tras la Parte A (es lo urgente).

---

## PARTE B — Caracterización visual por mapa (overlays)
Pulir e inmersar los overlays de **muerte**, **expulsión** y **víctima elegida** con ambientación por `mapKey`. **Excluir** la pantalla final de datos (queda como está).

- **Pampa:** cuero/polvo, madera gastada, tonos cálidos.
- **Grecia:** mármol claro, dorado, líneas sobrias.
- **Medieval:** madera oscura, sello de cera, hierro.
- Reusar la dirección visual ya usada (`bg_chat_box_*` / drawables temáticos); fondo/marco/acentos del overlay según mapa.
- Mejorar animación/tipografía/efectos manteniendo legibilidad (la muerte ya tiene salpicaduras de sangre; expulsión y víctima pueden recibir un tratamiento acorde).
- Botón "Continuar" con estilo consistente entre los tres overlays.
- Respetar `appliedGameplayTextScale`.

---

## Verificación
- Un jugador nuevo alcanza a leer **quién murió + rol** sin apuro; toca "Continuar" para seguir; si no toca, avanza solo a ~9s.
- Local arranca en ritmo de presentación (no test rápido).
- Online no se traba esperando taps.
- Los tres overlays se ven ambientados por mapa; la pantalla final de datos no cambió.

## Documentación a actualizar al cerrar (Claude)
- `docs/general/07-flujo-funcionamiento.md` (pacing: tap-para-continuar; quickTestMode off por defecto).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: pacing de reveals / default de presentación).
- `docs/desarrollo/backlog.md` (D7/pacing; quickTestMode default).
- `README.md` (el modo local ya no arranca en test rápido).
