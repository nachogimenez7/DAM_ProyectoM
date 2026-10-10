import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import Foundation
import Observation
import TraidoresCore

/// Real waiting rooms in Firestore, with the same documents Android writes
/// (`OnlineRoomFirestore.createRoom`, `LobbyBrowserActivity` join, `LobbyActivity` ready/leave).
/// iOS only creates and joins server-authority rooms (`protocolVersion = 3`): the server
/// deals, resolves and declares the winner; no phone arbitrates (`puedeArbitrar = false`).
@MainActor @Observable
final class FirebaseRooms: RoomDirectoryService, RoomSessionService {
    private(set) var snapshot: RoomSnapshot?
    private(set) var connection: ConnectionState = .connecting

    @ObservationIgnored private let account: any OnlineAccountService
    @ObservationIgnored private let profiles: any PublicProfileService
    @ObservationIgnored private var roomListener: ListenerRegistration?
    @ObservationIgnored private var playersListener: ListenerRegistration?
    @ObservationIgnored private var roomData: [String: Any]?
    @ObservationIgnored private var playerData: [String: [String: Any]] = [:]
    @ObservationIgnored private var attachedRoomId: String?
    @ObservationIgnored private var everLive = false

    static let protocolVersion = 3
    private static let roomsCollection = "partidas"
    private static let codesCollection = "codigosSala"
    private static let playersCollection = "jugadores"
    private static let browserFreshness: TimeInterval = 30 * 60
    private static let recoveryKey = "online.serverRoom.recovery"

    init(account: any OnlineAccountService, profiles: any PublicProfileService) {
        self.account = account
        self.profiles = profiles
    }

    private var db: Firestore { Firestore.firestore() }

    // MARK: Directory

    func publicRooms() async throws -> [RoomSummary] {
        try ready()
        let since = Timestamp(date: Date().addingTimeInterval(-Self.browserFreshness))
        let query = db.collection(Self.roomsCollection)
            .whereField("estado", isEqualTo: "esperando")
            .whereField("visibilidad", isEqualTo: "publica")
            .whereField("actualizadaEn", isGreaterThan: since)
            .order(by: "actualizadaEn", descending: true)
            .limit(to: 30)
        let result = try await perform { try await query.getDocuments() }
        // This client only plays server-authority rooms; older rooms stay on Android.
        return result.documents.compactMap { document in
            let data = document.data()
            guard (data["protocolVersion"] as? Int) == Self.protocolVersion else { return nil }
            return Self.summary(id: document.documentID, data: data)
        }
        .sorted { ($0.current, $1.name) > ($1.current, $0.name) }
    }

    func create(_ draft: RoomDraft) async throws -> String {
        let me = try ready()
        guard me.isRegistered, let profile = profiles.profile, profile.uid == me.uid else {
            throw OnlineError.accountRequired
        }
        let map = GameMap(onlineKey: draft.mapKey)
        let testRoom = Self.testRoomRequested
        let minimum = testRoom ? 3 : 5
        let expected = min(max(draft.expected, minimum), 15)
        let name = Self.memberName(me, profile: profile)
        let config = try OnlineContract.lobbyConfigFields(draft.config)
        // A code collision makes the `codigosSala` create fail; retry with another code.
        for _ in 0..<3 {
            let room = db.collection(Self.roomsCollection).document()
            let code = Self.generateCode()
            let suffix = String(format: "%04d", Int(Date().timeIntervalSince1970 * 1000) % 10_000)
            let trimmed = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            let roomName = trimmed.isEmpty ? "Sala de \(name) \(suffix)" : String(trimmed.prefix(40))
            let roomFields: [String: Any] = [
                "nombre": roomName, "codigoSala": code, "estado": "esperando",
                "mapa": map.rawValue, "mapaNombre": map.title,
                "hostId": me.uid, "hostNombre": name, "hostActivoId": me.uid, "hostVersion": 0,
                "partidaInicialCreada": false, "limpiezaPendiente": false,
                "jugadoresEsperados": expected, "maxJugadores": expected, "jugadoresActuales": 1,
                "modoPrueba": testRoom, "visibilidad": draft.isPublic ? "publica" : "privada",
                "soloCuentas": draft.accountsOnly, "configLobby": config, "origen": "ios",
                "protocolVersion": Self.protocolVersion,
                "creadaEn": FieldValue.serverTimestamp(), "actualizadaEn": FieldValue.serverTimestamp()
            ]
            var host = Self.memberFields(me, profile: profile, isHost: true, order: 0)
            host["unidoEn"] = FieldValue.serverTimestamp()
            let batch = db.batch()
            batch.setData(roomFields, forDocument: room)
            batch.setData(host, forDocument: room.collection(Self.playersCollection).document(me.uid))
            batch.setData(["partidaId": room.documentID, "codigoSala": code, "hostId": me.uid,
                           "creadaEn": FieldValue.serverTimestamp()],
                          forDocument: db.collection(Self.codesCollection).document(code))
            do {
                try await batch.commit()
                remember(roomId: room.documentID, uid: me.uid)
                return room.documentID
            } catch {
                let ns = error as NSError
                if ns.domain == FirestoreErrorDomain, ns.code == FirestoreErrorCode.permissionDenied.rawValue,
                   (try? await db.collection(Self.codesCollection).document(code).getDocument())?.exists == true {
                    continue
                }
                throw FirebaseAccountService.onlineError(error)
            }
        }
        throw OnlineError.server("No se pudo generar un código de sala. Probá otra vez.")
    }

