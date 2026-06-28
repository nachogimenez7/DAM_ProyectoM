# ESTADO ACTUAL - Traidores

Auditoria tecnica de solo lectura basada en el codigo actual del repositorio. No se usaron docs previas como fuente de verdad salvo cuando la seccion pide detectar discrepancias contra strings visibles o comentarios del propio codigo.

## Tabla resumen

| Subsistema | Estado |
|---|---|
| 1. Arquitectura general | PARCIAL |
| 2. Navegacion completa | PARCIAL |
| 3. Modelo de datos y reglas | PARCIAL |
| 4. Motor de juego | PARCIAL |
| 5. IA de bots | PARCIAL |
| 6. UI de Gameplay | PARCIAL |
| 7. Modo Online (Firebase/Firestore) | PARCIAL |
| 8. Configuracion / Opciones | PARCIAL |
| 9. Persistencia y serializacion | PARCIAL |
| 10. Deuda tecnica visible | PARCIAL |
| 11. Testing | PARCIAL |
| 12. Recursos | PARCIAL |

## 1. Arquitectura general

Estado: PARCIAL

Evidencia:
- `app/src/main/AndroidManifest.xml:15-39`: Activities declaradas: `MainActivity`, `JugarActivity`, `LocalModeActivity`, `OnlineModeActivity`, `LobbyBrowserActivity`, `LobbyActivity`, `AssigningRolesActivity`, `GameplayMockActivity`, `RolesActivity`, `AyudaActivity`, `OpcionesActivity`, `ProfileActivity`, `ProfileSelectionActivity`.
- `app/src/main/java/com/traidores/juego/BaseActivity.kt:7-21`: `BaseActivity` aplica `MusicManager.onActivityStarted/onActivityStopped` en lifecycle.
- `app/src/main/java/com/traidores/juego/BaseActivity.kt:23-39`: `BaseActivity` aplica preferencia de orientacion solo a `LobbyBrowserActivity`, `LobbyActivity`, `AssigningRolesActivity` y `GameplayMockActivity`.
- `app/src/main/java/com/traidores/juego/GameModels.kt:5-55`: `GameSession` concentra estado de partida y es `Serializable`.
- `app/src/main/java/com/traidores/juego/LobbyActivity.kt:1994-1996`, `AssigningRolesActivity.kt:444-446`, `GameplayMockActivity.kt:5866-5869`: el estado viaja por `Intent` usando `getSerializableExtra`.
- `app/src/main/java/com/traidores/juego/LobbyActivity.kt:620-633`, `OnlineMatchSessionBuilder.kt:31-120`: en online tambien se reconstruye desde Firestore (`partidaInicial`, `estadoPartida`).

Descripcion real:
- `MainActivity`: menu principal; lanza jugar, roles, ayuda, opciones y perfil (`MainActivity.kt:33-49`).
- `JugarActivity`: selector local/online (`JugarActivity.kt:25-29`).
- `LocalModeActivity`: crea una sesion local y la manda a lobby (`LocalModeActivity.kt:37-40`).
- `OnlineModeActivity`: crea/busca salas Firestore, puede entrar a lobby o gameplay recuperado (`OnlineModeActivity.kt:307-314`, `433-443`, `492-499`, `803-810`).
- `LobbyBrowserActivity`: lista salas online y entra al lobby (`LobbyBrowserActivity.kt:265-272`).
- `LobbyActivity`: lobby local/online, asigna roles y arranca asignacion/gameplay (`LobbyActivity.kt:213-222`, `994-1001`).
- `AssigningRolesActivity`: pantalla intermedia de reparto, luego abre gameplay (`AssigningRolesActivity.kt:400-418`).
- `GameplayMockActivity`: pantalla principal de partida local/online (`GameplayMockActivity.kt:57`, `359-388`).
- `RolesActivity`: guia/listado de roles (`RolesActivity.kt:10-20`).
- `AyudaActivity`: ayuda (`AyudaActivity.kt:10-16`).
- `OpcionesActivity`: preferencias, idioma, audio, orientacion gameplay y smoke test Firebase (`OpcionesActivity.kt:61-104`).
- `ProfileActivity` y `ProfileSelectionActivity`: perfil local y seleccion de avatar/banner/rol (`ProfileActivity.kt:65-81`, `ProfileSelectionActivity.kt:17-63`).

Discrepancias:
- El nombre `GameplayMockActivity` sugiere mock, pero hoy contiene la pantalla real de gameplay, incluyendo flujo online, acciones, chat y resolucion (`GameplayMockActivity.kt:57`, `1097-1152`, `1888-1916`).
- Coexisten dos mecanismos de estado: `Serializable` por `Intent` para flujo local y parte del online, y Firestore para reconstruccion/sincronizacion online (`GameModels.kt:55`, `OnlineMatchSessionBuilder.kt:31-120`).

