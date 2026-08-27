package com.traidores.juego

internal fun openingIntent(session: GameSession, bot: GamePlayer, index: Int): BotSpeechIntent {
    val personality = personalityFor(session, bot)
    return when (personality) {
        BotPersonality.TRANQUI -> if (index == 0) BotSpeechIntent.CALM_DOWN else BotSpeechIntent.ASK
        BotPersonality.PICANTE -> BotSpeechIntent.ACCUSE
        BotPersonality.JODON -> BotSpeechIntent.ASK
        BotPersonality.DESCONFIADO -> BotSpeechIntent.ASK
        BotPersonality.IMPULSIVO -> BotSpeechIntent.ACCUSE
        BotPersonality.ANALITICO -> if (unansweredQuestionFor(session, bot) != null) BotSpeechIntent.FOLLOW_UP else BotSpeechIntent.ASK
    }
}
internal fun reactionIntent(
    session: GameSession,
    bot: GamePlayer,
    humanMessage: String,
    focusNames: Set<String>,
    mood: BotMood,
    index: Int,
    memory: BotMemory
): BotSpeechIntent {
    val personality = personalityFor(session, bot)
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:intent:$index:$humanMessage")
    if (mood == BotMood.DEFENSIVE) return BotSpeechIntent.DEFEND
    if (memory.unansweredTarget?.let { focusNames.contains(it) || mentionsName(humanMessage, it) } == true) {
        return BotSpeechIntent.FOLLOW_UP
    }
    if (
        session.botDifficulty == BotDifficulty.HARD &&
        memory.pendingHumanQuestion != null &&
        index <= 1
    ) {
        return BotSpeechIntent.FOLLOW_UP
    }
    if (
        session.botDifficulty == BotDifficulty.NORMAL &&
        focusNames.isNotEmpty() &&
        index > 0 &&
        seed % 3 == 0
    ) {
        return BotSpeechIntent.ADMIT_DOUBT
    }
    if (humanMessage.trim().endsWith("?")) return if (index == 0) BotSpeechIntent.ASK else BotSpeechIntent.ADMIT_DOUBT
    if (focusNames.isNotEmpty() && index == 0) return BotSpeechIntent.ASK
    when (agendaFor(session, bot)) {
        BotAgenda.ASK_ROLES -> if (index == 0) return BotSpeechIntent.ASK
        BotAgenda.CALM_TABLE -> if (index == 0) return BotSpeechIntent.CALM_DOWN
        BotAgenda.PUSH_VOTE -> if (index == 0) return BotSpeechIntent.ACCUSE
        BotAgenda.DEFEND_WEAK -> if (index == 0) return BotSpeechIntent.DEFEND
        BotAgenda.FOLLOW_THREAD -> if (memory.lastPressuredTarget != null) return BotSpeechIntent.FOLLOW_UP
        BotAgenda.DEFLECT_PRESSURE -> if (index == 0) return BotSpeechIntent.ACCUSE
    }
    return when (personality) {
        BotPersonality.TRANQUI -> listOf(BotSpeechIntent.CALM_DOWN, BotSpeechIntent.ASK, BotSpeechIntent.ADMIT_DOUBT)[seed % 3]
        BotPersonality.PICANTE -> listOf(BotSpeechIntent.ACCUSE, BotSpeechIntent.ASK, BotSpeechIntent.TEASE)[seed % 3]
        BotPersonality.JODON -> listOf(BotSpeechIntent.TEASE, BotSpeechIntent.ASK, BotSpeechIntent.ACCUSE)[seed % 3]
        BotPersonality.DESCONFIADO -> listOf(BotSpeechIntent.ASK, BotSpeechIntent.FOLLOW_UP, BotSpeechIntent.ACCUSE)[seed % 3]
        BotPersonality.IMPULSIVO -> listOf(BotSpeechIntent.ACCUSE, BotSpeechIntent.DEFEND, BotSpeechIntent.ADMIT_DOUBT)[seed % 3]
        BotPersonality.ANALITICO -> listOf(BotSpeechIntent.ASK, BotSpeechIntent.FOLLOW_UP, BotSpeechIntent.ADMIT_DOUBT)[seed % 3]
    }
}

internal fun conversationRole(index: Int): BotConversationRole {
    return when (index) {
        0 -> BotConversationRole.OPENER
        1 -> BotConversationRole.FOLLOWER
        2 -> BotConversationRole.SKEPTIC
        3 -> BotConversationRole.CALMER
        else -> BotConversationRole.CLOSER
    }
}

