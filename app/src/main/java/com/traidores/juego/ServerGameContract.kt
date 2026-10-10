package com.traidores.juego

import java.io.Serializable
import java.util.UUID

/** V3 is a distinct protocol. Unknown phases never fall back to the local engine. */
internal enum class ServerGamePhase(val title: String) {
    REPARTO("Tu rol"), NOCHE("Noche"), AMANECER("Amanecer"), DIA_DEBATE("Debate"),
    CONTRAPUNTO("Contrapunto"), VOTACION("Votación"), DESEMPATE_VOTACION("Segunda votación"),
    RECUENTO_VOTOS("Recuento"), ALCALDE_DESEMPATE("Decisión del Alcalde"), RESULTADO("Resultado del día"),
    DESERTOR_RECONSIDERACION("Revisión de bando"), FINALIZADA("Partida terminada"), LOBBY("Sala")
}

internal data class ServerGamePlayer(
    val uid: String, val order: Int, val name: String, val publicId: String,
    val alive: Boolean, val muted: Boolean, val deathCause: String, val publicRoleKey: String?
) : Serializable
internal data class ServerGameEvent(val seq: Long, val code: String, val round: Int, val players: List<String>, val text: String) : Serializable
internal data class ServerGamePublic(
    val matchId: String, val phase: ServerGamePhase, val phaseIndex: Int, val revision: Long,
    val round: Int, val deadlineMs: Long?, val announcement: String, val winner: String?,
    val players: List<ServerGamePlayer>, val events: List<ServerGameEvent>, val mayorUid: String?,
    val tieCandidates: List<String>, val counterpointPlayers: List<String>, val oracleGuestUid: String?,
    val deserterTeam: String?, val specialWinners: List<String>,
    val voteTotals: Map<Int, Int> = emptyMap(), val dayEliminationUid: String? = null,
    val voteRound: Int = 0, val mayorCorruption: Boolean = false,
    val specialWinnerKeys: Set<String> = emptySet(),
    /** Who voted for whom (voter order to target order), only after the vote closes and if the room shows votes. */
    val ballots: List<Pair<Int, Int>> = emptyList(),
    val counterpointPointedUid: String? = null,
    val readyToVote: ServerReadyToVote? = null
) : Serializable
/** Usual table's "VOTAR ANTES" aggregate: never identifies who marked. */
internal data class ServerReadyToVote(val ready: Int, val total: Int, val fromEpochMs: Long) : Serializable
internal data class ServerGameConfirmedAction(val action: String, val targetUid: String?, val team: String?) : Serializable
internal data class ServerGamePrivate(
    val matchId: String, val phaseIndex: Int, val revision: Long, val visibleRoleKeys: Map<Int, String>,
    val confirmed: List<ServerGameConfirmedAction>, val blockedTargets: Set<String>,
    val deserterTeam: String?, val deserterUsed: Boolean, val oracleUsed: Boolean, val payadorUsed: Boolean,
    val investigations: List<Pair<String, Boolean>>
) : Serializable
internal data class ServerGamePermissions(
    val matchId: String, val phaseIndex: Int, val member: Boolean,
    val publicChat: Boolean, val traitorChat: Boolean, val deadChat: Boolean, val reactions: Boolean = false
) : Serializable
internal data class ServerGameSnapshot(
    val publicState: ServerGamePublic, val privateState: ServerGamePrivate, val permissions: ServerGamePermissions,
    val ownUid: String
) {
    val human: ServerGamePlayer get() = publicState.players.single { it.uid == ownUid }
    val ownRoleKey: String get() = privateState.visibleRoleKeys.getValue(human.order)
    fun roleKey(player: ServerGamePlayer): String? = player.publicRoleKey ?: privateState.visibleRoleKeys[player.order]
    fun won(player: ServerGamePlayer): Boolean {
        if (publicState.winner !in setOf("Pueblo", "Traidores") || player.deathCause == "ABANDONO") return false
        if (player.name in publicState.specialWinners) return true
        return when (val role = roleKey(player)) {
            "desertor" -> player.alive && (publicState.deserterTeam ?: privateState.deserterTeam) == publicState.winner
            "bufon", null -> false
            else -> if (publicState.winner == "Traidores") role in setOf("asesino", "mercenario", "espia")
                else role !in setOf("asesino", "mercenario", "espia")
        }
    }
}

