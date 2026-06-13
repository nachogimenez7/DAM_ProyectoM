package com.traidores.juego

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RolesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var selectedMapTitle: TextView
    private lateinit var selectedMapContext: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roles)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        selectedMapTitle = findViewById(R.id.selectedMapTitle)
        selectedMapContext = findViewById(R.id.selectedMapContext)
        recyclerView = findViewById(R.id.rolesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<LinearLayout>(R.id.mapMedieval).setOnClickListener {
            showMapRoles(RoleMap.MEDIEVAL)
        }
        findViewById<LinearLayout>(R.id.mapGreece).setOnClickListener {
            showMapRoles(RoleMap.GREECE)
        }
        findViewById<LinearLayout>(R.id.mapPampa).setOnClickListener {
            showMapRoles(RoleMap.PAMPA)
        }

        showMapRoles(RoleMap.MEDIEVAL)
    }

    private fun showMapRoles(map: RoleMap) {
        val mapInfo = RoleCatalog.mapInfo(map)
        selectedMapTitle.text = mapInfo.name
        selectedMapContext.text =
            "${mapInfo.description} Rol exclusivo: ${mapInfo.exclusiveRole}."
        val items = buildList {
            add(RoleListItem.SectionHeader("ROLES DEL MAPA"))
            addAll(RoleCatalog.rolesForMap(map).map { RoleListItem.RoleCard(it) })
        }
        recyclerView.adapter = RoleAdapter(this, items) { role ->
            RoleDetailDialog.show(this, role)
        }
    }
}
