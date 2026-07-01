# Spec — Correcciones ronda 2: saltar noche + reveal "nadie murió"

> Handoff Claude (diseño/review) → Codex (implementación). Continuación de `saltar-noche-y-reveal-sin-muertes.md` (ya implementado, cambios sin commitear en el árbol de trabajo al momento de escribir esto). Código = fuente de verdad. Diff acotado; el usuario valida en Android Studio.

**Contexto:** la implementación de la ronda 1 ya está en el árbol de trabajo (`NoDeathRevealAnimator.kt`, cambios en `GameplayMockActivity.kt`/`GameplayTableUi.kt`/`activity_gameplay_mock.xml`, drawables `no_death_sun_*`). El usuario probó y reporta tres cosas: un bug funcional grave en el salto de noche, una decisión pendiente sobre el contenido del ícono central del reveal, y un ajuste visual menor de superposición.

---

## 1. 🔴 BUG — La noche se salta sin que el jugador toque el botón de salto

### Reporte del usuario
Partida nueva, jugador humano = Aldeano (sin acción nocturna). El usuario dice explícitamente: **"no toqué absolutamente ningún botón, solo le di a continuar y ya estaba en el día 1 con todas las acciones hechas."** No es la sensación de "el salto se siente apresurado" — es que el jugador nunca vio ninguna sub-fase nocturna ni tocó el botón "SALTAR NOCHE" en ningún momento.

### Lo que confirmamos leyendo el código (y lo que NO logramos explicar)

`skipRemainingNight()` (`GameplayMockActivity.kt`, agregado en la ronda 1) es la única función que recorre varias sub-fases nocturnas de una sola vez. Trazamos todos sus call sites:

```
grep "skipRemainingNight\|canSkipRemainingNight" GameplayMockActivity.kt
```

Aparece exactamente en 4 lugares: la definición de `canSkipRemainingNight()`, su uso en `renderAdvanceButton()` (solo para decidir el label/enabled del botón, no ejecuta nada), su uso en `handleCurrentPhase()` (`if (canSkipRemainingNight()) { skipRemainingNight(); return }`), y la propia definición de `skipRemainingNight()`.

`handleCurrentPhase()` a su vez **solo se invoca desde `btnAction.setOnClickListener { handleCurrentPhase() }`** (un tap real) o desde un `post` ligado a promoción de host online (`promoteToOnlineHost`, irrelevante en modo local). **No encontramos ningún timer, `Runnable` programado, ni callback que llame a `handleCurrentPhase()` automáticamente en modo local.** Es decir: por lectura estática del código, no debería ser posible que `skipRemainingNight()` se ejecute sin un tap sobre el botón de acción principal (`btnAction`).

Esto no significa que no haya bug — significa que **hace falta reproducirlo con logging/debugger en el dispositivo**, porque el camino no es obvio desde el código solo.

### Dónde mirar primero (hipótesis, no confirmadas)

1. **`canSkipRemainingNight()` se vuelve `true` en el primer frame de la noche para roles sin acción** (correcto por diseño: "el botón aparece desde el arranque para roles sin acción nunca"). Si en algún punto del flujo de arranque (`onCreate` → `showRolePreview` → `closeRolePreview` → `resumeGameFlowAfterBlockingUi` → `scheduleAutoAdvanceIfNeeded`) el `btnAction` ya está habilitado con label "SALTAR NOCHE" en el mismo frame/gesto donde el usuario todavía tiene el dedo sobre la pantalla (por ejemplo, justo debajo de donde estaba el botón "EMPEZAR" del preview de rol, `btnContinueRolePreview`), un segundo tap accidental (o el mismo gesto registrado dos veces) podría estar cayendo sobre `btnAction` ya en modo "SALTAR NOCHE" sin que el usuario lo perciba como una acción separada. Revisar el layout: ¿`btnAction` y `btnContinueRolePreview` ocupan la misma región de pantalla en algún momento de la transición?
2. **Confirmar que la fase `REPARTO` no está resolviendo instantáneamente hacia noche completa.** `REPARTO` se resuelve vía `GameEngine.startNight(session)` cuando expira su countdown de transición. Verificar que `activePhaseSeconds()` para `REPARTO` no devuelva `null`/`0` de forma que el ciclo `onCountdownExpired()` → `TRANSITION` stage → siguiente stage se resuelva en cascada sin pausa visible, y que esto no esté aconteciendo repetidamente en un mismo tick (loop) en vez de una vez por sub-fase.
3. **Instrumentar temporalmente `skipRemainingNight()`** con un log (`Log.d` o similar, quitar antes de cerrar) que imprima el stack trace o al menos un marcador claro cada vez que se ejecuta, y correr una partida nueva como Aldeano tocando únicamente los botones "EMPEZAR"/"CONTINUAR" que aparezcan, sin tocar nunca el botón de acción principal, para confirmar si el log aparece igual. Si aparece, el bug está en cómo se disparan los clicks (gesto fantasma/doble-registro); si no aparece pero igual se llega a Día 1, el salto real está pasando por otro camino que no vimos en esta lectura y hay que rastrear `session.phase`/`session.phaseIndex` en cada `renderGame()` para encontrar el salto.

