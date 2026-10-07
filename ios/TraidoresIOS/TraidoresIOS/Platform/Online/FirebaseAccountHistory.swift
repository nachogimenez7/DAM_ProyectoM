import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation
import Observation
import TraidoresCore

/// Only confirmed server snapshots count as account history. Listeners live while
/// the Profile is visible and are invalidated immediately when Auth changes UID.
@MainActor @Observable
final class FirebaseAccountHistory: AccountHistoryService {
    private(set) var owner: String?
    private(set) var status: AccountHistoryStatus = .signedOut
    private(set) var matches = 0
    private(set) var wins = 0
    private(set) var entries: [AccountHistoryEntry] = []
    var processing: Bool { entries.contains { !$0.counted } || LocalAccountHistoryOutbox.pendingCount(uid: owner) > 0 }
    @ObservationIgnored private var listeners: [ListenerRegistration] = []
    @ObservationIgnored private var authListener: AuthStateDidChangeListenerHandle?
    @ObservationIgnored private var timeout: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var summaryReceived = false
    @ObservationIgnored private var entriesReceived = false

    func attach(uid: String) {
        detach()
        guard FirebaseSetup.configureIfNeeded(), let user = Auth.auth().currentUser,
              !user.isAnonymous, user.uid == uid else { return }
        owner = uid
        status = .loading
        Task { await LocalAccountHistoryOutbox.flush() }
        let token = generation
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor [weak self] in
                guard let self, self.generation == token else { return }
                if user?.uid != uid || user?.isAnonymous != false { self.detach() }
            }
        }
        let account = Firestore.firestore().collection("cuentas").document(uid)
        listeners.append(account.addSnapshotListener(includeMetadataChanges: true) { [weak self] snapshot, error in
            Task { @MainActor [weak self] in
                guard let self, self.accepts(uid, token) else { return }
                if let error { self.fail(error); return }
                guard let snapshot, !snapshot.metadata.isFromCache, !snapshot.metadata.hasPendingWrites else { return }
                let data = snapshot.data() ?? [:]
                guard !snapshot.exists || (data["schemaVersion"] as? Int == 1 &&
                    (data["partidas"] as? Int ?? -1) >= 0 &&
                    (data["victorias"] as? Int ?? -1) >= 0 &&
                    (data["victorias"] as? Int ?? 0) <= (data["partidas"] as? Int ?? 0)) else {
                    self.fail(OnlineError.server("No pudimos leer las estadísticas de tu cuenta.")); return
                }
                self.matches = data["partidas"] as? Int ?? 0
                self.wins = data["victorias"] as? Int ?? 0
                self.summaryReceived = true
                self.received()
            }
        })
        listeners.append(account.collection("historial").order(by: "finalizadaEn", descending: true).limit(to: 50)
            .addSnapshotListener(includeMetadataChanges: true) { [weak self] snapshot, error in
                Task { @MainActor [weak self] in
                    guard let self, self.accepts(uid, token) else { return }
                    if let error { self.fail(error); return }
                    guard let snapshot, !snapshot.metadata.isFromCache, !snapshot.metadata.hasPendingWrites else { return }
                    do {
                        self.entries = try snapshot.documents.map { try Self.entry(id: $0.documentID, uid: uid, data: $0.data()) }
                        self.entriesReceived = true
                        self.received()
                    } catch { self.fail(error) }
                }
            })
        timeout = Task { [weak self] in
            try? await Task.sleep(for: .seconds(12))
            guard !Task.isCancelled, let self, self.accepts(uid, token), self.status == .loading else { return }
            self.fail(OnlineError.offline)
        }
    }

    func retry() { if let owner { attach(uid: owner) } }

    func detach() {
        generation += 1
        listeners.forEach { $0.remove() }; listeners = []
        if let authListener { Auth.auth().removeStateDidChangeListener(authListener) }
        authListener = nil
        timeout?.cancel(); timeout = nil
        owner = nil; status = .signedOut
        matches = 0; wins = 0; entries = []
        summaryReceived = false; entriesReceived = false
    }

    private func accepts(_ uid: String, _ token: Int) -> Bool {
        generation == token && owner == uid && Auth.auth().currentUser?.uid == uid &&
            Auth.auth().currentUser?.isAnonymous == false
    }
    private func received() {
        if summaryReceived && entriesReceived { status = .ready; timeout?.cancel() }
    }
    private func fail(_ error: Error) {
        status = .failed(FirebaseAccountService.onlineError(error).message)
        summaryReceived = false; entriesReceived = false
        matches = 0; wins = 0; entries = []
    }
    static func entry(id: String, uid: String, data: [String: Any]) throws -> AccountHistoryEntry {
        guard data["schemaVersion"] as? Int == 1, data["uid"] as? String == uid,
              let origin = data["origen"] as? String, ["online", "local"].contains(origin),
              let key = data["matchKey"] as? String, id == AccountHistoryID.make(key),
              let map = data["mapName"] as? String, !map.isEmpty,
              let role = data["roleName"] as? String, !role.isEmpty,
              let won = data["won"] as? Bool, let count = data["participantCount"] as? Int, (2...30).contains(count),
              let date = data["finalizadaEn"] as? Timestamp else {
            throw OnlineError.server("No pudimos leer una partida de tu historial.")
        }
        return AccountHistoryEntry(id: id, mapName: map, roleName: role, won: won,
            participantCount: count, finishedAt: date.dateValue(), isOnline: origin == "online",
            counted: data["contabilizada"] as? Bool == true)
    }
}

