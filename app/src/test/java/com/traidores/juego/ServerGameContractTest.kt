package com.traidores.juego

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Cross-language contract tests consume projections produced by the real Node game engine. */
class ServerGameContractTest {
    private fun unwrap(raw: Any?): Any? = when (raw) {
        JSONObject.NULL -> null
        is JSONObject -> raw.keys().asSequence().associateWith { unwrap(raw.get(it)) }
        is JSONArray -> (0 until raw.length()).map { unwrap(raw.get(it)) }
        else -> raw
    }
    @Suppress("UNCHECKED_CAST")
    private val fixtures: List<Map<String, Any?>> by lazy {
        val text = javaClass.getResourceAsStream("/server_game_v3.json")!!.bufferedReader().use { it.readText() }
        unwrap(JSONArray(text)) as List<Map<String, Any?>>
    }
    @Suppress("UNCHECKED_CAST")
    private fun projection(name: String) = fixtures.single { it["name"] == name }["projection"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    private fun snapshots(raw: Map<String, Any?>): List<ServerGameSnapshot> {
        val pub = ServerGameParser.parsePublic(raw["public"])
        val own = raw["private"] as Map<String, Any?>
        val permissions = raw["permissions"] as Map<String, Any?>
        return own.map { (uid, value) ->
            val inbox = ServerGameInbox(uid, pub.matchId)
            inbox.acceptPermissions(ServerGameParser.parsePermissions(permissions[uid]))
            inbox.acceptPrivate(ServerGameParser.parsePrivate(value))
            assertNull(inbox.snapshot())
            inbox.acceptPublic(pub)
            requireNotNull(inbox.snapshot())
        }
    }
    private fun byRole(raw: Map<String, Any?>, role: String) = snapshots(raw).first { it.ownRoleKey == role }
    @Test fun `server projections of five ten and fifteen players parse for every member`() {
        var count = 0
        fixtures.forEach { fixture ->
            @Suppress("UNCHECKED_CAST") val raw = fixture["projection"] as Map<String, Any?>
            snapshots(raw).forEach { state ->
                assertNotNull(state.human)
                assertNotNull(state.ownRoleKey)
                if (state.publicState.winner == null) {
                    assertTrue(state.publicState.players.all { it.publicRoleKey == null || !it.alive || it.uid == state.publicState.mayorUid })
                    val allies = setOf("asesino", "espia", "mercenario")
                    state.privateState.visibleRoleKeys.forEach { (order, key) ->
                        assertTrue(order == state.human.order || state.ownRoleKey in allies && key in allies)
                    }
                }
                assertEquals(emptyList<ServerGameActionOption>(), ServerGameActionPolicy.options(state, null))
                assertEquals(emptyList<ServerGameActionOption>(), ServerGameActionPolicy.options(state, state.publicState.deadlineMs))
                count++
            }
        }
        assertTrue("Expected broad native/server contract coverage", count > 300)
    }
    @Test fun `listeners arriving out of order never mix phases or accept regressions`() {
        val initial = snapshots(projection("pampa-15-deal")).first()
        val next = snapshots(projection("pampa-15-night")).first { it.ownUid == initial.ownUid }
        val inbox = ServerGameInbox(initial.ownUid, initial.publicState.matchId)
        inbox.acceptPublic(initial.publicState); inbox.acceptPrivate(initial.privateState); inbox.acceptPermissions(initial.permissions)
        assertNotNull(inbox.snapshot())
        inbox.acceptPublic(next.publicState)
        assertNull(inbox.snapshot())
        inbox.acceptPermissions(next.permissions)
        assertNull(inbox.snapshot())
        inbox.acceptPrivate(next.privateState)
        assertEquals(next, inbox.snapshot())
        inbox.acceptPublic(initial.publicState); inbox.acceptPrivate(initial.privateState); inbox.acceptPermissions(initial.permissions)
        assertEquals(next, inbox.snapshot())
        assertThrows(IllegalArgumentException::class.java) { inbox.acceptPublic(next.publicState.copy(matchId = "another-match")) }
        inbox.acceptPermissions(next.permissions.copy(member = false)); assertNull(inbox.snapshot())
    }
    @Test fun `muted mayor has no actions while muted deserter keeps the private choice from round four`() {
        val raw = projection("muted-deserter-and-mayor")
        val mayor = byRole(raw, "alcalde")
        assertTrue(ServerGameActionPolicy.options(mayor, mayor.publicState.deadlineMs!! - 1).isEmpty())
        val deserter = byRole(raw, "desertor")
        val options = ServerGameActionPolicy.options(deserter, deserter.publicState.deadlineMs!! - 1)
        assertEquals(2, options.size); assertTrue(options.all { it.action == "desertor_rethink" })
        assertTrue(ServerGameActionPolicy.options(deserter.copy(publicState = deserter.publicState.copy(round = 3)), deserter.publicState.deadlineMs!! - 1).isEmpty())
    }
    @Test fun `mercenary cannot select the previous nights target and cannot send twice`() {
        val state = byRole(projection("mercenary-previous-night"), "mercenario")
        val options = ServerGameActionPolicy.options(state, state.publicState.deadlineMs!! - 1)
        assertEquals(1, options.size)
        assertTrue(options.single().targets.none { it in state.privateState.blockedTargets })
        val accepted = byRole(projection("pampa-15-secret"), "mercenario")
        assertTrue(ServerGameActionPolicy.options(accepted, accepted.publicState.deadlineMs!! - 1).isEmpty())
    }
    @Test fun `oracle can invite dead players but never restore a departed player`() {
        val state = byRole(projection("oracle-dead-and-departed-target"), "oraculo")
        val options = ServerGameActionPolicy.options(state, state.publicState.deadlineMs!! - 1)
        val invite = options.single { it.action == "invitar_muerto" }
        assertTrue(invite.targets.isNotEmpty())
        assertTrue(invite.targets.all { id -> state.publicState.players.single { it.uid == id }.let { !it.alive && it.deathCause != "ABANDONO" } })
    }
    @Test fun `final result shows real roles but a departed player cannot win`() {
        val state = snapshots(projection("final-roles-and-abandonment")).first()
        assertTrue(state.publicState.players.all { it.publicRoleKey != null })
        val departed = state.publicState.players.single { it.deathCause == "ABANDONO" }
        assertFalse(state.won(departed))
    }
    @Test fun `command contains only an intention with a stable request id`() {
        val state = snapshots(projection("pampa-5-deal")).first()
        val command = ServerGameCommand.create("room", state, "role_ack")
        assertEquals(command.payload(), command.copy().payload())
        assertEquals(setOf("roomId", "matchId", "phaseIndex", "requestId", "action"), command.payload().keys)
        assertNotEquals(command.requestId, ServerGameCommand.create("room", state, "role_ack").requestId)
    }
    @Test fun `recovery is staggered bounded and resets only on a new phase`() {
        val initial = snapshots(projection("pampa-5-deal")).first().publicState
        val policy = ServerGameRecoveryPolicy("uid")
        val expiry = initial.deadlineMs!!
        assertFalse(policy.due(initial, expiry + 4999))
        var now = expiry + 7000
        repeat(3) {
            assertTrue(policy.due(initial, now)); policy.attempted(now)
            assertFalse(policy.due(initial, now)); now += 60000
        }
        assertFalse(policy.due(initial, now))
        assertTrue(policy.due(initial.copy(phaseIndex = initial.phaseIndex + 1), now))
        assertFalse(policy.due(initial.copy(phaseIndex = initial.phaseIndex + 2, winner = "Pueblo", deadlineMs = null), now))
    }
    @Test fun `sparse numeric arrays retain numeric order and unknown protocols fail closed`() {
        @Suppress("UNCHECKED_CAST") val raw = projection("pampa-15-deal")["public"] as Map<String, Any?>
        val roster = raw["jugadores"] as List<*>
        val mapped = roster.indices.reversed().associate { it.toString() to roster[it] }
        assertEquals((0..14).toList(), ServerGameParser.parsePublic(raw + ("jugadores" to mapped)).players.map { it.order })
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("protocolVersion" to 4)) }
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("fase" to "UNKNOWN")) }
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("phaseIndex" to 0.5)) }
    }
}
