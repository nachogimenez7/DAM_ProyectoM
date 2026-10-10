package com.traidores.juego

import android.content.Intent
import android.os.Bundle

/** Directed visual fixture only in Debug; never authenticates or writes a real room. */
class GameplayParityActivity : BaseActivity() {
    private val sharedWindowInsets = GameplayWindowInsets()
    override fun onSystemBarInsetsChanged(safeArea: androidx.core.graphics.Insets) {
        sharedWindowInsets.apply(this, safeArea)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phase = ServerGamePhase.valueOf(intent.getStringExtra("phase") ?: "DIA_DEBATE")
        val names = listOf("Nacho", "Thiago", "Lautaro", "Valen", "Mora", "Gael", "Dante", "Alma", "Bruno", "Cata", "Dario", "Emi", "Fede", "Gise", "Hugo").take(intent.getIntExtra("count", 8))
        val expulsion = intent.getBooleanExtra("expulsion", false)
        val victory = phase == ServerGamePhase.FINALIZADA
        val players = names.mapIndexed { i, name -> ServerGamePlayer("visual-$i", i, name, "", !(expulsion && i == 1), false, if (expulsion && i == 1) "VOTE" else "NONE", if (victory || expulsion && i == 1) (if (i == 0) "medico" else if (i == 1) "asesino" else "aldeano") else null) }
        val index = phase.ordinal + 20
        val state = ServerGameSnapshot(
            ServerGamePublic("visual-parity", phase, index, 1, 2, System.currentTimeMillis() + 180000,
                "Amanecer: esta vez nadie murió.", if (victory) "Pueblo" else null, players,
                listOf(if (expulsion) ServerGameEvent(1, "DAY_EXPULSION", 2, listOf("visual-1"), "Thiago fue expulsado.")
                    else ServerGameEvent(1, "DAWN_NO_VICTIMS", 2, emptyList(), "Amanecer: esta vez nadie murió.")),
                null, emptyList(), emptyList(), null, null, emptyList(), dayEliminationUid = if (expulsion) "visual-1" else null),
            ServerGamePrivate("visual-parity", index, 1, if (victory) players.associate { it.order to it.publicRoleKey!! } else mapOf(0 to "medico"), emptyList(), emptySet(), null, false, false, false, emptyList()),
            ServerGamePermissions("visual-parity", index, true, phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.VOTACION), false, false, reactions = phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.VOTACION, ServerGamePhase.RECUENTO_VOTOS)), "visual-0")
        val models = players.map { ServerGameTablePresentation.player(state, it, RoleMap.PAMPA) }
        val messages = listOf(GameChatMessage(GameplayFeedMessages.GOD_SPEAKER, state.publicState.announcement, isGod = true, round = 2),
            GameChatMessage("Mora", "Yo no vi nada extraño anoche.", round = 2), GameChatMessage("Valen", "Conversemos antes de votar.", round = 2))
        val localPhase = when (phase) {
            ServerGamePhase.NOCHE -> GamePhase.NOCHE_MEDICO
            ServerGamePhase.AMANECER -> GamePhase.AMANECER
            ServerGamePhase.VOTACION -> GamePhase.VOTACION
            ServerGamePhase.RECUENTO_VOTOS -> GamePhase.RECUENTO_VOTOS
            ServerGamePhase.RESULTADO, ServerGamePhase.FINALIZADA -> GamePhase.RESULTADO
            else -> GamePhase.DIA_DEBATE
        }
        val session = GameSession("VISUAL", "pampa", "Pampa", models, phase = localPhase, phaseIndex = index, round = 2,
            winner = if (victory) "Pueblo" else "", dayEliminationTarget = if (expulsion) names[1] else "", revealRolesOnDeath = true, showIndividualVotes = false,
            publicAnnouncement = state.publicState.announcement, chatHistory = messages, onlineMatchId = "visual-parity",
            onlinePhaseDeadlineEpochMs = state.publicState.deadlineMs!!, onlinePhaseDeadlinePhaseIndex = index)
        if (intent.getBooleanExtra("common", false)) {
            startActivity(Intent(this, GameplayMockActivity::class.java).putExtra(LobbyActivity.EXTRA_SESSION, session)
                .putExtra("extra_debug_chat_preview", "visual-parity").putExtra("extra_debug_parity_hold", true))
            finish(); return
        }
        val table = ServerGameTableRenderer(this, session, { _, _, _ -> false }, {}, {}, { _, _, complete -> complete(false) }, {}, {}, {}, {}, System::currentTimeMillis)
        table.bind()
        table.render(state, ServerGamePresentationUpdate(expulsion, if (expulsion) state.publicState.events else emptyList()))
        table.controls(ServerGameActionPolicy.options(state, System.currentTimeMillis()), true, false, false, false, false, false, "publico")
        table.messages(listOf(ServerChatMessage("1", "visual-4", "Yo no vi nada extraño anoche.", 1),
            ServerChatMessage("2", "visual-3", "Conversemos antes de votar.", 2)), "publico")
        table.setCountdown(180, false)
        if (intent.getBooleanExtra("chat", false)) table.setChatOpen(true)
    }
}
