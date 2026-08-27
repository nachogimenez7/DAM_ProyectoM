package com.traidores.juego

internal fun informalReason(reason: String?, contextSeed: String = ""): String {
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
        "dos dijeron el mismo rol" -> listOf(
            "dos dijeron el mismo rol",
            "dos personas dijeron lo mismo",
            "ese rol esta peleado"
        )
        "tiro rol y falta detalle" -> listOf(
            "tiro rol pero falta detalle",
            "dijo rol y no cerro nada",
            "lo del rol quedo medio suelto"
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
            "primero dijo un rol y despues otro",
            "dijo dos roles distintos"
        )
        "se contradijo con la accion" -> listOf(
            "cambio lo que dijo que hizo",
            "dio dos versiones de su accion",
            "no sostuvo la misma historia"
        )
        "tengo una pista privada" -> listOf(
            "hay una pista que me lo deja mal",
            "tengo un hilo concreto con el",
            "mi lectura de anoche no me cierra"
        )
        "mi pista lo baja" -> listOf(
            "mi lectura no lo deja arriba",
            "tengo motivos para no cerrarlo ahi",
            "hay una pista que lo baja bastante"
        )
        else -> listOf(
            "hay algo q no me cierra",
            "me hace ruido",
            "algo ahi esta raro"
        )
    }
    return variants[stableNoise("reason:$reason:$contextSeed") % variants.size]
}

