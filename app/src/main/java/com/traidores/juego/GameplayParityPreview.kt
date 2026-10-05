package com.traidores.juego

/** Used only by the existing debuggable-APK preview gate in GameplayMockActivity. */
internal object GameplayParityPreview {
    fun create(key: String, base: GameSession): GameSession? {
        val players = base.players.mapIndexed { index, player ->
            player.copy(role = RoleCatalog.gameRole(listOf(RoleCatalog.ALDEANO, RoleCatalog.ALCALDE,
                RoleCatalog.PAYADOR, RoleCatalog.ASESINO, RoleCatalog.DESERTOR)[index], RoleMap.MEDIEVAL))
        }
        val session = base.copy(players = players, round = 2, phaseIndex = 30,
            publicAnnouncement = "El pueblo espera el resultado.")
        val ballots = mapOf(players[0].name to players[3].name, players[1].name to players[3].name,
            players[2].name to players[0].name, players[3].name to players[0].name, players[4].name to players[3].name)
        return when (key) {
            "parity-recount" -> session.copy(phase = GamePhase.RECUENTO_VOTOS, voteRound = 1,
                votes = ballots, alcaldeRevealed = true, contrapuntoSuspicion = players[3].name,
                dayEliminationTarget = players[3].name)
            "parity-mayor" -> session.copy(phase = GamePhase.RECUENTO_VOTOS, voteRound = 3,
                votes = ballots, alcaldeRevealed = true, tieVoteCandidates = listOf(players[0].name, players[3].name),
                dayEliminationTarget = players[3].name)
            "parity-muted", "parity-eliminated" -> session.copy(phase = GamePhase.VOTACION,
                players = players.mapIndexed { index, player -> if (index == 0) {
                    player.copy(alive = key != "parity-eliminated", muted = key == "parity-muted")
                } else player })
            "parity-winner" -> session.copy(phase = GamePhase.RESULTADO, winner = GameRules.TOWN_WINNER,
                desertorTeam = GameRules.TOWN_WINNER, players = players.mapIndexed { index, player ->
                    when (index) {
                        2 -> player.copy(role = RoleCatalog.gameRole(RoleCatalog.BUFON, RoleMap.MEDIEVAL),
                            alive = false, deathCause = DeathCause.VOTE)
                        3, 4 -> player.copy(alive = false, deathCause = DeathCause.VOTE)
                        else -> player
                    }
                }, specialVictories = listOf(GameSpecialVictory("bufon_expulsado", players[2].name, RoleCatalog.BUFON, 1)))
            else -> null
        }
    }
}
