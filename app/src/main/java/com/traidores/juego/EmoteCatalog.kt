package com.traidores.juego

import android.content.Context

data class EmoteSpec(
    val id: String,
    val emotionKey: String,
    val imageRes: Int,
    val label: String,
    val toneHex: String,
    val themeKey: String,
    val themeLabel: String
)

object EmoteCatalog {
    const val LOADOUT_SIZE = 4
    const val THEME_GREEK = "griego"
    const val THEME_MEDIEVAL_ASSASSIN = "medieval_asesino"
    const val THEME_GAUCHO_DETECTIVE = "gaucho_detective"

    val all = listOf(
        emote(
            id = "griego_enojado",
            emotionKey = "angry",
            imageRes = R.drawable.reaction_angry,
            label = "Enojado",
            toneHex = "#C7442E",
            themeKey = THEME_GREEK,
            themeLabel = "Aldeano griego"
        ),
        emote(
            id = "griego_triste",
            emotionKey = "sad",
            imageRes = R.drawable.reaction_sad,
            label = "Triste",
            toneHex = "#5486B7",
            themeKey = THEME_GREEK,
            themeLabel = "Aldeano griego"
        ),
        emote(
            id = "griego_contento",
            emotionKey = "happy",
            imageRes = R.drawable.reaction_happy,
            label = "Contento",
            toneHex = "#D9A53A",
            themeKey = THEME_GREEK,
            themeLabel = "Aldeano griego"
        ),
        emote(
            id = "griego_sospechoso",
            emotionKey = "suspicious",
            imageRes = R.drawable.reaction_suspicious,
            label = "Sospechoso",
            toneHex = "#8D6B33",
            themeKey = THEME_GREEK,
            themeLabel = "Aldeano griego"
        ),
        emote(
            id = "medieval_enojado",
            emotionKey = "angry",
            imageRes = R.drawable.reaction_assassin_medieval_angry,
            label = "Enojado",
            toneHex = "#C7442E",
            themeKey = THEME_MEDIEVAL_ASSASSIN,
            themeLabel = "Asesino medieval"
        ),
        emote(
            id = "medieval_triste",
            emotionKey = "sad",
            imageRes = R.drawable.reaction_assassin_medieval_sad,
            label = "Triste",
            toneHex = "#5486B7",
            themeKey = THEME_MEDIEVAL_ASSASSIN,
            themeLabel = "Asesino medieval"
        ),
        emote(
            id = "medieval_contento",
            emotionKey = "happy",
            imageRes = R.drawable.reaction_assassin_medieval_happy,
            label = "Contento",
            toneHex = "#D9A53A",
            themeKey = THEME_MEDIEVAL_ASSASSIN,
            themeLabel = "Asesino medieval"
        ),
        emote(
            id = "medieval_sospechoso",
            emotionKey = "suspicious",
            imageRes = R.drawable.reaction_assassin_medieval_suspicious,
            label = "Sospechoso",
            toneHex = "#8D6B33",
            themeKey = THEME_MEDIEVAL_ASSASSIN,
            themeLabel = "Asesino medieval"
        ),
        emote(
            id = "gaucho_enojado",
            emotionKey = "angry",
            imageRes = R.drawable.reaction_detective_gaucho_angry,
            label = "Enojado",
            toneHex = "#C7442E",
            themeKey = THEME_GAUCHO_DETECTIVE,
            themeLabel = "Detective gaucho"
        ),
        emote(
            id = "gaucho_triste",
            emotionKey = "sad",
            imageRes = R.drawable.reaction_detective_gaucho_sad,
            label = "Triste",
            toneHex = "#5486B7",
            themeKey = THEME_GAUCHO_DETECTIVE,
            themeLabel = "Detective gaucho"
        ),
        emote(
            id = "gaucho_contento",
            emotionKey = "happy",
            imageRes = R.drawable.reaction_detective_gaucho_happy,
            label = "Contento",
            toneHex = "#D9A53A",
            themeKey = THEME_GAUCHO_DETECTIVE,
            themeLabel = "Detective gaucho"
        ),
        emote(
            id = "gaucho_sospechoso",
            emotionKey = "suspicious",
            imageRes = R.drawable.reaction_detective_gaucho_suspicious,
            label = "Sospechoso",
            toneHex = "#8D6B33",
            themeKey = THEME_GAUCHO_DETECTIVE,
            themeLabel = "Detective gaucho"
        )
    )

    val defaultLoadoutIds = all
        .filter { it.themeKey == THEME_GREEK }
        .map { it.id }

    private val byId = all.associateBy { it.id }

    fun byId(id: String): EmoteSpec? = byId[id]

    fun byTheme(): Map<String, List<EmoteSpec>> {
        return all.groupBy { it.themeKey }
    }

    private fun emote(
        id: String,
        emotionKey: String,
        imageRes: Int,
        label: String,
        toneHex: String,
        themeKey: String,
        themeLabel: String
    ): EmoteSpec {
        return EmoteSpec(id, emotionKey, imageRes, label, toneHex, themeKey, themeLabel)
    }
}

object EmoteLoadout {
    const val PREF_EMOTE_LOADOUT = "profile_emote_loadout"

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val SEPARATOR = "|"

    fun selectedIds(context: Context): List<String> {
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_EMOTE_LOADOUT, null)
            .orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
        return normalizeIds(stored)
    }

    fun save(context: Context, ids: List<String>) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_EMOTE_LOADOUT, normalizeIds(ids).joinToString(SEPARATOR))
            .apply()
    }

    fun selectedSpecs(context: Context): List<EmoteSpec> {
        return selectedIds(context).mapNotNull(EmoteCatalog::byId)
    }

    fun normalizeIds(ids: List<String>): List<String> {
        val selected = ids
            .filter { EmoteCatalog.byId(it) != null }
            .distinct()
            .take(EmoteCatalog.LOADOUT_SIZE)
            .toMutableList()

        val fillers = (EmoteCatalog.defaultLoadoutIds + EmoteCatalog.all.map { it.id })
            .filterNot { it in selected }

        fillers.forEach { id ->
            if (selected.size < EmoteCatalog.LOADOUT_SIZE) selected += id
        }

        return selected.take(EmoteCatalog.LOADOUT_SIZE)
    }
}