internal fun toneAdjustedIntent(session: GameSession, intent: BotSpeechIntent): BotSpeechIntent {
    if (intent != BotSpeechIntent.TEASE) return intent
    return if (session.botDifficulty == BotDifficulty.HARD) {
        BotSpeechIntent.ACCUSE
    } else {
        BotSpeechIntent.ASK
    }
}

internal fun hardOpeningLine(
    session: GameSession,
    bot: GamePlayer,
    role: BotConversationRole,
    target: String,
    index: Int
): String? {
    if (session.botDifficulty != BotDifficulty.HARD || role != BotConversationRole.OPENER) return null
    val options = listOf(
        "vamos por partes, quien tiene una contradiccion real?",
        "$target, que rol decis tener?",
        "$target, que hiciste anoche?"
    )
    return chooseFreshLine(options, session, bot, "hard-opening:$target:$index:${socialChatSize(session)}")
}

internal fun coordinatedIntent(
    session: GameSession,
    base: BotSpeechIntent,
    role: BotConversationRole,
    hasStrongRead: Boolean,
    hasThread: Boolean
): BotSpeechIntent {
    if (session.botDifficulty == BotDifficulty.HARD && hasThread && role != BotConversationRole.CALMER) {
        return if (hasStrongRead || base == BotSpeechIntent.FOLLOW_UP) BotSpeechIntent.FOLLOW_UP else BotSpeechIntent.ASK
    }
    if (base == BotSpeechIntent.FOLLOW_UP && hasThread) return BotSpeechIntent.FOLLOW_UP
    if (base == BotSpeechIntent.DEFEND) return when (role) {
        BotConversationRole.SKEPTIC -> if (hasStrongRead) BotSpeechIntent.ASK else BotSpeechIntent.ADMIT_DOUBT
        BotConversationRole.CALMER -> BotSpeechIntent.CALM_DOWN
        else -> BotSpeechIntent.DEFEND
    }
    return when (role) {
        BotConversationRole.OPENER -> when {
            base in setOf(BotSpeechIntent.ACCUSE, BotSpeechIntent.TEASE) && !hasStrongRead -> BotSpeechIntent.ASK
            else -> base
        }
        BotConversationRole.FOLLOWER -> when (base) {
            BotSpeechIntent.ACCUSE,
            BotSpeechIntent.TEASE -> if (hasStrongRead) BotSpeechIntent.FOLLOW_UP else BotSpeechIntent.ASK
            BotSpeechIntent.CALM_DOWN -> BotSpeechIntent.DEFEND
            else -> base
        }
        BotConversationRole.SKEPTIC -> when {
            hasStrongRead -> when (base) {
                BotSpeechIntent.CALM_DOWN,
                BotSpeechIntent.DEFEND -> BotSpeechIntent.ASK
                else -> BotSpeechIntent.ACCUSE
            }
            else -> BotSpeechIntent.ADMIT_DOUBT
        }
        BotConversationRole.CALMER -> BotSpeechIntent.CALM_DOWN
        BotConversationRole.CLOSER -> when {
            hasThread -> BotSpeechIntent.FOLLOW_UP
            hasStrongRead -> BotSpeechIntent.ADMIT_DOUBT
            else -> BotSpeechIntent.DEFEND
        }
    }
}

internal fun coordinationLine(
    session: GameSession,
    bot: GamePlayer,
    role: BotConversationRole,
    target: String,
    reason: String,
    hasThread: Boolean
): String? {
    val options = when (role) {
        BotConversationRole.CALMER -> listOf(
            "paren un toque, no votemos solo porque todos repiten $target",
            "bajen un cambio, primero escuchemos a $target y después vemos",
            "ordenemos: una pregunta para $target y una respuesta clara"
        )
        BotConversationRole.CLOSER -> if (hasThread) {
            listOf(
                "me falta cerrar lo de $target, pero no lo venderia como seguro",
                "lo de $target queda arriba, aunque puedo estar flasheando",
                "si $target responde bien, cambiaria el voto"
            )
        } else {
            listOf(
                "por ahora no cerraría voto, falta una punta más",
                "no veo una acusación limpia todavía",
                "si nadie suma algo concreto esto queda medio al aire"
            )
        }
        else -> emptyList()
    }
    if (options.isEmpty()) return null
    val seed = "coordination:$role:$target:$reason:${socialChatSize(session)}"
    return chooseFreshLine(options, session, bot, seed)
}

