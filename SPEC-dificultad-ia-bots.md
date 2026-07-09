# SPEC — Presión por dificultad + tono por dificultad + split de `LocalBotAi.kt`

> Para Codex. Alcance acordado con el usuario tras auditoría del código actual. Español, `archivo:línea` donde aplica. Sin compilar (el usuario valida en Android Studio). Implementar **en el orden de las partes** (Parte 1 antes que 2 y 3): la Parte 1 es mecánica y de riesgo cero, y deja el terreno ordenado para las partes 2 y 3.

## Contexto

Ya existe `BotDifficulty` (`NORMAL` / `HARD`) en `GameModels.kt:16` y `GameModels.kt:59-62`, seleccionable desde `LocalModeActivity.kt:21-22` y guardado en `GameSession`. Se usa extensamente en `LocalBotAi.kt` para confianza de voto, si el bot miente sobre su rol, si duda, etc. (~30 puntos del archivo). **Lo que falta**: que la dificultad también afecte a quién apuntan las acciones nocturnas (matar, silenciar) y que la diferencia se note un poco en el tono del diálogo. Además, `LocalBotAi.kt` tiene ~3600 líneas y hay que partirlo por responsabilidad.

**Fuera de alcance de este spec** (no tocar): chat de traidores (feature aparte, viene después), `chooseProtectionTarget` (médico) y `chooseOracleTarget` — son acciones a favor de alguien, no "acciones sobre el jugador", no aplica el sesgo de presión.

---

## Parte 1 — Split de `LocalBotAi.kt` en 3 archivos (hacer primero)

**Objetivo:** reducir el archivo de ~3600 líneas a tres, por responsabilidad, **sin cambiar ningún comportamiento observable**. Ningún archivo externo debe cambiar: `GameEngine.kt`, `GameplayMockActivity.kt`, `GameplayChatController.kt` siguen llamando a `LocalBotAi.chooseAssassinTarget(...)`, `LocalBotAi.openingDebateMessages(...)`, etc. exactamente igual.

### Cómo partir un `object` de Kotlin sin romper nada

Kotlin no tiene `partial class`. La forma segura: `LocalBotAi` sigue siendo el **único punto de entrada público** (mantiene todas sus funciones `fun` actuales, sin renombrar ninguna), pero sus helpers `private fun` se mueven a **funciones de nivel de archivo** (`internal fun`, mismo paquete `com.traidores.juego`) en los 2 archivos nuevos. Como están en el mismo paquete, `LocalBotAi.kt` las sigue llamando por su nombre sin import. Antes de mover cada función, verificar con grep que nada fuera de `LocalBotAi.kt` la usa (no debería, ya eran `private`).

### Mapeo de funciones a archivos

**`LocalBotAi.kt` (queda, ~fachada pública + targeting/scoring):**
Toda la superficie pública (`chooseAssassinTarget`, `chooseSilenceTarget`, `chooseInvestigationTarget`, `chooseProtectionTarget`, `chooseOracleTarget`, `chooseVoteTarget`, `openingDebateMessages`, `votingIntentMessages`, `reactionsToHumanMessage`, `reactionsToEvent`, `publicEventFromAnnouncement`, `isDebugVoteCommand`, `roleClaimFrom`, `publicStatementFrom`, `votePlanSnapshot`, `personalityProfile`) más el motor de targeting/scoring: `conversationVotePlan`, `choosePlanForDifficulty`, `historicalVotePlans`, `canVotePlanTarget`, `votePluralityTarget`, `humanSuggestedVoteTarget`, `rankedPublicSuspects`, `scoreCandidate`, `nightPressureScore`, `relationshipRead(s)`, `roundObjectiveFor`, `agendaFor`, `debugVoteCommandTarget`, `fallbackTarget`, `canUseBotVoteTarget`, `voteCandidatesFor`, `withoutHumanIfDebug`, `isTraitor`, `stableNoise`, `pushedPublicTarget`, `followedPluralityWithoutReason`, todos los `data class`/`enum` internos actuales.

