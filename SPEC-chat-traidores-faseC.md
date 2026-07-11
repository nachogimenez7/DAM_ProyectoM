# SPEC — Chat de traidores · Fase C: IA estratégica en el "Plan de los Asesinos"

> Para Codex. Español, `archivo:línea`. No compilar (el usuario valida en Android Studio). Continúa `SPEC-chat-traidores.md`. Depende de que estén **Fase A/B del chat traidor** (ya implementadas) y la **memoria de la IA** (`TableMemory` + lectura del `claimLedger`, ya presentes). **Foco de este spec: la LÓGICA de los bots** — cómo deciden, cómo el plan nocturno persiste y cómo se ejecuta de día para que "se noten las estrategias".
>
> **Meta de la fase:** los bots traidores conversan de noche en el canal rojo y **planean de verdad** leyendo lo que pasó el día (quién se declaró detective, quién los marcó, quién junta votos). La charla evoluciona de "matar y listo" (noche 1) a coordinar coartadas, cruces de rol y objetivos de votación (noche 3+). Y lo que acuerdan de noche **lo cumplen de día**. Ese hilo día↔noche es el corazón de la feature.

## 0. Estado actual (verificado en el código — no re-hacer)

Ya existe y funciona:
- **Canal**: `ChatChannel { PUBLICO, TRAIDORES }` en `GameChatMessage` (`GameModels.kt:308-317`). Cap por canal (`takeLastPerChannel`, `GameEngine.kt:2086`).
- **Gate**: `GameEngine.aliveTraitors` (`GameEngine.kt:1027`), `isTraitorChatUnlocked` (`:1203`), `canSeeTraitorChat` (`:1207`), `isTraitorChatWritable` (`:1211`, usa `isNightActionPhase`), `canHumanChatTraitor` (`:1217`).
- **Ruteo**: `addHumanChatMessage`/`addBotChatMessage` aceptan `channel` (`GameEngine.kt:1231,1271`). Los mensajes `TRAIDORES` **no** alimentan la memoria pública (filtro `channel == PUBLICO`, `GameEngine.kt:579`, `:1385`).
- **Sistema de mesa**: `withTraitorNightHeader` (`GameEngine.kt:2037`, "los malos son X e Y…") y `withTraitorTargetMessage` (`:2049`, "el plan cae sobre X esta noche"), habladas por `TRAITOR_PLAN_SPEAKER = "Plan"` (`GameEngine.kt:6`). **Esto es Fase B: líneas de sistema, sin diálogo de bots.**
- **UI**: piel roja completa (feed + panel + toggle PUEBLO/PLAN + fondos), en `GameplayChatController.kt` (`activeChatChannel` `:1766`, `renderTraitorChatBackgrounds` `:1707`, toggle `:993`).
- **Director de día**: `BotConversationDirector` (`BotConversationDirector.kt`) — genera un beat por vez para el chat **público** de día (fases `DIA_DEBATE/CONTRAPUNTO/VOTACION/DESEMPATE`). Usa `LocalBotAi.nextConversationLine` (`LocalBotAi.kt:636`).
- **Decisión nocturna existente**: `LocalBotAi.chooseAssassinTarget` (`LocalBotAi.kt:257`), `chooseSilenceTarget`, `nightPressureScore` (`:1555`). Ya eligen víctima con criterio; hoy la elección **no** se conversa.
- **Coartadas de día ya existentes pero improvisadas**: `traitorCounterClaimLine` (`BotDialogueLines.kt:67`), `traitorFakeClaimLine` (`:1150`). Hoy salen sueltas de día; Fase C las vuelve **coordinadas de noche**.

Lo que falta (este spec): (1) el **cerebro** que arma el plan, (2) el **diálogo** nocturno que lo expresa, (3) la **ejecución** del plan de día, (4) el **staging/timing** en el controller, (5) **relajar el secreto** dentro del canal.

---

## 1. El problema del timing nocturno (leer antes de codear)

`enterUnifiedNight` (`GameEngine.kt:1552`) recorre las fases de noche en un loop y las auto-resuelve, **salvo** que `requiresHumanInput` la frene (`:1558`). De ahí salen dos casos que la lógica trata distinto:

