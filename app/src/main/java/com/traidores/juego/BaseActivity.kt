package com.traidores.juego

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applySystemBarInsets()
    }

    /**
     * Android 15 impone edge-to-edge y Android 16 elimina la posibilidad de desactivarlo.
     * La app todavía usa layouts clásicos pensados para ocupar el área segura, así que se
     * aplica el inset al contenedor raíz común de cada Activity. El fondo de la ventana puede
     * seguir dibujándose detrás de las barras, pero ningún botón ni texto queda tapado.
     */
    private fun applySystemBarInsets() {
        val content = findViewById<View>(android.R.id.content) ?: return
        val initialPadding = Insets.of(
            content.paddingLeft,
            content.paddingTop,
            content.paddingRight,
            content.paddingBottom
        )
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
            windowInsets
        }
        ViewCompat.requestApplyInsets(content)
    }

    override fun onStart() {
        super.onStart()
        MusicManager.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.onActivityStopped()
    }
}
