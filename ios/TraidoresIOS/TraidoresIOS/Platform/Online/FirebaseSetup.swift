import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import FirebaseFunctions
import FirebaseFirestore
import FirebaseStorage
import Foundation
import TraidoresCore

/// Configures Firebase on first online use, never at launch: the menu and the game against
/// the AI keep working without network. Same project as Android (`GoogleService-Info.plist`).
@MainActor
enum FirebaseSetup {
    private static var configured = false
    static var profileStorageEnabled: Bool {
        #if DEBUG
        if emulatorHost != nil { return true }
        #endif
        return (Bundle.main.object(forInfoDictionaryKey: "TraidoresProfileStorageEnabled") as? String) == "YES"
    }
    static var storageEmulatorOrigin: URL? {
        #if DEBUG
        if let host = emulatorHost { return URL(string: "http://\(host):9199") }
        #endif
        return nil
    }
    static var options: FirebaseOptions? {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") else { return nil }
        return FirebaseOptions(contentsOfFile: path)
    }

    /// False when the bundle has no Firebase configuration; callers report the online mode
    /// as unavailable instead of letting `FirebaseApp.configure()` crash.
    @discardableResult
    static func configureIfNeeded() -> Bool {
        if configured { return true }
        guard let options else { return false }
        #if DEBUG
        let host = emulatorHost
        #else
        let host: String? = nil
        #endif
        // `iniciarPartidaV2` enforces App Check. Debug builds print a token to register in the
        // console; release uses App Attest, which needs the paid Apple Developer team. The
        // emulators skip it, so no request reaches the real project.
        #if DEBUG
        if host != nil {
            AppCheck.setAppCheckProviderFactory(EmulatorAppCheckFactory())
        }
        #endif
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
            let firestore = Firestore.firestore()
            let settings = firestore.settings
            settings.host = "\(host):8081"
            settings.isSSLEnabled = false
            settings.cacheSettings = MemoryCacheSettings()
            firestore.settings = settings
            Functions.functions(region: OnlineMatchStartContract.region).useEmulator(withHost: host, port: 5001)
            let storage = Storage.storage()
            storage.useEmulator(withHost: host, port: 9199)
            storage.maxUploadRetryTime = 5
            storage.maxOperationRetryTime = 5
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

#if DEBUG
/// A local emulator does not validate App Check. Supply a local token so the SDK never
/// exchanges a debug token with the real Firebase project during emulator-only tests.
private final class EmulatorAppCheckFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? { EmulatorAppCheckProvider() }
}

private final class EmulatorAppCheckProvider: NSObject, AppCheckProvider {
    func getToken(completion: @escaping (AppCheckToken?, Error?) -> Void) {
        completion(AppCheckToken(token: "emulator-only", expirationDate: Date().addingTimeInterval(3600)), nil)
    }
}
#endif

private final class AppAttestFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app) ?? DeviceCheckProvider(app: app)
    }
}
