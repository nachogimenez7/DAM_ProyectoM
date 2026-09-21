import Foundation
import Testing
@testable import TraidoresCore

struct ClassicGameTests {
    private func fixed(_ phase: GamePhase) -> ClassicGame {
        var game = ClassicGame(name: "Humano", seed: 1)
        game.players = [
            .init(id: 0, name: "Humano", role: .villager),
            .init(id: 1, name: "Asesino", role: .assassin),
            .init(id: 2, name: "Comisario", role: .detective),
            .init(id: 3, name: "Médico", role: .medic),
            .init(id: 4, name: "Aldeano", role: .villager)
        ]
        game.phase = phase
        return game
    }

    @Test func classicCompositionAndTrainingDoNotChangeDeck() {
        for role in Set(ClassicGame.roles) {
            let game = ClassicGame(name: "  Nacho  ", seed: 17, trainingRole: role)
            #expect(game.human.role == role)
            #expect(game.human.name == "Nacho")
            #expect(game.players.map(\.role.rawValue).sorted() == ClassicGame.roles.map(\.rawValue).sorted())
        }
    }

    @Test func lobbyDifficultyAndAndroidBotNamesReachTheMatch() throws {
        let game = ClassicGame(name: "Humano", seed: 17, difficulty: .hard)
        #expect(game.difficulty == .hard)
        #expect(game.players.map(\.name) == ["Humano", "Thiago", "Mora", "Lautaro", "Valen"])
        #expect(try ClassicSave.decode(ClassicSave.encode(game)).difficulty == .hard)
    }

    @Test func classicLobbySupportsFiveThroughFifteenPlayers() throws {
        for count in ClassicGame.minimumPlayers...ClassicGame.maximumPlayers {
            let bots = Array(ClassicGame.defaultBotNames.prefix(count - 1))
            let game = ClassicGame(name: "Humano", seed: UInt64(count), botNames: bots)
            #expect(game.players.count == count)
            #expect(game.players.filter { $0.role == .assassin }.count == 1)
            #expect(game.players.filter { $0.role == .detective }.count == 1)
            #expect(game.players.filter { $0.role == .medic }.count == 1)
            #expect(game.players.filter { $0.role == .villager }.count == count - 3)
            #expect(try ClassicSave.decode(ClassicSave.encode(game)) == game)
        }
    }

    @Test func lobbyNamesAreCleanedAndMissingPlayersAreFilled() {
        let game = ClassicGame(name: "  Nacho  ", seed: 3, botNames: ["  Bot Uno  ", ""])
        #expect(game.players.count == ClassicGame.minimumPlayers)
        #expect(game.players.map(\.name) == ["Nacho", "Bot Uno", "Mora", "Lautaro", "Valen"])
    }

    // Android GameEngine.resolveDawn: protection cancels death, all night actors act before dawn.
    @Test func protectionAndDawnWinner() {
        var protected = fixed(.dawn)
        protected.nightTarget = 2
        protected.protectedPlayer = 2
        let accepted32 = protected.advance(expectedPhaseIndex: 0)
        #expect(accepted32)
        #expect(protected.players[2].alive)
        #expect(protected.phase == .discussion)
        var unprotected = fixed(.dawn)
        unprotected.nightTarget = 2
        unprotected.protectedPlayer = 3
        let accepted38 = unprotected.advance(expectedPhaseIndex: 0)
        #expect(accepted38)
        #expect(!unprotected.players[2].alive)
        #expect(unprotected.winner == nil)
        var parity = fixed(.dawn)
        parity.players[2].alive = false
        parity.players[3].alive = false
        parity.nightTarget = 4
        let accepted45 = parity.advance(expectedPhaseIndex: 0)
        #expect(accepted45)
        #expect(parity.winner == .traitors)
    }

    // Android isValidNightTarget allows medic self-action; votes and investigations exclude self.
    @Test func actionsRejectInvalidAndStaleInput() {
        var game = ClassicGame(name: "", seed: 2, trainingRole: .medic)
        let accepted52 = game.advance(expectedPhaseIndex: 0)
        #expect(accepted52)
        #expect(game.phase == .medicNight)
        #expect(game.legalTargets(for: 0).contains(0))
        let before = game
        let accepted56 = !game.advance(target: 99, expectedPhaseIndex: game.phaseIndex)
        #expect(accepted56)
        #expect(game == before)
        let revision = game.phaseIndex
        let accepted59 = game.advance(target: 0, expectedPhaseIndex: revision)
        #expect(accepted59)
        let after = game
        let accepted61 = !game.advance(target: 0, expectedPhaseIndex: revision)
        #expect(accepted61)
        #expect(game == after)
        var detective = ClassicGame(name: "", seed: 2, trainingRole: .detective)
        let accepted64 = detective.advance(expectedPhaseIndex: 0)
        #expect(accepted64)
        #expect(!detective.legalTargets(for: 0).contains(0))
        let target = detective.legalTargets(for: 0).first!
        let accepted67 = detective.advance(target: target, expectedPhaseIndex: detective.phaseIndex)
        #expect(accepted67)
        #expect(detective.humanInvestigations.last?.suspicious == (detective.players[target].role == .assassin))
        #expect(detective.messages.allSatisfy { !$0.text.contains("Investigué") })
    }

