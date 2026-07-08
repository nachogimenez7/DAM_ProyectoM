package com.traidores.juego


internal fun roleClaimStatementReaction(
    session: GameSession,
    claim: RoleClaim?,
    statement: PublicStatement?,
    index: Int
): String? {
    if (claim?.roleKey != RoleCatalog.POLICIA || statement?.target == null) return null
    val target = statement.target
    val shownTarget = GameEngine.playerByName(session, target)
        ?.let { safeName(it, session) }
        ?: target
    return when (statement.type) {
        StatementType.TRUST -> when (index) {
            0 -> "ok detective, entonces $shownTarget te dio limpio. no lo votaria hoy"
            1 -> "bien, usemos eso para ordenar: $shownTarget queda mas abajo por ahora"
            2 -> "igual ojo, que $shownTarget sea limpio no resuelve quien empujo raro"
            else -> null
        }
        StatementType.ACCUSE -> when (index) {
            0 -> "ok detective, si $shownTarget te dio mal entonces que responda ya"
            1 -> "ese dato si cambia la ronda. $shownTarget explica o se complica"
            2 -> "no lo tomaria como sentencia, pero $shownTarget queda arriba"
            else -> null
        }
        StatementType.INVESTIGATED -> when (index) {
            0 -> "ok detective, miraste a $shownTarget. falta decir si te cerrÃ³ o no"
            1 -> "$shownTarget queda en el hilo entonces, no saltemos de tema"
            else -> null
        }
        else -> null
    }
}

