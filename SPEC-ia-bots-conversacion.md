# SPEC — IA de bots viva: memoria, conversación encadenada y lenguaje natural

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). **Ciclo de trabajo sobre la IA del modo local**: el objetivo es que los bots se sientan vivos — que sigan el hilo de la charla, recuerden lo que se dijo, se contesten entre ellos, reaccionen a lo que escribe el humano y hablen en español natural.
>
> **Decisiones tomadas con el usuario:**
> 1. Alcance: **reorganización completa** de los módulos de IA (se permite mover bastante código), manteniendo la API pública de `LocalBotAi` como fachada para no romper los call sites de `GameEngine`.
> 2. Charla: **conversación encadenada** — cada mensaje de bot se decide DESPUÉS de ver el anterior, no en tandas pregeneradas.
> 3. Tono: **argentino informal** (como está), pero eliminando anglicismos de jerga tipo "claim".
> 4. Entrega **por fases**: cada fase compila y es jugable por sí sola. No empezar una fase sin terminar la anterior.

## Contexto: qué existe hoy y por qué se siente verde

La IA vive en 4 archivos (~5.200 líneas):

| Archivo | Rol actual |
|---|---|
| `LocalBotAi.kt` (1.634) | Decisiones (matar/investigar/proteger/votar), scoring de sospechosos, parseo de claims/declaraciones, generación de mensajes |
| `BotConversationMemory.kt` (640) | Helpers de parseo + "memoria" reconstruida del chat |
| `BotDialogueLines.kt` (1.513) | Plantillas de frases |
| `GameplayChatController.kt` (1.397) | UI del chat + scheduling de mensajes con delay e indicador de "escribiendo…" |

Hay mucho construido que funciona: personalidades (`BotPersonality`), agendas (`agendaFor`, `LocalBotAi.kt:1399`), planes de voto con razones (`conversationVotePlan`, `LocalBotAi.kt:833`), contradicciones vía `claimLedger` (`BotConversationMemory.kt:353`), delays con typing indicator (`GameplayChatController.kt:1233`). Los problemas concretos:

1. **Memoria de pez**: `recentPublicMessages` (`BotConversationMemory.kt:514`) toma solo los **últimos 16 mensajes**; `conversationMemory` (`BotConversationMemory.kt:168`) y `scoreCandidate` (`LocalBotAi.kt:1433`) se reconstruyen desde esa ventana cada vez. Si el humano dice "soy detective, mora me dio sospechoso" y pasan 16 mensajes, los bots lo olvidan — aunque el `claimLedger` (`GameModels.kt:39`, alimentado en `GameEngine.kt:1975`) **sí lo tiene guardado en forma persistente**. La info está; nadie la lee.
2. **Parseo frágil**: `roleClaimFrom` (`LocalBotAi.kt:743`) y `publicStatementFrom` (`LocalBotAi.kt:754`) son contains/regex exactos. Fallan con femeninos ("mora me salió sospechosa"), variantes ("me resultó", "el doc soy yo") y `mentionsName` (`BotConversationMemory.kt:557`) usa `contains` sin límites de palabra ("Mora" matchea dentro de "demora").
3. **Jerga en spanglish**: "doble claim", "claim anotado", "ese claim solo no alcanza" (`BotDialogueLines.kt:56,58,60,85,110,674`; `LocalBotAi.kt:578,859,989,998`; `BotConversationMemory` razones "hay doble claim"). *Claim* es jerga de Mafia/Werewolf online (= declarar rol); el jugador promedio no la entiende.
4. **Tandas, no conversación**: `openingDebateMessages` (`LocalBotAi.kt:444`) y `votingIntentMessages` (`LocalBotAi.kt:528`) generan N mensajes contra la MISMA foto del juego en `GameEngine.withBotDebate` (`GameEngine.kt:2003`); después `stageBotBurstForCurrentPhase` (`GameplayChatController.kt:199`) los des-agrupa y los suelta con delay. Parece diálogo pero ningún bot escuchó al anterior.
5. **Off-topic sin manejar**: `isCasualHumanMessage` (`BotConversationMemory.kt:102`) solo detecta saludos. Un tema random cae en `HumanMessageIntent.OTHER` → respuesta genérica en vez de frenar al jugador y volver al juego.
6. **Cero tests de IA**: no existe ningún test para estos 4 archivos.

