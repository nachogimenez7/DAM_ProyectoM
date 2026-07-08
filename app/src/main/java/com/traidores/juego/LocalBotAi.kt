package com.traidores.juego

internal typealias BotEvent = LocalBotAi.BotEvent
internal typealias BotEventType = LocalBotAi.BotEventType

internal const val HUMAN_NIGHT_PRESSURE_BONUS = 25

internal data class RoleClaim(
    val roleKey: String,
    val label: String
)

internal data class PublicStatement(
    val type: StatementType,
    val target: String? = null
)

internal data class VotePlanSnapshot(
    val target: String,
    val reason: String,
    val confidence: Int,
    val beats: Int
)

internal data class SocialRead(
    val defended: String? = null,
    val pressured: String? = null,
    val ignoredBy: String? = null,
    val failedPush: String? = null,
    val heated: Boolean = false
)

internal data class ClaimContradiction(
    val first: ClaimRecord,
    val latest: ClaimRecord
)

internal data class BotMemory(
    val unansweredTarget: String? = null,
    val lastPressuredTarget: String? = null,
    val pendingHumanQuestion: PendingHumanQuestion? = null,
    val table: Map<String, PlayerConversationMemory> = emptyMap(),
    val recentLines: Set<String> = emptySet()
)

internal data class PlayerConversationMemory(
    val roleClaim: RoleClaim? = null,
    val latestStatement: PublicStatement? = null,
    val accusedTargets: Set<String> = emptySet(),
    val defendedTargets: Set<String> = emptySet(),
    val accusedBy: Set<String> = emptySet(),
    val defendedBy: Set<String> = emptySet(),
    val pendingQuestionFrom: String? = null
)

internal data class PendingHumanQuestion(
    val speaker: String,
    val message: String
)

internal data class VotePlan(
    val target: String,
    val reason: String,
    val confidence: Int,
    val beats: Int = 1
)

internal data class RelationshipRead(
    val player: GamePlayer,
    val level: TrustLevel,
    val score: Int,
    val reason: String
)

internal data class RoundObjective(
    val type: RoundObjectiveType,
    val target: String? = null,
    val reason: String = "",
    val confidence: Int = 0
)

internal enum class BotAgenda {
    ASK_ROLES,
    CALM_TABLE,
    PUSH_VOTE,
    DEFEND_WEAK,
    FOLLOW_THREAD,
    DEFLECT_PRESSURE
}

internal enum class TrustLevel {
    CONFIA,
    NEUTRAL,
    DUDA,
    SOSPECHA,
    PRESIONA
}

internal enum class RoundObjectiveType {
    ASK_PLAYER,
    DEFEND_PLAYER,
    PUSH_VOTE,
    CALM_TABLE,
    FOLLOW_CONTRADICTION,
    DEFLECT_PRESSURE
}

internal enum class BotPersonality {
    TRANQUI,
    PICANTE,
    JODON,
    DESCONFIADO,
    IMPULSIVO,
    ANALITICO
}

internal enum class BotMood {
    CALM,
    AMUSED,
    ANNOYED,
    DEFENSIVE,
    SUSPICIOUS
}

internal enum class BotSpeechIntent {
    ASK,
    FOLLOW_UP,
    ACCUSE,
    DEFEND,
    TEASE,
    CALM_DOWN,
    ADMIT_DOUBT
}

internal enum class BotConversationRole {
    OPENER,
    FOLLOWER,
    SKEPTIC,
    CALMER,
    CLOSER
}

internal enum class HumanQuestionKind {
    ROLE_HELP,
    VOTE_HELP,
    ACTION_HELP,
    SUSPECT_HELP
}

internal enum class HumanMessageIntent {
    CASUAL,
    ROLE_QUESTION,
    ACTION_HELP,
    VOTE_HELP,
    SUSPECT_HELP,
    ROLE_CLAIM,
    REFUSE_ROLE,
    ACCUSE,
    DEFEND,
    DOUBT,
    ANSWER_PENDING,
    SECRET_LEAK,
    OTHER
}

internal val accusationWords = listOf(
    "sospe",
    "raro",
    "rara",
    "miente",
    "menti",
    "acuso",
    "voto",
    "culpa",
    "callado",
    "silencio",
    "cambio de tema",
    "defiende",
    "nervioso"
)

internal val defenseWords = listOf("confio", "inocente", "limpio", "defiendo", "creo en")

internal val actionStatementTypes = setOf(
    StatementType.PROTECTED,
    StatementType.INVESTIGATED
)

internal val casualWords = setOf("hola", "buenas", "epa", "ey", "eu", "holaa", "holaaa")

internal val casualPhrases = setOf("que onda", "q onda", "todo bien", "toy", "estoy")

internal val secretWords = listOf(
    "asesino",
    "asesina",
    "traidor",
    "traidores",
    "policia",
    "comisario",
    "detective",
    "medico",
    "aldeano",
    "pueblo",
    "neutral",
    "mercenario",
    "espia"
)

internal val roleAliases = linkedMapOf(
    RoleCatalog.MEDICO to listOf("medico", "doc"),
    RoleCatalog.POLICIA to listOf("detective", "comisario", "policia"),
    RoleCatalog.ALCALDE to listOf("alcalde"),
    RoleCatalog.PAYADOR to listOf("payador"),
    RoleCatalog.ORACULO to listOf("oraculo"),
    RoleCatalog.ALDEANO to listOf("aldeano", "pueblo"),
    RoleCatalog.DESERTOR to listOf("desertor")
)

internal data class SuspectRead(
    val player: GamePlayer,
    val score: Int,
    val reasons: List<String>
) {
    fun reason(): String = reasons.firstOrNull() ?: "su postura todavía no cierra"
}

internal object LocalBotAi {
    enum class BotEventType {
        MUERTE_NOCTURNA,
        EXPULSION,
        SILENCIO
    }

    data class BotEvent(
        val type: BotEventType,
        val target: String
    )

    internal fun personalityProfile(session: GameSession): Map<String, String> {
        return session.players
            .filterNot { it.isHuman }
            .associate { player -> player.name to personalityFor(session, player).name }
    }

    internal fun votePlanSnapshot(session: GameSession, voterName: String): VotePlanSnapshot? {
        val voter = GameEngine.playerByName(session, voterName) ?: return null
        return conversationVotePlan(session, voter)?.let { plan ->
            VotePlanSnapshot(
                target = plan.target,
                reason = plan.reason,
                confidence = plan.confidence,
                beats = plan.beats
            )
        }
    }

