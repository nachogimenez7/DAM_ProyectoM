package com.traidores.juego

internal object OnlineMatchReturnGate {
    const val FALLBACK_MS = 45_000L
    const val HOST_REQUEST_GRACE_MS = 2_000L

    data class Progress(
        val readyCount: Int,
        val totalCount: Int,
        val requiredCount: Int,
        val allRequiredReady: Boolean
    )

    fun initialDeadline(existingDeadlineEpochMs: Long, nowEpochMs: Long): Long {
        return existingDeadlineEpochMs.takeIf { it > 0L } ?: (nowEpochMs + FALLBACK_MS)
    }

    fun remainingMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long {
        return (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
    }

    fun progress(
        expectedPlayerIds: Collection<String>,
        connectedPlayerIds: Collection<String>,
        acknowledgedPlayerIds: Collection<String>,
        presenceKnown: Boolean
    ): Progress {
        val expected = expectedPlayerIds.filter { it.isNotBlank() }.toSet()
        val acknowledged = acknowledgedPlayerIds.toSet().intersect(expected)
        val connected = connectedPlayerIds.toSet().intersect(expected)
        val required = if (presenceKnown) connected else expected
        return Progress(
            readyCount = acknowledged.size,
            totalCount = expected.size,
            requiredCount = required.size,
            allRequiredReady = required.isNotEmpty() && required.all(acknowledged::contains)
        )
    }
}
