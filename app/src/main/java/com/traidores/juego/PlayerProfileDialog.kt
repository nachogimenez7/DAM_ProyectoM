package com.traidores.juego

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.WeakHashMap

data class PlayerProfileAction(
    val label: String,
    val dangerous: Boolean = false,
    val description: String? = null,
    val onClick: () -> Unit
)

object PlayerProfileDialog {
    private val openDialogs = WeakHashMap<Activity, MutableSet<AlertDialog>>()

    fun dismissAll(activity: Activity) {
        openDialogs[activity]
            ?.toList()
            ?.forEach { dialog -> dialog.dismiss() }
        openDialogs.remove(activity)
    }

    fun showFull(
        activity: Activity,
        profile: PlayerProfile,
        canEdit: Boolean,
        actions: List<PlayerProfileAction> = emptyList()
    ) {
        val content = profileView(
            activity,
            profile,
            compact = false,
            canEdit = canEdit,
            actions = actions
        )
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.findViewWithTag<View>("close")?.setOnClickListener { dialog.dismiss() }
        content.findViewWithTag<View>("edit")?.setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, ProfileActivity::class.java))
        }
        actions.forEachIndexed { index, action ->
            content.findViewWithTag<View>("profile_action_$index")?.setOnClickListener {
                dialog.dismiss()
                action.onClick()
            }
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = (activity.resources.displayMetrics.widthPixels - dp(activity, 32))
                    .coerceAtMost(dp(activity, 760))
                val height = (activity.resources.displayMetrics.heightPixels - dp(activity, 24))
                    .coerceAtMost(dp(activity, 640))
                setLayout(width, height)
                setDimAmount(0.62f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        showTracked(activity, dialog)
    }

    fun showMini(
        activity: Activity,
        profile: PlayerProfile,
        actions: List<PlayerProfileAction> = emptyList()
    ) {
        val content = profileView(
            activity,
            profile,
            compact = true,
            canEdit = false,
            actions = actions
        )
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.findViewWithTag<View>("close")?.setOnClickListener { dialog.dismiss() }
        content.findViewWithTag<View>("expand")?.setOnClickListener {
            dialog.dismiss()
            showFull(activity, profile, canEdit = false, actions = actions)
        }
        actions.forEachIndexed { index, action ->
            content.findViewWithTag<View>("profile_action_$index")?.setOnClickListener {
                dialog.dismiss()
                action.onClick()
            }
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = (activity.resources.displayMetrics.widthPixels - dp(activity, 56))
                    .coerceAtMost(dp(activity, 500))
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setDimAmount(0.52f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        showTracked(activity, dialog)
    }

    private fun profileView(
        activity: Activity,
        profile: PlayerProfile,
        compact: Boolean,
        canEdit: Boolean,
        actions: List<PlayerProfileAction>
    ): View {
        val cosmeticTheme = CosmeticPilot.normalizeTheme(profile.cosmeticThemeId)
            ?: CosmeticPilot.THEME_CLASSIC
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        val rootScroll = ScrollView(activity).apply {
            isFillViewport = !compact
            background = if (decorated) {
                CosmeticPilot.profilePanelOverlay(activity, cosmeticTheme)
            } else {
                panelBackground(activity)
            }
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 14))
        }
        rootScroll.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // El mini lleva el CERRAR abajo (mas al alcance del pulgar en partida); el completo
        // lo mantiene arriba a la derecha.
        if (!compact) {
            root.addView(topBar(activity, cosmeticTheme))
        }
        root.addView(bannerView(activity, profile, compact))
        root.addView(identityRow(activity, profile, compact))
        root.addView(statsRow(activity, profile.stats, compact, cosmeticTheme))

        if (profile.bio.isNotBlank()) {
            root.addView(sectionTitle(activity, "DESCRIPCIÓN", cosmeticTheme))
            root.addView(
                textBlock(
                    activity = activity,
                    text = "\"${profile.bio}\"",
                    sizeSp = if (compact) 12f else 13f,
                    color = if (decorated) {
                        CosmeticPilot.textColor(cosmeticTheme)
                    } else {
                        Color.parseColor("#E8D8B8")
                    },
                    cosmeticTheme = cosmeticTheme
                )
            )
        }

        if (compact) {
            root.addView(compactFavoriteRole(activity, profile.favoriteRoleKey, cosmeticTheme))
            if (actions.isNotEmpty()) root.addView(moderationButtons(activity, actions, cosmeticTheme))
            root.addView(miniButtons(activity, cosmeticTheme))
        } else {
            root.addView(sectionTitle(activity, "ROL FAVORITO", cosmeticTheme))
            root.addView(roleRow(activity, profile.favoriteRoleKey, cosmeticTheme))
            root.addView(sectionTitle(activity, "EMOTES", cosmeticTheme))
            root.addView(emoteRow(activity, profile.emoteIds, cosmeticTheme))
            root.addView(sectionTitle(activity, "LOGROS DESTACADOS", cosmeticTheme))
            root.addView(achievementRow(activity, profile.featuredAchievementIds, cosmeticTheme))
            if (actions.isNotEmpty()) {
                root.addView(sectionTitle(activity, "ACCIONES SOBRE ESTE JUGADOR", cosmeticTheme))
                root.addView(moderationButtons(activity, actions, cosmeticTheme))
            }
            if (canEdit) {
                root.addView(editButton(activity, cosmeticTheme))
            }
        }

        return rootScroll
    }

    private fun moderationButtons(
        activity: Activity,
        actions: List<PlayerProfileAction>,
        cosmeticTheme: String
    ): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 10), 0, 0)
            actions.forEachIndexed { index, action ->
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            Button(activity).apply {
                                tag = "profile_action_$index"
                                text = action.label
                                textSize = 11f
                                typeface = Typeface.DEFAULT_BOLD
                                minHeight = 0
                                minWidth = 0
                                setTextColor(
                                    Color.parseColor(
                                        if (action.dangerous) "#FFB4AB" else "#F3D488"
                                    )
                                )
                                background = if (decorated && !action.dangerous) {
                                    CosmeticPilot.profileSurface(activity, cosmeticTheme)
                                } else {
                                    chipBackground(
                                        activity,
                                        if (action.dangerous) "#351616" else "#251A10",
                                        if (action.dangerous) "#8F2633" else "#6B4F2A",
                                        9
                                    )
                                }
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(activity, 40)
                            )
                        )
                        action.description
                            ?.takeIf { it.isNotBlank() }
                            ?.let { description ->
                                addView(
                                    TextView(activity).apply {
                                        text = description
                                        gravity = Gravity.CENTER
                                        textSize = 10f
                                        setTextColor(Color.parseColor("#AFA084"))
                                        setPadding(
                                            dp(activity, 8),
                                            dp(activity, 5),
                                            dp(activity, 8),
                                            0
                                        )
                                    },
                                    LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                )
                            }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = dp(activity, 6)
                    }
                )
            }
        }
    }

    private fun topBar(activity: Activity, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return FrameLayout(activity).apply {
            addView(
                Button(activity).apply {
                    tag = "close"
                    text = "CERRAR"
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        if (decorated) CosmeticPilot.accentColor(cosmeticTheme)
                        else activity.getColor(R.color.accent_gold)
                    )
                    background = if (decorated) {
                        CosmeticPilot.profileSurface(activity, cosmeticTheme)
                    } else {
                        chipBackground(activity, "#2A2318", "#6B4F2A", 9)
                    }
                    minHeight = 0
                    minWidth = 0
                },
                FrameLayout.LayoutParams(dp(activity, 92), dp(activity, 34), Gravity.END)
            )
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 36)
            )
        }
    }

    private fun bannerView(activity: Activity, profile: PlayerProfile, compact: Boolean): View {
        // ImageView + centerCrop en vez de un View con background: se puede agrandar sin
        // estirar/deformar el arte del banner (recorta para llenar, no distorsiona).
        return ImageView(activity).apply {
            setImageResource(ProfileCustomizationCatalog.banner(profile.bannerKey).drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.95f
            val cosmeticTheme = CosmeticPilot.normalizeTheme(profile.cosmeticThemeId)
                ?: CosmeticPilot.THEME_CLASSIC
            foreground = if (CosmeticPilot.isDecoratedTheme(cosmeticTheme)) {
                CosmeticPilot.bannerVeil(activity, cosmeticTheme)
            } else {
                null
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, if (compact) 96 else 100)
            ).apply {
                bottomMargin = dp(activity, 10)
            }
        }
    }

    private fun identityRow(activity: Activity, profile: PlayerProfile, compact: Boolean): View {
        val avatarEntry = ProfileRoleCatalog.find(profile.avatarKey)
        val useLocalPhoto = hasLocalPhotoFor(activity, profile)
        val playGamesAvatarUri = if (useLocalPhoto) "" else profile.playGamesAvatarUri
        val cosmeticTheme = CosmeticPilot.normalizeTheme(profile.cosmeticThemeId)
            ?: CosmeticPilot.THEME_CLASSIC
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 8))

            val avatarSize = dp(activity, if (compact) 72 else 88)
            val avatarFrame = FrameLayout(activity).apply {
                setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4))
                background = if (decorated) {
                    CosmeticPilot.avatarFrame(activity, cosmeticTheme)
                } else {
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#E6140F08"))
                        setStroke(dp(activity, 2), activity.getColor(R.color.accent_gold))
                    }
                }
                isClickable = true
                isFocusable = true
                contentDescription = "Ampliar foto de perfil"
                setOnClickListener {
                    showExpandedAvatar(
                        activity,
                        avatarEntry,
                        useLocalPhoto,
                        playGamesAvatarUri
                    )
                }
            }
            val avatar = CircleProfileImageView(activity).apply {
                scaleType = if (useLocalPhoto || playGamesAvatarUri.isNotBlank()) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.MATRIX
                }
                contentDescription = "Avatar de ${profile.name}"
            }
            avatarFrame.addView(
                avatar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            val fallbackRes = DrawableResourceCatalog.resolveOrPlaceholder(
                avatarEntry.role.imageResName
            )
            val showingLocalPhoto = useLocalPhoto &&
                LocalProfilePhotoStore.render(activity, avatar, false)
            val showingPlayGamesPhoto = !showingLocalPhoto &&
                PlayGamesProfileAvatar.render(
                    context = activity,
                    image = avatar,
                    uriValue = playGamesAvatarUri,
                    fallbackDrawableRes = fallbackRes
                )
            if (!showingLocalPhoto && !showingPlayGamesPhoto) {
                setRoleImage(activity, avatar, avatarEntry.role)
                alignAvatarToFocus(avatar, avatarEntry.verticalFocus)
            }
            addView(
                avatarFrame,
                LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    rightMargin = dp(activity, 14)
                }
            )

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(activity).apply {
                        text = profile.name.uppercase()
                        setTextColor(
                            if (decorated) {
                                CosmeticPilot.accentColor(cosmeticTheme)
                            } else {
                                activity.getColor(R.color.accent_gold)
                            }
                        )
                        textSize = if (compact) 21f else 25f
                        typeface = Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                        maxLines = 1
                        if (decorated) {
                            background = CosmeticPilot.namePlate(activity, cosmeticTheme)
                            setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3))
                        }
                    })
                    addView(TextView(activity).apply {
                        text = profile.publicId.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "#SIN ID"
                        setTextColor(Color.parseColor("#B9AD92"))
                        textSize = 12f
                        includeFontPadding = false
                    })
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun statsRow(
        activity: Activity,
        stats: PlayerStats,
        compact: Boolean,
        cosmeticTheme: String
    ): View {
        val values = if (stats.hasProgress) {
            listOf(
                "PARTIDAS" to stats.matches.toString(),
                "VICTORIAS" to stats.wins.toString(),
                "RATIO" to "${stats.winRatePercent}%"
            )
        } else {
            listOf("PARTIDAS" to "--", "VICTORIAS" to "--", "RATIO" to "--")
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, if (compact) 48 else 54)
            ).apply {
                bottomMargin = dp(activity, 8)
            }
            values.forEachIndexed { index, (label, value) ->
                addView(
                    statChip(activity, label, value, compact, cosmeticTheme),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (index > 0) leftMargin = dp(activity, 6)
                    }
                )
            }
        }
    }

    private fun statChip(
        activity: Activity,
        label: String,
        value: String,
        compact: Boolean,
        cosmeticTheme: String
    ): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (decorated) {
                CosmeticPilot.profileSurface(activity, cosmeticTheme)
            } else {
                chipBackground(activity, "#251A10", "#6B4F2A")
            }
            addView(TextView(activity).apply {
                text = value
                setTextColor(
                    if (decorated) CosmeticPilot.accentColor(cosmeticTheme)
                    else activity.getColor(R.color.accent_gold)
                )
                textSize = if (compact) 15f else 17f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
            addView(TextView(activity).apply {
                text = label
                setTextColor(
                    if (decorated) CosmeticPilot.textColor(cosmeticTheme)
                    else Color.parseColor("#B9AD92")
                )
                textSize = if (compact) 8.5f else 9.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
        }
    }

    private fun compactFavoriteRole(
        activity: Activity,
        favoriteRoleKey: String,
        cosmeticTheme: String
    ): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        val entry = ProfileRoleCatalog.find(favoriteRoleKey)
        return TextView(activity).apply {
            text = "Rol favorito: ${entry.role.name.uppercase()}"
            setTextColor(
                if (decorated) CosmeticPilot.textColor(cosmeticTheme)
                else Color.parseColor("#F3D488")
            )
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = if (decorated) {
                CosmeticPilot.profileSurface(activity, cosmeticTheme)
            } else {
                chipBackground(activity, "#1C140D", "#6B4F2A")
            }
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setOnClickListener { RoleDetailDialog.show(activity, entry.role) }
        }
    }

    private fun roleRow(
        activity: Activity,
        favoriteRoleKey: String,
        cosmeticTheme: String
    ): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        val entry = ProfileRoleCatalog.find(favoriteRoleKey)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
            background = if (decorated) {
                CosmeticPilot.profileSurface(activity, cosmeticTheme)
            } else {
                chipBackground(activity, "#20150D", "#6B4F2A")
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { RoleDetailDialog.show(activity, entry.role) }

            val imageFrame = FrameLayout(activity).apply {
                setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2))
                background = if (decorated) {
                    CosmeticPilot.gameplayRoleFrame(activity, theme = cosmeticTheme)
                } else {
                    chipBackground(activity, "#0D0906", "#6B4F2A", 7)
                }
            }
            val image = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            setRoleImage(activity, image, entry.role)
            imageFrame.addView(
                image,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(imageFrame, LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)))
            addView(TextView(activity).apply {
                text = entry.role.name.uppercase()
                setTextColor(
                    if (decorated) CosmeticPilot.textColor(cosmeticTheme)
                    else Color.parseColor("#F3D488")
                )
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(activity, 12), 0, 0, 0)
            })
        }
    }

    private fun emoteRow(activity: Activity, ids: List<String>, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                EmoteLoadout.normalizeIds(ids).forEach { id ->
                    val spec = EmoteCatalog.byId(id) ?: return@forEach
                    addView(
                        ImageView(activity).apply {
                            setEmoteImageResource(spec.imageRes)
                            background = if (decorated) {
                                CosmeticPilot.emoteFrame(activity, theme = cosmeticTheme)
                            } else {
                                chipBackground(activity, "#20150D", spec.toneHex, 8)
                            }
                            setPadding(dp(activity, 5), dp(activity, 5), dp(activity, 5), dp(activity, 5))
                            contentDescription = spec.tooltipText()
                            androidx.appcompat.widget.TooltipCompat.setTooltipText(this, spec.tooltipText())
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                EmoteSoundEffects.play(activity, spec.emotionKey)
                                showEmotePreview(activity, spec)
                            }
                        },
                        LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)).apply {
                            rightMargin = dp(activity, 8)
                        }
                    )
                }
            })
        }
    }

    private fun achievementRow(activity: Activity, ids: List<String>, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        val achievements = ids.mapNotNull(ProfileCustomizationCatalog::achievementById)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            if (achievements.isEmpty()) {
                addView(
                    textBlock(
                        activity,
                        "Sin logros destacados.",
                        12f,
                        if (decorated) CosmeticPilot.textColor(cosmeticTheme)
                        else Color.parseColor("#B9AD92"),
                        cosmeticTheme
                    )
                )
            } else {
                achievements.forEachIndexed { index, achievement ->
                    addView(
                        TextView(activity).apply {
                            text = achievement.shortName.uppercase()
                            setTextColor(Color.parseColor(achievement.rarity.borderColorHex))
                            textSize = 11.5f
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            background = if (decorated) {
                                CosmeticPilot.achievementFrame(
                                    activity,
                                    Color.parseColor(achievement.rarity.borderColorHex),
                                    emphasized = false,
                                    theme = cosmeticTheme
                                )
                            } else {
                                chipBackground(activity, "#20150D", achievement.rarity.borderColorHex, 9)
                            }
                            setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7))
                            setOnClickListener { AchievementDetailDialog.show(activity, achievement) }
                        },
                        LinearLayout.LayoutParams(0, dp(activity, 42), 1f).apply {
                            if (index > 0) leftMargin = dp(activity, 7)
                        }
                    )
                }
            }
        }
    }

    private fun miniButtons(activity: Activity, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(activity, 12)
            }
            addView(
                Button(activity).apply {
                    tag = "expand"
                    text = "PERFIL COMPLETO"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#211407"))
                    background = if (decorated) {
                        CosmeticPilot.primaryButton(activity, cosmeticTheme)
                    } else {
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(activity, 10).toFloat()
                            setColor(activity.getColor(R.color.accent_gold))
                        }
                    }
                    minHeight = 0
                    minWidth = 0
                    setPadding(dp(activity, 6), 0, dp(activity, 6), 0)
                },
                LinearLayout.LayoutParams(0, dp(activity, 42), 1.3f).apply {
                    rightMargin = dp(activity, 8)
                }
            )
            addView(
                Button(activity).apply {
                    tag = "close"
                    text = "CERRAR"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        if (decorated) CosmeticPilot.accentColor(cosmeticTheme)
                        else activity.getColor(R.color.accent_gold)
                    )
                    background = if (decorated) {
                        CosmeticPilot.profileSurface(activity, cosmeticTheme)
                    } else {
                        chipBackground(activity, "#2A2318", "#6B4F2A", 10)
                    }
                    minHeight = 0
                    minWidth = 0
                },
                LinearLayout.LayoutParams(0, dp(activity, 42), 1f)
            )
        }
    }

    private fun editButton(activity: Activity, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return Button(activity).apply {
            tag = "edit"
            text = "EDITAR PERFIL"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#211407"))
            background = if (decorated) {
                CosmeticPilot.primaryButton(activity, cosmeticTheme)
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(activity, 10).toFloat()
                    setColor(activity.getColor(R.color.accent_gold))
                }
            }
            layoutParams = LinearLayout.LayoutParams(dp(activity, 190), dp(activity, 42)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(activity, 14)
            }
        }
    }

    private fun sectionTitle(activity: Activity, text: String, cosmeticTheme: String): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return TextView(activity).apply {
            this.text = text
            setTextColor(
                if (decorated) CosmeticPilot.accentColor(cosmeticTheme)
                else Color.parseColor("#C49A52")
            )
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(activity, 13), 0, dp(activity, 6))
        }
    }

    private fun textBlock(
        activity: Activity,
        text: String,
        sizeSp: Float,
        color: Int,
        cosmeticTheme: String = CosmeticPilot.THEME_CLASSIC
    ): View {
        val decorated = CosmeticPilot.isDecoratedTheme(cosmeticTheme)
        return TextView(activity).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            gravity = Gravity.CENTER
            setPadding(dp(activity, 7), dp(activity, 8), dp(activity, 7), dp(activity, 8))
            background = if (decorated) {
                CosmeticPilot.profileSurface(activity, cosmeticTheme)
            } else {
                chipBackground(activity, "#14100A", "#332719", 8)
            }
        }
    }

    private fun showExpandedAvatar(
        activity: Activity,
        avatarEntry: ProfileRoleCatalog.Entry,
        useLocalPhoto: Boolean,
        playGamesAvatarUri: String
    ) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_profile_avatar, null)
        val avatar = content.findViewById<ImageView>(R.id.expandedProfileAvatar)
        val showingLocalPhoto = useLocalPhoto &&
            LocalProfilePhotoStore.render(activity, avatar, false)
        val fallbackRes = DrawableResourceCatalog.resolveOrPlaceholder(
            avatarEntry.role.imageResName
        )
        val showingPlayGamesPhoto = !showingLocalPhoto &&
            PlayGamesProfileAvatar.render(
                context = activity,
                image = avatar,
                uriValue = playGamesAvatarUri,
                fallbackDrawableRes = fallbackRes
            )
        if (!showingLocalPhoto && !showingPlayGamesPhoto) {
            setRoleImage(activity, avatar, avatarEntry.role)
        }
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        content.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxW = (activity.resources.displayMetrics.widthPixels - dp(activity, 24))
                    .coerceAtLeast(dp(activity, 220))
                val maxH = (activity.resources.displayMetrics.heightPixels - dp(activity, 48))
                    .coerceAtLeast(dp(activity, 220))
                setLayout(dp(activity, 320).coerceAtMost(maxW), dp(activity, 320).coerceAtMost(maxH))
            }
            if (!showingLocalPhoto && !showingPlayGamesPhoto) {
                alignAvatarToFocus(avatar, avatarEntry.verticalFocus)
            }
        }
        showTracked(activity, dialog)
    }

    private fun hasLocalPhotoFor(activity: Activity, profile: PlayerProfile): Boolean {
        return LocalProfilePhotoStore.isEnabledForProfile(activity, profile)
    }

    private fun showEmotePreview(activity: Activity, spec: EmoteSpec) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 16))
            background = chipBackground(activity, "#14100A", spec.toneHex, 16)
            addView(
                ImageView(activity).apply { setEmoteImageResource(spec.imageRes) },
                LinearLayout.LayoutParams(dp(activity, 168), dp(activity, 168))
            )
            addView(
                TextView(activity).apply {
                    text = spec.label
                    setTextColor(Color.parseColor("#F3D488"))
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(activity, 10), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            if (spec.description.isNotBlank()) {
                addView(
                    TextView(activity).apply {
                        text = spec.description
                        setTextColor(Color.parseColor("#B9AD92"))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(activity, 4), 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        val dialog = AlertDialog.Builder(activity).setView(container).create()
        container.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        showTracked(activity, dialog)
    }

    private fun showTracked(activity: Activity, dialog: AlertDialog) {
        openDialogs.getOrPut(activity) { linkedSetOf() }.add(dialog)
        dialog.setOnDismissListener {
            openDialogs[activity]?.let { dialogs ->
                dialogs.remove(dialog)
                if (dialogs.isEmpty()) openDialogs.remove(activity)
            }
        }
        dialog.show()
    }

    private fun panelBackground(activity: Activity): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(activity, 12).toFloat()
            setColor(Color.parseColor("#F0140F08"))
            setStroke(dp(activity, 1), activity.getColor(R.color.accent_gold))
        }
    }

    private fun chipBackground(
        activity: Activity,
        fillHex: String,
        strokeHex: String,
        radiusDp: Int = 9
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(activity, radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(activity, 1), Color.parseColor(strokeHex))
        }
    }

    private fun setRoleImage(activity: Activity, image: ImageView, role: Role) {
        image.setImageResource(DrawableResourceCatalog.resolveOrPlaceholder(role.imageResName))
    }

    private fun alignAvatarToFocus(image: ImageView, verticalFocus: Float) {
        image.post {
            val drawable = image.drawable ?: return@post
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            if (drawableWidth <= 0f || drawableHeight <= 0f) return@post
            val scale = maxOf(
                image.width / drawableWidth,
                image.height / drawableHeight
            ) * 1.12f
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val horizontalOffset = (image.width - scaledWidth) / 2f
            val focusedY = scaledHeight * verticalFocus.coerceIn(0f, 1f)
            val verticalOffset = (image.height / 2f - focusedY)
                .coerceIn(image.height - scaledHeight, 0f)
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(horizontalOffset, verticalOffset)
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
