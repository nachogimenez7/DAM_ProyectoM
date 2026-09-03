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
    const val THEME_SEA = "sea"
    const val THEME_FIRE = "fire"
    const val DEFAULT_THEME = THEME_CLASSIC

    const val accentCyan = "#62E9FF"
    const val accentViolet = "#965CFF"
    const val textCyan = "#BFF8FF"

    private const val PREFERENCES = "TraidoresPrefs"
    private const val KEY_THEME = "profile_cosmetic_theme"
    private const val KEY_THEME_EXPLICIT = "profile_cosmetic_theme_explicit"
    private const val LEGACY_PREFERENCES = "cosmetic_loadout"
    private const val LEGACY_KEY_THEME = "equipped_theme"

    fun selectedTheme(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_THEME, null)
        resolveStoredTheme(
            stored,
            preferences.getBoolean(KEY_THEME_EXPLICIT, false)
        )?.let { resolved ->
            if (resolved != normalizeTheme(stored)) {
                // Las versiones iniciales guardaban Espacial automáticamente aunque el
                // usuario nunca lo hubiera elegido. Esta migración lo devuelve a Clásico
                // una sola vez; una elección hecha desde el selector queda marcada abajo.
                preferences.edit().putString(KEY_THEME, resolved).apply()
            }
            return resolved
        }
        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY_THEME, null)
            ?.let(::normalizeTheme)
        val resolved = legacy ?: DEFAULT_THEME
        preferences.edit()
            .putString(KEY_THEME, resolved)
            .putBoolean(KEY_THEME_EXPLICIT, legacy != null)
            .apply()
        return resolved
    }

    fun isSpaceEnabled(context: Context): Boolean = selectedTheme(context) == THEME_SPACE

    fun isSpaceTheme(theme: String?): Boolean = normalizeTheme(theme) == THEME_SPACE

    fun isDecoratedTheme(theme: String?): Boolean {
        return normalizeTheme(theme)?.let { it != THEME_CLASSIC } == true
    }

    fun normalizeTheme(theme: String?): String? {
        return theme?.takeIf {
            it == THEME_CLASSIC || it == THEME_SPACE || it == THEME_SEA || it == THEME_FIRE
        }
    }

    fun selectTheme(context: Context, theme: String) {
        val validTheme = normalizeTheme(theme) ?: DEFAULT_THEME
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, validTheme)
            .putBoolean(KEY_THEME_EXPLICIT, true)
            .apply()
    }

    internal fun resolveStoredTheme(theme: String?, explicitlySelected: Boolean): String? {
        val normalized = normalizeTheme(theme) ?: return null
        return if (normalized == THEME_SPACE && !explicitlySelected) DEFAULT_THEME else normalized
    }

    fun displayName(theme: String?): String = when (normalizeTheme(theme)) {
        THEME_SPACE -> "Espacial"
        THEME_SEA -> "Abismo Real"
        THEME_FIRE -> "Forja Infernal"
        else -> "Clásico"
    }

    fun accentColor(theme: String?): Int = palette(theme).primary

    fun textColor(theme: String?): Int = palette(theme).text

    fun profileBackgroundRes(theme: String?): Int = when (normalizeTheme(theme)) {
        THEME_SPACE -> R.drawable.profile_background_space
        THEME_SEA -> R.drawable.profile_background_sea
        THEME_FIRE -> R.drawable.profile_background_fire
        else -> R.drawable.fondo_menu
    }

    fun profileShadeColor(theme: String?): Int = Color.parseColor(
        when (normalizeTheme(theme)) {
            THEME_SEA -> "#34000000"
            THEME_FIRE -> "#26000000"
            THEME_SPACE -> "#26000000"
            else -> "#52000000"
        }
    )

    fun bubbleShell(context: Context, theme: String = THEME_SPACE): Drawable = layeredFrame(
        context = context,
        radiusDp = 14,
        outerColors = palette(theme).outer,
        innerColors = palette(theme).bubble,
        insetDp = 3,
        innerStrokeColor = palette(theme).primary
    )

    fun bubbleTail(context: Context, theme: String = THEME_SPACE): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        palette(theme).bubble
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(2).toFloat()
        setStroke(context.dpForCosmetic(1), palette(theme).primary)
    }

    fun emoteFrame(
        context: Context,
        selected: Boolean = false,
        theme: String = THEME_SPACE
    ): Drawable = layeredFrame(
        context = context,
        radiusDp = 11,
        outerColors = if (selected) {
            intArrayOf(
                palette(theme).primary,
                palette(theme).text,
                palette(theme).secondary
            )
        } else {
            palette(theme).outer
        },
        innerColors = palette(theme).surface,
        insetDp = if (selected) 3 else 2,
        innerStrokeColor = palette(theme).primary
    )

    fun avatarFrame(context: Context, theme: String = THEME_SPACE): Drawable {
        val colors = palette(theme)
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            this.colors = colors.outer
            orientation = GradientDrawable.Orientation.TL_BR
        }
        val darkRing = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.avatarInner)
            setStroke(context.dpForCosmetic(2), colors.secondary)
        }
        return LayerDrawable(arrayOf(outer, darkRing)).apply {
            val inset = context.dpForCosmetic(4)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    fun namePlate(context: Context, theme: String = THEME_SPACE): Drawable = layeredFrame(
        context = context,
        radiusDp = 11,
        outerColors = palette(theme).outer,
        innerColors = palette(theme).surface,
        insetDp = 2,
        innerStrokeColor = palette(theme).primary
    )

    fun profileSurface(context: Context, theme: String = THEME_SPACE): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        palette(theme).surface
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(10).toFloat()
        setStroke(context.dpForCosmetic(1), palette(theme).softStroke)
    }

    fun profilePanelOverlay(context: Context, theme: String = THEME_SPACE): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        palette(theme).panel
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(12).toFloat()
        setStroke(context.dpForCosmetic(1), palette(theme).softStroke)
    }

    fun primaryButton(context: Context, theme: String = THEME_SPACE): Drawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        palette(theme).outer
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpForCosmetic(10).toFloat()
        setStroke(context.dpForCosmetic(1), palette(theme).text)
    }

    fun bannerVeil(context: Context, theme: String = THEME_SPACE): Drawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        palette(theme).veil
    ).apply {
        shape = GradientDrawable.RECTANGLE
        setStroke(context.dpForCosmetic(1), palette(theme).softStroke)
    }

    fun chatMessageBubble(context: Context, theme: String = THEME_SPACE): Drawable = layeredFrame(
        context = context,
        radiusDp = 12,
        outerColors = palette(theme).outer,
        innerColors = palette(theme).bubble,
        insetDp = 2,
        innerStrokeColor = palette(theme).primary
    )

    fun gameplayPlayerPanel(context: Context, theme: String = THEME_SPACE): Drawable = layeredFrame(
        context = context,
        radiusDp = 15,
        outerColors = palette(theme).outer,
        innerColors = palette(theme).panel,
        insetDp = 2,
        innerStrokeColor = palette(theme).primary
    )

    fun gameplayRoleFrame(
        context: Context,
        stateColor: Int? = null,
        theme: String = THEME_SPACE
    ): Drawable = layeredFrame(
        context = context,
        radiusDp = 9,
        outerColors = intArrayOf(
            palette(theme).primary,
            stateColor ?: palette(theme).secondary,
            palette(theme).primary
        ),
        innerColors = palette(theme).surface,
        insetDp = 2,
        innerStrokeColor = palette(theme).primary
    )

    fun achievementFrame(
        context: Context,
        rarityColor: Int,
        emphasized: Boolean,
        theme: String = THEME_SPACE
    ): Drawable = layeredFrame(
        context = context,
        radiusDp = if (emphasized) 14 else 12,
        outerColors = intArrayOf(
            palette(theme).primary,
            rarityColor,
            palette(theme).secondary
        ),
        innerColors = palette(theme).surface,
        insetDp = if (emphasized) 3 else 2,
        innerStrokeColor = palette(theme).primary
    )

    fun achievementMedalFrame(
        context: Context,
        rarityColor: Int,
        theme: String = THEME_SPACE
    ): Drawable {
        val colors = palette(theme)
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            this.colors = intArrayOf(colors.primary, rarityColor, colors.secondary)
            orientation = GradientDrawable.Orientation.TL_BR
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.avatarInner)
            setStroke(context.dpForCosmetic(1), colors.primary)
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
        insetDp: Int,
        innerStrokeColor: Int
    ): Drawable {
        val outer = GradientDrawable(GradientDrawable.Orientation.TL_BR, outerColors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpForCosmetic(radiusDp).toFloat()
        }
        val inner = GradientDrawable(GradientDrawable.Orientation.TL_BR, innerColors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpForCosmetic((radiusDp - 2).coerceAtLeast(1)).toFloat()
            setStroke(context.dpForCosmetic(1), innerStrokeColor)
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = context.dpForCosmetic(insetDp)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun palette(theme: String?): CosmeticPalette = when (normalizeTheme(theme)) {
        THEME_SEA -> CosmeticPalette(
            primary = Color.parseColor("#3DE6E0"),
            secondary = Color.parseColor("#D6BD76"),
            text = Color.parseColor("#C9FBF5"),
            outer = colors("#3DE6E0", "#D6BD76", "#58AFC0"),
            surface = colors("#ED0B3440", "#F0071A24", "#ED092A35"),
            panel = colors("#D00A2D37", "#EA05141D", "#DE08242E"),
            bubble = colors("#F00A3A46", "#F006202B", "#F004151D"),
            veil = colors("#8A063E4C", "#58051722", "#7A0C5560"),
            avatarInner = Color.parseColor("#071C25"),
            softStroke = Color.parseColor("#A03DE6E0")
        )
        THEME_FIRE -> CosmeticPalette(
            primary = Color.parseColor("#FF6A32"),
            secondary = Color.parseColor("#F2C15D"),
            text = Color.parseColor("#FFE0B2"),
            outer = colors("#B92A1D", "#F2C15D", "#FF6A32"),
            surface = colors("#F035110D", "#F00E0B0A", "#ED250906"),
            panel = colors("#DF2C0D09", "#F0080707", "#E21B0806"),
            bubble = colors("#F23B130D", "#F0120B08", "#F0060505"),
            veil = colors("#872D0905", "#4F0A0504", "#7A501308"),
            avatarInner = Color.parseColor("#170806"),
            softStroke = Color.parseColor("#A0FF6A32")
        )
        else -> CosmeticPalette(
            primary = Color.parseColor(accentCyan),
            secondary = Color.parseColor(accentViolet),
            text = Color.parseColor(textCyan),
            outer = colors(accentViolet, accentCyan, accentViolet),
            surface = colors("#E51B2343", "#EB0C132B", "#E526153E"),
            panel = colors("#B90B1530", "#D7070B1C", "#C5110923"),
            bubble = colors("#EE2B174B", "#F0141C39", "#EE091321"),
            veil = colors("#9A251154", "#65101738", "#8A063C61"),
            avatarInner = Color.parseColor("#111326"),
            softStroke = Color.parseColor("#875CCFEA")
        )
    }

    private fun colors(vararg hex: String): IntArray = hex.map { Color.parseColor(it) }.toIntArray()

    private data class CosmeticPalette(
        val primary: Int,
        val secondary: Int,
        val text: Int,
        val outer: IntArray,
        val surface: IntArray,
        val panel: IntArray,
        val bubble: IntArray,
        val veil: IntArray,
        val avatarInner: Int,
        val softStroke: Int
    )

    private fun Context.dpForCosmetic(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