---

## Arquitectura objetivo

Reorganizar en módulos con una responsabilidad cada uno (mismo package `com.traidores.juego`):

```
BotPerception.kt            ← qué se dijo (parseo de mensajes → hechos estructurados)
BotTableMemory.kt           ← qué pasó (memoria persistente + derivada; hoy BotConversationMemory.kt)
BotBrain.kt                 ← qué hacer (targets nocturnos, scoring, planes de voto, objetivos)
BotDialogueLines.kt         ← qué decir (plantillas; se mantiene el nombre)
BotConversationDirector.kt  ← cuándo/quién habla (NUEVO — scheduling encadenado, lado UI)
LocalBotAi.kt               ← fachada delgada: mantiene la API pública actual
```

Reglas de la migración:
- `LocalBotAi` conserva **exactamente** las firmas públicas que usan `GameEngine` y `GameplayChatController` (`chooseAssassinTarget`, `chooseVoteTarget`, `reactionsToHumanMessage`, `roleClaimFrom`, `publicStatementFrom`, `isDebugVoteCommand`, `publicEventFromAnnouncement`, `reactionsToEvent`, etc.), delegando a los módulos nuevos. Así los call sites no cambian.
- Los identificadores internos en inglés (`counterClaim`, `fakeClaim`, `RoleClaim`) **pueden quedarse** — solo cambian los **strings visibles al jugador**.
- Nada de esto toca el modo online (`isOnlineGameplay()` ya cortocircuita los bots en el controller).

---

## Fase 1 — Lenguaje: fuera anglicismos, léxico ampliado, off-topic

La fase de menor riesgo y efecto inmediato. Sin cambios estructurales.

### 1.1 Traducir la jerga (strings visibles)
Reemplazar en `BotDialogueLines.kt` y `LocalBotAi.kt` toda aparición de "claim" en texto visible. Guía (adaptar al contexto de cada línea, no literal):

| Hoy | Propuesta |
|---|---|
| "eso es doble claim" | "los dos no pueden ser $rol, uno miente" |
| "doble claim entonces, uno esta vendiendo humo" | "ah bueno, dos que dicen lo mismo. uno vende humo seguro" |
| "hay doble claim y uno esta mintiendo" (razón de voto) | "dos dijeron el mismo rol y uno miente" |
| "claim anotado, pero falta explicar que hiciste" | "anotado lo del rol, pero contá qué hiciste" |
| "ese claim solo no alcanza" | "decir el rol solo no alcanza" |
| "si $target no explica ese claim yo voy por ahi" | "si $target no explica lo del rol yo voy por ahí" |
| "cambio el claim de rol" (razón) | "primero dijo un rol y después otro" |
| "si necesitan claim después lo doy" | "si necesitan que diga mi rol después lo digo" |
| "listo, claim de ${claim.label}..." | "listo, dijiste ${claim.label}..." |

Barrer con grep `claim` sobre strings (comillas) en los 4 archivos al terminar: no debe quedar ninguno visible.

### 1.2 Parseo robusto (`BotPerception.kt`, nuevo — mover ahí `roleClaimFrom`, `publicStatementFrom`, `humanQuestionKind`, `isCasualHumanMessage`, `normalizedForParsing` y el léxico)
- **Femeninos y variantes de resultado**: en `publicStatementFrom`, generalizar los patrones `me dio/salio X` a una regex con raíz: `(me )?(dio|salio|resulto|marco como) (sospechos[oa]|inocente|culpable|traidor[a]?|limpi[oa])`. Cubrir también "X es sospechosa", "X está rara", "X me parece turbia".
- **Claims con más formas**: `roleClaimFrom` debe cubrir "soy el/la X", "me tocó X", "tengo el rol de X", "el X soy yo", "a mí me tocó ser X". Agregar alias que falten a `roleAliases` (`LocalBotAi.kt:209`): "inspector", "poli" para POLICIA; "doctor/a", "médica" para MEDICO.
- **`mentionsName` con límites de palabra** (`BotConversationMemory.kt:557`): tras normalizar, matchear con `Regex("(^|[^a-z0-9])$nombre($|[^a-z0-9])")` en vez de `contains`. Test obligatorio: "demora" NO matchea a "Mora"; "mora," sí.
- **Apodos**: aceptar prefijos de nombre de ≥4 letras como mención ("lauta" → Lautaro) solo si el prefijo es no ambiguo entre los vivos.

