package com.traidores.juego

internal object LocalBotAi {
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

    private val accusationWords = listOf(
        "sospe",
        "raro",
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

    fun chooseVoteTarget(session: GameSession, voter: GamePlayer): String {
        return rankedPublicSuspects(session, voter)
            .firstOrNull()
            ?.player
            ?.name
            ?: fallbackTarget(session, voter)
    }

    fun openingDebateMessages(session: GameSession, limit: Int = 3): List<Pair<String, String>> {
        val mutedNames = session.players.filter { it.alive && it.muted }.map { safeName(it, session) }
        val noDeath = session.publicAnnouncement.contains("no murio nadie", ignoreCase = true)
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).getOrNull(index)
                ?: rankedPublicSuspects(session, bot).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = informalReason(read?.reason())
            val muted = mutedNames.lastOrNull()
            val intent = openingIntent(session, bot, index)
            val line = when {
                muted != null && index == 0 ->
                    "bueno $muted no puede contestar, $target vos q onda? bancas lo q dijiste?"
                noDeath && index == 1 ->
                    "igual q no haya muerto nadie no limpia a nadie eh, $target me hace ruido pq $reason"
                else -> lineForIntent(session, bot, intent, target, reason)
            }
            bot.name to finishSpeech(line, session, bot, "opening:$index")
        }
    }

    fun votingIntentMessages(session: GameSession, limit: Int = 2): List<Pair<String, String>> {
        return messageBots(session, limit).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = informalReason(read?.reason())
            val templates = listOf(
                "si no cambia nada voto a $target, $reason",
                "yo hoy estoy para votar a $target pq $reason",
                "para mi es $target eh, $reason",
                "nose ustedes pero yo voy con $target, $reason"
            )
            val line = templates[
                stableNoise("${session.code}:${session.round}:${bot.name}:vote:$index") % templates.size
            ]
            bot.name to finishSpeech(line, session, bot, "vote:$index")
        }
    }

    fun reactionsToHumanMessage(session: GameSession, humanMessage: String): List<Pair<String, String>> {
        val focusNames = mentionedPlayerNames(session, humanMessage).toSet()
        val claimsHiddenInfo = containsSecretTerm(humanMessage, session)
        val replyCount = if (focusNames.isNotEmpty() || claimsHiddenInfo || humanMessage.length > 45) 2 else 1
        return messageBots(session, replyCount).mapIndexed { index, bot ->
            val read = rankedPublicSuspects(session, bot, focusNames).firstOrNull()
            val target = read?.let { safeName(it.player, session) } ?: "alguien"
            val reason = informalReason(read?.reason())
            val mood = moodFor(session, bot, humanMessage)
            val intent = reactionIntent(session, bot, humanMessage, focusNames, mood, index)
            val unanswered = unansweredQuestionFor(session, bot)
                ?: pendingQuestionTarget(session).takeIf { index == 0 }
            val line = when {
                claimsHiddenInfo && index == 0 ->
                    "para para, no demos cartas por hechas. decime q hizo y listo"
                claimsHiddenInfo ->
                    "$target me hace ruido por lo q vimos nomas, $reason"
                focusNames.contains(bot.name) ->
                    defensiveLine(bot, mood)
                unanswered != null && intent == Intent.FOLLOW_UP ->
                    "$unanswered igual sigo esperando esa respuesta"
                else -> lineForIntent(session, bot, intent, target, reason)
            }
            bot.name to finishSpeech(line, session, bot, "reply:$index:${humanMessage.length}")
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
        index: Int
    ): Intent {
        val personality = personalityFor(bot)
        val seed = stableNoise("${session.code}:${session.round}:${bot.name}:intent:$index:$humanMessage")
        if (mood == Mood.DEFENSIVE) return Intent.DEFEND
        if (unansweredQuestionFor(session, bot) != null || (index == 0 && pendingQuestionTarget(session) != null)) {
            return Intent.FOLLOW_UP
        }
        if (humanMessage.trim().endsWith("?")) return if (index == 0) Intent.ASK else Intent.ADMIT_DOUBT
        if (focusNames.isNotEmpty() && index == 0) return Intent.ASK
        return when (personality) {
            Personality.TRANQUI -> listOf(Intent.CALM_DOWN, Intent.ASK, Intent.ADMIT_DOUBT)[seed % 3]
            Personality.PICANTE -> listOf(Intent.ACCUSE, Intent.ASK, Intent.TEASE)[seed % 3]
            Personality.JODON -> listOf(Intent.TEASE, Intent.ASK, Intent.ACCUSE)[seed % 3]
            Personality.DESCONFIADO -> listOf(Intent.ASK, Intent.FOLLOW_UP, Intent.ACCUSE)[seed % 3]
            Personality.IMPULSIVO -> listOf(Intent.ACCUSE, Intent.DEFEND, Intent.ADMIT_DOUBT)[seed % 3]
            Personality.ANALITICO -> listOf(Intent.ASK, Intent.FOLLOW_UP, Intent.ADMIT_DOUBT)[seed % 3]
        }
    }

    private fun lineForIntent(
        session: GameSession,
        bot: GamePlayer,
        intent: Intent,
        target: String,
        reason: String
    ): String {
        val personality = personalityFor(bot)
        val lines = when (intent) {
            Intent.ASK -> listOf(
                "$target pq hiciste eso?",
                "$target explica bien lo tuyo, pq $reason?",
                "che $target y vos q decis de todo esto?",
                "$target posta no te parece raro q $reason?"
            )
            Intent.FOLLOW_UP -> listOf(
                "$target si pero no respondiste lo q te preguntaron",
                "no no, para $target, responde eso primero",
                "$target estas esquivando la pregunta hace rato",
                "dale $target contestá bien, pq $reason?"
            )
            Intent.ACCUSE -> listOf(
                "para mi $target se esta regalando, $reason",
                "$target no me cierra nada amigo",
                "dale $target, $reason y queres q no sospeche?",
                "yo lo digo ahora, $target esta re raro"
            )
            Intent.DEFEND -> listOf(
                "nah tampoco para matarlo por eso",
                "yo a $target no lo veo tan raro todavia",
                "banco un toque a $target, dejenlo explicar",
                "capaz estamos flasheando cualquiera con $target"
            )
            Intent.TEASE -> listOf(
                "jajaja $target esa explicacion fue malisima",
                "$target te estas regalando solo jsjs",
                "kjjj dale $target inventate una mejor",
                "no puede ser $target, cada vez te hundis mas jajaj"
            )
            Intent.CALM_DOWN -> listOf(
                "para un toque, dejen hablar a $target",
                "bajen un cambio y q $target explique",
                "igual no votemos por votar, escuchemos a $target",
                "tranqui, primero veamos pq $reason"
            )
            Intent.ADMIT_DOUBT -> listOf(
                "igual nose, capaz estoy flasheando",
                "puede ser eh, no la tengo tan clara",
                "bueno capaz me fui al pasto con $target",
                "mmm no se, lo quiero pensar un toque"
            )
        }
        val offset = if (personality == Personality.ANALITICO) 1 else 0
        val index = stableNoise("${session.code}:${session.round}:${bot.name}:$intent:$target") + offset
        return lines[index % lines.size]
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

    private fun pendingQuestionTarget(session: GameSession): String? {
        val messages = recentPublicMessages(session)
        for (index in messages.indices.reversed()) {
            val question = messages[index]
            if (!question.message.contains("?")) continue
            val target = mentionedPlayerNames(session, question.message)
                .firstOrNull { it != question.speaker }
                ?: continue
            val answered = messages.drop(index + 1).any { it.speaker == target }
            if (!answered) return target
        }
        return null
    }

    private fun informalReason(reason: String?): String {
        return when (reason) {
            "lo nombraron en la mesa" -> "lo vienen nombrando todos"
            "le pidieron explicaciones" -> "le preguntaron y no aclaro mucho"
            "aparecio demasiado en la charla" -> "esta metido en todas"
            "esta hablando poco" -> "no esta diciendo nada"
            "esta ocupando mucho espacio" -> "habla una banda y no dice mucho"
            "ya venia bajo presion" -> "ya venia medio complicado"
            else -> "hay algo q no me cierra"
        }
    }

    private fun finishSpeech(
        raw: String,
        session: GameSession,
        bot: GamePlayer,
        context: String
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

        text = text
            .replace(Regex("[.!]{1,}$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return sanitizeBotSpeech(text, session)
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
            reasons += "lo nombraron en la mesa"
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

        val spokeCount = recent.count { it.speaker == candidate.name }
        when {
            spokeCount == 0 -> {
                score += 2
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

    private fun messageBots(session: GameSession, limit: Int): List<GamePlayer> {
        return GameEngine.alivePlayers(session)
            .filter { !it.isHuman && GameEngine.canSpeak(it) }
            .sortedBy { stableNoise("${session.code}:${session.round}:${session.chatHistory.size}:${it.name}:talk") }
            .take(limit)
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
        val text = normalized(message)
        return forbiddenTerms(session).any { term -> term.length > 2 && text.contains(normalized(term)) }
    }

    private fun mentionsName(message: String, name: String): Boolean {
        return normalized(message).contains(normalized(name))
    }

    private fun safeName(player: GamePlayer, session: GameSession): String {
        return sanitizeBotSpeech(player.name, session).ifBlank { "alguien" }
    }

    private fun sanitizeBotSpeech(raw: String, session: GameSession): String {
        var safe = raw
        forbiddenTerms(session).forEach { term ->
            if (term.length > 2) {
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
