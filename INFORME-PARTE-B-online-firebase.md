# INFORME — Modo online + Firebase · App Traidores (Parte B)

> Auditoría de solo lectura (jul 2026) sobre `Online*.kt`, `firestore.rules`, los caminos online de `GameplayMockActivity.kt` / `LobbyActivity.kt` / `LobbyBrowserActivity.kt` / `GameplayChatController.kt`, los tests `Online*Test.kt` y `docs/firebase-online-schema.md`. Todas las referencias son `archivo:línea` verificadas contra el working tree **posterior a la implementación de la Parte A por Codex**.
>
> **Re-verificado (jul 2026):** los cambios de la Parte A fueron solo de copy/texto (migración a tildes + `GameplayTextMarkers`) y **no alteraron ningún hecho del análisis online** — roles en claro, acciones públicas, doc de sala abierto y reglas siguen igual. Solo se corrigieron algunas líneas que se corrieron respecto de la primera versión. `Online*.kt` y `firestore.rules` no fueron tocados por Codex.

---

## B.1 — Resumen ejecutivo

- **El online está bien construido para lo que dice ser (experimental, entre amigos), pero está lejos de ser jugable con extraños de forma segura.** El modelo es 100% cliente-autoritativo sobre un Firestore con lectura pública total y escritura casi libre: cualquier persona con el `google-services.json` (que va dentro del APK) puede leer los roles de todos, escribir votos a nombre de otro o pisar el estado de la partida — sin siquiera modificar la app, desde la consola de Firebase o un script.
- **La ingeniería del cliente es sorprendentemente sólida**: gates puros y testeados (`OnlinePhaseGate`, `OnlineStartupGate`, `OnlineSyncWatchdog`, `OnlineActionResolver`, `OnlineLobbyRules`, `OnlineMatchSessionBuilder` — todos con tests JUnit), transacciones para join/inicio/handoff, idempotencia de arranque, recovery sin fallback local, y logging estructurado ejemplar (`OnlineDebugLog`). El problema no es el código Android: es que no hay nadie del otro lado verificando identidad ni ocultando información.
- **Los tres agujeros más graves, en orden**: (1) los roles de TODOS los jugadores viajan en claro en `partidaInicial`, legible por cualquiera ([LobbyActivity.kt:1381-1383](app/src/main/java/com/traidores/juego/LobbyActivity.kt:1381) + [firestore.rules:115](firestore.rules:115)); (2) las acciones nocturnas (quién mata/protege/investiga a quién) son documentos públicos en tiempo real ([GameplayMockActivity.kt:2003-2022](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2003)); (3) el documento de sala entero —incluido `estadoPartida` y `hostActivoId`— es actualizable por cualquiera que respete la forma ([firestore.rules:155](firestore.rules:155)).
- **Primeros 3 pasos recomendados**: ① Auth anónima + reglas que aten cada escritura a `request.auth.uid` (barato, ~1 día, elimina la suplantación); ② repartir el rol de cada jugador en un documento privado por jugador en vez de `partidaInicial` público (elimina el wallhack de roles); ③ App Check con Play Integrity + filtros `whereEqualTo("ronda", …)` en las lecturas de `acciones` (reduce abuso y costo). Cloud Functions recién después, si el objetivo pasa a ser "jugar con extraños".

---

## B.2 — Estado actual: arquitectura real

