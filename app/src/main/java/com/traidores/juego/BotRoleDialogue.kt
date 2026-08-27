package com.traidores.juego

internal fun roleDrivenLine(
    session: GameSession,
    bot: GamePlayer,
    read: SuspectRead?,
    social: SocialRead,
    index: Int
): String? {
    if (index > 4 || hasClaimedRole(session, bot.name)) return null
    val roleKey = bot.role?.key ?: return null
    val pressure = social.heated ||
        social.pressured == bot.name ||
        recentPublicMessages(session).any { message ->
            message.speaker != bot.name &&
                mentionsName(message.message, bot.name) &&
                hasAnySignal(message.message, accusationWords)
        }
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:role-line:$index:${socialChatSize(session)}")
    val target = read?.player?.let { safeName(it, session) } ?: "alguien"
    val action = latestOwnAction(session, bot)
    val options = when {
        isTraitor(bot) -> traitorRoleLines(session, target, pressure, seed)
        roleKey == RoleCatalog.MEDICO -> medicRoleLines(session, action, pressure, seed)
        roleKey == RoleCatalog.POLICIA -> policeRoleLines(session, action, target, pressure, seed)
        roleKey == RoleCatalog.DESERTOR -> deserterRoleLines(target, pressure, seed)
        roleKey == RoleCatalog.BUFON -> jesterProvocationLines(target, pressure)
        roleKey == RoleCatalog.ALDEANO && pressure -> listOf(
            "soy pueblo raso, si me sacan por ruido pierden un voto",
            "no tengo carta fuerte, pero tampoco me inventen cosas",
            "soy aldeano, decime q hice y escuchemos antes de quemarme gratis"
        )
        else -> emptyList()
    }
    if (options.isEmpty()) return null
    val shouldSpeak = when {
        pressure -> true
        // El Bufon habla un poco mas seguido que un rol normal (pero no siempre) para hacerse notar.
        roleKey == RoleCatalog.BUFON -> seed % 3 == 0
        session.botDifficulty == BotDifficulty.HARD -> seed % 4 == 0
        else -> seed % 7 == 0
    }
    if (!shouldSpeak) return null
    return chooseFreshLine(options, session, bot, "role-line:$roleKey:$index:$seed")
}
// El Bufon busca que lo expulsen: provoca, molesta y se hace el raro. Sin terminos de rol
// (los limpiaria sanitizeBotSpeech) y con frecuencia moderada para que no gane siempre.
internal fun jesterProvocationLines(
    target: String,
    pressure: Boolean
): List<String> {
    val provocations = listOf(
        "que aburridos todos, si fuera por mi ya habriamos sacado a alguien",
        "yo digo saquenme a mi y listo, total nadie me banca",
        "$target habla mucho pero el mas raro de la mesa soy yo, no?",
        "voten al mas molesto, ah pero ese soy yo, que problema",
        "hagan lo que quieran igual, esto lo termino decidiendo yo",
        "si buscan un culpable facil aca estoy, no se hagan drama"
    )
    val underPressure = listOf(
        "gracias eh, justo queria que me miren a mi",
        "dale si, saquenme, es lo unico interesante que va a pasar hoy",
        "me encanta que desconfien de mi, hago un sospechoso ideal no?",
        "por fin alguien me da bola, voten tranquilos"
    )
    return if (pressure) underPressure else provocations
}

internal fun jesterEmbraceAccusationLine(
    session: GameSession,
    bot: GamePlayer,
    seed: Int
): String {
    val options = listOf(
        "gracias por nombrarme, justo queria un poco de atencion",
        "si me quieren sacar haganlo, no pienso defenderme",
        "dale, tirenme toda la sospecha encima, me la banco",
        "por fin alguien me mira, seria un buen expulsado no?"
    )
    return chooseFreshLine(options, session, bot, "jester-embrace:$seed")
}

internal fun traitorRoleLines(
    session: GameSession,
    target: String,
    pressure: Boolean,
    seed: Int
): List<String> {
    val fakeRole = fakeClaimedRole(seed)
    val label = roleLabel(fakeRole)
    return if (pressure) {
        TRAITOR_FAKE_CLAIM_UNDER_PRESSURE_LINES.map { template ->
            template.replace("\$label", label).replace("\$target", target)
        }
    } else if (session.botDifficulty == BotDifficulty.HARD) {
        listOf(
            "yo por ahora no quiero quemar rol, pero $target tiene que hablar",
            "si necesitan que diga mi rol después lo digo, ahora me importa $target",
            "no regalen roles gratis, primero que $target cierre lo suyo"
        )
    } else {
        emptyList()
    }
}

