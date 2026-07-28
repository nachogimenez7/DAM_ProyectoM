package com.traidores.juego

import android.content.Context
import kotlin.math.abs

/**
 * Identidad de un jugador sin cuenta.
 *
 * Un invitado no escribe su nombre: elige un alias de una **lista cerrada** y el juego le
 * agrega un numero estable derivado del uid. Eso resuelve dos cosas a la vez: los amigos lo
 * reconocen dentro de una sala, y **es imposible que un invitado use un nombre ofensivo**.
 * Ese segundo punto es el unico control de contenido que hoy se puede hacer valer en el
 * servidor sin backend: `firestore.rules` valida el nombre de un invitado contra el mismo
 * patron que arma [displayName].
 *
 * El numero **no** sale de `meta/public_ids`: el `#` publico es exclusivo de las cuentas
 * registradas, asi que el contador global deja de gastarse con gente que abre el perfil una
 * vez y desinstala.
 */
object GuestIdentity {

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_GUEST_ALIAS = "guest_alias"

    /**
     * Lista cerrada. Al agregar o sacar un alias hay que actualizar tambien el patron de
     * `validGuestName()` en `firestore.rules`, o el servidor rechaza el nombre nuevo.
     * Ninguno puede pasar de 13 caracteres: con el numero, el nombre entero tiene que entrar
     * en los 18 que aceptan las reglas.
     */
    val aliases = listOf(
        "Forastero",
        "Mala Onda",
        "Aguafiestas",
        "Chamuyero",
        "Careta",
        "Mufa",
        "Perejil",
        "Metepatas",
        "Don Nadie",
        "El Colado",
        "Sospechoso",
        "Rezongón"
    )

    private val NAME_REGEX = Regex("^(${aliases.joinToString("|") { Regex.escape(it) }}) [0-9]{4}$")

    /**
     * Se apoya en `FirebaseUser.isAnonymous`, que se actualiza apenas se vincula la cuenta.
     * El servidor no puede usar esta señal: alla se miran los claims del token, que tardan un
     * refresco en reflejar el vinculo (ver `AccountLink.refreshClaims`).
     */
    fun isGuest(): Boolean = AccountLink.isGuest()

    /** Nombre visible de un invitado, por ejemplo `Aguafiestas 4821`. */
    fun displayName(context: Context): String {
        return "${selectedAlias(context)} ${guestNumber(context)}"
    }

    fun selectedAlias(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_GUEST_ALIAS, null)
        return stored?.takeIf { it in aliases } ?: defaultAlias(context)
    }

    fun saveAlias(context: Context, alias: String) {
        if (alias !in aliases) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_GUEST_ALIAS, alias)
            .apply()
    }

    /**
     * Cuatro digitos derivados del uid. Estable en el dispositivo y sin ninguna llamada de
     * red. Dos invitados pueden colisionar; dentro de una sala eso se resuelve igual que
     * cualquier nombre repetido.
     */
    fun guestNumber(context: Context): String {
        return numberFor(OnlineTempIdentity.getOrCreate(context))
    }

    internal fun numberFor(uid: String): String {
        val hash = abs(uid.hashCode().toLong())
        return (1000L + (hash % 9000L)).toString()
    }

    internal fun defaultAlias(context: Context): String {
        val hash = abs(OnlineTempIdentity.getOrCreate(context).hashCode().toLong())
        return aliases[(hash % aliases.size).toInt()]
    }

    /** Misma validacion que hace `firestore.rules` sobre el nombre de un invitado. */
    fun isValidGuestName(name: String): Boolean = NAME_REGEX.matches(name)
}
