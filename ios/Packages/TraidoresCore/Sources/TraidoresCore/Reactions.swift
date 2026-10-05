import Foundation

// In-game emotes, ported from Android's GameplayReactionLimiter and the bot reaction
// policy of GameplayMockActivity. Pure rules: the app draws the bubbles and plays the
// sounds, and the same limits apply to online rooms.

public enum ReactionBlock: Equatable, Sendable {
    case none
    /// Seconds until the next emote, rounded up.
    case cooldown(seconds: Int)
    case roundLimit
}

/// At most two emotes per player and round, ten seconds apart (Android defaults).
public struct ReactionLimiter: Sendable {
    public static let maxPerRound = 2
    public static let cooldown: TimeInterval = 10

    private var round = 0
    private var used: [Int: Int] = [:]
    private var cooldownUntil: [Int: TimeInterval] = [:]

    public init() {}

    public func check(player: Int, round: Int, now: TimeInterval) -> ReactionBlock {
        let used = round == self.round ? used[player, default: 0] : 0
        if used >= Self.maxPerRound { return .roundLimit }
        let remaining = (cooldownUntil[player] ?? 0) - now
        if remaining > 0 { return .cooldown(seconds: Int(remaining.rounded(.up))) }
        return .none
    }

    /// Records an emote when allowed; returns the block otherwise.
    @discardableResult
    public mutating func record(player: Int, round: Int, now: TimeInterval) -> ReactionBlock {
        if round != self.round {
            self.round = round
            used = [:]
        }
        let block = check(player: player, round: round, now: now)
        guard block == .none else { return block }
        used[player, default: 0] += 1
        cooldownUntil[player] = now + Self.cooldown
        return .none
    }
}

public enum ReactionRules {
    /// Debate, Contrapunto and the votes: the public moments of the day.
    public static func isPublicPhase(_ phase: GamePhase) -> Bool {
        [.discussion, .counterpoint, .voting, .tieVote, .mayorTieBreak].contains(phase)
    }

    /// Android's emote sets: the medieval Asesino and the gaucho Comisario have their own,
    /// everyone else uses the Greek villager's.
    public static func botEmoteIDs(map: GameMap, role: RoleKey) -> [String] {
        let theme = map == .medieval && role == .assassin ? "medieval"
            : map == .pampa && role == .detective ? "gaucho" : "griego"
        return ["contento", "triste", "sospechoso", "enojado"].map { "\(theme)_\($0)" }
    }

    /// The emotion a bot shows: suspicion and anger while voting, a role bias one time in three.
    public static func botEmoteID(map: GameMap, role: RoleKey, phase: GamePhase, seed: Int) -> String {
        let pool: [String] = switch phase {
        case .voting, .tieVote, .mayorTieBreak: ["sospechoso", "enojado"]
        case .counterpoint: ["enojado", "sospechoso", "triste"]
        default: ["contento", "sospechoso", "enojado", "triste"]
        }
        let bias: String? = switch role {
        case .assassin, .mercenary: "sospechoso"
        case .payador: "contento"
        case .medic, .oracle: "triste"
        default: nil
        }
        let keys = bias != nil && seed % 3 == 0 ? [bias!] + pool : pool
        let emotion = keys[abs(seed) % keys.count]
        let ids = botEmoteIDs(map: map, role: role)
        return ids.first { $0.hasSuffix("_\(emotion)") } ?? ids[0]
    }

    /// Sound key of an emote id: the four classic emotions share one sound per emotion.
    public static func soundKey(forEmoteID id: String) -> String {
        if id.hasPrefix("premium_") { return id }
        if id.hasSuffix("_contento") { return "happy" }
        if id.hasSuffix("_triste") { return "sad" }
        if id.hasSuffix("_sospechoso") { return "suspicious" }
        return "angry"
    }
}
