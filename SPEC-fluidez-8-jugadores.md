# SPEC — Fluidez con 8+ jugadores: fin de los congelamientos (local) y arranque estable (online)

> Para Codex. Español, referencias `archivo:línea`. No compilar (el usuario valida en Android Studio). Prioridad de este ciclo: **que el juego no se trabe, no se congele y no se corte**, ni en local ni en online.
>
> **Síntomas reportados (16 jul 2026, teléfono real):**
> 1. **Local**: con 5-7 jugadores anda bien; con 8+ se congela en la votación/expulsión. Antes eran 1-2 segundos de freeze y volvía; ahora la app deja de responder (ANR), Android la mata y al reabrir cae en el lobby. Se repite siempre.
> 2. **Online**: una sala de 8 no arrancaba y echaba a los jugadores ("La sala perdió datos de partida"); con 5-6 anduvo bien.
>
> **Las causas están diagnosticadas y verificadas en el código** (secciones abajo). No es un bug único: es un costo de IA que crece ~cuadrático con la cantidad de jugadores multiplicado por regex recompiladas miles de veces, ejecutado en el main thread; y en online, un payload de arranque no determinista que se corrompe si la presencia parpadea durante la transacción.
>
> **Restricción clave**: las Partes 1 y 2 deben ser **neutrales en comportamiento** — la IA tiene que decidir exactamente lo mismo que hoy, solo más barato. Nada de tocar balance, pesos ni texto de los bots.

## Diagnóstico resumido (leer antes de tocar)

### Local — por qué se congela la votación con 8+

La resolución de una votación local llama `LocalBotAi.chooseVoteTarget` por cada bot (`GameEngine.resolveVotingInternal`, `GameEngine.kt:689-698`; ídem desempate `:753-774` y timeout AFK `GameEngine.kt:1428-1478`). Cada llamada:

1. Computa `rankedPublicSuspects` **dos veces** (directo en `LocalBotAi.kt:434` y de nuevo dentro de `conversationVotePlan`, `LocalBotAi.kt:1013`).
2. `scoreCandidate` (`LocalBotAi.kt:1618`) re-parsea los últimos 16 mensajes públicos **por cada candidato** — `publicStatementFrom` sobre cada mensaje en `LocalBotAi.kt:1657-1659`, más `mentionsName` por mensaje en `:1641`.
3. El filtro de opciones hace `hasUsefulPublicRead` dentro de un `any` anidado — O(n²) llamadas por bot (`LocalBotAi.kt:456-459`).

Y el costo unitario del parseo es enorme porque **nada está precompilado ni cacheado**:

- `normalizedForParsing` crea **2 `Regex` nuevas en cada llamada** (`BotConversationMemory.kt:614-619`).
- `mentionsName` crea 1 `Regex` nueva por llamada (`BotConversationMemory.kt:591-597`).
- `mentionedPlayerNames` re-normaliza el mensaje y **todos los nombres de jugadores** por llamada, con un conteo de prefijos O(vivos²) (`BotConversationMemory.kt:526-538`).
- `BotPerception.roleClaimFrom` compila **2 regex por alias por rol** en cada llamada (~11 roles × sus alias) (`BotPerception.kt:19-31`).
- `BotPerception.publicStatementFrom` compila ~4 regex por llamada (`BotPerception.kt:33-95`).

Cuenta rápida con 10 jugadores: ~9 bots × ~9 candidatos × 16 mensajes × (`publicStatementFrom` + `mentionsName` + normalizaciones de 10 nombres) ≈ **decenas de miles de `Pattern.compile` + `Normalizer` por UNA votación**. Con 6 jugadores es ~5-8 veces menos — por eso 5-7 "solo" trababa 1-2 segundos y 8+ pasa el umbral de ANR (5 s).

**Dónde corre ese costo**: el único camino off-main-thread es el tap del humano (`shouldResolveLocalVoteOffMainThread` + `resolveLocalVoteOffMainThread`, `GameplayMockActivity.kt:1528-1610`, agregado en el commit "Optimiza audio y votaciones"). Pero:

