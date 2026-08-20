package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.widget.Button

/** Normas mínimas que se aceptan una vez antes de usar el modo online. */
object CommunityRules {
    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_ACCEPTED = "community_rules_accepted_v1"

    private const val SUMMARY =
        "En Traidores se puede mentir, acusar y engañar dentro de la partida. " +
            "Eso nunca justifica atacar a la persona detrás del personaje.\n\n" +
            "• Sin insultos, amenazas, acoso ni discriminación.\n" +
            "• No compartas datos personales propios o ajenos.\n" +
            "• Sin contenido sexual, spam, trampas ni suplantación.\n" +
            "• Respetá a quien decida silenciarte o abandonar la conversación.\n\n" +
            "Podés silenciar para vos o reportar desde el perfil de otro jugador. " +
            "Los reportes falsos o abusivos también incumplen estas normas.\n\n" +
            "Podés consultar la versión completa en Ayuda > Normas y seguridad online."

    fun hasAccepted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ACCEPTED, false)
    }

    fun showBeforeFirstOnline(activity: Activity) {
        if (hasAccepted(activity)) return

        val dialog = GameDialog.confirm(
            activity = activity,
            title = "NORMAS DE LA COMUNIDAD",
            message = SUMMARY,
            positiveLabel = "ACEPTO Y CONTINÚO",
            negativeLabel = "VOLVER"
        ) {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ACCEPTED, true)
                .apply()
        }
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.findViewById<Button>(R.id.gameDialogNegative)?.setOnClickListener {
            dialog.dismiss()
            activity.finish()
        }
    }
}