import CryptoKit
enum AccountHistoryID {
    static func make(_ key: String) -> String {
        (key.hasPrefix("online:") ? "online_" : "local_") +
            SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Local results are client-declared, just like Android. The backend counts each
/// canonical ID once; online results remain exclusively server-written.
@MainActor
enum LocalAccountHistoryOutbox {
    private struct Record: Codable {
        let matchKey: String
        let fechaLocalMs: Int64
        let mapKey: String
        let mapName: String
        let roleKey: String
        let roleName: String
        let won: Bool
        let participantCount: Int
        let winner: String
    }
    private static var sending = Set<String>()
    private static func key(_ uid: String) -> String { "online.localHistoryOutbox.\(uid)" }
    private static func queue(_ uid: String) -> [Record] {
        UserDefaults.menuStore.data(forKey: key(uid)).flatMap { try? JSONDecoder().decode([Record].self, from: $0) } ?? []
    }
    static func pendingCount(uid: String?) -> Int { uid.map { queue($0).count } ?? 0 }
    static func record(game: ClassicGame, uid: String, matchKey: String, finishedAt: Date) {
        guard let winner = game.winner, winner != .neutral else { return }
        var records = queue(uid)
        guard !records.contains(where: { $0.matchKey == matchKey }) else { return }
        // humanWon covers the Desertor's final side and the Bufón's special victory.
        records.append(Record(matchKey: matchKey, fechaLocalMs: Int64(finishedAt.timeIntervalSince1970 * 1000),
            mapKey: game.map.rawValue, mapName: game.map.title, roleKey: game.human.role.rawValue,
            roleName: game.human.role.classicTitle(on: game.map), won: game.humanWon,
            participantCount: game.players.count, winner: winner.rawValue))
        UserDefaults.menuStore.set(try? JSONEncoder().encode(records), forKey: key(uid))
        Task { await flush() }
    }
    static func flush() async {
        guard FirebaseApp.app() != nil, let user = Auth.auth().currentUser, !user.isAnonymous,
              sending.insert(user.uid).inserted else { return }
        let uid = user.uid
        defer { sending.remove(uid) }
        for record in queue(uid) {
            guard Auth.auth().currentUser?.uid == uid else { return }
            do {
                let ref = Firestore.firestore().collection("cuentas").document(uid).collection("historial")
                    .document(AccountHistoryID.make(record.matchKey))
                let existing = try await ref.getDocument(source: .server)
                guard Auth.auth().currentUser?.uid == uid else { return }
                if !existing.exists {
                    try await ref.setData(["schemaVersion": 1, "uid": uid, "matchKey": record.matchKey, "origen": "local",
                        "roomId": "", "matchId": "", "fechaLocalMs": record.fechaLocalMs,
                        "mapKey": record.mapKey, "mapName": record.mapName, "roleKey": record.roleKey,
                        "roleName": record.roleName, "won": record.won, "participantCount": record.participantCount,
                        "winner": record.winner, "finalizadaEn": FieldValue.serverTimestamp()])
                }
                let remaining = queue(uid).filter { $0.matchKey != record.matchKey }
                UserDefaults.menuStore.set(try JSONEncoder().encode(remaining), forKey: key(uid))
            } catch { return } // Retain the exact record for the next authenticated retry.
        }
    }
}