    fun chooseAssassinTarget(session: GameSession, assassin: GamePlayer): String {
        val candidates = GameEngine.alivePlayers(session)
            .filter { GameEngine.isValidKillTarget(session, it.name, assassin) }
        if (session.quickTestMode && !session.debugBotsNeverKillHuman) {
            val humanName = GameEngine.humanPlayer(session).name
            if (candidates.any { it.name == humanName }) return humanName
        }
        val preferredCandidates = withoutHumanIfDebug(session.debugBotsNeverKillHuman, candidates)
        return preferredCandidates
            .sortedWith(
                compareByDescending<GamePlayer> {
                    nightPressureScore(session, it) + humanNightTargetBonus(session, it, "kill")
                }
                    .thenBy { stableNoise("${session.code}:${session.round}:${assassin.name}:${it.name}:kill") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            .orEmpty()
    }

    fun chooseSilenceTarget(session: GameSession, mercenary: GamePlayer): String {
        val candidates = GameEngine.alivePlayers(session)
            .filter { GameEngine.isValidSilenceTarget(session, it.name, mercenary) }
        val nonTraitors = candidates.filterNot { isTraitor(it) }
        val preferred = nonTraitors.ifEmpty { candidates }
        return preferred
            .sortedWith(
                compareByDescending<GamePlayer> {
                    nightPressureScore(session, it) + humanNightTargetBonus(session, it, "silence")
                }
                    .thenBy { stableNoise("${session.code}:${session.round}:${mercenary.name}:${it.name}:silence") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            .orEmpty()
    }

    fun chooseInvestigationTarget(session: GameSession, police: GamePlayer): String {
        return rankedPublicSuspects(session, police)
            .firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, police)
    }

    fun chooseProtectionTarget(session: GameSession, medic: GamePlayer): String {
        return GameEngine.alivePlayers(session)
            .sortedWith(
                compareByDescending<GamePlayer> { nightPressureScore(session, it) + if (it.name == medic.name) 1 else 0 }
                    .thenBy { stableNoise("${session.code}:${session.round}:${medic.name}:${it.name}:save") }
                    .thenBy { it.name }
            )
            .firstOrNull()
            ?.name
            ?: medic.name
    }

    fun chooseOracleTarget(session: GameSession, oracle: GamePlayer): String {
        if (!oracle.alive || oracle.role?.key != RoleCatalog.ORACULO) return ""
        val candidates = GameEngine.oracleCandidates(session)
        if (candidates.isEmpty()) return ""
        val shouldWait = session.round == 1 && candidates.size == 1 &&
            stableNoise("${session.code}:${oracle.name}:oracle-wait") % 3 != 0
        if (shouldWait) return ""
        return candidates
            .sortedWith(
                compareByDescending<GamePlayer> { player ->
                    session.chatHistory.count { it.speaker == player.name } +
                        session.actionHistory.count { it.actor == player.name }
                }.thenByDescending { player ->
                    session.publicHistory.indexOfLast { it.contains(player.name) }
                }.thenBy { it.name }
            )
            .first()
            .name
    }

    fun chooseVoteTarget(session: GameSession, voter: GamePlayer): String {
        debugVoteCommandTarget(session, voter)
            ?.takeIf { canUseBotVoteTarget(session, voter, it) }
            ?.let { return it }
        conversationVotePlan(session, voter)?.let { return it.target }
        val ranked = rankedPublicSuspects(session, voter)
        val declaredTarget = declaredSuspicionTarget(session, voter)
        val coordinated = if (isTraitor(voter)) {
            val allies = GameEngine.alivePlayers(session)
                .filter { it.name != voter.name && isTraitor(it) }
                .map { it.name }
                .toSet()
            val coverVote = session.botDifficulty == BotDifficulty.NORMAL &&
                stableNoise("${session.code}:${session.round}:${voter.name}:cover-vote") % 10 == 0
            val nonAllies = ranked.filterNot { it.player.name in allies }
            val exposedAlly = ranked.firstOrNull { it.player.name in allies && it.score >= 18 }
            when {
                coverVote -> ranked
                exposedAlly != null && session.botDifficulty == BotDifficulty.HARD ->
                    nonAllies.sortedByDescending { it.score + 4 }
                exposedAlly != null && stableNoise("${session.code}:${session.round}:${voter.name}:bus-ally") % 12 == 0 ->
                    ranked
                else -> nonAllies
            }
        } else {
            ranked
        }
        val voteOptions = coordinated.filterNot { read ->
            hasUsefulPublicRead(session, read.player.name) &&
                coordinated.any { other -> other.player.name != read.player.name && other.score >= 4 }
        }.ifEmpty { coordinated }
        return voteOptions
            .firstOrNull { it.player.name == declaredTarget }
            ?.player
            ?.name
            ?: voteOptions.firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, voter)
    }

    fun isDebugVoteCommand(session: GameSession, message: String): Boolean {
        if (!session.debugBotsObeyVoteCommands) return false
        val normalizedMessage = normalizedVoteCommand(message)
        if (
            normalizedMessage.contains("votenme") ||
            normalizedMessage.contains("voten por mi")
        ) {
            return true
        }
        return session.players.any { player ->
            val name = normalizedVoteCommand(player.name)
            name.isNotBlank() && (
                normalizedMessage.contains("voten a $name") ||
                    normalizedMessage.contains("voten por $name")
                )
        }
    }

    fun publicEventFromAnnouncement(session: GameSession): BotEvent? {
        val announcement = session.publicAnnouncement.takeIf { it.isNotBlank() } ?: return null
        val expelledTarget = eventTarget(session, announcement, "fue expulsado")
            ?: eventTarget(session, announcement, "expulsar a")
            ?: eventTarget(session, announcement, "expulsó a")
        if (expelledTarget != null) {
            return BotEvent(BotEventType.EXPULSION, expelledTarget)
        }
        val normalizedAnnouncement = GameplayTextMarkers.normalize(announcement)
        if (!normalizedAnnouncement.contains("no murio nadie")) {
            eventTarget(session, announcement, "murió")?.let { target ->
                return BotEvent(BotEventType.MUERTE_NOCTURNA, target)
            }
        }
        if (
            normalizedAnnouncement.contains("no puede hablar ni votar") ||
            normalizedAnnouncement.contains("silenciado")
        ) {
            eventTarget(session, announcement, "no puede hablar")?.let { target ->
                return BotEvent(BotEventType.SILENCIO, target)
            }
        }
        return null
    }

    fun reactionsToEvent(
        session: GameSession,
        event: BotEvent,
        limit: Int = 3
    ): List<Pair<String, String>> {
        if (session.winner.isNotBlank() || recentBotStreak(session) >= 3) return emptyList()
        val boundedLimit = limit.coerceIn(1, 3)
        val target = event.target
        val suspects = GameEngine.alivePlayers(session)
            .filter { it.name != target }
            .sortedWith(
                compareByDescending<GamePlayer> { player ->
                    rankedPublicSuspects(session, player).firstOrNull()?.score ?: 0
                }.thenBy { stableNoise("${session.code}:${session.round}:event-suspect:${event.type}:${it.name}") }
            )
        val fallback = suspects.firstOrNull()?.let { safeName(it, session) } ?: "alguien"
        return messageBots(session, boundedLimit)
            .mapIndexed { index, bot ->
                val line = eventReactionLine(session, bot, event, fallback, index)
                bot.name to finishSpeech(line, session, bot, "event:${event.type}:$target:$index")
            }
            .distinctBy { normalizedForParsing(it.second).take(42) }
    }

    fun openingDebateMessages(session: GameSession, limit: Int = 5): List<Pair<String, String>> {
        val mutedNames = session.players.filter { it.alive && it.muted }.map { safeName(it, session) }
        val noDeath = GameplayTextMarkers.contains(session.publicAnnouncement, "no murió nadie")
        val dawnVictim = eventTarget(session, session.publicAnnouncement, "murió")
        val expelled = latestExpelledTarget(session)
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).getOrNull(index)
                ?: rankedPublicSuspects(session, bot).firstOrNull()
            val target = speechTarget(session, bot, read)
            val contextSeed = "opening:$index:${session.phaseIndex}:${socialChatSize(session)}"
            val reason = informalReason(read?.reason(), contextSeed)
            val muted = mutedNames.lastOrNull()
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val fakeClaim = traitorFakeClaimLine(session, bot, social, index)
            val roleLine = roleDrivenLine(session, bot, read, social, index)
            val agenda = agendaFor(session, bot)
            val weakRead = isWeakSuspicion(read)
            val objective = roundObjectiveFor(session, bot)
            val objectiveLine = objectiveLine(session, bot, objective, index)
            val conversationRole = conversationRole(index)
            val coordinationLine = coordinationLine(
                session = session,
                bot = bot,
                role = conversationRole,
                target = target,
                reason = reason,
                hasThread = social.ignoredBy != null || social.pressured != null || contradiction != null
            )
            val agendaLine = agendaLine(session, bot, agenda, target, reason, weakRead, index)
            val playerLine = playerFocusLine(session, bot, target, reason, weakRead, index)
            val baseIntent = openingIntent(session, bot, index)
            val intent = if (
                baseIntent in listOf(BotSpeechIntent.ACCUSE, BotSpeechIntent.TEASE) &&
                (read?.score ?: 0) < 8
            ) {
                BotSpeechIntent.ASK
            } else {
                baseIntent
            }.let { base ->
                coordinatedIntent(
                    session = session,
                    base = base,
                    role = conversationRole(index),
                    hasStrongRead = !weakRead,
                    hasThread = social.ignoredBy != null || social.pressured != null || contradiction != null
                )
            }.let { toneAdjustedIntent(session, it) }
            val hardLine = hardOpeningLine(session, bot, conversationRole, target, index)
            val line = when {
                contradiction != null && index <= 1 ->
                    contradictionLine(read.player.name, contradiction)
                dawnVictim != null && index == 0 ->
                    "lo de $dawnVictim anoche cambia todo, $target explica bien pq $reason"
                noDeath && index == 0 ->
                    "no murió nadie pero no nos durmamos, $target vos q hiciste ayer?"
                social.failedPush != null ->
                    "ayer me pude haber equivocado con ${social.failedPush}, hoy quiero escuchar mas antes de mandar fruta"
                social.ignoredBy != null ->
                    "${social.ignoredBy} me sigue debiendo una respuesta de antes"
                expelled != null && index == 1 ->
                    "ayer sacamos a $expelled y seguimos igual, no votemos por inercia"
                roleLine != null -> roleLine
                coordinationLine != null -> coordinationLine
                botToBotLine(session, bot, index) != null ->
                    botToBotLine(session, bot, index).orEmpty()
                muted != null && index == 0 ->
                    "bueno $muted no puede contestar, $target vos q onda? bancas lo q dijiste?"
                fakeClaim != null -> fakeClaim
                objectiveLine != null -> objectiveLine
                hardLine != null -> hardLine
                playerLine != null -> playerLine
                agendaLine != null -> agendaLine
                weakRead -> lowEvidenceOpeningLine(session, bot, index)
                isTraitor(bot) && social.heated && index == 0 ->
                    traitorDeflectionLine(session, bot, target, reason)
                else -> lineForIntent(session, bot, intent, target, reason, contextSeed)
            }
            val allowRoleTerms = line.contains("soy ", ignoreCase = true) ||
                line.contains("rol", ignoreCase = true)
            bot.name to finishSpeech(line, session, bot, "opening:$index", allowRoleTerms = allowRoleTerms)
        }.dedupeBotMessages()
    }

