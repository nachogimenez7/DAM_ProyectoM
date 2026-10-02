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
        let phases: [GamePhase] = [.assignment, .assassinNight, .mercenaryNight, .detectiveNight, .medicNight,
                                   .dawn, .discussion, .voting, .voteCount, .tieVote, .result]
        let playerCount = game.players.count
        let currentRoles = ClassicGame.roles(for: playerCount)
        // Older decks may lack the second assassin (13+) or the spy (10+).
        // Apply training after each historical deck is built: training can itself
        // introduce a spy into a deck that predates the default spy slot.
        let roleDecks: [[RoleKey]] = [false, true].flatMap { oldAssassinCount in
            [false, true].map { oldSpyCount in
                var deck = currentRoles
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
        let savedRoles = game.players.map(\.role.rawValue).sorted()
        func valid(_ id: Int) -> Bool { (0..<playerCount).contains(id) }
        guard envelope.version == 1,
              (ClassicGame.minimumPlayers...ClassicGame.maximumPlayers).contains(playerCount),
              game.players.map(\.id) == Array(0..<playerCount),
              roleDecks.contains(where: { savedRoles == $0.map(\.rawValue).sorted() }),
              game.players.allSatisfy({ !$0.name.isEmpty && $0.name.count <= 18 }),
              phases.contains(game.phase), game.round > 0, game.phaseIndex >= 0,
              game.winner == ClassicGame.winner(for: game.players),
              game.nightTarget.map(valid) ?? true, game.protectedPlayer.map(valid) ?? true,
              game.eliminationTarget.map(valid) ?? true,
              game.votes.allSatisfy({ valid($0.key) && valid($0.value) && $0.key != $0.value }),
              game.tieCandidates.allSatisfy(valid), game.declaredDetectives.allSatisfy(valid),
              game.suspicion.keys.allSatisfy(valid), (0...2).contains(game.voteRound),
              game.investigations.allSatisfy({ valid($0.target) && valid($0.investigator) }),
              game.messages.allSatisfy({ $0.speaker.map(valid) ?? true }),
              Set(game.messages.map(\.id)).count == game.messages.count
        else { throw SaveError.unsupportedOrInvalid }
        return game
    }
}