    func join(code raw: String) async throws -> String {
        let me = try ready()
        let code = try OnlineContract.roomCode(raw)
        let lookup = try await perform { try await self.db.collection(Self.codesCollection).document(code).getDocument() }
        guard let roomId = lookup.data()?["partidaId"] as? String, !roomId.isEmpty else { throw OnlineError.roomNotFound }
        let profile = me.isRegistered ? profiles.profile : nil
        let fields = Self.memberFields(me, profile: profile, isHost: false, order: 0)
        let room = db.collection(Self.roomsCollection).document(roomId)
        let player = room.collection(Self.playersCollection).document(me.uid)
        try await transaction { transaction in
            let roomSnapshot = try transaction.getDocument(room)
            guard roomSnapshot.exists, let data = roomSnapshot.data(), data["codigoSala"] as? String == code else {
                throw OnlineError.roomNotFound
            }
            guard (data["protocolVersion"] as? Int) == Self.protocolVersion else { throw OnlineError.incompatibleRoom }
            guard data["cleanupState"] as? String != "deleting", data["estado"] as? String == "esperando",
                  data["limpiezaPendiente"] as? Bool != true else { throw OnlineError.roomAlreadyStarted }
            if data["soloCuentas"] as? Bool == true, !me.isRegistered { throw OnlineError.accountRequired }
            let existing = try transaction.getDocument(player)
            let wasActive = existing.exists && existing.data()?["activoEnPartida"] as? Bool != false
            let current = data["jugadoresActuales"] as? Int ?? 0
            let limit = data["jugadoresEsperados"] as? Int ?? data["maxJugadores"] as? Int ?? 15
            if !wasActive, current >= limit { throw OnlineError.roomFull }
            var update = fields
            update["esHost"] = data["hostId"] as? String == me.uid
            if wasActive {
                update.removeValue(forKey: "orden")
                transaction.updateData(update, forDocument: player)
            } else {
                update["orden"] = current
                update["unidoEn"] = FieldValue.serverTimestamp()
                if existing.exists {
                    if profile == nil { update["publicId"] = FieldValue.delete() }
                    transaction.updateData(update, forDocument: player)
                } else {
                    transaction.setData(update, forDocument: player)
                }
                transaction.updateData(["jugadoresActuales": FieldValue.increment(Int64(1)),
                                        "actualizadaEn": FieldValue.serverTimestamp()], forDocument: room)
            }
        }
        remember(roomId: roomId, uid: me.uid)
        return roomId
    }

