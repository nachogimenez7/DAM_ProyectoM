# SPEC — Chat de las Ánimas: canal de espectadores online

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **Feature nueva** acordada con el usuario: un tercer canal de chat, **solo online y solo para jugadores eliminados**, para que los muertos puedan hablar entre ellos mientras siguen mirando la partida. Hoy el eliminado queda en "solo lectura" total; esto le devuelve una voz sin contaminar la partida.
>
> **Decisiones tomadas con el usuario:**
> 1. **Solo online.** En local (vs IA) no existe: el muerto local sigue exactamente como hoy (solo lectura). Nada de bots hablando con muertos.
> 2. **Escritura siempre**: los muertos escriben de día y de noche, en cualquier fase (no influyen en nada y la noche es cuando más tienen para comentar). Se cierra solo cuando hay ganador.
> 3. **Al morir, el chat salta solo al canal de las Ánimas** con un aviso claro; con el botón de canal vuelve a PUEBLO (solo lectura) cuando quiera seguir el debate.
> 4. **Estética "Ánimas"**: azul espectral, paleta programática como la roja de traidores. **Sin assets nuevos** (nada de PNG/pergaminos dedicados en esta fase).
> 5. Los vivos **nunca** ven el canal ni el botón. Seguridad de lectura honor-system, igual que `chat_traidores` (documentar, no bloquear).

## Contexto técnico (lo que ya existe)

- **Canales**: `enum class ChatChannel { PUBLICO, TRAIDORES }` (`GameModels.kt:359`) y `GameChatMessage(speaker, message, isGod, channel)` (`GameModels.kt:364`). Todo vive en `GameSession.chatHistory`, cap `GameplayFeedMessages.MAX_FEED_MESSAGES = 60` **por canal**.
- **Controller**: `GameplayChatController.kt` maneja los dos canales con el mismo panel y dos "pieles". Piezas espejo que vamos a replicar:
  - Listener RTDB público: `startOnlineChatListener` (`GameplayChatController.kt:1500`) + `applyOnlineChatEntries` (`:1538`).
  - Listener RTDB traidor (suscripción condicional por rol, con reintento en `onSessionUpdated` porque el rol llega tarde en reconstrucción): `startOnlineTraitorChatListener` (`:1559`) + `applyOnlineTraitorChatEntries` (`:1600`). El comentario de `:179-184` explica el patrón de reintento; la muerte del humano también "llega tarde", así que aplica igual.
  - Envío online con cooldown y dedupe: `sendOnlineTraitorChatMessage` (`:1441`); dispatch en `sendHumanChatMessage` (`:1341`).
  - Selección de canal: `activeChatChannel` (`:2086`), `canHumanChatInChannel` (`:2097`), `canToggleTraitorChannel` (`:2107`), botón `btnChatFeedFilter` con doble uso (canal o filtro SUCESOS) en `toggleFeedFilter` (`:1075`) / `renderFeedFilterButton` (`:1094`).
  - Piel por canal: `renderChatBackgrounds` (`:1972`) despacha a `renderTraitorChatBackgrounds` (`:2017`); títulos en `renderChatTitle` (`:799`); chip en `renderTraitorHeaderChip` (`:828`); burbujas/feed en `addChatBubble` (`:1192`), `createAmbientFeedRow` (`:701`), `createAmbientPlaceholderRow` (`:676`), `eventPresentationFor` (`:861`), `createDayDivider` (`:942`).
  - Hints y bloqueos: `chatInputHint` (`:1922`, hoy "Eliminado: solo lectura") y `blockedChatMessage` (`:1954`, hoy "Estás eliminado. Puedes mirar el chat, pero no escribir.").
  - Constantes: nodos RTDB en `:2174-2175`, cooldown `ONLINE_CHAT_COOLDOWN_MS = 1200`, `ONLINE_CHAT_MAX_MESSAGES = 40`, persistencia de canal en `onSaveInstanceState` (`:198-201`, clave `STATE_CHAT_CHANNEL`).
