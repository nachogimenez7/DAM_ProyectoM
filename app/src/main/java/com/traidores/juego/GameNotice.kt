package com.traidores.juego

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import java.util.WeakHashMap

object GameNotice {
    enum class Duration(val milliseconds: Long) {
        SHORT(2_300L),
        LONG(3_800L)
    }

    private data class ActiveNotice(
        val view: View,
        val dismiss: Runnable
    )

    private val handler = Handler(Looper.getMainLooper())
    private val active = WeakHashMap<Activity, ActiveNotice>()

    fun show(
        activity: Activity,
        message: CharSequence,
        duration: Duration = Duration.SHORT
    ) {
        if (activity.isFinishing || activity.isDestroyed || message.isBlank()) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { show(activity, message, duration) }
            return
        }
        active.remove(activity)?.let { previous ->
            handler.removeCallbacks(previous.dismiss)
            (previous.view.parent as? ViewGroup)?.removeView(previous.view)
        }
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val notice = TextView(activity).apply {
            text = message
            gravity = Gravity.CENTER
            maxLines = 3
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_game_notice)
            elevation = activity.dp(12).toFloat()
            alpha = 0f
            translationY = activity.dp(12).toFloat()
            isClickable = true
            contentDescription = message
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = activity.dp(18)
            rightMargin = activity.dp(18)
            bottomMargin = activity.dp(28)
        }
        container.addView(notice, params)
        val dismiss = Runnable { dismiss(activity, notice) }
        active[activity] = ActiveNotice(notice, dismiss)
        notice.setOnClickListener {
            handler.removeCallbacks(dismiss)
            dismiss(activity, notice)
        }
        notice.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(170L)
            .start()
        notice.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
        handler.postDelayed(dismiss, duration.milliseconds)
    }

    private fun dismiss(activity: Activity, notice: View) {
        if (notice.parent == null) return
        notice.animate()
            .alpha(0f)
            .translationY(activity.dp(8).toFloat())
            .setDuration(150L)
            .withEndAction {
                (notice.parent as? ViewGroup)?.removeView(notice)
                if (active[activity]?.view === notice) active.remove(activity)
            }
            .start()
    }

    private fun Activity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