    func recoverableRoom() async throws -> RoomSummary? {
        guard case .ready(let me) = account.access,
              let saved = UserDefaults.standard.dictionary(forKey: Self.recoveryKey) as? [String: String],
              saved["uid"] == me.uid, let roomId = saved["roomId"] else { return nil }
        let room = db.collection(Self.roomsCollection).document(roomId)
        guard let data = try? await room.getDocument().data(),
              let player = try? await room.collection(Self.playersCollection).document(me.uid).getDocument().data(),
              player["activoEnPartida"] as? Bool != false,
              data["protocolVersion"] as? Int == Self.protocolVersion,
              ["esperando", "en_juego", "finalizada"].contains(data["estado"] as? String ?? "") else {
            forgetRecovery()
            return nil
        }
        return Self.summary(id: roomId, data: data)
    }

    // MARK: Session

    var startAvailability: MatchStartAvailability {
        guard let snapshot, case .waiting = snapshot.phase,
              (roomData?["protocolVersion"] as? Int) == Self.protocolVersion else {
            return .unavailable(.onlineGameplay)
        }
        let everyoneReady = snapshot.players.count == snapshot.expected && snapshot.players.allSatisfy(\.isReady)
        return everyoneReady ? .ready : .waitingForPlayers
    }

    func attach(roomId: String) async throws {
        let me = try ready()
        if attachedRoomId == roomId, roomListener != nil { return }
        detach()
        attachedRoomId = roomId
        connection = .connecting
        everLive = false
        let room = db.collection(Self.roomsCollection).document(roomId)
        roomListener = room.addSnapshotListener(includeMetadataChanges: true) { [weak self] document, error in
            Task { @MainActor [weak self] in
                guard let self, self.attachedRoomId == roomId else { return }
                if error != nil { self.connection = .lost; return }
                guard let document else { return }
                guard document.exists, let data = document.data() else {
                    self.roomData = nil
                    self.snapshot = nil
                    self.connection = .lost
                    self.forgetRecovery()
                    return
                }
                self.roomData = data
                self.updateConnection(fromCache: document.metadata.isFromCache)
                self.rebuild()
            }
        }
        playersListener = room.collection(Self.playersCollection)
            .addSnapshotListener(includeMetadataChanges: true) { [weak self] query, error in
                Task { @MainActor [weak self] in
                    guard let self, self.attachedRoomId == roomId else { return }
                    if error != nil { self.connection = .lost; return }
                    guard let query else { return }
                    self.playerData = Dictionary(uniqueKeysWithValues: query.documents.map { ($0.documentID, $0.data()) })
                    self.updateConnection(fromCache: query.metadata.isFromCache)
                    self.rebuild()
                }
            }
        // Presence in the waiting room. In-match connection comes from the server projection.
        try? await room.collection(Self.playersCollection).document(me.uid).updateData(Self.connectedFields())
    }

    func detach() {
        roomListener?.remove()
        playersListener?.remove()
        roomListener = nil
        playersListener = nil
        if let roomId = attachedRoomId, case .ready(let me) = account.access,
           case .waiting = snapshot?.phase, snapshot?.players.contains(where: { $0.id == me.uid }) == true {
            let player = db.collection(Self.roomsCollection).document(roomId).collection(Self.playersCollection).document(me.uid)
            player.updateData(["estado": "desconectado", "ultimaConexionLocal": Self.nowMs(),
                               "ultimaConexion": FieldValue.serverTimestamp()]) { _ in }
        }
        attachedRoomId = nil
        roomData = nil
        playerData = [:]
        snapshot = nil
        connection = .connecting
    }

    func setReady(_ isReady: Bool) async throws {
        let (me, roomId) = try attached()
        guard case .waiting = snapshot?.phase else { throw OnlineError.roomAlreadyStarted }
        var fields = Self.connectedFields()
        fields["listo"] = isReady
        let player = db.collection(Self.roomsCollection).document(roomId).collection(Self.playersCollection).document(me.uid)
        _ = try await perform { try await player.updateData(fields) }
    }

