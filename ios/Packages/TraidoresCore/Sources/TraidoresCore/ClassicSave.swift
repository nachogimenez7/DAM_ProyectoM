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
        let phases: [GamePhase] = [.assignment, .assassinNight, .detectiveNight, .medicNight,
                                   .dawn, .discussion, .voting, .voteCount, .tieVote, .result]
        let playerCount = game.players.count
        func valid(_ id: Int) -> Bool { (0..<playerCount).contains(id) }
        guard envelope.version == 1,
              (ClassicGame.minimumPlayers...ClassicGame.maximumPlayers).contains(playerCount),
              game.players.map(\.id) == Array(0..<playerCount),
              game.players.map({ $0.role.rawValue }).sorted() == ClassicGame.roles(for: playerCount).map(\.rawValue).sorted(),
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
