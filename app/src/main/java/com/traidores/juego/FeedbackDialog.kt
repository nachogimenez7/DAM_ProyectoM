package com.traidores.juego

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/** Formulario interno de comentarios. No depende de que el teléfono tenga Gmail instalado. */
object FeedbackDialog {
    private const val COLLECTION = "comentarios"
    private const val DESTINATION_EMAIL = "bandidogamesestudio@gmail.com"
    private const val MAX_NAME = 40
    private const val MAX_SUBJECT = 80
    private const val MAX_MESSAGE = 1200

    fun show(activity: Activity) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(6), 0, activity.dp(6), activity.dp(2))
        }
        content.addView(label(activity, "ENVIAR COMENTARIO O ERROR", 20f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.accent_gold))
        })
        content.addView(label(
            activity,
            "Contanos qué pasó o qué mejorarías. Los datos técnicos se agregan automáticamente.",
            13f,
            false
        ).apply {
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.text_secondary))
            setPadding(0, activity.dp(6), 0, activity.dp(12))
        })

        val nameInput = input(
            activity = activity,
            title = "NOMBRE",
            hint = "Cómo querés que te llamemos",
            maxLength = MAX_NAME,
            initialValue = PlayerPublicIdentity.profileName(activity)
        )
        val subjectInput = input(
            activity = activity,
            title = "ASUNTO",
            hint = "Ej.: problema en una partida vs IA",
            maxLength = MAX_SUBJECT
        )
        val messageInput = input(
            activity = activity,
            title = "MENSAJE",
            hint = "Describí qué pasó, qué esperabas o qué te gustaría mejorar",
            maxLength = MAX_MESSAGE,
            multiline = true
        )
        content.addView(nameInput.container)
        content.addView(subjectInput.container, marginTop(activity, 9))
        content.addView(messageInput.container, marginTop(activity, 9))

        val status = label(activity, "", 12f, false).apply {
            visibility = View.GONE
            setPadding(activity.dp(4), activity.dp(8), activity.dp(4), 0)
        }
        content.addView(status)

        val dialog = GameDialog.custom(
            activity = activity,
            contentView = ScrollView(activity).apply {
                isFillViewport = false
                addView(content)
            },
            widthDp = 440,
            contentHeightDp = 500,
            negativeLabel = "CANCELAR",
            positiveLabel = "ENVIAR"
        )
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val sendButton = dialog.findViewById<Button>(R.id.gameDialogPositive) ?: return
        sendButton.setOnClickListener {
            val name = nameInput.editText.text.toString().trim()
            val subject = subjectInput.editText.text.toString().trim()
            val message = messageInput.editText.text.toString().trim()
            when {
                name.length < 2 -> nameInput.editText.error = "Escribí tu nombre."
                subject.length < 3 -> subjectInput.editText.error = "Contanos el asunto."
                message.length < 10 -> messageInput.editText.error = "Agregá un poco más de detalle."
                else -> submit(
                    activity = activity,
                    name = name,
                    subject = subject,
                    message = message,
                    sendButton = sendButton,
                    status = status,
                    onSuccess = { dialog.dismiss() }
                )
            }
        }
    }

    private fun submit(
        activity: Activity,
        name: String,
        subject: String,
        message: String,
        sendButton: Button,
        status: TextView,
        onSuccess: () -> Unit
    ) {
        sendButton.isEnabled = false
        sendButton.text = "ENVIANDO…"
        status.visibility = View.VISIBLE
        status.setTextColor(activity.getColor(R.color.text_secondary))
        status.text = "Guardando tu mensaje de forma segura…"

        OnlineTempIdentity.ensureAuthenticated(activity)
            .addOnSuccessListener { uid ->
                val version = runCatching {
                    activity.packageManager
                        .getPackageInfo(activity.packageName, 0)
                        .versionName
                        .orEmpty()
                }.getOrDefault("")
                val payload = mapOf(
                    "uid" to uid,
                    "nombre" to name,
                    "asunto" to subject,
                    "mensaje" to message,
                    "destino" to DESTINATION_EMAIL,
                    "estado" to "pendiente",
                    "origen" to "formulario_app",
                    "version" to version,
                    "dispositivo" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    "android" to Build.VERSION.RELEASE,
                    "fechaLocal" to System.currentTimeMillis(),
                    "creadaEn" to FieldValue.serverTimestamp()
                )
                FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .add(payload)
                    .addOnSuccessListener {
                        GameNotice.show(
                            activity = activity,
                            message = "Gracias. Tu mensaje llegó a Bandido Games.",
                            duration = GameNotice.Duration.LONG
                        )
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        OnlineDebugLog.e("feedback_submit_failure", error)
                        showFailure(activity, sendButton, status)
                    }
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("feedback_auth_failure", error)
                showFailure(activity, sendButton, status)
            }
    }

    private fun showFailure(activity: Activity, sendButton: Button, status: TextView) {
        sendButton.isEnabled = true
        sendButton.text = "REINTENTAR"
        status.setTextColor(activity.getColor(R.color.traitor_red_bright))
        status.text = "No se pudo enviar. Revisá tu conexión y volvé a intentar."
    }

    private data class InputBlock(val container: LinearLayout, val editText: EditText)

    private fun input(
        activity: Activity,
        title: String,
        hint: String,
        maxLength: Int,
        initialValue: String = "",
        multiline: Boolean = false
    ): InputBlock {
        val field = EditText(activity).apply {
            setText(initialValue)
            this.hint = hint
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setTextColor(activity.getColor(R.color.text_primary))
            setHintTextColor(activity.getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_chat_input)
            setPadding(activity.dp(13), activity.dp(10), activity.dp(13), activity.dp(10))
            textSize = 14f
            if (multiline) {
                minLines = 5
                maxLines = 8
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        return InputBlock(
            container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(activity, title, 11f, true).apply {
                    setTextColor(activity.getColor(R.color.accent_gold))
                    setPadding(activity.dp(3), 0, 0, activity.dp(4))
                })
                addView(field, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            },
            editText = field
        )
    }

    private fun label(activity: Activity, value: String, size: Float, bold: Boolean): TextView {
        return TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(activity.getColor(R.color.text_primary))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun marginTop(activity: Activity, topDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = activity.dp(topDp) }
    }

    private fun Activity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