    /// Android's host picks the map and the server ignores votes (`resolveMap`), so the map
    /// panel is the host's selector here. Changing the map clears everybody's «listo».
    func voteMap(_ key: String) async throws {
        let (me, roomId) = try attached()
        guard let snapshot, snapshot.activeHostId == me.uid else {
            throw OnlineError.server("El mapa lo elige el anfitrión.")
        }
        guard case .waiting = snapshot.phase else { throw OnlineError.roomAlreadyStarted }
        let map = GameMap(onlineKey: key)
        guard map.rawValue != snapshot.mapKey else { return }
        let room = db.collection(Self.roomsCollection).document(roomId)
        let batch = db.batch()
        batch.updateData(["mapa": map.rawValue, "mapaNombre": map.title,
                          "actualizadaEn": FieldValue.serverTimestamp()], forDocument: room)
        for player in snapshot.players {
            batch.updateData(["listo": false], forDocument: room.collection(Self.playersCollection).document(player.id))
        }
        _ = try await perform { try await batch.commit() }
    }

    func updateConfig(_ config: LobbyConfig) async throws {
        let (me, roomId) = try attached()
        guard let snapshot, snapshot.activeHostId == me.uid else { throw OnlineError.permissionDenied }
        guard case .waiting = snapshot.phase else { throw OnlineError.roomAlreadyStarted }
        let fields = try OnlineContract.lobbyConfigFields(config)
        let room = db.collection(Self.roomsCollection).document(roomId)
        let batch = db.batch()
        batch.updateData(["configLobby": fields, "actualizadaEn": FieldValue.serverTimestamp()], forDocument: room)
        for player in snapshot.players where player.id != me.uid {
            batch.updateData(["listo": false], forDocument: room.collection(Self.playersCollection).document(player.id))
        }
        _ = try await perform { try await batch.commit() }
    }

    func start(hostTieBreakChoice: String?) async throws -> MatchStartResult {
        let (_, roomId) = try attached()
        guard startAvailability == .ready else { throw OnlineError.roomNotReady }
        let data = try await ServerMatchCalls.call("iniciarPartidaV3", ["roomId": roomId])
        guard let response = data as? [String: Any], let matchId = response["matchId"] as? String, !matchId.isEmpty else {
            throw OnlineError.server(nil)
        }
        let mapKey = response["mapKey"] as? String ?? snapshot?.mapKey ?? GameMap.pampa.rawValue
        return .started(matchId: matchId, mapKey: mapKey, alreadyStarted: response["status"] as? String == "already_started")
    }

    func leave() async throws {
        let (me, roomId) = try attached()
        let room = db.collection(Self.roomsCollection).document(roomId)
        switch snapshot?.phase {
        case .inGame(let matchId):
            // Leaving a started match is a server decision: elimination and a recorded defeat.
            _ = try await ServerMatchCalls.call("abandonarPartidaV3", ["roomId": roomId, "matchId": matchId])
        case .waiting:
            if roomData?["authorityMode"] as? String == "lobby", let matchId = roomData?["preparedMatchId"] as? String {
                _ = try await ServerMatchCalls.call("abandonarPartidaV3", ["roomId": roomId, "matchId": matchId])
            } else if snapshot?.activeHostId == me.uid {
                try await leaveAsHost(me: me, room: room)
            } else {
                try await transaction { transaction in
                    let roomSnapshot = try transaction.getDocument(room)
                    guard roomSnapshot.exists else { return }
                    let current = roomSnapshot.data()?["jugadoresActuales"] as? Int ?? 1
                    transaction.updateData(Self.departedFields(), forDocument: room.collection(Self.playersCollection).document(me.uid))
                    transaction.updateData(["jugadoresActuales": max(0, current - 1),
                                            "actualizadaEn": FieldValue.serverTimestamp()], forDocument: room)
                }
            }
        default:
            break
        }
        forgetRecovery()
        detach()
    }

