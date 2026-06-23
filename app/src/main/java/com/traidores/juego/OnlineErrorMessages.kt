package com.traidores.juego

import com.google.firebase.firestore.FirebaseFirestoreException

object OnlineErrorMessages {
    fun forAction(action: String, error: Throwable): String {
        val detail = when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firebase rechazo la accion. Revisa que firestore.rules este publicado."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No hay conexion estable con Firebase. Proba otra vez."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "La sala ya no existe o fue borrada."
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED ->
                "La operacion se interrumpio. Proba otra vez."
            else -> fallbackDetail(error)
        }
        return "$action. $detail"
    }

    private fun fallbackDetail(error: Throwable): String {
        val message = error.message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(120)
            .orEmpty()
        return when {
            message.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Firebase rechazo la accion. Revisa que firestore.rules este publicado."
            message.contains("Missing or insufficient permissions", ignoreCase = true) ->
                "Firebase rechazo la accion. Revisa que firestore.rules este publicado."
            message.contains("network", ignoreCase = true) ||
                message.contains("unavailable", ignoreCase = true) ->
                "No hay conexion estable con Firebase. Proba otra vez."
            message.isNotBlank() -> message
            else -> "Error tecnico: ${error.javaClass.simpleName}."
        }
    }
}
