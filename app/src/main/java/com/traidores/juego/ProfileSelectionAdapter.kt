package com.traidores.juego

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView

data class ProfileSelectionOption(
    val key: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val drawableRes: Int
)

enum class ProfileSelectionDisplay {
    ROLE,
    BANNER
}

class ProfileSelectionAdapter(
    private val options: List<ProfileSelectionOption>,
    private val display: ProfileSelectionDisplay,
    private val selectedKey: String,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<ProfileSelectionAdapter.OptionViewHolder>() {

    class OptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.selectionImage)
        val title: TextView = view.findViewById(R.id.selectionOptionTitle)
        val subtitle: TextView = view.findViewById(R.id.selectionOptionSubtitle)
        val state: TextView = view.findViewById(R.id.selectionOptionState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val layout = when (display) {
            ProfileSelectionDisplay.ROLE -> R.layout.item_profile_role_selection
            ProfileSelectionDisplay.BANNER -> R.layout.item_profile_banner_selection
        }
        return OptionViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        val option = options[position]
        val selected = option.key == selectedKey

        holder.image.setImageResource(option.drawableRes)
        holder.title.text = option.title
        holder.subtitle.text = option.subtitle.uppercase()
        holder.state.text = if (selected) "SELECCIONADO" else "ELEGIR"
        holder.itemView.setBackgroundResource(
            if (selected) R.drawable.bg_profile_selection_selected else R.drawable.bg_btn_dark
        )
        holder.itemView.contentDescription =
            "${option.title}. ${if (selected) "Seleccionado" else "Tocar para elegir"}"
        holder.itemView.setOnClickListener { onSelect(option.key) }
    }

    override fun getItemCount(): Int = options.size
}
