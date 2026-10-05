import Foundation
import Testing
@testable import TraidoresCore

struct ClassicSpecialRolesTests {
    private func game(_ role: RoleKey, phase: GamePhase = .discussion) -> ClassicGame {
        let map: GameMap = role == .oracle ? .greece : role == .jester ? .medieval : .pampa
        let count = role == .deserter ? 14 : 8
        var game = ClassicGame(name: "Vos", seed: 42, trainingRole: role, map: map,
                               botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
        game.phase = phase
        return game
    }

    @Test func recommendedDeckMatchesAndroidOnEveryMapAndCount() throws {
        for map in GameMap.allCases {
            for count in 5...15 {
                let deck = ClassicGame.roles(for: count, map: map)
                #expect(deck.count == count)
                #expect(deck.filter { $0 == .assassin }.count == (count >= 13 ? 2 : 1))
                #expect(deck.contains(.mercenary) == (count >= 7))
                #expect(deck.contains(.mayor) == (count >= 8))
                #expect(deck.contains(.spy) == (count >= 10))
                #expect(deck.contains(.deserter) == (count >= 14))
                for role in [RoleKey.payador, .oracle, .jester] {
                    let definition = try #require(RoleCatalog.all.first { $0.id == role })
                    #expect(deck.contains(role) == (count >= 8 && definition.exclusiveMap == map))
                }
                let match = ClassicGame(name: "Vos", seed: UInt64(count), map: map,
                                        botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
                #expect(match.players.map(\.role.rawValue).sorted() == deck.map(\.rawValue).sorted())
                #expect(try ClassicSave.decode(ClassicSave.encode(match)) == match)
            }
        }
    }

    @Test func mayorRevealWeightsBothBallotsAndRejectsDuplicateStaleActions() {
        var match = game(.mayor)
        let accepted38 = !match.revealMayor(expectedPhaseIndex: 99)
        #expect(accepted38)
        let accepted39 = match.revealMayor(expectedPhaseIndex: 0)
        #expect(accepted39)
        #expect(match.revealedMayorID == 0)
        let accepted41 = !match.revealMayor(expectedPhaseIndex: 0)
        #expect(accepted41)
        for phase in [GamePhase.voting, .tieVote] {
            match.phase = phase
            match.tieCandidates = [1, 2]
            match.recordVotes([0: 1, 3: 2])
            #expect(match.eliminationTarget == 1)
        }
    }

    @Test func mayorDecidesOnlyAfterSecondTieAndMustReveal() {
        var match = game(.mayor, phase: .voting)
        match.recordVotes([0: 1, 3: 2])
        let accepted53 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted53)
        #expect(match.phase == .tieVote)
        match.recordVotes([0: 1, 3: 2])
        let accepted56 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted56)
        #expect(match.phase == .mayorTieBreak)
        let accepted58 = !match.chooseMayorTie(1, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted58)
        let accepted59 = match.revealMayor(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted59)
        let accepted60 = !match.chooseMayorTie(7, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted60)
        let revision = match.phaseIndex
        let accepted62 = match.chooseMayorTie(1, expectedPhaseIndex: revision)
        #expect(accepted62)
        #expect(match.phase == .voteCount)
        #expect(match.eliminationTarget == 1)
        let accepted65 = !match.chooseMayorTie(2, expectedPhaseIndex: revision)
        #expect(accepted65)
        let accepted66 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted66)
        #expect(match.phase == .result)
    }

    @Test func mayorProtectsHimselfInSecondTieLikeAndroid() {
        var match = game(.mayor, phase: .tieVote)
        match.tieCandidates = [0, 1]
        match.recordVotes([2: 0, 3: 1])
        let accepted74 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted74)
        #expect(match.revealedMayorID == 0)
        #expect(match.eliminationTarget == 1)
        #expect(match.phase == .voteCount)
    }

    @Test func deadOrWrongRoleCannotRevealMayor() {
        var match = game(.mayor)
        match.players[0].alive = false
        let accepted83 = !match.revealMayor(expectedPhaseIndex: 0)
        #expect(accepted83)
        var other = game(.payador)
        let accepted85 = !other.revealMayor(expectedPhaseIndex: 0)
        #expect(accepted85)
    }

    @Test func deserterMustChooseInitiallyAndReconsiderOnlyOnceAtCeilingThreshold() {
        var match = game(.deserter, phase: .assignment)
        #expect(match.needsInitialDeserterChoice)
        let accepted91 = !match.advance(expectedPhaseIndex: 0)
        #expect(accepted91)
        let accepted92 = !match.chooseDeserterTeam(.neutral, expectedPhaseIndex: 0)
        #expect(accepted92)
        let accepted93 = !match.chooseDeserterTeam(.town, expectedPhaseIndex: 1)
        #expect(accepted93)
        let accepted94 = match.chooseDeserterTeam(.town, expectedPhaseIndex: 0)
        #expect(accepted94)
        #expect(!match.deserterReconsiderationAvailable)
        let accepted96 = !match.chooseDeserterTeam(.traitors, expectedPhaseIndex: 0)
        #expect(accepted96)
        for id in 10..<14 { match.players[id].alive = false }
        #expect(match.deserterReconsiderationAvailable) // ceil(14 * 2/3) = 10
        let accepted99 = match.chooseDeserterTeam(.traitors, expectedPhaseIndex: 0)
        #expect(accepted99)
        #expect(match.deserterReconsiderationUsed)
        let accepted101 = !match.chooseDeserterTeam(.town, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted101)
    }

    @Test func deserterWinRequiresFinalSideAndSurvival() {
        var match = game(.deserter)
        match.winner = .traitors
        match.deserterTeam = .town
        #expect(!match.humanWon)
        match.deserterTeam = .traitors
        #expect(match.humanWon)
        match.players[0].alive = false
        #expect(!match.humanWon)
        #expect(!match.deserterReconsiderationAvailable)
        let accepted114 = !match.chooseDeserterTeam(.town, expectedPhaseIndex: 0)
        #expect(accepted114)
    }

    @Test func neutralsNeverCountForAndroidParityAndUnchosenDeserterBlocksTraitorWin() {
        let players: [ClassicPlayer] = [
            .init(id: 0, name: "A", role: .assassin), .init(id: 1, name: "D", role: .deserter),
            .init(id: 2, name: "B", role: .jester), .init(id: 3, name: "P", role: .villager)
        ]
        #expect(ClassicGame.winner(for: players) == nil)
        #expect(ClassicGame.winner(for: players, deserterTeam: .town) == .traitors)
        #expect(ClassicGame.winner(for: players, deserterTeam: .traitors) == .traitors)
        #expect(ClassicGame.winner(for: Array(players.dropFirst())) == .town)
    }

    @Test func deserterAlignedWithTraitorsCannotBeNightTarget() throws {
        var match = game(.deserter, phase: .assassinNight)
        let killer = try #require(match.players.first { $0.role == .assassin }?.id)
        match.deserterTeam = .town
        #expect(match.legalTargets(for: killer).contains(0))
        match.deserterTeam = .traitors
        #expect(!match.legalTargets(for: killer).contains(0))
    }

    @Test func payadorRestrictsSpeechAndAddsVoteToBothBallotsOnlyToday() {
        var match = game(.payador)
        let accepted139 = !match.chooseContrapuntoPlayer(0, expectedPhaseIndex: 0)
        #expect(accepted139)
        let accepted140 = !match.chooseContrapuntoPlayer(99, expectedPhaseIndex: 0)
        #expect(accepted140)
        let accepted141 = match.chooseContrapuntoPlayer(1, expectedPhaseIndex: 0)
        #expect(accepted141)
        let accepted142 = !match.chooseContrapuntoPlayer(1, expectedPhaseIndex: 0)
        #expect(accepted142)
        let accepted143 = match.chooseContrapuntoPlayer(2, expectedPhaseIndex: 0)
        #expect(accepted143)
        #expect(match.phase == .counterpoint)
        #expect(match.payadorUsed)
        #expect(!match.canSpeak(0))
        #expect(match.canSpeak(1))
        #expect(!match.canSpeak(3))
        let accepted149 = !match.sendPublicMessage("Hola", expectedPhaseIndex: match.phaseIndex)
        #expect(accepted149)
        let accepted150 = !match.pointContrapuntoPlayer(3, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted150)
        let accepted151 = match.pointContrapuntoPlayer(1, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted151)
        #expect(match.phase == .voting)
        for phase in [GamePhase.voting, .tieVote] {
            match.phase = phase; match.tieCandidates = [1, 2]
            match.recordVotes([3: 1, 4: 2])
            #expect(match.eliminationTarget == 1)
        }
        match.phase = .discussion
        let accepted159 = !match.chooseContrapuntoPlayer(3, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted159)
        match.phase = .result; match.eliminationTarget = nil
        let accepted161 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted161)
        #expect(match.payadorPointedPlayer == nil)
        #expect(match.contrapuntoParticipants.isEmpty)
        #expect(match.payadorUsed)
    }

    @Test func contrapuntoParticipantsCanTalkButOthersCannotAccuseOrShare() throws {
        var match = game(.mayor, phase: .counterpoint)
        match.contrapuntoParticipants = [0, 1]
        let accepted170 = match.sendPublicMessage("Escuchá mi versión", expectedPhaseIndex: 0)
        #expect(accepted170)
        let accepted171 = match.accuse(1, expectedPhaseIndex: 0)
        #expect(accepted171)
        match.contrapuntoParticipants = [1, 2]
        let accepted173 = !match.sendPublicMessage("Hola", expectedPhaseIndex: 0)
        #expect(accepted173)
        let accepted174 = !match.accuse(1, expectedPhaseIndex: 0)
        #expect(accepted174)
    }

    @Test func oracleInvokesOnlyDeadPlayersAfterFirstNightAndOnlyOnce() throws {
        var match = game(.oracle, phase: .oracleNight)
        let target = try #require(match.players.first { $0.role == .villager }?.id)
        match.players[target].alive = false
        let accepted181 = !match.invokeOracle(target, expectedPhaseIndex: 0)
        #expect(accepted181)
        match.round = 2
        let accepted183 = !match.invokeOracle(target, expectedPhaseIndex: 99)
        #expect(accepted183)
        let accepted184 = !match.invokeOracle(0, expectedPhaseIndex: 0)
        #expect(accepted184)
        let accepted185 = match.invokeOracle(target, expectedPhaseIndex: 0)
        #expect(accepted185)
        #expect(match.oracleUsed)
        #expect(match.phase == .dawn)
        #expect(!match.players[target].alive)
        let accepted189 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted189)
        #expect(match.oracleGuest == target)
        #expect(match.canSpeak(target))
        #expect(match.legalTargets(for: target).isEmpty)
        #expect(try ClassicSave.decode(ClassicSave.encode(match)) == match)
        match.phase = .oracleNight
        let accepted195 = !match.invokeOracle(target, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted195)
    }

    @Test func oracleSkipKeepsPowerAndGuestExpiresAtNight() {
        var match = game(.oracle, phase: .oracleNight)
        match.round = 2
        let accepted201 = match.invokeOracle(nil, expectedPhaseIndex: 0)
        #expect(accepted201)
        #expect(!match.oracleUsed)
        match.players[1].alive = false
        match.oracleGuest = 1; match.oracleUsed = true
        match.phase = .result
        let accepted206 = match.advance(expectedPhaseIndex: match.phaseIndex)
        #expect(accepted206)
        #expect(match.oracleGuest == nil)
    }

    @Test func deadHumanOracleGuestCanSpeakButCannotUseAbilitiesOrVote() {
        var match = game(.mayor)
        match.players[0].alive = false; match.oracleGuest = 0; match.oracleUsed = true
        match.investigations = [.init(round: 1, investigator: 0, target: 1, suspicious: true)]
        let accepted214 = match.sendPublicMessage("Volví para hablar con vos", expectedPhaseIndex: 0)
        #expect(accepted214)
        let accepted215 = !match.revealMayor(expectedPhaseIndex: 0)
        #expect(accepted215)
        let accepted216 = !match.shareInvestigation(expectedPhaseIndex: 0)
        #expect(accepted216)
        match.phase = .voting
        #expect(match.legalTargets(for: 0).isEmpty)
        let accepted219 = !match.sendPublicMessage("Hola", expectedPhaseIndex: 0)
        #expect(accepted219)
    }

    @Test func jesterExpulsionRecordsPersonalVictoryAndContinues() throws {
        var match = game(.jester, phase: .result)
        match.eliminationTarget = 0
        let accepted225 = match.advance(expectedPhaseIndex: 0)
        #expect(accepted225)
        #expect(!match.human.alive)
        #expect(match.specialVictories == [.init(playerID: 0, reason: "bufon_expulsado", round: 1)])
        #expect(match.humanWon)
        #expect(match.winner == nil)
        #expect(match.round == 2)
        #expect(try ClassicSave.decode(ClassicSave.encode(match)) == match)
    }

    @Test func jesterNightDeathNeverWinsAndTownVictoryMayEndSameExpulsion() throws {
        var night = game(.jester, phase: .dawn)
        night.nightTarget = 0
        let accepted237 = night.advance(expectedPhaseIndex: 0)
        #expect(accepted237)
        #expect(night.specialVictories.isEmpty)
        #expect(!night.humanWon)
        var match = game(.jester, phase: .result)
        for id in match.players.filter({ [.assassin, .spy].contains($0.role) }).map(\.id) { match.players[id].alive = false }
        match.eliminationTarget = 0
        let accepted243 = match.advance(expectedPhaseIndex: 0)
        #expect(accepted243)
        #expect(match.winner == .town)
        #expect(match.humanWon)
        #expect(match.specialVictories.count == 1)
    }

    @Test func allAbilityGuardsRejectFinishedGamesWithoutMutation() {
        for role in [RoleKey.mayor, .deserter, .payador, .oracle] {
            var match = game(role)
            match.winner = .town
            let before = match
            let accepted254 = !match.revealMayor(expectedPhaseIndex: 0)
            #expect(accepted254)
            let accepted255 = !match.chooseDeserterTeam(.town, expectedPhaseIndex: 0)
            #expect(accepted255)
            let accepted256 = !match.chooseContrapuntoPlayer(1, expectedPhaseIndex: 0)
            #expect(accepted256)
            let accepted257 = !match.pointContrapuntoPlayer(1, expectedPhaseIndex: 0)
            #expect(accepted257)
            let accepted258 = !match.invokeOracle(1, expectedPhaseIndex: 0)
            #expect(accepted258)
            #expect(match == before)
        }
    }

    @Test func oldFormatSaveDecodesWithEveryNewPropertyAbsent() throws {
        for count in [5, 8, 14] {
            var match = ClassicGame(name: "Vos", seed: 1, botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
            let oldRoles: [RoleKey] = [.assassin, .detective, .medic] + Array(repeating: .villager, count: count - 3)
            match.players = oldRoles.enumerated().map { .init(id: $0.offset, name: "J\($0.offset)", role: $0.element) }
            var envelope = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(match)) as? [String: Any])
            var body = try #require(envelope["game"] as? [String: Any])
            for key in ["revealedMayorID", "deserterTeam", "deserterReconsiderationUsed", "payadorUsed", "contrapuntoParticipants", "payadorPointedPlayer", "oracleUsed", "oracleGuest", "specialVictories", "silencedPlayer", "lastSilencedRounds"] { body.removeValue(forKey: key) }
            envelope["game"] = body
            let restored = try ClassicSave.decode(JSONSerialization.data(withJSONObject: envelope))
            #expect(restored.revealedMayorID == nil && restored.deserterTeam == nil)
            #expect(!restored.deserterReconsiderationUsed && !restored.payadorUsed && !restored.oracleUsed)
            #expect(restored.contrapuntoParticipants.isEmpty && restored.specialVictories.isEmpty)
            #expect(restored.payadorPointedPlayer == nil && restored.oracleGuest == nil && restored.silencedPlayer == nil)
        }
    }

    @Test func botsDecideSpecialRolesDeterministicallyAndMatchesFinish() throws {
        for map in GameMap.allCases {
            for seed in 1...12 {
                var first = ClassicGame(name: "Vos", seed: UInt64(seed), map: map, botNames: ClassicGame.defaultBotNames)
                var second = first
                for _ in 0..<250 {
                    if first.winner != nil { break }
                    for index in 0..<2 {
                        var match = index == 0 ? first : second
                        if match.needsInitialDeserterChoice { _ = match.chooseDeserterTeam(.town, expectedPhaseIndex: match.phaseIndex) }
                        if match.phase == .mayorTieBreak { _ = match.revealMayor(expectedPhaseIndex: match.phaseIndex) }
                        let target = match.legalTargets(for: 0).first
                        _ = match.advance(target: target, expectedPhaseIndex: match.phaseIndex)
                        if index == 0 { first = match } else { second = match }
                    }
                    #expect(first == second)
                    #expect(try ClassicSave.decode(ClassicSave.encode(first)) == first)
                }
                #expect(first.winner != nil)
            }
        }
    }
    private func botGame(_ role: RoleKey, phase: GamePhase) -> ClassicGame {
        var match = game(role, phase: phase)
        let original = match.players[1]
        match.players[0] = .init(id: 0, name: "Vos", role: original.role)
        match.players[1] = .init(id: 1, name: original.name, role: role)
        return match
    }

    @Test func botsRevealMayorFromSecondDayAndStartAndResolveContrapunto() {
        var mayor = botGame(.mayor, phase: .dawn)
        mayor.round = 2
        let accepted = mayor.advance(expectedPhaseIndex: 0)
        #expect(accepted)
        #expect(mayor.revealedMayorID == 1)
        var payador = botGame(.payador, phase: .dawn)
        let started = payador.advance(expectedPhaseIndex: 0)
        #expect(started)
        #expect(payador.phase == .counterpoint)
        #expect(payador.contrapuntoParticipants.count == 2)
        #expect(!payador.contrapuntoParticipants.contains(1))
        #expect(!payador.canSpeak(1))
        let finished = payador.advance(expectedPhaseIndex: payador.phaseIndex)
        #expect(finished)
        #expect(payador.phase == .voting)
        #expect(payador.contrapuntoParticipants.contains(payador.payadorPointedPlayer ?? -1))
    }

    @Test func botOracleInvokesDeadGuestAndBotDeserterReconsiders() {
        var oracle = botGame(.oracle, phase: .assignment)
        oracle.round = 2
        oracle.players[7].alive = false
        for _ in 0..<6 {
            if oracle.phase == .dawn { break }
            let target = oracle.legalTargets(for: 0).first
            _ = oracle.advance(target: target, expectedPhaseIndex: oracle.phaseIndex)
        }
        #expect(oracle.oracleUsed)
        #expect(oracle.oracleGuest == 7)
        var deserter = botGame(.deserter, phase: .dawn)
        deserter.deserterTeam = .traitors
        for id in 10..<14 { deserter.players[id].alive = false }
        let accepted = deserter.advance(expectedPhaseIndex: 0)
        #expect(accepted)
        #expect(deserter.deserterReconsiderationUsed)
        #expect(deserter.deserterTeam != nil)
    }

    @Test func roleAbilitiesRejectWrongPhasesDeadActorsAndStaleRevisions() {
        var payador = game(.payador)
        let before = payador
        let stale = payador.chooseContrapuntoPlayer(1, expectedPhaseIndex: 99)
        #expect(!stale && payador == before)
        payador.players[0].alive = false
        let dead = payador.chooseContrapuntoPlayer(1, expectedPhaseIndex: 0)
        #expect(!dead)
        var oracle = game(.oracle, phase: .oracleNight)
        oracle.round = 2; oracle.players[1].alive = false; oracle.players[0].alive = false
        let deadOracle = oracle.invokeOracle(1, expectedPhaseIndex: 0)
        #expect(!deadOracle)
        var deserter = game(.deserter, phase: .discussion)
        let wrongPhase = deserter.chooseDeserterTeam(.town, expectedPhaseIndex: 0)
        #expect(!wrongPhase)
        var mayor = game(.mayor, phase: .dawn)
        let wrongMayorPhase = mayor.revealMayor(expectedPhaseIndex: 0)
        #expect(!wrongMayorPhase)
    }

    @Test func specialVictoryNeverAwardedToAlreadyDeadJester() {
        var match = game(.jester, phase: .result)
        match.players[0].alive = false
        match.eliminationTarget = 0
        let accepted = match.advance(expectedPhaseIndex: 0)
        #expect(accepted)
        #expect(match.specialVictories.isEmpty)
    }

    @Test func savedMayorDecisionAndContrapuntoRestoreExactly() throws {
        var mayor = game(.mayor, phase: .tieVote)
        mayor.tieCandidates = [1, 2]
        mayor.recordVotes([0: 1, 3: 2])
        _ = mayor.advance(expectedPhaseIndex: mayor.phaseIndex)
        #expect(try ClassicSave.decode(ClassicSave.encode(mayor)) == mayor)
        var payador = game(.payador)
        _ = payador.chooseContrapuntoPlayer(1, expectedPhaseIndex: 0)
        _ = payador.chooseContrapuntoPlayer(2, expectedPhaseIndex: 0)
        #expect(try ClassicSave.decode(ClassicSave.encode(payador)) == payador)
    }

    @Test func saveRejectsMalformedNewState() throws {
        var match = game(.payador)
        match.phase = .counterpoint
        #expect(throws: (any Error).self) { try ClassicSave.decode(ClassicSave.encode(match)) }
        match.phase = .discussion
        match.contrapuntoParticipants = [99]
        #expect(throws: (any Error).self) { try ClassicSave.decode(ClassicSave.encode(match)) }
    }

    @Test func recommendedKillersCoordinateAndSpyLooksInnocent() throws {
        var match = ClassicGame(name: "Vos", seed: 1, trainingRole: .spy,
                                botNames: ClassicGame.defaultBotNames)
        _ = match.advance(expectedPhaseIndex: 0)
        #expect(match.phase == .assassinNight)
        let target = try #require(match.legalTargets(for: 0).first)
        let accepted = match.advance(target: target, expectedPhaseIndex: match.phaseIndex)
        #expect(accepted)
        #expect(match.nightTarget == target)
        var detective = ClassicGame(name: "Vos", seed: 1, trainingRole: .detective,
                                    botNames: ClassicGame.defaultBotNames)
        _ = detective.advance(expectedPhaseIndex: 0)
        let spy = try #require(detective.players.first { $0.role == .spy }?.id)
        _ = detective.skipPassiveNight(expectedPhaseIndex: detective.phaseIndex)
        let investigated = detective.advance(target: spy, expectedPhaseIndex: detective.phaseIndex)
        #expect(investigated)
        #expect(detective.humanInvestigations.last?.suspicious == false)
    }

    @Test func mercenarySilenceKeepsCurrentIOSPortTargetRules() {
        var match = game(.mercenary, phase: .mercenaryNight)
        let accepted = match.advance(target: 1, expectedPhaseIndex: 0)
        #expect(accepted)
        match.phase = .discussion
        #expect(!match.canSpeak(1))
        match.phase = .voting
        #expect(match.legalTargets(for: 1).isEmpty)
        match.phase = .mercenaryNight; match.round = 2
        #expect(match.legalTargets(for: 0).contains(1))
        match.round = 3
        #expect(match.legalTargets(for: 0).contains(1))
    }

    @Test func iosPortSaveWithoutSpecialRolesPreservesExistingPrivateState() throws {
        for count in [7, 10, 13, 15] {
            var match = ClassicGame(name: "Vos", seed: UInt64(count), trainingRole: .spy,
                                    testOptions: .init(botsNeverKillHuman: true),
                                    botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
            for player in match.players where [.mayor, .payador, .deserter].contains(player.role) {
                match.players[player.id] = .init(id: player.id, name: player.name, role: .villager)
            }
            match.phase = .assassinNight
            match.humanAccusation = 2
            _ = match.sendTraitorMessage("Elegimos juntos", expectedPhaseIndex: match.phaseIndex)
            var envelope = try #require(JSONSerialization.jsonObject(with: ClassicSave.encode(match)) as? [String: Any])
            var body = try #require(envelope["game"] as? [String: Any])
            for key in ["revealedMayorID", "deserterTeam", "deserterReconsiderationUsed", "payadorUsed",
                        "contrapuntoParticipants", "payadorPointedPlayer", "oracleUsed", "oracleGuest", "specialVictories"] {
                body.removeValue(forKey: key)
            }
            envelope["game"] = body
            let restored = try ClassicSave.decode(JSONSerialization.data(withJSONObject: envelope))
            #expect(restored.players == match.players)
            #expect(restored.privateChatMessages == match.privateChatMessages)
            #expect(restored.humanAccusation == 2)
            #expect(restored.trainingRoleConfig == .spy)
            #expect(restored.testOptions == match.testOptions)
            #expect(restored.specialVictories.isEmpty && !restored.payadorUsed && !restored.oracleUsed)
        }
    }

    @Test func forcedTiesAccountForMayorWeightAndContrapuntoVote() {
        for count in [8, 10, 15] {
            var match = ClassicGame(name: "Vos", seed: UInt64(count), trainingRole: .mayor,
                                    testOptions: .init(forceVoteTies: true),
                                    botNames: Array(ClassicGame.defaultBotNames.prefix(count - 1)))
            match.phase = .voting
            match.revealedMayorID = 0
            match.payadorPointedPlayer = 1
            let accepted = match.advance(target: 1, expectedPhaseIndex: match.phaseIndex)
            #expect(accepted)
            #expect(match.tieCandidates.count == 2)
            #expect(Set(match.tieCandidates.compactMap { match.voteTotals[$0] }).count == 1)
        }
    }

}
