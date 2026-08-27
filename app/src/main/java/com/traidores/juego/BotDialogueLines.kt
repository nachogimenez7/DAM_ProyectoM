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
            0 -> "ok detective, miraste a $shownTarget. falta decir si te cerró o no"
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
    dangerousRoleClaimReaction(session, claim, index)?.let { return it }
    roleContradiction(session, GameEngine.humanPlayer(session).name)?.let { contradiction ->
        return contradictionLine(GameEngine.humanPlayer(session).name, contradiction)
    }
    if (bot.name == claimResponder?.name) {
        return "para, yo soy ${claim.label}. si decis lo mismo conta ${claimFollowUp(claim.roleKey)}"
    }
    traitorCounterClaimLine(session, bot, claim, index)?.let { return it }
    playfulVillagerClaimLine(session, bot, claim, index)?.let { return it }
    roleAwareClaimQuestion(session, bot, claim, index)?.let { return it }
    val existingClaim = publicClaimants(session, claim.roleKey)
        .firstOrNull { it != GameEngine.humanPlayer(session).name }
    return when {
        existingClaim != null && index == 0 ->
            "ojo q $existingClaim ya habia dicho ${claim.label}, los dos no pueden tener ese rol"
        claimResponder != null && index == 1 ->
            "ah bueno, dos que dicen lo mismo. uno vende humo seguro"
        index == 0 ->
            if (claim.roleKey == RoleCatalog.ALDEANO) {
                "ok decis ${claim.label}; no tenes accion, tira a quien miras y por que"
            } else {
                "ok decis ${claim.label}, pero tira algo concreto sin quemar de mas"
            }
        index == 1 ->
            "bien, preguntemos antes de votar por votar"
        else -> null
    }
}
internal fun playfulVillagerClaimLine(
    session: GameSession,
    bot: GamePlayer,
    claim: RoleClaim,
    index: Int
): String? {
    if (
        session.botDifficulty != BotDifficulty.NORMAL ||
        claim.roleKey != RoleCatalog.ALDEANO
    ) {
        return null
    }
    val humanName = safeName(GameEngine.humanPlayer(session), session)
    return when (index) {
        0 -> when (personalityFor(session, bot)) {
            BotPersonality.JODON -> "aldeano, la tipicaaaa. bueno, de quien sospechas?"
            BotPersonality.PICANTE,
            BotPersonality.DESCONFIADO -> "mmm no te creo del todo, $humanName. a quien votarias?"
            else -> "puede ser, pero ser aldeano no dice mucho. a quien miras?"
        }
        1 -> "parenn, puede ser posta. que diga de quien sospecha y vemos"
        2 -> "$humanName, si sos aldeano juga con la charla: quien te hace ruido?"
        else -> null
    }
}

