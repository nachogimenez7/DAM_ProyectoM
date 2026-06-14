package com.traidores.juego

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class AyudaActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ayuda)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        renderRoleGuides(findViewById(R.id.roleGuidesContainer))
    }

    private fun renderRoleGuides(container: LinearLayout) {
        var expandedDetails: View? = null
        var expandedIndicator: TextView? = null

        RoleCatalog.guideKeys().forEach { roleKey ->
            val definition = RoleCatalog.definition(roleKey)
            val card = layoutInflater.inflate(R.layout.item_help_role, container, false)
            val name = card.findViewById<TextView>(R.id.helpRoleName)
            val meta = card.findViewById<TextView>(R.id.helpRoleMeta)
            val indicator = card.findViewById<TextView>(R.id.helpRoleIndicator)
            val details = card.findViewById<LinearLayout>(R.id.helpRoleDetails)
            val function = card.findViewById<TextView>(R.id.helpRoleFunction)
            val advice = card.findViewById<TextView>(R.id.helpRoleAdvice)

            name.text = RoleCatalog.guideName(roleKey).uppercase()
            name.setTextColor(teamColor(definition.team))
            meta.text = "${definition.team.uppercase()} - ${RoleCatalog.guideAvailability(roleKey)}"
            function.text = definition.function
            advice.text = RoleCatalog.advice(roleKey)
            card.contentDescription = "Consejos para ${RoleCatalog.guideName(roleKey)}"

            card.setOnClickListener {
                val shouldExpand = details.visibility != View.VISIBLE
                if (shouldExpand) {
                    expandedDetails?.visibility = View.GONE
                    expandedIndicator?.text = "+"
                    details.visibility = View.VISIBLE
                    indicator.text = "-"
                    expandedDetails = details
                    expandedIndicator = indicator
                } else {
                    details.visibility = View.GONE
                    indicator.text = "+"
                    expandedDetails = null
                    expandedIndicator = null
                }
                GameplayEffects.play(this, GameplayEffect.PANEL)
            }
            container.addView(card)
        }
    }

    private fun teamColor(team: String): Int {
        return Color.parseColor(
            when (team) {
                GameRules.TRAITOR_WINNER -> "#E1746E"
                GameRules.TOWN_WINNER -> "#8FCB91"
                else -> "#E0B85F"
            }
        )
    }
}
