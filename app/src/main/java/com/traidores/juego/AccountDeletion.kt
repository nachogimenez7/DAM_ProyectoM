package com.traidores.juego

import android.app.Activity
import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore

sealed class AccountDeletionResult {
    object Deleted : AccountDeletionResult()
    data class Failed(val message: String, val error: Exception? = null) : AccountDeletionResult()
}

/**
 * Borrado ordenado de una cuenta registrada.
 *
 * La reautenticación ocurre primero. Después se eliminan los datos que todavía necesitan la
 * identidad vigente y Firebase Auth se borra al final. Así no perdemos permisos a mitad del
 * proceso.
 */
object AccountDeletion {
    const val CONFIRMATION_TEXT = "ELIMINAR"
    private const val LOCAL_PREFS_NAME = "TraidoresPrefs"

    fun delete(
        activity: Activity,
        emailPassword: String?,
        onResult: (AccountDeletionResult) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            onResult(AccountDeletionResult.Failed("No hay una cuenta registrada para eliminar."))
            return
        }

        reauthenticate(activity, emailPassword) { reauthError ->
            if (reauthError != null) {
                onResult(
                    AccountDeletionResult.Failed(
                        message = reauthenticationMessage(reauthError),
                        error = reauthError
                    )
                )
                return@reauthenticate
            }
            deleteCloudSave(activity) { cloudError ->
                if (cloudError != null) {
                    onResult(
                        AccountDeletionResult.Failed(
                            "No pudimos borrar el respaldo de Play Games. " +
                                "No se eliminó la cuenta; revisá tu conexión e intentá otra vez.",
                            cloudError
                        )
                    )
                    return@deleteCloudSave
                }
                deletePublicProfile(user.uid) { profileError ->
                    if (profileError != null) {
                        onResult(
                            AccountDeletionResult.Failed(
                                "No pudimos borrar tu perfil online. " +
                                    "No se eliminó la cuenta; intentá nuevamente.",
                                profileError
                            )
                        )
                        return@deletePublicProfile
                    }
                    user.delete()
                        .addOnSuccessListener {
                            finishLocalDeletion(activity)
                            onResult(AccountDeletionResult.Deleted)
                        }
                        .addOnFailureListener { error ->
                            OnlineDebugLog.e("account_auth_delete_failure", error)
                            onResult(
                                AccountDeletionResult.Failed(
                                    if (error is FirebaseAuthRecentLoginRequiredException) {
                                        "Por seguridad necesitás volver a verificar tu cuenta."
                                    } else {
                                        "El respaldo y el perfil se borraron, pero Firebase no " +
                                            "pudo cerrar la cuenta. Contactá a Bandido Games."
                                    },
                                    error
                                )
                            )
                        }
                }
            }
        }
    }

    private fun reauthenticate(
        activity: Activity,
        emailPassword: String?,
        onDone: (Exception?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone(IllegalStateException("La sesión ya no está disponible."))
            return
        }
        val email = user.email.orEmpty()
        if (email.isNotBlank()) {
            if (emailPassword.isNullOrBlank()) {
                onDone(IllegalArgumentException("Ingresá tu contraseña para continuar."))
                return
            }
            user.reauthenticate(EmailAuthProvider.getCredential(email, emailPassword))
                .addOnSuccessListener { onDone(null) }
                .addOnFailureListener(onDone)
            return
        }
        if (PlayGamesIdentity.hasPlayGamesProvider()) {
            PlayGamesIdentity.reauthenticate(activity, onDone)
            return
        }
        onDone(IllegalStateException("No encontramos un método para verificar la cuenta."))
    }

    private fun deleteCloudSave(activity: Activity, onDone: (Exception?) -> Unit) {
        if (!PlayGamesIdentity.hasPlayGamesProvider()) {
            onDone(null)
            return
        }
        PlayGamesCloudSave.deleteAccountSnapshot(activity, onDone)
    }

    private fun deletePublicProfile(uid: String, onDone: (Exception?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("perfiles_publicos")
            .document(uid)
            .delete()
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener(onDone)
    }

    private fun finishLocalDeletion(context: Context) {
        AccountDeletionPreferences.suppressAutoLink(context)
        context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        FirebaseAuth.getInstance().signOut()
    }

    private fun reauthenticationMessage(error: Exception): String {
        return when (error) {
            is IllegalArgumentException -> error.message.orEmpty()
            else -> "No pudimos verificar tu cuenta. Revisá la contraseña o tu conexión."
        }
    }
}