- **CASO A — el humano es traidor**: la noche **se frena** en el turno de acción del humano (elegir víctima/silenciar). Hay una **ventana real** en la que el humano está sentado en el chat rojo. Acá los bots traidores deben **conversar en vivo** (dripping con delay, como el director de día). Es donde el jugador ve la estrategia pasar frente a sus ojos.
- **CASO B — el humano NO es traidor**: la noche se resuelve **instantáneo**; nadie mira el canal en vivo. El plan igual **debe calcularse** (decisiones + coordinación de día), pero el diálogo nocturno es **estado interno**: no se dripea en vivo (no hay quién lo vea), se commitea al canal para que quede de registro/replay, y sobre todo **influye el día**.

Regla práctica: el **cerebro (Parte 2) corre siempre**; el **diálogo en vivo (Parte 4) solo en CASO A**. En CASO B, generar el diálogo y commitearlo de una (sin delays), o directamente saltearlo y quedarse solo con el plan — decisión de implementación, pero el plan **siempre** se persiste.

---

## 2. El cerebro: `TraitorPlan` (LA LÓGICA)

El plan es una estructura **persistente** en la sesión, recomputada al entrar la noche. Es lo que hace el hilo día↔noche: se decide de noche, se lee de día.

### 2.1 Estructura (nuevo, `GameModels.kt`, al lado de `TableMemory`)
```kotlin
data class TraitorPlan(
    val round: Int,
    val killTarget: String,              // víctima de la noche (DEBE coincidir con chooseAssassinTarget)
    val killRationale: KillRationale,
    val dayPushTarget: String,           // a quién empujar/ensuciar en la votación del día siguiente
    val threats: List<TraitorThreat>,    // amenazas detectadas contra el equipo
    val cover: CoverMove? = null,        // coartada coordinada (claim falso / contra-claim), si aplica
    val speakingOrder: List<String> = emptyList()  // traidores vivos, orden estable de habla en el canal
) : Serializable

enum class KillRationale : Serializable {
    LIDER_DE_OPINION,     // arrastra votos, ordena al pueblo
    CONFIRMA_ROL,         // está por confirmarse como policía/médico útil
    NOS_MARCO,            // señaló a un traidor y hay que callarlo (con cuidado — ver 2.4)
    JUNTA_VOTOS_LIMPIOS,  // viene votando bien y sin exponerse
    CALLADO_PELIGROSO,    // lee mucho y habla poco
    SIN_LECTURA           // noche 1 / no hay señal fuerte
}

data class TraitorThreat(
    val player: String,          // quién amenaza (declaró detective, nos marcó, etc.)
    val kind: ThreatKind,
    val markedTraitor: String?   // a qué traidor apunta, si aplica
) : Serializable

enum class ThreatKind : Serializable {
    DETECTIVE_DECLARADO,   // dijo ser policía en público
    NOS_MARCO_SOSPECHA,    // acusó/ensució a un traidor
    JUNTA_VOTOS            // está ordenando al pueblo
}

data class CoverMove(          // qué van a hacer de día para tapar
    val kind: CoverKind,
    val actor: String,         // qué traidor ejecuta la jugada
    val backer: String?,       // qué traidor lo banca en el chat público
    val fakeRoleKey: String?,  // rol que se va a inventar (para FAKE_CLAIM / COUNTER_CLAIM)
    val targetToDirty: String? // a quién van a ensuciar entre todos
) : Serializable

enum class CoverKind : Serializable {
    LOW_PROFILE,     // no exponerse, hablar poco, no defenderse de más
    COUNTER_CLAIM,   // cruzar a un detective declarado ("yo también soy el detective")
    FAKE_CLAIM,      // inventar un rol de pueblo para ganar credibilidad
    BUS_ALLY         // sacrificar/soltar a un aliado quemado para salvar al resto
}
```
Agregar `val traitorPlan: TraitorPlan? = null` a `GameSession` (`GameModels.kt:40` zona), reset a `null` en `createSession` (`GameModels.kt:564` zona, junto a `TableMemory()`).