internal data class ServerGameCommand(
    val roomId: String, val matchId: String, val phaseIndex: Int, val requestId: String,
    val action: String, val targetUid: String? = null, val team: String? = null
) : Serializable {
    fun payload(): Map<String, Any> = buildMap {
        put("roomId", roomId); put("matchId", matchId); put("phaseIndex", phaseIndex)
        put("requestId", requestId); put("action", action)
        targetUid?.let { put("targetUid", it) }; team?.let { put("team", it) }
    }
    companion object {
        fun create(roomId: String, state: ServerGameSnapshot, action: String, targetUid: String? = null, team: String? = null) =
            ServerGameCommand(roomId, state.publicState.matchId, state.publicState.phaseIndex, UUID.randomUUID().toString(), action, targetUid, team)
    }
}

internal object ServerGameParser {
    private val roles = setOf("aldeano", "asesino", "policia", "medico", "mercenario", "alcalde", "desertor", "espia", "payador", "bufon", "oraculo")
    fun parsePublic(raw: Any?): ServerGamePublic {
        val data = objectMap(raw)
        require(integer(data, "protocolVersion") == 3L) { "La sala usa otro protocolo online." }
        val phase = ServerGamePhase.valueOf(string(data, "fase"))
        require(data["authorityMode"] == if (phase == ServerGamePhase.LOBBY) "lobby" else "server") {
            "La sala perdió la autoridad del servidor."
        }
        val matchId = string(data, "matchId")
        val roster = entries(data["jugadores"]).map {
            val p = objectMap(it)
            ServerGamePlayer(string(p, "uidTemporal"), integer(p, "orden").toInt(), string(p, "nombre"),
                p["publicId"] as? String ?: "", boolean(p, "vivo"), boolean(p, "muteado"),
                p["causaEliminacion"] as? String ?: "NONE", optionalRole(p["rolKey"]))
        }.sortedBy { it.order }
        require(phase == ServerGamePhase.LOBBY || roster.size in 2..30)
        require(roster.map { it.uid }.distinct().size == roster.size)
        require(roster.map { it.order } == roster.indices.toList())
        val events = entries(data["eventosPublicos"]).takeLast(60).map {
            val e = objectMap(it); ServerGameEvent(integer(e, "seq"), string(e, "codigo"), integer(e, "ronda").toInt(),
                strings(e["jugadores"]), string(e, "texto"))
        }
        require(events.all { it.seq > 0 } && events.zipWithNext().all { (a, b) -> a.seq < b.seq }) {
            "La secuencia de eventos online no es válida."
        }
        val winner = (data["ganador"] as? String)?.takeIf { it.isNotBlank() }
        require(winner == null || winner in setOf("Pueblo", "Traidores", "Cancelada"))
        val deadline = data["limiteFaseEpochMs"]?.let { integer(data, "limiteFaseEpochMs") }
        require(phase in setOf(ServerGamePhase.FINALIZADA, ServerGamePhase.LOBBY) || deadline != null)
        val totals = when (val rawTotals = data["votosTotales"]) {
            null -> emptyMap()
            is List<*> -> rawTotals.mapIndexedNotNull { index, count -> count?.let { index to it } }.toMap()
            is Map<*, *> -> rawTotals.entries.associate { entry ->
                val order = (entry.key as? String)?.toIntOrNull()
                requireNotNull(order) { "Orden del recuento inválido." } to entry.value
            }
            else -> error("Recuento online inválido.")
        }.mapValues { (_, rawCount) ->
            val count = rawCount as? Number ?: error("Cantidad de votos inválida.")
            require(count.toDouble() == count.toInt().toDouble() && count.toInt() in 0..60)
            count.toInt()
        }
        require(totals.keys.all { order -> roster.any { it.order == order } })
        require(phase !in setOf(ServerGamePhase.VOTACION, ServerGamePhase.DESEMPATE_VOTACION) || totals.isEmpty()) {
            "El recuento no puede publicarse durante una votación."
        }
        return ServerGamePublic(matchId, phase, integer(data, "phaseIndex").toInt(), integer(data, "revision"),
            (data["ronda"] as? Number)?.toInt() ?: 1, deadline, data["anuncioPublico"] as? String ?: "", winner,
            roster, events, data["alcaldeRevelado"] as? String, strings(data["empateVoto"]),
            strings(data["jugadoresContrapunto"]), data["invitadoOraculo"] as? String,
            data["desertorBando"] as? String, entries(data["victoriasEspeciales"]).map { string(objectMap(it), "jugador") },
            totals, (data["expulsadoDia"] as? String)?.also { uid -> require(roster.any { it.uid == uid }) },
            data["rondaVoto"]?.let { integer(data, "rondaVoto").toInt().also { require(it in 0..4) } } ?: 0,
            data["alcaldeCorrupcion"] == true,
            entries(data["victoriasEspeciales"]).mapNotNull { objectMap(it)["key"] as? String }.toSet(),
            entries(data["votosIndividuales"]).map {
                val b = objectMap(it); integer(b, "votante").toInt() to integer(b, "objetivo").toInt()
            }.also { ballots ->
                require(phase !in setOf(ServerGamePhase.VOTACION, ServerGamePhase.DESEMPATE_VOTACION) || ballots.isEmpty()) {
                    "Los votos no pueden publicarse durante una votación."
                }
                require(ballots.all { (voter, target) -> roster.any { it.order == voter } && roster.any { it.order == target } })
            },
            (data["sospechaContrapunto"] as? String)?.takeIf { uid -> roster.any { it.uid == uid } },
            (data["listosVotar"] as? Map<*, *>)?.let {
                val r = objectMap(it)
                ServerReadyToVote(integer(r, "listos").toInt(), integer(r, "total").toInt(), integer(r, "desdeEpochMs"))
            })
    }
    fun parsePrivate(raw: Any?): ServerGamePrivate {
        val data = objectMap(raw)
        val roleEntries = entries(data["rolesVisibles"]).map {
            val r = objectMap(it); integer(r, "orden").toInt() to requireNotNull(optionalRole(r["rolKey"]))
        }
        require(roleEntries.map { it.first }.distinct().size == roleEntries.size)
        return ServerGamePrivate(string(data, "matchId"), integer(data, "phaseIndex").toInt(), integer(data, "revision"),
            roleEntries.toMap(), entries(data["accionesConfirmadas"]).map {
                val a = objectMap(it); ServerGameConfirmedAction(string(a, "action"), a["targetUid"] as? String, a["team"] as? String)
            }, strings(data["objetivosBloqueados"]).toSet(), data["desertorBando"] as? String,
            data["desertorCambioBando"] == true, data["oraculoUsado"] == true, data["payadorUsado"] == true,
            entries(data["investigaciones"]).map { val i = objectMap(it); string(i, "targetUid") to boolean(i, "traitor") })
    }
    fun parsePermissions(raw: Any?): ServerGamePermissions {
        val data = objectMap(raw)
        return ServerGamePermissions(string(data, "matchId"), integer(data, "phaseIndex").toInt(), boolean(data, "member"),
            data["publicChat"] == true, data["traitorChat"] == true, data["deadChat"] == true, data["reactions"] == true)
    }
    private fun optionalRole(value: Any?): String? {
        if (value == null) return null
        return (value as? String)?.takeIf { it in roles } ?: error("El servidor envió un rol desconocido.")
    }
    private fun integer(data: Map<String, Any?>, key: String): Long {
        val n = data[key] as? Number ?: error("Falta $key en el estado online.")
        val result = n.toLong()
        require(n.toDouble() == result.toDouble() && result in 0..Int.MAX_VALUE.toLong() ||
            key in setOf("limiteFaseEpochMs", "revision", "desdeEpochMs") && n.toDouble() == result.toDouble() && result >= 0)
        return result
    }
    private fun string(data: Map<String, Any?>, key: String) = (data[key] as? String)
        ?.takeIf { it.isNotBlank() && it.length <= 2000 } ?: error("Falta $key en el estado online.")
    private fun boolean(data: Map<String, Any?>, key: String) = data[key] as? Boolean ?: error("Falta $key en el estado online.")
    private fun objectMap(raw: Any?): Map<String, Any?> = (raw as? Map<*, *>)?.entries?.associate {
        require(it.key is String); it.key as String to it.value
    } ?: error("El estado online no es válido.")
    // RTDB can return numeric-key objects for sparse arrays. Sort by the numeric index, never lexically.
    private fun entries(raw: Any?): List<Any?> = when (raw) {
        null -> emptyList()
        is List<*> -> raw.filterNotNull()
        is Map<*, *> -> raw.entries.sortedBy { (it.key as? String)?.toIntOrNull() ?: error("Índice online inválido.") }.map { it.value }
        else -> error("La lista online no es válida.")
    }
    private fun strings(raw: Any?): List<String> = entries(raw).map { it as? String ?: error("Identificador online inválido.") }
}

