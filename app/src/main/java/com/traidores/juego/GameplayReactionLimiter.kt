package com.traidores.juego

internal class GameplayReactionLimiter(
    private val maxPerRound: Int = DEFAULT_MAX_PER_ROUND,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    private val usesByPlayerRound = mutableMapOf<PlayerRoundKey, Int>()
    private val cooldownUntilByPlayer = mutableMapOf<String, Long>()

    fun check(playerName: String, round: Int, nowMs: Long): ReactionCheck {
        val safeRound = round.coerceAtLeast(1)
        val key = PlayerRoundKey(playerName, safeRound)
        val used = usesByPlayerRound[key] ?: 0
        if (used >= maxPerRound) {
            return ReactionCheck(
                allowed = false,
                reason = ReactionBlockReason.ROUND_LIMIT,
                remainingUses = 0
            )
        }

        val cooldownUntil = cooldownUntilByPlayer[playerName] ?: 0L
        if (cooldownUntil > nowMs) {
            return ReactionCheck(
                allowed = false,
                reason = ReactionBlockReason.COOLDOWN,
                remainingCooldownMs = cooldownUntil - nowMs,
                remainingUses = maxPerRound - used
            )
        }

        return ReactionCheck(
            allowed = true,
            reason = ReactionBlockReason.NONE,
            remainingUses = maxPerRound - used
        )
    }

    fun record(playerName: String, round: Int, nowMs: Long): ReactionCheck {
        val check = check(playerName, round, nowMs)
        if (!check.allowed) return check

        val safeRound = round.coerceAtLeast(1)
        val key = PlayerRoundKey(playerName, safeRound)
        val used = (usesByPlayerRound[key] ?: 0) + 1
        usesByPlayerRound[key] = used
        cooldownUntilByPlayer[playerName] = nowMs + cooldownMs

        return ReactionCheck(
            allowed = true,
            reason = ReactionBlockReason.NONE,
            remainingUses = (maxPerRound - used).coerceAtLeast(0)
        )
    }

    fun resetOutsideRound(round: Int) {
        val safeRound = round.coerceAtLeast(1)
        usesByPlayerRound.keys
            .filterNot { it.round == safeRound }
            .forEach { usesByPlayerRound.remove(it) }
    }

    private data class PlayerRoundKey(
        val playerName: String,
        val round: Int
    )

    companion object {
        const val DEFAULT_MAX_PER_ROUND = 2
        const val DEFAULT_COOLDOWN_MS = 10_000L
    }
}

internal data class ReactionCheck(
    val allowed: Boolean,
    val reason: ReactionBlockReason,
    val remainingCooldownMs: Long = 0L,
    val remainingUses: Int = 0
)

internal enum class ReactionBlockReason {
    NONE,
    COOLDOWN,
    ROUND_LIMIT
}