    fun votingIntentMessages(session: GameSession, limit: Int = 4): List<Pair<String, String>> {
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).firstOrNull()
            val votePlan = conversationVotePlan(session, bot)
            val target = votePlan?.target ?: speechTarget(session, bot, read)
            val role = conversationRole(index)
            val contextSeed = "vote:$index:${session.phaseIndex}:${socialChatSize(session)}"
            val reason = votePlan?.reason ?: informalReason(read?.reason(), contextSeed)
            val claim = read?.player?.name?.let { latestClaimBySpeaker(session, it) }
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val templates = if (votePlan != null && votePlan.beats >= 3 && role == BotConversationRole.OPENER) {
                listOf(
                    "voy con $target por toda la secuencia: $reason",
                    "para mi el voto sale de esto: $reason",
                    "no es una corazonada, $target viene mal por $reason"
                )
            } else if (role == BotConversationRole.CALMER) {
                listOf(
                    "si votan a $target que sea por $reason, no por manada",
                    "yo todavía quiero una respuesta más antes de cerrar con $target",
                    "ojo con apurarnos, $target tiene que explicar $reason"
                )
            } else if (role == BotConversationRole.SKEPTIC && votePlan != null) {
                listOf(
                    "$target me hace ruido por $reason, pero no lo venderia como seguro",
                    "puede ser $target, igual quiero escuchar si alguien lo banca",
                    "tengo a $target arriba, pero no me gusta votar sin ultima respuesta"
                )
            } else if (role == BotConversationRole.FOLLOWER && votePlan != null) {
                listOf(
                    "acompaño lo de $target por ahora, $reason",
                    "si el voto va por $target, para mi el motivo es $reason",
                    "me suma el voto a $target, pero que quede claro por que"
                )
            } else if (votePlan != null && votePlan.confidence >= 10) {
                listOf(
                    "voy con $target, $reason",
                    "mi voto va a $target por esto: $reason",
                    "si hay que cerrar, cierro con $target. $reason"
                )
            } else if (votePlan != null) {
                listOf(
                    "tengo a $target arriba, pero no estoy casado con eso",
                    "por ahora voto a $target porque $reason",
                    "$target es mi mejor opcion, aunque falta una respuesta mas"
                )
            } else if (claim != null) {
                listOf(
                    "antes de votar quiero q $target cierre lo del rol",
                    "si $target no explica ese claim yo voy por ahi",
                    "$target tira rol pero falta detalle, ojo"
                )
            } else if (social.ignoredBy != null && mentionsName(target, social.ignoredBy)) {
                listOf(
                    "voto a $target porque le pregunte y esquivo todo",
                    "$target dejo preguntas colgadas, para mi va por ahi",
                    "voy con $target, si era bueno tenia q contestar algo"
                )
            } else if (social.pressured != null && mentionsName(target, social.pressured)) {
                listOf(
                    "lo voto a $target porque le pregunte y nunca termino de cerrar",
                    "voy con $target, vengo marcando eso hace rato",
                    "si sale mal me hago cargo, pero $target no respondió bien"
                )
            } else if (social.failedPush != null) {
                listOf(
                    "ayer le erre con ${social.failedPush}, hoy no quiero votar apurado",
                    "si votamos mal de nuevo mañana revisen quien empujo esto",
                    "voto a $target pero no estoy tan cerrado, $reason"
                )
            } else {
                listOf(
                    "si no cambia nada voto a $target, $reason",
                    "yo hoy estoy para votar a $target pq $reason",
                    "para mi es $target eh, $reason",
                    "nose ustedes pero yo voy con $target, $reason"
                )
            }
            val line = if (contradiction != null && index <= 1) {
                contradictionVoteLine(target, contradiction)
            } else {
                templates[
                    stableNoise("${session.code}:${session.round}:${bot.name}:vote:$index:${socialChatSize(session)}") % templates.size
                ].let { base ->
                    val hesitationSeed = stableNoise("${session.code}:${session.round}:${bot.name}:hesitate:$index:${socialChatSize(session)}")
                    val shouldHesitate = session.botDifficulty == BotDifficulty.NORMAL &&
                        hesitationSeed % 5 == 0 &&
                        social.pressured != target
                    if (shouldHesitate) {
                        "$base, igual puedo estar flasheando"
                    } else {
                        base
                    }
                }
            }
            bot.name to finishSpeech(line, session, bot, "vote:$index")
        }.dedupeBotMessages()
    }

    fun reactionsToHumanMessage(session: GameSession, humanMessage: String): List<Pair<String, String>> {
        val focusNames = mentionedPlayerNames(session, humanMessage).toSet()
        val roleClaim = LocalBotAi.roleClaimFrom(humanMessage)
        val publicStatement = LocalBotAi.publicStatementFrom(session, humanMessage)
        val claimResponder = roleClaim?.let { botWithRole(session, it.roleKey) }
        val claimsHiddenInfo = containsSecretTerm(humanMessage, session)
        val casualMessage = isCasualHumanMessage(humanMessage)
        val questionKind = humanQuestionKind(humanMessage)
        val answeredQuestion = answeredQuestionForHuman(session, humanMessage)
        val messageIntent = if (answeredQuestion != null && humanMessage.trim().length >= 4) {
            HumanMessageIntent.ANSWER_PENDING
        } else {
            humanMessageIntent(
            session = session,
            message = humanMessage,
            roleClaim = roleClaim,
            publicStatement = publicStatement,
            claimsHiddenInfo = claimsHiddenInfo,
            casualMessage = casualMessage,
            questionKind = questionKind
            )
        }
        val desiredReplyCount = when {
            messageIntent in setOf(
                HumanMessageIntent.ROLE_CLAIM,
                HumanMessageIntent.ROLE_QUESTION,
                HumanMessageIntent.ACTION_HELP,
                HumanMessageIntent.VOTE_HELP,
                HumanMessageIntent.SUSPECT_HELP,
                HumanMessageIntent.ACCUSE,
                HumanMessageIntent.DEFEND
            ) || focusNames.isNotEmpty() -> 3
            publicStatement != null ||
                claimsHiddenInfo ||
                messageIntent == HumanMessageIntent.ANSWER_PENDING ||
                humanMessage.length > 45 -> 2
            else -> 1
        }
        val replyCount = limitedReplyCount(session, desiredReplyCount)
        val preferredResponder = claimResponder?.name ?: answeredQuestion?.speaker
        return messageBots(session, replyCount, preferredFirst = preferredResponder).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot, focusNames).firstOrNull()
            val memory = memoryFor(session, bot).let { currentMemory ->
                if (answeredQuestion != null) {
                    currentMemory.copy(pendingHumanQuestion = answeredQuestion)
                } else {
                    currentMemory
                }
            }
            val baseTarget = speechTarget(session, bot, read)
            val contextSeed = "reply:$index:${session.phaseIndex}:${socialChatSize(session)}:${humanMessage.length}"
            val reason = informalReason(read?.reason(), contextSeed)
            val mood = moodFor(session, bot, humanMessage)
            val baseIntent = reactionIntent(session, bot, humanMessage, focusNames, mood, index, memory)
            val intent = coordinatedIntent(
                session = session,
                base = baseIntent,
                role = conversationRole(index),
                hasStrongRead = read != null && !isWeakSuspicion(read),
                hasThread = memory.unansweredTarget != null || memory.pendingHumanQuestion != null
            ).let { toneAdjustedIntent(session, it) }
            val target = if (intent == BotSpeechIntent.FOLLOW_UP && memory.lastPressuredTarget != null) {
                memory.lastPressuredTarget
            } else {
                baseTarget
            }
            val unanswered = memory.unansweredTarget
                ?.takeUnless { unansweredTarget -> unansweredTarget == bot.name }
            val claimLine = roleClaim?.let { claim ->
                roleClaimReaction(session, bot, claim, claimResponder, index)
            }
            val claimStatementLine = if (claimLine == null || bot.name != claimResponder?.name) {
                roleClaimStatementReaction(session, roleClaim, publicStatement, index)
            } else {
                null
            }
            val statementLine = publicStatement?.let { statement ->
                actionContradiction(session, GameEngine.humanPlayer(session).name, statement)
                    ?.let { contradictionLine(GameEngine.humanPlayer(session).name, it) }
                    ?: statementReaction(statement, index)
            }
            val line = when {
                claimStatementLine != null -> claimStatementLine
                claimLine != null -> claimLine
                messageIntent == HumanMessageIntent.ANSWER_PENDING ->
                    pendingAnswerReply(session, bot, humanMessage, memory, index)
                messageIntent == HumanMessageIntent.ACCUSE && focusNames.contains(bot.name) ->
                    defensiveLine(session, bot, mood)
                statementLine != null -> statementLine
                questionKind != null -> humanQuestionReply(session, bot, questionKind, read, index)
                casualMessage -> casualHumanReply(session, bot, humanMessage, index)
                unanswered != null && (
                    intent == BotSpeechIntent.FOLLOW_UP ||
                        messageIntent in setOf(HumanMessageIntent.ACCUSE, HumanMessageIntent.DOUBT, HumanMessageIntent.OTHER)
                    ) -> "$unanswered igual sigo esperando esa respuesta"
                messageIntent == HumanMessageIntent.DOUBT ->
                    humanDoubtReply(session, bot, read, index)
                claimsHiddenInfo && index == 0 ->
                    "para para, no demos cartas por hechas. decime q hizo y listo"
                claimsHiddenInfo ->
                    "$target me hace ruido por lo q vimos nomas, $reason"
                focusNames.contains(bot.name) ->
                    defensiveLine(session, bot, mood)
                else -> lineForIntent(session, bot, intent, target, reason, contextSeed)
            }
            bot.name to finishSpeech(
                line,
                session,
                bot,
                "reply:$index:${humanMessage.length}",
                allowRoleTerms = roleClaim != null
            )
        }
    }

    internal fun roleClaimFrom(message: String): RoleClaim? {
        val text = normalizedForParsing(message)
        return roleAliases.entries.firstOrNull { (_, aliases) ->
            aliases.any { alias ->
                Regex("(^|\\s)(soy|tengo|me toco)(\\s+el|\\s+la)?\\s+$alias(\\s|$)").containsMatchIn(text)
            }
        }?.let { (roleKey, aliases) ->
            RoleClaim(roleKey, aliases.first())
        }
    }

    internal fun publicStatementFrom(session: GameSession, message: String): PublicStatement? {
        val text = normalizedForParsing(message)
        val target = mentionedPlayerNames(session, message).firstOrNull()
        val targetText = target?.let { normalizedForParsing(it) }
        return when {
            Regex("(^|\\s)(protegi|cuide|salve|cure)(\\s+a)?\\s+").containsMatchIn(text) ->
                PublicStatement(StatementType.PROTECTED, target)
            text.contains("no digo mi rol") ||
                text.contains("no voy a decir rol") ||
                text.contains("no quiero decir rol") ||
                text.contains("no revelo rol") ||
                text.contains("no voy a revelar") ||
                text.contains("prefiero no decir") ||
                text.contains("no digo rol") ->
                PublicStatement(StatementType.REFUSED_ROLE)
            target != null && (
                text.contains("confio en $targetText") ||
                    text.contains("banco a $targetText") ||
                    text.contains("$targetText es limpio") ||
                    text.contains("$targetText es inocente") ||
                    text.contains("$targetText salio inocente") ||
                    text.contains("$targetText dio inocente") ||
                    text.contains("$targetText me dio inocente") ||
                    text.contains("es inocente") ||
                    text.contains("salio inocente") ||
                    text.contains("$targetText no me parece raro") ||
                    text.contains("$targetText no es") ||
                    text.contains("no votaria a $targetText") ||
                    text.contains("me dio inocente") ||
                    text.contains("dio inocente")
                ) ->
                PublicStatement(StatementType.TRUST, target)
            target != null && (
                text.contains("$targetText miente") ||
                    text.contains("no confio en $targetText") ||
                    text.contains("$targetText esta raro") ||
                    text.contains("$targetText es raro") ||
                    text.contains("$targetText me hace ruido") ||
                    text.contains("$targetText es culpable") ||
                    text.contains("$targetText salio culpable") ||
                    text.contains("$targetText dio culpable") ||
                    text.contains("$targetText me dio culpable") ||
                    text.contains("$targetText es sospechoso") ||
                    text.contains("$targetText salio sospechoso") ||
                    text.contains("$targetText dio sospechoso") ||
                    text.contains("$targetText me dio sospechoso") ||
                    text.contains("$targetText es traidor") ||
                    text.contains("$targetText salio traidor") ||
                    text.contains("$targetText me dio traidor") ||
                    text.contains("es culpable") ||
                    text.contains("salio culpable") ||
                    text.contains("es sospechoso") ||
                    text.contains("salio sospechoso") ||
                    text.contains("es traidor") ||
                    text.contains("salio traidor") ||
                    text.contains("sospecho de $targetText") ||
                    text.contains("para mi es $targetText") ||
                    text.contains("me dio culpable") ||
                    text.contains("dio culpable") ||
                    text.contains("me dio sospechoso") ||
                    text.contains("dio sospechoso") ||
                    text.contains("me dio traidor") ||
                    text.contains("dio traidor")
                ) ->
                PublicStatement(StatementType.ACCUSE, target)
            target != null && (
                text.contains("voto a $targetText") ||
                text.contains("votaria a $targetText") ||
                    text.contains("voy con $targetText")
                ) ->
                PublicStatement(StatementType.VOTE, target)
            Regex("(^|\\s)(investigue|revise|mire|pregunte)(\\s+a|\\s+por)?\\s+").containsMatchIn(text) ->
                PublicStatement(StatementType.INVESTIGATED, target)
            else -> null
        }
    }

}

