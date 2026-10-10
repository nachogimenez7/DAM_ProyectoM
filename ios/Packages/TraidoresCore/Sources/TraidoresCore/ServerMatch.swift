import Foundation

// Client side of the server-authority protocol (V3): `docs/CONTRATO_AUTORIDAD_SERVIDOR_V3.md`.
// Same interpretation as Android's `ServerGameContract.kt`. Nothing here resolves a phase,
// deals a role or declares a winner: it decodes what the server published and says which
// intentions the player may send. Unknown phases or roles are errors, never a local fallback.

public enum ServerMatchPhase: String, CaseIterable, Sendable {
    case assignment = "REPARTO", night = "NOCHE", dawn = "AMANECER", debate = "DIA_DEBATE"
    case counterpoint = "CONTRAPUNTO", vote = "VOTACION", tieVote = "DESEMPATE_VOTACION"
    case voteCount = "RECUENTO_VOTOS", mayorTie = "ALCALDE_DESEMPATE", dayResult = "RESULTADO"
    case deserterWindow = "DESERTOR_RECONSIDERACION", finished = "FINALIZADA", lobby = "LOBBY"

    public var title: String {
        switch self {
        case .assignment: "Tu rol"
        case .night: "Noche"
        case .dawn: "Amanecer"
        case .debate: "Debate"
        case .counterpoint: "Contrapunto"
        case .vote: "Votación"
        case .tieVote: "Segunda votación"
        case .voteCount: "Recuento"
        case .mayorTie: "Decisión del Alcalde"
        case .dayResult: "Resultado del día"
        case .deserterWindow: "Revisión de bando"
        case .finished: "Partida terminada"
        case .lobby: "Sala"
        }
    }

    public var isNight: Bool { self == .night }
}

public enum ServerMatchError: Error, Equatable, Sendable {
    case invalid(String)
    case otherProtocol
    case lostAuthority
    case unknownRole
    case unknownPhase(String)
}

public struct ServerMatchPlayer: Equatable, Sendable, Identifiable {
    public let uid: String
    public let order: Int
    public let name: String
    public let publicId: String
    public let alive: Bool
    public let muted: Bool
    public let deathCause: String
    /// Only when the server made it public: dead with reveal on, revealed Mayor, or the final.
    public let publicRole: RoleKey?
    public var id: String { uid }
    public var abandoned: Bool { deathCause == "ABANDONO" }
}

public struct ServerMatchEvent: Equatable, Sendable, Identifiable {
    public let seq: Int
    public let code: String
    public let round: Int
    public let players: [String]
    public let text: String
    public var id: Int { seq }
}

public struct ServerMatchPublic: Equatable, Sendable {
    public let matchId: String
    public let phase: ServerMatchPhase
    public let phaseIndex: Int
    public let revision: Int
    public let round: Int
    public let deadlineMs: Int64?
    public let announcement: String
    public let winner: String?
    public let players: [ServerMatchPlayer]
    public let events: [ServerMatchEvent]
    public let mayorUid: String?
    public let mayorCorruption: Bool
    public let tieCandidates: [String]
    public let counterpointPlayers: [String]
    public let counterpointPointed: String?
    public let oracleGuestUid: String?
    public let voteRound: Int
    /// Vote totals by player order; hidden while a vote is open.
    public let voteTotals: [Int: Int]
    public let eliminationUid: String?
    public let deserterTeam: String?
    public let specialWinners: [String]

    public func player(_ uid: String?) -> ServerMatchPlayer? { players.first { $0.uid == uid } }
}

public struct ServerMatchConfirmedAction: Equatable, Sendable {
    public let action: String
    public let targetUid: String?
    public let team: String?
}

public struct ServerMatchInvestigation: Equatable, Sendable {
    public let targetUid: String
    public let round: Int
    public let traitor: Bool
}

public struct ServerMatchPrivate: Equatable, Sendable {
    public let matchId: String
    public let phaseIndex: Int
    public let revision: Int
    public let visibleRoles: [Int: RoleKey]
    public let confirmed: [ServerMatchConfirmedAction]
    public let blockedTargets: Set<String>
    public let deserterTeam: String?
    public let deserterUsed: Bool
    public let oracleUsed: Bool
    public let payadorUsed: Bool
    public let investigations: [ServerMatchInvestigation]
}

public struct ServerMatchPermissions: Equatable, Sendable {
    public let matchId: String
    public let phaseIndex: Int
    public let member: Bool
    public let alive: Bool
    public let publicChat: Bool
    public let traitorChat: Bool
    public let deadChat: Bool
}

