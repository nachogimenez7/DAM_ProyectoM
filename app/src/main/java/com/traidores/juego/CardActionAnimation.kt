package com.traidores.juego

data class CardActionAnimation(
    val startScaleX: Float,
    val startScaleY: Float,
    val startRotation: Float,
    val endRotation: Float,
    val startTranslationXFraction: Float = 0f,
    val startTranslationYFraction: Float = 0f,
    val overshootScale: Float = 1f,
    val durationMs: Long,
    val rotationKeyframes: List<Float> = listOf(startRotation, endRotation)
)

/** Personalidad de movimiento de cada marca; mantiene la UI Android declarativa y testeable. */
object CardActionAnimations {
    fun forRole(roleKey: String, index: Int = 0, count: Int = 1): CardActionAnimation {
        val crossedRotation = if (count > 1 && roleKey in GameRules.killerRoleKeys) {
            if (index == 0) -11f else 11f
        } else {
            0f
        }
        return when (roleKey) {
            RoleCatalog.ASESINO,
            RoleCatalog.ESPIA -> CardActionAnimation(
                startScaleX = 1.28f,
                startScaleY = 1.28f,
                startRotation = crossedRotation - 18f,
                endRotation = crossedRotation,
                startTranslationYFraction = -0.34f,
                overshootScale = 1.06f,
                durationMs = 410L
            )
            RoleCatalog.POLICIA -> CardActionAnimation(
                startScaleX = 0.72f,
                startScaleY = 0.72f,
                startRotation = -22f,
                endRotation = 0f,
                startTranslationXFraction = -0.42f,
                overshootScale = 1.04f,
                durationMs = 560L
            )
            RoleCatalog.MEDICO -> CardActionAnimation(
                startScaleX = 0.48f,
                startScaleY = 0.48f,
                startRotation = -5f,
                endRotation = 0f,
                startTranslationYFraction = 0.22f,
                overshootScale = 1.14f,
                durationMs = 520L
            )
            RoleCatalog.MERCENARIO -> CardActionAnimation(
                startScaleX = 0.16f,
                startScaleY = 0.92f,
                startRotation = 0f,
                endRotation = 0f,
                overshootScale = 1.03f,
                durationMs = 610L
            )
            RoleCatalog.ORACULO -> CardActionAnimation(
                startScaleX = 0.66f,
                startScaleY = 0.32f,
                startRotation = 0f,
                endRotation = 0f,
                startTranslationYFraction = 0.34f,
                overshootScale = 1.08f,
                durationMs = 650L
            )
            RoleCatalog.PAYADOR -> CardActionAnimation(
                startScaleX = 0.88f,
                startScaleY = 0.88f,
                startRotation = -15f,
                endRotation = 0f,
                startTranslationXFraction = -0.08f,
                overshootScale = 1.04f,
                durationMs = 620L,
                rotationKeyframes = listOf(-15f, 9f, -6f, 4f, 0f)
            )
            else -> CardActionAnimation(
                startScaleX = 0.58f,
                startScaleY = 0.58f,
                startRotation = -9f,
                endRotation = 0f,
                overshootScale = 1.08f,
                durationMs = 430L
            )
        }
    }
}
