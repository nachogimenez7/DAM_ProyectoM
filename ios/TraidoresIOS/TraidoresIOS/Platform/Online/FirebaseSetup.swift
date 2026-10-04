import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import FirebaseFunctions
import Foundation
import TraidoresCore

/// Configures Firebase on first online use, never at launch: the menu and the game against
/// the AI keep working without network. Same project as Android (`GoogleService-Info.plist`).
@MainActor
enum FirebaseSetup {
    private static var configured = false

    /// False when the bundle has no Firebase configuration; callers report the online mode
    /// as unavailable instead of letting `FirebaseApp.configure()` crash.
    @discardableResult
    static func configureIfNeeded() -> Bool {
        if configured { return true }
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else { return false }
        #if DEBUG
        let host = emulatorHost
        #else
        let host: String? = nil
        #endif
        // `iniciarPartidaV2` enforces App Check. Debug builds print a token to register in the
        // console; release uses App Attest, which needs the paid Apple Developer team. The
        // emulators skip it, so no request reaches the real project.
        if host == nil {
            #if DEBUG
            AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
            #else
            AppCheck.setAppCheckProviderFactory(AppAttestFactory())
            #endif
        }
        FirebaseApp.configure(options: options)
        if let host {
            // Same ports as `firebase.json`; Auth is emulated too so emulated UIDs never meet
            // real data.
            Auth.auth().useEmulator(withHost: host, port: 9099)
            Functions.functions(region: OnlineMatchStartContract.region).useEmulator(withHost: host, port: 5001)
        }
        configured = true
        return true
    }

    #if DEBUG
    /// `-firebase-emulator-host 127.0.0.1` points debug builds at the local emulators.
    static var emulatorHost: String? {
        UserDefaults.standard.string(forKey: "firebase-emulator-host").flatMap { $0.isEmpty ? nil : $0 }
    }
    #endif
}

private final class AppAttestFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app) ?? DeviceCheckProvider(app: app)
    }
}
