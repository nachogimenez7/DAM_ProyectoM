package com.traidores.juego

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException

object OnlineErrorMessages {
    fun forAction(action: String, error: Throwable): String {
        val functionsError = findFunctionsError(error)
        val detail = when (functionsError?.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Tu sesion vencio. Volve a entrar al modo online."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "El servidor rechazo la accion. Solo el anfitrion puede realizarla."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                "No hay conexion estable con el servidor. Proba otra vez."
            FirebaseFunctionsException.Code.NOT_FOUND ->
                "La sala ya no existe o fue borrada."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message?.takeIf(String::isNotBlank)
                    ?: "La sala todavia no esta lista."
            FirebaseFunctionsException.Code.INTERNAL ->
                "El servidor no pudo completar la accion. Proba otra vez."
            null -> firestoreDetail(error)
            else -> fallbackDetail(functionsError)
        }
        return "$action. $detail"
    }

    private fun firestoreDetail(error: Throwable): String {
        return when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "El servidor rechazo la accion. Proba otra vez."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No hay conexion estable con el servidor. Proba otra vez."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "La sala ya no existe o fue borrada."
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED ->
                "La operacion se interrumpio. Proba otra vez."
            else -> fallbackDetail(error)
        }
    }

    private fun findFunctionsError(error: Throwable): FirebaseFunctionsException? {
        var current: Throwable? = error
        repeat(5) {
            if (current is FirebaseFunctionsException) return current
            current = current?.cause
        }
        return null
    }

    private fun fallbackDetail(error: Throwable): String {
        val message = error.message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(120)
            .orEmpty()
        return when {
            message.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "El servidor rechazo la accion. Proba otra vez."
            message.contains("Missing or insufficient permissions", ignoreCase = true) ->
                "El servidor rechazo la accion. Proba otra vez."
            message.contains("network", ignoreCase = true) ||
                message.contains("unavailable", ignoreCase = true) ||
                message.contains("offline", ignoreCase = true) ->
                "No hay conexion estable con el servidor. Proba otra vez."
            else -> "El servidor devolvio un error inesperado. Proba otra vez."
        }
    }
}
