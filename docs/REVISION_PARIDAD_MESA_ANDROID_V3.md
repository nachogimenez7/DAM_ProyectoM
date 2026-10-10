# Revisión de paridad de la mesa Android V3

Fecha: 8/10/2026. Autor: Claude (solo lectura). Implementa y verifica: Codex.
Objetivo del usuario: online V3 correcto y con la sensación de la partida habitual
para una beta **antes del sábado 10/10**. Esta revisión no edita código ni contrato,
y una propuesta de este documento no cuenta como cambio aplicado.

Convención: **[leído]** = comprobado por lectura de código; **[captura]** = comprobado
en una imagen de `output/`; **[hipótesis]** = falta confirmación nativa.

## 0. Resumen ejecutivo

1. **La expulsión diurna nunca se presenta en V3 mientras la partida sigue** [leído].
   El servidor aplica la muerte y emite `DAY_EXPULSION` al *vencer* `RESULTADO` y, en
   la misma transacción, pasa a `NOCHE` de la ronda siguiente. La proyección solo
   publica eventos de la ronda vigente, así que el evento nunca llega. Android lo espera
   en `RESULTADO`, donde todavía no existe. Además, en un caso límite se pierde la
   expulsión en el servidor (ver §3.2).
2. **Las fases de transición duran 4 s** (`transicionSeg`, rango de 1 a 10:
   `functions/src/onlineStartCore.js:200`) y cada cambio de `phaseIndex` cancela todas las
   ceremonias (`ServerGameTableRenderer.kt:194` → `stopReveals()`). La muerte nocturna,
   el silencio encolado detrás de ella y cualquier ceremonia de expulsión se cortan
   [leído]. Las capturas dirigidas no lo muestran porque usan fixtures con plazos largos:
   `server-v3-table-recount.png` muestra **181 s** en `RECUENTO` [captura].
3. **El audio está casi ausente** [leído]. La mesa V3 solo usa `TIE_BREAK`, `SILENCE`,
   `ORACLE` y `PAYADOR`. No hay música (`MusicManager`), transición día/noche,
   `NIGHT_FALL`, `DAWN`, `ELIMINATION`, `NO_DEATH`, `EXPULSION`, `JESTER`, `VOTE_CAST`
   ni música de victoria.
4. **El Bufón no tiene ceremonia** [leído]. `victoriasEspeciales` solo se publica en
   `FINALIZADA` (`onlineGameCore.js:506`). La partida habitual muestra «X ERA EL BUFÓN»
   al expulsarlo (`GameplayMockActivity.kt:11659`). **Esto requiere una decisión del usuario
   sobre privacidad y reglas.**

Propuesta mínima: ajustar **una función del motor** (la expulsión se aplica al *entrar*
en `RESULTADO`), definir una política cliente de «ceremonia que sobrevive al cambio
de fase» y conectar animadores y sonidos ya existentes. No hacen falta publicaciones,
listeners ni callables nuevos.

## Decisiones del usuario (8/10, después de la revisión)

1. **Plazo de RESULTADO: C1 aprobado.** Con expulsión, `RESULTADO` dura
   `max(transitionSeconds, 8)` s; si el expulsado es el Bufón, 12 s. Sin expulsión
   sigue en `transitionSeconds`. Lo calcula el servidor al entrar en la fase.
2. **Bufón público: aprobado.** Al aplicarse la expulsión, la proyección publica
   `victoriasEspeciales` sin esperar al final, y todos ven «X ERA EL BUFÓN».
3. **Muerte al entrar en RESULTADO (opción C): aprobada con una condición.** Todos
   tienen que enterarse a la vez, sin anticipos. Esto obliga a aplicar las protecciones
   de §3.6 en el cliente y en las permisiones.

## 1. Matriz por fase