## 2. Navegacion completa

Estado: PARCIAL

Evidencia:
- `MainActivity.kt:33-49`: menu principal abre jugar, roles, ayuda, opciones y perfil sin llamar `finish()`.
- `JugarActivity.kt:16`, `25-29`: boton back hace `finish()`; local/online se abren sin cerrar `JugarActivity`.
- `LocalModeActivity.kt:19`, `37-40`: back con `finish()`; crear sala abre `LobbyActivity`.
- `OnlineModeActivity.kt:42`, `58`, `307-314`, `433-443`, `492-499`, `803-810`: back con `finish()`; abre navegador de lobbies, lobby online o gameplay recuperado.
- `LobbyActivity.kt:141-147`: registra callback de back; `LobbyActivity.kt:220-222` abre `AssigningRolesActivity`; `LobbyActivity.kt:994-1001` abre gameplay/asignacion con extras online.
- `AssigningRolesActivity.kt:69-71`: back callback consume el evento; `AssigningRolesActivity.kt:400-418` abre gameplay y luego `finish()`.
- `GameplayMockActivity.kt:769-775`, `971-974`: back cierra overlays/chat o termina la Activity.
- `LobbyActivity.kt:239-249`: `onStop` en lobby online marca presencia desconectada si no esta transitando a gameplay.

Descripcion real:
- Flujo local principal: `MainActivity -> JugarActivity -> LocalModeActivity -> LobbyActivity -> AssigningRolesActivity -> GameplayMockActivity`.
- Flujo online por codigo/lista: `MainActivity -> JugarActivity -> OnlineModeActivity` o `LobbyBrowserActivity -> LobbyActivity -> AssigningRolesActivity/GameplayMockActivity`.
- Recuperacion online puede saltar desde `OnlineModeActivity` directo a `GameplayMockActivity` si reconstruye una partida en curso (`OnlineModeActivity.kt:453-499`).
- La navegacion local conserva pantallas previas en el stack hasta llegar a `AssigningRolesActivity`, que si hace `finish()` al abrir gameplay (`AssigningRolesActivity.kt:418`).

Discrepancias / riesgos de estado:
- En local, volver atras desde gameplay puede terminar la Activity y dejar en el stack pantallas anteriores; no hay evidencia de invalidacion global de una partida ya iniciada (`GameplayMockActivity.kt:971-974`).
- En online, `onStop` de lobby marca desconectado salvo transicion controlada; esto reduce pero no elimina estados ambiguos si Android detiene la Activity por otros motivos (`LobbyActivity.kt:239-249`).
- `AssigningRolesActivity` bloquea back durante reparto, pero `GameplayMockActivity` permite salir (`AssigningRolesActivity.kt:69-71`, `GameplayMockActivity.kt:971-974`).

## 3. Modelo de datos y reglas

Estado: PARCIAL

Evidencia:
- `RoleCatalog.kt:29-39`: claves reales: `aldeano`, `policia`, `medico`, `alcalde`, `asesino`, `espia`, `mercenario`, `desertor`, `payador`, `bufon`, `oraculo`.
- `RoleCatalog.kt:41-114`: definiciones de datos para los 11 roles anteriores.
- `GameModels.kt:315-330`: fases reales de `GamePhase`.
- `GameRules.kt` no existe como archivo separado; `GameRules` esta dentro de `GameModels.kt:332-373`.
- `GameModels.kt:546-585`: composicion recomendada incluye Mercenario desde 7, Alcalde y rol exclusivo desde 8, Desertor desde 9, Espia desde 10.
- `GameModels.kt:704-709`: rol exclusivo por mapa: Pampa `payador`, Grecia `oraculo`, Medieval `bufon`.
- `GameModels.kt:592-601`: composicion online segura solo usa Policia, Medico, Asesino y Aldeanos.
- `GameModels.kt:712-731`: `roleDeckFor` construye el mazo desde `RoleCompositionConfig`.

Estado por rol pedido:

| Rol | Datos | Logica motor | Accion UI |
|---|---|---|---|
| Asesino | Si, `RoleCatalog.kt:66-70` | Si, `resolveAssassin` y votos de asesinos (`GameEngine.kt:34-90`, `94-134`) | Si, accion `MATAR` (`GameEngine.kt:1018-1019`, `1045`) |
| Medico | Si, `RoleCatalog.kt:54-58` | Si, `resolveMedic` (`GameEngine.kt:276-310`) | Si, accion `SALVAR` (`GameEngine.kt:1024-1025`, `1048`) |
| Detective/Policia | Si, clave `policia`, `RoleCatalog.kt:48-52` | Si, `resolvePolice` (`GameEngine.kt:242-273`) | Si, accion `INVESTIGAR` (`GameEngine.kt:1022-1023`, `1047`) |
| Mercenario | Si, `RoleCatalog.kt:78-82` | Si, `resolveMercenary` (`GameEngine.kt:206-239`) | Si, accion `SILENCIAR` (`GameEngine.kt:1020-1021`, `1046`) |
| Alcalde | Si, `RoleCatalog.kt:60-64` | Si, revelar y desempatar (`GameEngine.kt:433-470`, `751-881`) | Si, `DECIDIR` en desempate y revelar desde gameplay (`GameEngine.kt:1033-1035`, `1042-1053`, `GameplayMockActivity.kt:5262`) |
| Payador | Si, mapa Pampa, `RoleCatalog.kt:90-97` | Si, Contrapunto (`GameEngine.kt:501-546`) | Si, `CONTRAPUNTO` y `SENALAR` (`GameEngine.kt:1028-1032`, `1050-1051`) |
| Espia | Si, `RoleCatalog.kt:72-76` | Parcial: cuenta como traidor/killer y aparece inocente ante investigacion (`GameModels.kt:336-342`, `GameEngine.kt:1802-1805`), pero no tiene fase propia | Parcial: no tiene accion propia; comparte asesinato por `killerRoleKeys`/`activeKillers` |
| Desertor | Si, `RoleCatalog.kt:84-89` | Si, elige/reconsidera bando y afecta ganador (`GameEngine.kt:473-499`, `GameModels.kt:344-356`) | Si, dialogo de eleccion (`GameplayMockActivity.kt:5876-5920`) |
| Oraculo | Si, mapa Grecia, `RoleCatalog.kt:106-113` | Si, invoca muertos una vez (`GameEngine.kt:313-351`, `1546-1564`) | Si, `INVOCAR` y `GUARDAR PODER` (`GameEngine.kt:1026-1027`, `1049`, `GameplayMockActivity.kt:2478-2480`) |
| Bufon | Si, mapa Medieval, `RoleCatalog.kt:98-105` | Si, victoria especial al ser expulsado (`GameEngine.kt:907-925`) | Parcial: no tiene accion; se muestra victoria especial (`GameplayMockActivity.kt:5457`) |

Desertor:
- Esta en las definiciones y en `baseRoleKeys` (`RoleCatalog.kt:84-89`, `116-125`).
- Entra al deck recomendado desde 9 jugadores y al deck si la composicion lo pide (`GameModels.kt:571`, `712-731`).
- No tiene `GamePhase` propia; su eleccion ocurre como input requerido fuera de una fase exclusiva (`GameEngine.kt:1204-1219`).
- Tiene logica real de cambio de equipo (`GameEngine.kt:473-499`, `1959-1978`).

Oraculo y Bufon:
- Existen en modelo, catalogo, recursos y motor. Oraculo tiene fase `NOCHE_ORACULO`; Bufon no tiene fase, pero tiene victoria especial por expulsion (`RoleCatalog.kt:38-39`, `GameModels.kt:321`, `GameEngine.kt:313-351`, `907-925`).

Payador/Contrapunto:
- Sigue restringido a Pampa por `exclusiveMap = RoleMap.PAMPA` y por `exclusiveRoleForMap` (`RoleCatalog.kt:90-97`, `GameModels.kt:704-709`).
- No se encontraron claves `orador` ni `juglar` en los `rg` hechos sobre codigo/recursos; el codigo usa `payador`, `bufon`, `oraculo` como exclusivos.

Discrepancias:
- El rol "Detective/Policia" se implementa con clave `policia`; los assets usan nombres `rol_detective_*` y `RoleCatalog.gameName` solo renombra `policia` a "Comisario" en Pampa (`RoleCatalog.kt:30`, `314-318`).
- Online seguro desactiva roles especiales aunque el lobby local permite composiciones mas amplias (`GameModels.kt:592-601`).

## 4. Motor de juego

Estado: PARCIAL

