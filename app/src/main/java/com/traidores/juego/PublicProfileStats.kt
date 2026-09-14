package com.traidores.juego

/** Informational device history shared by its owner, never a competitive ranking. */
internal object PublicProfileStats {
    fun fromMap(data: Map<*, *>?): PlayerStats {
        val matches = (data?.get("partidas") as? Number)?.toLong()
        val wins = (data?.get("victorias") as? Number)?.toLong()
        if (matches == null || wins == null || matches !in 0L..1_000_000L || wins !in 0L..matches) {
            return PlayerStats(0, 0, false)
        }
        return PlayerStats(matches.toInt(), wins.toInt(), true)
    }
}
