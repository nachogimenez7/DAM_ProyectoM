# Spec — Online v1 jugable (5 humanos, roles seguros)

Objetivo: que 5 amigos con Android puedan **crear sala, unirse, jugar una partida completa y reingresar si se cae**, hoy. No es el online final (sin App Check, sin Cloud Functions, roles world-readable — ver "Fuera de alcance").

**Decisiones tomadas (jul 2026):**
- Roles online v1: preset seguro ya implementado (`LocalGameFactory.onlineSafeRoleComposition`) = 1 asesino, 1 médico, 1 detective/comisario, resto aldeanos. Con 5 jugadores: 2 aldeanos. **No se toca.**
- Personalización del host en v1: **mapa al crear** + **tiempos de fase** + **reglas de partida** (revelar roles al morir, votos individuales). Nombre de sala sigue automático.
- **PARTIDA RAPIDA se saca** del menú online (era un lobby simulado con bots, confunde).
- El lobby online **no** tiene agregar/quitar jugadores ni elegir rol (elegir rol ya era solo debug local; agregar/quitar hoy aparecen grisados — pasan a ocultos).
- **Modo prueba de pocos jugadores** (§E): herramienta de desarrollo para poder testear el online **solo**, con salas de 3-4 en vez de 5 (mínimo 3: con 2 la partida termina al arrancar). Es la forma de iterar sin juntar 5 personas/dispositivos.

**Diagnóstico del "ya no deja crear sala":** el commit `8c0bbd7` (seguridad online) hizo que el cliente exija **Firebase Auth anónima** antes de crear/unirse/buscar (`OnlineTempIdentity.ensureAuthenticated` → `signInAnonymously()`), y endureció `firestore.rules` para exigir `request.auth`. Pero en la consola de Firebase: (a) el proveedor **Anónimo no está habilitado** → el sign-in falla → toast "No se pudo preparar la sala online" **antes de tocar Firestore** (síntoma confirmado); (b) las **reglas publicadas son las viejas** (sin auth, sin `chat_traidores`, sin `listoParaVotar*`). Además, las reglas nuevas del repo tienen **un bug bloqueante** (§0): no permiten que los invitados publiquen `estadoClientes.{uid}`, lo que trabaría el arranque sincronizado de cada partida.

Orden recomendado: **§0 (parche de reglas) → PARTE 1 (consola) → §E (modo prueba, para testear solo) → §A–C (pulido del lobby)**. Con §0 + PARTE 1 ya se puede jugar con el build actual; §E desbloquea el testeo en solitario; §A–C es el pulido. §E depende de §0 (sin el parche, ni una sala de 3 arranca la primera noche).

---

# PARTE 1 — Para Ignacio: destrabar el online (consola Firebase, sin código)

