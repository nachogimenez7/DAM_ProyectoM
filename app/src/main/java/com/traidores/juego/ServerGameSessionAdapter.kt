package com.traidores.juego

/** Presentation model only. It never resolves a game, supplies hidden roles or writes a room. */
internal object ServerGameSessionAdapter {
    fun channel(value: String) = when (value) {
        "traidores" -> ChatChannel.TRAIDORES; "muertos" -> ChatChannel.ESPECTADORES; else -> ChatChannel.PUBLICO
    }
    fun channel(value: ChatChannel) = when (value) {
        ChatChannel.TRAIDORES -> "traidores"; ChatChannel.ESPECTADORES -> "muertos"; else -> "publico"
    }
    fun narration(identity: GameSession, event: ServerGameEvent, name: (String?) -> String): String {
        val base = identity.copy(round = event.round)
        val subject = name(event.players.firstOrNull())
        return when {
            event.code == "NIGHT_START" -> GameEngine.nightStartMessage(base)
            event.code == "DAWN_NO_VICTIMS" -> GameEngine.dawnNoDeathMessage(base)
            event.code == "NIGHT_DEATH" && subject.isNotBlank() -> GameEngine.dawnDeathMessage(base, subject)
            event.code == "DAY_EXPULSION" && subject.isNotBlank() -> GameEngine.expulsionMessage(base, subject)
            else -> event.text
        }
    }
    fun adapt(identity: GameSession, state: ServerGameSnapshot, messages: Map<String, List<ServerChatMessage>> = emptyMap()): GameSession {
        val p = state.publicState
        val map = RoleMap.fromSessionKey(identity.mapKey)
        fun name(uid: String?) = p.players.firstOrNull { it.uid == uid }?.name.orEmpty()
        // The usual table's chronicle narration (per map) for the events the server publishes, so
        // the feed shows the decorated "Amanecer: murió X. …" entry instead of a bare sentence that
        // duplicates the round card and gets hidden.
        val narrated = p.events.map { narration(identity, it, ::name) }
        val events = p.events.zip(narrated) { e, text -> GameChatMessage(GameplayFeedMessages.GOD_SPEAKER, text, isGod = true, round = e.round) }
        val chat = messages.flatMap { (channel, entries) -> entries.filter { channel != "traidores" || it.phaseIndex < 0 || it.phaseIndex == p.phaseIndex }.map { message ->
            GameChatMessage(name(message.uid).ifBlank { "Jugador" }, message.text, channel = channel(channel),
                round = message.round.takeIf { it > 0 } ?: p.round)
        } }
        val phase = when (p.phase) {
            ServerGamePhase.REPARTO -> GamePhase.REPARTO
            ServerGamePhase.NOCHE -> when (state.ownRoleKey) {
                "medico" -> GamePhase.NOCHE_MEDICO; "policia" -> GamePhase.NOCHE_POLICIA
                "mercenario" -> GamePhase.NOCHE_MERCENARIO; "oraculo" -> GamePhase.NOCHE_ORACULO
                else -> GamePhase.NOCHE_ASESINO
            }
            ServerGamePhase.AMANECER -> GamePhase.AMANECER
            ServerGamePhase.DIA_DEBATE, ServerGamePhase.DESERTOR_RECONSIDERACION -> GamePhase.DIA_DEBATE
            ServerGamePhase.CONTRAPUNTO -> GamePhase.CONTRAPUNTO
            ServerGamePhase.VOTACION -> GamePhase.VOTACION
            ServerGamePhase.DESEMPATE_VOTACION -> GamePhase.DESEMPATE_VOTACION
            ServerGamePhase.RECUENTO_VOTOS -> GamePhase.RECUENTO_VOTOS
            ServerGamePhase.ALCALDE_DESEMPATE -> GamePhase.ALCALDE_DESEMPATE
            else -> GamePhase.RESULTADO
        }
        return GameSession(code = identity.code, mapKey = identity.mapKey, mapName = identity.mapName,
            playerProfiles = identity.playerProfiles, timingConfig = identity.timingConfig,
            roleRevealConfig = identity.roleRevealConfig, roleComposition = identity.roleComposition,
            players = p.players.sortedBy { it.order }.map { ServerGameTablePresentation.player(state, it, map) },
            phase = phase, phaseIndex = p.phaseIndex, round = p.round,
            publicAnnouncement = p.events.lastOrNull { it.text == p.announcement }?.let { narration(identity, it, ::name) } ?: p.announcement,
            publicHistory = narrated, chatHistory = events + chat,
            winner = p.winner.orEmpty(), showIndividualVotes = false, onlineTestMode = false,
            onlineMatchId = p.matchId, onlinePlayerUids = p.players.sortedBy { it.order }.map { it.uid },
            onlinePhaseDeadlineEpochMs = p.deadlineMs ?: 0, onlinePhaseDeadlinePhaseIndex = p.phaseIndex,
            dayEliminationTarget = name(p.dayEliminationUid), votes = emptyMap(), assassinVotes = emptyMap(), voteRound = p.voteRound,
            tieVoteCandidates = p.tieCandidates.map(::name), alcaldeTieCandidates = p.tieCandidates.map(::name),
            alcaldeRevealed = p.mayorUid != null, alcaldeCorruption = p.mayorCorruption,
            contrapuntoPlayers = p.counterpointPlayers.map(::name), oracleInvitedPlayer = name(p.oracleGuestUid),
            desertorTeam = state.privateState.deserterTeam.orEmpty(), desertorChangedTeam = state.privateState.deserterUsed,
            oracleUsed = state.privateState.oracleUsed, payadorUsed = state.privateState.payadorUsed,
            privateHint = ServerGameTablePresentation.investigationHint(state).orEmpty(),
            revealRolesOnDeath = identity.revealRolesOnDeath,
            specialVictories = p.players.mapNotNull { player ->
                "${p.matchId}:${player.uid}:bufon".takeIf { it in p.specialWinnerKeys }
                    ?.let { GameSpecialVictory(it, player.name, "bufon", p.round) }
            })
    }
}
