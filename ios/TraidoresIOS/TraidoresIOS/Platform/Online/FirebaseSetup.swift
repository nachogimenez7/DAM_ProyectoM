import FirebaseAppCheck
import FirebaseAuth
import FirebaseCore
import FirebaseDatabase
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
        if let host = emulatorHost { return URL(string: "http://\(host):\(emulatorPorts.storage)") }
        #endif
        return nil
    }
    /// Server-authority rooms (protocol V3), like Android's `SERVER_ONLINE_V3`: on in Debug,
    /// opt-in for other builds with `TraidoresServerRoomsEnabled = YES` until V3 opens.
    static var serverRoomsEnabled: Bool {
        #if DEBUG
        return true
        #else
        return (Bundle.main.object(forInfoDictionaryKey: "TraidoresServerRoomsEnabled") as? String) == "YES"
        #endif
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
            let ports = emulatorPorts
            Auth.auth().useEmulator(withHost: host, port: ports.auth)
            let firestore = Firestore.firestore()
            let settings = firestore.settings
            settings.host = "\(host):\(ports.firestore)"
            settings.isSSLEnabled = false
            settings.cacheSettings = MemoryCacheSettings()
            firestore.settings = settings
            Functions.functions(region: OnlineMatchStartContract.region).useEmulator(withHost: host, port: ports.functions)
            Database.database().useEmulator(withHost: host, port: ports.database)
            let storage = Storage.storage()
            storage.useEmulator(withHost: host, port: ports.storage)
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

    /// `-firebase-emulator-ports auth,firestore,functions,database[,storage]` runs against an
    /// isolated emulator set, without touching emulators other tools keep on the default ports.
    static var emulatorPorts: (auth: Int, firestore: Int, functions: Int, database: Int, storage: Int) {
        let custom = (UserDefaults.standard.string(forKey: "firebase-emulator-ports") ?? "")
            .split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        guard custom.count >= 4 else { return (9099, 8081, 5001, 9000, 9199) }
        return (custom[0], custom[1], custom[2], custom[3], custom.count > 4 ? custom[4] : 9199)
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
