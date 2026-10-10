#if DEBUG
import FirebaseAuth
import FirebaseFirestore
import Foundation
import TraidoresCore
import FirebaseStorage
import CryptoKit
import ImageIO
import Observation
import UIKit

@MainActor @Observable final class NativeMediaCheckReport {
    var message = "MEDIA RUNNING"
}

/// Debug-only check of the start callable against the local emulators. Launch with
/// `-firebase-emulator-host 127.0.0.1 -firebase-smoke-room <id>` plus
/// `-firebase-smoke-email`/`-firebase-smoke-password` of an Auth emulator user. It prints
/// `FIREBASE SMOKE:` lines; a missing room must map to `roomNotFound` before the real call.
@MainActor
enum FirebaseSmokeCheck {
    static let mediaReport = NativeMediaCheckReport()
    static func runIfRequested() {
        let defaults = UserDefaults.standard
        if FirebaseSetup.emulatorHost != nil, let phase = defaults.string(forKey: "firebase-media-smoke") {
            Task { await mediaCheck(phase: phase) }
            return
        }
        // Repeatable UI tests may reset only their local emulator session. Never accept
        // this switch without an explicit emulator host, and never compile it in Release.
        if FirebaseSetup.emulatorHost != nil, defaults.bool(forKey: "firebase-ui-reset-auth"), FirebaseSetup.configureIfNeeded() {
            try? Auth.auth().signOut()
        }
        // `-firebase-emulator-email`/`-firebase-emulator-password`: start signed in to an Auth
        // emulator account, so multi-device emulator runs do not depend on typing in the UI.
        if FirebaseSetup.emulatorHost != nil, let email = defaults.string(forKey: "firebase-emulator-email"),
           let password = defaults.string(forKey: "firebase-emulator-password"), FirebaseSetup.configureIfNeeded() {
            Task {
                do { _ = try await Auth.auth().signIn(withEmail: email, password: password) }
                catch { print("FIREBASE EMULATOR SIGN-IN FAILED: \(error.localizedDescription)") }
            }
        }
        if FirebaseSetup.emulatorHost != nil, defaults.bool(forKey: "firebase-account-smoke") {
            Task { await accountCheck() }
            return
        }
        guard FirebaseSetup.emulatorHost != nil,
              let room = defaults.string(forKey: "firebase-smoke-room"),
              let email = defaults.string(forKey: "firebase-smoke-email"),
              let password = defaults.string(forKey: "firebase-smoke-password") else { return }
        Task {
            guard FirebaseSetup.configureIfNeeded() else { return report("sin configuración") }
            do {
                let uid = try await Auth.auth().signIn(withEmail: email, password: password).user.uid
                report("auth uid=\(uid)")
                let client = OnlineMatchStartClient()
                do {
                    _ = try await client.start(roomId: "sala-inexistente-\(UUID().uuidString)")
                    report("missing room UNEXPECTED success")
                } catch {
                    report("missing room -> \(error)")
                }
                report("start -> \(try await client.start(roomId: room))")
                report("again -> \(try await client.start(roomId: room))")
            } catch {
                report("error -> \(error)")
            }
        }
    }