internal fun roleClaimReaction(
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
    traitorCounterClaimLine(session, bot, claim, index)?.let { return it }
    roleAwareClaimQuestion(session, bot, claim, index)?.let { return it }
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

internal fun traitorCounterClaimLine(
    session: GameSession,
    bot: GamePlayer,
    claim: RoleClaim,
    index: Int
): String? {
    if (!isTraitor(bot) || hasClaimedRole(session, bot.name) || index > 1) return null
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:counter-claim:${claim.roleKey}:${socialChatSize(session)}")
    val shouldLie = if (session.botDifficulty == BotDifficulty.HARD) {
        seed % 3 != 0
    } else {
        index == 0 && seed % 5 == 0
    }
    if (!shouldLie) return null
    return when (claim.roleKey) {
        RoleCatalog.MEDICO,
        RoleCatalog.POLICIA -> "mmm raro, yo tambien tengo ${claim.label}. conta ${claimFollowUp(claim.roleKey)}"
        RoleCatalog.ALDEANO -> "aldeano dicen todos cuando los apuran, dame algo mas"
        else -> "puede ser, pero ese claim solo no alcanza"
    }
}

internal fun roleAwareClaimQuestion(
    session: GameSession,
    bot: GamePlayer,
    claim: RoleClaim,
    index: Int
): String? {
    if (index > 1 || isTraitor(bot)) return null
    if (session.botDifficulty == BotDifficulty.HARD) {
        return when (claim.roleKey) {
            RoleCatalog.MEDICO -> "si sos medico, deci la ronda exacta y a quien, el titulo solo no alcanza"
            RoleCatalog.POLICIA -> "si sos detective, dame el nombre que investigaste anoche, ahora"
            RoleCatalog.ALDEANO -> "aldeano no explica nada por si solo, con quien votaste y por que"
            else -> null
        }
    }
    val roleKey = bot.role?.key ?: return null
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:role-question:${claim.roleKey}:$index")
    return when (roleKey) {
        RoleCatalog.MEDICO -> when (claim.roleKey) {
            RoleCatalog.MEDICO -> "si sos medico, deci a quien cuidaste sin vender humo"
            RoleCatalog.POLICIA -> "ok detective, tira el hilo pero no regales todo"
            else -> if (seed % 2 == 0) "claim anotado, pero falta explicar que hiciste" else null
        }
        RoleCatalog.POLICIA -> when (claim.roleKey) {
            RoleCatalog.MEDICO -> "si sos medico, tu noche deberia ordenar algo"
            RoleCatalog.POLICIA -> "si sos detective, no tires solo el titulo, deci a quien miraste"
            else -> "ok ${claim.label}, pero necesito una lectura concreta"
        }
        RoleCatalog.ALDEANO -> if (index == 0) {
            "yo no tengo rol para cruzarte, pero explica ${claimFollowUp(claim.roleKey)}"
        } else {
            null
        }
        else -> null
    }
}

internal fun eventReactionLine(
    session: GameSession,
    bot: GamePlayer,
    event: BotEvent,
    fallbackTarget: String,
    index: Int
): String {
    val target = event.target
    val personality = personalityFor(session, bot)
    if (
        event.type == BotEventType.MUERTE_NOCTURNA &&
        session.botDifficulty == BotDifficulty.HARD &&
        GameEngine.playerByName(session, target)?.isHuman == true
    ) {
        val options = listOf(
            "anoche fueron directo a por vos, $target. esto no va a aflojar",
            "$target la vio venir? alguien ya venia calculando esto",
            "sacaron a $target porque era una voz incomoda, no lo lean como azar"
        )
        return chooseFreshLine(options, session, bot, "event:hard-human-death:$target:$index:${socialChatSize(session)}")
    }
    val options = when (event.type) {
        BotEventType.MUERTE_NOCTURNA -> when (personality) {
            BotPersonality.TRANQUI -> listOf(
                "bueno, muriÃ³ $target. bajemos un cambio y ordenemos quien lo venÃ­a mirando",
                "lo de $target duele, pero ahora importan las versiones",
                "no regalemos otro voto por pÃ¡nico, revisemos quien gana con $target fuera"
            )
            BotPersonality.PICANTE -> listOf(
                "mataron a $target y alguno aca esta actuando demasiado tranquilo",
                "$target cae justo cuando $fallbackTarget venia flojo, mira vos",
                "esto no fue al azar, alguien queria sacar a $target del medio",
                "a $target lo callaron de noche, de dÃ­a nadie se anima"
            )
            BotPersonality.JODON -> listOf(
                "bueno $target se fue a mirar la partida desde platea, pero dejo ruido",
                "chau $target, igual esto huele peor que excusa de aldeano",
                "$target no habla mas, asi que ahora hablan los que quedaron raros",
                "$target ya esta criando malvas, los raros siguen aca"
            )
            BotPersonality.DESCONFIADO -> listOf(
                "si mataron a $target, revisen a quien le convenia ese silencio",
                "no compro que lo de $target sea casualidad",
                "$target afuera cambia el mapa, yo miraria a $fallbackTarget"
            )
            BotPersonality.IMPULSIVO -> listOf(
                "nah listo, con $target muerto hay que apurar a alguien ya",
                "esto me calienta, $fallbackTarget explica antes de que votemos cualquiera",
                "no durmamos, $target muriÃ³ y alguno se estÃ¡ escondiendo"
            )
            BotPersonality.ANALITICO -> listOf(
                "$target muriÃ³; miren quiÃ©n lo nombrÃ³ ayer y quiÃ©n evitÃ³ hablar de Ã©l",
                "si $target era una voz cÃ³moda para el pueblo, el ataque tiene sentido",
                "dato: $target fuera beneficia a quien estaba quedando bajo presion"
            )
        }
        BotEventType.EXPULSION -> when (personality) {
            BotPersonality.TRANQUI -> listOf(
                "se fue $target. ahora no repitamos voto por inercia",
                "$target queda fuera, pero la ronda siguiente hay que leer quien empujo",
                "bien o mal, lo de $target nos deja votos para revisar"
            )
            BotPersonality.PICANTE -> listOf(
                "si lo de $target saliÃ³ mal, miren quienes lo empujaron primeros",
                "$target afuera, pero yo no me olvido de quiÃ©n lo vendiÃ³ como seguro",
                "el voto a $target tuvo dueÃ±os, despuÃ©s no se hagan los perdidos"
            )
            BotPersonality.JODON -> listOf(
                "$target saliÃ³ por la puerta grande, ahora falta ver si nos mandamos cualquiera",
                "bueno $target fue el elegido del pueblo, premio raro",
                "chau $target, la mesa queda mas picante ahora"
            )
            BotPersonality.DESCONFIADO -> listOf(
                "la expulsion de $target dice mas de los votantes que de los discursos",
                "anoten quien se subio tarde a $target",
                "$target se fue, pero yo revisaria el tren de votos"
            )
            BotPersonality.IMPULSIVO -> listOf(
                "listo, $target afuera. ahora que nadie cambie la historia",
                "si $target era mala salida, voy directo contra los que empujaron",
                "no me gusta cÃ³mo se cerrÃ³ lo de $target, ojo"
            )
            BotPersonality.ANALITICO -> listOf(
                "$target expulsado: comparen el primer voto con los que se sumaron al final",
                "lo importante no es solo $target, es quien necesito cerrar ese voto",
                "la votaciÃ³n a $target deja informaciÃ³n, no la desperdicien"
            )
        }
        BotEventType.SILENCIO -> when (personality) {
            BotPersonality.TRANQUI -> listOf(
                "$target no puede hablar, no lo usemos como excusa facil",
                "si $target estÃ¡ silenciado, preguntemos a quienes sÃ­ pueden responder",
                "ojo con armar todo sobre $target si hoy no puede defenderse"
            )
            BotPersonality.PICANTE -> listOf(
                "callaron a $target, justo cuando habia que escuchar versiones",
                "$target silenciado me suena a alguien intentando tapar un hilo",
                "si silencian a $target, miren quien queda comodo hablando"
            )
            BotPersonality.JODON -> listOf(
                "$target modo estatua hoy, igual los demÃ¡s no zafan",
                "a $target le taparon la boca, pero al resto no",
                "$target no habla, perfecto, ahora no griten todos a la vez"
            )
            BotPersonality.DESCONFIADO -> listOf(
                "silenciar a $target no es casual, alguien le tenia miedo a esa voz",
                "$target callado deja una pregunta: a quien estaba molestando?",
                "si $target no puede contestar, busquemos quien se beneficia"
            )
            BotPersonality.IMPULSIVO -> listOf(
                "silenciaron a $target, entonces apuremos a otro ya",
                "no me gusta nada esto, $fallbackTarget habla ahora",
                "$target callado y todos mirando para otro lado, dale"
            )
            BotPersonality.ANALITICO -> listOf(
                "$target silenciado: revisen sus mensajes anteriores, no su silencio de hoy",
                "el silencio de $target es informaciÃ³n sobre quien querÃ­a cortar esa lÃ­nea",
                "si $target molestaba a alguien, ese alguien acaba de ganar tiempo"
            )
        }
    }
    return chooseFreshLine(options, session, bot, "event:${event.type}:$target:$index:${socialChatSize(session)}")
}

internal fun agendaLine(
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
        BotAgenda.ASK_ROLES -> if (session.botDifficulty == BotDifficulty.HARD) {
            listOf(
                "no pido rol por pedir: quiero quien miente, con pruebas",
                "$target, si vas a hablar de rol trae dato concreto",
                "menos titulo y mas secuencia: quien hizo que y cuando"
            )
        } else if (weakRead) {
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
                "$target tiene que contestar ahora, despuÃ©s no hay tiempo",
                "si $target no cierra esto, para mÃ­ va por ahÃ­"
            )
        }
        BotAgenda.DEFEND_WEAK -> if (weakRead) {
            listOf(
                "no maten a alguien solo por intuiciÃ³n, falta evidencia",
                "esto todavÃ­a estÃ¡ flojo, no compremos una acusaciÃ³n gratis",
                "si van a marcar a alguien, que sea con algo mÃ¡s que silencio"
            )
        } else {
            listOf(
                "puede ser $target, pero dejemos que responda primero",
                "no cierro a $target todavÃ­a, aunque $reason",
                "yo escucharÃ­a a $target antes de mandar el voto"
            )
        }
        BotAgenda.FOLLOW_THREAD -> if (session.botDifficulty == BotDifficulty.HARD) {
            listOf(
                "$threadTarget dejo un cabo suelto y lo vamos a cerrar ahora, no despues",
                "seguimos con $threadTarget hasta que la version cierre",
                "no cambio de tema: $threadTarget debe una respuesta concreta"
            )
        } else {
            listOf(
                "$threadTarget quedo como hilo abierto, cerremos eso",
                "vuelvo a $threadTarget porque ahi falta una respuesta",
                "no saltemos de tema, lo de $threadTarget sigue pendiente"
            )
        }
        BotAgenda.DEFLECT_PRESSURE -> listOf(
            "estan mirando para cualquier lado, $target viene mas raro",
            "si me quieren apurar ok, pero $target sigue pasando gratis",
            "no se enganchen conmigo, revisen a $target por esto: $reason"
        )
    }
    return chooseFreshLine(options, session, bot, "agenda:$agenda:$target:$index:${socialChatSize(session)}")
}

