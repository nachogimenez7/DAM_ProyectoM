package com.traidores.juego

/** Converts authorized projections to shared artwork models. Never feeds GameEngine. */
internal object ServerGameTablePresentation {
    enum class ExpulsionMode { FULL, COMPRESSED, STATIC }
    data class Expulsion(val player: ServerGamePlayer, val mode: ExpulsionMode, val jester: Boolean,
                         val remainingMs: Long)

    fun expulsion(state: ServerGamePublic, fresh: List<ServerGameEvent>, nowMs: Long?): Expulsion? {
        if (state.phase != ServerGamePhase.RESULTADO || state.winner != null) return null
        val target = state.players.firstOrNull { it.uid == state.dayEliminationUid && !it.alive && it.deathCause == "VOTE" } ?: return null
        val remaining = if (nowMs == null) 0 else ((state.deadlineMs ?: nowMs) - nowMs).coerceAtLeast(0)
        val unseen = fresh.any { it.code == "DAY_EXPULSION" && it.round == state.round && target.uid in it.players }
        val jester = "${state.matchId}:${target.uid}:bufon" in state.specialWinnerKeys
        val mode = when {
            !unseen || remaining < 2500 -> ExpulsionMode.STATIC
            remaining >= 6200 && !jester -> ExpulsionMode.FULL
            else -> ExpulsionMode.COMPRESSED
        }
        return Expulsion(target, mode, jester, remaining)
    }

    /** Retains display only. Actions/chat always validate the original authoritative snapshot. */
    fun visibleDuringExpulsion(state: ServerGameSnapshot, heldUid: String?, holdDeath: Boolean,
                               holdJester: Boolean, previous: ServerGamePlayer?): ServerGameSnapshot {
        if (heldUid == null || !holdDeath && !holdJester) return state
        val p = state.publicState
        val target = p.players.firstOrNull { it.uid == heldUid } ?: return state
        val events = if (holdDeath) p.events.filterNot { it.code == "DAY_EXPULSION" && heldUid in it.players } else p.events
        return state.copy(publicState = p.copy(
            players = if (holdDeath) p.players.map { if (it.uid != heldUid) it else it.copy(alive = true,
                muted = previous?.muted ?: false, deathCause = "NONE",
                publicRoleKey = previous?.publicRoleKey ?: "alcalde".takeIf { p.mayorUid == heldUid }) } else p.players,
            events = events,
            announcement = if (holdDeath) events.lastOrNull()?.text ?: "El pueblo conoce el resultado de la votación." else p.announcement,
            specialWinners = if (holdJester) p.specialWinners.filterNot { it == target.name } else p.specialWinners,
            specialWinnerKeys = if (holdJester) p.specialWinnerKeys - "${p.matchId}:$heldUid:bufon" else p.specialWinnerKeys))
    }

    /** Same text as the usual table's primaryTargetActionLabel; its color comes from GameplayTableUi.actionToneFor. */
    fun targetActionLabel(action: String, targetName: String): String {
        val target = targetName.uppercase()
        return when (action) {
            "matar" -> "MATAR A $target"
            "silenciar" -> "SILENCIAR A $target"
            "investigar" -> "INVESTIGAR A $target"
            "salvar" -> "SALVAR A $target"
            "invitar_muerto" -> "INVOCAR A $target"
            "senalar_contrapunto" -> "SEÑALAR A $target"
            "contrapunto" -> "SEÑALAR A $target" // usual table maps CONTRAPUNTO to SEÑALAR
            "decidir_empate" -> "EXPULSAR A $target"
            "votar" -> "VOTAR A $target"
            else -> target
        }
    }

