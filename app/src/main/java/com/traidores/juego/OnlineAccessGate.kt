package com.traidores.juego

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

data class OnlineBan(val reason: String)

object OnlineAccessGate {
    fun verify(
        context: Context,
        onAllowed: () -> Unit,
        onBlocked: (OnlineBan) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        OnlineTempIdentity.ensureAuthenticated(context)
            .addOnSuccessListener { uid ->
                FirebaseFirestore.getInstance()
                    .collection("bans")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            onBlocked(
                                OnlineBan(
                                    document.getString("motivo")
                                        ?.takeIf(String::isNotBlank)
                                        ?: "Incumplimiento de las reglas de convivencia."
                                )
                            )
                        } else {
                            onAllowed()
                        }
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }
}
