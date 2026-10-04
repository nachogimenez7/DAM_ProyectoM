import Foundation
import Observation
import AuthenticationServices

@MainActor protocol OnlineAccountService: AnyObject, Observable {
    var access: OnlineAccessState { get }
    var appleSignInAvailable: Bool { get }
    func enterAsGuest() async
    func selectGuestAlias(_ alias: String) async throws
    // Link an anonymous UID; if the email already exists, recover that account's profile.
    func linkAccount(email: String, password: String) async throws
    func signIn(email: String, password: String) async throws
    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) throws
    func completeAppleSignIn(_ result: Result<ASAuthorization, Error>) async throws
    func signOut() async throws
}

extension OnlineAccountService {
    // Implementations without enabled Apple capabilities keep that path unavailable.
    var appleSignInAvailable: Bool { false }
    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) throws {
        throw OnlineError.featureUnavailable(.appleSignIn)
    }
    func completeAppleSignIn(_ result: Result<ASAuthorization, Error>) async throws {
        throw OnlineError.featureUnavailable(.appleSignIn)
    }
}

@MainActor protocol PublicProfileService: AnyObject, Observable {
    var profile: PublicProfile? { get }
    var photoSync: PhotoSyncState { get }
    // Owner-scoped local preview, retained after a failed publication.
    var pendingPhoto: PendingProfilePhoto? { get }
    func refresh() async throws
    func save(_ draft: PublicProfileDraft) async throws
    // Accept picker bytes; the adapter crops, recodes without EXIF, and enforces 256 KiB.
    func setPhoto(imageData: Data) async throws
    func retryPhotoSync() async throws
    func removePhoto() async throws
}

extension PublicProfileService {
    var pendingPhotoData: Data? {
        guard case .image(let data) = pendingPhoto else { return nil }
        return data
    }
}

@MainActor protocol RoomDirectoryService: AnyObject {
    func publicRooms() async throws -> [RoomSummary]
    func create(_ draft: RoomDraft) async throws -> String
    func join(code: String) async throws -> String
    func recoverableRoom() async throws -> RoomSummary?
}

@MainActor protocol RoomSessionService: AnyObject, Observable {
    var snapshot: RoomSnapshot? { get }
    var connection: ConnectionState { get }
    var startAvailability: MatchStartAvailability { get }
    func attach(roomId: String) async throws
    // Detach listeners without removing membership; for lifecycle/account changes.
    func detach()
    func setReady(_ ready: Bool) async throws
    func voteMap(_ key: String) async throws
    func updateConfig(_ config: LobbyConfig) async throws
    func start(hostTieBreakChoice: String?) async throws -> MatchStartResult
    // Do not announce success until membership/host transfer is acknowledged.
    func leave() async throws
}

// SwiftUI's type-based environment needs a concrete observable container.
// Views use @Environment(OnlineServices.self), with real or fake implementations injected.
@MainActor @Observable final class OnlineServices {
    let account: any OnlineAccountService
    let profile: any PublicProfileService
    let directory: any RoomDirectoryService
    let room: any RoomSessionService

    init(account: any OnlineAccountService, profile: any PublicProfileService,
         directory: any RoomDirectoryService, room: any RoomSessionService) {
        self.account = account
        self.profile = profile
        self.directory = directory
        self.room = room
    }
}
