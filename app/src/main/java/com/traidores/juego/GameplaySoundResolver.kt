package com.traidores.juego

object GameplaySoundResolver {

    fun transitionSoundFor(phase: GamePhase, nightHadNoVictim: Boolean = false): GameSound? {
        return when {
            GameplayTableUi.isNightPhase(phase) -> GameSound.NIGHT_FALL
            phase == GamePhase.AMANECER && !nightHadNoVictim -> GameSound.DAWN
            else -> null
        }
    }

    fun resolvedActionSound(beforePhase: GamePhase): GameSound? {
        return when (beforePhase) {
            GamePhase.VOTACION,
            GamePhase.DESEMPATE_VOTACION,
            GamePhase.ALCALDE_DESEMPATE -> GameSound.VOTE_CAST
            else -> null
        }
    }
}
