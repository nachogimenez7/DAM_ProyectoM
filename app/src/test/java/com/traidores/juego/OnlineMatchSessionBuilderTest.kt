package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

class OnlineMatchSessionBuilderTest {

    @Test
    fun buildPreservesRolesAndHumanByTemporaryUid() {
        val result = buildSession(uidTemporal = "uid_2")

        val session = (result as OnlineMatchSessionResult.Success).session
        assertEquals(5, session.players.size)
        assertEquals("grecia", session.mapKey)
        assertEquals("COD123", session.code)
        assertEquals("Bruno", session.players.single { it.isHuman }.name)
        assertEquals(RoleCatalog.POLICIA, session.players.single { it.isHuman }.role?.key)
        assertEquals(1, session.players.count { it.role?.key == RoleCatalog.ASESINO })
        assertEquals(1, session.players.count { it.role?.key == RoleCatalog.MEDICO })
        assertEquals(1, session.players.count { it.role?.key == RoleCatalog.POLICIA })
        assertEquals(2, session.players.count { it.role?.key == RoleCatalog.ALDEANO })
        assertEquals(true, session.afkExpulsionEnabled)
        assertEquals(1, session.players.count { it.control == PlayerControl.LOCAL })
        assertEquals(4, session.players.count { it.control == PlayerControl.REMOTE })
        assertEquals(0, session.players.count { it.control == PlayerControl.BOT })
    }

    @Test
    fun buildUsesSerializedOnlineConfiguration() {
        val initial = initialMatch(players = defaultPlayers()) + mapOf(
            "config" to mapOf(
                "transicionSeg" to 7L,
                "nocheSeg" to 75L,
                "discusionSeg" to 165L,
                "votacionSeg" to 55L,
                "revelarRolesAlMorir" to true,
                "votosIndividuales" to false
            )
        )

        val result = buildSession(uidTemporal = "uid_2", initialMatch = initial)
        val session = (result as OnlineMatchSessionResult.Success).session

        assertEquals(GameTimingConfig(7, 75, 165, 55), session.timingConfig)
        assertEquals(true, session.revealRolesOnDeath)
        assertEquals(false, session.showIndividualVotes)
    }

