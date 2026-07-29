package com.traidores.juego

import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.traidores.juego.GameToast as Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.games.PlayGames
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : BaseActivity() {

    private data class ProfileDraft(
        var name: String,
        var publicId: String,
        var bio: String,
        var avatarKey: String,
        var bannerKey: String,
        var favoriteRoleKey: String,
        var achievements: List<String>,
        var emoteLoadout: List<String>
    )

    private data class AchievementVisualStyle(
        val backgroundHex: String,
        val borderHex: String,
        val textHex: String,
        val titleHex: String,
        val dateHex: String
    )

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val avatarSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        applyProfileSelectionResult(result) { draftProfile.avatarKey = it }
    }
    private val bannerSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        applyProfileSelectionResult(result) { draftProfile.bannerKey = it }
    }
    private val favoriteRoleSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        applyProfileSelectionResult(result) { draftProfile.favoriteRoleKey = it }
    }
    private val playGamesFriendsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadPlayGamesFriends(forceReload = true)
        } else {
            Toast.makeText(
                this,
                getString(R.string.play_games_friends_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private lateinit var savedProfile: ProfileDraft
    private lateinit var draftProfile: ProfileDraft
    private var isEditing = false

    private lateinit var profileAvatar: ImageView
    private lateinit var profileBanner: View
    private lateinit var favoriteRoleImage: ImageView
    private lateinit var favoriteRoleCard: LinearLayout
    private lateinit var profileName: TextView
    private lateinit var profilePublicId: TextView
    private lateinit var profileBio: TextView
    private lateinit var favoriteRoleName: TextView
    private lateinit var editProfileButton: Button
    private lateinit var editPublicIdIcon: View
    private lateinit var lastMatchCard: LinearLayout
    private lateinit var lastMatchMapRole: TextView
    private lateinit var lastMatchResultDate: TextView
    private lateinit var lastMatchRoleImage: ImageView
    private lateinit var statMatchesValue: TextView
    private lateinit var statWinsValue: TextView
    private lateinit var statWinRateValue: TextView
    private lateinit var profileStatsHint: TextView
    private lateinit var accountStateText: TextView
    private lateinit var accountButton: Button
    private lateinit var deleteAccountButton: Button
    private lateinit var playGamesActions: View
    private lateinit var playGamesAchievementsButton: Button
    private lateinit var playGamesLeaderboardsButton: Button
    private lateinit var playGamesFriendsButton: Button
    private var accountRequestInProgress = false
    private lateinit var achievementViews: List<TextView>
    private lateinit var emoteViews: List<ImageView>
    private lateinit var editIcons: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bindViews()
        savedProfile = loadProfile()
        val restoredEditing = savedInstanceState?.getBoolean(STATE_IS_EDITING) ?: false
        draftProfile = if (restoredEditing) {
            restoreDraft(savedInstanceState) ?: copyProfile(savedProfile)
        } else {
            copyProfile(savedProfile)
        }
        renderProfile()
        setEditing(restoredEditing)
        ensureNumericPublicId()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { handleBack() }
        profileAvatar.setOnClickListener {
            if (isEditing) showAvatarSelector() else showExpandedAvatar()
        }
        profileBanner.setOnClickListener {
            if (isEditing) showBannerSelector()
        }
        favoriteRoleCard.setOnClickListener {
            if (isEditing) {
                showFavoriteRoleSelector()
            } else {
                RoleDetailDialog.show(
                    this,
                    ProfileRoleCatalog.find(draftProfile.favoriteRoleKey).role
                )
            }
        }
        lastMatchCard.setOnClickListener {
            if (MatchHistoryStore.lastMatch(this) != null) {
                showMatchHistory()
            }
        }

        findViewById<View>(R.id.editAvatar).setOnClickListener { showAvatarSelector() }
        findViewById<View>(R.id.editBanner).setOnClickListener { showBannerSelector() }
        findViewById<View>(R.id.editName).setOnClickListener { showNameEditor() }
        findViewById<View>(R.id.editPublicId).setOnClickListener { showFixedPublicIdMessage() }
        findViewById<View>(R.id.editBio).setOnClickListener { showBioEditor() }
        findViewById<View>(R.id.editFavoriteRole).setOnClickListener {
            showFavoriteRoleSelector()
        }
        findViewById<View>(R.id.editAchievements).setOnClickListener {
            showAchievementsSelector()
        }
        findViewById<View>(R.id.editEmotes).setOnClickListener {
            showEmoteSelector()
        }
        findViewById<View>(R.id.editAvatar).contentDescription = "Editar foto de perfil"
        findViewById<View>(R.id.editBanner).contentDescription = "Editar banner del perfil"
        findViewById<View>(R.id.editName).contentDescription = "Editar nombre visible"
        findViewById<View>(R.id.editPublicId).contentDescription = "ID publico fijo"
        findViewById<View>(R.id.editBio).contentDescription = "Editar frase del perfil"
        findViewById<View>(R.id.editFavoriteRole).contentDescription = "Editar rol favorito"
        findViewById<View>(R.id.editAchievements).contentDescription = "Editar logros destacados"
        findViewById<View>(R.id.editEmotes).contentDescription = "Editar emotes del perfil"

        accountButton.setOnClickListener { showAccountDialog() }
        deleteAccountButton.setOnClickListener { showDeleteAccountDialog() }
        playGamesAchievementsButton.setOnClickListener { showPlayGamesAchievements() }
        playGamesLeaderboardsButton.setOnClickListener { showPlayGamesLeaderboards() }
        playGamesFriendsButton.setOnClickListener { loadPlayGamesFriends() }
        if (intent.getBooleanExtra(EXTRA_OPEN_ACCOUNT, false)) {
            intent.removeExtra(EXTRA_OPEN_ACCOUNT)
            accountButton.post { showAccountDialog() }
        }

        profileName.setOnClickListener { if (isEditing) showNameEditor() }
        profilePublicId.setOnClickListener { showFixedPublicIdMessage() }
        profileBio.setOnClickListener { if (isEditing) showBioEditor() }
        emoteViews.forEach { view ->
            view.setOnClickListener { if (isEditing) showEmoteSelector() }
        }
        achievementViews.forEach { view ->
            view.setOnClickListener {
                (view.tag as? String)?.let(::showAchievementDetail)
            }
        }

        editProfileButton.setOnClickListener {
            if (isEditing) saveChanges() else startEditing()
        }
    }

    private fun bindViews() {
        profileAvatar = findViewById(R.id.profileAvatar)
        profileBanner = findViewById(R.id.profileBanner)
        favoriteRoleImage = findViewById(R.id.favoriteRoleImage)
        favoriteRoleCard = findViewById(R.id.favoriteRoleCard)
        profileName = findViewById(R.id.profileName)
        profilePublicId = findViewById(R.id.profilePublicId)
        profileBio = findViewById(R.id.profileBio)
        favoriteRoleName = findViewById(R.id.favoriteRoleName)
        editProfileButton = findViewById(R.id.btnEditProfile)
        editPublicIdIcon = findViewById(R.id.editPublicId)
        lastMatchCard = findViewById(R.id.lastMatchCard)
        lastMatchMapRole = findViewById(R.id.lastMatchMapRole)
        lastMatchResultDate = findViewById(R.id.lastMatchResultDate)
        lastMatchRoleImage = findViewById(R.id.lastMatchRoleImage)
        statMatchesValue = findViewById(R.id.statMatchesValue)
        statWinsValue = findViewById(R.id.statWinsValue)
        statWinRateValue = findViewById(R.id.statWinRateValue)
        profileStatsHint = findViewById(R.id.profileStatsHint)
        accountStateText = findViewById(R.id.profileAccountState)
        accountButton = findViewById(R.id.btnProfileAccount)
        deleteAccountButton = findViewById(R.id.btnDeleteAccount)
        playGamesActions = findViewById(R.id.profilePlayGamesActions)
        playGamesAchievementsButton = findViewById(R.id.btnPlayGamesAchievements)
        playGamesLeaderboardsButton = findViewById(R.id.btnPlayGamesLeaderboards)
        playGamesFriendsButton = findViewById(R.id.btnPlayGamesFriends)
        achievementViews = listOf(
            findViewById(R.id.achievementOne),
            findViewById(R.id.achievementTwo),
            findViewById(R.id.achievementThree)
        )
        emoteViews = listOf(
            findViewById(R.id.emoteOne),
            findViewById(R.id.emoteTwo),
            findViewById(R.id.emoteThree),
            findViewById(R.id.emoteFour)
        )
        editIcons = listOf(
            findViewById(R.id.editAvatar),
            findViewById(R.id.editBanner),
            findViewById(R.id.editName),
            findViewById(R.id.editBio),
            findViewById(R.id.editFavoriteRole),
            findViewById(R.id.editAchievements),
            findViewById(R.id.editEmotes)
        )
    }

    private fun loadProfile(): ProfileDraft {
        val profile = PlayerProfileStore.loadHumanProfile(this)
        return ProfileDraft(
            name = profile.name,
            publicId = profile.publicId,
            bio = profile.bio,
            avatarKey = profile.avatarKey,
            bannerKey = profile.bannerKey,
            favoriteRoleKey = profile.favoriteRoleKey,
            achievements = profile.featuredAchievementIds
                .mapNotNull { ProfileCustomizationCatalog.achievementById(it)?.name }
                .let(::validFeaturedAchievements),
            emoteLoadout = profile.emoteIds
        )
    }

    /**
     * Se consulta a Firebase en vez de guardarse en un campo: la cuenta puede vincularse desde
     * esta misma pantalla y todo lo que depende del estado tiene que reflejarlo enseguida.
     */
    private val isGuestAccount: Boolean
        get() = GuestIdentity.isGuest()

    /**
     * Puerta unica de la personalizacion. Devuelve `true` cuando el jugador es invitado, o
     * sea cuando quien llama tiene que cortar lo que estaba por hacer.
     *
     * El invitado igual puede **mirar** los catalogos de avatar y banner: se lo frena recien
     * al elegir. Ver algo lindo y chocar con la puerta explica mucho mejor para que sirve una
     * cuenta que un boton que directamente no existe.
     */
    private fun requireAccountFor(action: String): Boolean {
        if (!isGuestAccount) return false
        GameDialog.confirm(
            activity = this,
            title = "Solo con cuenta",
            message = "$action necesita una cuenta. Es gratis, tarda un minuto y no perdés " +
                "nada de lo que ya jugaste: se te guarda todo tal cual está.",
            positiveLabel = "CREAR CUENTA",
            negativeLabel = "AHORA NO"
        ) { showAccountDialog() }
        return true
    }

    private fun renderAccountSection() {
        val email = AccountLink.currentEmail()
        val emailLinked = email.isNotBlank()
        val playGamesLinked = PlayGamesIdentity.hasPlayGamesProvider()
        accountStateText.text = when {
            emailLinked -> getString(R.string.profile_account_linked, email)
            playGamesLinked -> getString(R.string.profile_account_play_games)
            else -> getString(R.string.profile_account_guest)
        }
        accountButton.isEnabled = !emailLinked && !accountRequestInProgress
        accountButton.alpha = if (accountButton.isEnabled) 1f else 0.5f
        accountButton.text = when {
            emailLinked -> getString(R.string.profile_account_action_linked)
            playGamesLinked -> getString(R.string.profile_account_action_add_email)
            else -> getString(R.string.profile_account_action)
        }
        deleteAccountButton.visibility = if (isGuestAccount) View.GONE else View.VISIBLE
        deleteAccountButton.isEnabled = !accountRequestInProgress
        deleteAccountButton.alpha = if (deleteAccountButton.isEnabled) 1f else 0.5f
        playGamesActions.visibility = if (
            playGamesLinked && PlayGamesConfig.isIdentityConfigured(this)
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showPlayGamesAchievements() {
        if (!PlayGamesIdentity.isReady(this)) return
        PlayGames.getAchievementsClient(this)
            .achievementsIntent
            .addOnSuccessListener(::startActivity)
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.play_games_open_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showPlayGamesLeaderboards() {
        if (!PlayGamesIdentity.isReady(this)) return
        PlayGames.getLeaderboardsClient(this)
            .allLeaderboardsIntent
            .addOnSuccessListener(::startActivity)
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.play_games_open_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun loadPlayGamesFriends(forceReload: Boolean = false) {
        if (!PlayGamesIdentity.isReady(this)) return
        PlayGamesFriends.load(this, forceReload) { result ->
            when (result) {
                is PlayGamesFriendsResult.Loaded -> showPlayGamesFriends(result.friends)
                is PlayGamesFriendsResult.PermissionRequired -> {
                    runCatching {
                        playGamesFriendsPermissionLauncher.launch(
                            IntentSenderRequest.Builder(result.resolution).build()
                        )
                    }.onFailure {
                        Toast.makeText(
                            this,
                            getString(R.string.play_games_open_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is PlayGamesFriendsResult.Failed -> {
                    OnlineDebugLog.e("play_games_friends_failure", result.error)
                    Toast.makeText(
                        this,
                        getString(R.string.play_games_friends_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showPlayGamesFriends(friends: List<PlayGamesFriend>) {
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        rows.addView(
            TextView(this).apply {
                text = getString(R.string.play_games_friends_title)
                setTextColor(getColor(R.color.accent_gold))
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(10))
            }
        )
        if (friends.isEmpty()) {
            rows.addView(
                TextView(this).apply {
                    text = getString(R.string.play_games_friends_empty)
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(18), dp(10), dp(18))
                }
            )
        } else {
            friends.forEach { friend ->
                rows.addView(
                    TextView(this).apply {
                        text = friend.displayName.ifBlank {
                            getString(R.string.play_games_friend_unknown)
                        }
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 16f
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_btn_dark)
                        minHeight = dp(48)
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
                )
            }
        }
        GameDialog.custom(
            activity = this,
            contentView = ScrollView(this).apply { addView(rows) },
            widthDp = 420,
            contentHeightDp = 360,
            negativeLabel = getString(R.string.play_games_close)
        )
    }

    /**
     * Vincula una cuenta a la identidad anonima que ya tiene este dispositivo. No hay
     * "cerrar sesion" a proposito: salir dejaria el `#` guardado apuntando a un uid que ya
     * no es el de la sesion, que es justo el escenario que rompe la identidad.
     */
    private fun showAccountDialog() {
        if (accountRequestInProgress || AccountLink.currentEmail().isNotBlank()) return
        val content = layoutInflater.inflate(R.layout.dialog_account_link, null)
        val emailInput = content.findViewById<EditText>(R.id.accountEmailInput)
        val passwordInput = content.findViewById<EditText>(R.id.accountPasswordInput)
        val errorText = content.findViewById<TextView>(R.id.accountDialogError)
        val googleButton = content.findViewById<Button>(R.id.accountGoogleButton)

        val dialog = GameDialog.custom(
            activity = this,
            contentView = content,
            widthDp = 400,
            negativeLabel = "CANCELAR",
            positiveLabel = "CONTINUAR"
        )
        // El boton positivo de GameDialog cierra siempre. Se reemplaza su accion para que un
        // error de tipeo no borre lo que el jugador ya escribio.
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val problem = AccountCredentials.validationError(email, password)
            if (problem != null) {
                errorText.text = problem
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dialog.dismiss()
            submitAccountRequest(email, password)
        }
        googleButton.setOnClickListener {
            dialog.dismiss()
            submitGoogleAccountRequest()
        }
    }

    private fun submitGoogleAccountRequest() {
        accountRequestInProgress = true
        renderAccountSection()
        GoogleAccountLink.linkOrSignIn(this) { result ->
            accountRequestInProgress = false
            if (isFinishing || isDestroyed) return@linkOrSignIn
            when (result) {
                is GoogleAccountResult.Linked -> GameNotice.show(
                    this,
                    "Listo. Tu perfil quedó vinculado a ${result.email.ifBlank { "Google" }}.",
                    GameNotice.Duration.LONG
                )
                is GoogleAccountResult.SignedIn -> {
                    draftProfile.publicId = result.recoveredPublicId
                    savedProfile.publicId = result.recoveredPublicId
                    renderProfile()
                    GameNotice.show(
                        this,
                        "Entraste con Google y recuperaste tu perfil #${result.recoveredPublicId}.",
                        GameNotice.Duration.LONG
                    )
                }
                GoogleAccountResult.Cancelled -> Unit
                is GoogleAccountResult.Failed -> {
                    result.error?.let { OnlineDebugLog.e("google_account_failure", it) }
                    GameNotice.show(this, result.message, GameNotice.Duration.LONG)
                }
            }
            renderAccountSection()
        }
    }

    private fun submitAccountRequest(email: String, password: String) {
        accountRequestInProgress = true
        renderAccountSection()
        AccountLink.linkOrSignIn(this, email, password) { result ->
            accountRequestInProgress = false
            if (isFinishing || isDestroyed) return@linkOrSignIn
            when (result) {
                is AccountLinkResult.Linked -> {
                    GameNotice.show(
                        activity = this,
                        message = getString(R.string.profile_account_linked_toast, result.email),
                        duration = GameNotice.Duration.LONG
                    )
                }
                is AccountLinkResult.SignedIn -> {
                    // El uid cambio: el `#` que se muestra es el de la cuenta recuperada.
                    draftProfile.publicId = result.recoveredPublicId
                    savedProfile.publicId = result.recoveredPublicId
                    GameNotice.show(
                        activity = this,
                        message = getString(
                            R.string.profile_account_recovered_toast,
                            result.email,
                            result.recoveredPublicId
                        ),
                        duration = GameNotice.Duration.LONG
                    )
                    renderProfile()
                }
                is AccountLinkResult.Failed -> {
                    GameNotice.show(
                        activity = this,
                        message = result.message,
                        duration = GameNotice.Duration.LONG
                    )
                }
            }
            renderAccountSection()
        }
    }

    private fun showDeleteAccountDialog() {
        if (isGuestAccount || accountRequestInProgress) return
        val hasPasswordProvider = FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.any { it.providerId == "password" } == true
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        content.addView(
            TextView(this).apply {
                text = getString(R.string.account_delete_explanation)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setLineSpacing(0f, 1.15f)
            }
        )
        val confirmationInput = EditText(this).apply {
            hint = AccountDeletion.CONFIRMATION_TEXT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
        }
        content.addView(
            confirmationInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        val passwordInput = if (hasPasswordProvider) {
            EditText(this).apply {
                hint = getString(R.string.account_delete_password_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                setTextColor(getColor(R.color.text_primary))
                setHintTextColor(getColor(R.color.text_muted))
                content.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                )
            }
        } else {
            null
        }
        val errorText = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(errorText)

        val dialog = GameDialog.custom(
            activity = this,
            contentView = content,
            widthDp = 420,
            negativeLabel = getString(R.string.account_delete_cancel),
            positiveLabel = getString(R.string.account_delete_action)
        )
        dialog.findViewById<Button>(R.id.gameDialogPositive)?.setOnClickListener {
            if (confirmationInput.text.toString().trim() != AccountDeletion.CONFIRMATION_TEXT) {
                errorText.text = getString(
                    R.string.account_delete_confirmation_error,
                    AccountDeletion.CONFIRMATION_TEXT
                )
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (hasPasswordProvider && passwordInput?.text.isNullOrBlank()) {
                errorText.text = getString(R.string.account_delete_password_error)
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dialog.dismiss()
            submitAccountDeletion(passwordInput?.text?.toString())
        }
    }

    private fun submitAccountDeletion(password: String?) {
        accountRequestInProgress = true
        renderAccountSection()
        GameNotice.show(
            activity = this,
            message = getString(R.string.account_delete_progress),
            duration = GameNotice.Duration.LONG
        )
        AccountDeletion.delete(this, password) { result ->
            accountRequestInProgress = false
            if (isFinishing || isDestroyed) return@delete
            when (result) {
                AccountDeletionResult.Deleted -> {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                            putExtra("account_deleted", true)
                        }
                    )
                    finish()
                }
                is AccountDeletionResult.Failed -> {
                    result.error?.let {
                        OnlineDebugLog.e("account_deletion_failure", it)
                    }
                    GameNotice.show(
                        activity = this,
                        message = result.message,
                        duration = GameNotice.Duration.LONG
                    )
                    renderAccountSection()
                }
            }
        }
    }

    private fun renderProfile() {
        renderAccountSection()
        profileName.text = draftProfile.name
        // El `#` es de las cuentas. Al invitado se le muestra su condicion en ese lugar, en
        // vez de un "#SIN ID" que parece un error del juego.
        profilePublicId.text = when {
            draftProfile.publicId.isNotBlank() -> "#${draftProfile.publicId}"
            isGuestAccount -> "INVITADO"
            else -> "#SIN ID"
        }
        val hasBio = draftProfile.bio.isNotBlank()
        profileBio.text = if (hasBio) {
            "\"${draftProfile.bio}\""
        } else {
            EMPTY_BIO_PLACEHOLDER
        }
        profileBio.setTextColor(getColor(R.color.text_primary))

        val avatarEntry = ProfileRoleCatalog.find(draftProfile.avatarKey)
        setRoleImage(profileAvatar, avatarEntry.role)
        alignAvatarToFocus(profileAvatar, avatarEntry.verticalFocus)

        profileBanner.setBackgroundResource(
            ProfileCustomizationCatalog.banner(draftProfile.bannerKey).drawableRes
        )

        val favoriteRole = ProfileRoleCatalog.find(draftProfile.favoriteRoleKey).role
        favoriteRoleName.text = favoriteRole.name
        setRoleImage(favoriteRoleImage, favoriteRole)
        alignRoleThumbnailFromTop(favoriteRoleImage)
        updateInteractiveContentDescriptions(favoriteRole.name)
        renderEmoteLoadout()

        achievementViews.forEachIndexed { index, view ->
            val achievementName = draftProfile.achievements.getOrNull(index)
            val achievement = achievementName
                ?.let(ProfileCustomizationCatalog::achievement)
                ?.let { AchievementTracker.achievementWithProgress(this, it) }
            view.text = achievement?.shortName ?: achievementName.orEmpty()
            view.tag = achievement?.name
            view.contentDescription = achievement?.let { "Ver logro ${it.name}" }
            view.visibility = if (achievement == null) View.INVISIBLE else View.VISIBLE
            view.isEnabled = achievement != null
            view.isClickable = achievement != null
            achievement?.let { applyAchievementBadgeStyle(view, it.rarity) }
        }
        renderMatchProgress()
    }

    private fun renderMatchProgress() {
        val stats = MatchHistoryStore.stats(this)
        statMatchesValue.text = stats.matches.toString()
        statWinsValue.text = stats.wins.toString()
        statWinRateValue.text = "${stats.winRatePercent}%"
        profileStatsHint.text = if (stats.matches == 0) {
            "Las estadísticas aparecerán cuando termines una partida local."
        } else {
            "Progreso de partidas locales finalizadas."
        }

        val lastMatch = MatchHistoryStore.lastMatch(this)
        if (lastMatch == null) {
            lastMatchMapRole.text = "Todavía no jugaste ninguna partida."
            lastMatchResultDate.text = "Las partidas locales finalizadas aparecerán aquí."
            lastMatchResultDate.setTextColor(getColor(R.color.text_secondary))
            lastMatchRoleImage.visibility = View.GONE
            lastMatchCard.background = getDrawable(R.drawable.bg_profile_stat)
            lastMatchCard.contentDescription = "Todavía no hay partidas en el historial"
            lastMatchCard.isEnabled = false
            return
        }

        lastMatchMapRole.text = "${lastMatch.mapName} · ${lastMatch.roleName}"
        lastMatchResultDate.text = buildString {
            append(if (lastMatch.won) "VICTORIA" else "DERROTA")
            append(" · ")
            append(formatMatchDate(lastMatch.dateEpochMs))
        }
        lastMatchResultDate.setTextColor(
            getColor(if (lastMatch.won) R.color.winner_town_accent else R.color.traitor_red_bright)
        )
        bindMatchRoleImage(lastMatchRoleImage, lastMatch)
        lastMatchCard.background = matchHistoryBackground(lastMatch.won, emphasized = true)
        lastMatchCard.contentDescription =
            "Última partida, ${lastMatch.mapName}, ${lastMatch.roleName}, " +
                (if (lastMatch.won) "victoria" else "derrota")
        lastMatchCard.isEnabled = true
    }

    private fun showMatchHistory() {
        val records = MatchHistoryStore.lastMatches(this, 5)
        if (records.isEmpty()) return
        val content = layoutInflater.inflate(R.layout.dialog_match_history, null)
        val list: LinearLayout = content.findViewById(R.id.matchHistoryList)
        content.findViewById<View>(R.id.matchHistoryScroll).layoutParams =
            content.findViewById<View>(R.id.matchHistoryScroll).layoutParams.apply {
                height = dp((resources.configuration.screenHeightDp - 180).coerceIn(140, 280))
            }
        records.forEach { record ->
            list.addView(
                createMatchHistoryRow(record),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        content.findViewById<Button>(R.id.btnCloseMatchHistory).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = resources.displayMetrics.widthPixels - dp(24)
                setLayout(dp(390).coerceAtMost(maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    private fun createMatchHistoryRow(record: MatchRecord): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = matchHistoryBackground(record.won, emphasized = false)
            contentDescription =
                "${record.mapName}, ${record.roleName}, " +
                    (if (record.won) "victoria" else "derrota")

            addView(
                LinearLayout(this@ProfileActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@ProfileActivity).apply {
                        text = "${record.mapName} · ${record.roleName}"
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 15f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(TextView(this@ProfileActivity).apply {
                        text = "${if (record.won) "VICTORIA" else "DERROTA"} · " +
                            formatMatchDate(record.dateEpochMs)
                        setTextColor(
                            getColor(
                                if (record.won) R.color.winner_town_accent
                                else R.color.traitor_red_bright
                            )
                        )
                        textSize = 12f
                        setPadding(0, dp(4), 0, 0)
                    })
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10)
                }
            )
            addView(ImageView(this@ProfileActivity).apply {
                scaleType = ImageView.ScaleType.MATRIX
                setBackgroundResource(R.drawable.bg_role_card)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                clipToOutline = true
                this@ProfileActivity.bindMatchRoleImage(this, record)
            }, LinearLayout.LayoutParams(dp(54), dp(54)))
        }
    }

    private fun bindMatchRoleImage(view: ImageView, record: MatchRecord) {
        val roleImage = runCatching {
            RoleCatalog.gameRole(
                record.roleKey,
                RoleMap.fromSessionKey(record.mapKey)
            ).imageResName
        }.getOrNull()
        val imageRes = roleImage
            ?.let { resources.getIdentifier(it, "drawable", packageName) }
            ?.takeIf { it != 0 }
        if (imageRes == null) {
            view.visibility = View.GONE
            return
        }
        view.setImageResource(imageRes)
        alignRoleThumbnailFromTop(view)
        view.contentDescription = "Rol ${record.roleName}"
        view.visibility = View.VISIBLE
    }

    private fun matchHistoryBackground(won: Boolean, emphasized: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (emphasized) 10 else 8).toFloat()
            setColor(Color.parseColor(if (won) "#D91B2A1D" else "#D92A1718"))
            setStroke(
                dp(if (emphasized) 2 else 1),
                getColor(if (won) R.color.accent_green else R.color.accent_red)
            )
        }
    }

    private fun formatMatchDate(epochMs: Long): String {
        return SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    private fun ensureNumericPublicId() {
        if (draftProfile.publicId.isNotBlank()) return
        // Un invitado no reserva numero: el `#` es la señal de que hay una cuenta detras.
        if (isGuestAccount) return
        PlayerPublicIdentity.ensurePublicId(
            context = this,
            firestore = FirebaseFirestore.getInstance(),
            onReady = { publicId ->
                savedProfile.publicId = publicId
                draftProfile.publicId = publicId
                renderProfile()
            },
            onFailure = { error ->
                OnlineDebugLog.e("public_id_profile_allocate_fallback", error)
            }
        )
    }

    private fun validFeaturedAchievements(names: List<String>): List<String> {
        val unlocked = AchievementTracker.unlockedAchievements(this)
        val validNames = unlocked.map { it.name }.toSet()
        val selected = names
            .filter { it in validNames }
            .distinct()
            .take(MAX_FEATURED_ACHIEVEMENTS)
        return selected.ifEmpty {
            unlocked.map { it.name }.take(MAX_FEATURED_ACHIEVEMENTS)
        }
    }

    private fun renderEmoteLoadout() {
        val ids = EmoteLoadout.normalizeIds(draftProfile.emoteLoadout)
        if (draftProfile.emoteLoadout != ids) {
            draftProfile.emoteLoadout = ids
        }
        emoteViews.forEachIndexed { index, image ->
            val spec = EmoteCatalog.byId(ids[index]) ?: return@forEachIndexed
            image.setImageResource(spec.imageRes)
            image.contentDescription = "${spec.label} - ${spec.themeLabel}"
            image.isClickable = isEditing
            image.isFocusable = isEditing
        }
    }

    private fun setRoleImage(image: ImageView, role: Role) {
        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        image.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
    }

    private fun startEditing() {
        draftProfile = copyProfile(savedProfile)
        setEditing(true)
        renderProfile()
    }

    private fun setEditing(editing: Boolean) {
        isEditing = editing
        val guest = isGuestAccount
        editIcons.forEach { icon ->
            icon.visibility = if (editing) View.VISIBLE else View.GONE
            // El candado reemplaza al lapiz: el invitado ve que la opcion existe y que le
            // falta algo para usarla, en vez de encontrarse una pantalla sin botones. El
            // nombre queda con lapiz porque el alias si lo puede cambiar.
            val locked = guest && icon.id != R.id.editName
            (icon as? ImageButton)?.setImageResource(
                if (locked) R.drawable.ic_lock_gold else R.drawable.ic_edit_pencil
            )
        }
        emoteViews.forEach {
            it.isClickable = editing
            it.isFocusable = editing
        }
        editPublicIdIcon.visibility = View.GONE
        editProfileButton.text = if (editing) "GUARDAR CAMBIOS" else "EDITAR PERFIL"
        editProfileButton.contentDescription = if (editing) {
            "Guardar cambios del perfil"
        } else {
            "Entrar al modo edicion del perfil"
        }
        updateInteractiveContentDescriptions(favoriteRoleName.text.toString())
    }

    private fun saveChanges() {
        // Un invitado no guarda nada: su alias ya se guardo al elegirlo y el resto del perfil
        // esta bloqueado. Escribir aca ademas seria un error, porque dejaria el nombre
        // derivado ("Aguafiestas 4821") como nombre propio el dia que se registre.
        if (isGuestAccount) {
            setEditing(false)
            renderProfile()
            return
        }
        draftProfile.emoteLoadout = EmoteLoadout.normalizeIds(draftProfile.emoteLoadout)
        preferences.edit()
            .putString(PREF_NAME, draftProfile.name)
            .putString(OpcionesActivity.PREF_PLAYER_NAME, draftProfile.name)
            .putString(PREF_BIO, draftProfile.bio)
            .putString(PREF_AVATAR, draftProfile.avatarKey)
            .putString(PREF_BANNER, draftProfile.bannerKey)
            .putString(PREF_FAVORITE_ROLE, draftProfile.favoriteRoleKey)
            .putString(
                PREF_ACHIEVEMENTS,
                draftProfile.achievements.joinToString(ACHIEVEMENT_SEPARATOR)
            )
            .apply()
        EmoteLoadout.save(this, draftProfile.emoteLoadout)

        savedProfile = copyProfile(draftProfile)
        PlayGamesProgressSync.onProfileSaved(this)
        setEditing(false)
        Toast.makeText(this, "Perfil actualizado.", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isGuestAccount) {
            PlayGamesProgressSync.onProfileSaved(this)
        }
        super.onStop()
    }

    private fun handleBack() {
        if (!isEditing) {
            finish()
            return
        }

        GameDialog.confirm(
            activity = this,
            title = "Descartar cambios",
            message = "Los cambios del perfil todavía no fueron guardados.",
            positiveLabel = "DESCARTAR",
            negativeLabel = "SEGUIR EDITANDO"
        ) {
            draftProfile = copyProfile(savedProfile)
            setEditing(false)
            renderProfile()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_IS_EDITING, isEditing)
        if (isEditing) {
            outState.putString(STATE_DRAFT_NAME, draftProfile.name)
            outState.putString(STATE_DRAFT_PUBLIC_ID, draftProfile.publicId)
            outState.putString(STATE_DRAFT_BIO, draftProfile.bio)
            outState.putString(STATE_DRAFT_AVATAR, draftProfile.avatarKey)
            outState.putString(STATE_DRAFT_BANNER, draftProfile.bannerKey)
            outState.putString(STATE_DRAFT_FAVORITE_ROLE, draftProfile.favoriteRoleKey)
            outState.putStringArrayList(
                STATE_DRAFT_ACHIEVEMENTS,
                ArrayList(draftProfile.achievements)
            )
            outState.putStringArrayList(
                STATE_DRAFT_EMOTES,
                ArrayList(draftProfile.emoteLoadout)
            )
        }
        super.onSaveInstanceState(outState)
    }

    private fun showExpandedAvatar() {
        val content = layoutInflater.inflate(R.layout.dialog_profile_avatar, null)
        val expandedAvatar: ImageView = content.findViewById(R.id.expandedProfileAvatar)
        val avatarEntry = ProfileRoleCatalog.find(draftProfile.avatarKey)
        setRoleImage(expandedAvatar, avatarEntry.role)
        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        content.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(220))
                val maxHeight = (resources.displayMetrics.heightPixels - dp(48)).coerceAtLeast(dp(220))
                setLayout(dp(320).coerceAtMost(maxWidth), dp(320).coerceAtMost(maxHeight))
            }
            // Mismo encuadre tipo retrato que el avatar chico (alignAvatarToFocus),
            // en vez de centerCrop plano: consistencia visual y evita que la version
            // ampliada muestre la imagen sin recortar dentro del marco circular.
            alignAvatarToFocus(expandedAvatar, avatarEntry.verticalFocus)
        }
        dialog.show()
    }

    private fun showAvatarSelector() {
        avatarSelectionLauncher.launch(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_AVATAR,
                draftProfile.avatarKey
            )
        )
    }

    private fun showBannerSelector() {
        bannerSelectionLauncher.launch(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_BANNER,
                draftProfile.bannerKey
            )
        )
    }

    private fun showNameEditor() {
        // El invitado no escribe su nombre: elige un alias de la lista cerrada. Es la unica
        // personalizacion que se le deja, porque no tiene forma de abusarse y sin ella los
        // amigos no se distinguen entre si dentro de una sala.
        if (isGuestAccount) {
            showGuestAliasPicker()
            return
        }
        showTextEditor(
            title = "Editar nombre",
            currentValue = draftProfile.name,
            maxLength = MAX_NAME_LENGTH,
            hint = "Nombre visible"
        ) { value ->
            if (value.isBlank()) {
                "El nombre no puede quedar vacio."
            } else {
                draftProfile.name = value
                renderProfile()
                null
            }
        }
    }

    /**
     * Lista cerrada de alias para invitados. No hay campo de texto a proposito: es lo que
     * hace imposible que un jugador sin cuenta entre a una sala con un nombre ofensivo, y es
     * la unica parte del control de contenido que hoy se puede verificar en el servidor.
     */
    private fun showGuestAliasPicker() {
        val number = GuestIdentity.guestNumber(this)
        val current = GuestIdentity.selectedAlias(this)
        var dialog: androidx.appcompat.app.AlertDialog? = null

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        container.addView(
            TextView(this).apply {
                text = "TU ALIAS DE INVITADO"
                setTextColor(getColor(R.color.accent_gold))
                textSize = 16f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            }
        )
        container.addView(
            TextView(this).apply {
                text = "El número es tuyo y no cambia. Con una cuenta podés usar el nombre " +
                    "que quieras."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(10))
            }
        )

        GuestIdentity.aliases.forEach { alias ->
            val selected = alias == current
            val option = TextView(this).apply {
                text = "$alias $number"
                setBackgroundResource(R.drawable.bg_btn_dark)
                setTextColor(getColor(if (selected) R.color.accent_gold else R.color.text_primary))
                textSize = 16f
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                isFocusable = true
                contentDescription = if (selected) "$alias, alias actual" else alias
                setOnClickListener {
                    GuestIdentity.saveAlias(this@ProfileActivity, alias)
                    val updatedName = GuestIdentity.displayName(this@ProfileActivity)
                    draftProfile.name = updatedName
                    savedProfile.name = updatedName
                    renderProfile()
                    dialog?.dismiss()
                }
            }
            container.addView(
                option,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }
        dialog = GameDialog.custom(
            activity = this,
            contentView = scroll,
            widthDp = 380,
            contentHeightDp = 420,
            negativeLabel = "CERRAR"
        )
    }

    private fun showFixedPublicIdMessage() {
        Toast.makeText(
            this,
            "Tu ID publico es fijo y se usa para agregarte como amigo.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBioEditor() {
        if (requireAccountFor("Escribir tu frase")) return
        showTextEditor(
            title = "Editar frase",
            currentValue = draftProfile.bio,
            maxLength = MAX_BIO_LENGTH,
            hint = "Hasta 40 caracteres"
        ) { value ->
            draftProfile.bio = value
            renderProfile()
            null
        }
    }

    private fun showTextEditor(
        title: String,
        currentValue: String,
        maxLength: Int,
        hint: String,
        onAccept: (String) -> String?
    ) {
        GameDialog.input(
            activity = this,
            title = title,
            currentValue = currentValue,
            hint = hint,
            maxLength = maxLength,
            onAccept = onAccept
        )
    }

    private fun showEmoteSelector() {
        if (requireAccountFor("Elegir tus emotes")) return
        val content = layoutInflater.inflate(R.layout.dialog_emote_selector, null)
        val counter: TextView = content.findViewById(R.id.emoteSelectorCounter)
        val themeContainer: LinearLayout = content.findViewById(R.id.emoteThemeContainer)
        val selectedIds = EmoteLoadout.normalizeIds(draftProfile.emoteLoadout).toMutableList()
        val optionViews = mutableMapOf<String, FrameLayout>()
        val orderBadges = mutableMapOf<String, TextView>()

        fun refreshSelectionState() {
            counter.text = "${selectedIds.size}/${EmoteCatalog.LOADOUT_SIZE}"
            EmoteCatalog.all.forEach { spec ->
                val order = selectedIds.indexOf(spec.id)
                val selected = order >= 0
                optionViews[spec.id]?.background = emoteOptionBackground(selected, spec.toneHex)
                orderBadges[spec.id]?.apply {
                    text = if (selected) (order + 1).toString() else ""
                    visibility = if (selected) View.VISIBLE else View.GONE
                }
            }
        }

        EmoteCatalog.byTheme().values.forEachIndexed { themeIndex, emotes ->
            val title = TextView(this).apply {
                text = emotes.firstOrNull()?.themeLabel.orEmpty().uppercase()
                setTextColor(getColor(R.color.accent_gold))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                if (themeIndex > 0) {
                    setPadding(0, dp(12), 0, dp(4))
                } else {
                    setPadding(0, 0, 0, dp(4))
                }
            }
            themeContainer.addView(
                title,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            emotes.forEachIndexed { index, spec ->
                val option = FrameLayout(this).apply {
                    isClickable = true
                    isFocusable = true
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    contentDescription = "${spec.label} - ${spec.themeLabel}"
                    setOnClickListener {
                        val currentIndex = selectedIds.indexOf(spec.id)
                        if (currentIndex >= 0) {
                            selectedIds.removeAt(currentIndex)
                        } else if (selectedIds.size >= EmoteCatalog.LOADOUT_SIZE) {
                            Toast.makeText(this@ProfileActivity, "Ya elegiste 4 emotes.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedIds += spec.id
                        }
                        refreshSelectionState()
                    }
                }
                val icon = ImageView(this).apply {
                    setImageResource(spec.imageRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }
                option.addView(
                    icon,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                val orderBadge = TextView(this).apply {
                    background = getDrawable(R.drawable.bg_emote_order_badge)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(getColor(R.color.bg_dark))
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    visibility = View.GONE
                }
                option.addView(
                    orderBadge,
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END)
                )
                optionViews[spec.id] = option
                orderBadges[spec.id] = orderBadge
                row.addView(
                    option,
                    LinearLayout.LayoutParams(0, dp(72), 1f).apply {
                        if (index > 0) leftMargin = dp(4)
                        if (index < emotes.lastIndex) rightMargin = dp(4)
                    }
                )
            }
            themeContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        content.findViewById<Button>(R.id.btnCancelEmoteSelection).setOnClickListener {
            dialog.dismiss()
        }
        content.findViewById<Button>(R.id.btnApplyEmoteSelection).setOnClickListener {
            if (selectedIds.size != EmoteCatalog.LOADOUT_SIZE) {
                Toast.makeText(this, "Elegi 4 emotes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            draftProfile.emoteLoadout = selectedIds.toList()
            renderProfile()
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = resources.displayMetrics.widthPixels - dp(24)
                setLayout(dp(380).coerceAtMost(maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        refreshSelectionState()
        dialog.show()
    }

    private fun showFavoriteRoleSelector() {
        favoriteRoleSelectionLauncher.launch(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_FAVORITE_ROLE,
                draftProfile.favoriteRoleKey
            )
        )
    }

    private fun applyProfileSelectionResult(
        result: ActivityResult,
        applySelection: (String) -> Unit
    ) {
        if (result.resultCode != Activity.RESULT_OK) return
        val selectedKey = result.data
            ?.getStringExtra(ProfileSelectionActivity.EXTRA_SELECTED_KEY)
            .orEmpty()
        if (selectedKey.isBlank()) return
        // El invitado pudo recorrer el catalogo entero; el corte esta al elegir.
        if (requireAccountFor("Quedarte con lo que elegiste")) return
        applySelection(selectedKey)
        renderProfile()
    }

    private fun showAchievementsSelector() {
        if (requireAccountFor("Elegir que logros mostrar")) return
        val achievements = AchievementTracker.achievementsWithProgress(this)
        val unlockedNames = AchievementTracker.unlockedAchievements(this)
            .map { it.name }
            .toSet()
        val content = layoutInflater.inflate(R.layout.dialog_achievement_selector, null)
        val counter: TextView = content.findViewById(R.id.achievementSelectorCounter)
        val listContainer: LinearLayout = content.findViewById(R.id.achievementListContainer)
        val selectedNames = validFeaturedAchievements(draftProfile.achievements).toMutableList()
        val rowViews = mutableMapOf<String, LinearLayout>()
        val orderBadges = mutableMapOf<String, TextView>()

        fun refreshSelectionState() {
            counter.text = "${selectedNames.size}/${MAX_FEATURED_ACHIEVEMENTS}"
            achievements.forEach { achievement ->
                val order = selectedNames.indexOf(achievement.name)
                val selected = order >= 0
                rowViews[achievement.name]?.background =
                    achievementSelectionBackground(selected, achievement.rarity)
                orderBadges[achievement.name]?.apply {
                    text = if (selected) (order + 1).toString() else ""
                    visibility = if (selected) View.VISIBLE else View.GONE
                }
            }
        }

        achievements.forEach { achievement ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(10), dp(10), dp(10), dp(10))
                alpha = if (achievement.name in unlockedNames) 1f else 0.58f
                contentDescription = if (achievement.name in unlockedNames) {
                    "Logro ${achievement.name}"
                } else {
                    "Logro pendiente ${achievement.name}"
                }
                setOnClickListener {
                    if (achievement.name !in unlockedNames) {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Todavia no desbloqueaste este logro.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    val currentIndex = selectedNames.indexOf(achievement.name)
                    if (currentIndex >= 0) {
                        selectedNames.removeAt(currentIndex)
                    } else if (selectedNames.size >= MAX_FEATURED_ACHIEVEMENTS) {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Ya elegiste 3 logros destacados.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        selectedNames += achievement.name
                    }
                    refreshSelectionState()
                }
            }

            val medalFrame = FrameLayout(this).apply {
                background = achievementMedalFrameBackground(achievement.rarity)
            }
            val medal = ImageView(this).apply {
                setImageResource(achievement.rarity.medalRes)
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            medalFrame.addView(
                medal,
                FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)
            )
            val orderBadge = TextView(this).apply {
                background = getDrawable(R.drawable.bg_emote_order_badge)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(getColor(R.color.bg_dark))
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                visibility = View.GONE
            }
            medalFrame.addView(
                orderBadge,
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END)
            )
            row.addView(
                medalFrame,
                LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                    rightMargin = dp(10)
                }
            )

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            texts.addView(
                TextView(this).apply {
                    text = "${achievement.name} (${achievement.rarity.label})"
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 2
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            texts.addView(
                TextView(this).apply {
                    text = "Como obtenerlo: ${achievement.description}"
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 12f
                    setPadding(0, dp(4), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            texts.addView(
                TextView(this).apply {
                    text = "Fecha: ${achievement.obtainedDate}"
                    setTextColor(Color.parseColor(achievement.rarity.borderColorHex))
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, dp(5), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            row.addView(
                texts,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            rowViews[achievement.name] = row
            orderBadges[achievement.name] = orderBadge
            listContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        content.findViewById<Button>(R.id.btnCancelAchievementSelection).setOnClickListener {
            dialog.dismiss()
        }
        content.findViewById<Button>(R.id.btnApplyAchievementSelection).setOnClickListener {
            if (selectedNames.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un logro.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            draftProfile.achievements = selectedNames.take(MAX_FEATURED_ACHIEVEMENTS)
            renderProfile()
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = resources.displayMetrics.widthPixels - dp(24)
                setLayout(dp(390).coerceAtMost(maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        refreshSelectionState()
        dialog.show()
    }

    private fun showAchievementDetail(name: String) {
        val achievement = ProfileCustomizationCatalog.achievement(name)
            ?.let { AchievementTracker.achievementWithProgress(this, it) }
            ?: return
        val content = layoutInflater.inflate(R.layout.dialog_achievement_detail, null)
        val visualStyle = achievementVisualStyle(achievement.rarity)
        content.findViewById<View>(R.id.achievementDetailPanel).background =
            achievementDetailBackground(achievement.rarity)
        content.findViewById<View>(R.id.achievementGlow).alpha =
            if (achievement.rarity == AchievementRarity.GOLD) 1f else 0f
        content.findViewById<TextView>(R.id.achievementDetailTitle).text = achievement.name
        content.findViewById<TextView>(R.id.achievementDetailTitle)
            .setTextColor(Color.parseColor(visualStyle.titleHex))
        content.findViewById<TextView>(R.id.achievementDetailTitle).typeface =
            if (achievement.rarity == AchievementRarity.GOLD) {
                android.graphics.Typeface.SERIF
            } else {
                android.graphics.Typeface.DEFAULT_BOLD
            }
        content.findViewById<TextView>(R.id.achievementDetailDescription).text =
            achievement.description
        content.findViewById<TextView>(R.id.achievementDetailDescription)
            .setTextColor(Color.parseColor(visualStyle.textHex))
        content.findViewById<TextView>(R.id.achievementDetailDate).text =
            "Obtenido el ${achievement.obtainedDate}"
        content.findViewById<TextView>(R.id.achievementDetailDate)
            .setTextColor(Color.parseColor(visualStyle.dateHex))
        content.findViewById<View>(R.id.achievementMedalFrame).background =
            achievementMedalFrameBackground(achievement.rarity)
        content.findViewById<ImageView>(R.id.achievementMedal)
            .setImageResource(achievement.rarity.medalRes)

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        content.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val maxWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260))
                setLayout(dp(420).coerceAtMost(maxWidth), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    private fun applyAchievementBadgeStyle(view: TextView, rarity: AchievementRarity) {
        val visualStyle = achievementVisualStyle(rarity)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (rarity == AchievementRarity.GOLD) 14 else 12).toFloat()
            setColor(Color.parseColor(visualStyle.backgroundHex))
            setStroke(dp(if (rarity == AchievementRarity.GOLD) 3 else 2), Color.parseColor(visualStyle.borderHex))
        }
        view.setTextColor(Color.parseColor(visualStyle.textHex))
        view.typeface = if (rarity == AchievementRarity.GOLD) {
            android.graphics.Typeface.SERIF
        } else {
            android.graphics.Typeface.DEFAULT_BOLD
        }
        view.setPadding(dp(8), dp(7), dp(8), dp(7))
    }

    private fun achievementDetailBackground(rarity: AchievementRarity): GradientDrawable {
        val visualStyle = achievementVisualStyle(rarity)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor(visualStyle.backgroundHex))
            setStroke(dp(2), Color.parseColor(visualStyle.borderHex))
        }
    }

    private fun achievementMedalFrameBackground(rarity: AchievementRarity): GradientDrawable {
        val visualStyle = achievementVisualStyle(rarity)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC2A2318"))
            setStroke(dp(2), Color.parseColor(visualStyle.borderHex))
        }
    }

    private fun achievementVisualStyle(rarity: AchievementRarity): AchievementVisualStyle {
        return when (rarity) {
            AchievementRarity.BRONZE -> AchievementVisualStyle(
                backgroundHex = "#E6332017",
                borderHex = "#F0A35A",
                textHex = "#FFE2BC",
                titleHex = "#FFB56E",
                dateHex = "#EBC49A"
            )
            AchievementRarity.SILVER -> AchievementVisualStyle(
                backgroundHex = "#E6202830",
                borderHex = "#DCEAFF",
                textHex = "#F4F8FF",
                titleHex = "#E7F1FF",
                dateHex = "#C8D4E2"
            )
            AchievementRarity.GOLD -> AchievementVisualStyle(
                backgroundHex = "#F2140F05",
                borderHex = "#FFF2A3",
                textHex = "#FFF6CF",
                titleHex = "#FFF7B2",
                dateHex = "#FFE27A"
            )
        }
    }

    private fun alignAvatarToFocus(image: ImageView, verticalFocus: Float) {
        image.post {
            val drawable = image.drawable ?: return@post
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            if (drawableWidth <= 0f || drawableHeight <= 0f) return@post

            // Encuadre tipo retrato: llenar por ancho con un leve zoom y anclar cerca del borde
            // superior (saltando el aire sobre la cabeza) para mostrar cabeza y hombros, no el
            // cuerpo entero. Los valores son ajustables si algun rol queda muy alto/bajo.
            val scale = maxOf(
                image.width / drawableWidth,
                image.height / drawableHeight
            ) * 1.12f
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val horizontalOffset = (image.width - scaledWidth) / 2f
            val focusedY = scaledHeight * verticalFocus.coerceIn(0f, 1f)
            val verticalOffset = (image.height / 2f - focusedY)
                .coerceIn(image.height - scaledHeight, 0f)
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(horizontalOffset, verticalOffset)
            }
        }
    }

    private fun alignRoleThumbnailFromTop(image: ImageView) {
        image.scaleType = ImageView.ScaleType.MATRIX
        image.post {
            val drawable = image.drawable ?: return@post
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            val contentWidth = (image.width - image.paddingLeft - image.paddingRight).toFloat()
            val contentHeight = (image.height - image.paddingTop - image.paddingBottom).toFloat()
            if (
                drawableWidth <= 0f ||
                drawableHeight <= 0f ||
                contentWidth <= 0f ||
                contentHeight <= 0f
            ) {
                return@post
            }

            val fillScale = maxOf(
                contentWidth / drawableWidth,
                contentHeight / drawableHeight
            )
            val fitScale = minOf(
                contentWidth / drawableWidth,
                contentHeight / drawableHeight
            )
            // Encuadre superior y algo menos cerrado que CENTER_CROP: primero conserva rostro y
            // torso, y acepta un margen lateral pequeño antes que volver a cortar la cabeza.
            val scale = maxOf(fitScale, fillScale * ROLE_THUMBNAIL_ZOOM)
            val scaledWidth = drawableWidth * scale
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate((contentWidth - scaledWidth) / 2f, 0f)
            }
        }
    }

    private fun restoreDraft(savedInstanceState: Bundle?): ProfileDraft? {
        if (savedInstanceState == null) return null
        return ProfileDraft(
            name = savedInstanceState.getString(STATE_DRAFT_NAME, savedProfile.name).orEmpty(),
            publicId = savedInstanceState
                .getString(STATE_DRAFT_PUBLIC_ID, savedProfile.publicId)
                .orEmpty(),
            bio = savedInstanceState.getString(STATE_DRAFT_BIO, savedProfile.bio).orEmpty(),
            avatarKey = savedInstanceState
                .getString(STATE_DRAFT_AVATAR, savedProfile.avatarKey)
                .orEmpty(),
            bannerKey = savedInstanceState
                .getString(STATE_DRAFT_BANNER, savedProfile.bannerKey)
                .orEmpty(),
            favoriteRoleKey = savedInstanceState
                .getString(STATE_DRAFT_FAVORITE_ROLE, savedProfile.favoriteRoleKey)
                .orEmpty(),
            achievements = savedInstanceState
                .getStringArrayList(STATE_DRAFT_ACHIEVEMENTS)
                ?.toList()
                ?.ifEmpty { savedProfile.achievements }
                ?: savedProfile.achievements,
            emoteLoadout = EmoteLoadout.normalizeIds(
                savedInstanceState
                    .getStringArrayList(STATE_DRAFT_EMOTES)
                    ?.toList()
                    ?.ifEmpty { savedProfile.emoteLoadout }
                    ?: savedProfile.emoteLoadout
            )
        )
    }

    private fun copyProfile(source: ProfileDraft): ProfileDraft {
        return source.copy(
            achievements = source.achievements.toList(),
            emoteLoadout = source.emoteLoadout.toList()
        )
    }

    private fun updateInteractiveContentDescriptions(favoriteRole: String) {
        profileAvatar.contentDescription = if (isEditing) {
            "Cambiar foto de perfil"
        } else {
            "Ampliar foto de perfil"
        }
        profileBanner.contentDescription = if (isEditing) {
            "Cambiar banner del perfil"
        } else {
            "Banner del perfil"
        }
        favoriteRoleCard.contentDescription = if (isEditing) {
            "Elegir rol favorito"
        } else {
            "Ver informacion del rol $favoriteRole"
        }
    }

    private fun emoteOptionBackground(selected: Boolean, toneHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(if (selected) "#332719" else "#1F1711"))
            setStroke(
                dp(if (selected) 2 else 1),
                Color.parseColor(if (selected) toneHex else "#6B4F2A")
            )
        }
    }

    private fun achievementSelectionBackground(
        selected: Boolean,
        rarity: AchievementRarity
    ): GradientDrawable {
        val visualStyle = achievementVisualStyle(rarity)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(if (selected) "#332719" else "#CC2A2318"))
            setStroke(
                dp(if (selected) 2 else 1),
                Color.parseColor(if (selected) visualStyle.borderHex else "#6B4F2A")
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_OPEN_ACCOUNT = "open_account"
        const val PREFS_NAME = "TraidoresPrefs"
        const val PREF_NAME = "profile_name"
        const val PREF_BIO = "profile_bio"
        const val PREF_AVATAR = "profile_avatar"
        const val PREF_BANNER = "profile_banner"
        const val PREF_FAVORITE_ROLE = "profile_favorite_role"
        const val ROLE_THUMBNAIL_ZOOM = 0.90f
        const val PREF_ACHIEVEMENTS = "profile_achievements"

        const val DEFAULT_BIO = "No fui yo. Esta vez."
        const val DEFAULT_AVATAR_KEY = "aldeana"
        const val DEFAULT_BANNER_KEY = "pampa"
        const val DEFAULT_ROLE_KEY = "detective"
        const val ACHIEVEMENT_SEPARATOR = "|"

        const val MAX_NAME_LENGTH = 20
        const val MAX_BIO_LENGTH = 40
        const val MAX_FEATURED_ACHIEVEMENTS = 3
        const val EMPTY_BIO_PLACEHOLDER = "Agrega una frase breve para tu perfil."
        const val STATE_IS_EDITING = "profile_state_is_editing"
        const val STATE_DRAFT_NAME = "profile_state_draft_name"
        const val STATE_DRAFT_PUBLIC_ID = "profile_state_draft_public_id"
        const val STATE_DRAFT_BIO = "profile_state_draft_bio"
        const val STATE_DRAFT_AVATAR = "profile_state_draft_avatar"
        const val STATE_DRAFT_BANNER = "profile_state_draft_banner"
        const val STATE_DRAFT_FAVORITE_ROLE = "profile_state_draft_favorite_role"
        const val STATE_DRAFT_ACHIEVEMENTS = "profile_state_draft_achievements"
        const val STATE_DRAFT_EMOTES = "profile_state_draft_emotes"
    }
}