internal fun pendingAnswerReply(
    session: GameSession,
    bot: GamePlayer,
    humanMessage: String,
    memory: BotMemory,
    index: Int
): String {
    val pending = memory.pendingHumanQuestion
    val claim = LocalBotAi.roleClaimFrom(humanMessage)
    val statement = LocalBotAi.publicStatementFrom(session, humanMessage)
    val statementTarget = statement?.target?.let { target ->
        GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target
    }
    val mentionedTarget = mentionedPlayerNames(session, humanMessage)
        .firstOrNull { it != GameEngine.humanPlayer(session).name }
        ?.let { target -> GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target }
    val botWasMentioned = mentionedTarget != null &&
        normalizedForParsing(mentionedTarget) == normalizedForParsing(safeName(bot, session))
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:pending-answer:$index:$humanMessage")
    val priorStatement = if (isDirectClarification(humanMessage)) {
        previousHumanStatement(session, humanMessage)
    } else {
        null
    }
    val priorTarget = priorStatement?.target?.let { target ->
        GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target
    }
    val options = when {
        pending != null && pending.speaker == bot.name && priorStatement?.type == StatementType.TRUST && priorTarget != null -> listOf(
            "ah ok, me lo decías a mi. entonces $priorTarget queda más limpio por ahora",
            "listo, entendi. si tu dato es $priorTarget limpio, no lo voto hoy",
            "ok, tomo esa lectura sobre $priorTarget. ahora busquemos quien queda peor"
        )
        pending != null && pending.speaker == bot.name && priorStatement?.type == StatementType.ACCUSE && priorTarget != null -> listOf(
            "ah ok, me lo decias a mi. entonces sigamos con $priorTarget",
            "listo, entendi. si $priorTarget te dio mal, tiene que contestar",
            "ok, vuelvo a $priorTarget entonces, no cambiemos de hilo"
        )
        claim != null -> listOf(
            "ok, dijiste ${claim.label}. ahora falta ver si alguien te cruza",
            "bien, queda ese rol anotado. no lo cambiemos después eh",
            "listo, dijiste ${claim.label}. ahora explica la jugada sin regalar de más"
        )
        botWasMentioned -> listOf(
            "me nombraste a mi, que queres que responda?",
            "si, soy yo. decime que queres saber",
            "me estas mirando a mi? decime por que"
        )
        statement?.type in setOf(StatementType.ACCUSE, StatementType.VOTE) && statementTarget != null -> listOf(
            "ok, entonces estas mirando a $statementTarget. que responda eso",
            "bien, ya tiraste nombre: $statementTarget tiene que contestar",
            "eso ya es una punta. no saltemos de $statementTarget tan rapido"
        )
        statement?.type == StatementType.TRUST && statementTarget != null -> listOf(
            "ok, bancas a $statementTarget. deci por que y vemos si cierra",
            "bien, queda que confias en $statementTarget, pero necesito razon",
            "si $statementTarget te cierra, explica que viste"
        )
        mentionedTarget != null -> listOf(
            "ok, entonces estas mirando a $mentionedTarget. sigamos por ahi",
            "bien, nombraste a $mentionedTarget. que conteste algo",
            "eso ya es una punta con $mentionedTarget, no la dejemos colgada"
        )
        statement?.type == StatementType.REFUSED_ROLE -> listOf(
            "ok, no digas rol, pero entonces tira una lectura",
            "bien, no te quemes, pero aporta algo de la ronda",
            "te banco no revelar, pero no te quedes sin decir nada"
        )
        pending != null && pending.speaker == bot.name -> listOf(
            "ok, eso ya me sirve mas. ahora decime si bancas algun nombre",
            "bien, al menos contestaste. no lo cierro pero te saco un toque de encima",
            "eso queria escuchar, ahora veamos quien se sube raro"
        )
        pending != null -> listOf(
            "ahi le respondiste a ${pending.speaker}, sigamos ese hilo",
            "ok, ${pending.speaker} pidio eso y ya contestaste, no saltemos a otra cosa",
            "bien, esa respuesta suma. ahora falta ver si cierra con lo anterior"
        )
        else -> listOf(
            "ok, tomo eso. falta ver si alguien lo contradice",
            "bien, queda anotado. ahora que responda el resto",
            "eso ayuda mas que tirar nombres al aire"
        )
    }
    return options[seed % options.size]
}