### Salas y lobby
- **Creación**: batch (sala + doc del host) en [OnlineRoomFirestore.kt:121-124](app/src/main/java/com/traidores/juego/OnlineRoomFirestore.kt:121). El código de 6 chars se chequea contra colisión con una query previa **no transaccional** ([OnlineModeActivity.kt:219-254](app/src/main/java/com/traidores/juego/OnlineModeActivity.kt:219)) — dos hosts simultáneos pueden crear el mismo código; mitigado en el join, que exige `singleOrNull()` sobre salas `esperando` y bloquea si hay más de una ([OnlineModeActivity.kt:636-651](app/src/main/java/com/traidores/juego/OnlineModeActivity.kt:636)).
- **Join**: transacción correcta que valida existencia, estado `esperando`, cupo, y reactiva jugadores inactivos con `FieldValue.increment` ([OnlineModeActivity.kt:715-784](app/src/main/java/com/traidores/juego/OnlineModeActivity.kt:715); análogo en [LobbyBrowserActivity.kt:182+](app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt:182)).
- **Inicio**: el host reparte roles **localmente** (`LocalGameFactory.assignRoles`) y escribe `partidaInicial` + `estadoPartida` + `estado=en_juego` en una transacción que re-valida host, cupo exacto y ready de cada jugador, con guard de idempotencia `partidaInicialCreada` ([LobbyActivity.kt:1160-1203](app/src/main/java/com/traidores/juego/LobbyActivity.kt:1160)). Preset online seguro: 1 asesino, 1 médico, 1 detective, resto aldeanos.

### Autoridad y sync de fases
- **Quién manda**: el `hostActivoId` resuelve fases; los invitados **no avanzan localmente** (`OnlinePhaseGate.canAdvanceLocally`, [OnlinePhaseGate.kt:11-13](app/src/main/java/com/traidores/juego/OnlinePhaseGate.kt:11)). Si el timer del invitado vence antes de recibir estado, queda "Sincronizando con el pueblo..." ([GameplayMockActivity.kt:4251-4259](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4251)).
- **Publicación**: el host publica `estadoPartida` completo (fase, ronda, votos, víctima, silenciado, vivos/muteados, historial público) al doc de sala, con dedupe por `stateKey` ([GameplayMockActivity.kt:1499-1581](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1499)). Al ganar alguien, marca la sala `finalizada` (:1561-1564).
- **Aplicación**: los invitados escuchan el doc de sala ([:1583-1618](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1583)) y aplican estados nuevos, ignorando viejos/duplicados por `phaseIndex`/`stateKey` (`OnlinePhaseGate.evaluateIncomingState`).
- **Resolución de noche/votos**: al vencer el timer, el host hace `get()` de la subcolección `acciones` y resuelve con `OnlineActionResolver` (última acción por `creadaEnLocal`), reutilizando el `GameEngine` con un truco de re-marcar `isHuman` por actor ([GameplayMockActivity.kt:4382-4483](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4382), [:4510-4570](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4510)). Importante: el engine **sí** valida por nombre que el actor tenga el rol (p. ej. `resolveAssassinWithRecordedVotes` filtra killers en [GameEngine.kt:108-115](app/src/main/java/com/traidores/juego/GameEngine.kt:108); los votos filtran `canVote` en [:564-577](app/src/main/java/com/traidores/juego/GameEngine.kt:564)) — lo que no puede validar es **quién escribió** ese documento.

### Presencia, startup, watchdog, recovery
- **Presencia**: pulso `conectado` cada 10s vía watchdog de 5s ([OnlineSyncWatchdog.kt:11-13](app/src/main/java/com/traidores/juego/OnlineSyncWatchdog.kt:11), [GameplayMockActivity.kt:2065-2148](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2065)).
- **Startup gate**: cada cliente publica `estadoClientes.{uid}` (fase vista, cartas vistas, rol leído); el host no inicia la primera noche hasta all-ready, con `FORZAR NOCHE` a los 30s ([OnlineStartupGate.kt:41-78](app/src/main/java/com/traidores/juego/OnlineStartupGate.kt:41), [GameplayMockActivity.kt:1642-1672](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1642)).
- **Handoff de host**: si el host activo no figura `conectado`, el primer conectado por `orden` reclama con transacción que re-valida todo e incrementa `hostVersion` ([GameplayMockActivity.kt:2180-2226](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2180), [OnlineLobbyRules.kt:20-31](app/src/main/java/com/traidores/juego/OnlineLobbyRules.kt:20)).
- **Recovery**: `roomId` en SharedPreferences ([OnlineRoomRecovery.kt](app/src/main/java/com/traidores/juego/OnlineRoomRecovery.kt)); gate por estado de sala ([OnlineRecoveryGate.kt](app/src/main/java/com/traidores/juego/OnlineRecoveryGate.kt)); reconstrucción tipificada con 6 errores claros y **sin fallback al reparto local** ([OnlineMatchSessionBuilder.kt:8-15](app/src/main/java/com/traidores/juego/OnlineMatchSessionBuilder.kt:8)). Muy bien resuelto.
- **Chat online**: escritura directa a `chat/`, listener `orderBy(creadaEnLocal).limit(40)`; los bots no reaccionan en online ([GameplayChatController.kt:1151-1195](app/src/main/java/com/traidores/juego/GameplayChatController.kt:1151)).