internal fun conversationVotePlan(session: GameSession, voter: GamePlayer): VotePlan? {
    val aliveNames = voteCandidatesFor(session, voter)
        .filter { it.name != voter.name }
        .map { it.name }
        .toSet()
    if (aliveNames.isEmpty()) return null

    val social = socialRead(session, voter)
    val ranked = rankedPublicSuspects(session, voter)
    val rawPlans = mutableListOf<VotePlan>()

    aliveNames.forEach { name ->
        publicContradiction(session, name)?.let { contradiction ->
            val reason = if (contradiction.latest.roleKey != null) {
                "se contradijo con el rol"
            } else {
                "cambio la version de su accion"
            }
            rawPlans += VotePlan(name, reason, 18)
        }
    }

    ranked.forEach { read ->
        val name = read.player.name
        latestClaimBySpeaker(session, name)?.let { claim ->
            if (publicClaimants(session, claim.roleKey).size > 1) {
                rawPlans += VotePlan(name, "hay doble claim y uno esta mintiendo", 15)
            }
        }
    }

    social.ignoredBy
        ?.takeIf { it in aliveNames }
        ?.let { target ->
            if (hasUsefulPublicRead(session, target)) {
                rawPlans += VotePlan(target, "respondió a medias pero dejó una pista", 5)
            } else {
                rawPlans += VotePlan(target, "dejo una pregunta colgada", 12)
            }
        }
    social.pressured
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "viene esquivando una presion de antes", 10) }

    votePluralityTarget(session, voter)
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "el pueblo ya lo esta empujando", 8) }

    humanSuggestedVoteTarget(session)
        ?.takeIf { it in aliveNames }
        ?.let { target ->
            val confidence = if (session.botDifficulty == BotDifficulty.HARD) 11 else 9
            rawPlans += VotePlan(target, "vos lo marcaste y merece respuesta", confidence)
        }

    rawPlans += historicalVotePlans(session, voter, ranked, aliveNames)

    roundObjectiveFor(session, voter).takeIf {
        it.type in setOf(RoundObjectiveType.PUSH_VOTE, RoundObjectiveType.FOLLOW_CONTRADICTION) &&
            it.target in aliveNames
    }?.let { objective ->
        rawPlans += VotePlan(
            objective.target.orEmpty(),
            objective.reason.ifBlank { "es el hilo mas fuerte de la ronda" },
            objective.confidence.coerceAtLeast(10)
        )
    }

    declaredSuspicionTarget(session, voter)
        ?.takeIf { it in aliveNames }
        ?.let { rawPlans += VotePlan(it, "yo ya venia marcando eso", 9) }

    ranked.firstOrNull()?.takeIf { it.score >= 9 }?.let { read ->
        rawPlans += VotePlan(
            read.player.name,
            informalReason(read.reason(), "vote-plan:${voter.name}"),
            read.score.coerceIn(9, 13)
        )
    }

    val plans = rawPlans
        .map { plan ->
            if (hasUsefulPublicRead(session, plan.target)) {
                plan.copy(
                    reason = "dejo una pista, aunque falta cerrar su explicacion",
                    confidence = (plan.confidence - 14).coerceIn(1, 3)
                )
            } else {
                plan
            }
        }
        .filter { plan -> plan.confidence >= 4 && canVotePlanTarget(session, voter, plan) }
        .distinctBy { it.target to it.reason }
    return choosePlanForDifficulty(session, voter, plans)
}