    private func leaveAsHost(me: OnlineIdentity, room: DocumentReference) async throws {
        let others = snapshot?.players.filter { $0.id != me.uid } ?? []
        if others.isEmpty {
            let code = roomData?["codigoSala"] as? String
            let batch = db.batch()
            batch.deleteDocument(room.collection(Self.playersCollection).document(me.uid))
            if let code { batch.deleteDocument(db.collection(Self.codesCollection).document(code)) }
            batch.deleteDocument(room)
            _ = try await perform { try await batch.commit() }
            return
        }
        // Same handoff as Android `transferLobbyHost(exitAfterTransfer = true)`.
        guard let next = others.first(where: { $0.isConnected && $0.publicId != nil }) else {
            throw OnlineError.server("Para salir, la sala tiene que quedar con otro jugador con cuenta conectado.")
        }
        try await transaction { transaction in
            let roomSnapshot = try transaction.getDocument(room)
            guard roomSnapshot.exists, let data = roomSnapshot.data(), data["estado"] as? String == "esperando",
                  data["hostActivoId"] as? String == me.uid else { throw OnlineError.roomChanged }
            let candidate = try transaction.getDocument(room.collection(Self.playersCollection).document(next.id))
            guard candidate.data()?["activoEnPartida"] as? Bool != false else { throw OnlineError.roomChanged }
            let current = data["jugadoresActuales"] as? Int ?? 1
            transaction.updateData(["hostId": next.id, "hostNombre": String(next.nombrePerfil.prefix(18)), "hostActivoId": next.id,
                                    "hostVersion": FieldValue.increment(Int64(1)),
                                    "jugadoresActuales": max(1, current - 1),
                                    "actualizadaEn": FieldValue.serverTimestamp()], forDocument: room)
            var departed = Self.departedFields()
            departed["esHost"] = false
            transaction.updateData(departed, forDocument: room.collection(Self.playersCollection).document(me.uid))
            transaction.updateData(["esHost": true], forDocument: room.collection(Self.playersCollection).document(next.id))
        }
    }

    // MARK: Snapshot

    private func updateConnection(fromCache: Bool) {
        if fromCache {
            connection = everLive ? .reconnecting : .connecting
        } else {
            everLive = true
            connection = .live
        }
    }

    private func rebuild() {
        guard let roomId = attachedRoomId, let data = roomData else { return }
        let hostId = data["hostId"] as? String ?? ""
        let players = playerData.compactMap { id, fields -> RoomPlayer? in
            guard fields["activoEnPartida"] as? Bool != false else { return nil }
            let stored = fields["nombreSala"] as? String ?? fields["nombre"] as? String ?? "Jugador"
            return RoomPlayer(
                id: id, order: fields["orden"] as? Int ?? 99,
                nombrePerfil: fields["nombrePerfil"] as? String ?? stored, nombreSala: stored,
                publicId: OnlineContract.publicId(fields["publicId"] as? String),
                fotoPerfil: OnlineContract.photoURL(fields["fotoPerfil"] as? String, emulatorOrigin: FirebaseSetup.storageEmulatorOrigin),
                avatarPerfil: fields["avatarPerfil"] as? String ?? "aldeano",
                isHost: id == hostId, isReady: fields["listo"] as? Bool == true,
                isConnected: fields["estado"] as? String == "conectado",
                mapVote: fields["votoMapa"] as? String,
                fotoPlayGames: OnlineContract.photoURL(fields["fotoPlayGames"] as? String),
                canArbitrate: fields["puedeArbitrar"] as? Bool ?? true,
                bioPerfil: String((fields["bioPerfil"] as? String ?? "").prefix(40)),
                bannerPerfil: fields["bannerPerfil"] as? String,
                rolFavoritoPerfil: fields["rolFavoritoPerfil"] as? String,
                temaCosmeticoPerfil: fields["temaCosmeticoPerfil"] as? String ?? "classic",
                emotesPerfil: OnlineContract.publishableEmotes(fields["emotesPerfil"] as? [String] ?? []),
                estadisticas: OnlineContract.profileStats(fields["estadisticasPerfil"]))
        }
        .sorted { ($0.order, $0.id) < ($1.order, $1.id) }
        let phase: RoomPhase
        if data["protocolVersion"] as? Int != Self.protocolVersion {
            phase = .closed(.deleted)
        } else if data["estado"] as? String == "finalizada",
                  let initial = data["partidaInicial"] as? [String: Any], let matchId = initial["matchId"] as? String {
            // A relaunch after the server finished still opens the real result, until the
            // player voluntarily leaves or the server prepares the rematch.
            phase = .inGame(matchId: matchId)
        } else {
            do { phase = try OnlineContract.roomPhase(data) } catch { phase = .closed(.deleted) }
        }
        snapshot = RoomSnapshot(
            id: roomId, code: data["codigoSala"] as? String ?? "",
            name: data["nombre"] as? String ?? "Sala", mapKey: data["mapa"] as? String ?? GameMap.pampa.rawValue,
            expected: data["jugadoresEsperados"] as? Int ?? 5,
            isPublic: data["visibilidad"] as? String != "privada",
            accountsOnly: data["soloCuentas"] as? Bool == true,
            hostId: hostId, activeHostId: data["hostActivoId"] as? String ?? hostId,
            config: (try? OnlineContract.lobbyConfig(data["configLobby"] as? [String: Any] ?? [:])) ?? LobbyConfig(),
            players: players, phase: phase)
    }

