# SPEC — Chat de traidores · Fase D: online (dejar preparado)

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). Última fase de `SPEC-chat-traidores.md`. Objetivo: llevar el "Plan de los Asesinos" al modo online **con la seguridad realista que hoy permite el proyecto**, y dejar documentado qué falta para secreto fuerte. **El online es humano↔humano (sin IA).**

## 0. Reencuadre de seguridad (leer primero — cambia la premisa original)

En el spec original dijimos "online al final, bloqueado por seguridad porque no hay Auth". Revisando el repo, la realidad es **mejor y más matizada**:

- **Ya hay reglas de Firestore con autenticación** (`firestore.rules`). Usan `signedIn()` = `request.auth != null` y exigen, para crear un mensaje de chat, `request.resource.data.actorId == request.auth.uid` (`firestore.rules:308-327`). O sea: **hay integridad de autoría** — un cliente solo puede escribir mensajes como sí mismo. (El doc `docs/firebase-online-schema.md:3` dice "todavia no hay Firebase Auth"; eso quedó **desactualizado** respecto a las reglas, que sí usan `request.auth.uid`. Vale confirmarlo, pero la app hoy escribe chat con éxito, lo que implica que hay auth —probablemente anónima— detrás.)
- **El agujero real no es el chat de traidores**: es que el documento de sala y `partidaInicial` (que contiene **todos los roles**) son `allow read: if true` (`firestore.rules:208`). Es decir, **cualquier cliente ya puede leer los roles de todos**. El secreto de roles en online **ya es honor-system hoy**. Un chat de traidores no agrega una clase nueva de vulnerabilidad: hereda exactamente la misma confianza que ya existe.

**Conclusión práctica**: no tiene sentido "bloquear" el chat de traidores por un estándar de secreto que el resto del online ya no cumple. Lo preparamos ahora con: (a) plomería cliente calcada del chat público (bajo riesgo), (b) reglas que **al menos** garantizan autoría e idealmente verifican que el que escribe/lee sea traidor de verdad, y (c) documentación honesta de que el secreto de lectura es tan fuerte como el de los roles (hoy: honor-system, cerrable solo con backend autoritativo). Nada de esto empeora la postura actual; varias partes la mejoran.

---

## 1. El online no tiene bots (implica: Fase C no aplica)

El preset online reparte solo roles para **jugadores humanos** (`docs/firebase-online-schema.md:128`: 1 asesino, 1 médico, 1 detective, resto aldeanos). La ruta online del director de bots ya cortocircuita (`onHumanMessage` sale temprano si `isOnlineGameplay()`, `GameplayChatController.kt:1401`). Por lo tanto:
- **`TraitorPlanBrain`/`nextTraitorLine`/el dripping de la Fase C NO corren en online.** El chat de traidores online es **solo humano↔humano**.
- Lo que sí viaja: los **mensajes que escriben los jugadores traidores** entre ellos. Y las **líneas de sistema** locales (header/objetivo), que se generan por cliente sin sincronizar (ver Parte 4).

Esto simplifica la Fase D: es plomería de un segundo canal de chat + reglas, sin cerebro.

---

## 2. Cliente: subcolección `chat_traidores`

Espejo casi exacto del chat público existente (`sendOnlineHumanChatMessage` `GameplayChatController.kt:1298`, `startOnlineChatListener` `:1355`, `applyOnlineChatEntries` `:1383`), pero en una **subcolección separada**.

### 2.1 Por qué subcolección separada (no un campo `canal`)
Un campo `canal` sobre la colección `chat` (que es `allow read: if true`) **no esconde nada**: cualquiera baja todos los docs y filtra en cliente. Una **subcolección aparte** (`partidas/{sala}/chat_traidores`) permite ponerle una regla de lectura estricta a **toda** la colección (Parte 3). Por eso: subcolección separada.

### 2.2 Escritura
Nueva `sendOnlineTraitorChatMessage(rawMessage)`, gemela de `sendOnlineHumanChatMessage` pero:
- Gate: `GameEngine.canHumanChatTraitor(session)` en vez de `canHumanChat` (mismo helper que ya usa el local, `GameEngine.kt:1217`).
- Escribe en `.collection("partidas").document(roomId).collection("chat_traidores")`.
- Mismo payload que el chat público (`actorId`, `speaker`, `mensaje`, `fase`, `ronda`, `isGod=false`, timestamps) **más** `canal: "traidores"` (para que la regla lo valide explícitamente).
- Reusar cooldown/anti-repetición (`ONLINE_CHAT_COOLDOWN_MS`, `lastOnlineChatMessage`) — o variables propias para no interferir con el chat público.

