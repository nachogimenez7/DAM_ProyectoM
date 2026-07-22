package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes

/**
 * Compatibility facade for old Toast call sites. It keeps their message and duration semantics,
 * while rendering every notice inside the visual language of the game.
 */
object GameToast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    fun makeText(context: Context, message: CharSequence, duration: Int): Notice {
        return Notice(context, message, duration)
    }

    fun makeText(context: Context, @StringRes messageRes: Int, duration: Int): Notice {
        return Notice(context, context.getText(messageRes), duration)
    }

    class Notice internal constructor(
        private val context: Context,
        private val message: CharSequence,
        private val duration: Int
    ) {
        fun show() {
            val activity = context.findActivity() ?: return
            GameNotice.show(
                activity,
                message,
                if (duration == LENGTH_LONG) GameNotice.Duration.LONG else GameNotice.Duration.SHORT
            )
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        var depth = 0
        while (depth < 8) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> {
                    val base = current.baseContext
                    if (base === current) return null
                    current = base
                }
                else -> return null
            }
            depth += 1
        }
        return null
    }
}