internal fun objectiveLine(
    session: GameSession,
    bot: GamePlayer,
    objective: RoundObjective,
    index: Int
): String? {
    if (index > 2) return null
    val target = objective.target ?: return when (objective.type) {
        RoundObjectiveType.CALM_TABLE -> if (index == 0) chooseFreshLine(
            listOf(
                "paren un toque, primero ordenemos quien dijo que",
                "si hablamos todos encima terminamos votando cualquier cosa",
                "quiero una pregunta clara y una respuesta, nada mas"
            ),
            session,
            bot,
            "objective:calm:$index:${socialChatSize(session)}"
        ) else null
        else -> null
    }
    val reason = informalReason(objective.reason, "objective:${objective.type}:$target")
    val options = when (objective.type) {
        RoundObjectiveType.ASK_PLAYER -> listOf(
            "$target vos que lectura tenes? no hace falta quemarte",
            "$target tirame una sospecha o alguien que te cierre",
            "$target necesito algo tuyo para ordenar la ronda"
        )
        RoundObjectiveType.DEFEND_PLAYER -> listOf(
            "yo no matarÃ­a a $target por ahora, falta algo mÃ¡s fuerte",
            "$target no me parece el voto mÃ¡s limpio todavÃ­a",
            "si van contra $target, que sea con algo mejor que ruido"
        )
        RoundObjectiveType.PUSH_VOTE -> listOf(
            "$target tiene que contestar esto, $reason",
            "para mi el hilo fuerte es $target, $reason",
            "si $target no aclara ahora, se complica"
        )
        RoundObjectiveType.FOLLOW_CONTRADICTION -> listOf(
            "volvamos a $target, esa contradiccion no puede quedar suelta",
            "$target ordena lo que dijiste, porque $reason",
            "no saltemos de tema: lo de $target es lo mas concreto"
        )
        RoundObjectiveType.DEFLECT_PRESSURE -> listOf(
            "estan mirando mal, $target viene mucho mas raro",
            "si me quieren apurar ok, pero $target esta pasando gratis",
            "no se distraigan conmigo, revisen a $target por esto: $reason"
        )
        RoundObjectiveType.CALM_TABLE -> emptyList()
    }
    return chooseFreshLine(options, session, bot, "objective:${objective.type}:$target:$index:${socialChatSize(session)}")
}

