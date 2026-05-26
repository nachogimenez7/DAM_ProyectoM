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
    private val roles: List<Role>
) : RecyclerView.Adapter<RoleAdapter.RoleViewHolder>() {

    class RoleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roleImage: ImageView = view.findViewById(R.id.roleImage)
        val roleName: TextView = view.findViewById(R.id.roleName)
        val roleBadge: TextView = view.findViewById(R.id.roleBadge)
        val roleDesc: TextView = view.findViewById(R.id.roleDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoleViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_role, parent, false)
        return RoleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        val role = roles[position]
        holder.roleName.text = role.name
        holder.roleDesc.text = role.description
        holder.roleBadge.text = role.team.uppercase()

        // Set badge color based on team
        val badgeColor = when (role.team.lowercase()) {
            "pueblo" -> Color.parseColor("#4a7fb5")     // Blue
            "asesino" -> Color.parseColor("#a83232")    // Red
            "neutral" -> Color.parseColor("#d4a24e")    // Gold / Orange
            "rol de mapa" -> Color.parseColor("#5a8a3c") // Green
            else -> Color.parseColor("#c4b69c")         // Default secondary text color
        }
        holder.roleBadge.setTextColor(badgeColor)

        // Load image dynamically from drawables
        val resId = context.resources.getIdentifier(role.imageResName, "drawable", context.packageName)
        if (resId != 0) {
            holder.roleImage.setImageResource(resId)
        } else {
            // Fallback placeholder image or icon
            holder.roleImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun getItemCount(): Int = roles.size
}