internal fun dangerousRoleClaimReaction(
    session: GameSession,
    claim: RoleClaim,
    index: Int
): String? {
    val humanName = safeName(GameEngine.humanPlayer(session), session)
    return when (claim.roleKey) {
        RoleCatalog.ASESINO,
        RoleCatalog.MERCENARIO,
        RoleCatalog.ESPIA -> when (index) {
            0 -> "no me tientes a votarte, $humanName"
            1 -> "la tiro demasiado facil, para mi esta jodiendo o quiere medir reacciones"
            2 -> "$humanName sos ${claim.label} posta o estas tirando cualquiera?"
            else -> null
        }
        RoleCatalog.BUFON -> when (index) {
            0 -> "si fueras el bufon no te regalarias asi... igual nadie te vote por las dudas"
            1 -> "$humanName explica que buscas con decir eso"
            else -> null
        }
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
        else -> "puede ser, pero decir el rol solo no alcanza"
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
            RoleCatalog.ALDEANO -> "aldeano no tiene accion nocturna; dame una sospecha y el motivo"
            else -> null
        }
    }
    val roleKey = bot.role?.key ?: return null
    val seed = stableNoise("${session.code}:${session.round}:${bot.name}:role-question:${claim.roleKey}:$index")
    return when (roleKey) {
        RoleCatalog.MEDICO -> when (claim.roleKey) {
            RoleCatalog.MEDICO -> "si sos medico, deci a quien cuidaste sin vender humo"
            RoleCatalog.POLICIA -> "ok detective, tira el hilo pero no regales todo"
            RoleCatalog.ALDEANO -> if (seed % 2 == 0) "anotado lo de aldeano; entonces tira lectura, no accion" else null
            else -> if (seed % 2 == 0) "anotado lo del rol, pero conta que hiciste" else null
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
    if (session.round <= 2) {
        val earlyOptions = when (event.type) {
            BotEventType.MUERTE_NOCTURNA -> if (session.round == 1) {
                listOf(
                    "murió $target. alguien sabe algo de anoche?",
                    "antes de acusar, alguien tiene una pista sobre lo de $target?",
                    "lo de $target es lo único seguro. alguien vio algo?"
                )
            } else {
                listOf(
                    "murió $target. comparemos esto con lo que se dijo ayer",
                    "alguien tiene una pista concreta sobre lo de $target?",
                    "antes de votar, revisemos quién había hablado de $target"
                )
            }
            BotEventType.EXPULSION -> listOf(
                "se fue $target. anotemos quiénes empujaron ese voto",
                "lo de $target ya está. ahora revisemos qué información dejó",
                "no repitamos el voto a $target por inercia"
            )
            BotEventType.SILENCIO -> listOf(
                "$target no puede hablar. preguntemos a quienes sí pueden responder",
                "como $target está silenciado, no lo acusemos sin que pueda contestar",
                "$target hoy no puede explicar nada. busquemos otra pista"
            )
        }
        return chooseFreshLine(
            earlyOptions,
            session,
            bot,
            "event:early:${event.type}:$target:$index:${socialChatSize(session)}"
        )
    }
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
                "bueno, murió $target. bajemos un cambio y ordenemos quien lo venía mirando",
                "lo de $target duele, pero ahora importan las versiones",
                "no regalemos otro voto por pánico, revisemos quien gana con $target fuera"
            )
            BotPersonality.PICANTE -> listOf(
                "mataron a $target y alguno aca esta actuando demasiado tranquilo",
                "$target cae justo cuando $fallbackTarget venia flojo, mira vos",
                "esto no fue al azar, alguien queria sacar a $target del medio",
                "a $target lo callaron de noche, de día nadie se anima"
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
                "no durmamos, $target murió y alguno se está escondiendo"
            )
            BotPersonality.ANALITICO -> listOf(
                "$target murió; miren quién lo nombró ayer y quién evitó hablar de él",
                "si $target era una voz cómoda para el pueblo, el ataque tiene sentido",
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
                "si lo de $target salió mal, miren quienes lo empujaron primeros",
                "$target afuera, pero yo no me olvido de quién lo vendió como seguro",
                "el voto a $target tuvo dueños, después no se hagan los perdidos"
            )
            BotPersonality.JODON -> listOf(
                "$target salió por la puerta grande, ahora falta ver si nos mandamos cualquiera",
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
                "no me gusta cómo se cerró lo de $target, ojo"
            )
            BotPersonality.ANALITICO -> listOf(
                "$target expulsado: comparen el primer voto con los que se sumaron al final",
                "lo importante no es solo $target, es quien necesito cerrar ese voto",
                "la votación a $target deja información, no la desperdicien"
            )
        }
        BotEventType.SILENCIO -> when (personality) {
            BotPersonality.TRANQUI -> listOf(
                "$target no puede hablar, no lo usemos como excusa facil",
                "si $target está silenciado, preguntemos a quienes sí pueden responder",
                "ojo con armar todo sobre $target si hoy no puede defenderse"
            )
            BotPersonality.PICANTE -> listOf(
                "callaron a $target, justo cuando habia que escuchar versiones",
                "$target silenciado me suena a alguien intentando tapar un hilo",
                "si silencian a $target, miren quien queda comodo hablando"
            )
            BotPersonality.JODON -> listOf(
                "$target modo estatua hoy, igual los demás no zafan",
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
                "el silencio de $target es información sobre quien quería cortar esa línea",
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
                "$target tiene que contestar ahora, después no hay tiempo",
                "si $target no cierra esto, para mí va por ahí"
            )
        }
        BotAgenda.DEFEND_WEAK -> if (weakRead) {
            listOf(
                "no maten a alguien solo por intuición, falta evidencia",
                "esto todavía está flojo, no compremos una acusación gratis",
                "si van a marcar a alguien, que sea con algo más que silencio"
            )
        } else {
            listOf(
                "puede ser $target, pero dejemos que responda primero",
                "no cierro a $target todavía, aunque $reason",
                "yo escucharía a $target antes de mandar el voto"
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
        BotAgenda.DEFLECT_PRESSURE -> if (weakRead) {
            listOf(
                "$target, no te acuso: quiero que ordenes tu version",
                "si me van a mirar, primero comparemos lo que dijo cada uno",
                "$target responde algo concreto y despues vemos"
            )
        } else {
            listOf(
                "estan mirando para cualquier lado, $target viene mas raro",
                "si me quieren apurar ok, pero $target sigue pasando gratis",
                "no se enganchen conmigo, revisen a $target por esto: $reason"
            )
        }
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
            "yo no mataría a $target por ahora, falta algo más fuerte",
            "$target no me parece el voto más limpio todavía",
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
            "$name no puede hablar, así que no armemos todo sobre él",
            "ojo que $name está silenciado, busquemos otro hilo",
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
    val reason = statement.reason
    return when (statement.type) {
        StatementType.PROTECTED -> when (index) {
            0 -> "ok, queda anotado lo de $target. si después no cierra te lo vamos a cobrar"
            1 -> "$target confirma algo de eso o nada que ver?"
            else -> null
        }
        StatementType.INVESTIGATED -> when (index) {
            0 -> "bien, pero decí qué te dio esa investigación sin vender humo"
            1 -> "ojo con tirar info a medias, eso después confunde todo"
            else -> null
        }
        StatementType.REFUSED_ROLE -> when (index) {
            0 -> "ok no digas rol, pero aporta algo entonces"
            1 -> "si esquivás todo después no te quejes si te miran raro"
            else -> null
        }
        StatementType.TRUST -> when (index) {
            0 -> if (reason != null) {
                "ok, bancas a $target porque $reason. queda anotado"
            } else {
                "por que confias en $target? dame algo mas que corazonada"
            }
            1 -> if (reason != null) {
                "ese motivo puede servir, pero $target igual tiene que sostenerlo"
            } else {
                "bancar a alguien sin explicar tambien hace ruido"
            }
            else -> null
        }
        StatementType.ACCUSE -> when (index) {
            0 -> if (reason != null) {
                "ok, marcas a $target porque $reason. que responda eso"
            } else {
                "puede ser, pero deci que viste de $target"
            }
            1 -> if (reason != null) {
                "ahi hay algo concreto para discutir con $target"
            } else {
                "acusarlo asi nomas es medio gratis, explica"
            }
            else -> null
        }
        StatementType.VOTE -> when (index) {
            0 -> if (reason != null) {
                "vas con $target porque $reason, entendido. que conteste antes del cierre"
            } else {
                "si vas con $target explica rapido pq"
            }
            1 -> if (reason != null) {
                "al menos hay motivo; ahora escuchemos a $target"
            } else {
                "no votemos en manada sin escuchar respuesta"
            }
            else -> null
        }
    }
}