internal fun medicRoleLines(
    session: GameSession,
    action: GameAction?,
    pressure: Boolean,
    seed: Int
): List<String> {
    val protected = action
        ?.takeIf { it.type == GameActionType.PROTECT }
        ?.target
        ?.let { target -> GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target }
    return when {
        protected != null && pressure -> listOf(
            "soy medico, anoche cuide a $protected. no me saquen sin pensar",
            "me quemo porque me estan por votar: cuide a $protected",
            "si dudan de mi ok, pero mi jugada fue cubrir a $protected"
        )
        protected != null -> listOf(
            "yo tengo una jugada de noche anotada con $protected, no la ignoren",
            "no quiero regalar todo, pero $protected entra en mi lectura de anoche",
            "si hace falta después explico lo de $protected, ahora escuchemos"
        )
        pressure -> listOf(
            "soy medico, no me sirve morir por una corazonada",
            "si me van a sacar, minimo sepan que tengo rol util",
            "me estan apurando y soy medico, aflojen un toque"
        )
        seed % 5 == 0 -> listOf(
            "si no murió nadie, no asumamos cualquiera, ordenemos primero",
            "ojo con leer la noche como prueba total, falta hablar",
            "la noche dio algo de aire, pero no alcanza para votar ciego"
        )
        else -> emptyList()
    }
}

internal fun policeRoleLines(
    session: GameSession,
    action: GameAction?,
    target: String,
    pressure: Boolean,
    seed: Int
): List<String> {
    val checked = action
        ?.takeIf { it.type == GameActionType.INVESTIGATE }
        ?.target
        ?.let { investigated -> GameEngine.playerByName(session, investigated)?.let { safeName(it, session) } ?: investigated }
    return when {
        checked != null && pressure -> listOf(
            "soy detective, mire a $checked. no voy a tirar todo a lo bruto",
            "me estan obligando a quemarme: revise a $checked",
            "soy detective, y mi hilo de anoche pasa por $checked"
        )
        checked != null -> listOf(
            "$checked necesito que hables, tengo una lectura de anoche ahi",
            "yo miraria a $checked con calma, no como voto automatico",
            "tengo un hilo con $checked, pero quiero escuchar antes"
        )
        pressure -> listOf(
            "soy detective, no me saquen por ruido sin preguntarme nada",
            "si me votan asi pierden info, primero pregunten",
            "me puedo revelar si hace falta: soy detective"
        )
        seed % 5 == 0 -> listOf(
            "yo iria por preguntas concretas, no por gritos",
            "$target tiene que explicar una cosa puntual",
            "si alguien cambia version, ahi hay que apretar"
        )
        else -> emptyList()
    }
}

internal fun deserterRoleLines(target: String, pressure: Boolean, seed: Int): List<String> {
    return when {
        pressure -> listOf(
            "a mi no me conviene regalarme, pero tampoco soy el voto de hoy",
            "si me apuran asi solo ayudan a los que estan escondidos",
            "mi carta es rara, pero $target tiene mas para explicar ahora"
        )
        seed % 4 == 0 -> listOf(
            "yo no me caso con ningún bando todavía, quiero ver quién se pisa",
            "me sirve escuchar más, no votar por costumbre",
            "$target me interesa mas por como viene respondiendo"
        )
        else -> emptyList()
    }
}

internal fun traitorFakeClaimLine(
    session: GameSession,
    bot: GamePlayer,
    social: SocialRead,
    index: Int
): String? {
    if (!isTraitor(bot) || index > 1 || hasClaimedRole(session, bot.name)) return null
    val pressure = social.heated || social.pressured == bot.name
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:fake-claim:${socialChatSize(session)}")
    val shouldLie = if (session.botDifficulty == BotDifficulty.HARD) {
        pressure || seed % 7 == 0
    } else {
        pressure && seed % 3 != 0
    }
    if (!shouldLie) return null
    val fakeRole = fakeClaimedRole(seed)
    val label = roleLabel(fakeRole)
    val options = TRAITOR_FAKE_CLAIM_UNDER_PRESSURE_LINES.map { template ->
        template.replace("\$label", label).replace("\$target", GameEngine.humanPlayer(session).name)
    }
    return options[seed % options.size]
}