internal fun choosePlanForDifficulty(
    session: GameSession,
    voter: GamePlayer,
    plans: List<VotePlan>
): VotePlan? {
    val sorted = plans.sortedWith(
        compareByDescending<VotePlan> { it.confidence }
            .thenByDescending { it.beats }
            .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.target}:vote-plan") }
            .thenBy { it.target }
    )
    if (session.botDifficulty == BotDifficulty.HARD) return sorted.firstOrNull()
    val top = sorted.firstOrNull() ?: return null
    val closePlans = sorted
        .drop(1)
        .filter { plan -> top.confidence - plan.confidence <= 3 }
    if (closePlans.isEmpty()) return top
    val seed = stableNoise("${session.code}:${session.round}:${voter.name}:normal-vote-wobble:${socialChatSize(session)}")
    return if (seed % 4 == 0) {
        closePlans[seed % closePlans.size]
    } else {
        top
    }
}

internal fun historicalVotePlans(
    session: GameSession,
    voter: GamePlayer,
    ranked: List<SuspectRead>,
    aliveNames: Set<String>
): List<VotePlan> {
    val memory = conversationMemory(session)
    val plurality = votePluralityTarget(session, voter)
    val humanTarget = humanSuggestedVoteTarget(session)
    val declared = declaredSuspicionTarget(session, voter)
    val expelled = latestExpelledTarget(session)
    return aliveNames.mapNotNull { name ->
        val playerMemory = memory[name]
        val reasons = mutableListOf<String>()
        var confidence = 0

        publicContradiction(session, name)?.let { contradiction ->
            confidence += if (contradiction.latest.roleKey != null) {
                if (session.botDifficulty == BotDifficulty.HARD) 20 else 16
            } else {
                if (session.botDifficulty == BotDifficulty.HARD) 17 else 13
            }
            reasons += if (contradiction.latest.roleKey != null) {
                "cambio el claim de rol"
            } else {
                "cambio la historia de su accion"
            }
        }

        latestClaimBySpeaker(session, name)?.let { claim ->
            if (publicClaimants(session, claim.roleKey).size > 1) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 16 else 12
                reasons += "hay doble claim"
            } else if (!hasUsefulPublicRead(session, name)) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 3 else 4
                reasons += "tiro rol y falta detalle"
            }
        }

        playerMemory?.pendingQuestionFrom?.let {
            if (hasUsefulPublicRead(session, name)) {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
                reasons += "respondió a medias pero dejó una pista"
            } else {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 13 else 10
                reasons += "dejo una pregunta colgada"
            }
        }
        val accusers = playerMemory?.accusedBy.orEmpty().size
        if (accusers >= 2) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) {
                (accusers * 5).coerceAtMost(14)
            } else {
                (accusers * 4).coerceAtMost(12)
            }
            reasons += "lo marcaron varios"
        } else if (accusers == 1) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 4 else 5
            reasons += "alguien lo marco"
        }

        val defenders = playerMemory?.defendedBy.orEmpty().size
        if (defenders >= 2) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
            reasons += "lo estan bancando demasiado"
        } else if (defenders == 1) {
            confidence -= 1
        }

        when (playerMemory?.latestStatement?.type) {
            StatementType.REFUSED_ROLE -> {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 6 else 4
                reasons += "esquivo el rol"
            }
            StatementType.PROTECTED,
            StatementType.INVESTIGATED -> {
                confidence += if (session.botDifficulty == BotDifficulty.HARD) 4 else 2
                reasons += "dio info a medias"
            }
            else -> Unit
        }

        if (name == plurality) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 5 else 9
            reasons += "ya junta votos"
        }
        if (name == humanTarget) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) 8 else 6
            reasons += "vos lo marcaste"
        }
        if (name == declared) {
            confidence += 6
            reasons += "vengo marcando eso"
        }
        if (expelled != null && pushedPublicTarget(session, name, expelled)) {
            confidence += 5
            reasons += "empujo mal ayer"
        }
        if (followedPluralityWithoutReason(session, name)) {
            confidence += 4
            reasons += "se subio al monton sin explicar"
        }

        val rankedRead = ranked.firstOrNull { it.player.name == name }
        if (rankedRead != null && rankedRead.score >= 7) {
            confidence += if (session.botDifficulty == BotDifficulty.HARD) {
                (rankedRead.score / 2).coerceAtMost(7)
            } else {
                (rankedRead.score / 3).coerceAtMost(4)
            }
            reasons += informalReason(rankedRead.reason(), "history-vote:${voter.name}:$name")
        }

        val distinctReasons = reasons.distinct()
        if (session.botDifficulty == BotDifficulty.HARD && distinctReasons.size >= 2) {
            confidence += 3
        }
        val minimumConfidence = if (session.botDifficulty == BotDifficulty.HARD) 10 else 8
        if (confidence < minimumConfidence || distinctReasons.isEmpty()) return@mapNotNull null
        VotePlan(
            target = name,
            reason = historyReason(distinctReasons),
            confidence = confidence.coerceAtMost(if (session.botDifficulty == BotDifficulty.HARD) 28 else 22),
            beats = distinctReasons.size
        )
    }
}