### 2.2 Cuándo se arma
Al entrar la noche (mismo gancho que `withTraitorNightHeader`, `GameEngine.kt:32` y `:1549`): antes/junto a poner el header, calcular `session.traitorPlan = TraitorPlanBrain.build(session)` **si** `isTraitorChatUnlocked`. Recomputar cada noche (las amenazas cambian). Nuevo objeto `TraitorPlanBrain` (archivo nuevo `TraitorPlanBrain.kt`).

### 2.3 Cómo se arma (el algoritmo — foco del usuario)
`TraitorPlanBrain.build(session): TraitorPlan`. Todo lee de estado **persistente** (`tableMemory`, `claimLedger`, votos, historial), no de la ventana de 16 mensajes.

**Paso 1 — víctima (`killTarget` + `killRationale`).** Fuente de verdad: `LocalBotAi.chooseAssassinTarget(session, lider)` para que **coincida** con lo que el engine va a matar (nunca conversar una víctima distinta a la que muere). El `rationale` se **deriva** de por qué ese target ganó, ordenado por prioridad:
1. Es un **detective declarado** con reads reales contra el pueblo o contra un traidor → `CONFIRMA_ROL` (matarlo antes de que ordene al pueblo) — salvo que convenga dejarlo vivo para no confirmarlo (ver 2.4; en ese caso NO es la víctima y el rationale de la víctima elegida es otro).
2. **Junta pluralidad de votos** o aparece como líder (`votePluralityTarget`, mucha presencia en `suspicion` ajena baja = confían en él) → `LIDER_DE_OPINION` / `JUNTA_VOTOS_LIMPIOS`.
3. **Marcó a un traidor** (aparece como `source` de una acusación contra un traidor en `claimLedger`/`tableMemory`) → `NOS_MARCO`.
4. Habla poco pero lee bien (`spokeCount` bajo, ronda > 1) → `CALLADO_PELIGROSO`.
5. Nada de lo anterior (noche 1) → `SIN_LECTURA`.

**Paso 2 — amenazas (`threats`).** Barrer los vivos NO-traidores y marcar:
- `DETECTIVE_DECLARADO`: tiene un claim de `POLICIA` en `claimLedger` (usar `latestClaimBySpeaker`/`publicClaimants`).
- `NOS_MARCO_SOSPECHA`: hay una acusación suya contra algún traidor vivo (revisar `tableMemory.suspicion[ese_jugador][traidor]` alto, o `claimLedger` con statement ACCUSE cuyo target sea un traidor). Setear `markedTraitor`.
- `JUNTA_VOTOS`: es `votePluralityTarget` o tiene defensa múltiple (el pueblo lo banca).
Ordenar por peligro: detective que marcó a un traidor > detective declarado > marcó a un traidor > junta votos.

**Paso 3 — objetivo de día (`dayPushTarget`).** A quién van a empujar/ensuciar juntos en la votación. Heurística:
- Si hay una amenaza `DETECTIVE_DECLARADO` que apunta a un traidor, el `dayPushTarget` es **ese detective** (hay que enterrarlo con votos antes de que lo entierren a uno) — coordinado con el `cover` COUNTER_CLAIM.
- Si no, un no-traidor creíble como culpable: alto en `suspicion` del pueblo, o alguien ya ensuciado, priorizando **no** a un traidor. Reusar la lógica de `conversationVotePlan`/`rankedPublicSuspects` pero **excluyendo traidores vivos**.
- Nunca debe apuntar a un traidor salvo `BUS_ALLY` explícito (ver 2.4).

**Paso 4 — coartada (`cover`).** Elegir `CoverKind` según amenaza y dificultad:
- Sin amenaza fuerte → `LOW_PROFILE`.
- Un traidor fue marcado por un detective declarado → `COUNTER_CLAIM`: `actor` = ese traidor, `fakeRoleKey = POLICIA`, `backer` = otro traidor vivo, `targetToDirty` = el detective. (Reusa `traitorCounterClaimLine`, pero ahora la jugada está **acordada** y con backer asignado.)
- Un traidor está bajo presión difusa pero sin detective en contra → `FAKE_CLAIM` de un rol de pueblo verificable-a-medias (aldeano/médico), `actor` = el más presionado, `backer` = otro.
- Un traidor está **quemado sin retorno** (mucha `suspicion`, ya lo van a colgar) y salvarlo cuesta a los demás → `BUS_ALLY`: soltarlo, `targetToDirty` = ese mismo aliado, para desviar el foco y sobrevivir. Solo en dificultad HARD y solo si matemáticamente conviene.

