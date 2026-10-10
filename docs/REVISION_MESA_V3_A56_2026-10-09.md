# Mesa V3: causas de las fallas del A56 y correcciones — 9/10/2026

Trabajo de Claude a partir del handoff de Codex. Solo Android. No se tocaron iOS,
`sources/`, el backend, las reglas, App Check ni el gate. No se desplegó nada ni se
hizo commit.

## Síntomas que informó el usuario (segunda práctica, rol Asesino, en la noche)

El botón de matar no se ponía rojo, no aparecían carteles (solo textos), había demora,
ventanas encimadas o trabadas, botones que no respondían, fases que saltaban y faltaban
respuestas o anuncios.

## Cómo se reprodujo

Se usaron emuladores locales de Firebase con el motor real (Functions, Firestore, RTDB,
Tasks y Auth), bots autenticados y la app QA en un AVD Pixel 10.
Script: `scripts/play-server-client-android.cjs`. Se jugó con la mesa real, tocando la
pantalla, y se registró cada fase con registros de depuración (solo Debug).
Se hicieron tres partidas:

- **Partida 1 (5 jugadores, antes de corregir):** reparto → noche → amanecer → debate →
  votación → noche 2. Los traidores ganaron en la noche 2.
- **Partida 2 (5 jugadores, versión corregida):** noche y protección.
- **Partida 3 (8 jugadores, versión corregida):** como Asesino, completa hasta la
  victoria, más revancha.
- **Partida 4 (la revancha, 8 jugadores):** completa en 4 rondas, con una reconexión a
  mitad de la noche.

## Causas encontradas

### Presentación (Android): confirmadas y corregidas

1. **Botón de matar gris** (confirmado en código y captura). `controls()` coloreaba
   el botón con la etiqueta interna del servidor («ELEGIR VÍCTIMA»), que
   `GameplayTableUi.actionToneFor` no reconoce. Resultado: tono por defecto siempre,
   incluso después de elegir «MATAR A X».
2. **Faltaban los carteles de confirmación** (confirmado). En la mesa común, cada poder
   nocturno abre la ventana privada («VÍCTIMA ELEGIDA», «SILENCIO REGISTRADO»,
   «PROTECCIÓN REGISTRADA», «INVOCACIÓN REGISTRADA») y las decisiones de día muestran un
   aviso. V3 solo cambiaba el texto a «Acción registrada.» tres a cinco segundos después.
3. **El voto no se emitía al tocar la carta** (confirmado). En la mesa común
   (`DirectVotePolicy`), tocar la carta vota de inmediato: «Votaste a X. Podés cambiar
   hasta el cierre.» y «✓ VOTO REGISTRADO». V3 exigía tocar la carta y después el botón;
   si no se tocaba el botón, el voto no salía. Es la explicación más probable de
   «botones que no respondían».
4. **La partida terminaba sin mostrar la última muerte** (confirmado en captura). Cuando
   una noche termina la partida, el servidor publica `FINALIZADA` con `NIGHT_DEATH` en la
   misma ronda. `revealEvents` exige `winner == null`, así que V3 saltaba directo a la
   victoria. Es una de las causas de «fases que saltan».
5. **Faltaba la presentación de los compañeros traidores** (`TraitorRevealAnimator`) y
   **el aviso central** («ALCALDE REVELADO», «FUERA DEL PUEBLO»).
6. **La victoria usaba textos y diseño propios** («GANÓ X» en lugar de «VICTORIA DEL
   PUEBLO / La plaza vuelve a respirar.»).
7. Texto menor: a un rol sin acción nocturna le decía «Puedes mirar la noche o
   saltarla». En V3 no se puede saltar la noche.

### Servidor: no se corrigieron (son decisiones de backend)

8. **Demora entre la acción y la pantalla.** `accionPartidaV3` confirma la transacción,
   pero **no publica la proyección**: lo hace después el trigger `publicarPartidaV3`
   (Firestore → RTDB, `minInstances: 0`). Cada respuesta visible necesita ese segundo
   salto, que en la nube suma la latencia del trigger y, si está frío, el arranque.
   `recuperarFaseV3` y `resolverFaseV3` sí publican en el mismo paso.
   En emuladores locales, las acciones tardaron entre 2,2 y 3,5 s (contención de
   transacciones con los bots).
9. **«Resolviendo…» en cada cambio de fase.** La tarea vence a deadline + 1,5 s
   (`DEADLINE_MARGIN_MS`). En la nube se suma el arranque en frío de `resolverFaseV3`
   (São Paulo, `minInstances: 0`). En los emuladores la cola de tareas disparaba antes
   de hora y los reintentos con espera creciente llegaron a 7 s de atraso. **Eso es del
   emulador**, no de Cloud, y no se tomó como evidencia de producción.