    // Android continueAfterVoteRecount / resolveSecondTie without an alcalde: one runoff, then no expulsion.
    @Test func secondTieDoesNotKillAnyoneAndInvalidVotesAreIgnored() {
        var game = fixed(.voting)
        game.recordVotes([0: 1, 1: 0, 2: 0, 3: 1, 4: 3, 99: 1])
        #expect(game.votes.count == 5)
        #expect(game.tieCandidates == [0, 1])
        let accepted78 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted78)
        #expect(game.phase == .tieVote)
        #expect(game.legalTargets(for: 0) == [1])
        game.recordVotes([0: 1, 1: 0, 2: 0, 3: 1, 4: 4])
        #expect(game.votes[4] == nil)
        let accepted83 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted83)
        #expect(game.phase == .result)
        #expect(game.eliminationTarget == nil)
        let accepted86 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted86)
        #expect(game.round == 2)
        #expect(game.living.count == 5)
    }

    @Test func firstTieCanResolveWithExpulsionAndTownVictory() {
        var game = fixed(.voting)
        game.recordVotes([0: 1, 1: 0, 2: 0, 3: 1, 4: 3])
        let accepted94 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted94)
        game.recordVotes([0: 1, 1: 0, 2: 1, 3: 1, 4: 0])
        #expect(game.eliminationTarget == 1)
        #expect(game.players[1].alive) // Recount is a presentation phase, not an elimination.
        let accepted98 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted98)
        let accepted99 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted99)
        #expect(!game.players[1].alive)
        #expect(game.winner == .town)
        let finished = game
        let accepted103 = !game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted103)
        #expect(game == finished)
    }

    @Test func deadHumanCanSpectateButCannotVoteOrSpeak() {
        var game = fixed(.discussion)
        game.players[0].alive = false
        let accepted110 = !game.accuse(1, expectedPhaseIndex: game.phaseIndex)
        #expect(accepted110)
        let accepted111 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted111)
        #expect(game.legalTargets(for: 0).isEmpty)
        let accepted113 = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted113)
        #expect(game.votes[0] == nil)
    }

    @Test func botPerceptionExcludesOtherRolesAndPrivateActions() {
        let original = fixed(.discussion)
        var changed = original
        changed.players.swapAt(1, 2) // Only checking perception, not persisting this deliberately reordered fixture.
        changed.nightTarget = 3
        changed.protectedPlayer = 4
        changed.investigations = [.init(round: 1, investigator: 2, target: 1, suspicious: true)]
        #expect(original.perception(for: 0) == changed.perception(for: 0))
        #expect(changed.perception(for: 2).reads.count == 1)
    }

    @Test func accusationAndPrivateReadSharingAreOncePerDay() {
        var game = fixed(.discussion)
        let accepted130 = game.accuse(1, expectedPhaseIndex: game.phaseIndex)
        #expect(accepted130)
        let accepted131 = !game.accuse(2, expectedPhaseIndex: game.phaseIndex)
        #expect(accepted131)
        let accepted132 = !game.shareInvestigation(expectedPhaseIndex: game.phaseIndex)
        #expect(accepted132)
    }

    @Test func savedGamePreservesDecisionsAndRejectsInvalidVersion() throws {
        let game = ClassicGame(name: "Nacho", seed: 46)
        #expect(try ClassicSave.decode(ClassicSave.encode(game)) == game)
        var object = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(game)) as? [String: Any])
        object["version"] = 999
        let unsupported = try JSONSerialization.data(withJSONObject: object)
        #expect(throws: (any Error).self) { try ClassicSave.decode(unsupported) }
        #expect(throws: (any Error).self) { try ClassicSave.decode(Data("invalid".utf8)) }
        var invalid = game
        invalid.players.removeLast()
        #expect(throws: (any Error).self) { try ClassicSave.decode(ClassicSave.encode(invalid)) }
    }

    @Test func seededMatchesFinishWithoutIllegalActionsOrSaveDivergence() throws {
        var wins: Set<RoleTeam> = []
        var rolesPlayed: Set<RoleKey> = []
        for seed in UInt64(0)..<500 {
            var game = ClassicGame(name: "Prueba", seed: seed)
            var choices = ClassicRandom(state: seed &+ 1)
            rolesPlayed.insert(game.human.role)
            for _ in 0..<350 {
                if let winner = game.winner { wins.insert(winner); break }
                let target = game.legalTargets(for: 0).randomElement(using: &choices)
                let previous = game
                var restored = try ClassicSave.decode(ClassicSave.encode(game))
                let accepted160 = game.advance(target: target, expectedPhaseIndex: game.phaseIndex)
                #expect(accepted160)
                let accepted161 = restored.advance(target: target, expectedPhaseIndex: restored.phaseIndex)
                #expect(accepted161)
                #expect(game == restored)
                #expect(game.phaseIndex > previous.phaseIndex)
                #expect(game.players.count == 5)
                for old in previous.players where !old.alive { #expect(!game.players[old.id].alive) }
                if game.phase == .voteCount {
                    for (voter, vote) in game.votes {
                        #expect(voter != vote)
                        #expect(game.players[voter].alive && game.players[vote].alive)
                    }
                }
            }
            #expect(game.winner != nil, "Nonterminating seed: \(seed)")
        }
        #expect(wins == [.town, .traitors])
        #expect(rolesPlayed == Set(ClassicGame.roles))
    }
}