### Qué anda bien / qué es frágil (síntesis)

| Anda bien | Frágil |
|---|---|
| Lógica pura extraída y testeada (8 archivos `Online*Test.kt`) | Identidad = `uidTemporal` autodeclarado en SharedPreferences ([OnlineTempIdentity.kt:10-21](app/src/main/java/com/traidores/juego/OnlineTempIdentity.kt:10)) |
| Transacciones en join/inicio/handoff con re-validación | Detección de caída del host depende del campo `estado`, que nadie pone en `desconectado` si la app crashea (ver B.3.6) |
| Idempotencia (`partidaInicialCreada`, `stateKey` dedupe) | Orden de acciones por reloj del cliente (`creadaEnLocal`) |
| Recovery estricto sin reparto local de emergencia | `get()` de `acciones` sin filtro por ronda (costo creciente) |
| Logging estructurado con eventos snake_case | Todo el estado sensible en un solo doc público de sala |

---

## B.3 — Riesgos de seguridad e integridad

Contexto: no hay Auth ni App Check; `firestore.rules` valida **forma** (campos, tipos, tamaños) pero no **identidad** ni **transición de estado**. `allow read: if true` en salas, jugadores, acciones y chat ([firestore.rules:115](firestore.rules:115), [:159](firestore.rules:159), [:196](firestore.rules:196), [:232](firestore.rules:232)).

### Superficies de trampa (de mayor a menor gravedad)