internal fun fakeClaimedRole(seed: Int): String = when (seed % 3) {
    0 -> RoleCatalog.MEDICO
    1 -> RoleCatalog.POLICIA
    else -> RoleCatalog.ALDEANO
}

internal val TRAITOR_FAKE_CLAIM_UNDER_PRESSURE_LINES = listOf(
    "me estan apurando al pedo, soy \$label y no me conviene decir mas",
    "ok lo digo: soy \$label. no me hagan gastar todo ahora",
    "paren un toque, soy \$label. miren a \$target que viene peor"
)

internal fun traitorPlannedDayLine(
    session: GameSession,
    bot: GamePlayer,
    index: Int
): String? {
    if (!isTraitor(bot)) return null
    val plan = activeTraitorPlanForPublicDay(session) ?: return null
    val cover = plan.cover
    val dirtyTarget = cover?.targetToDirty?.takeIf { isAlivePublicTarget(session, it, bot) }
    val pushTarget = plan.dayPushTarget.takeIf { isAlivePublicTarget(session, it, bot) }
        ?: dirtyTarget

    return when {
        cover?.kind == CoverKind.COUNTER_CLAIM &&
            cover.actor == bot.name &&
            dirtyTarget != null &&
            !hasClaimedRole(session, bot.name) -> {
            val label = roleLabel(cover.fakeRoleKey ?: RoleCatalog.POLICIA)
            if (isOpeningInvestigationStage(session)) {
                "paro ahi: yo tambien soy $label. $dirtyTarget explica como pueden cerrar las dos versiones"
            } else {
                "paro ahi: yo tambien soy $label. $dirtyTarget esta acomodando la historia"
            }
        }
        cover?.kind == CoverKind.COUNTER_CLAIM &&
            cover.backer == bot.name &&
            dirtyTarget != null &&
            cover.actor.isNotBlank() -> if (isOpeningInvestigationStage(session)) {
                "${cover.actor} y $dirtyTarget no pueden tener razon los dos; escuchemos las versiones"
            } else {
                "yo le creo mas a ${cover.actor}; $dirtyTarget viene forzando esa lectura"
            }
        cover?.kind == CoverKind.FAKE_CLAIM &&
            cover.actor == bot.name &&
            !hasClaimedRole(session, bot.name) -> {
            val label = roleLabel(cover.fakeRoleKey ?: RoleCatalog.ALDEANO)
            "lo digo para ordenar: soy $label. no me hagan quemar mas de lo necesario"
        }
        cover?.kind == CoverKind.BUS_ALLY &&
            dirtyTarget != null -> if (isOpeningInvestigationStage(session)) {
                "$dirtyTarget ordena lo que hiciste antes de que alguien cierre una acusacion"
            } else {
                "no voy a tapar a $dirtyTarget, ya queda demasiado raro todo lo que hizo"
            }
        pushTarget != null && index <= 2 -> {
            val read = relationshipReads(session, bot).firstOrNull { it.player.name == pushTarget }
            if (!canVoiceStrongAccusation(session, read)) {
                "$pushTarget, antes de tirar votos quiero saber a quien miras y por que"
            } else if (cover?.backer == bot.name && cover.actor.isNotBlank()) {
                "banco a ${cover.actor}; el voto para mi esta en $pushTarget"
            } else {
                "para mi hoy hay que ordenar con $pushTarget, ${read?.reason.orEmpty()}"
            }
        }
        else -> null
    }
}

private fun isAlivePublicTarget(
    session: GameSession,
    targetName: String,
    speaker: GamePlayer
): Boolean {
    if (targetName.isBlank() || targetName == speaker.name) return false
    return GameEngine.playerByName(session, targetName)?.alive == true
}