- El **vencimiento del timer** resuelve en el main thread (`onCountdownExpired`, `GameplayMockActivity.kt:5344-5440`: VOTACION en `:5415-5421`, DESEMPATE en `:5423-5429`, CONTRAPUNTO `:5408-5414`, noche `:5393-5406`) y **no chequea `localVoteResolutionInProgress`** → si el timer vence mientras el executor cuenta, se resuelve una segunda vez, en main.
- El **auto-avance** (humano muerto/espectador, o fases sin input) va por `advanceCurrentPhase` (`GameplayMockActivity.kt:1476-1499`), también en main. Con 8+ jugadores el humano muere más seguido → todas las votaciones de esas rondas congelan.
- El chat de bots corre entero en el main handler (`GameplayChatController.kt:66`): cada beat que invalida el batch (`cachedConversationBatch`, `LocalBotAi.kt:739-762` — la key incluye el último mensaje, `:770-774`) regenera líneas para **todos** los bots (`:752-755` con `limit =` todos) con el mismo scoring caro → los micro-freezes durante el debate.

Nota: el crash del GridLayout de desempate con 5+ empatados **ya fue arreglado** (`tieVoteGridMetrics`, `GameplayMockActivity.kt:6699-6717`); queda solo en verificación.

### Online — por qué una sala de 8 no arranca y echa a todos

`startOnlineRoomForEveryone` (`LobbyActivity.kt:2075-2215`) captura `activePlayersAtStart` en `:2096` y valida el conteo dentro de la transacción (`:2122`), **pero el payload no usa esa lista**:

- `buildOnlineBaseSession` vuelve a llamar `activeOnlinePlayers()` (`LobbyActivity.kt:2474`), y
- `initialMatchPayload` la vuelve a llamar otra vez, apareando por índice: `activeOnlinePlayers().getOrNull(index)` (`LobbyActivity.kt:2566-2567`).

`activeOnlinePlayers()` lee el estado del listener de Firestore, que se muta en el main thread **mientras la transacción corre en un thread de background** (y la transacción reintenta hasta 5 veces). Si la presencia de un jugador parpadea en ese momento — mucho más probable con 8 en el wifi del instituto — el `partidaInicial` sale con menos jugadores, o con `uidTemporal`/`publicId` vacíos o corridos de índice. Consecuencia: **todos** los clientes fallan al reconstruir la sesión (`OnlineMatchSessionBuilder` devuelve `INCOMPLETE_PLAYERS`/`MISSING_HUMAN_PLAYER`, `OnlineMatchSessionBuilder.kt:69-75`, o `onlineMatchEntryProblem` "La sala no coincide…", `LobbyActivity.kt:2536-2547`) → `coordinateOnlineMatchEntry` cae en `startOnlineMatch` (`:2226-2229`) → "La sala perdió datos de partida. Creen una sala nueva." + `finish()` (`:2409-2423`) = **echa a todos, siempre igual**. Exactamente el síntoma.

Agravantes con 8+:

- La transacción hace `transaction.get()` **secuencial por cada doc de jugador** (`LobbyActivity.kt:2126-2143`): 9 round-trips por intento, y cualquier toggle de listo / voto de mapa concurrente invalida el intento (default 5 reintentos) → "No se pudo iniciar online" intermitente que no aparece con 5-6.
- `acknowledgeOnlineMatchEntry` exige ver **exactamente** `jugadoresEsperados` activos para mandar el ack (`LobbyActivity.kt:2264`): un parpadeo de presencia y ese cliente no ackea → la release espera el timeout de 10 s (`:4086`) y encima los 8 acks pegan al mismo doc de sala casi simultáneos.
- En partida, cada cliente escribe `estadoClientes` al doc de sala con pulso de 10 s (`OnlineSyncWatchdog.kt:11-13`, publicador en `GameplayMockActivity.kt:1849-1882`) más ráfagas por cambio de fase: con 8-10 clientes se roza el límite blando de Firestore de ~1 escritura/s sostenida por documento.

---

## Parte 1 — Parseo de la IA: compilar una vez, parsear cada mensaje una sola vez

Es la causa raíz del costo. Todo en esta parte es **neutral en comportamiento** (mismo resultado, menos trabajo).

