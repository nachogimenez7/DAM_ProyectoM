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
    @Test fun `private investigation is presented to the investigator and never added to the public feed`() {
        val police = byRole(projection("pampa-15-night"), "policia")
        val target = police.publicState.players.first { it.uid != police.ownUid }
        val clue = police.copy(privateState = police.privateState.copy(investigations = listOf(target.uid to true)))
        assertEquals("${target.name} parece CULPABLE.", ServerGameTablePresentation.investigationHint(clue))
        val adapted = ServerGameSessionAdapter.adapt(GameSession(code = "QA", mapKey = "pampa", mapName = "Pampa", players = emptyList()), clue)
        assertEquals("${target.name} parece CULPABLE.", adapted.privateHint)
        assertTrue(adapted.chatHistory.none { it.message.contains("CULPABLE") })
        val innocent = clue.copy(privateState = clue.privateState.copy(investigations = listOf(target.uid to false)))
        assertEquals("${target.name} parece INOCENTE.", ServerGameTablePresentation.investigationHint(innocent))
        val villager = byRole(projection("pampa-15-night"), "aldeano")
        assertNull(ServerGameTablePresentation.investigationHint(villager.copy(privateState = villager.privateState.copy(investigations = listOf(target.uid to true)))))
    }
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

    @Test fun `table artwork exposes only roles granted to this viewer and public reveals hide private allies`() {
        fixtures.forEach { fixture ->
            @Suppress("UNCHECKED_CAST") val raw = fixture["projection"] as Map<String, Any?>
            snapshots(raw).forEach { state ->
                state.publicState.players.forEach { player ->
                    val card = ServerGameTablePresentation.player(state, player, RoleMap.PAMPA)
                    assertEquals(state.roleKey(player), card.role?.key)
                    assertEquals(PlayerControl.REMOTE, card.control)
                    val publicReveal = ServerGameTablePresentation.player(state, player, RoleMap.PAMPA, publicOnly = true)
                    assertEquals(player.publicRoleKey, publicReveal.role?.key)
                }
            }
        }
    }

    @Test fun `shared table adapter cannot inherit a stale identity role vote or chat from another game`() {
        val state = snapshots(projection("pampa-15-night")).first { it.ownRoleKey == "aldeano" }
        val identity = GameSession("room", "pampa", "Pampa",
            votes = mapOf("secret voter" to "secret target"),
            players = state.publicState.players.map { ServerGameTablePresentation.player(state, it, RoleMap.PAMPA).copy(role = RoleCatalog.gameRole("asesino", RoleMap.PAMPA)) },
            chatHistory = listOf(GameChatMessage("old account", "old secret", channel = ChatChannel.TRAIDORES)))
        val adapted = ServerGameSessionAdapter.adapt(identity, state)
        assertEquals(state.publicState.players.map { it.uid }, adapted.onlinePlayerUids)
        assertEquals(state.publicState.phaseIndex, adapted.phaseIndex)
        assertEquals(state.publicState.matchId, adapted.onlineMatchId)
        assertFalse(adapted.showIndividualVotes)
        assertTrue(adapted.votes.isEmpty())
        assertFalse(adapted.chatHistory.any { it.message == "old secret" })
        adapted.players.forEachIndexed { index, player ->
            assertEquals(state.roleKey(state.publicState.players[index]), player.role?.key)
            assertEquals(PlayerControl.REMOTE, player.control)
        }
    }

    @Test fun `shared chat model retains public chronicle but excludes old traitor night messages`() {
        val state = snapshots(projection("pampa-15-night")).first { it.ownRoleKey == "asesino" }
        val identity = GameSession("room", "pampa", "Pampa", emptyList())
        val actor = state.publicState.players.last().uid
        val old = ServerChatMessage("old", actor, "previous night", 1, state.publicState.phaseIndex - 1)
        val fresh = ServerChatMessage("new", actor, "current night", 2, state.publicState.phaseIndex)
        val result = ServerGameSessionAdapter.adapt(identity, state, mapOf("traidores" to listOf(old, fresh)))
        assertEquals(listOf("current night"), result.chatHistory.filter { it.channel == ChatChannel.TRAIDORES }.map { it.message })
        // Same events, with the usual table's per-map chronicle narration.
        assertEquals(state.publicState.events.map { ServerGameSessionAdapter.narration(identity, it) { uid ->
            state.publicState.players.firstOrNull { p -> p.uid == uid }?.name.orEmpty() } },
            result.chatHistory.filter { it.isGod }.map { it.message })
        assertTrue(result.chatHistory.first { it.isGod }.message.startsWith("Noche 1: "))
    }

    @Test fun `full expulsion covers the card flight and margin without waiting for final reading`() {
        val state = snapshots(projection("pampa-15-night")).first().publicState
        val target = state.players.first().copy(alive = false, deathCause = "VOTE")
        val p = state.copy(phase = ServerGamePhase.RESULTADO, dayEliminationUid = target.uid,
            players = state.players.map { if (it.uid == target.uid) target else it }, deadlineMs = 10000)
        val event = ServerGameEvent(999, "DAY_EXPULSION", p.round, listOf(target.uid), "Expulsado")
        assertEquals(ServerGameTablePresentation.ExpulsionMode.FULL, ServerGameTablePresentation.expulsion(p, listOf(event), 3800)?.mode)
        assertEquals(ServerGameTablePresentation.ExpulsionMode.COMPRESSED, ServerGameTablePresentation.expulsion(p, listOf(event), 3801)?.mode)
    }

    @Test fun `reconnecting into voting cannot replay dawn animations over the current action window`() {
        val original = snapshots(projection("pampa-15-night")).first().publicState
        val death = ServerGameEvent(200, "NIGHT_DEATH", original.round, listOf(original.players.first().uid), "Murió un jugador.")
        val dawn = original.copy(phase = ServerGamePhase.AMANECER)
        assertEquals(listOf(death), ServerGameTablePresentation.revealEvents(dawn, listOf(death)))
        assertTrue(ServerGameTablePresentation.revealEvents(dawn.copy(phase = ServerGamePhase.VOTACION), listOf(death)).isEmpty())
        assertTrue(ServerGameTablePresentation.revealEvents(dawn.copy(winner = "Pueblo"), listOf(death)).isEmpty())
        assertTrue(ServerGameTablePresentation.revealEvents(dawn.copy(round = dawn.round + 1), listOf(death)).isEmpty())
    }
    @Test fun `silence reveal uses public living players only once at dawn and never overlays voting`() {
        val original = snapshots(projection("pampa-15-night")).first().publicState
        val living = original.players[0].copy(alive = true, muted = true)
        val dead = original.players[1].copy(alive = false, muted = true)
        val dawn = original.copy(phase = ServerGamePhase.AMANECER, players = listOf(living, dead))
        assertEquals(listOf(living), ServerGameTablePresentation.silencedAtDawn(dawn, true))
        assertTrue(ServerGameTablePresentation.silencedAtDawn(dawn, false).isEmpty())
        assertTrue(ServerGameTablePresentation.silencedAtDawn(dawn.copy(phase = ServerGamePhase.VOTACION), true).isEmpty())
        assertTrue(ServerGameTablePresentation.silencedAtDawn(dawn.copy(winner = "Pueblo"), true).isEmpty())
    }
    @Test fun `oracle reveal follows the invited dead player and cannot grant an abandoned player a voice`() {
        val original = snapshots(projection("pampa-15-night")).first().publicState
        val guest = original.players.first().copy(alive = false, deathCause = "NIGHT")
        val debate = original.copy(phase = ServerGamePhase.DIA_DEBATE, oracleGuestUid = guest.uid, players = listOf(guest))
        assertEquals(guest, ServerGameTablePresentation.oracleReveal(debate, true))
        assertNull(ServerGameTablePresentation.oracleReveal(debate, false))
        assertNull(ServerGameTablePresentation.oracleReveal(debate.copy(phase = ServerGamePhase.NOCHE), true))
        assertNull(ServerGameTablePresentation.oracleReveal(debate.copy(winner = "Traidores"), true))
        assertNull(ServerGameTablePresentation.oracleReveal(debate.copy(players = listOf(guest.copy(deathCause = "ABANDONO"))), true))
    }
    @Test fun `tie window remains public and identical for a secretly muted or dead mayor`() {
        val mayor = byRole(projection("pampa-15-night"), "alcalde")
        val candidates = mayor.publicState.players.take(2).map { it.uid }
        val pub = mayor.publicState.copy(phase = ServerGamePhase.ALCALDE_DESEMPATE, tieCandidates = candidates, mayorUid = null)
        assertTrue(ServerGameTablePresentation.tieWindow(pub))
        assertEquals(candidates, ServerGameTablePresentation.tiePlayers(pub).map { it.uid })
        for (human in listOf(mayor.human.copy(muted = true), mayor.human.copy(alive = false))) {
            val hidden = pub.copy(players = pub.players.map { if (it.uid == human.uid) human else it })
            assertTrue(ServerGameTablePresentation.tieWindow(hidden))
            assertEquals(candidates, ServerGameTablePresentation.tiePlayers(hidden).map { it.uid })
            assertTrue(ServerGameActionPolicy.options(mayor.copy(publicState = hidden), pub.deadlineMs!! - 1).isEmpty())
        }
        assertTrue(ServerGameTablePresentation.tieWindow(pub.copy(phase = ServerGamePhase.DESEMPATE_VOTACION)))
        assertFalse(ServerGameTablePresentation.tieWindow(pub.copy(phase = ServerGamePhase.RESULTADO)))
        assertFalse(ServerGameTablePresentation.tieWindow(pub.copy(winner = "Pueblo")))
        // Reaching zero removes intentions, not the public window: only the server advances it.
        assertTrue(ServerGameActionPolicy.options(mayor.copy(publicState = pub), pub.deadlineMs).isEmpty())
        assertTrue(ServerGameTablePresentation.tieWindow(pub))
    }
    @Test fun `counterpoint introduction uses its two public names once without exposing the payador`() {
        val s = snapshots(projection("pampa-15-night")).first()
        val players = s.publicState.players.take(2)
        val pub = s.publicState.copy(phase = ServerGamePhase.CONTRAPUNTO, counterpointPlayers = players.map { it.uid })
        assertEquals(players, ServerGameTablePresentation.counterpointReveal(pub, true))
        assertTrue(ServerGameTablePresentation.counterpointReveal(pub, false).isEmpty())
        assertTrue(ServerGameTablePresentation.counterpointReveal(pub.copy(phase = ServerGamePhase.VOTACION), true).isEmpty())
        assertTrue(ServerGameTablePresentation.counterpointReveal(pub.copy(winner = "Pueblo"), true).isEmpty())
        assertTrue(ServerGameTablePresentation.counterpointReveal(pub.copy(counterpointPlayers = listOf("unknown", players[0].uid)), true).isEmpty())
    }
    @Test fun `tie target cannot carry over to a new phase or be dispatched when the clock expires`() {
        val s = snapshots(projection("pampa-15-night")).first { it.ownRoleKey == "aldeano" }
        val target = s.publicState.players.first { it.uid != s.ownUid }
        val tie = s.copy(publicState = s.publicState.copy(phase = ServerGamePhase.DESEMPATE_VOTACION, tieCandidates = listOf(target.uid, s.ownUid)))
        val now = tie.publicState.deadlineMs!! - 1
        val vote = ServerGameActionPolicy.options(tie, now).single { it.action == "votar" }
        assertTrue(ServerGameTablePresentation.canSubmit(tie, vote, target.uid, tie.publicState.phaseIndex, now))
        assertFalse(ServerGameTablePresentation.canSubmit(tie, vote, s.ownUid, tie.publicState.phaseIndex, now))
        assertFalse(ServerGameTablePresentation.canSubmit(tie, vote, target.uid, tie.publicState.phaseIndex, now + 1))
        assertFalse(ServerGameTablePresentation.canSubmit(tie.copy(publicState = tie.publicState.copy(phaseIndex = tie.publicState.phaseIndex + 1)), vote, target.uid, tie.publicState.phaseIndex, now))
    }
    @Test fun `recount uses published aggregate mayor weight and never exposes voter identities`() {
        val voting = snapshots(projection("votes-still-secret")).first().publicState
        assertTrue(voting.voteTotals.isEmpty())
        val recount = snapshots(projection("aggregate-recount")).first().publicState
        assertEquals(ServerGamePhase.RECUENTO_VOTOS, recount.phase)
        assertEquals(3, recount.voteTotals.values.single()) // One ordinary vote plus the revealed Mayor's two.
        assertEquals(recount.players.single { it.order == recount.voteTotals.keys.single() }.uid, recount.dayEliminationUid)
        val malformed = projection("votes-still-secret").toMutableMap()
        @Suppress("UNCHECKED_CAST") val leaked = (malformed["public"] as Map<String, Any?>).toMutableMap()
        leaked["votosTotales"] = mapOf("0" to 1)
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(leaked) }
        @Suppress("UNCHECKED_CAST") val invalid = (projection("aggregate-recount")["public"] as Map<String, Any?>).toMutableMap()
        invalid["votosTotales"] = mapOf("99" to 1)
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(invalid) }
        invalid["votosTotales"] = mapOf("0" to 1.5)
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(invalid) }
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
        // A muted Mayor cannot reveal; like every living player (usual table) he can still ask to vote early.
        assertEquals(listOf("listo_votar"), ServerGameActionPolicy.options(mayor, mayor.publicState.deadlineMs!! - 1).map { it.action })
        val deserter = byRole(raw, "desertor")
        val options = ServerGameActionPolicy.options(deserter, deserter.publicState.deadlineMs!! - 1)
        assertEquals(2, options.count { it.action == "desertor_rethink" })
        assertEquals(listOf("listo_votar"), options.filterNot { it.action == "desertor_rethink" }.map { it.action })
        assertTrue(ServerGameActionPolicy.options(deserter.copy(publicState = deserter.publicState.copy(round = 3)), deserter.publicState.deadlineMs!! - 1)
            .none { it.action == "desertor_rethink" })
    }
    @Test fun `mercenary cannot select the previous nights target and cannot send twice`() {
        val state = byRole(projection("mercenary-previous-night"), "mercenario")
        val options = ServerGameActionPolicy.options(state, state.publicState.deadlineMs!! - 1)
        assertEquals(1, options.size)
        assertTrue(options.single().targets.none { it in state.privateState.blockedTargets })
        val accepted = byRole(projection("pampa-15-secret"), "mercenario")
        assertTrue(ServerGameActionPolicy.options(accepted, accepted.publicState.deadlineMs!! - 1).isEmpty())
    }

    @Test fun `target confirmation cannot carry an old dialog into another phase or submit a forbidden target`() {
        val state = byRole(projection("mercenary-previous-night"), "mercenario")
        val now = state.publicState.deadlineMs!! - 1
        val option = ServerGameActionPolicy.options(state, now).single()
        assertTrue(ServerGameTablePresentation.canSubmit(state, option, option.targets.first(), state.publicState.phaseIndex, now))
        assertFalse(ServerGameTablePresentation.canSubmit(state, option, option.targets.first(), state.publicState.phaseIndex - 1, now))
        assertFalse(ServerGameTablePresentation.canSubmit(state, option, state.ownUid, state.publicState.phaseIndex, now))
        assertFalse(ServerGameTablePresentation.canSubmit(state, option, option.targets.first(), state.publicState.phaseIndex, state.publicState.deadlineMs))
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
    @Test fun `event sequence survives reconnect clipping recreation and rematch`() {
        val original = snapshots(projection("pampa-15-night")).first().publicState
        val events = (1L..60L).map { ServerGameEvent(it, "NIGHT_START", 1, emptyList(), "Noche") }
        val state = original.copy(events = events)
        val tracker = ServerGamePresentationTracker()
        assertTrue(tracker.accept(state).phaseChanged)
        assertEquals(60, tracker.cursor!!.eventSeq.toInt())
        assertTrue(tracker.accept(state).events.isEmpty())
        val restored = ServerGamePresentationTracker(tracker.cursor)
        val clipped = state.copy(revision = state.revision + 1, events = events.drop(1) + events.last().copy(seq = 61))
        assertEquals(listOf(61L), restored.accept(clipped).events.map { it.seq })
        assertTrue(restored.accept(state).events.isEmpty())
        assertEquals(61L, restored.cursor!!.eventSeq)
        val nextPhase = clipped.copy(phaseIndex = clipped.phaseIndex + 1, events = emptyList())
        assertTrue(restored.accept(nextPhase).phaseChanged)
        assertFalse(restored.accept(nextPhase).phaseChanged)
        val rematch = nextPhase.copy(matchId = "new-match", phaseIndex = 0, revision = 1, events = listOf(events.first()))
        val fresh = restored.accept(rematch)
        assertTrue(fresh.phaseChanged); assertEquals(listOf(1L), fresh.events.map { it.seq })
    }
    @Test fun `room protocol downgrade and forged event identifiers fail closed`() {
        assertTrue(ServerRoomProtocol.compatible(null, null, null, false))
        assertTrue(ServerRoomProtocol.compatible(3, null, null, false))
        assertTrue(ServerRoomProtocol.compatible(3, "lobby", 2, true))
        for (version in listOf(null, 2L)) {
            assertFalse(ServerRoomProtocol.compatible(version, "lobby", 2, false))
            assertFalse(ServerRoomProtocol.compatible(version, null, null, true))
        }
        assertFalse(ServerRoomProtocol.compatible(3, "client", 2, true))
        @Suppress("UNCHECKED_CAST") val raw = projection("pampa-15-night")["public"] as Map<String, Any?>
        val event = mapOf("seq" to 1L, "codigo" to "NIGHT_START", "ronda" to 1, "texto" to "Noche")
        for (bad in listOf( event + ("seq" to 0), event + ("seq" to 1.5))) {
            assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("eventosPublicos" to listOf(bad))) }
        }
        assertThrows(IllegalStateException::class.java) { ServerGameParser.parsePublic(raw + ("eventosPublicos" to listOf(event - "seq"))) }
        for (events in listOf(listOf(event, event), listOf(event + ("seq" to 2), event))) {
            assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("eventosPublicos" to events)) }
        }
        assertThrows(IllegalArgumentException::class.java) { ServerGameParser.parsePublic(raw + ("authorityMode" to "client")) }
    }

    @Test fun `expulsion is driven by confirmed result and adapts to remaining time without replay`() {
        val p = snapshots(projection("expulsion-role-true")).first().publicState
        val plan = ServerGameTablePresentation.expulsion(p, p.events, p.deadlineMs!! - 8000)!!
        assertEquals(ServerGameTablePresentation.ExpulsionMode.FULL, plan.mode)
        assertFalse(plan.player.alive)
        assertEquals(ServerGameTablePresentation.ExpulsionMode.COMPRESSED,
            ServerGameTablePresentation.expulsion(p, p.events, p.deadlineMs - 4000)!!.mode)
        for (now in listOf(null, p.deadlineMs - 2499, p.deadlineMs)) {
            assertEquals(ServerGameTablePresentation.ExpulsionMode.STATIC,
                ServerGameTablePresentation.expulsion(p, p.events, now)!!.mode)
        }
        assertEquals(ServerGameTablePresentation.ExpulsionMode.STATIC,
            ServerGameTablePresentation.expulsion(p, emptyList(), p.deadlineMs - 8000)!!.mode)
        assertNull(ServerGameTablePresentation.expulsion(p.copy(phase = ServerGamePhase.RECUENTO_VOTOS), p.events, p.deadlineMs - 8000))
        assertNull(ServerGameTablePresentation.expulsion(p.copy(players = p.players.map { it.copy(alive = true) }), p.events, p.deadlineMs - 8000))
        assertTrue(ServerGameTablePresentation.revealEvents(p, p.events).isEmpty()) // Never a second death ceremony.
    }

    @Test fun `roster events and accessibility text cannot anticipate the expulsion impact`() {
        val s = snapshots(projection("expulsion-role-true")).first { it.ownRoleKey == "aldeano" }
        val target = s.publicState.players.single { it.uid == s.publicState.dayEliminationUid }
        val previous = target.copy(alive = true, publicRoleKey = null, deathCause = "NONE")
        val held = ServerGameTablePresentation.visibleDuringExpulsion(s, target.uid, true, false, previous)
        val shown = held.publicState.players.single { it.uid == target.uid }
        assertTrue(shown.alive); assertNull(held.roleKey(shown)); assertEquals("En partida", ServerGameTablePresentation.condition(shown))
        assertFalse(held.publicState.events.any { it.code == "DAY_EXPULSION" })
        assertFalse(held.publicState.announcement.contains("fue expulsado"))
        assertFalse(s.publicState.players.single { it.uid == target.uid }.alive) // Authoritative state remains intact.
        assertEquals(s.permissions, held.permissions)
        assertTrue(ServerGameActionPolicy.options(held, s.publicState.deadlineMs!! - 1).isEmpty())
        val impacted = ServerGameTablePresentation.visibleDuringExpulsion(s, target.uid, false, false, previous)
        assertEquals(s, impacted)
        assertEquals("policia", impacted.roleKey(target))
    }

    @Test fun `jester public victory waits for its ceremony and adds no private role to public artwork`() {
        val s = snapshots(projection("jester-public-result")).first { it.ownRoleKey == "aldeano" }
        val target = s.publicState.players.single { it.uid == s.publicState.dayEliminationUid }
        assertTrue(target.name in s.publicState.specialWinners)
        assertNull(target.publicRoleKey)
        assertFalse(s.permissions.deadChat) // This living observer has no dead chat.
        val plan = ServerGameTablePresentation.expulsion(s.publicState, s.publicState.events, s.publicState.deadlineMs!! - 12000)!!
        assertTrue(plan.jester); assertEquals(ServerGameTablePresentation.ExpulsionMode.COMPRESSED, plan.mode)
        val held = ServerGameTablePresentation.visibleDuringExpulsion(s, target.uid, true, true, null)
        assertTrue(held.publicState.specialWinners.isEmpty())
        val afterImpact = ServerGameTablePresentation.visibleDuringExpulsion(s, target.uid, false, true, null)
        assertFalse(afterImpact.publicState.players.single { it.uid == target.uid }.alive)
        assertTrue(afterImpact.publicState.specialWinners.isEmpty())
        assertEquals(s, ServerGameTablePresentation.visibleDuringExpulsion(s, target.uid, false, false, null))
    }

    @Test fun `a repeated display name never gives another expelled player the jester ceremony`() {
        val p = snapshots(projection("jester-public-result")).first().publicState
        val jester = p.players.single { it.uid == p.dayEliminationUid }
        val other = p.players.first { it.uid != jester.uid }.copy(name = jester.name, alive = false, deathCause = "VOTE")
        val repeatedName = p.copy(dayEliminationUid = other.uid,
            players = p.players.map { if (it.uid == other.uid) other else it },
            events = p.events.map { if (it.code == "DAY_EXPULSION") it.copy(players = listOf(other.uid)) else it })
        val plan = ServerGameTablePresentation.expulsion(repeatedName, repeatedName.events, p.deadlineMs!! - 8000)!!
        assertEquals(other.uid, plan.player.uid)
        assertFalse(plan.jester)
        assertEquals(ServerGameTablePresentation.ExpulsionMode.FULL, plan.mode)
        assertTrue(ServerGameTablePresentation.expulsion(p, p.events, p.deadlineMs - 12000)!!.jester)
    }

    @Test fun `carry policy leaves ten seconds for current actions and never carries into votes or final`() {
        val p = snapshots(projection("pampa-15-night")).first().publicState
        val now = p.deadlineMs!! - 10000
        for (phase in listOf(ServerGamePhase.NOCHE, ServerGamePhase.DIA_DEBATE, ServerGamePhase.CONTRAPUNTO,
            ServerGamePhase.DESERTOR_RECONSIDERACION)) {
            val next = p.copy(phase = phase)
            assertTrue(ServerGameTablePresentation.canCarryCeremonies(next, now))
            assertFalse(ServerGameTablePresentation.canCarryCeremonies(next, now + 1))
            assertFalse(ServerGameTablePresentation.canCarryCeremonies(next, null))
            assertFalse(ServerGameTablePresentation.canCarryCeremonies(next.copy(winner = "Pueblo"), now))
        }
        for (phase in listOf(ServerGamePhase.VOTACION, ServerGamePhase.DESEMPATE_VOTACION, ServerGamePhase.ALCALDE_DESEMPATE,
            ServerGamePhase.RECUENTO_VOTOS, ServerGamePhase.RESULTADO, ServerGamePhase.FINALIZADA))
            assertFalse(ServerGameTablePresentation.canCarryCeremonies(p.copy(phase = phase), now))
    }

    @Test fun `phase sound is deduplicated by persisted phase cursor and no victim dawn has its own cue`() {
        val p = snapshots(projection("pampa-15-night")).first().publicState
        val tracker = ServerGamePresentationTracker()
        assertTrue(tracker.accept(p).phaseChanged)
        assertEquals(GameSound.NIGHT_FALL, ServerGameTablePresentation.phaseSound(p))
        assertFalse(ServerGamePresentationTracker(tracker.cursor).accept(p).phaseChanged)
        val dawn = p.copy(phase = ServerGamePhase.AMANECER, events = listOf(ServerGameEvent(100, "DAWN_NO_VICTIMS", p.round, emptyList(), "Sin víctimas.")))
        assertNull(ServerGameTablePresentation.phaseSound(dawn))
        assertEquals(GameSound.DAWN, ServerGameTablePresentation.phaseSound(dawn.copy(events = emptyList())))
        assertEquals(GameSound.VOTE_CAST, ServerGameTablePresentation.phaseSound(p.copy(phase = ServerGamePhase.RECUENTO_VOTOS)))
    }

    @Test fun `primary action text carries the usual table's color, not the server label`() {
        fun tone(action: String) = GameplayTableUi.actionToneFor(ServerGameTablePresentation.targetActionLabel(action, "Mateo"))
        assertEquals("MATAR A MATEO", ServerGameTablePresentation.targetActionLabel("matar", "Mateo"))
        assertEquals(GameplayActionTone.KILL, tone("matar"))
        assertEquals(GameplayActionTone.SAVE, tone("salvar"))
        assertEquals(GameplayActionTone.INVESTIGATE, tone("investigar"))
        assertEquals(GameplayActionTone.SILENCE, tone("silenciar"))
        assertEquals(GameplayActionTone.INVOKE, tone("invitar_muerto"))
        assertEquals(GameplayActionTone.DECIDE, tone("votar"))
        assertEquals("EXPULSAR A MATEO", ServerGameTablePresentation.targetActionLabel("decidir_empate", "Mateo"))
        // The intention label from the policy has no tone: it was the cause of the grey kill button.
        assertEquals(GameplayActionTone.DEFAULT, GameplayTableUi.actionToneFor("ELEGIR VÍCTIMA"))
    }

    @Test fun `accepted night powers open the usual private window and day decisions a banner`() {
        val state = snapshots(projection("pampa-15-night")).first { it.ownRoleKey == "asesino" }
        val session = ServerGameSessionAdapter.adapt(GameSession("room", "pampa", "Pampa", emptyList()), state)
        val kill = ServerGameTablePresentation.confirmationFeedback(session, "matar", "Mateo", null, 0)!!
        assertEquals("VÍCTIMA ELEGIDA", kill.title)
        assertTrue(kill.blocksGameplay)
        assertEquals(GameplayActionTone.KILL, kill.tone)
        val own = GameEngine.humanPlayer(session).name
        assertEquals("Te protegiste durante esta noche.",
            ServerGameTablePresentation.confirmationFeedback(session, "salvar", own, null, 0)!!.message)
        assertEquals("PROTECCIÓN REGISTRADA", ServerGameTablePresentation.confirmationFeedback(session, "salvar", "Mateo", null, 0)!!.title)
        val decision = ServerGameTablePresentation.confirmationFeedback(session, "decidir_empate", "Mateo", null, 0)!!
        assertFalse(decision.blocksGameplay)
        assertEquals("Elegiste a Mateo. El Contrapunto empieza ahora.",
            ServerGameTablePresentation.confirmationFeedback(session, "contrapunto", "Mateo", null, 1)!!.message)
        // Votes use the direct-vote notice; the Comisario's answer comes from the private projection.
        assertNull(ServerGameTablePresentation.confirmationFeedback(session, "votar", "Mateo", null, 0))
        assertNull(ServerGameTablePresentation.confirmationFeedback(session, "investigar", "Mateo", null, 0))
        assertNull(ServerGameTablePresentation.confirmationFeedback(session, "role_ack", null, null, 0))
    }

    @Test fun `a night death that ends the match is revealed before the victory, once`() {
        val p = snapshots(projection("final-roles-and-abandonment")).first().publicState
        val victim = p.players.first { !it.alive }
        val death = ServerGameEvent((p.events.maxOfOrNull { it.seq } ?: 0) + 1, "NIGHT_DEATH", p.round, listOf(victim.uid), "${victim.name} murió durante la noche.")
        val ended = p.copy(events = p.events + death, winner = p.winner ?: "Traidores")
        assertEquals(listOf(death), ServerGameTablePresentation.finalRevealEvents(ended, ended.events))
        assertTrue(ServerGameTablePresentation.finalRevealEvents(ended, emptyList()).isEmpty()) // reconnection
        assertTrue(ServerGameTablePresentation.finalRevealEvents(ended.copy(winner = "Cancelada"), ended.events).isEmpty())
        assertTrue(ServerGameTablePresentation.finalRevealEvents(ended.copy(winner = null), ended.events).isEmpty())
        assertTrue(ServerGameTablePresentation.finalRevealEvents(ended, listOf(death.copy(round = p.round - 1))).isEmpty())
    }

    @Test fun `ready to vote and individual ballots parse as aggregates and only after voting`() {
        val night = snapshots(projection("pampa-5-night")).first()
        val debate = night.publicState.copy(phase = ServerGamePhase.DIA_DEBATE,
            readyToVote = ServerReadyToVote(0, 5, night.publicState.deadlineMs!! - 5_000))
        val alive = night.copy(publicState = debate)
        assertTrue(ServerGameActionPolicy.options(alive, debate.readyToVote!!.fromEpochMs - 1).none { it.action == "listo_votar" })
        assertTrue(ServerGameActionPolicy.options(alive, debate.readyToVote!!.fromEpochMs).any { it.action == "listo_votar" })
        val marked = alive.copy(privateState = alive.privateState.copy(confirmed = listOf(ServerGameConfirmedAction("listo_votar", null, null))))
        assertTrue(ServerGameActionPolicy.options(marked, debate.readyToVote!!.fromEpochMs).any { it.action == "cancelar_listo" })
        val recount = fixtures.map { it["projection"] }.map { ServerGameParser.parsePublic((it as Map<*, *>)["public"]) }
            .first { it.ballots.isNotEmpty() }
        assertEquals(ServerGamePhase.RECUENTO_VOTOS, recount.phase)
        assertTrue(recount.ballots.all { (voter, target) -> recount.players.any { it.order == voter } && recount.players.any { it.order == target } })
    }
}
