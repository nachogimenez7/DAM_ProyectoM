package com.traidores.juego

enum class ChronicleEntryKind {
    PLAYER,
    ROLE_COMPOSITION,
    DEATH,
    SILENCE,
    VOTE,
    EXPULSION,
    NIGHT,
    DAWN,
    TIE,
    SPECIAL_VICTORY,
    SYSTEM,
    DAY_DIVIDER
}

enum class ChronicleTone {
    PLAYER,
    DEATH,
    SILENCE,
    VOTE,
    NIGHT,
    DAWN,
    TIE,
    SPECIAL,
    SYSTEM,
    DAY_DIVIDER
}

data class ChronicleEntry(
    val kind: ChronicleEntryKind,
    val round: Int?,
    val text: String,
    val speaker: String?,
    val tone: ChronicleTone
)

object ChronicleFeedPresenter {
    private val roundPattern = Regex("""(?:dia|noche)\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun entries(
        messages: List<GameChatMessage>,
        showOnlyEvents: Boolean = false
    ): List<ChronicleEntry> {
        val visible = if (showOnlyEvents) {
            messages.filter { it.isGod }
        } else {
            messages
        }
        val result = mutableListOf<ChronicleEntry>()
        var lastRound: Int? = null
        visible.map(::entryFor).forEach { entry ->
            if (entry.round != null && entry.round != lastRound) {
                result += dayDivider(entry.round)
                lastRound = entry.round
            }
            result += entry
        }
        return result
    }

    fun entryFor(message: GameChatMessage): ChronicleEntry {
        val explicitRound = message.round.takeIf { it > 0 }
        if (!message.isGod) {
            return ChronicleEntry(
                kind = ChronicleEntryKind.PLAYER,
                round = explicitRound,
                text = message.message,
                speaker = message.speaker,
                tone = ChronicleTone.PLAYER
            )
        }

        val text = message.message.trim()
        val lower = normalizeText(text)
        val round = explicitRound ?: roundFor(text)
        val kind = when {
            "en juego:" in lower && "identidades siguen ocultas" in lower ->
                ChronicleEntryKind.ROLE_COMPOSITION
            "victoria especial" in lower || "bufon" in lower -> ChronicleEntryKind.SPECIAL_VICTORY
            ("murio" in lower || "asesin" in lower) &&
                "no murio" !in lower &&
                "nadie murio" !in lower -> ChronicleEntryKind.DEATH
            "silenci" in lower || "mudo" in lower || "no puede hablar" in lower -> ChronicleEntryKind.SILENCE
            "nadie murio" in lower || "amanec" in lower -> ChronicleEntryKind.DAWN
            "desempate" in lower || "empate" in lower -> ChronicleEntryKind.TIE
            "expuls" in lower -> ChronicleEntryKind.EXPULSION
            "votacion" in lower || "voto" in lower -> ChronicleEntryKind.VOTE
            "noche" in lower || "oscuridad" in lower -> ChronicleEntryKind.NIGHT
            else -> ChronicleEntryKind.SYSTEM
        }
        return ChronicleEntry(
            kind = kind,
            round = round,
            text = text,
            speaker = GameplayFeedMessages.GOD_SPEAKER,
            tone = toneFor(kind)
        )
    }

    private fun roundFor(text: String): Int? {
        return roundPattern.find(normalizeText(text))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun normalizeText(text: String): String {
        return GameplayTextMarkers.normalize(text)
    }

    private fun dayDivider(round: Int): ChronicleEntry {
        return ChronicleEntry(
            kind = ChronicleEntryKind.DAY_DIVIDER,
            round = round,
            text = "DIA $round",
            speaker = null,
            tone = ChronicleTone.DAY_DIVIDER
        )
    }

    private fun toneFor(kind: ChronicleEntryKind): ChronicleTone {
        return when (kind) {
            ChronicleEntryKind.PLAYER -> ChronicleTone.PLAYER
            ChronicleEntryKind.ROLE_COMPOSITION -> ChronicleTone.SYSTEM
            ChronicleEntryKind.DEATH -> ChronicleTone.DEATH
            ChronicleEntryKind.SILENCE -> ChronicleTone.SILENCE
            ChronicleEntryKind.VOTE,
            ChronicleEntryKind.EXPULSION -> ChronicleTone.VOTE
            ChronicleEntryKind.NIGHT -> ChronicleTone.NIGHT
            ChronicleEntryKind.DAWN -> ChronicleTone.DAWN
            ChronicleEntryKind.TIE -> ChronicleTone.TIE
            ChronicleEntryKind.SPECIAL_VICTORY -> ChronicleTone.SPECIAL
            ChronicleEntryKind.SYSTEM -> ChronicleTone.SYSTEM
            ChronicleEntryKind.DAY_DIVIDER -> ChronicleTone.DAY_DIVIDER
        }
    }
}