internal fun canVotePlanTarget(session: GameSession, voter: GamePlayer, plan: VotePlan): Boolean {
    val target = GameEngine.playerByName(session, plan.target) ?: return false
    if (!target.alive || target.name == voter.name) return false
    if (!isTraitor(voter) || !isTraitor(target)) return true
    if (plan.confidence >= 17 && session.botDifficulty == BotDifficulty.NORMAL) {
        return stableNoise("${session.code}:${session.round}:${voter.name}:${target.name}:traitor-bus") % 5 == 0
    }
    return plan.confidence >= 17 && session.botDifficulty == BotDifficulty.HARD
}

internal fun votePluralityTarget(session: GameSession, voter: GamePlayer): String? {
    val votes = session.votes
        .filterKeys { it != voter.name }
        .values
        .filter { name -> GameEngine.playerByName(session, name)?.alive == true }
    return votes
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= 2 }
        .maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { -stableNoise("${session.code}:${session.round}:${it.key}:plurality") }
        )
        ?.key
}

internal fun humanSuggestedVoteTarget(session: GameSession): String? {
    val human = GameEngine.humanPlayer(session)
    return recentPublicMessages(session)
        .asReversed()
        .filter { it.speaker == human.name }
        .mapNotNull { LocalBotAi.publicStatementFrom(session, it.message) }
        .firstOrNull { it.type == StatementType.ACCUSE || it.type == StatementType.VOTE }
        ?.target
}

internal fun historyReason(reasons: List<String>): String {
    val primary = reasons.take(3)
    return when (primary.size) {
        0 -> "la historia de la ronda lo deja mal"
        1 -> primary.first()
        2 -> "${primary[0]} y ${primary[1]}"
        else -> "${primary[0]}, ${primary[1]} y ${primary[2]}"
    }
}

internal fun pushedPublicTarget(session: GameSession, speaker: String, target: String): Boolean {
    return recentPublicMessages(session).any { message ->
        message.speaker == speaker &&
            mentionsName(message.message, target) &&
            (
                hasAnySignal(message.message, accusationWords) ||
                    LocalBotAi.publicStatementFrom(session, message.message)?.type in setOf(StatementType.ACCUSE, StatementType.VOTE)
                )
    }
}

internal fun followedPluralityWithoutReason(session: GameSession, speaker: String): Boolean {
    val voteTarget = session.votes[speaker] ?: return false
    val plurality = session.votes
        .values
        .filter { target -> GameEngine.playerByName(session, target)?.alive == true }
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= 2 }
        .maxByOrNull { it.value }
        ?.key
        ?: return false
    if (voteTarget != plurality) return false
    return recentPublicMessages(session)
        .filter { it.speaker == speaker }
        .none { message ->
            mentionsName(message.message, voteTarget) &&
                (
                    hasAnySignal(message.message, accusationWords) ||
                        message.message.contains("porque", ignoreCase = true) ||
                        message.message.contains("pq", ignoreCase = true)
                    )
        }
}

internal fun relationshipReads(session: GameSession, bot: GamePlayer): List<RelationshipRead> {
    return GameEngine.alivePlayers(session)
        .filter { it.name != bot.name }
        .map { player -> relationshipRead(session, bot, player) }
        .sortedWith(
            compareByDescending<RelationshipRead> { it.score }
                .thenBy { stableNoise("${session.code}:${session.round}:${bot.name}:${it.player.name}:relationship") }
                .thenBy { it.player.name }
        )
}