/** Three listeners can arrive in any order. Never mix a new public phase with old private permissions. */
internal class ServerGameInbox(private val ownUid: String, private val matchId: String) {
    var publicState: ServerGamePublic? = null; private set
    var privateState: ServerGamePrivate? = null; private set
    var permissions: ServerGamePermissions? = null; private set
    fun acceptPublic(value: ServerGamePublic) {
        require(value.matchId == matchId)
        val old = publicState
        if (old == null || value.phaseIndex > old.phaseIndex || value.phaseIndex == old.phaseIndex && value.revision > old.revision) publicState = value
    }
    fun acceptPrivate(value: ServerGamePrivate) {
        require(value.matchId == matchId)
        val old = privateState
        if (old == null || value.phaseIndex > old.phaseIndex || value.phaseIndex == old.phaseIndex && value.revision >= old.revision) privateState = value
    }
    fun acceptPermissions(value: ServerGamePermissions) {
        require(value.matchId == matchId)
        if (permissions == null || value.phaseIndex >= permissions!!.phaseIndex) permissions = value
    }
    fun snapshot(): ServerGameSnapshot? {
        val p = publicState ?: return null; val own = privateState ?: return null; val access = permissions ?: return null
        if (!access.member || p.phaseIndex != own.phaseIndex || p.phaseIndex != access.phaseIndex) return null
        val human = p.players.singleOrNull { it.uid == ownUid } ?: return null
        require(own.visibleRoleKeys.containsKey(human.order)) { "Falta tu rol privado." }
        return ServerGameSnapshot(p, own, access, ownUid)
    }
}

