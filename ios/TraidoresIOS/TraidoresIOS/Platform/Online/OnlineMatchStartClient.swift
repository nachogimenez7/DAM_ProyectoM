import FirebaseFunctions
import Foundation
import TraidoresCore

/// The only way this client starts an online match: the server deals the roles; this app
/// never deals locally. Not wired to any screen yet: the callable keeps the requester as
/// active host and nothing on the server resolves later phases, while `ClassicGame` is not
/// interchangeable with Android's `GameEngine`. Real starts stay disabled until one of the two
/// authorities exists (`startAvailability == .unavailable(.onlineGameplay)`).
@MainActor
final class OnlineMatchStartClient {
    // Presentation DTO conversion only; request/response parsing stays in TraidoresCore.
    func startForLobby(roomId: String, hostTieBreakChoice: String?) async throws -> MatchStartResult {
        let choice: GameMap?
        if let hostTieBreakChoice {
            guard let map = GameMap(rawValue: hostTieBreakChoice) else {
                throw OnlineError.invalidRoomConfiguration
            }
            choice = map
        } else {
            choice = nil
        }
        switch try await start(roomId: roomId, hostTieBreakChoice: choice) {
        case .accepted(let matchId, let map, let alreadyStarted):
            return .started(matchId: matchId, mapKey: map.rawValue, alreadyStarted: alreadyStarted)
        case .mapTieBreakRequired(let maps):
            return .mapTieBreakRequired(maps.map(\.rawValue))
        }
    }

    func start(roomId: String, hostTieBreakChoice: GameMap? = nil) async throws -> OnlineMatchStartResult {
        let request = try OnlineMatchStartContract.request(roomId: roomId, hostTieBreakChoice: hostTieBreakChoice)
        guard FirebaseSetup.configureIfNeeded() else {
            throw OnlineError.featureUnavailable(.configuration)
        }
        let callable = Functions.functions(region: OnlineMatchStartContract.region)
            .httpsCallable(OnlineMatchStartContract.functionName)
        let data: Any
        do {
            data = try await callable.call(request).data
        } catch let error as NSError where error.domain == FunctionsErrorDomain {
            throw OnlineMatchStartContract.failure(code: Self.codeName(error.code), serverMessage: error.localizedDescription)
        }
        return try OnlineMatchStartContract.parse(data)
    }

    private static func codeName(_ raw: Int) -> String {
        switch FunctionsErrorCode(rawValue: raw) {
        case .unauthenticated: "unauthenticated"
        case .permissionDenied: "permission-denied"
        case .unavailable: "unavailable"
        case .deadlineExceeded: "deadline-exceeded"
        case .notFound: "not-found"
        case .failedPrecondition: "failed-precondition"
        case .invalidArgument: "invalid-argument"
        default: "internal"
        }
    }
}
