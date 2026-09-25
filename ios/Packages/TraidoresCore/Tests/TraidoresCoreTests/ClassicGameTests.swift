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

    @Test func publicChatSendsRepliesAndRespectsSilenceAndPhase() throws {
        var game = fixed(.discussion)
        let revision = game.phaseIndex
        let blankSent = game.sendPublicMessage("  ", expectedPhaseIndex: revision)
        let staleSent = game.sendPublicMessage("Hola", expectedPhaseIndex: revision + 1)
        #expect(!blankSent)
        #expect(!staleSent)
        let initialCount = game.messages.count
        let sent = game.sendPublicMessage("Sospecho de Asesino", expectedPhaseIndex: revision)
        #expect(sent)
        #expect(game.messages.count == initialCount + 2)
        #expect(game.messages[initialCount].speaker == 0)
        #expect(game.messages[initialCount + 1].speaker == 1)
        #expect(game.humanAccusation == 1)
        #expect(try ClassicSave.decode(ClassicSave.encode(game)).messages == game.messages)

        game.silencedPlayer = 0
        let silencedSent = game.sendPublicMessage("No debería salir", expectedPhaseIndex: revision)
        #expect(!silencedSent)
        game.silencedPlayer = nil
        game.phase = .voting
        let votingSent = game.sendPublicMessage("Tampoco", expectedPhaseIndex: revision)
        #expect(!votingSent)
    }

    @Test func lobbyMapDifficultyAndAndroidBotNamesReachTheMatch() throws {
        let game = ClassicGame(name: "Humano", seed: 17, map: .greece, difficulty: .hard)
        #expect(game.map == .greece)
        #expect(game.difficulty == .hard)
        #expect(game.players.map(\.name) == ["Humano", "Thiago", "Mora", "Lautaro", "Valen"])
        let restored = try ClassicSave.decode(ClassicSave.encode(game))
        #expect(restored.map == .greece)
        #expect(restored.difficulty == .hard)

        var oldEnvelope = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(game)) as? [String: Any])
        var oldGame = try #require(oldEnvelope["game"] as? [String: Any])
        oldGame.removeValue(forKey: "mapConfig")
        oldEnvelope["game"] = oldGame
        let oldData = try JSONSerialization.data(withJSONObject: oldEnvelope)
        #expect(try ClassicSave.decode(oldData).map == .pampa)
    }

    @Test func classicLobbySupportsFiveThroughFifteenPlayers() throws {
        for count in ClassicGame.minimumPlayers...ClassicGame.maximumPlayers {
            let bots = Array(ClassicGame.defaultBotNames.prefix(count - 1))
            let game = ClassicGame(name: "Humano", seed: UInt64(count), botNames: bots)
            #expect(game.players.count == count)
            #expect(game.players.filter { $0.role == .assassin }.count == 1)
            #expect(game.players.filter { $0.role == .detective }.count == 1)
            #expect(game.players.filter { $0.role == .medic }.count == 1)
            #expect(game.players.filter { $0.role == .mercenary }.count == (count >= 7 ? 1 : 0))
            #expect(game.players.filter { $0.role == .villager }.count == count - (count >= 7 ? 4 : 3))
            #expect(try ClassicSave.decode(ClassicSave.encode(game)) == game)
        }
    }

    @Test func lobbyNamesAreCleanedAndMissingPlayersAreFilled() {
        let game = ClassicGame(name: "  Nacho  ", seed: 3, botNames: ["  Bot Uno  ", ""])
        #expect(game.players.count == ClassicGame.minimumPlayers)
        #expect(game.players.map(\.name) == ["Nacho", "Bot Uno", "Mora", "Lautaro", "Valen"])
    }

    @Test func testRoleAndDebugRulesAffectTheGame() throws {
        let bots = Array(ClassicGame.defaultBotNames.prefix(4))
        let options = LocalTestOptions(quickMatch: true, botsFollowAccusation: true,
                                       forceVoteTies: true, botsNeverKillHuman: true,
                                       botsNeverVoteHuman: true)
        var game = ClassicGame(name: "Humano", seed: 7, trainingRole: .mercenary,
                               testOptions: options, botNames: bots)
        #expect(game.human.role == .mercenary)
        #expect(game.players.filter { $0.role == .villager }.count == 1)
        #expect(game.effectiveTiming.transitionSeconds == 1)
        #expect(try ClassicSave.decode(ClassicSave.encode(game)).testOptions == options)
        let started = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(started)
        // The assassin is a bot and may not select the human under this debug setting.
        #expect(game.nightTarget != 0)

        game.phase = .discussion
        let accused = game.accuse(2, expectedPhaseIndex: game.phaseIndex)
        #expect(accused)
        let beganVoting = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(beganVoting)
        #expect(game.legalTargets(for: 1).contains(0) == false)
        let voted = game.advance(target: 2, expectedPhaseIndex: game.phaseIndex)
        #expect(voted)
        #expect(game.phase == .voteCount)
        #expect(game.tieCandidates.count == 2)
        #expect(game.votes.values.filter { $0 == game.tieCandidates[0] }.count ==
                game.votes.values.filter { $0 == game.tieCandidates[1] }.count)
    }

    @Test func forcedTiesRemainLegalAcrossTableSizes() {
        for count in [5, 7, 10, 12, 15] {
            let options = LocalTestOptions(forceVoteTies: true, botsNeverVoteHuman: true)
            var game = ClassicGame(name: "Humano", seed: UInt64(count),
                                   testOptions: options,
                                   botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
            game.phase = .voting
            let target = game.legalTargets(for: 0).first
            let voted = game.advance(target: target, expectedPhaseIndex: game.phaseIndex)
            #expect(voted)
            #expect(game.tieCandidates.count == 2)
            for (voter, choice) in game.votes {
                #expect(voter != choice)
                #expect(voter == 0 || choice != 0)
            }
        }
    }

    @Test func androidTimingPresetsAndLimitsReachTheSavedMatch() throws {
        #expect(GameTimingConfig.normal == .init(transitionSeconds: 4, nightSeconds: 40,
                                                 discussionSeconds: 120, votingSeconds: 20))
        #expect(GameTimingConfig.slow == .init(transitionSeconds: 6, nightSeconds: 90,
                                               discussionSeconds: 180, votingSeconds: 60))
        #expect(GameTimingConfig.fast == .init(transitionSeconds: 2, nightSeconds: 20,
                                               discussionSeconds: 60, votingSeconds: 15))
        let unsafe = GameTimingConfig(transitionSeconds: 99, nightSeconds: 1,
                                      discussionSeconds: 999, votingSeconds: 0)
        let game = ClassicGame(name: "Humano", seed: 8, timing: unsafe)
        #expect(game.timing == .init(transitionSeconds: 10, nightSeconds: 10,
                                     discussionSeconds: 180, votingSeconds: 10))
        #expect(try ClassicSave.decode(ClassicSave.encode(game)).timing == game.timing)

        var oldEnvelope = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(game)) as? [String: Any])
        var oldGame = try #require(oldEnvelope["game"] as? [String: Any])
        oldGame.removeValue(forKey: "timingConfig")
        oldEnvelope["game"] = oldGame
        let oldData = try JSONSerialization.data(withJSONObject: oldEnvelope)
        #expect(try ClassicSave.decode(oldData).timing == .normal)
    }

    @Test func androidAdvancedDefaultsAndReadingChoicesReachTheSavedMatch() throws {
        #expect(AdvancedGameConfig.standard == .init(revealRolesOnDeath: false,
                                                     showIndividualVotes: true,
                                                     roleReadingSeconds: 0))
        let game = ClassicGame(name: "Humano", seed: 9,
                               advanced: .init(revealRolesOnDeath: true,
                                               showIndividualVotes: false,
                                               roleReadingSeconds: 7))
        #expect(game.advanced == .init(revealRolesOnDeath: true,
                                       showIndividualVotes: false,
                                       roleReadingSeconds: 10))
        #expect(try ClassicSave.decode(ClassicSave.encode(game)).advanced == game.advanced)

        var oldEnvelope = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(game)) as? [String: Any])
        var oldGame = try #require(oldEnvelope["game"] as? [String: Any])
        oldGame.removeValue(forKey: "advancedConfig")
        oldEnvelope["game"] = oldGame
        let oldData = try JSONSerialization.data(withJSONObject: oldEnvelope)
        #expect(try ClassicSave.decode(oldData).advanced == .standard)
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
        let reachedMedic = game.skipPassiveNight(expectedPhaseIndex: game.phaseIndex)
        #expect(reachedMedic)
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
        let reachedDetective = detective.skipPassiveNight(expectedPhaseIndex: detective.phaseIndex)
        #expect(reachedDetective)
        #expect(!detective.legalTargets(for: 0).contains(0))
        let target = detective.legalTargets(for: 0).first!
        let accepted67 = detective.advance(target: target, expectedPhaseIndex: detective.phaseIndex)
        #expect(accepted67)
        #expect(detective.humanInvestigations.last?.suspicious == (detective.players[target].role == .assassin))
        #expect(detective.messages.allSatisfy { !$0.text.contains("Investigué") })
    }

    @Test func passiveNightCanBeSkippedButAnActionCannot() {
        var villager = ClassicGame(name: "Humano", seed: 3, trainingRole: .villager)
        let started = villager.advance(expectedPhaseIndex: villager.phaseIndex)
        #expect(started)
        #expect(villager.phase == .assassinNight)
        #expect(villager.legalTargets(for: 0).isEmpty)
        let skipped = villager.skipPassiveNight(expectedPhaseIndex: villager.phaseIndex)
        #expect(skipped)
        #expect(villager.phase == .dawn)
        #expect(villager.nightTarget != nil)

        var medic = ClassicGame(name: "Humano", seed: 3, trainingRole: .medic)
        let startedMedic = medic.advance(expectedPhaseIndex: medic.phaseIndex)
        #expect(startedMedic)
        let skippedToMedic = medic.skipPassiveNight(expectedPhaseIndex: medic.phaseIndex)
        #expect(skippedToMedic)
        #expect(medic.phase == .medicNight)
        let cannotSkip = medic.skipPassiveNight(expectedPhaseIndex: medic.phaseIndex)
        #expect(!cannotSkip)
    }

    // Android GameEngine.enterUnifiedNight resolves every bot-only phase after
    // the human acts, rather than keeping the local player waiting for each bot.
    @Test func humanNightActionImmediatelyReachesDawn() {
        for role in [RoleKey.assassin, .mercenary, .detective, .medic] {
            var game = ClassicGame(name: "Vos", seed: 42, trainingRole: role,
                                   botNames: Array(ClassicGame.defaultBotNames.prefix(6)))
            let started = game.advance(expectedPhaseIndex: game.phaseIndex)
            #expect(started)
            if game.legalTargets(for: 0).isEmpty {
                let skipped = game.skipPassiveNight(expectedPhaseIndex: game.phaseIndex)
                #expect(skipped)
            }
            #expect(game.human.role == role)
            #expect(game.isNight)
            let target = game.legalTargets(for: 0).first!
            let revision = game.phaseIndex
            let acted = game.advance(target: target, expectedPhaseIndex: revision)
            #expect(acted)
            #expect(game.phase == .dawn)
            #expect(!game.isNight)
            #expect(game.nightTarget != nil)
            #expect(game.protectedPlayer != nil)
            #expect(game.silencedPlayer != nil)
            if role == .detective { #expect(game.humanInvestigations.count == 1) }
        }
    }

    @Test func classicRoleTargetRulesMatchAndroid() {
        var assassin = fixed(.assassinNight)
        assassin.players[0] = .init(id: 0, name: "Humano", role: .assassin)
        assassin.players[1] = .init(id: 1, name: "Aldeano", role: .villager)
        #expect(!assassin.legalTargets(for: 0).contains(0))

        var detective = fixed(.detectiveNight)
        detective.players[0] = .init(id: 0, name: "Humano", role: .detective)
        detective.players[2] = .init(id: 2, name: "Aldeano", role: .villager)
        #expect(!detective.legalTargets(for: 0).contains(0))

        var medic = fixed(.medicNight)
        medic.players[0] = .init(id: 0, name: "Humano", role: .medic)
        medic.players[3] = .init(id: 3, name: "Aldeano", role: .villager)
        #expect(medic.legalTargets(for: 0).contains(0))

        var voting = fixed(.voting)
        voting.players[4].alive = false
        #expect(!voting.legalTargets(for: 0).contains(0))
        #expect(!voting.legalTargets(for: 0).contains(4))
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