/** Persist only presentation cursors. Their keys never depend on the moving array index. */
internal data class ServerGamePresentationCursor(
    val matchId: String, val phaseIndex: Int = -1, val revision: Long = -1, val eventSeq: Long = 0
) : Serializable
internal data class ServerGamePresentationUpdate(val phaseChanged: Boolean, val events: List<ServerGameEvent>)
internal class ServerGamePresentationTracker(restored: ServerGamePresentationCursor? = null) {
    var cursor: ServerGamePresentationCursor? = restored; private set
    fun accept(state: ServerGamePublic): ServerGamePresentationUpdate {
        val old = cursor?.takeIf { it.matchId == state.matchId }
        if (old != null && (state.phaseIndex < old.phaseIndex ||
                state.phaseIndex == old.phaseIndex && state.revision < old.revision)) {
            return ServerGamePresentationUpdate(false, emptyList())
        }
        val unseen = state.events.filter { it.seq > (old?.eventSeq ?: 0) }
        cursor = ServerGamePresentationCursor(state.matchId, state.phaseIndex, state.revision,
            maxOf(old?.eventSeq ?: 0, state.events.maxOfOrNull { it.seq } ?: 0))
        return ServerGamePresentationUpdate(old == null || state.phaseIndex > old.phaseIndex, unseen)
    }
}

internal object ServerRoomProtocol {
    fun compatible(version: Long?, authority: String?, generation: Long?, alreadyV3: Boolean): Boolean {
        val expectsV3 = alreadyV3 || version == 3L || authority in setOf("server", "lobby") || generation != null
        return !expectsV3 || version == 3L && authority in setOf(null, "server", "lobby")
    }
}