**`BotDialogueLines.kt` (nuevo):**
Generación de texto y sus templates: `eventReactionLine`, `agendaLine`, `objectiveLine`, `playerFocusLine`, `statementReaction`, `lineForIntent`, `linesFor`, `chooseFreshLine`, `informalReason`, `historyReason`, `traitorRoleLines`, `medicRoleLines`, `policeRoleLines`, `deserterRoleLines`, `traitorFakeClaimLine`, `roleClaimReaction`, `traitorCounterClaimLine`, `roleAwareClaimQuestion`, `roleDrivenLine`, `defensiveLine`, `traitorDeflectionLine`, `lowEvidenceOpeningLine`, `casualHumanReply`, `humanQuestionReply`, `humanDoubtReply`, `pendingAnswerReply`, `contradictionLine`, `contradictionVoteLine`, `botToBotLine`, `coordinationLine`, `roleLabel`, `actionLabel`, `claimFollowUp`, `roleAliases`, `finishSpeech`, `withOccasionalEmoji`, `canUseOccasionalEmoji`, `sanitizeBotSpeech`, `isSelfAccusatoryLine`, `neutralSelfAccusationFallback`, `dedupeBotMessages`, `laughFor`, `containsLaugh`, `conversationRole`, `coordinatedIntent`, `openingIntent`, `reactionIntent`, `isWeakSuspicion`, `speechTarget`.

**`BotConversationMemory.kt` (nuevo):**
Lectura del estado social/conversacional: `conversationMemory`, `memoryFor`, `socialRead`, `moodFor`, `personalityFor`, `pendingQuestionForHuman`, `answeredQuestionForHuman`, `unansweredQuestionFor`, `declaredSuspicionTarget`, `publicContradiction`, `roleContradiction`, `actionContradiction`, `stanceContradiction`, `latestExpelledTarget`, `eventTarget`, `mentionedPlayerNames`, `hasUsefulPublicRead`, `latestClaimBySpeaker`, `latestStatementBySpeaker`, `publicClaimants`, `botWithRole`, `hasClaimedRole`, `recentPublicMessages`, `socialChatSize`, `recentBotStreak`, `recentBotSpeakers`, `isBotSpeaker`, `messageBots`, `limitedReplyCount`, `humanMessageIntent`, `humanQuestionKind`, `isCasualHumanMessage`, `isDoubtMessage`, `isDirectClarification`, `previousHumanStatement`, `hasAnySignal`, `hasAccusatoryTargetSignal`, `containsSecretTerm`, `forbiddenTerms`, `mentionsName`, `safeName`, `normalized`, `normalizedForParsing`, `normalizedVoteCommand`, `stripSpanishAccents`, `latestOwnAction`.

Ajustar este mapeo si al mover algo el compilador marca una dependencia cruzada que lo haga más simple en otro archivo — el criterio de "responsabilidad" importa más que la lista exacta.

### Deduplicación obligatoria durante el split

**Caso 1 — selección de rol falso repetida.** `traitorRoleLines` (`LocalBotAi.kt:2596-2623`) y `traitorFakeClaimLine` (`LocalBotAi.kt:2718-2745`) calculan el mismo `fakeRole` con el mismo `when (seed % 3) { 0 -> MEDICO; 1 -> POLICIA; else -> ALDEANO }`. Extraer a un helper único en `BotDialogueLines.kt`:
```kotlin
internal fun fakeClaimedRole(seed: Int): String = when (seed % 3) {
    0 -> RoleCatalog.MEDICO
    1 -> RoleCatalog.POLICIA
    else -> RoleCatalog.ALDEANO
}
```
y usarlo en ambos call sites.

**Caso 2 — las 3 líneas de "pressure" de `traitorRoleLines`** (líneas 2610-2612) son **exactamente** las primeras 3 opciones de `traitorFakeClaimLine` (líneas 2740-2742), en distinto orden. Unificar en una sola lista compartida (p. ej. `internal val TRAITOR_FAKE_CLAIM_UNDER_PRESSURE_LINES = listOf(...)` con las 3 variantes) y que ambas funciones la usen. Verificar con cuidado el orden de llamada real de ambas (una se dispara desde `openingDebateMessages`, la otra desde `roleDrivenLine`) para no cambiar cuál gana si algún día compiten — hoy no compiten en el mismo `when` de `openingDebateMessages`, así que unificar el *contenido* de las listas es seguro; no unificar los *call sites* en sí.