public struct ServerMatchSnapshot: Equatable, Sendable {
    public let publicState: ServerMatchPublic
    public let privateState: ServerMatchPrivate
    public let permissions: ServerMatchPermissions
    public let ownUid: String

    public var me: ServerMatchPlayer { publicState.players.first { $0.uid == ownUid }! }
    public var ownRole: RoleKey { privateState.visibleRoles[me.order]! }

    public func role(of player: ServerMatchPlayer) -> RoleKey? {
        player.publicRole ?? privateState.visibleRoles[player.order]
    }

    /// Same rule as the account history: an abandonment never wins; a special victory always does.
    public func won(_ player: ServerMatchPlayer) -> Bool {
        guard let winner = publicState.winner, ["Pueblo", "Traidores"].contains(winner), !player.abandoned else { return false }
        if publicState.specialWinners.contains(player.name) { return true }
        switch role(of: player) {
        case .deserter?:
            return player.alive && (publicState.deserterTeam ?? privateState.deserterTeam) == winner
        case .jester?, nil:
            return false
        case let role?:
            let traitor = ServerMatchRules.traitorRoles.contains(role)
            return winner == "Traidores" ? traitor : !traitor
        }
    }
}

public enum ServerMatchRules {
    public static let protocolVersion = 3
    public static let traitorRoles: Set<RoleKey> = [.assassin, .mercenary, .spy]
    public static let nightActions: Set<String> = ["matar", "silenciar", "investigar", "salvar", "invitar_muerto", "guardar_poder"]
    public static let deserterReconsiderationRound = 4
}

/// Explains server-owned choices without predicting the random initial assignment or
/// the final winner. Keeping a team explicitly uses the same single review as switching.
public enum ServerMatchDeserterPresentation {
    public static func explanation(_ state: ServerMatchSnapshot) -> String {
        guard state.privateState.deserterTeam != nil else {
            return "Elegís en privado. Si no elegís antes de que termine el reparto, el servidor sortea tu bando."
        }
        if state.privateState.deserterUsed {
            return "Ya usaste tu revisión de bando. Esta elección es privada."
        }
        if state.publicState.phase == .deserterWindow {
            return "Antes de cerrar la victoria Traidora, podés mantener o cambiar tu bando. Ambas opciones consumen tu revisión. Si vence el plazo, mantenés tu bando."
        }
        if state.publicState.round < ServerMatchRules.deserterReconsiderationRound {
            return "Desde la ronda 4 podés mantener o cambiar tu bando una sola vez, durante el debate."
        }
        return "Durante el debate podés mantener o cambiar tu bando una sola vez. Mantener también consume la revisión. Podés decidir aunque estés silenciado."
    }
}

// MARK: - Parsing

