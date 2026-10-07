import Testing
@testable import TraidoresCore

struct AssassinClassicGameTests {
    // GameModels.kt: RECOMMENDED adds a second assassin at 13, while
    // maxAssassinsFor permits 2 at 8 and 3 at 13 for custom compositions.
    @Test func recommendedLocalDeckHasTwoAssassinsFromThirteen() {
        for count in ClassicGame.minimumPlayers...ClassicGame.maximumPlayers {
            let deck = ClassicGame.roles(for: count)
            #expect(deck.count == count)
            #expect(deck.filter { $0 == .assassin }.count == (count >= 13 ? 2 : 1))
            #expect(deck.filter { $0 == .spy }.count == (count >= 10 ? 1 : 0))
        }
        let trained = ClassicGame(name: "Humano", seed: 7, trainingRole: .assassin,
                                  botNames: Array(ClassicGame.defaultBotNames.prefix(12)))
        #expect(trained.human.role == .assassin)
        #expect(trained.players.filter { $0.role == .assassin }.count == 2)
    }

    // GameEngineTest.multipleAssassinsVoteForOneNightVictim and
    // botTraitorsFollowHumanNightTargetOnNormalAndHard.
    @Test func livingKillersCoordinateOnHumanTarget() {
        for difficulty in BotDifficulty.allCases {
            for assassinCount in [2, 3] {
                var game = ClassicGame(name: "Humano", seed: 12, trainingRole: .assassin,
                                       difficulty: difficulty,
                                       botNames: Array(ClassicGame.defaultBotNames.prefix(12)))
                if assassinCount == 3 {
                    let villager = game.players.first { $0.role == .villager }!.id
                    game.players[villager] = .init(id: villager, name: game.name(villager), role: .assassin)
                }
                game.phase = .assassinNight
                let killers = game.living.filter { $0.role == .assassin || $0.role == .spy }
                let target = game.players.first { $0.role == .detective }!.id
                #expect(killers.count == assassinCount + 1)
                for killer in killers {
                    #expect(game.legalTargets(for: killer.id).contains(target))
                    for teammate in game.living where [.assassin, .mercenary, .spy].contains(teammate.role) {
                        #expect(!game.legalTargets(for: killer.id).contains(teammate.id))
                    }
                }
                let advanced = game.advance(target: target, expectedPhaseIndex: game.phaseIndex)
                #expect(advanced)
                #expect(game.nightTarget == target)
            }
        }
    }

    // GameRules.winnerFor: a living spy or assassin keeps the killers alive;
    // mercenary counts toward traitor parity but cannot sustain the faction alone.
    @Test func multipleAssassinsAndSpyDetermineFactionVictory() {
        var game = ClassicGame(name: "Humano", seed: 4,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(12)))
        let assassins = game.players.filter { $0.role == .assassin }.map(\.id)
        let spy = game.players.first { $0.role == .spy }!.id
        let mercenary = game.players.first { $0.role == .mercenary }!.id
        let town = game.players.filter { ![.assassin, .spy, .mercenary].contains($0.role) }.map(\.id)

        for id in town.dropFirst(4) { game.players[id].alive = false }
        #expect(ClassicGame.winner(for: game.players) == .traitors) // 4 vs 4
        game.players[assassins[0]].alive = false
        #expect(ClassicGame.winner(for: game.players) == nil) // 3 vs 4
        game.players[town[0]].alive = false
        #expect(ClassicGame.winner(for: game.players) == .traitors) // 3 vs 3
        game.players[assassins[1]].alive = false
        #expect(ClassicGame.winner(for: game.players) == nil) // spy still kills
        game.players[spy].alive = false
        #expect(game.players[mercenary].alive)
        #expect(ClassicGame.winner(for: game.players) == .town)
    }

    @Test func oldThirteenPlayerSavesWithOneAssassinStillLoad() throws {
        let original = ClassicGame(name: "Humano", seed: 8,
                                   botNames: Array(ClassicGame.defaultBotNames.prefix(12)))
        for oldSpyDeck in [false, true] {
            var game = original
            let extraAssassin = game.players.last { $0.role == .assassin }!.id
            game.players[extraAssassin] = .init(id: extraAssassin, name: game.name(extraAssassin), role: .villager)
            if oldSpyDeck {
                let spy = game.players.first { $0.role == .spy }!.id
                game.players[spy] = .init(id: spy, name: game.name(spy), role: .villager)
            }
            #expect(try ClassicSave.decode(ClassicSave.encode(game)) == game)
        }
    }
}
