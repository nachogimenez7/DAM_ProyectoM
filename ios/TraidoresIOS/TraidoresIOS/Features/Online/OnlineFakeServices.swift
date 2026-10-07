#if DEBUG
import Foundation
import Observation
import UIKit

/// In-memory services for previews and UI tests (`-ui-testing-online <scenario>`, or
/// `-online-preview` for a manual look). Nothing here talks to Firebase, and debug builds are
/// the only ones that can open the online screens until Codex's real adapters exist.
enum FakeOnlineScenario: String, CaseIterable {
    /// Guest with two public rooms.
    case guest
    /// Registered account #7 with a public profile.
    case registered
    /// The first request of each kind fails offline; retrying works.
    case offline
    /// The account has a global ban.
    case suspended
    /// No public rooms.
    case empty

    /// Launching once with `-online-preview` keeps the preview on for that device's debug
    /// install, so it can be tried from the home screen; `-online-preview-off` turns it off.
    @MainActor static var requested: FakeOnlineScenario? {
        let defaults = UserDefaults.standard
        if let name = defaults.string(forKey: "ui-testing-online") { return FakeOnlineScenario(rawValue: name) ?? .guest }
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-ui-testing") { return nil }
        if arguments.contains("-online-preview-off") { defaults.removeObject(forKey: previewKey) }
        if arguments.contains("-online-preview") { defaults.set(true, forKey: previewKey) }
        return defaults.bool(forKey: previewKey) ? .guest : nil
    }

    private static let previewKey = "debug.onlinePreview"

    @MainActor func makeServices() -> OnlineServices {
        let world = FakeOnlineWorld(scenario: self)
        return OnlineServices(account: FakeAccountService(world: world), profile: FakeProfileService(world: world),
                              directory: FakeDirectoryService(world: world), room: FakeRoomSession(world: world))
    }
}

/// Shared state so the fake account, profile and rooms agree on who the player is.
@MainActor final class FakeOnlineWorld {
    let scenario: FakeOnlineScenario
    var identity: OnlineIdentity?
    var profile: PublicProfile?
    var rooms: [String: RoomSnapshot] = [:]
    private var failedOnce: Set<String> = []

    init(scenario: FakeOnlineScenario) {
        self.scenario = scenario
        rooms = Dictionary(uniqueKeysWithValues: Self.publicRooms(scenario).map { ($0.id, $0) })
    }

    /// Offline scenario: the first call of each operation fails, the retry succeeds.
    func failOnceIfOffline(_ operation: String) throws {
        guard scenario == .offline, failedOnce.insert(operation).inserted else { return }
        throw OnlineError.offline
    }

    func pause() async {
        try? await Task.sleep(for: .milliseconds(350))
    }

    static let guestUid = "fake-guest-uid"
    static let accountUid = "fake-account-uid"

    func guestIdentity() -> OnlineIdentity {
        OnlineIdentity(uid: Self.guestUid, publicId: nil, isRegistered: false,
                       displayName: OnlineContract.guestName(uid: Self.guestUid))
    }

    func registeredProfile(publicId: String = "7") -> PublicProfile {
        PublicProfile(uid: Self.accountUid, publicId: publicId, nombrePerfil: "Lucía", nombreSala: "Lucía #\(publicId)",
                      bioPerfil: "No fui yo. Esta vez.", avatarPerfil: "pampa_medico", bannerPerfil: "pampa",
                      rolFavoritoPerfil: "medico", fotoPerfil: Self.samplePhotoURL("rol_medico_gaucho"))
    }

