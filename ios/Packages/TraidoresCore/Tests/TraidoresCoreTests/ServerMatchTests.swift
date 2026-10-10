import Foundation
import Testing
@testable import TraidoresCore

/// Fixtures come from the production server engine (`ios/Scripts/generate_server_v3_fixtures.cjs`),
/// shaped as Realtime Database delivers them. 8 players, pampa: J3 assassin, J4 mercenary, J2 medic, J0 villager.
struct ServerMatchTests {
    private func deserter(phase: ServerMatchPhase, round: Int, team: String?, used: Bool = false,
                          muted: Bool = false) throws -> ServerMatchSnapshot {
        let raw = try fixture("reparto")
        var publicData = try #require(raw["public"] as? [String: Any])
        publicData["fase"] = phase.rawValue
        publicData["ronda"] = round
        var players = try #require(publicData["jugadores"] as? [[String: Any]])
        players[0]["muteado"] = muted
        publicData["jugadores"] = players
        var own = try #require((raw["private"] as? [String: Any])?["p0"] as? [String: Any])
        own["rolesVisibles"] = [["orden": 0, "rolKey": "desertor"]]
        own["desertorBando"] = team
        own["desertorCambioBando"] = used
        var inbox = ServerMatchInbox(ownUid: "p0", matchId: "match-1")
        inbox.accept(try ServerMatchParser.parsePublic(publicData))
        inbox.accept(try ServerMatchParser.parsePrivate(own))
        inbox.accept(try ServerMatchParser.parsePermissions((raw["permissions"] as? [String: Any])?["p0"]))
        return try #require(inbox.snapshot)
    }

    @Test func deserterChoiceIsPrivateInitialAndReviewNeedsRoundFour() throws {
        let initial = try deserter(phase: .assignment, round: 1, team: nil)
        #expect(ServerMatchActionPolicy.options(initial, nowMs: 1_800_000_000_001).map(\.team) == ["Pueblo", "Traidores"])
        #expect(ServerMatchDeserterPresentation.explanation(initial).contains("servidor sortea"))
        let early = try deserter(phase: .debate, round: 3, team: "Pueblo")
        #expect(ServerMatchActionPolicy.options(early, nowMs: 1_800_000_000_001).isEmpty)
        let review = try deserter(phase: .debate, round: 4, team: "Pueblo", muted: true)
        #expect(ServerMatchActionPolicy.options(review, nowMs: 1_800_000_000_001).map(\.team) == ["mantener", "Traidores"])
        #expect(ServerMatchDeserterPresentation.explanation(review).contains("Mantener también consume"))
        let spent = try deserter(phase: .debate, round: 4, team: "Pueblo", used: true)
        #expect(ServerMatchActionPolicy.options(spent, nowMs: 1_800_000_000_001).isEmpty)
    }

    @Test func finalDeserterWindowKeepsTheTeamAtExpiryAndOffersNoLateChoice() throws {
        let window = try deserter(phase: .deserterWindow, round: 4, team: "Traidores", muted: true)
        #expect(ServerMatchActionPolicy.options(window, nowMs: 1_800_000_000_001).map(\.team) == ["mantener", "Pueblo"])
        #expect(ServerMatchDeserterPresentation.explanation(window).contains("Si vence el plazo, mantenés"))
        #expect(ServerMatchActionPolicy.options(window, nowMs: window.publicState.deadlineMs!).isEmpty)
    }

