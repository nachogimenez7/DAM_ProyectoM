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
    public internal(set) var silencedPlayer: Int?
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
    public internal(set) var humanAccusation: Int?
    /// Optional keeps saves from builds that only supported Pampa decodable.
    public let mapConfig: GameMap?
    public let difficulty: BotDifficulty
    /// Optional keeps saves from earlier iOS builds decodable; `timing` supplies the Android default.
    public let timingConfig: GameTimingConfig?
    public let advancedConfig: AdvancedGameConfig?
    public let testOptionsConfig: LocalTestOptions?
    public let trainingRoleConfig: RoleKey?
    internal var random: ClassicRandom
    internal var messageSequence = 0

    public var human: ClassicPlayer { players[0] }
    public var living: [ClassicPlayer] { players.filter(\.alive) }
    public var humanInvestigations: [Investigation] { investigations.filter { $0.investigator == 0 } }
    public var map: GameMap { mapConfig ?? .pampa }
    public var timing: GameTimingConfig { (timingConfig ?? .normal).normalized }
    public var advanced: AdvancedGameConfig { (advancedConfig ?? .standard).normalized }
    public var testOptions: LocalTestOptions { testOptionsConfig ?? .standard }
    public var effectiveTiming: GameTimingConfig {
        guard testOptions.quickMatch else { return timing }
        return .init(transitionSeconds: 1, nightSeconds: 10,
                     discussionSeconds: 30, votingSeconds: 10)
    }
    public var isNight: Bool { [.assassinNight, .mercenaryNight, .detectiveNight, .medicNight].contains(phase) }
    public func name(_ id: Int) -> String { players.first { $0.id == id }?.name ?? "Jugador" }

    public init(
        name: String,
        seed: UInt64 = .random(in: .min ... .max),
        trainingRole: RoleKey? = nil,
        map: GameMap = .pampa,
        difficulty: BotDifficulty = .normal,
        timing: GameTimingConfig = .normal,
        advanced: AdvancedGameConfig = .standard,
        testOptions: LocalTestOptions = .standard,
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
        if let trainingRole, let index = roles.firstIndex(of: trainingRole) {
            roles.swapAt(0, index)
        } else if let trainingRole,
                  Self.supportedTrainingRoles.contains(trainingRole),
                  let villager = roles.firstIndex(of: .villager) {
            // Android's test mode replaces one villager when the requested role
            // is absent from the recommended composition.
            roles[villager] = trainingRole
            roles.swapAt(0, villager)
        }
        let cleanName = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(18))
        let names = [cleanName.isEmpty ? "Vos" : cleanName] + filledBots
        players = roles.enumerated().map { ClassicPlayer(id: $0.offset, name: names[$0.offset], role: $0.element) }
        mapConfig = map
        self.difficulty = difficulty
        timingConfig = timing.normalized
        advancedConfig = advanced.normalized
        testOptionsConfig = testOptions
        trainingRoleConfig = trainingRole
        self.random = random
        let villagers = players.filter { $0.role == .villager }.count
        let mercenary = players.contains { $0.role == .mercenary } ? "1 Mercenario, " : ""
        append("\(map.title): 1 Asesino, \(mercenary)1 Comisario, 1 Médico y \(villagers) Aldeanos.")
    }

    public static func roles(for playerCount: Int) -> [RoleKey] {
        let count = min(max(playerCount, minimumPlayers), maximumPlayers)
        let special: [RoleKey] = count >= 7
            ? [.assassin, .mercenary, .detective, .medic]
            : [.assassin, .detective, .medic]
        return special + Array(repeating: .villager, count: count - special.count)
    }

    public static let supportedTrainingRoles: [RoleKey] =
        [.villager, .detective, .medic, .assassin, .mercenary]

    /// Android GameRules.winnerFor for the roles currently supported by the local port.
    public static func winner(for players: [ClassicPlayer]) -> RoleTeam? {
        let alive = players.filter(\.alive)
        guard !alive.isEmpty else { return nil }
        guard alive.contains(where: { $0.role == .assassin }) else { return .town }
        let traitors = alive.filter { $0.role == .assassin || $0.role == .mercenary }.count
        return traitors >= alive.count - traitors ? .traitors : nil
    }

    public func legalTargets(for actor: Int) -> [Int] {
        guard winner == nil, let player = living.first(where: { $0.id == actor }) else { return [] }
        switch phase {
        case .assassinNight where player.role == .assassin:
            return living.filter { $0.id != actor && $0.role != .mercenary && $0.role != .assassin }
                .filter { !(actor != 0 && testOptions.botsNeverKillHuman && $0.id == 0) }
                .map(\.id)
        case .mercenaryNight where player.role == .mercenary,
             .detectiveNight where player.role == .detective:
            return living.filter { $0.id != actor }.map(\.id)
        case .medicNight where player.role == .medic:
            return living.map(\.id) // Android allows self-protection and repeated protection.
        case .voting:
            if actor == silencedPlayer { return [] }
            return living.filter { $0.id != actor &&
                !(actor != 0 && testOptions.botsNeverVoteHuman && $0.id == 0) }.map(\.id)
        case .tieVote:
            if actor == silencedPlayer { return [] }
            return living.filter { $0.id != actor && tieCandidates.contains($0.id) &&
                !(actor != 0 && testOptions.botsNeverVoteHuman && $0.id == 0) }.map(\.id)
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
        case .assassinNight, .mercenaryNight, .detectiveNight, .medicNight:
            if !targets.isEmpty {
                guard let target, targets.contains(target) else { return false }
                performNightAction(actor: 0, target: target)
            }
            advanceThroughPassiveNight()
        case .dawn:
            if let victim = nightTarget, victim != protectedPlayer {
                players[victim].alive = false
                append("\(name(victim)) murió durante la noche.")
            } else { append("Amanece sin víctimas.") }
            if let silencedPlayer, players[silencedPlayer].alive {
                append("\(name(silencedPlayer)) no puede hablar ni votar durante el día.")
            }
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
            recordVotes(forcedTieBallot(from: ballot) ?? ballot)
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

    /// A timed-out human night action ends without inventing a target.
    @discardableResult
    public mutating func expireNight(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil, isNight else { return false }
        advanceThroughPassiveNight()
        return true
    }

    /// Android's SALTAR NOCHE skips only passive phases, stopping before any
    /// action that requires the human. The UI arms this after 3.5 seconds.
    @discardableResult
    public mutating func skipPassiveNight(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil, isNight,
              legalTargets(for: 0).isEmpty else { return false }
        advanceThroughPassiveNight()
        return true
    }

    /// Like Android's unified local night: finish bot-only phases immediately,
    /// but stop before another human action or at dawn.
    private mutating func advanceThroughPassiveNight() {
        for _ in 0..<4 {
            nextNightPhase()
            resolveBotNight()
            if !isNight || !legalTargets(for: 0).isEmpty { break }
        }
    }

    @discardableResult
    public mutating func accuse(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, silencedPlayer != 0, !humanSpoke,
              living.contains(where: { $0.id == target && target != 0 }) else { return false }
        humanSpoke = true
        humanAccusation = target
        suspicion[target, default: 0] += 1
        append("Sospecho de \(name(target)). Quiero escuchar su versión.", speaker: 0)
        // This is a structured local debate, not a language-model chatbot.
        append("Una acusación no es una prueba. Miren también cómo votamos.", speaker: target)
        return true
    }

    /// Adds a player's public message to the local debate. The response is
    /// deliberately deterministic and local; it never sends text to a server.
    @discardableResult
    public mutating func sendPublicMessage(_ text: String, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, silencedPlayer != 0 else { return false }
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(140))
        guard !clean.isEmpty else { return false }
        append(clean, speaker: 0)

        let mentioned = living.first { player in
            player.id != 0 && clean.localizedCaseInsensitiveContains(player.name)
        }
        let accusation = ["sospecho", "voto", "contra", "culpable"].contains {
            clean.localizedCaseInsensitiveContains($0)
        }
        if let mentioned, accusation {
            humanAccusation = mentioned.id
            suspicion[mentioned.id, default: 0] += 1
        }

        if let responder = (mentioned?.id == silencedPlayer ? nil : mentioned)
            ?? living.first(where: { $0.id != 0 && $0.id != silencedPlayer }) {
            let answer = mentioned != nil
                ? (accusation ? "¿Qué prueba tenés contra mí? Escuchemos a los demás."
                   : "Te escucho. Comparemos lo que pasó esta noche.")
                : "Antes de votar, comparemos las versiones de todos."
            append(answer, speaker: responder.id)
        }
        return true
    }

    @discardableResult
    public mutating func shareInvestigation(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, silencedPlayer != 0, !humanSharedRead,
              let read = humanInvestigations.last else { return false }
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
        nightTarget = nil; protectedPlayer = nil; silencedPlayer = nil; eliminationTarget = nil
        votes = [:]; tieCandidates = []; voteRound = 0
        humanSpoke = false; humanSharedRead = false
        humanAccusation = nil
        suspicion = suspicion.mapValues { $0 / 2 }
        transition(.assassinNight)
        append("Noche \(round). \(map.title) duerme.")
        resolveBotNight()
    }

    private mutating func nextNightPhase() {
        switch phase {
        case .assassinNight:
            transition(living.contains(where: { $0.role == .mercenary }) ? .mercenaryNight : .detectiveNight)
        case .mercenaryNight: transition(.detectiveNight)
        case .detectiveNight: transition(.medicNight)
        case .medicNight: transition(.dawn)
        default: break
        }
    }

    private mutating func resolveBotNight() {
        guard isNight else { return }
        let role: RoleKey = switch phase {
        case .assassinNight: .assassin
        case .mercenaryNight: .mercenary
        case .detectiveNight: .detective
        default: .medic
        }
        if let actor = living.first(where: { $0.role == role }), actor.id != 0,
           let target = botChoice(actor: actor.id) {
            performNightAction(actor: actor.id, target: target)
        }
    }

    private mutating func performNightAction(actor: Int, target: Int) {
        switch phase {
        case .assassinNight: nightTarget = target
        case .mercenaryNight: silencedPlayer = target
        case .detectiveNight:
            investigations.append(.init(round: round, investigator: actor, target: target,
                                        suspicious: [.assassin, .mercenary].contains(players[target].role)))
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
        for actor in living where actor.id != 0 && actor.id != silencedPlayer {
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
        if (phase == .voting || phase == .tieVote), testOptions.botsFollowAccusation,
           let humanAccusation, legalTargets(for: actor).contains(humanAccusation) {
            return humanAccusation
        }
        return pick(legalTargets(for: actor), actor: actor, purpose: phase)
    }

    private func forcedTieBallot(from original: [Int: Int]) -> [Int: Int]? {
        guard phase == .voting, testOptions.forceVoteTies else { return nil }
        let voters = living.filter { $0.id != silencedPlayer }.map(\.id)
        guard voters.count >= 4 else { return nil }
        let bots = voters.filter { $0 != 0 }
        let candidates = living.map(\.id)
        for first in candidates {
            for second in candidates where second > first {
                let firstBase = original[0] == first ? 1 : 0
                let secondBase = original[0] == second ? 1 : 0
                if let result = tieAssignment(bots: bots, index: 0, first: first, second: second,
                                              firstVotes: firstBase, secondVotes: secondBase,
                                              otherVotes: original[0].map { $0 != first && $0 != second ? 1 : 0 } ?? 0,
                                              ballot: original.filter { $0.key == 0 }) {
                    return result
                }
            }
        }
        return nil
    }

    private func tieAssignment(bots: [Int], index: Int, first: Int, second: Int,
                               firstVotes: Int, secondVotes: Int, otherVotes: Int,
                               ballot: [Int: Int]) -> [Int: Int]? {
        let remaining = bots.count - index
        guard abs(firstVotes - secondVotes) <= remaining,
              otherVotes <= 1 else { return nil }
        if index == bots.count {
            return firstVotes == secondVotes && firstVotes > otherVotes ? ballot : nil
        }
        let bot = bots[index]
        let legal = legalTargets(for: bot)
        for target in [first, second] where legal.contains(target) {
            var next = ballot
            next[bot] = target
            if let found = tieAssignment(bots: bots, index: index + 1, first: first, second: second,
                                         firstVotes: firstVotes + (target == first ? 1 : 0),
                                         secondVotes: secondVotes + (target == second ? 1 : 0),
                                         otherVotes: otherVotes, ballot: next) { return found }
        }
        if otherVotes == 0, let third = legal.first(where: { $0 != first && $0 != second }) {
            var next = ballot
            next[bot] = third
            return tieAssignment(bots: bots, index: index + 1, first: first, second: second,
                                 firstVotes: firstVotes, secondVotes: secondVotes,
                                 otherVotes: 1, ballot: next)
        }
        return nil
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