| Fase | Referencia habitual | Diferencia V3 comprobada | Reutilizar | Datos | Criterio de aceptación |
|---|---|---|---|---|---|
| REPARTO | Vista previa del rol y elección del Desertor | OK [captura `deserter-initial`]. Sin `CARD_DEAL`; la referencia lo reproduce en `AssigningRolesActivity` [leído] | `RolePreviewAnimator` (ya está) | — | El rol se abre una vez por partida; «YA LEÍ MI ROL» envía `role_ack` una sola vez |
| NOCHE | `DayNightTransitionAnimator`, `NIGHT_FALL`, música nocturna | Cambio brusco de fondo, sin sonido ni música [leído] | `DayNightTransitionAnimator`, `GameplaySoundResolver.transitionSoundFor`, `MusicManager` | `fase`, `phaseIndex` | Una transición y un sonido por `matchId:phaseIndex`; no se repiten al reconectar en la misma fase; las cartas de acción quedan usables antes de 3 s |
| AMANECER | `DAWN`, `DeathRevealAnimator` con `ELIMINATION` y continuación automática, `NO_DEATH`, silencio | No se oye la muerte ni «sin víctimas»; la continuación es solo manual; a los 4 s `DIA_DEBATE` cancela la muerte y **descarta el silencio encolado** [leído] | Animadores ya conectados + política §2 | `eventosPublicos` (NIGHT_DEATH/DAWN_NO_VICTIMS) y `muteado` | Con muerte y silencio en la misma noche, ambas ceremonias se ven completas una vez aunque el debate ya haya empezado |
| DIA_DEBATE | Oráculo, revelación del Alcalde y crónica | Oráculo OK [captura `oracle`]. `MAYOR_REVEALED` sin ceremonia: solo cambia la carta del roster [leído] | Revelación del Oráculo (ya está); para el Alcalde, a definir (pulido) | `alcaldeRevelado` | El Oráculo no tapa a una ceremonia del amanecer todavía en curso: se encola |
| CONTRAPUNTO | Ilustración del Payador | OK [captura `counterpoint`] | — | — | — |
| VOTACION | `VOTE_CAST` al resolverse | Sin sonido [leído] | `GameplaySoundResolver.resolvedActionSound` | Cambio de fase | Un sonido por cierre de votación |
| RECUENTO_VOTOS | Fichas de voto animadas | Totales estáticos [captura `recount`]. Es aceptable: V3 no publica votantes | `VoteResultAnimator.showServerTotals` (ya está) | `votosTotales` | No se inventan votantes |
| DESEMPATE / ALCALDE | Ventana de desempate | OK [capturas `tie`, `mayor-muted`] | — | — | — |
| **RESULTADO** | Recuento → `playExpulsion` (bota, `EXPULSION`) → `JesterVictoryAnimator` si era Bufón → noche | Vuelve a mostrar los totales con otro título (doble aparición); no hay expulsión, patada, sonido ni Bufón [leído] | `VoteResultAnimator.playExpulsion` / `showNoExpulsion`, `JesterVictoryAnimator` (los overlays ya están en `activity_gameplay_mock.xml`) | Requiere el cambio de §3 | Ver §3.4 |
| DESERTOR_RECONSIDERACION | Diálogo del Desertor | OK [captura `deserter-rethink`] | — | — | — |
| FINALIZADA | `WinnerRevealAnimator` y música de victoria | Pantalla OK [captura `a56-winner`]. Sin música; resumen con duración «—» y la línea fija «Eventos disponibles de la última ronda» [leído] | `MusicManager.playVictoryMusic` | `ganador` | La música suena una vez y se detiene al salir o en la revancha |

## 2. Política cliente: ceremonias y cambios de fase

Hoy `render()` llama a `stopReveals()` en cada cambio de `phaseIndex`. Se propone
**separar las ventanas de acción de las ceremonias**:

- Siguen cerrándose al cambiar de fase: desempate, diálogo del Desertor, selección de
  objetivo y vista previa del rol. Son controles atados a un plazo.
- Pueden continuar en la fase siguiente: muerte, sin víctimas, silencio, expulsión y
  Bufón. Solo continúan si se cumplen **todas** estas condiciones:
  - no hay ganador;
  - quedan al menos 10 s en la fase nueva (`deadlineMs - now`);
  - la ceremonia avanza sola, sin esperar «Continuar»: reutilizar el patrón
    `deathRevealContinueTimeoutRunnable` de la partida habitual;
  - tiene un tope de unos 6 s por ceremonia.
  Si no se cumplen, saltan al cuadro final o se descartan. Nunca detienen el reloj ni
  envían acciones.
