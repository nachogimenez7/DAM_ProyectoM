# Spec — Seguridad y moderación online (plan gratuito)

Contexto y justificación de cada punto: [`docs/seguridad-online.md`](../../seguridad-online.md).

Objetivo: llevar el online de "experimental entre amigos" a "publicable en Play Store" **sin
salir del plan Spark**. Cubre tres cosas: cerrar lo que se puede cerrar gratis, darle al
anfitrión y a la mesa herramientas contra el jugador tóxico, y dejar documentado lo que no se
cierra sin backend pago.

**Ya aplicado en el repo (no rehacer):** `firestore.rules` con membresía en el traspaso de
anfitrión y en el contador de jugadores, `meta/public_ids` limitado a `+1`, `pruebas/` acotado,
y las colecciones nuevas `partidas/{id}/baneados/{uid}` y `bans/{uid}`. La mitad de servidor
del baneo **ya está**; falta la mitad de cliente.

**Orden obligatorio:** §A antes que todo lo demás (sin App Check, §C y §D son decorativos).
§E.1 antes que §E.2 (si se invierte, se rompe el chat). §F es la fase más grande y va al final.

**Restricciones generales:** refactors chicos y justificados. No tocar `GameEngine`, el preset
de roles online, ni los gates de sincronización por fases. Estilo del repo: Kotlin con strings
en español hardcodeadas en el archivo que se toca, salvo que ya exista el string en
`strings.xml`.

---

# PARTE 1 — Para Ignacio (consola y trámites, sin código)

## 1. Publicar las reglas nuevas

```bash
firebase deploy --only firestore:rules
```

Si el CLI rechaza el archivo, el error trae línea y columna. **No pude validar la sintaxis en
esta máquina** (el emulador de Firestore pide Java 11 y hay Java 8), así que este deploy es la
verificación.

Prueba de humo después de publicar, con dos celulares:

1. Crear sala, entrar desde el otro celular por código. Debe seguir funcionando igual.
2. Opciones → PROBAR FIREBASE. Debe seguir dando OK.
3. Jugar una partida corta hasta el final.
4. Cerrar la app del anfitrión en mitad de la partida y verificar que el traspaso de anfitrión
   sigue ocurriendo (Logcat, filtro `TraidoresOnline`, evento `host_handoff_claim_success`).

Si algo diera `PERMISSION_DENIED`, el evento de Logcat dice exactamente qué escritura fue.

## 2. Activar App Check (§A) — el paso más importante de todos

1. Consola de Firebase → **App Check** → pestaña **Apps** → registrar la app Android con el
   proveedor **Play Integrity**.
2. Play Console → tu app → **Integridad de la aplicación**: vinculá el proyecto de Firebase si
   te lo pide.
3. Con el build de debug corriendo, buscar en Logcat una línea con
   `Enter this debug secret into the allow list` y un UUID. Copiarlo en App Check →
   **Apps** → menú de la app → **Manage debug tokens**. Sin esto no vas a poder probar desde
   Android Studio.
4. Dejar App Check en **modo no aplicado (unenforced)** al menos una semana. La pestaña
   **APIs** muestra el porcentaje de peticiones verificadas.
5. Cuando ese porcentaje esté alto y estable, **aplicar (enforce)** en Firestore, en Realtime
   Database y en Authentication. Desde ese momento, cualquier cliente que no sea tu APK queda
   afuera.

> Ojo: aplicar App Check antes de que la versión con el código de §A esté en manos de los
> jugadores deja afuera a todos los que tengan una versión vieja. Primero se publica la
> actualización, después se aplica.

## 3. Banear una cuenta a mano

Mientras no haya Cloud Functions, el baneo global es manual y a vos te alcanza:

1. Consola → Firestore → colección `reportes` (la crea §D). Cada documento trae `reportadoId`,
   el motivo y la sala.
2. Para banear: crear a mano el documento `bans/{uid}` con los campos `motivo` (texto) y
   `creadaEn` (timestamp). Con que exista el documento, alcanza.
3. Efecto inmediato: esa cuenta no puede crear salas ni entrar a ninguna. Para levantar el
   baneo, borrar el documento.

Ningún cliente puede escribir en `bans`: la regla es `allow write: if false`.

## 4. Trámites de Play Store (bloqueantes para publicar)

Con cuentas por correo ya en la app, Google exige:

