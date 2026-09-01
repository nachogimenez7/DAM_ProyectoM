package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions

internal object OnlineStartCallableContract {
    const val REGION = "southamerica-west1"
    const val FUNCTION_NAME = "iniciarPartidaV2"
}

internal sealed interface OnlineStartCallableResult {
    data class Accepted(
        val matchId: String,
        val mapKey: String,
        val alreadyStarted: Boolean
    ) : OnlineStartCallableResult

    data class MapTieBreakRequired(
        val mapKeys: List<String>
    ) : OnlineStartCallableResult
}

internal object OnlineStartCallableResponseParser {
    fun parse(raw: Any?): OnlineStartCallableResult {
        val data = raw as? Map<*, *>
            ?: throw IllegalArgumentException("La funcion devolvio una respuesta invalida.")
        return when (val status = data["status"] as? String) {
            "started", "already_started" -> {
                val matchId = (data["matchId"] as? String).orEmpty()
                val mapKey = (data["mapKey"] as? String).orEmpty()
                require(matchId.isNotBlank()) { "La funcion no devolvio matchId." }
                require(mapKey in OnlineMapVoteResolver.mapKeys) {
                    "La funcion devolvio un mapa invalido."
                }
                OnlineStartCallableResult.Accepted(
                    matchId = matchId,
                    mapKey = mapKey,
                    alreadyStarted = status == "already_started"
                )
            }
            "tie_break_required" -> {
                val mapKeys = (data["mapKeys"] as? List<*>)
                    .orEmpty()
                    .mapNotNull { it as? String }
                    .filter { it in OnlineMapVoteResolver.mapKeys }
                    .distinct()
                require(mapKeys.isNotEmpty()) { "El desempate no contiene mapas validos." }
                OnlineStartCallableResult.MapTieBreakRequired(mapKeys)
            }
            else -> throw IllegalArgumentException(
                "La funcion devolvio un estado desconocido: ${status ?: "vacio"}."
            )
        }
    }
}

internal class OnlineStartCallableClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(
        OnlineStartCallableContract.REGION
    )
) {
    fun start(
        roomId: String,
        hostTieBreakChoice: String?
    ): Task<OnlineStartCallableResult> {
        require(roomId.isNotBlank()) { "La sala no puede estar vacia." }
        val request = mutableMapOf<String, Any>("roomId" to roomId)
        hostTieBreakChoice
            ?.takeIf { it in OnlineMapVoteResolver.mapKeys }
            ?.let { request["hostTieBreakChoice"] = it }
        return functions
            .getHttpsCallable(OnlineStartCallableContract.FUNCTION_NAME)
            .call(request)
            .continueWith { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("La callable fallo sin detalle.")
                }
                OnlineStartCallableResponseParser.parse(task.result?.data)
            }
    }
}
