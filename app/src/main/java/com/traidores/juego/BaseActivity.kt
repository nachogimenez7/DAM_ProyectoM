package com.traidores.juego

import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        MusicManager.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.onActivityStopped()
    }
}
