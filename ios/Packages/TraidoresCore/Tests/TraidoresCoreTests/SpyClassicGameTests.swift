import Testing
@testable import TraidoresCore

struct SpyClassicGameTests {
    // GameEngineTest.onlineSafeRoleCompositionKeepsDesertorOutUntilFourteenAndAddsSpyAtTen
    @Test func spyJoinsCompositionAtTen() {
        for count in ClassicGame.minimumPlayers...ClassicGame.maximumPlayers {
            let roles = ClassicGame.roles(for: count)
            #expect(roles.count == count)
            #expect(roles.filter { $0 == .spy }.count == (count >= 10 ? 1 : 0))
            #expect(roles.filter { $0 == .mercenary }.count == (count >= 7 ? 1 : 0))
        }
    }

    // GameEngineTest.spyLooksInnocentToPoliceInvestigation
    @Test func detectiveReadsSpyAsInnocent() {
        var game = ClassicGame(name: "Comisario", seed: 7, trainingRole: .detective,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(9)))
        let spy = game.players.first { $0.role == .spy }!.id
        game.phase = .detectiveNight
        let investigated = game.advance(target: spy, expectedPhaseIndex: game.phaseIndex)
        #expect(investigated)
        #expect(game.humanInvestigations.last?.target == spy)
        #expect(game.humanInvestigations.last?.suspicious == false)
    }

    // GameEngineTest.townWinsWhenNoAssassinOrSpyRemainsEvenIfMercenaryLives
    // and GameEngineTest.traitorRolesWinTogetherAtExactParity
    @Test func spyKeepsKillersAliveAndCountsForParity() {
        var game = ClassicGame(name: "Espía", seed: 3, trainingRole: .spy,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(9)))
        let assassin = game.players.first { $0.role == .assassin }!.id
        let mercenary = game.players.first { $0.role == .mercenary }!.id
        game.players[assassin].alive = false
        #expect(ClassicGame.winner(for: game.players) == nil)

        // Remove the added town specials too: this fixture must remain at exact 2-vs-2 parity.
        for player in game.players where [.villager, .mayor, .payador].contains(player.role) {
            game.players[player.id].alive = false
        }
        #expect(ClassicGame.winner(for: game.players) == .traitors)

        game.players[0].alive = false
        #expect(game.players[mercenary].alive)
        #expect(ClassicGame.winner(for: game.players) == .town)
    }

    // GameEngineTest.spyResolvesKillWhenNoAssassinIsAlive and
    // GameEngineTest.spyChoosesVictimAlongsideLivingAssassins
    @Test func spyChoosesNightVictimWithOrWithoutAssassin() {
        for assassinAlive in [true, false] {
            var game = ClassicGame(name: "Espía", seed: 12, trainingRole: .spy,
                                   botNames: Array(ClassicGame.defaultBotNames.prefix(9)))
            let assassin = game.players.first { $0.role == .assassin }!.id
            game.players[assassin].alive = assassinAlive
            let target = game.players.first { $0.role == .detective }!.id
            let started = game.advance(expectedPhaseIndex: game.phaseIndex)
            #expect(started)
            #expect(game.phase == .assassinNight)
            #expect(game.legalTargets(for: 0).contains(target))
            #expect(!game.legalTargets(for: 0).contains(assassin))
            let acted = game.advance(target: target, expectedPhaseIndex: game.phaseIndex)
            #expect(acted)
            #expect(game.nightTarget == target)
        }
    }

    @Test func botSpyCanKillAfterAssassinDies() {
        var game = ClassicGame(name: "Aldeano", seed: 12,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(9)))
        let assassin = game.players.first { $0.role == .assassin }!.id
        game.players[assassin].alive = false
        let started = game.advance(expectedPhaseIndex: game.phaseIndex)
        #expect(started)
        #expect(game.phase == .assassinNight)
        #expect(game.nightTarget != nil)
        #expect(game.players[game.nightTarget!].role != .spy)
    }

    @Test func olderTenPlayerSaveWithoutSpyStillLoads() throws {
        var game = ClassicGame(name: "Aldeano", seed: 8,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(9)))
        let spy = game.players.first { $0.role == .spy }!.id
        game.players[spy] = .init(id: spy, name: game.players[spy].name, role: .villager)
        #expect(try ClassicSave.decode(ClassicSave.encode(game)) == game)
    }
}