- **Gates del engine**: `canHumanChat` (`GameEngine.kt:1198`), `canSeeTraitorChat` (`:1222`, exige `alive` — o sea un traidor muerto **pierde** el plan; eso queda así), `canHumanChatTraitor` (`:1232`).
- **RTDB**: nodos `salas/{roomId}/chat|chat_traidores|chat_lobby|presencia` con reglas en `database.rules.json` (bloque `chat_traidores` en líneas 25-42 es el molde). Autoría garantizada (`actorId === auth.uid`), lectura honor-system.
- **Limpieza en revancha**: `LobbyActivity.kt:2601-2612` borra `chat_lobby` + `chat` + `chat_traidores` de RTDB (constantes en `:3817-3818`); el teardown total de sala (`:950-956`) borra el nodo completo, así que no necesita cambios.
- **Docs**: `docs/firebase-online-schema.md` — árbol RTDB (~`:317-323`), regla de revancha "los tres chats" (`:224-225`), sección de seguridad del canal traidor (`:306-311`).

---

## Parte 1 — Modelo y gates

### 1.1 Canal nuevo
```kotlin
// GameModels.kt:359
enum class ChatChannel : Serializable { PUBLICO, TRAIDORES, ESPECTADORES }
```
El default `PUBLICO` de `GameChatMessage` mantiene compatible todo lo existente.

### 1.2 Gates en GameEngine (junto a los de traidor, `GameEngine.kt:1222-1235`)
```kotlin
fun canSeeSpectatorChat(session: GameSession, player: GamePlayer): Boolean =
    !player.alive

fun isSpectatorChatWritable(session: GameSession): Boolean =
    session.winner.isBlank()   // siempre: día, noche, votación, desempate (decisión 2)

fun canHumanChatSpectator(session: GameSession): Boolean =
    canSeeSpectatorChat(session, humanPlayer(session)) && isSpectatorChatWritable(session)
```
- El "solo online" **no** va en el engine (que es agnóstico de modo): lo gatea el controller con `host.isOnlineGameplay()`, igual que hace el canal traidor online.
- `muted` no aplica: un muerto silenciado no existe como concepto; la muerte pisa todo.
- **No tocar** `canHumanChat` ni `canParticipateInChat`: el público sigue siendo solo-lectura para muertos.

---

## Parte 2 — Controller: listener, envío y merge

Todo en `GameplayChatController.kt`, espejo del canal traidor.

### 2.1 Constantes y estado
- `RTDB_SPECTATOR_CHAT_NODE = "chat_espectadores"` (junto a `:2174-2175`).
- `onlineSpectatorChatQuery/Listener`, `lastOnlineSpectatorChatSentAtMs`, `lastOnlineSpectatorChatMessage` (espejo de `:84-87`). Limpiar en `onDestroy` (`:203-214`).

### 2.2 Listener condicional por muerte
`startOnlineSpectatorChatListener()`: copiar `startOnlineTraitorChatListener` (`:1559`) cambiando el guard de rol por:
```kotlin
if (GameEngine.humanPlayer(host.currentSession).alive) return
```
Llamarlo desde `onCreate` (`:172-175`, cubre el reingreso de un jugador ya muerto) **y** desde `onSessionUpdated` (`:178-184`, cubre morir a mitad de partida; se auto-protege contra doble suscripción como el traidor). Mismo `limitToLast(ONLINE_CHAT_MAX_MESSAGES)` y mismo filtro por `matchId`.

**Nota**: una vez suscripto, no desuscribir nunca (un muerto no revive). Y un vivo jamás se suscribe → jamás descarga esos mensajes.

### 2.3 Merge de tres canales (refactor chico y justificado)
`applyOnlineChatEntries` (`:1538`) y `applyOnlineTraitorChatEntries` (`:1600`) hoy reconstruyen `chatHistory` preservando "el otro" canal. Con tres canales eso se vuelve frágil (el merge público pisaría los mensajes de espectador). Extraer un helper único:
```kotlin
private fun mergeOnlineChannelMessages(
    channel: ChatChannel,
    onlineMessages: List<GameChatMessage>,
    preserveGodOfChannel: Boolean
) {
    val session = host.currentSession
    val others = session.chatHistory.filter { it.channel != channel }
    val kept = if (preserveGodOfChannel) {
        session.chatHistory.filter { it.channel == channel && it.isGod }
    } else emptyList()
    host.currentSession = session.copy(
        chatHistory = others + (kept + onlineMessages).takeLast(GameplayFeedMessages.MAX_FEED_MESSAGES)
    )
    // + updateUnreadChatCount()/renderChatPanel()/renderChatBadge() como hoy
}
```
Los tres `applyOnline*Entries` llaman a esto (público con `preserveGodOfChannel = true` para las líneas de Dios derivadas de `estadoPartida`; traidor igual que hoy; espectador `false` — no hay líneas de sistema v1). El orden entre canales no importa: `activeChannelMessages` (`:2093`) filtra por canal antes de renderizar. **Cuidado**: mantener el cap de 60 **por canal**, no sobre la mezcla.

