# SPEC — "Plan de los Asesinos": chat secreto de traidores

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **Feature nueva** acordada con el usuario. Objetivo: un segundo canal de chat, **solo para traidores y solo de noche**, con estética roja "malvada", donde de a poco (con la IA) se planea la partida. Se estrena en **local**; el online queda como fase final, condicionado a que exista seguridad server-side.
>
> **Decisiones tomadas con el usuario:**
> 1. **Nombre:** "Plan de los Asesinos".
> 2. **Ventana:** abierto **toda la noche** (desde que cae la noche hasta el amanecer), no solo en el turno de acción del traidor.
> 3. **1 solo traidor** (partidas 4-6): se muestra igual, con aviso "sos el único que mueve las fichas esta noche". Sirve de bloc de plan y deja el flujo listo para la IA.
> 4. **De día:** el canal queda visible pero **bloqueado (solo lectura)**, como recap del plan; se reactiva la noche siguiente.
> 5. **Estética:** bien sangrienta y decorada (rojo `accent_red` #8F2633 y familia). Se aceptan adornos chicos (ver Parte 4).
> 6. **Orden:** primero **local + diseño + hablar vos solo** (Fase A). La **IA estratégica** (Fase C) y el **online** (Fase D, el último) van después.

## Contexto técnico (lo que ya existe)

- **Un solo canal de chat hoy**: `GameSession.chatHistory: List<GameChatMessage>` (`GameModels.kt:38`). `GameChatMessage(speaker, message, isGod)` (`GameModels.kt:308`) — **no tiene canal**. Todo (Dios, humano, bots) va a la misma lista, cap 60 (`GameplayFeedMessages.MAX_FEED_MESSAGES`).
- **UI del chat**: `GameplayChatController.kt`. Dos estados: feed colapsado (`chatAmbientFeed`, `chatAmbient*` views) y panel expandido (`chatPanel`, `chatPanelContent`, `chatMessagesContainer`, composer con `chatInput`/`btnSendChat`). Todos los IDs se bindean en `GameplayChatController.kt:81-113`.
- **Tematización ya existente**: `renderChatBackgrounds` (`GameplayChatController.kt:1347`) elige drawable y alfa **por mapa** (`host.currentSession.mapKey`). Es el gancho natural para meter la piel roja por rol+fase. El proyecto ya tiene un patrón de "marcos por mapa" en las ventanas de evento (`applyRevealOverlayTheme`) — mismo espíritu.
- **Gate de chat**: `GameEngine.canHumanChat` (`GameEngine.kt:1169`) hoy devuelve `false` en todas las fases de noche. `canParticipateInChat` (`GameEngine.kt:1189`) controla quién habla.
- **Detección de noche**: `GameplayTableUi.isNightPhase(phase)` (`GameplayTableUi.kt:132`) — asesino/mercenario/policía/médico/oráculo.
- **Es traidor**: `GameRules.isTraitorRole(role)` (`GameModels.kt:363`) — keys `asesino`, `mercenario`, `espia`, o `team == "Traidores"`.
- **Escritura de mensajes**: `GameEngine.addHumanChatMessage` (`GameEngine.kt:1198`) y `addBotChatMessage` (`GameEngine.kt:1226`); ambos pasan por `withChatMessage` (`GameEngine.kt:1968`).

---

## Fase A — Canal, gate y piel roja en local (vos hablás solo)

Al terminar la Fase A: en una partida local, cuando sos traidor y cae la noche, el chat se pone rojo, dice "Plan de los Asesinos", podés escribir, y lo que escribís queda en un canal aparte que el pueblo nunca ve. De día ese canal se ve bloqueado como recap. Los bots traidores **todavía no hablan** ahí (eso es Fase C).

### A.1 Modelo: segundo canal
En vez de una lista nueva paralela (que duplicaría el manejo de cap/estado/serialización), agregar un **canal** al mensaje y **una sola** lista:

```kotlin
// GameModels.kt
enum class ChatChannel : Serializable { PUBLICO, TRAIDORES }

data class GameChatMessage(
    val speaker: String,
    val message: String,
    val isGod: Boolean = false,
    val channel: ChatChannel = ChatChannel.PUBLICO   // nuevo, default no rompe nada existente
) : Serializable
```
- El default `PUBLICO` mantiene compatible todo el historial y los call sites actuales.
- Cap por canal: al agregar, aplicar `takeLast(MAX_FEED_MESSAGES)` **por canal** (no sobre la mezcla), para que una noche larga de traidores no borre el feed público ni al revés. Ajustar `withChatMessage` (`GameEngine.kt:1968`) para respetar el cap por canal.

### A.2 Helper de identidad y gate
Nuevos helpers (en `GameEngine` o un `TraitorChat` object):
```kotlin
fun isTraitorChatUnlocked(session): Boolean   // hay al menos 1 traidor VIVO en la partida
fun canSeeTraitorChat(session, player): Boolean = isTraitorRole(player.role) && player.alive
fun isTraitorChatWritable(session): Boolean =
    isNightPhase(session.phase) && !session.winner.isNotBlank()   // toda la noche
fun aliveTraitors(session): List<GamePlayer>
```
- **Regla del humano**: el humano ve/usa el canal traidor solo si `canSeeTraitorChat(session, human)`.
- **Escribir**: nueva `GameEngine.canHumanChatTraitor(session)` = `canSeeTraitorChat(human) && isTraitorChatWritable(session)`. Es independiente de `canHumanChat` (que sigue gobernando el chat público de día).
- **Muerto**: un traidor muerto **no** ve el canal (queda fuera del complot). Consistente con `canParticipateInChat`.

### A.3 Ruteo de mensajes
- `addHumanChatMessage` (`GameEngine.kt:1198`): si estamos en noche y el humano es traidor, el mensaje se **etiqueta `TRAIDORES`** y se saltea la reacción de bots públicos (`reactionsToHumanMessage` es para el debate del día). Firmar el canal explícitamente para no adivinar: agregar parámetro `channel: ChatChannel = ChatChannel.PUBLICO` y que el controller pase el correcto.
- `withRecordedClaim` / memoria pública (`GameEngine.kt:1975` y la `tableMemory`): **los mensajes del canal traidor NO alimentan la memoria pública** (no son "declaraciones ante el pueblo"). Filtrar por `channel == PUBLICO` en todo lo que lee el chat para deducir sospechas/claims. Esto evita que un plan nocturno contamine el scoring diurno.

### A.4 UI: selección de canal por fase+rol (mismo panel, dos pieles)
El chat **no** es un panel nuevo. `GameplayChatController` decide, en cada render, qué canal y qué piel mostrar:
```
canal activo =
  si (canSeeTraitorChat(human) && (isNightPhase || fase de día con recap))  -> TRAIDORES
  si no -> PUBLICO
```
- `renderChatMessages` filtra `chatHistory` por el canal activo.
- **Noche + traidor**: piel roja, escribible (si `isTraitorChatWritable`).
- **Día + traidor**: piel roja atenuada + input bloqueado, hint "El plan descansa hasta la noche" (recap de solo lectura, decisión 4).
- **Pueblo, o humano no-traidor**: exactamente el comportamiento de hoy (dorado, gate actual).
- Guardar el canal activo en `onSaveInstanceState` no hace falta: se deriva de fase+rol, que ya persisten.

### A.5 Piel roja (feed colapsado + panel abierto)
Extender `renderChatBackgrounds` (`GameplayChatController.kt:1347`) y la construcción de mensajes con una variante de tema. Aplicar cuando el canal activo es `TRAIDORES`:
- **Feed colapsado** (`chatAmbient*`): borde/tinte `#8F2633`, ícono de llama (`chatAmbientTitle` → "Plan de los Asesinos" con un pequeño candado a la derecha si está bloqueado de día), texto de mensajes en tonos rojizos apagados (`#c99`), fondo con sombra interna roja. Referencia visual: el mockup que aprobó el usuario (mockup 1, columna derecha).
- **Panel abierto** (`chatPanel*`): header `#2a1013` con espadas cruzadas + "Plan de los Asesinos · Noche N", borde `#8F2633`, sombra interna `rgba(143,38,51,.28)`, composer con input "Tramar en las sombras…" y botón enviar `#8F2633`. Referencia: mockup 2 y 3.
- Colores a `colors.xml` (no hardcodear en Kotlin): `traitor_red` #8F2633, `traitor_red_bright` #d9455a / #e0616f (nombres), `traitor_panel` #2a1013, `traitor_bg` #160b0b, `traitor_text` #d8c3c6, `traitor_muted` #8a5560. Respeta la convención del proyecto (mover texto/estilo a recursos al tocar una pantalla).
- Si hay drawables de pergamino, hacer variante roja (tinte por `ColorFilter` sobre el mismo drawable si no querés arte nuevo — el usuario maneja assets si hace falta arte dedicado).

### A.6 Aviso de traidor único (decisión 3)
Si `aliveTraitors(session).size == 1` y sos vos: al abrir el canal, una línea de sistema (rojo, centrada) "sos el único que mueve las fichas esta noche". No bloquea escribir (bloc de notas del plan).

### A.7 Encabezado nocturno del canal
Al entrar la noche, insertar una línea de sistema en canal `TRAIDORES`: "— el pueblo duerme. tramen tranquilos —" y, arriba, "Plan de los Asesinos · Noche N". (Análogo a cómo `withPublicHistory` mete líneas de Dios, pero en el canal traidor.)

### A.8 Verificación manual (Android Studio)
- Partida local **siendo traidor** (forzar rol): de noche el chat se pone rojo, dice "Plan de los Asesinos", escribís y tu mensaje aparece ahí; de día ese canal se ve rojo-apagado y bloqueado; el chat público del día sigue dorado y normal.
- Partida **siendo del pueblo**: nunca ves el canal rojo; todo igual que hoy.
- Lo que escribís de noche en el canal traidor **no** aparece en el feed público ni influye en las sospechas/votos de los bots al otro día.
- Con 1 solo traidor: aparece el aviso; con 2-3, no.
- Rotar el teléfono de noche no pierde ni mezcla mensajes de los dos canales.

---

## Fase B — Bots de relleno presentes (sin estrategia todavía)

Puente chico entre A y C para que el canal no se sienta vacío antes de la IA completa.
- Los bots traidores aparecen **listados** en el header del canal (avatares/nombres en rojo, "2 traidores", como el mockup).
- 1 línea de sistema por noche que nombra el objetivo elegido por la IA nocturna existente (ya se decide en `LocalBotAi.chooseAssassinTarget`, `LocalBotAi.kt:257`): "🗡️ el plan cae sobre Valen esta noche". Es **reflejo** de una decisión que ya se toma, no IA nueva. Da sensación de coordinación sin construir el diálogo todavía.

---

## Fase C — IA estratégica en el canal (el corazón de "que se noten las estrategias")

Depende de que estén las fases de memoria del otro spec (`SPEC-ia-bots-conversacion.md`: `tableMemory`, lectura del `claimLedger`). Reusa el director de conversación de esa Fase 3, pero para el canal `TRAIDORES`.

### C.1 Qué planean, y cómo escala con los días
La charla nocturna se genera contra el **estado del día que pasó** (memoria persistente):
- **Noche 1** (poco que planear): elegir víctima con criterio simple y declararlo. Ej: "arranquemos tranqui, todavía no sabemos nada. bajemos al que hable más". Alinear el objetivo dicho con `chooseAssassinTarget`.
- **Noche 2+** (planificación real): leer de `tableMemory`/`claimLedger`:
  - **Amenazas**: ¿quién declaró ser policía y marcó a un traidor? → "lauta dijo que es detective y me marcó, hay que decidir qué hago mañana".
  - **Contra-jugada de rol**: coordinar un claim falso o un cruce ("mejor no lo matamos, si muere confirma que era detective; mañana YO digo que soy el detective y lo cruzo"). Se apoya en `traitorCounterClaimLine`/`traitorFakeClaimLine` que ya existen (`BotDialogueLines.kt:67,1150`) — ahora **coordinados de noche** en vez de improvisados de día.
  - **Objetivo del día**: coordinar a quién empujar en la votación (a quién ensuciar, a quién bancar para no exponerse).
  - **Elección de víctima informada**: matar al líder de opinión, al que junta votos limpios, o a quien está por confirmar un rol — no al azar. Conectar con el scoring nocturno existente (`nightPressureScore`, `LocalBotAi.kt:1555`).
- **Consistencia día↔noche**: lo que un bot traidor acuerda de noche debe **cumplirlo de día** (si dijo "yo te banco", que su línea diurna banque). Esto es lo que hace que "se noten las estrategias": el jugador ve la trama y después la ve ejecutarse.

### C.2 Ritmo
Mismo director encadenado (un mensaje por vez, delay natural, "escribiendo…") que el chat público, pero corriendo sobre el canal `TRAIDORES` durante la noche. Los traidores se responden entre ellos (2-3 idas y vueltas por noche, más si hay 3 traidores).

### C.3 Verificación
- Noche 1 vs noche 3: la charla evoluciona de "matar y listo" a planear en base a lo hablado (reproduce el mockup aprobado).
- Un bot traidor que de noche dijo "mañana te banco" efectivamente banca de día.
- La víctima elegida coincide con lo que se tramó en el canal.

---

## Fase D — Online (la última)

Solo cuando exista seguridad server-side. **Advertencia bloqueante**: hoy el online no tiene Firebase Auth, App Check ni reglas de Firestore (`docs/firebase-online-schema.md`). Una subcolección `chat_traidores` sería legible por cualquier cliente con el SDK → un jugador del pueblo podría espiar el plan. No implementar el online hasta que:
- Haya **reglas de Firestore** que restrinjan lectura/escritura de `chat_traidores` a los UIDs de jugadores cuyo rol asignado sea traidor (lo que a su vez requiere que el reparto de roles viva server-side o esté firmado), **o**
- Se acepte explícitamente como "honor system" documentado (no recomendado para un canal cuyo valor es el secreto).

Diseño previsto (para cuando toque): subcolección `partidas/{sala}/chat_traidores` espejo de `chat`, con el mismo listener de `startOnlineChatListener` (`GameplayChatController.kt:1166`) pero filtrado por canal; el gate de visibilidad se hace además en cliente por rol. El canal `ChatChannel.TRAIDORES` del modelo ya lo deja listo para enchufar.

---

## Parte 4 — Adornos chicos (polish, el usuario los pidió)

Opcionales, para que el canal se sienta vivo y "bien decorado". Priorizar los baratos:
1. **Separador de gota de sangre / sello de lacre** entre noches (una línea divisoria roja con un ícono central, en vez del separador normal).
2. **Chip de objetivo**: cuando se define la víctima de la noche, un chip rojo con calavera al lado del nombre ("🗀 objetivo: Valen").
3. **Recuento de víctimas**: contador chico en el header ("caídos por el plan: 2").
4. **Viñetas de daga**: los mensajes del canal traidor usan un bullet de daga en vez del punto normal.
5. **Pulso sutil del borde rojo** al abrir el panel de noche (una sola animación de entrada, sin loops — respeta la restricción de no recargar la pantalla de gameplay).
6. **Línea de "pacto" inicial** en la primera noche: los nombres de los traidores en rojo, "el pacto: Thiago y Mora".
7. **Marca de agua** tenue (daga/llama) detrás de los mensajes, muy baja opacidad, para textura.

Marcar 1, 2 y 6 como los de mejor relación esfuerzo/impacto; el resto según ganas.

---

## Orden de entrega
1. **Fase A** — canal + gate + piel roja + recap de día (local, hablás solo). El grueso del diseño.
2. **Fase B** — bots traidores listados + línea de objetivo (reflejo, sin diálogo).
3. **Fase C** — IA estratégica en el canal (depende de la memoria del otro spec).
4. **Adornos** (Parte 4) — en cualquier momento después de A.
5. **Fase D** — online, **la última**, bloqueada por seguridad.