### 1.3 Detección de off-topic y redirección
- **Léxico de señales de juego** (nuevo, en `BotPerception.kt`): el mensaje "es del juego" si contiene alguna de: un nombre de jugador vivo; una palabra de rol o alias; verbos de acción del juego con sinónimos amplios (`votar, echar, sacar, expulsar, colgar, quemar, linchar, matar, proteger, curar, investigar, revisar, mirar, salvar, silenciar, callar`); vocabulario de sospecha ya existente (`accusationWords`, `defenseWords`); o meta del juego (`ronda, noche, día, voto, empate, rol, carta, pueblo, aldea, fase, partida, juego, traidor, inocente`). **La lista debe ser generosa**: ante la duda, el mensaje se considera del juego (el usuario pidió explícitamente tolerancia a otras palabras para referirse al juego).
- **Nuevo `HumanMessageIntent.OFF_TOPIC`**: se clasifica así solo si NO hay ninguna señal de juego, NO es saludo casual, NO es respuesta a una pregunta pendiente (`ANSWER_PENDING` mantiene prioridad, `BotConversationMemory.kt:21`) y el texto tiene ≥ 12 caracteres. Mensajes cortos ambiguos ("jaja", "aaa ok") siguen cayendo en CASUAL/OTHER.
- **Reacción**: responde **1 solo bot** (2 si es reincidencia), con líneas por personalidad que frenan y reenganchan con una pregunta de juego. Ejemplos de tono:
  - TRANQUI: "jaja después lo hablamos, ahora concentrate: ¿a quién mirás vos?"
  - PICANTE: "¿eso qué tiene que ver? acá hay un traidor suelto, decí algo útil"
  - JODON: "buenísimo, contálo en el velorio del que muera esta noche. dale, ¿a quién votás?"
  - DESCONFIADO: "cambiás de tema justo ahora… ¿te estás haciendo el distraído?"
- **Reincidencia** (2+ off-topic seguidos del humano): el segundo bot sube el tono ("en serio, si no jugás te van a votar a vos por dar vueltas") — esto usa la memoria de Fase 2; en Fase 1 alcanza con mirar si el mensaje humano anterior también fue off-topic dentro de la ventana actual.

### 1.4 Tests (nuevo `BotPerceptionTest.kt`)
- Tabla de frases reales → claim/statement esperado. Incluir como mínimo: "soy detective, mora me dio sospechoso", "mora me salió sospechosa", "soy el médico", "me tocó ser policía", "no pienso decir mi rol", "confío en valen", "voto a thiago", "MORA ES TRAIDORA" (mayúsculas), "morá" (acento).
- `mentionsName`: casos límite de subcadenas.
- Clasificación off-topic: "anoche vi una película buenísima" → OFF_TOPIC; "¿a quién echamos hoy?" → juego (sinónimo "echamos"); "hola" → CASUAL; respuesta a pregunta pendiente nunca es OFF_TOPIC.
- Anti-jerga: iterar los generadores de líneas con varias seeds/sesiones y asertar que ningún string producido contiene "claim".

---

## Fase 2 — Memoria persistente: que los bots recuerden

### 2.1 Leer el `claimLedger` en vez de la ventana de 16
El ledger ya persiste claims y declaraciones con ronda y fase (`GameEngine.withRecordedClaim`, `GameEngine.kt:1975`; se alimenta desde `addHumanChatMessage` (`GameEngine.kt:1188`) y `addBotChatMessage` (`GameEngine.kt:1216`)). Cambios:
- `conversationMemory` (`BotConversationMemory.kt:168`): construir `roleClaim`, `latestStatement`, `accusedTargets/By`, `defendedTargets/By` **desde `session.claimLedger`** (todas las rondas), no desde los últimos 16 mensajes. La ventana de 16 queda solo para lo genuinamente conversacional (preguntas recientes, streaks, eco de líneas).
- `publicClaimants` (`BotConversationMemory.kt:606`) y `latestClaimBySpeaker` (`:614`): ídem, leer del ledger. Hoy un "doble claim"… un **cruce de roles** deja de detectarse cuando los mensajes salen de la ventana; con el ledger queda para toda la partida.
- `humanSuggestedVoteTarget` (`LocalBotAi.kt:1120`): leer la última declaración ACCUSE/VOTE del humano desde el ledger (con `round` para ponderar: lo de esta ronda pesa más que lo de hace dos).

