package com.traidores.juego

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialViewAnimationTest {
    @Test
    fun zeroOrInvalidNegativeScaleRequiresIndependentPresentation() {
        assertTrue(EssentialViewAnimation.requiresFallback(0f))
        assertTrue(EssentialViewAnimation.requiresFallback(-1f))
    }

    @Test
    fun enabledAnimatorScaleKeepsNativeAnimation() {
        assertFalse(EssentialViewAnimation.requiresFallback(0.5f))
        assertFalse(EssentialViewAnimation.requiresFallback(1f))
        assertFalse(EssentialViewAnimation.requiresFallback(10f))
    }
}