internal fun relationshipRead(
    session: GameSession,
    bot: GamePlayer,
    player: GamePlayer
): RelationshipRead {
    val suspectRead = scoreCandidate(session, bot, player, emptySet())
    val tableMemory = conversationMemory(session)
    val playerMemory = tableMemory[player.name]
    val reasons = suspectRead.reasons.toMutableList()
    var score = suspectRead.score

    publicContradiction(session, player.name)?.let { contradiction ->
        score += if (contradiction.latest.roleKey != null) 10 else 8
        reasons += if (contradiction.latest.roleKey != null) {
            "se contradijo de rol"
        } else {
            "cambio su accion"
        }
    }

    latestClaimBySpeaker(session, player.name)?.let { claim ->
        if (publicClaimants(session, claim.roleKey).size > 1) {
            score += 8
            reasons += "hay doble claim"
        }
    }

    if (playerMemory?.accusedTargets?.contains(bot.name) == true) {
        score += 3
        reasons += "me marco antes"
    }
    if (playerMemory?.defendedTargets?.contains(bot.name) == true) {
        score -= 3
        reasons += "me banco antes"
    }
    if (!playerMemory?.accusedBy.isNullOrEmpty()) {
        score += playerMemory?.accusedBy.orEmpty().size.coerceAtMost(3) * 2
        reasons += "lo marcaron varios"
    }
    if (!playerMemory?.defendedBy.isNullOrEmpty()) {
        score -= playerMemory?.defendedBy.orEmpty().size.coerceAtMost(2)
        reasons += "alguien lo banco"
    }
    playerMemory?.pendingQuestionFrom?.let { speaker ->
        score += if (speaker == bot.name) 5 else 3
        reasons += "dejo una pregunta colgada"
    }
    playerMemory?.roleClaim?.let {
        score += 1
        reasons += "declaro rol"
    }

    if (player.isHuman && pendingQuestionForHuman(session) != null && !hasUsefulPublicRead(session, player.name)) {
        score += 2
        reasons += "debe una respuesta"
    }

    session.votes[player.name]?.takeIf { it == bot.name }?.let {
        score += 4
        reasons += "me voto"
    }
    session.votes[bot.name]?.takeIf { it == player.name }?.let {
        score += 2
        reasons += "yo ya lo venia votando"
    }

    val level = when {
        score <= -2 -> TrustLevel.CONFIA
        score <= 3 -> TrustLevel.NEUTRAL
        score <= 7 -> TrustLevel.DUDA
        score <= 12 -> TrustLevel.SOSPECHA
        else -> TrustLevel.PRESIONA
    }
    return RelationshipRead(
        player = player,
        level = level,
        score = score,
        reason = relationshipReason(reasons)
    )
}

internal fun relationshipReason(reasons: List<String>): String {
    val priority = listOf(
        "se contradijo de rol",
        "cambio su accion",
        "hay doble claim",
        "dejo una pregunta colgada",
        "debe una respuesta",
        "lo marcaron varios",
        "me voto",
        "yo ya lo venia votando",
        "me marco antes",
        "me banco antes",
        "alguien lo banco",
        "declaro rol"
    )
    return priority.firstOrNull { it in reasons }
        ?: reasons.firstOrNull()
        ?: "no tengo lectura fuerte"
}

internal fun roundObjectiveFor(session: GameSession, bot: GamePlayer): RoundObjective {
    val agenda = agendaFor(session, bot)
    val reads = relationshipReads(session, bot)
    val strongest = reads.firstOrNull()
    val human = GameEngine.humanPlayer(session).takeIf { it.alive }
    val humanRead = human?.let { target -> reads.firstOrNull { it.player.name == target.name } }
    val contradiction = reads.firstOrNull { publicContradiction(session, it.player.name) != null }
    val tableMemory = conversationMemory(session)
    val unansweredTarget = tableMemory.entries.firstOrNull { (_, memory) ->
        memory.pendingQuestionFrom == bot.name
    }?.key

    if (unansweredTarget != null) {
        val targetPlayer = reads.firstOrNull { it.player.name == unansweredTarget }?.player
            ?: GameEngine.playerByName(session, unansweredTarget)
        if (targetPlayer != null && targetPlayer.name != bot.name && targetPlayer.alive) {
            val read = reads.firstOrNull { it.player.name == unansweredTarget }
            return RoundObjective(
                type = RoundObjectiveType.FOLLOW_CONTRADICTION,
                target = safeName(targetPlayer, session),
                reason = "dejo una pregunta colgada",
                confidence = (read?.score ?: 8).coerceAtLeast(8)
            )
        }
    }

    if (isTraitor(bot) && socialRead(session, bot).heated) {
        val target = reads.firstOrNull { !isTraitor(it.player) } ?: strongest
        return RoundObjective(
            type = RoundObjectiveType.DEFLECT_PRESSURE,
            target = target?.player?.let { safeName(it, session) },
            reason = target?.reason.orEmpty(),
            confidence = target?.score ?: 0
        )
    }

    if (contradiction != null && contradiction.score >= 8) {
        return RoundObjective(
            type = RoundObjectiveType.FOLLOW_CONTRADICTION,
            target = safeName(contradiction.player, session),
            reason = contradiction.reason,
            confidence = contradiction.score
        )
    }

    if (
        strongest != null &&
        strongest.level in setOf(TrustLevel.PRESIONA, TrustLevel.SOSPECHA) &&
        agenda in setOf(BotAgenda.PUSH_VOTE, BotAgenda.FOLLOW_THREAD, BotAgenda.ASK_ROLES)
    ) {
        return RoundObjective(
            type = RoundObjectiveType.PUSH_VOTE,
            target = safeName(strongest.player, session),
            reason = strongest.reason,
            confidence = strongest.score
        )
    }

    if (
        humanRead != null &&
        humanRead.level in setOf(TrustLevel.NEUTRAL, TrustLevel.DUDA) &&
        GameEngine.canSpeak(session, humanRead.player)
    ) {
        return RoundObjective(
            type = RoundObjectiveType.ASK_PLAYER,
            target = safeName(humanRead.player, session),
            reason = humanRead.reason,
            confidence = humanRead.score
        )
    }

    if (agenda == BotAgenda.DEFEND_WEAK) {
        val trusted = reads.lastOrNull { it.level in setOf(TrustLevel.CONFIA, TrustLevel.NEUTRAL) }
        if (trusted != null) {
            return RoundObjective(
                type = RoundObjectiveType.DEFEND_PLAYER,
                target = safeName(trusted.player, session),
                reason = trusted.reason,
                confidence = trusted.score
            )
        }
    }

    return RoundObjective(RoundObjectiveType.CALM_TABLE)
}

internal fun debugVoteCommandTarget(session: GameSession, voter: GamePlayer): String? {
    if (!session.debugBotsObeyVoteCommands || voter.isHuman) return null
    val human = session.players.firstOrNull { it.isHuman && it.alive } ?: return null
    val message = session.chatHistory
        .asReversed()
        .firstOrNull { !it.isGod && it.speaker == human.name }
        ?.message
        ?.let(::normalizedVoteCommand)
        ?: return null
    val targetName = when {
        message.contains("votenme") || message.contains("voten por mi") -> human.name
        else -> session.players
            .filter { it.alive && it.name != voter.name }
            .firstOrNull { player ->
                val name = normalizedVoteCommand(player.name)
                message.contains("voten a $name") ||
                    message.contains("voten por $name")
            }
            ?.name
    } ?: return null
    return targetName.takeIf { name ->
        val target = GameEngine.playerByName(session, name)
        target != null && target.alive && target.name != voter.name
    }
}

internal fun agendaFor(session: GameSession, bot: GamePlayer): BotAgenda {
    if (isTraitor(bot) && socialRead(session, bot).heated) return BotAgenda.DEFLECT_PRESSURE
    return when (personalityFor(session, bot)) {
        BotPersonality.TRANQUI -> BotAgenda.CALM_TABLE
        BotPersonality.PICANTE -> BotAgenda.PUSH_VOTE
        BotPersonality.JODON -> listOf(BotAgenda.FOLLOW_THREAD, BotAgenda.PUSH_VOTE, BotAgenda.CALM_TABLE)[
            stableNoise("${session.code}:${bot.name}:agenda:jodon") % 3
        ]
        BotPersonality.DESCONFIADO -> BotAgenda.ASK_ROLES
        BotPersonality.IMPULSIVO -> BotAgenda.PUSH_VOTE
        BotPersonality.ANALITICO -> when (bot.role?.key) {
            RoleCatalog.POLICIA,
            RoleCatalog.MEDICO,
            RoleCatalog.ORACULO -> BotAgenda.FOLLOW_THREAD
            else -> BotAgenda.DEFEND_WEAK
        }
    }
}