- Lo que aparece en la fase nueva (Oráculo, Contrapunto, ventana de acción) **se encola
  detrás** de la ceremonia en curso, en lugar de reemplazarla.
- Deduplicación: ceremonias por `matchId:seq` (el cursor ya existe en
  `ServerGamePresentationTracker`); transiciones y música por `matchId:phaseIndex`.
  No hacen falta listeners ni lecturas nuevas.

Con un debate de 30 a 180 s y una noche de 10 a 90 s, este margen no tapa ningún plazo
que esté por vencer. Una noche configurada en 10 s nunca recibe una ceremonia arrastrada,
porque no deja los 10 s de margen requeridos.

## 3. Expulsión, Bufón y reconexión

### 3.1 Problema confirmado [leído]

- `afterVoteCount` y `decidir_empate` entran en `RESULTADO` con `expulsadoDia` fijado y
  el expulsado **todavía vivo** (`onlineGameCore.js:405-406` y `242-244`).
- Al vencer `RESULTADO`, `resolveResult` (`:441-449`) marca la muerte, emite
  `DAY_EXPULSION` (ronda N) y, si no hay ganador, ejecuta `round++` y `startNight`. A su vez,
  `startNight` pone `eliminationUid = null`.
- La proyección filtra `event.ronda === state.round` (`:498`), así que la nueva `NOCHE`
  (ronda N+1) no incluye `DAY_EXPULSION`. `revealEvents` lo espera en `RESULTADO`
  (`ServerGameTablePresentation.kt:70`): **la condición nunca se cumple**.
- Con ganador, la expulsión y `VICTORY` llegan juntas en `FINALIZADA`, y la mesa pasa
  directo a la pantalla de victoria (`revealEvents` exige `winner == null`).
- Con el Desertor (ronda ≥ 4): `DESERTOR_RECONSIDERACION` se abre sin cambiar de ronda,
  y `DAY_EXPULSION` sí sería visible, pero en una fase que Android no presenta.
- **Defecto latente del servidor:** si alguien abandona *durante* `RESULTADO` y eso abre
  la ventana del Desertor, `resumeAfterDeserterWindow` con `phase === "RESULTADO"` ejecuta
  `round++; startNight` (`:102`) **sin ejecutar `resolveResult`**. El expulsado sigue vivo.
  Si en cambio el abandono termina la partida, el expulsado tampoco queda marcado. Es poco
  frecuente, pero modifica el resultado.

### 3.2 Opciones

| | A. Solo cliente | B. Ampliar la proyección | **C. Aplicar la expulsión al entrar en RESULTADO** |
|---|---|---|---|
| Cambio | Ceremonia en `RESULTADO` con `expulsadoDia` (vivo) y la muerte deducida en `NOCHE` | Incluir en la proyección eventos de la ronda anterior durante `NOCHE` | Mover las líneas 442-447 a un `enterResult()` que usan las cuatro entradas a `RESULTADO`; al vencer, solo `evaluateWinner` / `startNight` |
| Rol revelado (si `revelarRolesAlMorir`) | No disponible en `RESULTADO` | En `NOCHE` | Ya disponible en `RESULTADO` |
| Tapa acciones | Sí: la revelación del rol cae al inicio de la noche | Sí: la ceremonia cae sobre la noche | No: ocurre en una fase sin acciones |
| Defecto latente | Sigue | Sigue | **Corregido** |
| Bytes y publicaciones | 0 | Más bytes en cada `NOCHE` y en toda la ronda siguiente | 0 publicaciones extra; unos +150 a 300 B en la publicación de `RESULTADO` (un evento más y el `roleView` del expulsado) |

**Recomendación: C.** Coincide con lo que Android ya espera (`DAY_EXPULSION` en
`RESULTADO`), no agrega fases ni publicaciones y corrige el defecto latente. Efectos
que conviene marcar:

- **Momento de la muerte (cambio de reglas menor, a marcar):** el expulsado queda muerto
  durante `RESULTADO` en lugar de al final. Recibe `deadChat` unos segundos antes.
  `publicChat` no existe en `RESULTADO`. El resultado de la partida no cambia, porque el
  ganador se sigue evaluando al vencer `RESULTADO`.
- Un abandono durante `RESULTADO` ahora evalúa el ganador con el expulsado ya muerto,
  igual que hará el vencimiento.
- Pruebas que cambian: `onlineGameCore.unit.test.js`. Los casos que hacen
  `phase(state,"RESULTADO")` y fijan `eliminationUid` deben pasar por la entrada nueva
  (líneas ~247, 263, 325, 357, 380). También hay que regenerar las 55 proyecciones
  (`scripts/generate-server-client-fixtures.cjs` → `server_game_v3.json`).
- iOS no presenta todavía el gameplay V3 (`CONTRATO_AUTORIDAD_SERVIDOR_V3.md:405-414`),
  así que no hace falta cambiar código iOS. Sí conviene actualizar el contrato.
- **Requiere desplegar Functions** antes de la beta. El encargo de revisión no despliega.

### 3.3 Plazo de RESULTADO (decidido: C1)

`playExpulsion` dura unos 5,8 s sin revelar el rol y unos 6,5 s revelándolo; el Bufón
agrega hasta 8 s (`JESTER_VICTORY_DURATION_MS`). Con 4 s de `RESULTADO` no entra. Hay
dos opciones:

- **C1 (recomendada, cambia plazos):** cuando hay expulsión, `RESULTADO` dura
  `max(transitionSeconds, 8)`, y 12 s si el expulsado es el Bufón y se acepta §3.5.
  Cuesta 0 publicaciones extra y suma unos 4 s por ronda con expulsión.
- **C2 (sin cambiar plazos):** el cliente comprime la ceremonia al tiempo restante:
  entrada de la carta, rol si corresponde, bota e impacto en unos 3,5 s. El Bufón se
  arrastra a la noche con la política §2 (tope de 6 s, noche ≥ 10 s restantes).

### 3.4 Comportamiento esperado del cliente

- `RECUENTO_VOTOS` → `showServerTotals` (como hoy).
- Entrada en `RESULTADO`:
  - si hay `DAY_EXPULSION` sin ver: `voteResult.playExpulsion(session)` **sobre el mismo
    overlay**, sin volver a mostrar los totales, con `onImpact = EXPULSION`;
  - si hay `DAY_NO_EXPULSION`, `TIE_NO_EXPULSION` o `MAYOR_NO_DECISION`:
    `showNoExpulsion()`;
  - `session.dayEliminationTarget`, `revealRolesOnDeath` y `alcaldeCorruption`
    (`rondaVoto==4`) salen del público de V3. El rol solo sale de `publicRoleKey`.
- Al terminar la expulsión del Bufón (si se acepta §3.5): `JesterVictoryAnimator` + `JESTER`.
  Botón «CONTINUAR PARTIDA», sin acciones de espectador local.
- Con ganador: la expulsión final ya existe en `RESULTADO`, así que se presenta antes;
  `FINALIZADA` muestra la victoria después. No se reproduce dentro de `FINALIZADA`.
- `DAY_EXPULSION` deja de pasar por `DeathRevealAnimator`
  (`ServerGameTableRenderer.kt:658`) para no duplicar la ceremonia.

**Reconexión durante la ceremonia:**

- con `seq` ya visto: cuadro final estático (carta con bota) y ninguna animación;
- con `seq` sin ver y al menos 2,5 s restantes: versión comprimida;
- con `seq` sin ver y menos tiempo: solo el estado final;
- al reconectar en `NOCHE`: nada. El roster muestra la bota y el rol, si es público.

La muerte nunca depende del evento: el roster usa `vivo`/`causaEliminacion`.

### 3.5 Bufón (decidido: público al expulsarlo)

