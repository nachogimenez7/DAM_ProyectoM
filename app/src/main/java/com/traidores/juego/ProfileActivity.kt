package com.traidores.juego

import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import java.util.concurrent.TimeUnit

class ProfileActivity : BaseActivity() {

    private data class ProfileDraft(
        var name: String,
        var publicId: String,
        var bio: String,
        var avatarKey: String,
        var bannerKey: String,
        var favoriteRoleKey: String,
        var achievements: List<String>
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
    private lateinit var profileName: TextView
    private lateinit var profilePublicId: TextView
    private lateinit var profileBio: TextView
    private lateinit var favoriteRoleName: TextView
    private lateinit var editProfileButton: Button
    private lateinit var achievementViews: List<TextView>
    private lateinit var editIcons: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bindViews()
        savedProfile = loadProfile()
        draftProfile = savedProfile.copy(achievements = savedProfile.achievements.toList())
        renderProfile()
        setEditing(false)

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
        findViewById<LinearLayout>(R.id.favoriteRoleCard).setOnClickListener {
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
        findViewById<View>(R.id.editPublicId).setOnClickListener { showPublicIdEditor() }
        findViewById<View>(R.id.editBio).setOnClickListener { showBioEditor() }
        findViewById<View>(R.id.editFavoriteRole).setOnClickListener {
            showFavoriteRoleSelector()
        }
        findViewById<View>(R.id.editAchievements).setOnClickListener {
            showAchievementsSelector()
        }

        profileName.setOnClickListener { if (isEditing) showNameEditor() }
        profilePublicId.setOnClickListener { if (isEditing) showPublicIdEditor() }
        profileBio.setOnClickListener { if (isEditing) showBioEditor() }
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
        profileName = findViewById(R.id.profileName)
        profilePublicId = findViewById(R.id.profilePublicId)
        profileBio = findViewById(R.id.profileBio)
        favoriteRoleName = findViewById(R.id.favoriteRoleName)
        editProfileButton = findViewById(R.id.btnEditProfile)
        achievementViews = listOf(
            findViewById(R.id.achievementOne),
            findViewById(R.id.achievementTwo),
            findViewById(R.id.achievementThree)
        )
        editIcons = listOf(
            findViewById(R.id.editAvatar),
            findViewById(R.id.editBanner),
            findViewById(R.id.editName),
            findViewById(R.id.editPublicId),
            findViewById(R.id.editBio),
            findViewById(R.id.editFavoriteRole),
            findViewById(R.id.editAchievements)
        )
    }

    private fun loadProfile(): ProfileDraft {
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
            .ifEmpty { ProfileCustomizationCatalog.achievements.map { it.name } }

        return ProfileDraft(
            name = preferences.getString(PREF_NAME, fallbackName).orEmpty().ifBlank { fallbackName },
            publicId = preferences.getString(PREF_PUBLIC_ID, DEFAULT_PUBLIC_ID)
                .orEmpty()
                .ifBlank { DEFAULT_PUBLIC_ID },
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
            achievements = achievements
        )
    }

    private fun renderProfile() {
        profileName.text = draftProfile.name
        profilePublicId.text = "#${draftProfile.publicId}"
        profileBio.text = "\"${draftProfile.bio}\""

        val avatar = ProfileRoleCatalog.find(draftProfile.avatarKey).role
        setRoleImage(profileAvatar, avatar)
        alignAvatarToTop(profileAvatar)

        profileBanner.setBackgroundResource(
            ProfileCustomizationCatalog.banner(draftProfile.bannerKey).drawableRes
        )

        val favoriteRole = ProfileRoleCatalog.find(draftProfile.favoriteRoleKey).role
        favoriteRoleName.text = favoriteRole.name
        setRoleImage(favoriteRoleImage, favoriteRole)
        findViewById<LinearLayout>(R.id.favoriteRoleCard).contentDescription =
            "Ver informacion del rol ${favoriteRole.name}"

        achievementViews.forEachIndexed { index, view ->
            val achievementName = draftProfile.achievements.getOrNull(index)
            val achievement = achievementName?.let(ProfileCustomizationCatalog::achievement)
            view.text = achievement?.shortName ?: achievementName.orEmpty()
            view.tag = achievementName
            view.contentDescription = achievementName?.let { "Ver logro $it" }
            view.visibility = if (achievementName == null) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun setRoleImage(image: ImageView, role: Role) {
        val resId = resources.getIdentifier(role.imageResName, "drawable", packageName)
        image.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
    }

    private fun startEditing() {
        draftProfile = savedProfile.copy(achievements = savedProfile.achievements.toList())
        setEditing(true)
        renderProfile()
    }

    private fun setEditing(editing: Boolean) {
        isEditing = editing
        editIcons.forEach { it.visibility = if (editing) View.VISIBLE else View.GONE }
        editProfileButton.text = if (editing) "GUARDAR CAMBIOS" else "EDITAR PERFIL"
    }

    private fun saveChanges() {
        val publicIdChanged = draftProfile.publicId != savedProfile.publicId
        preferences.edit()
            .putString(PREF_NAME, draftProfile.name)
            .putString(OpcionesActivity.PREF_PLAYER_NAME, draftProfile.name)
            .putString(PREF_PUBLIC_ID, draftProfile.publicId)
            .putString(PREF_BIO, draftProfile.bio)
            .putString(PREF_AVATAR, draftProfile.avatarKey)
            .putString(PREF_BANNER, draftProfile.bannerKey)
            .putString(PREF_FAVORITE_ROLE, draftProfile.favoriteRoleKey)
            .putString(
                PREF_ACHIEVEMENTS,
                draftProfile.achievements.joinToString(ACHIEVEMENT_SEPARATOR)
            )
            .apply {
                if (publicIdChanged) {
                    putLong(PREF_PUBLIC_ID_CHANGED_AT, System.currentTimeMillis())
                }
            }
            .apply()

        savedProfile = draftProfile.copy(achievements = draftProfile.achievements.toList())
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
                draftProfile = savedProfile.copy(achievements = savedProfile.achievements.toList())
                setEditing(false)
                renderProfile()
            }
            .show()
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
                setLayout(dp(320), dp(320))
            }
            alignAvatarToTop(expandedAvatar)
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

    private fun showPublicIdEditor() {
        val remainingDays = publicIdCooldownDays()
        if (remainingDays > 0) {
            Toast.makeText(
                this,
                "Podras cambiar tu ID nuevamente en $remainingDays dias.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showTextEditor(
            title = "Editar ID publico",
            currentValue = draftProfile.publicId,
            maxLength = MAX_PUBLIC_ID_LENGTH,
            hint = "Entre 4 y 16 caracteres"
        ) { rawValue ->
            val value = rawValue.lowercase()
            when {
                value == draftProfile.publicId -> null
                !PUBLIC_ID_PATTERN.matches(value) ->
                    "Usa de 4 a 16 letras, numeros o guion bajo."
                else -> {
                    draftProfile.publicId = value
                    renderProfile()
                    null
                }
            }
        }
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
        val achievements = ProfileCustomizationCatalog.achievements
        val selected = achievements
            .map { it.name in draftProfile.achievements }
            .toBooleanArray()
        val labels = achievements.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Logros destacados")
            .setMultiChoiceItems(labels, selected) { _, index, checked ->
                selected[index] = checked
            }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                val chosen = achievements
                    .filterIndexed { index, _ -> selected[index] }
                    .map { it.name }
                if (chosen.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Selecciona al menos un logro.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    draftProfile.achievements = chosen.take(MAX_FEATURED_ACHIEVEMENTS)
                    renderProfile()
                }
            }
            .show()
    }

    private fun showAchievementDetail(name: String) {
        val achievement = ProfileCustomizationCatalog.achievement(name) ?: return
        AlertDialog.Builder(this)
            .setTitle(achievement.name)
            .setMessage(
                "${achievement.description}\n\n" +
                    "OBTENIDO EL ${achievement.obtainedDate}"
            )
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun publicIdCooldownDays(): Long {
        if (draftProfile.publicId != savedProfile.publicId) return 0
        val lastChange = preferences.getLong(PREF_PUBLIC_ID_CHANGED_AT, 0L)
        if (lastChange == 0L) return 0

        val availableAt = lastChange + PUBLIC_ID_COOLDOWN_MS
        val remaining = availableAt - System.currentTimeMillis()
        if (remaining <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays(remaining).coerceAtLeast(0) + 1
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val PREFS_NAME = "TraidoresPrefs"
        const val PREF_NAME = "profile_name"
        const val PREF_PUBLIC_ID = "profile_public_id"
        const val PREF_PUBLIC_ID_CHANGED_AT = "profile_public_id_changed_at"
        const val PREF_BIO = "profile_bio"
        const val PREF_AVATAR = "profile_avatar"
        const val PREF_BANNER = "profile_banner"
        const val PREF_FAVORITE_ROLE = "profile_favorite_role"
        const val PREF_ACHIEVEMENTS = "profile_achievements"

        const val DEFAULT_PUBLIC_ID = "trd-a7k4p2"
        const val DEFAULT_BIO = "No fui yo. Esta vez."
        const val DEFAULT_AVATAR_KEY = "aldeana"
        const val DEFAULT_BANNER_KEY = "pampa"
        const val DEFAULT_ROLE_KEY = "detective"
        const val ACHIEVEMENT_SEPARATOR = "|"
        const val REQUEST_AVATAR = 101
        const val REQUEST_BANNER = 102
        const val REQUEST_FAVORITE_ROLE = 103

        const val MAX_NAME_LENGTH = 20
        const val MAX_PUBLIC_ID_LENGTH = 16
        const val MAX_BIO_LENGTH = 40
        const val MAX_FEATURED_ACHIEVEMENTS = 3
        val PUBLIC_ID_PATTERN = Regex("^[a-z0-9_]{4,16}$")
        val PUBLIC_ID_COOLDOWN_MS = TimeUnit.DAYS.toMillis(30)
    }
}
