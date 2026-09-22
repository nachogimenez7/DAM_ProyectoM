import Foundation

/// Local-only save model. Firebase will use separate DTOs, never this private state.
public struct ClassicPlayer: Codable, Equatable, Identifiable, Sendable {
    public let id: Int
    public let name: String
    public let role: RoleKey
    public internal(set) var alive = true
}

public struct Investigation: Codable, Equatable, Sendable {
    public let round: Int
    public let investigator: Int
    public let target: Int
    public let suspicious: Bool
}

public struct TableMessage: Codable, Equatable, Identifiable, Sendable {
    public let id: Int
    public let round: Int
    public let speaker: Int?
    public let text: String
}

public struct ClassicGame: Codable, Equatable, Sendable {
    public static let roles: [RoleKey] = [.assassin, .detective, .medic, .villager, .villager]
    public static let minimumPlayers = 5
    public static let maximumPlayers = 15
    public static let defaultBotNames = [
        "Thiago", "Mora", "Lautaro", "Valen", "Rami", "Juli", "Santi",
        "Mili", "Toto", "Agus", "Bruno", "Lola", "Fede", "Cata"
    ]
    public internal(set) var players: [ClassicPlayer]
    public internal(set) var phase: GamePhase = .assignment
    public internal(set) var phaseIndex = 0
    public internal(set) var round = 1
    public internal(set) var winner: RoleTeam?
    public internal(set) var nightTarget: Int?
    public internal(set) var protectedPlayer: Int?
    public internal(set) var investigations: [Investigation] = []
    public internal(set) var votes: [Int: Int] = [:]
    public internal(set) var voteRound = 0
    public internal(set) var tieCandidates: [Int] = []
    public internal(set) var eliminationTarget: Int?
    public internal(set) var messages: [TableMessage] = []
    public internal(set) var suspicion: [Int: Int] = [:]
    public internal(set) var declaredDetectives: [Int] = []
    public internal(set) var humanSpoke = false
    public internal(set) var humanSharedRead = false
    /// Optional keeps saves from builds that only supported Pampa decodable.
    public let mapConfig: GameMap?
    public let difficulty: BotDifficulty
    /// Optional keeps saves from earlier iOS builds decodable; `timing` supplies the Android default.
    public let timingConfig: GameTimingConfig?
    public let advancedConfig: AdvancedGameConfig?
    internal var random: ClassicRandom
    internal var messageSequence = 0

    public var human: ClassicPlayer { players[0] }
    public var living: [ClassicPlayer] { players.filter(\.alive) }
    public var humanInvestigations: [Investigation] { investigations.filter { $0.investigator == 0 } }
    public var map: GameMap { mapConfig ?? .pampa }
    public var timing: GameTimingConfig { (timingConfig ?? .normal).normalized }
    public var advanced: AdvancedGameConfig { (advancedConfig ?? .standard).normalized }
    public var isNight: Bool { [.assassinNight, .detectiveNight, .medicNight].contains(phase) }
    public func name(_ id: Int) -> String { players.first { $0.id == id }?.name ?? "Jugador" }