1. **Wallhack total de roles.** `partidaInicial` incluye `rolKey`/`rolNombre`/`rolEquipo` de cada jugador ([LobbyActivity.kt:1372-1386](app/src/main/java/com/traidores/juego/LobbyActivity.kt:1372)) y el doc de sala es de lectura pública. Cualquier jugador (o espectador anónimo) ve quién es el asesino desde el segundo 1. Es la falla que invalida jugar con extraños; con amigos es "no mires la consola".
2. **Acciones nocturnas públicas en tiempo real.** Cada `matar/salvar/investigar/silenciar` se escribe como doc legible con `actorNombre` y `objetivoNombre` ([GameplayMockActivity.kt:2006-2022](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2006), reglas [:195-229](firestore.rules:195)). Un cliente puede mostrar en vivo a quién apunta el asesino.
3. **Suplantación de actor / vote-stuffing.** `actorId`, `actorNombre` y `actorOrden` son autodeclarados; las reglas solo exigen que sean strings ([firestore.rules:213-214](firestore.rules:213)). La resolución mapea `actorOrden → nombre` ([GameplayMockActivity.kt:4490-4495](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4490)) y en votos gana la **última** escritura por actor ([OnlineActionResolver.kt:48-55](app/src/main/java/com/traidores/juego/OnlineActionResolver.kt:48)) — puedo escribir un voto a nombre tuyo con `creadaEnLocal` futuro y pisarte el voto real. El engine valida rol/vivo por nombre, no quién firmó.
4. **Secuestro del documento de sala.** `allow update` solo valida forma ([firestore.rules:155](firestore.rules:155)): cualquiera puede pisar `estadoPartida` entero (matar jugadores, declarar ganador), cambiar `hostActivoId` a su propio uid (los clientes lo promueven a host automáticamente en [GameplayMockActivity.kt:1605-1610](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1605)), marcar la sala `finalizada`/`abandonada` (echa a todos y limpia recovery, [:1598-1604](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1598)), o editar `estadoClientes.{uid}` ajeno para forzar/impedir el arranque.
5. **Cronista falso por chat.** Las reglas aceptan `isGod: true` de cualquiera ([firestore.rules:251](firestore.rules:251)) y el cliente renderiza el flag tal cual llega ([GameplayChatController.kt:1166-1186](app/src/main/java/com/traidores/juego/GameplayChatController.kt:1166)): se pueden inyectar anuncios "de Dios" falsos ("Amanecer: murio X…"). Peor aún: como los bots y parsers de la Parte A reaccionan al texto del cronista, un anuncio forjado puede alterar la percepción del estado.
6. **Host muerto = partida colgada.** El handoff exige que el host figure NO `conectado` ([GameplayMockActivity.kt:2183-2187](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2183)), pero la presencia solo escribe `conectado` ([:2142](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2142)); Firestore no tiene `onDisconnect`. Si el host pierde red o la app muere, su doc queda `conectado` para siempre, nadie reclama el host y los invitados quedan en "Sincronizando" indefinidamente. No se usa la antigüedad de `ultimaConexion`/`ultimaConexionLocal` (los timestamps ya se escriben, solo falta usarlos).
7. **Contador global `meta/public_ids` reseteable.** Las reglas permiten a cualquiera escribir `nextId` con cualquier valor ≥1 ([firestore.rules:258-268](firestore.rules:258)) → IDs públicos duplicados para todos los jugadores futuros. También `perfiles_publicos/{uid}` es escribible declarando el uid ([:270-282](firestore.rules:270)).
8. **Relojes de cliente como fuente de verdad.** `creadaEnLocal` ordena acciones, votos y chat ([OnlineActionResolver.kt:30](app/src/main/java/com/traidores/juego/OnlineActionResolver.kt:30), [:53](app/src/main/java/com/traidores/juego/OnlineActionResolver.kt:53); [GameplayChatController.kt:1158](app/src/main/java/com/traidores/juego/GameplayChatController.kt:1158)). Un reloj adelantado (o malicioso) gana todos los last-write-wins. `creadaEn` (serverTimestamp) ya existe en todos los docs pero no se usa para ordenar.

### Condiciones de carrera (más allá de lo malicioso)

- **Doble host transitorio**: entre que el host viejo revive y el nuevo reclamó, ambos publican `estadoPartida`; el `hostVersion` se incrementa pero **no se incluye en `estadoPartida` ni se valida al aplicar** — el gate solo compara `phaseIndex` ([OnlinePhaseGate.kt:23-36](app/src/main/java/com/traidores/juego/OnlinePhaseGate.kt:23)), así que dos hosts en la misma fase pueden pisarse alternadamente sin que los invitados lo detecten.
- **Colisión de código de sala** en creación concurrente (ver B.2); hoy degrada a "creen una sala nueva", aceptable.
- **`publishAuthoritativeOnlineState` no es transaccional**: usa `update()` plano; si dos escrituras del mismo host se reordenan por red, el dedupe local (`stateKey`) no protege contra el reordenamiento en el servidor. Riesgo bajo con un solo host sano.

### Costos y límites de Firestore

