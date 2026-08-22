package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.PlayGames
import java.net.URI

/**
 * Foto publica elegida por el jugador en Google Play Juegos.
 *
 * No descarga ni vuelve a subir la imagen a Firebase: conserva el URI entregado por Play
 * Games Services y deja que ImageManager lo cargue y lo guarde en su cache local.
 */
object PlayGamesProfileAvatar {
    const val MAX_URI_LENGTH = 1000

    fun requestCurrent(
        activity: Activity,
        onReady: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!PlayGamesConfig.isIdentityConfigured(activity)) {
            onFailure("Play Juegos todavía no está configurado para esta versión.")
            return
        }
        PlayGames.getGamesSignInClient(activity).isAuthenticated
            .addOnSuccessListener { authentication ->
                if (authentication.isAuthenticated) {
                    loadCurrent(activity, onReady, onFailure)
                } else {
                    PlayGamesIdentity.requestInteractiveSignIn(activity) { result ->
                        when (result) {
                            is PlayGamesIdentityResult.Linked ->
                                loadCurrent(activity, onReady, onFailure)
                            PlayGamesIdentityResult.NotAuthenticated ->
                                onFailure("Iniciá sesión en Play Juegos para usar esa foto.")
                            PlayGamesIdentityResult.NotConfigured ->
                                onFailure("Play Juegos todavía no está configurado.")
                            is PlayGamesIdentityResult.Failed -> {
                                OnlineDebugLog.e("play_games_avatar_sign_in_failure", result.error)
                                onFailure("No pudimos conectar con Play Juegos. Probá nuevamente.")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_avatar_auth_status_failure", error)
                onFailure("No pudimos consultar tu perfil de Play Juegos.")
            }
    }

    fun normalize(uriValue: String): String {
        val candidate = uriValue.trim().take(MAX_URI_LENGTH)
        if (candidate.isBlank() || candidate.length != uriValue.trim().length) return ""
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
        // Un content:// pertenece al dispositivo que lo emitio y no serviria en el lobby de
        // los demas. Play Juegos publica sus imagenes compartibles como HTTPS.
        return candidate.takeIf { uri.scheme.equals("https", ignoreCase = true) }.orEmpty()
    }

    fun render(
        context: Context,
        image: ImageView,
        uriValue: String,
        fallbackDrawableRes: Int
    ): Boolean {
        val normalized = normalize(uriValue)
        if (normalized.isBlank()) return false
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        return runCatching {
            ImageManager.create(context.applicationContext).loadImage(
                image,
                Uri.parse(normalized),
                fallbackDrawableRes
            )
            true
        }.getOrElse { error ->
            OnlineDebugLog.e("play_games_avatar_render_failure", error)
            false
        }
    }

    private fun loadCurrent(
        activity: Activity,
        onReady: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        PlayGames.getPlayersClient(activity).currentPlayer
            .addOnSuccessListener { player ->
                val uri = normalize(
                    (player.hiResImageUri ?: player.iconImageUri)?.toString().orEmpty()
                )
                if (uri.isBlank()) {
                    onFailure("Tu perfil de Play Juegos no tiene una foto pública disponible.")
                } else {
                    onReady(uri)
                }
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_avatar_player_failure", error)
                onFailure("No pudimos obtener tu foto de Play Juegos.")
            }
    }
}