Evidencia:
- `GameModels.kt:315-330`: fases: `REPARTO`, `NOCHE_ASESINO`, `NOCHE_MERCENARIO`, `NOCHE_POLICIA`, `NOCHE_MEDICO`, `NOCHE_ORACULO`, `AMANECER`, `DIA_DEBATE`, `CONTRAPUNTO`, `VOTACION`, `RECUENTO_VOTOS`, `DESEMPATE_VOTACION`, `ALCALDE_DESEMPATE`, `RESULTADO`.
- `GameEngine.kt:7-32`: `startNight` entra en `NOCHE_ASESINO`.
- `GameEngine.kt:34-351`: resoluciones reales para Asesino, Mercenario, Policia, Medico y Oraculo.
- `GameEngine.kt:354-431`: Amanecer y Debate.
- `GameEngine.kt:501-546`: Contrapunto.
- `GameEngine.kt:551-680`, `684-747`, `751-881`, `885-936`: votacion, desempate, recuento, alcalde y resultado.
- `GameEngine.kt:1087-1102`, `1222-1249`, `1251-1357`: timeouts y autoavance.
- `GameEngine.kt:1567-1624`, `1648-1654`: validaciones de targets nocturnos, Oraculo y votos.

Descripcion real:
- El flujo nocturno puede autoavanzar por roles ausentes o bots con `enterUnifiedNight` hasta encontrar input humano o salir de noche (`GameEngine.kt:1458-1479`).
- Asesino y Espia son killers: `killerRoleKeys = setOf("asesino", "espia")`; Mercenario y Espia cuentan como traidores (`GameModels.kt:336-338`).
- Las validaciones impiden que killers maten traidores (`GameEngine.kt:1567-1578`) y que Mercenario silencie muertos, ya muteados, a si mismo o a alguien silenciado la ronda previa (`GameEngine.kt:1580-1597`).
- `payadorUsed` no se resetea entre rondas de forma intencional segun comentario: "el Contrapunto se usa una sola vez por partida" (`GameEngine.kt:1427-1431`).
- `contrapuntoPlayers` y `contrapuntoSuspicion` si se limpian en `startNextRound` (`GameEngine.kt:1448-1449`).
- `oracleUsed` tampoco se resetea en `startNextRound`, consistente con su definicion de una vez por partida (`GameEngine.kt:1427-1455`, `RoleCatalog.kt:106-109`).

Discrepancias:
- La lista de fases contiene `REPARTO`, pero no hay `resolveReparto`; la transicion a noche se hace con `startNight`, no con una funcion `resolveReparto` (`GameModels.kt:315-330`, `GameEngine.kt:7-32`).
- `payadorUsed` podria parecer bug si se espera reset por ronda, pero el comentario del codigo lo define como uso unico por partida (`GameEngine.kt:1430`).

## 5. IA de bots

Estado: PARCIAL

Evidencia:
- `LocalBotAi.kt:3085-3091`: `stableNoise` es determinista por string.
- `LocalBotAi.kt:1039-1041`, `1867`, `1878-1879`: muchas frases incorporan `chatHistory.size`, fase o indice para variar.
- `GameModels.kt:35-36`, `71-77`: `GameSession` guarda `chatHistory` y `claimLedger`.
- `GameEngine.kt:1884-1896`: `withRecordedClaim` persiste claims por speaker, hasta 12 registros.
- `LocalBotAi.kt:1996-2070`: `conversationMemory` reconstruye memoria conversacional desde historial y claims.
- `LocalBotAi.kt:1970-1977`, `1237-1245`, `1455-1512`, `2622-2645`: personalidad afecta agenda, intencion y estilo textual.
- `LocalBotAi.kt:288-320`, `551-559`: traidores filtran aliados en voto salvo excepciones de cover/bus por dificultad/confianza.

Descripcion real:
- Los bots no usan memoria externa persistente; su memoria vive en `GameSession` (`chatHistory`, `claimLedger`, `votes`, `publicHistory`) y se recalcula desde ahi.
- `stableNoise` por si solo es determinista, pero muchas llamadas incluyen ronda, fase, `chatHistory.size`, indice o mensaje, asi que no se puede afirmar que todos los bots repitan siempre la misma linea dentro de la ronda.
- Personalidad no es solo cosmetica: modifica agenda e intenciones de conversacion; para decision de voto, el peso principal esta en sospechas, claims, historial y dificultad, no en personalidad de forma directa en `chooseVoteTarget`.
- Los bots traidores normalmente evitan votar aliados (`nonAllies`), pero pueden hacerlo por cobertura en NORMAL o por "bus" en casos de alta exposicion/dificultad (`LocalBotAi.kt:293-309`, `551-559`).

Discrepancias:
- `personalityProfile` expone personalidad como mapa publico (`LocalBotAi.kt:21-24`), pero la votacion no parece depender directamente de esa personalidad sino de planes/sospechas; su efecto medible esta mas claro en chat que en voto.

## 6. UI de Gameplay

Estado: PARCIAL