- **Política de privacidad** publicada en una URL fija. Tiene que decir qué se guarda (correo,
  nombre de perfil, ID público, historial de partidas) y cómo se borra.
- Formulario de **Data safety** completo y coherente con lo anterior.
- **Borrado de cuenta**: desde dentro de la app **y** desde una URL pública, sin tener que
  escribirte. Ver §G.4.
- `targetSdk` ya migrado a 36 (`compileSdk 36.1`) el 25 de julio de 2026. Falta la prueba
  manual final en dispositivos Android 15 y 16 antes del primer build de Play.

---

# PARTE 2 — Spec de implementación (para Codex)

## §A. App Check con Play Integrity — **PRIMERO**

**Por qué:** sin esto, `google-services.json` (que está commiteado en el repo) es todo lo que
necesita un tercero para hablar con Firestore y RTDB desde un script, sin la app. Toda
validación del lado del cliente —cooldowns, silencios, límites— vale cero mientras el cliente
pueda ser un script. Con App Check, Firebase rechaza cualquier petición que no venga del APK
firmado corriendo en un dispositivo íntegro.

**A.1 — `app/build.gradle`**, dentro de `dependencies`:

```groovy
implementation 'com.google.firebase:firebase-appcheck-playintegrity'
debugImplementation 'com.google.firebase:firebase-appcheck-debug'
```

Van sin versión: las resuelve el BOM que ya está declarado.

**A.2 — `TraidoresApplication.onCreate()`**, antes de `configureFirestore()`:

```kotlin
private fun configureAppCheck() {
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val factory = if (debuggable) {
        DebugAppCheckProviderFactory.getInstance()
    } else {
        PlayIntegrityAppCheckProviderFactory.getInstance()
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
}
```

Se usa `FLAG_DEBUGGABLE` y no `BuildConfig.DEBUG` a propósito: con AGP 8 el `BuildConfig` no se
genera salvo que se active `buildFeatures { buildConfig true }`, y no hace falta tocar el build
para esto.

**Criterio de aceptación:** la app arranca y el online sigue funcionando igual (App Check
todavía no está aplicado en la consola). En Logcat de un build de debug aparece el token de
depuración. En la consola, la pestaña APIs de App Check empieza a contar peticiones
verificadas.

---

## §B. Sacar el rol de `estadoClientes` — dos líneas, impacto enorme

**Problema:** cada cliente publica su propio rol, en claro, en el documento de sala, y ese
documento lo lee cualquier cuenta autenticada. Es el camino más corto para hacer trampa: no
hay que interpretar el reparto, el rol de cada jugador aparece servido y actualizado en cada
fase.

**Fix** — en [`GameplayMockActivity.kt:2018`](../../../app/src/main/java/com/traidores/juego/GameplayMockActivity.kt:2018),
borrar del payload de `estadoClientes.{uid}`:

```kotlin
"jugador" to human.name,
"rolKey" to human.role?.key.orEmpty(),
```

Son campos de depuración ("datos de depuración para Logcat/Firebase Console" según el schema).
Si hacen falta para diagnosticar, van por `OnlineDebugLog`, que es local, nunca al documento
compartido.

**Además:** revisar que ningún otro payload publicado en `partidas/{id}` lleve roles. Hoy el
mapa de jugadores de `estadoPartida` publica `nombre`, `vivo`, `muteado`,
`ultimaRondaSilenciado`, `afkNoche`, `afkVoto` y `causaEliminacion`, sin rol: **mantenerlo
así**.

**Actualizar** `docs/firebase-online-schema.md`, sección `estadoClientes`, quitando los dos
campos.

**Criterio de aceptación:** una partida online completa funciona igual, y en la consola de
Firebase el documento de sala ya no contiene ningún `rolKey`.

---

## §C. Moderación

Tres herramientas, de menor a mayor peso. La idea es que la mesa se defienda sola y que el
anfitrión sólo intervenga cuando la mesa no alcanza.

### §C.1 — Silenciar local (no necesita servidor)

Cada jugador puede ocultar los mensajes y emotes de otro, sólo para sí mismo. No se puede
abusar, no se puede evadir y resuelve el caso más común.

- Entrada: el mini-perfil de jugador que ya existe (`PlayerProfileDialog`), botón
  **SILENCIAR PARA MÍ** / **VOLVER A ESCUCHAR**.
- Estado: en memoria de la Activity de gameplay más `SharedPreferences` (`TraidoresPrefs`,
  clave `silenciados_locales`, set de `publicId`) para que sobreviva a la partida.