    // MARK: Helpers

    @discardableResult
    private func ready() throws -> OnlineIdentity {
        guard FirebaseSetup.configureIfNeeded() else { throw OnlineError.featureUnavailable(.configuration) }
        guard case .ready(let identity) = account.access, Auth.auth().currentUser?.uid == identity.uid else {
            throw OnlineError.sessionExpired
        }
        return identity
    }

    private func attached() throws -> (OnlineIdentity, String) {
        let me = try ready()
        guard let roomId = attachedRoomId else { throw OnlineError.roomNotFound }
        return (me, roomId)
    }

    private func perform<T>(_ work: () async throws -> T) async throws -> T {
        do { return try await work() } catch { throw FirebaseAccountService.onlineError(error) }
    }

    private func transaction(_ body: @escaping (Transaction) throws -> Void) async throws {
        do {
            _ = try await db.runTransaction { transaction, errorPointer in
                do { try body(transaction) } catch { errorPointer?.pointee = Self.wrap(error) }
                return nil
            }
        } catch {
            throw Self.unwrap(error)
        }
    }

    // OnlineError cannot cross the Objective-C transaction boundary; carry it in userInfo.
    private nonisolated static func wrap(_ error: Error) -> NSError {
        if let error = error as? OnlineError {
            return NSError(domain: "TraidoresRoom", code: 1, userInfo: ["onlineError": error])
        }
        return error as NSError
    }

    private static func unwrap(_ error: Error) -> OnlineError {
        let ns = error as NSError
        if let wrapped = ns.userInfo["onlineError"] as? OnlineError { return wrapped }
        return FirebaseAccountService.onlineError(error)
    }

    private func remember(roomId: String, uid: String) {
        UserDefaults.standard.set(["roomId": roomId, "uid": uid], forKey: Self.recoveryKey)
    }

    private func forgetRecovery() {
        UserDefaults.standard.removeObject(forKey: Self.recoveryKey)
    }

    private static func summary(id: String, data: [String: Any]) -> RoomSummary? {
        guard let code = data["codigoSala"] as? String, let name = data["nombre"] as? String else { return nil }
        return RoomSummary(id: id, code: code, name: name, mapKey: data["mapa"] as? String ?? GameMap.pampa.rawValue,
                           hostName: data["hostNombre"] as? String ?? "",
                           current: data["jugadoresActuales"] as? Int ?? 0,
                           expected: data["jugadoresEsperados"] as? Int ?? 5,
                           accountsOnly: data["soloCuentas"] as? Bool == true)
    }

    private static func memberName(_ me: OnlineIdentity, profile: PublicProfile?) -> String {
        let raw = (profile?.nombrePerfil ?? me.displayName).trimmingCharacters(in: .whitespacesAndNewlines)
        let name = String(raw.prefix(18))
        return name.isEmpty ? "Jugador" : name
    }

