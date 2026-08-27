package com.traidores.juego

import android.content.Context

/** Nombres locales elegidos para cada integrante fijo del plantel de bots. */
object LocalBotNameStore {
    const val MAX_NAME_LENGTH = 18

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_BOT_NAME_PREFIX = "local_bot_name_"

    fun apply(context: Context, session: GameSession): GameSession {
        val usedSlots = mutableSetOf<Int>()
        val humanNames = session.players
            .filter(GamePlayer::isHuman)
            .mapTo(mutableSetOf()) { it.name.lowercase() }
        val usedDisplayNames = humanNames.toMutableSet()
        val updatedProfiles = linkedMapOf<String, PlayerProfile>()

        val updatedPlayers = session.players.map { player ->
            if (player.isHuman) {
                session.playerProfiles[player.name]?.let { updatedProfiles[player.name] = it }
                return@map player
            }

            val slot = resolveSlot(context, player.name, usedSlots)
            if (slot == null) {
                session.playerProfiles[player.name]?.let { updatedProfiles[player.name] = it }
                return@map player
            }
            usedSlots += slot

            val defaultName = LocalGameFactory.defaultBotName(slot) ?: player.name
            val preferredName = displayName(context, slot)
            val finalName = preferredName.takeUnless {
                it.lowercase() in usedDisplayNames
            } ?: defaultName
            usedDisplayNames += finalName.lowercase()

            val previousProfile = session.playerProfiles[player.name]
                ?: session.playerProfiles[defaultName]
                ?: BotProfileFactory.profileFor(defaultName)
            updatedProfiles[finalName] = previousProfile.copy(name = finalName)
            player.copy(
                name = finalName,
                initial = initialFor(finalName)
            )
        }

        return session.copy(
            players = updatedPlayers,
            playerProfiles = updatedProfiles
        )
    }

    fun slotForPlayer(context: Context, session: GameSession, playerIndex: Int): Int? {
        val target = session.players.getOrNull(playerIndex)?.takeUnless(GamePlayer::isHuman)
            ?: return null
        val usedSlots = mutableSetOf<Int>()
        session.players.forEachIndexed { index, player ->
            if (player.isHuman) return@forEachIndexed
            val slot = resolveSlot(context, player.name, usedSlots) ?: return@forEachIndexed
            if (index == playerIndex || player === target) return slot
            usedSlots += slot
        }
        return null
    }

    fun nextAvailableName(context: Context, session: GameSession): String? {
        val occupied = session.players.mapTo(mutableSetOf()) { it.name.lowercase() }
        return LocalGameFactory.botSlots()
            .asSequence()
            .map { slot -> displayName(context, slot) }
            .firstOrNull { candidate -> candidate.lowercase() !in occupied }
    }

    fun save(context: Context, slot: Int, name: String) {
        val normalized = normalize(name)
        val defaultName = LocalGameFactory.defaultBotName(slot).orEmpty()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (normalized.equals(defaultName, ignoreCase = true)) {
                    remove(keyFor(slot))
                } else {
                    putString(keyFor(slot), normalized)
                }
            }
            .apply()
    }

    fun validationError(session: GameSession, playerIndex: Int, rawName: String): String? {
        val name = normalize(rawName)
        if (name.length < 2) return "Escribí al menos 2 caracteres."
        if (name.length > MAX_NAME_LENGTH) return "Usá hasta $MAX_NAME_LENGTH caracteres."
        if (!name.matches(Regex("[\\p{L}\\p{N} ]+"))) {
            return "Usá solamente letras, números y espacios."
        }
        if (name.equals("Vos", ignoreCase = true)) return "Ese nombre está reservado."
        val duplicate = session.players.withIndex().any { (index, player) ->
            index != playerIndex && player.name.equals(name, ignoreCase = true)
        }
        return if (duplicate) "Ya hay un jugador con ese nombre." else null
    }

    fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_NAME_LENGTH)

    private fun displayName(context: Context, slot: Int): String {
        val defaultName = LocalGameFactory.defaultBotName(slot).orEmpty()
        return normalize(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(keyFor(slot), defaultName)
                .orEmpty()
        ).ifBlank { defaultName }
    }

    private fun resolveSlot(
        context: Context,
        currentName: String,
        usedSlots: Set<Int>
    ): Int? {
        return LocalGameFactory.botSlots().firstOrNull { slot ->
            slot !in usedSlots && (
                currentName.equals(displayName(context, slot), ignoreCase = true) ||
                    currentName.equals(LocalGameFactory.defaultBotName(slot), ignoreCase = true)
                )
        } ?: LocalGameFactory.botSlots().firstOrNull { it !in usedSlots }
    }

    private fun keyFor(slot: Int): String = "$PREF_BOT_NAME_PREFIX$slot"

    private fun initialFor(name: String): String =
        name.firstOrNull()?.uppercase() ?: "?"
}