1. **Precompilar toda regex que hoy se crea por llamada:**
   - `normalizedForParsing` (`BotConversationMemory.kt:614-619`): las 2 regex a `private val` top-level precompiladas. Además, memoizar el resultado con un LRU chico (p. ej. `object` con `LinkedHashMap` de 256 entradas, `removeEldestEntry`) — los mismos 16 mensajes y ~10 nombres se normalizan miles de veces por resolución.
   - `mentionsName` (`BotConversationMemory.kt:591-597`): cachear la `Regex` por nombre normalizado (mapa `nombre → Regex` en un objeto; los nombres no cambian durante la partida). Alternativa sin regex: buscar el nombre normalizado con chequeo manual de límites de palabra sobre el string normalizado (más rápido todavía); si se toma esta vía, cubrir con tests los casos con acentos/`#`/mayúsculas.
   - `BotPerception.roleClaimFrom` (`BotPerception.kt:19-31`): construir **una vez** (init del objeto) la lista `(roleKey, alias, Regex, Regex)` y en cada llamada solo iterar y matchear.
   - `BotPerception.publicStatementFrom` (`BotPerception.kt:33-95`): las ~4 regex fijas a `private val`. Ojo: las ramas con `"$targetText ..."` interpolado son `contains` (no regex), esas quedan igual.
   - `mentionedPlayerNames` (`BotConversationMemory.kt:526-538`): precomputar los nombres normalizados de los vivos una vez por llamada (hoy re-normaliza dentro de dos lambdas anidadas, O(vivos²) normalizaciones); con el memo de `normalizedForParsing` esto ya mejora solo, pero conviene igualmente sacar `normalizedForParsing(player.name)` del lambda interno.

2. **Parsear cada mensaje una sola vez por sesión** — cachear el resultado de `publicStatementFrom` y `roleClaimFrom` por mensaje:
   - Cache LRU (~256 entradas) keyed por el string del mensaje. Cuidado con `publicStatementFrom`: su `target` depende de los vivos (via `mentionedPlayerNames`), así que la key debe incluir un hash de la lista de vivos (p. ej. `aliveNames.hashCode()`), o invalidar el cache cuando cambia. Con eso el resultado es idéntico al actual.
   - Dónde pega: `scoreCandidate` (`LocalBotAi.kt:1657-1659`), `latestStatementBySpeaker`/`publicClaimants`/`publicContradiction`/`hasUsefulPublicRead` (`BotConversationMemory.kt`), `BotTableMemory.kt:10-11`, `GameEngine.withRecordedPublicMemory` (`GameEngine.kt:2172-2198`).
   - Nota: `session.tableMemory` ya persiste claims/statements parseados al ingresar cada mensaje — si resulta más simple, `scoreCandidate` puede leer de ahí en vez de re-parsear `recent`, pero **solo si** se demuestra con tests que el resultado es idéntico (el ledger guarda round/phase, no la lista exacta de los últimos 16). Si hay dudas, quedarse con el cache por mensaje, que es 100 % equivalente.

3. **Tests JVM** en `app/src/test/java/com/traidores/juego/` (estilo de los existentes):
   - `BotPerceptionParseCacheTest` (o similar): para un set de mensajes representativos (claims con `soy/me tocó`, acusaciones, defensas, menciones con acentos y nombres con `#`), el resultado con cache/precompilado == el resultado de la lógica vieja (se puede duplicar la lógica vieja en el test como oráculo, o fijar los resultados esperados a mano).
   - Un test de que `chooseVoteTarget` devuelve lo mismo antes/después en 2-3 sesiones sintéticas de 10 jugadores con historial armado (fijar seed por `session.code`).

## Parte 2 — `chooseVoteTarget`: una sola pasada de scoring

- `LocalBotAi.kt:428-468`: computar `rankedPublicSuspects` **una vez** y pasarla a `conversationVotePlan` como parámetro (default `null` → la computa, para no romper otros call sites), eliminando la doble pasada de `:434` + `:1013`.
- Filtro `LocalBotAi.kt:456-459`: precomputar `hasUsefulPublicRead(session, name)` en un `Map<String, Boolean>` una vez por llamada antes del `filterNot` (hoy se evalúa dentro de un `any` anidado → O(n²) escaneos de historial).
- Revisar que `votingIntentMessages`/`openingDebateMessages` (`LocalBotAi.kt:537,625`) no dupliquen el mismo ranking por bot dentro de una misma generación de batch; si lo hacen, mismo patrón: computar por bot una vez y reusar dentro de la pasada.
- Con Parte 1 + 2, la resolución de votación con 10 jugadores tiene que bajar de segundos a decenas de milisegundos. El log ya existente `local_vote_resolution_complete ... durationMs=` (`GameplayMockActivity.kt:1604-1606`) sirve de medición antes/después — anotar valores en la entrega.