### 2.4 Envío
`sendOnlineSpectatorChatMessage(rawMessage)`: copiar `sendOnlineTraitorChatMessage` (`:1441`) con:
- guard `GameEngine.canHumanChatSpectator(session)`,
- nodo `RTDB_SPECTATOR_CHAT_NODE`,
- `"canal" to "espectadores"`,
- logs `spectator_chat_send_success/failure`.

En `sendHumanChatMessage` (`:1348-1355`), el dispatch online pasa a un `when(activeChatChannel())` con las tres ramas. **Ojo con el lock de transición** (`:1343`): se mantiene tal cual (durante la animación día/noche tampoco escriben los muertos; son 2-3 segundos).

### 2.5 Selección de canal y salto al morir
En `activeChatChannel()` (`:2086`):
```kotlin
private fun activeChatChannel(): ChatChannel {
    val session = host.currentSession
    if (canUseSpectatorChatUi(session)) {
        // Muerto online: solo PUEBLO (lectura) o ESPECTADORES; un TRAIDORES guardado se coacciona.
        return if (selectedChatChannel == ChatChannel.PUBLICO) ChatChannel.PUBLICO
        else ChatChannel.ESPECTADORES
    }
    if (!canUseTraitorChatUi(session)) return ChatChannel.PUBLICO
    if (GameplayTableUi.isNightPhase(session.phase)) return ChatChannel.TRAIDORES
    return selectedChatChannel
}

private fun canUseSpectatorChatUi(session: GameSession): Boolean {
    return host.isOnlineGameplay() &&
        GameEngine.canSeeSpectatorChat(session, GameEngine.humanPlayer(session))
}
```
- El forzado nocturno a TRAIDORES (`:2089`) queda **después** del branch de muerto: un traidor muerto ya no ve el plan (consistente con `canSeeTraitorChat`, que exige `alive`).
- **Salto automático al morir (decisión 3)**: trackear `wasHumanAlive` en el controller. En `onSessionUpdated`, si pasó de vivo a muerto (y `isOnlineGameplay`):
  - `selectedChatChannel = ChatChannel.ESPECTADORES`, `showOnlyEvents = false`,
  - `host.showToast("Caíste. Ahora hablás en el canal de las Ánimas.")`,
  - re-render (los `render*` ya se llaman al final de `onSessionUpdated`).
  - Solo la **primera** vez (flag en memoria; no hace falta persistirlo: tras rotación, `STATE_CHAT_CHANNEL` ya restaura ESPECTADORES y el flag se puede re-derivar inicializando `wasHumanAlive` con el estado actual en `onCreate`).
- **Reingreso ya muerto**: en `onCreate` (`:140-144`), si no hay `savedState` y el humano está muerto en online, el default de `selectedChatChannel` es `ESPECTADORES` (sin toast).

### 2.6 Botón de canal y permisos
- `canHumanChatInChannel` (`:2097`): rama `ESPECTADORES -> host.isOnlineGameplay() && GameEngine.canHumanChatSpectator(session)`.
- Toggle (`toggleFeedFilter :1075` / `renderFeedFilterButton :1094`): si `canUseSpectatorChatUi(session)`, el botón alterna PUEBLO ↔ ESPECTADORES:
  - en canal ÁNIMAS el botón dice `PUEBLO` ("Volver al chat del pueblo"),
  - en canal PUEBLO dice `ÁNIMAS` ("Hablar con los muertos"), estilo azul espectral (mismo patrón que el par rojo de `:1106-1119`).
  - El filtro TODO/SUCESOS queda superseded para muertos, igual que ya pasa con los traidores vivos (tradeoff aceptado; resetear `showOnlyEvents = false` al morir, ver 2.5).