internal fun humanQuestionReply(
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
    val reason = informalReason(read?.reason(), "human-question:$index:${socialChatSize(session)}")
    val hasRead = memory.lastPressuredTarget != null || (read != null && !isWeakSuspicion(read))
    val options = when (kind) {
        HumanQuestionKind.ROLE_HELP -> listOf(
            "tu carta la sabes vos, no la regales al toque. conta algo de la ronda",
            "si vas a decir rol, decilo con una razon. sino habla de lo que viste",
            "no quemes rol porque si, primero fijate quien te esta apurando",
            "yo no diria rol gratis. tira una lectura y vemos quien salta"
        )
        HumanQuestionKind.VOTE_HELP -> if (hasRead) {
            listOf(
                "si votas ahora yo miraria a $target, $reason",
                "para mi antes de votar hay que hacer hablar a $target",
                "yo no votaria ciego, pero $target tiene que cerrar eso"
            )
        } else {
            listOf(
                "todavía no votaría apurado, falta escuchar más",
                "por ahora no tengo voto claro, preguntemos primero",
                "si votamos ahora es medio al aire, esperaria una respuesta mas"
            )
        }
        HumanQuestionKind.ACTION_HELP -> listOf(
            "aporta algo concreto sin regalar toda tu carta",
            "pregunta tranqui y mira quien responde raro",
            "no te desesperes, marca una duda y fijate quien se sube",
            "si no sabes que hacer, pregunta por una contradiccion o por un voto"
        )
        HumanQuestionKind.SUSPECT_HELP -> if (hasRead) {
            listOf(
                "yo estoy mirando a $target porque $reason",
                "$target me hace ruido, pero quiero escucharlo antes",
                "si tengo que marcar a uno ahora diria $target, no como sentencia"
            )
        } else {
            listOf(
                "todavía nadie me cierra como culpable fuerte",
                "por ahora no hay sospecha limpia, hay que hablar más",
                "no tengo nombre firme, ojo con votar por costumbre"
            )
        }
        HumanQuestionKind.WHY_VOTE,
        HumanQuestionKind.WHY_ACCUSE,
        HumanQuestionKind.EXPLAIN_STANCE,
        HumanQuestionKind.OPINION,
        HumanQuestionKind.BELIEF,
        HumanQuestionKind.ASK_ROLE -> listOf(
            "esa pregunta es para quien nombraste, que responda sin vueltas",
            "quiero escuchar primero al que estas cruzando",
            "que conteste el involucrado y despues opino"
        )
    }
    return chooseFreshLine(options, session, bot, "human-question:$kind:$index:${socialChatSize(session)}")
}

