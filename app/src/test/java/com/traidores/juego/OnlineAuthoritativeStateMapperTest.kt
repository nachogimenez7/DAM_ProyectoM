package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAuthoritativeStateMapperTest {

    @Test
    fun expelledRoleCanBePublishedBeforeKickWhenRevealOptionIsEnabled() {
        assertTrue(
            OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                revealRolesOnDeath = true,
                playerAlive = true,
                winner = "",
                votePresentation = "expulsion|1|1|9|Ana",
                playerName = "Ana",
                dayEliminationTarget = "Ana"
            )
        )
        assertFalse(
            OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                revealRolesOnDeath = false,
                playerAlive = true,
                winner = "",
                votePresentation = "expulsion|1|1|9|Ana",
                playerName = "Ana",
                dayEliminationTarget = "Ana"
            )
        )
        assertFalse(
            OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                revealRolesOnDeath = true,
                playerAlive = true,
                winner = "",
                votePresentation = "expulsion|1|1|9|Ana",
                playerName = "Beto",
                dayEliminationTarget = "Ana"
            )
        )
    }

    @Test
    fun revealedMayorRoleIsPublicEvenWhenDeathRevealIsDisabled() {
        assertTrue(
            OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                revealRolesOnDeath = false,
                playerAlive = true,
                winner = "",
                votePresentation = "",
                playerName = "Ana",
                dayEliminationTarget = "",
                alcaldeRevealed = true,
                playerRoleKey = RoleCatalog.ALCALDE
            )
        )
        assertFalse(
            OnlineAuthoritativeStateMapper.canPublishPlayerRole(
                revealRolesOnDeath = false,
                playerAlive = true,
                winner = "",
                votePresentation = "",
                playerName = "Beto",
                dayEliminationTarget = "",
                alcaldeRevealed = true,
                playerRoleKey = RoleCatalog.MEDICO
            )
        )
    }

    @Test
    fun publicPresentationFieldsAreReadFromAuthoritativeState() {
        val state = mapOf(
            "nocheSinVictima" to true,
            "presentacionVotacion" to "expulsion|2|1|18|Ana"
        )

        assertTrue(OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(state))
        assertEquals(
            "expulsion|2|1|18|Ana",
            OnlineAuthoritativeStateMapper.votePresentationFromState(state)
        )
    }

    @Test
    fun missingPublicPresentationFieldsUseSafeDefaults() {
        assertFalse(OnlineAuthoritativeStateMapper.nightHadNoVictimFromState(emptyMap()))
        assertEquals("", OnlineAuthoritativeStateMapper.votePresentationFromState(emptyMap()))
        assertEquals(0, OnlineAuthoritativeStateMapper.schemaVersionFromState(emptyMap()))
    }

    @Test
    fun playersFromStateUsesOrderForDuplicateNames() {
        val players = listOf(
            GamePlayer(name = "Federico", initial = "F", alive = true, muted = false),
            GamePlayer(name = "Federico", initial = "F", alive = true, muted = false),
            GamePlayer(name = "Ana", initial = "A", alive = true, muted = false)
        )
        val state = mapOf(
            "jugadores" to listOf(
                mapOf("orden" to 0, "nombre" to "Federico", "vivo" to false, "muteado" to false),
                mapOf("orden" to 1, "nombre" to "Federico", "vivo" to true, "muteado" to true),
                mapOf("orden" to 2, "nombre" to "Ana", "vivo" to true, "muteado" to false)
            )
        )

        val mapped = OnlineAuthoritativeStateMapper.playersFromState(players, state)!!

        assertEquals(false, mapped[0].alive)
        assertEquals(false, mapped[0].muted)
        assertEquals(true, mapped[1].alive)
        assertEquals(true, mapped[1].muted)
        assertEquals(true, mapped[2].alive)
    }

    @Test
    fun playersFromStateRestoresAfkStreaksAndDeathCause() {
        val players = listOf(
            GamePlayer(name = "Ana", initial = "A"),
            GamePlayer(name = "Bruno", initial = "B")
        )
        val state = mapOf(
            "jugadores" to listOf(
                mapOf(
                    "orden" to 0,
                    "afkNoche" to 1,
                    "afkVoto" to 0,
                    "causaEliminacion" to DeathCause.NONE.name
                ),
                mapOf(
                    "orden" to 1,
                    "vivo" to false,
                    "afkNoche" to 0,
                    "afkVoto" to 2,
                    "causaEliminacion" to DeathCause.AFK.name
                )
            )
        )

        val mapped = OnlineAuthoritativeStateMapper.playersFromState(players, state)!!

        assertEquals(1, mapped[0].consecutiveNightAfk)
        assertEquals(2, mapped[1].consecutiveVoteAfk)
        assertEquals(DeathCause.AFK, mapped[1].deathCause)
    }

    @Test
    fun finalPublicStateRevealsRolesForEveryClientWinnerScreen() {
        val players = listOf(
            GamePlayer(name = "Ana", initial = "A"),
            GamePlayer(name = "Bruno", initial = "B")
        )
        val state = mapOf(
            "ganador" to GameRules.TRAITOR_WINNER,
            "jugadores" to listOf(
                mapOf(
                    "orden" to 0,
                    "rolKey" to "aldeano",
                    "rolNombre" to "Aldeana",
                    "rolEquipo" to "Pueblo",
                    "rolImagen" to "rol_aldeano_medieval"
                ),
                mapOf(
                    "orden" to 1,
                    "rolKey" to "asesino",
                    "rolNombre" to "Asesino",
                    "rolEquipo" to "Traidores",
                    "rolImagen" to "rol_asesino_medieval"
                )
            )
        )

        val mapped = OnlineAuthoritativeStateMapper.playersFromState(players, state)!!

        assertEquals("aldeano", mapped[0].role?.key)
        assertEquals("Pueblo", mapped[0].role?.team)
        assertEquals("asesino", mapped[1].role?.key)
        assertEquals("rol_asesino_medieval", mapped[1].role?.imageResName)
        val presentation = GameplayTableUi.winnerPresentation(
            GameSession(
                code = "ONLINE",
                mapKey = "medieval",
                mapName = "Medieval",
                players = mapped,
                winner = GameRules.TRAITOR_WINNER
            )
        )
        assertEquals(listOf("Bruno"), presentation.winningPlayers.map { it.name })
    }

    @Test
    fun specialVictoriesAreRestoredAndInvalidEntriesIgnored() {
        val state = mapOf(
            "victoriasEspeciales" to listOf(
                mapOf("key" to "bufon:2:Ana", "jugador" to "Ana", "rol" to "bufon", "ronda" to 2),
                mapOf("key" to "incompleta", "jugador" to "", "rol" to "bufon", "ronda" to 2)
            )
        )

        assertEquals(
            listOf(GameSpecialVictory("bufon:2:Ana", "Ana", "bufon", 2)),
            OnlineAuthoritativeStateMapper.specialVictoriesFromState(state)
        )
    }

    @Test
    fun sharedPhaseDeadlineUsesSafeRemainingTime() {
        val state = mapOf(
            "limiteFaseEpochMs" to 50_000L,
            "limiteFasePhaseIndex" to 7,
            "inicioAutomaticoEpochMs" to 42_000L
        )

        assertEquals(50_000L, OnlineAuthoritativeStateMapper.phaseDeadlineFromState(state))
        assertEquals(42_000L, OnlineAuthoritativeStateMapper.startupDeadlineFromState(state))
        assertEquals(7, OnlineAuthoritativeStateMapper.phaseDeadlineIndexFromState(state))
        assertEquals(12_000L, OnlineAuthoritativeStateMapper.remainingPhaseMillis(50_000L, 38_000L))
        assertEquals(0L, OnlineAuthoritativeStateMapper.remainingPhaseMillis(50_000L, 60_000L))
    }
}