### 2.2 Nuevo estado persistente en `GameSession` (`GameModels.kt`)
```kotlin
val tableMemory: TableMemory = TableMemory()

data class TableMemory(
    // observador -> objetivo -> puntaje de sospecha acumulado (evoluciona, no se recalcula)
    val suspicion: Map<String, Map<String, Int>> = emptyMap(),
    // preguntas dirigidas sin responder: objetivo -> (ronda, quién preguntó)
    val pendingQuestions: Map<String, PendingQuestion> = emptyMap(),
    // resultados de investigación anunciados públicamente (quién dijo, sobre quién, qué dio, ronda)
    val investigationReads: List<InvestigationRead> = emptyList()
) : Serializable
```
- **`suspicion`**: se actualiza incrementalmente en `GameEngine` cada vez que se agrega un mensaje al chat (mismo lugar que `withRecordedClaim`): acusación sobre X → +N para todos los observadores; defensa → −N; contradicción detectada → +grande; al cambiar de ronda, decaimiento suave (p. ej. multiplicar por 0.7 redondeando) para que lo viejo pese menos pero no desaparezca. `scoreCandidate` (`LocalBotAi.kt:1433`) pasa a usar `suspicion[bot][candidato]` como base y le suma solo las señales frescas de la ventana. **Resultado: continuidad** — un bot que sospechaba de X ayer arranca hoy sospechando de X.
- **`pendingQuestions`**: reemplaza la detección frágil de `unansweredQuestionFor`/`pendingQuestionForHuman` (`BotConversationMemory.kt:257,223`) — se registra al commitear un mensaje con "?" dirigido a alguien y se limpia cuando el objetivo habla. Sobrevive al cambio de fase (hoy se pierde si salen de la ventana).
- **`investigationReads`**: cuando alguien con claim de POLICIA declara un resultado ("mora me dio sospechoso"), guardar el read completo. Los bots lo usan en rondas siguientes: si el "detective" declarado muere, sus reads quedan como herencia ("ojo que el detective había marcado a mora antes de morir" — línea nueva en `BotDialogueLines.kt`).
- Actualizar `createSession` (`GameModels.kt:536` zona de reset) y cualquier `copy()` de reinicio para arrancar con `TableMemory()` limpio.

### 2.3 Seguimiento del caso "soy detective, mora me dio sospechoso"
Con 2.1 + 2.2 el flujo completo debe quedar así (test de integración obligatorio):
1. El mensaje se parsea como claim POLICIA + statement ACCUSE(target=mora) → ya funciona (`reactionsToHumanMessage`, `LocalBotAi.kt:628`), pero ahora persiste.
2. Reacción inmediata: el bot detective real (si existe) cruza al humano; un traidor puede contra-declarar (`traitorCounterClaimLine`, `BotDialogueLines.kt:67`) — ya existe, se mantiene.
3. **Rondas siguientes**: mora arranca con sospecha alta para todos los bots de pueblo (via `suspicion` + `investigationReads`); los planes de voto la citan ("el detective la marcó y nunca se explicó"); si mora (bot) es traidora, su prioridad nocturna es matar al humano detective (ya hay infraestructura de presión nocturna en `nightPressureScore`, `LocalBotAi.kt:1555` — sumar un bonus fuerte si el candidato declaró ser POLICIA con un read).
4. Si aparece un segundo "detective", los bots recuerdan el cruce **aunque hayan pasado 30 mensajes**.

### 2.4 Tests (`BotTableMemoryTest.kt`)
- Declaración en ronda 1 + 20 mensajes de relleno → el plan de voto de ronda 3 todavía la cita.
- `suspicion` decae entre rondas pero no se borra.
- `pendingQuestions` sobrevive a un cambio de fase y se limpia cuando el objetivo responde.
- Herencia del detective muerto: sus `investigationReads` siguen influyendo.