### Otros hallazgos (sin cambio)

- Al empezar una partida V3, `LobbyActivity` intenta renovar el permiso de anfitrión del
  online anterior (`host_lease_refresh_failure`). Las reglas lo rechazan con
  `PERMISSION_DENIED`. El jugador no lo ve, pero no debería ejecutarse en salas V3.
- La insignia del Payador dice «ELEGIR PARA CONTRA…» (texto cortado). En la mesa común
  la acción se llama «SEÑALAR».
- La presentación de traidores usa el panel por defecto. La mesa común le aplica además
  el tema del mapa (`applyTraitorRevealOverlayTheme`).
- Si el Alcalde decide al instante, la ventana «ÚLTIMA PALABRA» aparece menos de 0,1 s.

## Correcciones (Android)

- `ServerGameTableRenderer.kt`:
  - **Botón principal:** usa los textos y colores de la mesa común: «MATAR A X» en rojo,
    «SALVAR A X» o «SALVARME» en verde, «GUARDAR PODER» para el Oráculo, «ELEGIR BANDO»
    o «REVISAR BANDO» para el Desertor.
  - **Voto al tocar la carta,** en la votación y en el desempate, con el aviso y el
    botón verde «✓ VOTO REGISTRADO» de la mesa común. El cambio de voto también funciona.
  - **`actionConfirmed()`:** al llegar el recibo del servidor muestra la ventana privada
    o el aviso de la mesa común, sin esperar la proyección. Se cierran al cambiar de fase.
  - **Muerte nocturna final:** se muestra antes de la victoria (`resultPending`). Al
    reconectar no se repite.
  - **Compañeros traidores:** se presentan al confirmar el rol o al empezar la noche 1,
    una sola vez por partida.
  - **Aviso central** para Alcalde revelado e inactividad.
  - **Victoria** con los títulos, la frase, el resumen y el botón «VER CRÓNICA» de la mesa
    común.
  - **Registros `v3_render`, `v3_reveal`, `v3_action_confirmed` y `v3_result`,** solo en
    Debug.
- `ServerGameTablePresentation.kt`: agrega `targetActionLabel`, `confirmationFeedback`
  (reutiliza `GameplayTableUi.feedbackForResolvedAction`, `feedbackForMayorReveal` y
  `feedbackForDesertorChoice`) y `finalRevealEvents`.
- `GameplayWinnerRevealLayout.kt` (nuevo): copia del diseño de victoria de
  `GameplayMockActivity`. La mesa común no se modificó.
- `ServerGameplayActivity.kt`: avisa al renderer cuando llega el recibo.
- `ServerGameContractTest.kt`: tres pruebas nuevas (tono y texto del botón, carteles de
  confirmación, muerte final una sola vez).

## Qué se probó realmente

- **741 pruebas unitarias de Android, 0 fallos.**
- **Partidas locales** con el motor real en emuladores, en el AVD Pixel 10:
  - Asesino: presentación de compañeros, botón rojo, «ENVIANDO…», «VÍCTIMA ELEGIDA».
  - Médica: «PROTECCIÓN REGISTRADA».
  - Votación con voto al tocar la carta y cambio de voto.
  - Desempate con «SELECCIONADO ✓ / ✓ VOTO REGISTRADO».
  - Recuento, expulsión completa («SENTENCIA DEL PUEBLO», modo FULL con 7,8 s restantes).
  - Amanecer con muerte y silencio, transiciones día/noche y chat de muertos.
  - Victoria con el diseño común, revancha al lobby y reingreso a la sala tras reinstalar.
  - Partida de 8 jugadores con 4 rondas completas.
  - Reconexión: la app en segundo plano 8 s durante la noche vuelve a la fase correcta
    sin repetir animaciones.
- Evidencia: `output/v3-claude-2026-10-09/` (las capturas «antes» y «después»).

## Qué NO se probó

- **El A56 y Firebase real.** La latencia en la nube es justamente la parte del servidor.
- **En ejecución, el final por muerte nocturna con la versión corregida:** no salió por
  azar. Está cubierto por pruebas unitarias y por la lectura del flujo.
- Abandono voluntario, Bufón, Oráculo, Contrapunto y Desertor, en estas partidas
  manuales. Las pruebas dirigidas de Codex los cubren.
- Una partida con varias personas reales.

## Segunda tanda (a pedido del usuario: «todo lo máximo posible»)

