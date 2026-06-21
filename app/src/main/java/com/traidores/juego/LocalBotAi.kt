package com.traidores.juego

internal object LocalBotAi {
    internal data class RoleClaim(
        val roleKey: String,
        val label: String
    )

    internal data class PublicStatement(
        val type: StatementType,
        val target: String? = null
    )

    private data class SocialRead(
        val defended: String? = null,
        val pressured: String? = null,
        val ignoredBy: String? = null,
        val failedPush: String? = null,
        val heated: Boolean = false
    )

    private data class ClaimContradiction(
        val first: ClaimRecord,
        val latest: ClaimRecord
    )

    private data class BotMemory(
        val unansweredTarget: String? = null,
        val lastPressuredTarget: String? = null,
        val recentLines: Set<String> = emptySet()
    )

    private data class VotePlan(
        val target: String,
        val reason: String,
        val confidence: Int
    )

    private enum class BotAgenda {
        ASK_ROLES,
        CALM_TABLE,
        PUSH_VOTE,
        DEFEND_WEAK,
        FOLLOW_THREAD,
        DEFLECT_PRESSURE
    }

    private enum class Personality {
        TRANQUI,
        PICANTE,
        JODON,
        DESCONFIADO,
        IMPULSIVO,
        ANALITICO
    }

    private enum class Mood {
        CALM,
        AMUSED,
        ANNOYED,
        DEFENSIVE,
        SUSPICIOUS
    }

    private enum class Intent {
        ASK,
        FOLLOW_UP,
        ACCUSE,
        DEFEND,
        TEASE,
        CALM_DOWN,
        ADMIT_DOUBT
    }

    private enum class HumanQuestionKind {
        ROLE_HELP,
        VOTE_HELP,
        ACTION_HELP,
        SUSPECT_HELP
    }