- `typingSpeakerBelongsToChannel` (`:1066`): rama `ESPECTADORES -> false` (no hay bots online; el `when` debe quedar exhaustivo).
- `canScheduledBotSpeak` (`:1892`): rama `ESPECTADORES -> false` (defensivo; el director nunca corre online).
- Hints (`chatInputHint :1922`):
  - muerto mirando PUEBLO → `"Eliminado: hablá en el canal de las Ánimas"` (reemplaza "Eliminado: solo lectura" **solo** cuando `canUseSpectatorChatUi`; en local queda el texto de hoy),
  - canal ÁNIMAS escribible → `"Susurrar entre las ánimas..."`; hint del feed colapsado (`:657-661`): `"Toca para susurrar"`,
  - con ganador → "Solo lectura" como hoy.
- `blockedChatMessage` (`:1954`): la rama `!human.alive` distingue: si está parado en PUEBLO y existe el canal → `"Estás eliminado. Hablá en el canal de las Ánimas."`; en local, texto actual.

---

## Parte 3 — Piel "Ánimas" (azul espectral, sin assets)

Espejo estructural de la piel roja, despachado por canal. Convertir los branches binarios `== TRAIDORES` de burbujas/feed/títulos en `when(channel)` exhaustivos.

### 3.1 Colores a `colors.xml` (no hardcodear en Kotlin)
```xml
<color name="espectro_bg">#0B1220</color>          <!-- fondo profundo azul-negro -->
<color name="espectro_panel">#16233A</color>       <!-- paneles/header -->
<color name="espectro_blue">#3E5C8C</color>        <!-- bordes/strokes -->
<color name="espectro_blue_bright">#7FA3D8</color> <!-- nombres/acentos -->
<color name="espectro_text">#C7D4E8</color>        <!-- cuerpo de mensaje -->
<color name="espectro_muted">#6B7E9C</color>       <!-- hints/placeholder -->
```
Referencia de familia: los tonos NIGHT ya existentes (`#25334F`/`#6B86B8`/`#B7C7E8` en `eventPresentationFor :897-903`) — la piel debe sentirse de esa misma noche, un paso más fantasmal.

### 3.2 Fondos
`renderChatBackgrounds` (`:1972`): si canal `ESPECTADORES` → `renderSpectatorChatBackgrounds()`, copia de `renderTraitorChatBackgrounds` (`:2017`) mapeando rojo→azul (`traitor_red→espectro_blue`, `traitor_red_bright→espectro_blue_bright`, `traitor_panel→espectro_panel`, `traitor_bg→espectro_bg`, etc.). `writable` sale de `canHumanChatSpectator`.
- **Marcos**: no hay `bg_chat_frame_espectro_*` y no se crean assets → `chatPanel.foreground = null` y `chatAmbientFeed.foreground = null` en esta piel (agregar la variante nula a `applyChatFrameForegrounds :2078` o setear directo). Si al usuario le queda muy desnudo, un marco dedicado va como asset futuro.

### 3.3 Título, chip y textos del canal
- `renderChatTitle` (`:799`): canal ESPECTADORES → ambos títulos `"CHAT DE LAS ÁNIMAS"`, color `espectro_blue_bright`, mismo tamaño que el traidor (13sp, maxLines 1).
- Chip (espejo de `renderTraitorHeaderChip :828`): cantidad de muertos de la sesión — `"N ÁNIMAS"` (o `"SOS LA PRIMERA"` si el humano es el único muerto), fill `espectro_panel`, stroke `espectro_blue`.
- Placeholders: feed vacío → `"Las ánimas aún no susurran"` (`createAmbientPlaceholderRow :676` y rama vacía de `renderChatMessages :989-1011`).
- Burbujas (`addChatBubble :1192`): propia = fill `espectro_blue` / stroke `espectro_blue_bright`; ajena = fill `espectro_panel`; texto `espectro_text`; nombres en `espectro_blue_bright` (los muertos no conservan su color de jugador: acá son ánimas, uniforme como el canal rojo).
- `eventPresentationFor` (`:861`) y `createDayDivider` (`:942`): en este canal no se inyectan eventos de Dios v1, pero dejar un default azul coherente por si un `ChronicleEntry` no-PLAYER se cuela (icono `"~"`, label `"MÁS ALLÁ"`).

### 3.4 Adorno opcional (barato, si sobra tiempo)
Línea de sistema **local** (no RTDB, derivada del estado, patrón "eventos de Dios derivados") al entrar al canal: `"— bienvenido al más allá, {nombre} —"` una sola vez por muerte. Marcar como opcional; no bloquea la entrega.

