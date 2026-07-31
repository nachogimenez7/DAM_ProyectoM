package com.traidores.juego

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore

sealed class AccountLinkResult {
    /** La cuenta quedo pegada a esta identidad: se conserva el `#` y todo el historial. */
    data class Linked(val email: String) : AccountLinkResult()

    /** El correo ya existia: se entro con esa cuenta y se recupero su `#`. */
    data class SignedIn(val email: String, val recoveredPublicId: String) : AccountLinkResult()

    data class Failed(val message: String) : AccountLinkResult()
}

/**
 * Cuentas reales sobre la identidad anonima que ya existe.
 *
 * La clave del diseno es **vincular, no reemplazar**: `linkWithCredential` le agrega correo y
 * contrasena a la sesion anonima actual y **mantiene el mismo uid**. Como el `publicId`, el
 * perfil publico y las reglas de Firestore cuelgan del uid, no hay nada que migrar.
 *
 * Solo cuando el correo ya pertenece a otra sesion (tipico: reinstalo la app o cambio de
 * celular) hay que entrar con esa cuenta, y ahi si el uid cambia: en ese caso se recupera el
 * `#` publico de la cuenta vieja en vez de dejar el del invitado, que quedaria duplicado.
 */
object AccountLink {
    private const val PASSWORD_PROVIDER_ID = "password"

    fun isGuest(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        return user == null || user.isAnonymous
    }

    fun hasPasswordProvider(): Boolean =
        FirebaseAuth.getInstance().currentUser?.providerData?.any {
            it.providerId == PASSWORD_PROVIDER_ID
        } == true

    fun currentEmail(): String {
        return FirebaseAuth.getInstance().currentUser
            ?.takeIf { !it.isAnonymous }
            ?.email
            .orEmpty()
    }

    fun linkOrSignIn(
        context: Context,
        rawEmail: String,
        password: String,
        onResult: (AccountLinkResult) -> Unit
    ) {
        AccountCredentials.validationError(rawEmail, password)?.let {
            onResult(AccountLinkResult.Failed(it))
            return
        }
        val email = AccountCredentials.normalizeEmail(rawEmail)
        val credential = EmailAuthProvider.getCredential(email, password)

        OnlineTempIdentity.ensureAuthenticated(context)
            .addOnSuccessListener {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    onResult(AccountLinkResult.Failed("No se pudo abrir sesión. Probá de nuevo."))
                    return@addOnSuccessListener
                }
                if (user.email?.isNotBlank() == true) {
                    onResult(AccountLinkResult.Failed("Ya tenés un correo vinculado."))
                    return@addOnSuccessListener
                }
                val wasAnonymous = user.isAnonymous
                user.linkWithCredential(credential)
                    .addOnSuccessListener {
                        refreshClaims { onResult(AccountLinkResult.Linked(email)) }
                    }
                    .addOnFailureListener { error ->
                        if (error is FirebaseAuthUserCollisionException && wasAnonymous) {
                            adoptExistingAccount(context, email, password, onResult)
                        } else if (error is FirebaseAuthUserCollisionException) {
                            onResult(
                                AccountLinkResult.Failed(
                                    "Ese correo ya pertenece a otra cuenta. " +
                                        "No se puede mezclar automáticamente con Play Games."
                                )
                            )
                        } else {
                            onResult(AccountLinkResult.Failed(messageFor(error)))
                        }
                    }
            }
            .addOnFailureListener { error ->
                onResult(AccountLinkResult.Failed(messageFor(error)))
            }
    }

    /**
     * El correo ya estaba registrado. Se entra con esa cuenta y se recupera su `#`: si se
     * dejara el del invitado, dos identidades distintas terminarian mostrando el mismo
     * numero publico.
     */
    private fun adoptExistingAccount(
        context: Context,
        email: String,
        password: String,
        onResult: (AccountLinkResult) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                if (uid.isBlank()) {
                    onResult(AccountLinkResult.Failed("No se pudo entrar con esa cuenta."))
                    return@addOnSuccessListener
                }
                OnlineTempIdentity.adopt(context, uid)
                // Antes de recuperar: el `#` guardado es el del invitado y pertenece al uid
                // viejo. Si no se borra, `ensurePublicId` lo daria por bueno y dos cuentas
                // distintas mostrarian el mismo numero.
                PlayerPublicIdentity.clearPublicId(context)
                recoverPublicId(context, uid) { recovered ->
                    onResult(AccountLinkResult.SignedIn(email, recovered))
                }
            }
            .addOnFailureListener { error ->
                onResult(AccountLinkResult.Failed(messageFor(error)))
            }
    }

    /**
     * Fuerza un token nuevo. Despues de `linkWithCredential`, el token que el cliente tiene en
     * mano puede seguir diciendo `sign_in_provider = anonymous`, y las reglas de Firestore
     * deciden con eso si sos invitado o no. Sin este refresco, alguien se registra y durante
     * un rato el servidor lo sigue tratando como invitado: un bug intermitente y dependiente
     * del reloj, de los peores de diagnosticar.
     */
    internal fun refreshClaims(onDone: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone()
            return
        }
        user.getIdToken(true)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { onDone() }
    }

    internal fun recoverPublicId(
        context: Context,
        uid: String,
        onReady: (String) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("perfiles_publicos")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                // El perfil visual tambien vivia en el servidor y no se bajaba: se recuperaba
                // el `#` pero el avatar, el banner y la frase quedaban los del celular nuevo,
                // justo al reves de lo que promete la pantalla de cuenta.
                PlayerProfileStore.saveRecoveredProfile(
                    context = context,
                    name = document.getString(PlayerPublicIdentity.FIELD_PROFILE_NAME).orEmpty(),
                    bio = document.getString(PlayerPublicIdentity.FIELD_PROFILE_BIO).orEmpty(),
                    avatarKey = document.getString(PlayerPublicIdentity.FIELD_PROFILE_AVATAR).orEmpty(),
                    bannerKey = document.getString(PlayerPublicIdentity.FIELD_PROFILE_BANNER).orEmpty(),
                    favoriteRoleKey = document
                        .getString(PlayerPublicIdentity.FIELD_PROFILE_FAVORITE_ROLE)
                        .orEmpty()
                )
                val stored = document.getString(PlayerPublicIdentity.FIELD_PUBLIC_ID).orEmpty()
                if (PlayerPublicIdentity.isValidPublicId(stored)) {
                    PlayerPublicIdentity.savePublicId(context, stored)
                    onReady(stored)
                    return@addOnSuccessListener
                }
                // La cuenta existia pero nunca reservo numero: se pide uno nuevo para el uid.
                PlayerPublicIdentity.ensurePublicId(
                    context = context,
                    firestore = firestore,
                    onReady = onReady
                )
            }
            .addOnFailureListener {
                onReady(PlayerPublicIdentity.currentPublicId(context))
            }
    }

    private fun messageFor(error: Exception): String {
        // Falta habilitar "Correo electronico/contrasena" en Firebase Console > Authentication
        // > Sign-in method. Sin ese paso el codigo esta bien y las cuentas igual fallan.
        if (error is FirebaseAuthException && error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
            return "Las cuentas por correo todavía no están habilitadas en el servidor."
        }
        return when (error) {
            is FirebaseAuthWeakPasswordException ->
                "Esa contraseña es muy débil. Probá con una más larga."
            is FirebaseAuthInvalidCredentialsException ->
                "El correo o la contraseña no son correctos."
            is FirebaseAuthInvalidUserException ->
                "Esa cuenta no está disponible."
            else -> OnlineErrorMessages.forAction("No se pudo crear la cuenta", error)
        }
    }
}
