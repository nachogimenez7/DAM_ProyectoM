package com.traidores.juego

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        configureEdgeToEdgeWindow()
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applySystemBarInsets()
    }

    @Suppress("DEPRECATION")
    private fun configureEdgeToEdgeWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    /** Keep controls inside the system insets while the artwork covers the entire window. */
    private fun applySystemBarInsets() {
        val content = findViewById<FrameLayout>(android.R.id.content) ?: return
        val screen = content.getChildAt(0) as? ViewGroup ?: return
        val fullBleedBackground = moveScreenBackgroundToWindow(content, screen)
        val initialPadding = Insets.of(
            content.paddingLeft,
            content.paddingTop,
            content.paddingRight,
            content.paddingBottom
        )
        (content as? ViewGroup)?.apply {
            clipChildren = false
            clipToPadding = false
        }
        WindowCompat.getInsetsController(window, content).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val safeArea = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            if (fullBleedBackground || drawsFullScreenContent()) {
                // The artwork is a sibling of the safe-area layout, so it fills the real
                // window without scaling or depending on parents allowing overflow.
                view.setPadding(0, 0, 0, 0)
                val layout = screen.layoutParams as FrameLayout.LayoutParams
                val left = if (fullBleedBackground) initialPadding.left + safeArea.left else 0
                val top = if (fullBleedBackground) initialPadding.top + safeArea.top else 0
                val right = if (fullBleedBackground) initialPadding.right + safeArea.right else 0
                val bottom = if (fullBleedBackground) initialPadding.bottom + safeArea.bottom else 0
                if (layout.leftMargin != left || layout.topMargin != top ||
                    layout.rightMargin != right || layout.bottomMargin != bottom) {
                    layout.setMargins(left, top, right, bottom)
                    screen.layoutParams = layout
                }
            } else {
                view.setPadding(
                    initialPadding.left + safeArea.left,
                    initialPadding.top + safeArea.top,
                    initialPadding.right + safeArea.right,
                    initialPadding.bottom + safeArea.bottom
                )
                extendBackgroundBehindSystemBars(content)
            }
            onSystemBarInsetsChanged(safeArea)
            windowInsets
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun moveScreenBackgroundToWindow(content: FrameLayout, screen: ViewGroup): Boolean {
        val background = screen.getChildAt(0) as? ImageView ?: return false
        if (background.layoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            background.layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT) return false
        screen.removeViewAt(0)
        content.addView(background, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        // Flat dimming layers belong to the artwork. Leaving one inside the inset layout
        // creates a visible bright strip above and below it on devices with system buttons.
        val shade = screen.getChildAt(0)
        if (shade is View && shade !is ViewGroup && shade !is ImageView &&
            shade.layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT &&
            shade.layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT &&
            shade.background != null && !shade.isClickable && !shade.isFocusable) {
            screen.removeViewAt(0)
            content.addView(shade, 1, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        return true
    }

    private fun extendBackgroundBehindSystemBars(content: View) {
        val background = findFullScreenBackground(content) ?: return
        val parent = background.parent as? ViewGroup ?: return
        parent.clipChildren = false
        parent.clipToPadding = false
        parent.post {
            if (parent.width <= 0 || parent.height <= 0) return@post
            val contentPadding = Insets.of(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                content.paddingBottom
            )
            // RelativeLayout re-measures MATCH_PARENT children against its safe-area-sized
            // bounds, so changing the layout params alone gets clamped back on the next pass.
            // Scale the already measured background instead; the parents do not clip it, which
            // lets the artwork actually cover the status and navigation areas on every device.
            background.pivotX = 0f
            background.pivotY = 0f
            background.scaleX = (parent.width + contentPadding.left + contentPadding.right)
                .toFloat() / parent.width.toFloat()
            background.scaleY = (parent.height + contentPadding.top + contentPadding.bottom)
                .toFloat() / parent.height.toFloat()
            background.translationX = -contentPadding.left.toFloat()
            background.translationY = -contentPadding.top.toFloat()
        }
    }

    private fun findFullScreenBackground(view: View): ImageView? {
        if (view is ImageView && view.layoutParams?.let {
                it.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                    it.height == ViewGroup.LayoutParams.MATCH_PARENT
            } == true
        ) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findFullScreenBackground(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    protected open fun onSystemBarInsetsChanged(safeArea: Insets) = Unit

    protected open fun drawsFullScreenContent(): Boolean = false

    override fun onStart() {
        super.onStart()
        MusicManager.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.onActivityStopped()
    }
}
