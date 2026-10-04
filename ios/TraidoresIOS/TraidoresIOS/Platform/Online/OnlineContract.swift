import Foundation

enum OnlineContract {
    static let mapKeys = ["pampa", "grecia", "medieval"]
    static let guestAliases = ["Forastero", "Mala Onda", "Aguafiestas", "Chamuyero", "Careta",
                               "Mufa", "Perejil", "Metepatas", "Don Nadie", "El Colado",
                               "Sospechoso", "Rezongón"]
    static let roleKeys = ["aldeano", "policia", "medico", "asesino", "mercenario", "alcalde",
                           "desertor", "espia", "payador", "oraculo", "bufon"]
    static let maxPhotoBytes = 256 * 1024
    static let photoSidePixels = 512

    private static func fullMatch(_ pattern: String, _ value: String) -> Bool {
        value.range(of: pattern, options: .regularExpression) == value.startIndex..<value.endIndex
    }

    static func publicId(_ value: String?) -> String? {
        guard let value, fullMatch("^[0-9]{1,12}$", value) else { return nil }
        return value
    }

    static func roomCode(_ value: String) throws -> String {
        let code = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard fullMatch("^[A-HJ-NP-Z2-9]{6}$", code) else {
            throw OnlineError.invalidCode
        }
        return code
    }

    static func photoURL(_ value: String?, emulatorOrigin: URL? = nil) -> URL? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 1000,
              let url = URL(string: trimmed), let host = url.host, !host.isEmpty else { return nil }
        if url.scheme?.lowercased() == "https" { return url }
        if let emulatorOrigin, url.scheme == "http", emulatorOrigin.scheme == "http",
           host == emulatorOrigin.host, url.port == 9199, emulatorOrigin.port == 9199 {
            return url
        }
        return nil
    }

    // Match Kotlin/Java String.hashCode using UTF-16 code units and signed 32-bit overflow.
    static func guestName(uid: String, selectedAlias: String? = nil) -> String {
        var hash: Int32 = 0
        for unit in uid.utf16 { hash = (hash &* 31) &+ Int32(unit) }
        let magnitude = abs(Int64(hash))
        let alias = selectedAlias.flatMap { guestAliases.contains($0) ? $0 : nil }
            ?? guestAliases[Int(magnitude % Int64(guestAliases.count))]
        return "\(alias) \(1000 + magnitude % 9000)"
    }

    static func profile(uid: String, data: [String: Any], emulatorOrigin: URL? = nil) throws -> PublicProfile {
        guard data["uidTemporal"] as? String == uid,
              let id = publicId(data["publicId"] as? String),
              let name = data["nombrePerfil"] as? String, !name.isEmpty, name.count <= 18 else {
            throw OnlineError.invalidProfile
        }
        return PublicProfile(
            uid: uid, publicId: id, nombrePerfil: name,
            nombreSala: data["nombreSala"] as? String ?? name,
            bioPerfil: data["bioPerfil"] as? String ?? "",
            avatarPerfil: data["avatarPerfil"] as? String ?? "aldeano",
            bannerPerfil: data["bannerPerfil"] as? String ?? "pampa",
            rolFavoritoPerfil: data["rolFavoritoPerfil"] as? String ?? "aldeano",
            fotoPerfil: photoURL(data["fotoPerfil"] as? String, emulatorOrigin: emulatorOrigin),
            fotoPlayGames: photoURL(data["fotoPlayGames"] as? String, emulatorOrigin: emulatorOrigin),
            emotesPerfil: data["emotesPerfil"] as? [String] ?? [],
            temaCosmeticoPerfil: data["temaCosmeticoPerfil"] as? String ?? "classic"
        )
    }

    static func lobbyConfig(_ data: [String: Any]) throws -> LobbyConfig {
        guard let transition = data["transicionSeg"] as? Int, (1...10).contains(transition),
              let night = data["nocheSeg"] as? Int, (10...90).contains(night),
              let discussion = data["discusionSeg"] as? Int, (30...180).contains(discussion),
              let voting = data["votacionSeg"] as? Int, (10...60).contains(voting),
              let reveal = data["revelarRolesAlMorir"] as? Bool,
              let individual = data["votosIndividuales"] as? Bool else {
            throw OnlineError.invalidRoomConfiguration
        }
        let preset = data["presetRoles"] as? String
        let counts = data["roles"] as? String
        if preset != nil || counts != nil {
            guard let preset, ["RECOMMENDED", "CLASSIC", "CHAOTIC", "PERSONALIZADO"].contains(preset),
                  let counts, fullMatch("^[0-9]{1,2},[0-9]{1,2},[0-9]{1,2},[1-3],[0-9]{1,2},[0-9]{1,2},[0-9]{1,2},[0-9]{1,2},[0-9]{1,2},[0-9]{1,2},[0-9]{1,2}$", counts) else {
                throw OnlineError.invalidRoomConfiguration
            }
        }
        return LobbyConfig(transicionSeg: transition, nocheSeg: night, discusionSeg: discussion,
                           votacionSeg: voting, revelarRolesAlMorir: reveal, votosIndividuales: individual,
                           presetRoles: preset ?? "RECOMMENDED", roles: counts ?? LobbyConfig().roles)
    }

    static func lobbyConfigFields(_ config: LobbyConfig) throws -> [String: Any] {
        let fields: [String: Any] = [
            "transicionSeg": config.transicionSeg, "nocheSeg": config.nocheSeg,
            "discusionSeg": config.discusionSeg, "votacionSeg": config.votacionSeg,
            "revelarRolesAlMorir": config.revelarRolesAlMorir, "votosIndividuales": config.votosIndividuales,
            "presetRoles": config.presetRoles, "roles": config.roles
        ]
        _ = try lobbyConfig(fields)
        return fields
    }

    static func roomPhase(_ data: [String: Any]) throws -> RoomPhase {
        switch data["estado"] as? String {
        case "esperando": return .waiting
        case "en_juego":
            guard let initial = data["partidaInicial"] as? [String: Any],
                  let matchId = initial["matchId"] as? String, !matchId.isEmpty else {
                throw OnlineError.incompatibleRoom
            }
            return .inGame(matchId: matchId)
        case "abandonada": return .closed(.abandoned)
        case "finalizada": return .closed(.finished)
        default: throw OnlineError.incompatibleRoom
        }
    }

}
