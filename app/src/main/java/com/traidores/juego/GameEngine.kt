package com.traidores.juego

object GameEngine {

    private const val MAX_ACTION_HISTORY = 60

    fun startNight(session: GameSession): GameSession {
        if (session.winner.isNotBlank()) return session

        val prepared = clearTemporaryMutes(session)
        val message = nightStartMessage(prepared)
        return prepared.copy(
            phase = GamePhase.NOCHE_ASESINO,
            publicAnnouncement = message,
            privateHint = privateRoleHint(prepared),
            nightKillTarget = "",
            assassinVotes = emptyMap(),
            protectedPlayer = "",
            nightSilenceTarget = "",
            investigatedPlayer = "",
            investigatedResult = "",
            dayEliminationTarget = "",
            votes = emptyMap(),
            voteRound = 0,
            tieVoteCandidates = emptyList(),
            alcaldeCorruption = false,
            alcaldeTieCandidates = emptyList(),
            phaseIndex = prepared.phaseIndex + 1
        ).withPublicHistory(message)
    }

    fun resolveAssassin(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_ASESINO)) return session

        val killers = activeKillers(session)
        if (killers.isEmpty()) {
            return advanceNight(
                session,
                nextPhaseAfterAssassin(session),
                nightContinuesMessage(session)
            )
        }

        val humanKiller = killers.firstOrNull { it.isHuman }
        if (
            humanKiller != null &&
            !isValidKillTarget(session, selectedTarget, humanKiller)
        ) {
            return session
        }

        val assassinVotes = killers.mapNotNull { killer ->
            val target = if (killer.isHuman) {
                selectedTarget
            } else {
                LocalBotAi.chooseAssassinTarget(session, killer)
            }
            if (isValidKillTarget(session, target, killer)) {
                killer.name to target
            } else {
                null
            }
        }.toMap()

        if (assassinVotes.isEmpty()) {
            return advanceNight(
                session,
                nextPhaseAfterAssassin(session),
                nightContinuesMessage(session)
            )
        }

        val target = assassinVoteWinner(session, assassinVotes)
            ?: return advanceNight(
                session,
                nextPhaseAfterAssassin(session),
                nightContinuesMessage(session)
            )

        val updated = session.copy(
            nightKillTarget = target,
            assassinVotes = assassinVotes,
            actionHistory = recordAssassinVotes(session, assassinVotes)
        )
        return updated.transitionTo(
            nextPhaseAfterAssassin(updated),
            nightContinuesMessage(updated),
            assassinVotePrivateHint(updated, humanKiller != null)
        )
    }

    private fun activeKillers(session: GameSession): List<GamePlayer> {
        val assassins = alivePlayers(session).filter { it.role?.key == "asesino" }
        if (assassins.isNotEmpty()) return assassins
        return alivePlayers(session).filter { it.role?.key == "espia" }
    }

    private fun assassinVoteWinner(session: GameSession, assassinVotes: Map<String, String>): String? {
        val voteCounts = assassinVotes.values.groupingBy { it }.eachCount()
        val highest = voteCounts.values.maxOrNull() ?: return null
        return voteCounts
            .filterValues { it == highest }
            .keys
            .sortedWith(
                compareBy<String> { stableVoteNoise("${session.code}:${session.round}:$it") }
                    .thenBy { it }
            )
            .firstOrNull()
    }

    private fun stableVoteNoise(seed: String): Int {
        return seed.fold(0) { acc, char ->
            ((acc * 31) + char.code) and 0x7fffffff
        }
    }

    private fun assassinVotePrivateHint(session: GameSession, humanVoted: Boolean): String {
        val human = humanPlayer(session)
        if (human.role?.key !in GameRules.traitorRoleKeys) {
            return privateRoleHint(session)
        }
        val voteSummary = session.assassinVotes.entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key} voto a ${it.value}" }
        val opening = if (humanVoted) {
            "Tu voto fue registrado."
        } else {
            "Observaste la votacion asesina."
        }
        return "$opening $voteSummary. Victima elegida: ${session.nightKillTarget}."
    }

    private fun recordAssassinVotes(
        session: GameSession,
        assassinVotes: Map<String, String>
    ): List<GameAction> {
        return assassinVotes.entries
            .sortedBy { it.key }
            .fold(session.actionHistory) { history, vote ->
                (
                    history + GameAction(
                        type = GameActionType.KILL,
                        actor = vote.key,
                        target = vote.value,
                        round = session.round,
                        phase = session.phase,
                        publiclyKnown = false
                    )
                ).takeLast(MAX_ACTION_HISTORY)
            }
    }

    fun resolveMercenary(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_MERCENARIO)) return session

        val mercenary = alivePlayers(session).firstOrNull { it.role?.key == "mercenario" }
            ?: return advanceNight(
                session,
                GamePhase.NOCHE_POLICIA,
                nightContinuesMessage(session)
            )

        val target = if (mercenary.isHuman) selectedTarget else LocalBotAi.chooseSilenceTarget(session, mercenary)
        if (!isValidSilenceTarget(session, target, mercenary)) {
            return if (mercenary.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.NOCHE_POLICIA, nightContinuesMessage(session))
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
        return updated.transitionTo(
            GamePhase.NOCHE_POLICIA,
            nightContinuesMessage(updated),
            privateHint
        )
    }

    fun resolvePolice(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_POLICIA)) return session

        val police = alivePlayers(session).firstOrNull { it.role?.key == "policia" }
            ?: return advanceNight(session, GamePhase.NOCHE_MEDICO, nightContinuesMessage(session))

        val target = if (police.isHuman) selectedTarget else LocalBotAi.chooseInvestigationTarget(session, police)
        if (!isValidNightTarget(session, target, police, allowSelf = false)) {
            return if (police.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.NOCHE_MEDICO, nightContinuesMessage(session))
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
        return updated.transitionTo(
            GamePhase.NOCHE_MEDICO,
            nightContinuesMessage(updated),
            privateHint
        )
    }

    fun resolveMedic(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_MEDICO)) return session

        val medic = alivePlayers(session).firstOrNull { it.role?.key == "medico" }
            ?: return advanceAfterMedic(session)

        val target = if (medic.isHuman) selectedTarget else LocalBotAi.chooseProtectionTarget(session, medic)
        if (!isValidNightTarget(session, target, medic, allowSelf = true)) {
            return if (medic.isHuman) {
                session
            } else {
                advanceAfterMedic(session)
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
        val nextPhase = nextPhaseAfterMedic(updated)
        return updated.transitionTo(
            nextPhase,
            if (nextPhase == GamePhase.AMANECER) {
                dawnApproachesMessage()
            } else {
                nightContinuesMessage(updated)
            },
            privateHint
        )
    }

    fun resolveOracle(session: GameSession, selectedTarget: String): GameSession {
        if (!canResolve(session, GamePhase.NOCHE_ORACULO)) return session
        val oracle = alivePlayers(session).firstOrNull { it.role?.key == RoleCatalog.ORACULO }
            ?: return advanceNight(session, GamePhase.AMANECER, dawnApproachesMessage())
        if (session.oracleUsed || oracleCandidates(session).isEmpty()) {
            return advanceNight(session, GamePhase.AMANECER, dawnApproachesMessage())
        }

        val target = if (oracle.isHuman) {
            selectedTarget
        } else {
            LocalBotAi.chooseOracleTarget(session, oracle)
        }
        if (target.isBlank()) {
            return advanceNight(session, GamePhase.AMANECER, dawnApproachesMessage())
        }
        if (!isValidOracleTarget(session, target, oracle)) {
            return if (oracle.isHuman) {
                session
            } else {
                advanceNight(session, GamePhase.AMANECER, dawnApproachesMessage())
            }
        }

        val privateHint = if (oracle.isHuman) {
            "Invocacion registrada. La identidad del Oraculo permanecera oculta."
        } else {
            session.privateHint.ifBlank { privateRoleHint(session) }
        }
        val updated = session.copy(
            oracleUsed = true,
            oracleInvitedPlayer = target,
            actionHistory = recordAction(session, GameActionType.INVITE_DEAD, oracle.name, target)
        )
        return updated.transitionTo(
            GamePhase.AMANECER,
            dawnApproachesMessage(),
            privateHint
        )
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
        val oracleMessage = session.oracleInvitedPlayer.takeIf { it.isNotBlank() }?.let { invited ->
            "El Oraculo ha permitido que $invited regrese para discutir durante este dia."
        }.orEmpty()
        val publicMessage = listOf(killMessage, silenceMessage, oracleMessage)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val dawn = session.copy(
            players = updatedPlayers,
            phase = GamePhase.DIA_DEBATE,
            publicAnnouncement = publicMessage,
            privateHint = privateRoleHint(session.copy(players = updatedPlayers)),
            oracleRevealPending = oracleMessage.isNotBlank(),
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
        val message = dayDebateMessage(session, muted)
        return session.copy(
            oracleInvitedPlayer = "",
            oracleRevealPending = false
        ).transitionTo(
            GamePhase.VOTACION,
            message,
            privateRoleHint(session)
        ).withPublicHistory(message)
            .withBotVotingIntent()
    }

    fun revealAlcalde(session: GameSession): GameSession {
        if (
            session.phase != GamePhase.DIA_DEBATE &&
            session.phase != GamePhase.VOTACION &&
            session.phase != GamePhase.DESEMPATE_VOTACION &&
            session.phase != GamePhase.ALCALDE_DESEMPATE
        ) {
            return session
        }
        if (session.alcaldeRevealed) return session
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
        val message = if (session.alcaldeCorruption) {
            "Corrupcion en el pueblo: el Alcalde se protegio y decidio expulsar a $targetName."
        } else {
            "El Alcalde decidio el empate. Se resolvera la expulsion de $targetName."
        }
        return session.copy(
            dayEliminationTarget = targetName,
            votes = emptyMap(),
            voteRound = if (session.alcaldeCorruption) 4 else 3,
            tieVoteCandidates = emptyList(),
            alcaldeTieCandidates = emptyList()
        ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(session))
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
        return resolveVotingInternal(session, selectedTarget, allowHumanAbstention = false)
    }

    fun resolveTieVoting(session: GameSession, selectedTarget: String): GameSession {
        return resolveTieVotingInternal(session, selectedTarget, allowHumanAbstention = false)
    }

    private fun resolveVotingInternal(
        session: GameSession,
        selectedTarget: String,
        allowHumanAbstention: Boolean,
        leadingAnnouncement: String = ""
    ): GameSession {
        if (!canResolve(session, GamePhase.VOTACION)) return session

        val human = humanPlayer(session)
        if (
            canVote(human) &&
            !allowHumanAbstention &&
            !isValidVoteTarget(session, selectedTarget, human)
        ) {
            return session
        }

        val votingSession = autoRevealBotAlcalde(session)
        val votes = debugForcedTieVotes(
            votingSession,
            alivePlayers(votingSession).map { it.name }
        )?.takeIf { votingSession.debugForceVoteTies }?.toMutableMap()
            ?: mutableMapOf<String, String>().apply {
                votingSession.players.filter { canVote(it) }.forEach { voter ->
                    val voteTarget = if (voter.isHuman) {
                        selectedTarget
                    } else {
                        LocalBotAi.chooseVoteTarget(votingSession, voter)
                    }
                    if (isValidVoteTarget(votingSession, voteTarget, voter)) {
                        this[voter.name] = voteTarget
                    }
                }
            }

        val leaders = weightedVoteLeaders(votingSession, votes)
        val eliminated = leaders.singleOrNull().orEmpty()
        val votingMessage = "La votacion termino. Comienza el recuento."
        val message = listOf(leadingAnnouncement, votingMessage)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return votingSession.copy(
            votes = votes,
            voteRound = 1,
            tieVoteCandidates = if (leaders.size > 1) leaders else emptyList(),
            dayEliminationTarget = eliminated,
            alcaldeTieCandidates = emptyList(),
            actionHistory = recordVotes(session, votes)
        ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(votingSession))
            .withPublicHistory(message)
    }

    private fun resolveTieVotingInternal(
        session: GameSession,
        selectedTarget: String,
        allowHumanAbstention: Boolean,
        leadingAnnouncement: String = ""
    ): GameSession {
        if (!canResolve(session, GamePhase.DESEMPATE_VOTACION)) return session
        val candidates = session.tieVoteCandidates.filter { candidate ->
            playerByName(session, candidate)?.alive == true
        }
        if (candidates.size < 2) {
            return session.copy(
                dayEliminationTarget = candidates.singleOrNull().orEmpty(),
                votes = emptyMap(),
                voteRound = 2
            ).transitionTo(
                GamePhase.RECUENTO_VOTOS,
                "El desempate termino. Comienza el recuento final.",
                privateRoleHint(session)
            )
        }

        val human = humanPlayer(session)
        if (
            canVote(human) &&
            !allowHumanAbstention &&
            !isValidTieVoteTarget(session, selectedTarget, human)
        ) {
            return session
        }

        val votes = debugForcedTieVotes(session, candidates)
            ?.takeIf { session.debugForceVoteTies }
            ?.toMutableMap()
            ?: mutableMapOf<String, String>().apply {
                session.players.filter { canVote(it) }.forEach { voter ->
                    val preferred = if (voter.isHuman) {
                        selectedTarget
                    } else {
                        LocalBotAi.chooseVoteTarget(session, voter)
                    }
                    val target = preferred.takeIf {
                        isValidTieVoteTarget(session, it, voter)
                    } ?: candidates.firstOrNull { it != voter.name }.orEmpty()
                    if (isValidTieVoteTarget(session, target, voter)) {
                        this[voter.name] = target
                    }
                }
            }

        val leaders = weightedVoteLeaders(session, votes)
        val eliminated = leaders.singleOrNull().orEmpty()
        val message = listOf(
            leadingAnnouncement,
            "El desempate termino. Comienza el recuento final."
        ).filter { it.isNotBlank() }.joinToString(" ")
        return session.copy(
            votes = votes,
            voteRound = 2,
            tieVoteCandidates = if (leaders.size > 1) leaders else emptyList(),
            dayEliminationTarget = eliminated,
            alcaldeTieCandidates = emptyList(),
            actionHistory = recordVotes(session, votes)
        ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(session))
            .withPublicHistory(message)
    }

    fun continueAfterVoteRecount(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.RECUENTO_VOTOS)) return session
        if (session.voteRound == 1 && session.tieVoteCandidates.size > 1) {
            val candidates = session.tieVoteCandidates.joinToString(", ")
            val message = "Empate entre $candidates. Si el empate se repite, nadie sera expulsado."
            return session.copy(
                votes = emptyMap(),
                dayEliminationTarget = ""
            ).transitionTo(
                GamePhase.DESEMPATE_VOTACION,
                message,
                "Vota solamente entre los jugadores empatados."
            ).withPublicHistory(message)
                .withBotVotingIntent()
        }

        if (session.voteRound == 2 && session.tieVoteCandidates.size > 1) {
            return resolveSecondTie(session)
        }

        val message = if (session.dayEliminationTarget.isBlank()) {
            "El pueblo no llego a un acuerdo. Nadie sera expulsado esta jornada."
        } else {
            "${session.dayEliminationTarget} recibio la mayoria. Se resolvera su expulsion."
        }
        return session.copy(
            tieVoteCandidates = emptyList(),
            alcaldeTieCandidates = emptyList()
        ).transitionTo(GamePhase.RESULTADO, message, privateRoleHint(session))
            .withPublicHistory(message)
    }

    private fun resolveSecondTie(session: GameSession): GameSession {
        val candidates = session.tieVoteCandidates
        val alcalde = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" }
        if (alcalde == null) {
            val message = "El empate se repitio. Nadie sera expulsado esta jornada."
            return session.copy(
                dayEliminationTarget = "",
                tieVoteCandidates = emptyList(),
                alcaldeTieCandidates = emptyList()
            ).transitionTo(GamePhase.RESULTADO, message, privateRoleHint(session))
                .withPublicHistory(message)
        }

        if (alcalde.name in candidates) {
            val revealedSession = if (session.alcaldeRevealed) {
                session
            } else {
                val revealMessage =
                    "${alcalde.name} revelo su cargo cuando la votacion amenazo con expulsarlo."
                session.copy(alcaldeRevealed = true).withPublicHistory(revealMessage)
            }
            val opponents = candidates.filter { it != alcalde.name }
            if (opponents.size == 1) {
                val expelled = opponents.single()
                val message =
                    "Corrupcion en el pueblo: el Alcalde impuso su autoridad y $expelled sera expulsado."
                return revealedSession.copy(
                    dayEliminationTarget = expelled,
                    votes = emptyMap(),
                    voteRound = 4,
                    tieVoteCandidates = emptyList(),
                    alcaldeCorruption = true,
                    alcaldeTieCandidates = emptyList()
                ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(revealedSession))
                    .withPublicHistory(message)
            }
            if (!alcalde.isHuman) {
                val expelled = LocalBotAi.chooseVoteTarget(revealedSession, alcalde)
                    .takeIf { it in opponents }
                    ?: opponents.first()
                val message =
                    "Corrupcion en el pueblo: el Alcalde se protegio y decidio expulsar a $expelled."
                return revealedSession.copy(
                    dayEliminationTarget = expelled,
                    votes = emptyMap(),
                    voteRound = 4,
                    tieVoteCandidates = emptyList(),
                    alcaldeCorruption = true,
                    alcaldeTieCandidates = emptyList()
                ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(revealedSession))
                    .withPublicHistory(message)
            }
            return revealedSession.copy(
                dayEliminationTarget = "",
                tieVoteCandidates = emptyList(),
                alcaldeCorruption = true,
                alcaldeTieCandidates = opponents
            ).transitionTo(
                GamePhase.ALCALDE_DESEMPATE,
                "Corrupcion en el pueblo: el Alcalde quedo protegido y decidira quien sera expulsado.",
                "Tu cargo te protege. Elegi a quien expulsar entre ${opponents.joinToString(" o ")}."
            )
        }

        if (alcalde.isHuman) {
            val privateMessage = if (session.alcaldeRevealed) {
                "Elegi quien sera expulsado entre ${candidates.joinToString(" o ")}."
            } else {
                "Podes revelarte como Alcalde y decidir el empate."
            }
            return session.copy(
                dayEliminationTarget = "",
                tieVoteCandidates = emptyList(),
                alcaldeTieCandidates = candidates
            ).transitionTo(
                GamePhase.ALCALDE_DESEMPATE,
                "El empate se repitio. El Alcalde puede decidir la expulsion.",
                privateMessage
            )
        }

        val revealedSession = if (session.alcaldeRevealed) {
            session
        } else {
            val revealMessage = "${alcalde.name} se revelo como Alcalde para decidir el empate."
            session.copy(alcaldeRevealed = true).withPublicHistory(revealMessage)
        }
        val chosen = LocalBotAi.chooseVoteTarget(revealedSession, alcalde)
            .takeIf { it in candidates && it != alcalde.name }
            ?: candidates.firstOrNull { it != alcalde.name }
            ?: candidates.first()
        val message = "El Alcalde decidio el empate. Se resolvera la expulsion de $chosen."
        return revealedSession.copy(
            dayEliminationTarget = chosen,
            votes = emptyMap(),
            voteRound = 3,
            tieVoteCandidates = emptyList(),
            alcaldeTieCandidates = emptyList()
        ).transitionTo(GamePhase.RECUENTO_VOTOS, message, privateRoleHint(revealedSession))
            .withPublicHistory(message)
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
        val expelledPlayer = targetPlayer.copy(alive = false, muted = false)
        val jesterVictory = expelledPlayer.role?.key == RoleCatalog.BUFON &&
            session.specialVictories.none {
                it.key == JESTER_VICTORY_KEY && it.playerName == target
            }
        val message = if (jesterVictory) {
            "Dia ${session.round}: $target fue expulsado. " +
                "$target era el Bufon y gano al ser expulsado por el pueblo."
        } else {
            "Dia ${session.round}: $target fue expulsado."
        }
        val specialVictories = if (jesterVictory) {
            session.specialVictories + GameSpecialVictory(
                key = JESTER_VICTORY_KEY,
                playerName = target,
                roleKey = RoleCatalog.BUFON,
                round = session.round
            )
        } else {
            session.specialVictories
        }
        val resolved = session.copy(
            players = updatedPlayers,
            specialVictories = specialVictories,
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
            session.phase == GamePhase.DIA_DEBATE &&
                session.oracleInvitedPlayer == human.name ->
                " El Oraculo te devolvio la voz durante este debate. No podes votar."
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
            GamePhase.NOCHE_ORACULO -> !isHumanRoleTurn(session, RoleCatalog.ORACULO) ||
                canActOnTarget(session, selectedTarget)
            GamePhase.CONTRAPUNTO -> false
            GamePhase.ALCALDE_DESEMPATE -> false
            GamePhase.VOTACION -> !canVote(humanPlayer(session)) || canActOnTarget(session, selectedTarget)
            GamePhase.DESEMPATE_VOTACION ->
                !canVote(humanPlayer(session)) || canActOnTarget(session, selectedTarget)
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
            GamePhase.NOCHE_ORACULO -> canActAs(session, human, RoleCatalog.ORACULO) &&
                isValidOracleTarget(session, targetName, human)
            GamePhase.DIA_DEBATE -> human.role?.key == "payador" &&
                !session.payadorUsed &&
                isValidContrapuntoTarget(session, targetName, human)
            GamePhase.CONTRAPUNTO -> human.role?.key == "payador" &&
                targetName in session.contrapuntoPlayers
            GamePhase.ALCALDE_DESEMPATE -> human.role?.key == "alcalde" &&
                session.alcaldeRevealed &&
                targetName in session.alcaldeTieCandidates
            GamePhase.VOTACION -> isValidVoteTarget(session, targetName, human)
            GamePhase.DESEMPATE_VOTACION -> isValidTieVoteTarget(session, targetName, human)
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
            GamePhase.NOCHE_ORACULO -> "INVOCAR"
            GamePhase.DIA_DEBATE -> "CONTRAPUNTO"
            GamePhase.CONTRAPUNTO -> "SENALAR"
            GamePhase.ALCALDE_DESEMPATE -> "DECIDIR"
            GamePhase.VOTACION -> "VOTAR"
            GamePhase.DESEMPATE_VOTACION -> "VOTAR"
            else -> ""
        }
    }

    fun resolveHumanTargetAction(session: GameSession, targetName: String): GameSession {
        if (!canActOnTarget(session, targetName)) return session
        val prepared = when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> resetHumanAfkStreak(session, night = true)
            GamePhase.NOCHE_ORACULO -> resetHumanAfkStreak(session, night = true)
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> resetHumanAfkStreak(session, night = false)
            else -> session
        }
        return when (prepared.phase) {
            GamePhase.NOCHE_ASESINO -> resolveAssassin(prepared, targetName)
            GamePhase.NOCHE_MERCENARIO -> resolveMercenary(prepared, targetName)
            GamePhase.NOCHE_POLICIA -> resolvePolice(prepared, targetName)
            GamePhase.NOCHE_MEDICO -> resolveMedic(prepared, targetName)
            GamePhase.NOCHE_ORACULO -> resolveOracle(prepared, targetName)
            GamePhase.DIA_DEBATE -> chooseContrapuntoPlayer(session, targetName)
            GamePhase.CONTRAPUNTO -> resolveContrapunto(session, targetName)
            GamePhase.ALCALDE_DESEMPATE -> chooseAlcaldeTie(session, targetName)
            GamePhase.VOTACION -> resolveVoting(prepared, targetName)
            GamePhase.DESEMPATE_VOTACION -> resolveTieVoting(prepared, targetName)
            else -> session
        }
    }

    fun resolveHumanTimeout(session: GameSession): GameSession {
        if (session.winner.isNotBlank()) return session
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> resolveNightTimeout(session)
            GamePhase.NOCHE_ORACULO -> resolveNightTimeout(session)
            GamePhase.VOTACION -> resolveVotingTimeout(session)
            GamePhase.DESEMPATE_VOTACION -> resolveTieVotingTimeout(session)
            GamePhase.ALCALDE_DESEMPATE -> resolveAlcaldeTieTimeout(session)
            GamePhase.CONTRAPUNTO -> resolveContrapuntoTimeout(session)
            else -> session
        }
    }

    fun resolveContrapuntoTimeout(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.CONTRAPUNTO)) return session
        val message = "El Contrapunto termino sin un senalamiento."
        return session.copy(contrapuntoSuspicion = "")
            .transitionTo(GamePhase.VOTACION, message, privateRoleHint(session))
            .withPublicHistory(message)
            .withBotVotingIntent()
    }

    fun resolveAlcaldeTieTimeout(session: GameSession): GameSession {
        if (!canResolve(session, GamePhase.ALCALDE_DESEMPATE)) return session
        val message = "El Alcalde no decidio el empate. Nadie sera expulsado."
        return session.copy(
            dayEliminationTarget = "",
            alcaldeTieCandidates = emptyList()
        ).transitionTo(GamePhase.RESULTADO, message, privateRoleHint(session))
            .withPublicHistory(message)
    }

    fun isAlive(player: GamePlayer): Boolean {
        return player.alive
    }

    fun canSpeak(player: GamePlayer): Boolean {
        return player.alive && !player.muted
    }

    fun canSpeak(session: GameSession, player: GamePlayer): Boolean {
        return canSpeak(player) || (
            session.phase == GamePhase.DIA_DEBATE &&
                session.oracleInvitedPlayer == player.name
            )
    }

    fun canVote(player: GamePlayer): Boolean {
        return player.alive && !player.muted
    }

    fun canHumanChat(session: GameSession): Boolean {
        val human = humanPlayer(session)
        if (!canSpeak(session, human) || session.winner.isNotBlank()) return false

        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> true
            GamePhase.CONTRAPUNTO ->
                human.role?.key == "payador" || human.name in session.contrapuntoPlayers
            GamePhase.ALCALDE_DESEMPATE -> false
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> false
            GamePhase.NOCHE_ORACULO -> false
            else -> false
        }
    }

    fun addHumanChatMessage(
        session: GameSession,
        rawMessage: String,
        includeBotReactions: Boolean = true
    ): GameSession {
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(140)
        if (message.isBlank() || !canHumanChat(session)) return session
        val withHumanMessage = session.withChatMessage(humanPlayer(session).name, message)
        if (!includeBotReactions || LocalBotAi.isDebugVoteCommand(withHumanMessage, message)) {
            return withHumanMessage
        }
        return when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION -> try {
                withHumanMessage.withBotMessages(
                    LocalBotAi.reactionsToHumanMessage(withHumanMessage, message)
                )
            } catch (_: RuntimeException) {
                withHumanMessage
            }
            else -> withHumanMessage
        }
    }

    fun addBotChatMessage(session: GameSession, speaker: String, rawMessage: String): GameSession {
        val message = rawMessage.trim().replace(Regex("\\s+"), " ").take(140)
        val bot = playerByName(session, speaker)
        if (message.isBlank() || bot == null || bot.isHuman || !canSpeak(session, bot)) return session
        if (
            session.winner.isNotBlank() ||
            session.phase !in botChatPhases
        ) {
            return session
        }
        return session.withChatMessage(bot.name, message)
    }

    fun requiresHumanInput(session: GameSession): Boolean {
        val human = humanPlayer(session)
        return when (session.phase) {
            GamePhase.NOCHE_ASESINO -> isAlive(human) && canActAs(session, human, "asesino")
            GamePhase.NOCHE_MERCENARIO -> isHumanRoleTurn(session, "mercenario")
            GamePhase.NOCHE_POLICIA -> isHumanRoleTurn(session, "policia")
            GamePhase.NOCHE_MEDICO -> isHumanRoleTurn(session, "medico")
            GamePhase.NOCHE_ORACULO -> isHumanRoleTurn(session, RoleCatalog.ORACULO)
            GamePhase.CONTRAPUNTO ->
                isAlive(human) && human.role?.key == "payador"
            GamePhase.ALCALDE_DESEMPATE ->
                isAlive(human) && human.role?.key == "alcalde"
            GamePhase.VOTACION -> canVote(human)
            GamePhase.DESEMPATE_VOTACION -> canVote(human)
            else -> needsInitialDesertorChoice(session)
        }
    }

    fun shouldAutoAdvance(session: GameSession): Boolean {
        return session.quickTestMode &&
            session.winner.isBlank() &&
            session.phase != GamePhase.RECUENTO_VOTOS &&
            !requiresHumanInput(session) &&
            !canDesertorReconsider(session)
    }

    fun autoAdvanceDelayMs(session: GameSession): Long {
        val timing = session.timingConfig.normalized()
        val seconds = when (session.phase) {
            GamePhase.DIA_DEBATE,
            GamePhase.CONTRAPUNTO -> timing.discussionSeconds
            GamePhase.VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> timing.votingSeconds
            GamePhase.DESEMPATE_VOTACION -> (timing.votingSeconds / 2).coerceAtLeast(10)
            GamePhase.NOCHE_ASESINO,
            GamePhase.NOCHE_MERCENARIO,
            GamePhase.NOCHE_POLICIA,
            GamePhase.NOCHE_MEDICO -> timing.nightSeconds
            GamePhase.NOCHE_ORACULO -> timing.nightSeconds
            GamePhase.REPARTO,
            GamePhase.AMANECER,
            GamePhase.RECUENTO_VOTOS,
            GamePhase.RESULTADO -> timing.transitionSeconds
        }
        return seconds * 1000L
    }

    private fun resolveNightTimeout(session: GameSession): GameSession {
        if (!requiresHumanInput(session)) return session
        val missed = registerHumanAfkMiss(session, night = true)
        if (missed.session.winner.isNotBlank()) return missed.session

        val publicMessage = if (missed.expelled) {
            "${missed.humanName} fue expulsado por inactividad. ${nightContinuesMessage(session)}"
        } else {
            nightContinuesMessage(session)
        }
        val advanced = when (session.phase) {
            GamePhase.NOCHE_ASESINO -> missed.session.transitionTo(
                nextPhaseAfterAssassin(missed.session),
                publicMessage,
                missed.session.privateHint
            )
            GamePhase.NOCHE_MERCENARIO -> missed.session.transitionTo(
                GamePhase.NOCHE_POLICIA,
                publicMessage,
                missed.session.privateHint
            )
            GamePhase.NOCHE_POLICIA -> missed.session.transitionTo(
                GamePhase.NOCHE_MEDICO,
                publicMessage,
                missed.session.privateHint
            )
            GamePhase.NOCHE_MEDICO -> missed.session.transitionTo(
                nextPhaseAfterMedic(missed.session),
                if (missed.expelled) {
                    "${missed.humanName} fue expulsado por inactividad. " +
                        if (nextPhaseAfterMedic(missed.session) == GamePhase.AMANECER) {
                            dawnApproachesMessage()
                        } else {
                            nightContinuesMessage(missed.session)
                        }
                } else {
                    if (nextPhaseAfterMedic(missed.session) == GamePhase.AMANECER) {
                        dawnApproachesMessage()
                    } else {
                        nightContinuesMessage(missed.session)
                    }
                },
                missed.session.privateHint
            )
            GamePhase.NOCHE_ORACULO -> missed.session.transitionTo(
                GamePhase.AMANECER,
                if (missed.expelled) {
                    "${missed.humanName} fue expulsado por inactividad. ${dawnApproachesMessage()}"
                } else {
                    dawnApproachesMessage()
                },
                missed.session.privateHint
            )
            else -> missed.session
        }
        return advanced
    }

    private fun resolveVotingTimeout(session: GameSession): GameSession {
        val human = humanPlayer(session)
        if (!canVote(human)) {
            return resolveVotingInternal(session, "", allowHumanAbstention = true)
        }

        val missed = registerHumanAfkMiss(session, night = false)
        if (missed.session.winner.isNotBlank()) return missed.session
        val afkAnnouncement = if (missed.expelled) {
            "${missed.humanName} fue expulsado por inactividad."
        } else {
            ""
        }
        val resolved = resolveVotingInternal(
            missed.session,
            "",
            allowHumanAbstention = true,
            leadingAnnouncement = afkAnnouncement
        )
        return if (missed.expelled) {
            resolved
        } else {
            resolved.copy(privateHint = missed.session.privateHint)
        }
    }

    private fun resolveTieVotingTimeout(session: GameSession): GameSession {
        val human = humanPlayer(session)
        if (!canVote(human)) {
            return resolveTieVotingInternal(session, "", allowHumanAbstention = true)
        }

        val missed = registerHumanAfkMiss(session, night = false)
        if (missed.session.winner.isNotBlank()) return missed.session
        val afkAnnouncement = if (missed.expelled) {
            "${missed.humanName} fue expulsado por inactividad."
        } else {
            ""
        }
        val resolved = resolveTieVotingInternal(
            missed.session,
            "",
            allowHumanAbstention = true,
            leadingAnnouncement = afkAnnouncement
        )
        return if (missed.expelled) {
            resolved
        } else {
            resolved.copy(privateHint = missed.session.privateHint)
        }
    }

    private fun registerHumanAfkMiss(session: GameSession, night: Boolean): AfkMissResult {
        val human = humanPlayer(session)
        val nextStreak = if (night) {
            human.consecutiveNightAfk + 1
        } else {
            human.consecutiveVoteAfk + 1
        }
        val expelled = nextStreak >= 2
        val updatedPlayers = session.players.map { player ->
            if (!player.isHuman) {
                player
            } else if (expelled) {
                player.copy(
                    alive = false,
                    muted = false,
                    consecutiveNightAfk = if (night) nextStreak else player.consecutiveNightAfk,
                    consecutiveVoteAfk = if (night) player.consecutiveVoteAfk else nextStreak
                )
            } else if (night) {
                player.copy(consecutiveNightAfk = nextStreak)
            } else {
                player.copy(consecutiveVoteAfk = nextStreak)
            }
        }

        if (!expelled) {
            val nextOpportunity = if (night) "proxima noche" else "proxima votacion"
            val action = if (night) "accion" else "voto"
            return AfkMissResult(
                session = session.copy(
                    players = updatedPlayers,
                    privateHint = "Perdiste tu $action. Si volves a ausentarte en tu $nextOpportunity, seras expulsado por AFK."
                ),
                expelled = false,
                humanName = human.name
            )
        }

        val message = "${human.name} fue expulsado por inactividad."
        val expelledSession = session.copy(
            players = updatedPlayers,
            publicAnnouncement = message,
            privateHint = "Fuiste expulsado por AFK."
        ).withPublicHistory(message)
            .withWinnerCheck()
        return AfkMissResult(expelledSession, expelled = true, humanName = human.name)
    }

    private fun resetHumanAfkStreak(session: GameSession, night: Boolean): GameSession {
        return session.copy(
            players = session.players.map { player ->
                if (!player.isHuman) {
                    player
                } else if (night) {
                    player.copy(consecutiveNightAfk = 0)
                } else {
                    player.copy(consecutiveVoteAfk = 0)
                }
            }
        )
    }

    private fun canResolve(session: GameSession, expectedPhase: GamePhase): Boolean {
        return session.winner.isBlank() && session.phase == expectedPhase
    }

    private fun startNextRound(session: GameSession, previousMessage: String): GameSession {
        val prepared = clearTemporaryMutes(session)
        val message = "$previousMessage ${nextNightMessage(prepared)}"
        // payadorUsed no se reinicia: el Contrapunto se usa una sola vez por partida.
        return prepared.copy(
            phase = GamePhase.NOCHE_ASESINO,
            round = prepared.round + 1,
            nightKillTarget = "",
            assassinVotes = emptyMap(),
            protectedPlayer = "",
            nightSilenceTarget = "",
            investigatedPlayer = "",
            investigatedResult = "",
            oracleInvitedPlayer = "",
            oracleRevealPending = false,
            dayEliminationTarget = "",
            votes = emptyMap(),
            voteRound = 0,
            tieVoteCandidates = emptyList(),
            alcaldeCorruption = false,
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

    private fun advanceAfterMedic(session: GameSession): GameSession {
        val nextPhase = nextPhaseAfterMedic(session)
        val message = if (nextPhase == GamePhase.AMANECER) {
            dawnApproachesMessage()
        } else {
            nightContinuesMessage(session)
        }
        return advanceNight(session, nextPhase, message)
    }

    private fun nightStartMessage(session: GameSession): String {
        return when (session.mapKey) {
            "medieval" ->
                "Noche ${session.round}: el pueblo atranca sus puertas. Afuera, nadie espera misericordia."
            "grecia" ->
                "Noche ${session.round}: el pueblo guarda silencio. Hasta los sabios temen a las sombras."
            else ->
                "Noche ${session.round}: el pueblo apaga los faroles. Entre el polvo, nadie distingue los pasos."
        }
    }

    private fun nightContinuesMessage(session: GameSession): String {
        return when (session.mapKey) {
            "medieval" -> "La noche aprieta su mano sobre el pueblo."
            "grecia" -> "La noche sigue su curso y el pueblo permanece en silencio."
            else -> "La noche sigue su curso; en el pueblo no se oye ni un susurro."
        }
    }

    private fun dawnApproachesMessage(): String = "El amanecer se acerca."

    private fun dayDebateMessage(session: GameSession, muted: String): String {
        val opening = when (session.mapKey) {
            "medieval" -> "El pueblo despierta con miedo. Cada palabra puede condenar a alguien."
            "grecia" -> "El pueblo despierta. Que la razon separe la verdad del engaño."
            else -> "El pueblo despierta y se junta a discutir antes de votar."
        }
        return if (muted.isBlank()) {
            "Dia ${session.round}: $opening"
        } else {
            "Dia ${session.round}: $opening Muteados: $muted."
        }
    }

    private fun nextNightMessage(session: GameSession): String {
        return when (session.mapKey) {
            "medieval" -> "La oscuridad vuelve a caer y el pueblo cierra sus puertas."
            "grecia" -> "La oscuridad vuelve a caer sobre el pueblo y sus dudas."
            else -> "La oscuridad vuelve a caer sobre el pueblo."
        }
    }

    private fun nextPhaseAfterAssassin(session: GameSession): GamePhase {
        return if (alivePlayers(session).any { it.role?.key == "mercenario" }) {
            GamePhase.NOCHE_MERCENARIO
        } else {
            GamePhase.NOCHE_POLICIA
        }
    }

    private fun nextPhaseAfterMedic(session: GameSession): GamePhase {
        val oracle = alivePlayers(session).firstOrNull { it.role?.key == RoleCatalog.ORACULO }
        return if (
            oracle != null &&
            !session.oracleUsed &&
            oracleCandidates(session).isNotEmpty()
        ) {
            GamePhase.NOCHE_ORACULO
        } else {
            GamePhase.AMANECER
        }
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

    internal fun oracleCandidates(session: GameSession): List<GamePlayer> {
        return session.players.filter { !it.alive }
    }

    internal fun isValidOracleTarget(
        session: GameSession,
        selectedTarget: String,
        oracle: GamePlayer
    ): Boolean {
        if (!isAlive(oracle) || oracle.role?.key != RoleCatalog.ORACULO || session.oracleUsed) {
            return false
        }
        val target = playerByName(session, selectedTarget)
        return target != null && !target.alive
    }

    fun skipOraclePower(session: GameSession): GameSession {
        if (
            session.phase != GamePhase.NOCHE_ORACULO ||
            !isHumanRoleTurn(session, RoleCatalog.ORACULO)
        ) {
            return session
        }
        return session.transitionTo(
            GamePhase.AMANECER,
            dawnApproachesMessage(),
            "Guardaste tu poder para otra noche."
        )
    }

    fun acknowledgeOracleReveal(session: GameSession): GameSession {
        return if (session.oracleRevealPending) {
            session.copy(oracleRevealPending = false)
        } else {
            session
        }
    }

    private fun isValidVoteTarget(session: GameSession, selectedTarget: String, voter: GamePlayer): Boolean {
        if (!canVote(voter)) return false
        val target = playerByName(session, selectedTarget)
        return target != null && isAlive(target) && target.name != voter.name
    }

    private fun isValidTieVoteTarget(
        session: GameSession,
        selectedTarget: String,
        voter: GamePlayer
    ): Boolean {
        return isValidVoteTarget(session, selectedTarget, voter) &&
            selectedTarget in session.tieVoteCandidates
    }

    private fun debugForcedTieVotes(
        session: GameSession,
        availableCandidates: List<String>
    ): Map<String, String>? {
        if (!session.debugForceVoteTies) return null
        val candidates = availableCandidates.distinct().filter { candidate ->
            playerByName(session, candidate)?.alive == true
        }
        for (firstIndex in 0 until candidates.lastIndex) {
            for (secondIndex in firstIndex + 1 until candidates.size) {
                forcedTiePlan(session, candidates[firstIndex], candidates[secondIndex])?.let {
                    return it.votes
                }
            }
        }
        return null
    }

    private fun forcedTiePlan(
        session: GameSession,
        firstCandidate: String,
        secondCandidate: String
    ): ForcedTiePlan? {
        val otherFixedVotes = if (
            session.contrapuntoSuspicion.isNotBlank() &&
            session.contrapuntoSuspicion != firstCandidate &&
            session.contrapuntoSuspicion != secondCandidate
        ) {
            1
        } else {
            0
        }
        val firstFixedVotes = if (session.contrapuntoSuspicion == firstCandidate) 1 else 0
        val secondFixedVotes = if (session.contrapuntoSuspicion == secondCandidate) 1 else 0
        var plans = mutableMapOf(
            firstFixedVotes - secondFixedVotes to ForcedTiePlan(
                votes = emptyMap(),
                firstWeight = firstFixedVotes,
                secondWeight = secondFixedVotes
            )
        )
        val mayor = alivePlayers(session).firstOrNull { it.role?.key == "alcalde" }
        session.players.filter { canVote(it) }.forEach { voter ->
            val voteWeight = if (
                session.alcaldeRevealed &&
                mayor?.name == voter.name
            ) {
                2
            } else {
                1
            }
            val nextPlans = plans.toMutableMap()
            plans.values.forEach { plan ->
                if (voter.name != firstCandidate) {
                    keepBetterTiePlan(
                        nextPlans,
                        plan.copy(
                            votes = plan.votes + (voter.name to firstCandidate),
                            firstWeight = plan.firstWeight + voteWeight
                        )
                    )
                }
                if (voter.name != secondCandidate) {
                    keepBetterTiePlan(
                        nextPlans,
                        plan.copy(
                            votes = plan.votes + (voter.name to secondCandidate),
                            secondWeight = plan.secondWeight + voteWeight
                        )
                    )
                }
            }
            plans = nextPlans
        }
        return plans[0]?.takeIf { plan ->
            plan.firstWeight > otherFixedVotes &&
                plan.secondWeight > otherFixedVotes
        }
    }

    private fun keepBetterTiePlan(
        plans: MutableMap<Int, ForcedTiePlan>,
        candidate: ForcedTiePlan
    ) {
        val difference = candidate.firstWeight - candidate.secondWeight
        val current = plans[difference]
        if (current == null || candidate.votes.size > current.votes.size) {
            plans[difference] = candidate
        }
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

    private data class AfkMissResult(
        val session: GameSession,
        val expelled: Boolean,
        val humanName: String
    )

    private data class ForcedTiePlan(
        val votes: Map<String, String>,
        val firstWeight: Int,
        val secondWeight: Int
    )

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

    private val botChatPhases = setOf(
        GamePhase.DIA_DEBATE,
        GamePhase.CONTRAPUNTO,
        GamePhase.VOTACION,
        GamePhase.DESEMPATE_VOTACION
    )

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

    private const val JESTER_VICTORY_KEY = "bufon_expulsado"

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
