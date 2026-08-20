package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Introduccion breve y repetible. Explica solamente el ciclo esencial; los detalles de salas,
 * chat y roles quedan en Ayuda para no convertir el primer inicio en un manual obligatorio.
 */
object TutorialDialog {
    private const val PREFS_NAME = "TraidoresPrefs"
    // v2 migra el tutorial que se mostraba por error en el menú: quienes ya lo vieron allí
    // lo reciben una única vez en su ubicación correcta, al entrar al primer lobby.
    private const val PREF_TUTORIAL_SEEN = "tutorial_lobby_seen_v2"

    private data class Page(
        val icon: Int,
        val title: Int,
        val body: Int,
        val hint: Int
    )

    private val pages = listOf(
        Page(
            R.drawable.ic_chronicle_role_cards,
            R.string.tutorial_role_title,
            R.string.tutorial_role_body,
            R.string.tutorial_role_hint
        ),
        Page(
            R.drawable.ic_chronicle_moon,
            R.string.tutorial_night_title,
            R.string.tutorial_night_body,
            R.string.tutorial_night_hint
        ),
        Page(
            R.drawable.ic_chat_speaking,
            R.string.tutorial_debate_title,
            R.string.tutorial_debate_body,
            R.string.tutorial_debate_hint
        ),
        Page(
            R.drawable.ic_chronicle_vote_point,
            R.string.tutorial_vote_title,
            R.string.tutorial_vote_body,
            R.string.tutorial_vote_hint
        )
    )

    fun hasBeenSeen(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_TUTORIAL_SEEN, false)
    }

    fun show(
        activity: Activity,
        markAsSeen: Boolean = true,
        onFinished: () -> Unit = {}
    ) {
        // La primera apertura ya cuenta como visto. Guardarlo antes de navegar las páginas
        // evita que un cierre abrupto, el botón Atrás del emulador o un cambio de Activity
        // hagan reaparecer el tutorial en el siguiente lobby. Abrirlo desde Ayuda usa
        // markAsSeen=false y no altera esta preferencia.
        if (markAsSeen) {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_TUTORIAL_SEEN, true)
                .commit()
        }
        var pageIndex = 0
        var finished = false

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(activity.dp(8), 0, activity.dp(8), 0)
        }
        val progress = TextView(activity).apply {
            setTextColor(activity.getColor(R.color.text_muted))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        val icon = ImageView(activity).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val title = TextView(activity).apply {
            setTextColor(activity.getColor(R.color.accent_gold))
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(8), 0, 0)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val body = TextView(activity).apply {
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.14f)
            setPadding(0, activity.dp(10), 0, 0)
        }
        val hint = TextView(activity).apply {
            setTextColor(activity.getColor(R.color.text_secondary))
            textSize = 13f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            setPadding(activity.dp(8), activity.dp(14), activity.dp(8), 0)
        }
        content.addView(progress)
        content.addView(icon, LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)).apply {
            topMargin = activity.dp(8)
        })
        content.addView(title, matchWrap())
        content.addView(body, matchWrap())
        content.addView(hint, matchWrap())

        val dialog = GameDialog.custom(
            activity = activity,
            contentView = content,
            widthDp = 410,
            negativeLabel = activity.getString(R.string.tutorial_skip),
            neutralLabel = activity.getString(R.string.tutorial_previous),
            positiveLabel = activity.getString(R.string.tutorial_next)
        )
        dialog.setCanceledOnTouchOutside(false)

        val previous: Button? = dialog.findViewById(R.id.gameDialogNeutral)
        val next: Button? = dialog.findViewById(R.id.gameDialogPositive)
        val skip: Button? = dialog.findViewById(R.id.gameDialogNegative)

        fun complete() {
            if (finished) return
            finished = true
            dialog.dismiss()
            onFinished()
        }

        fun renderPage() {
            val page = pages[pageIndex]
            progress.text = activity.getString(
                R.string.tutorial_progress,
                pageIndex + 1,
                pages.size
            )
            icon.setImageResource(page.icon)
            title.setText(page.title)
            body.setText(page.body)
            hint.setText(page.hint)
            previous?.visibility = if (pageIndex == 0) View.GONE else View.VISIBLE
            next?.setText(
                if (pageIndex == pages.lastIndex) {
                    R.string.tutorial_finish
                } else {
                    R.string.tutorial_next
                }
            )
        }

        skip?.setOnClickListener { complete() }
        previous?.setOnClickListener {
            if (pageIndex > 0) {
                pageIndex -= 1
                renderPage()
                GameplayEffects.play(activity, GameplayEffect.PANEL)
            }
        }
        next?.setOnClickListener {
            if (pageIndex == pages.lastIndex) {
                complete()
            } else {
                pageIndex += 1
                renderPage()
                GameplayEffects.play(activity, GameplayEffect.PANEL)
            }
        }
        dialog.setOnCancelListener { complete() }
        renderPage()
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun Activity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
