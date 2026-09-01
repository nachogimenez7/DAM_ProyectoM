package com.traidores.juego

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.android.gms.games.PlayGamesSdk
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

class TraidoresApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OnlineDebugLog.configure(
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )
        configureAppCheck()
        FirebaseEmulatorConfig.configureIfEnabled()
        configurePlayGames()
        configureFirestore()
        TraidoresNotifications.createChannel(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            ShortSoundPool.release()
            MusicManager.releaseForBackground()
        }
    }

    private fun configurePlayGames() {
        if (PlayGamesConfig.isSdkConfigured(this)) {
            PlayGamesSdk.initialize(this)
        }
    }

    private fun configureAppCheck() {
        FirebaseApp.initializeApp(this)
        AppCheckProviderInstaller.install(FirebaseAppCheck.getInstance())
    }

    private fun configureFirestore() {
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }
}
