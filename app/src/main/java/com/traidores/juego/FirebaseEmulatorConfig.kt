package com.traidores.juego

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth

internal object FirebaseEmulatorConfig {
    // Retain the configured instance. SDK 22 mutates its RepoInfo on first access;
    // resolving getInstance() again afterward can return an unconfigured database.
    val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    val usesAuthoritativeOnlineStart: Boolean
        get() = BuildConfig.USE_ONLINE_AUTHORITY_EMULATOR

    fun configureIfEnabled() {
        if (!usesAuthoritativeOnlineStart) return
        val host = BuildConfig.FIREBASE_EMULATOR_HOST.trim()
        require(host.isNotBlank()) { "FIREBASE_EMULATOR_HOST no puede estar vacio" }

        val isolatedV3 = BuildConfig.SERVER_ONLINE_V3
        FirebaseAuth.getInstance().useEmulator(host, if (isolatedV3) 19099 else 9099)
        FirebaseStorage.getInstance().useEmulator(host, 9199)
        FirebaseFirestore.getInstance().useEmulator(host, if (isolatedV3) 18081 else FIRESTORE_PORT)
        database.useEmulator(host, if (isolatedV3) 19000 else DATABASE_PORT)
        FirebaseFunctions.getInstance(OnlineStartCallableContract.REGION)
            .useEmulator(host, if (isolatedV3) 15001 else FUNCTIONS_PORT)
        OnlineDebugLog.i(
            "firebase_emulators_enabled host=$host " +
                "firestore=${if (isolatedV3) 18081 else FIRESTORE_PORT} " +
                "database=${if (isolatedV3) 19000 else DATABASE_PORT} functions=${if (isolatedV3) 15001 else FUNCTIONS_PORT}"
        )
    }

    private const val FIRESTORE_PORT = 8081
    private const val DATABASE_PORT = 9000
    private const val FUNCTIONS_PORT = 5001
}
