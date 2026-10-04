import Foundation

@main enum OnlineContractsChecks {
    struct Failure: Error { let message: String }

    static func check(_ condition: @autoclosure () throws -> Bool, _ message: String) throws {
        guard try condition() else { throw Failure(message: message) }
    }

    static func rejects(_ expected: OnlineError, _ operation: () throws -> Void) throws {
        do { try operation() } catch let error as OnlineError {
            try check(error == expected, "Expected \(expected), got \(error)")
            return
        }
        throw Failure(message: "Expected rejection: \(expected)")
    }

    static func main() throws {
        // Cross-language golden values, including surrogate pairs and Int32.min overflow.
        let names = ["owner": "Chamuyero 1915", "other": "Forastero 5776",
                     "uid_A😀": "Metepatas 4259", "zzzzzz": "Careta 4664",
                     "polygenelubricants": "Don Nadie 3648"]
        for (uid, expected) in names {
            try check(OnlineContract.guestName(uid: uid) == expected, "Kotlin guest name mismatch")
        }
        try check(OnlineContract.guestName(uid: "owner", selectedAlias: "Mufa") == "Mufa 1915", "Selected alias")
        try check(OnlineContract.guestName(uid: "owner", selectedAlias: "arbitrary") == names["owner"], "Closed alias list")
        try check(OnlineContract.publicId("00042") == "00042", "Public ID must remain a string")
        try check(OnlineContract.publicId("999999999999") != nil, "12-digit ID")
        for invalid in ["", "#42", "1234567890123", "-1", " 42", "42\n", "٤٢"] {
            try check(OnlineContract.publicId(invalid) == nil, "Invalid public ID: \(invalid)")
        }
        try check(try OnlineContract.roomCode(" abc234 ") == "ABC234", "Code normalization")
        for code in ["ABC23", "ABC2345", "ABC2I4", "ABC2O4", "ABC201", "ABC 23"] {
            try rejects(.invalidCode) { _ = try OnlineContract.roomCode(code) }
        }

        let newURL = "https://firebasestorage.googleapis.com/v0/b/traidores/o/photo.jpg?alt=media&token=token&v=hash"
        let oldURL = "https://lh3.googleusercontent.com/old-photo"
        let localURL = "http://127.0.0.1:9199/v0/b/demo/o/photo.jpg?token=token"
        try check(OnlineContract.photoURL(newURL)?.absoluteString == newURL, "Preserve tokens/revision")
        for invalid in ["content://gallery/file", "file:///tmp/photo.jpg", "https:///", localURL,
                        "https://example.com/" + String(repeating: "a", count: 1000)] {
            try check(OnlineContract.photoURL(invalid) == nil, "Unsafe or oversized photo URL")
        }
        let origin = URL(string: "http://127.0.0.1:9199")!
        try check(OnlineContract.photoURL(localURL, emulatorOrigin: origin) != nil, "Explicit emulator")
        try check(OnlineContract.photoURL(localURL, emulatorOrigin: URL(string: "http://10.0.2.2:9199")) == nil, "Emulator host must match")

        let wireProfile: [String: Any] = ["uidTemporal": "owner", "publicId": "00042",
            "nombrePerfil": "Nacho", "fotoPerfil": newURL, "fotoPlayGames": oldURL,
            "emotesPerfil": ["premium_mate"], "temaCosmeticoPerfil": "fire"]
        let recovered = try OnlineContract.profile(uid: "owner", data: wireProfile)
        try check(recovered.uid == "owner" && recovered.publicId == "00042", "Recovered identity")
        try check(recovered.avatarURL?.absoluteString == newURL, "Gallery photo takes precedence")
        try check(recovered.draft.emotesPerfil == ["premium_mate"] && recovered.draft.temaCosmeticoPerfil == "fire", "Preserve cosmetics")
        try rejects(.invalidProfile) { _ = try OnlineContract.profile(uid: "other", data: wireProfile) }
        var legacy = wireProfile
        legacy.removeValue(forKey: "fotoPerfil")
        try check(try OnlineContract.profile(uid: "owner", data: legacy).avatarURL?.absoluteString == oldURL, "Old clients remain readable")
        legacy.removeValue(forKey: "fotoPlayGames")
        try check(try OnlineContract.profile(uid: "owner", data: legacy).avatarURL == nil, "Illustrated fallback")

        let custom = LobbyConfig(transicionSeg: 3, nocheSeg: 30, discusionSeg: 60, votacionSeg: 30,
                                 revelarRolesAlMorir: true, votosIndividuales: false,
                                 presetRoles: "PERSONALIZADO", roles: "0,1,1,2,0,1,0,0,0,0,0")
        let fields = try OnlineContract.lobbyConfigFields(custom)
        try check(try OnlineContract.lobbyConfig(fields) == custom, "Round trip retains Android role composition")
        var invalid = custom
        invalid.roles = "0,1,1,0,0,1,0,0,0,0,0"
        try rejects(.invalidRoomConfiguration) { _ = try OnlineContract.lobbyConfigFields(invalid) }
        invalid = custom
        invalid.nocheSeg = 9
        try rejects(.invalidRoomConfiguration) { _ = try OnlineContract.lobbyConfigFields(invalid) }
        invalid = custom
        invalid.presetRoles = "UNKNOWN"
        try rejects(.invalidRoomConfiguration) { _ = try OnlineContract.lobbyConfigFields(invalid) }
        invalid = custom
        invalid.roles += "\n"
        try rejects(.invalidRoomConfiguration) { _ = try OnlineContract.lobbyConfigFields(invalid) }
        try check(try OnlineContract.roomPhase(["estado": "esperando"]) == .waiting, "Waiting phase")
        try check(try OnlineContract.roomPhase(["estado": "en_juego", "partidaInicial": ["matchId": "m1"]]) == .inGame(matchId: "m1"), "Match identity")
        try check(try OnlineContract.roomPhase(["estado": "finalizada"]) == .closed(.finished), "Finished phase")
        try rejects(.incompatibleRoom) { _ = try OnlineContract.roomPhase(["estado": "en_juego"]) }
        try rejects(.incompatibleRoom) { _ = try OnlineContract.roomPhase(["estado": "future_state"]) }
        print("Online contracts: Kotlin identity, codes, photo recovery, URLs, lobby composition and phases passed.")
    }
}
