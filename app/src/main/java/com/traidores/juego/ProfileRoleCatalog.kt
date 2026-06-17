package com.traidores.juego

object ProfileRoleCatalog {

    data class Entry(
        val key: String,
        val role: Role,
        val map: RoleMap
    )

    private val mapOrder = listOf(RoleMap.PAMPA, RoleMap.GREECE, RoleMap.MEDIEVAL)

    private val baseRoleKeys = listOf(
        RoleCatalog.ALDEANO,
        RoleCatalog.POLICIA,
        RoleCatalog.MEDICO,
        RoleCatalog.ALCALDE,
        RoleCatalog.ASESINO,
        RoleCatalog.ESPIA,
        RoleCatalog.MERCENARIO,
        RoleCatalog.DESERTOR
    )

    private val legacyKeys = mapOf(
        "aldeana" to profileKey(RoleMap.PAMPA, RoleCatalog.ALDEANO),
        "detective" to profileKey(RoleMap.PAMPA, RoleCatalog.POLICIA),
        "medica" to profileKey(RoleMap.PAMPA, RoleCatalog.MEDICO),
        "alcalde" to profileKey(RoleMap.PAMPA, RoleCatalog.ALCALDE),
        "asesino" to profileKey(RoleMap.PAMPA, RoleCatalog.ASESINO),
        "espia" to profileKey(RoleMap.PAMPA, RoleCatalog.ESPIA),
        "mercenario" to profileKey(RoleMap.PAMPA, RoleCatalog.MERCENARIO),
        "desertora" to profileKey(RoleMap.PAMPA, RoleCatalog.DESERTOR),
        "payador" to profileKey(RoleMap.PAMPA, RoleCatalog.PAYADOR),
        "bufon" to profileKey(RoleMap.MEDIEVAL, RoleCatalog.BUFON),
        "oraculo" to profileKey(RoleMap.GREECE, RoleCatalog.ORACULO)
    )

    val entries: List<Entry> = mapOrder.flatMap(::entriesForMap)

    fun entriesForMap(map: RoleMap): List<Entry> {
        return roleKeysForMap(map).map { roleKey ->
            Entry(
                key = profileKey(map, roleKey),
                role = RoleCatalog.role(roleKey, map),
                map = map
            )
        }
    }

    fun find(key: String): Entry {
        val normalizedKey = legacyKeys[key] ?: key
        return entries.firstOrNull { it.key == normalizedKey } ?: entries.first()
    }

    private fun roleKeysForMap(map: RoleMap): List<String> {
        return baseRoleKeys + when (map) {
            RoleMap.PAMPA -> RoleCatalog.PAYADOR
            RoleMap.GREECE -> RoleCatalog.ORACULO
            RoleMap.MEDIEVAL -> RoleCatalog.BUFON
        }
    }

    private fun profileKey(map: RoleMap, roleKey: String): String {
        return "${map.sessionKey}_$roleKey"
    }
}
