package com.traidores.juego

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.*
import android.view.*
import android.view.animation.*
import android.util.TypedValue
import android.widget.*

internal data class ReactionSpec(
        val id: String,
        val key: String,
        val imageRes: Int,
        val label: String,
        val toneHex: String,
        val description: String = ""
    ) {
        fun tooltipText(): String {
            return if (description.isBlank()) label else "$label\n$description"
        }
    }

/** Original palette and bubbles, shared by the local/legacy table and V3. */
internal class GameplayReactionUi(
    private val activity: Activity,
    private val gameplayRoot: RelativeLayout,
    private val anchorFor: (String) -> View?,
    private val isHuman: (String) -> Boolean,
    private val cosmeticFor: (String) -> String,
    private val dp: (Int) -> Int
) {
    private val activeReactionBubbles = mutableMapOf<String, View>()
    fun palette(button: View, specs: List<ReactionSpec>, humanKey: String, onChoose: (ReactionSpec) -> Unit): PopupWindow {
        val palette = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E8211710"))
                setStroke(dp(1), activity.getColor(R.color.accent_gold))
            }
        }

        specs.forEachIndexed { index, spec ->
            val option = ImageButton(activity).apply {
                setEmoteImageResource(spec.imageRes)
                background = reactionOptionBackground(spec, humanKey)
                contentDescription = spec.tooltipText()
                androidx.appcompat.widget.TooltipCompat.setTooltipText(this, spec.tooltipText())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { onChoose(spec) }
            }
            palette.addView(
                option,
                LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                    if (index > 0) leftMargin = dp(7)
                }
            )
        }

        palette.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        GameplayEffects.play(activity, GameplayEffect.PANEL)
        return PopupWindow(
            palette,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(10).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(
                button,
                button.width - palette.measuredWidth,
                dp(6)
            )
        }
    }

    private fun reactionOptionBackground(spec: ReactionSpec, humanKey: String): Drawable {
        val cosmeticTheme = cosmeticFor(humanKey)
        if (CosmeticPilot.isDecoratedTheme(cosmeticTheme)) {
            return CosmeticPilot.emoteFrame(activity, theme = cosmeticTheme)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#2A2318"))
            setStroke(dp(2), Color.parseColor(spec.toneHex))
        }
    }

    fun clear() {
        activeReactionBubbles.values.toList().forEach { bubble ->
            bubble.animate().cancel()
            stopEmoteAnimations(bubble)
            (bubble.parent as? ViewGroup)?.removeView(bubble)
        }
        activeReactionBubbles.clear()
    }

    fun show(playerName: String, spec: ReactionSpec) {
        val anchor = anchorFor(playerName) ?: return
        if (anchor.width <= 0 || anchor.height <= 0 || gameplayRoot.width <= 0) {
            anchor.post { show(playerName, spec) }
            return
        }

        // Sonido del emote (humano y bots): canal único con "el último gana" + throttle.
        EmoteSoundEffects.play(activity, spec.key)

        activeReactionBubbles.remove(playerName)?.let { oldBubble ->
            oldBubble.animate().cancel()
            stopEmoteAnimations(oldBubble)
            (oldBubble.parent as? ViewGroup)?.removeView(oldBubble)
        }

        val isHuman = isHuman(playerName)
        val bubbleSize = dp(if (isHuman) 66 else 52)
        val tailSize = dp(if (isHuman) 12 else 9)
        val bubbleWidth = bubbleSize
        val bubbleHeight = bubbleSize + tailSize / 2
        val cosmeticTheme = cosmeticFor(playerName)
        val usesDecoratedCosmetic = CosmeticPilot.isDecoratedTheme(cosmeticTheme)

        val bubble = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            scaleX = 0.78f
            scaleY = 0.78f
            translationY = dp(6).toFloat()
        }

        val shell = FrameLayout(activity).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = if (usesDecoratedCosmetic) {
                CosmeticPilot.bubbleShell(activity, cosmeticTheme)
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(13).toFloat()
                    setColor(Color.parseColor("#2A2318"))
                    setStroke(dp(2), Color.parseColor(spec.toneHex))
                }
            }
            elevation = dp(8).toFloat()
        }
        val icon = ImageView(activity).apply {
            if (
                spec.key == "premium_six_seven" &&
                VisualEffectsPreferences.isReduced(activity)
            ) {
                setImageResource(R.drawable.reaction_premium_six_seven_a)
            } else {
                setEmoteImageResource(
                    spec.imageRes,
                    loop = spec.key == "premium_six_seven"
                )
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = spec.label
        }
        shell.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        bubble.addView(
            shell,
            FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )

        if (usesDecoratedCosmetic) {
            listOf(Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END).forEach { starGravity ->
                bubble.addView(
                    TextView(activity).apply {
                        text = when (cosmeticTheme) {
                            CosmeticPilot.THEME_SEA -> "◦"
                            CosmeticPilot.THEME_FIRE -> "◆"
                            else -> "✦"
                        }
                        includeFontPadding = false
                        gravity = Gravity.CENTER
                        setTextColor(CosmeticPilot.accentColor(cosmeticTheme))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setShadowLayer(
                            dp(4).toFloat(),
                            0f,
                            0f,
                            CosmeticPilot.textColor(cosmeticTheme)
                        )
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(dp(16), dp(16), starGravity).apply {
                        topMargin = -dp(3)
                        if (starGravity and Gravity.START == Gravity.START) {
                            leftMargin = -dp(3)
                        } else {
                            rightMargin = -dp(3)
                        }
                    }
                )
            }
        }

        val tail = View(activity).apply {
            rotation = 45f
            background = if (usesDecoratedCosmetic) {
                CosmeticPilot.bubbleTail(activity, cosmeticTheme)
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(2).toFloat()
                    setColor(Color.parseColor("#2A2318"))
                    setStroke(dp(1), Color.parseColor(spec.toneHex))
                }
            }
        }
        bubble.addView(
            tail,
            FrameLayout.LayoutParams(tailSize, tailSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )

        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        gameplayRoot.getLocationOnScreen(rootLocation)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorCenterX = anchorLocation[0] - rootLocation[0] + anchor.width / 2
        val anchorTop = anchorLocation[1] - rootLocation[1]
        val left = (anchorCenterX - bubbleWidth / 2)
            .coerceIn(dp(4), (gameplayRoot.width - bubbleWidth - dp(4)).coerceAtLeast(dp(4)))
        val top = (anchorTop - bubbleHeight + dp(if (isHuman) 6 else 2))
            .coerceIn(dp(6), (gameplayRoot.height - bubbleHeight - dp(6)).coerceAtLeast(dp(6)))

        gameplayRoot.addView(
            bubble,
            RelativeLayout.LayoutParams(bubbleWidth, bubbleHeight).apply {
                leftMargin = left
                topMargin = top
            }
        )
        activeReactionBubbles[playerName] = bubble

        bubble.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                bubble.animate()
                    .alpha(0f)
                    .translationY(-dp(10).toFloat())
                    .setStartDelay(3_650L)
                    .setDuration(240L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (activeReactionBubbles[playerName] === bubble) {
                            activeReactionBubbles.remove(playerName)
                        }
                        stopEmoteAnimations(bubble)
                        (bubble.parent as? ViewGroup)?.removeView(bubble)
                    }
                    .start()
            }
            .start()
    }

    private fun stopEmoteAnimations(view: View) {
        (view as? ImageView)?.drawable?.let { drawable ->
            (drawable as? Animatable)?.stop()
        }
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { index -> stopEmoteAnimations(group.getChildAt(index)) }
        }
    }

}
