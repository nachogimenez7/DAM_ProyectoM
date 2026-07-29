package com.traidores.juego

import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

object PlayerModeration {
    private data class Reason(val key: String, val label: String)

    private val reasons = listOf(
        Reason("toxicidad", "Toxicidad o insultos"),
        Reason("trampa", "Trampa"),
        Reason("spam", "Spam"),
        Reason("nombre_ofensivo", "Nombre ofensivo"),
        Reason("otro", "Otro")
    )

    fun showReportDialog(
        activity: AppCompatActivity,
        roomId: String,
        matchId: String,
        reportedUid: String,
        reportedName: String
    ) {
        if (
            roomId.isBlank() ||
            matchId.length !in 8..80 ||
            reportedUid.isBlank() ||
            reportedUid == OnlineTempIdentity.getOrCreate(activity)
        ) return

        GameDialog.choose(
            activity = activity,
            title = "REPORTAR JUGADOR",
            message = "¿Por qué querés reportar a $reportedName?",
            options = reasons.map { it.label },
            negativeLabel = "CANCELAR"
        ) { index ->
            val reason = reasons[index]
            GameDialog.input(
                activity = activity,
                title = "Detalle opcional",
                currentValue = "",
                hint = "Contanos brevemente qué pasó",
                maxLength = 140,
                positiveLabel = "ENVIAR",
                negativeLabel = "SIN DETALLE"
            ) { detail ->
                submit(
                    activity,
                    roomId,
                    matchId,
                    reportedUid,
                    reportedName,
                    reason.key,
                    detail
                )
                null
            }.also { detailDialog ->
                detailDialog.findViewById<android.widget.Button>(R.id.gameDialogNegative)
                    ?.setOnClickListener {
                        submit(
                            activity,
                            roomId,
                            matchId,
                            reportedUid,
                            reportedName,
                            reason.key,
                            ""
                        )
                        detailDialog.dismiss()
                    }
            }
        }
    }

    private fun submit(
        activity: AppCompatActivity,
        roomId: String,
        matchId: String,
        reportedUid: String,
        reportedName: String,
        reason: String,
        detail: String
    ) {
        val reporterUid = OnlineTempIdentity.getOrCreate(activity)
        val documentId = "${safeId(matchId)}_${safeId(reporterUid)}_${safeId(reportedUid)}"
        val data = hashMapOf<String, Any>(
            "reportanteId" to reporterUid,
            "reportadoId" to reportedUid,
            "reportadoNombre" to reportedName.take(18),
            "roomId" to roomId.take(80),
            "matchId" to matchId.take(80),
            "motivo" to reason,
            "creadaEn" to FieldValue.serverTimestamp()
        )
        detail.trim().take(140).takeIf(String::isNotBlank)?.let { data["detalle"] = it }
        FirebaseFirestore.getInstance()
            .collection("reportes")
            .document(documentId)
            .set(data)
            .addOnSuccessListener {
                GameNotice.show(activity, "Gracias. Vamos a revisarlo.", GameNotice.Duration.LONG)
            }
            .addOnFailureListener { error ->
                if (
                    error is FirebaseFirestoreException &&
                    error.code in setOf(
                        FirebaseFirestoreException.Code.ALREADY_EXISTS,
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                    )
                ) {
                    GameNotice.show(
                        activity,
                        "Gracias. Vamos a revisarlo.",
                        GameNotice.Duration.LONG
                    )
                } else {
                    OnlineDebugLog.e("player_report_failure target=$reportedUid", error)
                    GameNotice.show(
                        activity,
                        "No pudimos enviar el reporte. Probá de nuevo cuando tengas conexión.",
                        GameNotice.Duration.LONG
                    )
                }
            }
    }

    private fun safeId(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
}