- Efecto: `GameplayChatController` descarta los mensajes de ese `actorId` al renderizar, y
  `GameplayEffects` no reproduce sus emotes. **No** se filtran los eventos de sistema ni los
  votos: silenciar es dejar de oír a alguien, no dejar de ver lo que hace.
- El feed debe mostrar una línea discreta la primera vez: *"Silenciaste a {nombre}. No vas a
  ver sus mensajes."*

### §C.2 — Silencio por votación de la mesa

Lo que pediste: la mesa vota y el silenciado deja de poder escribir texto libre, pero sigue
pudiendo jugar con las respuestas rápidas.

**Reglas de producto**

- Puede proponerlo cualquier jugador **vivo**, sobre otro jugador **vivo**, durante la fase de
  discusión o votación. No durante la noche.
- Una sola propuesta activa por vez en la sala. Cooldown de 60 s entre propuestas de un mismo
  proponente.
- Ventana de votación: 30 s. Pasa con **mayoría estricta de los vivos** (más de la mitad).
- Efecto: por el resto de la partida, ese jugador **no puede enviar texto libre** en el chat
  público ni en el de traidores. **Sí** puede usar respuestas rápidas y emotes. No se toca su
  capacidad de votar, actuar de noche ni ganar.
- No se puede proponer contra el anfitrión activo (evita el bloqueo de la partida).
- Se anuncia en el feed: *"La mesa silenció a {nombre}."* Con el resultado de la votación
  visible, como cualquier otra votación.

**Datos (RTDB)**

```text
/salas/{roomId}
    /votos_silencio/{objetivoUid}/{votanteUid}   = { ts }
    /silenciados/{objetivoUid}                    = { ts, votos }
```

**Reglas de RTDB** (`database.rules.json`), dentro de `salas/$roomId`:

```json
"votos_silencio": {
  ".read": "auth != null && root.child('salas').child($roomId).child('presencia').child(auth.uid).exists()",
  "$objetivoUid": {
    "$votanteUid": {
      ".write": "auth != null && auth.uid === $votanteUid && $votanteUid !== $objetivoUid && !data.exists() && root.child('salas').child($roomId).child('presencia').child(auth.uid).exists()",
      ".validate": "newData.hasChildren(['ts'])",
      "ts": { ".validate": "newData.isNumber() && newData.val() <= now" },
      "$other": { ".validate": false }
    }
  }
},
"silenciados": {
  ".read": "auth != null",
  "$objetivoUid": {
    ".write": "auth != null && auth.uid !== $objetivoUid && !data.exists() && root.child('salas').child($roomId).child('presencia').child(auth.uid).exists() && root.child('salas').child($roomId).child('votos_silencio').child($objetivoUid).numChildren() >= 3",
    ".validate": "newData.hasChildren(['ts', 'votos'])",
    "ts": { ".validate": "newData.isNumber() && newData.val() <= now" },
    "votos": { ".validate": "newData.isNumber() && newData.val() >= 3 && newData.val() <= 15" },
    "$other": { ".validate": false }
  }
}
```

El piso de 3 votos es lo único que el servidor puede contar por sí solo; la mayoría real la
calcula el cliente. Consecuencia aceptada: **en salas de prueba de 3 o 4 jugadores el silencio
por votación no está disponible** (no hay 3 votantes posibles distintos del objetivo). La UI
debe ocultar la opción en ese caso, no fallar.

**Aplicación del silencio en el servidor** — en los nodos `chat` y `chat_traidores`, agregar el
campo opcional `tipo` al esquema y condicionar la escritura:

```json
"tipo": { ".validate": "newData.val() === 'texto' || newData.val() === 'rapida'" }
```

y en el `.write` de `$messageId`, sumar a la condición de creación:

```text
&& (newData.child('tipo').val() === 'rapida'
    || !root.child('salas').child($roomId).child('silenciados').child(auth.uid).exists())
```

`tipo` tiene que ser **opcional** en el `.validate` del mensaje (no agregarlo a `hasChildren`)
para que un cliente que todavía no actualizó no quede roto; pero el cliente nuevo debe
mandarlo siempre. Recordar que hay un `"$other": { ".validate": false }`: si `tipo` no se
declara explícitamente, todos los mensajes nuevos se rechazan.

**Cliente**