    /// Android `PlayerPublicIdentity.publicProfileFields`: a guest publishes no `publicId`, no
    /// bio and no photo; registered players share their public profile.
    private static func memberFields(_ me: OnlineIdentity, profile: PublicProfile?, isHost: Bool, order: Int) -> [String: Any] {
        let name = memberName(me, profile: profile)
        var fields: [String: Any] = [
            "nombre": name, "nombrePerfil": name, "nombreSala": name,
            "bioPerfil": profile.map { String($0.bioPerfil.prefix(40)) } ?? "",
            "avatarPerfil": profile?.avatarPerfil ?? "aldeano",
            "bannerPerfil": profile?.bannerPerfil ?? "pampa",
            "rolFavoritoPerfil": profile?.rolFavoritoPerfil ?? "aldeano",
            "temaCosmeticoPerfil": profile?.temaCosmeticoPerfil ?? "classic",
            "esHost": isHost, "orden": order, "activoEnPartida": true, "uidTemporal": me.uid, "listo": false,
            "protocolVersion": protocolVersion, "puedeArbitrar": false
        ]
        if let profile, let publicId = profile.publicId {
            fields["publicId"] = publicId
            if let photo = profile.fotoPerfil?.absoluteString { fields["fotoPerfil"] = photo }
            // Registered players share their emotes and the statistics the backend already
            // confirmed (read from their public profile; clients cannot write them there).
            fields["emotesPerfil"] = OnlineContract.publishableEmotes(profile.emotesPerfil)
            if let stats = profile.estadisticas {
                let matches = min(max(stats.matches, 0), 1_000_000)
                fields["estadisticasPerfil"] = ["partidas": matches, "victorias": min(max(stats.wins, 0), matches)]
            }
        }
        return fields.merging(connectedFields()) { _, new in new }
    }

    private nonisolated static func connectedFields() -> [String: Any] {
        ["estado": "conectado", "ultimaConexionLocal": nowMs(), "ultimaConexion": FieldValue.serverTimestamp()]
    }

    private nonisolated static func departedFields() -> [String: Any] {
        ["activoEnPartida": false, "listo": false, "estado": "desconectado",
         "ultimaConexionLocal": nowMs(), "ultimaConexion": FieldValue.serverTimestamp()]
    }

    private nonisolated static func nowMs() -> Int { Int(Date().timeIntervalSince1970 * 1000) }

    private static func generateCode() -> String {
        let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<6).map { _ in alphabet.randomElement()! })
    }

    /// Debug-only: `-online-test-room YES` creates a three-player test room (`modoPrueba`),
    /// like Android's test mode, so two simulators plus an emulator can play a full match.
    private static var testRoomRequested: Bool {
        #if DEBUG
        UserDefaults.standard.bool(forKey: "online-test-room")
        #else
        false
        #endif
    }
}

/// Callables of the server authority (`functions/src/onlineGameFunctions.js`). Errors carry
/// `details.reason`, which becomes a readable `OnlineError`.
@MainActor
enum ServerMatchCalls {
    static let region = "southamerica-west1"

    static func call(_ name: String, _ payload: [String: Any]) async throws -> Any {
        guard FirebaseSetup.configureIfNeeded() else { throw OnlineError.featureUnavailable(.configuration) }
        do {
            return try await Functions.functions(region: region).httpsCallable(name).call(payload).data
        } catch let error as NSError where error.domain == FunctionsErrorDomain {
            let details = error.userInfo[FunctionsErrorDetailsKey] as? [String: Any]
            throw failure(code: FunctionsErrorCode(rawValue: error.code), reason: details?["reason"] as? String)
        } catch {
            throw FirebaseAccountService.onlineError(error)
        }
    }

    static func failure(code: FunctionsErrorCode?, reason: String?) -> OnlineError {
        switch reason {
        case "server-mode-unavailable": return .server("Este modo online todavía está en preparación.")
        case "incompatible-client": return .incompatibleRoom
        case "host-required": return .server("Solo el anfitrión puede iniciar.")
        case "not-ready", "players-not-ready", "player-count-mismatch": return .roomNotReady
        case "room-not-found": return .roomNotFound
        case "request-rate-limit", "too-many-actions": return .server("Esperá un momento antes de volver a intentar.")
        case "publication-pending": return .server("La sala se está preparando. Probá en unos segundos.")
        case "phase-closed", "stale-phase", "wrong-phase": return .roomChanged
        default: break
        }
        switch code {
        case .unauthenticated: return .sessionExpired
        case .permissionDenied: return .permissionDenied
        case .unavailable, .deadlineExceeded: return .offline
        case .notFound: return .roomNotFound
        default: return .server(nil)
        }
    }
}