- **Amplificación de lecturas por el doc de sala**: todo vive en un solo documento (`partidaInicial` + `estadoPartida` + `estadoClientes` de N jugadores) escuchado por N clientes. Cada pulso de `estadoClientes` (cada ~10s por jugador, [GameplayMockActivity.kt:1463-1490](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:1463)) re-entrega el doc a los N listeners → con 15 jugadores, ~1.5 escrituras/s y ~22 lecturas/s solo de presencia de gameplay: ≈ 80.000 lecturas/hora por sala llena, más el ancho de banda del doc completo (con 15 jugadores el doc ronda decenas de KB; lejos del límite de 1 MiB, pero se baja entero en cada snapshot).
- **`get()` de `acciones` sin filtro**: cada resolución de noche/votación/alcalde lee **toda** la subcolección histórica ([GameplayMockActivity.kt:4389](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4389), [:4519](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4519), [:4579](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:4579)). En ronda 20, eso son cientos de docs releídos varias veces por ronda. Falta `whereEqualTo("ronda", session.round)` (+ `tipo`), que las reglas ya soportan sin índice compuesto (un solo campo de igualdad; con dos, Firestore pedirá un índice compuesto — trivial de crear).
- **Sin limpieza**: salas `abandonada`/`finalizada` se acumulan (documentado en [docs/firebase-online-schema.md:221-238](docs/firebase-online-schema.md)); el browser escucha **todas** las salas `esperando` sin `limit()` ([LobbyBrowserActivity.kt:52-54](app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt:52)) — con basura acumulada, cada apertura del browser lee todo.
- **Spam sin costo para el atacante**: las reglas no pueden limitar frecuencia; todas las escrituras (chat de 140 chars incluido) se facturan al proyecto. El cooldown es solo local ([GameplayChatController.kt:1102-1106](app/src/main/java/com/traidores/juego/GameplayChatController.kt:1102)).

---

## B.4 — Plan de mejora priorizado con Firebase

### Barato / ahora

**(b) Auth anónima — el paso 1 real (antes que endurecer reglas).** Sin `request.auth`, las reglas *no pueden* atar nada a nadie; por eso va primero. `FirebaseAuth.signInAnonymously()` al entrar al modo online y usar `auth.uid` como `uidTemporal` (cambio contenido: [OnlineTempIdentity.kt](app/src/main/java/com/traidores/juego/OnlineTempIdentity.kt) pasa a devolver el uid de Auth, manteniendo el prefijo/migración para salas viejas). Costo: gratis, sin UI de login, ~1 día. Habilita todo lo demás.

**(a) Reglas de seguridad — inmediatamente después de (b).** Con `request.auth.uid` disponible:
- `jugadores/{uid}`: `allow write: if request.auth.uid == uid` (hoy cualquiera escribe el doc de otro).
- `acciones`: `allow create: if request.resource.data.actorId == request.auth.uid` → mata la suplantación de votos/acciones (riesgo B.3.3).
- `chat`: ídem `actorId`, y `isGod == false` para escrituras de clientes → mata el cronista falso (B.3.5).
- Doc de sala: separar campos. Lo más simple sin Functions: `allow update: if request.auth.uid == resource.data.hostActivoId` para tocar `estadoPartida`/`estado`/`hostActivoId` (con excepción transaccional para el handoff: permitir cambiar `hostActivoId` si el nuevo valor es `request.auth.uid` y `hostVersion` se incrementa). `estadoClientes` conviene moverlo a subcolección `estadoClientes/{uid}` para poder reglarlo por dueño — las reglas de field-path sobre mapas son frágiles; este movimiento además arregla el costo (ver (e)).
- `meta/public_ids`: `allow update: if request.resource.data.nextId > resource.data.nextId` (solo crece) — tapa B.3.7 hasta moverlo a una Function.
- Mientras tanto (hoy mismo, sin Auth): al menos `allow update` de sala que prohíba cambiar `codigoSala`, `creadaEn`, `hostId` y que `estado` solo transicione hacia adelante (`esperando→en_juego→finalizada/abandonada`). Es forma, no identidad, pero corta los griefs más baratos.

**(e-1) Robustez barata de sync (independiente de Auth, se puede hacer ya):**
- **Handoff por staleness**: en `handleOnlineHostHandoff` ([GameplayMockActivity.kt:2180-2189](app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2180)) tratar como caído a un host cuyo `ultimaConexionLocal` tenga más de ~30s (el pulso es cada 10s), en vez de depender del campo `estado`. Arregla la partida colgada (B.3.6), que es el bug de robustez más visible en pruebas reales.
- **Filtrar `acciones` por ronda** en las tres resoluciones (`whereEqualTo("ronda", session.round)`), y ordenar por `creadaEn` (server) con fallback a `creadaEnLocal`. Corta el costo creciente y el abuso de reloj (B.3.8).
- **`limit(30)` + orden por `actualizadaEn`** en el browser de salas ([LobbyBrowserActivity.kt:52](app/src/main/java/com/traidores/juego/LobbyBrowserActivity.kt:52)).
- **TTL**: activar política TTL de Firestore sobre `ultimaActividadOnline` (config de consola, cero código) para que las salas viejas se borren solas.

