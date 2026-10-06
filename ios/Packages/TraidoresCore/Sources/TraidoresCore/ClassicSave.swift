import Foundation

/// Versioned local persistence; intentionally unrelated to Android/Firebase wire documents.
public enum ClassicSave {
    private struct Envelope: Codable { let version: Int; let game: ClassicGame }
    public enum SaveError: Error { case unsupportedOrInvalid }

    public static func encode(_ game: ClassicGame) throws -> Data {
        try JSONEncoder().encode(Envelope(version: 1, game: game))
    }

    public static func decode(_ data: Data) throws -> ClassicGame {
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        let game = envelope.game
        let phases: [GamePhase] = [.assignment, .assassinNight, .mercenaryNight, .detectiveNight, .medicNight, .oracleNight,
                                   .dawn, .discussion, .counterpoint, .voting, .voteCount, .tieVote, .mayorTieBreak, .result]
        let playerCount = game.players.count
        let currentRoles = ClassicGame.roles(for: playerCount, map: game.map)
        let previousRoles = currentRoles.map { role in
            [.mayor, .payador, .oracle, .jester, .deserter].contains(role) ? RoleKey.villager : role
        }
        let legacyRoles: [RoleKey] = [.assassin, .detective, .medic] +
            Array(repeating: .villager, count: max(0, playerCount - 3))
        // Older decks may lack the second assassin (13+) or the spy (10+).
        // Apply training after each historical deck is built: training can itself
        // introduce a spy into a deck that predates the default spy slot.
        let roleDecks: [[RoleKey]] = [currentRoles, previousRoles, legacyRoles].flatMap { base in
            [false, true].flatMap { oldAssassinCount in
                [false, true].map { oldSpyCount in
                    var deck = base
                    if oldAssassinCount && playerCount >= 13,
                       let extra = deck.lastIndex(of: .assassin) { deck[extra] = .villager }
                    if oldSpyCount && playerCount >= 10,
                       let spy = deck.firstIndex(of: .spy) { deck[spy] = .villager }
                    if let trainingRole = game.trainingRoleConfig,
                       ClassicGame.supportedTrainingRoles.contains(trainingRole),
                       !deck.contains(trainingRole),
                       let villager = deck.firstIndex(of: .villager) {
                        deck[villager] = trainingRole
                    }
                    return deck
                }
            }
        }
        let savedRoles = game.players.map(\.role.rawValue).sorted()
        func valid(_ id: Int) -> Bool { (0..<playerCount).contains(id) }
        guard envelope.version == 1,
              (ClassicGame.minimumPlayers...ClassicGame.maximumPlayers).contains(playerCount),
              game.players.map(\.id) == Array(0..<playerCount),
              roleDecks.contains(where: { savedRoles == $0.map(\.rawValue).sorted() }),
              game.players.allSatisfy({ !$0.name.isEmpty && $0.name.count <= 18 }),
              phases.contains(game.phase), game.round > 0, game.phaseIndex >= 0,
              game.deserterReconsiderationPending ?
                (game.winner == nil && game.human.alive && game.human.role == .deserter &&
                 game.deserterReconsiderationAvailable &&
                 ClassicGame.winner(for: game.players, deserterTeam: game.deserterTeam) == .traitors) :
                game.winner == ClassicGame.winner(for: game.players, deserterTeam: game.deserterTeam),
              game.nightTarget.map(valid) ?? true, game.protectedPlayer.map(valid) ?? true,
              game.eliminationTarget.map(valid) ?? true,
              game.votes.allSatisfy({ valid($0.key) && valid($0.value) && $0.key != $0.value }),
              game.tieCandidates.allSatisfy(valid), game.declaredDetectives.allSatisfy(valid),
              game.suspicion.keys.allSatisfy(valid), (0...3).contains(game.voteRound),
              game.revealedMayorID.map({ valid($0) && game.players[$0].role == .mayor }) ?? true,
              game.deserterTeam != .neutral,
              game.phase != .counterpoint || (game.payadorUsed && game.contrapuntoParticipants.count == 2 && game.map == .pampa && game.living.contains { $0.role == .payador }),
              game.phase != .mayorTieBreak || (game.tieCandidates.count >= 2 && game.living.contains { $0.role == .mayor }),
              game.phase != .oracleNight || (!game.oracleUsed && game.round > 1 && game.map == .greece && game.living.contains { $0.role == .oracle } && game.players.contains { !$0.alive }),
              game.silencedPlayer.map(valid) ?? true,
              game.lastSilencedRounds.allSatisfy({ valid($0.key) && $0.value > 0 && $0.value <= game.round }),
              game.contrapuntoParticipants.count <= 2,
              Set(game.contrapuntoParticipants).count == game.contrapuntoParticipants.count,
              game.contrapuntoParticipants.allSatisfy(valid),
              game.payadorPointedPlayer.map({ game.contrapuntoParticipants.contains($0) }) ?? true,
              game.oracleGuest.map({ valid($0) && !game.players[$0].alive && game.oracleUsed }) ?? true,
              game.specialVictories.allSatisfy({ valid($0.playerID) && game.players[$0.playerID].role == .jester && !game.players[$0.playerID].alive && $0.reason == "bufon_expulsado" && $0.round > 0 && $0.round <= game.round }),
              Set(game.specialVictories.map(\.playerID)).count == game.specialVictories.count,
              game.investigations.allSatisfy({ valid($0.target) && valid($0.investigator) }),
              game.messages.allSatisfy({ $0.speaker.map(valid) ?? true }),
              Set(game.messages.map(\.id)).count == game.messages.count
        else { throw SaveError.unsupportedOrInvalid }
        return game
    }
}