    private static func accountCheck() async {
        do {
            guard FirebaseSetup.configureIfNeeded() else { throw OnlineError.featureUnavailable(.configuration) }
            try Auth.auth().signOut()
            let profiles = FirebasePublicProfileService()
            let account = FirebaseAccountService(profiles: profiles)
            func identity() throws -> OnlineIdentity {
                guard case .ready(let identity) = account.access else { throw OnlineError.server("Access is not ready: \(account.access)") }
                return identity
            }
            func check(_ condition: Bool, _ message: String) throws {
                guard condition else { throw OnlineError.server(message) }
            }
            await account.enterAsGuest()
            let guest = try identity()
            try check(!guest.isRegistered && guest.publicId == nil, "guest must have no public ID")
            let marker = UUID().uuidString
            let tokenData = try JSONSerialization.data(withJSONObject: ["sub": "google-\(marker)",
                "email": "\(marker)@traidores.test", "email_verified": true])
            let credential = GoogleAuthProvider.credential(withIDToken: String(decoding: tokenData, as: UTF8.self), accessToken: "emulator-only")
            try await account.useEmulatorCredential(credential)
            let linked = try identity()
            try check(linked.uid == guest.uid && linked.isRegistered && linked.publicId != nil, "Google must preserve guest UID")
            guard var draft = profiles.profile?.draft else { throw OnlineError.invalidProfile }
            draft.nombrePerfil = "Cuenta iOS real"
            draft.bioPerfil = "Guardada en Firestore"
            draft.temaCosmeticoPerfil = "fire"
            draft.emotesPerfil = ["premium_mate"]
            try await profiles.save(draft)
            let server = try await Firestore.firestore().collection("perfiles_publicos").document(linked.uid).getDocument(source: .server)
            try check(server.data()?["publicId"] as? String == linked.publicId && server.data()?["nombrePerfil"] as? String == draft.nombrePerfil,
                      "profile must be acknowledged by Firestore")
            let savedAt = server.data()?["actualizadaEn"] as? Timestamp
            try await profiles.save(draft)
            let unchanged = try await Firestore.firestore().collection("perfiles_publicos").document(linked.uid).getDocument(source: .server)
            try check(savedAt != nil && unchanged.data()?["actualizadaEn"] as? Timestamp == savedAt,
                      "saving an unchanged profile must not write again")
            try await account.signOut()
            await account.enterAsGuest()
            try check(try identity().uid != linked.uid, "recovery starts with a different guest")
            try await account.useEmulatorCredential(credential)
            let recovered = try identity()
            try check(recovered.uid == linked.uid && recovered.publicId == linked.publicId && profiles.profile?.draft == draft,
                      "existing Google account must recover its own profile and number")
            try await Firestore.firestore().disableNetwork()
            await account.enterAsGuest()
            try check(account.access == .failed(.offline) && profiles.profile == nil, "cached profile must not bypass server verification")
            try await Firestore.firestore().enableNetwork()
            await account.enterAsGuest()
            try check(try identity().publicId == linked.publicId, "retry must recover canonical ID")
            // A registered player cannot silently adopt a different registered account.
            try await account.signOut()
            await account.enterAsGuest()
            let email = "email-\(marker)@traidores.test"
            try await account.linkAccount(email: email, password: "test-password-123")
            let emailOwner = try identity()
            do {
                try await account.useEmulatorCredential(credential)
                throw OnlineError.server("registered account was replaced")
            } catch OnlineError.emailInUse {}
            try check(try identity().uid == emailOwner.uid, "collision must keep registered UID")
            try await account.signOut()
            await account.enterAsGuest()
            try await account.linkAccount(email: email, password: "test-password-123")
            try check(try identity().uid == emailOwner.uid && identity().publicId == emailOwner.publicId, "email recovery must preserve number")
            try await account.signOut()
            await account.enterAsGuest()
            let appleGuest = try identity()
            let appleToken = try JSONSerialization.data(withJSONObject: ["sub": "apple-\(marker)", "email": "\(marker)@privaterelay.appleid.com"])
            let apple = OAuthProvider.appleCredential(withIDToken: String(decoding: appleToken, as: UTF8.self), rawNonce: "emulator-only", fullName: nil)
            try await account.useEmulatorCredential(apple)
            try check(try identity().uid == appleGuest.uid && identity().isRegistered, "Apple relay identity must be registered")
            try await account.signOut()
            report("ACCOUNT PASS: native Firebase SDK; Google guest linking/recovery, email linking/recovery, Apple private relay, registered collision protection, canonical ID, Firestore profile persistence and offline cache rejection")
        } catch {
            report("ACCOUNT FAIL: \(error)")
        }
    }

    private static func report(_ line: String) {
        NSLog("FIREBASE SMOKE: %@", line)
    }