internal fun rankedPublicSuspects(
    session: GameSession,
    voter: GamePlayer,
    focusNames: Set<String> = emptySet()
): List<SuspectRead> {
    return voteCandidatesFor(session, voter)
        .filter { it.name != voter.name }
        .map { candidate -> scoreCandidate(session, voter, candidate, focusNames) }
        .sortedWith(
            compareByDescending<SuspectRead> { it.score }
                .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.player.name}:suspect") }
                .thenBy { it.player.name }
        )
}

internal fun scoreCandidate(
    session: GameSession,
    voter: GamePlayer,
    candidate: GamePlayer,
    focusNames: Set<String>
): SuspectRead {
    val recent = recentPublicMessages(session)
    val reasons = mutableListOf<String>()
    var score = stableNoise("${session.code}:${session.round}:${voter.name}:${candidate.name}:base") % 3

    if (candidate.name in focusNames) {
        score += 8
        reasons += "lo nombraron en el pueblo"
    }

    val mentions = recent.filter { mentionsName(it.message, candidate.name) }
    val accusations = mentions.count { hasAnySignal(it.message, accusationWords) }
    val defenses = mentions.count { hasAnySignal(it.message, defenseWords) }

    if (accusations > 0) {
        score += accusations * 5
        reasons += "le pidieron explicaciones"
    }
    if (mentions.size > accusations) {
        score += 2
        reasons += "aparecio demasiado en la charla"
    }
    if (defenses > 0) {
        score -= defenses * 2
    }

    val statementsAboutCandidate = recent.mapNotNull { message ->
        LocalBotAi.publicStatementFrom(session, message.message)?.takeIf { it.target == candidate.name }
    }
    val statementPressure = statementsAboutCandidate.count {
        it.type == StatementType.ACCUSE || it.type == StatementType.VOTE
    }
    val statementTrust = statementsAboutCandidate.count { it.type == StatementType.TRUST }
    if (statementPressure > 0) {
        score += statementPressure * if (session.botDifficulty == BotDifficulty.HARD) 5 else 4
        reasons += "lo presionaron con algo concreto"
    }
    if (statementTrust > 0) {
        score -= statementTrust * 2
    }
    latestStatementBySpeaker(session, candidate.name)?.let { statement ->
        when (statement.type) {
            StatementType.REFUSED_ROLE -> {
                score += if (session.botDifficulty == BotDifficulty.HARD) 5 else 3
                reasons += "esquivo el rol"
            }
            StatementType.PROTECTED,
            StatementType.INVESTIGATED -> {
                score += 2
                reasons += "tiro dato y falta detalle"
            }
            else -> Unit
        }
    }

    latestClaimBySpeaker(session, candidate.name)?.let { claim ->
        val claimants = publicClaimants(session, claim.roleKey)
        val voterHasClaimedRole = voter.role?.key == claim.roleKey && voter.name != candidate.name
        if (claimants.size > 1 || voterHasClaimedRole) {
            score += if (session.botDifficulty == BotDifficulty.HARD) 14 else 10
            reasons += "hay doble claim"
        } else {
            if (!hasUsefulPublicRead(session, candidate.name)) {
                score += 2
                reasons += "tiro rol y falta detalle"
            }
        }
    }

    publicContradiction(session, candidate.name)?.let { contradiction ->
        if (contradiction.latest.roleKey != null) {
            score += if (session.botDifficulty == BotDifficulty.HARD) 16 else 12
            reasons += "se contradijo de rol"
        } else {
            score += if (session.botDifficulty == BotDifficulty.HARD) 12 else 9
            reasons += "se contradijo con la accion"
        }
    }

    val spokeCount = recent.count { it.speaker == candidate.name }
    when {
        spokeCount == 0 && session.round > 1 && !candidate.isHuman -> {
            score += 1
            reasons += "esta hablando poco"
        }
        spokeCount >= 3 -> {
            score += 1
            reasons += "esta ocupando mucho espacio"
        }
    }
    if (candidate.isHuman && !hasUsefulPublicRead(session, candidate.name)) {
        val bonus = humanDayPressureBonus(session)
        if (bonus > 0) {
            score += bonus
            reasons += if (spokeCount == 0) {
                "esta poco leido"
            } else {
                "falta cerrar su version"
            }
        }
    }

    val voterPressedCandidate = recent.any {
        it.speaker == voter.name && mentionsName(it.message, candidate.name)
    }
    if (voterPressedCandidate) {
        score += 2
        reasons += "ya venia bajo presion"
    }

    return SuspectRead(candidate, score, reasons.distinct())
}

internal fun nightPressureScore(session: GameSession, candidate: GamePlayer): Int {
    val recent = recentPublicMessages(session)
    val spokeCount = recent.count { it.speaker == candidate.name }
    val namedCount = recent.count { mentionsName(it.message, candidate.name) }
    val accusedCount = recent.count {
        mentionsName(it.message, candidate.name) && hasAnySignal(it.message, accusationWords)
    }
    return spokeCount * 3 +
        namedCount -
        accusedCount * 2 +
        stableNoise("${session.code}:${session.round}:${candidate.name}:night") % 2
}

internal fun humanDayPressureBonus(session: GameSession): Int {
    if (session.botDifficulty != BotDifficulty.HARD) return 0
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (4 + roundsElapsed * 2).coerceAtMost(14)
}

internal fun humanPressureChancePercent(session: GameSession): Int {
    val (base, perRound, cap) = if (session.botDifficulty == BotDifficulty.HARD) {
        Triple(12, 7, 45)
    } else {
        Triple(5, 3, 20)
    }
    val roundsElapsed = (session.round - 1).coerceAtLeast(0)
    return (base + perRound * roundsElapsed).coerceAtMost(cap)
}

internal fun humanNightTargetBonus(session: GameSession, candidate: GamePlayer, actionTag: String): Int {
    if (!candidate.isHuman) return 0
    val chance = humanPressureChancePercent(session)
    val roll = stableNoise("${session.code}:${session.round}:$actionTag:human-pressure") % 100
    return if (roll < chance) HUMAN_NIGHT_PRESSURE_BONUS else 0
}

internal fun fallbackTarget(session: GameSession, actor: GamePlayer): String {
    return voteCandidatesFor(session, actor)
        .firstOrNull { it.name != actor.name }
        ?.name
        .orEmpty()
}

internal fun canUseBotVoteTarget(session: GameSession, voter: GamePlayer, targetName: String): Boolean {
    val target = GameEngine.playerByName(session, targetName) ?: return false
    if (!target.alive || target.name == voter.name) return false
    return !session.debugBotsNeverVoteHuman ||
        !target.isHuman ||
        GameEngine.alivePlayers(session).none { it.name != voter.name && !it.isHuman }
}

internal fun voteCandidatesFor(session: GameSession, voter: GamePlayer): List<GamePlayer> {
    return withoutHumanIfDebug(
        session.debugBotsNeverVoteHuman,
        GameEngine.alivePlayers(session).filter { it.name != voter.name }
    )
}

internal fun withoutHumanIfDebug(
    enabled: Boolean,
    candidates: List<GamePlayer>
): List<GamePlayer> {
    if (!enabled) return candidates
    val filtered = candidates.filterNot { it.isHuman }
    return filtered.ifEmpty { candidates }
}

internal fun isTraitor(player: GamePlayer): Boolean {
    return GameRules.isTraitorRole(player.role)
}

internal fun stableNoise(seed: String): Int {
    var value = 17
    seed.forEach { char ->
        value = (value * 31 + char.code) and 0x7fffffff
    }
    return value
}
