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
    @param:DrawableRes val drawableRes: Int
)

enum class ProfileSelectionDisplay {
    ROLE,
    AVATAR,
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
            ProfileSelectionDisplay.ROLE, ProfileSelectionDisplay.AVATAR -> R.layout.item_profile_role_selection
            ProfileSelectionDisplay.BANNER -> R.layout.item_profile_banner_selection
        }
        return OptionViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        val option = options[position]
        val selected = option.key == selectedKey

        holder.image.scaleType = if (display == ProfileSelectionDisplay.AVATAR) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        holder.image.layoutParams = holder.image.layoutParams.apply {
            val density = holder.image.resources.displayMetrics.density
            width = (82 * density).toInt()
            height = ((if (display == ProfileSelectionDisplay.AVATAR) 82 else 104) * density).toInt()
        }
        holder.image.setImageResource(option.drawableRes)
        if (display == ProfileSelectionDisplay.AVATAR) {
            holder.image.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#211B12"))
            }
            holder.image.clipToOutline = true
            ProfilePortraitRenderer.alignArtwork(holder.image, 0.5f)
        }
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
