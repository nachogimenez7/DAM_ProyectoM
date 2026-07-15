# SPEC — Migración de chat + presencia a Realtime Database

> Para Codex. Español, `archivo:línea`. **No compilar** (el usuario valida en Android Studio). Un solo spec, en dos partes. Se corre **después** del rediseño de lobby (ya terminado y revisado OK). Objetivo: sacar de Firestore el "chorro" de datos que más escala mal (chat y presencia) y llevarlo a **Realtime Database (RTDB)**, que cobra por datos y no por operación. **El motor de la partida NO se toca** (el estado autoritativo `partidaInicial`/`estadoPartida` sigue en Firestore).
>
> **Decisiones ya tomadas con el usuario (no re-preguntar):**
> 1. **Arquitectura híbrida:** Firestore = lo durable (sala, perfiles, `publicId`, `estadoPartida`, votos/acciones); RTDB = lo vivo (chat + presencia).
> 2. **Migran 3 flujos de chat online:** `chat` (público de partida), `chat_traidores` (secreto de asesinos), `chat_lobby` (lobby). **El chat del modo LOCAL NO migra** (es 100% en memoria, nunca tocó Firebase).
> 3. **Presencia con `onDisconnect()`** (RTDB marca offline solo cuando se cae el socket, incluso si la app crashea).
> 4. **Orden interno:** primero el chat (probar bien), después la presencia (es la parte delicada porque toca la lógica de host/listos que recién estabilizamos).
>
> **Qué NO migra (queda en Firestore):** el doc de sala `partidas/{id}`, los docs de jugador `jugadores/{uid}` (salvo el campo de presencia — ver Parte 2), `partidaInicial`, `estadoPartida`, `acciones`, `votos`, `configLobby`, `votoMapa`, perfiles y `publicId`. Los **eventos "de Dios"** del chat de partida NO viven en la colección de chat: se derivan de `estadoPartida` (`GameplayFeedMessages.appendGodEvents`) y **se quedan como están**. Los **avisos de sistema del lobby** ya se generan localmente (`addLobbySystemNotice`, `LobbyActivity.kt:665`), tampoco tocan Firebase.

---

## Setup (una vez, antes de tocar código)

- **Consola:** habilitar Realtime Database (crear la base, elegir ubicación, arrancar en modo bloqueado).
- **Dependencia:** agregar `com.google.firebase:firebase-database-ktx` en `app/build.gradle` (ya está el BoM de Firebase).
- **Reglas:** crear `database.rules.json` en la raíz y registrarlo en `firebase.json` (junto a `firestore`). Ver el ejemplo al final.

### Estructura de datos en RTDB
```
/salas/{roomId}
    /chat/{pushId}            -> { actorId, speaker, mensaje, tipo, ts }
    /chat_traidores/{pushId}  -> { actorId, speaker, mensaje, tipo, ts, isGod }
    /chat_lobby/{pushId}      -> { actorId, speaker, mensaje, emoteId?, tipo, ts }
    /presencia/{uid}          -> { estado, ts }
```
`{pushId}` = clave de `push()` (ya ordena cronológicamente). `ts` = `ServerValue.TIMESTAMP`. `roomId` = el `onlinePartidaId` actual.

---

## Parte 1 — Chat a RTDB (3 flujos)

### Diagnóstico (código actual)
- **Lobby:** `LobbyChatController.kt` — hoy escribe/lee Firestore `chat_lobby` (`add`, `orderBy(creadaEn).limitToLast(30)`, trim en batch). Clase autocontenida (~150 líneas), la UI la consume por callbacks.
- **Gameplay público:** `GameplayChatController.startOnlineChatListener` (`GameplayChatController.kt:1464`, colección `chat` en `:1470`) + envío `sendOnlineHumanChatMessage` (`:1343`, `add` en `:1367`).
- **Gameplay traidores:** `startOnlineTraitorChatListener` (`:1517`, colección `chat_traidores` en `:1526`) + envío `sendOnlineTraitorChatMessage` (`:1403`, `add` en `:1427`).
- **Limpieza en revancha:** la función de limpieza (`rematch_cleanup`, `LobbyActivity.kt:~2400-2467`) borra en lote las subcolecciones de chat en Firestore.

