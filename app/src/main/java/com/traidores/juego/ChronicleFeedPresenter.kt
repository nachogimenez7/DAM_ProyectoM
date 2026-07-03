package com.traidores.juego

import java.text.Normalizer

enum class ChronicleEntryKind {
    PLAYER,
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
        if (!message.isGod) {
            return ChronicleEntry(
                kind = ChronicleEntryKind.PLAYER,
                round = null,
                text = message.message,
                speaker = message.speaker,
                tone = ChronicleTone.PLAYER
            )
        }

        val text = message.message.trim()
        val lower = normalizeText(text)
        val round = roundFor(text)
        val kind = when {
            "victoria especial" in lower || "bufon" in lower -> ChronicleEntryKind.SPECIAL_VICTORY
            "nadie murio" in lower || "amanec" in lower -> ChronicleEntryKind.DAWN
            "murio" in lower || "asesin" in lower -> ChronicleEntryKind.DEATH
            "silenci" in lower || "mudo" in lower -> ChronicleEntryKind.SILENCE
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
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
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
