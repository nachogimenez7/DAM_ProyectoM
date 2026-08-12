package com.traidores.juego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardActionAnimationTest {

    @Test
    fun everyImplementedRoleHasARecognizableMotionSignature() {
        val roles = listOf(
            RoleCatalog.ASESINO,
            RoleCatalog.ESPIA,
            RoleCatalog.POLICIA,
            RoleCatalog.MEDICO,
            RoleCatalog.MERCENARIO,
            RoleCatalog.ORACULO,
            RoleCatalog.PAYADOR
        )
        val animations = roles.map { role -> CardActionAnimations.forRole(role) }
        val signatures = animations.map { it.signature() }

        assertEquals(6, signatures.toSet().size)
        assertEquals(signatures[0], signatures[1]) // misma daga, mismo lenguaje de movimiento
        assertTrue(animations.all { it.durationMs in 350L..700L })
    }

    @Test
    fun twoKillerMarksFinishCrossedInsteadOfStacked() {
        val first = CardActionAnimations.forRole(RoleCatalog.ASESINO, index = 0, count = 2)
        val second = CardActionAnimations.forRole(RoleCatalog.ESPIA, index = 1, count = 2)

        assertEquals(-11f, first.endRotation)
        assertEquals(11f, second.endRotation)
        assertNotEquals(first.endRotation, second.endRotation)
    }

    @Test
    fun payadorStrumActuallyOscillates() {
        val payador = CardActionAnimations.forRole(RoleCatalog.PAYADOR)

        assertTrue(payador.rotationKeyframes.size >= 5)
        assertTrue(payador.rotationKeyframes.zipWithNext().any { (a, b) -> a < 0f && b > 0f })
        assertEquals(0f, payador.rotationKeyframes.last())
    }

    private fun CardActionAnimation.signature() = listOf(
        startScaleX,
        startScaleY,
        startRotation,
        startTranslationXFraction,
        startTranslationYFraction,
        durationMs
    ).joinToString("|")
}