    public init(
        name: String,
        seed: UInt64 = .random(in: .min ... .max),
        trainingRole: RoleKey? = nil,
        map: GameMap = .pampa,
        difficulty: BotDifficulty = .normal,
        timing: GameTimingConfig = .normal,
        advanced: AdvancedGameConfig = .standard,
        botNames: [String] = Array(Self.defaultBotNames.prefix(4))
    ) {
        var random = ClassicRandom(state: seed)
        let cleanBots = botNames.prefix(Self.maximumPlayers - 1).enumerated().map { index, value in
            let clean = String(value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(18))
            return clean.isEmpty ? Self.defaultBotNames[index] : clean
        }
        let filledBots = cleanBots + Self.defaultBotNames.dropFirst(cleanBots.count)
            .prefix(max(0, Self.minimumPlayers - 1 - cleanBots.count))
        var roles = Self.roles(for: filledBots.count + 1).shuffled(using: &random)
        if let trainingRole, let index = roles.firstIndex(of: trainingRole) { roles.swapAt(0, index) }
        let cleanName = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(18))
        let names = [cleanName.isEmpty ? "Vos" : cleanName] + filledBots
        players = roles.enumerated().map { ClassicPlayer(id: $0.offset, name: names[$0.offset], role: $0.element) }
        mapConfig = map
        self.difficulty = difficulty
        timingConfig = timing.normalized
        advancedConfig = advanced.normalized
        self.random = random
        let villagers = players.count - 3
        append("\(map.title): 1 Asesino, 1 Comisario, 1 Médico y \(villagers) Aldeanos.")
    }

    public static func roles(for playerCount: Int) -> [RoleKey] {
        let count = min(max(playerCount, minimumPlayers), maximumPlayers)
        return [.assassin, .detective, .medic] + Array(repeating: .villager, count: count - 3)
    }

    /// Android GameRules.winnerFor, restricted to the four classic roles.
    public static func winner(for players: [ClassicPlayer]) -> RoleTeam? {
        let alive = players.filter(\.alive)
        guard !alive.isEmpty else { return nil }
        let killers = alive.filter { $0.role == .assassin }.count
        if killers == 0 { return .town }
        return killers >= alive.count - killers ? .traitors : nil
    }

    public func legalTargets(for actor: Int) -> [Int] {
        guard winner == nil, let player = living.first(where: { $0.id == actor }) else { return [] }
        switch phase {
        case .assassinNight where player.role == .assassin,
             .detectiveNight where player.role == .detective:
            return living.filter { $0.id != actor }.map(\.id)
        case .medicNight where player.role == .medic:
            return living.map(\.id) // Android allows self-protection and repeated protection.
        case .voting:
            return living.filter { $0.id != actor }.map(\.id)
        case .tieVote:
            return living.filter { $0.id != actor && tieCandidates.contains($0.id) }.map(\.id)
        default: return []
        }
    }

    /// Every UI action carries its phase revision, so repeated/stale taps are harmless.
    @discardableResult
    public mutating func advance(target: Int? = nil, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil else { return false }
        let targets = legalTargets(for: 0)
        if !targets.isEmpty && !targets.contains(target ?? -1) { return false }
        switch phase {
        case .assignment:
            startNight()
        case .assassinNight, .detectiveNight, .medicNight:
            guard let target, targets.contains(target) else { return false }
            performNightAction(actor: 0, target: target)
            nextNightPhase()
            resolveBotNight()
        case .dawn:
            if let victim = nightTarget, victim != protectedPlayer {
                players[victim].alive = false
                append("\(name(victim)) murió durante la noche.")
            } else { append("Amanece sin víctimas.") }
            transition(.discussion)
            checkWinner()
            if winner == nil { botDebate() }
        case .discussion:
            transition(.voting)
            append("Comienza la votación. No se permite votar por uno mismo.")
        case .voting, .tieVote:
            var ballot: [Int: Int] = [:]
            // Decisions use the same pre-ballot knowledge; bots cannot read the human's vote.
            for player in living {
                if player.id == 0 {
                    if let target, targets.contains(target) { ballot[0] = target }
                } else if let choice = botChoice(actor: player.id) { ballot[player.id] = choice }
            }
            recordVotes(ballot)
        case .voteCount:
            if voteRound == 1 && tieCandidates.count > 1 {
                votes = [:]
                transition(.tieVote)
                append("Empate. Voten entre \(tieCandidates.map { name($0) }.joined(separator: ", ")).")
            } else {
                if tieCandidates.count > 1 { eliminationTarget = nil }
                transition(.result)
                append(eliminationTarget.map { "\(name($0)) será expulsado." }
                       ?? "Nadie será expulsado: no hubo una mayoría única.")
            }
        case .result:
            if let id = eliminationTarget {
                players[id].alive = false
                append("\(name(id)) fue expulsado por el pueblo.")
            }
            checkWinner()
            if winner == nil { round += 1; startNight() }
        default: return false
        }
        return true
    }

    @discardableResult
    public mutating func accuse(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, !humanSpoke, living.contains(where: { $0.id == target && target != 0 }) else { return false }
        humanSpoke = true
        suspicion[target, default: 0] += 1
        append("Sospecho de \(name(target)). Quiero escuchar su versión.", speaker: 0)
        // This is a structured local debate, not a language-model chatbot.
        append("Una acusación no es una prueba. Miren también cómo votamos.", speaker: target)
        return true
    }

    @discardableResult
    public mutating func shareInvestigation(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, !humanSharedRead, let read = humanInvestigations.last else { return false }
        humanSharedRead = true
        declare(read)
        return true
    }

    internal mutating func recordVotes(_ ballot: [Int: Int]) {
        guard phase == .voting || phase == .tieVote else { return }
        votes = ballot.filter { legalTargets(for: $0.key).contains($0.value) }
        let totals = Dictionary(grouping: votes.values, by: { $0 }).mapValues(\.count)
        let maximum = totals.values.max() ?? 0
        let leaders = totals.keys.filter { totals[$0] == maximum }.sorted()
        voteRound = phase == .voting ? 1 : 2
        tieCandidates = leaders.count > 1 ? leaders : []
        eliminationTarget = leaders.count == 1 ? leaders[0] : nil
        transition(.voteCount)
        append("Votos contados. Revisá el resultado de la mesa.")
    }

    internal mutating func append(_ text: String, speaker: Int? = nil) {
        messageSequence += 1
        messages.append(.init(id: messageSequence, round: round, speaker: speaker, text: text))
        messages = Array(messages.suffix(100))
    }

    private mutating func transition(_ next: GamePhase) { phase = next; phaseIndex += 1 }
    private mutating func checkWinner() {
        winner = Self.winner(for: players)
        if let winner { transition(.result); append("Victoria de \(winner.rawValue).") }
    }

    private mutating func startNight() {
        nightTarget = nil; protectedPlayer = nil; eliminationTarget = nil
        votes = [:]; tieCandidates = []; voteRound = 0
        humanSpoke = false; humanSharedRead = false
        suspicion = suspicion.mapValues { $0 / 2 }
        transition(.assassinNight)
        append("Noche \(round). \(map.title) duerme.")
        resolveBotNight()
    }

    private mutating func nextNightPhase() {
        switch phase {
        case .assassinNight: transition(.detectiveNight)
        case .detectiveNight: transition(.medicNight)
        case .medicNight: transition(.dawn)
        default: break
        }
    }

    private mutating func resolveBotNight() {
        // At most three night phases; never a timer or an unbounded loop.
        for _ in 0..<3 {
            guard isNight else { return }
            let role: RoleKey = phase == .assassinNight ? .assassin : phase == .detectiveNight ? .detective : .medic
            if let actor = living.first(where: { $0.role == role }) {
                if actor.id == 0 { return }
                if let target = botChoice(actor: actor.id) { performNightAction(actor: actor.id, target: target) }
            }
            nextNightPhase()
        }
    }

    private mutating func performNightAction(actor: Int, target: Int) {
        switch phase {
        case .assassinNight: nightTarget = target
        case .detectiveNight:
            investigations.append(.init(round: round, investigator: actor, target: target,
                                        suspicious: players[target].role == .assassin))
        case .medicNight: protectedPlayer = target
        default: break
        }
    }

    private mutating func declare(_ read: Investigation) {
        if !declaredDetectives.contains(read.investigator) { declaredDetectives.append(read.investigator) }
        suspicion[read.target, default: 0] += read.suspicious ? 3 : -2
        append("Soy el Comisario. Investigué a \(name(read.target)): parece \(read.suspicious ? "sospechoso" : "inocente").",
               speaker: read.investigator)
    }

    private mutating func botDebate() {
        for actor in living where actor.id != 0 {
            if let read = investigations.last(where: { $0.investigator == actor.id && $0.round == round }) {
                declare(read)
            } else {
                let candidates = living.filter { $0.id != actor.id }.map(\.id)
                if let target = pick(candidates, actor: actor.id, purpose: .voting) {
                    append("Me genera dudas \(name(target)). Voy a observar su voto.", speaker: actor.id)
                }
            }
        }
    }

    private mutating func botChoice(actor: Int) -> Int? {
        pick(legalTargets(for: actor), actor: actor, purpose: phase)
    }

    /// Deliberately contains only own role, own investigations and public information.
    /// No target roles, pending kill/protection or private reads of other players reach the AI.
    internal func perception(for actor: Int) -> ClassicBotPerception {
        .init(actor: actor, role: players[actor].role,
              reads: investigations.filter { $0.investigator == actor },
              suspicion: suspicion, declaredDetectives: declaredDetectives)
    }

    private mutating func pick(_ candidates: [Int], actor: Int, purpose: GamePhase) -> Int? {
        let view = perception(for: actor)
        let scores = candidates.map { ($0, view.score($0, purpose: purpose)) }
        guard let highest = scores.map(\.1).max() else { return nil }
        let best = scores.filter { $0.1 == highest }.map(\.0)
        return best.randomElement(using: &random)
    }
}