internal fun directHumanQuestionReply(
    session: GameSession,
    bot: GamePlayer,
    humanMessage: String,
    kind: HumanQuestionKind
): String {
    if (kind == HumanQuestionKind.ASK_ROLE) return directRoleQuestionReply(session, bot)
    val human = GameEngine.humanPlayer(session)
    val mentionedTarget = mentionedPlayerNames(session, humanMessage)
        .firstOrNull { it != bot.name && it != human.name }
        ?.let { GameEngine.playerByName(session, it) }
    val latestVoteAction = session.actionHistory
        .asReversed()
        .firstOrNull { action -> action.actor == bot.name && action.type == GameActionType.VOTE }
    val latestVoteTarget = latestVoteAction?.target?.let { GameEngine.playerByName(session, it) }
    val latestStance = session.claimLedger[bot.name].orEmpty()
        .asReversed()
        .firstOrNull { record ->
            record.target != null && record.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE)
        }
    val declaredTarget = latestStance?.target?.let { GameEngine.playerByName(session, it) }
    val rankedRead = rankedPublicSuspects(session, bot).firstOrNull()
    val rankedTarget = rankedRead
        ?.takeUnless(::isWeakSuspicion)
        ?.player
    val target = when (kind) {
        HumanQuestionKind.WHY_VOTE -> latestVoteTarget ?: mentionedTarget
        HumanQuestionKind.WHY_ACCUSE,
        HumanQuestionKind.EXPLAIN_STANCE -> declaredTarget ?: mentionedTarget ?: latestVoteTarget ?: rankedTarget
        HumanQuestionKind.OPINION -> mentionedTarget ?: declaredTarget ?: rankedTarget
        HumanQuestionKind.VOTE_HELP,
        HumanQuestionKind.SUSPECT_HELP -> declaredTarget ?: latestVoteTarget ?: rankedTarget
        else -> mentionedTarget ?: declaredTarget ?: rankedTarget
    }
    val targetRead = target?.let { relationshipRead(session, bot, it) }
    val targetName = target?.let { safeName(it, session) } ?: "esa persona"
    val recordedReason = latestStance
        ?.takeIf { stance -> stance.target == target?.name }
        ?.reason
    val reason = informalReason(
        recordedReason ?: targetRead?.reason,
        "direct-question:${bot.name}:$kind:${socialChatSize(session)}"
    )
    val options = when (kind) {
        HumanQuestionKind.WHY_VOTE -> when {
            latestVoteAction == null -> listOf(
                "todavia no vote a nadie, sigo escuchando antes de cerrarlo",
                "no tengo un voto hecho para justificar; primero quiero ordenar la ronda"
            )
            mentionedTarget != null && latestVoteTarget?.name != mentionedTarget.name -> listOf(
                "no vote a ${safeName(mentionedTarget, session)}; mi voto fue a $targetName por $reason",
                "ojo, fui con $targetName, no con ${safeName(mentionedTarget, session)}. fue por $reason"
            )
            target?.isHuman == true -> listOf(
                "te vote porque $reason, no fue por copiar al resto",
                "fui con vos por $reason. si cambia eso puedo revisar",
                "mi voto contra vos salio de esto: $reason"
            )
            else -> listOf(
                "fui con $targetName porque $reason, no fue por copiar al resto",
                "vote a $targetName por $reason. si cambia eso puedo revisar",
                "mi voto a $targetName salio de esto: $reason"
            )
        }
        HumanQuestionKind.WHY_ACCUSE -> when {
            latestStance == null -> listOf(
                "todavia no acuse a nadie como seguro, estoy tratando de ordenar las versiones",
                "no tengo una acusacion cerrada; si marco a alguien quiero tener un motivo"
            )
            mentionedTarget != null && declaredTarget?.name != mentionedTarget.name -> listOf(
                "no estaba marcando a ${safeName(mentionedTarget, session)}; venia mirando a $targetName por $reason",
                "mi duda era con $targetName, no con ${safeName(mentionedTarget, session)}. me hizo ruido por $reason"
            )
            target?.isHuman == true -> listOf(
                "te marque porque $reason, pero quiero escuchar tu respuesta",
                "lo tuyo me hace ruido por $reason, no lo invente de la nada",
                "te acuse por $reason. si lo explicas bien, aflojo"
            )
            else -> listOf(
                "marque a $targetName porque $reason, pero quiero escuchar su respuesta",
                "lo de $targetName me hace ruido por $reason, no lo invente de la nada",
                "acuse a $targetName por $reason. si lo explica bien, aflojo"
            )
        }
        HumanQuestionKind.EXPLAIN_STANCE -> if (target != null) {
            listOf(
                "lo digo por $targetName: $reason",
                "mi punto con $targetName es este: $reason",
                "vengo siguiendo a $targetName por $reason; si aparece algo mejor, cambio"
            )
        } else {
            listOf(
                "todavia no tire una acusacion concreta; me falta una respuesta clara",
                "no tengo una postura cerrada para defender, sigo juntando datos"
            )
        }
        HumanQuestionKind.OPINION -> if (target == null) {
            listOf(
                "decime de quien queres mi opinion y te respondo directo",
                "sobre quien? dame un nombre y te digo como lo veo"
            )
        } else when (targetRead?.level) {
            TrustLevel.CONFIA -> listOf(
                "a $targetName lo vengo bancando, por ahora me cierra mas que el resto",
                "$targetName hoy me parece bastante limpio, aunque no lo doy por seguro"
            )
            TrustLevel.SOSPECHA,
            TrustLevel.PRESIONA -> listOf(
                "$targetName me hace ruido porque $reason",
                "a $targetName lo tengo arriba; necesito que explique $reason"
            )
            else -> listOf(
                "con $targetName estoy en duda, no tengo una prueba fuerte todavia",
                "$targetName no me cierra del todo, pero tampoco lo votaria ciego"
            )
        }
        HumanQuestionKind.BELIEF -> {
            val humanRead = relationshipRead(session, bot, human)
            when (humanRead.level) {
                TrustLevel.CONFIA -> listOf(
                    "por ahora te creo, venis sosteniendo lo que decis",
                    "te banco por ahora, pero no cambies la historia despues"
                )
                TrustLevel.SOSPECHA,
                TrustLevel.PRESIONA -> listOf(
                    "no del todo, me haces ruido porque ${informalReason(humanRead.reason, "belief:${bot.name}")}",
                    "todavia no te compro; necesito una respuesta mas concreta"
                )
                else -> listOf(
                    "te doy el beneficio de la duda, pero no estoy cerrado",
                    "mitad y mitad: te escucho, pero quiero ver si sostenes eso"
                )
            }
        }
        HumanQuestionKind.VOTE_HELP,
        HumanQuestionKind.SUSPECT_HELP -> if (target != null) {
            val cautiousSuffix = if (session.botDifficulty == BotDifficulty.NORMAL) {
                ", aunque primero quiero escucharlo"
            } else {
                ", es la lectura que mejor cierra ahora"
            }
            listOf(
                "si tengo que elegir ahora, voy con $targetName por $reason$cautiousSuffix",
                "hoy tengo mas arriba a $targetName: $reason$cautiousSuffix",
                "mi principal duda es $targetName por $reason$cautiousSuffix"
            )
        } else {
            listOf(
                "todavia no tengo un nombre firme; preguntaria antes de votar",
                "no voy a inventar un sospechoso: me falta escuchar una respuesta"
            )
        }
        HumanQuestionKind.ACTION_HELP -> listOf(
            "hace una pregunta concreta sobre la ronda y fijate si la respuesta coincide con lo anterior",
            "marca una duda con motivo, escucha la defensa y recien despues decidi el voto",
            "revisa lo que se dijo, pregunta por una contradiccion y no regales tu carta porque si"
        )
        HumanQuestionKind.ROLE_HELP -> listOf(
            "tu carta la ves vos; lee su objetivo y no la reveles sin necesidad",
            "mira tu carta y usa el boton de la fase cuando corresponda; no regales el rol de entrada"
        )
        else -> listOf("te respondo directo: todavia no lo tengo claro")
    }
    return chooseFreshLine(options, session, bot, "direct-question:$kind:$humanMessage")
}

