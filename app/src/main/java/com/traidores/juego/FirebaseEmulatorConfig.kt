package com.traidores.juego

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

internal object FirebaseEmulatorConfig {
    val usesAuthoritativeOnlineStart: Boolean
        get() = BuildConfig.USE_ONLINE_AUTHORITY_EMULATOR

    fun configureIfEnabled() {
        if (!usesAuthoritativeOnlineStart) return
        val host = BuildConfig.FIREBASE_EMULATOR_HOST.trim()
        require(host.isNotBlank()) { "FIREBASE_EMULATOR_HOST no puede estar vacio" }

        FirebaseFirestore.getInstance().useEmulator(host, FIRESTORE_PORT)
        FirebaseDatabase.getInstance().useEmulator(host, DATABASE_PORT)
        FirebaseFunctions.getInstance(OnlineStartCallableContract.REGION)
            .useEmulator(host, FUNCTIONS_PORT)
        OnlineDebugLog.i(
            "firebase_emulators_enabled host=$host " +
                "firestore=$FIRESTORE_PORT database=$DATABASE_PORT functions=$FUNCTIONS_PORT"
        )
    }

    private const val FIRESTORE_PORT = 8081
    private const val DATABASE_PORT = 9000
    private const val FUNCTIONS_PORT = 5001
}