Evidencia:
- `activity_gameplay_mock.xml:16`: incluye `@layout/gameplay_table_section`.
- `gameplay_table_section.xml:1-16`: `merge` con `gameplayBody` horizontal.
- `gameplay_table_section.xml:18-37`, `510-540`: columnas izquierda/derecha de jugadores.
- `gameplay_table_section.xml:186-300`: panel de eventos con fondo y contenedor.
- `gameplay_table_section.xml:302-505`: panel inferior con rol, estado y acciones.
- `gameplay_table_section.xml:543-692`: `chatPanel` integrado en el layout, inicialmente `gone`, con header, mensajes e input.
- `layout-land/gameplay_table_section.xml:2-677`: existe variante landscape del mismo include.
- `GameplayMockActivity.kt:2580-2625`: usa `GameplayTableUi.companionCardMetrics(...)` y aplica layout adaptativo.
- `GameplayMockActivity.kt:3253-3284`, `3286-3343`, `3407-3428`: chat tiene manejo de IME y compacta `bottomPlayerPanel` en portrait con teclado abierto.
- `GameplayMockActivity.kt:5816-5845`: fondos verticales por tema cuando la preferencia vertical esta activa.
- `GameplayMockActivity.kt:5848-5853`: log de eventos por tema.

Estado de la migracion portrait pedida:
- Split de layouts con `<merge>/<include>`: IMPLEMENTADO. `activity_gameplay_mock.xml` incluye `gameplay_table_section`; hay version base y `layout-land` (`activity_gameplay_mock.xml:16`, `gameplay_table_section.xml:2`, `layout-land/gameplay_table_section.xml:2`).
- `companionCardMetrics` con escalado: IMPLEMENTADO/PARCIAL. Se usa `companionCardMetrics` desde gameplay (`GameplayMockActivity.kt:2591-2596`); no se verifico una funcion llamada literalmente `scaledBy()` en el codigo leido, por lo que ese detalle exacto queda `NO DETERMINABLE`.
- Chat como bottom sheet: PARCIAL. No es `Dialog`; es panel integrado/overlay que anima desde abajo en portrait y ajusta dimensiones como sheet (`gameplay_table_section.xml:543-692`, `GameplayMockActivity.kt:3172-3229`, `3407-3428`).
- Toggle de orientacion en Opciones: IMPLEMENTADO. `switchGameplayVerticalDev` persiste `BaseActivity.PREF_GAMEPLAY_VERTICAL_DEV`; `BaseActivity` fuerza portrait/landscape para pantallas de gameplay/lobby (`OpcionesActivity.kt:172-176`, `BaseActivity.kt:23-39`).

Descripcion real:
- La UI tiene columnas laterales, columna central con top status/event log/panel inferior, y chat integrado sobre el layout.
- El chat online escribe en `partidas/{id}/chat` y escucha la subcoleccion (`GameplayMockActivity.kt:3673-3698`, `3713-3738`).
- El panel inferior si tiene manejo de colapso con teclado abierto: oculta role card, status, hint, acciones y usa chip compacto (`GameplayMockActivity.kt:3286-3343`).

Discrepancias:
- La instruccion del prompt mencionaba "en el GameplayMockActivity.kt actual el chat es un Dialog separado"; el codigo actual contradice eso: no se encontro `Dialog` para chat y el panel esta en XML (`gameplay_table_section.xml:543-692`). Los `AlertDialog` visibles se usan, por ejemplo, para Desertor (`GameplayMockActivity.kt:5880-5920`).

## 7. Modo Online (Firebase/Firestore)

Estado: PARCIAL

Evidencia:
- `OnlineRoomFirestore.kt:19-53`: constantes de colecciones, estados y campos de sala.
- `OnlineRoomFirestore.kt:67-134`: crear sala escribe documento de `partidas` y jugador host en batch.
- `OnlineModeActivity.kt:219-245`: generacion de codigo verifica colision antes de crear, con reintentos.
- `OnlineModeActivity.kt:634-668`: union por codigo exige una sola sala en espera.
- `OnlineModeActivity.kt:715-784`: union a sala usa transaccion Firestore y asigna `orden` con `jugadoresActuales`.
- `LobbyActivity.kt:850-920`: inicio online usa transaccion, chequea estado, host activo, jugadores activos y listos, y escribe `partidaInicial`/`estadoPartida`.
- `LobbyActivity.kt:863-887`: evita re-repartir si `partidaInicialCreada` o `partidaInicial` ya existen.
- `GameplayMockActivity.kt:1074-1086`, `OnlinePhaseGate.kt:10-21`: invitados online no avanzan localmente; host publica estado autoritativo.
- `GameplayMockActivity.kt:1403-1473`: host publica `estadoPartida`.
- `GameplayMockActivity.kt:1490-1530`, `1684-1759`: clientes escuchan y aplican `estadoPartida`.
- `GameplayMockActivity.kt:2079-2117`, `2128`: existe handoff de host activo por transaccion.
- `firestore.rules:114-156`, `158-192`, `195-255`: reglas permiten `partidas`, `jugadores`, `acciones`, `chat` validando forma/campos.

