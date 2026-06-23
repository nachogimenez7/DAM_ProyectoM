package com.traidores.juego

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyGameplayOrientationPreference()
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        MusicManager.onActivityStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.onActivityStopped()
    }

    private fun applyGameplayOrientationPreference() {
        if (!usesGameplayOrientationPreference()) return
        val verticalEnabled = getSharedPreferences(AudioPreferences.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_GAMEPLAY_VERTICAL_DEV, true)
        requestedOrientation = if (verticalEnabled) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun usesGameplayOrientationPreference(): Boolean {
        return this is LobbyBrowserActivity ||
            this is LobbyActivity ||
            this is AssigningRolesActivity ||
            this is GameplayMockActivity
    }

    companion object {
        const val PREF_GAMEPLAY_VERTICAL_DEV = "pref_gameplay_vertical_dev"
    }
}