internal fun humanDoubtReply(
    session: GameSession,
    bot: GamePlayer,
    read: SuspectRead?,
    index: Int
): String {
    val target = read?.takeUnless { isWeakSuspicion(it) }?.player?.let { safeName(it, session) }
    val options = if (target != null) {
        listOf(
            "esta bien dudar, pero entonces preguntale algo concreto a $target",
            "si no estas seguro, hagamos hablar a $target antes de votar",
            "banco la duda, pero no la dejemos en el aire: $target tiene que responder"
        )
    } else {
        listOf(
            "esta bien no estar seguro, pero tiremos preguntas concretas",
            "si estamos todos dudando, nadie vote por impulso",
            "ok, entonces ordenemos: quien tiene un dato real?"
        )
    }
    return chooseFreshLine(options, session, bot, "human-doubt:$index:${socialChatSize(session)}")
}

internal fun casualHumanReply(
    session: GameSession,
    bot: GamePlayer,
    humanMessage: String,
    index: Int
): String {
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:casual:$index:$humanMessage")
    val options = listOf(
        "hola, pero hablemos de la ronda",
        "buenas, quien les parece raro?",
        "estoy, pero primero ordenemos que paso",
        "por ahora no tengo nada fuerte, quiero escuchar",
        "a quien votarias ahora?"
    )
    return chooseFreshLine(options, session, bot, "casual:$seed")
}

internal fun offTopicReply(
    session: GameSession,
    bot: GamePlayer,
    repeated: Boolean,
    index: Int
): String {
    val options = if (repeated) {
        listOf(
            "volvamos a la partida, quien te parece raro?",
            "eso no ayuda a decidir el voto, a quien miras?"
        )
    } else {
        listOf("volvamos a la ronda, quien te genera mas dudas?")
    }
    return chooseFreshLine(options, session, bot, "off-topic:$repeated:$index:${socialChatSize(session)}")
}

internal fun lowEvidenceOpeningLine(
    session: GameSession,
    bot: GamePlayer,
    target: String,
    index: Int
): String {
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:low-evidence:$index:${socialChatSize(session)}")
    val options = listOf(
        "por ahora no tengo nada fuerte, escuchemos versiones",
        "$target, que rol decis tener?",
        "$target, que hiciste anoche?",
        "antes de votar quiero saber por que sospechan",
        "si alguien tiene un dato que lo diga sin mostrar toda la carta"
    )
    return chooseFreshLine(options, session, bot, "low-evidence:$seed")
}