### Arreglo
**1.1 — `LobbyChatController` a RTDB (el más limpio, empezar por acá).** Cambiar solo la **capa de datos**, sin tocar la UI ni los callbacks (`onMessagesChanged`, avisos de sistema, dock, sheet, ocultar):
- Referencia: `FirebaseDatabase.getInstance().getReference("salas/$roomId/chat_lobby")`.
- Enviar: `ref.push().setValue(payload)` donde `payload` = { actorId, speaker, mensaje, emoteId?, tipo, ts: ServerValue.TIMESTAMP } (mismo shape que hoy, `creadaEn` → `ts`).
- Escuchar: `ref.orderByKey().limitToLast(MAX_MESSAGES)` con un `ChildEventListener` o `ValueEventListener`; mapear a `LobbyChatMessage` (usar la clave `push` como `id`, `ts` como `createdAtLocal`).
- Trim: reemplazar todo `requestTrimIfAllowed`/batch por nada — con `limitToLast` alcanza; y si se quiere podar duro, `removeValue()` de las claves viejas. El campo `canTrimHistory` deja de tener sentido.

**1.2 — `GameplayChatController` a RTDB (público + traidores).** Mismos dos listeners y dos envíos, apuntando a `salas/$roomId/chat` y `salas/$roomId/chat_traidores`, con `push()`/`limitToLast`. Mantener igual: el toggle PUEBLO/PLAN, el filtrado por canal, y que los eventos de Dios sigan viniendo de `estadoPartida` (no de RTDB). El `isGod` del canal traidores se conserva en el payload.

**1.3 — Limpieza en revancha = un renglón.** En la función de limpieza (`rematch_cleanup`, `LobbyActivity.kt:~2400`), reemplazar el borrado en lote de las subcolecciones Firestore por:
```kotlin
val db = FirebaseDatabase.getInstance()
listOf("chat", "chat_traidores", "chat_lobby").forEach { node ->
    db.getReference("salas/$onlinePartidaId/$node").removeValue()
}
```

**1.4 — Reglas RTDB (chat).** En `database.rules.json`, por cada nodo de chat: lectura por miembro de sala; escritura solo del propio autor y con tamaños válidos. (Aclaración honesta: las reglas de RTDB no hacen `get()` cruzados tan ricos como Firestore, así que el **secreto del canal traidores sigue honor-system**, igual que hoy — no es una regresión.) Ejemplo al final.

### Verificación
- Dos celulares en la misma sala ven los mensajes del lobby y de la partida en tiempo real.
- Los emotes y los avisos de sistema del lobby siguen apareciendo.
- Al preparar la revancha, el chat queda vacío (los 3 nodos borrados).
- En la consola: los mensajes aparecen en `Realtime Database → Datos` bajo `/salas/...`, y Firestore deja de recibir escrituras de chat.

---

## Parte 2 — Presencia a RTDB con `onDisconnect()` (la parte delicada)

### Diagnóstico
- Hoy la presencia es el campo `estado` (`conectado`/`desconectado`) + `ultimaConexion` en el doc de jugador Firestore, que cada cliente actualiza con heartbeats (`markOnlinePresence`, `LobbyActivity.kt:838`; `markOnlineGameplayPresence`, `GameplayMockActivity.kt:908`). Problema real: si la app se cierra de golpe, queda un `conectado` fantasma hasta que caduca el heartbeat.
- **Punto clave (chokepoint):** el flag `connected` que consume toda la lógica se calcula en **un solo lugar** por pantalla:
  - Lobby: `onlineParticipants()` (`LobbyActivity.kt:1481`) arma `OnlineLobbyParticipant(connected = player.status == PLAYER_STATE_CONNECTED)`. De ahí lo leen `activeOnlinePlayers` (`:1494`), `releasableDisconnectedOnlinePlayers` (`:1499`), `maybeClaimOnlineLobbyHostHandoff` (`:1593`), `allOnlinePlayersReady` (`:2478`), `onlineRoomCanStart` (`:2483`) y `OnlineLobbyRules.canStart`/`hostHandoffCandidate`.
  - Gameplay: chequeos directos `player.state == PLAYER_STATE_CONNECTED` en el handoff (`GameplayMockActivity.kt:2590`, `:2649`, `:2689`, `:2702`, `claimOnlineHostHandoff` en `:2666`).

### Arreglo (bajo riesgo gracias al chokepoint)
**2.1 — Publicar presencia en RTDB con `onDisconnect()`.** Al entrar a la sala/gameplay, en vez de (o además de) escribir `estado` en Firestore:
```kotlin
val ref = FirebaseDatabase.getInstance().getReference("salas/$roomId/presencia/$uid")
ref.setValue(mapOf("estado" to "conectado", "ts" to ServerValue.TIMESTAMP))
ref.onDisconnect().setValue(mapOf("estado" to "desconectado", "ts" to ServerValue.TIMESTAMP))
```
RTDB marca `desconectado` solo cuando se cae el socket (incluye crash/kill). Reemplaza a los heartbeats como fuente de verdad de "conectado".

