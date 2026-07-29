package com.traidores.juego

import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

sealed class GoogleAccountResult {
    data class Linked(val email: String) : GoogleAccountResult()
    data class SignedIn(val email: String, val recoveredPublicId: String) : GoogleAccountResult()
    object Cancelled : GoogleAccountResult()
    data class Failed(val message: String, val error: Exception? = null) : GoogleAccountResult()
}

/**
 * Vincula Google a la identidad actual sin perder el uid anonimo. Si la cuenta ya existia,
 * entra en ella y recupera el perfil remoto, igual que el acceso por correo.
 */
object GoogleAccountLink {
    private const val PROVIDER_ID = "google.com"

    fun isConfigured(activity: AppCompatActivity): Boolean =
        serverClientId(activity).isNotBlank()

    fun hasGoogleProvider(): Boolean =
        FirebaseAuth.getInstance().currentUser?.providerData?.any {
            it.providerId == PROVIDER_ID
        } == true

    fun linkOrSignIn(
        activity: AppCompatActivity,
        onResult: (GoogleAccountResult) -> Unit
    ) {
        requestFirebaseCredential(activity, onResult)
    }

    fun reauthenticate(
        activity: AppCompatActivity,
        onDone: (Exception?) -> Unit
    ) {
        requestGoogleCredential(activity) { result ->
            when (result) {
                is CredentialResult.Ready -> {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        onDone(IllegalStateException("La sesion ya no esta disponible."))
                    } else {
                        user.reauthenticate(result.credential)
                            .addOnSuccessListener { onDone(null) }
                            .addOnFailureListener(onDone)
                    }
                }
                CredentialResult.Cancelled ->
                    onDone(IllegalStateException("Cancelaste la verificacion con Google."))
                is CredentialResult.Failed -> onDone(result.error)
            }
        }
    }

    private fun requestFirebaseCredential(
        activity: AppCompatActivity,
        onResult: (GoogleAccountResult) -> Unit
    ) {
        requestGoogleCredential(activity) { result ->
            when (result) {
                CredentialResult.Cancelled -> onResult(GoogleAccountResult.Cancelled)
                is CredentialResult.Failed -> onResult(
                    GoogleAccountResult.Failed(result.message, result.error)
                )
                is CredentialResult.Ready -> {
                    OnlineTempIdentity.ensureAuthenticated(activity)
                        .addOnSuccessListener {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                onResult(GoogleAccountResult.Failed("No se pudo abrir la sesion."))
                                return@addOnSuccessListener
                            }
                            if (hasGoogleProvider()) {
                                onResult(GoogleAccountResult.Linked(user.email.orEmpty()))
                                return@addOnSuccessListener
                            }
                            val wasAnonymous = user.isAnonymous
                            user.linkWithCredential(result.credential)
                                .addOnSuccessListener { authResult ->
                                    finishLinkedAccount(
                                        activity,
                                        authResult.user?.email.orEmpty(),
                                        onResult
                                    )
                                }
                                .addOnFailureListener { error ->
                                    if (error is FirebaseAuthUserCollisionException && wasAnonymous) {
                                        adoptExistingAccount(
                                            activity,
                                            result.credential,
                                            onResult
                                        )
                                    } else {
                                        onResult(
                                            GoogleAccountResult.Failed(
                                                if (error is FirebaseAuthUserCollisionException) {
                                                    "Esa cuenta de Google ya pertenece a otro perfil de Traidores."
                                                } else {
                                                    OnlineErrorMessages.forAction(
                                                        "No se pudo vincular Google",
                                                        error
                                                    )
                                                },
                                                error
                                            )
                                        )
                                    }
                                }
                        }
                        .addOnFailureListener { error ->
                            onResult(
                                GoogleAccountResult.Failed(
                                    OnlineErrorMessages.forAction(
                                        "No se pudo preparar el acceso",
                                        error
                                    ),
                                    error
                                )
                            )
                        }
                }
            }
        }
    }

    private fun requestGoogleCredential(
        activity: AppCompatActivity,
        onResult: (CredentialResult) -> Unit
    ) {
        val clientId = serverClientId(activity)
        if (clientId.isBlank()) {
            onResult(
                CredentialResult.Failed(
                    "Google todavia no esta configurado en Firebase. Podes usar correo mientras tanto.",
                    IllegalStateException("Falta default_web_client_id")
                )
            )
            return
        }
        activity.lifecycleScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(clientId).build()
                val response = CredentialManager.create(activity).getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                )
                val custom = response.credential as? CustomCredential
                if (custom?.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    onResult(
                        CredentialResult.Failed(
                            "Google devolvio una credencial que Traidores no reconoce.",
                            IllegalStateException("Tipo de credencial inesperado")
                        )
                    )
                    return@launch
                }
                val token = GoogleIdTokenCredential.createFrom(custom.data).idToken
                onResult(CredentialResult.Ready(GoogleAuthProvider.getCredential(token, null)))
            } catch (_: GetCredentialCancellationException) {
                onResult(CredentialResult.Cancelled)
            } catch (error: GetCredentialException) {
                onResult(
                    CredentialResult.Failed(
                        OnlineErrorMessages.forAction("No se pudo abrir Google", error),
                        error
                    )
                )
            } catch (error: Exception) {
                onResult(
                    CredentialResult.Failed(
                        OnlineErrorMessages.forAction("No se pudo usar Google", error),
                        error
                    )
                )
            }
        }
    }

    private fun finishLinkedAccount(
        activity: AppCompatActivity,
        email: String,
        onResult: (GoogleAccountResult) -> Unit
    ) {
        AccountLink.refreshClaims {
            PlayerPublicIdentity.ensurePublicId(
                context = activity,
                firestore = FirebaseFirestore.getInstance(),
                onReady = {
                    PlayGamesProgressSync.onAuthenticated(activity)
                    onResult(GoogleAccountResult.Linked(email))
                }
            )
        }
    }

    private fun adoptExistingAccount(
        activity: AppCompatActivity,
        credential: com.google.firebase.auth.AuthCredential,
        onResult: (GoogleAccountResult) -> Unit
    ) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid.orEmpty()
                if (uid.isBlank()) {
                    onResult(GoogleAccountResult.Failed("No se pudo entrar con esa cuenta."))
                    return@addOnSuccessListener
                }
                OnlineTempIdentity.adopt(activity, uid)
                PlayerPublicIdentity.clearPublicId(activity)
                AccountLink.recoverPublicId(activity, uid) { recovered ->
                    AccountLink.refreshClaims {
                        PlayGamesProgressSync.onAuthenticated(activity)
                        onResult(
                            GoogleAccountResult.SignedIn(
                                user?.email.orEmpty(),
                                recovered
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { error ->
                onResult(
                    GoogleAccountResult.Failed(
                        OnlineErrorMessages.forAction("No se pudo entrar con Google", error),
                        error
                    )
                )
            }
    }

    private fun serverClientId(activity: AppCompatActivity): String {
        val resourceId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        return if (resourceId == 0) "" else activity.getString(resourceId).trim()
    }

    private sealed class CredentialResult {
        data class Ready(val credential: com.google.firebase.auth.AuthCredential) :
            CredentialResult()
        object Cancelled : CredentialResult()
        data class Failed(val message: String, val error: Exception) : CredentialResult()
    }
}
