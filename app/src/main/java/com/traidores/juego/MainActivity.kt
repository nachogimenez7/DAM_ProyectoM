package com.traidores.juego

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.res.ResourcesCompat
import com.traidores.juego.GameToast as Toast

class MainActivity : BaseActivity() {

    private lateinit var btnMusic: ImageButton
    private lateinit var bandidoIntro: BandidoIntroController
    private var isMusicOn = true
    private var introVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind main buttons
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnRoles: Button = findViewById(R.id.btnRoles)
        val btnHelp: Button = findViewById(R.id.btnHelp)
        val btnOptions: Button = findViewById(R.id.btnOptions)
        val btnAbout: TextView = findViewById(R.id.btnAbout)

        // Bind bottom-bar action
        btnMusic = findViewById(R.id.btnMusic)
        val btnProfile: ImageButton = findViewById(R.id.btnProfile)

        val sharedPref = AudioPreferences.preferences(this)
        loadAudioState(sharedPref)

        bandidoIntro = BandidoIntroController(this) {
            introVisible = false
            if (!isFinishing) {
                MusicManager.playMenuMusic(this)
                maybeShowAccountInvitation()
            }
        }
        if (savedInstanceState == null) {
            introVisible = true
            MusicManager.pauseForTransition()
            bandidoIntro.show()
        } else {
            findViewById<View>(R.id.brandIntroOverlay).post {
                maybeShowAccountInvitation()
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
        if (isFinishing || isDestroyed || !GuestIdentity.isGuest()) return
        val preferences = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE)
        if (preferences.getBoolean(PREF_ACCOUNT_INVITATION_SEEN, false)) return
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
        }
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.apply {
            isAllCaps = false
            textSize = 12f
        }
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.setOnClickListener {
            dialog.dismiss()
            GoogleAccountLink.linkOrSignIn(this) { result ->
                if (isFinishing || isDestroyed) return@linkOrSignIn
                when (result) {
                    is GoogleAccountResult.Linked -> GameNotice.show(
                        this,
                        "Tu perfil ya quedó guardado con Google.",
                        GameNotice.Duration.LONG
                    )
                    is GoogleAccountResult.SignedIn -> GameNotice.show(
                        this,
                        "Recuperaste tu perfil #${result.recoveredPublicId}.",
                        GameNotice.Duration.LONG
                    )
                    GoogleAccountResult.Cancelled -> Unit
                    is GoogleAccountResult.Failed -> GameDialog.confirm(
                        activity = this,
                        title = "No pudimos conectar con Google",
                        message = result.message,
                        positiveLabel = "ABRIR MI PERFIL",
                        negativeLabel = "MÁS TARDE",
                        onConfirm = ::openAccountProfile
                    )
                }
            }
        }
    }

    private fun openAccountProfile() {
        startActivity(
            Intent(this, ProfileActivity::class.java)
                .putExtra(ProfileActivity.EXTRA_OPEN_ACCOUNT, true)
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ONBOARDING_PREFS = "TraidoresPrefs"
        const val PREF_ACCOUNT_INVITATION_SEEN = "account_onboarding_seen_v4"
    }

}