Puntos pedidos:
- Deadlock por invitado desconectado antes de listo: PARCIAL. No se borra su documento (`firestore.rules:192` prohibe delete); existe "liberar cupos" que marca `activoEnPartida=false` y decrementa/recalcula en transaccion (`LobbyActivity.kt:432-510`). El boton de kick directo sigue en desarrollo (`LobbyActivity.kt:321-323`, `1951-1955`).
- Generacion de codigo de sala: PARCIAL/IMPLEMENTADO. Hay chequeo de colision por query antes de crear y reintentos (`OnlineModeActivity.kt:219-245`), pero no hay unicidad garantizada por ID/documento o regla transaccional sobre `codigoSala`.
- Write inicial del match: PARCIAL. La asignacion se calcula en cliente host antes de la transaccion (`LobbyActivity.kt:870-877`), pero la transaccion falla cerrado si ya existe match inicial (`LobbyActivity.kt:880-887`). La ventana de doble host queda reducida por transaccion, no eliminada por backend.
- Host handoff: PARCIAL. Existe reclamo de `hostActivoId` por transaccion en gameplay (`GameplayMockActivity.kt:2079-2117`), pero no se verifico mecanismo equivalente completo en lobby antes de iniciar.
- Timer/resolucion host-authoritative: PARCIAL. `OnlinePhaseGate` bloquea avance local de invitados y el host publica `estadoPartida` (`OnlinePhaseGate.kt:11-21`, `GameplayMockActivity.kt:1403-1473`). No hay Cloud Functions en el codigo; si el host queda sin conexion, el respaldo observable es handoff de cliente, no backend.
- Reglas de seguridad Firestore: PARCIAL/ROTO para produccion. Validan forma y limites, pero no usan `request.auth.uid`; por ejemplo `allow update: if validRoomPostUpdate(request.resource.data)` y jugadores solo comparan `uidTemporal` con id de documento (`firestore.rules:155`, `181`).
- Union a sala con transaccion atomica: IMPLEMENTADO. `firestore.runTransaction` lee sala/jugador, valida cupo y asigna `orden` (`OnlineModeActivity.kt:715-784`).
- `OnlineMatchSessionBuilder` falla cerrado ante reentrada incompleta: IMPLEMENTADO. Devuelve `Failure` si faltan `partidaInicial`, `estadoPartida`, jugadores, jugador humano o fase valida (`OnlineMatchSessionBuilder.kt:31-76`, `123-127`).

Discrepancias:
- La UI de opciones informa "ONLINE EXPERIMENTAL" y "cuentas y estadisticas pendientes"; eso coincide con reglas sin Auth fuerte y online client-host (`OpcionesActivity.kt:349-352`, `firestore.rules:155-255`).

## 8. Configuracion / Opciones

Estado: PARCIAL

Evidencia:
- `activity_opciones.xml:88-167`: switches de musica, efectos y vibracion.
- `activity_opciones.xml:200-263`: selector de tamano de texto y switch de modo vertical gameplay.
- `activity_opciones.xml:295-316`: selector de idioma.
- `activity_opciones.xml:336-382`: bloque online/perfil y boton de prueba Firebase.
- `activity_opciones.xml:388-395`: boton reset.
- `OpcionesActivity.kt:107-180`: wiring de controles y persistencia.
- `OpcionesActivity.kt:242-255`: carga preferencias.
- `OpcionesActivity.kt:389-399`: reset de preferencias.
- `OpcionesActivity.kt:514-525`: claves persistidas locales.

Descripcion real:
- Opciones expone musica on/off, efectos on/off, volumen musica, volumen efectos, vibracion, tamano de texto gameplay, modo vertical gameplay, idioma, prueba Firebase y reset.
- Se persiste en `TraidoresPrefs` via `getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` (`OpcionesActivity.kt:65`).
- Claves observadas: `music_volume`, `voice_volume`, `vibration_on`, `gameplay_text_size`, `language`, `pref_gameplay_vertical_dev`, `player_name`, `last_selected_map`, mas las claves de `AudioPreferences.MUSIC_ENABLED` y `AudioPreferences.EFFECTS_ENABLED` usadas en `OpcionesActivity.kt:152-162`.
- El control de orientacion existe como "Modo vertical de gameplay" y afecta lobby/gameplay/asignacion via `BaseActivity` (`OpcionesActivity.kt:172-176`, `BaseActivity.kt:23-39`).