    /// A photo on disk stands in for a Storage download URL, so tests never touch the network.
    static func samplePhotoURL(_ asset: String) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("fake-online-\(asset).png")
        if !FileManager.default.fileExists(atPath: url.path) {
            guard let data = UIImage(named: asset)?.pngData() else { return nil }
            try? data.write(to: url)
        }
        return url
    }

    func me() -> RoomPlayer? {
        guard let identity else { return nil }
        let name = profile?.nombreSala ?? identity.displayName
        return RoomPlayer(id: identity.uid, order: 0, nombrePerfil: profile?.nombrePerfil ?? identity.displayName,
                          nombreSala: name, publicId: identity.publicId, fotoPerfil: profile?.fotoPerfil,
                          avatarPerfil: profile?.avatarPerfil ?? "pampa_aldeano", isHost: false, isReady: false,
                          isConnected: true, mapVote: nil)
    }

    private static func publicRooms(_ scenario: FakeOnlineScenario) -> [RoomSnapshot] {
        guard scenario != .empty else { return [] }
        let others = [
            RoomPlayer(id: "fake-host-1", order: 0, nombrePerfil: "Federico", nombreSala: "Federico #12", publicId: "12",
                       fotoPerfil: samplePhotoURL("rol_detective_gaucho"), avatarPerfil: "pampa_policia", isHost: true,
                       isReady: true, isConnected: true, mapVote: "pampa"),
            RoomPlayer(id: "fake-p2", order: 1, nombrePerfil: "Mala Onda", nombreSala: "Mala Onda 4821", publicId: nil,
                       fotoPerfil: nil, avatarPerfil: "grecia_oraculo", isHost: false, isReady: true,
                       isConnected: true, mapVote: "grecia"),
            // Same display name as the registered fake player: identity is the UID, so this
            // player must keep its own portrait.
            RoomPlayer(id: "fake-p3", order: 2, nombrePerfil: "Lucía", nombreSala: "Lucía #31", publicId: "31",
                       fotoPerfil: nil, avatarPerfil: "medieval_bufon", isHost: false, isReady: false,
                       isConnected: false, mapVote: nil)
        ]
        let config = LobbyConfig()
        return [
            RoomSnapshot(id: "room-pampa", code: "SALA23", name: "Pulpería de Federico", mapKey: "pampa", expected: 6,
                         isPublic: true, accountsOnly: false, hostId: "fake-host-1", activeHostId: "fake-host-1",
                         config: config, players: others, phase: .waiting),
            RoomSnapshot(id: "room-medieval", code: "CUENTA", name: "Castillo de cuentas", mapKey: "medieval", expected: 8,
                         isPublic: true, accountsOnly: true, hostId: "fake-host-1", activeHostId: "fake-host-1",
                         config: config, players: Array(others.prefix(2)), phase: .waiting),
            RoomSnapshot(id: "room-full", code: "LLENA2", name: "Llena", mapKey: "grecia", expected: 3, isPublic: false,
                         accountsOnly: false, hostId: "fake-host-1", activeHostId: "fake-host-1", config: config,
                         players: others, phase: .waiting),
            RoomSnapshot(id: "room-started", code: "JUGAN2", name: "Ya empezó", mapKey: "pampa", expected: 5,
                         isPublic: false, accountsOnly: false, hostId: "fake-host-1", activeHostId: "fake-host-1",
                         config: config, players: others, phase: .inGame(matchId: "fake-match"))
        ]
    }
}

@MainActor @Observable final class FakeAccountService: OnlineAccountService {
    private(set) var access: OnlineAccessState = .signedOut
    @ObservationIgnored private let world: FakeOnlineWorld

    init(world: FakeOnlineWorld) { self.world = world }

    func enterAsGuest() async {
        access = .connecting
        await world.pause()
        switch world.scenario {
        case .suspended:
            access = .suspended(reason: "Incumplimiento de las reglas de convivencia.")
        case .registered:
            world.profile = world.registeredProfile()
            let identity = OnlineIdentity(uid: FakeOnlineWorld.accountUid, publicId: "7", isRegistered: true,
                                          displayName: "Lucía")
            world.identity = identity
            access = .ready(identity)
        default:
            do {
                try world.failOnceIfOffline("access")
                let identity = world.guestIdentity()
                world.identity = identity
                access = .ready(identity)
            } catch {
                access = .failed(error as? OnlineError ?? .offline)
            }
        }
    }

    func selectGuestAlias(_ alias: String) async throws {
        guard case .ready(let identity) = access, !identity.isRegistered else { throw OnlineError.accountRequired }
        let updated = OnlineIdentity(uid: identity.uid, publicId: nil, isRegistered: false,
                                     displayName: OnlineContract.guestName(uid: identity.uid, selectedAlias: alias))
        world.identity = updated
        access = .ready(updated)
    }

    func linkAccount(email: String, password: String) async throws {
        await world.pause()
        try validate(email: email, password: password)
        // Like Android's `linkOrSignIn`: an email that already has an account enters it and
        // recovers its number and profile instead of linking.
        if email.lowercased() == "usado@traidores.test" { return try await signIn(email: email, password: password) }
        // Linking keeps the guest UID and gets a fresh public number.
        guard case .ready(let guest) = access else { throw OnlineError.sessionExpired }
        var profile = world.registeredProfile(publicId: "8")
        profile = PublicProfile(uid: guest.uid, publicId: "8", nombrePerfil: "Jugador", nombreSala: "Jugador #8",
                                bioPerfil: "", avatarPerfil: profile.avatarPerfil, bannerPerfil: "pampa",
                                rolFavoritoPerfil: "aldeano", fotoPerfil: nil)
        world.profile = profile
        let identity = OnlineIdentity(uid: guest.uid, publicId: "8", isRegistered: true, displayName: "Jugador")
        world.identity = identity
        access = .ready(identity)
    }

