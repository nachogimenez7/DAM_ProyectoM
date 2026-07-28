package com.traidores.juego

import android.content.Context

object PlayGamesConfig {
    fun isSdkConfigured(context: Context): Boolean {
        return gameServicesProjectId(context)
            .takeIf { it != "0" }
            ?.all(Char::isDigit) == true
    }

    fun isIdentityConfigured(context: Context): Boolean {
        return isSdkConfigured(context) &&
            webClientId(context).endsWith(".apps.googleusercontent.com")
    }

    fun gameServicesProjectId(context: Context): String {
        return context.getString(R.string.game_services_project_id).trim()
    }

    fun webClientId(context: Context): String {
        return context.getString(R.string.play_games_web_client_id).trim()
    }

    fun configuredRemoteId(context: Context, resourceId: Int): String? {
        return context.getString(resourceId)
            .trim()
            .takeIf { it.isNotBlank() && !it.startsWith("REPLACE_", ignoreCase = true) }
    }
}
