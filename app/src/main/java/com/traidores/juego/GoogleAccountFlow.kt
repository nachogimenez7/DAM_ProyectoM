package com.traidores.juego

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Job
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Capa visual y de seguridad del acceso con Google.
 *
 * Credential Manager puede quedar abierto o sin devolver nada en algunos dispositivos.
 * Este coordinador hace visible cada etapa, permite cancelar mientras se elige la cuenta
 * y corta la espera para que el jugador siempre recupere el control de la pantalla.
 */
object GoogleAccountFlow {
    private const val TIMEOUT_MS = 35_000L

    fun start(
        activity: AppCompatActivity,
        useAlternativePicker: Boolean = false,
        onBusyChanged: (Boolean) -> Unit = {},
        onResult: (GoogleAccountResult) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val content = activity.layoutInflater.inflate(
            R.layout.dialog_account_link_progress,
            null
        )
        val message = content.findViewById<TextView>(R.id.accountGoogleProgressMessage)
        val dialog = GameDialog.custom(
            activity = activity,
            contentView = content,
            widthDp = 340,
            negativeLabel = "CANCELAR"
        )
        val cancelButton = dialog.findViewById<Button>(R.id.gameDialogNegative)
        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        var credentialJob: Job? = null
        lateinit var timeout: Runnable
        lateinit var finish: (GoogleAccountResult) -> Unit

        finish = finish@{ result ->
            if (!completed.compareAndSet(false, true)) return@finish
            handler.removeCallbacks(timeout)
            if (dialog.isShowing) dialog.dismiss()
            onBusyChanged(false)
            if (!activity.isFinishing && !activity.isDestroyed) {
                onResult(result)
            }
        }
        timeout = Runnable {
            val error = TimeoutException("Google account linking exceeded $TIMEOUT_MS ms")
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("auth_flow", "google_account_link")
                setCustomKey("auth_stage", "timeout")
                setCustomKey("auth_error_type", error.javaClass.simpleName)
                recordException(error)
            }
            finish(
                GoogleAccountResult.Failed(
                    activity.getString(R.string.account_google_timeout_message),
                    error
                )
            )
            credentialJob?.cancel()
        }

        onBusyChanged(true)
        cancelButton?.setOnClickListener {
            finish(GoogleAccountResult.Cancelled)
            credentialJob?.cancel()
        }
        dialog.setOnCancelListener {
            finish(GoogleAccountResult.Cancelled)
            credentialJob?.cancel()
        }
        handler.postDelayed(timeout, TIMEOUT_MS)

        credentialJob = GoogleAccountLink.linkOrSignIn(
            activity = activity,
            useAlternativePicker = useAlternativePicker,
            onCredentialReady = {
                if (!completed.get()) {
                    message.setText(R.string.account_google_progress_linking)
                    // Desde este punto Firebase ya puede estar aplicando la vinculación:
                    // ocultar "Cancelar" evita prometer que se puede deshacer a mitad de camino.
                    cancelButton?.visibility = View.GONE
                }
            },
            onResult = finish
        )
    }
}