    func signIn(email: String, password: String) async throws {
        await world.pause()
        try validate(email: email, password: password)
        if password == "incorrecta" { throw OnlineError.invalidCredentials }
        // An existing account recovers its own UID and profile before announcing ready.
        world.profile = world.registeredProfile(publicId: "3")
        let identity = OnlineIdentity(uid: FakeOnlineWorld.accountUid, publicId: "3", isRegistered: true,
                                      displayName: "Lucía")
        world.identity = identity
        access = .ready(identity)
    }

    func signOut() async throws {
        await world.pause()
        world.profile = nil
        world.identity = nil
        access = .signedOut
    }

    private func validate(email: String, password: String) throws {
        guard email.contains("@"), email.contains(".") else { throw OnlineError.invalidCredentials }
        guard password.count >= 6 else { throw OnlineError.weakPassword }
    }
}

@MainActor @Observable final class FakeProfileService: PublicProfileService {
    private(set) var photoSync: PhotoSyncState = .idle
    private(set) var pendingPhoto: PendingProfilePhoto?
    @ObservationIgnored private let world: FakeOnlineWorld
    private var revision = 0

    init(world: FakeOnlineWorld) { self.world = world }

    var profile: PublicProfile? {
        _ = revision
        return world.profile
    }

    func refresh() async throws {
        await world.pause()
        revision += 1
    }

    func save(_ draft: PublicProfileDraft) async throws {
        guard var profile = world.profile else { throw OnlineError.accountRequired }
        let name = draft.nombrePerfil.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 18, draft.bioPerfil.count <= 40 else { throw OnlineError.invalidProfile }
        await world.pause()
        try world.failOnceIfOffline("save")
        profile.nombrePerfil = name
        profile.nombreSala = profile.publicId.map { "\(name) #\($0)" } ?? name
        profile.bioPerfil = draft.bioPerfil
        profile.avatarPerfil = draft.avatarPerfil
        world.profile = profile
        revision += 1
    }

    func setPhoto(imageData: Data) async throws {
        guard world.profile != nil else { throw OnlineError.accountRequired }
        guard UIImage(data: imageData) != nil else { throw OnlineError.invalidImage }
        pendingPhoto = .image(imageData)
        photoSync = .pending
        try await publishPending()
    }

    func retryPhotoSync() async throws {
        try await publishPending()
    }

    func removePhoto() async throws {
        guard world.profile != nil else { throw OnlineError.accountRequired }
        pendingPhoto = .removal
        try await publishPending()
    }

    private func publishPending() async throws {
        guard let pending = pendingPhoto else { return }
        photoSync = .uploading
        await world.pause()
        do {
            try world.failOnceIfOffline("photo")
        } catch {
            photoSync = .failed(.offline)
            throw error
        }
        switch pending {
        case .image(let data):
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("fake-online-upload-\(UUID().uuidString).jpg")
            try? data.write(to: url)
            world.profile?.fotoPerfil = url
            photoSync = .published(url)
        case .removal:
            world.profile?.fotoPerfil = nil
            photoSync = .idle
        }
        pendingPhoto = nil
        revision += 1
    }
}

@MainActor final class FakeDirectoryService: RoomDirectoryService {
    private let world: FakeOnlineWorld

    init(world: FakeOnlineWorld) { self.world = world }

    func publicRooms() async throws -> [RoomSummary] {
        await world.pause()
        try world.failOnceIfOffline("rooms")
        return world.rooms.values.filter { $0.isPublic && $0.phase == .waiting }
            .sorted { $0.name < $1.name }
            .map { room in
                RoomSummary(id: room.id, code: room.code, name: room.name, mapKey: room.mapKey,
                            hostName: room.players.first(where: { $0.id == room.hostId })?.nombreSala ?? "",
                            current: room.players.count, expected: room.expected, accountsOnly: room.accountsOnly)
            }
    }

    func create(_ draft: RoomDraft) async throws -> String {
        guard case .some(let identity) = world.identity, identity.isRegistered else { throw OnlineError.accountRequired }
        await world.pause()
        try world.failOnceIfOffline("create")
        let id = "room-created-\(world.rooms.count)"
        world.rooms[id] = RoomSnapshot(id: id, code: "NUEV23", name: draft.name, mapKey: draft.mapKey,
                                       expected: draft.expected, isPublic: draft.isPublic, accountsOnly: draft.accountsOnly,
                                       hostId: identity.uid, activeHostId: identity.uid, config: draft.config,
                                       players: [], phase: .waiting)
        return id
    }

    func join(code: String) async throws -> String {
        let normalized = try OnlineContract.roomCode(code)
        await world.pause()
        try world.failOnceIfOffline("join")
        guard let room = world.rooms.values.first(where: { $0.code == normalized }) else { throw OnlineError.roomNotFound }
        guard room.phase == .waiting else { throw OnlineError.roomAlreadyStarted }
        guard room.players.count < room.expected else { throw OnlineError.roomFull }
        if room.accountsOnly, world.identity?.isRegistered != true { throw OnlineError.accountRequired }
        return room.id
    }