**2.2 — Un listener de presencia que alimente el flag `connected`.** Suscribirse a `salas/$roomId/presencia` y mantener un `Map<uid, Boolean> presenceConnected`. **No** tocar los sitios de lectura de arriba: cambiar únicamente **cómo se calcula `connected` en el chokepoint**:
- Lobby `onlineParticipants()`: `connected = presenceConnected[player.id] == true` (en vez de leer el `estado` de Firestore).
- Gameplay: donde hoy hace `player.state == PLAYER_STATE_CONNECTED`, leer del mismo `presenceConnected`.
Así toda la lógica de host/listos/liberar sigue igual, solo cambia la fuente del dato.

**2.3 — Firestore mantiene lo durable.** El doc de jugador sigue con `nombre`, perfil, `orden`, `listo`, `activoEnPartida`, `esHost`, `votoMapa`. El campo `estado`/`ultimaConexion` queda **legacy** (se puede seguir escribiendo por compatibilidad con clientes viejos, pero ya nadie decide con él). Se puede quitar en una limpieza posterior.

**2.4 — Reglas RTDB (presencia).** Cada uno escribe solo su `/presencia/{uid}` (`auth.uid === $uid`); lectura por miembro de sala.

### Verificación
- Matar la app de un jugador (swipe/force-stop) → los demás lo ven **desconectado en segundos**, sin esperar el heartbeat.
- Sigue funcionando: `FALTAN N LISTOS`, `canStart`, el handoff de host cuando el creador se cae, y liberar cupos de desconectados.
- El host handoff en gameplay (creador muerto/caído) sigue eligiendo bien al siguiente por orden.

---

## Pasos en Firebase (para chequear y entender todo)

Entrá a **console.firebase.google.com → tu proyecto**, sección **Build/Compilación**:

1. **Antes de migrar — tomá un "antes":** en **Firestore Database → Uso**, mirá cuántas **lecturas/escrituras** hacés en una partida con chat. Anotá el número.
2. **Realtime Database:**
   - **"Crear base de datos"** (ubicación, ej. `us-central1`, modo bloqueado).
   - Pestaña **Reglas**: pegás las de `database.rules.json` (o `firebase deploy --only database` desde la CLI).
   - Pestaña **Datos**: el árbol JSON en vivo. Al probar, vas a ver aparecer `/salas/{codigo}/chat_lobby/…`, `/chat/…`, `/presencia/{uid}` — **acá confirmás que el chat y la presencia ya viven en RTDB.**
   - Pestaña **Uso**: conexiones simultáneas, almacenamiento y descarga.
3. **Después de migrar — compará:** volvé a **Firestore → Uso** y fijate que las lecturas **bajaron** (ya no hay chat ni heartbeats); lo nuevo aparece en **Realtime Database → Uso**.
4. **Authentication → Users:** sin cambios; seguís viendo los usuarios anónimos (`uidTemporal`), que son los que las reglas de RTDB validan con `auth.uid`.

Tier gratis (Spark) de RTDB: 100 conexiones simultáneas, 1 GB guardado, 10 GB/mes de descarga — con 6-8 jugadores te sobra.

### Ejemplo de `database.rules.json`
```json
{
  "rules": {
    "salas": {
      "$roomId": {
        "chat":            { ".read": "auth != null", "$msg": { ".write": "auth != null && !data.exists() && newData.child('actorId').val() === auth.uid && newData.child('mensaje').val().length >= 1 && newData.child('mensaje').val().length <= 140" } },
        "chat_traidores":  { ".read": "auth != null", "$msg": { ".write": "auth != null && !data.exists() && newData.child('actorId').val() === auth.uid && newData.child('mensaje').val().length <= 140" } },
        "chat_lobby":      { ".read": "auth != null", "$msg": { ".write": "auth != null && !data.exists() && newData.child('actorId').val() === auth.uid && newData.child('mensaje').val().length <= 140" } },
        "presencia":       { ".read": "auth != null", "$uid": { ".write": "auth != null && auth.uid === $uid" } }
      }
    }
  }
}
```
(Es un piso razonable, alineado a lo que hoy validás en Firestore. `!data.exists()` evita editar mensajes ajenos; el secreto de lectura sigue honor-system como hoy.)

---

## Orden de entrega
1. **Setup** (habilitar RTDB, dependencia, `database.rules.json` + `firebase.json`).
2. **Parte 1 — chat** (lobby primero, después gameplay; probar 2 celulares + revancha).
3. **Parte 2 — presencia** (probar el handoff de host y "faltan N listos" con una app matada de golpe).

Actualizar `docs/firebase-online-schema.md` con la sección de RTDB (`/salas/{roomId}/…`) y marcar el `estado` de Firestore como legacy.