internal fun playerFocusLine(
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
            "$name no puede hablar, asÃ­ que no armemos todo sobre Ã©l",
            "ojo que $name estÃ¡ silenciado, busquemos otro hilo",
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
    return chooseFreshLine(options, session, bot, "player-focus:$index:$target:${socialChatSize(session)}")
}

internal fun statementReaction(statement: PublicStatement, index: Int): String? {
    val target = statement.target ?: "eso"
    return when (statement.type) {
        StatementType.PROTECTED -> when (index) {
            0 -> "ok, queda anotado lo de $target. si despuÃ©s no cierra te lo vamos a cobrar"
            1 -> "$target confirma algo de eso o nada que ver?"
            else -> null
        }
        StatementType.INVESTIGATED -> when (index) {
            0 -> "bien, pero decÃ­ quÃ© te dio esa investigaciÃ³n sin vender humo"
            1 -> "ojo con tirar info a medias, eso despuÃ©s confunde todo"
            else -> null
        }
        StatementType.REFUSED_ROLE -> when (index) {
            0 -> "ok no digas rol, pero aporta algo entonces"
            1 -> "si esquivÃ¡s todo despuÃ©s no te quejes si te miran raro"
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

internal fun openingIntent(session: GameSession, bot: GamePlayer, index: Int): BotSpeechIntent {
    val personality = personalityFor(session, bot)
    return when (personality) {
        BotPersonality.TRANQUI -> if (index == 0) BotSpeechIntent.CALM_DOWN else BotSpeechIntent.ASK
        BotPersonality.PICANTE -> BotSpeechIntent.ACCUSE
        BotPersonality.JODON -> BotSpeechIntent.TEASE
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
    if (session.botDifficulty != BotDifficulty.HARD) return intent
    return if (intent == BotSpeechIntent.TEASE) BotSpeechIntent.ACCUSE else intent
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
        "vamos por partes: quien tiene una contradiccion real, no una sospecha del aire",
        "hoy no hay lugar para joda, necesito una lectura seria de cada uno",
        "menos vueltas, mas estrategia: $target, quiero tu version exacta de anoche"
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
            base == BotSpeechIntent.ACCUSE && !hasStrongRead -> BotSpeechIntent.ASK
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
            "bajen un cambio, primero escuchemos a $target y despuÃ©s vemos",
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
                "por ahora no cerrarÃ­a voto, falta una punta mÃ¡s",
                "no veo una acusaciÃ³n limpia todavÃ­a",
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
            "ah ok, me lo decÃ­as a mi. entonces $priorTarget queda mÃ¡s limpio por ahora",
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
            "bien, queda ese rol anotado. no lo cambiemos despuÃ©s eh",
            "listo, claim de ${claim.label}. ahora explica la jugada sin regalar de mÃ¡s"
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
                "todavÃ­a no votarÃ­a apurado, falta escuchar mÃ¡s",
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
                "todavÃ­a nadie me cierra como culpable fuerte",
                "por ahora no hay sospecha limpia, hay que hablar mÃ¡s",
                "no tengo nombre firme, ojo con votar por costumbre"
            )
        }
    }
    return chooseFreshLine(options, session, bot, "human-question:$kind:$index:${socialChatSize(session)}")
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
    val options = if (session.botDifficulty == BotDifficulty.HARD) {
        listOf(
            "no tenemos tiempo para la joda, aporta algo de la ronda",
            "guardemos los chistes para despues, ahora hay que pensar",
            "che, concentrate, esto se juega en serio"
        )
    } else {
        listOf(
            "hola, pero tiren algo util asi no votamos al aire",
            "buenas, arranquemos tranqui y con datos",
            "toy, pero hablemos de la ronda que sino es humo",
            "dale, igual ordenemos un poco quien hizo que",
            "ok, por ahora no tengo nada fuerte"
        )
    }
    return chooseFreshLine(options, session, bot, "casual:$seed")
}

internal fun lowEvidenceOpeningLine(session: GameSession, bot: GamePlayer, index: Int): String {
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:low-evidence:$index:${socialChatSize(session)}")
    val options = listOf(
        "por ahora no tengo nada fuerte, escuchemos versiones",
        "arranquemos tranqui, acusar por acusar no sirve",
        "yo preguntaria roles solo si hace falta, no quememos todo al toque",
        "si alguien tiene dato real que lo tire sin regalar de mas",
        "no me copa votar por silencio nomas, falta charla"
    )
    return chooseFreshLine(options, session, bot, "low-evidence:$seed")
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
            "$spokenTarget pq hiciste eso?",
            "$spokenTarget explica bien lo tuyo, pq $reason?",
            "che $spokenTarget y vos q decis de todo esto?",
            "$spokenTarget posta no te parece raro q $reason?",
            "$spokenTarget tirame una razon concreta",
            "a ver $spokenTarget, conta bien que onda",
            "$spokenTarget no te estoy acusando, pero explica eso",
            "che posta $spokenTarget, eso como lo justificas?"
        )
        BotSpeechIntent.FOLLOW_UP -> listOf(
            "$spokenTarget si pero no respondiste lo q te preguntaron",
            "no no, para $spokenTarget, responde eso primero",
            "$spokenTarget estas esquivando la pregunta hace rato",
            "dale $spokenTarget contesta bien, pq $reason?",
            "$spokenTarget no saltes a otra cosa, cerrÃ¡ lo anterior",
            "me falta la respuesta de $spokenTarget todavÃ­a",
            "$spokenTarget estÃ¡s pateando la pelota, respondÃ©",
            "eso de $spokenTarget quedo colgado"
        )
        BotSpeechIntent.ACCUSE -> listOf(
            "para mi $spokenTarget se esta regalando, $reason",
            "$spokenTarget no me cierra nada amigo",
            "dale $spokenTarget, $reason y queres q no sospeche?",
            "yo lo digo ahora, $spokenTarget esta re raro",
            "$spokenTarget viene flojisimo con eso",
            "no me gusta nada lo de $spokenTarget",
            "para mi hay que mirar fuerte a $spokenTarget",
            "$spokenTarget cada vez me cierra menos",
            "yo a $spokenTarget no le fÃ­o ni una moneda, $reason",
            "$spokenTarget jura mucho y explica poco"
        )
        BotSpeechIntent.DEFEND -> listOf(
            "nah tampoco para matarlo por eso",
            "yo a $spokenTarget no lo veo tan raro todavÃ­a",
            "banco un toque a $spokenTarget, dejenlo explicar",
            "capaz estamos flasheando cualquiera con $spokenTarget",
            "no compremos tan rapido contra $spokenTarget",
            "$spokenTarget todavÃ­a puede explicar, aflojen",
            "a mi $spokenTarget no me parece el peor ahora",
            "si vamos contra $spokenTarget que sea con algo mas",
            "no quememos a $spokenTarget en la plaza sin escucharlo"
        )
        BotSpeechIntent.TEASE -> listOf(
            "jajaja $spokenTarget esa explicacion fue malisima",
            "$spokenTarget te estas regalando solo jsjs",
            "kjjj dale $spokenTarget inventate una mejor",
            "no puede ser $spokenTarget, cada vez te hundis mas jajaj",
            "$spokenTarget esa no te la compra nadie",
            "amigo $spokenTarget, ayudate un poco",
            "$spokenTarget estas actuando para la tribuna",
            "na $spokenTarget, eso sono muy armado",
            "$spokenTarget esa mentira no sobrevive ni al primer gallo",
            "jajaja $spokenTarget vendehumo de feria"
        )
        BotSpeechIntent.CALM_DOWN -> listOf(
            "para un toque, dejen hablar a $spokenTarget",
            "bajen un cambio y q $spokenTarget explique",
            "igual no votemos por votar, escuchemos a $spokenTarget",
            "tranqui, primero veamos pq $reason",
            "no se apuren, falta escuchar a $spokenTarget",
            "paren un poco, todavÃ­a hay tiempo",
            "si lo van a marcar a $spokenTarget que sea con calma",
            "ordenemos esto, porque sino votamos cualquier cosa",
            "no armemos la horca antes del juicio, escuchemos a $spokenTarget"
        )
        BotSpeechIntent.ADMIT_DOUBT -> listOf(
            "igual nose, capaz estoy flasheando",
            "puede ser eh, no la tengo tan clara",
            "bueno capaz me fui al pasto con $spokenTarget",
            "mmm no se, lo quiero pensar un toque",
            "no estoy cerrado igual",
            "me hace ruido pero puedo estar viendo fantasmas",
            "capaz estoy viendo brujas donde no hay",
            "si me equivoco despuÃ©s me hago cargo",
            "lo tengo en duda, no como sentencia"
        )
    }
}