**Paso 5 — orden de habla (`speakingOrder`).** `aliveTraitors` en orden estable (por `stableNoise` sembrado en `session.code`+ronda) para que la conversación no la monopolice siempre el mismo.

`TraitorPlanBrain` debe ser **puro y testeable** (sin Android): entra `GameSession`, sale `TraitorPlan`.

### 2.4 Regla fina: no confirmar al detective matándolo
Matar a un detective declarado lo **confirma** como detective real ante el pueblo (los inocentes ven que los malos lo temían). Si el detective marcó a un traidor, muchas veces conviene **NO** matarlo y en cambio **cruzarlo de día** (`COUNTER_CLAIM`): así queda "palabra contra palabra" y el pueblo no sabe a quién creer. El cerebro debe preferir esta jugada en HARD cuando: hay 2+ traidores vivos (uno puede hacer de "detective" falso) y el detective todavía no encadenó dos reads consistentes. En NORMAL, puede simplemente matarlo (más directo, menos fino). Esta decisión es exactamente el ejemplo del mockup (noche 3).

---

## 3. Del plan al diálogo: `nextTraitorLine`

Nuevo generador espejo de `LocalBotAi.nextConversationLine` (`LocalBotAi.kt:636`), pero para el canal traidor:
```kotlin
fun nextTraitorLine(session: GameSession, speaker: String): String?
```
- Solo produce si `GameEngine.canSeeTraitorChat(session, bot)` y hay `traitorPlan`.
- Genera **una** línea contra el estado ACTUAL del canal (que ya incluye lo que dijeron los traidores anteriores — igual que el director de día lee la conversación en curso). Nada de tandas pregeneradas.
- El contenido sale del `TraitorPlan` + la personalidad del bot (`personalityFor`). Plantillas nuevas en `BotDialogueLines.kt` (bloque nuevo `traitor*`), por tema:
  - **Apertura de noche** (primer beat): nombrar el clima. Noche 1: "arranquemos tranqui, todavía no sabemos nada de nadie". Noche N con amenaza: "ojo que $detective se paró de detective y me marcó a mí".
  - **Propuesta de víctima**: expresar `killTarget` + `killRationale` en criollo. `LIDER_DE_OPINION` → "bajemos a $target, ordena al pueblo y nos arrastra". `CONFIRMA_ROL` → "si dejamos a $target va a confirmar el rol, cae hoy". `NOS_MARCO` → "$target me señaló, pero si lo mato me confirmo… mejor lo cruzamos de día" (cuando el cover es COUNTER_CLAIM, la línea DEBE ser coherente con no matarlo).
  - **Coartada**: expresar el `cover`. COUNTER_CLAIM → "mañana YO digo que soy el detective y lo cruzo, vos bancame". El `backer` responde: "dale, yo te sigo y le tiro que se contradijo". FAKE_CLAIM → "yo mañana tiro que soy médico para despegarme". BUS_ALLY → "a $aliado ya lo tienen, soltémoslo y salvamos la ronda".
  - **Objetivo de día**: "en la votación empujamos a $dayPushTarget entre todos".
  - **Cierre/acuerdo**: confirmaciones cortas por personalidad ("cerrado", "me gusta", "de una").
- **Respuestas encadenadas**: si el último beat del canal fue una pregunta o una propuesta de otro traidor, este bot responde a eso (mismo patrón `preferredSpeakerFromLastMessage` del director de día, `BotConversationDirector.kt:135`). Reusar esa lógica apuntando al canal TRAIDORES.
- Devuelve `null` cuando el plan ya se dijo (no forzar relleno): apertura → víctima → coartada (si hay) → objetivo de día → 1-2 confirmaciones, y listo.