    private val accusationWords = listOf(
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
    private val defenseWords = listOf("confio", "inocente", "limpio", "defiendo", "creo en")
    private val actionStatementTypes = setOf(
        StatementType.PROTECTED,
        StatementType.INVESTIGATED
    )
    private val casualWords = setOf("hola", "buenas", "epa", "ey", "eu", "holaa", "holaaa")
    private val casualPhrases = setOf("que onda", "q onda", "todo bien", "toy", "estoy")
    private val secretWords = listOf(
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

    fun chooseAssassinTarget(session: GameSession, assassin: GamePlayer): String {
        val candidates = GameEngine.alivePlayers(session)
            .filter { GameEngine.isValidKillTarget(session, it.name, assassin) }
        return candidates
            .sortedWith(
                compareByDescending<GamePlayer> { nightPressureScore(session, it) }
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
                compareByDescending<GamePlayer> { nightPressureScore(session, it) }
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
        debugVoteCommandTarget(session, voter)?.let { return it }
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
        return coordinated
            .firstOrNull { it.player.name == declaredTarget }
            ?.player
            ?.name
            ?: coordinated.firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, voter)
    }

    private fun conversationVotePlan(session: GameSession, voter: GamePlayer): VotePlan? {
        val aliveNames = GameEngine.alivePlayers(session)
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
            ?.let { rawPlans += VotePlan(it, "dejo una pregunta colgada", 12) }
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
            .filter { plan -> canVotePlanTarget(session, voter, plan) }
            .distinctBy { it.target to it.reason }
        return plans
            .sortedWith(
                compareByDescending<VotePlan> { it.confidence }
                    .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.target}:vote-plan") }
                    .thenBy { it.target }
            )
            .firstOrNull()
    }

    private fun canVotePlanTarget(session: GameSession, voter: GamePlayer, plan: VotePlan): Boolean {
        val target = GameEngine.playerByName(session, plan.target) ?: return false
        if (!target.alive || target.name == voter.name) return false
        if (!isTraitor(voter) || !isTraitor(target)) return true
        if (plan.confidence >= 17 && session.botDifficulty == BotDifficulty.NORMAL) {
            return stableNoise("${session.code}:${session.round}:${voter.name}:${target.name}:traitor-bus") % 5 == 0
        }
        return plan.confidence >= 17 && session.botDifficulty == BotDifficulty.HARD
    }

    private fun votePluralityTarget(session: GameSession, voter: GamePlayer): String? {
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

    private fun humanSuggestedVoteTarget(session: GameSession): String? {
        val human = GameEngine.humanPlayer(session)
        return recentPublicMessages(session)
            .asReversed()
            .filter { it.speaker == human.name }
            .mapNotNull { publicStatementFrom(session, it.message) }
            .firstOrNull { it.type == StatementType.ACCUSE || it.type == StatementType.VOTE }
            ?.target
    }

    private fun debugVoteCommandTarget(session: GameSession, voter: GamePlayer): String? {
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

    fun openingDebateMessages(session: GameSession, limit: Int = 5): List<Pair<String, String>> {
        val mutedNames = session.players.filter { it.alive && it.muted }.map { safeName(it, session) }
        val noDeath = session.publicAnnouncement.contains("no murio nadie", ignoreCase = true)
        val dawnVictim = eventTarget(session, session.publicAnnouncement, "murio")
        val expelled = latestExpelledTarget(session)
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).getOrNull(index)
                ?: rankedPublicSuspects(session, bot).firstOrNull()
            val target = speechTarget(session, bot, read)
            val contextSeed = "opening:$index:${session.phaseIndex}:${session.chatHistory.size}"
            val reason = informalReason(read?.reason(), contextSeed)
            val muted = mutedNames.lastOrNull()
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val fakeClaim = traitorFakeClaimLine(session, bot, social, index)
            val agenda = agendaFor(session, bot)
            val weakRead = isWeakSuspicion(read)
            val agendaLine = agendaLine(session, bot, agenda, target, reason, weakRead, index)
            val playerLine = playerFocusLine(session, bot, target, reason, weakRead, index)
            val baseIntent = openingIntent(session, bot, index)
            val intent = if (
                baseIntent in listOf(Intent.ACCUSE, Intent.TEASE) &&
                (read?.score ?: 0) < 8
            ) {
                Intent.ASK
            } else {
                baseIntent
            }
            val line = when {
                contradiction != null && index <= 1 ->
                    contradictionLine(read.player.name, contradiction)
                dawnVictim != null && index == 0 ->
                    "lo de $dawnVictim anoche cambia todo, $target explica bien pq $reason"
                noDeath && index == 0 ->
                    "no murio nadie pero no nos durmamos, $target vos q hiciste ayer?"
                social.failedPush != null ->
                    "ayer me pude haber equivocado con ${social.failedPush}, hoy quiero escuchar mas antes de mandar fruta"
                social.ignoredBy != null ->
                    "${social.ignoredBy} me sigue debiendo una respuesta de antes"
                expelled != null && index == 1 ->
                    "ayer sacamos a $expelled y seguimos igual, no votemos por inercia"
                botToBotLine(session, bot, index) != null ->
                    botToBotLine(session, bot, index).orEmpty()
                muted != null && index == 0 ->
                    "bueno $muted no puede contestar, $target vos q onda? bancas lo q dijiste?"
                fakeClaim != null -> fakeClaim
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
        }
    }

    fun votingIntentMessages(session: GameSession, limit: Int = 4): List<Pair<String, String>> {
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).firstOrNull()
            val votePlan = conversationVotePlan(session, bot)
            val target = votePlan?.target ?: speechTarget(session, bot, read)
            val contextSeed = "vote:$index:${session.phaseIndex}:${session.chatHistory.size}"
            val reason = votePlan?.reason ?: informalReason(read?.reason(), contextSeed)
            val claim = read?.player?.name?.let { latestClaimBySpeaker(session, it) }
            val social = socialRead(session, bot)
            val contradiction = read?.player?.name?.let { publicContradiction(session, it) }
            val templates = if (votePlan != null && votePlan.confidence >= 10) {
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
                    "si sale mal me hago cargo, pero $target no respondio bien"
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
                    stableNoise("${session.code}:${session.round}:${bot.name}:vote:$index:${session.chatHistory.size}") % templates.size
                ].let { base ->
                    val hesitationSeed = stableNoise("${session.code}:${session.round}:${bot.name}:hesitate:$index:${session.chatHistory.size}")
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
        }
    }

    fun reactionsToHumanMessage(session: GameSession, humanMessage: String): List<Pair<String, String>> {
        val focusNames = mentionedPlayerNames(session, humanMessage).toSet()
        val roleClaim = roleClaimFrom(humanMessage)
        val publicStatement = publicStatementFrom(session, humanMessage)
        val claimResponder = roleClaim?.let { botWithRole(session, it.roleKey) }
        val claimsHiddenInfo = containsSecretTerm(humanMessage, session)
        val casualMessage = isCasualHumanMessage(humanMessage)
        val questionKind = humanQuestionKind(humanMessage)
        val desiredReplyCount = when {
            roleClaim != null || questionKind != null || focusNames.isNotEmpty() -> 3
            publicStatement != null || claimsHiddenInfo || humanMessage.length > 45 -> 2
            else -> 1
        }
        val replyCount = limitedReplyCount(session, desiredReplyCount)
        return messageBots(session, replyCount, preferredFirst = claimResponder?.name).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot, focusNames).firstOrNull()
            val memory = memoryFor(session, bot)
            val baseTarget = speechTarget(session, bot, read)
            val contextSeed = "reply:$index:${session.phaseIndex}:${session.chatHistory.size}:${humanMessage.length}"
            val reason = informalReason(read?.reason(), contextSeed)
            val mood = moodFor(session, bot, humanMessage)
            val intent = reactionIntent(session, bot, humanMessage, focusNames, mood, index, memory)
            val target = if (intent == Intent.FOLLOW_UP && memory.lastPressuredTarget != null) {
                memory.lastPressuredTarget
            } else {
                baseTarget
            }
            val unanswered = memory.unansweredTarget
                ?.takeIf { target -> focusNames.contains(target) || mentionsName(humanMessage, target) }
                ?.takeUnless { target -> target == bot.name }
            val claimLine = roleClaim?.let { claim ->
                roleClaimReaction(session, bot, claim, claimResponder, index)
            }
            val statementLine = publicStatement?.let { statement ->
                actionContradiction(session, GameEngine.humanPlayer(session).name, statement)
                    ?.let { contradictionLine(GameEngine.humanPlayer(session).name, it) }
                    ?: statementReaction(statement, index)
            }
            val line = when {
                claimLine != null -> claimLine
                statementLine != null -> statementLine
                questionKind != null -> humanQuestionReply(session, bot, questionKind, read, index)
                casualMessage -> casualHumanReply(session, bot, humanMessage, index)
                claimsHiddenInfo && index == 0 ->
                    "para para, no demos cartas por hechas. decime q hizo y listo"
                claimsHiddenInfo ->
                    "$target me hace ruido por lo q vimos nomas, $reason"
                focusNames.contains(bot.name) ->
                    defensiveLine(bot, mood)
                unanswered != null && intent == Intent.FOLLOW_UP ->
                    "$unanswered igual sigo esperando esa respuesta"
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

    private fun roleClaimReaction(
        session: GameSession,
        bot: GamePlayer,
        claim: RoleClaim,
        claimResponder: GamePlayer?,
        index: Int
    ): String? {
        roleContradiction(session, GameEngine.humanPlayer(session).name)?.let { contradiction ->
            return contradictionLine(GameEngine.humanPlayer(session).name, contradiction)
        }
        if (bot.name == claimResponder?.name) {
            return "para, yo soy ${claim.label}. si decis lo mismo conta ${claimFollowUp(claim.roleKey)}"
        }
        val existingClaim = publicClaimants(session, claim.roleKey)
            .firstOrNull { it != GameEngine.humanPlayer(session).name }
        return when {
            existingClaim != null && index == 0 ->
                "ojo q $existingClaim ya habia dicho ${claim.label}, eso es doble claim"
            claimResponder != null && index == 1 ->
                "doble claim entonces, uno esta vendiendo humo"
            index == 0 ->
                "ok decis ${claim.label}, pero tira algo concreto sin quemar de mas"
            index == 1 ->
                "bien, preguntemos antes de votar por votar"
            else -> null
        }
    }

    private fun agendaFor(session: GameSession, bot: GamePlayer): BotAgenda {
        if (isTraitor(bot) && socialRead(session, bot).heated) return BotAgenda.DEFLECT_PRESSURE
        return when (personalityFor(bot)) {
            Personality.TRANQUI -> BotAgenda.CALM_TABLE
            Personality.PICANTE -> BotAgenda.PUSH_VOTE
            Personality.JODON -> listOf(BotAgenda.FOLLOW_THREAD, BotAgenda.PUSH_VOTE, BotAgenda.CALM_TABLE)[
                stableNoise("${session.code}:${bot.name}:agenda:jodon") % 3
            ]
            Personality.DESCONFIADO -> BotAgenda.ASK_ROLES
            Personality.IMPULSIVO -> BotAgenda.PUSH_VOTE
            Personality.ANALITICO -> when (bot.role?.key) {
                RoleCatalog.POLICIA,
                RoleCatalog.MEDICO,
                RoleCatalog.ORACULO -> BotAgenda.FOLLOW_THREAD
                else -> BotAgenda.DEFEND_WEAK
            }
        }
    }

    private fun agendaLine(
        session: GameSession,
        bot: GamePlayer,
        agenda: BotAgenda,
        target: String,
        reason: String,
        weakRead: Boolean,
        index: Int
    ): String? {
        if (index > 2) return null
        val memory = memoryFor(session, bot)
        val threadTarget = memory.lastPressuredTarget ?: target
        val options = when (agenda) {
            BotAgenda.ASK_ROLES -> if (weakRead) {
                listOf(
                    "antes de pedir roles por pedir, quiero escuchar versiones",
                    "no quemen rol al pedo, pero tampoco se escondan todos",
                    "si alguien va a esquivar rol que por lo menos aporte algo"
                )
            } else {
                listOf(
                    "$target no hace falta que te quemes, pero explica algo concreto",
                    "$target si vas a guardar rol, tira al menos que viste",
                    "quiero una respuesta de $target sin vender toda la carta"
                )
            }
            BotAgenda.CALM_TABLE -> listOf(
                "bajen un cambio, con ruido vamos a votar cualquier cosa",
                "ordenemos la charla: una pregunta y una respuesta",
                "no me sirve que todos tiren nombres sin explicar"
            )
            BotAgenda.PUSH_VOTE -> if (weakRead) {
                listOf(
                    "si no aparece nada mas, vamos a terminar votando al aire",
                    "necesito un nombre con motivo, no puro humo",
                    "alguien tiene que marcar algo concreto ya"
                )
            } else {
                listOf(
                    "yo apuraria a $target, $reason",
                    "$target tiene que contestar ahora, despues no hay tiempo",
                    "si $target no cierra esto, para mi va por ahi"
                )
            }
            BotAgenda.DEFEND_WEAK -> if (weakRead) {
                listOf(
                    "no maten a alguien solo por intuicion, falta evidencia",
                    "esto todavia esta flojo, no compremos una acusacion gratis",
                    "si van a marcar a alguien, que sea con algo mas que silencio"
                )
            } else {
                listOf(
                    "puede ser $target, pero dejemos que responda primero",
                    "no cierro a $target todavia, aunque $reason",
                    "yo escucharia a $target antes de mandar el voto"
                )
            }
            BotAgenda.FOLLOW_THREAD -> listOf(
                "$threadTarget quedo como hilo abierto, cerremos eso",
                "vuelvo a $threadTarget porque ahi falta una respuesta",
                "no saltemos de tema, lo de $threadTarget sigue pendiente"
            )
            BotAgenda.DEFLECT_PRESSURE -> listOf(
                "estan mirando para cualquier lado, $target viene mas raro",
                "si me quieren apurar ok, pero $target sigue pasando gratis",
                "no se enganchen conmigo, revisen a $target por esto: $reason"
            )
        }
        return chooseFreshLine(options, session, bot, "agenda:$agenda:$target:$index:${session.chatHistory.size}")
    }

    private fun playerFocusLine(
        session: GameSession,
        bot: GamePlayer,
        target: String,
        reason: String,
        weakRead: Boolean,
        index: Int
    ): String? {
        val human = GameEngine.humanPlayer(session)
        if (!human.alive) return null
        if (index !in setOf(0, 2)) return null
        val recentlySpoke = recentPublicMessages(session)
            .takeLast(4)
            .any { it.speaker == human.name }
        if (recentlySpoke && index == 0) return null
        val name = safeName(human, session)
        val options = when {
            !GameEngine.canSpeak(session, human) -> listOf(
                "$name no puede hablar, asi que no armemos todo sobre el",
                "ojo que $name esta silenciado, busquemos otro hilo",
                "como $name no puede contestar, no lo usemos de excusa"
            )
            mentionsName(target, human.name) && !weakRead -> listOf(
                "$name explica eso con calma, pq $reason",
                "$name te estan mirando, tira algo concreto sin quemarte de mas",
                "$name si esto es cualquiera, cerralo ahora"
            )
            weakRead -> listOf(
                "$name vos que viste? tira una punta aunque sea",
                "$name necesitamos tu lectura, no te quedes mirando",
                "$name a quien estas mirando vos por ahora?"
            )
            else -> listOf(
                "$name vos que opinas de $target?",
                "$name te cierra lo de $target o estoy flasheando?",
                "$name fijate a $target, pq $reason"
            )
        }
        return chooseFreshLine(options, session, bot, "player-focus:$index:$target:${session.chatHistory.size}")
    }

    private fun statementReaction(statement: PublicStatement, index: Int): String? {
        val target = statement.target ?: "eso"
        return when (statement.type) {
            StatementType.PROTECTED -> when (index) {
                0 -> "ok, queda anotado lo de $target. si despues no cierra te lo vamos a cobrar"
                1 -> "$target confirma algo de eso o nada que ver?"
                else -> null
            }
            StatementType.INVESTIGATED -> when (index) {
                0 -> "bien, pero deci que te dio esa investigacion sin vender humo"
                1 -> "ojo con tirar info a medias, eso despues confunde todo"
                else -> null
            }
            StatementType.REFUSED_ROLE -> when (index) {
                0 -> "ok no digas rol, pero aporta algo entonces"
                1 -> "si esquivas todo despues no te quejes si te miran raro"
                else -> null
            }
            StatementType.TRUST -> when (index) {
                0 -> "por que confias en $target? dame algo mas que corazonada"
                1 -> "bancar a alguien sin explicar tambien hace ruido"
                else -> null
            }
            StatementType.ACCUSE -> when (index) {
                0 -> "puede ser, pero deci que viste de $target"
                1 -> "acusarlo asi nomas es medio gratis, explica"
                else -> null
            }
            StatementType.VOTE -> when (index) {
                0 -> "si vas con $target explica rapido pq"
                1 -> "no votemos en manada sin escuchar respuesta"
                else -> null
            }
        }
    }

    private fun openingIntent(session: GameSession, bot: GamePlayer, index: Int): Intent {
        val personality = personalityFor(bot)
        return when (personality) {
            Personality.TRANQUI -> if (index == 0) Intent.CALM_DOWN else Intent.ASK
            Personality.PICANTE -> Intent.ACCUSE
            Personality.JODON -> Intent.TEASE
            Personality.DESCONFIADO -> Intent.ASK
            Personality.IMPULSIVO -> Intent.ACCUSE
            Personality.ANALITICO -> if (unansweredQuestionFor(session, bot) != null) Intent.FOLLOW_UP else Intent.ASK
        }
    }

    private fun reactionIntent(
        session: GameSession,
        bot: GamePlayer,
        humanMessage: String,
        focusNames: Set<String>,
        mood: Mood,
        index: Int,
        memory: BotMemory
    ): Intent {
        val personality = personalityFor(bot)
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:intent:$index:$humanMessage")
        if (mood == Mood.DEFENSIVE) return Intent.DEFEND
        if (memory.unansweredTarget?.let { focusNames.contains(it) || mentionsName(humanMessage, it) } == true) {
            return Intent.FOLLOW_UP
        }
        if (humanMessage.trim().endsWith("?")) return if (index == 0) Intent.ASK else Intent.ADMIT_DOUBT
        if (focusNames.isNotEmpty() && index == 0) return Intent.ASK
        when (agendaFor(session, bot)) {
            BotAgenda.ASK_ROLES -> if (index == 0) return Intent.ASK
            BotAgenda.CALM_TABLE -> if (index == 0) return Intent.CALM_DOWN
            BotAgenda.PUSH_VOTE -> if (index == 0) return Intent.ACCUSE
            BotAgenda.DEFEND_WEAK -> if (index == 0) return Intent.DEFEND
            BotAgenda.FOLLOW_THREAD -> if (memory.lastPressuredTarget != null) return Intent.FOLLOW_UP
            BotAgenda.DEFLECT_PRESSURE -> if (index == 0) return Intent.ACCUSE
        }
        return when (personality) {
            Personality.TRANQUI -> listOf(Intent.CALM_DOWN, Intent.ASK, Intent.ADMIT_DOUBT)[seed % 3]
            Personality.PICANTE -> listOf(Intent.ACCUSE, Intent.ASK, Intent.TEASE)[seed % 3]
            Personality.JODON -> listOf(Intent.TEASE, Intent.ASK, Intent.ACCUSE)[seed % 3]
            Personality.DESCONFIADO -> listOf(Intent.ASK, Intent.FOLLOW_UP, Intent.ACCUSE)[seed % 3]
            Personality.IMPULSIVO -> listOf(Intent.ACCUSE, Intent.DEFEND, Intent.ADMIT_DOUBT)[seed % 3]
            Personality.ANALITICO -> listOf(Intent.ASK, Intent.FOLLOW_UP, Intent.ADMIT_DOUBT)[seed % 3]
        }
    }

    private fun humanQuestionKind(message: String): HumanQuestionKind? {
        val text = normalizedForParsing(message)
        return when {
            text.contains("que soy") ||
                text.contains("quien soy") ||
                text.contains("q soy") ||
                text.contains("cual es mi rol") ||
                text.contains("que rol soy") ||
                text.contains("q rol soy") ||
                text.contains("mi rol") ->
                HumanQuestionKind.ROLE_HELP
            text.contains("a quien voto") ||
                text.contains("a quien votamos") ||
                text.contains("quien voto") ||
                text.contains("voto a quien") ->
                HumanQuestionKind.VOTE_HELP
            text.contains("que hago") ||
                text.contains("q hago") ||
                text.contains("que deberia hacer") ||
                text.contains("como juego") ->
                HumanQuestionKind.ACTION_HELP
            text.contains("quien sospecha") ||
                text.contains("de quien sospechan") ||
                text.contains("a quien miramos") ||
                text.contains("quien les parece") ->
                HumanQuestionKind.SUSPECT_HELP
            else -> null
        }
    }

    private fun humanQuestionReply(
        session: GameSession,
        bot: GamePlayer,
        kind: HumanQuestionKind,
        read: SuspectRead?,
        index: Int
    ): String {
        val memory = memoryFor(session, bot)
        val target = memory.lastPressuredTarget
            ?: read?.player?.let { safeName(it, session) }
            ?: "alguien"
        val reason = informalReason(read?.reason(), "human-question:$index:${session.chatHistory.size}")
        val hasRead = memory.lastPressuredTarget != null || (read != null && !isWeakSuspicion(read))
        val options = when (kind) {
            HumanQuestionKind.ROLE_HELP -> listOf(
                "tu carta la sabes vos, no la regales al toque. conta algo de la ronda",
                "si vas a decir rol, decilo con una razon. sino habla de lo que viste",
                "no quemes rol porque si, primero fijate quien te esta apurando"
            )
            HumanQuestionKind.VOTE_HELP -> if (hasRead) {
                listOf(
                    "si votas ahora yo miraria a $target, $reason",
                    "para mi antes de votar hay que hacer hablar a $target",
                    "yo no votaria ciego, pero $target tiene que cerrar eso"
                )
            } else {
                listOf(
                    "todavia no votaria apurado, falta escuchar mas",
                    "por ahora no tengo voto claro, preguntemos primero",
                    "si votamos ahora es medio al aire, esperaria una respuesta mas"
                )
            }
            HumanQuestionKind.ACTION_HELP -> listOf(
                "aporta algo concreto sin regalar toda tu carta",
                "pregunta tranqui y mira quien responde raro",
                "no te desesperes, marca una duda y fijate quien se sube"
            )
            HumanQuestionKind.SUSPECT_HELP -> if (hasRead) {
                listOf(
                    "yo estoy mirando a $target porque $reason",
                    "$target me hace ruido, pero quiero escucharlo antes",
                    "si tengo que marcar a uno ahora diria $target, no como sentencia"
                )
            } else {
                listOf(
                    "todavia nadie me cierra como culpable fuerte",
                    "por ahora no hay sospecha limpia, hay que hablar mas",
                    "no tengo nombre firme, ojo con votar por costumbre"
                )
            }
        }
        return chooseFreshLine(options, session, bot, "human-question:$kind:$index:${session.chatHistory.size}")
    }

    private fun isCasualHumanMessage(message: String): Boolean {
        val text = normalizedForParsing(message)
        if (text.isBlank()) return false
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.size <= 2 && words.any { it in casualWords }) return true
        return text in casualPhrases
    }

    private fun casualHumanReply(
        session: GameSession,
        bot: GamePlayer,
        humanMessage: String,
        index: Int
    ): String {
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:casual:$index:$humanMessage")
        val options = listOf(
            "hola, pero tiren algo util asi no votamos al aire",
            "buenas, arranquemos tranqui y con datos",
            "toy, pero hablemos de la ronda que sino es humo",
            "dale, igual ordenemos un poco quien hizo que",
            "ok, por ahora no tengo nada fuerte"
        )
        return chooseFreshLine(options, session, bot, "casual:$seed")
    }

    private fun isWeakSuspicion(read: SuspectRead?): Boolean {
        return read == null || read.score < 6 || read.reason() == "esta hablando poco"
    }

    private fun lowEvidenceOpeningLine(session: GameSession, bot: GamePlayer, index: Int): String {
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:low-evidence:$index:${session.chatHistory.size}")
        val options = listOf(
            "por ahora no tengo nada fuerte, escuchemos versiones",
            "arranquemos tranqui, acusar por acusar no sirve",
            "yo preguntaria roles solo si hace falta, no quememos todo al toque",
            "si alguien tiene dato real que lo tire sin regalar de mas",
            "no me copa votar por silencio nomas, falta charla"
        )
        return chooseFreshLine(options, session, bot, "low-evidence:$seed")
    }

    private fun lineForIntent(
        session: GameSession,
        bot: GamePlayer,
        intent: Intent,
        target: String,
        reason: String,
        contextSeed: String
    ): String {
        val personality = personalityFor(bot)
        val spokenTarget = target.takeUnless { it.equals(bot.name, ignoreCase = true) } ?: "alguien"
        val lines = linesFor(intent, spokenTarget, reason)
        val offset = if (personality == Personality.ANALITICO) 1 else 0
        val index = stableNoise("${session.code}:${session.round}:${bot.name}:$intent:$spokenTarget:$contextSeed") + offset
        return chooseFreshLine(lines, session, bot, "$intent:$spokenTarget:$contextSeed:$index")
    }

    private fun chooseFreshLine(
        options: List<String>,
        session: GameSession,
        bot: GamePlayer,
        seed: String
    ): String {
        if (options.isEmpty()) return ""
        val recent = memoryFor(session, bot).recentLines
        val start = stableNoise(seed) % options.size
        return options.indices
            .map { options[(start + it) % options.size] }
            .firstOrNull { normalizedForParsing(it) !in recent }
            ?: options[start]
    }

    private fun linesFor(intent: Intent, spokenTarget: String, reason: String): List<String> {
        return when (intent) {
            Intent.ASK -> listOf(
                "$spokenTarget pq hiciste eso?",
                "$spokenTarget explica bien lo tuyo, pq $reason?",
                "che $spokenTarget y vos q decis de todo esto?",
                "$spokenTarget posta no te parece raro q $reason?",
                "$spokenTarget tirame una razon concreta",
                "a ver $spokenTarget, conta bien que onda",
                "$spokenTarget no te estoy acusando, pero explica eso",
                "che posta $spokenTarget, eso como lo justificas?"
            )
            Intent.FOLLOW_UP -> listOf(
                "$spokenTarget si pero no respondiste lo q te preguntaron",
                "no no, para $spokenTarget, responde eso primero",
                "$spokenTarget estas esquivando la pregunta hace rato",
                "dale $spokenTarget contestá bien, pq $reason?",
                "$spokenTarget no saltes a otra cosa, cerra lo anterior",
                "me falta la respuesta de $spokenTarget todavia",
                "$spokenTarget estas pateando la pelota, responde",
                "eso de $spokenTarget quedo colgado"
            )
            Intent.ACCUSE -> listOf(
                "para mi $spokenTarget se esta regalando, $reason",
                "$spokenTarget no me cierra nada amigo",
                "dale $spokenTarget, $reason y queres q no sospeche?",
                "yo lo digo ahora, $spokenTarget esta re raro",
                "$spokenTarget viene flojisimo con eso",
                "no me gusta nada lo de $spokenTarget",
                "para mi hay que mirar fuerte a $spokenTarget",
                "$spokenTarget cada vez me cierra menos"
            )
            Intent.DEFEND -> listOf(
                "nah tampoco para matarlo por eso",
                "yo a $spokenTarget no lo veo tan raro todavia",
                "banco un toque a $spokenTarget, dejenlo explicar",
                "capaz estamos flasheando cualquiera con $spokenTarget",
                "no compremos tan rapido contra $spokenTarget",
                "$spokenTarget todavia puede explicar, aflojen",
                "a mi $spokenTarget no me parece el peor ahora",
                "si vamos contra $spokenTarget que sea con algo mas"
            )
            Intent.TEASE -> listOf(
                "jajaja $spokenTarget esa explicacion fue malisima",
                "$spokenTarget te estas regalando solo jsjs",
                "kjjj dale $spokenTarget inventate una mejor",
                "no puede ser $spokenTarget, cada vez te hundis mas jajaj",
                "$spokenTarget esa no te la compra nadie",
                "amigo $spokenTarget, ayudate un poco",
                "$spokenTarget estas jugando para el clip",
                "na $spokenTarget, eso sono muy armado"
            )
            Intent.CALM_DOWN -> listOf(
                "para un toque, dejen hablar a $spokenTarget",
                "bajen un cambio y q $spokenTarget explique",
                "igual no votemos por votar, escuchemos a $spokenTarget",
                "tranqui, primero veamos pq $reason",
                "no se apuren, falta escuchar a $spokenTarget",
                "paren un poco, todavia hay tiempo",
                "si lo van a marcar a $spokenTarget que sea con calma",
                "ordenemos esto, porque sino votamos cualquier cosa"
            )
            Intent.ADMIT_DOUBT -> listOf(
                "igual nose, capaz estoy flasheando",
                "puede ser eh, no la tengo tan clara",
                "bueno capaz me fui al pasto con $spokenTarget",
                "mmm no se, lo quiero pensar un toque",
                "no estoy cerrado igual",
                "me hace ruido pero puedo estar viendo fantasmas",
                "si me equivoco despues me hago cargo",
                "lo tengo en duda, no como sentencia"
            )
        }
    }

    private fun defensiveLine(bot: GamePlayer, mood: Mood): String {
        return when {
            mood == Mood.ANNOYED -> "dale amigo me marcas a mi y ni explicas pq"
            personalityFor(bot) == Personality.JODON -> "jajaja ahora yo? dale, tirame una razon aunque sea"
            personalityFor(bot) == Personality.IMPULSIVO -> "para para yo no dije eso, no inventes"
            else -> "bueno me marcas a mi, pero decime q hice concretamente"
        }
    }

    private fun personalityFor(bot: GamePlayer): Personality {
        val personalities = Personality.entries
        return personalities[stableNoise("personality:${bot.name}") % personalities.size]
    }

    private fun moodFor(session: GameSession, bot: GamePlayer, latestMessage: String): Mood {
        val recent = recentPublicMessages(session).takeLast(8)
        val mentions = recent.count { mentionsName(it.message, bot.name) }
        val accusations = recent.count {
            mentionsName(it.message, bot.name) && hasAnySignal(it.message, accusationWords)
        }
        val latestTargetsBot = mentionsName(latestMessage, bot.name)
        return when {
            latestTargetsBot && accusations >= 2 -> Mood.ANNOYED
            latestTargetsBot -> Mood.DEFENSIVE
            latestMessage.contains("jaja", ignoreCase = true) ||
                latestMessage.contains("jsjs", ignoreCase = true) -> Mood.AMUSED
            mentions >= 3 -> Mood.SUSPICIOUS
            else -> Mood.CALM
        }
    }

    private fun memoryFor(session: GameSession, bot: GamePlayer): BotMemory {
        val recent = recentPublicMessages(session)
        val candidates = GameEngine.alivePlayers(session).filter { it.name != bot.name }
        val lastPressured = recent
            .asReversed()
            .filter { it.speaker == bot.name }
            .mapNotNull { message ->
                candidates.firstOrNull { candidate ->
                    mentionsName(message.message, candidate.name) &&
                        (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
                }?.name
            }
            .firstOrNull()
        val recentLines = recent
            .takeLast(10)
            .map { normalizedForParsing(it.message) }
            .filter { it.isNotBlank() }
            .toSet()
        return BotMemory(
            unansweredTarget = unansweredQuestionFor(session, bot),
            lastPressuredTarget = lastPressured,
            recentLines = recentLines
        )
    }

    private fun unansweredQuestionFor(session: GameSession, bot: GamePlayer): String? {
        val messages = recentPublicMessages(session)
        val botQuestionIndex = messages.indexOfLast {
            it.speaker == bot.name && it.message.contains("?")
        }
        if (botQuestionIndex < 0) return null
        val question = messages[botQuestionIndex]
        val target = mentionedPlayerNames(session, question.message)
            .firstOrNull { it != bot.name }
            ?: return null
        val answered = messages.drop(botQuestionIndex + 1).any { it.speaker == target }
        return if (answered) null else "$target"
    }

    private fun declaredSuspicionTarget(session: GameSession, bot: GamePlayer): String? {
        val candidates = GameEngine.alivePlayers(session)
            .filter { it.name != bot.name }
        return recentPublicMessages(session)
            .asReversed()
            .asSequence()
            .filter { it.speaker == bot.name }
            .mapNotNull { message ->
                candidates.firstOrNull { candidate ->
                    mentionsName(message.message, candidate.name) &&
                        (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
                }?.name
            }
            .firstOrNull()
    }

    private fun socialRead(session: GameSession, bot: GamePlayer): SocialRead {
        val recent = recentPublicMessages(session)
        val candidates = GameEngine.alivePlayers(session).filter { it.name != bot.name }
        val pressured = recent
            .asReversed()
            .filter { it.speaker == bot.name }
            .mapNotNull { message ->
                candidates.firstOrNull { candidate ->
                    mentionsName(message.message, candidate.name) &&
                        (hasAnySignal(message.message, accusationWords) || message.message.contains("?"))
                }?.name
            }
            .firstOrNull()
        val defended = recent
            .asReversed()
            .filter { it.speaker == bot.name }
            .mapNotNull { message ->
                candidates.firstOrNull { candidate ->
                    mentionsName(message.message, candidate.name) &&
                        hasAnySignal(message.message, defenseWords)
                }?.name
            }
            .firstOrNull()
        val ignoredBy = recent
            .asReversed()
            .filter { it.speaker == bot.name && it.message.contains("?") }
            .mapNotNull { question ->
                val target = mentionedPlayerNames(session, question.message)
                    .firstOrNull { it != bot.name }
                target?.takeUnless { player ->
                    recent.drop(recent.indexOf(question) + 1).any { it.speaker == player }
                }
            }
            .firstOrNull()
        val expelled = latestExpelledTarget(session)
        val failedPush = expelled?.takeIf { target ->
            recent.any {
                it.speaker == bot.name &&
                    mentionsName(it.message, target) &&
                    hasAnySignal(it.message, accusationWords)
            }
        }
        val heated = recent.count {
            mentionsName(it.message, bot.name) &&
                hasAnySignal(it.message, accusationWords)
        } >= 2
        return SocialRead(
            defended = defended,
            pressured = pressured,
            ignoredBy = ignoredBy,
            failedPush = failedPush,
            heated = heated
        )
    }

    private fun botToBotLine(session: GameSession, bot: GamePlayer, index: Int): String? {
        if (index == 0) return null
        val recent = recentPublicMessages(session)
        val lastBotMessage = recent.asReversed().firstOrNull { message ->
            message.speaker != bot.name &&
                session.players.any { !it.isHuman && it.name == message.speaker }
        } ?: return null
        val speaker = lastBotMessage.speaker
        val target = mentionedPlayerNames(session, lastBotMessage.message)
            .firstOrNull { it != bot.name && it != speaker }
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:btb:${lastBotMessage.message}")
        return when {
            target != null && hasAnySignal(lastBotMessage.message, accusationWords) -> {
                val options = listOf(
                    "$speaker tiene un punto con $target, pero falta respuesta",
                    "no se si compro todo lo de $speaker, pero $target deberia contestar",
                    "$target, respondele a $speaker asi cerramos esto"
                )
                options[seed % options.size]
            }
            target != null && hasAnySignal(lastBotMessage.message, defenseWords) -> {
                val options = listOf(
                    "$speaker banca a $target pero yo quiero una razon concreta",
                    "ok $speaker, pero defender a $target sin explicar no alcanza",
                    "$target igual habla vos, no te escondas atras de $speaker"
                )
                options[seed % options.size]
            }
            lastBotMessage.message.contains("?") -> {
                val options = listOf(
                    "eso q pregunta $speaker no es menor",
                    "respondanle a $speaker, sino estamos girando en circulos",
                    "banco la pregunta de $speaker"
                )
                options[seed % options.size]
            }
            else -> null
        }
    }

    private fun traitorFakeClaimLine(
        session: GameSession,
        bot: GamePlayer,
        social: SocialRead,
        index: Int
    ): String? {
        if (!isTraitor(bot) || index > 1 || hasClaimedRole(session, bot.name)) return null
        val pressure = social.heated || social.pressured == bot.name
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:fake-claim:${session.chatHistory.size}")
        val shouldLie = if (session.botDifficulty == BotDifficulty.HARD) {
            pressure || seed % 7 == 0
        } else {
            pressure && seed % 3 != 0
        }
        if (!shouldLie) return null
        val fakeRole = when (seed % 3) {
            0 -> RoleCatalog.MEDICO
            1 -> RoleCatalog.POLICIA
            else -> RoleCatalog.ALDEANO
        }
        val label = roleLabel(fakeRole)
        val options = listOf(
            "paren un toque, soy $label. si me queman ahora despues no lloren",
            "me estan apurando al pedo, soy $label y no me conviene decir mas",
            "ok lo digo: soy $label. no me hagan gastar todo ahora"
        )
        return options[seed % options.size]
    }

    private fun hasClaimedRole(session: GameSession, playerName: String): Boolean {
        return session.claimLedger[playerName].orEmpty().any { it.roleKey != null }
    }

    private fun publicContradiction(session: GameSession, playerName: String): ClaimContradiction? {
        return roleContradiction(session, playerName) ?: actionContradiction(session, playerName)
    }

    private fun roleContradiction(session: GameSession, playerName: String): ClaimContradiction? {
        val records = session.claimLedger[playerName].orEmpty()
            .filter { it.roleKey != null }
        val first = records.firstOrNull() ?: return null
        val latestDifferent = records.lastOrNull { it.roleKey != first.roleKey } ?: return null
        return ClaimContradiction(first, latestDifferent)
    }

    private fun actionContradiction(
        session: GameSession,
        playerName: String,
        latestStatement: PublicStatement? = null
    ): ClaimContradiction? {
        val records = session.claimLedger[playerName].orEmpty()
            .filter {
                it.statementType != null &&
                    it.statementType in actionStatementTypes &&
                    it.target != null &&
                    it.round == session.round
            }
        val latestSynthetic = latestStatement?.takeIf {
            it.type in actionStatementTypes && it.target != null
        }?.let {
            ClaimRecord(
                round = session.round,
                phase = session.phase,
                statementType = it.type,
                target = it.target
            )
        }
        val all = if (latestSynthetic != null && records.none {
                it.statementType == latestSynthetic.statementType &&
                    it.target == latestSynthetic.target &&
                    it.round == latestSynthetic.round
            }
        ) {
            records + latestSynthetic
        } else {
            records
        }
        actionStatementTypes.forEach { type ->
            val sameType = all.filter { it.statementType == type }
            val first = sameType.firstOrNull() ?: return@forEach
            val latestDifferent = sameType.lastOrNull { it.target != first.target }
            if (latestDifferent != null) return ClaimContradiction(first, latestDifferent)
        }
        return null
    }

    private fun contradictionLine(playerName: String, contradiction: ClaimContradiction): String {
        val firstRole = contradiction.first.roleKey
        val latestRole = contradiction.latest.roleKey
        return if (firstRole != null && latestRole != null) {
            "$playerName espera, el dia ${contradiction.first.round} dijiste ${roleLabel(firstRole)} y ahora ${roleLabel(latestRole)}? eso no cierra"
        } else {
            val action = actionLabel(contradiction.latest.statementType)
            "$playerName dijiste que $action a ${contradiction.first.target} y ahora a ${contradiction.latest.target}, ordena esa version"
        }
    }

    private fun contradictionVoteLine(target: String, contradiction: ClaimContradiction): String {
        return if (contradiction.latest.roleKey != null) {
            "voto a $target por la contradiccion de rol, eso no pasa gratis"
        } else {
            "voy con $target, cambio la historia de lo que hizo"
        }
    }

    private fun roleLabel(roleKey: String): String {
        return roleAliases[roleKey]?.firstOrNull() ?: roleKey
    }

    private fun actionLabel(type: StatementType?): String {
        return when (type) {
            StatementType.PROTECTED -> "protegiste"
            StatementType.INVESTIGATED -> "investigaste"
            else -> "hiciste eso"
        }
    }

    private fun traitorDeflectionLine(
        session: GameSession,
        bot: GamePlayer,
        target: String,
        reason: String
    ): String {
        val alternative = rankedPublicSuspects(session, bot)
            .firstOrNull { !isTraitor(it.player) }
            ?.player
            ?.let { safeName(it, session) }
            ?: target
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:deflect")
        val options = listOf(
            "me estan mirando a mi por nada, pero $alternative sigue sin explicar pq $reason",
            "no se enganchen conmigo, miren a $alternative que viene flojo",
            "dale, si me quieren votar voten, pero $alternative esta pasando gratis"
        )
        return options[seed % options.size]
    }

    private fun latestExpelledTarget(session: GameSession): String? {
        val announcement = session.publicHistory
            .asReversed()
            .firstOrNull { it.contains("expulso a", ignoreCase = true) }
            ?: return null
        return eventTarget(session, announcement, "expulso a")
    }

    private fun eventTarget(
        session: GameSession,
        announcement: String,
        marker: String
    ): String? {
        if (!announcement.contains(marker, ignoreCase = true)) return null
        return session.players
            .sortedByDescending { it.name.length }
            .firstOrNull { mentionsName(announcement, it.name) }
            ?.let { safeName(it, session) }
    }

    private fun informalReason(reason: String?, contextSeed: String = ""): String {
        val variants = when (reason) {
            "lo nombraron en el pueblo" -> listOf(
                "lo vienen nombrando todos",
                "aparecio demasiado en la charla",
                "varios lo tiraron al medio"
            )
            "le pidieron explicaciones" -> listOf(
                "le preguntaron y no aclaro mucho",
                "dejo respuestas medio flojas",
                "cuando le preguntaron no cerro"
            )
            "aparecio demasiado en la charla" -> listOf(
                "esta metido en todas",
                "su nombre no para de salir",
                "viene dando vueltas en todo"
            )
            "esta hablando poco" -> listOf(
                "no esta diciendo nada",
                "esta demasiado callado",
                "pasa muy de costado"
            )
            "esta ocupando mucho espacio" -> listOf(
                "habla una banda y no dice mucho",
                "mete mucho ruido",
                "esta tapando la charla"
            )
            "ya venia bajo presion" -> listOf(
                "ya venia medio complicado",
                "lo vienen apurando hace rato",
                "ya estaba en la mira"
            )
            "hay doble claim" -> listOf(
                "hay doble claim",
                "dos personas dijeron lo mismo",
                "ese claim esta peleado"
            )
            "tiro rol y falta detalle" -> listOf(
                "tiro rol pero falta detalle",
                "dijo rol y no cerro nada",
                "el claim quedo medio suelto"
            )
            "lo presionaron con algo concreto" -> listOf(
                "lo marcaron con algo concreto",
                "no es una sospecha de la nada",
                "hay algo puntual para mirar"
            )
            "esquivo el rol" -> listOf(
                "esquivo el rol",
                "no quiso decir nada util",
                "se guardo demasiado"
            )
            "tiro dato y falta detalle" -> listOf(
                "tiro dato pero falta detalle",
                "dio info a medias",
                "conto algo pero no lo termino"
            )
            "se contradijo de rol" -> listOf(
                "se contradijo con el rol",
                "cambio el claim",
                "dijo dos roles distintos"
            )
            "se contradijo con la accion" -> listOf(
                "cambio lo que dijo que hizo",
                "dio dos versiones de su accion",
                "no sostuvo la misma historia"
            )
            else -> listOf(
                "hay algo q no me cierra",
                "me hace ruido",
                "algo ahi esta raro"
            )
        }
        return variants[stableNoise("reason:$reason:$contextSeed") % variants.size]
    }

    private fun finishSpeech(
        raw: String,
        session: GameSession,
        bot: GamePlayer,
        context: String,
        allowRoleTerms: Boolean = false
    ): String {
        val personality = personalityFor(bot)
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:style:$context")
        var text = raw.lowercase()
            .replace("porque", if (seed % 3 == 0) "pq" else "porque")
            .replace("que ", if (seed % 5 == 0) "q " else "que ")
            .replace("tambien", if (seed % 4 == 0) "tmb" else "tambien")
            .replace("no se", if (seed % 2 == 0) "nose" else "no se")

        if (personality == Personality.PICANTE && seed % 4 == 0 && !text.startsWith("dale")) {
            text = "dale, $text"
        }
        if (personality == Personality.JODON && seed % 3 == 0 && !containsLaugh(text)) {
            text = "${laughFor(seed)} $text"
        }
        if (personality == Personality.IMPULSIVO && seed % 5 == 0) {
            text = text.replace("para ", "PARA ")
        }
        if (personality == Personality.TRANQUI && seed % 4 == 0 && !text.startsWith("igual")) {
            text = "igual $text"
        }
        if (
            session.botSpicyLanguage &&
            personality in listOf(Personality.PICANTE, Personality.IMPULSIVO) &&
            seed % 6 == 0 &&
            !text.contains("boludo") &&
            !text.contains("dejate de joder")
        ) {
            text = if (seed % 2 == 0) {
                "$text boludo"
            } else {
                "dejate de joder, $text"
            }
        }

        text = text
            .replace(Regex("[.!]{1,}$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val allowedTerms = if (allowRoleTerms) {
            roleAliases.values.flatten().toSet()
        } else {
            emptySet()
        }
        return sanitizeBotSpeech(text, session, allowedTerms)
    }

    private fun containsLaugh(text: String): Boolean {
        return text.contains("jaja") || text.contains("jsjs") || text.contains("kjjj")
    }

    private fun laughFor(seed: Int): String {
        val laughs = listOf("jajaja", "jsjs", "kjjj")
        return laughs[seed % laughs.size]
    }

    private fun rankedPublicSuspects(
        session: GameSession,
        voter: GamePlayer,
        focusNames: Set<String> = emptySet()
    ): List<SuspectRead> {
        return GameEngine.alivePlayers(session)
            .filter { it.name != voter.name }
            .map { candidate -> scoreCandidate(session, voter, candidate, focusNames) }
            .sortedWith(
                compareByDescending<SuspectRead> { it.score }
                    .thenBy { stableNoise("${session.code}:${session.round}:${voter.name}:${it.player.name}:suspect") }
                    .thenBy { it.player.name }
            )
    }

    private fun speechTarget(
        session: GameSession,
        bot: GamePlayer,
        read: SuspectRead?
    ): String {
        val candidate = read?.player?.takeUnless { it.name == bot.name }
            ?: GameEngine.alivePlayers(session).firstOrNull { it.name != bot.name }
        return candidate?.let { safeName(it, session) } ?: "alguien"
    }

    private fun scoreCandidate(
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
            publicStatementFrom(session, message.message)?.takeIf { it.target == candidate.name }
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
                score += 2
                reasons += "tiro rol y falta detalle"
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
            spokeCount == 0 && session.round > 1 -> {
                score += 1
                reasons += "esta hablando poco"
            }
            spokeCount >= 3 -> {
                score += 1
                reasons += "esta ocupando mucho espacio"
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

    private fun nightPressureScore(session: GameSession, candidate: GamePlayer): Int {
        val recent = recentPublicMessages(session)
        val spokeCount = recent.count { it.speaker == candidate.name }
        val namedCount = recent.count { mentionsName(it.message, candidate.name) }
        val accusedCount = recent.count {
            mentionsName(it.message, candidate.name) && hasAnySignal(it.message, accusationWords)
        }
        return (if (candidate.isHuman && session.round > 1) 2 else 0) +
            spokeCount * 3 +
            namedCount -
            accusedCount * 2 +
            stableNoise("${session.code}:${session.round}:${candidate.name}:night") % 2
    }

    private fun messageBots(
        session: GameSession,
        limit: Int,
        preferredFirst: String? = null
    ): List<GamePlayer> {
        if (limit <= 0) return emptyList()
        val recentSpeakers = recentBotSpeakers(session, amount = 2)
        return session.players
            .filter { !it.isHuman && GameEngine.canSpeak(session, it) }
            .sortedWith(
                compareBy<GamePlayer> { if (it.name == preferredFirst) 0 else 1 }
                    .thenBy { if (it.name in recentSpeakers && it.name != preferredFirst) 1 else 0 }
                    .thenBy { stableNoise("${session.code}:${session.round}:${session.chatHistory.size}:${it.name}:talk") }
                    .thenBy { it.name }
            )
            .take(limit)
    }

    private fun limitedReplyCount(session: GameSession, desired: Int): Int {
        val streak = recentBotStreak(session)
        return when {
            streak >= 3 -> 0
            streak >= 2 -> desired.coerceAtMost(1)
            else -> desired
        }
    }

    private fun recentBotStreak(session: GameSession): Int {
        return recentPublicMessages(session)
            .asReversed()
            .takeWhile { isBotSpeaker(session, it.speaker) }
            .count()
    }

    private fun recentBotSpeakers(session: GameSession, amount: Int): Set<String> {
        return recentPublicMessages(session)
            .asReversed()
            .filter { isBotSpeaker(session, it.speaker) }
            .take(amount)
            .map { it.speaker }
            .toSet()
    }

    private fun isBotSpeaker(session: GameSession, speaker: String): Boolean {
        return session.players.any { !it.isHuman && it.name == speaker }
    }

    private fun mentionedPlayerNames(session: GameSession, message: String): List<String> {
        return GameEngine.alivePlayers(session)
            .filter { mentionsName(message, it.name) }
            .map { it.name }
    }

    private fun fallbackTarget(session: GameSession, actor: GamePlayer): String {
        return GameEngine.alivePlayers(session)
            .firstOrNull { it.name != actor.name }
            ?.name
            .orEmpty()
    }

    private fun isTraitor(player: GamePlayer): Boolean {
        return GameRules.isTraitorRole(player.role)
    }

    private fun recentPublicMessages(session: GameSession): List<GameChatMessage> {
        return session.chatHistory.filterNot { it.isGod }.takeLast(16)
    }

    private fun hasAnySignal(message: String, signals: List<String>): Boolean {
        val text = normalized(message)
        return signals.any { text.contains(it) }
    }

    private fun containsSecretTerm(message: String, session: GameSession): Boolean {
        if (roleClaimFrom(message) != null) return false
        val text = normalized(message)
        return forbiddenTerms(session).any { term -> term.length > 2 && text.contains(normalized(term)) }
    }

    private fun mentionsName(message: String, name: String): Boolean {
        return normalized(message).contains(normalized(name))
    }

    private fun safeName(player: GamePlayer, session: GameSession): String {
        return sanitizeBotSpeech(player.name, session).ifBlank { "alguien" }
    }

    private fun sanitizeBotSpeech(
        raw: String,
        session: GameSession,
        allowedTerms: Set<String> = emptySet()
    ): String {
        var safe = raw
        val normalizedAllowedTerms = allowedTerms.map(::normalizedForParsing).toSet()
        forbiddenTerms(session).forEach { term ->
            if (term.length > 2 && normalizedForParsing(term) !in normalizedAllowedTerms) {
                safe = safe.replace(
                    Regex(
                        "(?<![\\wáéíóúüñÁÉÍÓÚÜÑ])${Regex.escape(term)}(?![\\wáéíóúüñÁÉÍÓÚÜÑ])",
                        RegexOption.IGNORE_CASE
                    ),
                    "esa carta"
                )
            }
        }
        return safe.replace(Regex("\\s+"), " ").trim().take(140)
    }

    private fun forbiddenTerms(session: GameSession): Set<String> {
        val roleTerms = session.players.flatMap { player ->
            listOfNotNull(player.role?.key, player.role?.name, player.role?.team)
        }
        return (secretWords + roleTerms).map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun normalized(value: String): String {
        return value.lowercase()
    }

    private fun normalizedForParsing(value: String): String {
        return normalized(value)
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9ñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizedVoteCommand(value: String): String {
        return normalizedForParsing(value)
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .replace(Regex("[^a-z0-9ñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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

    private fun botWithRole(session: GameSession, roleKey: String): GamePlayer? {
        return session.players.firstOrNull {
            !it.isHuman &&
                GameEngine.canSpeak(session, it) &&
                it.role?.key == roleKey &&
                !GameRules.isTraitorRole(it.role)
        }
    }

    private fun publicClaimants(session: GameSession, roleKey: String): List<String> {
        return recentPublicMessages(session)
            .mapNotNull { message ->
                message.speaker.takeIf { roleClaimFrom(message.message)?.roleKey == roleKey }
            }
            .distinct()
    }

    private fun latestClaimBySpeaker(session: GameSession, speaker: String): RoleClaim? {
        return recentPublicMessages(session)
            .asReversed()
            .firstOrNull { it.speaker == speaker }
            ?.message
            ?.let(::roleClaimFrom)
    }

    private fun latestStatementBySpeaker(session: GameSession, speaker: String): PublicStatement? {
        return recentPublicMessages(session)
            .asReversed()
            .firstOrNull { it.speaker == speaker }
            ?.message
            ?.let { publicStatementFrom(session, it) }
    }

    private fun claimFollowUp(roleKey: String): String {
        return when (roleKey) {
            RoleCatalog.MEDICO -> "a quien cuidaste"
            RoleCatalog.POLICIA -> "a quien investigaste"
            RoleCatalog.ALCALDE -> "por que no te revelaste antes"
            RoleCatalog.PAYADOR -> "cuando pensas usar la jugada"
            RoleCatalog.ORACULO -> "a quien queres traer"
            else -> "que hiciste"
        }
    }

    private val roleAliases = linkedMapOf(
        RoleCatalog.MEDICO to listOf("medico", "doc"),
        RoleCatalog.POLICIA to listOf("detective", "comisario", "policia"),
        RoleCatalog.ALCALDE to listOf("alcalde"),
        RoleCatalog.PAYADOR to listOf("payador"),
        RoleCatalog.ORACULO to listOf("oraculo"),
        RoleCatalog.ALDEANO to listOf("aldeano", "pueblo"),
        RoleCatalog.DESERTOR to listOf("desertor")
    )

    internal fun publicStatementFrom(session: GameSession, message: String): PublicStatement? {
        val text = normalizedForParsing(message)
        val target = mentionedPlayerNames(session, message).firstOrNull()
        return when {
            Regex("(^|\\s)(protegi|cuide|salve|cure)(\\s+a)?\\s+").containsMatchIn(text) ->
                PublicStatement(StatementType.PROTECTED, target)
            Regex("(^|\\s)(investigue|revise|mire)(\\s+a)?\\s+").containsMatchIn(text) ->
                PublicStatement(StatementType.INVESTIGATED, target)
            text.contains("no digo mi rol") ||
                text.contains("no voy a decir rol") ||
                text.contains("no quiero decir rol") ||
                text.contains("no digo rol") ->
                PublicStatement(StatementType.REFUSED_ROLE)
            target != null && (
                text.contains("confio en ${normalizedForParsing(target)}") ||
                    text.contains("banco a ${normalizedForParsing(target)}") ||
                    text.contains("${normalizedForParsing(target)} es limpio")
                ) ->
                PublicStatement(StatementType.TRUST, target)
            target != null && (
                text.contains("${normalizedForParsing(target)} miente") ||
                    text.contains("no confio en ${normalizedForParsing(target)}") ||
                    text.contains("${normalizedForParsing(target)} esta raro") ||
                    text.contains("${normalizedForParsing(target)} es raro")
                ) ->
                PublicStatement(StatementType.ACCUSE, target)
            target != null && (
                text.contains("voto a ${normalizedForParsing(target)}") ||
                    text.contains("votaria a ${normalizedForParsing(target)}") ||
                    text.contains("voy con ${normalizedForParsing(target)}")
                ) ->
                PublicStatement(StatementType.VOTE, target)
            else -> null
        }
    }

    private fun stableNoise(seed: String): Int {
        var value = 17
        seed.forEach { char ->
            value = (value * 31 + char.code) and 0x7fffffff
        }
        return value
    }

    private data class SuspectRead(
        val player: GamePlayer,
        val score: Int,
        val reasons: List<String>
    ) {
        fun reason(): String = reasons.firstOrNull() ?: "su postura todavia no cierra"
    }
}