    private func fixture(_ name: String) throws -> [String: Any] {
        let url = try #require(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures/server-v3"))
        return try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func snapshot(_ name: String, uid: String) throws -> ServerMatchSnapshot {
        let raw = try fixture(name)
        var inbox = ServerMatchInbox(ownUid: uid, matchId: "match-1")
        inbox.accept(try ServerMatchParser.parsePublic(raw["public"]))
        inbox.accept(try ServerMatchParser.parsePrivate((raw["private"] as? [String: Any])?[uid]))
        inbox.accept(try ServerMatchParser.parsePermissions((raw["permissions"] as? [String: Any])?[uid]))
        return try #require(inbox.snapshot)
    }

    @Test func assignmentHidesForeignRolesAndOffersTheAcknowledgement() throws {
        let villager = try snapshot("reparto", uid: "p0")
        #expect(villager.publicState.phase == .assignment)
        #expect(villager.ownRole == .villager)
        #expect(villager.publicState.players.allSatisfy { $0.publicRole == nil })
        #expect(villager.privateState.visibleRoles.count == 1)
        let options = ServerMatchActionPolicy.options(villager, nowMs: 1_800_000_000_001)
        #expect(options.map(\.action) == ["role_ack"])
        // Traitors see each other, not the town.
        let assassin = try snapshot("reparto", uid: "p3")
        #expect(Set(assassin.privateState.visibleRoles.values) == [.assassin, .mercenary])
    }

    @Test func nightConfirmedActionsAndPermissionsAreOnlyTheOwnOnes() throws {
        let assassin = try snapshot("noche", uid: "p3")
        #expect(assassin.publicState.phase == .night)
        #expect(assassin.privateState.confirmed == [.init(action: "matar", targetUid: "p0", team: nil)])
        #expect(assassin.permissions.traitorChat)
        #expect(ServerMatchActionPolicy.options(assassin, nowMs: 1_800_000_000_002).isEmpty, "one night action per phase")
        let medic = try snapshot("noche", uid: "p2")
        #expect(medic.privateState.confirmed.isEmpty)
        #expect(!medic.permissions.traitorChat)
        let protect = try #require(ServerMatchActionPolicy.options(medic, nowMs: 1_800_000_000_002).first)
        #expect(protect.action == "salvar" && protect.targets.count == 8, "the medic may protect anyone alive, also herself")
        let mercenary = try snapshot("noche", uid: "p4")
        #expect(mercenary.privateState.blockedTargets == ["p4"])
        // After the deadline, nothing is offered: the phase is resolving on the server.
        #expect(ServerMatchActionPolicy.options(medic, nowMs: medic.publicState.deadlineMs!).isEmpty)
    }

    @Test func debateAfterAKillAndASilence() throws {
        let medic = try snapshot("debate", uid: "p2")
        #expect(medic.publicState.phase == .debate)
        #expect(medic.publicState.player("p0")?.alive == false)
        #expect(medic.publicState.player("p0")?.deathCause == "NIGHT")
        #expect(medic.me.muted)
        #expect(!medic.permissions.publicChat, "silenced players cannot talk in the debate")
        #expect(ServerMatchActionPolicy.options(medic, nowMs: 1_800_000_000_000).isEmpty)
        let codes = medic.publicState.events.map(\.code)
        #expect(codes.contains("NIGHT_DEATH"))
    }

    @Test func voteExcludesSelfAndSilencedVoterSendsNothing() throws {
        let assassin = try snapshot("votacion", uid: "p3")
        let vote = try #require(ServerMatchActionPolicy.options(assassin, nowMs: assassin.publicState.deadlineMs! - 1).first)
        #expect(vote.action == "votar")
        #expect(!vote.targets.contains("p3") && !vote.targets.contains("p0"))
        #expect(assassin.publicState.voteTotals.isEmpty, "the tally is hidden while voting")
        let medic = try snapshot("votacion", uid: "p2")
        #expect(ServerMatchActionPolicy.options(medic, nowMs: medic.publicState.deadlineMs! - 1).isEmpty)
    }

    @Test func finalRevealsRolesAndAbandonmentNeverWins() throws {
        let assassin = try snapshot("final", uid: "p3")
        #expect(assassin.publicState.phase == .finished)
        #expect(assassin.publicState.winner == "Traidores")
        #expect(assassin.publicState.deadlineMs == nil)
        #expect(assassin.publicState.players.allSatisfy { $0.publicRole != nil })
        #expect(assassin.won(assassin.me))
        let leaver = try #require(assassin.publicState.players.first { $0.abandoned && $0.publicRole == .mercenary })
        #expect(!assassin.won(leaver), "a traitor who left does not share the victory")
        let medic = try snapshot("final", uid: "p2")
        #expect(!medic.won(medic.me))
    }

    @Test func inboxWaitsForTheSamePhaseAndIgnoresOldPublications() throws {
        let night = try fixture("noche"), debate = try fixture("debate")
        var inbox = ServerMatchInbox(ownUid: "p2", matchId: "match-1")
        inbox.accept(try ServerMatchParser.parsePublic(debate["public"]))
        inbox.accept(try ServerMatchParser.parsePrivate((night["private"] as? [String: Any])?["p2"]))
        inbox.accept(try ServerMatchParser.parsePermissions((night["permissions"] as? [String: Any])?["p2"]))
        #expect(inbox.snapshot == nil, "public debate with night private data is not a coherent snapshot")
        inbox.accept(try ServerMatchParser.parsePublic(night["public"]))
        #expect(inbox.publicState?.phase == .debate, "an older public phase never replaces a newer one")
        inbox.accept(try ServerMatchParser.parsePrivate((debate["private"] as? [String: Any])?["p2"]))
        inbox.accept(try ServerMatchParser.parsePermissions((debate["permissions"] as? [String: Any])?["p2"]))
        #expect(inbox.snapshot?.publicState.phase == .debate)
        var other = ServerMatchInbox(ownUid: "p2", matchId: "rematch")
        other.accept(try ServerMatchParser.parsePublic(debate["public"]))
        #expect(other.publicState == nil, "another match is never an update of this one")
    }

    @Test func presentationShowsEachEventOnceAcrossReconnections() throws {
        let night = try ServerMatchParser.parsePublic(try fixture("noche")["public"])
        let debate = try ServerMatchParser.parsePublic(try fixture("debate")["public"])
        var tracker = ServerMatchPresentationTracker()
        let firstUpdate = tracker.accept(night)
        #expect(firstUpdate.phaseChanged && firstUpdate.events.map(\.code) == ["NIGHT_START"])
        let second = tracker.accept(debate)
        #expect(second.phaseChanged && !second.events.isEmpty && second.events.allSatisfy { $0.seq > 1 })
        // Reconnecting delivers the same state again: nothing to replay.
        var restored = ServerMatchPresentationTracker(restored: tracker.cursor)
        let again = restored.accept(debate), older = restored.accept(night)
        #expect(again == ServerMatchPresentationUpdate(phaseChanged: false, events: []))
        #expect(older == ServerMatchPresentationUpdate(phaseChanged: false, events: []))
    }

    @Test func parserRejectsUnknownPhasesRolesAndLostAuthority() throws {
        var raw = try #require(try fixture("noche")["public"] as? [String: Any])
        raw["fase"] = "NOCHE_LOCAL"
        #expect(throws: ServerMatchError.unknownPhase("NOCHE_LOCAL")) { try ServerMatchParser.parsePublic(raw) }
        raw["fase"] = "NOCHE"
        raw["authorityMode"] = "client"
        #expect(throws: ServerMatchError.lostAuthority) { try ServerMatchParser.parsePublic(raw) }
        raw["authorityMode"] = "server"
        raw["protocolVersion"] = 2
        #expect(throws: ServerMatchError.otherProtocol) { try ServerMatchParser.parsePublic(raw) }
        var own = try #require((try fixture("noche")["private"] as? [String: Any])?["p0"] as? [String: Any])
        own["rolesVisibles"] = [["orden": 0, "rolKey": "brujo"]]
        #expect(throws: ServerMatchError.unknownRole) { try ServerMatchParser.parsePrivate(own) }
    }

    @Test func sparseArraysAndTotalsFromRealtimeDatabase() throws {
        var raw = try #require(try fixture("votacion")["public"] as? [String: Any])
        raw["empateVoto"] = ["1": "p5", "0": "p1", "10": "p7"]
        raw["votosTotales"] = [NSNull(), 2, 1]
        let parsed = try ServerMatchParser.parsePublic(raw)
        #expect(parsed.tieCandidates == ["p1", "p5", "p7"], "numeric keys sort numerically")
        #expect(parsed.voteTotals == [1: 2, 2: 1])
    }

    @Test func commandPayloadAndRecoverySchedule() throws {
        let night = try ServerMatchParser.parsePublic(try fixture("noche")["public"])
        let command = ServerMatchCommand(roomId: "room", state: night, action: "salvar", targetUid: "p2", requestId: "req-1")
        #expect(command.payload["phaseIndex"] as? Int == 1)
        #expect(command.payload["requestId"] as? String == "req-1")
        #expect(command.payload["team"] == nil)
        var recovery = ServerMatchRecoveryPolicy(uid: "p2")
        let deadline = try #require(night.deadlineMs)
        let early = recovery.due(night, nowMs: deadline + 1000)
        #expect(!early, "Tasks get the first chance")
        let first = recovery.due(night, nowMs: deadline + 7000)
        #expect(first)
        recovery.attempted(nowMs: deadline + 7000)
        let backoff = recovery.due(night, nowMs: deadline + 8000)
        #expect(!backoff)
        recovery.attempted(nowMs: deadline + 20_000)
        recovery.attempted(nowMs: deadline + 40_000)
        let exhausted = recovery.due(night, nowMs: deadline + 600_000)
        #expect(!exhausted, "at most three attempts per phase")
    }
}