Discrepancias:
- El texto de idioma dice que la traduccion completa sigue en desarrollo (`OpcionesActivity.kt:346-348`, `372-374`); por codigo, el idioma cambia textos de opciones, no necesariamente toda la app.

## 9. Persistencia y serializacion

Estado: PARCIAL

Evidencia:
- `GameModels.kt:5-55`, `57-59`, `62-77`, `79-82`, `102-112`, `204-209`, `306-313`, `315-330`: modelos principales implementan `Serializable`.
- `LobbyActivity.kt:1994-1996`: lectura de `GameSession` por `getSerializableExtra` con `@Suppress("DEPRECATION")`.
- `AssigningRolesActivity.kt:444-446`: lectura de `GameSession` por `getSerializableExtra` con `@Suppress("DEPRECATION")`.
- `GameplayMockActivity.kt:5866-5869`: usa API moderna con fallback deprecated para `getSerializableExtra`.
- `ProfileActivity.kt:45-53`, `290-297`: perfil local se persiste en SharedPreferences.
- `OnlineRoomRecovery.kt:30-57`: recuperacion online se persiste en SharedPreferences.
- `PlayerPublicIdentity.kt:24-37`, `61-80`, `103`: identidad publica usa SharedPreferences y Firestore.

Descripcion real:
- `GameSession`/`GamePlayer` siguen usando serializacion Java nativa, no `Parcelable`.
- Online persiste estado compartido en Firestore y cachea datos de recuperacion/identidad en SharedPreferences.
- No se encontro migracion completa a `Parcelable`.

Discrepancias:
- Hay supresiones de deprecacion alrededor de serializacion, lo que confirma deuda conocida en el codigo (`LobbyActivity.kt:1994`, `AssigningRolesActivity.kt:444`, `GameplayMockActivity.kt:5868`).

## 10. Deuda tecnica visible

Estado: PARCIAL

Evidencia:
- Busqueda de `TODO`/`FIXME`: no arrojo resultados en `app/src/main`, `app/src/test`, `firestore.rules`, `build.gradle`, `app/build.gradle`.
- `@Suppress("DEPRECATION")`: `LobbyActivity.kt:1994`, `GameplayMockActivity.kt:363`, `GameplayMockActivity.kt:440`, `GameplayMockActivity.kt:5868`, `GameplayEffects.kt:57`, `GameplayEffects.kt:76`, `AssigningRolesActivity.kt:444`.
- `LobbyActivity.kt:321-323`, `1951-1955`: expulsion online visible como "todavia esta en desarrollo".
- `GameEngine.kt:1430`: comentario explicito de no resetear `payadorUsed`.
- `GameModels.kt:592-601` vs `546-585`: composicion online segura duplica/parcializa reglas de composicion local.
- `OnlineRoomFirestore.kt:19-53` y `LobbyActivity.kt:1999-2034`: duplicacion de constantes de campos/estados online.

Descripcion real:
- La deuda visible no aparece como `TODO`, sino como supresiones, strings de funcionalidad incompleta y duplicacion de constantes/reglas.
- Hay dos fuentes de constantes online: objeto `OnlineRoomFirestore` y companion de `LobbyActivity`.
- Hay dos composiciones de roles: local completa y online segura reducida.

Discrepancias:
- "Expulsion online" aparece en UI/codigo, pero el flujo real solo muestra Toast de no implementado (`LobbyActivity.kt:1951-1955`).

## 11. Testing

Estado: PARCIAL

Evidencia:
- `app/src/test/java/com/traidores/juego`: existen tests unitarios JVM.
- No se encontro `app/src/androidTest` con archivos instrumentados en el repo inspeccionado.
- Conteo de `@Test` por archivo:
  - `GameEngineTest.kt`: 126
  - `GameplayCountdownTest.kt`: 4
  - `GameplayFeedbackStateTest.kt`: 3
  - `GameplayTableUiTest.kt`: 27
  - `GameTableLayoutTest.kt`: 2
  - `OnlineActionResolverTest.kt`: 7
  - `OnlineAuthoritativeStateMapperTest.kt`: 1
  - `OnlineLobbyRulesTest.kt`: 4
  - `OnlineMatchSessionBuilderTest.kt`: 7
  - `OnlinePhaseGateTest.kt`: 8
  - `OnlineRecoveryGateTest.kt`: 3
  - `OnlineRoomFirestoreTest.kt`: 1
  - `OnlineStartupGateTest.kt`: 6
  - `OnlineSyncWatchdogTest.kt`: 4
  - `RoleCatalogTest.kt`: 5

