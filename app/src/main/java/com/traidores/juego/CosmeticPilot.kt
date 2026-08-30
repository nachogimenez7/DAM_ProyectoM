package com.traidores.juego

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/**
 * Primer piloto visual de los conjuntos cosméticos.
 *
 * La selección se guarda junto al resto del perfil para que Play Games pueda respaldarla.
 * Firebase solo recibe el identificador corto del tema cuando el perfil ya se publica en una
 * sala; ninguna animación o render genera escrituras adicionales.
 */
object CosmeticPilot {
    const val THEME_CLASSIC = "classic"
    const val THEME_SPACE = "space"

    const val accentCyan = "#62E9FF"
    const val accentViolet = "#965CFF"
    const val textCyan = "#BFF8FF"

    private const val PREFERENCES = "TraidoresPrefs"
    private const val KEY_THEME = "profile_cosmetic_theme"
    private const val LEGACY_PREFERENCES = "cosmetic_loadout"
    private const val LEGACY_KEY_THEME = "equipped_theme"

    fun selectedTheme(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(KEY_THEME, null)?.let(::normalizeTheme)?.let { stored ->
            return stored
        }
        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY_THEME, null)
            ?.let(::normalizeTheme)
            ?: THEME_SPACE
        preferences.edit().putString(KEY_THEME, legacy).apply()
        return legacy
    }

    fun isSpaceEnabled(context: Context): Boolean = selectedTheme(context) == THEME_SPACE

    fun isSpaceTheme(theme: String?): Boolean = normalizeTheme(theme) == THEME_SPACE

    fun normalizeTheme(theme: String?): String? {
        return theme?.takeIf { it == THEME_CLASSIC || it == THEME_SPACE }
    }

    fun selectTheme(context: Context, theme: String) {
        val validTheme = normalizeTheme(theme) ?: THEME_CLASSIC
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, validTheme)
            .apply()
    }

    fun bubbleShell(context: Context): Drawable = layeredFrame(
        context = context,
        radiusDp = 14,
        outerColors = intArrayOf(
            Color.parseColor(accentCyan),
            Color.parseColor(accentViolet),
            Color.parseColor(accentCyan)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#2B174C"),
            Color.parseColor("#111A35"),
            Color.parseColor("#090B18")
        ),
        insetDp = 3
    )

    fun bubbleTail(context: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor("#2A1A4C"), Color.parseColor("#102B45"))
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(2).toFloat()
        setStroke(context.dpForCosmetic(1), Color.parseColor(accentCyan))
    }

    fun emoteFrame(context: Context, selected: Boolean = false): Drawable = layeredFrame(
        context = context,
        radiusDp = 11,
        outerColors = if (selected) {
            intArrayOf(
                Color.parseColor(accentCyan),
                Color.parseColor("#E4FAFF"),
                Color.parseColor(accentViolet)
            )
        } else {
            intArrayOf(
                Color.parseColor("#6F4CC4"),
                Color.parseColor(accentCyan),
                Color.parseColor("#6F4CC4")
            )
        },
        innerColors = intArrayOf(
            Color.parseColor("#25183B"),
            Color.parseColor("#101427"),
            Color.parseColor("#090B14")
        ),
        insetDp = if (selected) 3 else 2
    )

    fun avatarFrame(context: Context): Drawable {
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.parseColor(accentCyan),
                Color.parseColor(accentViolet),
                Color.parseColor(accentCyan)
            )
            orientation = GradientDrawable.Orientation.TL_BR
        }
        val darkRing = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#111326"))
            setStroke(context.dpForCosmetic(2), Color.parseColor("#B28AFF"))
        }
        return LayerDrawable(arrayOf(outer, darkRing)).apply {
            val inset = context.dpForCosmetic(4)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    fun namePlate(context: Context): Drawable = layeredFrame(
        context = context,
        radiusDp = 11,
        outerColors = intArrayOf(
            Color.parseColor(accentViolet),
            Color.parseColor(accentCyan),
            Color.parseColor(accentViolet)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#25163E"),
            Color.parseColor("#101528"),
            Color.parseColor("#171025")
        ),
        insetDp = 2
    )

    fun profileSurface(context: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.parseColor("#E51B2343"),
            Color.parseColor("#EB0C132B"),
            Color.parseColor("#E526153E")
        )
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(10).toFloat()
        setStroke(context.dpForCosmetic(1), Color.parseColor("#875CCFEA"))
    }

    fun profilePanelOverlay(context: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.parseColor("#B90B1530"),
            Color.parseColor("#D7070B1C"),
            Color.parseColor("#C5110923")
        )
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(12).toFloat()
        setStroke(context.dpForCosmetic(1), Color.parseColor("#7559DCF3"))
    }

    fun primaryButton(context: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.parseColor("#7447D8"),
            Color.parseColor("#257FCF"),
            Color.parseColor("#42DDF3")
        )
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(10).toFloat()
        setStroke(context.dpForCosmetic(1), Color.parseColor("#C9F9FF"))
    }

    fun bannerVeil(context: Context): Drawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.parseColor("#9A251154"),
            Color.parseColor("#65101738"),
            Color.parseColor("#8A063C61")
        )
    ).apply {
        shape = GradientDrawable.RECTANGLE
        setStroke(context.dpForCosmetic(1), Color.parseColor("#8B62E9FF"))
    }

    fun chatMessageBubble(context: Context): Drawable = layeredFrame(
        context = context,
        radiusDp = 12,
        outerColors = intArrayOf(
            Color.parseColor(accentCyan),
            Color.parseColor(accentViolet),
            Color.parseColor(accentCyan)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#EE2B174B"),
            Color.parseColor("#F0141C39"),
            Color.parseColor("#EE091321")
        ),
        insetDp = 2
    )

    fun gameplayPlayerPanel(context: Context): Drawable = layeredFrame(
        context = context,
        radiusDp = 15,
        outerColors = intArrayOf(
            Color.parseColor(accentViolet),
            Color.parseColor(accentCyan),
            Color.parseColor(accentViolet)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#EE241747"),
            Color.parseColor("#F00A1730"),
            Color.parseColor("#ED150B2A")
        ),
        insetDp = 2
    )

    fun gameplayRoleFrame(context: Context, stateColor: Int? = null): Drawable = layeredFrame(
        context = context,
        radiusDp = 9,
        outerColors = intArrayOf(
            Color.parseColor(accentCyan),
            stateColor ?: Color.parseColor(accentViolet),
            Color.parseColor(accentCyan)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#28183E"),
            Color.parseColor("#0B1429"),
            Color.parseColor("#171027")
        ),
        insetDp = 2
    )

    fun achievementFrame(
        context: Context,
        rarityColor: Int,
        emphasized: Boolean
    ): Drawable = layeredFrame(
        context = context,
        radiusDp = if (emphasized) 14 else 12,
        outerColors = intArrayOf(
            Color.parseColor(accentCyan),
            rarityColor,
            Color.parseColor(accentViolet)
        ),
        innerColors = intArrayOf(
            Color.parseColor("#F0201744"),
            Color.parseColor("#F00D1630"),
            Color.parseColor("#F0150C2A")
        ),
        insetDp = if (emphasized) 3 else 2
    )

    fun achievementMedalFrame(context: Context, rarityColor: Int): Drawable {
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor(accentCyan), rarityColor, Color.parseColor(accentViolet))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EE10162D"))
            setStroke(context.dpForCosmetic(1), Color.parseColor(accentCyan))
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = context.dpForCosmetic(3)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun layeredFrame(
        context: Context,
        radiusDp: Int,
        outerColors: IntArray,
        innerColors: IntArray,
        insetDp: Int
    ): Drawable {
        val outer = GradientDrawable(GradientDrawable.Orientation.TL_BR, outerColors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpForCosmetic(radiusDp).toFloat()
        }
        val inner = GradientDrawable(GradientDrawable.Orientation.TL_BR, innerColors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpForCosmetic((radiusDp - 2).coerceAtLeast(1)).toFloat()
            setStroke(context.dpForCosmetic(1), Color.parseColor("#6FE9FF"))
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = context.dpForCosmetic(insetDp)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun Context.dpForCosmetic(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
