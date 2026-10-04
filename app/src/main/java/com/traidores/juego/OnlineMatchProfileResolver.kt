package com.traidores.juego

/** Reuses the room roster already observed by every member; no extra network lookup. */
internal object OnlineMatchProfileResolver {
    fun attach(
        match: GameSession,
        profilesByUid: Map<String, PlayerProfile>,
        canArbitrateByUid: Map<String, Boolean> = emptyMap()
    ): GameSession {
        val profiles = match.players.mapIndexedNotNull { index, player ->
            val uid = match.onlinePlayerUids.getOrNull(index) ?: return@mapIndexedNotNull null
            val profile = profilesByUid[uid] ?: return@mapIndexedNotNull null
            player.name to profile.copy(name = player.name)
        }.toMap()
        val blockedUids = match.onlinePlayerUids.filter { uid ->
            canArbitrateByUid[uid]?.not() ?: (uid in match.onlineNonAuthorityPlayerUids)
        }
        return match.copy(
            playerProfiles = match.playerProfiles + profiles,
            onlineNonAuthorityPlayerUids = blockedUids
        )
    }
}
