package com.traidores.juego

object ProfileRoleCatalog {

    data class Entry(
        val key: String,
        val role: Role
    )

    private data class Selection(
        val profileKey: String,
        val roleKey: String,
        val map: RoleMap
    )

    private val selections = listOf(
        Selection("aldeana", RoleCatalog.ALDEANO, RoleMap.PAMPA),
        Selection("detective", RoleCatalog.POLICIA, RoleMap.PAMPA),
        Selection("medica", RoleCatalog.MEDICO, RoleMap.PAMPA),
        Selection("alcalde", RoleCatalog.ALCALDE, RoleMap.PAMPA),
        Selection("asesino", RoleCatalog.ASESINO, RoleMap.PAMPA),
        Selection("espia", RoleCatalog.ESPIA, RoleMap.PAMPA),
        Selection("mercenario", RoleCatalog.MERCENARIO, RoleMap.PAMPA),
        Selection("desertora", RoleCatalog.DESERTOR, RoleMap.PAMPA),
        Selection("payador", RoleCatalog.PAYADOR, RoleMap.PAMPA),
        Selection("bufon", RoleCatalog.BUFON, RoleMap.MEDIEVAL),
        Selection("oraculo", RoleCatalog.ORACULO, RoleMap.GREECE)
    )

    val entries: List<Entry> = selections.map { selection ->
        Entry(
            key = selection.profileKey,
            role = RoleCatalog.role(selection.roleKey, selection.map)
        )
    }

    fun find(key: String): Entry {
        return entries.firstOrNull { it.key == key } ?: entries.first()
    }
}