Durante el split, si aparece algún otro patrón de duplicación evidente (mismo criterio: bloques casi idénticos, no "se parecen"), extraerlo también — pero no forzar deduplicaciones dudosas solo por reducir líneas.

### Verificación
- Grep de `LocalBotAi\.` en todo `app/src/main` y `app/src/test`: todos los call sites externos deben seguir compilando sin cambios.
- Correr la suite de tests existente (`LocalBotAiTest.kt` si existe, o los tests de `GameEngineTest.kt` que ejercitan bots) — el split no debe cambiar ningún resultado.

---

## Parte 2 — Presión por dificultad en el targeting

### 2.1 — Sospecha de día (`scoreCandidate`, mueve el voto y la investigación de policía)

Ubicación actual: `LocalBotAi.kt:4` (constante) y `LocalBotAi.kt:3193-3205` (uso), dentro de `scoreCandidate`.

**Antes:**
```kotlin
private const val HUMAN_BASELINE_SUSPICION_NORMAL = 3
...
if (
    session.botDifficulty == BotDifficulty.NORMAL &&
    candidate.isHuman &&
    !hasUsefulPublicRead(session, candidate.name)
) {
    score += HUMAN_BASELINE_SUSPICION_NORMAL
    reasons += if (spokeCount == 0) "esta poco leido" else "falta cerrar su version"
}
```

**Después** (invertido: presión en Difícil, no en Normal; escala con las rondas):
```kotlin
if (candidate.isHuman && !hasUsefulPublicRead(session, candidate.name)) {
    val bonus = humanDayPressureBonus(session)
    if (bonus > 0) {
        score += bonus
        reasons += if (spokeCount == 0) "esta poco leido" else "falta cerrar su version"
    }
}
```
Nueva función (en el archivo de targeting):
```kotlin
private fun humanDayPressureBonus(session: GameSession): Int {
    if (session.botDifficulty != BotDifficulty.HARD) return 0
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (4 + roundsElapsed * 2).coerceAtMost(14)
}
```
Se puede borrar `HUMAN_BASELINE_SUSPICION_NORMAL`.

**Por qué esto también resuelve "investigar" (punto 3 del usuario) sin código nuevo:** `chooseInvestigationTarget` (`LocalBotAi.kt:266-272`) usa `rankedPublicSuspects` → `scoreCandidate`, la misma función. Al quedar este cambio, la policía bot hereda automáticamente el mismo sesgo. **No tocar `chooseInvestigationTarget`.**

Se preserva la condición `!hasUsefulPublicRead`: un humano que aporta información útil nunca recibe este empujón, en ningún modo ni ronda — jugar bien sigue protegiendo.

### 2.2 — Acciones nocturnas de un solo blanco: matar y silenciar

Ubicaciones: `chooseAssassinTarget` (`LocalBotAi.kt:231-248`), `chooseSilenceTarget` (`LocalBotAi.kt:250-264`). Ambas ordenan candidatos por `nightPressureScore`. Agregar un sesgo probabilístico (no un empujón fijo): algunas noches el bot "va directo" contra el humano, otras lo evalúa normal.

Nuevas funciones (junto a `nightPressureScore`):
```kotlin
private const val HUMAN_NIGHT_PRESSURE_BONUS = 25

private fun humanPressureChancePercent(session: GameSession): Int {
    val (base, perRound, cap) = if (session.botDifficulty == BotDifficulty.HARD) {
        Triple(12, 7, 45)
    } else {
        Triple(5, 3, 20)
    }
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (base + perRound * roundsElapsed).coerceAtMost(cap)
}

private fun humanNightTargetBonus(session: GameSession, candidate: GamePlayer, actionTag: String): Int {
    if (!candidate.isHuman) return 0
    val chance = humanPressureChancePercent(session)
    val roll = stableNoise("${session.code}:${session.round}:$actionTag:human-pressure") % 100
    return if (roll < chance) HUMAN_NIGHT_PRESSURE_BONUS else 0
}
```

