package com.traidores.juego

import android.app.Activity
import android.widget.TextView

/**
 * Confirmación visual compartida por el alta inicial y la vinculación desde el perfil.
 */
object AccountLinkedDialog {
    fun show(activity: Activity, recoveredPublicId: String? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.layoutInflater.inflate(
            R.layout.dialog_account_link_success,
            null
        )
        content.findViewById<TextView>(R.id.accountLinkSuccessMessage).text =
            if (recoveredPublicId.isNullOrBlank()) {
                activity.getString(R.string.account_link_success_message)
            } else {
                activity.getString(
                    R.string.account_link_recovered_message,
                    recoveredPublicId
                )
            }
        GameDialog.custom(
            activity = activity,
            contentView = content,
            widthDp = 340,
            negativeLabel = "LISTO"
        )
    }
}
