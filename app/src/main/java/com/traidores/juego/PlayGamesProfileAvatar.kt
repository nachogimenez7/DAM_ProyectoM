package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.PlayGames
import com.google.firebase.auth.FirebaseAuth
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

    /**
     * Adopta la foto de Play Juegos como opción inicial, sin abrir ninguna ventana de acceso.
     * Si Google no entrega una URL HTTPS compartible, se conserva el avatar ilustrado.
     */
    fun applyCurrentAsDefault(
        activity: Activity,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (
            !PlayGamesIdentity.isReady(activity) ||
            !ProfileAvatarSourceStore.allowsAutomaticPlayGamesPhoto(activity)
        ) {
            onComplete(false)
            return
        }
        PlayGames.getPlayersClient(activity).currentPlayer
            .addOnSuccessListener { player ->
                resolveFirstUsableUri(
                    activity = activity,
                    candidates = shareableUris(
                        player.hiResImageUri?.toString().orEmpty(),
                        player.iconImageUri?.toString().orEmpty(),
                        linkedGoogleAccountPhoto()
                    ),
                    onReady = { uri ->
                        activity.getSharedPreferences(
                            ProfileActivity.PREFS_NAME,
                            Context.MODE_PRIVATE
                        ).edit()
                            .putBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
                            .putString(ProfileActivity.PREF_PLAY_GAMES_AVATAR_URI, uri)
                            .apply()
                        LocalProfilePhotoStore.deleteSavedPhoto(activity)
                        ProfileAvatarSourceStore.saveSelection(
                            activity,
                            ProfileAvatarSource.PLAY_GAMES
                        )
                        onComplete(true)
                    },
                    onUnavailable = {
                        OnlineDebugLog.i("play_games_default_avatar_unavailable")
                        onComplete(false)
                    }
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_default_avatar_failure", error)
                onComplete(false)
            }
    }

    fun render(
        context: Context,
        image: ImageView,
        uriValue: String,
        fallbackDrawableRes: Int,
        onUnavailable: (() -> Unit)? = null
    ): Boolean {
        val normalized = normalize(uriValue)
        if (normalized.isBlank()) return false
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.setImageResource(fallbackDrawableRes)
        return runCatching {
            ImageManager.create(context.applicationContext).loadImage(
                ImageManager.OnImageLoadedListener { _, drawable, isRequestedDrawable ->
                    image.post {
                        if (isRequestedDrawable && drawable != null) {
                            image.scaleType = ImageView.ScaleType.CENTER_CROP
                            image.setImageDrawable(drawable)
                        } else {
                            image.scaleType = ImageView.ScaleType.FIT_CENTER
                            image.setImageResource(fallbackDrawableRes)
                            onUnavailable?.invoke()
                        }
                    }
                },
                Uri.parse(normalized)
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
                resolveFirstUsableUri(
                    activity = activity,
                    candidates = shareableUris(
                        player.hiResImageUri?.toString().orEmpty(),
                        player.iconImageUri?.toString().orEmpty(),
                        linkedGoogleAccountPhoto()
                    ),
                    onReady = onReady,
                    onUnavailable = {
                        onFailure(
                            "Google no comparte una foto que Traidores pueda mostrar. " +
                                "Mantenemos tu avatar ilustrado; podés agregar una foto pública " +
                                "a tu cuenta y reintentar."
                        )
                    }
                )
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_avatar_player_failure", error)
                onFailure("No pudimos obtener tu foto de Play Juegos.")
            }
    }

    /** Descarta URI locales, duplicados o demasiado largos antes de intentar descargarlos. */
    private fun shareableUris(vararg candidates: String): List<String> {
        return candidates.asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    /**
     * Comprueba la imagen real antes de anunciar que fue seleccionada. Algunas cuentas
     * entregan una URL de Play Juegos válida en apariencia pero sin contenido público; antes
     * eso hacía que ImageManager mostrara silenciosamente el retrato ilustrado de respaldo.
     */
    private fun resolveFirstUsableUri(
        activity: Activity,
        candidates: List<String>,
        onReady: (String) -> Unit,
        onUnavailable: () -> Unit
    ) {
        if (candidates.isEmpty()) {
            onUnavailable()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val manager = ImageManager.create(activity.applicationContext)

        fun attempt(index: Int) {
            if (index >= candidates.size) {
                onUnavailable()
                return
            }
            val candidate = candidates[index]
            var completed = false
            val timeout = Runnable {
                if (!completed) {
                    completed = true
                    attempt(index + 1)
                }
            }
            handler.postDelayed(timeout, IMAGE_CHECK_TIMEOUT_MS)
            runCatching {
                manager.loadImage(
                    ImageManager.OnImageLoadedListener { _, drawable, isRequestedDrawable ->
                        if (!completed) {
                            completed = true
                            handler.removeCallbacks(timeout)
                            if (isRequestedDrawable && drawable != null) {
                                onReady(candidate)
                            } else {
                                attempt(index + 1)
                            }
                        }
                    },
                    Uri.parse(candidate)
                )
            }.onFailure { error ->
                if (!completed) {
                    completed = true
                    handler.removeCallbacks(timeout)
                    OnlineDebugLog.e("play_games_avatar_candidate_failure", error)
                    attempt(index + 1)
                }
            }
        }

        attempt(0)
    }

    /**
     * Play Juegos puede autenticar correctamente y devolver ambos URI de jugador en null.
     * Cuando la cuenta de Traidores está vinculada con Google, Firebase Auth conserva la foto
     * pública de esa misma identidad y sirve como alternativa sin subir archivos a Storage.
     */
    private fun linkedGoogleAccountPhoto(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: return ""
        val googleProvider = user.providerData.firstOrNull { provider ->
            provider.providerId == "google.com"
        } ?: return ""
        return googleProvider.photoUrl?.toString()
            ?: user.photoUrl?.toString()
            ?: ""
    }

    private const val IMAGE_CHECK_TIMEOUT_MS = 6_000L
}
