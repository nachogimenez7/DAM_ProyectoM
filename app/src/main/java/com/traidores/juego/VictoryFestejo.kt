package com.traidores.juego

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Celebration (win) or defeat (lose) sticker each winner-screen card shows for the player's equipped style. */
internal enum class FestejoKind { RISE, SOB, FLEX, RAGE, TWINKLE, DIZZY, STAMP, STAMP_CRACK }

internal object VictoryFestejo {
    const val STYLE_SELLO = CosmeticPilot.THEME_SELLO
    private const val DEBUG_PREFS = "traidores_debug"
    private const val DEBUG_KEY = "festejo_preview"
    private val ALL_STYLES = listOf(CosmeticPilot.THEME_SEA, CosmeticPilot.THEME_FIRE, CosmeticPilot.THEME_SPACE, STYLE_SELLO)

    fun drawableFor(style: String, won: Boolean): Int? = when (style) {
        CosmeticPilot.THEME_SEA -> if (won) R.drawable.festejo_mar_gana else R.drawable.festejo_mar_pierde
        CosmeticPilot.THEME_FIRE -> if (won) R.drawable.festejo_fuego_gana else R.drawable.festejo_fuego_pierde
        CosmeticPilot.THEME_SPACE -> if (won) R.drawable.festejo_espacio_gana else R.drawable.festejo_espacio_pierde
        STYLE_SELLO -> if (won) R.drawable.festejo_sello_gana else R.drawable.festejo_sello_pierde
        else -> null
    }

    fun kindFor(style: String, won: Boolean): FestejoKind? = when (style) {
        CosmeticPilot.THEME_SEA -> if (won) FestejoKind.RISE else FestejoKind.SOB
        CosmeticPilot.THEME_FIRE -> if (won) FestejoKind.FLEX else FestejoKind.RAGE
        CosmeticPilot.THEME_SPACE -> if (won) FestejoKind.TWINKLE else FestejoKind.DIZZY
        STYLE_SELLO -> if (won) FestejoKind.STAMP else FestejoKind.STAMP_CRACK
        else -> null
    }

    /**
     * Debug builds only: `festejo_preview` in the `traidores_debug` prefs forces a style on every card
     * ("sea", "fire", "space", "sello") or cycles through all of them ("all"). Set it with adb run-as.
     */
    /** Debug builds only: `festejo_preview_lose` = true shows the defeat festejo on every card. */
    fun forcedLose(context: Context): Boolean = BuildConfig.DEBUG &&
        context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE).getBoolean("festejo_preview_lose", false)

    fun styleFor(context: Context, equipped: String, cardIndex: Int): String {
        if (BuildConfig.DEBUG) {
            val forced = context.getSharedPreferences(DEBUG_PREFS, Context.MODE_PRIVATE).getString(DEBUG_KEY, null)
            if (forced == "all") return ALL_STYLES[cardIndex % ALL_STYLES.size]
            if (forced != null && forced in ALL_STYLES) return forced
        }
        return equipped
    }
}

/** What a winner-screen card needs to play its festejo. */
internal class FestejoTarget(
    val sticker: ImageView,
    val card: View,
    val particles: FestejoParticlesView,
    val kind: FestejoKind
)