public enum ServerMatchParser {
    public static func parsePublic(_ raw: Any?) throws -> ServerMatchPublic {
        let data = try object(raw)
        guard try integer(data, "protocolVersion") == ServerMatchRules.protocolVersion else { throw ServerMatchError.otherProtocol }
        let phaseName = try string(data, "fase")
        guard let phase = ServerMatchPhase(rawValue: phaseName) else { throw ServerMatchError.unknownPhase(phaseName) }
        guard data["authorityMode"] as? String == (phase == .lobby ? "lobby" : "server") else { throw ServerMatchError.lostAuthority }
        let roster = try entries(data["jugadores"]).map { raw -> ServerMatchPlayer in
            let p = try object(raw)
            return ServerMatchPlayer(uid: try string(p, "uidTemporal"), order: try integer(p, "orden"), name: try string(p, "nombre"),
                                     publicId: p["publicId"] as? String ?? "", alive: try boolean(p, "vivo"),
                                     muted: try boolean(p, "muteado"), deathCause: p["causaEliminacion"] as? String ?? "NONE",
                                     publicRole: try optionalRole(p["rolKey"]))
        }.sorted { $0.order < $1.order }
        guard phase == .lobby || (2...30).contains(roster.count) else { throw ServerMatchError.invalid("jugadores") }
        guard Set(roster.map(\.uid)).count == roster.count, roster.map(\.order) == Array(roster.indices) else {
            throw ServerMatchError.invalid("orden")
        }
        let events = try entries(data["eventosPublicos"]).suffix(60).map { raw -> ServerMatchEvent in
            let e = try object(raw)
            return ServerMatchEvent(seq: try integer(e, "seq"), code: try string(e, "codigo"), round: try integer(e, "ronda"),
                                    players: try strings(e["jugadores"]), text: try string(e, "texto"))
        }
        guard events.allSatisfy({ $0.seq > 0 }), zip(events, events.dropFirst()).allSatisfy({ $0.seq < $1.seq }) else {
            throw ServerMatchError.invalid("eventos")
        }
        let winner = (data["ganador"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        guard winner == nil || ["Pueblo", "Traidores", "Cancelada"].contains(winner!) else { throw ServerMatchError.invalid("ganador") }
        let deadline = data["limiteFaseEpochMs"] == nil ? nil : try int64(data, "limiteFaseEpochMs")
        guard [.finished, .lobby].contains(phase) || deadline != nil else { throw ServerMatchError.invalid("limiteFaseEpochMs") }
        return ServerMatchPublic(
            matchId: try string(data, "matchId"), phase: phase, phaseIndex: try integer(data, "phaseIndex"),
            revision: try integer(data, "revision"), round: (data["ronda"] as? NSNumber)?.intValue ?? 1,
            deadlineMs: deadline, announcement: data["anuncioPublico"] as? String ?? "", winner: winner,
            players: roster, events: Array(events), mayorUid: data["alcaldeRevelado"] as? String,
            mayorCorruption: data["alcaldeCorrupcion"] as? Bool ?? false, tieCandidates: try strings(data["empateVoto"]),
            counterpointPlayers: try strings(data["jugadoresContrapunto"]),
            counterpointPointed: data["sospechaContrapunto"] as? String, oracleGuestUid: data["invitadoOraculo"] as? String,
            voteRound: (data["rondaVoto"] as? NSNumber)?.intValue ?? 0, voteTotals: try totals(data["votosTotales"]),
            eliminationUid: data["expulsadoDia"] as? String, deserterTeam: data["desertorBando"] as? String,
            specialWinners: try entries(data["victoriasEspeciales"]).map { try string(try object($0), "jugador") })
    }

    public static func parsePrivate(_ raw: Any?) throws -> ServerMatchPrivate {
        let data = try object(raw)
        var roles: [Int: RoleKey] = [:]
        for raw in try entries(data["rolesVisibles"]) {
            let r = try object(raw)
            let order = try integer(r, "orden")
            guard roles[order] == nil, let role = try optionalRole(r["rolKey"]) else { throw ServerMatchError.invalid("rolesVisibles") }
            roles[order] = role
        }
        return ServerMatchPrivate(
            matchId: try string(data, "matchId"), phaseIndex: try integer(data, "phaseIndex"),
            revision: (data["revision"] as? NSNumber)?.intValue ?? 0, visibleRoles: roles,
            confirmed: try entries(data["accionesConfirmadas"]).map {
                let a = try object($0)
                return ServerMatchConfirmedAction(action: try string(a, "action"), targetUid: a["targetUid"] as? String, team: a["team"] as? String)
            },
            blockedTargets: Set(try strings(data["objetivosBloqueados"])), deserterTeam: data["desertorBando"] as? String,
            deserterUsed: data["desertorCambioBando"] as? Bool == true, oracleUsed: data["oraculoUsado"] as? Bool == true,
            payadorUsed: data["payadorUsado"] as? Bool == true,
            investigations: try entries(data["investigaciones"]).map {
                let i = try object($0)
                return ServerMatchInvestigation(targetUid: try string(i, "targetUid"), round: (i["round"] as? NSNumber)?.intValue ?? 0,
                                                traitor: try boolean(i, "traitor"))
            })
    }

    public static func parsePermissions(_ raw: Any?) throws -> ServerMatchPermissions {
        let data = try object(raw)
        return ServerMatchPermissions(matchId: try string(data, "matchId"), phaseIndex: try integer(data, "phaseIndex"),
                                      member: try boolean(data, "member"), alive: data["alive"] as? Bool ?? false,
                                      publicChat: data["publicChat"] as? Bool == true, traitorChat: data["traitorChat"] as? Bool == true,
                                      deadChat: data["deadChat"] as? Bool == true)
    }

    private static func optionalRole(_ value: Any?) throws -> RoleKey? {
        guard let value else { return nil }
        guard let raw = value as? String, let role = RoleKey(rawValue: raw) else { throw ServerMatchError.unknownRole }
        return role
    }

    private static func object(_ raw: Any?) throws -> [String: Any] {
        guard let data = raw as? [String: Any] else { throw ServerMatchError.invalid("objeto") }
        return data
    }

    // RTDB returns sparse arrays as objects keyed by index: sort numerically, never lexically.
    private static func entries(_ raw: Any?) throws -> [Any] {
        switch raw {
        case nil: return []
        case let list as [Any]: return list.filter { !($0 is NSNull) }
        case let map as [String: Any]:
            return try map.map { key, value -> (Int, Any) in
                guard let index = Int(key) else { throw ServerMatchError.invalid("índice") }
                return (index, value)
            }.sorted { $0.0 < $1.0 }.map(\.1)
        default: throw ServerMatchError.invalid("lista")
        }
    }

    private static func strings(_ raw: Any?) throws -> [String] {
        try entries(raw).map { value in
            guard let string = value as? String else { throw ServerMatchError.invalid("identificador") }
            return string
        }
    }

    /// `votosTotales` is keyed by player order; RTDB may deliver it as an array (dense keys).
    private static func totals(_ raw: Any?) throws -> [Int: Int] {
        switch raw {
        case nil: return [:]
        case let list as [Any]:
            var result: [Int: Int] = [:]
            for (index, value) in list.enumerated() { if let n = value as? NSNumber { result[index] = n.intValue } }
            return result
        case let map as [String: Any]:
            var result: [Int: Int] = [:]
            for (key, value) in map {
                guard let order = Int(key), let n = value as? NSNumber else { throw ServerMatchError.invalid("votosTotales") }
                result[order] = n.intValue
            }
            return result
        default: throw ServerMatchError.invalid("votosTotales")
        }
    }

    private static func integer(_ data: [String: Any], _ key: String) throws -> Int {
        guard let n = data[key] as? NSNumber, n.doubleValue == n.doubleValue.rounded(), n.intValue >= 0 else {
            throw ServerMatchError.invalid(key)
        }
        return n.intValue
    }

    private static func int64(_ data: [String: Any], _ key: String) throws -> Int64 {
        guard let n = data[key] as? NSNumber, n.doubleValue == n.doubleValue.rounded(), n.int64Value >= 0 else {
            throw ServerMatchError.invalid(key)
        }
        return n.int64Value
    }

    private static func string(_ data: [String: Any], _ key: String) throws -> String {
        guard let value = data[key] as? String, !value.isEmpty, value.count <= 2000 else { throw ServerMatchError.invalid(key) }
        return value
    }

    private static func boolean(_ data: [String: Any], _ key: String) throws -> Bool {
        guard let value = data[key] as? Bool else { throw ServerMatchError.invalid(key) }
        return value
    }
}

// MARK: - Inbox

/// The three listeners arrive in any order. A snapshot exists only when the public phase,
/// the private data and the permissions all belong to the same phase of the same match.
public struct ServerMatchInbox: Sendable {
    public let ownUid: String
    public let matchId: String
    public private(set) var publicState: ServerMatchPublic?
    public private(set) var privateState: ServerMatchPrivate?
    public private(set) var permissions: ServerMatchPermissions?

    public init(ownUid: String, matchId: String) {
        self.ownUid = ownUid
        self.matchId = matchId
    }

    public mutating func accept(_ value: ServerMatchPublic) {
        guard value.matchId == matchId else { return }
        if let old = publicState, value.phaseIndex < old.phaseIndex || value.phaseIndex == old.phaseIndex && value.revision <= old.revision {
            return
        }
        publicState = value
    }

    public mutating func accept(_ value: ServerMatchPrivate) {
        guard value.matchId == matchId else { return }
        if let old = privateState, value.phaseIndex < old.phaseIndex || value.phaseIndex == old.phaseIndex && value.revision < old.revision {
            return
        }
        privateState = value
    }

    public mutating func accept(_ value: ServerMatchPermissions) {
        guard value.matchId == matchId else { return }
        if let old = permissions, value.phaseIndex < old.phaseIndex { return }
        permissions = value
    }

    public var snapshot: ServerMatchSnapshot? {
        guard let p = publicState, let own = privateState, let access = permissions, access.member,
              p.phaseIndex == own.phaseIndex, p.phaseIndex == access.phaseIndex,
              let me = p.players.first(where: { $0.uid == ownUid }), own.visibleRoles[me.order] != nil else { return nil }
        return ServerMatchSnapshot(publicState: p, privateState: own, permissions: access, ownUid: ownUid)
    }
}

// MARK: - Presentation cursor

/// Which events and phase changes have already been presented. Keys never depend on the
/// position in the event window: events by `matchId + seq`, phases by `matchId + phaseIndex`.
public struct ServerMatchPresentationCursor: Codable, Equatable, Sendable {
    public var matchId: String
    public var phaseIndex: Int
    public var revision: Int
    public var eventSeq: Int

    public init(matchId: String, phaseIndex: Int = -1, revision: Int = -1, eventSeq: Int = 0) {
        self.matchId = matchId
        self.phaseIndex = phaseIndex
        self.revision = revision
        self.eventSeq = eventSeq
    }
}

public struct ServerMatchPresentationUpdate: Equatable, Sendable {
    public let phaseChanged: Bool
    public let events: [ServerMatchEvent]
}

public struct ServerMatchPresentationTracker: Sendable {
    public private(set) var cursor: ServerMatchPresentationCursor?

    public init(restored: ServerMatchPresentationCursor? = nil) { cursor = restored }

    public mutating func accept(_ state: ServerMatchPublic) -> ServerMatchPresentationUpdate {
        let old = cursor?.matchId == state.matchId ? cursor : nil
        if let old, state.phaseIndex < old.phaseIndex || state.phaseIndex == old.phaseIndex && state.revision < old.revision {
            return ServerMatchPresentationUpdate(phaseChanged: false, events: [])
        }
        let seen = old?.eventSeq ?? 0
        let unseen = state.events.filter { $0.seq > seen }
        cursor = ServerMatchPresentationCursor(matchId: state.matchId, phaseIndex: state.phaseIndex, revision: state.revision,
                                               eventSeq: max(seen, state.events.map(\.seq).max() ?? 0))
        return ServerMatchPresentationUpdate(phaseChanged: old == nil || state.phaseIndex > old!.phaseIndex, events: unseen)
    }
}

// MARK: - Intentions

public struct ServerMatchActionOption: Equatable, Sendable, Identifiable {
    public let action: String
    public let label: String
    public let targets: [String]
    public let team: String?
    public var id: String { "\(action):\(team ?? "")" }
    public var needsTarget: Bool { !targets.isEmpty }
}

public enum ServerMatchActionPolicy {
    /// The intentions the server would accept from this player now. The server validates again.
    public static func options(_ state: ServerMatchSnapshot, nowMs: Int64?) -> [ServerMatchActionOption] {
        let p = state.publicState, own = state.privateState, me = state.me, role = state.ownRole
        guard let nowMs, state.permissions.member, p.winner == nil, let deadline = p.deadlineMs, nowMs < deadline, me.alive else {
            return []
        }
        var choices: [ServerMatchActionOption] = []
        let living = p.players.filter(\.alive)
        func target(_ action: String, _ label: String, _ players: [ServerMatchPlayer]) {
            if !players.isEmpty { choices.append(.init(action: action, label: label, targets: players.map(\.uid), team: nil)) }
        }
        let confirmed = Set(own.confirmed.map(\.action))
        let canReconsider = role == .deserter && own.deserterTeam != nil && !own.deserterUsed
            && p.round >= ServerMatchRules.deserterReconsiderationRound
        if p.phase == .assignment {
            if role == .deserter && own.deserterTeam == nil {
                choices.append(.init(action: "desertor_initial", label: "ELEGIR PUEBLO", targets: [], team: "Pueblo"))
                choices.append(.init(action: "desertor_initial", label: "ELEGIR TRAIDORES", targets: [], team: "Traidores"))
            } else if !confirmed.contains("role_ack") {
                choices.append(.init(action: "role_ack", label: "YA LEÍ MI ROL", targets: [], team: nil))
            }
        }
        if p.phase == .night && confirmed.isDisjoint(with: ServerMatchRules.nightActions) {
            switch role {
            case .assassin, .spy:
                target("matar", "ELEGIR VÍCTIMA", living.filter { player in
                    player.uid != me.uid && !(state.role(of: player).map(ServerMatchRules.traitorRoles.contains) ?? false)
                })
            case .mercenary:
                target("silenciar", "SILENCIAR", living.filter { $0.uid != me.uid && !own.blockedTargets.contains($0.uid) })
            case .detective:
                target("investigar", "INVESTIGAR", living.filter { $0.uid != me.uid })
            case .medic:
                target("salvar", "PROTEGER", living)
            case .oracle:
                if !own.oracleUsed && p.round > 1 {
                    let dead = p.players.filter { !$0.alive && !$0.abandoned }
                    target("invitar_muerto", "INVITAR A UN MUERTO", dead)
                    if !dead.isEmpty { choices.append(.init(action: "guardar_poder", label: "GUARDAR PODER", targets: [], team: nil)) }
                }
            default: break
            }
        }
        if canReconsider && [.debate, .deserterWindow].contains(p.phase) {
            let other = own.deserterTeam == "Pueblo" ? "Traidores" : "Pueblo"
            choices.append(.init(action: "desertor_rethink", label: "MANTENER BANDO", targets: [], team: "mantener"))
            choices.append(.init(action: "desertor_rethink", label: "PASARME A \(other.uppercased())", targets: [], team: other))
        }
        // The Deserter's decision is private and stays allowed while silenced.
        if me.muted { return choices }
        if [.vote, .tieVote].contains(p.phase) {
            target("votar", "VOTAR", living.filter { $0.uid != me.uid && (p.phase != .tieVote || p.tieCandidates.contains($0.uid)) })
        }
        if role == .mayor {
            if p.mayorUid == nil && [.debate, .vote, .tieVote, .mayorTie].contains(p.phase) {
                choices.append(.init(action: "revelar_alcalde", label: "REVELAR MI CARGO", targets: [], team: nil))
            }
            if p.phase == .mayorTie && p.mayorUid == me.uid {
                target("decidir_empate", "DECIDIR EXPULSIÓN", living.filter { p.tieCandidates.contains($0.uid) })
            }
        }
        if role == .payador {
            if p.phase == .debate && !own.payadorUsed {
                target("contrapunto", "ELEGIR PARA CONTRAPUNTO", living.filter { $0.uid != me.uid && !p.counterpointPlayers.contains($0.uid) })
            }
            if p.phase == .counterpoint {
                target("senalar_contrapunto", "SEÑALAR SOSPECHOSO", living.filter { p.counterpointPlayers.contains($0.uid) })
            }
        }
        return choices
    }
}

/// One intention as the callable receives it. `requestId` is generated once and reused on retry.
public struct ServerMatchCommand: Equatable, Sendable {
    public let roomId: String
    public let matchId: String
    public let phaseIndex: Int
    public let requestId: String
    public let action: String
    public let targetUid: String?
    public let team: String?

    public init(roomId: String, state: ServerMatchPublic, action: String, targetUid: String? = nil, team: String? = nil,
                requestId: String = UUID().uuidString) {
        self.roomId = roomId
        matchId = state.matchId
        phaseIndex = state.phaseIndex
        self.requestId = requestId
        self.action = action
        self.targetUid = targetUid
        self.team = team
    }

    public var payload: [String: Any] {
        var data: [String: Any] = ["roomId": roomId, "matchId": matchId, "phaseIndex": phaseIndex,
                                   "requestId": requestId, "action": action]
        if let targetUid { data["targetUid"] = targetUid }
        if let team { data["team"] = team }
        return data
    }
}

// MARK: - Recovery

/// A bounded nudge after a phase expired without publication: never a timer write or a
/// local transition. Spread by UID so a full table does not call at the same instant.
public struct ServerMatchRecoveryPolicy: Sendable {
    public static let graceMs: Int64 = 5000
    public static let maxAttempts = 3
    private let spreadMs: Int64
    private var phaseKey = ""
    private var attempts = 0
    private var nextAtMs: Int64 = 0

    public init(uid: String) {
        // Java/Kotlin String.hashCode, as Android's policy.
        var hash: Int32 = 0
        for unit in uid.utf16 { hash = hash &* 31 &+ Int32(unit) }
        spreadMs = Int64(UInt32(bitPattern: hash) & 0x7fffffff) % 2000
    }

    public mutating func due(_ state: ServerMatchPublic, nowMs: Int64) -> Bool {
        let key = "\(state.matchId):\(state.phaseIndex)"
        if key != phaseKey { phaseKey = key; attempts = 0; nextAtMs = 0 }
        guard let deadline = state.deadlineMs, state.winner == nil else { return false }
        return attempts < Self.maxAttempts && nowMs >= deadline + Self.graceMs + spreadMs && nowMs >= nextAtMs
    }

    public mutating func attempted(nowMs: Int64, retryAfterMs: Int64 = 0) {
        attempts += 1
        nextAtMs = nowMs + max(retryAfterMs, 4000 << Int64(attempts - 1))
    }
}
