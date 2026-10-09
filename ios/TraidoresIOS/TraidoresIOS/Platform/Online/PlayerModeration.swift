import FirebaseFirestore
import Foundation

/// Android's `LocalMuteStore`: a mute lives only on this device. It keeps two keys when it can
/// (the Firebase uid and the public `#`) so it still works in the current room and when a known
/// account shows up again.
enum LocalMuteStore {
    private static let storageKey = "silenciados_locales"

    private static func keys(publicId: String?, uid: String) -> [String] {
        var keys: [String] = []
        if let publicId, !publicId.isEmpty, publicId.allSatisfy(\.isNumber) { keys.append("public:\(publicId)") }
        if !uid.isEmpty { keys.append("uid:\(uid)") }
        return keys
    }

    private static func saved() -> Set<String> {
        Set(UserDefaults.menuStore.stringArray(forKey: storageKey) ?? [])
    }

    static func isMuted(publicId: String?, uid: String) -> Bool {
        let current = saved()
        return keys(publicId: publicId, uid: uid).contains(where: current.contains)
    }

    /// Returns the new state.
    @discardableResult
    static func toggle(publicId: String?, uid: String) -> Bool {
        var current = saved()
        let target = keys(publicId: publicId, uid: uid)
        let willMute = !target.contains(where: current.contains)
        if willMute { current.formUnion(target) } else { current.subtract(target) }
        UserDefaults.menuStore.set(Array(current).sorted(), forKey: storageKey)
        return willMute
    }
}

/// The closed list of reasons the `reportes` rules accept (same as Android).
enum PlayerReportReason: String, CaseIterable, Identifiable {
    case toxicidad, trampa, spam, nombre_ofensivo, otro
    var id: String { rawValue }
    var title: String {
        switch self {
        case .toxicidad: "Toxicidad o insultos"
        case .trampa: "Trampa"
        case .spam: "Spam"
        case .nombre_ofensivo: "Nombre ofensivo"
        case .otro: "Otro"
        }
    }
}

/// Android's `PlayerModeration.submit`: one write to `reportes`, with the id the rules require
/// (`matchId_reporter_reported`). It never claims success when the server refused the report.
enum PlayerReports {
    enum Outcome: Equatable {
        case sent
        /// The server refused it. The most likely cause is a report already made for this player
        /// in this match (documents cannot be rewritten), but it could be a real rejection.
        case refused
        case failed
        case invalid
    }

    static func submit(roomId: String, matchId: String, reporterUid: String, reportedUid: String,
                       reportedName: String, reason: PlayerReportReason, detail: String) async -> Outcome {
        guard !roomId.isEmpty, (8...80).contains(matchId.count), !reportedUid.isEmpty,
              !reporterUid.isEmpty, reportedUid != reporterUid else { return .invalid }
        func safe(_ value: String) -> String {
            String(String(value.map { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "_" || $0 == "-") ? $0 : "_" }).prefix(80))
        }
        var data: [String: Any] = [
            "reportanteId": reporterUid,
            "reportadoId": reportedUid,
            "reportadoNombre": String(reportedName.prefix(18)),
            "roomId": String(roomId.prefix(80)),
            "matchId": String(matchId.prefix(80)),
            "motivo": reason.rawValue,
            "creadaEn": FieldValue.serverTimestamp()
        ]
        let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { data["detalle"] = String(trimmed.prefix(140)) }
        do {
            try await Firestore.firestore().collection("reportes")
                .document("\(safe(matchId))_\(safe(reporterUid))_\(safe(reportedUid))")
                .setData(data)
            return .sent
        } catch {
            let code = (error as NSError).code
            if (error as NSError).domain == FirestoreErrorDomain,
               code == FirestoreErrorCode.permissionDenied.rawValue || code == FirestoreErrorCode.alreadyExists.rawValue {
                return .refused
            }
            return .failed
        }
    }
}