## Parte 3 — Ninguna resolución local en el main thread + guardas de carrera

Generalizar el patrón que ya existe para el tap (`resolveLocalVoteOffMainThread`, `GameplayMockActivity.kt:1567-1610`) a **todos** los avances de fase locales pesados:

1. Extraer un helper genérico `resolveLocalPhaseOffMainThread(before: GameSession, label: String, resolver: (GameSession) -> GameSession)` con el mismo token (`localVoteResolutionToken`), el flag `localVoteResolutionInProgress`, el guard de fase (`:1597-1603`), el log de duración y el hint "Contando los votos del pueblo..." (generalizar el texto por fase: "Resolviendo la jornada...", etc.).
2. Usarlo en:
   - `onCountdownExpired` (`GameplayMockActivity.kt:5344-5440`) para VOTACION, DESEMPATE_VOTACION, CONTRAPUNTO y las fases de noche locales (`resolveLocalNightWithoutHumanInput` incluido, `:5455-5470`). **Además**, `onCountdownExpired` debe salir temprano si `localVoteResolutionInProgress` (hoy no lo chequea → doble resolución cuando el timer vence durante el conteo; la segunda corre en main y es la que congela).
   - `advanceCurrentPhase` (`GameplayMockActivity.kt:1476-1499`) para las mismas fases en modo local (incluye RESULTADO → `resolveResult` → arranque de noche siguiente con la preparación del plan traidor, que también hace scoring).
   - `handleCurrentPhase`/auto-advance ya chequean el flag (`:1173`) — verificar que `autoAdvanceRunnable` y `scheduleReadyVoteBotCascade` (`:4168`) también lo respeten antes de disparar un avance.
3. Reglas del helper: en online no aplica (los caminos online ya difieren, `:5366-5392`); mientras corre, countdown pausado y botón deshabilitado (ya pasa: `:4002,4028`); al completar, mismo flujo actual (`completeResolvedTargetAction` o `renderGame` según el call site).
4. El chat de bots puede quedarse en el main handler: con Partes 1-2 cada beat baja a <10 ms. Solo si al medir con 10 bots un beat sigue >30 ms, mover la generación del batch (`cachedConversationBatch`, `LocalBotAi.kt:739-762`) al mismo executor y postear el resultado al handler — cuidando que el batch se aplique solo si la key sigue vigente.

## Parte 4 — Online: arranque determinista con 8+

1. **Payload determinista (el fix del "los echaba"):**
   - Pasar `activePlayersAtStart` como parámetro a `buildOnlineBaseSession(...)` (`LobbyActivity.kt:2470-2493`) y a `initialMatchPayload(...)` (`:2549-2582`). Ninguna de las dos debe volver a llamar `activeOnlinePlayers()` — la lista se captura **una vez** en `:2096` y es la única fuente durante toda la transacción.
   - El apareo por índice `jugadores[i] ↔ activePlayers[i]` (`:2566-2567`) solo es válido si `assignRoles` no reordena. Verificarlo con un test JVM (`LocalGameFactory.assignRoles` preserva el orden de entrada); si reordenara, aparear por nombre.
   - Con esto, el reparto sale siempre con N jugadores y uids consistentes, parpadee o no la presencia.
