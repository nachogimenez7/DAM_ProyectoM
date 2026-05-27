package com.traidores.juego

sealed class RoleListItem {
    data class MapCard(val map: MapInfo) : RoleListItem()
    data class SectionHeader(val title: String) : RoleListItem()
    data class RoleCard(val role: Role) : RoleListItem()
}
