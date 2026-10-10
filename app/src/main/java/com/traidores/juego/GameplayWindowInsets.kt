package com.traidores.juego

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.graphics.Insets

/** The same full-window scrim and safe modal panels on both tables. */
internal class GameplayWindowInsets {
    private val overlays = mutableListOf<FrameLayout>()
    fun apply(activity: Activity, safeArea: Insets) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val root = activity.findViewById<RelativeLayout>(R.id.gameplayRoot) ?: return
        val scrim = activity.findViewById<View>(R.id.topSystemBarScrim) ?: return
        if (scrim.parent === root) {
            root.removeView(scrim)
            content.addView(scrim, 1, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (120 * activity.resources.displayMetrics.density).toInt() + safeArea.top
            ))
        } else {
            scrim.layoutParams = scrim.layoutParams.apply { height = (120 * activity.resources.displayMetrics.density).toInt() + safeArea.top }
        }
        scrim.translationY = 0f

        // Full-screen transitions and dimmed dialogs need to cover the system bars as well.
        // Their panels still lay out inside the safe area so controls remain reachable.
        val moved = (0 until root.childCount)
            .map(root::getChildAt)
            .filterIsInstance<FrameLayout>()
            .filter { view ->
                view.layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                    view.layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT
            }
        moved.forEach { overlay ->
            root.removeView(overlay)
            content.addView(overlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            if (overlay.id != R.id.dayNightTransitionOverlay) {
                overlays.add(overlay)
            }
        }
        overlays.forEach { overlay ->
            overlay.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
        }
    }
}
