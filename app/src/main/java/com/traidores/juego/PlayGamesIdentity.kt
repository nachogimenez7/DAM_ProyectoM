package com.traidores.juego

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PlayGamesAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

sealed class PlayGamesIdentityResult {
    object NotConfigured : PlayGamesIdentityResult()
    object NotAuthenticated : PlayGamesIdentityResult()
    data class Linked(val firebaseUid: String, val gamerTag: String) : PlayGamesIdentityResult()
    data class Failed(val error: Exception) : PlayGamesIdentityResult()
}

/**
 * Puente entre la sesión automática de Play Games Services v2 y Firebase Auth.
 *
 * Cuando Firebase todavía es anónimo se vincula la credencial para conservar el uid. Si esa
 * cuenta de Play Games ya existía en Firebase, se adopta su uid y se recupera el perfil público.
 */
object PlayGamesIdentity {
    @Volatile
    private var requestInProgress = false

    @Volatile
    private var completedFirebaseUid = ""

    fun ensureLinked(
        activity: Activity,
        onResult: (PlayGamesIdentityResult) -> Unit = {}
    ) {
        if (AccountDeletionPreferences.isAutoLinkSuppressed(activity)) {
            onResult(PlayGamesIdentityResult.NotAuthenticated)
            return
        }
        if (!PlayGamesConfig.isIdentityConfigured(activity)) {
            onResult(PlayGamesIdentityResult.NotConfigured)
            return
        }
        if (requestInProgress) return

        val current = FirebaseAuth.getInstance().currentUser
        if (
            current != null &&
            current.uid == completedFirebaseUid &&
            hasPlayGamesProvider()
        ) {
            onResult(
                PlayGamesIdentityResult.Linked(
                    firebaseUid = current.uid,
                    gamerTag = ""
                )
            )
            return
        }

        requestInProgress = true
        val signInClient = PlayGames.getGamesSignInClient(activity)
        signInClient.isAuthenticated
            .addOnSuccessListener { authentication ->
                if (!authentication.isAuthenticated) {
                    finish(onResult, PlayGamesIdentityResult.NotAuthenticated)
                    return@addOnSuccessListener
                }
                loadGamerTagAndLink(activity, onResult)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_auth_status_failure", error)
                finish(onResult, PlayGamesIdentityResult.Failed(error))
            }
    }

    fun hasPlayGamesProvider(): Boolean {
        return FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.any { it.providerId == PlayGamesAuthProvider.PROVIDER_ID } == true
    }

    fun isReady(activity: Activity): Boolean {
        return PlayGamesConfig.isIdentityConfigured(activity) && hasPlayGamesProvider()
    }

    fun requestInteractiveSignIn(
        activity: Activity,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        if (!PlayGamesConfig.isIdentityConfigured(activity)) {
            onResult(PlayGamesIdentityResult.NotConfigured)
            return
        }
        PlayGames.getGamesSignInClient(activity)
            .signIn()
            .addOnSuccessListener { result ->
                if (!result.isAuthenticated) {
                    onResult(PlayGamesIdentityResult.NotAuthenticated)
                    return@addOnSuccessListener
                }
                AccountDeletionPreferences.allowAutoLink(activity)
                requestInProgress = false
                completedFirebaseUid = ""
                ensureLinked(activity, onResult)
            }
            .addOnFailureListener { onResult(PlayGamesIdentityResult.Failed(it)) }
    }

    /**
     * Obtiene una credencial nueva de Play Games y la usa para confirmar una operación
     * sensible de Firebase, como eliminar la cuenta.
     */
    fun reauthenticate(activity: Activity, onDone: (Exception?) -> Unit) {
        if (!PlayGamesConfig.isIdentityConfigured(activity)) {
            onDone(IllegalStateException("Play Games no está configurado."))
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !hasPlayGamesProvider()) {
            onDone(IllegalStateException("La cuenta no está vinculada con Play Games."))
            return
        }
        PlayGames.getGamesSignInClient(activity)
            .signIn()
            .addOnSuccessListener { result ->
                if (!result.isAuthenticated) {
                    onDone(IllegalStateException("No se confirmó la sesión de Play Games."))
                    return@addOnSuccessListener
                }
                PlayGames.getGamesSignInClient(activity)
                    .requestServerSideAccess(
                        PlayGamesConfig.webClientId(activity),
                        false
                    )
                    .addOnSuccessListener { serverAuthCode ->
                        user.reauthenticate(
                            PlayGamesAuthProvider.getCredential(serverAuthCode)
                        )
                            .addOnSuccessListener { onDone(null) }
                            .addOnFailureListener(onDone)
                    }
                    .addOnFailureListener(onDone)
            }
            .addOnFailureListener(onDone)
    }

