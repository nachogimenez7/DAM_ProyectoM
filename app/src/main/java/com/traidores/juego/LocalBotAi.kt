package com.traidores.juego

internal typealias BotEvent = LocalBotAi.BotEvent
internal typealias BotEventType = LocalBotAi.BotEventType

private data class ConversationBatchCache(
    val key: String,
    val messages: List<Pair<String, String>>
)

internal data class SuspectRead(
    val player: GamePlayer,
    val score: Int,
    val reasons: List<String>
) {
    fun reason(): String = reasons.firstOrNull() ?: "su postura todavía no cierra"
}
internal object LocalBotAi {
    private var conversationBatchCache: ConversationBatchCache? = null

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
        return (traitorPlanVotePlan(session, voter) ?: conversationVotePlan(session, voter))?.let { plan ->
            VotePlanSnapshot(
                target = plan.target,
                reason = plan.reason,
                confidence = plan.confidence,
                beats = plan.beats
            )
        }
    }

    fun chooseAssassinTarget(session: GameSession, assassin: GamePlayer): String {
        // Un pedido del aliado humano en el Plan de los Asesinos pesa mas que el plan
        // armado al empezar la noche, porque llega despues.
        TraitorChatRequests.killTarget(session, assassin)?.let { return it }

        session.traitorPlan
            ?.takeIf { it.round == session.round }
            ?.killTarget
            ?.takeIf { GameEngine.isValidKillTarget(session, it, assassin) }
            ?.let { return it }

        return chooseAssassinTargetWithoutPlan(session, assassin)
    }

    internal fun chooseAssassinTargetWithoutPlan(session: GameSession, assassin: GamePlayer): String {
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
        TraitorChatRequests.silenceTarget(session, mercenary)?.let { return it }

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
                    session.chatHistory.count {
                        it.channel == ChatChannel.PUBLICO && !it.isGod && it.speaker == player.name
                    } +
                        session.actionHistory.count { it.actor == player.name }
                }.thenByDescending { player ->
                    session.publicHistory.indexOfLast { it.contains(player.name) }
                }.thenBy { it.name }
            )
            .first()
            .name
    }

    // El Payador bot abre el Contrapunto solo si hay material real (un conflicto entre dos
    // jugadores). Devuelve el par a enfrentar, o null para no malgastar la habilidad.
    fun chooseBotContrapuntoPair(session: GameSession, payador: GamePlayer): Pair<String, String>? {
        val candidates = GameEngine.alivePlayers(session).filter { it.name != payador.name }
        if (candidates.size < 2) return null
        val candidateNames = candidates.map { it.name }.toSet()
        val memory = conversationMemory(session)

        // Preferencia A: un jugador con contradiccion publica vs su antagonista natural.
        candidates.forEach { player ->
            if (publicContradiction(session, player.name) == null) return@forEach
            val playerMemory = memory[player.name]
            val antagonist = (playerMemory?.accusedBy.orEmpty() + playerMemory?.accusedTargets.orEmpty())
                .firstOrNull { it != player.name && it in candidateNames }
            if (antagonist != null) return player.name to antagonist
        }

        // Preferencia B: los dos sospechosos mas calientes, si ambos pesan (umbral alineado
        // con isWeakSuspicion: score < 6 es lectura floja).
        val topTwo = rankedPublicSuspects(session, payador).take(2)
        if (topTwo.size == 2 && topTwo.all { it.score >= 6 }) {
            return topTwo[0].player.name to topTwo[1].player.name
        }
        return null
    }

    // De los dos participantes del Contrapunto, el que la lectura del payador marca mas sospechoso.
    fun chooseBotContrapuntoSuspect(
        session: GameSession,
        payador: GamePlayer,
        participants: List<String>
    ): String {
        return rankedPublicSuspects(session, payador)
            .firstOrNull { it.player.name in participants }
            ?.player
            ?.name
            ?: participants.firstOrNull().orEmpty()
    }

    fun nextTraitorLine(session: GameSession, speaker: String): String? {
        val bot = GameEngine.playerByName(session, speaker)
            ?.takeIf { !it.isHuman && GameEngine.canSeeTraitorChat(it) }
            ?: return null
        val plan = session.traitorPlan
            ?.takeIf { it.round == session.round }
            ?: return null
        val recent = recentTraitorMessages(session)
        val spokenCount = recent.count { it.speaker == speaker }
        val normalizedLines = recent.map { normalizedForParsing(it.message) }
        val cover = plan.cover
        val raw = when {
            normalizedLines.none { it.contains("arranqu") || it.contains("ojo ") || it.contains("mesa") } ->
                traitorPlanOpeningLine(session, bot, plan)
            plan.killTarget.isNotBlank() && normalizedLines.none { line ->
                line.contains(normalizedForParsing(plan.killTarget)) &&
                    (line.contains("baj") || line.contains("cae") || line.contains("matar") || line.contains("objetivo"))
            } ->
                traitorKillProposalLine(session, bot, plan)
            cover != null && shouldSpeakerTakeCoverLine(cover, speaker, normalizedLines) ->
                traitorCoverLine(session, bot, plan, cover)
            plan.dayPushTarget.isNotBlank() && normalizedLines.none { line ->
                line.contains(normalizedForParsing(plan.dayPushTarget)) &&
                    (line.contains("vot") || line.contains("empuj") || line.contains("ensuci") || line.contains("cruz"))
            } ->
                traitorDayPushLine(session, bot, plan)
            spokenCount == 0 || recent.count { !it.isGod } < minimumTraitorPlanLines(session) ->
                traitorAgreementLine(session, bot, plan)
            else -> null
        } ?: return null
        return finishTraitorSpeech(raw, session, bot, "traitor:${recent.size}:$speaker")
    }

    fun traitorReplyToHuman(
        session: GameSession,
        speaker: String,
        humanMessage: String
    ): String? {
        val bot = GameEngine.playerByName(session, speaker)
            ?.takeIf { !it.isHuman && GameEngine.canSeeTraitorChat(it) }
            ?: return null
        val plan = session.traitorPlan?.takeIf { it.round == session.round }
        val human = GameEngine.humanPlayer(session)
        val raw = traitorHumanReplyLine(
            session = session,
            bot = bot,
            plan = plan,
            request = TraitorChatRequests.classify(session, humanMessage),
            humanName = safeName(human, session)
        ) ?: return null
        return finishTraitorSpeech(
            raw,
            session,
            bot,
            "traitor-reply:${recentTraitorMessages(session).size}:$speaker"
        )
    }

    fun chooseVoteTarget(session: GameSession, voter: GamePlayer): String {
        debugVoteCommandTarget(session, voter)
            ?.takeIf { canUseBotVoteTarget(session, voter, it) }
            ?.let { return it }
        traitorPlanVotePlan(session, voter)?.let { return it.target }
        val ranked = rankedPublicSuspects(session, voter)
        conversationVotePlan(session, voter, ranked)?.let { return it.target }
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
        val usefulPublicReads = coordinated.associate { read ->
            read.player.name to hasUsefulPublicRead(session, read.player.name)
        }
        val voteOptions = coordinated.filterNot { read ->
            usefulPublicReads[read.player.name] == true &&
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
            val ranked = rankedPublicSuspects(session, bot)
            val read = ranked.getOrNull(index) ?: ranked.firstOrNull()
            val target = speechTarget(session, bot, read)
            val contextSeed = "opening:$index:${session.phaseIndex}:${socialChatSize(session)}"
            val reason = informalReason(read?.reason(), contextSeed)
            val muted = mutedNames.lastOrNull()
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val fakeClaim = traitorFakeClaimLine(session, bot, social, index)
            val roleLine = roleDrivenLine(session, bot, read, social, index)
            val agenda = agendaFor(session, bot)
            val weakRead = !canVoiceStrongAccusation(session, read)
            val objective = roundObjectiveFor(session, bot)
            val objectiveLine = objectiveLine(session, bot, objective, index)
            val plannedTraitorLine = traitorPlannedDayLine(session, bot, index)
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
                !canVoiceStrongAccusation(session, read)
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
            val pastThreadLine = pastRoundThreadLine(session, bot, index)
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
                pastThreadLine != null -> pastThreadLine
                expelled != null && index == 1 ->
                    "ayer sacamos a $expelled y seguimos igual, no votemos por inercia"
                plannedTraitorLine != null -> plannedTraitorLine
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
        }.dropEchoesOfRecentChat(session).dedupeBotMessages()
    }

    fun votingIntentMessages(session: GameSession, limit: Int = 4): List<Pair<String, String>> {
        return messageBots(session, limit).mapIndexed { index, bot ->
            val ranked = rankedPublicSuspects(session, bot)
            val votePlan = traitorPlanVotePlan(session, bot) ?: conversationVotePlan(session, bot, ranked)
            val read = votePlan
                ?.target
                ?.let { target -> ranked.firstOrNull { it.player.name == target } }
                ?: ranked.firstOrNull()
            val target = votePlan?.target ?: speechTarget(session, bot, read)
            val role = conversationRole(index)
            val contextSeed = "vote:$index:${session.phaseIndex}:${socialChatSize(session)}"
            val reason = votePlan?.reason ?: informalReason(read?.reason(), contextSeed)
            val claim = read?.player?.name?.let { latestClaimBySpeaker(session, it) }
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val pastThreadLine = pastRoundThreadLine(session, bot, index)
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
                    "si $target no explica lo del rol yo voy por ahi",
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
            } else if (pastThreadLine != null && index <= 1) {
                pastThreadLine
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
        }.dropEchoesOfRecentChat(session).dedupeBotMessages()
    }

    fun nextConversationLine(session: GameSession, speaker: String): String? {
        val bot = GameEngine.playerByName(session, speaker)
            ?.takeIf { !it.isHuman && GameEngine.canParticipateInChat(session, it) }
            ?: return null
        val candidates = cachedConversationBatch(session)
        return candidates.firstOrNull { it.first == bot.name }?.second
    }

    private fun cachedConversationBatch(session: GameSession): List<Pair<String, String>> {
        if (session.winner.isNotBlank()) {
            conversationBatchCache = null
            return emptyList()
        }
        val key = conversationBatchCacheKey(session) ?: run {
            conversationBatchCache = null
            return emptyList()
        }
        conversationBatchCache
            ?.takeIf { it.key == key }
            ?.let { return it.messages }

        val limit = session.players.count { !it.isHuman }.coerceAtLeast(1)
        val messages = when (session.phase) {
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> votingIntentMessages(session, limit = limit)
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO -> openingDebateMessages(session, limit = limit)
            else -> emptyList()
        }
        conversationBatchCache = ConversationBatchCache(key, messages)
        return messages
    }

    private fun conversationBatchCacheKey(session: GameSession): String? {
        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO,
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> {
                val lastPublicMessage = session.chatHistory
                    .asReversed()
                    .firstOrNull { it.channel == ChatChannel.PUBLICO && !it.isGod }
                    ?.let { "${it.speaker}:${it.message.hashCode()}" }
                    .orEmpty()
                val playersState = session.players.joinToString("|") {
                    "${it.name}:${it.alive}:${it.muted}:${it.isHuman}:${it.role?.key.orEmpty()}"
                }
                listOf(
                    session.code,
                    session.round,
                    session.phaseIndex,
                    session.phase.name,
                    session.voteRound,
                    socialChatSize(session),
                    lastPublicMessage,
                    playersState.hashCode(),
                    session.votes.hashCode(),
                    session.contrapuntoPlayers.hashCode(),
                    session.claimLedger.values.sumOf { it.size },
                    session.tableMemory.declaredInvestigationReads.size,
                    session.tableMemory.pendingQuestions.size
                ).joinToString(":")
            }
            else -> null
        }
    }

    fun eliminationLastWords(session: GameSession, player: GamePlayer): String? {
        if (player.isHuman || !player.alive || session.dayEliminationTarget != player.name) return null
        return eliminationLastWordsLine(session, player).takeIf { it.isNotBlank() }
    }

    fun reactionsToHumanMessage(
        session: GameSession,
        humanMessage: String,
        intentHint: HumanMessageIntent? = null
    ): List<Pair<String, String>> =
        BotHumanMessageEngine.reactionsTo(session, humanMessage, intentHint)

    internal fun roleClaimFrom(message: String): RoleClaim? = BotPerception.roleClaimFrom(message)

    internal fun publicStatementFrom(session: GameSession, message: String): PublicStatement? =
        BotPerception.publicStatementFrom(session, message)

}