---

## Fase 3 — Conversación encadenada (`BotConversationDirector.kt`)

La fase más grande. Reemplaza las tandas pregeneradas por un director que decide **un mensaje por vez contra el estado actual**.

### 3.1 Sacar la generación del engine
- `GameEngine.withBotDebate` / `withBotVotingIntent` (`GameEngine.kt:2003-2008`) dejan de inyectar mensajes en `chatHistory`. El engine solo cambia de fase; la charla la produce el director del lado UI. (Los flujos auto-resueltos — humano muerto/AFK, `quickTestMode` — no necesitan chat, y `chooseVoteTarget` ya no depende de que las "intenciones de voto" estén en el historial porque `conversationVotePlan` lee memoria persistente tras la Fase 2.)
- `stageBotBurstForCurrentPhase` (`GameplayChatController.kt:199`) y `stagedBotBurstPhaseIndex` se eliminan; los 5 call sites en `GameplayMockActivity.kt` (1272, 1305, 3654, 4825, 4840) pasan a llamar `conversationDirector.onPhaseSettled()`.

### 3.2 El director (lado UI, `Handler`, mismo patrón que el scheduler actual)
Estado propio: cola de "beats", contador de líneas de la fase, timestamp del último mensaje humano. API:
- `onPhaseSettled()` — arranca el loop en fases de charla (`DIA_DEBATE`, `CONTRAPUNTO`, `VOTACION`, `DESEMPATE_VOTACION` — el set ya existe en `GameEngine.kt:2021`).
- `onHumanMessage(rawMessage)` — reemplaza `scheduleBotChatReactions` (`GameplayChatController.kt:1209`): encola beats de reacción con prioridad y patea los beats ociosos para después.
- `cancel()` — en cambio de fase, overlay/transición (`host.isTransitionLocked`), winner, `onDestroy` (reusar la limpieza actual, `GameplayChatController.kt:193`).

Loop de un beat:
1. **Elegir hablante** contra el estado ACTUAL: prioridad a (a) el bot nombrado/interpelado por el último mensaje (así hay respuestas de verdad), (b) el bot cuyo objetivo de ronda (`roundObjectiveFor`, `LocalBotAi.kt:1287`) apunta al tema activo, (c) frescura (reusar el ordenamiento de `messageBots`, `BotConversationMemory.kt:453`). Nunca el mismo hablante dos veces seguidas; respetar `limitedReplyCount` (`BotConversationMemory.kt:471`).
2. **Generar UNA línea**: nueva API `LocalBotAi.nextConversationLine(session, speaker): String?` que internamente reusa la maquinaria existente (agenda, objetivo, social read, intents, plantillas) pero para un solo bot contra la sesión actual — que ya incluye lo que dijeron los anteriores. Devuelve null si el bot no tiene nada que valga la pena decir (no forzar relleno).
3. **Commitear** via `GameEngine.addBotChatMessage` (esto registra claims y actualiza `tableMemory` — la conversación alimenta la memoria en tiempo real).
4. **Programar el siguiente beat** con delay natural (ver 3.3).

### 3.3 Ritmo y pausas (los mensajes "van cayendo de a poco")
- Mantener el sistema de typing indicator (`typingBotSpeakers`, `GameplayChatController.kt:79`) y el cálculo por longitud (`botReactionDelayMs`, `GameplayChatController.kt:1294`). Base sugerida: 2,5–5 s entre beats con jitter; typing visible ~60% del delay.
- **Ventana de silencio**: cada 3–5 líneas de bots seguidas, pausa de 6–10 s. Si el humano no habló en toda la fase y queda tiempo, un bot lo interpela directo ("$humano estás muy callado, ¿a quién mirás?") — máximo 1 vez por fase.
- **Presupuesto por fase**: debate ~1,5 líneas por bot vivo (tope 10); votación ~1 intención por bot. Al agotarse, el director calla hasta que el humano hable (sus reacciones no consumen presupuesto de idle).
- Si el humano escribe mientras hay beats pendientes, los beats de reacción al humano van primero y el idle se reprograma — nunca debe sentirse que los bots lo ignoran.

