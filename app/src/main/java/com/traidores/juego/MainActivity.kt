package com.traidores.juego

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import com.traidores.juego.GameToast as Toast
import java.util.concurrent.TimeoutException

class MainActivity : BaseActivity() {

    private lateinit var btnMusic: ImageButton
    private lateinit var bandidoIntro: BandidoIntroController
    private var isMusicOn = true
    private var introVisible = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableNotifications()
        } else {
            NotificationPreferences.setEnabled(this, false)
            Toast.makeText(
                this,
                getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind main buttons
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnRoles: Button = findViewById(R.id.btnRoles)
        val btnHelp: Button = findViewById(R.id.btnHelp)
        val btnOptions: Button = findViewById(R.id.btnOptions)
        val btnFeedback: Button = findViewById(R.id.btnFeedback)
        val btnAbout: View = findViewById(R.id.btnAbout)

        // Bind bottom-bar action
        btnMusic = findViewById(R.id.btnMusic)
        val btnProfile: ImageButton = findViewById(R.id.btnProfile)

        val sharedPref = AudioPreferences.preferences(this)
        loadAudioState(sharedPref)

        bandidoIntro = BandidoIntroController(this) {
            introVisible = false
            if (!isFinishing) {
                MusicManager.playMenuMusic(this)
                maybeShowBetaNoticeThenAccountInvitation()
            }
        }
        if (savedInstanceState == null) {
            introVisible = true
            MusicManager.pauseForTransition()
            bandidoIntro.show()
        } else {
            findViewById<View>(R.id.brandIntroOverlay).post {
                maybeShowBetaNoticeThenAccountInvitation()
            }
        }
        if (intent.getBooleanExtra("account_deleted", false)) {
            getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ACCOUNT_INVITATION_SEEN, true)
                .apply()
            Toast.makeText(
                this,
                getString(R.string.account_delete_success),
                Toast.LENGTH_LONG
            ).show()
            intent.removeExtra("account_deleted")
        }

        // On clicks
        btnPlay.setOnClickListener {
            startActivity(Intent(this, JugarActivity::class.java))
        }

        btnRoles.setOnClickListener {
            startActivity(Intent(this, RolesActivity::class.java))
        }

        btnHelp.setOnClickListener {
            startActivity(Intent(this, AyudaActivity::class.java))
        }

        btnOptions.setOnClickListener {
            startActivity(Intent(this, OpcionesActivity::class.java))
        }

        btnFeedback.setOnClickListener { FeedbackDialog.show(this) }

        btnAbout.setOnClickListener {
            startActivity(Intent(this, AcercaDeActivity::class.java))
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnMusic.setOnClickListener {
            isMusicOn = !isMusicOn
            sharedPref.edit().putBoolean(AudioPreferences.MUSIC_ENABLED, isMusicOn).apply()
            updateAudioButtonIcon()
            MusicManager.refresh(this)
            val msg = if (isMusicOn) "Música activada" else "Música silenciada"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

    }

    override fun onResume() {
        super.onResume()
        PlayGamesIdentity.ensureLinked(this)
        loadAudioState(AudioPreferences.preferences(this))
        if (!introVisible) MusicManager.playMenuMusic(this)
    }

    override fun onDestroy() {
        if (::bandidoIntro.isInitialized) bandidoIntro.release()
        super.onDestroy()
    }

    private fun loadAudioState(sharedPref: android.content.SharedPreferences) {
        isMusicOn = AudioPreferences.isMusicEnabled(sharedPref)
        if (::btnMusic.isInitialized) updateAudioButtonIcon()
    }

    private fun updateAudioButtonIcon() {
        btnMusic.setImageResource(if (isMusicOn) R.drawable.ic_music_note else R.drawable.ic_music_off)
        btnMusic.alpha = if (isMusicOn) 1f else 0.55f
        btnMusic.contentDescription = if (isMusicOn) "Silenciar música" else "Activar música"
    }

    private fun maybeShowAccountInvitation() {
        if (isFinishing || isDestroyed) return
        if (!GuestIdentity.isGuest()) {
            maybeShowNotificationInvitation()
            return
        }
        val preferences = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE)
        if (preferences.getBoolean(PREF_ACCOUNT_INVITATION_SEEN, false)) {
            maybeShowNotificationInvitation()
            return
        }
        preferences.edit().putBoolean(PREF_ACCOUNT_INVITATION_SEEN, true).apply()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(2), dp(10), 0)
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_traidores_clean)
            contentDescription = null
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(68), dp(68)).apply {
            bottomMargin = dp(8)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.account_invitation_title)
            setTextColor(getColor(R.color.accent_gold))
            textSize = 21f
            gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.bree_serif)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.account_invitation_intro)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.12f)
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.account_invitation_benefits)
            setTextColor(getColor(R.color.text_primary))
            textSize = 13.5f
            gravity = Gravity.START
            setLineSpacing(dp(4).toFloat(), 1.08f)
            setPadding(dp(8), dp(13), dp(8), 0)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(TextView(this).apply {
            text = getString(R.string.account_invitation_guest_hint)
            setTextColor(getColor(R.color.text_muted))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            setPadding(dp(4), dp(13), dp(4), dp(2))
        })
        val dialog = GameDialog.custom(
            activity = this,
            contentView = content,
            widthDp = 380,
            negativeLabel = "Ahora no",
            neutralLabel = null,
            positiveLabel = "Vincular con Google"
        )
        dialog.findViewById<Button>(R.id.gameDialogNegative)?.apply {
            isAllCaps = false
            textSize = 12f
            setOnClickListener {
                dialog.dismiss()
                maybeShowNotificationInvitation()
            }
        }
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.apply {
            isAllCaps = false
            textSize = 12f
        }
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.setOnClickListener {
            dialog.dismiss()
            submitOnboardingGoogleRequest()
        }
    }

    private fun maybeShowNotificationInvitation() {
        if (isFinishing || isDestroyed ||
            NotificationPreferences.wasInvitationSeen(this)
        ) return
        NotificationPreferences.markInvitationSeen(this)
        GameDialog.confirm(
            activity = this,
            title = getString(R.string.notification_invitation_title),
            message = getString(R.string.notification_invitation_message),
            positiveLabel = "ACTIVAR",
            negativeLabel = "AHORA NO",
            onConfirm = { requestNotificationPermissionOrEnable() }
        ).setCancelable(false)
    }

    private fun requestNotificationPermissionOrEnable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableNotifications()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun enableNotifications() {
        NotificationPreferences.setEnabled(this, true)
        Toast.makeText(
            this,
            getString(R.string.notification_enabled),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun maybeShowBetaNoticeThenAccountInvitation() {
        if (isFinishing || isDestroyed) return
        val preferences = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE)
        if (preferences.getBoolean(PREF_BETA_NOTICE_SEEN, false)) {
            maybeShowAccountInvitation()
            return
        }
        preferences.edit().putBoolean(PREF_BETA_NOTICE_SEEN, true).apply()
        GameDialog.notice(
            activity = this,
            title = "VERSIÓN BETA",
            message = "Traidores está en etapa de pruebas. Puede haber fallas o diferencias " +
                "de sincronización mientras seguimos mejorando el juego. Gracias por probarlo " +
                "y contarnos qué ocurrió si encontrás un problema.",
            positiveLabel = "ENTENDIDO",
            onPositive = {
                findViewById<View>(R.id.btnPlay).post { maybeShowAccountInvitation() }
            }
        ).setCancelable(false)
    }

    private fun submitOnboardingGoogleRequest(useAlternativePicker: Boolean = false) {
        GoogleAccountFlow.start(
            activity = this,
            useAlternativePicker = useAlternativePicker
        ) { result ->
            when (result) {
                is GoogleAccountResult.Linked -> AccountLinkedDialog.show(this)
                is GoogleAccountResult.SignedIn -> AccountLinkedDialog.show(
                    activity = this,
                    recoveredPublicId = result.recoveredPublicId
                )
                GoogleAccountResult.Cancelled -> Unit
                is GoogleAccountResult.Failed -> GameDialog.confirm(
                    activity = this,
                    title = getString(
                        if (result.error is TimeoutException) {
                            R.string.account_google_timeout_title
                        } else {
                            R.string.account_google_failure_title
                        }
                    ),
                    message = result.message,
                    positiveLabel = getString(R.string.account_google_retry),
                    negativeLabel = "MÁS TARDE",
                    onConfirm = {
                        submitOnboardingGoogleRequest(
                            useAlternativePicker =
                                result.retryWithAlternativePicker
                        )
                    }
                )
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ONBOARDING_PREFS = "TraidoresPrefs"
        const val PREF_ACCOUNT_INVITATION_SEEN = "account_onboarding_seen_v4"
        const val PREF_BETA_NOTICE_SEEN = "beta_notice_seen_v1"
    }

}
