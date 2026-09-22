package com.traidores.juego

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
    }

    /**
     * Android 15 impone edge-to-edge y Android 16 elimina la posibilidad de desactivarlo.
     * Los controles siguen recibiendo el inset en el contenedor raíz común, mientras que la
     * imagen de fondo se extiende visualmente detrás de las barras del sistema.
     */
    private fun applySystemBarInsets() {
        val content = findViewById<View>(android.R.id.content) ?: return
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
            view.setPadding(
                initialPadding.left + safeArea.left,
                initialPadding.top + safeArea.top,
                initialPadding.right + safeArea.right,
                initialPadding.bottom + safeArea.bottom
            )
            // The content view may already carry system padding from the platform. Use the
            // padding that is actually applied, not only the freshly reported inset: this
            // keeps the background continuous through both the status and navigation areas.
            extendBackgroundBehindSystemBars(content)
            onSystemBarInsetsChanged(safeArea)
            windowInsets
        }
        ViewCompat.requestApplyInsets(content)
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

    override fun onStart() {
        super.onStart()
        MusicManager.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.onActivityStopped()
    }
}