internal fun pastRoundThreadLine(session: GameSession, bot: GamePlayer, index: Int): String? {
    if (index > 2 || session.round <= 1) return null
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:past-thread:$index:${socialChatSize(session)}")
    if (index > 0 && seed % 3 == 1) return null

    previousDetectiveReadLine(session, bot, index)?.let { return it }
    previousStanceFlipLine(session, bot)?.let { return it }
    return null
}

private fun previousDetectiveReadLine(session: GameSession, bot: GamePlayer, index: Int): String? {
    val read = session.tableMemory.declaredInvestigationReads
        .asReversed()
        .firstOrNull {
            it.round < session.round &&
                normalizedForParsing(it.result) in setOf("sospechoso", "sospechosa", "traidor", "culpable") &&
                GameEngine.playerByName(session, it.target)?.alive == true &&
                it.source != bot.name
        }
        ?: return null
    val target = GameEngine.playerByName(session, read.target)?.let { safeName(it, session) } ?: read.target
    val source = GameEngine.playerByName(session, read.source)?.let { safeName(it, session) } ?: read.source
    val sourceAlive = GameEngine.playerByName(session, read.source)?.alive == true
    return when {
        !sourceAlive ->
            "antes de morir, $source habia marcado a $target. no lo borremos del hilo"
        index == 0 ->
            "$source habia marcado a $target y todavia falta una respuesta clara"
        else ->
            "lo de $target no nace de la nada: ya venia marcado por $source"
    }
}

private fun previousStanceFlipLine(session: GameSession, bot: GamePlayer): String? {
    val aliveNames = GameEngine.alivePlayers(session).map { it.name }.toSet()
    session.claimLedger.forEach { (speaker, records) ->
        if (speaker == bot.name || speaker !in aliveNames) return@forEach
        records
            .filter { it.target in aliveNames }
            .groupBy { it.target.orEmpty() }
            .forEach { (target, targetRecords) ->
                val trusted = targetRecords.firstOrNull { it.statementType == StatementType.TRUST }
                val pushed = targetRecords.lastOrNull {
                    it.statementType in setOf(StatementType.ACCUSE, StatementType.VOTE) &&
                        trusted != null &&
                        it.round > trusted.round
                }
                if (trusted != null && pushed != null) {
                    val shownSpeaker = GameEngine.playerByName(session, speaker)?.let { safeName(it, session) } ?: speaker
                    val shownTarget = GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target
                    return "ayer $shownSpeaker bancaba a $shownTarget y ahora lo empuja. eso hay que explicarlo"
                }
            }
    }
    return null
}

internal fun eliminationLastWordsLine(session: GameSession, player: GamePlayer): String {
    val seed = stableNoise("${session.code}:${session.round}:${player.name}:last-words:${session.voteRound}")
    val options = when {
        GameRules.isTraitorRole(player.role) -> listOf(
            "miren bien quienes empujaron este voto, despues no digan que no avise",
            "se van a acordar de este voto cuando sea tarde",
            "buena suerte con lo que dejaron vivo"
        )
        player.role?.key == RoleCatalog.ALDEANO -> listOf(
            "me sacan sin accion para defenderme, revisen quien apuro esto",
            "soy pueblo raso, si sale mal miren a los que cerraron rapido",
            "no tenia poder, tenia lectura. no la tiren a la basura"
        )
        else -> listOf(
            "si esto sale mal, revisen quien no quiso escuchar",
            "me voy, pero el hilo queda: no voten por inercia manana",
            "acuerdense de quien cambio la historia antes de cerrar"
        )
    }
    return finishSpeech(
        raw = options[seed % options.size],
        session = session,
        bot = player,
        context = "last-words:${session.phaseIndex}",
        allowRoleTerms = true
    )
}

internal fun lineForIntent(
    session: GameSession,
    bot: GamePlayer,
    intent: BotSpeechIntent,
    target: String,
    reason: String,
    contextSeed: String
): String {
    val personality = personalityFor(session, bot)
    val spokenTarget = target.takeUnless { it.equals(bot.name, ignoreCase = true) } ?: "alguien"
    val lines = linesFor(intent, spokenTarget, reason)
    val offset = if (personality == BotPersonality.ANALITICO) 1 else 0
    val index = stableNoise("${session.code}:${session.round}:${bot.name}:$intent:$spokenTarget:$contextSeed") + offset
    return chooseFreshLine(lines, session, bot, "$intent:$spokenTarget:$contextSeed:$index")
}