La partida habitual revela «X ERA EL BUFÓN» a todos al expulsarlo, aunque
`revelarRolesAlMorir` esté apagado. Para igualarla, V3 tendría que publicar
`victoriasEspeciales` **sin esperar al final**, solo una vez aplicada la expulsión.
Costo: unos 100 B en cada publicación pública de esa partida a partir de la expulsión.
Sigue siendo 0 publicaciones extra. `accountHistoryService.js:40` no cambia.
Decidido: se publica. La publicación ocurre en la misma escritura de entrada a
`RESULTADO`, nunca antes.

### 3.6 Sin anticipos: todos se enteran a la vez (condición del usuario)

Con la opción C, una sola publicación de entrada a `RESULTADO` trae juntos `vivo:false`,
`causaEliminacion:VOTE`, el rol (si `revelarRolesAlMorir`), `DAY_EXPULSION` y, si
corresponde, la victoria del Bufón. Todos los teléfonos lo reciben al mismo tiempo.
El riesgo de anticipo lo crea **el cliente**, si dibuja esos datos antes de que la
ceremonia los revele. Reglas obligatorias:

1. **Roster congelado durante la ceremonia.** Mientras se reproduce la expulsión, la
   carta del expulsado en el roster sigue como estaba al votar: viva, sin bota ni rol.
   Se actualiza en el impacto de la bota (`onImpact`) o al terminar la ceremonia.
   Si la ceremonia se salta (reconexión con poco tiempo o `seq` ya visto), se muestra
   el estado final directamente. Es una retención solo de presentación y no cambia
   ninguna acción: en `RESULTADO` no hay acciones.
2. **Textos retenidos.** La barra de anuncio (`phaseSubtitle`), el resumen de eventos
   (`eventLogSummary`) y `announceForAccessibility` no muestran «X fue expulsado» ni
   «ERA EL BUFÓN» hasta que la ceremonia llegue a ese punto. Mientras tanto muestran el
   anuncio de la votación (por ejemplo «X recibió la mayoría de los votos»), que ya era
   público en `RECUENTO_VOTOS`.
3. **Rol oculto hasta la tarjeta.** El rol del expulsado aparece solo en el paso
   «CARTA REVELADA» de `playExpulsion`. Ni la vista previa ni el contentDescription del
   roster lo anticipan. El Bufón se anuncia después del impacto, nunca antes.
4. **Chat de muertos (servidor, recomendado).** Para que el expulsado no escriba en el
   chat de muertos antes de que el resto vea su expulsión, `deadChat` es falso para
   `eliminationUid` mientras la fase sea `RESULTADO`:
   `deadChat: !p.left && !state.winner && !p.alive && !(state.phase === "RESULTADO" && p.uid === state.eliminationUid)`.
   Se habilita en la noche, como hoy. No cambia publicaciones.
5. **Mismo inicio para todos.** La ceremonia arranca cuando llega la publicación de
   `RESULTADO`, no por relojes locales. Las diferencias entre teléfonos son solo de red.
   No se agrega ninguna sincronización extra.

Criterio observable: en dos teléfonos lado a lado, ninguno muestra la bota, el rol ni
el texto de expulsión antes de que su propia ceremonia llegue a ese punto. En el
teléfono del expulsado, el chat de muertos sigue cerrado hasta la noche.

## 4. Orden de implementación

**Bloqueantes para la beta**

1. Servidor: `enterResult()` (C), pruebas del motor y proyecciones regeneradas. Incluye
   una prueba del abandono durante `RESULTADO` con la ventana del Desertor.
2. Servidor, según lo decidido: plazo C1 de `RESULTADO` (8 s, o 12 s con Bufón),
   `victoriasEspeciales` público al expulsar y `deadChat` retenido (§3.6.4).
3. Cliente: política §2 (no cancelar ceremonias al cambiar de fase, continuación
   automática y cola), presentación de `RESULTADO` según §3.4 y protecciones contra
   anticipos de §3.6.
4. Sonidos de eventos: `ELIMINATION`, `NO_DEATH`, `EXPULSION`, `JESTER`, `VOTE_CAST`,
   `NIGHT_FALL` y `DAWN`. Son llamadas a recursos existentes con deduplicación.
