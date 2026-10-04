import Testing
@testable import TraidoresCore

struct OnlineMatchStartTests {
    @Test func requestMatchesTheCallableContract() throws {
        #expect(OnlineMatchStartContract.region == "southamerica-west1")
        #expect(OnlineMatchStartContract.functionName == "iniciarPartidaV2")
        #expect(try OnlineMatchStartContract.request(roomId: " sala1 ") == ["roomId": "sala1"])
        #expect(try OnlineMatchStartContract.request(roomId: "sala1", hostTieBreakChoice: .greece)
                == ["roomId": "sala1", "hostTieBreakChoice": "grecia"])
        #expect(throws: OnlineMatchStartFailure.invalidRoom) { try OnlineMatchStartContract.request(roomId: "  ") }
    }

    @Test func parsesStartedAndAlreadyStarted() throws {
        #expect(try OnlineMatchStartContract.parse(["status": "started", "matchId": "m1", "mapKey": "pampa"])
                == .accepted(matchId: "m1", map: .pampa, alreadyStarted: false))
        #expect(try OnlineMatchStartContract.parse(["status": "already_started", "matchId": "m1", "mapKey": "medieval"])
                == .accepted(matchId: "m1", map: .medieval, alreadyStarted: true))
    }

    @Test func tieBreakKeepsOnlyKnownMapsOnce() throws {
        let raw: [String: Any] = ["status": "tie_break_required", "mapKeys": ["grecia", "luna", "grecia", "pampa", 3]]
        #expect(try OnlineMatchStartContract.parse(raw) == .mapTieBreakRequired([.greece, .pampa]))
    }

    @Test func rejectsMalformedResponses() {
        for raw: Any? in [nil, "started", ["status": "started", "mapKey": "pampa"],
                          ["status": "started", "matchId": "m1", "mapKey": "luna"],
                          ["status": "tie_break_required", "mapKeys": ["luna"]], ["status": "otro"]] {
            #expect(throws: OnlineMatchStartFailure.self) { try OnlineMatchStartContract.parse(raw) }
        }
    }

    @Test func mapsCallableErrorsLikeAndroid() {
        #expect(OnlineMatchStartContract.failure(code: "unauthenticated", serverMessage: "x") == .sessionExpired)
        #expect(OnlineMatchStartContract.failure(code: "permission-denied", serverMessage: nil) == .notHost)
        #expect(OnlineMatchStartContract.failure(code: "deadline-exceeded", serverMessage: nil) == .unavailable)
        #expect(OnlineMatchStartContract.failure(code: "not-found", serverMessage: nil) == .roomNotFound)
        #expect(OnlineMatchStartContract.failure(code: "failed-precondition", serverMessage: "Faltan jugadores listos.")
                == .notReady("Faltan jugadores listos."))
        #expect(OnlineMatchStartContract.failure(code: "invalid-argument", serverMessage: " ") == .notReady(nil))
        #expect(OnlineMatchStartContract.failure(code: "internal", serverMessage: "stack") == .server)
        #expect(OnlineMatchStartFailure.notReady(nil).message == "No se pudo iniciar la partida. La sala todavía no está lista.")
    }
}
