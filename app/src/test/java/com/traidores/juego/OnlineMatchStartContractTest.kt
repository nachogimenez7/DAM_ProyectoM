package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchStartContractTest {

    @Test
    fun onlyHostCanStartWaitingRoom() {
        val decision = OnlineMatchStartPolicy.evaluate(
            requesterId = "intruder",
            room = room(),
            players = readyPlayers(),
            hostTieBreakChoice = null
        )

        assertEquals(
            OnlineMatchStartDecision.Rejected(OnlineMatchStartError.HOST_REQUIRED),
            decision
        )
    }

    @Test
    fun existingInitialMatchMakesStartIdempotent() {
        val decision = OnlineMatchStartPolicy.evaluate(
            requesterId = "host",
            room = room(initialMatchCreated = true),
            players = readyPlayers(),
            hostTieBreakChoice = null
        )

        assertEquals(OnlineMatchStartDecision.AlreadyStarted, decision)
    }

    @Test
    fun cleanupAndIncompletePlayersAreRejected() {
        assertEquals(
            OnlineMatchStartDecision.Rejected(OnlineMatchStartError.CLEANUP_PENDING),
            OnlineMatchStartPolicy.evaluate(
                requesterId = "host",
                room = room(cleanupPending = true),
                players = readyPlayers(),
                hostTieBreakChoice = null
            )
        )
        assertEquals(
            OnlineMatchStartDecision.Rejected(OnlineMatchStartError.PLAYER_COUNT_MISMATCH),
            OnlineMatchStartPolicy.evaluate(
                requesterId = "host",
                room = room(),
                players = readyPlayers().dropLast(1),
                hostTieBreakChoice = null
            )
        )
    }

    @Test
    fun nonWaitingRoomAndUnreadyPlayerAreRejected() {
        assertEquals(
            OnlineMatchStartDecision.Rejected(OnlineMatchStartError.ROOM_NOT_WAITING),
            OnlineMatchStartPolicy.evaluate(
                requesterId = "host",
                room = room().copy(state = OnlineLobbyRules.ROOM_STATE_IN_GAME),
                players = readyPlayers(),
                hostTieBreakChoice = null
            )
        )
        assertEquals(
            OnlineMatchStartDecision.Rejected(OnlineMatchStartError.PLAYERS_NOT_READY),
            OnlineMatchStartPolicy.evaluate(
                requesterId = "host",
                room = room(),
                players = readyPlayers().mapIndexed { index, player ->
                    if (index == 2) player.copy(ready = false) else player
                },
                hostTieBreakChoice = null
            )
        )
    }

    @Test
    fun playersAreNormalizedBeforeRoleAssignment() {
        val shuffled = readyPlayers().reversed().mapIndexed { index, player ->
            player.copy(order = readyPlayers().lastIndex - index)
        }

        val decision = OnlineMatchStartPolicy.evaluate(
            requesterId = "host",
            room = room(),
            players = shuffled,
            hostTieBreakChoice = null
        ) as OnlineMatchStartDecision.Ready

        assertEquals(listOf("host", "p1", "p2"), decision.orderedPlayers.map { it.id })
        assertEquals("pampa", decision.mapKey)
    }

    @Test
    fun tiedVoteRequiresAValidHostChoice() {
        val players = readyPlayers().mapIndexed { index, player ->
            player.copy(mapVote = listOf("pampa", "grecia", "medieval")[index])
        }
        val unresolved = OnlineMatchStartPolicy.evaluate(
            requesterId = "host",
            room = room(),
            players = players,
            hostTieBreakChoice = null
        )
        assertEquals(
            OnlineMatchStartDecision.MapTieBreakRequired(
                listOf("pampa", "grecia", "medieval")
            ),
            unresolved
        )

        val resolved = OnlineMatchStartPolicy.evaluate(
            requesterId = "host",
            room = room(),
            players = players,
            hostTieBreakChoice = "grecia"
        ) as OnlineMatchStartDecision.Ready
        assertEquals("grecia", resolved.mapKey)
    }

    @Test
    fun publicPayloadNeverContainsPrivateRoles() {
        val payloads = OnlineMatchStartPayloadFactory.build(
            assignedSession = assignedSession(),
            playersAtStart = readyPlayers(),
            updatedBy = "host",
            createdAtLocalMs = 1234L
        )

        val privateKeys = setOf("rolKey", "rolNombre", "rolEquipo", "rolImagen", "rolesVisibles")
        assertTrue(collectKeys(payloads.initialMatch).intersect(privateKeys).isEmpty())
        assertTrue(collectKeys(payloads.matchState).intersect(privateKeys).isEmpty())
        assertEquals("match-12345678", payloads.initialMatch["matchId"])
        assertEquals("host", payloads.matchState["actualizadaPor"])
    }

    @Test
    fun privatePayloadShowsOnlyOwnRoleOrTraitorTeam() {
        val payloads = OnlineMatchStartPayloadFactory.build(
            assignedSession = assignedSession(),
            playersAtStart = readyPlayers(),
            updatedBy = "host",
            createdAtLocalMs = 1234L
        )

        assertEquals(
            listOf(0, 1),
            payloads.privateRolesByPlayer.getValue("host").visibleRoles.map { it["orden"] }
        )
        assertEquals(
            listOf(0, 1),
            payloads.privateRolesByPlayer.getValue("p1").visibleRoles.map { it["orden"] }
        )
        assertEquals(
            listOf(2),
            payloads.privateRolesByPlayer.getValue("p2").visibleRoles.map { it["orden"] }
        )
        assertFalse(payloads.realtimeAccess.getValue("p2").traitor == true)
    }

    private fun room(
        cleanupPending: Boolean = false,
        initialMatchCreated: Boolean = false
    ) = OnlineMatchStartRoomState(
        state = OnlineLobbyRules.ROOM_STATE_WAITING,
        cleanupPending = cleanupPending,
        hostId = "host",
        activeHostId = "host",
        initialMatchCreated = initialMatchCreated,
        hasInitialMatch = false,
        expectedPlayers = 3,
        currentMapKey = "pampa"
    )

    private fun readyPlayers() = listOf(
        player("host", "Host", "H", 0),
        player("p1", "Uno", "U", 1),
        player("p2", "Dos", "D", 2)
    )

    private fun player(
        id: String,
        name: String,
        initial: String,
        order: Int
    ) = OnlineMatchStartPlayer(
        id = id,
        name = name,
        initial = initial,
        ready = true,
        order = order,
        activeInMatch = true,
        mapVote = null,
        publicId = "#$order"
    )

    private fun assignedSession(): GameSession {
        val assassin = GameRole("asesino", "Asesino", GameRules.TRAITOR_WINNER, "assassin")
        val spy = GameRole("espia", "Espia", GameRules.TRAITOR_WINNER, "spy")
        val villager = GameRole("aldeano", "Aldeano", GameRules.TOWN_WINNER, "villager")
        return GameSession(
            code = "ABC234",
            mapKey = "pampa",
            mapName = "La Pampa",
            players = listOf(
                GamePlayer("Host", "H", role = assassin),
                GamePlayer("Uno", "U", role = spy),
                GamePlayer("Dos", "D", role = villager)
            ),
            roleComposition = RoleCompositionConfig(
                counts = mapOf("asesino" to 1, "espia" to 1, "aldeano" to 1),
                customized = true
            ),
            onlineMatchId = "match-12345678"
        )
    }

    private fun collectKeys(value: Any?): Set<String> {
        val keys = linkedSetOf<String>()
        when (value) {
            is Map<*, *> -> value.forEach { (key, nested) ->
                if (key is String) keys += key
                keys += collectKeys(nested)
            }
            is Iterable<*> -> value.forEach { keys += collectKeys(it) }
        }
        return keys
    }
}