internal data class ServerGameActionOption(val action: String, val label: String, val targets: List<String> = emptyList(), val team: String? = null)
internal object ServerGameActionPolicy {
    fun options(state: ServerGameSnapshot, nowMs: Long?): List<ServerGameActionOption> {
        val p = state.publicState; val own = state.privateState; val human = state.human; val role = state.ownRoleKey
        if (nowMs == null || !state.permissions.member || p.winner != null || p.deadlineMs == null || nowMs >= p.deadlineMs) return emptyList()
        if (!human.alive) return emptyList()
        val choices = mutableListOf<ServerGameActionOption>()
        val living = p.players.filter { it.alive }
        fun target(action: String, label: String, players: List<ServerGamePlayer>) {
            if (players.isNotEmpty()) choices += ServerGameActionOption(action, label, players.map { it.uid })
        }
        val canReconsider = role == "desertor" && own.deserterTeam != null && !own.deserterUsed && p.round >= 4
        if (p.phase == ServerGamePhase.REPARTO) {
            if (role == "desertor" && own.deserterTeam == null) {
                choices += ServerGameActionOption("desertor_initial", "ELEGIR PUEBLO", team = "Pueblo")
                choices += ServerGameActionOption("desertor_initial", "ELEGIR TRAIDORES", team = "Traidores")
            } else if (own.confirmed.none { it.action == "role_ack" }) choices += ServerGameActionOption("role_ack", "YA LEÍ MI ROL")
        }
        if (p.phase == ServerGamePhase.NOCHE && own.confirmed.none { it.action in nightActions }) when (role) {
            "asesino", "espia" -> target("matar", "ELEGIR VÍCTIMA", living.filter { it.uid != human.uid && state.roleKey(it) !in setOf("asesino", "mercenario", "espia") })
            "mercenario" -> target("silenciar", "SILENCIAR", living.filter { it.uid != human.uid && it.uid !in own.blockedTargets })
            "policia" -> target("investigar", "INVESTIGAR", living.filter { it.uid != human.uid })
            "medico" -> target("salvar", "PROTEGER", living)
            "oraculo" -> if (!own.oracleUsed && p.round > 1) {
                val dead = p.players.filter { !it.alive && it.deathCause != "ABANDONO" }
                target("invitar_muerto", "INVITAR A UN MUERTO", dead)
                if (dead.isNotEmpty()) choices += ServerGameActionOption("guardar_poder", "GUARDAR PODER")
            }
        }
        if (canReconsider && p.phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.DESERTOR_RECONSIDERACION)) {
            choices += ServerGameActionOption("desertor_rethink", "MANTENER BANDO", team = "mantener")
            choices += ServerGameActionOption("desertor_rethink", "CAMBIAR A ${if (own.deserterTeam == "Pueblo") "TRAIDORES" else "PUEBLO"}",
                team = if (own.deserterTeam == "Pueblo") "Traidores" else "Pueblo")
        }
        // Usual table: every living player, muted ones too, can ask to vote early after ten seconds.
        p.readyToVote?.takeIf { p.phase == ServerGamePhase.DIA_DEBATE && nowMs >= it.fromEpochMs }?.let {
            choices += if (own.confirmed.any { it.action == "listo_votar" }) ServerGameActionOption("cancelar_listo", "CANCELAR")
                else ServerGameActionOption("listo_votar", "LISTOS PARA VOTAR")
        }
        if (human.muted) return choices // Deserter's decision is private and remains permitted.
        if (p.phase in setOf(ServerGamePhase.VOTACION, ServerGamePhase.DESEMPATE_VOTACION))
            target("votar", "VOTAR", living.filter { it.uid != human.uid && (p.phase != ServerGamePhase.DESEMPATE_VOTACION || it.uid in p.tieCandidates) })
        if (role == "alcalde") {
            if (p.mayorUid == null && p.phase in setOf(ServerGamePhase.DIA_DEBATE, ServerGamePhase.VOTACION,
                    ServerGamePhase.DESEMPATE_VOTACION, ServerGamePhase.ALCALDE_DESEMPATE)) choices += ServerGameActionOption("revelar_alcalde", "REVELAR MI CARGO")
            if (p.phase == ServerGamePhase.ALCALDE_DESEMPATE && p.mayorUid == human.uid)
                target("decidir_empate", "DECIDIR EXPULSIÓN", living.filter { it.uid in p.tieCandidates })
        }
        if (role == "payador") {
            if (p.phase == ServerGamePhase.DIA_DEBATE && !own.payadorUsed)
                target("contrapunto", "ELEGIR PARA CONTRAPUNTO", living.filter { it.uid != human.uid && it.uid !in p.counterpointPlayers })
            if (p.phase == ServerGamePhase.CONTRAPUNTO)
                target("senalar_contrapunto", "SEÑALAR SOSPECHOSO", living.filter { it.uid in p.counterpointPlayers })
        }
        return choices
    }
    private val nightActions = setOf("matar", "silenciar", "investigar", "salvar", "invitar_muerto", "guardar_poder")
}

/** Recovery is a bounded nudge after expiry, never a timer write or a local phase transition. */
internal class ServerGameRecoveryPolicy(private val uid: String) {
    private var phaseKey = ""; private var attempts = 0; private var nextAtMs = 0L
    fun due(state: ServerGamePublic, nowMs: Long): Boolean {
        val key = "${state.matchId}:${state.phaseIndex}"
        if (key != phaseKey) { phaseKey = key; attempts = 0; nextAtMs = 0 }
        val deadline = state.deadlineMs ?: return false
        val stagger = (uid.hashCode().toLong() and 0x7fffffffL) % 2000
        return state.winner == null && attempts < 3 && nowMs >= deadline + 5000 + stagger && nowMs >= nextAtMs
    }
    fun attempted(nowMs: Long, retryAfterMs: Long = 0) {
        attempts++; nextAtMs = nowMs + maxOf(retryAfterMs, 4000L shl (attempts - 1))
    }
}