internal fun contradictionLine(playerName: String, contradiction: ClaimContradiction): String {
    val firstRole = contradiction.first.roleKey
    val latestRole = contradiction.latest.roleKey
    return if (firstRole != null && latestRole != null) {
        "$playerName espera, el día ${contradiction.first.round} dijiste ${roleLabel(firstRole)} y ahora ${roleLabel(latestRole)}? eso no cierra"
    } else if (
        contradiction.first.statementType in setOf(StatementType.TRUST, StatementType.ACCUSE) &&
        contradiction.latest.statementType in setOf(StatementType.TRUST, StatementType.ACCUSE)
    ) {
        val firstAction = actionLabel(contradiction.first.statementType)
        val latestAction = actionLabel(contradiction.latest.statementType)
        "$playerName primero $firstAction a ${contradiction.first.target} y ahora $latestAction a ${contradiction.latest.target}, explica ese giro"
    } else {
        val action = actionLabel(contradiction.latest.statementType)
        "$playerName dijiste que $action a ${contradiction.first.target} y ahora a ${contradiction.latest.target}, ordena esa version"
    }
}

internal fun contradictionVoteLine(target: String, contradiction: ClaimContradiction): String {
    return if (contradiction.latest.roleKey != null) {
        "voto a $target por la contradiccion de rol, eso no pasa gratis"
    } else {
        "voy con $target, cambio la historia de lo que hizo"
    }
}

internal fun roleLabel(roleKey: String): String {
    return roleAliases[roleKey]?.firstOrNull() ?: roleKey
}

internal fun actionLabel(type: StatementType?): String {
    return when (type) {
        StatementType.PROTECTED -> "protegiste"
        StatementType.INVESTIGATED -> "investigaste"
        StatementType.TRUST -> "bancaste"
        StatementType.ACCUSE -> "acusaste"
        else -> "hiciste eso"
    }
}

internal fun traitorDeflectionLine(
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

internal fun traitorPlanOpeningLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan
): String {
    val mainThreat = plan.threats.firstOrNull()
    return when {
        session.round <= 1 ->
            "arranquemos tranquilos, todavia no hay mucha lectura. yo miraria a ${plan.killTarget.ifBlank { "alguien activo" }}"
        mainThreat?.kind == ThreatKind.DETECTIVE_DECLARADO && mainThreat.markedTraitor != null ->
            "ojo que ${mainThreat.player} se paro de detective y marco a ${mainThreat.markedTraitor}. hay que jugar fino"
        mainThreat?.kind == ThreatKind.NOS_MARCO_SOSPECHA ->
            "${mainThreat.player} esta empujando contra ${mainThreat.markedTraitor ?: bot.name}. si lo dejamos, nos ordena el dia"
        mainThreat?.kind == ThreatKind.JUNTA_VOTOS ->
            "${mainThreat.player} esta juntando gente. no lo dejemos manejar la mesa"
        else ->
            "tenemos noche para ordenar esto. no regalemos roles y salgamos con una sola linea manana"
    }
}

internal fun traitorKillProposalLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan
): String {
    val target = plan.killTarget.ifBlank { return "si no hay objetivo claro, mejor no inventar ruido" }
    return when (plan.killRationale) {
        KillRationale.LIDER_DE_OPINION ->
            "bajemos a $target, esta ordenando demasiado al pueblo"
        KillRationale.CONFIRMA_ROL ->
            "si dejamos vivo a $target puede confirmar rol, me gusta que caiga hoy"
        KillRationale.NOS_MARCO ->
            if (plan.cover?.kind == CoverKind.COUNTER_CLAIM) {
                "$target nos marco, pero matarlo lo confirma. mejor lo cruzamos de dia"
            } else {
                "$target nos marco y puede arrastrar votos, que no llegue comodo al dia"
            }
        KillRationale.JUNTA_VOTOS_LIMPIOS ->
            "$target viene juntando votos limpios, hay que cortarlo antes de que mande la plaza"
        KillRationale.CALLADO_PELIGROSO ->
            "$target habla poco pero lee bastante, esos despues te ganan sin hacer ruido"
        KillRationale.SIN_LECTURA ->
            "no hay lectura fuerte todavia; $target es una baja segura para empezar a mover la mesa"
    }
}

