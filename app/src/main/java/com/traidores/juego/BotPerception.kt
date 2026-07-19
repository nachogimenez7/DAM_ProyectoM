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
        "(^|\\s)(me\\s+)?(dio|salio|resulto|marco\\s+como)\\s+" +
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
        val target = mentionedPlayerNames(session, message).firstOrNull()
        val targetText = target?.let(::normalizedForParsing)
        val publicResult = publicResultPattern.find(text)?.groupValues?.getOrNull(4)

        val statement = when {
            protectedStatementPattern.containsMatchIn(text) ->
                PublicStatement(StatementType.PROTECTED, target)
            text.contains("no digo mi rol") ||
                text.contains("no pienso decir mi rol") ||
                text.contains("no voy a decir rol") ||
                text.contains("no quiero decir rol") ||
                text.contains("no revelo rol") ||
                text.contains("no voy a revelar") ||
                text.contains("prefiero no decir") ||
                text.contains("no digo rol") ->
                PublicStatement(StatementType.REFUSED_ROLE)
            target != null && publicResult in setOf("inocente", "limpio", "limpia") ->
                PublicStatement(StatementType.TRUST, target)
            target != null && publicResult in setOf(
                "sospechoso", "sospechosa", "culpable", "traidor", "traidora"
            ) -> PublicStatement(StatementType.ACCUSE, target)
            target != null && (
                text.contains("confio en $targetText") ||
                    text.contains("banco a $targetText") ||
                    text.contains("$targetText es limpio") ||
                    text.contains("$targetText es limpia") ||
                    text.contains("$targetText es inocente") ||
                    text.contains("$targetText no me parece raro") ||
                    text.contains("$targetText no me parece rara") ||
                    text.contains("$targetText no es") ||
                    text.contains("no votaria a $targetText")
                ) -> PublicStatement(StatementType.TRUST, target)
            target != null && (
                text.contains("$targetText miente") ||
                    text.contains("no confio en $targetText") ||
                    text.contains("$targetText esta raro") ||
                    text.contains("$targetText esta rara") ||
                    text.contains("$targetText es raro") ||
                    text.contains("$targetText es rara") ||
                    text.contains("$targetText me hace ruido") ||
                    text.contains("$targetText es culpable") ||
                    text.contains("$targetText es sospechoso") ||
                    text.contains("$targetText es sospechosa") ||
                    text.contains("$targetText es traidor") ||
                    text.contains("$targetText es traidora") ||
                    text.contains("sospecho de $targetText") ||
                    text.contains("para mi es $targetText")
                ) -> PublicStatement(StatementType.ACCUSE, target)
            target != null && (
                text.contains("voto a $targetText") ||
                    text.contains("votaria a $targetText") ||
                    text.contains("voy con $targetText")
                ) -> PublicStatement(StatementType.VOTE, target)
            investigatedStatementPattern.containsMatchIn(text) ->
                PublicStatement(StatementType.INVESTIGATED, target)
            else -> null
        }
        synchronized(publicStatementCache) {
            publicStatementCache[cacheKey] = CachedValue(statement)
        }
        return statement
    }

    fun humanQuestionKind(message: String): HumanQuestionKind? {
        val text = normalizedForParsing(message)
        return when {
            text.contains("que soy") || text.contains("quien soy") || text.contains("q soy") ||
                text.contains("cual es mi rol") || text.contains("que rol soy") ||
                text.contains("q rol soy") || text.contains("mi rol") -> HumanQuestionKind.ROLE_HELP
            text.contains("a quien voto") || text.contains("a quien votamos") ||
                text.contains("quien voto") || text.contains("voto a quien") -> HumanQuestionKind.VOTE_HELP
            text.contains("que hago") || text.contains("q hago") ||
                text.contains("que deberia hacer") || text.contains("como juego") -> HumanQuestionKind.ACTION_HELP
            text.contains("quien sospecha") || text.contains("de quien sospechan") ||
                text.contains("a quien miramos") || text.contains("quien les parece") -> HumanQuestionKind.SUSPECT_HELP
            else -> null
        }
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
}