### 2.3 Lectura
Nuevo `startOnlineTraitorChatListener`, gemelo de `startOnlineChatListener`, escuchando `chat_traidores`. **Solo se arranca si `GameEngine.canSeeTraitorChat(session, human)`** (el humano es traidor vivo). Si el jugador no es traidor, **ni siquiera se suscribe** (y la regla de lectura lo rechazaría igual, ver Parte 3).

### 2.4 Ruteo al canal local
`applyOnlineTraitorChatEntries` mapea las entradas a `GameChatMessage(..., channel = ChatChannel.TRAIDORES)` y las mergea en `chatHistory` **preservando** los mensajes públicos (mismo patrón que `applyOnlineChatEntries` `:1383`, pero para el canal traidor; cuidar de no pisar el otro canal — filtrar por canal al reconstruir, análogo al filtro que ya existe). La UI (toggle PUEBLO/PLAN, piel roja) ya funciona por canal desde la Fase A: **no hay que tocar la UI**, solo alimentar el canal `TRAIDORES` desde Firestore en vez de desde el engine local.

### 2.5 Ciclo de vida
- Arrancar el listener traidor junto al público en `onCreate` **si** el humano es traidor (`GameplayChatController.kt:144` zona `startOnlineChatListener`).
- Removerlo en `onDestroy` (agregar a la limpieza existente de `onlineChatListener`).
- El botón de enviar del canal traidor rutea a `sendOnlineTraitorChatMessage` cuando `activeChatChannel() == TRAIDORES` y `isOnlineGameplay()` (hoy el envío online siempre va a `sendOnlineHumanChatMessage`, `GameplayChatController.kt:1288` zona — bifurcar por canal).

---

## 3. Reglas de Firestore para `chat_traidores`

Agregar en `firestore.rules`, dentro de `match /partidas/{partidaId}`, un bloque `match /chat_traidores/{mensajeId}`.

### 3.1 Escritura — autoría + forma (mínimo garantizado)
Calcado de la regla de `chat` (`firestore.rules:308-327`) más el campo `canal`:
```
allow create: if signedIn()
  && hasOnly(request.resource.data, ['actorId','speaker','mensaje','fase','ronda','isGod','canal','creadaEn','creadaEnLocal'])
  && request.resource.data.actorId == request.auth.uid
  && isString(request.resource.data.speaker, 1, 18)
  && isString(request.resource.data.mensaje, 1, 140)
  && request.resource.data.isGod == false
  && request.resource.data.canal == 'traidores'
  && ...(mismos checks de fase/ronda/timestamps que chat);
allow update, delete: if false;
```
Esto ya garantiza que nadie escriba en nombre de otro y que el payload sea válido.

### 3.2 Gate por rol vía `get()` (secreto real de escritura y lectura — recomendado si el shape lo permite)
Si `partidaInicial` expone el rol por `uidTemporal` de forma navegable por reglas (p. ej. `partidaInicial.jugadores[uid].rolKey` o un mapa `roles`), agregar una función:
```
function isTraitorInRoom(partidaId) {
  return signedIn()
    && ( get(/databases/$(database)/documents/partidas/$(partidaId)).data
           .partidaInicial.<ruta-al-rol-del-uid>(request.auth.uid) in ['asesino','mercenario','espia'] );
}
```
y usarla:
```
allow read: if isTraitorInRoom(partidaId);
allow create: if isTraitorInRoom(partidaId) && <checks de forma de 3.1>;
```
- **Lectura**: como TODOS los docs de `chat_traidores` requieren la misma condición (el lector es traidor), un listener sobre la subcolección pasa entero para un traidor y es rechazado entero para un no-traidor. Esto **sí cierra la lectura** del canal a los no-traidores a nivel servidor.
- **Costo**: un `get()` por operación (facturado + latencia). Aceptable para el volumen de un chat de sala.
- **Requisito**: que el reparto online guarde el rol por uid en `partidaInicial` de forma legible por reglas. **Verificar el shape real de `partidaInicial`** antes de escribir la función (si hoy no está así, o se ajusta el shape, o se pospone este gate y se queda con 3.1 + honor-system de lectura, igual que el resto del online).