internal fun traitorCoverLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan,
    cover: CoverMove
): String {
    return when (cover.kind) {
        CoverKind.COUNTER_CLAIM -> when (bot.name) {
            cover.actor ->
                "manana yo digo que soy ${roleLabel(cover.fakeRoleKey ?: RoleCatalog.POLICIA)} y cruzo a ${cover.targetToDirty}. que quede palabra contra palabra"
            cover.backer ->
                "dale, yo te banco y le tiro a ${cover.targetToDirty} que se contradijo"
            else ->
                "${cover.actor} cruza a ${cover.targetToDirty}; los demas no lo sobredefendemos"
        }
        CoverKind.FAKE_CLAIM -> when (bot.name) {
            cover.actor ->
                "si me aprietan, tiro ${roleLabel(cover.fakeRoleKey ?: RoleCatalog.ALDEANO)} y salgo por ahi"
            cover.backer ->
                "si te preguntan, te banco suave. sin defenderte de mas"
            else ->
                "dejemos que ${cover.actor} tire rol si lo aprietan, pero sin hacer una muralla"
        }
        CoverKind.BUS_ALLY ->
            "si ${cover.targetToDirty ?: cover.actor} esta quemado, lo soltamos y salvamos la ronda"
        CoverKind.LOW_PROFILE ->
            "perfil bajo. hablamos lo justo y dejamos que el pueblo se pelee solo"
    }
}

internal fun traitorDayPushLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan
): String {
    val target = plan.dayPushTarget.ifBlank { return "manana no empujemos al azar, esperamos quien se regala" }
    return when (personalityFor(session, bot)) {
        BotPersonality.PICANTE,
        BotPersonality.IMPULSIVO ->
            "en votacion empujamos a $target. si duda, lo clavamos ahi"
        BotPersonality.ANALITICO ->
            "manana armemos el caso contra $target con calma, no por manada"
        BotPersonality.DESCONFIADO ->
            "a $target lo quiero incomodo todo el dia, que explique cada cosa"
        else ->
            "de dia llevemos la charla a $target y no nos crucemos entre nosotros"
    }
}

internal fun traitorAgreementLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan
): String {
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:traitor-agree")
    val options = when (personalityFor(session, bot)) {
        BotPersonality.PICANTE -> listOf(
            "cerrado. si el pueblo duda, lo empujamos nosotros",
            "me sirve. sin miedo, pero sin regalarse"
        )
        BotPersonality.JODON -> listOf(
            "jaja hermoso, plan turbio pero prolijo",
            "listo, actuemos normales que es lo mas dificil"
        )
        BotPersonality.ANALITICO -> listOf(
            "bien. victima, coartada y voto tienen que coincidir",
            "ordenado entonces: noche limpia y dia con foco"
        )
        else -> listOf(
            "de una, quedamos asi",
            "cerrado, manana nadie se pisa",
            "me gusta, corto y claro"
        )
    }
    return options[seed % options.size]
}

internal fun shouldSpeakerTakeCoverLine(
    cover: CoverMove,
    speaker: String,
    normalizedLines: List<String>
): Boolean {
    val actorSaidCover = normalizedLines.any {
        it.contains("yo digo") ||
            it.contains("tiro") ||
            it.contains("cruzo") ||
            it.contains("perfil bajo")
    }
    val backerSaidCover = normalizedLines.any {
        it.contains("yo te banco") ||
            it.contains("te banco") ||
            it.contains("no lo sobredefendemos")
    }
    return when (cover.kind) {
        CoverKind.COUNTER_CLAIM,
        CoverKind.FAKE_CLAIM -> when (speaker) {
            cover.actor -> !actorSaidCover
            cover.backer -> actorSaidCover && !backerSaidCover
            else -> actorSaidCover && !backerSaidCover
        }
        CoverKind.LOW_PROFILE -> !actorSaidCover
        CoverKind.BUS_ALLY -> !actorSaidCover
    }
}

/**
 * Respuesta de un traidor bot a lo que escribio el humano en el Plan de los Asesinos.
 * Los bots siempre terminan acompanando el pedido (lo ejecutan en la resolucion de la
 * noche), pero pueden decir que no era su lectura antes de acompanarlo.
 */
