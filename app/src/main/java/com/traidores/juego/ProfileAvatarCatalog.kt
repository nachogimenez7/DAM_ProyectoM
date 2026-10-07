package com.traidores.juego

/** Public identity only: these animals never reveal the player's secret role. */
object ProfileAvatarCatalog {
    val keys = listOf("carpincho", "buho", "cuervo", "lobo", "mamona", "liebre", "puma",
        "zorzal", "calandria", "hornero", "zorro", "yaguarete", "nandu", "yacare", "border_collie").map { "avatar_$it" }
    private val labels = listOf("Carpincho", "Búho", "Cuervo", "Lobo", "Mamona", "Liebre", "Puma",
        "Zorzal", "Calandria", "Hornero", "Zorro", "Yaguareté", "Ñandú", "Yacaré", "Border collie")
    val entries: List<ProfileRoleCatalog.Entry> = keys.mapIndexed { index, key ->
        ProfileRoleCatalog.Entry(key, Role(labels[index], "Pampa", "", "", "", key), RoleMap.PAMPA, 0.5f)
    }

    /** Keep the original 14-way legacy migration stable when new animals are added. */
    fun normalize(key: String): String = key.takeIf { it in keys }
        ?: keys[((key.fold(0L) { hash, char -> (hash * 31 + char.code) % 2147483647 }) % 14).toInt()]
    fun find(key: String): ProfileRoleCatalog.Entry = entries.first { it.key == normalize(key) }
    fun forBotSlot(slot: Int): String = keys[Math.floorMod(slot, keys.size)]

    fun getOrCreate(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("TraidoresPrefs", android.content.Context.MODE_PRIVATE)
        val stored = prefs.getString("profile_avatar", null)
        val selected = if (stored.isNullOrBlank()) keys.random() else normalize(stored)
        if (selected != stored) prefs.edit().putString("profile_avatar", selected).apply()
        return selected
    }
}
