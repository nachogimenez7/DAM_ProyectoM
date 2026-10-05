package com.traidores.juego

/** Weighted seals for the ceremony; a mayor's tiebreak decision is not a new ballot. */
object VoteRecountPresentation {
    data class Token(val voterName: String, val targetName: String, val initial: String)

    fun candidates(session: GameSession): List<GamePlayer> {
        if (session.voteRound == 3) {
            return session.players.filter { it.name == session.dayEliminationTarget }
        }
        return session.players.filter { player ->
            session.votes.values.any { it == player.name } || session.contrapuntoSuspicion == player.name
        }
    }

    fun tokens(session: GameSession): List<Token> {
        if (session.voteRound == 3) return emptyList()
        val playersByName = session.players.associateBy { it.name }
        val result = session.votes.map { (voter, target) ->
            Token(voter, target, playersByName[voter]?.initial ?: "?")
        }.toMutableList()
        val mayor = session.players.firstOrNull { it.alive && it.role?.key == RoleCatalog.ALCALDE }
        if (session.alcaldeRevealed && mayor != null) {
            session.votes[mayor.name]?.let { result += Token(mayor.name, it, mayor.initial) }
        }
        if (session.contrapuntoSuspicion.isNotBlank()) {
            val payador = session.players.firstOrNull { it.role?.key == RoleCatalog.PAYADOR }
            result += Token(payador?.name ?: "Señalamiento del Payador", session.contrapuntoSuspicion, payador?.initial ?: "P")
        }
        return result
    }
}