Tabla de probabilidad resultante (validada con el usuario):

| | N1 | N2 | N3 | N4 | N5 | N6+ (tope) |
|---|---|---|---|---|---|---|
| Normal | 5% | 8% | 11% | 14% | 17% | 20% |
| Difícil | 12% | 19% | 26% | 33% | 40% | 45% |

Uso en `chooseAssassinTarget` — cambiar:
```kotlin
compareByDescending<GamePlayer> { nightPressureScore(session, it) }
```
por:
```kotlin
compareByDescending<GamePlayer> { nightPressureScore(session, it) + humanNightTargetBonus(session, it, "kill") }
```
Análogo en `chooseSilenceTarget` con `"silence"` como `actionTag`. **No tocar** `chooseProtectionTarget` ni `chooseOracleTarget`.

Nota: `roll` y `chance` son deterministas por `session.code + session.round + actionTag` (mismo patrón de `stableNoise` usado en todo el archivo), así que son testeables sin flakiness: dado un `session.round` y `botDifficulty` fijos, el resultado es reproducible.

### 2.3 — Ajuste posterior a playtesting

Estos números (`5/3/20`, `12/7/45`, `4/2/14`, bonus `25`) son punto de partida, no verdad final. Están todos centralizados en 2-3 funciones chicas — after playtesting, tunear ahí es un cambio de una línea, no hace falta tocar el resto del sistema.

---

## Parte 3 — Tono de diálogo por dificultad

**Objetivo ampliado** (pedido explícito del usuario): que Difícil se *sienta* claramente distinto — menos risas/joda, más seriedad, más presión estratégica y más preguntas de rol — sin volver a inflar el archivo que la Parte 1 acaba de ordenar. La forma de lograr ambas cosas a la vez: la mayor parte del cambio es **sustractivo** (sacar risas/cargadas en Difícil, no agregar nada) y donde sí se agrega contenido nuevo, se concentra en **4-5 puntos de alto impacto** (no se duplica cada familia de líneas del archivo).

### 3.1 — Menos risas y joda en Difícil (cambio de selección, no de contenido nuevo)

En `finishSpeech` (`LocalBotAi.kt:3004-3006`), la inserción de risas de `Personality.JODON` queda condicionada a que **no** sea Difícil:
```kotlin
if (personality == Personality.JODON && seed % 3 == 0 && !containsLaugh(text) &&
    session.botDifficulty != BotDifficulty.HARD
) {
    text = "${laughFor(seed)} $text"
}
```
(La línea 3007, el mayúsculas de `Personality.IMPULSIVO`, **se deja como está** — es intensidad/urgencia, no joda, y encaja incluso mejor con "más seriedad".)

Agregar un helper que reencauza el intent `TEASE` (cargada/burla) hacia algo más serio cuando es Difícil, y aplicarlo en los dos lugares donde se resuelve el `Intent` final del bot (`openingDebateMessages` y `reactionsToHumanMessage`, en el punto donde hoy se llama a `coordinatedIntent(...)`):
```kotlin
private fun toneAdjustedIntent(session: GameSession, intent: Intent): Intent {
    if (session.botDifficulty != BotDifficulty.HARD) return intent
    return if (intent == Intent.TEASE) Intent.ACCUSE else intent
}
```
Con esto, `Personality.JODON` (y cualquier personalidad que en su rotación por seed caiga en `Intent.TEASE`, p. ej. `PICANTE`) deja de cargar/burlarse en Difícil y pasa a acusar directo — más filoso, no más gracioso. Todo esto es **sustractivo**: cero líneas nuevas, dos condicionales.

### 3.2 — Más preguntas de rol y presión estratégica (contenido nuevo, acotado a 3 funciones)

**`roleAwareClaimQuestion`** (`LocalBotAi.kt:1350`): agregar, solo cuando `session.botDifficulty == BotDifficulty.HARD`, variantes que piden el detalle concreto en vez de conformarse con el claim (reemplazan o se suman a las ramas existentes de `MEDICO`/`POLICIA`/`ALDEANO`, mismo patrón `chooseFreshLine`):
- `"si sos medico, deci la ronda exacta y a quien, el titulo solo no alcanza"`
- `"si sos detective, dame el nombre que investigaste anoche, ahora"`
- `"aldeano no explica nada por si solo, con quien votaste y por que"`