5. Despliegue restringido de Functions y prueba en el A56 con plazos reales
   (`transicionSeg=4`), grabando la pantalla.

**Sensación habitual (después, si alcanza el tiempo)**

6. Música por fase y de victoria (`MusicManager` recibe un `GameSession`: armar un
   adaptador mínimo desde la proyección o agregar una sobrecarga por fase y mapa).
7. `DayNightTransitionAnimator` sin bloquear: sus botones de acción se habilitan igual.
8. Doble aparición del recuento, resumen final (duración, crónica) y ceremonia
   del Alcalde revelado.

**Fuera del alcance de la beta:** emotes V3 (protocolo nuevo), crónica completa de la
partida (la proyección solo trae la ronda vigente) y votantes individuales (V3 no
los publica).

## 5. Pruebas de regresión

| Escenario | Automatizable (unitaria/motor/AVD) | Observación visual (A56, plazos reales) |
|---|---|---|
| Reparto y Desertor inicial | Sí (existe) | Rol legible antes de la noche |
| Poderes nocturnos y voto | Sí (existe) | — |
| Muerte + silencio en la misma noche | Sí: cola y deduplicación por `seq` | Se ven ambas completas aunque el debate empiece |
| Amanecer sin víctimas | Sí | Sonido una vez |
| Expulsión con y sin `revelarRolesAlMorir` | Motor: muerte y evento en `RESULTADO`. Android: `revealEvents` | Bota, sonido y rol, sin doble recuento |
| Sin anticipos (§3.6) | Android: roster y textos retenidos hasta `onImpact`; motor: `deadChat` falso en `RESULTADO` para el expulsado | Dos teléfonos lado a lado: nadie ve bota, rol ni texto antes de su ceremonia |
| Expulsión que termina la partida | Motor: `RESULTADO` → `FINALIZADA` | Expulsión primero, victoria después |
| Bufón expulsado (si §3.5) | Motor: `victoriasEspeciales` antes del final | Ceremonia una vez y la partida continúa |
| Desempate, Alcalde y corrupción | Sí (existe) + `rondaVoto==4` en `RESULTADO` | Texto «CORRUPCIÓN EN EL PUEBLO» |
| Desertor tras la expulsión (ronda ≥ 4) | Motor (existe) | La ventana no tapa la ceremonia ya vista |
| Abandono durante `RESULTADO` | **Motor nuevo** (defecto latente) | — |
| Reingreso a mitad de la ceremonia | Android: cursor visto o no visto | Sin repetición; cuadro final |
| Revancha | Sí (existe) | Sin música ni ceremonias de la partida anterior |

## 6. Archivos y puntos de integración

- `functions/src/onlineGameCore.js`: `resolveResult` (441), `afterVoteCount` (405, 414),
  `decidir_empate` (242), vencimiento de `ALCALDE_DESEMPATE` (475), `resumeAfterDeserterWindow`
  (102) y proyección `victoriasEspeciales` (506).
- `ServerGameTableRenderer.kt`: `render()` (194, 216-228), `stopReveals()`,
  `playNextReveal()` y la construcción de `VoteResultAnimator` (161, pasar `onImpact`).
- `ServerGameTablePresentation.kt`: `revealEvents` y una función pura nueva para la
  política de arrastre. Tiene que ser testeable sin vistas.
- `docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`: semántica de `RESULTADO` y del Bufón.

## 7. Dudas e información faltante

- **Captura faltante:** una grabación en el A56 con plazos reales de una ronda completa
  con muerte, silencio y expulsión. Las capturas actuales son de fixtures con plazos
  largos y no validan los 4 s.
- Confirmar si el reparto online habitual pasaba por `AssigningRolesActivity` (`CARD_DEAL`)
  o si V3 puede omitirlo.
- `AFK_EXPULSION` y `PLAYER_LEFT` no tienen ceremonia en V3. Confirmar el comportamiento
  habitual antes de agregarla.
- Duración real de `DayNightTransitionAnimator` cuando se escala a 4 s: [hipótesis] entra
  sin tapar acciones.