- La votación se coordina por el mismo canal que ya usan las presentaciones compartidas: el
  proponente escribe su voto, todos escuchan `votos_silencio/{objetivo}` y renderizan el
  contador en vivo.
- Cuando los votos superan la mayoría de vivos, **el primer cliente que lo detecta** escribe
  `silenciados/{objetivo}`. Es idempotente: la regla exige `!data.exists()`, así que el resto
  falla silenciosamente y no hay que coordinar quién escribe.
- El silenciado ve su campo de texto deshabilitado con el texto *"La mesa te silenció. Podés
  seguir jugando con las respuestas rápidas."* y la barra de respuestas rápidas visible.
- Limpieza: `silenciados` y `votos_silencio` se borran junto con el resto del nodo de sala en
  la revancha y en el desarme (mismo lugar donde hoy se vacían los cuatro chats).

**Criterio de aceptación:** con 5 celulares, 3 votos silencian; el silenciado no puede mandar
texto (falla también si se fuerza desde otro cliente) y sí puede mandar respuestas rápidas; el
resto de la partida funciona normal.

### §C.3 — Expulsar y banear de la sala (anfitrión)

El servidor ya lo permite; falta el cliente.

**Regla de producto:** sólo en el lobby (`esperando` o `finalizada`), nunca en mitad de una
partida. En partida, la herramienta es §C.2.

**Cliente** — sobre `removeOnlinePlayer()`
([`LobbyActivity.kt:1271`](../../../app/src/main/java/com/traidores/juego/LobbyActivity.kt:1271)),
que ya hace la expulsión:

1. El diálogo de confirmación pasa a tener dos acciones: **EXPULSAR** (lo de hoy) y
   **EXPULSAR Y BANEAR** (no puede volver a entrar a esta sala).
2. En la variante con baneo, dentro de la misma transacción, crear
   `partidas/{id}/baneados/{uid}`:

```kotlin
mapOf(
    "uidTemporal" to player.id,
    "nombre" to player.name,
    "baneadoPor" to onlineTempUid,
    "creadaEn" to FieldValue.serverTimestamp()
)
```

   La regla exige exactamente esos campos (más `motivo` opcional de hasta 60 caracteres),
   `creadaEn == request.time` y que el objetivo no sea ni vos ni el creador de la sala.

3. **Lado del baneado:** hoy se entera sólo porque sus escrituras empiezan a fallar. Agregar un
   listener a `partidas/{id}/baneados/{miUid}`; si aparece, cerrar el lobby con
   *"El anfitrión te expulsó de esta sala."* y volver al menú online. Mismo chequeo antes de
   entrar por código o desde el navegador, para dar el mensaje correcto en vez de un
   `PERMISSION_DENIED` genérico.
4. **Lista de baneados:** en las opciones del lobby, una entrada **EXPULSADOS (n)** que muestra
   los nombres y permite quitar el baneo (`delete`, que la regla ya autoriza al anfitrión).
5. **Limpieza:** agregar `"baneados"` a la lista de subcolecciones de
   `teardownEmptyOnlineRoom()` ([`LobbyActivity.kt:1044`](../../../app/src/main/java/com/traidores/juego/LobbyActivity.kt:1044)).
   **No** agregarla a la limpieza de revancha: el baneo tiene que sobrevivir a la revancha, que
   es justamente cuando el expulsado intentaría volver.

**Criterio de aceptación:** el anfitrión banea a un jugador; ese celular vuelve al menú con el
mensaje correcto y no puede reingresar ni por código ni desde el navegador, ni siquiera después
de una revancha. El anfitrión puede levantar el baneo desde la lista.

---

## §D. Reportar jugador y baneo global

**Datos** — nueva colección raíz `reportes/{reporteId}`, con id **determinista**
`{matchId}_{reportanteUid}_{reportadoUid}`: como `create` falla si el documento ya existe, eso
deduplica los reportes sin necesidad de contarlos.

Campos: `reportanteId`, `reportadoId`, `reportadoNombre`, `roomId`, `matchId`, `motivo`
(`toxicidad` | `trampa` | `spam` | `nombre_ofensivo` | `otro`), `detalle` opcional de hasta 140
caracteres, `creadaEn`.

**Reglas** (agregar a `firestore.rules`, junto a `bans`):