**(c) App Check (Play Integrity).** Barato (config + una dependencia), pero **después** de Auth: sin Auth, App Check solo garantiza "es la app oficial", y la app oficial igual puede leer los roles. Con Auth+reglas, App Check corta los scripts/consola anónimos, que es donde vive el 90% del abuso descrito. Nota: exige builds firmados registrados; en debug requiere debug provider.

### Grande / después

**(d) ¿Cuándo Cloud Functions?** Cuando el objetivo pase de "probar con conocidos" a "jugar con extraños". El motivo de fondo: **aunque hagas todo lo anterior, el host sigue siendo un jugador** — ve `partidaInicial` completa (necesita los roles para resolver la noche) y publica el resultado. Con amigos alcanza; con extraños el host-tramposo es inaceptable. El orden razonable:
1. **Roles privados** (se puede hacer antes de Functions, con Auth + reglas): al iniciar, escribir el rol de cada jugador en `partidas/{id}/roles/{uid}` legible solo por su dueño, y dejar en `partidaInicial` solo nombres/orden. El host todavía necesita los roles para resolver → este paso solo rinde completo junto con el 2.
2. **Resolución server-side**: una Function (callable o trigger sobre `acciones`) que resuelva noche/votación y publique `estadoPartida`. El `GameEngine` es un object Kotlin puro; portarlo a una Function (Kotlin/JS transpilado o reescritura TS de las ~10 funciones de resolución) es el costo real. Con esto, `hostActivoId` deja de existir como autoridad y desaparecen: wallhack del host, handoff, doble host, y la mitad del watchdog.
3. **Housekeeping en Functions**: reserva de `publicId`, limpieza programada de salas, y rate-limiting real (contadores por uid).
   Requiere plan Blaze (con free tier generoso); para el volumen de este proyecto el costo es ~$0.

**(e-2) Rediseño de listeners (junto con el paso a subcolecciones):** mover `estadoPartida` y `estadoClientes/{uid}` fuera del doc raíz. Cada cliente escucha: el doc raíz (metadata chica, casi estática), `estadoPartida` (un doc chico que cambia por fase, no por pulso) y — solo el host — `estadoClientes`. Con 15 jugadores esto baja las lecturas de presencia de ~N² a ~N por pulso y reduce el ancho de banda por snapshot en un orden de magnitud.

### Qué NO hacer todavía
- No invertir en anti-cheat del lado del cliente (ofuscación, checks locales): con el modelo actual es maquillaje.
- No abrir matchmaking público ("partida rápida" real) antes de (b)+(a)+roles privados: hoy `btnQuick` va a un lobby simulado ([OnlineModeActivity.kt:49-55](app/src/main/java/com/traidores/juego/OnlineModeActivity.kt:49)) — mantenerlo así.
- No borrar `pruebas/{docId}` de las reglas sin borrar el botón de prueba que la usa (las reglas actuales la dejan `read, write: if true` — [firestore.rules:110-112](firestore.rules:110) — recordar cerrarla cuando se limpie).

---

## Apéndice — Cobertura de tests online

Existe test unitario para toda la lógica pura: `OnlineActionResolverTest` (118 líneas), `OnlineMatchSessionBuilderTest` (208), `OnlineLobbyRulesTest` (106), `OnlineStartupGateTest` (98), `OnlinePhaseGateTest` (87), `OnlineSyncWatchdogTest` (68), `OnlineRecoveryGateTest` (30), `OnlineAuthoritativeStateMapperTest` (26). `OnlineRoomFirestoreTest` (12 líneas) solo cubre helpers. **No hay tests de integración contra el emulador de Firestore ni de las reglas** (`firebase emulators` + `@firebase/rules-unit-testing`) — sería el complemento natural cuando se endurezcan las reglas, para no romper el cliente sin darse cuenta.
