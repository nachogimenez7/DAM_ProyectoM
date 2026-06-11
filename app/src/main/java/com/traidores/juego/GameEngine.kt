package com.traidores.juego

object GameEngine {

    private const val MAX_ACTION_HISTORY = 60

    fun startNight(session: GameSession): GameSession {
        if (session.winner.isNotBlank()) return session

        val prepared = clearTemporaryMutes(session)
        val message = "Noche ${prepared.round}: todos guardan silencio. Las acciones nocturnas se resuelven en secreto."
        return prepared.copy(
            phase = GamePhase.NOCHE_ASESINO,
            publicAnnouncement = message,
            privateHint = privateRoleHint(prepared),
            nightKillTarget = "",
            protectedPlayer = "",
            nightSilenceTarget = "",
            investigatedPlayer = "",
            investigatedResult = "",
            dayEliminationTarget = "",
            votes = emptyMap(),
            phaseIndex = prepared.phaseIndex + 1
        ).withPublicHistory(message)
    }

    fun resolveAssassin(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_ASESINO)) return session

        val assassin = activeKiller(session)
            ?: return advanceNight(session, nextPhaseAfterAssassin(session), "La noche continua.")

        val target = if (assassin.isHuman) selectedTarget else LocalBotAi.chooseAssassinTarget(session, assassin)
        if (!isValidKillTarget(session, target, assassin)) {
            return if (assassin.isHuman) {
                session
            } else {
                advanceNight(session, nextPhaseAfterAssassin(session), "La noche continua.")
            }
        }

        val privateHint = if (assassin.isHuman) {
            "Elegiste una victima. Dios no lo anunciara hasta el amanecer."
        } else {
            session.privateHint.ifBlank { privateRoleHint(session) }
        }

        val updated = session.copy(
            nightKillTarget = target,
            actionHistory = recordAction(session, GameActionType.KILL, assassin.name, target)
        )
        return updated.transitionTo(
            nextPhaseAfterAssassin(updated),
            "La noche continua.",
            privateHint
        )
    }

    fun resolveMercenary(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_MERCENARIO)) return session

        val mercenary = alivePlayers(session).firstOrNull { it.role?.key == "mercenario" }
            ?: return advanceNight(session, GamePhase.NOCHE_POLICIA, "La noche continua.")

        val target = if (mercenary.isHuman) selectedTarget else LocalBotAi.chooseSilenceTarget(session, mercenary)
        if (!isValidSilenceTarget(session, target, mercenary)) {
            return if (mercenary.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.NOCHE_POLICIA, "La noche continua.")
            }
        }

        val privateHint = if (mercenary.isHuman) {
            "Silencio registrado. Ese jugador no podra hablar ni votar durante el dia."
        } else {
            session.privateHint.ifBlank { privateRoleHint(session) }
        }

        val updated = session.copy(
            nightSilenceTarget = target,
            actionHistory = recordAction(session, GameActionType.SILENCE, mercenary.name, target)
        )
        return updated.transitionTo(GamePhase.NOCHE_POLICIA, "La noche continua.", privateHint)
    }

    fun resolvePolice(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_POLICIA)) return session

        val police = alivePlayers(session).firstOrNull { it.role?.key == "policia" }
            ?: return advanceNight(session, GamePhase.NOCHE_MEDICO, "La noche continua.")

        val target = if (police.isHuman) selectedTarget else LocalBotAi.chooseInvestigationTarget(session, police)
        if (!isValidNightTarget(session, target, police, allowSelf = false)) {
            return if (police.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.NOCHE_MEDICO, "La noche continua.")
            }
        }

        val result = playerByName(session, target)?.let { investigationResult(it) } ?: "inocente"
        val privateHint = if (police.isHuman) {
            "Respuesta privada de Dios: $target parece $result."
        } else {
            session.privateHint.ifBlank { privateRoleHint(session) }
        }

        val updated = session.copy(
            investigatedPlayer = target,
            investigatedResult = result,
            actionHistory = recordAction(session, GameActionType.INVESTIGATE, police.name, target)
        )
        return updated.transitionTo(GamePhase.NOCHE_MEDICO, "La noche continua.", privateHint)
    }

    fun resolveMedic(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_MEDICO)) return session

        val medic = alivePlayers(session).firstOrNull { it.role?.key == "medico" }
            ?: return advanceNight(session, GamePhase.AMANECER, "La noche llega a su fin.")

        val target = if (medic.isHuman) selectedTarget else LocalBotAi.chooseProtectionTarget(session, medic)
        if (!isValidNightTarget(session, target, medic, allowSelf = true)) {
            return if (medic.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.AMANECER, "La noche llega a su fin.")
            }
        }

        val privateHint = if (medic.isHuman) {
            "Proteccion registrada. Dios solo anunciara si hubo muerte."
        } else {
            session.privateHint.ifBlank { privateRoleHint(session) }
        }

        val updated = session.copy(
            protectedPlayer = target,
            actionHistory = recordAction(session, GameActionType.PROTECT, medic.name, target)
        )
        return updated.transitionTo(GamePhase.AMANECER, "La noche llega a su fin.", privateHint)
    }

    fun resolveDawn(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.AMANECER)) return session

        val victim = session.nightKillTarget
        var updatedPlayers = session.players
        val killMessage = if (victim.isBlank() || victim == session.protectedPlayer) {
            "Amanecer: no murio nadie."
        } else {
            updatedPlayers = updatedPlayers.map { player ->
                if (player.name == victim) player.copy(alive = false, muted = false) else player
            }
            "Amanecer: murio $victim."
        }

        var silenceApplied = false
        updatedPlayers = updatedPlayers.map { player ->
            if (player.name == session.nightSilenceTarget && player.alive) {
                silenceApplied = true
                player.copy(muted = true, lastSilencedRound = session.round)
            } else {
                player
            }
        }

        val silenceMessage = if (silenceApplied) {
            "${session.nightSilenceTarget} no puede hablar ni votar hoy."
        } else {
            ""
        }
        val publicMessage = listOf(killMessage, silenceMessage)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val dawn = session.copy(
            players = updatedPlayers,
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = publicMessage,
            privateHint = privateRoleHint(session.copy(players = updatedPlayers)),
            phaseIndex = session.phaseIndex + 1
        ).withPublicHistory(publicMessage)
            .withWinnerCheck()

        return if (dawn.winner.isBlank()) dawn.withBotDebate() else dawn
    }

    fun resolveDayDebate(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.DIA_DEBATE)) return session

        val botPayador = alivePlayers(session).firstOrNull {
            it.role?.key == "payador" && !it.isHuman
        }
        if (botPayador != null && !session.payadorUsed) {
            val candidates = alivePlayers(session)
                .filter { it.name != botPayador.name }
                .sortedBy { it.name }
                .take(2)
            if (candidates.size == 2) {
                val first = chooseContrapuntoPlayer(session, candidates[0].name)
                return chooseContrapuntoPlayer(first, candidates[1].name)
            }
        }

        val muted = mutedSummary(session)
        val message = if (muted.isBlank()) {
            "Dia ${session.round}: debatan. No hay jugadores muteados."
        } else {
            "Dia ${session.round}: debatan. Muteados: $muted."
        }
        return session.transitionTo(
            GamePhase.VOTACION,
            message,
            privateRoleHint(session)
        ).withPublicHistory(message)
            .withBotVotingIntent()
    }

    fun revealAlcalde(session: GameSession): GameSession {
        if (session.phase != GamePhase.DIA_DEBATE || session.alcaldeRevealed) return session
        val alcalde = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" } ?: return session
        if (!alcalde.isHuman) return session
        val message = "${alcalde.name} se revelo como Alcalde. Desde ahora su voto vale doble y decide los empates."
        return session.copy(
            alcaldeRevealed = true,
            publicAnnouncement = message,
            privateHint = "Alcalde revelado. Tu voto vale doble y decidis empates entre los mas votados."
        ).withPublicHistory(message)
    }

    fun chooseAlcaldeTie(session: GameSession, targetName: String): GameSession {
        if (!canResolve(session, GamePhase.ALCALDE_DESEMPATE)) return session
        val alcalde = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" } ?: return session
        if (!session.alcaldeRevealed || targetName !in session.alcaldeTieCandidates) return session
        if (!alcalde.isHuman) return session
        val message = "El Alcalde decidio el empate. Se resolvera la expulsion de $targetName."
        return session.copy(
            dayEliminationTarget = targetName,
            alcaldeTieCandidates = emptyList()
        ).transitionTo(GamePhase.RESULTADO, message, privateRoleHint(session))
            .withPublicHistory(message)
    }

    fun chooseDesertorTeam(session: GameSession, team: String): GameSession {
        if (team != GameRules.TOWN_WINNER && team != GameRules.TRAITOR_WINNER) return session
        val desertor = session.players.firstOrNull { it.role?.key == "desertor" } ?: return session
        if (!desertor.isHuman || !desertor.alive) return session

        val isInitialChoice = session.desertorTeam.isBlank()
        if (!isInitialChoice && !canDesertorReconsider(session)) return session
        return session.copy(
            desertorTeam = team,
            desertorChangedTeam = !isInitialChoice || session.desertorChangedTeam,
            privateHint = "Desertor - Neutral. Tu bando actual es $team."
        )
    }

    fun needsInitialDesertorChoice(session: GameSession): Boolean {
        val human = humanPlayer(session)
        return human.role?.key == "desertor" && human.alive && session.desertorTeam.isBlank()
    }

    fun canDesertorReconsider(session: GameSession): Boolean {
        val human = humanPlayer(session)
        return human.role?.key == "desertor" &&
            human.alive &&
            session.desertorTeam.isNotBlank() &&
            !session.desertorChangedTeam &&
            session.players.count { it.alive } <= GameRules.desertorSwitchThreshold(session.initialPlayerCount)
    }

    fun chooseContrapuntoPlayer(session: GameSession, targetName: String): GameSession {
        if (session.phase != GamePhase.DIA_DEBATE || session.payadorUsed) return session
        val payador = alivePlayers(session).firstOrNull { it.role?.key == "payador" } ?: return session
        val target = playerByName(session, targetName) ?: return session
        if (!isAlive(target) || target.name == payador.name || target.name in session.contrapuntoPlayers) {
            return session
        }

        val selected = session.contrapuntoPlayers + target.name
        if (selected.size < 2) {
            return session.copy(
                contrapuntoPlayers = selected,
                privateHint = "Elegiste a ${target.name}. Falta un participante para el Contrapunto."
            )
        }

        val message = "El Payador inicia un Contrapunto entre ${selected[0]} y ${selected[1]}. Solo ellos y el Payador pueden hablar."
        return session.copy(
            phase = GamePhase.CONTRAPUNTO,
            payadorUsed = true,
            contrapuntoPlayers = selected,
            publicAnnouncement = message,
            privateHint = if (payador.isHuman) {
                "Escucha el Contrapunto y senala al participante que te parezca mas sospechoso."
            } else {
                privateRoleHint(session)
            },
            phaseIndex = session.phaseIndex + 1
        ).withPublicHistory(message)
    }

    fun resolveContrapunto(session: GameSession, suspiciousPlayer: String): GameSession {
        if (!canResolve(session, GamePhase.CONTRAPUNTO)) return session
        val payador = alivePlayers(session).firstOrNull { it.role?.key == "payador" }
            ?: return session.transitionTo(GamePhase.VOTACION, "El Contrapunto termino.", privateRoleHint(session))
        val selected = if (payador.isHuman) {
            suspiciousPlayer.takeIf { it in session.contrapuntoPlayers }.orEmpty()
        } else {
            session.contrapuntoPlayers.firstOrNull().orEmpty()
        }
        if (selected.isBlank()) return session

        val message = "El Contrapunto termino. El Payador senalo a $selected como mas sospechoso."
        return session.copy(
            contrapuntoSuspicion = selected
        ).transitionTo(GamePhase.VOTACION, message, privateRoleHint(session))
            .withPublicHistory(message)
            .withBotVotingIntent()
    }

    fun resolveVoting(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.VOTACION)) return session

        val human = humanPlayer(session)
        if (canVote(human) && !isValidVoteTarget(session, selectedTarget, human)) return session

        val votes = mutableMapOf<String, String>()
        session.players.filter { canVote(it) }.forEach { voter ->
            val voteTarget = if (voter.isHuman) selectedTarget else LocalBotAi.chooseVoteTarget(session, voter)
            if (isValidVoteTarget(session, voteTarget, voter)) {
                votes[voter.name] = voteTarget
            }
        }

        val votingSession = autoRevealBotAlcalde(session)
        val alcalde = alivePlayers(votingSession).firstOrNull { it.role?.key == "alcalde" }
        val leaders = weightedVoteLeaders(votingSession, votes)
        val humanAlcaldeMustChoose = leaders.size == 2 &&
            votingSession.alcaldeRevealed &&
            alcalde?.isHuman == true
        val eliminated = when {
            leaders.size == 1 -> leaders.first()
            leaders.size == 2 && votingSession.alcaldeRevealed && alcalde != null ->
                if (alcalde.isHuman) "" else LocalBotAi.chooseVoteTarget(votingSession, alcalde)
                    .takeIf { it in leaders }
                    ?: leaders.first()
            else -> ""
        }
        val message = if (eliminated.isBlank()) {
            "Dios cerro la votacion. No hubo mayoria clara."
        } else {
            "Dios cerro la votacion. Se resolvera la expulsion."
        }

        val updated = votingSession.copy(
            votes = votes,
            dayEliminationTarget = eliminated,
            alcaldeTieCandidates = if (humanAlcaldeMustChoose) leaders else emptyList(),
            actionHistory = recordVotes(session, votes)
        )
        if (humanAlcaldeMustChoose) {
            return updated.transitionTo(
                GamePhase.ALCALDE_DESEMPATE,
                "La votacion termino empatada. El Alcalde debe decidir quien es expulsado.",
                "Elegi entre ${leaders.joinToString(" o ")}."
            )
        }
        return updated.transitionTo(GamePhase.RESULTADO, message, privateRoleHint(updated))
    }

    fun resolveResult(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.RESULTADO)) return session

        val target = session.dayEliminationTarget
        if (target.isBlank()) {
            val message = "Dia ${session.round}: nadie fue expulsado."
            val checked = session.copy(
                publicAnnouncement = message,
                privateHint = privateRoleHint(session)
            ).withPublicHistory(message)
                .withWinnerCheck()
            return if (checked.winner.isBlank()) startNextRound(checked, message) else checked
        }

        val targetPlayer = playerByName(session, target)
        if (targetPlayer == null || !targetPlayer.alive) {
            return startNextRound(session, "Dia ${session.round}: nadie fue expulsado.")
        }

        val updatedPlayers = session.players.map { player ->
            if (player.name == target) player.copy(alive = false, muted = false) else player
        }
        val message = "Dia ${session.round}: $target fue expulsado."
        val resolved = session.copy(
            players = updatedPlayers,
            publicAnnouncement = message,
            privateHint = privateRoleHint(session.copy(players = updatedPlayers))
        ).withPublicHistory(message)
            .withWinnerCheck()

        return if (resolved.winner.isBlank()) startNextRound(resolved, message) else resolved
    }

    fun privateRoleHint(session: GameSession): String {
        val human = humanPlayer(session)
        val role = human.role ?: return "Tu rol todavia no esta asignado."
        val espiaKillerHint = if (
            role.key == "espia" &&
            session.phase == GamePhase.NOCHE_ASESINO &&
            alivePlayers(session).none { it.role?.key == "asesino" }
        ) {
            " El Asesino cayo: ahora sos el ejecutor de los Traidores."
        } else {
            ""
        }
        val alcaldeHint = if (role.key == "alcalde" && session.alcaldeRevealed) {
            " Estas revelado: tu voto vale doble y decidis empates."
        } else {
            ""
        }
        val desertorHint = if (role.key == "desertor") {
            if (session.desertorTeam.isBlank()) " Elegi tu bando." else " Tu bando actual es ${session.desertorTeam}."
        } else {
            ""
        }
        val policeHint = if (role.key == "policia" && session.investigatedPlayer.isNotBlank()) {
            " Ultima pista: ${session.investigatedPlayer} parece ${session.investigatedResult}."
        } else {
            ""
        }
        val statusHint = when {
            !human.alive -> " Estas eliminado."
            human.muted -> " Estas muteado durante el dia."
            else -> ""
        }
        return "${role.name} - ${role.team}.$statusHint$espiaKillerHint$alcaldeHint$desertorHint$policeHint"
    }

    fun humanPlayer(session: GameSession): GamePlayer {
        return session.players.firstOrNull { it.isHuman } ?: session.players.first()
    }

    fun playerByName(session: GameSession, name: String): GamePlayer? {
        return session.players.firstOrNull { it.name == name }
    }

    fun alivePlayers(session: GameSession): List<GamePlayer> {
        return session.players.filter { isAlive(it) }
    }

    fun isHumanRoleTurn(session: GameSession, roleKey: String): Boolean {
        return alivePlayers(session).any { it.isHuman && canActAs(session, it, roleKey) }
    }

    fun isValidHumanActionTarget(session: GameSession, selectedTarget: String): Boolean {
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> !isHumanRoleTurn(session, "asesino") ||
                canActOnTarget(session, selectedTarget)
            GamePhase.NOCHE_MERCENARIO -> !isHumanRoleTurn(session, "mercenario") ||
                canActOnTarget(session, selectedTarget)
            GamePhase.NOCHE_POLICIA -> !isHumanRoleTurn(session, "policia") ||
                canActOnTarget(session, selectedTarget)
            GamePhase.NOCHE_MEDICO -> !isHumanRoleTurn(session, "medico") ||
                canActOnTarget(session, selectedTarget)
            GamePhase.CONTRAPUNTO -> false
            GamePhase.ALCALDE_DESEMPATE -> false
            GamePhase.VOTACION -> !canVote(humanPlayer(session)) || canActOnTarget(session, selectedTarget)
            else -> true
        }
    }

    fun canActOnTarget(session: GameSession, targetName: String): Boolean {
        val human = humanPlayer(session)
        if (!isAlive(human) || session.winner.isNotBlank()) return false
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> canActAs(session, human, "asesino") &&
                isValidKillTarget(session, targetName, human)
            GamePhase.NOCHE_MERCENARIO -> canActAs(session, human, "mercenario") &&
                isValidSilenceTarget(session, targetName, human)
            GamePhase.NOCHE_POLICIA -> canActAs(session, human, "policia") &&
                isValidNightTarget(session, targetName, human, allowSelf = false)
            GamePhase.NOCHE_MEDICO -> canActAs(session, human, "medico") &&
                isValidNightTarget(session, targetName, human, allowSelf = true)
            GamePhase.DIA_DEBATE -> human.role?.key == "payador" &&
                !session.payadorUsed &&
                isValidContrapuntoTarget(session, targetName, human)
            GamePhase.CONTRAPUNTO -> human.role?.key == "payador" &&
                targetName in session.contrapuntoPlayers
            GamePhase.ALCALDE_DESEMPATE -> human.role?.key == "alcalde" &&
                session.alcaldeRevealed &&
                targetName in session.alcaldeTieCandidates
            GamePhase.VOTACION -> isValidVoteTarget(session, targetName, human)
            else -> false
        }
    }

    fun targetActionLabel(session: GameSession, targetName: String): String {
        if (!canActOnTarget(session, targetName)) return ""
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> "MATAR"
            GamePhase.NOCHE_MERCENARIO -> "SILENCIAR"
            GamePhase.NOCHE_POLICIA -> "INVESTIGAR"
            GamePhase.NOCHE_MEDICO -> "SALVAR"
            GamePhase.DIA_DEBATE -> "CONTRAPUNTO"
            GamePhase.CONTRAPUNTO -> "SENALAR"
            GamePhase.ALCALDE_DESEMPATE -> "DECIDIR"
            GamePhase.VOTACION -> "VOTAR"
            else -> ""
        }
    }

    fun resolveHumanTargetAction(session: GameSession, targetName: String): GameSession {
        if (!canActOnTarget(session, targetName)) return session
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> resolveAssassin(session, targetName)
            GamePhase.NOCHE_MERCENARIO -> resolveMercenary(session, targetName)
            GamePhase.NOCHE_POLICIA -> resolvePolice(session, targetName)
            GamePhase.NOCHE_MEDICO -> resolveMedic(session, targetName)
            GamePhase.DIA_DEBATE -> chooseContrapuntoPlayer(session, targetName)
            GamePhase.CONTRAPUNTO -> resolveContrapunto(session, targetName)
            GamePhase.ALCALDE_DESEMPATE -> chooseAlcaldeTie(session, targetName)
            GamePhase.VOTACION -> resolveVoting(session, targetName)
            else -> session
        }
    }

    fun isAlive(player: GamePlayer): Boolean {
        return player.alive
    }

    fun canSpeak(player: GamePlayer): Boolean {
        return player.alive && !player.muted
    }

    fun canVote(player: GamePlayer): Boolean {
        return player.alive && !player.muted
    }

    fun canHumanChat(session: GameSession): Boolean {
        val human = humanPlayer(session)
        if (!canSpeak(human) || session.winner.isNotBlank()) return false

        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.VOTACION -> true
            GamePhase.CONTRAPUNTO ->
                human.role?.key == "payador" || human.name in session.contrapuntoPlayers
            GamePhase.ALCALDE_DESEMPATE -> false
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> false
            else -> false
        }
    }

    fun addHumanChatMessage(session: GameSession, rawMessage: String): GameSession {
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(140)
        if (message.isBlank() || !canHumanChat(session)) return session
        val withHumanMessage = session.withChatMessage(humanPlayer(session).name, message)
        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.VOTACION -> withHumanMessage.withBotMessages(
                LocalBotAi.reactionsToHumanMessage(withHumanMessage, message)
            )
            else -> withHumanMessage
        }
    }

    fun requiresHumanInput(session: GameSession): Boolean {
        val human = humanPlayer(session)
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> isAlive(human) && canActAs(session, human, "asesino")
            GamePhase.NOCHE_MERCENARIO -> isHumanRoleTurn(session, "mercenario")
            GamePhase.NOCHE_POLICIA -> isHumanRoleTurn(session, "policia")
            GamePhase.NOCHE_MEDICO -> isHumanRoleTurn(session, "medico")
            GamePhase.CONTRAPUNTO ->
                isAlive(human) && human.role?.key == "payador"
            GamePhase.ALCALDE_DESEMPATE ->
                isAlive(human) && human.role?.key == "alcalde"
            GamePhase.VOTACION -> canVote(human)
            else -> needsInitialDesertorChoice(session)
        }
    }

    fun shouldAutoAdvance(session: GameSession): Boolean {
        return session.winner.isBlank() &&
            !requiresHumanInput(session) &&
            !canDesertorReconsider(session)
    }

    fun autoAdvanceDelayMs(session: GameSession): Long {
        return when (session.phase) {
            GamePhase.REPARTO -> 10000L
            GamePhase.DIA_DEBATE -> 14000L
            GamePhase.AMANECER,
            GamePhase.RESULTADO -> 8000L
            GamePhase.VOTACION -> 9000L
            GamePhase.CONTRAPUNTO -> 14000L
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> 7000L
            GamePhase.ALCALDE_DESEMPATE -> 9000L
        }
    }

    private fun canResolve(session: GameSession, expectedPhase: GamePhase): Boolean {
        return session.winner.isBlank() && session.phase == expectedPhase
    }

    private fun startNextRound(session: GameSession, previousMessage: String): GameSession {
        val prepared = clearTemporaryMutes(session)
        val message = "$previousMessage Noche ${prepared.round + 1}: todos guardan silencio."
        // payadorUsed no se reinicia: el Contrapunto se usa una sola vez por partida.
        return prepared.copy(
            phase = GamePhase.NOCHE_ASESINO,
            round = prepared.round + 1,
            nightKillTarget = "",
            protectedPlayer = "",
            nightSilenceTarget = "",
            investigatedPlayer = "",
            investigatedResult = "",
            dayEliminationTarget = "",
            votes = emptyMap(),
            contrapuntoPlayers = emptyList(),
            contrapuntoSuspicion = "",
            alcaldeTieCandidates = emptyList(),
            publicAnnouncement = message,
            privateHint = privateRoleHint(prepared),
            phaseIndex = prepared.phaseIndex + 1
        ).withPublicHistory(message)
    }

    private fun advanceNight(session: GameSession, nextPhase: GamePhase, publicMessage: String): GameSession {
        return session.transitionTo(nextPhase, publicMessage, privateRoleHint(session))
    }

    private fun nextPhaseAfterAssassin(session: GameSession): GamePhase {
        return if (alivePlayers(session).any { it.role?.key == "mercenario" }) {
            GamePhase.NOCHE_MERCENARIO
        } else {
            GamePhase.NOCHE_POLICIA
        }
    }

    private fun activeKiller(session: GameSession): GamePlayer? {
        return alivePlayers(session).firstOrNull { it.role?.key == "asesino" }
            ?: alivePlayers(session).firstOrNull { it.role?.key == "espia" }
    }

    internal fun isValidKillTarget(
        session: GameSession,
        selectedTarget: String,
        actor: GamePlayer
    ): Boolean {
        if (!isAlive(actor)) return false
        val target = playerByName(session, selectedTarget)
        return target != null &&
            isAlive(target) &&
            target.name != actor.name &&
            target.role?.key !in GameRules.traitorRoleKeys
    }

    internal fun isValidSilenceTarget(
        session: GameSession,
        selectedTarget: String,
        actor: GamePlayer
    ): Boolean {
        if (!isAlive(actor)) return false
        val target = playerByName(session, selectedTarget)
        return target != null &&
            isAlive(target) &&
            !target.muted &&
            target.name != actor.name &&
            canBeSilenced(session, target)
    }

    internal fun canBeSilenced(session: GameSession, target: GamePlayer): Boolean {
        val lastRound = target.lastSilencedRound ?: return true
        return session.round - lastRound >= 2
    }

    private fun isValidNightTarget(
        session: GameSession,
        selectedTarget: String,
        actor: GamePlayer,
        allowSelf: Boolean
    ): Boolean {
        if (!isAlive(actor)) return false
        val target = playerByName(session, selectedTarget)
        return target != null && isAlive(target) && (allowSelf || target.name != actor.name)
    }

    private fun isValidVoteTarget(session: GameSession, selectedTarget: String, voter: GamePlayer): Boolean {
        if (!canVote(voter)) return false
        val target = playerByName(session, selectedTarget)
        return target != null && isAlive(target) && target.name != voter.name
    }

    private fun isValidContrapuntoTarget(
        session: GameSession,
        selectedTarget: String,
        payador: GamePlayer
    ): Boolean {
        val target = playerByName(session, selectedTarget)
        return isAlive(payador) &&
            target != null &&
            isAlive(target) &&
            target.name != payador.name &&
            target.name !in session.contrapuntoPlayers
    }

    private fun clearVoteWinner(voteTargets: List<String>): String {
        val leaders = topVoteCandidates(voteTargets)
        return leaders.singleOrNull().orEmpty()
    }

    private fun topVoteCandidates(voteTargets: List<String>): List<String> {
        val counts = voteTargets.groupingBy { it }.eachCount()
        val topCount = counts.values.maxOrNull() ?: return emptyList()
        return counts.filter { it.value == topCount }.keys.sorted()
    }

    internal fun weightedVoteLeaders(
        session: GameSession,
        votes: Map<String, String>
    ): List<String> {
        val weightedVotes = votes.values.toMutableList()
        val alcalde = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" }
        if (session.alcaldeRevealed && alcalde != null) {
            votes[alcalde.name]?.let { weightedVotes += it }
        }
        if (session.contrapuntoSuspicion.isNotBlank()) {
            weightedVotes += session.contrapuntoSuspicion
        }
        return topVoteCandidates(weightedVotes)
    }

    private fun canActAs(session: GameSession, player: GamePlayer, roleKey: String): Boolean {
        if (!isAlive(player)) return false
        return when (roleKey) {
            "asesino" -> player.role?.key == "asesino" ||
                (player.role?.key == "espia" && alivePlayers(session).none { it.role?.key == "asesino" })
            else -> player.role?.key == roleKey
        }
    }

    private fun investigationResult(target: GamePlayer): String {
        return if (target.role?.key == "espia") {
            "inocente"
        } else if (isTraitor(target)) {
            "sospechoso"
        } else {
            "inocente"
        }
    }

    private fun isTraitor(player: GamePlayer): Boolean {
        return GameRules.isTraitorRole(player.role)
    }

    private fun clearTemporaryMutes(session: GameSession): GameSession {
        return session.copy(
            players = session.players.map { player ->
                if (player.alive && player.muted) player.copy(muted = false) else player
            }
        )
    }

    private fun mutedSummary(session: GameSession): String {
        return session.players.filter { it.alive && it.muted }.joinToString(", ") { it.name }
    }

    private fun recordAction(
        session: GameSession,
        type: GameActionType,
        actor: String,
        target: String
    ): List<GameAction> {
        val action = GameAction(
            type = type,
            actor = actor,
            target = target,
            round = session.round,
            phase = session.phase,
            publiclyKnown = false
        )
        return (session.actionHistory + action).takeLast(MAX_ACTION_HISTORY)
    }

    private fun recordVotes(session: GameSession, votes: Map<String, String>): List<GameAction> {
        var history = session.actionHistory
        votes.forEach { (voter, target) ->
            history = (history + GameAction(
                type = GameActionType.VOTE,
                actor = voter,
                target = target,
                round = session.round,
                phase = session.phase,
                publiclyKnown = true
            )).takeLast(MAX_ACTION_HISTORY)
        }
        return history
    }

    private fun GameSession.transitionTo(
        nextPhase: GamePhase,
        publicMessage: String,
        privateMessage: String = privateRoleHint(this)
    ): GameSession {
        return copy(
            phase = nextPhase,
            publicAnnouncement = publicMessage,
            privateHint = privateMessage,
            phaseIndex = phaseIndex + if (phase == nextPhase) 0 else 1
        )
    }

    private fun GameSession.withPublicHistory(message: String): GameSession {
        return copy(
            publicHistory = (publicHistory + message).takeLast(8),
            godHistory = (godHistory + message).takeLast(32)
        )
    }

    private fun GameSession.withChatMessage(speaker: String, message: String, isGod: Boolean = false): GameSession {
        return copy(chatHistory = (chatHistory + GameChatMessage(speaker, message, isGod)).takeLast(40))
    }

    private fun GameSession.withBotDebate(): GameSession {
        return withBotMessages(LocalBotAi.openingDebateMessages(this))
    }

    private fun GameSession.withBotVotingIntent(): GameSession {
        return withBotMessages(LocalBotAi.votingIntentMessages(this))
    }

    private fun GameSession.withBotMessages(messages: List<Pair<String, String>>): GameSession {
        var updated = this
        messages.forEach { (speaker, message) ->
            updated = updated.withChatMessage(speaker, message)
        }
        return updated
    }

    private fun GameSession.withWinnerCheck(): GameSession {
        val prepared = autoResolveBotDesertorChoice(this)
        val winner = GameRules.winnerFor(prepared)
        if (winner == GameRules.TOWN_WINNER) {
            return prepared.copy(winner = winner)
        }
        if (canDesertorReconsider(prepared)) {
            return prepared.copy(winner = "")
        }
        return prepared.copy(winner = winner)
    }

    private fun autoRevealBotAlcalde(session: GameSession): GameSession {
        if (session.alcaldeRevealed) return session
        val alcalde = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" } ?: return session
        if (alcalde.isHuman || session.round < 2) return session
        val message = "${alcalde.name} se revelo como Alcalde. Su voto vale doble y decidira los empates."
        return session.copy(alcaldeRevealed = true).withPublicHistory(message)
    }

    private fun autoResolveBotDesertorChoice(session: GameSession): GameSession {
        val desertor = session.players.firstOrNull { it.role?.key == "desertor" } ?: return session
        if (
            desertor.isHuman ||
            !desertor.alive ||
            session.desertorChangedTeam ||
            session.desertorTeam.isBlank() ||
            session.players.count { it.alive } > GameRules.desertorSwitchThreshold(session.initialPlayerCount)
        ) {
            return session
        }

        val alive = session.players.filter { it.alive && it.role?.key != "desertor" }
        val traitors = alive.count { GameRules.isTraitorRole(it.role) }
        val town = alive.count { it.role?.team == GameRules.TOWN_WINNER }
        val selectedTeam = if (traitors >= town) GameRules.TRAITOR_WINNER else GameRules.TOWN_WINNER
        return session.copy(
            desertorTeam = selectedTeam,
            desertorChangedTeam = true
        )
    }
}
