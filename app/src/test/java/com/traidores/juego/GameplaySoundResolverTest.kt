package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameplaySoundResolverTest {

    @Test
    fun transitionSoundForResolvesNightPhases() {
        assertEquals(GameSound.NIGHT_FALL, GameplaySoundResolver.transitionSoundFor(GamePhase.NOCHE_ASESINO))
        assertEquals(GameSound.NIGHT_FALL, GameplaySoundResolver.transitionSoundFor(GamePhase.NOCHE_MERCENARIO))
        assertEquals(GameSound.NIGHT_FALL, GameplaySoundResolver.transitionSoundFor(GamePhase.NOCHE_POLICIA))
        assertEquals(GameSound.NIGHT_FALL, GameplaySoundResolver.transitionSoundFor(GamePhase.NOCHE_MEDICO))
        assertEquals(GameSound.NIGHT_FALL, GameplaySoundResolver.transitionSoundFor(GamePhase.NOCHE_ORACULO))
    }

    @Test
    fun transitionSoundForResolvesDawn() {
        assertEquals(GameSound.DAWN, GameplaySoundResolver.transitionSoundFor(GamePhase.AMANECER, nightHadNoVictim = false))
        assertNull(GameplaySoundResolver.transitionSoundFor(GamePhase.AMANECER, nightHadNoVictim = true))
    }

    @Test
    fun transitionSoundForReturnsNullForOtherPhases() {
        assertNull(GameplaySoundResolver.transitionSoundFor(GamePhase.DIA_DEBATE))
        assertNull(GameplaySoundResolver.transitionSoundFor(GamePhase.VOTACION))
        assertNull(GameplaySoundResolver.transitionSoundFor(GamePhase.RESULTADO))
    }

    @Test
    fun resolvedActionSoundResolvesVotingPhases() {
        assertEquals(GameSound.VOTE_CAST, GameplaySoundResolver.resolvedActionSound(GamePhase.VOTACION))
        assertEquals(GameSound.VOTE_CAST, GameplaySoundResolver.resolvedActionSound(GamePhase.DESEMPATE_VOTACION))
        assertEquals(GameSound.VOTE_CAST, GameplaySoundResolver.resolvedActionSound(GamePhase.ALCALDE_DESEMPATE))
    }

    @Test
    fun resolvedActionSoundReturnsNullForNonVotePhases() {
        assertNull(GameplaySoundResolver.resolvedActionSound(GamePhase.DIA_DEBATE))
        assertNull(GameplaySoundResolver.resolvedActionSound(GamePhase.AMANECER))
        assertNull(GameplaySoundResolver.resolvedActionSound(GamePhase.NOCHE_ASESINO))
    }
}