### 3.4 Estado y restauración
- Como los mensajes solo existen una vez commiteados a `chatHistory` (cap 60, `GameplayFeedMessages.kt:5`), `onSaveInstanceState` sigue funcionando sin cambios: al restaurar, el director simplemente arranca beats frescos sobre el historial persistido. Verificar que no se duplique la "apertura" de fase tras rotación/restauración (guardar `phaseIndex` de la última fase abierta por el director, análogo al `stagedBotBurstPhaseIndex` actual).

### 3.5 Tests (`BotConversationDirectorTest.kt` — la parte pura)
Separar la decisión (pura: elegir hablante + generar línea dado un session) del scheduling (Handler). Testear la parte pura:
- El bot interpelado por nombre responde en el siguiente beat.
- Nunca habla el mismo bot dos veces seguidas; streak respeta `limitedReplyCount`.
- Con presupuesto agotado, `nextConversationLine` deja de producir.
- Una pregunta de bot A a bot B produce respuesta de B que menciona el tema (no una línea random).

---

## Fase 4 — Vida extra (después de 1–3; cada ítem es independiente)

1. **Muletillas por personalidad**: 2–3 expresiones firma por `BotPersonality` que se inyectan con baja probabilidad en `finishSpeech` (ej: JODON cierra con "jaja" o "obvio no?"; DESCONFIADO abre con "mmm no sé eh"; ANALITICO numera: "van dos cosas:"). Barato y de alto impacto — los bots se vuelven reconocibles.
2. **Reacción a votos en vivo**: cuando un bot acumula 2+ votos durante VOTACION, se defiende o negocia ("pará pará, ¿yo por qué? el que cambió la historia fue $otro"); un traidor puede sacrificar el tono y tirar la culpa. Hook: donde el gameplay registra cada voto.
3. **Últimas palabras**: al confirmarse expulsión, el expulsado tira 1 línea antes de la animación de la bota — inocente dolido ("se van a arrepentir…"), traidor con picardía si el juego ya lo revela. Hook: la secuencia de expulsión en `VoteResultAnimator`/gameplay.
4. **Interpelar al humano callado**: ya cubierto en 3.3 (ventana de silencio) — dejarlo configurable por dificultad (en HARD lo presionan más).
5. **Referencias a rondas pasadas**: con `tableMemory`, líneas tipo "ayer defendiste a $x y hoy lo votás, decidite" (detectable con `claimLedger`: TRUST ronda N, VOTE ronda N+1 sobre el mismo target). Es el "seguir el hilo" entre días.

**Backlog (no spec'd, para otro ciclo)**: bots que usan emotes del `EmoteCatalog` de vez en cuando; pares de afinidad estables por partida (dos bots que se bancan → subtramas); reaccionar a los emotes del humano.

---

## Verificación manual (Android Studio, el usuario)
- **Hilo**: decir "soy detective, mora me dio sospechoso" en ronda 1 → reacción inmediata coherente; en ronda 2–3 los bots siguen citando el dato y votan en consecuencia; si hay detective bot, te cruza; un traidor puede contra-declararse.
- **Conversación**: durante el debate los mensajes caen de a uno con "escribiendo…", los bots se contestan entre sí (respuesta menciona el tema del mensaje anterior), hay pausas, y si escribís te responden a vos primero.
- **Off-topic**: escribir algo sin relación ("anoche vi una peli") → un bot te frena y te reengancha con una pregunta; "¿a quién echamos?" NO se trata como off-topic.
- **Lenguaje**: jugar 2–3 partidas y confirmar que no aparece "claim" ni ningún anglicismo raro.
- **Regresión**: la partida completa sigue fluyendo (fases, votación, empate, expulsión, victoria); rotar el teléfono en pleno debate no duplica ni pierde mensajes.

## Orden de entrega
1. **Fase 1** (lenguaje + parseo + off-topic + tests) — mejora visible sin riesgo estructural.
2. **Fase 2** (memoria persistente) — el "hilo" real; el ledger ya existe, es sobre todo cablear lecturas.
3. **Fase 3** (director de conversación) — la más grande; requiere 2 terminada.
4. **Fase 4** — ítems sueltos, en cualquier orden, cada uno entregable por separado.