internal class FestejoParticlesView(context: Context, private val kind: FestejoKind) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(kind.ordinal * 31 + 7)
    private val seeds = List(16) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init { visibility = INVISIBLE; isClickable = false; isFocusable = false }

    fun play(durationMs: Long) {
        animator?.cancel()
        visibility = VISIBLE
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { visibility = INVISIBLE }
            })
            start()
        }
    }

    fun stop() { animator?.cancel(); visibility = INVISIBLE }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val r = w / 26f
        seeds.forEachIndexed { i, s ->
            val t = ((progress * 1.4f) - s[2] * 0.4f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEachIndexed
            val fade = sin(t * PI).toFloat()
            val x = w * (0.08f + 0.84f * s[0])
            when (kind) {
                FestejoKind.SOB -> {
                    paint.color = Color.argb((200 * fade).toInt(), 140, 200, 255)
                    canvas.drawCircle(x, h * (0.25f + 0.65f * t), r * 1.2f, paint)
                }
                FestejoKind.FLEX, FestejoKind.RAGE -> {
                    val rage = kind == FestejoKind.RAGE
                    paint.color = if (rage) Color.argb((210 * fade).toInt(), 255, 70, 30)
                    else Color.argb((220 * fade).toInt(), 255, (150 + 80 * s[1]).toInt(), 40)
                    canvas.drawCircle(x, h * (0.95f - 0.8f * t), r * (1.6f - t), paint)
                }
                FestejoKind.TWINKLE, FestejoKind.DIZZY -> {
                    paint.color = Color.argb((230 * fade).toInt(), 255, 245, 190)
                    val y = h * (0.1f + 0.8f * s[1])
                    canvas.drawCircle(x, y, r * (0.6f + fade), paint)
                    if (i % 3 == 0) {
                        canvas.drawRect(x - r * 2f * fade, y - 1f, x + r * 2f * fade, y + 1f, paint)
                        canvas.drawRect(x - 1f, y - r * 2f * fade, x + 1f, y + r * 2f * fade, paint)
                    }
                }
                FestejoKind.STAMP -> {
                    paint.color = Color.argb((230 * fade).toInt(), 255, 215, 90)
                    val angle = (s[0] * 2f * PI).toFloat()
                    val dist = w * 0.15f + w * 0.3f * t
                    canvas.drawCircle(w / 2f + dist * kotlin.math.cos(angle), h * 0.65f + dist * sin(angle), r * (1.2f - t * 0.6f), paint)
                }
                else -> Unit
            }
        }
    }
}