internal fun defensiveLine(session: GameSession, bot: GamePlayer, mood: BotMood): String {
    return when {
        mood == BotMood.ANNOYED -> "dale amigo me marcas a mi y ni explicas pq"
        personalityFor(session, bot) == BotPersonality.JODON -> "jajaja ahora yo? dale, tirame una razon aunque sea"
        personalityFor(session, bot) == BotPersonality.IMPULSIVO -> "para para yo no dije eso, no inventes"
        else -> "bueno me marcas a mi, pero decime q hice concretamente"
    }
}

internal fun botToBotLine(session: GameSession, bot: GamePlayer, index: Int): String? {
    if (index == 0) return null
    val recent = recentPublicMessages(session)
    val lastBotMessage = recent.asReversed().firstOrNull { message ->
        message.speaker != bot.name &&
            session.players.any { !it.isHuman && it.name == message.speaker }
    } ?: return null
    val speaker = lastBotMessage.speaker
    if (
        mentionsName(lastBotMessage.message, bot.name) &&
        hasAccusatoryTargetSignal(lastBotMessage.message)
    ) {
        return defensiveLine(session, bot, moodFor(session, bot, lastBotMessage.message))
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
                "$target igual habla vos, no te escondas atrÃ¡s de $speaker"
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
        session.botDifficulty == BotDifficulty.HARD -> seed % 4 == 0
        else -> seed % 7 == 0
    }
    if (!shouldSpeak) return null
    return chooseFreshLine(options, session, bot, "role-line:$roleKey:$index:$seed")
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
            "si necesitan claim despuÃ©s lo doy, ahora me importa $target",
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
            "si hace falta despuÃ©s explico lo de $protected, ahora escuchemos"
        )
        pressure -> listOf(
            "soy medico, no me sirve morir por una corazonada",
            "si me van a sacar, minimo sepan que tengo rol util",
            "me estan apurando y soy medico, aflojen un toque"
        )
        seed % 5 == 0 -> listOf(
            "si no muriÃ³ nadie, no asumamos cualquiera, ordenemos primero",
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
            "yo no me caso con ningÃºn bando todavÃ­a, quiero ver quiÃ©n se pisa",
            "me sirve escuchar mÃ¡s, no votar por costumbre",
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

internal fun contradictionLine(playerName: String, contradiction: ClaimContradiction): String {
    val firstRole = contradiction.first.roleKey
    val latestRole = contradiction.latest.roleKey
    return if (firstRole != null && latestRole != null) {
        "$playerName espera, el dÃ­a ${contradiction.first.round} dijiste ${roleLabel(firstRole)} y ahora ${roleLabel(latestRole)}? eso no cierra"
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

internal fun finishSpeech(
    raw: String,
    session: GameSession,
    bot: GamePlayer,
    context: String,
    allowRoleTerms: Boolean = false
): String {
    val personality = personalityFor(session, bot)
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:style:$context")
    var text = raw.lowercase()
        .replace("porque", if (seed % 3 == 0) "pq" else "porque")
        .replace("que ", if (seed % 5 == 0) "q " else "que ")
        .replace("tambien", if (seed % 4 == 0) "tmb" else "tambien")
        .replace("no se", if (seed % 2 == 0) "nose" else "no se")

    if (personality == BotPersonality.PICANTE && seed % 4 == 0 && !text.startsWith("dale")) {
        text = "dale, $text"
    }
    if (
        personality == BotPersonality.JODON &&
        seed % 3 == 0 &&
        !containsLaugh(text) &&
        session.botDifficulty != BotDifficulty.HARD
    ) {
        text = "${laughFor(seed)} $text"
    }
    if (personality == BotPersonality.IMPULSIVO && seed % 5 == 0) {
        text = text.replace("para ", "PARA ")
    }
    if (personality == BotPersonality.TRANQUI && seed % 4 == 0 && !text.startsWith("igual")) {
        text = "igual $text"
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
    val safe = sanitizeBotSpeech(text, session, allowedTerms)
    val guarded = if (isSelfAccusatoryLine(safe, session, bot)) {
        neutralSelfAccusationFallback(session, bot, context)
    } else {
        safe
    }
    return withOccasionalEmoji(guarded, session, bot, context)
}

internal fun withOccasionalEmoji(
    line: String,
    session: GameSession,
    bot: GamePlayer,
    context: String
): String {
    if (line.isBlank() || !canUseOccasionalEmoji(session.phase)) return line
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:emoji:$context:${socialChatSize(session)}")
    if (seed % 7 != 0) return line

    val normalized = normalizedForParsing(line)
    val emoji = when {
        listOf("raro", "ruido", "sospech", "no cierra", "contradic").any { normalized.contains(it) } ->
            if (seed % 2 == 0) "ðŸ¤”" else "ðŸ‘€"
        listOf("murio", "silenci", "miedo", "callado").any { normalized.contains(it) } -> "ðŸ˜°"
        listOf("voto", "votar", "afuera", "cerrar").any { normalized.contains(it) } -> "ðŸ‘€"
        seed % 3 == 0 -> "ðŸ¤”"
        else -> return line
    }
    return if (line.length + 1 + emoji.length <= 140) "$line $emoji" else line
}

internal fun canUseOccasionalEmoji(phase: GamePhase): Boolean {
    return phase == GamePhase.DIA_DEBATE ||
        phase == GamePhase.CONTRAPUNTO ||
        phase == GamePhase.VOTACION ||
        phase == GamePhase.DESEMPATE_VOTACION
}

internal fun List<Pair<String, String>>.dedupeBotMessages(): List<Pair<String, String>> {
    return distinctBy { normalizedForParsing(it.second).take(42) }
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
        RoleCatalog.ALCALDE -> "por que no te revelaste antes"
        RoleCatalog.PAYADOR -> "cuando pensas usar la jugada"
        RoleCatalog.ORACULO -> "a quien queres traer"
        else -> "que hiciste"
    }
}