    @Test
    fun buildRestoresThreePlayerTestRoom() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers().take(3)),
            matchStateRaw = initialState(),
            uidTemporal = "uid_1",
            expectedPlayers = 3,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        val session = (result as OnlineMatchSessionResult.Success).session
        assertEquals(true, session.onlineTestMode)
        assertEquals(3, session.players.size)
        assertEquals(0, session.roleComposition.counts[RoleCatalog.ALDEANO])
    }

    @Test
    fun buildNormalizesSerializedTimingAndFallsBackForOldRooms() {
        val invalidConfig = initialMatch(players = defaultPlayers()) + mapOf(
            "config" to mapOf(
                "transicionSeg" to 999L,
                "nocheSeg" to -1L,
                "discusionSeg" to 999L,
                "votacionSeg" to -1L
            )
        )
        val normalized = (buildSession("uid_1", invalidConfig) as OnlineMatchSessionResult.Success).session
        assertEquals(GameTimingConfig(10, 10, 180, 10), normalized.timingConfig)
        assertEquals(false, normalized.revealRolesOnDeath)
        assertEquals(true, normalized.showIndividualVotes)

        val oldRoom = (buildSession("uid_1") as OnlineMatchSessionResult.Success).session
        assertEquals(GameTimingConfig(), oldRoom.timingConfig)
    }

    @Test
    fun buildAppliesAuthoritativeStateByOrderBeforeName() {
        val initial = initialMatch(
            players = listOf(
                player("uid_1", "Federico", RoleCatalog.ASESINO, 0),
                player("uid_2", "Federico", RoleCatalog.POLICIA, 1),
                player("uid_3", "Carla", RoleCatalog.MEDICO, 2),
                player("uid_4", "Diego", RoleCatalog.ALDEANO, 3),
                player("uid_5", "Eva", RoleCatalog.ALDEANO, 4)
            )
        )
        val state = mapOf(
            "versionEstado" to OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
            "fase" to GamePhase.DIA_DEBATE.name,
            "ronda" to 2,
            "phaseIndex" to 7,
            "anuncioPublico" to "Federico fue eliminado.",
            "historialPublico" to listOf("Noche resuelta.", "Federico fue eliminado."),
            "jugadores" to listOf(
                mapOf("orden" to 0, "nombre" to "Federico", "vivo" to false, "muteado" to false),
                mapOf("orden" to 1, "nombre" to "Federico", "vivo" to true, "muteado" to true),
                mapOf("orden" to 2, "nombre" to "Carla", "vivo" to true, "muteado" to false)
            )
        )

        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initial,
            matchStateRaw = state,
            uidTemporal = "uid_2",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        val session = (result as OnlineMatchSessionResult.Success).session
        assertEquals(GamePhase.DIA_DEBATE, session.phase)
        assertEquals(7, session.phaseIndex)
        assertEquals(false, session.players[0].alive)
        assertEquals(true, session.players[1].alive)
        assertEquals(true, session.players[1].muted)
    }

    @Test
    fun buildRejectsMissingHumanPlayer() {
        val result = buildSession(uidTemporal = "uid_ausente")

        assertEquals(
            OnlineMatchSessionError.MISSING_HUMAN_PLAYER,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildRejectsIncompleteInitialMatch() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers().take(4)),
            matchStateRaw = initialState(),
            uidTemporal = "uid_1",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        assertEquals(
            OnlineMatchSessionError.INCOMPLETE_PLAYERS,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildRejectsInvalidAuthoritativePhase() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers()),
            matchStateRaw = mapOf("fase" to "FASE_ROTA"),
            uidTemporal = "uid_1",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        assertEquals(
            OnlineMatchSessionError.INVALID_PHASE,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildRejectsAuthoritativeStateWithoutPhase() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers()),
            matchStateRaw = mapOf(
                "ronda" to 2,
                "phaseIndex" to 4,
                "anuncioPublico" to "Estado sin fase."
            ),
            uidTemporal = "uid_1",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        assertEquals(
            OnlineMatchSessionError.INVALID_PHASE,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildRejectsMissingAuthoritativeState() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers()),
            matchStateRaw = null,
            uidTemporal = "uid_1",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        assertEquals(
            OnlineMatchSessionError.MISSING_MATCH_STATE,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildRejectsStateFromPreviousOnlineSchema() {
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(players = defaultPlayers()),
            matchStateRaw = mapOf(
                "versionEstado" to 1,
                "fase" to GamePhase.REPARTO.name
            ),
            uidTemporal = "uid_1",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )

        assertEquals(
            OnlineMatchSessionError.INCOMPATIBLE_STATE,
            (result as OnlineMatchSessionResult.Failure).reason
        )
    }

    @Test
    fun buildProducesSerializableSessionForActivityIntentExtras() {
        val result = buildSession(uidTemporal = "uid_2")
        val session = (result as OnlineMatchSessionResult.Success).session

        ObjectOutputStream(ByteArrayOutputStream()).use { output ->
            output.writeObject(session)
        }
    }

    @Test
    fun buildUsesPrivateRoleAssignmentsWhenPublicPayloadHasNoRoles() {
        val publicPlayers = defaultPlayers().map {
            it - setOf("rolKey", "rolNombre", "rolEquipo", "rolImagen")
        }
        val police = RoleCatalog.gameRole(RoleCatalog.POLICIA, RoleMap.GREECE)
        val result = OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch(publicPlayers),
            matchStateRaw = initialState(),
            uidTemporal = "uid_2",
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true,
            privateRoleAssignments = listOf(
                mapOf(
                    "orden" to 1,
                    "rolKey" to police.key,
                    "rolNombre" to police.name,
                    "rolEquipo" to police.team,
                    "rolImagen" to police.imageResName
                )
            )
        )

        val session = (result as OnlineMatchSessionResult.Success).session
        assertEquals(RoleCatalog.POLICIA, GameEngine.humanPlayer(session).role?.key)
        assertEquals(1, session.players.count { it.role != null })
    }

    private fun buildSession(
        uidTemporal: String,
        initialMatch: Map<String, Any?> = initialMatch(players = defaultPlayers())
    ): OnlineMatchSessionResult {
        return OnlineMatchSessionBuilder.build(
            initialMatchRaw = initialMatch,
            matchStateRaw = initialState(),
            uidTemporal = uidTemporal,
            expectedPlayers = 5,
            fallbackRoomId = "room",
            fallbackRoomCode = "",
            fallbackMapKey = "grecia",
            fallbackMapName = "Grecia",
            revealRolesOnDeath = false,
            showIndividualVotes = true
        )
    }

    private fun initialMatch(players: List<Map<String, Any?>>): Map<String, Any?> {
        return mapOf(
            "codigoSala" to "COD123",
            "mapa" to "grecia",
            "mapaNombre" to "Grecia",
            "fase" to GamePhase.REPARTO.name,
            "ronda" to 1,
            "jugadores" to players
        )
    }

    private fun initialState(): Map<String, Any?> {
        return mapOf(
            "versionEstado" to OnlineAuthoritativeStateMapper.CURRENT_SCHEMA_VERSION,
            "fase" to GamePhase.REPARTO.name,
            "ronda" to 1,
            "phaseIndex" to 0,
            "anuncioPublico" to "Dios preparo una partida online con roles ocultos."
        )
    }

    private fun defaultPlayers(): List<Map<String, Any?>> {
        return listOf(
            player("uid_1", "Ana", RoleCatalog.ASESINO, 0),
            player("uid_2", "Bruno", RoleCatalog.POLICIA, 1),
            player("uid_3", "Carla", RoleCatalog.MEDICO, 2),
            player("uid_4", "Diego", RoleCatalog.ALDEANO, 3),
            player("uid_5", "Eva", RoleCatalog.ALDEANO, 4)
        )
    }

    private fun player(
        uid: String,
        name: String,
        roleKey: String,
        order: Int
    ): Map<String, Any?> {
        val role = RoleCatalog.gameRole(roleKey, RoleMap.GREECE)
        return mapOf(
            "orden" to order,
            "uidTemporal" to uid,
            "nombre" to name,
            "inicial" to name.first().uppercase(),
            "rolKey" to role.key,
            "rolNombre" to role.name,
            "rolEquipo" to role.team,
            "rolImagen" to role.imageResName
        )
    }
}