internal struct ClassicBotPerception: Equatable {
    let actor: Int
    let role: RoleKey
    let reads: [Investigation]
    let suspicion: [Int: Int]
    let declaredDetectives: [Int]

    func score(_ target: Int, purpose: GamePhase) -> Int {
        switch purpose {
        case .assassinNight: return declaredDetectives.contains(target) ? 5 : 0
        case .detectiveNight: return reads.contains { $0.target == target } ? -100 : (suspicion[target] ?? 0)
        case .medicNight: return declaredDetectives.contains(target) ? 5 : 0
        default:
            if role == .detective, let read = reads.last(where: { $0.target == target }) {
                return read.suspicious ? 100 : -100
            }
            // The single assassin can sow doubt about the detective, using public claims only.
            if role == .assassin && declaredDetectives.contains(target) { return 10 }
            return suspicion[target] ?? 0
        }
    }
}

/// Reproducible random stream, persisted with the match so restore cannot reroll bot decisions.
internal struct ClassicRandom: RandomNumberGenerator, Codable, Equatable, Sendable {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9e3779b97f4a7c15
        var z = state
        z = (z ^ (z >> 30)) &* 0xbf58476d1ce4e5b9
        z = (z ^ (z >> 27)) &* 0x94d049bb133111eb
        return z ^ (z >> 31)
    }
}