**`openingDebateMessages`**, rol `ConversationRole.OPENER` (`LocalBotAi.kt` ~964-1043): agregar 3-4 aperturas analíticas usadas cuando `session.botDifficulty == BotDifficulty.HARD` (reemplazan la selección liviana/casual de apertura en ese modo, mismo `target`/`reason` que ya arma la función):
- `"vamos por partes: quien tiene una contradiccion real, no una sospecha del aire"`
- `"hoy no hay lugar para joda, necesito una lectura seria de cada uno"`
- `"menos vueltas, mas estrategia: $target, quiero tu version exacta de anoche"`

**`agendaLine`**, ramas `BotAgenda.ASK_ROLES` y `BotAgenda.FOLLOW_THREAD` (`LocalBotAi.kt` ~1517-1573): agregar 2-3 variantes más clínicas por rama, usadas en Difícil en vez de (no además de) las actuales:
- ASK_ROLES: `"no pido rol por pedir: quiero quien miente, con pruebas"`
- FOLLOW_THREAD: `"$threadTarget dejo un cabo suelto y lo vamos a cerrar ahora, no despues"`

### 3.3 — Charla casual recortada en Difícil

**`casualHumanReply`** (`LocalBotAi.kt:2125`): en Difícil, reemplazar las respuestas amigables actuales por variantes cortas que descartan la joda y devuelven el foco a la ronda:
- `"no tenemos tiempo para la joda, aporta algo de la ronda"`
- `"guardemos los chistes para despues, ahora hay que pensar"`
- `"che, concentrate, esto se juega en serio"`

### 3.4 — Momento de mayor impacto: cuando el asesino difícil va directo contra el humano

Se mantiene lo ya definido: cuando el sesgo de la Parte 2.2 dispara (`humanNightTargetBonus` > 0) y ese bot termina eligiendo al humano, es el momento narrativo más fuerte de "modo difícil me presiona". Agregar 2-3 líneas dedicadas en `eventReactionLine` (bloque `MUERTE_NOCTURNA`), condicionadas a víctima humana + `session.botDifficulty == BotDifficulty.HARD`:
- `"anoche fuimos directo a por vos, $target. esto no va a aflojar"`
- `"$target la vio venir? nosotros ya veniamos calculando esto"`

(Todas las líneas de 3.2-3.4 son referencia de tono, ≤140 caracteres, en la voz ya establecida del archivo — ajustar redacción exacta al patrón real de cada función, no son texto final obligatorio.)

### Por qué esto no vuelve a inflar el archivo
- 3.1 es puramente sustractivo (condicionales sobre código existente, cero líneas de diálogo nuevas).
- 3.2-3.4 agregan contenido nuevo, pero acotado a 5 funciones puntuales (no a las ~20 familias de líneas del archivo), y en varios casos **reemplazan** la selección liviana en Difícil en vez de sumarse a ella (no hay una lista Normal + una lista Difícil conviviendo para siempre en cada función).
- Normal no se toca en ningún punto de esta parte — el tono actual, relajado, ya es el que corresponde a "podés tontear".

---

## Orden de entrega sugerido
1. Parte 1 (split + dedup) — commit propio, diff debe ser solo movimiento de código + las 2 deduplicaciones señaladas.
2. Parte 2 (presión por dificultad) — commit propio, sobre los archivos ya divididos.
3. Parte 3 (tono) — commit propio, más chico.

## Verificación manual (Android Studio, el usuario)
- Partida completa en Normal: confirmar que el bot que mata/silencia rara vez elige al humano, sobre todo en rondas tempranas.
- Partida completa en Difícil: confirmar que la presión sobre el humano se nota y crece con las rondas, sin ser instantánea en la noche 1.
- Confirmar que jugar bien (dar lecturas útiles) sigue bajando la sospecha en ambos modos.
- Revisar que el chat de bots sigue sonando natural (el modificador de tono no debe notarse forzado ni repetirse seguido).
