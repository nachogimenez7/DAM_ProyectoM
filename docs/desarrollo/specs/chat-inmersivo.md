# Spec — Chat Inmersivo Central (Ronda 2: extracción + decoración)

> Handoff Claude (diseño/review) → Codex (implementación). El código es la fuente de verdad. Codex arranca en frío: leer esto completo.

## Estado actual (lo ya hecho, INLINE)
Codex ya implementó la parte **visual** del chat directamente dentro de `GameplayMockActivity` (sin extraer):
- Feed ambiental central (`chatAmbientFeed`/`chatAmbientMessages`/`chatAmbientHint`) con últimos 3 mensajes, "Toca para hablar", fade-in. Vive dentro de `centerColumn` con `layout_marginTop=128dp` / `layout_marginBottom=126dp` (no tapa cartas laterales ni paneles).
- Panel expandido reubicado al **centro** (`CENTER_IN_PARENT`, ancho ≈ `centerColumn`).
- `PlayerChatColor.kt` (resolvedor de color por orador) aplicado a feed y burbujas.
- Drawable `bg_chat_ambient_feed.xml`.

**Problema:** la lógica del chat sigue **dentro del monolito** `GameplayMockActivity` (creció ~250 líneas). La extracción (prioridad #1 del proyecto) NO se hizo. Esta ronda lo corrige.

## Regla de proceso (importante)
- **Mantener cada diff acotado a la tarea.** No mezclar cambios no pedidos. En la ronda anterior se colaron cambios fuera de alcance (botón de acción flotante infinito, pulso del badge lateral, avatares ocultos en local, colores de tono KILL/SILENCE). NO agregar más de eso. Esos cambios previos se **preservan tal cual** durante la extracción (la extracción NO debe cambiar comportamiento); se revisarán por separado.
- Refactor pequeño y justificado; no reescribir de cero. El usuario valida compilación/apariencia en Android Studio.

---

## TAREA 1 (PRIORITARIA) — Extracción a `GameplayChatController`

Mover **toda** la lógica de chat (incluida la nueva del feed ambiental y el uso de `PlayerChatColor`) fuera de `GameplayMockActivity` a un controlador propio, **sin cambiar el comportamiento ni el layout**. Es el primer corte real del monolito; deja el patrón para extraer después otras piezas.

### Qué mover al controller
- **Estado:** `isChatOpen`, `isChatKeyboardCompact`, `chatKeyboardBottomInset`, `newChatMessagesWhileTyping`, `lastSeenChatCount`, `restoreTieVoteAfterChat`, `unreadChatCount`, `onlineChatListener`, `lastOnlineChatSentAtMs`, `lastOnlineChatMessage`, `pendingBotChatRunnables`.
- **Vistas:** `btnToggleChat`, `btnSendChat`, `btnCloseChat`, `chatAmbientFeed`, `chatAmbientHint`, `chatAmbientMessages`, `chatCharacterCount`, `chatComposer`, `chatHeader`, `chatInput`, `chatMessagesContainer`, `chatMessagesScroll`, `chatNewMessages`, `chatPanel`, `chatRoleChip`, `chatStatusRow`, `chatUnreadBadge`.
- **Métodos:** `toggleChatPanel`, `closeChatPanel`, `renderChatPanelVisibility`, `renderChatPanel`, `renderAmbientChatFeed`, `createAmbientChatRow`, `renderChatMessages`, `addChatBubble`, `configureChatPanelLayout`, `setChatKeyboardState`, `applyChatPanelDimensions`, `updateUnreadChatCount`, `renderChatCharacterCount`, `renderNewChatMessageNotice`, `acknowledgeNewChatMessages`, `renderChatBadge`, `updateChatToggleContentDescription`, `sendHumanChatMessage`, `sendOnlineHumanChatMessage`, `startOnlineChatListener`, `cancelPendingBotChat`, `clearChatComposerAfterSend`, `compactRoleChipText`, `chatInputHint`.

### Límite de responsabilidad
- El controller maneja **UI + estado de UI + envío** del chat (puede usar `GameEngine` y `PlayerChatColor` directo, son objetos sin estado).
- Lo que dependa del Activity (sesión actual, IA de bots, handles de Firestore/IDs online, toasts) entra por `ChatHost`.

### API sugerida
```kotlin
class GameplayChatController(
    private val host: ChatHost,
    root: View,                 // raíz donde están las vistas de chat (gameplay)
) {
    fun onCreate(savedState: Bundle?)
    fun onSessionUpdated()                 // re-render feed/panel/badge según host.currentSession
    fun onBackPressed(): Boolean           // true si consumió (estaba expandido)
    fun onKeyboardInsets(imeVisible: Boolean, bottomInset: Int)
    fun openExpanded()                     // usado por btnToggleChat, feed ambiental y chat de desempate
    fun onSaveInstanceState(out: Bundle)
    fun onDestroy()                        // cancela runnables + remove listener
}

interface ChatHost {
    val currentSession: GameSession
    fun isOnlineGameplay(): Boolean
    fun dp(value: Int): Int
    fun showToast(message: String)
    fun applyLocalSession(updated: GameSession)   // resultado de GameEngine.addHumanChatMessage local
    fun scheduleBotReactions(humanMessage: String)
    // Online (el Activity tiene los handles): el controller arma el payload y delega el envío/escucha.
    fun onlineChatContext(): OnlineChatContext?   // partidaId, playerId, firestore, etc. o null si no es online
}
```
- `btnTieVoteChat` queda en el Activity pero su listener llama `chatController.openExpanded()` (reemplaza `openChatFromTieVote`; conservar `restoreTieVoteAfterChat` dentro del controller).
- El Activity implementa `ChatHost`, instancia el controller en `onCreate`, y **delega**: en cada update de sesión llama `onSessionUpdated()`; en back llama `onBackPressed()`; reenvía insets de teclado; save/restore; destroy.

### Verificación (manual en Android Studio) — sin regresiones
- Abrir/cerrar chat; feed ambiental visible al cerrar.
- Enviar mensaje local (reacción de bots) y online (cooldown/anti-duplicado).
- No-leídos/badge (solo cuenta lo que NO se ve en el feed).
- **Chat expandido CON teclado abierto** (centrado, ya no anclado abajo) — riesgo principal.
- Chat de desempate abre el chat.
- Eliminado/silenciado: hint del feed dice el motivo, no "Toca para hablar".
- Rotación: estado preservado.

**➡️ Punto de revisión de Claude antes de seguir con Tareas 2-4.**

---

## TAREA 2 — Cuadro decorado por mapa (PROGRAMÁTICO, sin PNG)
Fondo del chat (feed ambiental y panel expandido) decorado según `session.mapKey`, con drawables de Android (shapes/gradientes/bordes). Translúcido: el mapa se ve atenuado detrás, pero el texto se lee bien. Sutil y chico.

- **Grecia (`grecia`):** panel claro tipo **mármol** (gris/blanco frío) con borde fino dorado.
- **Medieval (`medieval`):** panel **madera** oscura, borde, y un acento de **sello de cera roja** (un círculo rojo pequeño en una esquina, hecho con shape).
- **Pampa (`pampa`):** panel **cuero/marrón cálido**.

Implementación sugerida: 3 drawables (`bg_chat_box_grecia/medieval/pampa.xml`) + selección por mapa en el controller (helper `chatBoxBackgroundFor(mapKey)`). Mantener contraste suficiente para legibilidad.

## TAREA 3 — Color de identidad único (chat + cartas)
- Aplicar `PlayerChatColor.colorFor(...)` también al **nombre debajo de cada carta lateral** (`holder.name`), igual que en el chat. Que `PlayerChatColor` sea la **única fuente de color de identidad** del jugador (chat + cartas; a futuro reveals).
- Cuidar legibilidad del nombre sobre la carta (sombra/contraste si hace falta).

## TAREA 4 — Agrandar el feed central + texto
- Agrandar un poco el área del feed ambiental y el tamaño de fuente para que sea más legible. Integrar con `appliedGameplayTextScale` (ya se usa) — no romper el setting de tamaño de texto. Combina con Tarea 2 (texto sobre el cuadro decorado se lee mejor).
- Estado inicial del feed (sin mensajes aún): texto sutil tipo "El pueblo aún no habló" en vez de vacío.

---

## Futuro (NO ahora)
- **Unir las dos ventanas de arriba** (la de fase "DIA 1…" como **consejos/tips contextuales** + EVENTOS como **historial**) en un componente tipo "cronista/narrador". Es la **próxima extracción del monolito** (event log controller). Posible tie-in: que muertes/expulsiones de "Dios" aparezcan también en el feed con estilo especial.
- **Reveals sincronizados** entre teléfonos (online).

## Iteración B — selector de color en lobby
- `GamePlayer.color` en el modelo, serializable; UI en el lobby con manejo de colisiones (paleta curada; tomados deshabilitados en online); sync por Firestore (doc `jugadores`, 1 escritura por jugador por partida); default opcional del perfil. `PlayerChatColor` pasa a preferir el color elegido y cae al derivado.
- Nota: FCM (Cloud Messaging) es push, NO reemplaza lecturas de Firestore ni es transporte de chat; el costo/transporte online se diseña al abordar online.

## Documentación a actualizar al cerrar
- `docs/general/05-estructura-proyecto.md` (nuevo `GameplayChatController.kt`, `PlayerChatColor.kt`).
- `docs/general/03-arquitectura.md` (patrón controller / primer corte del monolito).
- `docs/general/07-flujo-funcionamiento.md` (chat ambiental + expandido).
- `docs/desarrollo/decisiones-arquitectura.md` (ADR: extracción de chat + chat ambiental + color por jugador + decoración por mapa).
- `docs/desarrollo/backlog.md` (avance D1).