internal fun chooseFreshLine(
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

internal fun linesFor(intent: BotSpeechIntent, spokenTarget: String, reason: String): List<String> {
    return when (intent) {
        BotSpeechIntent.ASK -> listOf(
            "$spokenTarget, que rol decis tener?",
            "$spokenTarget, que hiciste anoche?",
            "$spokenTarget, por que decis eso?",
            "$spokenTarget, explica bien lo tuyo",
            "$spokenTarget, a quien votarias ahora?",
            "$spokenTarget, dame una razon concreta"
        )
        BotSpeechIntent.FOLLOW_UP -> listOf(
            "$spokenTarget, no respondiste lo que te preguntaron",
            "$spokenTarget, responde eso primero",
            "$spokenTarget estas evitando la pregunta",
            "$spokenTarget, falta que expliques $reason",
            "$spokenTarget no cambies de tema, cerra lo anterior",
            "todavia falta la respuesta de $spokenTarget"
        )
        BotSpeechIntent.ACCUSE -> listOf(
            "para mi hay que mirar a $spokenTarget por $reason",
            "$spokenTarget no me cierra",
            "$spokenTarget esta muy raro por $reason",
            "$spokenTarget viene muy flojo con eso",
            "$spokenTarget cada vez me cierra menos",
            "$spokenTarget afirma mucho y explica poco"
        )
        BotSpeechIntent.DEFEND -> listOf(
            "eso no alcanza para votar a $spokenTarget",
            "yo a $spokenTarget no lo veo tan raro todavia",
            "dejen que $spokenTarget explique",
            "capaz nos estamos equivocando con $spokenTarget",
            "no cerremos tan rapido contra $spokenTarget",
            "si vamos contra $spokenTarget que sea por algo concreto"
        )
        BotSpeechIntent.TEASE -> listOf(
            "$spokenTarget, esa explicacion no alcanza",
            "$spokenTarget, eso sono muy armado",
            "$spokenTarget, explica mejor porque no cierra",
            "$spokenTarget, cada respuesta deja mas dudas"
        )
        BotSpeechIntent.CALM_DOWN -> listOf(
            "esperen, dejen hablar a $spokenTarget",
            "primero que $spokenTarget explique",
            "no votemos solo por seguir al resto",
            "no se apuren, falta escuchar a $spokenTarget",
            "ordenemos esto antes de votar"
        )
        BotSpeechIntent.ADMIT_DOUBT -> listOf(
            "igual puedo estar equivocado",
            "puede ser, no la tengo tan clara",
            "capaz me apure con $spokenTarget",
            "no se, lo quiero pensar un poco",
            "me hace ruido pero no estoy seguro",
            "lo tengo en duda, no como algo seguro"
        )
    }
}

internal fun defensiveLine(session: GameSession, bot: GamePlayer, mood: BotMood): String {
    return when {
        mood == BotMood.ANNOYED -> "me marcas a mi y ni explicas por que"
        personalityFor(session, bot) == BotPersonality.JODON -> "ahora yo? dame una razon concreta"
        personalityFor(session, bot) == BotPersonality.IMPULSIVO -> "para para yo no dije eso, no inventes"
        else -> "me marcas a mi, pero decime que hice concretamente"
    }
}

internal fun botToBotLine(session: GameSession, bot: GamePlayer): String? {
    val recent = recentPublicMessages(session)
    val lastBotMessage = recent.lastOrNull()?.takeIf { message ->
        message.speaker != bot.name &&
            session.players.any { !it.isHuman && it.name == message.speaker }
    } ?: return null
    val speaker = lastBotMessage.speaker
    val botWasAddressed = mentionsName(lastBotMessage.message, bot.name)
    val directStatement = LocalBotAi.publicStatementFrom(session, lastBotMessage.message)
    val directlyAccusesBot = directStatement?.target == bot.name &&
        directStatement.type in setOf(StatementType.ACCUSE, StatementType.VOTE)
    if (botWasAddressed && directlyAccusesBot) {
        return defensiveLine(session, bot, moodFor(session, bot, lastBotMessage.message))
    }
    if (botWasAddressed && lastBotMessage.message.contains("?")) {
        BotPerception.humanQuestionKind(lastBotMessage.message)?.let { kind ->
            return directHumanQuestionReply(session, bot, lastBotMessage.message, kind)
        }
    }
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
                "$target igual habla vos, no te escondas atrás de $speaker"
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
