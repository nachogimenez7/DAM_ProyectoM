package com.traidores.juego

/** Reuses the room roster already observed by every member; no extra network lookup. */
internal object OnlineMatchProfileResolver {
    fun attach(match: GameSession, profilesByUid: Map<String, PlayerProfile>): GameSession {
        val profiles = match.players.mapIndexedNotNull { index, player ->
            val uid = match.onlinePlayerUids.getOrNull(index) ?: return@mapIndexedNotNull null
            val profile = profilesByUid[uid] ?: return@mapIndexedNotNull null
            player.name to profile.copy(name = player.name)
        }.toMap()
        return match.copy(playerProfiles = match.playerProfiles + profiles)
    }
}
