package com.traidores.juego

import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class GoogleAccountResult {
    data class Linked(val email: String) : GoogleAccountResult()
    data class SignedIn(val email: String, val recoveredPublicId: String) : GoogleAccountResult()
    object Cancelled : GoogleAccountResult()
    data class Failed(
        val message: String,
        val error: Exception? = null,
        val retryWithAlternativePicker: Boolean = false
    ) : GoogleAccountResult()
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
        useAlternativePicker: Boolean = false,
        onCredentialReady: () -> Unit = {},
        onResult: (GoogleAccountResult) -> Unit
    ): Job? = requestFirebaseCredential(
        activity,
        useAlternativePicker,
        onCredentialReady,
        onResult
    )

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
        useAlternativePicker: Boolean,
        onCredentialReady: () -> Unit,
        onResult: (GoogleAccountResult) -> Unit
    ): Job? {
        return requestGoogleCredential(activity, useAlternativePicker) { result ->
            when (result) {
                CredentialResult.Cancelled -> onResult(GoogleAccountResult.Cancelled)
                is CredentialResult.Failed -> onResult(
                    GoogleAccountResult.Failed(
                        result.message,
                        result.error,
                        result.retryWithAlternativePicker
                    )
                )
                is CredentialResult.Ready -> {
                    logStage("credential_ready")
                    onCredentialReady()
                    OnlineTempIdentity.ensureAuthenticated(activity)
                        .addOnSuccessListener {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                onResult(
                                    failure(
                                        "No se pudo abrir la sesión.",
                                        IllegalStateException("FirebaseAuth no devolvió un usuario")
                                    )
                                )
                                return@addOnSuccessListener
                            }
                            if (hasGoogleProvider()) {
                                onResult(GoogleAccountResult.Linked(user.email.orEmpty()))
                                return@addOnSuccessListener
                            }
                            val wasAnonymous = user.isAnonymous
                            logStage(
                                if (wasAnonymous) {
                                    "link_anonymous_user"
                                } else {
                                    "link_registered_user"
                                }
                            )
                            user.linkWithCredential(result.credential)
                                .addOnSuccessListener { authResult ->
                                    logStage("firebase_link_success")
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
                                            failure(
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
                                failure(
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
        useAlternativePicker: Boolean = false,
        onResult: (CredentialResult) -> Unit
    ): Job? {
        val clientId = serverClientId(activity)
        if (clientId.isBlank()) {
            val error = IllegalStateException("Falta default_web_client_id")
            onResult(
                CredentialResult.Failed(
                    "Google todavia no esta disponible. Podes usar correo mientras tanto.",
                    error
                )
            )
            recordFailure(error, "Google no está configurado")
            return null
        }
        return activity.lifecycleScope.launch {
            try {
                logStage(
                    if (useAlternativePicker) {
                        "alternative_credential_request_started"
                    } else {
                        "credential_request_started"
                    }
                )
                val request = if (useAlternativePicker) {
                    val option = GetGoogleIdOption.Builder()
                        .setServerClientId(clientId)
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                    GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                } else {
                    val option = GetSignInWithGoogleOption.Builder(clientId).build()
                    GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                }
                val response = CredentialManager.create(activity).getCredential(
                    context = activity,
                    request = request
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
            } catch (_: CancellationException) {
                onResult(CredentialResult.Cancelled)
            } catch (error: GetCredentialCancellationException) {
                recordFailure(error, "Credential Manager cerró la selección")
                onResult(
                    CredentialResult.Failed(
                        if (useAlternativePicker) {
                            "Google tampoco pudo confirmar la cuenta con el selector alternativo. " +
                                "El intento quedó registrado para identificar la causa exacta."
                        } else {
                            "No recibimos la confirmación de Google después de elegir la cuenta. " +
                                "Tocá Reintentar para probar con otro selector de cuentas."
                        },
                        error,
                        retryWithAlternativePicker = !useAlternativePicker
                    )
                )
            } catch (error: NoCredentialException) {
                recordFailure(error, "Google no encontró una credencial disponible")
                onResult(
                    CredentialResult.Failed(
                        if (useAlternativePicker) {
                            "No encontramos una cuenta de Google disponible en este dispositivo."
                        } else {
                            "No encontramos una cuenta autorizada. Tocá Reintentar para elegir otra cuenta."
                        },
                        error,
                        retryWithAlternativePicker = !useAlternativePicker
                    )
                )
            } catch (error: GetCredentialException) {
                recordFailure(error, "Credential Manager no pudo abrir Google")
                onResult(
                    CredentialResult.Failed(
                        OnlineErrorMessages.forAction("No se pudo abrir Google", error),
                        error
                    )
                )
            } catch (error: Exception) {
                recordFailure(error, "No se pudo usar Google")
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
                    onResult(
                        failure(
                            "No se pudo entrar con esa cuenta.",
                            IllegalStateException("Google no devolvió un uid")
                        )
                    )
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
                    failure(
                        OnlineErrorMessages.forAction("No se pudo entrar con Google", error),
                        error
                    )
                )
            }
    }

    private fun serverClientId(activity: AppCompatActivity): String {
        return activity.getString(R.string.default_web_client_id).trim()
    }

    private fun failure(message: String, error: Exception): GoogleAccountResult.Failed {
        recordFailure(error, message)
        return GoogleAccountResult.Failed(message, error)
    }

    private fun recordFailure(error: Exception, stage: String) {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("auth_flow", "google_account_link")
            setCustomKey("auth_stage", stage.take(100))
            setCustomKey("auth_error_type", error.javaClass.simpleName)
            if (error is GetCredentialException) {
                setCustomKey("auth_credential_type", error.type.take(120))
            }
            recordException(error)
        }
    }

    private fun logStage(stage: String) {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("auth_flow", "google_account_link")
            setCustomKey("auth_stage", stage)
            log("Google account link stage: $stage")
        }
    }

    private sealed class CredentialResult {
        data class Ready(val credential: com.google.firebase.auth.AuthCredential) :
            CredentialResult()
        object Cancelled : CredentialResult()
        data class Failed(
            val message: String,
            val error: Exception,
            val retryWithAlternativePicker: Boolean = false
        ) : CredentialResult()
    }
}
