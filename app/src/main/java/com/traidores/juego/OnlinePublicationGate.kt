package com.traidores.juego

/** A coordinator must not present a new phase before its publication has succeeded. */
internal class OnlinePublicationGate {
    private var confirmedKey = ""

    fun key(session: GameSession, presentation: String = ""): String =
        "${session.onlineMatchId}|${session.phaseIndex}|${session.phase}|${session.round}|${session.winner}|${session.dayEliminationTarget}|${session.nightKillTarget}|${session.publicAnnouncement}|$presentation"

    fun isPending(session: GameSession, presentation: String = ""): Boolean = confirmedKey != key(session, presentation)

    fun confirm(publishedKey: String, current: GameSession, presentation: String = "") {
        if (publishedKey == key(current, presentation)) confirmedKey = publishedKey
    }
}
