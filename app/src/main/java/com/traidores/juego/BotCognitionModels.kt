package com.traidores.juego

internal const val HUMAN_NIGHT_PRESSURE_BONUS = 25

internal data class RoleClaim(
    val roleKey: String,
    val label: String
)

internal data class PublicStatement(
    val type: StatementType,
    val target: String? = null,
    val reason: String? = null
)

internal data class VotePlanSnapshot(
    val target: String,
    val reason: String,
    val confidence: Int,
    val beats: Int
)

internal data class SocialRead(
    val defended: String? = null,
    val pressured: String? = null,
    val ignoredBy: String? = null,
    val failedPush: String? = null,
    val heated: Boolean = false
)

internal data class ClaimContradiction(
    val first: ClaimRecord,
    val latest: ClaimRecord
)

internal data class BotMemory(
    val unansweredTarget: String? = null,
    val lastPressuredTarget: String? = null,
    val pendingHumanQuestion: PendingHumanQuestion? = null,
    val table: Map<String, PlayerConversationMemory> = emptyMap(),
    val recentLines: Set<String> = emptySet()
)

internal data class PlayerConversationMemory(
    val roleClaim: RoleClaim? = null,
    val latestStatement: PublicStatement? = null,
    val accusedTargets: Set<String> = emptySet(),
    val defendedTargets: Set<String> = emptySet(),
    val accusedBy: Set<String> = emptySet(),
    val defendedBy: Set<String> = emptySet(),
    val pendingQuestionFrom: String? = null
)

internal data class PendingHumanQuestion(
    val speaker: String,
    val message: String
)

internal data class VotePlan(
    val target: String,
    val reason: String,
    val confidence: Int,
    val beats: Int = 1
)

internal data class RelationshipRead(
    val player: GamePlayer,
    val level: TrustLevel,
    val score: Int,
    val reason: String
)

internal data class RoundObjective(
    val type: RoundObjectiveType,
    val target: String? = null,
    val reason: String = "",
    val confidence: Int = 0
)

internal enum class BotAgenda {
    ASK_ROLES,
    CALM_TABLE,
    PUSH_VOTE,
    DEFEND_WEAK,
    FOLLOW_THREAD,
    DEFLECT_PRESSURE
}

internal enum class TrustLevel {
    CONFIA,
    NEUTRAL,
    DUDA,
    SOSPECHA,
    PRESIONA
}

internal enum class RoundObjectiveType {
    ASK_PLAYER,
    DEFEND_PLAYER,
    PUSH_VOTE,
    CALM_TABLE,
    FOLLOW_CONTRADICTION,
    DEFLECT_PRESSURE
}

internal enum class BotPersonality {
    TRANQUI,
    PICANTE,
    JODON,
    DESCONFIADO,
    IMPULSIVO,
    ANALITICO
}

internal enum class BotMood {
    CALM,
    AMUSED,
    ANNOYED,
    DEFENSIVE,
    SUSPICIOUS
}

internal enum class BotSpeechIntent {
    ASK,
    FOLLOW_UP,
    ACCUSE,
    DEFEND,
    TEASE,
    CALM_DOWN,
    ADMIT_DOUBT
}

internal enum class BotConversationRole {
    OPENER,
    FOLLOWER,
    SKEPTIC,
    CALMER,
    CLOSER
}

internal enum class HumanQuestionKind {
    ROLE_HELP,
    ASK_ROLE,
    VOTE_HELP,
    ACTION_HELP,
    SUSPECT_HELP,
    WHY_VOTE,
    WHY_ACCUSE,
    OPINION,
    BELIEF
}

internal enum class HumanSocialSignal {
    PRAISE,
    INSULT
}

internal enum class HumanMessageIntent {
    CASUAL,
    PRAISE,
    INSULT,
    ROLE_QUESTION,
    ACTION_HELP,
    VOTE_HELP,
    SUSPECT_HELP,
    ROLE_CLAIM,
    ACTION_CLAIM,
    REFUSE_ROLE,
    ACCUSE,
    DEFEND,
    DOUBT,
    ANSWER_PENDING,
    SECRET_LEAK,
    OFF_TOPIC,
    OTHER
}

internal val accusationWords = listOf(
    "sospe",
    "raro",
    "rara",
    "miente",
    "menti",
    "acuso",
    "voto",
    "culpa",
    "callado",
    "silencio",
    "cambio de tema",
    "defiende",
    "nervioso"
)

internal val defenseWords = listOf("confio", "inocente", "limpio", "defiendo", "creo en")

internal val actionStatementTypes = setOf(
    StatementType.PROTECTED,
    StatementType.INVESTIGATED
)

internal val casualWords = setOf("hola", "buenas", "epa", "ey", "eu", "holaa", "holaaa")

internal val casualPhrases = setOf("que onda", "q onda", "todo bien", "toy", "estoy")

internal val secretWords = listOf(
    "asesino",
    "asesina",
    "traidor",
    "traidores",
    "policia",
    "comisario",
    "detective",
    "medico",
    "aldeano",
    "pueblo",
    "neutral",
    "mercenario",
    "espia",
    "bufon",
    "desertor"
)

internal val roleAliases = linkedMapOf(
    RoleCatalog.MEDICO to listOf("medico", "medica", "doctor", "doctora", "doc"),
    RoleCatalog.POLICIA to listOf("detective", "comisario", "policia", "inspector", "poli"),
    RoleCatalog.ALCALDE to listOf("alcalde"),
    RoleCatalog.PAYADOR to listOf("payador"),
    RoleCatalog.ORACULO to listOf("oraculo"),
    RoleCatalog.ALDEANO to listOf("aldeano", "pueblo"),
    RoleCatalog.ASESINO to listOf("asesino", "asesina", "traidor", "traidora"),
    RoleCatalog.MERCENARIO to listOf("mercenario", "mercenaria"),
    RoleCatalog.ESPIA to listOf("espia"),
    RoleCatalog.BUFON to listOf("bufon"),
    RoleCatalog.DESERTOR to listOf("desertor", "desertora")
)
