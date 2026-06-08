package com.traidores.juego

object GameEngine {

    private const val TEAM_TOWN = "Pueblo"
    private const val TEAM_TRAITORS = "Traidores"
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
        if (!isValidNightTarget(session, target, assassin, allowSelf = false)) {
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

        val mercenary = activePlayers(session).firstOrNull { it.role?.key == "mercenario" }
            ?: return advanceNight(session, GamePhase.NOCHE_POLICIA, "La noche continua.")

        val target = if (mercenary.isHuman) selectedTarget else LocalBotAi.chooseSilenceTarget(session, mercenary)
        if (!isValidNightTarget(session, target, mercenary, allowSelf = false)) {
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

        val police = activePlayers(session).firstOrNull { it.role?.key == "policia" }
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

        val medic = activePlayers(session).firstOrNull { it.role?.key == "medico" }
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
                if (player.name == victim) player.copy(alive = false, muted = true) else player
            }
            "Amanecer: murio $victim. $victim queda muteado."
        }

        var silenceApplied = false
        updatedPlayers = updatedPlayers.map { player ->
            if (player.name == session.nightSilenceTarget && player.alive) {
                silenceApplied = true
                player.copy(muted = true)
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

    fun resolveVoting(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.VOTACION)) return session

        val human = humanPlayer(session)
        if (isActive(human) && !isValidVoteTarget(session, selectedTarget, human)) return session

        val votes = mutableMapOf<String, String>()
        activePlayers(session).forEach { voter ->
            val voteTarget = if (voter.isHuman) selectedTarget else LocalBotAi.chooseVoteTarget(session, voter)
            if (isValidVoteTarget(session, voteTarget, voter)) {
                votes[voter.name] = voteTarget
            }
        }

        val eliminated = clearVoteWinner(votes.values.toList())
        val message = if (eliminated.isBlank()) {
            "Dios cerro la votacion. No hubo mayoria clara."
        } else {
            "Dios cerro la votacion. Se resolvera la expulsion."
        }

        val updated = session.copy(
            votes = votes,
            dayEliminationTarget = eliminated,
            actionHistory = recordVotes(session, votes)
        )
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
            if (player.name == target) player.copy(alive = false, muted = true) else player
        }
        val message = "Dia ${session.round}: $target fue expulsado y queda muteado."
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
        val policeHint = if (role.key == "policia" && session.investigatedPlayer.isNotBlank()) {
            " Ultima pista: ${session.investigatedPlayer} parece ${session.investigatedResult}."
        } else {
            ""
        }
        return if (isActive(human)) {
            "${role.name} - ${role.team}.$policeHint"
        } else {
            "${role.name} - ${role.team}. Estas muteado.$policeHint"
        }
    }

    fun humanPlayer(session: GameSession): GamePlayer {
        return session.players.firstOrNull { it.isHuman } ?: session.players.first()
    }

    fun playerByName(session: GameSession, name: String): GamePlayer? {
        return session.players.firstOrNull { it.name == name }
    }

    fun activePlayers(session: GameSession): List<GamePlayer> {
        return session.players.filter { isActive(it) }
    }

    fun isHumanRoleTurn(session: GameSession, roleKey: String): Boolean {
        return activePlayers(session).any { it.isHuman && canActAs(session, it, roleKey) }
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
            GamePhase.VOTACION -> !isActive(humanPlayer(session)) || canActOnTarget(session, selectedTarget)
            else -> true
        }
    }

    fun canActOnTarget(session: GameSession, targetName: String): Boolean {
        val human = humanPlayer(session)
        if (!isActive(human) || session.winner.isNotBlank()) return false
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> canActAs(session, human, "asesino") &&
                isValidNightTarget(session, targetName, human, allowSelf = false)
            GamePhase.NOCHE_MERCENARIO -> canActAs(session, human, "mercenario") &&
                isValidNightTarget(session, targetName, human, allowSelf = false)
            GamePhase.NOCHE_POLICIA -> canActAs(session, human, "policia") &&
                isValidNightTarget(session, targetName, human, allowSelf = false)
            GamePhase.NOCHE_MEDICO -> canActAs(session, human, "medico") &&
                isValidNightTarget(session, targetName, human, allowSelf = true)
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
            GamePhase.VOTACION -> resolveVoting(session, targetName)
            else -> session
        }
    }

    fun isActive(player: GamePlayer): Boolean {
        return player.alive && !player.muted
    }

    fun canHumanChat(session: GameSession): Boolean {
        val human = humanPlayer(session)
        if (!isActive(human) || session.winner.isNotBlank()) return false

        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.VOTACION -> true
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> canRoleChatAtNight(human.role)
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
            GamePhase.NOCHE_ASESINO -> isActive(human) && canActAs(session, human, "asesino")
            GamePhase.NOCHE_MERCENARIO -> isHumanRoleTurn(session, "mercenario")
            GamePhase.NOCHE_POLICIA -> isHumanRoleTurn(session, "policia")
            GamePhase.NOCHE_MEDICO -> isHumanRoleTurn(session, "medico")
            GamePhase.VOTACION -> isActive(human)
            else -> false
        }
    }

    fun shouldAutoAdvance(session: GameSession): Boolean {
        return session.winner.isBlank() &&
            !requiresHumanInput(session)
    }

    fun autoAdvanceDelayMs(session: GameSession): Long {
        return when (session.phase) {
            GamePhase.REPARTO -> 10000L
            GamePhase.DIA_DEBATE -> 14000L
            GamePhase.AMANECER,
            GamePhase.RESULTADO -> 8000L
            GamePhase.VOTACION -> 9000L
            else -> 5200L
        }
    }

    private fun canResolve(session: GameSession, expectedPhase: GamePhase): Boolean {
        return session.winner.isBlank() && session.phase == expectedPhase
    }

    private fun startNextRound(session: GameSession, previousMessage: String): GameSession {
        val prepared = clearTemporaryMutes(session)
        val message = "$previousMessage Noche ${prepared.round + 1}: todos guardan silencio."
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
            publicAnnouncement = message,
            privateHint = privateRoleHint(prepared),
            phaseIndex = prepared.phaseIndex + 1
        ).withPublicHistory(message)
    }

    private fun advanceNight(session: GameSession, nextPhase: GamePhase, publicMessage: String): GameSession {
        return session.transitionTo(nextPhase, publicMessage, privateRoleHint(session))
    }

    private fun nextPhaseAfterAssassin(session: GameSession): GamePhase {
        return if (activePlayers(session).any { it.role?.key == "mercenario" }) {
            GamePhase.NOCHE_MERCENARIO
        } else {
            GamePhase.NOCHE_POLICIA
        }
    }

    private fun activeKiller(session: GameSession): GamePlayer? {
        return activePlayers(session).firstOrNull { it.role?.key == "asesino" }
            ?: activePlayers(session).firstOrNull { isTraitor(it) }
    }

    private fun isValidNightTarget(
        session: GameSession,
        selectedTarget: String,
        actor: GamePlayer,
        allowSelf: Boolean
    ): Boolean {
        if (!isActive(actor)) return false
        val target = playerByName(session, selectedTarget)
        return target != null && isActive(target) && (allowSelf || target.name != actor.name)
    }

    private fun isValidVoteTarget(session: GameSession, selectedTarget: String, voter: GamePlayer): Boolean {
        if (!isActive(voter)) return false
        val target = playerByName(session, selectedTarget)
        return target != null && isActive(target) && target.name != voter.name
    }

    private fun clearVoteWinner(voteTargets: List<String>): String {
        val counts = voteTargets.groupingBy { it }.eachCount()
        val topCount = counts.values.maxOrNull() ?: return ""
        val leaders = counts.filter { it.value == topCount }.keys
        return leaders.singleOrNull().orEmpty()
    }

    private fun canActAs(session: GameSession, player: GamePlayer, roleKey: String): Boolean {
        if (!isActive(player)) return false
        return when (roleKey) {
            "asesino" -> player.role?.key == "asesino" ||
                (isTraitor(player) && activePlayers(session).none { it.role?.key == "asesino" })
            else -> player.role?.key == roleKey
        }
    }

    private fun canRoleChatAtNight(role: GameRole?): Boolean {
        return role?.let { isTraitorRole(it) } == true
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
        val role = player.role ?: return false
        return isTraitorRole(role)
    }

    private fun isTraitorRole(role: GameRole): Boolean {
        return role.team == TEAM_TRAITORS ||
            role.team == "Asesino" ||
            role.key == "asesino" ||
            role.key == "mercenario" ||
            role.key == "espia"
    }

    private fun clearTemporaryMutes(session: GameSession): GameSession {
        return session.copy(
            players = session.players.map { player ->
                if (player.alive && player.muted) player.copy(muted = false) else player
            }
        )
    }

    private fun mutedSummary(session: GameSession): String {
        return session.players.filter { it.muted }.joinToString(", ") { it.name }
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
        return copy(publicHistory = (publicHistory + message).takeLast(8), godHistory = (godHistory + message).takeLast(8))
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
        val alive = players.filter { it.alive }
        val traitors = alive.count { isTraitor(it) }
        val town = alive.size - traitors
        val winner = when {
            traitors == 0 -> TEAM_TOWN
            traitors >= town -> TEAM_TRAITORS
            else -> ""
        }
        return copy(winner = winner)
    }
}
