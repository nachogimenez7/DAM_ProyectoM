package com.traidores.juego

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Un detalle ambiental deliberadamente pequeño: nunca supera doce partículas en pantalla. */
class AmbientParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { PUBLIC, TRAITORS, SPECTATORS, VICTORY }

    private data class Particle(
        val x: Float,
        val y: Float,
        val speed: Float,
        val radius: Float,
        val phase: Float,
        val alpha: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var particles = emptyList<Particle>()
    private var startedAt = 0L
    private var reduced = false
    private var mode = Mode.PUBLIC

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        setWillNotDraw(false)
    }

    fun setMode(value: Mode) {
        if (mode == value && particles.isNotEmpty()) return
        mode = value
        rebuildParticles()
        invalidate()
    }

    fun setReducedMotion(value: Boolean) {
        reduced = value
        visibility = if (value) GONE else VISIBLE
        if (!value) {
            startedAt = System.currentTimeMillis()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildParticles()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startedAt = System.currentTimeMillis()
        if (!reduced) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (reduced || width <= 0 || height <= 0) return
        val seconds = (System.currentTimeMillis() - startedAt) / 1000f
        paint.color = when (mode) {
            Mode.PUBLIC -> Color.parseColor("#E3BA66")
            Mode.TRAITORS -> Color.parseColor("#D35C4F")
            Mode.SPECTATORS -> Color.parseColor("#9AC5EA")
            Mode.VICTORY -> Color.parseColor("#F4D680")
        }
        particles.forEach { particle ->
            val travel = (particle.y + seconds * particle.speed) % 1.12f
            val y = if (mode == Mode.TRAITORS || mode == Mode.VICTORY) {
                height * (1.06f - travel)
            } else {
                height * travel
            }
            val drift = sin(seconds * 0.72f + particle.phase) * width * 0.028f
            paint.alpha = particle.alpha
            canvas.drawCircle(width * particle.x + drift, y, particle.radius, paint)
        }
        postInvalidateOnAnimation()
    }

    private fun rebuildParticles() {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val count = if (mode == Mode.VICTORY) 12 else 9
        particles = List(count) { index ->
            val seed = index + mode.ordinal * 13
            Particle(
                x = 0.08f + ((seed * 37) % 83) / 100f,
                y = ((seed * 29) % 100) / 100f,
                speed = 0.018f + ((seed * 11) % 17) / 1000f,
                radius = density * (0.7f + (seed % 4) * 0.35f),
                phase = seed * 0.91f,
                alpha = 38 + (seed % 4) * 16
            )
        }
        startedAt = System.currentTimeMillis()
    }
}
