import AuthenticationServices
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation
import GoogleSignIn
import Observation
import UIKit

/// All providers share the same rule: link the current UID, or recover an existing account
/// only when the caller was a guest. Never silently replace another registered profile.
@MainActor
enum FirebaseCredentialLink {
    static func linkOrRecover(_ credential: AuthCredential) async throws -> User {
        let auth = Auth.auth()
        let user: User
        if let current = auth.currentUser { user = current }
        else { user = try await auth.signInAnonymously().user }
        let wasGuest = user.isAnonymous
        do {
            return try await user.link(with: credential).user
        } catch let error as NSError where wasGuest && [
            AuthErrorCode.credentialAlreadyInUse.rawValue,
            AuthErrorCode.emailAlreadyInUse.rawValue
        ].contains(error.code) {
            // Apple's nonce can only be used once; the SDK supplies a replacement credential.
            let recovery: AuthCredential
            if credential.provider == "apple.com" {
                guard let updated = error.userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential else {
                    throw OnlineError.server("Apple no pudo recuperar la cuenta. Volvé a intentar.")
                }
                recovery = updated
            } else {
                recovery = (error.userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential) ?? credential
            }
            return try await auth.signIn(with: recovery).user
        }
    }
}

@MainActor @Observable
final class FirebaseAccountService: OnlineAccountService {
    private(set) var access: OnlineAccessState = .signedOut
    @ObservationIgnored private let profiles: FirebasePublicProfileService
    @ObservationIgnored private let apple = AppleAccountLink()
    @ObservationIgnored private var operationInProgress = false

    init(profiles: FirebasePublicProfileService) { self.profiles = profiles }

    var googleSignInAvailable: Bool {
        guard let options = FirebaseSetup.options else { return false }
        return !(options.clientID ?? "").isEmpty
    }

    var appleSignInAvailable: Bool {
        // Local.xcconfig opts in only after the signing team supports the Apple entitlement.
        (Bundle.main.object(forInfoDictionaryKey: "TraidoresAppleSignInEnabled") as? String) == "YES"
    }

    func enterAsGuest() async {
        guard !operationInProgress else { return }
        operationInProgress = true
        defer { operationInProgress = false }
        access = .connecting
        do {
            try configure()
            let auth = Auth.auth()
            let user: User
            if let current = auth.currentUser { user = current }
            else { user = try await auth.signInAnonymously().user }
            try await loadIdentity(user)
        } catch {
            profiles.clear()
            access = state(for: error)
        }
    }

    func selectGuestAlias(_ alias: String) async throws {
        guard case .ready(let identity) = access, !identity.isRegistered else { throw OnlineError.accountRequired }
        guard OnlineContract.guestAliases.contains(alias) else { throw OnlineError.invalidProfile }
        UserDefaults.menuStore.set(alias, forKey: "online.guestAlias.\(identity.uid)")
        access = .ready(OnlineIdentity(uid: identity.uid, publicId: nil, isRegistered: false,
                                     displayName: OnlineContract.guestName(uid: identity.uid, selectedAlias: alias)))
    }

    func linkAccount(email: String, password: String) async throws {
        try await perform {
            try await FirebaseCredentialLink.linkOrRecover(EmailAuthProvider.credential(withEmail: email, password: password))
        }
    }

    func signIn(email: String, password: String) async throws {
        // UI uses linkAccount; an explicit login must not replace a registered session.
        try await perform {
            guard Auth.auth().currentUser?.isAnonymous != false else { throw OnlineError.emailInUse }
            return try await Auth.auth().signIn(withEmail: email, password: password).user
        }
    }