    private static func mediaCheck(phase: String) async {
        let defaults = UserDefaults.standard
        func check(_ value: Bool, _ message: String) throws {
            if !value { throw OnlineError.server(message) }
        }
        func wait(_ predicate: () -> Bool) async throws {
            for _ in 0..<400 {
                if predicate() { return }
                try await Task.sleep(for: .milliseconds(50))
            }
            throw OnlineError.server("Timed out waiting for a confirmed SDK result")
        }
        func image(_ color: UIColor) -> Data {
            UIGraphicsImageRenderer(size: CGSize(width: 100, height: 140)).image { context in
                color.setFill(); context.fill(CGRect(x: 0, y: 0, width: 100, height: 140))
            }.jpegData(compressionQuality: 0.9)!
        }
        func revision(_ data: Data) throws -> String {
            SHA256.hash(data: try GalleryPhotoCodec.jpeg(data)).map { String(format: "%02x", $0) }.joined()
        }
        do {
            guard FirebaseSetup.configureIfNeeded(), let email = defaults.string(forKey: "firebase-media-email"),
                  let otherEmail = defaults.string(forKey: "firebase-media-other-email"),
                  let password = defaults.string(forKey: "firebase-media-password") else { throw OnlineError.invalidCredentials }
            let owner = try await Auth.auth().signIn(withEmail: email, password: password).user.uid
            let profiles = FirebasePublicProfileService()
            _ = try await profiles.loadOrCreate(uid: owner)
            if phase == "local" {
                let database = Firestore.firestore()
                let history = FirebaseAccountHistory()
                history.attach(uid: owner)
                try await wait { history.status == .ready }
                let initialCount = history.matches
                let suite = "firebase.localHistoryQA"
                let storage = UserDefaults(suiteName: suite)!
                storage.removePersistentDomain(forName: suite)
                defer { storage.removePersistentDomain(forName: suite) }
                func start() -> LocalGameStore {
                    let store = LocalGameStore(defaults: storage)
                    store.start(name: "Media QA", difficulty: .normal, botNames: ClassicGame.defaultBotNames,
                                timing: .normal, advanced: .standard, seed: 0)
                    return store
                }
                func finish(_ store: LocalGameStore) throws {
                    for _ in 0..<350 {
                        guard let game = store.game else { throw OnlineError.invalidProfile }
                        if game.winner != nil { return }
                        store.advance(target: game.legalTargets(for: 0).first, revision: game.phaseIndex)
                    }
                    throw OnlineError.server("Native local match did not finish")
                }
                let first = start()
                try finish(first)
                try await wait { history.matches == initialCount + 1 && history.entries.first?.isOnline == false && history.entries.first?.counted == true }
                _ = LocalGameStore(defaults: storage) // Restore the finished save; must not count twice.
                await LocalAccountHistoryOutbox.flush()
                let same = try await database.document("cuentas/\(owner)").getDocument(source: .server)
                try check(same.data()?["partidas"] as? Int == initialCount + 1, "restored local result counted twice")
                first.cancel()
                let switched = start() // Ownership is fixed before switching Auth.
                let other = try await Auth.auth().signIn(withEmail: otherEmail, password: password).user.uid
                let beforeOther = try await database.document("cuentas/\(other)").getDocument(source: .server)
                try finish(switched)
                await LocalAccountHistoryOutbox.flush()
                let afterOther = try await database.document("cuentas/\(other)").getDocument(source: .server)
                try check(beforeOther.data()?["partidas"] as? Int == afterOther.data()?["partidas"] as? Int &&
                          LocalAccountHistoryOutbox.pendingCount(uid: owner) == 1,
                          "finished result must wait for its starting UID, never the new account")
                _ = try await Auth.auth().signIn(withEmail: email, password: password)
                history.attach(uid: owner)
                await LocalAccountHistoryOutbox.flush()
                try await wait { history.matches == initialCount + 2 && !history.processing }
                try check(history.entries.filter { !$0.isOnline }.count == 2, "both native local games must reach Firestore")
                switched.cancel(); history.detach()
                mediaReport.message = "MEDIA LOCAL PASS"
                report("MEDIA LOCAL PASS: actual ClassicGame results, server trigger counters, restore idempotence and start-UID isolation")
                return
            }
            if phase == "resume" {
                guard let expected = defaults.string(forKey: "firebase-media-expected-photo") else { throw OnlineError.invalidProfile }
                try await wait { profiles.profile?.fotoPerfil?.query?.contains(expected) == true && profiles.pendingPhoto == nil }
                let saved = try await Firestore.firestore().document("perfiles_publicos/\(owner)").getDocument(source: .server)
                try check((saved.data()?["fotoPerfil"] as? String)?.contains(expected) == true, "restart must publish its own pending bytes")
                mediaReport.message = "MEDIA PASS"
                report("MEDIA PASS: native SDK history, two-account isolation, gallery compression, cross-account downloads, replacement, removal, queue ownership and process-restart retry")
                return
            }
            let history = FirebaseAccountHistory()
            history.attach(uid: owner)
            try await wait { history.status == .ready }
            try check(history.matches == 1 && history.wins == 1 && history.entries.count == 1,
                      "history must read the backend's private records and counters")
            history.detach()
            let recovered = FirebaseAccountHistory()
            recovered.attach(uid: owner)
            try await wait { recovered.status == .ready }
            try check(recovered.entries.first?.isOnline == true, "new reader must recover the online result")
            try await Firestore.firestore().disableNetwork()
            recovered.attach(uid: owner)
            try check(recovered.status == .loading && recovered.entries.isEmpty,
                      "cached records must not masquerade as newly confirmed history")
            try await wait { if case .failed = recovered.status { return true }; return false }
            try await Firestore.firestore().enableNetwork()
            recovered.retry()
            try await wait { recovered.status == .ready }
            try check(recovered.matches == 1, "network retry must recover the same server counters")

            let firstImage = image(.red)
            let compressed = try GalleryPhotoCodec.jpeg(firstImage)
            let source = CGImageSourceCreateWithData(compressed as CFData, nil)!
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)! as NSDictionary
            try check(compressed.count <= GalleryPhotoCodec.maxBytes &&
                properties[kCGImagePropertyPixelWidth] as? Int == 512 &&
                properties[kCGImagePropertyPixelHeight] as? Int == 512 &&
                properties[kCGImagePropertyGPSDictionary] == nil, "published JPEG must be square, bounded and have no GPS")
            try await profiles.setPhoto(imageData: firstImage)
            guard let firstURL = profiles.profile?.fotoPerfil else {
                throw OnlineError.server("Uploaded photo URL was not recovered from the server")
            }
            let firstReference = Storage.storage().reference().child("profilePhotos/\(owner)/avatar_\(try revision(firstImage)).jpg")
            let metadata = try await firstReference.getMetadata()
            try check(metadata.contentType == "image/jpeg" && metadata.size <= GalleryPhotoCodec.maxBytes,
                      "Storage metadata must match the bounded JPEG")
            var text = profiles.profile!.draft
            text.bioPerfil = "Texto conserva foto"
            try await profiles.save(text)
            try check(profiles.profile?.fotoPerfil == firstURL, "text editing must preserve the published photo")

            profiles.clear()
            let other = try await Auth.auth().signIn(withEmail: otherEmail, password: password).user.uid
            try await wait { recovered.owner == nil }
            _ = try await profiles.loadOrCreate(uid: other)
            try check(profiles.profile?.fotoPerfil == nil && profiles.pendingPhoto == nil, "other UID must not inherit a portrait")
            let downloaded = try await firstReference.data(maxSize: Int64(GalleryPhotoCodec.maxBytes))
            try check(downloaded == compressed, "another registered player must download the published bytes")
            do {
                _ = try await firstReference.putDataAsync(compressed, metadata: metadata)
                throw OnlineError.server("another UID overwrote the photo")
            } catch let error as NSError where error.domain == StorageErrorDomain && error.code == StorageErrorCode.unauthorized.rawValue {}
            do {
                _ = try await Firestore.firestore().document("cuentas/\(owner)").getDocument(source: .server)
                throw OnlineError.server("another UID read private history")
            } catch let error as NSError where error.domain == FirestoreErrorDomain && error.code == FirestoreErrorCode.permissionDenied.rawValue {}
            profiles.clear()
            _ = try await Auth.auth().signIn(withEmail: email, password: password)
            _ = try await profiles.loadOrCreate(uid: owner)
            try check(profiles.profile?.fotoPerfil == firstURL, "another session of the same UID must recover its photo")

            // Hold the SDK after upload but before publication so two selections overlap deterministically.
            var release: CheckedContinuation<Void, Never>?
            profiles.setPhotoPublicationGateForTesting { await withCheckedContinuation { release = $0 } }
            let second = Task { try await profiles.setPhoto(imageData: image(.green)) }
            try await wait { release != nil }
            let latestData = image(.blue)
            let latest = Task { try await profiles.setPhoto(imageData: latestData) }
            let latestJPEG = try GalleryPhotoCodec.jpeg(latestData)
            try await wait { profiles.pendingPhotoData == latestJPEG }
            profiles.setPhotoPublicationGateForTesting(nil); release?.resume(); release = nil
            try await second.value; try await latest.value
            try check(profiles.profile?.fotoPerfil?.query?.contains(try revision(latestData)) == true,
                      "rapid selection must finish on the latest image")
            let oldDownload = try await firstReference.data(maxSize: Int64(GalleryPhotoCodec.maxBytes))
            try check(oldDownload == compressed, "an active roster's prior URL must remain readable")
            try await profiles.removePhoto()
            let removedVersions = try await Storage.storage().reference().child("profilePhotos/\(owner)").listAll()
            try check(profiles.profile?.fotoPerfil == nil && removedVersions.items.isEmpty,
                      "removal must clear the profile and every version")

            profiles.setPhotoPublicationGateForTesting { await withCheckedContinuation { release = $0 } }
            let abandoned = Task { try await profiles.setPhoto(imageData: image(.orange)) }
            try await wait { release != nil }
            profiles.clear()
            _ = try await Auth.auth().signIn(withEmail: otherEmail, password: password)
            _ = try await profiles.loadOrCreate(uid: other)
            release?.resume(); release = nil
            _ = try? await abandoned.value
            try check(profiles.profile?.fotoPerfil == nil && profiles.pendingPhoto == nil,
                      "an upload completion from another UID must not alter this profile")
            profiles.clear()
            _ = try await Auth.auth().signIn(withEmail: email, password: password)
            _ = try await profiles.loadOrCreate(uid: owner)
            try await wait { profiles.pendingPhoto == nil && profiles.profile?.fotoPerfil != nil }

            // Terminate the application with a durable edit that has not reached Firestore.
            profiles.setPhotoPublicationGateForTesting { await withCheckedContinuation { release = $0 } }
            let restartImage = image(.purple)
            defaults.set(try revision(restartImage), forKey: "firebase-media-expected-photo")
            Task { try? await profiles.setPhoto(imageData: restartImage) }
            try await wait { release != nil }
            mediaReport.message = "MEDIA PREPARED"
        } catch {
            mediaReport.message = "MEDIA FAIL: \(error)"
            report(mediaReport.message)
        }
    }
}
#endif