internal fun traitorHumanReplyLine(
    session: GameSession,
    bot: GamePlayer,
    plan: TraitorPlan?,
    request: TraitorRequest,
    humanName: String
): String? {
    val seed = stableNoise(
        "${session.code}:${session.round}:${bot.name}:traitor-reply:${request.kind}:${request.target}"
    )
    val target = request.target
    val personality = personalityFor(session, bot)
    val options = when (request.kind) {
        TraitorRequestKind.MATAR -> when {
            target.isBlank() -> listOf(
                "a quien? tirame un nombre y lo hacemos",
                "de acuerdo, pero decime a quien"
            )
            request.targetIsAlly -> listOf(
                "$target es de los nuestros, ni en broma",
                "para, $target juega con nosotros"
            )
            plan?.killTarget.isNullOrBlank() -> listOf(
                "no tenia a nadie fijo todavia, va $target",
                "me sirve, arrancamos por $target"
            )
            plan.killTarget == target -> listOf(
                "de una, $target era lo que venia pensando",
                "cerrado, $target"
            )
            else -> when (personality) {
                BotPersonality.ANALITICO -> listOf(
                    "yo venia con ${plan.killTarget}, pero si lo tenes claro va $target"
                )
                BotPersonality.DESCONFIADO -> listOf(
                    "$target no era mi lectura, igual te sigo"
                )
                BotPersonality.JODON -> listOf(
                    "jaja cambio de planes entonces. dale, $target"
                )
                BotPersonality.PICANTE,
                BotPersonality.IMPULSIVO -> listOf(
                    "me sirve, $target y listo"
                )
                else -> listOf(
                    "no era mi idea, pero vamos con $target"
                )
            }
        }
        TraitorRequestKind.SILENCIAR -> when {
            target.isBlank() -> listOf("a quien callamos?")
            request.targetIsAlly -> listOf("$target es nuestro, dejalo hablar tranquilo")
            bot.role?.key == RoleCatalog.MERCENARIO -> listOf(
                "listo, $target no habla manana",
                "hecho, a $target lo dejo mudo"
            )
            GameEngine.alivePlayers(session).any { it.role?.key == RoleCatalog.MERCENARIO } -> listOf(
                "que lo calle el que puede, $target no tiene que hablar",
                "me gusta, $target callado nos ordena el dia"
            )
            else -> listOf(
                "no tenemos con quien callarlo, pero a $target lo tengo marcado"
            )
        }
        TraitorRequestKind.DESCARTAR -> when {
            target.isBlank() -> listOf("dale, lo dejamos")
            else -> listOf(
                "listo, a $target lo sacamos de la lista",
                "bueno, $target queda afuera por ahora"
            )
        }
        TraitorRequestKind.CUIDADO -> when {
            target.isBlank() -> listOf("a quien le tenemos que tener miedo?")
            else -> listOf(
                "si, $target viene leyendo bien. ojo con eso",
                "lo tengo anotado, $target es el que ordena"
            )
        }
        TraitorRequestKind.COBERTURA -> when {
            plan?.cover?.backer == bot.name -> listOf(
                "para eso estoy, $humanName. te sigo la version"
            )
            else -> listOf(
                "tranquilo $humanName, si te aprietan te banco",
                "quedate piola que manana te defiendo"
            )
        }
        TraitorRequestKind.ROL_FALSO -> listOf(
            "dale, decilo y yo te acompano",
            "va, pero no te pases de detalle que se nota"
        )
        TraitorRequestKind.CIERRE -> listOf(
            "cerrado",
            "de una, asi queda",
            "listo, actuemos normales que es lo dificil"
        )
        TraitorRequestKind.OTRO -> listOf(
            "te escucho, pero cerremos algo antes de que amanezca",
            "puede ser. igual hay que salir con un nombre"
        )
    }
    return options.getOrNull(seed % options.size)
}

internal fun minimumTraitorPlanLines(session: GameSession): Int {
    val traitors = GameEngine.aliveTraitors(session).size.coerceAtLeast(1)
    return (traitors + 2).coerceIn(3, 6)
}

internal fun finishTraitorSpeech(
    raw: String,
    session: GameSession,
    bot: GamePlayer,
    context: String
): String {
    val personality = personalityFor(session, bot)
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:traitor-style:$context")
    var text = seriousNaturalSpeech(raw)
    text = applyPersonalitySignature(
        text,
        personality,
        seed,
        playful = false
    )
    return text
        .replace(Regex("[.!]{1,}$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(140)
}
