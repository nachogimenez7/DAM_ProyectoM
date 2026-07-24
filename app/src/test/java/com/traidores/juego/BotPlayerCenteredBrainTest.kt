package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPlayerCenteredBrainTest {
    @Test
    fun parserUnderstandsClaimsForEveryAlignment() {
        assertEquals(RoleCatalog.ASESINO, LocalBotAi.roleClaimFrom("soy asesino")?.roleKey)
        assertEquals(RoleCatalog.MERCENARIO, LocalBotAi.roleClaimFrom("me toco mercenario")?.roleKey)
        assertEquals(RoleCatalog.ESPIA, LocalBotAi.roleClaimFrom("la espia soy yo")?.roleKey)
        assertEquals(RoleCatalog.BUFON, LocalBotAi.roleClaimFrom("soy el bufon")?.roleKey)
        assertEquals(RoleCatalog.DESERTOR, LocalBotAi.roleClaimFrom("soy desertora")?.roleKey)
    }

    @Test
    fun sixBotsReceiveSixDifferentPersonalitiesForTheWholeMatch() {
        val session = session(
            extraPlayers = listOf(
                player("Luz", RoleCatalog.ALDEANO),
                player("Tomi", RoleCatalog.ALDEANO),
                player("Cata", RoleCatalog.ALDEANO)
            )
        ).copy(startedAtEpochMs = 1234L)

        val first = BotIdentity.personalityRoster(session)
        val restored = BotIdentity.personalityRoster(session.copy())

        assertEquals(6, first.size)
        assertEquals(6, first.values.toSet().size)
        assertEquals(first, restored)
        val competitiveness = session.players
            .filterNot { it.isHuman }
            .map { competitivenessFor(session, it) }
            .toSet()
        assertTrue(BotCompetitiveness.RELAJADO in competitiveness)
        assertTrue(BotCompetitiveness.EQUILIBRADO in competitiveness)
        assertTrue(BotCompetitiveness.OBSESIVO in competitiveness)
    }

    @Test
    fun jesterRiskComesFromPublicBehaviourAndNotTheSecretRole() {
        val base = session().copy(
            chatHistory = listOf(
                GameChatMessage("Mora", "votenme si se animan, seria un buen expulsado")
            )
        )
        val risk = BotJesterAwareness.read(base, "Mora")
        val warning = BotJesterAwareness.warningLine(
            session = base,
            speaker = GameEngine.playerByName(base, "Beto")!!,
            focusNames = setOf("Mora"),
            responseIndex = 1
        )

        assertTrue(risk.isPlausible)
        assertTrue(risk.reasons.any { "voten" in it })
        assertNotNull(warning)
        assertTrue(warning.orEmpty().contains("bufon"))
        assertFalse(BotJesterAwareness.read(base, "Beto").isPlausible)
    }

    @Test
    fun roleQuestionOffersTruthLieAndRefusalAsOrdinaryMessages() {
        val base = session(humanRole = RoleCatalog.MEDICO).copy(
            chatHistory = listOf(
                GameChatMessage("Beto", "Nacho, que rol sos?")
            )
        )

        val replies = BotQuickReplies.forSession(base)

        assertTrue(replies.any { normalizedForParsing(it.text) == "soy medico" })
        assertTrue(replies.any { it.text.equals("Soy aldeano", ignoreCase = true) })
        assertTrue(replies.any { "No voy a decir" in it.text })
    }

    @Test
    fun detectiveFollowUpLetsThePlayerAdmitTheLie() {
        val base = session().copy(
            claimLedger = mapOf(
                "Nacho" to listOf(
                    ClaimRecord(
                        round = 1,
                        phase = GamePhase.DIA_DEBATE,
                        roleKey = RoleCatalog.POLICIA
                    )
                )
            ),
            chatHistory = listOf(
                GameChatMessage("Beto", "si sos detective, a quien investigaste?")
            )
        )

        val replies = BotQuickReplies.forSession(base)

        assertTrue(replies.any { it.text.startsWith("Investigue a") })
        assertTrue(replies.any { it.text.startsWith("Menti") })
    }

    @Test
    fun extendedQuickChatOnlyOffersAliveTargetsAndRolesActuallyInTheMatch() {
        val base = session().copy(
            players = session().players.map { player ->
                if (player.name == "Mora") player.copy(alive = false) else player
            }
        )

        val targets = BotQuickReplies.aliveTargets(base)
        val roleKeys = BotQuickReplies.rolesInPlay(base).map { it.key }.toSet()

        assertEquals(setOf("Beto", "Dina"), targets.map { it.name }.toSet())
        assertTrue(RoleCatalog.ALDEANO in roleKeys)
        assertTrue(RoleCatalog.ASESINO in roleKeys)
        assertTrue(RoleCatalog.BUFON in roleKeys)
        assertFalse(RoleCatalog.MEDICO in roleKeys)
    }

    @Test
    fun extendedQuickChatCarriesStructuredIntentForTheLocalBrain() {
        val accusation = BotQuickReplies.suspect("Mora")
        val investigation = BotQuickReplies.investigation("Beto", suspicious = true)
        val lie = BotQuickReplies.claimRole(
            RoleCatalog.gameRole(RoleCatalog.ASESINO, RoleMap.MEDIEVAL)
        )

        assertEquals("Sospecho de Mora", accusation.text)
        assertEquals(HumanMessageIntent.ACCUSE, accusation.intentHint)
        assertEquals(HumanMessageIntent.ACTION_CLAIM, investigation.intentHint)
        assertTrue(investigation.text.contains("Beto"))
        assertEquals("Soy asesino", lie.text)
        assertEquals(HumanMessageIntent.ROLE_CLAIM, lie.intentHint)
    }

    @Test
    fun firstQuickRepliesAreBroadSelectorsInsteadOfTargetingOnePlayer() {
        val base = session().copy(
            chatHistory = listOf(
                GameChatMessage("Beto", "Para mi Mora esta rara")
            )
        )
        val replies = BotQuickReplies.forSession(base)

        assertEquals(
            listOf("Sospecho de", "Soy", "Votemos a"),
            replies.map { it.text }
        )
        assertEquals(
            listOf(
                QuickChatAction.CHOOSE_SUSPECT,
                QuickChatAction.CHOOSE_ROLE,
                QuickChatAction.CHOOSE_VOTE
            ),
            replies.map { it.action }
        )
        assertFalse(replies.any { reply -> base.players.any { it.name in reply.text } })
    }

    @Test
    fun dangerousHumanClaimDoesNotMakeTheRealTraitorRevealItself() {
        val base = GameEngine.addHumanChatMessage(
            session(),
            "soy asesino",
            includeBotReactions = false
        )

        val reactions = LocalBotAi.reactionsToHumanMessage(base, "soy asesino")

        assertTrue(reactions.size in 2..4)
        assertTrue(reactions.any { (_, message) -> message.contains("votarte") })
        assertFalse(reactions.any { (_, message) -> message.contains("yo soy asesino") })
    }

    @Test
    fun delayedNudgeUsesTheProfileName() {
        val base = session()

        val beat = BotConversationDirector.playerNudgeBeat(base, lastSpeaker = null)

        assertNotNull(beat)
        assertTrue(beat!!.message.contains("Nacho"))
        assertTrue(beat.promptsSilentHuman)
        assertTrue(BotConversationDirector.silenceDelayMs(base, 0) in 12_000L..17_999L)
    }

    @Test
    fun assignedMatchAnnouncesTheKnownRoleComposition() {
        val assigned = LocalGameFactory.assignRoles(LocalGameFactory.createSession())

        assertTrue(assigned.publicAnnouncement.contains("En juego:"))
        assertTrue(assigned.publicAnnouncement.contains("Aldeano"))
        assertTrue(assigned.publicAnnouncement.contains("identidades siguen ocultas"))
        assertEquals(assigned.publicAnnouncement, assigned.chatHistory.first().message)
    }

    private fun session(
        humanRole: String = RoleCatalog.ALDEANO,
        extraPlayers: List<GamePlayer> = emptyList()
    ): GameSession {
        val map = RoleMap.MEDIEVAL
        return GameSession(
            code = "PLAYER-CENTERED",
            mapKey = "medieval",
            mapName = "Medieval",
            phase = GamePhase.DIA_DEBATE,
            startedAtEpochMs = 99L,
            players = listOf(
                GamePlayer(
                    "Nacho",
                    "N",
                    RoleCatalog.gameRole(humanRole, map),
                    isHuman = true
                ),
                player("Mora", RoleCatalog.ALDEANO),
                player("Beto", RoleCatalog.ASESINO),
                player("Dina", RoleCatalog.BUFON)
            ) + extraPlayers
        )
    }

    private fun player(name: String, roleKey: String): GamePlayer {
        return GamePlayer(
            name,
            name.first().uppercase(),
            RoleCatalog.gameRole(roleKey, RoleMap.MEDIEVAL)
        )
    }
}
