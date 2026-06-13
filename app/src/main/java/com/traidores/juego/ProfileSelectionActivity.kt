package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProfileSelectionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        val mode = SelectionMode.from(intent.getStringExtra(EXTRA_MODE))
        val selectedKey = intent.getStringExtra(EXTRA_SELECTED_KEY).orEmpty()
        val title: TextView = findViewById(R.id.selectionTitle)
        val description: TextView = findViewById(R.id.selectionDescription)
        val recycler: RecyclerView = findViewById(R.id.selectionRecycler)

        title.text = mode.title
        description.text = mode.description
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ProfileSelectionAdapter(
            options = optionsFor(mode),
            display = mode.display,
            selectedKey = selectedKey
        ) { key ->
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_MODE, mode.value)
                    .putExtra(EXTRA_SELECTED_KEY, key)
            )
            finish()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun optionsFor(mode: SelectionMode): List<ProfileSelectionOption> {
        return when (mode.display) {
            ProfileSelectionDisplay.ROLE -> ProfileRoleCatalog.entries.map { entry ->
                ProfileSelectionOption(
                    key = entry.key,
                    title = entry.role.name,
                    subtitle = entry.role.team,
                    drawableRes = resources.getIdentifier(
                        entry.role.imageResName,
                        "drawable",
                        packageName
                    ).takeIf { it != 0 } ?: android.R.drawable.ic_menu_gallery
                )
            }
            ProfileSelectionDisplay.BANNER -> ProfileCustomizationCatalog.banners.map { banner ->
                ProfileSelectionOption(
                    key = banner.key,
                    title = banner.label,
                    subtitle = "BANNER DE MAPA",
                    drawableRes = banner.drawableRes
                )
            }
        }
    }

    private enum class SelectionMode(
        val value: String,
        val title: String,
        val description: String,
        val display: ProfileSelectionDisplay
    ) {
        AVATAR(
            MODE_AVATAR,
            "FOTO DE PERFIL",
            "Elige el personaje que representara tu perfil.",
            ProfileSelectionDisplay.ROLE
        ),
        BANNER(
            MODE_BANNER,
            "BANNER DEL PERFIL",
            "Elige el paisaje que aparecera detras de tu foto.",
            ProfileSelectionDisplay.BANNER
        ),
        FAVORITE_ROLE(
            MODE_FAVORITE_ROLE,
            "ROL FAVORITO",
            "Elige el rol que quieres destacar en tu perfil.",
            ProfileSelectionDisplay.ROLE
        );

        companion object {
            fun from(value: String?): SelectionMode {
                return entries.firstOrNull { it.value == value } ?: AVATAR
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "profile_selection_mode"
        const val EXTRA_SELECTED_KEY = "profile_selection_key"
        const val MODE_AVATAR = "avatar"
        const val MODE_BANNER = "banner"
        const val MODE_FAVORITE_ROLE = "favorite_role"

        fun intent(context: Context, mode: String, selectedKey: String): Intent {
            return Intent(context, ProfileSelectionActivity::class.java)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_SELECTED_KEY, selectedKey)
        }
    }
}