    func recoverableRoom() async throws -> RoomSummary? { nil }
}

@MainActor @Observable final class FakeRoomSession: RoomSessionService {
    private(set) var snapshot: RoomSnapshot?
    private(set) var connection: ConnectionState = .connecting
    @ObservationIgnored private let world: FakeOnlineWorld
    @ObservationIgnored private var reconnectTask: Task<Void, Never>?

    init(world: FakeOnlineWorld) { self.world = world }

    /// Matches Codex's rule for this block: real starts stay off until a gameplay authority exists.
    var startAvailability: MatchStartAvailability { .unavailable(.onlineGameplay) }

    func attach(roomId: String) async throws {
        connection = .connecting
        await world.pause()
        guard var room = world.rooms[roomId], var me = world.me() else { throw OnlineError.roomNotFound }
        me = RoomPlayer(id: me.id, order: room.players.count, nombrePerfil: me.nombrePerfil, nombreSala: me.nombreSala,
                        publicId: me.publicId, fotoPerfil: me.fotoPerfil, avatarPerfil: me.avatarPerfil,
                        isHost: room.hostId == me.id, isReady: false, isConnected: true, mapVote: nil)
        room = room.replacingPlayers(room.players.filter { $0.id != me.id } + [me])
        world.rooms[roomId] = room
        snapshot = room
        connection = .live
        if world.scenario == .offline {
            // Shows the reconnection banner once, then recovers.
            reconnectTask = Task { [weak self] in
                try? await Task.sleep(for: .milliseconds(800))
                self?.connection = .reconnecting
                try? await Task.sleep(for: .milliseconds(2500))
                self?.connection = .live
            }
        }
    }

    func detach() {
        reconnectTask?.cancel()
        snapshot = nil
        connection = .connecting
    }

    func setReady(_ ready: Bool) async throws {
        try await updateMe { $0.with(isReady: ready) }
    }

    func voteMap(_ key: String) async throws {
        guard OnlineContract.mapKeys.contains(key) else { throw OnlineError.invalidRoomConfiguration }
        try await updateMe { $0.with(mapVote: key) }
    }

    func updateConfig(_ config: LobbyConfig) async throws {
        guard let room = snapshot, room.hostId == world.identity?.uid else { throw OnlineError.permissionDenied }
        _ = try OnlineContract.lobbyConfigFields(config)
        await world.pause()
        let updated = RoomSnapshot(id: room.id, code: room.code, name: room.name, mapKey: room.mapKey,
                                   expected: room.expected, isPublic: room.isPublic, accountsOnly: room.accountsOnly,
                                   hostId: room.hostId, activeHostId: room.activeHostId, config: config,
                                   players: room.players, phase: room.phase)
        world.rooms[room.id] = updated
        snapshot = updated
    }

    func start(hostTieBreakChoice: String?) async throws -> MatchStartResult {
        throw OnlineError.featureUnavailable(.onlineGameplay)
    }

    func leave() async throws {
        guard let room = snapshot, let uid = world.identity?.uid else { return }
        await world.pause()
        try world.failOnceIfOffline("leave")
        world.rooms[room.id] = room.replacingPlayers(room.players.filter { $0.id != uid })
        detach()
    }

    private func updateMe(_ change: (RoomPlayer) -> RoomPlayer) async throws {
        guard let room = snapshot, let uid = world.identity?.uid else { throw OnlineError.roomNotFound }
        await world.pause()
        let updated = room.replacingPlayers(room.players.map { $0.id == uid ? change($0) : $0 })
        world.rooms[room.id] = updated
        snapshot = updated
    }
}

private extension RoomSnapshot {
    func replacingPlayers(_ players: [RoomPlayer]) -> RoomSnapshot {
        RoomSnapshot(id: id, code: code, name: name, mapKey: mapKey, expected: expected, isPublic: isPublic,
                     accountsOnly: accountsOnly, hostId: hostId, activeHostId: activeHostId, config: config,
                     players: players, phase: phase)
    }
}

private extension RoomPlayer {
    func with(isReady: Bool? = nil, mapVote: String? = nil) -> RoomPlayer {
        RoomPlayer(id: id, order: order, nombrePerfil: nombrePerfil, nombreSala: nombreSala, publicId: publicId,
                   fotoPerfil: fotoPerfil, avatarPerfil: avatarPerfil, isHost: self.isHost,
                   isReady: isReady ?? self.isReady, isConnected: isConnected, mapVote: mapVote ?? self.mapVote,
                   fotoPlayGames: fotoPlayGames)
    }
}
#endif