/** Violet wash with little stars: how the card of a Space-style player is left once the festejo ends. */
internal class StarryVioletDrawable : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Random(11).let { r -> List(14) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 0.6f) } }
    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.color = Color.argb(96, 118, 60, 230)
        canvas.drawRect(b, paint)
        val unit = b.width() / 60f
        stars.forEach { s ->
            val x = b.left + b.width() * s[0]
            val y = b.top + b.height() * s[1]
            val r = unit * (0.7f + s[2] * 1.3f)
            paint.color = Color.argb((120 + 120 * s[2]).toInt(), 255, 244, 200)
            canvas.drawCircle(x, y, r * 0.55f, paint)
            canvas.drawRect(x - r * 1.6f, y - 0.8f, x + r * 1.6f, y + 0.8f, paint)
            canvas.drawRect(x - 0.8f, y - r * 1.6f, x + 0.8f, y + r * 1.6f, paint)
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

internal object FestejoPlayer {
    private const val STAGGER_MS = 300L
    private const val PARTICLE_MS = 2_600L

    /** Final pose: sticker visible and settled; sad-sea cards keep their blue tint, the cracked seal stays. */
    fun settle(targets: List<FestejoTarget>) {
        targets.forEach { t ->
            t.sticker.animate().cancel()
            t.sticker.visibility = View.VISIBLE
            t.particles.stop()
            resetPose(t)
            applyTint(t)
        }
    }

    fun hide(targets: List<FestejoTarget>) {
        targets.forEach { it.sticker.alpha = 0f; it.sticker.visibility = View.INVISIBLE }
    }

    fun play(targets: List<FestejoTarget>, startDelayMs: Long) {
        targets.forEachIndexed { index, t ->
            t.sticker.postDelayed({ if (t.sticker.isAttachedToWindow) animate(t) }, startDelayMs + index * STAGGER_MS)
        }
    }

    private fun applyTint(t: FestejoTarget) {
        val frame = t.card as? android.widget.FrameLayout ?: return
        frame.foreground = when (t.kind) {
            FestejoKind.SOB -> ColorDrawable(Color.parseColor("#332F6FD0"))
            FestejoKind.RAGE -> ColorDrawable(Color.parseColor("#2EC0281C"))
            FestejoKind.TWINKLE, FestejoKind.DIZZY -> StarryVioletDrawable()
            else -> null
        }
    }

    /** The whole card (picture, name and role) moves, not just the sticker. */
    private fun mover(t: FestejoTarget): View = (t.card.parent as? View) ?: t.card

    private fun resetPose(t: FestejoTarget) {
        val s = t.sticker
        s.alpha = 1f; s.scaleX = 1f; s.scaleY = 1f; s.rotation = STICKER_TILT; s.translationX = 0f; s.translationY = 0f
        mover(t).apply { translationX = 0f; translationY = 0f; rotation = 0f; scaleX = 1f; scaleY = 1f; translationZ = 0f }
    }

    private fun animate(t: FestejoTarget) {
        val s = t.sticker
        val m = mover(t)
        val d = s.resources.displayMetrics.density
        s.visibility = View.VISIBLE
        applyTint(t)
        m.translationZ = 12f * d
        fun o(target: View, prop: android.util.Property<View, Float>, vararg v: Float) = ObjectAnimator.ofFloat(target, prop, *v)
        fun together(vararg a: Animator) = AnimatorSet().apply { playTogether(*a) }
        val tilt = STICKER_TILT
        val appear = together(
            o(s, View.ALPHA, 0f, 1f).setDuration(200L),
            o(s, View.SCALE_X, 0.1f, 1.25f, 1f), o(s, View.SCALE_Y, 0.1f, 1.25f, 1f)
        ).apply { duration = 450L; interpolator = OvershootInterpolator(2.5f) }
        val set = AnimatorSet()
        when (t.kind) {
            FestejoKind.RISE -> {
                val rise = together(
                    o(s, View.ALPHA, 0f, 1f).setDuration(200L),
                    o(s, View.TRANSLATION_Y, 70f * d, 0f).apply { duration = 800L; interpolator = OvershootInterpolator(3f) },
                    o(s, View.ROTATION, -40f, tilt).setDuration(800L)
                )
                val hop = together(
                    o(m, View.TRANSLATION_Y, 0f, -18f * d, 0f, -10f * d, 0f).setDuration(1000L),
                    o(m, View.SCALE_X, 1f, 1.1f, 1f, 1.06f, 1f).setDuration(1000L),
                    o(m, View.SCALE_Y, 1f, 1.1f, 1f, 1.06f, 1f).setDuration(1000L)
                )
                set.playSequentially(rise, hop)
            }
            FestejoKind.SOB -> {
                val sob = together(
                    o(s, View.TRANSLATION_Y, 0f, 7f * d, 0f).apply { duration = 280L; repeatCount = 6 },
                    o(m, View.TRANSLATION_Y, 0f, 4f * d, 0f).apply { duration = 280L; repeatCount = 6 },
                    o(m, View.ROTATION, -3f, 3f, -3f).apply { duration = 560L; repeatCount = 3 }
                )
                set.playSequentially(appear, sob)
            }
            FestejoKind.FLEX -> {
                val jumps = together(
                    o(m, View.TRANSLATION_Y, 0f, -30f * d, 0f).apply { duration = 480L; repeatCount = 3; interpolator = DecelerateInterpolator() },
                    o(m, View.SCALE_Y, 1f, 1.12f, 0.9f, 1f).apply { duration = 480L; repeatCount = 3 },
                    o(m, View.SCALE_X, 1f, 0.92f, 1.1f, 1f).apply { duration = 480L; repeatCount = 3 },
                    o(s, View.ROTATION, tilt, tilt - 14f, tilt + 14f, tilt).apply { duration = 480L; repeatCount = 3 }
                )
                set.playSequentially(appear, jumps)
            }
            FestejoKind.RAGE -> {
                val shake = together(
                    o(s, View.ROTATION, tilt, tilt - 16f, tilt + 16f, tilt - 16f, tilt + 16f, tilt).apply { duration = 420L; repeatCount = 4 },
                    o(m, View.TRANSLATION_X, 0f, -8f * d, 8f * d, -8f * d, 8f * d, 0f).apply { duration = 420L; repeatCount = 4 },
                    o(m, View.ROTATION, 0f, -4f, 4f, -4f, 4f, 0f).apply { duration = 420L; repeatCount = 4 },
                    o(m, View.SCALE_X, 1f, 1.08f, 1f).apply { duration = 420L; repeatCount = 4 },
                    o(m, View.SCALE_Y, 1f, 1.08f, 1f).apply { duration = 420L; repeatCount = 4 }
                )
                set.playSequentially(appear, shake)
            }
            FestejoKind.TWINKLE -> {
                val sway = together(
                    o(m, View.ROTATION, 0f, -9f, 9f, -9f, 9f, 0f).apply { duration = 560L; repeatCount = 2 },
                    o(m, View.TRANSLATION_Y, 0f, -14f * d, 0f).apply { duration = 560L; repeatCount = 2 },
                    o(m, View.SCALE_X, 1f, 1.14f, 1f).apply { duration = 560L; repeatCount = 2 },
                    o(m, View.SCALE_Y, 1f, 1.14f, 1f).apply { duration = 560L; repeatCount = 2 },
                    o(s, View.SCALE_X, 1f, 1.3f, 1f).apply { duration = 560L; repeatCount = 2 },
                    o(s, View.SCALE_Y, 1f, 1.3f, 1f).apply { duration = 560L; repeatCount = 2 }
                )
                set.playSequentially(appear, sway)
            }
            FestejoKind.DIZZY -> {
                val drift = together(
                    o(s, View.ROTATION, tilt, tilt + 720f).setDuration(1900L),
                    o(s, View.TRANSLATION_Y, 0f, -10f * d, 4f * d, -6f * d, 0f).setDuration(1900L),
                    o(m, View.ROTATION, 0f, -14f, 12f, -8f, 0f).setDuration(1900L),
                    o(m, View.TRANSLATION_X, 0f, 10f * d, -10f * d, 6f * d, 0f).setDuration(1900L),
                    o(m, View.TRANSLATION_Y, 0f, 12f * d, -4f * d, 8f * d, 0f).setDuration(1900L),
                    o(m, View.SCALE_X, 1f, 0.93f, 1f).setDuration(1900L),
                    o(m, View.SCALE_Y, 1f, 0.93f, 1f).setDuration(1900L)
                )
                set.playSequentially(appear, drift)
            }
            FestejoKind.STAMP, FestejoKind.STAMP_CRACK -> {
                val drop = together(
                    o(s, View.ALPHA, 0f, 1f).setDuration(100L),
                    o(s, View.SCALE_X, 4f, 1f), o(s, View.SCALE_Y, 4f, 1f),
                    o(s, View.ROTATION, 240f, tilt)
                ).apply { duration = 380L; interpolator = AccelerateInterpolator(1.6f) }
                val slam = together(
                    o(m, View.TRANSLATION_Y, 0f, 14f * d, -8f * d, 0f).setDuration(520L),
                    o(m, View.SCALE_X, 1f, 0.9f, 1.07f, 1f).setDuration(520L),
                    o(m, View.SCALE_Y, 1f, 0.9f, 1.07f, 1f).setDuration(520L)
                )
                val after = if (t.kind == FestejoKind.STAMP) {
                    together(
                        o(s, View.SCALE_X, 1f, 1.25f, 1f).setDuration(600L),
                        o(s, View.SCALE_Y, 1f, 1.25f, 1f).setDuration(600L),
                        o(m, View.ROTATION, 0f, -5f, 5f, 0f).setDuration(600L)
                    )
                } else {
                    together(
                        o(s, View.ROTATION, tilt, tilt - 12f, tilt + 12f, tilt - 9f, tilt + 9f, tilt).setDuration(700L),
                        o(m, View.TRANSLATION_X, 0f, -10f * d, 10f * d, -8f * d, 8f * d, 0f).setDuration(700L),
                        o(m, View.ROTATION, 0f, -6f, 6f, -4f, 4f, 0f).setDuration(700L)
                    )
                }
                set.playSequentially(drop, slam, after)
            }
        }
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = resetPose(t)
        })
        if (t.kind != FestejoKind.STAMP_CRACK) t.particles.play(PARTICLE_MS)
        set.start()
    }

    /** The sticker sits slightly rotated, like a sticker stuck over the card's corner. */
    const val STICKER_TILT = 8f
}
