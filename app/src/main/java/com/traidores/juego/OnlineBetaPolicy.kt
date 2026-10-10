package com.traidores.juego

/** Missing/invalid rollout configuration closes new rooms; it never downgrades protocol. */
internal object OnlineBetaPolicy {
    enum class Access { OPEN, MAINTENANCE, UPDATE }
    fun access(enabled: Boolean, minimumVersion: Long?, version: Int,
               uid: String? = null, allowedUids: Any? = null): Access = when {
        minimumVersion == null || minimumVersion < 1 -> Access.MAINTENANCE
        version < minimumVersion -> Access.UPDATE
        !enabled -> Access.MAINTENANCE
        allowedUids != null && (allowedUids !is List<*> || allowedUids.isEmpty() ||
            allowedUids.any { it !is String || it.isBlank() } || uid.isNullOrBlank() ||
            uid !in allowedUids) -> Access.MAINTENANCE
        else -> Access.OPEN
    }
    fun message(access: Access) = when (access) {
        Access.OPEN -> ""; Access.MAINTENANCE -> "Online en mantenimiento."
        Access.UPDATE -> "Actualizá la app para jugar en línea."
    }
}