    func continueWithGoogle() async throws {
        guard googleSignInAvailable else { throw OnlineError.featureUnavailable(.googleSignIn) }
        try await perform {
            guard let clientID = FirebaseApp.app()?.options.clientID,
                  let presenter = Self.presenter else { throw OnlineError.featureUnavailable(.googleSignIn) }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else { throw OnlineError.invalidCredentials }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
            return try await FirebaseCredentialLink.linkOrRecover(credential)
        }
    }

    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) throws {
        guard appleSignInAvailable else { throw OnlineError.featureUnavailable(.appleSignIn) }
        try configure()
        apple.prepare(request)
    }

    func completeAppleSignIn(_ result: Result<ASAuthorization, Error>) async throws {
        guard appleSignInAvailable else { throw OnlineError.featureUnavailable(.appleSignIn) }
        try await perform {
            _ = try await self.apple.finish(result)
            guard let user = Auth.auth().currentUser else { throw OnlineError.sessionExpired }
            return user
        }
    }

    func signOut() async throws {
        guard !operationInProgress else { throw OnlineError.server("Hay un acceso en curso.") }
        try configure()
        try Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        profiles.clear()
        access = .signedOut
    }

    #if DEBUG
    /// Exercises the real adapter with locally issued emulator credentials, never the
    /// external Google/Apple authorization UI or the production Auth service.
    func useEmulatorCredential(_ credential: AuthCredential) async throws {
        guard FirebaseSetup.emulatorHost != nil else { throw OnlineError.featureUnavailable(.configuration) }
        try await perform { try await FirebaseCredentialLink.linkOrRecover(credential) }
    }
    #endif

    private func configure() throws {
        guard FirebaseSetup.configureIfNeeded() else { throw OnlineError.featureUnavailable(.configuration) }
    }

    private func perform(_ action: () async throws -> User) async throws {
        guard !operationInProgress else { throw OnlineError.server("Hay un acceso en curso.") }
        operationInProgress = true
        defer { operationInProgress = false }
        let before = access
        let previousUID = FirebaseSetup.configureIfNeeded() ? Auth.auth().currentUser?.uid : nil
        let previouslyAnonymous = FirebaseSetup.configureIfNeeded() ? Auth.auth().currentUser?.isAnonymous : nil
        var authenticated = false
        do {
            try configure()
            let user = try await action()
            authenticated = true
            // Clear the previous person's portrait before reading the recovered account.
            if profiles.profile?.uid != user.uid { profiles.clear() }
            access = .connecting
            try await loadIdentity(user)
        } catch {
            let mapped = Self.onlineError(error)
            // A picker cancellation leaves the existing guest/account usable. A failure
            // after Auth changed UID must not announce the previous profile as ready.
            if !authenticated, FirebaseSetup.configureIfNeeded(), Auth.auth().currentUser?.uid == previousUID,
               Auth.auth().currentUser?.isAnonymous == previouslyAnonymous {
                access = before
            } else {
                profiles.clear()
                access = state(for: error)
            }
            throw mapped
        }
    }

    private func loadIdentity(_ user: User) async throws {
        let uid = user.uid
        let isGuest = user.isAnonymous
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.getIDTokenResult(forcingRefresh: true) { result, error in
                if let error { continuation.resume(throwing: error) }
                else if result != nil { continuation.resume() }
                else { continuation.resume(throwing: OnlineError.sessionExpired) }
            }
        }
        let ban = try await Firestore.firestore().collection("bans").document(uid).getDocument(source: .server)
        guard !ban.exists else {
            throw OnlineError.suspended(ban.data()?["motivo"] as? String ?? "Incumplimiento de las reglas de convivencia.")
        }
        guard Auth.auth().currentUser?.uid == uid else { throw OnlineError.sessionExpired }
        if isGuest {
            profiles.clear()
            let alias = UserDefaults.menuStore.string(forKey: "online.guestAlias.\(uid)")
            access = .ready(OnlineIdentity(uid: uid, publicId: nil, isRegistered: false,
                                         displayName: OnlineContract.guestName(uid: uid, selectedAlias: alias)))
        } else {
            // provider identity also covers Apple hidden/no email accounts.
            let profile = try await profiles.loadOrCreate(uid: uid)
            access = .ready(OnlineIdentity(uid: uid, publicId: profile.publicId, isRegistered: true,
                                         displayName: profile.nombrePerfil))
        }
    }

    private func state(for error: Error) -> OnlineAccessState {
        if case .suspended(let reason) = Self.onlineError(error) { return .suspended(reason: reason) }
        return .failed(Self.onlineError(error))
    }

    static func onlineError(_ error: Error) -> OnlineError {
        if let error = error as? OnlineError { return error }
        if let error = error as? AppleAccountLink.Failure { return error == .cancelled ? .cancelled : .server(error.message) }
        let ns = error as NSError
        if ns.domain == kGIDSignInErrorDomain, ns.code == GIDSignInError.canceled.rawValue { return .cancelled }
        if ns.domain == NSURLErrorDomain { return .offline }
        if ns.domain == FirestoreErrorDomain {
            switch ns.code {
            case FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue: return .offline
            case FirestoreErrorCode.permissionDenied.rawValue: return .permissionDenied
            default: return .server(nil)
            }
        }
        switch AuthErrorCode(rawValue: ns.code) {
        case .networkError: return .offline
        case .invalidEmail, .invalidCredential, .wrongPassword, .userNotFound: return .invalidCredentials
        case .emailAlreadyInUse, .credentialAlreadyInUse, .accountExistsWithDifferentCredential: return .emailInUse
        case .weakPassword: return .weakPassword
        case .userDisabled: return .suspended("Tu cuenta está suspendida.")
        case .operationNotAllowed: return .server("Ese acceso todavía no está habilitado. Podés usar otro método.")
        default: return .server(nil)
        }
    }

    private static var presenter: UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        guard var current = scene?.windows.first(where: \.isKeyWindow)?.rootViewController else { return nil }
        while let presented = current.presentedViewController { current = presented }
        return current
    }
}

