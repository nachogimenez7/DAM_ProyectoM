package com.traidores.juego

/** Converts the public chat into the small set of facts bots can reason about. */
internal object BotPerception {
    private const val PARSE_CACHE_SIZE = 256

    private data class CachedValue<T>(val value: T)

    private data class RoleClaimPatterns(
        val roleKey: String,
        val displayAlias: String,
        val aliases: List<Pair<Regex, Regex>>
    )

    private data class PublicStatementCacheKey(
        val message: String,
        val aliveNames: List<String>
    )

    private val roleClaimPatterns = roleAliases.map { (roleKey, aliases) ->
        RoleClaimPatterns(
            roleKey = roleKey,
            displayAlias = aliases.first(),
            aliases = aliases.map { alias ->
                val escaped = Regex.escape(alias)
                Regex(
                    "(^|\\s)(soy(\\s+(el|la))?|tengo(\\s+(el|la))?(\\s+rol\\s+de)?|" +
                        "me\\s+toco(\\s+ser)?|a\\s+mi\\s+me\\s+toco\\s+ser)\\s+$escaped($|\\s)"
                ) to Regex("(^|\\s)(el|la)\\s+$escaped\\s+soy\\s+yo($|\\s)")
            }
        )
    }
    private val publicResultPattern = Regex(
        "(^|\\s)(me\\s+)?(dio|salio|resulto|marco\\s+como|es)\\s+" +
            "(sospechos[oa]|inocente|culpable|traidor[a]?|limpi[oa])($|\\s)"
    )
    private val protectedStatementPattern = Regex(
        "(^|\\s)(protegi|cuide|salve|cure)(\\s+a)?\\s+"
    )
    private val investigatedStatementPattern = Regex(
        "(^|\\s)(investigue|revise|mire|pregunte)(\\s+a|\\s+por)?\\s+"
    )
    private val roleClaimCache = object :
        LinkedHashMap<String, CachedValue<RoleClaim?>>(PARSE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CachedValue<RoleClaim?>>?
        ): Boolean = size > PARSE_CACHE_SIZE
    }
    private val publicStatementCache = object :
        LinkedHashMap<PublicStatementCacheKey, CachedValue<PublicStatement?>>(
            PARSE_CACHE_SIZE,
            0.75f,
            true
        ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<PublicStatementCacheKey, CachedValue<PublicStatement?>>?
        ): Boolean = size > PARSE_CACHE_SIZE
    }
    private val gameActionWords = setOf(
        "votar", "voto", "echar", "sacar", "expulsar", "colgar", "quemar", "linchar",
        "matar", "proteger", "cuidar", "curar", "investigar", "revisar", "mirar",
        "salvar", "silenciar", "callar"
    )
    private val gameMetaWords = setOf(
        "ronda", "noche", "dia", "voto", "empate", "rol", "carta", "pueblo", "aldea",
        "fase", "partida", "juego", "traidor", "inocente"
    )
    private val gameActionRoots = setOf(
        "vot", "ech", "sac", "expuls", "colg", "quem", "linch", "mat", "proteg",
        "cuid", "cur", "investig", "revis", "mir", "salv", "silenci", "call"
    )

    fun roleClaimFrom(message: String): RoleClaim? {
        synchronized(roleClaimCache) {
            roleClaimCache[message]?.let { return it.value }
        }
        val text = normalizedForParsing(message)
        val result = roleClaimPatterns.firstOrNull { role ->
            role.aliases.any { (direct, inverse) ->
                direct.containsMatchIn(text) || inverse.containsMatchIn(text)
            }
        }?.let { role -> RoleClaim(role.roleKey, role.displayAlias) }
        synchronized(roleClaimCache) {
            roleClaimCache[message] = CachedValue(result)
        }
        return result
    }

    fun publicStatementFrom(session: GameSession, message: String): PublicStatement? {
        val cacheKey = PublicStatementCacheKey(
            message = message,
            aliveNames = GameEngine.alivePlayers(session).map { it.name }
        )
        synchronized(publicStatementCache) {
            publicStatementCache[cacheKey]?.let { return it.value }
        }
        val text = normalizedForParsing(message)
        val mentionedTargets = mentionedPlayerNames(session, message)
        val protectedTarget = targetAfterAction(
            text,
            mentionedTargets,
            listOf("protegi", "cuide", "salve", "cure")
        )
        val investigatedTarget = targetAfterAction(
            text,
            mentionedTargets,
            listOf("investigue", "revise", "mire", "pregunte")
        )
        val rejectedVoteTarget = mentionedTargets.firstOrNull { target ->
            explicitlyRejectsVote(text, normalizedForParsing(target))
        }
        val trustTarget = mentionedTargets.firstOrNull { target ->
            hasTrustSignal(text, normalizedForParsing(target))
        }
        val accusationTarget = mentionedTargets.firstOrNull { target ->
            hasAccusationSignal(text, normalizedForParsing(target))
        }
        val voteTarget = mentionedTargets.firstOrNull { target ->
            hasVoteSignal(text, normalizedForParsing(target))
        } ?: targetAfterAction(
            text,
            mentionedTargets,
            listOf("voto a", "votaria a", "voy con")
        )
        val resultTarget = investigatedTarget
            ?: mentionedTargets.firstOrNull { target ->
                val normalizedTarget = normalizedForParsing(target)
                text.contains("$normalizedTarget me dio") ||
                    text.contains("$normalizedTarget dio") ||
                    text.contains("$normalizedTarget salio")
            }
            ?: mentionedTargets.lastOrNull()
        val publicResult = publicResultPattern.find(text)?.groupValues?.getOrNull(4)
        val publicResultNegated = publicResult?.let { result ->
            listOf("no me dio $result", "no dio $result", "no salio $result", "no resulto $result")
                .any(text::contains)
        } == true
        val statedReason = statedReasonFrom(text)

        val statement = when {
            protectedStatementPattern.containsMatchIn(text) &&
                !hasNegatedAction(text, listOf("protegi", "cuide", "salve", "cure")) ->
                PublicStatement(StatementType.PROTECTED, protectedTarget ?: mentionedTargets.lastOrNull())
            text.contains("no digo mi rol") ||
                text.contains("no pienso decir mi rol") ||
                text.contains("no voy a decir rol") ||
                text.contains("no quiero decir rol") ||
                text.contains("no revelo rol") ||
                text.contains("no voy a revelar") ||
                text.contains("prefiero no decir") ||
                text.contains("no digo rol") ->
                PublicStatement(StatementType.REFUSED_ROLE)
            resultTarget != null && publicResult in setOf("inocente", "limpio", "limpia") &&
                !publicResultNegated ->
                PublicStatement(StatementType.TRUST, resultTarget)
            resultTarget != null && publicResult in setOf(
                "sospechoso", "sospechosa", "culpable", "traidor", "traidora"
            ) && !publicResultNegated -> PublicStatement(StatementType.ACCUSE, resultTarget)
            resultTarget != null && publicResultNegated &&
                publicResult in setOf("sospechoso", "sospechosa", "culpable", "traidor", "traidora") ->
                PublicStatement(StatementType.TRUST, resultTarget)
            resultTarget != null && publicResultNegated &&
                publicResult in setOf("inocente", "limpio", "limpia") ->
                PublicStatement(StatementType.ACCUSE, resultTarget)
            rejectedVoteTarget != null -> PublicStatement(StatementType.TRUST, rejectedVoteTarget)
            trustTarget != null -> PublicStatement(StatementType.TRUST, trustTarget)
            accusationTarget != null -> PublicStatement(StatementType.ACCUSE, accusationTarget)
            voteTarget != null -> PublicStatement(StatementType.VOTE, voteTarget)
            investigatedStatementPattern.containsMatchIn(text) &&
                !hasNegatedAction(text, listOf("investigue", "revise", "mire", "pregunte")) ->
                PublicStatement(StatementType.INVESTIGATED, investigatedTarget ?: mentionedTargets.lastOrNull())
            else -> null
        }?.let { parsed ->
            parsed.copy(reason = statedReason)
        }
        synchronized(publicStatementCache) {
            publicStatementCache[cacheKey] = CachedValue(statement)
        }
        return statement
    }

    fun humanQuestionKind(message: String): HumanQuestionKind? {
        val text = normalizedForParsing(message)
        return when {
            text.contains("por que me votaste") || text.contains("porque me votaste") ||
                text.contains("por que votaste") || text.contains("porque votaste") ||
                text.contains("por que me votas") || text.contains("porque me votas") ||
                text.contains("por que votas") || text.contains("porque votas") ->
                HumanQuestionKind.WHY_VOTE
            text.contains("por que me acusaste") || text.contains("porque me acusaste") ||
                text.contains("por que me acusas") || text.contains("porque me acusas") ||
                text.contains("por que lo acusas") || text.contains("porque lo acusas") ||
                text.contains("por que acusas") || text.contains("porque acusas") ||
                text.contains("por que sospechas de") || text.contains("porque sospechas de") ->
                HumanQuestionKind.WHY_ACCUSE
            text.contains("por que pensas eso") || text.contains("porque pensas eso") ||
                text.contains("por que decis eso") || text.contains("porque decis eso") ||
                text.contains("por que dijiste eso") || text.contains("porque dijiste eso") ||
                text.contains("en que te basas") || text.contains("en que te basaste") ->
                HumanQuestionKind.EXPLAIN_STANCE
            text.contains("que pensas de") || text.contains("que opinas de") ||
                text.contains("como ves a") || text.contains("que te parece") ->
                HumanQuestionKind.OPINION
            text.contains("me crees") || text.contains("me creen") ||
                text.contains("me bancas") || text.contains("confias en mi") ->
                HumanQuestionKind.BELIEF
            text.contains("que rol sos") || text.contains("q rol sos") ||
                text.contains("cual es tu rol") || text.contains("cual es el rol tuyo") ||
                text.contains("vos que sos") || text.contains("vos q sos") ||
                text.contains("decime tu rol") || text.contains("dime tu rol") ->
                HumanQuestionKind.ASK_ROLE
            text.contains("que soy") || text.contains("quien soy") || text.contains("q soy") ||
                text.contains("cual es mi rol") || text.contains("que rol soy") ||
                text.contains("q rol soy") || text.contains("mi rol") -> HumanQuestionKind.ROLE_HELP
            text.contains("a quien voto") || text.contains("a quien votamos") ||
                text.contains("a quien votas") || text.contains("a quien votarias") ||
                text.contains("quien voto") || text.contains("voto a quien") ||
                text.contains("con quien vas") || text.contains("por quien vas") -> HumanQuestionKind.VOTE_HELP
            text.contains("que hago") || text.contains("q hago") ||
                text.contains("que deberia hacer") || text.contains("como juego") -> HumanQuestionKind.ACTION_HELP
            text.contains("quien sospecha") || text.contains("de quien sospechan") ||
                text.contains("a quien sospechas") || text.contains("de quien sospechas") ||
                text.contains("a quien miramos") || text.contains("a quien miras") ||
                text.contains("quien les parece") || text.contains("quien te parece raro") ||
                text.contains("quien te parece rara") || text.contains("quien te hace ruido") ->
                HumanQuestionKind.SUSPECT_HELP
            else -> null
        }
    }

    fun socialSignal(message: String): HumanSocialSignal? {
        val text = normalizedForParsing(message)
        if (text.isBlank()) return null
        val praise = listOf(
            "gracias", "bien ahi", "bien jugado", "te banco", "me caes bien",
            "sos crack", "sos un crack", "sos genio", "sos genia", "sos un genio",
            "sos una genia", "buena jugada"
        ).any(text::contains) && !text.contains("no te banco")
        if (praise) return HumanSocialSignal.PRAISE
        val insult = listOf(
            "callate", "sos idiota", "sos un idiota", "sos una idiota",
            "sos inutil", "sos un inutil", "sos una inutil", "sos mentiroso",
            "sos mentirosa", "sos tarado", "sos tarada", "no servis"
        ).any(text::contains)
        return HumanSocialSignal.INSULT.takeIf { insult }
    }

    fun directAddressee(session: GameSession, message: String): String? {
        return directPlayerAddressee(session, message)
            ?.takeIf { name -> GameEngine.playerByName(session, name)?.isHuman == false }
    }

    fun directPlayerAddressee(session: GameSession, message: String): String? {
        val raw = message.trim()
        val text = normalizedForParsing(raw)
        if (text.isBlank()) return null
        return session.players
            .asSequence()
            .filter { GameEngine.canParticipateInChat(session, it) }
            .sortedByDescending { it.name.length }
            .firstOrNull { player ->
                val normalizedName = normalizedForParsing(player.name)
                if (normalizedName.isBlank() || !text.startsWith(normalizedName)) return@firstOrNull false
                val rawPunctuation = Regex(
                    "^\\s*${Regex.escape(player.name)}\\s*[,;:]",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(raw)
                val tail = text.removePrefix(normalizedName).trim()
                val directLanguage = listOf(
                    "vos", "votaste", "votas", "acusaste", "acusas", "pensaste", "pensas",
                    "opinaste", "opinas", "dijiste", "decis", "hiciste", "haces", "sos",
                    "sospechas", "miras", "votarias", "crees", "me crees", "me bancas",
                    "por que", "porque", "en que", "que rol", "q rol", "que hago", "q hago",
                    "a quien", "explica", "contesta", "responde"
                ).any { signal -> tail == signal || tail.startsWith("$signal ") || tail.contains(" $signal ") }
                rawPunctuation || directLanguage
            }
            ?.name
    }

    fun isCasualHumanMessage(message: String): Boolean {
        val text = normalizedForParsing(message)
        if (text.isBlank()) return false
        val words = text.split(" ").filter(String::isNotBlank)
        return (words.size <= 2 && words.any { it in casualWords }) || text in casualPhrases
    }

    fun isGameRelatedMessage(session: GameSession, message: String): Boolean {
        val text = normalizedForParsing(message)
        if (text.isBlank() || mentionedPlayerNames(session, message).isNotEmpty()) return text.isNotBlank()
        val words = text.split(" ").toSet()
        val roleWords = roleAliases.values.flatten().toSet() + secretWords
        return words.any { word ->
            word in gameActionWords ||
                word in gameMetaWords ||
                word in roleWords ||
                gameActionRoots.any(word::startsWith)
        } ||
            hasAnySignal(text, accusationWords) || hasAnySignal(text, defenseWords)
    }

    fun isOffTopicMessage(session: GameSession, message: String): Boolean {
        return message.trim().length >= 12 &&
            !isCasualHumanMessage(message) &&
            !isGameRelatedMessage(session, message)
    }

    private fun explicitlyRejectsVote(text: String, target: String): Boolean {
        if (target.isBlank()) return false
        return listOf(
            "no voto a $target",
            "no votaria a $target",
            "no voy a votar a $target",
            "ni loco voto a $target",
            "ni loca voto a $target"
        ).any(text::contains)
    }

    private fun hasTrustSignal(text: String, target: String): Boolean {
        if (target.isBlank()) return false
        if (text.contains("no confio en $target") || text.contains("no banco a $target")) return false
        return listOf(
            "confio en $target",
            "banco a $target",
            "$target es limpio",
            "$target es limpia",
            "$target es inocente",
            "$target no me parece raro",
            "$target no me parece rara",
            "$target no es traidor",
            "$target no es traidora",
            "$target no es culpable",
            "$target no es sospechoso",
            "$target no es sospechosa",
            "no votaria a $target"
        ).any(text::contains)
    }

    private fun hasAccusationSignal(text: String, target: String): Boolean {
        if (target.isBlank()) return false
        return listOf(
            "$target miente",
            "no confio en $target",
            "$target esta raro",
            "$target esta rara",
            "$target es raro",
            "$target es rara",
            "$target me hace ruido",
            "$target es culpable",
            "$target es sospechoso",
            "$target es sospechosa",
            "$target es traidor",
            "$target es traidora",
            "$target no es inocente",
            "$target no es limpio",
            "$target no es limpia",
            "sospecho de $target",
            "para mi es $target"
        ).any(text::contains)
    }

    private fun hasVoteSignal(text: String, target: String): Boolean {
        if (target.isBlank()) return false
        return listOf("voto a $target", "votaria a $target", "voy con $target").any(text::contains)
    }

    private fun hasNegatedAction(text: String, actions: List<String>): Boolean {
        return actions.any { action ->
            text.contains("no $action") || text.contains("nunca $action")
        }
    }

    private fun targetAfterAction(text: String, targets: List<String>, actions: List<String>): String? {
        val actionIndex = actions
            .map(text::indexOf)
            .filter { it >= 0 }
            .minOrNull()
            ?: return null
        return targets
            .mapNotNull { target ->
                val normalizedTarget = normalizedForParsing(target)
                val exactIndex = text.indexOf(normalizedTarget, startIndex = actionIndex)
                val prefixIndex = if (exactIndex >= 0) {
                    exactIndex
                } else {
                    Regex("[\\p{L}\\p{N}#_-]{4,}")
                        .findAll(text, actionIndex)
                        .firstOrNull { match -> normalizedTarget.startsWith(match.value) }
                        ?.range
                        ?.first
                        ?: -1
                }
                target.takeIf { prefixIndex >= 0 }?.let { it to prefixIndex }
            }
            .minByOrNull { (_, index) -> index }
            ?.first
    }

    private fun statedReasonFrom(text: String): String? {
        val marker = listOf(" porque ", " pq ", " ya que ")
            .mapNotNull { token ->
                text.indexOf(token).takeIf { it >= 0 }?.let { index -> index to token }
            }
            .minByOrNull { it.first }
            ?: return null
        return text.substring(marker.first + marker.second.length)
            .trim()
            .take(80)
            .takeIf { it.length >= 3 }
    }
}
