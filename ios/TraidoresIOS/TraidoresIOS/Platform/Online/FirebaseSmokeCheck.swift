#if DEBUG
import FirebaseAuth
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

    private static func report(_ line: String) {
        NSLog("FIREBASE SMOKE: %@", line)
    }
}
#endif