> Requiere: acceso al proyecto en [console.firebase.google.com](https://console.firebase.google.com) (el del `google-services.json` de la app). 10–15 minutos.

1. **Habilitar login anónimo** (la causa del error al tocar CREAR):
   - Consola → **Authentication** → pestaña **Sign-in method** (si es la primera vez, tocar "Comenzar").
   - Habilitar el proveedor **Anónimo** y guardar.

2. **Publicar las reglas nuevas** — recién después de que Codex aplique el parche §0 al `firestore.rules` del repo:
   - Vía A (sin CLI): consola → **Firestore Database** → **Reglas** → borrar todo, pegar el contenido completo de `firestore.rules` del repo → **Publicar**.
   - Vía B (con CLI): `firebase deploy --only firestore` desde la raíz del repo (publica reglas **e índices** juntos, usa `firebase.json`).

3. **Índice compuesto del buscador de salas** (si no existe, BUSCAR PARTIDA no lista nada):
   - Si usaste la Vía B, ya está (`firestore.indexes.json`).
   - Si no: consola → Firestore → **Índices** → crear índice compuesto en colección `partidas` con campos `estado` (Ascendente) + `actualizadaEn` (Descendente), alcance Colección. Alternativa: abrir BUSCAR PARTIDA en la app; si falla por índice, el error en Logcat (`lobby_browser_listen_failure`) trae un **link directo** para crearlo.

4. **Limpiar salas viejas**: Firestore → Datos → colección `partidas` → borrar documentos con `estado` `abandonada`/`finalizada` o de pruebas anteriores.

5. **Smoke test con 2 celulares antes de la juntada**: crear sala en uno, unirse por código en el otro, ambos LISTO (el host inicia con 5, así que para probar solo el ingreso alcanza ver `2/5` y sin errores). Si algo falla, Logcat filtrando `TraidoresOnline`:
   - `auth_create_room_failure` → falta el paso 1.
   - `create_room_failure` / `chat_send_failure` / `client_state_publish_failure` con PERMISSION_DENIED → reglas sin publicar o sin el parche §0.
   - `lobby_browser_listen_failure` → falta el índice (paso 3).
   - Guía de emergencia completa: `docs/prueba-online-8-celulares.md` (sigue vigente; para 5 jugadores, elegir 5 al crear).

---

# PARTE 2 — Spec de implementación (para Codex)

Restricciones generales: refactors chicos y justificados; no tocar `GameEngine`, el preset de roles online, ni el flujo de sincronización por fases (gates/watchdog). Estilo actual del repo (Kotlin, strings hardcodeadas en español como el resto del archivo tocado).

## §0. Parche de `firestore.rules`: publish de `estadoClientes` por jugadores — **BLOQUEANTE**

**Problema:** durante `REPARTO` (y todo el gameplay) **cada cliente** —también los invitados— hace `update()` sobre `partidas/{id}` con las claves `estadoClientes.{uid}` y `ultimaActividadOnline` (ver `GameplayMockActivity.kt` ~línea 1528, evento `client_state_publish_*`). El `allow update` actual de `partidas/{partidaId}` solo acepta `activeHostCanUpdateRoom() || waitingPlayerCountUpdate() || handoffUpdate()`, y ninguna cubre ese update de un invitado (la sala ya está `en_juego` y cambia `estadoClientes`). Resultado con las reglas nuevas publicadas: PERMISSION_DENIED en cada invitado → el host no ve `enGameplay`/`rolLeido` de nadie → la primera noche solo arranca con FORZAR NOCHE y los gates de arranque quedan ciegos.

**Fix — agregar esta función** junto a las demás funciones de sala en `firestore.rules`:

```
function clientStateSelfPublish(partidaId) {
  return signedIn()
    && validRoomPostUpdate(request.resource.data)
    && immutableRoomFieldsUnchanged()
    && changedOnly(['estadoClientes', 'ultimaActividadOnline'])
    && request.resource.data.get('estadoClientes', {})
        .diff(resource.data.get('estadoClientes', {}))
        .affectedKeys()
        .hasOnly([request.auth.uid])
    && exists(/databases/$(database)/documents/partidas/$(partidaId)/jugadores/$(request.auth.uid));
}
```

**y sumarla al update de la sala:**

```
allow update: if activeHostCanUpdateRoom()
  || waitingPlayerCountUpdate()
  || handoffUpdate()
  || clientStateSelfPublish(partidaId);
```

Notas:
- `get('estadoClientes', {})` cubre el primer publish (cuando la clave todavía no existe en el doc).
- El `hasOnly([request.auth.uid])` garantiza que cada jugador solo puede tocar **su** entrada del mapa.
- El `exists()` limita la escritura a jugadores registrados en esa sala.
- El publish del **host activo** ya pasa por `activeHostCanUpdateRoom` (incluye reafirmar `hostActivoId` y `estado: finalizada` al ganar); el handoff en gameplay (`claimOnlineHostHandoff`) cambia exactamente `hostActivoId`+`hostVersion`+`actualizadaEn` y matchea `handoffUpdate`. No tocar esas funciones.
- No hace falta ningún otro cambio de reglas para §B/§C: `partidaInicial` es un map libre dentro del doc de sala (las reglas solo validan el shape top-level), así que el bloque `config` nuevo pasa igual.

**Verificación:** con las reglas publicadas, una partida de prueba debe mostrar en Logcat `client_state_publish` sin `*_failure` en los invitados, y el host debe arrancar la primera noche sin FORZAR NOCHE.

## §A. Lobby online: ocultar controles locales y sacar PARTIDA RAPIDA

1. **`LobbyActivity.updateOnlineControlState()`** (~línea 1465): hoy deshabilita con alpha 0.55 `btnAddPlayer`, `btnRemovePlayer`, `timingOptionsButton`, `btnAdvancedOptions` cuando `isOnlineGuest() || isFirestoreOnlineLobby()`. Cambiar a:
   - `btnAddPlayer` y `btnRemovePlayer`: `View.GONE` en cualquier lobby online Firestore (host e invitado). En local quedan como están. Si en `activity_lobby.xml` quedan contenedores/espaciadores huérfanos al ocultarlos, colapsarlos también (sin re-maquetar la pantalla).
   - `timingOptionsButton` y `btnAdvancedOptions`: **visibles y habilitados solo para el host online** (`isFirestoreOnlineLobby() && currentUserIsOnlineHost()`); para invitados online, `View.GONE`. En local, sin cambios. Ojo: la promoción de host puede llegar por snapshot → re-evaluar visibilidad en cada `renderLobby()`/`applyOnlineRoomSnapshot`, no solo en `onCreate`.
2. **Diálogo de opciones avanzadas en online** (`showAdvancedOptionsDialog`): cuando `isFirestoreOnlineLobby()`, mostrar solo lo que tiene efecto online: **"Mostrar roles al morir"** y **votos individuales**. Ocultar la sección **COMPOSICION DE ROLES** (el reparto online usa el preset seguro fijo) y **LECTURA INICIAL DEL ROL** (el arranque online usa el gate de EMPEZAR + FORZAR NOCHE, no ese countdown). El diálogo de **TIEMPOS DE PARTIDA** queda completo (las 4 duraciones aplican).
3. **Sacar PARTIDA RAPIDA**: en `activity_online_mode.xml` quitar `btnQuick`; en `OnlineModeActivity` quitar su listener y `openOnlineLobby(...)` (queda sin usos). En `LobbyActivity`, eliminar las ramas muertas de `MODE_ONLINE_QUICK` (título "LOBBY ONLINE - PARTIDA RAPIDA", hint, y la constante si ya no se referencia). **Cuidado**: no tocar el comportamiento de `MODE_ONLINE_CREATE`/`MODE_ONLINE_SEARCH`, y verificar qué usa `isOnlineGuest()` — si solo existía para el modo simulado, simplificar sus usos sin cambiar el resultado en los modos Firestore.

## §B. Selector de mapa en CREAR SALA ONLINE

En `OnlineModeActivity.showCreateRoomDialog()`:
- Agregar debajo del selector de jugadores una fila de **selección de mapa** con los 3 mapas de `LocalGameFactory.maps` (Pampa/Grecia/Medieval). Reusar la estética existente de diálogos (`bg_dialog_game_panel`, botones `dialogButton`, dorado = seleccionado); alcanza con 3 botones de texto o miniaturas (`map.imageRes`) con marco dorado al seleccionar. Preseleccionar el mapa de `PREF_LAST_SELECTED_MAP` si existe, si no el primero.
- Al confirmar CREAR: pasar el `GameMap` elegido por parámetro a `createOnlineRoom(...)` → `createOnlineRoomWithPublicId(...)` → `commitOnlineRoomCreation(...)` (hoy `createOnlineRoomWithPublicId` lo lee de prefs; cambiar a recibirlo). Persistir la elección en `PREF_LAST_SELECTED_MAP` para mantener coherencia con reingresos y con el resto de la app.
- No hace falta tocar el lobby: `applyOnlineRoomSnapshot` ya sincroniza el mapa de la sala a todos los clientes, y el selector de mapa del lobby ya está bloqueado en online ("El mapa lo administra la sala online").

## §C. Config de partida del host serializada en `partidaInicial`

**Problema actual:** `buildOnlineBaseSession()` (LobbyActivity ~línea 1300) no copia `timingConfig`, y cada cliente reconstruye la sesión con `OnlineMatchSessionBuilder.build(..., revealRolesOnDeath = session.revealRolesOnDeath, showIndividualVotes = session.showIndividualVotes)` usando **su** valor local → en cuanto el host personalice algo, cada celular jugaría con tiempos/reglas distintas (los timers visibles y el ritmo de fases divergen; el host manda, pero los invitados verían countdowns incorrectos).

Cambios:
1. `buildOnlineBaseSession()`: incluir `timingConfig = session.timingConfig.normalized()` en el `GameSession` que arma.
2. `initialMatchPayload(assignedSession)` (LobbyActivity ~línea 1373): agregar al map raíz un bloque:
   ```kotlin
   "config" to mapOf(
       "transicionSeg" to assignedSession.timingConfig.transitionSeconds,
       "nocheSeg" to assignedSession.timingConfig.nightSeconds,
       "discusionSeg" to assignedSession.timingConfig.discussionSeconds,
       "votacionSeg" to assignedSession.timingConfig.votingSeconds,
       "revelarRolesAlMorir" to assignedSession.revealRolesOnDeath,
       "votosIndividuales" to assignedSession.showIndividualVotes
   )
   ```
3. `OnlineMatchSessionBuilder.build(...)`: leer `initialMatch["config"]` (map opcional). Si está, usarlo para `timingConfig` (con `.normalized()`), `revealRolesOnDeath` y `showIndividualVotes` del `GameSession` base; si falta (salas creadas antes del cambio), caer en los parámetros actuales — **no** cambiar la firma, los params pasan a ser fallback. Con esto quedan cubiertos los tres consumidores sin trabajo extra: inicio normal (`sessionFromInitialMatch`), reingreso desde `OnlineModeActivity.openRecoveredGameplay`, y cualquier reconstrucción futura.
4. Documentar el bloque `config` en `docs/firebase-online-schema.md` (sección `partidaInicial`).

No requiere cambios de reglas (ver §0, nota final). No tocar el countdown/gates online: al reconstruir todos con la misma `timingConfig`, los timers quedan alineados solos.

## §E. Modo prueba de pocos jugadores (herramienta de desarrollo) — **prioridad para testear solo**

**Motivación:** el mínimo online es 5 (`LocalGameFactory.MIN_PLAYERS`, replicado en `firestore.rules` como `jugadoresEsperados >= 5`). Sin 5 dispositivos/personas no se puede probar el flujo completo. Este modo permite crear salas de **3 o 4** para ejercitar toda la sincronización online (entrar, listos, reparto, noche, votación, victoria, reingreso) con 3 Android que maneja una sola persona. **No es una feature de producto** — es andamiaje de testeo; queda detrás de un toggle explícito.

**Por qué 3 y no 2:** con 2 jugadores el reparto sería 1 asesino + 1 no-asesino → la condición de victoria (`GameEngine`/`GameRules.winnerFor`: `traitors >= town` → ganan traidores) se cumple en el arranque y la partida termina sin jugarse. Con 3 (1 asesino + 1 médico + 1 detective) hay `town=2, traitors=1`: la partida corre al menos una noche + una votación y se ejercitan las 3 acciones nocturnas.

Cambios (todos puntuales, sin reescrituras; **no bajar `MIN_PLAYERS` global** — rompería el modo local y el balance):

1. **`LocalGameFactory`**: agregar `const val TEST_MIN_PLAYERS = 3`. En `onlineSafeRoleComposition(playerCount)`, cambiar el `playerCount.coerceIn(MIN_PLAYERS, MAX_PLAYERS)` inicial por `coerceIn(TEST_MIN_PLAYERS, MAX_PLAYERS)`. Con eso, count=3 → 1 asesino + 1 médico + 1 detective + 0 aldeanos; count=4 → +1 aldeano; count≥5 → sin cambios respecto de hoy. **No toca** el reparto de 5+.

2. **`OnlineModeActivity.showCreateRoomDialog()`**: agregar un `Switch` "SALA DE PRUEBA (POCOS JUGADORES)" (estilo `TraidoresSwitchStyle`, texto secundario aclarando "solo para testeo, 3-4 jugadores"). Cuando está ON, el selector `-`/`+` permite bajar hasta `TEST_MIN_PLAYERS` (3); cuando está OFF, el mínimo sigue en `MIN_PLAYERS` (5). Al confirmar CREAR, pasar el estado del toggle hacia `createRoom(...)` como el parámetro `modePrueba` (hoy es `true` por default; que refleje el toggle). El `expectedPlayers` inicial por default sigue en 5.

3. **`OnlineRoomFirestore.createRoom()`**: el `expectedPlayers.coerceIn(MIN_PLAYERS, MAX_PLAYERS)` (líneas ~83-86) debe usar el piso según modo: `coerceIn(if (modePrueba) LocalGameFactory.TEST_MIN_PLAYERS else LocalGameFactory.MIN_PLAYERS, MAX_PLAYERS)`.

4. **`LobbyActivity`**: dos puntos que hoy asumen 5.
   - `applyOnlineRoomSnapshot()` (~742-745): `onlineExpectedPlayers` se hace `coerceIn(MIN_PLAYERS, MAX_PLAYERS)`. Bajar el piso a `TEST_MIN_PLAYERS` cuando `onlineRoomModePrueba == true` (ese flag ya se lee del snapshot en la línea 737).
   - `onlineMatchEntryProblem()` (~1358): la guarda `if (session.players.size < LocalGameFactory.MIN_PLAYERS)` es redundante con la línea siguiente que compara contra `onlineExpectedPlayers`. Reemplazar el piso duro de 5 por comparar contra `onlineExpectedPlayers` (o quitar la primera guarda y dejar la de `!= onlineExpectedPlayers`), para no bloquear salas de 3-4.

5. **`firestore.rules`** — `validRoomBase(data)`: cambiar los pisos duros a condicionales por `modoPrueba`:
   ```
   && data.jugadoresEsperados >= (data.modoPrueba == true ? 3 : 5)
   && data.jugadoresEsperados <= 15
   && data.maxJugadores >= (data.modoPrueba == true ? 3 : 5)
   && data.maxJugadores <= 15
   ```
   (el resto de `validRoomBase` intacto). Nota de seguridad: `modoPrueba` lo controla el cliente, así que técnicamente se pueden crear salas de 3 marcándolo; es aceptable para el online experimental entre amigos y se cierra con App Check en producción — documentarlo junto a los otros límites en `docs/firebase-online-schema.md`.

6. **Verificar (no romper), sin cambios esperados**: (a) `GameplayMockActivity` startup gate — `expectedOnlineStartupPlayers()` debe salir de la sala, no de una constante 5; confirmar que con 3 el gate se satisface cuando los 3 tocaron EMPEZAR. (b) El layout de la mesa (`renderPlayerColumns`, ~3910) usa `coerceAtLeast(MIN_PLAYERS)` **solo** para calcular el tamaño de carta; con 3 dibuja 3 cartas un poco más chicas de lo óptimo, no rompe — dejarlo así en v1.

**Criterio de aceptación §E:** con el toggle ON, crear una sala de 3; unirse desde 2 instancias; los 3 marcan LISTO; el host inicia; los 3 tocan EMPEZAR; arranca la noche sin FORZAR NOCHE; se juega noche + día + votación hasta una victoria; sin `*_failure` en Logcat. Con el toggle OFF, el selector no baja de 5 (el modo normal queda intacto).

## §D. Criterios de aceptación (QA con 5 celulares)

1. Crear sala eligiendo mapa Grecia y 5 jugadores → la sala aparece en BUSCAR PARTIDA de otro celular con "Mapa Grecia" y `1/5`, y el lobby de todos muestra Grecia.
2. En el lobby online no existen botones de agregar/quitar jugador ni selector de rol; el invitado no ve TIEMPOS ni OPCIONES; el host sí.
3. Host configura TIEMPOS (p. ej. preset LENTO) y "revelar roles al morir" ON → tras iniciar, **los 5 celulares** muestran las mismas duraciones de noche/discusión/votación y revelan rol al morir.
4. Los 4 invitados entran por código, marcan LISTO; el host inicia; todos tocan EMPEZAR; la primera noche arranca **sin** FORZAR NOCHE y sin `client_state_publish_failure` en Logcat.
5. Partida completa: noche (asesino mata / médico salva / detective investiga), día con chat, votación con expulsión, hasta victoria de un bando. Sin `*_failure` en Logcat.
6. Un celular cierra la app en mitad de la partida → REINGRESAR lo devuelve al gameplay con su misma carta y fase.
7. El menú online ya no muestra PARTIDA RAPIDA; CREAR / UNIRSE POR CODIGO / BUSCAR / REINGRESAR funcionan.

## Fuera de alcance (v1) — no intentar

- App Check, Cloud Functions, Auth con cuentas reales, TTL/limpieza automática de salas.
- Cerrar el secreto de roles a nivel servidor (`partidaInicial` sigue world-readable; es honor-system, documentado en `docs/firebase-online-schema.md`).
- Roles online adicionales (alcalde, mercenario, etc.) y más de 1 asesino (el diseño para 2+ killers ya está documentado en el schema, no se implementa ahora).
- Rediseñar el lobby como Activity nueva — se pule `LobbyActivity` en su modo online.
