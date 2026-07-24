package com.traidores.juego

/**
 * Validacion local de los datos de cuenta, antes de molestar a Firebase.
 * Es un objeto puro para poder testear los mensajes sin red.
 */
object AccountCredentials {

    const val MIN_PASSWORD_LENGTH = 6

    fun normalizeEmail(rawEmail: String): String = rawEmail.trim()

    /** Devuelve el problema a mostrar, o null si los datos sirven para intentar. */
    fun validationError(rawEmail: String, password: String): String? {
        val email = normalizeEmail(rawEmail)
        return when {
            email.isBlank() -> "Escribí tu correo."
            !email.contains("@") || !email.substringAfterLast("@").contains(".") ->
                "Ese correo no parece válido."
            email.contains(" ") -> "El correo no puede tener espacios."
            password.isBlank() -> "Escribí una contraseña."
            password.length < MIN_PASSWORD_LENGTH ->
                "La contraseña necesita al menos $MIN_PASSWORD_LENGTH caracteres."
            else -> null
        }
    }
}