```
match /reportes/{reporteId} {
  // Nadie lee reportes desde la app: se revisan desde la consola de Firebase.
  allow read: if false;
  allow create: if signedIn()
    && hasOnly(request.resource.data, [
      'reportanteId', 'reportadoId', 'reportadoNombre',
      'roomId', 'matchId', 'motivo', 'detalle', 'creadaEn'
    ])
    && hasAll(request.resource.data, [
      'reportanteId', 'reportadoId', 'reportadoNombre',
      'roomId', 'matchId', 'motivo', 'creadaEn'
    ])
    && request.resource.data.reportanteId == request.auth.uid
    && request.resource.data.reportadoId != request.auth.uid
    && isString(request.resource.data.reportadoId, 1, 80)
    && isString(request.resource.data.reportadoNombre, 1, 18)
    && isString(request.resource.data.roomId, 1, 80)
    && isString(request.resource.data.matchId, 8, 80)
    && request.resource.data.motivo in ['toxicidad', 'trampa', 'spam', 'nombre_ofensivo', 'otro']
    && isOptionalString(request.resource.data, 'detalle', 140)
    && request.resource.data.creadaEn == request.time;
  allow update, delete: if false;
}
```

**Cliente**

- Botón **REPORTAR** en el mini-perfil de jugador (`PlayerProfileDialog`), sólo en online y
  sólo sobre otros jugadores.
- Diálogo con los cinco motivos y un campo opcional de detalle.
- Respuesta siempre igual, haya sido nuevo o duplicado: *"Gracias. Vamos a revisarlo."* No
  revelar si el reporte se registró o ya existía, y **nunca** avisarle al reportado.
- Al arrancar el online, leer `bans/{miUid}` (la regla permite leer el propio). Si existe,
  bloquear el acceso al online con el motivo y dejar el modo local intacto. El baneo global
  **no** debe bloquear el juego contra la IA.

**Criterio de aceptación:** reportar dos veces al mismo jugador en la misma partida deja un
solo documento; creando a mano `bans/{uid}` en la consola, ese celular deja de poder crear y
entrar a salas al reintentar, y sigue pudiendo jugar contra la IA.

---

## §E. Cerrar la lectura de los canales de RTDB

**Orden obligatorio: primero el cliente (E.1), después las reglas (E.2).** Al revés se rompe
el chat en producción, porque un listener de RTDB al que se le niega el permiso **se cancela y
no reintenta**.

### §E.1 — Enganchar los listeners recién con la presencia confirmada

Hoy [`LobbyActivity.kt:363-367`](../../../app/src/main/java/com/traidores/juego/LobbyActivity.kt:363)
llama a `startRealtimePresence()` y enseguida a `startLobbyChat()`, pero la publicación de
presencia es asíncrona: `onDisconnect().setValue()` y recién en su callback el
`setValue("conectado")`.

- Agregar a `RealtimeRoomPresence` un callback `onOnlinePublished: () -> Unit`, disparado
  cuando el `setValue(payload(STATE_CONNECTED))` **termina con éxito** (dentro de
  `armDisconnectThenPublishOnline`). Tiene que poder dispararse más de una vez (cada
  reconexión) y el consumidor debe ser idempotente.
- `LobbyActivity` y `GameplayMockActivity` enganchan los listeners de chat, emotes y silencios
  desde ese callback, no antes.

### §E.2 — Reglas

Recién con E.1 publicado y verificado, cambiar en `database.rules.json` el `.read` de `chat`,
`chat_traidores`, `chat_espectadores`, `chat_lobby` y `emotes`, de `"auth != null"` a:

```text
"auth != null && root.child('salas').child($roomId).child('presencia').child(auth.uid).exists()"
```

**Qué cierra:** que cualquier cuenta que conozca un `roomId` lea el plan de los asesinos de una
partida ajena. **Qué no cierra:** que un traidor vivo lea el canal de los muertos de su propia
sala. Ese secreto entre miembros necesita el backend de §F y no se resuelve acá.

**Criterio de aceptación:** entrar al lobby y al gameplay, cerrar la app y volver, poner el
celular en avión y sacarlo. El chat tiene que reaparecer en todos los casos sin quedar mudo.

---

## §F. Reparto por jugador — la fase grande

**Problema:** `partidaInicial` contiene el rol de todos y `partidas/{id}` es legible por
cualquier cuenta autenticada. Las reglas no filtran campos: no hay parche posible.

**Diseño propuesto (sin backend pago)**

