import Foundation

/// Client side of the `iniciarPartidaV2` callable (`functions/src/index.js`), mirroring
/// Android's `OnlineStartCallableClient.kt`. This client never deals roles nor runs the
/// gameplay authority: the server rereads the room, validates host, ready players and map
/// votes, and writes the public state plus private role documents.
public enum OnlineMatchStartContract {
    public static let region = "southamerica-west1"
    public static let functionName = "iniciarPartidaV2"

    public static func request(roomId: String, hostTieBreakChoice: GameMap? = nil) throws -> [String: String] {
        let room = roomId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !room.isEmpty else { throw OnlineMatchStartFailure.invalidRoom }
        var request = ["roomId": room]
        if let hostTieBreakChoice { request["hostTieBreakChoice"] = hostTieBreakChoice.rawValue }
        return request
    }

    public static func parse(_ raw: Any?) throws -> OnlineMatchStartResult {
        guard let data = raw as? [String: Any] else {
            throw OnlineMatchStartFailure.invalidResponse("La función devolvió una respuesta inválida.")
        }
        switch data["status"] as? String {
        case "started", "already_started":
            let matchId = (data["matchId"] as? String) ?? ""
            guard !matchId.isEmpty else {
                throw OnlineMatchStartFailure.invalidResponse("La función no devolvió matchId.")
            }
            guard let map = (data["mapKey"] as? String).flatMap(GameMap.init(rawValue:)) else {
                throw OnlineMatchStartFailure.invalidResponse("La función devolvió un mapa inválido.")
            }
            return .accepted(matchId: matchId, map: map, alreadyStarted: data["status"] as? String == "already_started")
        case "tie_break_required":
            var maps: [GameMap] = []
            for key in (data["mapKeys"] as? [Any]) ?? [] {
                if let map = (key as? String).flatMap(GameMap.init(rawValue:)), !maps.contains(map) { maps.append(map) }
            }
            guard !maps.isEmpty else {
                throw OnlineMatchStartFailure.invalidResponse("El desempate no contiene mapas válidos.")
            }
            return .mapTieBreakRequired(maps)
        case let status:
            throw OnlineMatchStartFailure.invalidResponse("La función devolvió un estado desconocido: \(status ?? "vacío").")
        }
    }

    /// `code` is the callable error name (`not-found`, `permission-denied`...). The server's
    /// own message is kept for precondition errors, which explain what the room lacks.
    public static func failure(code: String, serverMessage: String?) -> OnlineMatchStartFailure {
        let message = serverMessage?.trimmingCharacters(in: .whitespacesAndNewlines)
        switch code {
        case "unauthenticated": return .sessionExpired
        case "permission-denied": return .notHost
        case "unavailable", "deadline-exceeded": return .unavailable
        case "not-found": return .roomNotFound
        case "failed-precondition", "invalid-argument":
            return .notReady(message?.isEmpty == false ? message : nil)
        default: return .server
        }
    }
}

public enum OnlineMatchStartResult: Equatable, Sendable {
    case accepted(matchId: String, map: GameMap, alreadyStarted: Bool)
    /// Only the host breaks the tie, choosing among these maps, and calls again.
    case mapTieBreakRequired([GameMap])
}

public enum OnlineMatchStartFailure: Error, Equatable, Sendable {
    case invalidRoom, sessionExpired, notHost, unavailable, roomNotFound, server
    case notReady(String?)
    case invalidResponse(String)

    /// Same wording as Android's `OnlineErrorMessages`.
    public var message: String {
        let detail = switch self {
        case .invalidRoom: "La sala no puede estar vacía."
        case .sessionExpired: "Tu sesión venció. Volvé a entrar al modo online."
        case .notHost: "El servidor rechazó la acción. Solo el anfitrión puede realizarla."
        case .unavailable: "No hay conexión estable con el servidor. Probá otra vez."
        case .roomNotFound: "La sala ya no existe o fue borrada."
        case .notReady(let reason): reason ?? "La sala todavía no está lista."
        case .server: "El servidor no pudo completar la acción. Probá otra vez."
        case .invalidResponse(let reason): reason
        }
        return "No se pudo iniciar la partida. \(detail)"
    }
}