---

## Parte 4 — RTDB: reglas, limpieza y docs

### 4.1 `database.rules.json`
Clonar el bloque `chat_traidores` (líneas 25-42) como `chat_espectadores`, cambiando solo:
```json
"canal": { ".validate": "newData.val() === 'espectadores'" }
```
Mismos campos requeridos (`matchId, actorId, speaker, mensaje, fase, ronda, isGod, canal, ts`), misma autoría `actorId === auth.uid`, `isGod === false`, mensaje 1-140.
**Recordatorio para el usuario (ponerlo en el PR/mensaje final): publicar reglas antes de probar** — `firebase deploy --only database`. Sin eso, todo envío al canal va a fallar con "Firebase rechazó la acción".

### 4.2 Limpieza en revancha
`LobbyActivity.kt:2601-2612`: agregar `RTDB_SPECTATOR_CHAT_NODE to null` al mapa de `updateChildren` + la constante junto a `:3817-3818`. El teardown completo (`:950-956`) ya lo cubre por borrar `salas/{roomId}` entero.

### 4.3 `docs/firebase-online-schema.md`
- Árbol RTDB (~`:317-323`): agregar `/chat_espectadores/{pushId}`.
- Regla de revancha (`:224-225`): "los tres chats" → **los cuatro chats** (`chat`, `chat_traidores`, `chat_lobby`, `chat_espectadores`).
- Nueva subsección junto a la de seguridad del canal traidor (`:306-311`): mismas garantías (autoría por `auth.uid`) y misma limitación honor-system — un cliente modificado y autenticado podría leer el canal de muertos estando vivo; los muertos conocen secretos ("me mató X"), así que cerrar la lectura de verdad requiere el mismo backend autoritativo pendiente que `chat_traidores`. Nivel de confianza aceptado explícitamente para esta etapa.
- Nota de producto: los eliminados dejan de ser "solo lectura": leen `chat` y escriben en `chat_espectadores`.

---

## Parte 5 — Verificación manual (Android Studio + 2-3 celulares/emuladores)

Sala `modoPrueba` de 3 (asesino + médico + comisario) para forzar una muerte rápida:
1. **Morir online** (víctima de noche o expulsado): toast "…canal de las Ánimas", el chat queda en el canal azul, título "CHAT DE LAS ÁNIMAS", y se puede escribir **de día y de noche**. El mensaje aparece en RTDB `salas/{roomId}/chat_espectadores` con `canal: "espectadores"`.
2. **Vivo**: nunca ve el canal, ni el botón ÁNIMAS, ni descarga el nodo (verificar que no hay listener en Logcat `spectator_chat_listener_start` de un vivo). Su botón sigue siendo TODO/SUCESOS (o PUEBLO/PLAN si es traidor).
3. **Dos muertos** se leen entre sí; el chip dice "2 ÁNIMAS".
4. Muerto alterna **ÁNIMAS ↔ PUEBLO**: en PUEBLO lee el debate (input bloqueado con el hint nuevo), vuelve y escribe.
5. **Traidor muerto**: pierde PLAN, gana ÁNIMAS (y no puede escribir más en `chat_traidores`).
6. **Reingreso estando muerto** (cerrar app y "Reingresar"): entra directo con el canal ÁNIMAS activo, historial visible (limitToLast 40), sin toast duplicado.
7. **Rotación** con el panel abierto en ÁNIMAS: mantiene canal y mensajes (STATE_CHAT_CHANNEL).
8. **Fin de partida**: con `winner` seteado el composer se bloquea; en la **revancha** el nodo `chat_espectadores` desaparece de RTDB y la partida nueva arranca limpia (y mensajes de un `matchId` viejo no se muestran).
9. **Local vs IA**: morir como humano deja todo exactamente como hoy ("Eliminado: solo lectura", sin canal nuevo).
10. Chat público y traidor siguen funcionando igual que antes (regresión del merge de 2.3: mensajes de Dios del público no se pierden al llegar un snapshot de espectadores).

## Orden de entrega
1. Parte 1 (modelo + gates) y Parte 2 (listener/envío/merge) — el corazón.
2. Parte 3 (piel azul) — puede validarse visualmente después.
3. Parte 4 (reglas + limpieza + docs) — **las reglas van antes de cualquier prueba en dispositivo**.
4. Parte 5 — checklist con el usuario.