2. **Menos contención en la transacción de inicio** (`LobbyActivity.kt:2102-2186`): sacar los `transaction.get()` por jugador (`:2126-2143`) — validar listo/conectado/voto de mapa desde el estado del listener **antes** de la transacción (ya se hace en el preflight `:2080`), y dentro de la transacción re-leer solo el doc de sala (estado WAITING, `partidaInicialCreada`, host, `jugadoresEsperados`). El caso raro "alguien se desconectó justo" ya lo cubren la barrera de entrada del lobby y el gate de startup con FORZAR a los 30 s (`OnlineStartupGate.kt:35`). Esto reduce el read-set de 9 docs a 1 y elimina los reintentos agotados con 8+.
3. **Ack de entrada más tolerante** (`acknowledgeOnlineMatchEntry`, `LobbyActivity.kt:2263-2316`): reemplazar el guard `activeOnlinePlayers().size != onlineExpectedPlayers` (`:2264`) por `>= 1` jugador activo propio (el `matchId` ya ancla el ack a la partida correcta) — hoy un parpadeo de presencia bloquea el ack y fuerza el timeout de 10 s. Agregar un jitter aleatorio de 0-1500 ms antes de escribir el ack para que 8 clientes no peguen al mismo doc en el mismo instante.
4. **Endurecer el camino del kick**: en `coordinateOnlineMatchEntry`/`startOnlineMatch` (`LobbyActivity.kt:2217-2261, 2409-2432`), ante `sessionFromInitialMatch == null` o `onlineMatchEntryProblem != null`, **reintentar 2-3 veces con 1500 ms** (el snapshot puede estar a mitad de actualización / con `pendingWrites`) antes del toast + `finish()`; loguear la `reason` concreta (ya existe `online_match_rebuild_failure`). El `finish()` inmediato solo si el fallo persiste con snapshot confirmado del servidor.
5. **Checklist de despliegue (no es código, va en la entrega):** confirmar que las reglas publicadas en la consola == `firestore.rules` del repo (permiten `jugadoresEsperados <= 15`; ya hubo un incidente de reglas viejas) → `firebase deploy --only firestore:rules` y `firebase deploy --only database` antes de la próxima prueba con 8.

## Parte 5 — Presión de escrituras al doc de sala en partida (8-10 clientes)

- Pulso de `estadoClientes` (`OnlineSyncWatchdog.kt:11-13` + publicador `GameplayMockActivity.kt:1849-1882`): agregar jitter (±3 s sobre los 10 s) y **no escribir si el payload no cambió** desde la última publicación (comparar la key ya usada por `lastPublishedOnlineStateKey`; hoy el dedupe existe para avances de fase pero verificar que cubra el pulso periódico).
- Objetivo: con 10 clientes, el doc de sala debe recibir en régimen < 1 escritura/s sostenida fuera de los cambios de fase.
- Nada más en esta parte — no tocar el modelo de sincronización.

## Parte 6 — Fuera de alcance (explícito)

- No cambiar decisiones, pesos ni textos de la IA (solo costo).
- No migrar la sincronización online a otro esquema ni agregar Cloud Functions.
- No tocar el preset de roles online (`onlineSafeRoleComposition`, `GameModels.kt:691-702` — con 8 es la misma estructura que con 7; no es la causa de nada de esto).
- No rediseñar pantallas; el grid de desempate ya está corregido.

## Verificación

**Local (en un teléfono, no solo emulador):**
1. Partida local con **10 bots**: jugar 3+ rondas completas. La votación, el desempate y la expulsión no deben congelar la UI de forma perceptible; `local_vote_resolution_complete ... durationMs` < 500 ms en gama media (anotar el valor real antes/después).
2. Partida local con **15 bots**: una ronda completa sin ANR (peor caso).
3. Dejar vencer el timer de votación sin votar (y también con el humano muerto, espectando): la resolución no debe trabar el main thread ni resolverse dos veces (revisar que no aparezca `local_vote_resolution_discarded` en cadena).
4. Tap de voto en el último segundo del timer: una sola resolución (la guarda nueva de `onCountdownExpired`).
5. Desempate forzado con 5 empatados (comandos debug): la ventana muestra filas/scroll correctos (regresión del fix ya hecho).
6. Tests JVM nuevos en verde + suite existente en verde.

**Online:**
7. `modoPrueba` con 3 emuladores: crear, arrancar, jugar una ronda — sin regresiones del flujo de entrada.
8. Simular parpadeo de presencia durante INICIAR (apagar/prender wifi de un cliente justo al tocar el botón): la partida arranca igual con el reparto completo, o falla con reintento visible — **nunca** "La sala perdió datos" + expulsión masiva.
9. Prueba real con 8: el arranque entra en <15 s desde el último LISTO; anotar cuántos intentos de transacción hubo (logs `online_start_*`).
10. Confirmar reglas de consola actualizadas antes de la prueba real (Parte 4.5).
