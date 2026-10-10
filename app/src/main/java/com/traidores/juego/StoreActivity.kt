package com.traidores.juego

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat

/**
 * Store of permanent cosmetics. The list is fixed (see [PurchaseCatalog]); Google Play only
 * supplies the real price and the purchase sheet. While Play has no product for an item the
 * button reads «PRÓXIMAMENTE» and the price shown is the reference one.
 */
class StoreActivity : BaseActivity() {
    private data class Item(
        val id: String,
        val title: String,
        val description: String,
        val referencePrice: String,
        val grants: Set<String>,
        val theme: String? = null,
        val background: Int? = null,
        val win: Int? = null,
        val lose: Int? = null,
        val emotes: List<Int> = emptyList(),
        val featured: Boolean = false,
        val tag: String? = null,
        val extras: List<String> = emptyList()
    )

    private val items by lazy { catalog() }
    private val playPrices = mutableMapOf<String, String>()
    private var storeLoaded = false
    private lateinit var list: LinearLayout
    private lateinit var notice: TextView
    private var stopObserving: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        stopObserving = AccountEntitlements.observe { runOnUiThread { render() } }
        render()
        PlayPurchases.loadProducts(this) { products ->
            if (isFinishing || isDestroyed) return@loadProducts
            products.forEach { if (it.price.isNotBlank()) playPrices[it.id] = it.price }
            storeLoaded = true
            render()
        }
    }

    override fun onDestroy() {
        stopObserving?.invoke()
        super.onDestroy()
    }

    private fun catalog(): List<Item> {
        val seal = Item(
            PurchaseCatalog.PACK, "Pack del Benefactor",
            "Con el estilo Sello Carmesí completo y sin anuncios. Con esta compra ayudás a un desarrollador independiente a sostener los servidores y seguir mejorando el juego. ¡Gracias por apoyar!",
            "$7.999",
            setOf("sin_anuncios", "estilo_sello", "marco_sello", "insignia_sello", "banners_pack", "emotes_pack",
                "emote_brindis", "placa_sello", "burbujas_sello", "festejo_sello"),
            theme = CosmeticPilot.THEME_SELLO, background = R.drawable.profile_background_sello,
            win = R.drawable.festejo_sello_gana, lose = R.drawable.festejo_sello_pierde,
            featured = true, tag = "EL MÁS COMPLETO",
            extras = listOf("Sin anuncios", "Fondo y marco", "Insignia y 3 banners", "Emotes y Brindis", "Placa de nombre", "Burbujas de chat", "Festejo del Sello")
        )
        val noAds = Item(PurchaseCatalog.NO_ADS, "Sin anuncios", "Ningún video entre partidas, nunca más.", "$3.999", setOf("sin_anuncios"))
        val fire = Item(PurchaseCatalog.STYLE_FIRE, "Forja Infernal",
            "Fondo de lava, marco ardiente, placa y burbujas a juego. Festejo en llamas y rabieta al perder.", "$2.999",
            setOf("estilo_forja_infernal"), CosmeticPilot.THEME_FIRE, R.drawable.profile_background_fire,
            R.drawable.festejo_fuego_gana, R.drawable.festejo_fuego_pierde)
        val sea = Item(PurchaseCatalog.STYLE_SEA, "Abismo Real",
            "Fondo marino con marco y burbujas de agua. Un sol con anteojos sale por detrás de tu carta.", "$2.999",
            setOf("estilo_abismo_real"), CosmeticPilot.THEME_SEA, R.drawable.profile_background_sea,
            R.drawable.festejo_mar_gana, R.drawable.festejo_mar_pierde)
        val space = Item(PurchaseCatalog.STYLE_SPACE, "Espacial",
            "Órbita violeta con estrellas. Tu carta queda violeta y estrellada al terminar.", "$2.999",
            setOf("estilo_espacial"), CosmeticPilot.THEME_SPACE, R.drawable.profile_background_space,
            R.drawable.festejo_espacio_gana, R.drawable.festejo_espacio_pierde)
        val three = Item(PurchaseCatalog.STYLES_THREE, "Los 3 estilos",
            "Forja Infernal, Abismo Real y Espacial, con todos sus festejos.", "$5.999",
            setOf("estilo_espacial", "estilo_abismo_real", "estilo_forja_infernal"), featured = true, tag = "AHORRÁS $2.998")
        val memes = Item(PurchaseCatalog.EMOTES_MEMES, "Memes Culturales",
            "Hermosa mañana, Ruidito de mate y Me duermo zzz. Pueden venir más emotes próximamente en este pack.", "$2.999",
            setOf("emotes_memes"),
            emotes = listOf(R.drawable.reaction_premium_hermosa_manana, R.drawable.reaction_premium_mate, R.drawable.reaction_premium_dormida))
        return listOf(seal, fire, sea, space, three, memes, noAds)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildScreen(): View {
        val root = RelativeLayout(this)
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.fondo_menu); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = null
        }, RelativeLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(Color.parseColor("#80000000")) }, RelativeLayout.LayoutParams(-1, -1))
        val back = ImageButton(this).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.bg_btn_dark)
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(getColor(R.color.text_secondary))
            contentDescription = "Volver"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }
        root.addView(back, RelativeLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(14), dp(14), dp(14), dp(14)) })
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true; setPadding(dp(16), 0, dp(16), dp(28)) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, back.id) })
        notice = TextView(this)
        return root
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        list.addView(text("TIENDA", 28f, getColor(R.color.accent_gold), bree = true).apply { gravity = Gravity.CENTER })
        list.addView(text("Lo que comprás es tuyo para siempre y solo cambia cómo se ve el juego.", 15f, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(14))
        })
        val owned = AccountEntitlements.items(this)
        if (storeLoaded && playPrices.isEmpty()) {
            list.addView(note("Las compras se activan cuando termine la configuración con Google Play. Mientras tanto podés mirar y probar los estilos. Los precios son de referencia."))
        }
        if (GuestIdentity.isGuest()) {
            list.addView(note("Para comprar, primero vinculá tu cuenta desde tu perfil. Así tus compras te siguen si cambiás de teléfono."))
        }
        items.forEachIndexed { index, item ->
            if (index == 1) list.addView(section("ESTILOS CON FESTEJO"))
            if (index == 5) list.addView(section("EMOTES"))
            if (index == 6) list.addView(section("SIN ANUNCIOS"))
            list.addView(card(item, owned))
        }
        list.addView(Button(this).apply {
            text = "RESTAURAR COMPRAS"; isAllCaps = false
            setTextColor(getColor(R.color.accent_gold)); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { restore() }
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4) })
    }

    private fun text(value: String, size: Float, color: Int, bree: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bree) typeface = ResourcesCompat.getFont(this@StoreActivity, R.font.bree_serif)
    }

    private fun section(label: String) = text(label, 13f, getColor(R.color.text_secondary), bree = true).apply {
        letterSpacing = 0.1f; setPadding(dp(4), dp(10), 0, dp(8))
    }

    private fun note(message: String) = text(message, 13f, Color.parseColor("#E0A24A")).apply {
        setPadding(dp(12), dp(8), dp(12), dp(8)); setLineSpacing(0f, 1.1f)
        background = GradientDrawable().apply { setColor(Color.parseColor("#33E0A24A")); setStroke(dp(1), Color.parseColor("#E0A24A")); cornerRadius = dp(10).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun card(item: Item, owned: Set<String>): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6231810"))
                setStroke(dp(if (item.featured) 2 else 1), Color.parseColor(if (item.featured) "#E6BF73" else "#5B4524"))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        item.tag?.let { box.addView(text(it, 11f, Color.parseColor("#FF8A8A")).apply { letterSpacing = 0.08f; setPadding(0, 0, 0, dp(4)) }) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        when {
            item.background != null -> top.addView(backgroundThumb(item), LinearLayout.LayoutParams(dp(78), dp(138)).apply { marginEnd = dp(12) })
            item.id == PurchaseCatalog.STYLES_THREE -> top.addView(trioStrip(), LinearLayout.LayoutParams(dp(92), dp(60)).apply { marginEnd = dp(12) })
            item.emotes.isNotEmpty() -> Unit
            else -> top.addView(text(if (item.id == PurchaseCatalog.NO_ADS) "🚫" else "✦", 30f, getColor(R.color.accent_gold)).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(item.title, 18f, Color.parseColor("#FFF0C7"), bree = true))
        info.addView(text(item.description, 13f, getColor(R.color.text_secondary)).apply { setLineSpacing(0f, 1.1f) })
        if (item.win != null && item.lose != null) info.addView(festejoRow(item))
        if (item.emotes.isNotEmpty()) info.addView(emoteRow(item))
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(top)
        if (item.extras.isNotEmpty()) {
            box.addView(text(item.extras.joinToString("  ·  ") { "✓ $it" }, 12f, getColor(R.color.text_secondary)).apply { setPadding(0, dp(10), 0, 0); setLineSpacing(0f, 1.15f) })
        }
        box.addView(buyRow(item, owned))
        return box
    }

    private fun backgroundThumb(item: Item): View {
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply { setStroke(dp(1), Color.parseColor("#5B4524")); cornerRadius = dp(10).toFloat() }
            clipToOutline = true
        }
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        frame.addView(ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeResource(resources, item.background!!, options))
            scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = null
        }, FrameLayout.LayoutParams(-1, -1))
        item.win?.let { win ->
            frame.addView(ImageView(this).apply { setImageResource(win); contentDescription = null },
                FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER).apply { topMargin = dp(10) })
        }
        return frame
    }

    private fun emoteRow(item: Item): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(10), 0, 0)
        item.emotes.forEach { res -> addView(ImageView(this@StoreActivity).apply { setImageResource(res); contentDescription = null },
            LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(8) }) }
    }

    /** The three style stickers overlapping, so the bundle reads as "all of them". */
    private fun trioStrip(): View = FrameLayout(this).apply {
        listOf(R.drawable.festejo_fuego_gana, R.drawable.festejo_mar_gana, R.drawable.festejo_espacio_gana).forEachIndexed { i, res ->
            addView(ImageView(this@StoreActivity).apply { setImageResource(res); contentDescription = null },
                FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dp(23 * i) })
        }
    }

    private fun festejoRow(item: Item) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        listOf(item.win!! to "ganar", item.lose!! to "perder").forEach { (res, label) ->
            addView(LinearLayout(this@StoreActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(this@StoreActivity).apply { setImageResource(res); contentDescription = "Festejo al $label" }, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(text(label, 10.5f, getColor(R.color.text_secondary)))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        }
    }

    private fun buyRow(item: Item, owned: Set<String>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        val has = owned.containsAll(item.grants)
        val playPrice = playPrices[item.id]
        row.addView(text(playPrice ?: item.referencePrice, 20f, Color.parseColor("#FFF0C7")), LinearLayout.LayoutParams(0, -2, 1f))
        if (item.theme != null) row.addView(button("PROBAR", primary = false) { preview(item) }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
        val label = when {
            has && item.id == PurchaseCatalog.NO_ADS && owned.contains("estilo_sello") -> "INCLUIDO EN EL PACK"
            has -> "COMPRADO"
            playPrice == null -> "PRÓXIMAMENTE"
            else -> "COMPRAR"
        }
        row.addView(button(label, primary = !has && playPrice != null) { buy(item) }.apply { isEnabled = !has && playPrice != null },
            LinearLayout.LayoutParams(-2, dp(44)))
        return row
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f
        setBackgroundResource(if (primary) R.drawable.bg_winner_action_primary else R.drawable.bg_winner_action_secondary)
        setTextColor(Color.parseColor(if (primary) "#211407" else "#F3D488"))
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun buy(item: Item) {
        if (GuestIdentity.isGuest()) {
            Toast.makeText(this, "Vinculá tu cuenta desde el perfil para comprar.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }
        PlayPurchases.buy(this, item.id) { outcome -> report(outcome, "Compra lista. ¡Gracias!") }
    }

    private fun restore() {
        if (GuestIdentity.isGuest()) {
            Toast.makeText(this, "Vinculá tu cuenta desde el perfil para restaurar compras.", Toast.LENGTH_LONG).show()
            return
        }
        PlayPurchases.restore(this) { outcome -> report(outcome, "Compras restauradas.") }
    }

    private fun report(outcome: PurchaseOutcome, done: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val message = when (outcome) {
                is PurchaseOutcome.Granted -> done
                PurchaseOutcome.Pending -> "Tu pago está pendiente. Cuando Google Play lo confirme se activa solo."
                PurchaseOutcome.Cancelled -> "Compra cancelada."
                is PurchaseOutcome.Failed -> outcome.message
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            render()
        }
    }

    /** «Probar»: the player's real profile, drawn with this style and nothing saved. */
    private fun preview(item: Item) {
        val theme = item.theme ?: return
        startActivity(Intent(this, ProfileActivity::class.java).putExtra(EXTRA_STORE_PREVIEW_THEME, theme))
    }
}

/** Opens the profile showing a style that is not equipped; used by the store's «Probar». */
internal const val EXTRA_STORE_PREVIEW_THEME = "extra_store_preview_theme"

/** Which styles need a purchase. Off in release until Play Console sells them (see STORE_LOCKS_ENABLED). */
internal object StoreLocks {
    fun isLocked(context: android.content.Context, theme: String): Boolean {
        if (!BuildConfig.STORE_LOCKS_ENABLED) return false
        val needed = when (theme) {
            CosmeticPilot.THEME_SELLO -> "estilo_sello"
            CosmeticPilot.THEME_FIRE -> "estilo_forja_infernal"
            CosmeticPilot.THEME_SEA -> "estilo_abismo_real"
            CosmeticPilot.THEME_SPACE -> "estilo_espacial"
            else -> return false
        }
        return !AccountEntitlements.has(context, needed)
    }
}

/**
 * «¿Cansado de los videos?»: a small strip, never a window. It follows every 2nd video, once
 * the player is back on a lobby screen, and can be closed. No daily limit.
 */
internal object NoAdsStrip {
    private const val PREFS = "traidores_ads"
    private const val KEY = "strip_pending"

    fun markPending(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()

    fun showIfPending(activity: android.app.Activity) {
        val prefs = activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY, false)) return
        prefs.edit().putBoolean(KEY, false).apply()
        if (AdFreeEntitlement.isAdFree(activity)) return
        val content = activity.findViewById<android.widget.FrameLayout>(android.R.id.content) ?: return
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val strip = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(6), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F0231810")); setStroke(dp(2), android.graphics.Color.parseColor("#E6BF73")); cornerRadius = dp(14).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        val text = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
        text.addView(android.widget.TextView(activity).apply {
            this.text = "¿Cansado de los videos?"; textSize = 14f; setTextColor(android.graphics.Color.parseColor("#FFF0C7"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        text.addView(android.widget.TextView(activity).apply {
            this.text = "«Sin anuncios», para siempre."; textSize = 12f; setTextColor(activity.getColor(R.color.text_secondary))
        })
        strip.addView(text, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        strip.addView(android.widget.Button(activity).apply {
            this.text = "VER"; isAllCaps = false; textSize = 13f
            setBackgroundResource(R.drawable.bg_winner_action_primary); setTextColor(android.graphics.Color.parseColor("#211407"))
            setOnClickListener { content.removeView(strip); activity.startActivity(android.content.Intent(activity, StoreActivity::class.java)) }
        }, android.widget.LinearLayout.LayoutParams(dp(64), dp(40)).apply { marginStart = dp(8) })
        strip.addView(android.widget.TextView(activity).apply {
            this.text = "✕"; textSize = 18f; setTextColor(activity.getColor(R.color.text_secondary)); gravity = android.view.Gravity.CENTER
            contentDescription = "Cerrar"; setOnClickListener { content.removeView(strip) }
        }, android.widget.LinearLayout.LayoutParams(dp(44), dp(44)))
        val navBar = androidx.core.view.ViewCompat.getRootWindowInsets(content)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        content.addView(strip, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), dp(14) + navBar)
        })
    }
}