**Importante — secreto relajado (Parte 5)**: `nextTraitorLine` NO debe pasar por el saneo que borra términos de rol (`sanitizeBotSpeech`/`finishSpeech` scrub de `forbiddenTerms`). En su propio canal los traidores SÍ pueden decir "asesino", "soy el mercenario", "detective", nombres de rol. Ver Parte 5.

---

## 4. Ejecución de día: que cumplan lo que tramaron (hilo día↔noche)

Sin esto la estrategia no "se nota". El generador de día (`openingDebateMessages`/`votingIntentMessages` vía `nextConversationLine`) debe **leer `session.traitorPlan`** para los bots traidores:
- **`dayPushTarget`**: un bot traidor prioriza empujar a ese jugador en el debate/votación (sesga su `conversationVotePlan` hacia ese target, salvo que se haya vuelto imposible).
- **`cover`**:
  - `COUNTER_CLAIM`: el `actor` ejecuta el claim cruzado de día (dispara `traitorCounterClaimLine` de forma **determinada**, no aleatoria); el `backer` lo banca cuando le toca hablar ("yo le creo a $actor, el otro se contradijo").
  - `FAKE_CLAIM`: el `actor` suelta el rol falso; el resto no lo desmiente.
  - `LOW_PROFILE`: el `actor` habla menos y no se defiende de más (bajar su presupuesto de líneas / evitar auto-acusarse).
  - `BUS_ALLY`: los traidores **no** defienden al aliado quemado (o incluso lo votan) — pero con el límite de `canVotePlanTarget`/`canVotePlanTarget` que ya evita que los traidores se voten sin motivo fuerte (`LocalBotAi.kt` zona `conversationVotePlan`).
- **Consistencia de bancada**: si un traidor dijo de noche "yo te banco", su línea de día debe bancar, no acusar, al aliado. Un check simple: al construir la línea de día de un traidor, si el `plan.cover.backer == bot.name`, sesgar hacia defender al `actor` / atacar al `targetToDirty`.

Este acoplamiento es lo que convierte el plan en algo **visible**: el jugador ve la trama de noche y después la ve ejecutarse de día.

---

## 5. Staging y timing en el controller (`GameplayChatController.kt`)

Espejo del director de día, pero para la noche y el canal TRAIDORES. **Solo se dripea en vivo en CASO A** (humano traidor, noche frenada).

- **Disparo**: donde hoy se llama `onPhaseSettled()` (`GameplayChatController.kt:214`) y en las transiciones a noche. Agregar un `onTraitorNightSettled()` que corra si `isNightPhase` + `canUseTraitorChatUi(session)` (el humano ve el canal) + `isTraitorChatUnlocked`.
- **Generación de beats**: un `nextTraitorBeat(session, lastSpeaker)` análogo a `BotConversationDirector.nextIdleBeat` (`BotConversationDirector.kt:37`): elige hablante de `traitorPlan.speakingOrder` (nunca dos veces seguidas), pide `LocalBotAi.nextTraitorLine`, devuelve `BotConversationBeat`. Puede vivir como modo del mismo `BotConversationDirector` (agregar el canal traidor a un set aparte y ramificar `nextConversationLine`→`nextTraitorLine`) o como `TraitorChatDirector` gemelo. Preferible reusar `BotConversationDirector` para no duplicar el manejo de delays/typing.
- **Commit**: `GameEngine.addBotChatMessage(session, speaker, message, channel = ChatChannel.TRAIDORES)` (ya soporta el canal, `GameEngine.kt:1271-1280`).
- **Delays**: reusar `naturalDelayMs`/`silenceDelayMs` (`BotConversationDirector.kt:76,83`). El typing indicator ya existe; asegurarse de que se muestre en el canal traidor (hoy `renderChatMessages` oculta typing en TRAIDORES, `GameplayChatController.kt:913` — habilitarlo para el dripping nocturno).
- **Presupuesto por noche**: acotar a ~2 idas y vueltas por traidor (más si son 3). Al agotar el plan (`nextTraitorLine` devuelve null), callar hasta que el humano escriba o hasta que avance la noche.
- **Cancelación**: al salir de la noche / cambiar de fase / winner, cancelar los runnables pendientes (mismo `cancelPendingBotChat` que ya usa el director de día).
- **CASO B** (humano no traidor): no hay UI que mirar. El plan igual se calculó en el engine (Parte 2). Opcional: commitear el diálogo de una para que quede en el historial del canal (no lo verá nadie salvo replay/debug). No dripear.

