package com.traidores.juego

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

object RoleDetailDialog {

    fun show(context: Context, role: Role) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_role_detail)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val roleImage: ImageView = dialog.findViewById(R.id.detailRoleImage)
        val roleName: TextView = dialog.findViewById(R.id.detailRoleName)
        val roleTeam: TextView = dialog.findViewById(R.id.detailRoleTeam)
        val roleFantasy: TextView = dialog.findViewById(R.id.detailRoleFantasy)
        val roleGameplay: TextView = dialog.findViewById(R.id.detailRoleGameplay)
        val closeButton: ImageButton = dialog.findViewById(R.id.btnCloseRoleDetail)

        val resId = context.resources.getIdentifier(
            role.imageResName,
            "drawable",
            context.packageName
        )
        roleImage.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_gallery)
        roleName.text = role.name.uppercase()
        roleTeam.text = role.team.uppercase()
        roleFantasy.text = role.story
        roleGameplay.text = role.function
        roleTeam.setTextColor(teamColor(role.team))

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun teamColor(team: String): Int {
        return when (team.lowercase()) {
            "pueblo" -> Color.parseColor("#4a7fb5")
            "asesino" -> Color.parseColor("#a83232")
            "neutral" -> Color.parseColor("#d7dee8")
            "rol de mapa" -> Color.parseColor("#5a8a3c")
            else -> Color.parseColor("#c4b69c")
        }
    }
}