- `partidaInicial` conserva sólo lo no secreto: `matchId`, `mapa`, `config`, orden y nombres.
- Los roles pasan a `partidas/{id}/repartos/{uid}` = `{ rolKey, orden, creadaEn }`, un
  documento por jugador, creados por el anfitrión en la misma transacción que hoy crea
  `partidaInicial` (el límite de Firestore es 500 escrituras por transacción; con 15 jugadores
  sobra).
- Regla: `allow read: if isSelf(uid) || isRoomActiveHost(partidaId);` y
  `allow create: if isRoomActiveHost(partidaId)`, `update, delete: if false`.

**Qué se gana:** se pasa de "cualquiera puede ver todos los roles" a "sólo el anfitrión puede".
El anfitrión sigue viendo todo porque es él quien resuelve la partida; eso es inherente al
modelo y sólo se elimina con un servidor autoritativo. Con §A aplicado, además, tomar el
anfitrionazgo requiere el APK real.

**Dos consecuencias que hay que resolver sí o sí:**

1. **La pantalla final.** Hoy la revelación de todos los roles sale de `partidaInicial`. Con el
   reparto privado hay que publicarla explícitamente: al declarar ganador, el anfitrión escribe
   los roles en `ultimoResultado.roles` (o en `estadoPartida`, junto al ganador). Sin esto, la
   pantalla de victoria queda sin datos.
2. **El reingreso.** `OnlineRoomRecovery` reconstruye la partida desde `partidaInicial`; ahora
   tiene que leer además `repartos/{miUid}`. Si falta, es error explícito y limpieza de la
   recuperación, igual que hoy: **nunca** caer al reparto local como respaldo.

También hay que revisar `revelarRolesAlMorir`: la revelación de un jugador eliminado tiene que
publicarla el anfitrión en `estadoPartida`, no derivarse del reparto completo en cada cliente.

**Criterio de aceptación:** durante una partida en curso, leyendo el documento de sala desde la
consola de Firebase no se puede saber el rol de ningún jugador. El reingreso, la revelación al
morir y la pantalla final siguen funcionando.

---

## §G. Higiene de release

**G.1 — Ofuscación.** En `app/build.gradle`, `release { minifyEnabled true }` con
`proguard-android-optimize.txt` y las reglas de consumo de Firebase. Verificar que la
serialización de `GameSession` (que viaja por `Intent`) y las clases que Firestore mapea por
reflexión sobrevivan; si aparecen problemas, `-keep` acotado, no desactivar el shrink entero.

**G.2 — Logs.** `OnlineDebugLog` escribe nombres, uids y transiciones de fase a Logcat también
en release. Silenciar `i()` y `w()` cuando la app no es debuggable (mismo `FLAG_DEBUGGABLE` de
§A); dejar `e()` siempre.

**G.3 — `google-services.json`.** Está commiteado y el `CLAUDE.md` afirma lo contrario.
Decisión de Ignacio: o se corrige la frase del `CLAUDE.md`, o se saca el archivo del control de
versiones (y entonces hay que documentar cómo lo obtiene alguien que clona el repo, porque sin
ese archivo el build falla). No es un secreto en el sentido clásico y la mitigación real es
App Check, pero el `CLAUDE.md` no puede seguir diciendo algo falso.

**G.4 — Borrado de cuenta (requisito de Play).** En Opciones, dentro del bloque de cuenta,
**BORRAR MI CUENTA** con confirmación por texto. Tiene que borrar `perfiles_publicos/{uid}`,
los datos locales de `TraidoresPrefs` y la cuenta de Firebase Auth
(`FirebaseUser.delete()`). El `publicId` no se recicla. Hace falta además una página web
pública que explique el mismo procedimiento.

---

## Resumen de esfuerzo

| Fase | Qué cierra | Tamaño | Depende de |
|------|-----------|--------|-----------|
| §A App Check | D1 — hace que todo lo demás valga | chico | — |
| §B `rolKey` fuera | B2 — la trampa más fácil | mínimo | — |
| §C.1 Silencio local | C1 — el caso más común | chico | — |
| §C.2 Silencio por votación | C1 — toxicidad en partida | mediano | §A |
| §C.3 Expulsar y banear | C1 — el reincidente | chico | reglas ya publicadas |
| §D Reportes y baneo global | C1, C4 | mediano | §C.3 |
| §E Lectura de RTDB | B3 parcial | chico, delicado | orden E.1 → E.2 |
| §F Reparto por jugador | B1 | grande | §A |
| §G Higiene de release | D2, D4, Play | mediano | — |
