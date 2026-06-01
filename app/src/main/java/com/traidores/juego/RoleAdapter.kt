package com.traidores.juego

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RoleAdapter(
    private val context: Context,
    private val items: List<RoleListItem>,
    private val onRoleClick: (Role) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_MAP = 0
        const val TYPE_SECTION = 1
        const val TYPE_ROLE = 2
    }

    class MapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mapImage: ImageView = view.findViewById(R.id.mapImage)
        val mapName: TextView = view.findViewById(R.id.mapName)
        val mapEra: TextView = view.findViewById(R.id.mapEra)
        val mapDescription: TextView = view.findViewById(R.id.mapDescription)
        val mapRole: TextView = view.findViewById(R.id.mapRole)
    }

    class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionTitle: TextView = view.findViewById(R.id.sectionTitle)
    }

    class RoleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roleImage: ImageView = view.findViewById(R.id.roleImage)
        val roleName: TextView = view.findViewById(R.id.roleName)
        val roleMap: TextView = view.findViewById(R.id.roleMap)
        val roleBadge: TextView = view.findViewById(R.id.roleBadge)
        val roleStory: TextView = view.findViewById(R.id.roleStory)
        val roleFunction: TextView = view.findViewById(R.id.roleFunction)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is RoleListItem.MapCard -> TYPE_MAP
            is RoleListItem.SectionHeader -> TYPE_SECTION
            is RoleListItem.RoleCard -> TYPE_ROLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            TYPE_MAP -> MapViewHolder(inflater.inflate(R.layout.item_map, parent, false))
            TYPE_SECTION -> SectionViewHolder(inflater.inflate(R.layout.item_section_header, parent, false))
            else -> RoleViewHolder(inflater.inflate(R.layout.item_role, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RoleListItem.MapCard -> bindMap(holder as MapViewHolder, item.map)
            is RoleListItem.SectionHeader -> (holder as SectionViewHolder).sectionTitle.text = item.title
            is RoleListItem.RoleCard -> bindRole(holder as RoleViewHolder, item.role)
        }
    }

    private fun bindMap(holder: MapViewHolder, map: MapInfo) {
        holder.mapName.text = map.name
        holder.mapEra.text = map.era.uppercase()
        holder.mapDescription.text = map.description
        holder.mapRole.text = "ROL EXCLUSIVO: ${map.exclusiveRole.uppercase()}"

        val resId = context.resources.getIdentifier(map.imageResName, "drawable", context.packageName)
        holder.mapImage.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
    }

    private fun bindRole(holder: RoleViewHolder, role: Role) {
        holder.roleName.text = role.name
        holder.roleMap.text = role.mapName
        holder.roleStory.text = role.story
        holder.roleFunction.text = role.function
        holder.roleBadge.text = role.team.uppercase()

        val badgeColor = when (role.team.lowercase()) {
            "pueblo" -> Color.parseColor("#4a7fb5")
            "asesino" -> Color.parseColor("#a83232")
            "neutral" -> Color.parseColor("#d4a24e")
            "rol de mapa" -> Color.parseColor("#5a8a3c")
            else -> Color.parseColor("#c4b69c")
        }
        holder.roleBadge.setTextColor(badgeColor)

        val resId = context.resources.getIdentifier(role.imageResName, "drawable", context.packageName)
        holder.roleImage.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
        holder.itemView.setOnClickListener { onRoleClick(role) }
    }

    override fun getItemCount(): Int = items.size
}
