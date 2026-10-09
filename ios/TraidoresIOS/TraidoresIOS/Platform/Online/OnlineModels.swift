import Foundation

struct AccountHistoryEntry: Identifiable, Equatable {
    let id: String
    let mapName: String
    let roleName: String
    let won: Bool
    let participantCount: Int
    let finishedAt: Date
    let isOnline: Bool
    let counted: Bool
}
enum AccountHistoryStatus: Equatable { case signedOut, loading, ready, failed(String) }

// Presentation models shared by the Firebase adapters and the in-memory UI test services.
// They contain no Firebase types and never use a display name as player identity.
struct OnlineIdentity: Equatable, Sendable {
    let uid: String
    let publicId: String?
    let isRegistered: Bool
    let displayName: String
}

enum OnlineAccessState: Equatable, Sendable {
    case signedOut, connecting
    case ready(OnlineIdentity)
    case suspended(reason: String)
    case failed(OnlineError)
}

/// Account statistics shown in a profile. The backend maintains them; clients only read and echo them.
struct ProfileStats: Equatable, Sendable {
    let matches: Int
    let wins: Int
}

struct PublicProfile: Equatable, Sendable {
    let uid: String
    let publicId: String?
    var nombrePerfil: String
    var nombreSala: String
    var bioPerfil: String
    var avatarPerfil: String
    var bannerPerfil: String
    var rolFavoritoPerfil: String
    var fotoPerfil: URL?
    var fotoPlayGames: URL? = nil

    var emotesPerfil: [String] = []
    var temaCosmeticoPerfil = "classic"
    var estadisticas: ProfileStats? = nil

    var avatarURL: URL? { fotoPerfil ?? fotoPlayGames }

    var draft: PublicProfileDraft {
        PublicProfileDraft(nombrePerfil: nombrePerfil, bioPerfil: bioPerfil,
                           avatarPerfil: avatarPerfil, bannerPerfil: bannerPerfil,
                           rolFavoritoPerfil: rolFavoritoPerfil, emotesPerfil: emotesPerfil,
                           temaCosmeticoPerfil: temaCosmeticoPerfil)
    }
}

// Saving text/customization cannot change identity or overwrite an in-flight photo URL.
struct PublicProfileDraft: Equatable, Sendable {
    var nombrePerfil: String
    var bioPerfil: String
    var avatarPerfil: String
    var bannerPerfil: String
    var rolFavoritoPerfil: String
    var emotesPerfil: [String] = []
    var temaCosmeticoPerfil = "classic"
}

enum PhotoSyncState: Equatable, Sendable {
    case idle, pending, uploading
    case published(URL)
    case failed(OnlineError)
}

enum PendingProfilePhoto: Equatable, Sendable {
    case image(Data)
    case removal
}

struct RoomSummary: Identifiable, Equatable, Sendable {
    let id: String
    let code: String
    let name: String
    let mapKey: String
    let hostName: String
    let current: Int
    let expected: Int
    let accountsOnly: Bool
}

struct RoomDraft: Equatable, Sendable {
    var name: String
    var mapKey: String
    var expected: Int
    var isPublic: Bool
    var accountsOnly: Bool
    var config = LobbyConfig()
}

struct LobbyConfig: Equatable, Sendable {
    var transicionSeg = 4
    var nocheSeg = 40
    var discusionSeg = 120
    var votacionSeg = 20
    var revelarRolesAlMorir = false
    var votosIndividuales = true
    // Preserve Android's composition when only timers or visibility controls change.
    var presetRoles = "RECOMMENDED"
    var roles = "0,0,0,1,0,0,0,0,0,0,0"
}

struct RoomPlayer: Identifiable, Equatable, Sendable {
    let id: String
    let order: Int
    let nombrePerfil: String
    let nombreSala: String
    let publicId: String?
    let fotoPerfil: URL?
    let avatarPerfil: String
    let isHost: Bool
    let isReady: Bool
    let isConnected: Bool
    let mapVote: String?
    var fotoPlayGames: URL? = nil
    // Missing on legacy Android documents; new iOS clients explicitly publish false.
    var canArbitrate = true
    // Public profile as published in the member document (the same fields Android reads).
    var bioPerfil = ""
    var bannerPerfil: String? = nil
    var rolFavoritoPerfil: String? = nil
    var temaCosmeticoPerfil = "classic"
    var emotesPerfil: [String] = []
    var estadisticas: ProfileStats? = nil

    var avatarURL: URL? { fotoPerfil ?? fotoPlayGames }
}

enum RoomClosure: Equatable, Sendable { case abandoned, finished, deleted }

enum RoomPhase: Equatable, Sendable {
    case waiting
    // Local pending state, not a new Firestore value.
    case starting
    case inGame(matchId: String)
    case closed(RoomClosure)
}

struct RoomSnapshot: Equatable, Sendable {
    let id: String
    let code: String
    let name: String
    let mapKey: String
    let expected: Int
    let isPublic: Bool
    let accountsOnly: Bool
    let hostId: String
    let activeHostId: String
    let config: LobbyConfig
    let players: [RoomPlayer]
    let phase: RoomPhase
}

enum ConnectionState: Equatable, Sendable { case connecting, live, reconnecting, lost }

enum MatchStartAvailability: Equatable, Sendable {
    case unavailable(OnlineFeature)
    case waitingForPlayers
    case ready
}

enum MatchStartResult: Equatable, Sendable {
    case started(matchId: String, mapKey: String, alreadyStarted: Bool)
    case mapTieBreakRequired([String])
}

enum OnlineFeature: Equatable, Sendable { case configuration, profileStorage, onlineGameplay, appleSignIn, googleSignIn }

enum OnlineError: Error, Equatable, Sendable {
    case offline, sessionExpired, permissionDenied, accountRequired, cancelled
    case invalidCredentials, emailInUse, weakPassword, invalidCode
    case invalidProfile, invalidImage, imageTooLarge, invalidRoomConfiguration
    case roomNotFound, roomFull, roomAlreadyStarted, roomChanged, roomNotReady
    case incompatibleRoom, suspended(String), featureUnavailable(OnlineFeature)
    case server(String?)
}