    /**
     * The usual table's confirmation for an accepted intention: a private window for
     * night powers, a short banner for day decisions. Votes use the direct-vote notice;
     * the Comisario's answer arrives with its private projection.
     */
    fun confirmationFeedback(session: GameSession, action: String, targetName: String?, team: String?,
                             counterpointPlayers: Int): GameplayFeedbackSpec? {
        if (action == "desertor_initial" || action == "desertor_rethink") return team
            ?.takeIf { it != "mantener" }?.let { GameplayTableUi.feedbackForDesertorChoice(it, action == "desertor_rethink") }
        if (action == "revelar_alcalde") return GameplayTableUi.feedbackForMayorReveal(
            session.copy(alcaldeRevealed = false), session.copy(alcaldeRevealed = true))
        val phase = when (action) {
            "matar" -> GamePhase.NOCHE_ASESINO; "silenciar" -> GamePhase.NOCHE_MERCENARIO
            "salvar" -> GamePhase.NOCHE_MEDICO; "invitar_muerto" -> GamePhase.NOCHE_ORACULO
            "contrapunto" -> GamePhase.DIA_DEBATE; "senalar_contrapunto" -> GamePhase.CONTRAPUNTO
            "decidir_empate" -> GamePhase.ALCALDE_DESEMPATE
            else -> return null
        }
        val target = targetName?.takeIf { it.isNotBlank() } ?: return null
        val before = session.copy(phase = phase)
        val after = before.copy(phaseIndex = before.phaseIndex + 1,
            phase = if (action == "contrapunto" && counterpointPlayers >= 1) GamePhase.CONTRAPUNTO else phase)
        return GameplayTableUi.feedbackForResolvedAction(before, after, target)
    }