    private fun loadGamerTagAndLink(
        activity: Activity,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        PlayGames.getPlayersClient(activity)
            .currentPlayer
            .addOnCompleteListener { playerTask ->
                val gamerTag = playerTask.takeIf { it.isSuccessful }
                    ?.result
                    ?.displayName
                    .orEmpty()
                    .trim()
                    .take(18)
                if (hasPlayGamesProvider()) {
                    AccountLink.refreshClaims {
                        completeLinkedIdentity(
                            activity,
                            gamerTag,
                            preferGamerTag = profileStillUsesFallback(activity),
                            onResult
                        )
                    }
                } else {
                    requestFirebaseCredential(activity, gamerTag, onResult)
                }
            }
    }

    private fun requestFirebaseCredential(
        activity: Activity,
        gamerTag: String,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        PlayGames.getGamesSignInClient(activity)
            .requestServerSideAccess(
                PlayGamesConfig.webClientId(activity),
                false
            )
            .addOnSuccessListener { serverAuthCode ->
                val credential = PlayGamesAuthProvider.getCredential(serverAuthCode)
                linkCredential(activity, gamerTag, credential, onResult)
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_server_auth_code_failure", error)
                finish(onResult, PlayGamesIdentityResult.Failed(error))
            }
    }

    private fun linkCredential(
        activity: Activity,
        gamerTag: String,
        credential: com.google.firebase.auth.AuthCredential,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        OnlineTempIdentity.ensureAuthenticated(activity)
            .addOnSuccessListener {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    finish(
                        onResult,
                        PlayGamesIdentityResult.Failed(
                            IllegalStateException("Firebase no creó la sesión anónima.")
                        )
                    )
                    return@addOnSuccessListener
                }

                if (hasPlayGamesProvider()) {
                    AccountLink.refreshClaims {
                        completeLinkedIdentity(activity, gamerTag, preferGamerTag = false, onResult)
                    }
                    return@addOnSuccessListener
                }

                val wasAnonymous = user.isAnonymous
                user.linkWithCredential(credential)
                    .addOnSuccessListener {
                        AccountLink.refreshClaims {
                            completeLinkedIdentity(
                                activity,
                                gamerTag,
                                preferGamerTag = wasAnonymous,
                                onResult
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        if (error is FirebaseAuthUserCollisionException) {
                            adoptExistingPlayGamesAccount(
                                activity,
                                gamerTag,
                                credential,
                                onResult
                            )
                        } else {
                            OnlineDebugLog.e("play_games_firebase_link_failure", error)
                            finish(onResult, PlayGamesIdentityResult.Failed(error))
                        }
                    }
            }
            .addOnFailureListener { error ->
                finish(onResult, PlayGamesIdentityResult.Failed(error))
            }
    }

    private fun adoptExistingPlayGamesAccount(
        activity: Activity,
        gamerTag: String,
        credential: com.google.firebase.auth.AuthCredential,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid.orEmpty()
                if (uid.isBlank()) {
                    finish(
                        onResult,
                        PlayGamesIdentityResult.Failed(
                            IllegalStateException("Play Games no devolvió una identidad Firebase.")
                        )
                    )
                    return@addOnSuccessListener
                }
                OnlineTempIdentity.adopt(activity, uid)
                PlayerPublicIdentity.clearPublicId(activity)
                AccountLink.recoverPublicId(activity, uid) {
                    AccountLink.refreshClaims {
                        completeLinkedIdentity(
                            activity,
                            gamerTag,
                            preferGamerTag = profileStillUsesFallback(activity),
                            onResult
                        )
                    }
                }
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("play_games_firebase_adopt_failure", error)
                finish(onResult, PlayGamesIdentityResult.Failed(error))
            }
    }

    private fun completeLinkedIdentity(
        activity: Activity,
        gamerTag: String,
        preferGamerTag: Boolean,
        onResult: (PlayGamesIdentityResult) -> Unit
    ) {
        if (preferGamerTag && gamerTag.isNotBlank()) {
            PlayerProfileStore.saveRecoveredProfile(
                context = activity,
                name = OnlineRoomFirestore.normalizedPlayerName(gamerTag),
                bio = "",
                avatarKey = "",
                bannerKey = "",
                favoriteRoleKey = ""
            )
            PlayGamesCloudSave.markLocalChanged(activity)
        }

        PlayerPublicIdentity.ensurePublicId(
            context = activity,
            firestore = FirebaseFirestore.getInstance(),
            onReady = {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                completedFirebaseUid = uid
                PlayGamesProgressSync.onAuthenticated(activity)
                OnlineDebugLog.i("play_games_firebase_link_success uid=$uid")
                finish(
                    onResult,
                    PlayGamesIdentityResult.Linked(uid, gamerTag)
                )
            },
            onFailure = { error ->
                OnlineDebugLog.e("play_games_public_id_failure", error)
                finish(onResult, PlayGamesIdentityResult.Failed(error))
            }
        )
    }

    private fun profileStillUsesFallback(activity: Activity): Boolean {
        val name = PlayerProfileStore.loadHumanProfile(activity).name
        return name == "Jugador" || GuestIdentity.isValidGuestName(name)
    }

    private fun finish(
        onResult: (PlayGamesIdentityResult) -> Unit,
        result: PlayGamesIdentityResult
    ) {
        requestInProgress = false
        onResult(result)
    }
}
