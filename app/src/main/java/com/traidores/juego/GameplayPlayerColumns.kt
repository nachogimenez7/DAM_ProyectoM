package com.traidores.juego

import android.app.Activity
import android.animation.*
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.widget.TextViewCompat
import kotlin.math.ceil

internal data class SidePlayerCardHolder(
        val root: LinearLayout,
        val cardFace: FrameLayout,
        val cardBack: ImageView,
        val roleFace: ImageView,
        val deathCauseOverlay: ImageView,
        val actionMarkPrimary: ImageView,
        val actionMarkSecondary: ImageView,
        val actionMarkTertiary: ImageView,
        val actionMarkPrimaryLabel: TextView,
        val actionMarkSecondaryLabel: TextView,
        val actionMarkTertiaryLabel: TextView,
        val avatar: GameplayAvatarView,
        val mutedBadge: TextView,
        val reconnectingBadge: TextView,
        val actionBadge: TextView,
        val name: TextView,
        var selected: Boolean = false,
        var actionPulseKey: String? = null,
        var actionBadgeAnimator: AnimatorSet? = null,
        var actionMarkAnimator: AnimatorSet? = null,
        var actionMarkKey: String? = null,
        var hasBound: Boolean = false,
        var publicRoleVisible: Boolean = false,
        var renderKey: String? = null
    )

