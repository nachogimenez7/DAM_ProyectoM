#if DEBUG
import FirebaseAuth
import FirebaseFirestore
import Foundation
import TraidoresCore

/// Debug-only check of the start callable against the local emulators. Launch with
/// `-firebase-emulator-host 127.0.0.1 -firebase-smoke-room <id>` plus
/// `-firebase-smoke-email`/`-firebase-smoke-password` of an Auth emulator user. It prints
/// `FIREBASE SMOKE:` lines; a missing room must map to `roomNotFound` before the real call.
@MainActor
enum FirebaseSmokeCheck {
    static func runIfRequested() {
        let defaults = UserDefaults.standard
        // Repeatable UI tests may reset only their local emulator session. Never accept
        // this switch without an explicit emulator host, and never compile it in Release.
        if FirebaseSetup.emulatorHost != nil, defaults.bool(forKey: "firebase-ui-reset-auth"), FirebaseSetup.configureIfNeeded() {
            try? Auth.auth().signOut()
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
}
#endif
