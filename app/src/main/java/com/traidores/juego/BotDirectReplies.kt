package com.traidores.juego

/** Replies that belong exclusively to the bot named by the human. */
internal fun directRoleQuestionReply(session: GameSession, bot: GamePlayer): String {
    latestClaimBySpeaker(session, bot.name)?.let { previousClaim ->
        val label = roleLabel(previousClaim.roleKey)
        return chooseFreshLine(
            listOf(
                "ya te lo dije: soy $label. no voy a cambiar la historia ahora",
                "sigo diciendo lo mismo, soy $label. preguntame algo concreto de la ronda",
                "soy $label, como dije antes. si no me crees decime que no te cierra"
            ),
            session,
            bot,
            "direct-role-repeat:${previousClaim.roleKey}:${socialChatSize(session)}"
        )
    }

    if (isTraitor(bot)) {
        val fakeRole = traitorCoverRoleForDirectQuestion(session, bot)
        val label = roleLabel(fakeRole)
        return chooseFreshLine(
            listOf(
                "te respondo directo: soy $label. no lo dije antes porque no queria regalar la carta",
                "soy $label. ahora preguntame por lo que hice, no me votes solo por el titulo",
                "ok, lo digo: soy $label. y voy a sostener eso con lo que pase en la ronda"
            ),
            session,
            bot,
            "direct-role-traitor:$fakeRole:${socialChatSize(session)}"
        )
    }

    val roleKey = bot.role?.key ?: RoleCatalog.ALDEANO
    if (roleKey == RoleCatalog.BUFON) {
        return chooseFreshLine(
            listOf(
                "mi carta no te la voy a regalar; si te hago tanto ruido, votame",
                "te contesto: prefiero que me juzgues por como estoy jugando. si dudas, mandame a plaza",
                "no voy a quemar mi rol. marcame si queres y vemos quien se anima"
            ),
            session,
            bot,
            "direct-role-jester:${socialChatSize(session)}"
        )
    }

    val pressure = session.tableMemory.emotionalPressure[bot.name] ?: 0
    val personality = personalityFor(session, bot)
    val revealRole = roleKey == RoleCatalog.ALDEANO ||
        session.botDifficulty == BotDifficulty.NORMAL ||
        pressure >= 2 ||
        personality in setOf(BotPersonality.PICANTE, BotPersonality.IMPULSIVO)
    if (revealRole) {
        val label = roleLabel(roleKey)
        val action = latestOwnAction(session, bot)
        val actionTarget = action?.target
            ?.let { target -> GameEngine.playerByName(session, target)?.let { safeName(it, session) } ?: target }
        val actionOptions = when {
            roleKey == RoleCatalog.MEDICO && action?.type == GameActionType.PROTECT && actionTarget != null -> listOf(
                "soy $label y anoche cuide a $actionTarget. lo digo para que ordenemos la ronda",
                "te respondo directo: soy $label. mi accion fue proteger a $actionTarget",
                "soy $label; anoche fui con $actionTarget y puedo explicar por que"
            )
            roleKey == RoleCatalog.POLICIA && action?.type == GameActionType.INVESTIGATE && actionTarget != null -> listOf(
                "soy $label y anoche investigue a $actionTarget. el resultado me lo guardo por ahora",
                "te respondo directo: soy $label. mi hilo de anoche pasa por $actionTarget",
                "soy $label; revise a $actionTarget y primero quiero escuchar su version"
            )
            else -> emptyList()
        }
        val options = actionOptions.ifEmpty {
            listOf(
                "te respondo a vos: soy $label. ahora decime por que me lo preguntas",
                "soy $label. no tengo problema en decirlo, pero quiero saber que lectura haces con eso",
                "soy $label. queda dicho y no lo voy a cambiar despues"
            )
        }
        return chooseFreshLine(
            options,
            session,
            bot,
            "direct-role-town:$roleKey:${socialChatSize(session)}"
        )
    }

    return chooseFreshLine(
        listOf(
            "te respondo directo: juego para el pueblo, pero todavia no voy a quemar mi rol exacto",
            "no te ignoro; mi rol me conviene guardarlo un poco mas. preguntame por mi lectura",
            "por ahora no voy a decir la carta exacta. si me apuras otra vez, la pongo sobre la mesa"
        ),
        session,
        bot,
        "direct-role-evade:$roleKey:$pressure:${socialChatSize(session)}"
    )
}

private fun traitorCoverRoleForDirectQuestion(session: GameSession, bot: GamePlayer): String {
    session.traitorPlan
        ?.cover
        ?.takeIf { cover -> cover.actor == bot.name }
        ?.fakeRoleKey
        ?.let { return it }

    if (session.botDifficulty == BotDifficulty.NORMAL) return RoleCatalog.ALDEANO
    val availableSpecialRoles = listOf(RoleCatalog.MEDICO, RoleCatalog.POLICIA)
        .filter { roleKey -> publicClaimants(session, roleKey).isEmpty() }
    if (availableSpecialRoles.isEmpty()) return RoleCatalog.ALDEANO
    val seed = stableNoise("${session.code}:${bot.name}:direct-role-cover")
    return availableSpecialRoles[seed % availableSpecialRoles.size]
}

internal fun directSocialReply(
    session: GameSession,
    bot: GamePlayer,
    signal: HumanSocialSignal
): String {
    val personality = personalityFor(session, bot)
    val options = when (signal) {
        HumanSocialSignal.PRAISE -> when (personality) {
            BotPersonality.PICANTE -> listOf(
                "bien, por fin alguien que mira la partida",
                "te banco, pero no te duermas que todavia falta"
            )
            BotPersonality.JODON -> listOf(
                "viste? alguna bien tenia que pegar jaja",
                "gracias, despues te paso la factura jaja"
            )
            BotPersonality.DESCONFIADO -> listOf(
                "gracias, aunque igual voy a mirar lo que haces",
                "se agradece, pero no bajo la guardia"
            )
            else -> listOf(
                "gracias, te lo tomo. sigamos ese hilo",
                "bien ahi, yo tambien te escucho"
            )
        }
        HumanSocialSignal.INSULT -> if (session.botDifficulty == BotDifficulty.HARD) {
            listOf(
                "eso no es un argumento. decime que hice y lo discutimos",
                "insultarme no cambia la ronda; marca una contradiccion concreta",
                "si me vas a cruzar, hacelo con una razon y te respondo"
            )
        } else {
            when (personality) {
                BotPersonality.JODON -> listOf(
                    "jaja bueno, cuando aparezca el argumento avisame",
                    "fuerte el insulto, flojita la prueba"
                )
                BotPersonality.PICANTE,
                BotPersonality.IMPULSIVO -> listOf(
                    "dale, menos insulto y mas explicar que te molesta",
                    "decimelo con una prueba, si no es puro ruido"
                )
                else -> listOf(
                    "todo bien, pero hablame de la partida",
                    "no hace falta eso; decime que no te cierra"
                )
            }
        }
    }
    return chooseFreshLine(options, session, bot, "direct-social:$signal:${socialChatSize(session)}")
}