**Servidor, implementado y probado; NO desplegado.**
- `functions/src/onlineGameFunctions.js`: `iniciarPartidaV3`, `accionPartidaV3` y
  `abandonarPartidaV3` publican la proyección en la misma llamada (`publishNow`). El
  publicador ya era idempotente y corría en paralelo con el trigger en vencimientos y
  recuperación. El trigger queda como respaldo durable, y un fallo de esta publicación
  solo se registra (`online_v3_inline_publish_failed`); nunca rechaza la intención ya
  aceptada. Las respuestas al cliente conservan su forma (`clientResponse`).
- Pruebas: unitarias de backend 37 + 7 + 3 + 5 + 2, todas aprobadas. Integración
  callable con emuladores 1/1. Servicio y recuperación sin Functions 39/39.
- **Medición local:** la mesa se actualiza **en el mismo milisegundo** que llega el
  recibo, en todas las acciones (rol, investigación, voto, desempate). Antes había un
  segundo salto por el trigger. Una acción sin otras simultáneas tarda entre 0,07 y
  0,37 s.

**Hallazgo nuevo: contención.** Con 8 jugadores que actúan en el mismo segundo,
75 % de las 79 acciones reintentaron la transacción (de 2 a 4 intentos). Todas
escriben el mismo documento de estado. En el emulador eso llegó a 3–5 s. **Firestore
del emulador exagera la contención**, así que hay que medirlo en Cloud: el log
`online_v3_operation` ya trae `transactionAttempts` y `durationMs`. Los bots actúan
todos en el mismo instante; personas reales se distribuyen más. Si Cloud muestra lo
mismo, la solución es estructural (intenciones en documentos por jugador) y no para
esta beta.

**Android, además:**
- `LobbyActivity` deja de renovar el permiso de anfitrión del online anterior cuando una
  sala V3 está en juego. Era el `PERMISSION_DENIED` de los registros.
- Insignias del Payador como en la mesa común: «CONTRAPUNTO», «SEÑALAR» y
  «SEÑALAR A X».
- La ventana de compañeros traidores usa el marco del mapa, igual que
  `applyTraitorRevealOverlayTheme`.
- Registros Debug `v3_action_submit` y `v3_projection_confirmed`, para medir la latencia
  en el A56.
- 741 pruebas unitarias de Android, 0 fallos.
- Una partida más de 8 jugadores jugada en automático (Comisario): 4 rondas hasta la
  victoria, con revancha.

## Tercera tanda (después de la práctica Cloud de Codex, rechazada por el usuario)

Causas encontradas en la evidencia de Codex y en el código:

- **Inicio desordenado:** el online común pasa por `AssigningRolesActivity`; V3 abría la
  mesa directamente y la rellenaba al llegar el primer snapshot.
- **Faltaba el anuncio del amanecer en Cloud:** la publicación dejaba menos de 2,5 s de
  `AMANECER` y la transición se salteaba. En el A56, la muerte se mostró 0,26 s después
  de llegar la fase, sin «AMANECER».
- **Pausas entre fases:** margen de 1,5 s en las tareas, más el backoff de la cola cuando
  una tarea llegaba temprano.
- **Las acciones se sentían lentas:** la confirmación visual esperaba la ida y vuelta
  completa, de alrededor de 1 s en Cloud.

Correcciones, pruebas y pasos de despliegue en
`docs/BRIEF_CODEX_DESPLIEGUE_2_FLUIDEZ_2026-10-09.md`.

## Decisiones pendientes para el servidor

1. **Publicar en la misma llamada: implementado (ver «Segunda tanda»).** Falta desplegar
   Functions con el gate cerrado, con el OK del usuario.
2. **Instancias siempre encendidas durante la beta,** solo en `accionPartidaV3` y
   `resolverFaseV3`. Con 256 MiB y fracción de CPU, cada instancia atiende una acción
   por vez, así que para acciones conviene `cpu: 1` con concurrencia (por ejemplo
   `concurrency: 20`) y `minInstances: 1`. Estimación aproximada: del orden de
   US$ 15 por mes las dos juntas; confirmarlo con la calculadora de Google Cloud antes
   de activarlo. Pendiente del OK del usuario. La prueba unitaria que exige
   `minInstances: 0` debe actualizarse junto con el cambio.
3. Bajar `DEADLINE_MARGIN_MS` de 1,5 s a unos 0,5 s, medido en Cloud.

**Actualización:** el usuario aprobó los tres pasos. El arreglo 2 está en el código
(`accionPartidaV3`: `cpu 1`, `concurrency 20`, `minInstances 1`; `resolverFaseV3`:
`minInstances 1`). La APK Cloud QA está compilada en
`output/traidores-v3-cloud-practica-claude.apk`. La sesión de Claude bloqueó el
despliegue a producción, así que el despliegue y la práctica en el A56 pasan a Codex:
`docs/BRIEF_CODEX_DESPLIEGUE_Y_PRUEBA_A56_2026-10-09.md`.