internal fun finishSpeech(
    raw: String,
    session: GameSession,
    bot: GamePlayer,
    context: String,
    allowRoleTerms: Boolean = false
): String {
    val personality = personalityFor(session, bot)
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:style:$context")
    var text = seriousNaturalSpeech(raw)
    text = applyPersonalitySignature(
        text,
        personality,
        seed,
        playful = false
    )
    text = text
        .replace(Regex("[.!]{1,}$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    val allowedTerms = if (allowRoleTerms) {
        roleAliases.values.flatten().toSet()
    } else {
        emptySet()
    }
    val safe = sanitizeBotSpeech(text, session, allowedTerms)
    // El Bufon es el unico rol al que se le permite tirarse tierra encima: su victoria es que
    // el pueblo lo expulse, asi que la auto-incriminacion es intencional.
    val guarded = if (bot.role?.key != RoleCatalog.BUFON && isSelfAccusatoryLine(safe, session, bot)) {
        neutralSelfAccusationFallback(session, bot, context)
    } else {
        safe
    }
    return guarded
}

internal fun seriousNaturalSpeech(raw: String): String {
    return raw.lowercase()
        .replace(Regex("\\b(?:(?:ja){2,}|jsjs+|kj{2,})\\b"), "")
        .replace(Regex("\\bq\\b"), "que")
        .replace(Regex("\\bpq\\b"), "por que")
        .replace(Regex("\\btmb\\b"), "tambien")
        .replace(Regex("\\bnose\\b"), "no se")
        .replace(Regex("\\btoy\\b"), "estoy")
        .replace("flasheando", "equivocando")
        .replace("mandar fruta", "acusar sin motivo")
        .replace("vendehumo de feria", "eso no cierra")
        .replace("no me copa", "no me convence")
        .replace("al toque", "de entrada")
        .replace(Regex("\\btranqui\\b"), "con calma")
        .replace(Regex("\\bjoda\\b"), "bromas")
        .replace("una banda", "demasiado")
        .replace("me fui al pasto", "me apure")
        .replace("esta re raro", "esta muy raro")
        .replace("flojisimo", "muy flojo")
        .replace("bancas", "defendes")
        .replace("bancando", "defendiendo")
        .replace("banco", "defiendo")
        .replace(Regex("\\bposta\\b"), "en serio")
        .replace(Regex("\\bamigo\\b[,]?"), "")
        .replace(Regex("\\b(?:che|dale)\\b[,]?"), "")
        .replace(Regex("^(?:(?:che|dale|eh)[, ]+)+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun applyPersonalitySignature(
    text: String,
    personality: BotPersonality,
    seed: Int,
    playful: Boolean = true
): String {
    if (text.length > 110 || seed % 6 != 0) return text
    val variantIndex = Math.floorMod(seed / 6, 3)
    if (personality == BotPersonality.JODON) {
        if (!playful || containsLaugh(text)) return text
        val suffix = listOf("posta jaja", "jaja", "en serio mentira jaja")[variantIndex]
        return "$text $suffix"
    }
    val prefix = when (personality) {
        BotPersonality.TRANQUI -> listOf("igual,", "esperen,", "antes de votar,")[variantIndex]
        BotPersonality.PICANTE -> listOf("te lo digo derecho,", "sin vueltas,", "a las claras,")[variantIndex]
        BotPersonality.DESCONFIADO -> listOf("ojo,", "no se,", "hay algo raro:")[variantIndex]
        BotPersonality.IMPULSIVO -> listOf("bueno,", "para mi,", "yo digo esto:")[variantIndex]
        BotPersonality.ANALITICO -> listOf("mira:", "pensandolo bien:", "ordenemos esto:")[variantIndex]
        BotPersonality.JODON -> error("handled above")
    }
    val prefixStart = prefix.substringBefore(',').substringBefore(':')
    return if (text.startsWith(prefixStart)) text else "$prefix $text"
}

private val leadingBotFillers = listOf(
    "mira vos",
    "tal cual",
    "y si",
    "dale",
    "bien",
    "okey",
    "obvio",
    "claro",
    "bueno",
    "posta",
    "igual",
    "mira",
    "oka",
    "che",
    "eh",
    "ok",
    "ya",
    "nada"
)

internal fun botMessageCore(text: String): String {
    var core = normalizedForParsing(text)
    while (core.isNotBlank()) {
        val filler = leadingBotFillers.firstOrNull { core == it || core.startsWith("$it ") }
            ?: break
        if (core == filler) return ""
        core = core.removePrefix("$filler ").trim()
    }
    return core
}

private fun botMessagesAreEchoes(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    if (first == second) return true
    val shorter = if (first.length <= second.length) first else second
    val longer = if (first.length > second.length) first else second
    return shorter.length >= 14 &&
        shorter.count { it == ' ' } >= 2 &&
        longer.contains(shorter)
}

internal fun List<Pair<String, String>>.dedupeBotMessages(): List<Pair<String, String>> {
    val acceptedCores = mutableListOf<String>()
    return filter { (_, text) ->
        val core = botMessageCore(text)
        if (core.isBlank() || acceptedCores.any { botMessagesAreEchoes(it, core) }) {
            false
        } else {
            acceptedCores += core
            true
        }
    }
}

internal fun List<Pair<String, String>>.dropEchoesOfRecentChat(
    session: GameSession
): List<Pair<String, String>> {
    val botNames = session.players.asSequence()
        .filterNot { it.isHuman }
        .map { it.name }
        .toSet()
    val recentCores = recentPublicMessages(session)
        .filter { it.speaker in botNames }
        .takeLast(6)
        .map { botMessageCore(it.message) }
        .filter { it.isNotBlank() }
    return filterNot { (_, text) ->
        val core = botMessageCore(text)
        recentCores.any { botMessagesAreEchoes(it, core) }
    }
}

internal fun containsLaugh(text: String): Boolean {
    return text.contains("jaja") || text.contains("jsjs") || text.contains("kjjj")
}

internal fun laughFor(seed: Int): String {
    val laughs = listOf("jajaja", "jsjs", "kjjj")
    return laughs[seed % laughs.size]
}

internal fun speechTarget(
    session: GameSession,
    bot: GamePlayer,
    read: SuspectRead?
): String {
    val candidate = read?.player?.takeUnless { it.name == bot.name }
        ?: GameEngine.alivePlayers(session).firstOrNull { it.name != bot.name }
    return candidate?.let { safeName(it, session) } ?: "alguien"
}

internal fun isSelfAccusatoryLine(message: String, session: GameSession, bot: GamePlayer): Boolean {
    val text = normalizedForParsing(message)
    val asksToRemoveSelf = listOf(
        "saquenme", "votenme", "echenme", "expulsenme", "linchenme", "matenme"
    ).any(text::contains)
    if (asksToRemoveSelf) return true
    if (!mentionsName(message, bot.name)) return false
    if (hasAnySignal(message, defenseWords)) return false
    if (!hasAccusatoryTargetSignal(message)) return false
    val mentioned = mentionedPlayerNames(session, message)
    return mentioned.contains(bot.name)
}

internal fun neutralSelfAccusationFallback(session: GameSession, bot: GamePlayer, context: String): String {
    val options = listOf(
        "prefiero escuchar una respuesta mas antes de cerrar",
        "no compro cerrar tan rapido, falta una respuesta concreta",
        "ordenemos un poco antes de votar por inercia",
        "hay que separar dato real de ruido"
    )
    val line = chooseFreshLine(options, session, bot, "self-guard:$context")
    return sanitizeBotSpeech(line, session)
}

internal fun sanitizeBotSpeech(
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
                    "(?<![\\w\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa\\u00fc\\u00f1\\u00c1\\u00c9\\u00cd\\u00d3\\u00da\\u00dc\\u00d1])${Regex.escape(term)}(?![\\w\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa\\u00fc\\u00f1\\u00c1\\u00c9\\u00cd\\u00d3\\u00da\\u00dc\\u00d1])",
                    RegexOption.IGNORE_CASE
                ),
                "esa carta"
            )
        }
    }
    return safe.replace(Regex("\\s+"), " ").trim().take(140)
}

internal fun claimFollowUp(roleKey: String): String {
    return when (roleKey) {
        RoleCatalog.MEDICO -> "a quien cuidaste"
        RoleCatalog.POLICIA -> "a quien investigaste"
        RoleCatalog.ALDEANO -> "a quien miras y por que"
        RoleCatalog.ALCALDE -> "por que no te revelaste antes"
        RoleCatalog.PAYADOR -> "cuando pensas usar la jugada"
        RoleCatalog.ORACULO -> "a quien queres traer"
        else -> "que hiciste"
    }
}