    fun canCarryCeremonies(state: ServerGamePublic, nowMs: Long?): Boolean = nowMs != null &&
        state.winner == null && state.phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.NOCHE,
            ServerGamePhase.CONTRAPUNTO, ServerGamePhase.DESERTOR_RECONSIDERACION) &&
        (state.deadlineMs ?: nowMs) - nowMs >= 10000

    fun phaseSound(state: ServerGamePublic): GameSound? = when (state.phase) {
        ServerGamePhase.REPARTO -> GameSound.CARD_DEAL
        ServerGamePhase.NOCHE -> GameSound.NIGHT_FALL
        ServerGamePhase.AMANECER -> GameSound.DAWN.takeUnless { state.events.any { it.code == "DAWN_NO_VICTIMS" && it.round == state.round } }
        ServerGamePhase.RECUENTO_VOTOS -> GameSound.VOTE_CAST
        else -> null
    }
    /** Public phase alone controls this window, including a secretly incapacitated Mayor. */
    fun tieWindow(state: ServerGamePublic): Boolean = state.winner == null && state.phase in setOf(
        ServerGamePhase.DESEMPATE_VOTACION, ServerGamePhase.ALCALDE_DESEMPATE)

    fun tiePlayers(state: ServerGamePublic): List<ServerGamePlayer> =
        if (tieWindow(state)) state.players.filter { it.uid in state.tieCandidates }.sortedBy { it.order }
        else emptyList()

    fun counterpointReveal(state: ServerGamePublic, phaseChanged: Boolean): List<ServerGamePlayer> =
        if (phaseChanged && state.phase == ServerGamePhase.CONTRAPUNTO && state.winner == null)
            state.counterpointPlayers.mapNotNull { uid -> state.players.firstOrNull { it.uid == uid } }.takeIf { it.size == 2 }.orEmpty()
        else emptyList()

    fun investigationHint(state: ServerGameSnapshot): String? {
        if (state.ownRoleKey != "policia") return null
        val (uid, traitor) = state.privateState.investigations.lastOrNull() ?: return null
        val name = state.publicState.players.firstOrNull { it.uid == uid }?.name ?: return null
        return "$name parece ${if (traitor) "CULPABLE" else "INOCENTE"}."
    }

    fun waitingHint(state: ServerGameSnapshot): String = when (state.publicState.phase) {
        ServerGamePhase.REPARTO -> "Leé tu rol. La noche empieza enseguida."
        ServerGamePhase.NOCHE -> "El pueblo duerme. Esperá al amanecer."
        ServerGamePhase.AMANECER -> "El pueblo descubre lo que pasó durante la noche."
        ServerGamePhase.DIA_DEBATE -> "Conversá con el pueblo antes de votar."
        ServerGamePhase.CONTRAPUNTO -> "Escuchá a los jugadores del contrapunto."
        ServerGamePhase.VOTACION, ServerGamePhase.DESEMPATE_VOTACION -> "Esperá el cierre de la votación."
        ServerGamePhase.RECUENTO_VOTOS -> "Se están contando los votos."
        ServerGamePhase.ALCALDE_DESEMPATE -> "El Alcalde tiene la última palabra."
        ServerGamePhase.RESULTADO -> "El pueblo conoce el resultado de la votación."
        ServerGamePhase.DESERTOR_RECONSIDERACION -> "El Desertor está revisando su bando."
        ServerGamePhase.FINALIZADA -> "La partida terminó."
        ServerGamePhase.LOBBY -> "Preparando la próxima partida."
    }

    fun waitingButton(state: ServerGameSnapshot?): String = when {
        state == null -> "CONECTANDO…"
        state.privateState.confirmed.any { it.action == "votar" } -> "VOTO REGISTRADO"
        state.privateState.confirmed.isNotEmpty() -> "ACCIÓN REGISTRADA"
        state.publicState.phase == ServerGamePhase.DIA_DEBATE -> "DEBATE"
        state.publicState.phase == ServerGamePhase.NOCHE -> "EL PUEBLO DUERME"
        else -> "ESPERANDO…"
    }

    fun canSubmit(state: ServerGameSnapshot, option: ServerGameActionOption, target: String?, phaseIndex: Int, nowMs: Long?): Boolean =
        state.publicState.phaseIndex == phaseIndex && option in ServerGameActionPolicy.options(state, nowMs) &&
            (if (option.targets.isEmpty()) target == null else target in option.targets)

    fun player(state: ServerGameSnapshot, player: ServerGamePlayer, map: RoleMap, publicOnly: Boolean = false): GamePlayer {
        val key = if (publicOnly) player.publicRoleKey else state.roleKey(player)
        return GamePlayer(
            name = player.name, initial = player.name.take(1).uppercase(),
            role = key?.let { RoleCatalog.gameRole(it, map) }, alive = player.alive, muted = player.muted,
            isHuman = player.uid == state.ownUid, control = PlayerControl.REMOTE,
            deathCause = when (player.deathCause) {
                "NIGHT" -> DeathCause.NIGHT; "VOTE" -> DeathCause.VOTE
                "AFK", "ABANDONO" -> DeathCause.AFK; else -> DeathCause.NONE
            }
        )
    }

    fun condition(player: ServerGamePlayer): String = when {
        player.deathCause == "ABANDONO" -> "Abandonó"
        !player.alive -> "Eliminado"
        player.muted -> "Silenciado"
        else -> "En partida"
    }

    /** Reconnection reconstructs the table; old events must not cover a current action window. */
    fun revealEvents(state: ServerGamePublic, fresh: List<ServerGameEvent>): List<ServerGameEvent> = fresh.filter {
        it.round == state.round && state.winner == null && when (it.code) {
            "NIGHT_DEATH", "DAWN_NO_VICTIMS" -> state.phase == ServerGamePhase.AMANECER
            else -> false
        }
    }

    /**
     * A night that ends the match is published directly as FINALIZADA, with its death
     * in the same round. Only unseen events qualify, so reconnection never replays them.
     */
    fun finalRevealEvents(state: ServerGamePublic, fresh: List<ServerGameEvent>): List<ServerGameEvent> =
        if (state.winner == null || state.winner == "Cancelada") emptyList()
        else fresh.filter { it.round == state.round && it.code == "NIGHT_DEATH" && it.players.isNotEmpty() }

    fun silencedAtDawn(state: ServerGamePublic, phaseChanged: Boolean): List<ServerGamePlayer> =
        if (phaseChanged && state.phase == ServerGamePhase.AMANECER && state.winner == null)
            state.players.filter { it.alive && it.muted } else emptyList()

    fun oracleReveal(state: ServerGamePublic, phaseChanged: Boolean): ServerGamePlayer? =
        if (phaseChanged && state.phase == ServerGamePhase.DIA_DEBATE && state.winner == null)
            state.players.firstOrNull { it.uid == state.oracleGuestUid && !it.alive && it.deathCause != "ABANDONO" }
        else null
}
