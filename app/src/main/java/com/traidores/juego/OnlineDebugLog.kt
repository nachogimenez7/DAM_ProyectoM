package com.traidores.juego

import android.util.Log

object OnlineDebugLog {
    const val TAG = "TraidoresOnline"
    @Volatile private var verbose = true

    fun configure(debuggable: Boolean) {
        verbose = debuggable
    }

    fun i(message: String) {
        if (!verbose) return
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (!verbose) return
        if (error == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, error)
        }
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
    }
}
