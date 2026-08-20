package com.traidores.juego

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AyudaActivity : BaseActivity() {

    private data class HelpSection(
        val title: TextView,
        val body: View,
        val label: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ayuda)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnOpenTutorial).setOnClickListener {
            TutorialDialog.show(this, markAsSeen = false)
        }
        renderRoleGuides(findViewById(R.id.roleGuidesContainer))
        setupCompactSections()
    }

    private fun setupCompactSections() {
        val sections = listOf(
            section(R.id.helpTitleAbout, R.id.helpBodyAbout, "DE QUÉ TRATA"),
            section(R.id.helpTitleHow, R.id.helpBodyHow, "CÓMO SE JUEGA"),
            section(R.id.helpTitleModes, R.id.helpBodyModes, "LOCAL U ONLINE"),
            section(R.id.helpTitleControls, R.id.helpBodyControls, "CONTROLES ESENCIALES"),
            section(R.id.helpTitleWin, R.id.helpBodyWin, "CÓMO SE GANA"),
            section(R.id.helpTitleDeception, R.id.helpBodyDeception, "EL ENGAÑO ES PARTE DEL JUEGO"),
            section(R.id.helpTitleCommunity, R.id.helpBodyCommunity, "NORMAS Y SEGURIDAD ONLINE"),
            section(R.id.helpTitleRules, R.id.helpBodyRules, "REGLAS IMPORTANTES"),
            section(R.id.helpTitleTips, R.id.helpBodyTips, "CONSEJOS GENERALES"),
            section(R.id.helpTitleRoles, R.id.helpBodyRoles, "CONSEJOS POR ROL")
        )
        var expanded: HelpSection? = null

        fun render(section: HelpSection, open: Boolean) {
            section.body.visibility = if (open) View.VISIBLE else View.GONE
            section.title.text = "${section.label}  ${if (open) "−" else "+"}"
            section.title.contentDescription =
                "${section.label}. ${if (open) "Contraer sección" else "Expandir sección"}"
            section.title.isSelected = open
        }

        sections.forEach { section ->
            render(section, open = false)
            section.title.setOnClickListener {
                val shouldOpen = section.body.visibility != View.VISIBLE
                expanded?.takeIf { it !== section }?.let { render(it, open = false) }
                render(section, shouldOpen)
                expanded = section.takeIf { shouldOpen }
                GameplayEffects.play(this, GameplayEffect.PANEL)
            }
        }
    }

    private fun section(titleId: Int, bodyId: Int, label: String): HelpSection {
        return HelpSection(
            title = findViewById(titleId),
            body = findViewById(bodyId),
            label = label
        )
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