/** Original common table layout, card factory and death animation. No engine or network. */
internal class GameplayPlayerColumns(
    private val activity: Activity,
    private val playerCardViews: MutableMap<String, SidePlayerCardHolder>,
    private val bind: (SidePlayerCardHolder, GamePlayer, CompanionCardMetrics) -> Unit,
    private val onMetrics: (CompanionCardMetrics) -> Unit = {}
) {
    private var lastCompanionCardMetrics: CompanionCardMetrics? = null
    private var keyFor: (GamePlayer) -> String = { it.name }
    private var playerCount = 0
    private val BOTTOM_PLAYER_PANEL_HEIGHT_DP = 146
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private val leftPlayersScroll: ScrollView = activity.findViewById(R.id.leftPlayersScroll)
    private val rightPlayersScroll: ScrollView = activity.findViewById(R.id.rightPlayersScroll)
    private val leftPlayersContainer: LinearLayout = activity.findViewById(R.id.leftPlayersContainer)
    private val rightPlayersContainer: LinearLayout = activity.findViewById(R.id.rightPlayersContainer)
    private val rightColumn: LinearLayout = activity.findViewById(R.id.rightColumn)
    private val bottomPlayerPanel: LinearLayout = activity.findViewById(R.id.bottomPlayerPanel)
    private val topStatus: LinearLayout = activity.findViewById(R.id.topStatus)
    private val phaseSubtitle: TextView = activity.findViewById(R.id.phaseSubtitle)
    private val phaseTitle: TextView = activity.findViewById(R.id.phaseTitle)
    private val eventLogPanel: LinearLayout = activity.findViewById(R.id.eventLogPanel)
    private val eventLogHeader: LinearLayout = activity.findViewById(R.id.eventLogHeader)
    private val gameplayBody: LinearLayout = activity.findViewById(R.id.gameplayBody)

    fun render(players: List<GamePlayer>, newlyDeadPlayers: Set<String> = emptySet(), keyFor: (GamePlayer) -> String = { it.name }) {
        this.keyFor = keyFor
        this.playerCount = players.size
        val (leftPlayers, rightPlayers) = GameplayTableUi.splitCompanions(
            players,
            includeEliminated = true,
            putOddExtraOnLeft = true
        )
        val displayedPlayers = leftPlayers.size + rightPlayers.size + 1
        val totalPlayers = displayedPlayers.coerceAtLeast(LocalGameFactory.MIN_PLAYERS)
        val measuredHeightPx = listOf(leftPlayersScroll.height, rightPlayersScroll.height)
            .filter { it > 0 }
            .minOrNull()
        val bottomPanelInsetDp = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12
        val availableHeightDp = measuredHeightPx
            ?.let { ((it / activity.resources.displayMetrics.density).toInt() - bottomPanelInsetDp).coerceAtLeast(1) }
            ?: (activity.resources.configuration.screenHeightDp - 16 - bottomPanelInsetDp)
                .coerceAtLeast(240)
        val metrics = GameplayTableUi.companionCardMetrics(
            totalPlayers,
            availableHeightDp,
            availableWidthDp = availableSideColumnWidthDp()
        )
        if (lastCompanionCardMetrics != metrics) {
            lastCompanionCardMetrics = metrics
            onMetrics(metrics)
            applyAdaptiveGameplayLayout(metrics)
        }

        val desiredNames = (leftPlayers + rightPlayers).map(keyFor).toSet()
        playerCardViews.keys.toList()
            .filterNot { it in desiredNames }
            .forEach { name ->
                playerCardViews.remove(name)?.root?.let { root ->
                    (root.parent as? ViewGroup)?.removeView(root)
                }
            }

        syncPlayerContainer(leftPlayersContainer, leftPlayers, metrics, newlyDeadPlayers)
        syncPlayerContainer(rightPlayersContainer, rightPlayers, metrics, newlyDeadPlayers)
    }

    private fun applyAdaptiveGameplayLayout(metrics: CompanionCardMetrics) {
        leftPlayersScroll.layoutParams = (leftPlayersScroll.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.columnWidthDp)
        }
        rightColumn.layoutParams = (rightColumn.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.columnWidthDp)
        }

        leftPlayersScroll.isVerticalScrollBarEnabled = metrics.scrollEnabled
        rightPlayersScroll.isVerticalScrollBarEnabled = metrics.scrollEnabled
        leftPlayersScroll.overScrollMode = if (metrics.scrollEnabled) {
            View.OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            View.OVER_SCROLL_NEVER
        }
        rightPlayersScroll.overScrollMode = leftPlayersScroll.overScrollMode
        val verticalGravity = if (metrics.scrollEnabled) {
            Gravity.TOP
        } else {
            Gravity.CENTER_VERTICAL
        }
        leftPlayersContainer.gravity = verticalGravity or Gravity.START
        rightPlayersContainer.gravity = verticalGravity or Gravity.END
        leftPlayersContainer.setPadding(dp(2), 0, 0, 0)
        rightPlayersContainer.setPadding(0, 0, dp(2), 0)
        val bottomScrollInset = BOTTOM_PLAYER_PANEL_HEIGHT_DP + 12
        leftPlayersScroll.setPadding(0, 0, 0, dp(bottomScrollInset))
        rightPlayersScroll.setPadding(0, 0, 0, dp(bottomScrollInset))
        bottomPlayerPanel.layoutParams = (bottomPlayerPanel.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp((activity.resources.configuration.screenWidthDp - 24).coerceIn(244, 372))
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        applyAdaptiveHudSizing()

        gameplayBody.requestLayout()
    }

    private fun applyAdaptiveHudSizing() {
        val playerCount = playerCount
        val roomy = playerCount <= 8
        val relaxed = playerCount <= 10
        val topHeightDp = when {
            roomy -> 90
            relaxed -> 82
            else -> 76
        }
        val headerHeightDp = if (roomy) 44 else 38
        val subtitleHeightDp = topHeightDp - headerHeightDp - 4
        topStatus.layoutParams = (topStatus.layoutParams as FrameLayout.LayoutParams).apply {
            height = dp(topHeightDp)
        }
        (topStatus.getChildAt(0).layoutParams as LinearLayout.LayoutParams).height = dp(headerHeightDp)
        phaseSubtitle.layoutParams = (phaseSubtitle.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(subtitleHeightDp)
        }
        phaseTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (roomy) 18f else 16f)
        phaseSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (roomy) 11.5f else 10.5f)
        eventLogPanel.layoutParams = (eventLogPanel.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = dp(topHeightDp + 4)
        }
        eventLogHeader.layoutParams = (eventLogHeader.layoutParams as LinearLayout.LayoutParams).apply {
            height = dp(if (playerCount <= 8) 40 else 32)
        }
    }

    private fun availableSideColumnWidthDp(): Int {
        val totalWidthDp = activity.resources.configuration.screenWidthDp
        val gameplayBodyHorizontalMarginsDp = 8
        val centerColumnHorizontalMarginsDp = 8
        val centerColumnPreferredWidthDp = 220
        val combinedSideWidth = (
            totalWidthDp -
                gameplayBodyHorizontalMarginsDp -
                centerColumnHorizontalMarginsDp -
                centerColumnPreferredWidthDp
            ).coerceAtLeast(108)
        return (combinedSideWidth / 2).coerceIn(54, 78)
    }

    private fun syncPlayerContainer(
        container: LinearLayout,
        players: List<GamePlayer>,
        metrics: CompanionCardMetrics,
        newlyDeadPlayers: Set<String>
    ) {
        val containerNames = players.map(keyFor).toSet()
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index)
            if (child.tag !in containerNames) {
                container.removeViewAt(index)
            }
        }

        players.forEachIndexed { index, player ->
            val holder = playerCardViews.getOrPut(keyFor(player)) {
                createSidePlayerCard(metrics).also { created ->
                    created.root.tag = keyFor(player)
                    created.root.alpha = 0f
                    created.root.translationY = dp(6).toFloat()
                    created.root.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200L)
                        .start()
                }
            }
            val currentParent = holder.root.parent as? ViewGroup
            if (currentParent !== container) {
                currentParent?.removeView(holder.root)
                container.addView(holder.root, index.coerceAtMost(container.childCount))
            } else if (container.indexOfChild(holder.root) != index) {
                container.removeView(holder.root)
                container.addView(holder.root, index.coerceAtMost(container.childCount))
            }
            holder.root.gravity = Gravity.CENTER_VERTICAL or if (container === rightPlayersContainer) {
                Gravity.END
            } else {
                Gravity.START
            }
            bind(holder, player, metrics)
            if (keyFor(player) in newlyDeadPlayers) {
                animatePlayerDeath(holder.root)
            }
        }
    }

    private fun animatePlayerDeath(view: View) {
        view.alpha = 1f
        view.background = ColorDrawable(Color.argb(92, 150, 24, 24))

        val shake = ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f,
            -dp(7).toFloat(),
            dp(7).toFloat(),
            -dp(4).toFloat(),
            dp(4).toFloat(),
            0f
        ).apply {
            duration = 320L
        }
        val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.48f, 1f).apply {
            startDelay = 150L
            duration = 650L
            interpolator = AccelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(shake, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.background = null
                    view.translationX = 0f
                    // El estado eliminado se representa dentro de la carta. Mantener todo el
                    // contenedor al 40 % hacía desaparecer también el marco, el nombre y el
                    // indicador de muerte contra los fondos claros del mapa.
                    view.alpha = 1f
                }
            })
            start()
        }
    }

    private fun createSidePlayerCard(metrics: CompanionCardMetrics): SidePlayerCardHolder {
        val item = LinearLayout(activity)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.clipChildren = false
        item.clipToPadding = false
        item.minimumWidth = dp(metrics.minCardWidthDp)

        val cardFace = FrameLayout(activity)
        cardFace.clipChildren = false
        cardFace.clipToPadding = false
        val cardBack = ImageView(activity)
        cardBack.setImageResource(R.drawable.card_back_traidores)
        cardBack.scaleType = ImageView.ScaleType.FIT_CENTER
        cardFace.addView(
            cardBack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val roleFace = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        cardFace.addView(
            roleFace,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val deathCauseOverlay = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = null
            elevation = dp(8).toFloat()
        }
        cardFace.addView(
            deathCauseOverlay,
            FrameLayout.LayoutParams(
                dp(22),
                dp(22),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(1)
                bottomMargin = dp(1)
            }
        )

        val actionMarkPrimary = createCardActionMarkView()
        val actionMarkSecondary = createCardActionMarkView()
        val actionMarkTertiary = createCardActionMarkView()
        cardFace.addView(actionMarkPrimary)
        cardFace.addView(actionMarkSecondary)
        cardFace.addView(actionMarkTertiary)
        val actionMarkPrimaryLabel = createCardActionMarkLabel()
        val actionMarkSecondaryLabel = createCardActionMarkLabel()
        val actionMarkTertiaryLabel = createCardActionMarkLabel()
        cardFace.addView(actionMarkPrimaryLabel)
        cardFace.addView(actionMarkSecondaryLabel)
        cardFace.addView(actionMarkTertiaryLabel)

        val avatar = GameplayAvatarView(activity)
        cardFace.addView(
            avatar,
            FrameLayout.LayoutParams(
                dp(metrics.avatarSizeDp),
                dp(metrics.avatarSizeDp),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val mutedBadge = TextView(activity)
        mutedBadge.text = "MUDO"
        mutedBadge.gravity = Gravity.CENTER
        mutedBadge.includeFontPadding = false
        mutedBadge.setTextColor(activity.getColor(R.color.text_primary))
        mutedBadge.setBackgroundResource(R.drawable.bg_player_chip)
        mutedBadge.textSize = 6.5f
        mutedBadge.setTypeface(null, Typeface.BOLD)
        mutedBadge.setPadding(dp(2), 0, dp(2), 0)
        cardFace.addView(
            mutedBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(13),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val reconnectingBadge = TextView(activity).apply {
            text = "\u21BB RECONECTANDO\u2026"
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 5.5f
            setPadding(dp(3), 0, dp(3), 0)
            visibility = View.GONE
            elevation = dp(10).toFloat()
            isClickable = false
            isFocusable = false
        }
        cardFace.addView(
            reconnectingBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(14),
                Gravity.CENTER
            ).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            }
        )

        val actionBadge = TextView(activity)
        actionBadge.gravity = Gravity.CENTER
        actionBadge.includeFontPadding = false
        actionBadge.maxLines = 1
        actionBadge.ellipsize = TextUtils.TruncateAt.END
        actionBadge.setTypeface(null, Typeface.BOLD)
        actionBadge.setPadding(dp(4), dp(1), dp(4), dp(1))
        actionBadge.visibility = View.GONE
        cardFace.addView(
            actionBadge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(13),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(2)
            }
        )

        val cardParams = LinearLayout.LayoutParams(
            dp(metrics.cardWidthDp),
            dp(metrics.cardHeightDp)
        )
        item.addView(cardFace, cardParams)

        val name = TextView(activity)
        name.gravity = Gravity.CENTER
        name.ellipsize = TextUtils.TruncateAt.END
        name.includeFontPadding = false
        name.maxLines = 1
        name.setSingleLine(true)
        name.typeface = Typeface.DEFAULT_BOLD
        item.addView(
            name,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(metrics.nameHeightDp)
            )
        )

        return SidePlayerCardHolder(
            item,
            cardFace,
            cardBack,
            roleFace,
            deathCauseOverlay,
            actionMarkPrimary,
            actionMarkSecondary,
            actionMarkTertiary,
            actionMarkPrimaryLabel,
            actionMarkSecondaryLabel,
            actionMarkTertiaryLabel,
            avatar,
            mutedBadge,
            reconnectingBadge,
            actionBadge,
            name
        )
    }

    private fun createCardActionMarkView(): ImageView {
        return ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            alpha = 0f
            contentDescription = null
            isClickable = false
            isFocusable = false
            elevation = dp(7).toFloat()
        }
    }

    private fun createCardActionMarkLabel(): TextView {
        return TextView(activity).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            textSize = 6f
            setPadding(dp(2), 0, dp(2), 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                4,
                7,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            visibility = View.GONE
            alpha = 0f
            elevation = dp(9).toFloat()
            isClickable = false
            isFocusable = false
        }
    }

    fun bindPrimaryAction(button: Button, label: String, emphasized: Boolean) {
        val tone = if (emphasized) GameplayTableUi.actionToneFor(label) else GameplayActionTone.DEFAULT
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(if (emphasized) tone.colorHex else "#4A3A1E"))
            setStroke(dp(1), activity.getColor(R.color.accent_gold))
            cornerRadius = dp(6).toFloat()
        }
        button.setTextColor(activity.getColor(
            if (emphasized && tone.darkText) R.color.bg_dark else R.color.text_primary))
    }

    fun bindActionBadge(holder: SidePlayerCardHolder, metrics: CompanionCardMetrics,
        isDirectVoteSelection: Boolean, visible: Boolean, actionLabel: String, label: String) {
        holder.actionBadge.layoutParams = (holder.actionBadge.layoutParams as FrameLayout.LayoutParams).apply {
            height = dp(if (isDirectVoteSelection) 20 else (metrics.nameHeightDp - 2).coerceIn(12, 16))
            bottomMargin = dp(2)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        holder.actionBadge.maxWidth = dp(
            if (isDirectVoteSelection) (metrics.cardWidthDp - 4).coerceAtLeast(44)
            else (metrics.minCardWidthDp - 6).coerceAtLeast(44))
        val badgeMaxSp = ceil((metrics.nameTextSp - 1f).coerceIn(5.5f, 8.5f).toDouble())
            .toInt().coerceAtLeast(6)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            holder.actionBadge, 5, badgeMaxSp, 1, TypedValue.COMPLEX_UNIT_SP)
        holder.actionBadge.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            val tone = GameplayTableUi.actionToneFor(actionLabel)
            holder.actionBadge.text = label
            holder.actionBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor(tone.colorHex))
                when (tone) {
                    GameplayActionTone.KILL -> setStroke(dp(1), Color.parseColor("#F1C36A"))
                    GameplayActionTone.SILENCE -> setStroke(dp(1), Color.parseColor("#B46A72"))
                    else -> Unit
                }
            }
            holder.actionBadge.setTextColor(activity.getColor(
                if (tone.darkText) R.color.bg_dark else R.color.text_primary))
        }
    }

    fun bindAppearance(holder: SidePlayerCardHolder, player: GamePlayer, metrics: CompanionCardMetrics, session: GameSession,
        isOracleGuest: Boolean, showPublicRole: Boolean, isSelected: Boolean, isActionable: Boolean, actionLabel: String, isDirectVoteSelection: Boolean) {
        val isAlive = player.alive
        holder.name.layoutParams = (holder.name.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(metrics.cardWidthDp)
            height = dp(metrics.nameHeightDp)
        }
        holder.name.text = player.name
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            holder.name,
            7,
            ceil(metrics.nameTextSp.toDouble()).toInt().coerceAtLeast(8),
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        holder.name.setTextColor(
            when {
                isOracleGuest -> activity.getColor(R.color.accent_gold)
                !isAlive -> activity.getColor(R.color.text_muted)
                isSelected -> activity.getColor(R.color.accent_gold)
                else -> PlayerChatColor.colorFor(player.name, session)
            }
        )
        holder.name.alpha = if (isAlive || isOracleGuest) 1f else 0.86f
        holder.name.setShadowLayer(
            if (isActionable || isSelected) 3f else 1.8f,
            0f,
            1f,
            Color.BLACK
        )
        holder.name.paintFlags = if (isAlive) {
            holder.name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            holder.name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        val eliminatedBorderColor = when (player.deathCause) {
            DeathCause.NIGHT -> Color.parseColor("#C75A54")
            DeathCause.VOTE,
            DeathCause.AFK -> Color.parseColor("#B8924E")
            DeathCause.NONE -> Color.parseColor("#8C7652")
        }
        val outlineWidthDp = when {
            isSelected -> 3
            isActionable || !isAlive -> 2
            else -> 0
        }
        holder.cardFace.setPadding(
            dp(outlineWidthDp),
            dp(outlineWidthDp),
            dp(outlineWidthDp),
            dp(outlineWidthDp)
        )
        holder.cardFace.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                if (isAlive || isOracleGuest) {
                    Color.TRANSPARENT
                } else {
                    Color.parseColor(if (showPublicRole) "#D91A120D" else "#C916110D")
                }
            )
            cornerRadius = dp(4).toFloat()
            when {
                isSelected -> setStroke(dp(3), activity.getColor(R.color.accent_gold))
                isActionable -> {
                    val tone = GameplayTableUi.actionToneFor(actionLabel)
                    setStroke(
                        dp(2),
                        when (tone) {
                            GameplayActionTone.KILL -> Color.parseColor("#D12A1E")
                            GameplayActionTone.SILENCE -> Color.parseColor("#7C2A37")
                            else -> activity.getColor(R.color.accent_gold)
                        }
                    )
                }
                !isAlive -> setStroke(dp(2), eliminatedBorderColor)
            }
        }
        holder.cardFace.elevation = if (isDirectVoteSelection) dp(10).toFloat() else 0f
        if (holder.selected != isSelected) {
            holder.root.animate()
                .scaleX(if (isSelected) 1.055f else 1f)
                .scaleY(if (isSelected) 1.055f else 1f)
                .setDuration(180L)
                .start()
            holder.selected = isSelected
        }

        val eliminatedContentAlpha = if (isAlive || isActionable || isOracleGuest) 1f else 0.72f
        holder.cardBack.alpha = eliminatedContentAlpha
        holder.roleFace.alpha = when {
            isAlive || isOracleGuest -> 1f
            showPublicRole -> 0.58f
            else -> eliminatedContentAlpha
        }
        holder.avatar.alpha = eliminatedContentAlpha
        holder.root.alpha = 1f
    }
    fun updatePublicRoleCard(
        holder: SidePlayerCardHolder,
        player: GamePlayer,
        showPublicRole: Boolean,
        isAlive: Boolean
    ) {
        val applyFace = {
            holder.cardBack.visibility = if (showPublicRole) View.GONE else View.VISIBLE
            holder.roleFace.visibility = if (showPublicRole) View.VISIBLE else View.GONE
            if (showPublicRole) {
                holder.roleFace.setImageResource(player.role?.let { DrawableResourceCatalog.resolveOrPlaceholder(it.imageResName) } ?: R.drawable.card_back_traidores)
                holder.roleFace.alpha = if (isAlive) 1f else 0.58f
            }
        }
        val animateReveal = holder.hasBound && showPublicRole && !holder.publicRoleVisible
        holder.hasBound = true
        holder.publicRoleVisible = showPublicRole
        if (!animateReveal) {
            holder.cardFace.animate().cancel()
            holder.cardFace.rotationY = 0f
            applyFace()
            return
        }
        holder.cardFace.cameraDistance = dp(900).toFloat()
        holder.cardFace.animate()
            .rotationY(90f)
            .setDuration(150L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                applyFace()
                holder.cardFace.rotationY = -90f
                holder.cardFace.animate()
                    .rotationY(0f)
                    .setDuration(190L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }


}