---

## 6. Escalado por ronda y por dificultad

- **Noche 1**: `SIN_LECTURA` casi siempre → apertura + propuesta de víctima simple + cierre. Corto. ("todavía no sabemos nada, bajemos al que más habla.")
- **Noche 2+**: aparecen amenazas → coartadas y objetivo de día. La charla se alarga y se pone táctica.
- **NORMAL**: jugadas directas (matar amenazas, empujar sospechosos obvios), poca finura, algún error humano (a veces no arma cover aunque convendría).
- **HARD**: usa `COUNTER_CLAIM`/`BUS_ALLY`, prioriza no confirmar detectives, coordina bancadas, elige víctimas de alto valor. Es donde el hilo día↔noche se ve más filoso.
- Reusar los umbrales de dificultad que ya maneja la IA (`session.botDifficulty`, presente en `conversationVotePlan`, `historicalVotePlans`, etc.).

---

## 7. Tests (`TraitorPlanBrainTest.kt` + extensión de tests de diálogo)

Como `TraitorPlanBrain` es puro, es 100% testeable:
- **Coincidencia de víctima**: `plan.killTarget == LocalBotAi.chooseAssassinTarget(session, lider)` siempre.
- **Rationale correcto**: escenario con detective declarado que marcó a un traidor → amenaza `DETECTIVE_DECLARADO` presente; en HARD con 2 traidores → `cover.kind == COUNTER_CLAIM` con `actor` = el marcado y `backer` asignado; el `killTarget` NO es ese detective (no confirmar).
- **dayPushTarget nunca es un traidor** salvo `BUS_ALLY`.
- **Noche 1**: `SIN_LECTURA`, sin cover, plan corto.
- **Persistencia**: el plan armado en la noche N sigue leyéndose de día N (los bots traidores empujan `dayPushTarget` en la votación).
- **Consistencia día↔noche**: si `cover.backer == X`, la línea de día de X banca al `actor` y no lo acusa.
- **Secreto**: `nextTraitorLine` PUEDE contener términos de rol; `nextConversationLine` (público) NO (regresión del anti-secreto existente).
- **Diálogo**: barrer varias seeds y asertar que la conversación nocturna nombra la víctima del plan y, si hay cover, la jugada del cover.

---

## 8. Verificación manual (Android Studio)

- **Siendo traidor con 2+ traidores**: cae la noche → los bots traidores conversan en el canal rojo con delay ("escribiendo…"), proponen víctima con motivo, y si hay un detective declarado que marcó a alguien, acuerdan cruzarlo de día. Al día siguiente, ese cruce **efectivamente pasa** en el chat público y empujan al objetivo acordado.
- **Noche 1 vs noche 3**: la charla nocturna evoluciona de "matar y listo" a planear con lo que se dijo (reproduce el mockup aprobado).
- **La víctima que muere coincide** con la que se conversó de noche.
- **Siendo del pueblo**: no ves nada del canal rojo; pero notás la **coordinación** de los traidores de día (votos alineados, un cruce de "yo soy el detective" contra el detective real).
- **Con 1 traidor**: el canal muestra el plan en solitario (aviso de traidor único, sin diálogo entre pares) y la víctima sigue saliendo del cerebro.
- **Regresión**: nada de lo secreto del canal traidor se filtra al chat público ni a las sospechas de los bots de día.

## Orden de entrega
1. **Parte 2** (`TraitorPlanBrain` + `TraitorPlan` persistente) — el cerebro, puro y testeable. Sin UI todavía.
2. **Parte 3** (`nextTraitorLine` + plantillas) — el diálogo.
3. **Parte 5** (staging en el controller) — el dripping en vivo (CASO A).
4. **Parte 4** (ejecución de día) — cerrar el hilo día↔noche.
5. **Parte 6** (escalado por dificultad) — pulido.