Descripcion real:
- Hay cobertura unitaria fuerte alrededor de `GameEngine` por volumen de tests.
- Hay tests de UI helper/layout logic (`GameplayTableUi`, `GameTableLayout`, countdown/feedback) y de piezas online puras (`OnlineActionResolver`, gates, builder, lobby rules).
- No se puede determinar porcentaje real de cobertura de `GameEngine` o `LocalBotAi` sin ejecutar herramienta de coverage. `LocalBotAi` no tiene archivo de test dedicado visible por nombre.

Discrepancias:
- La cantidad de tests no prueba cobertura end-to-end Android ni Firestore real; no hay instrumentados visibles.

## 12. Recursos

Estado: PARCIAL

Evidencia:
- Fondos dia/noche por tema: `fondo_gaucho_dia.xml`, `fondo_gaucho_noche.xml`, `fondo_griego_dia.xml`, `fondo_griego_noche.xml`, `fondo_medieval_dia.xml`, `fondo_medieval_noche.xml`.
- Mapas dia/noche y verticales: `mapa_pampa(.webp/_noche.webp/_vertical_dia.webp/_vertical_noche.webp)`, equivalentes para `grecia` y `medieval`.
- Logs por tema: `log_gaucho.webp`, `log_griego.webp`, `log_medieval.webp`.
- Roles base por tema: `rol_aldeano_*`, `rol_alcalde_*`, `rol_asesino_*`, `rol_detective_*`, `rol_espia_*`, `rol_medico_*`, `rol_mercenario_*` existen para gaucho/griego/medieval.
- Desertor por tema: `rol_desertor_gaucho.webp`, `rol_desertor_griego.webp`, `rol_desertor_medieval.webp`.
- Exclusivos: `rol_payador_gaucho.webp`, `rol_oraculo_griego.webp`, `rol_bufon_medieval.webp`.
- `GameplayMockActivity.kt:5795-5798`: si falta un drawable por nombre de rol, cae a `android.R.drawable.ic_menu_gallery`.
- `GameplayMockActivity.kt:5816-5853`: backgrounds y logs se seleccionan por tema.

Descripcion real:
- Los recursos principales de gameplay existen para los 3 temas: fondo dia/noche, mapa normal/noche/vertical, log y cartas base.
- Los roles exclusivos no tienen equivalentes en los otros dos temas, consistente con su restriccion por mapa.
- Existen tambien drawables genericos sin sufijo (`rol_asesino.webp`, `rol_aldeano.webp`, etc.) para varios roles, pero no para todos los exclusivos.

Discrepancias:
- Los nombres de assets usan `detective`, mientras la clave de rol del codigo es `policia`; el puente se hace por `RoleCatalog.gameRole`/nombres de imagen generados, pero la nomenclatura no es uniforme (`RoleCatalog.kt:30`, `RoleCatalog.kt:251-259`).

## Preguntas abiertas

- Si `GameplayMockActivity` es la pantalla real, conviene decidir si el nombre "Mock" se mantiene como deuda aceptada o si confunde futuras auditorias.
- El rol Espia cuenta como killer y traidor, pero no tiene una fase/accion propia: es esa la regla final o una implementacion intermedia.
- `REPARTO` no tiene `resolveReparto`; la transicion ocurre con `startNight`. Es esta separacion intencional para la pantalla de asignacion o falta una abstraccion de fase.
- `payadorUsed` queda por partida completa y no por ronda; el comentario lo confirma, pero la pregunta de diseno es si Contrapunto debe ser una vez por partida para todos los modos.
- Online usa host activo como autoridad y handoff cliente-cliente; falta decidir si eso alcanza para el alcance experimental o si el contrato final exige backend/Cloud Functions.
- La expulsion/kick online esta visible como accion conceptual pero no implementada; debe quedar fuera del MVP online o bloquearse visualmente de forma distinta.
- Online seguro desactiva roles especiales; falta definir si el online objetivo debe soportar todo el motor local o una variante reducida.
- Las reglas Firestore no atan escrituras a `request.auth.uid`; queda abierta la decision de cuando el online deja de ser experimental.
- La traduccion de idioma en opciones existe parcialmente; falta definir si se va a internacionalizar toda la app o solo textos de configuracion.
- La nomenclatura `policia`/`detective`/`comisario` mezcla clave interna, asset y display por mapa; falta definir si esa pluralidad es intencional.
