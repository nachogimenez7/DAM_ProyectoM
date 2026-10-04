import AuthenticationServices
import CryptoKit
import FirebaseAuth
import Foundation

/// Sign in with Apple for Firebase Auth, meant for `SignInWithAppleButton`:
/// `onRequest: link.prepare`, `onCompletion: { result in try await link.finish(result) }`.
/// A guest keeps its UID when linking. An Apple ID that already owns an account recovers
/// that account instead; the guest's local photo must not move to it.
@MainActor
final class AppleAccountLink {
    enum Outcome: Equatable {
        case linkedGuest(uid: String)
        case signedIn(uid: String)
    }

    enum Failure: Error, Equatable {
        case cancelled, failed

        var message: String {
            switch self {
            case .cancelled: "Se canceló el acceso con Apple."
            case .failed: "No se pudo vincular la cuenta. Probá otra vez."
            }
        }
    }

    private var rawNonce: String?

    func prepare(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = Self.randomNonce()
        rawNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func finish(_ result: Result<ASAuthorization, Error>) async throws -> Outcome {
        defer { rawNonce = nil }
        let authorization: ASAuthorization
        switch result {
        case .success(let value): authorization = value
        case .failure(let error):
            throw (error as? ASAuthorizationError)?.code == .canceled ? Failure.cancelled : Failure.failed
        }
        guard let apple = authorization.credential as? ASAuthorizationAppleIDCredential,
              let nonce = rawNonce,
              let tokenData = apple.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else { throw Failure.failed }

        guard FirebaseSetup.configureIfNeeded() else { throw Failure.failed }
        let auth = Auth.auth()
        let credential = OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: apple.fullName)
        do {
            let outcome: Outcome
            if let guest = auth.currentUser, guest.isAnonymous {
                do {
                    outcome = .linkedGuest(uid: try await guest.link(with: credential).user.uid)
                } catch let error as NSError where error.code == AuthErrorCode.credentialAlreadyInUse.rawValue {
                    // Apple's nonce is single use; Firebase hands back the credential to sign in.
                    guard let existing = error.userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential else {
                        throw Failure.failed
                    }
                    outcome = .signedIn(uid: try await auth.signIn(with: existing).user.uid)
                }
            } else {
                outcome = .signedIn(uid: try await auth.signIn(with: credential).user.uid)
            }
            // Like Android's `AccountLink.refreshClaims`: the rules read the provider and the
            // email from the token, which still says anonymous until it is refreshed.
            _ = try await auth.currentUser?.getIDTokenResult(forcingRefresh: true)
            return outcome
        } catch let failure as Failure {
            throw failure
        } catch {
            throw Failure.failed
        }
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        if SecRandomCopyBytes(kSecRandomDefault, length, &bytes) != errSecSuccess {
            bytes = (0..<length).map { _ in UInt8.random(in: .min ... .max) }
        }
        return String(bytes.map { charset[Int($0) % charset.count] })
    }
}