@MainActor @Observable
final class FirebasePublicProfileService: PublicProfileService {
    private(set) var profile: PublicProfile?
    @ObservationIgnored private let photos = FirebaseProfilePhotos()
    var photoSync: PhotoSyncState { photos.state }
    var photoUploadsAvailable: Bool { FirebaseSetup.profileStorageEnabled }
    @ObservationIgnored private var profileListener: ListenerRegistration?
    @ObservationIgnored private var listeningUID: String?
    var pendingPhoto: PendingProfilePhoto? { photos.preview }

    func clear() {
        profileListener?.remove()
        profileListener = nil
        listeningUID = nil
        profile = nil
        photos.clear()
        let defaults = UserDefaults.menuStore
        if defaults.string(forKey: "online.profileOwner") != nil {
            defaults.set(try? JSONEncoder().encode(LocalMenuProfile()), forKey: "menu.localProfile.v1")
            defaults.removeObject(forKey: "online.profileOwner")
            defaults.set("classic", forKey: "menu.profileTheme")
            defaults.removeObject(forKey: "menu.profileEmotes")
        }
    }

    func loadOrCreate(uid: String) async throws -> PublicProfile {
        let db = Firestore.firestore()
        let ref = db.collection("perfiles_publicos").document(uid)
        let snapshot = try await ref.getDocument(source: .server)
        if snapshot.exists {
            let recovered = try OnlineContract.profile(uid: uid, data: snapshot.data() ?? [:], emulatorOrigin: FirebaseSetup.storageEmulatorOrigin)
            try ensureOwner(uid)
            confirm(recovered)
            return recovered
        }
        // Transaction reads the profile again: another device may have assigned an ID since
        // the initial lookup. The counter is the same one Android uses; no local fallback.
        let counter = db.collection("meta").document("public_ids")
        let defaults = UserDefaults.menuStore
        let owner = defaults.string(forKey: "online.profileOwner")
        let local = owner == nil || owner == uid
            ? LocalMenuProfile.load(defaults.data(forKey: "menu.localProfile.v1") ?? Data()) : LocalMenuProfile()
        let name = String(local.name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(18))
        let fields: [String: Any] = ["nombrePerfil": name.isEmpty ? "Jugador" : name,
                                     "bioPerfil": String(local.bio.prefix(40)), "avatarPerfil": OnlineAvatarArt.key(for: local.avatar),
                                     "bannerPerfil": local.banner, "rolFavoritoPerfil": OnlineAvatarArt.key(for: local.favorite)]
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
          db.runTransaction({ transaction, errorPointer in
            do {
                let current = try transaction.getDocument(ref)
                if current.exists { return nil }
                let count = try transaction.getDocument(counter)
                let next = max(1, (count.data()?["nextId"] as? NSNumber)?.int64Value ?? 1)
                guard next < 999_999_999_999 else { throw OnlineError.invalidProfile }
                transaction.setData(["nextId": next + 1, "actualizadaEn": FieldValue.serverTimestamp()], forDocument: counter, merge: true)
                var initial = fields
                initial["uidTemporal"] = uid
                initial["publicId"] = String(next)
                initial["nombreSala"] = "\(fields["nombrePerfil"] as? String ?? "Jugador") #\(next)"
                initial["actualizadaEn"] = FieldValue.serverTimestamp()
                transaction.setData(initial, forDocument: ref)
                return nil
            } catch {
                errorPointer?.pointee = error as NSError
                return nil
            }
          }, completion: { _, error in
              if let error { continuation.resume(throwing: error) }
              else { continuation.resume() }
          })
        }
        let confirmed = try await ref.getDocument(source: .server)
        let created = try OnlineContract.profile(uid: uid, data: confirmed.data() ?? [:], emulatorOrigin: FirebaseSetup.storageEmulatorOrigin)
        try ensureOwner(uid)
        confirm(created)
        return created
    }

    private func confirm(_ value: PublicProfile) {
        let cached = LocalMenuProfile(name: value.nombrePerfil, bio: value.bioPerfil,
                                      avatar: AnimalAvatarCatalog.normalize(value.avatarPerfil), banner: value.bannerPerfil,
                                      favorite: OnlineAvatarArt.asset(for: value.rolFavoritoPerfil),
                                      profilePhotoURL: value.avatarURL?.absoluteString)
        let defaults = UserDefaults.menuStore
        defaults.set(try? JSONEncoder().encode(cached), forKey: "menu.localProfile.v1")
        defaults.set(value.uid, forKey: "online.profileOwner")
        defaults.set(value.temaCosmeticoPerfil, forKey: "menu.profileTheme")
        defaults.set(value.emotesPerfil.joined(separator: ","), forKey: "menu.profileEmotes")
        profile = value
        photos.activate(uid: value.uid)
        Task { await LocalAccountHistoryOutbox.flush() }
        if listeningUID != value.uid {
            profileListener?.remove()
            listeningUID = value.uid
            let owner = value.uid
            profileListener = Firestore.firestore().collection("perfiles_publicos").document(owner)
                .addSnapshotListener(includeMetadataChanges: true) { [weak self] snapshot, error in
                    guard error == nil, let snapshot, snapshot.exists,
                          !snapshot.metadata.isFromCache, !snapshot.metadata.hasPendingWrites else { return }
                    let data = snapshot.data() ?? [:]
                    Task { @MainActor [weak self] in
                        guard let self, self.listeningUID == owner,
                              let user = Auth.auth().currentUser, !user.isAnonymous, user.uid == owner,
                              let confirmed = try? OnlineContract.profile(uid: owner, data: data, emulatorOrigin: FirebaseSetup.storageEmulatorOrigin) else { return }
                        if self.profile != confirmed { self.confirm(confirmed) }
                    }
                }
        }
    }

    func refresh() async throws {
        guard FirebaseSetup.configureIfNeeded(), let user = Auth.auth().currentUser, !user.isAnonymous else {
            throw OnlineError.accountRequired
        }
        _ = try await loadOrCreate(uid: user.uid)
    }

    func save(_ draft: PublicProfileDraft) async throws {
        guard let previous = profile else { throw OnlineError.accountRequired }
        try ensureOwner(previous.uid)
        let name = draft.nombrePerfil.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 18, draft.bioPerfil.count <= 40,
              ["classic", "space", "sea", "fire"].contains(draft.temaCosmeticoPerfil), draft.emotesPerfil.count <= 4 else {
            throw OnlineError.invalidProfile
        }
        var normalized = draft
        normalized.nombrePerfil = name
        // Reopening the editor or confirming an unchanged selection is not a write.
        guard normalized != previous.draft else { return }
        try await Firestore.firestore().collection("perfiles_publicos").document(previous.uid).updateData([
            "nombrePerfil": name, "nombreSala": "\(name) #\(previous.publicId ?? "")",
            "bioPerfil": draft.bioPerfil, "avatarPerfil": draft.avatarPerfil, "bannerPerfil": draft.bannerPerfil,
            "rolFavoritoPerfil": draft.rolFavoritoPerfil, "emotesPerfil": draft.emotesPerfil,
            "temaCosmeticoPerfil": draft.temaCosmeticoPerfil, "actualizadaEn": FieldValue.serverTimestamp()
        ])
        try ensureOwner(previous.uid)
        try await refresh()
    }

    func setPhoto(imageData: Data) async throws {
        guard photoUploadsAvailable else { throw OnlineError.featureUnavailable(.profileStorage) }
        try await photos.setPhoto(imageData)
        try await refresh()
    }
    func retryPhotoSync() async throws {
        guard photoUploadsAvailable else { throw OnlineError.featureUnavailable(.profileStorage) }
        try await photos.flush()
        try await refresh()
    }
    func removePhoto() async throws {
        guard photoUploadsAvailable else { throw OnlineError.featureUnavailable(.profileStorage) }
        try await photos.removePhoto()
        try await refresh()
    }
    #if DEBUG
    func setPhotoPublicationGateForTesting(_ gate: (() async -> Void)?) {
        guard FirebaseSetup.emulatorHost != nil else { return }
        photos.beforePublicationForTesting = gate
    }
    #endif

    private func ensureOwner(_ uid: String) throws {
        guard let user = Auth.auth().currentUser, !user.isAnonymous, user.uid == uid else { throw OnlineError.sessionExpired }
    }
}

/// Account access is available independently of the unfinished iOS match adapters. The hub
/// keeps room actions disabled; none of these methods returns invented room data.
@MainActor @Observable
final class UnavailableIOSRooms: RoomDirectoryService, RoomSessionService {
    var snapshot: RoomSnapshot? { nil }
    var connection: ConnectionState { .lost }
    var startAvailability: MatchStartAvailability { .unavailable(.onlineGameplay) }
    func publicRooms() async throws -> [RoomSummary] { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func create(_ draft: RoomDraft) async throws -> String { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func join(code: String) async throws -> String { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func recoverableRoom() async throws -> RoomSummary? { nil }
    func attach(roomId: String) async throws { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func detach() {}
    func setReady(_ ready: Bool) async throws { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func voteMap(_ key: String) async throws { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func updateConfig(_ config: LobbyConfig) async throws { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func start(hostTieBreakChoice: String?) async throws -> MatchStartResult { throw OnlineError.featureUnavailable(.onlineGameplay) }
    func leave() async throws { throw OnlineError.featureUnavailable(.onlineGameplay) }
}