### 3.3 Si el shape no da (fallback honesto)
Quedarse con 3.1 (autoría + forma) y **lectura honor-system** — idéntico al secreto de roles que ya rige hoy (`partidaInicial` world-readable). Documentarlo (Parte 5). No es un retroceso respecto al estado actual; es paridad.

---

## 4. Mensajes de sistema locales (no se sincronizan)

Las líneas de "Plan" (header nocturno "los malos son X e Y", `withTraitorNightHeader` `GameEngine.kt:2037`; y "el plan cae sobre X", `withTraitorTargetMessage` `:2049`) son **derivadas del estado local** y se generan por cliente. En online:
- El **header** ("los malos son…") lo puede generar cada cliente traidor localmente, porque cada traidor conoce a su equipo (los roles están en `partidaInicial`, que el cliente ya reconstruye). Es correcto y esperado que los traidores se conozcan entre sí (como en el Mafia real). **No** se escribe a Firestore — es local, así no ocupa cuota ni se filtra por la subcolección.
- La línea de **objetivo** ("el plan cae sobre X") en online **no aplica igual que en local**: la elige el humano al accionar, no un bot. Se puede mostrar localmente al traidor que ejecuta la acción ("marcaste a X") como línea de sistema local, o simplemente omitirla. Recomendado: omitir en online para no dar por hecho el objetivo del equipo (en online cada asesino/mercenario decide su acción; el resultado lo publica el host).
- Regla general: en online, **solo los mensajes escritos por humanos viajan por `chat_traidores`**; todo lo de sistema es local y no sincronizado.

---

## 5. Qué falta para secreto fuerte (fuera de alcance — documentar)

Actualizar `docs/firebase-online-schema.md` (sección "Limites actuales"/"Pendiente para produccion") con:
- El chat de traidores online tiene **integridad de autoría** (reglas) y, si se aplica 3.2, **secreto de lectura server-side**. Pero el secreto **general de roles** en online sigue siendo honor-system mientras `partidaInicial` sea `allow read: if true`.
- Cierre real requiere lo ya listado como pendiente (`schema:214-219`): **backend autoritativo** (Cloud Functions) que reparta y guarde roles **sin** exponerlos world-readable, o payloads de rol cifrados por jugador. Mientras eso no exista, un cliente modificado puede ver roles (y por ende inferir el equipo traidor) **con o sin** este chat.
- Agregar la ruta `partidas/{partidaId}/chat_traidores/{mensajeId}` a la doc de rutas (`schema:195` zona), con sus campos (incluye `canal`).

---

## 6. Verificación manual (Android Studio + Firestore Console + 2 dispositivos)

- **Dos traidores en una sala online**: ambos ven el canal rojo "Plan de los Asesinos", se escriben entre ellos y los mensajes llegan al otro dispositivo. En Firestore Console aparecen bajo `partidas/{sala}/chat_traidores`.
- **Un jugador del pueblo en la misma sala**: no ve el canal traidor (el listener no arranca; y con 3.2, la lectura la rechaza el servidor). El chat público sigue igual.
- **Autoría**: intentar escribir en `chat_traidores` con un `actorId` distinto al propio (desde consola/cliente modificado) → la regla lo rechaza.
- **Con 3.2 aplicado**: intentar leer `chat_traidores` siendo del pueblo (desde consola con otro uid) → rechazado.
- **Sin traidores vivos**: el canal no se ofrece (mismo gate `isTraitorChatUnlocked` que el local).
- **Regresión**: el chat público online no cambió; el reingreso/reconstrucción de partida sigue funcionando.

## Orden de entrega
1. **Parte 2** (cliente: subcolección, listener, envío, ruteo al canal) — el grueso, calcado del chat público.
2. **Parte 3.1** (reglas: autoría + forma) — cierra escritura.
3. **Parte 3.2** (reglas: gate por rol vía `get()`) — **solo si** el shape de `partidaInicial` lo permite; verificar primero. Si no, 3.3 + doc.
4. **Parte 4** (sistema local) y **Parte 5** (documentación honesta).
