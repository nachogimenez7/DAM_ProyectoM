package com.traidores.juego

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
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
        val mapSelector: LinearLayout = findViewById(R.id.mapSelector)
        val mapButtons = listOf(
            RoleMap.PAMPA to findViewById<Button>(R.id.btnMapPampa),
            RoleMap.GREECE to findViewById<Button>(R.id.btnMapGreece),
            RoleMap.MEDIEVAL to findViewById<Button>(R.id.btnMapMedieval)
        )
        val selectedEntry = ProfileRoleCatalog.find(selectedKey)
        val normalizedSelectedKey = selectedEntry.key
        var selectedMap = selectedEntry.map

        title.text = mode.title
        description.text = mode.description
        recycler.layoutManager = LinearLayoutManager(this)

        fun renderMapButtons() {
            mapButtons.forEach { (map, button) ->
                val selected = map == selectedMap
                button.setBackgroundResource(
                    if (selected) R.drawable.bg_profile_selection_selected else R.drawable.bg_btn_dark
                )
                button.contentDescription =
                    "${button.text}. ${if (selected) "Mapa seleccionado" else "Elegir mapa"}"
            }
        }

        fun renderOptions() {
            recycler.adapter = ProfileSelectionAdapter(
                options = optionsFor(mode, selectedMap),
                display = mode.display,
                selectedKey = normalizedSelectedKey
            ) { key ->
                setResult(
                    Activity.RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_MODE, mode.value)
                        .putExtra(EXTRA_SELECTED_KEY, key)
                )
                finish()
            }
        }

        mapSelector.visibility = if (mode.display == ProfileSelectionDisplay.ROLE) {
            View.VISIBLE
        } else {
            View.GONE
        }
        mapButtons.forEach { (map, button) ->
            button.setOnClickListener {
                selectedMap = map
                renderMapButtons()
                renderOptions()
            }
        }
        renderMapButtons()
        renderOptions()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun optionsFor(
        mode: SelectionMode,
        selectedMap: RoleMap
    ): List<ProfileSelectionOption> {
        return when (mode.display) {
            ProfileSelectionDisplay.ROLE -> ProfileRoleCatalog.entriesForMap(selectedMap)
                .map { entry ->
                    ProfileSelectionOption(
                        key = entry.key,
                        title = entry.role.name,
                        subtitle = entry.role.mapName,
                        drawableRes = resources.getIdentifier(
                            entry.role.imageResName,
                            "drawable",
                            packageName
                        ).takeIf { it != 0 } ?: R.drawable.placeholder_local
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
