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
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.google.firebase.firestore.FirebaseFirestore

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
        AchievementTracker.ensureProfileOpened(this)
        val fallbackName = preferences
            .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
            .orEmpty()
            .ifBlank { "Jugador" }
        val achievements = preferences
            .getString(PREF_ACHIEVEMENTS, null)
            ?.split(ACHIEVEMENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.take(MAX_FEATURED_ACHIEVEMENTS)
            .orEmpty()
            .let(::validFeaturedAchievements)

        return ProfileDraft(
            name = preferences.getString(PREF_NAME, fallbackName).orEmpty().ifBlank { fallbackName },
            publicId = PlayerPublicIdentity.currentPublicId(this),
            bio = preferences.getString(PREF_BIO, DEFAULT_BIO).orEmpty(),
            avatarKey = preferences.getString(PREF_AVATAR, DEFAULT_AVATAR_KEY)
                .orEmpty()
                .ifBlank { DEFAULT_AVATAR_KEY },
            bannerKey = ProfileCustomizationCatalog.normalizeBannerKey(
                preferences.getString(PREF_BANNER, DEFAULT_BANNER_KEY)
                    .orEmpty()
                    .ifBlank { DEFAULT_BANNER_KEY }
            ),
            favoriteRoleKey = preferences.getString(PREF_FAVORITE_ROLE, DEFAULT_ROLE_KEY)
                .orEmpty()
                .ifBlank { DEFAULT_ROLE_KEY },
            achievements = achievements,
            emoteLoadout = EmoteLoadout.selectedIds(this)
        )
    }

    private fun renderProfile() {
        profileName.text = draftProfile.name
        profilePublicId.text = draftProfile.publicId.takeIf { it.isNotBlank() }?.let { "#$it" }
            ?: "#SIN ID"
        val hasBio = draftProfile.bio.isNotBlank()
        profileBio.text = if (hasBio) {
            "\"${draftProfile.bio}\""
        } else {
            EMPTY_BIO_PLACEHOLDER
        }
        profileBio.setTextColor(getColor(R.color.text_primary))

        val avatar = ProfileRoleCatalog.find(draftProfile.avatarKey).role
        setRoleImage(profileAvatar, avatar)
        alignAvatarToTop(profileAvatar)

        profileBanner.setBackgroundResource(
            ProfileCustomizationCatalog.banner(draftProfile.bannerKey).drawableRes
        )

        val favoriteRole = ProfileRoleCatalog.find(draftProfile.favoriteRoleKey).role
        favoriteRoleName.text = favoriteRole.name
        setRoleImage(favoriteRoleImage, favoriteRole)
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
    }

    private fun ensureNumericPublicId() {
        if (draftProfile.publicId.isNotBlank()) return
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
        editIcons.forEach { it.visibility = if (editing) View.VISIBLE else View.GONE }
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
        setEditing(false)
        Toast.makeText(this, "Perfil actualizado.", Toast.LENGTH_SHORT).show()
    }

    private fun handleBack() {
        if (!isEditing) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Descartar cambios")
            .setMessage("Los cambios del perfil todavia no fueron guardados.")
            .setNegativeButton("Seguir editando", null)
            .setPositiveButton("Descartar") { _, _ ->
                draftProfile = copyProfile(savedProfile)
                setEditing(false)
                renderProfile()
                finish()
            }
            .show()
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
        setRoleImage(expandedAvatar, ProfileRoleCatalog.find(draftProfile.avatarKey).role)
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
            expandedAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        dialog.show()
    }

    private fun showAvatarSelector() {
        startActivityForResult(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_AVATAR,
                draftProfile.avatarKey
            ),
            REQUEST_AVATAR
        )
    }

    private fun showBannerSelector() {
        startActivityForResult(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_BANNER,
                draftProfile.bannerKey
            ),
            REQUEST_BANNER
        )
    }

    private fun showNameEditor() {
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

    private fun showFixedPublicIdMessage() {
        Toast.makeText(
            this,
            "Tu ID publico es fijo y se usa para agregarte como amigo.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBioEditor() {
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
        val input = EditText(this).apply {
            setText(currentValue)
            setSelection(text.length)
            this.hint = hint
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setSingleLine()
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = onAccept(input.text.toString().trim())
                if (error == null) {
                    dialog.dismiss()
                } else {
                    input.error = error
                }
            }
        }
        dialog.show()
    }

    private fun showEmoteSelector() {
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
        startActivityForResult(
            ProfileSelectionActivity.intent(
                this,
                ProfileSelectionActivity.MODE_FAVORITE_ROLE,
                draftProfile.favoriteRoleKey
            ),
            REQUEST_FAVORITE_ROLE
        )
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val selectedKey = data
            ?.getStringExtra(ProfileSelectionActivity.EXTRA_SELECTED_KEY)
            .orEmpty()
        if (selectedKey.isBlank()) return

        when (requestCode) {
            REQUEST_AVATAR -> draftProfile.avatarKey = selectedKey
            REQUEST_BANNER -> draftProfile.bannerKey = selectedKey
            REQUEST_FAVORITE_ROLE -> draftProfile.favoriteRoleKey = selectedKey
            else -> return
        }
        renderProfile()
    }

    private fun showAchievementsSelector() {
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

    private fun alignAvatarToTop(image: ImageView) {
        image.post {
            val drawable = image.drawable ?: return@post
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            if (drawableWidth <= 0f || drawableHeight <= 0f) return@post

            val scale = maxOf(
                image.width / drawableWidth,
                image.height / drawableHeight
            )
            val horizontalOffset = (image.width - drawableWidth * scale) / 2f
            image.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(horizontalOffset, -dp(8).toFloat())
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

    private companion object {
        const val PREFS_NAME = "TraidoresPrefs"
        const val PREF_NAME = "profile_name"
        const val PREF_BIO = "profile_bio"
        const val PREF_AVATAR = "profile_avatar"
        const val PREF_BANNER = "profile_banner"
        const val PREF_FAVORITE_ROLE = "profile_favorite_role"
        const val PREF_ACHIEVEMENTS = "profile_achievements"

        const val DEFAULT_BIO = "No fui yo. Esta vez."
        const val DEFAULT_AVATAR_KEY = "aldeana"
        const val DEFAULT_BANNER_KEY = "pampa"
        const val DEFAULT_ROLE_KEY = "detective"
        const val ACHIEVEMENT_SEPARATOR = "|"
        const val REQUEST_AVATAR = 101
        const val REQUEST_BANNER = 102
        const val REQUEST_FAVORITE_ROLE = 103

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