### Prioridad
Este es el hallazgo más importante de esta ronda. Si se confirma que ocurre sin tap, **es peor que el problema original** (antes al menos se veían 5 esperas de 40s; ahora el jugador no ve la noche en absoluto). No avanzar con pulido visual del reveal hasta confirmar esto.

---

## 2. Ícono central del reveal "nadie murió" — dirección elegida y advertencia de alcance

El usuario decidió: quiere una **imagen de un sol resplandeciente con aurora**, reutilizable en los 3 mapas, en vez de dejar el círculo vacío (opción "solo luz") que hay hoy.

**Advertencia antes de implementar:** un agente de código generalmente no tiene capacidad de generar arte bitmap/ilustrado — puede construir drawables vectoriales (formas, gradientes, capas), pero no "pintar" una imagen fotorrealista o estilizada tipo aurora boreal. Antes de intentarlo, confirmar si hay alguna herramienta de generación de imagen disponible en el entorno de Codex. Si no la hay, seguir este plan B, que sí es 100% construible en vector:

- Mantener `no_death_sun_core.xml`/`no_death_sun_ring.xml` como base (gradiente radial dorado que ya existe), y **sumar 1-2 capas más de gradiente radial** por fuera del núcleo actual, con colores fríos suaves y baja opacidad (ej. un violeta/celeste pálido tipo `#33B7A8E0` o similar, transicionando a transparente) para simular el halo de una aurora rodeando el resplandor dorado central — todo dentro de drawables `<shape>` apilados, sin necesidad de ningún archivo de imagen nuevo.
- Si en algún momento se consigue una imagen real (el usuario la genera con otra herramienta y la coloca en `drawable-xxhdpi/`), reemplazar `no_death_sun_core`/`no_death_sun_ring` por esa imagen es un cambio de una línea (`android:src`) — dejar la puerta abierta pero no bloquear esta entrega por eso.
- Sea cual sea el resultado, debe funcionar igual de bien sobre los 3 fondos de mapa (grecia/medieval/pampa) sin recolorear por tema — es un elemento "neutro" del reveal, no parte de la ornamentación del marco.

---

## 3. Ajuste visual — el título "AL AMANECER..." se monta sobre el borde del marco

Confirmado en captura: el título tiene su propio fondo (`bg_reveal_text_shade`) y es el primer hijo dentro de `noDeathRevealContent`, que recibe el marco ornamental + padding (`dp(50), dp(58), dp(50), dp(48))`). Aun así, visualmente el título queda montado sobre el relieve superior del marco.

Ajuste sugerido: aumentar el padding superior de `noDeathRevealContent` (hoy `dp(58)`) en unos `10-14dp` adicionales, y/o sumar un `layout_marginTop` chico al propio TextView del título dentro del layout, hasta que quede completamente dentro del área oscura del marco, igual que en el reveal de muerte. Verificar visualmente en Android Studio (el motivo decorativo del marco no es parejo, así que puede requerir un poco más que el cálculo teórico).

---

## Resumen de archivos a tocar

- `app/src/main/java/com/traidores/juego/GameplayMockActivity.kt` — investigar el bug de la sección 1 (con logging temporal si hace falta); no tocar el resto de la lógica de skip hasta confirmar la causa.
- `app/src/main/res/drawable/no_death_sun_core.xml`, `no_death_sun_ring.xml` (y posibles nuevos drawables de halo) — nueva versión en capas para el efecto aurora.
- `app/src/main/res/layout/activity_gameplay_mock.xml` — padding/margen del título dentro de `noDeathRevealContent`.

---

## Nota — sobre el siguiente encargo

Hay un segundo brief ya escrito y esperando (`cierre-logica-gameplay-y-chat-inmersivo.md`: tests puntuales del motor de reglas + chat central más grande y con marco). **Recomendación: no enviarlo todavía.** Primero cerrar esta ronda de correcciones — en particular confirmar y resolver el bug de la sección 1 — para no hacerle pasar a Codex de un problema funcional sin resolver a trabajo nuevo en archivos distintos. Una vez confirmado en Android Studio que esta ronda quedó bien, se manda el segundo brief como encargo aparte.
