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

    public init(id: Int, round: Int, speaker: Int?, text: String) {
        self.id = id
        self.round = round
        self.speaker = speaker
        self.text = text
    }
}

public struct SpecialVictory: Codable, Equatable, Sendable {
    public let playerID: Int
    /// Android's stable reason key.
    public let reason: String
    public let round: Int
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
    public internal(set) var lastSilencedRounds: [Int: Int] = [:]
    public internal(set) var investigations: [Investigation] = []
    public internal(set) var votes: [Int: Int] = [:]
    public internal(set) var voteRound = 0
    public internal(set) var tieCandidates: [Int] = []
    public internal(set) var eliminationTarget: Int?
    public internal(set) var messages: [TableMessage] = []
    /// Optional so matches saved before private chat was introduced still decode.
    public internal(set) var traitorMessages: [TableMessage]?
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
    public internal(set) var revealedMayorID: Int?
    public internal(set) var deserterTeam: RoleTeam?
    public internal(set) var deserterReconsiderationUsed = false
    /// A local choice must finish before advancing when traitors would otherwise win.
    /// Online uses the server's timed window, never this model as authority.
    public internal(set) var deserterReconsiderationPending = false
    public internal(set) var payadorUsed = false
    public internal(set) var contrapuntoParticipants: [Int] = []
    public internal(set) var payadorPointedPlayer: Int?
    public internal(set) var oracleUsed = false
    public internal(set) var oracleGuest: Int?
    public internal(set) var specialVictories: [SpecialVictory] = []
    internal var random: ClassicRandom
    internal var messageSequence = 0

    public var human: ClassicPlayer { players[0] }
    public var living: [ClassicPlayer] { players.filter(\.alive) }
    public var humanInvestigations: [Investigation] { investigations.filter { $0.investigator == 0 } }
    public var privateChatMessages: [TableMessage] { traitorMessages ?? [] }
    public var map: GameMap { mapConfig ?? .pampa }
    public var timing: GameTimingConfig { (timingConfig ?? .normal).normalized }
    public var advanced: AdvancedGameConfig { (advancedConfig ?? .standard).normalized }
    public var testOptions: LocalTestOptions { testOptionsConfig ?? .standard }
    public var effectiveTiming: GameTimingConfig {
        guard testOptions.quickMatch else { return timing }
        return .init(transitionSeconds: 1, nightSeconds: 10,
                     discussionSeconds: 30, votingSeconds: 10)
    }
    /// Weighted totals for the UI: revealed mayor ballot plus Contrapunto's fixed vote.
    public var voteTotals: [Int: Int] {
        var weighted = Array(votes.values)
        if let mayor = revealedMayorID, living.contains(where: { $0.id == mayor }), let target = votes[mayor] {
            weighted.append(target)
        }
        if let pointed = payadorPointedPlayer { weighted.append(pointed) }
        return Dictionary(grouping: weighted, by: { $0 }).mapValues(\.count)
    }

    public var needsInitialDeserterChoice: Bool {
        winner == nil && human.alive && human.role == .deserter && deserterTeam == nil
    }
    public var deserterReconsiderationAvailable: Bool {
        winner == nil && living.contains { $0.role == .deserter } && deserterTeam != nil &&
        !deserterReconsiderationUsed && round >= 4 &&
        (phase == .discussion || Self.winner(for: players, deserterTeam: deserterTeam) == .traitors)
    }
    public func isSilenceOnCooldown(_ id: Int) -> Bool {
        lastSilencedRounds[id].map { round - $0 < 2 } ?? false
    }
    public var humanWon: Bool {
        if specialVictories.contains(where: { $0.playerID == 0 }) { return true }
        guard let winner else { return false }
        if human.role == .deserter { return human.alive && deserterTeam == winner }
        return RoleCatalog.all.first { $0.id == human.role }?.team == winner
    }
    public var humanWin: Bool { humanWon }

    public func canSpeak(_ id: Int) -> Bool {
        guard winner == nil, let player = players.first(where: { $0.id == id }) else { return false }
        if phase == .counterpoint { return player.alive && id != silencedPlayer && contrapuntoParticipants.contains(id) }
        return phase == .discussion && ((player.alive && id != silencedPlayer) || oracleGuest == id)
    }
    public var isNight: Bool { [.assassinNight, .mercenaryNight, .detectiveNight, .medicNight, .oracleNight].contains(phase) }
    public func name(_ id: Int) -> String { players.first { $0.id == id }?.name ?? "Jugador" }

    private enum CodingKeys: String, CodingKey {
        case players, phase, phaseIndex, round, winner, nightTarget, protectedPlayer
        case investigations, votes, voteRound, tieCandidates, eliminationTarget, messages
        case suspicion, declaredDetectives, humanSpoke, humanSharedRead
        case mapConfig, difficulty, timingConfig, advancedConfig, random, messageSequence
        case revealedMayorID, deserterTeam, deserterReconsiderationUsed, deserterReconsiderationPending
        case payadorUsed, contrapuntoParticipants, payadorPointedPlayer
        case oracleUsed, oracleGuest, specialVictories, silencedPlayer, lastSilencedRounds, humanAccusation, traitorMessages, testOptionsConfig, trainingRoleConfig
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        players = try container.decode([ClassicPlayer].self, forKey: .players)
        phase = try container.decode(GamePhase.self, forKey: .phase)
        phaseIndex = try container.decode(Int.self, forKey: .phaseIndex)
        round = try container.decode(Int.self, forKey: .round)
        winner = try container.decodeIfPresent(RoleTeam.self, forKey: .winner)
        nightTarget = try container.decodeIfPresent(Int.self, forKey: .nightTarget)
        protectedPlayer = try container.decodeIfPresent(Int.self, forKey: .protectedPlayer)
        investigations = try container.decode([Investigation].self, forKey: .investigations)
        votes = try container.decode([Int: Int].self, forKey: .votes)
        voteRound = try container.decode(Int.self, forKey: .voteRound)
        tieCandidates = try container.decode([Int].self, forKey: .tieCandidates)
        eliminationTarget = try container.decodeIfPresent(Int.self, forKey: .eliminationTarget)
        messages = try container.decode([TableMessage].self, forKey: .messages)
        suspicion = try container.decode([Int: Int].self, forKey: .suspicion)
        declaredDetectives = try container.decode([Int].self, forKey: .declaredDetectives)
        humanSpoke = try container.decode(Bool.self, forKey: .humanSpoke)
        humanSharedRead = try container.decode(Bool.self, forKey: .humanSharedRead)
        mapConfig = try container.decodeIfPresent(GameMap.self, forKey: .mapConfig)
        difficulty = try container.decode(BotDifficulty.self, forKey: .difficulty)
        timingConfig = try container.decodeIfPresent(GameTimingConfig.self, forKey: .timingConfig)
        advancedConfig = try container.decodeIfPresent(AdvancedGameConfig.self, forKey: .advancedConfig)
        random = try container.decode(ClassicRandom.self, forKey: .random)
        messageSequence = try container.decode(Int.self, forKey: .messageSequence)
        revealedMayorID = try container.decodeIfPresent(Int.self, forKey: .revealedMayorID)
        deserterTeam = try container.decodeIfPresent(RoleTeam.self, forKey: .deserterTeam)
        deserterReconsiderationUsed = try container.decodeIfPresent(Bool.self, forKey: .deserterReconsiderationUsed) ?? false
        deserterReconsiderationPending = try container.decodeIfPresent(Bool.self, forKey: .deserterReconsiderationPending) ?? false
        payadorUsed = try container.decodeIfPresent(Bool.self, forKey: .payadorUsed) ?? false
        contrapuntoParticipants = try container.decodeIfPresent([Int].self, forKey: .contrapuntoParticipants) ?? []
        payadorPointedPlayer = try container.decodeIfPresent(Int.self, forKey: .payadorPointedPlayer)
        oracleUsed = try container.decodeIfPresent(Bool.self, forKey: .oracleUsed) ?? false
        oracleGuest = try container.decodeIfPresent(Int.self, forKey: .oracleGuest)
        specialVictories = try container.decodeIfPresent([SpecialVictory].self, forKey: .specialVictories) ?? []
        silencedPlayer = try container.decodeIfPresent(Int.self, forKey: .silencedPlayer)
        lastSilencedRounds = try container.decodeIfPresent([Int: Int].self, forKey: .lastSilencedRounds) ?? [:]
        humanAccusation = try container.decodeIfPresent(Int.self, forKey: .humanAccusation)
        traitorMessages = try container.decodeIfPresent([TableMessage].self, forKey: .traitorMessages)
        testOptionsConfig = try container.decodeIfPresent(LocalTestOptions.self, forKey: .testOptionsConfig)
        trainingRoleConfig = try container.decodeIfPresent(RoleKey.self, forKey: .trainingRoleConfig)
    }

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
        var roles = Self.roles(for: filledBots.count + 1, map: map).shuffled(using: &random)
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
        if let deserter = players.first(where: { $0.role == .deserter }), deserter.id != 0 {
            deserterTeam = random.next() % 2 == 0 ? .town : .traitors
            self.random = random
        }
        let composition = RoleCatalog.all.compactMap { definition -> String? in
            let count = players.filter { $0.role == definition.id }.count
            return count > 0 ? "\(count) \(definition.title)" : nil
        }.joined(separator: ", ")
        append("\(map.title): \(composition).")
    }

    public static func roles(for playerCount: Int) -> [RoleKey] {
        roles(for: playerCount, map: .pampa)
    }

    public static func roles(for playerCount: Int, map: GameMap) -> [RoleKey] {
        let count = min(max(playerCount, minimumPlayers), maximumPlayers)
        var deck: [RoleKey] = [.assassin, .detective, .medic]
        if count >= 7 { deck.append(.mercenary) }
        if count >= 8 {
            deck.append(.mayor)
            deck.append(map == .pampa ? .payador : map == .greece ? .oracle : .jester)
        }
        if count >= 10 { deck.append(.spy) }
        if count >= 13 { deck.append(.assassin) }
        if count >= 14 { deck.append(.deserter) }
        return deck + Array(repeating: .villager, count: count - deck.count)
    }

    /// Android excludes both neutrals from parity, regardless of the Desertor's side.
    public static func winner(for players: [ClassicPlayer]) -> RoleTeam? {
        winner(for: players, deserterTeam: nil)
    }

    public static func winner(for players: [ClassicPlayer], deserterTeam: RoleTeam?) -> RoleTeam? {
        let alive = players.filter(\.alive)
        guard !alive.isEmpty else { return nil }
        if !alive.contains(where: { [.assassin, .spy].contains($0.role) }) { return .town }
        if alive.contains(where: { $0.role == .deserter }) && deserterTeam == nil { return nil }
        let traitors = alive.filter { [.assassin, .spy, .mercenary].contains($0.role) }.count
        let town = alive.filter { player in RoleCatalog.all.first { $0.id == player.role }?.team == .town }.count
        return traitors >= town ? .traitors : nil
    }

    public static let supportedTrainingRoles: [RoleKey] =
        [.villager, .detective, .medic, .assassin, .mercenary, .spy, .mayor, .deserter, .payador, .oracle, .jester]

    public func legalTargets(for actor: Int) -> [Int] {
        guard winner == nil, let player = living.first(where: { $0.id == actor }) else { return [] }
        switch phase {
        case .assassinNight where player.role == .assassin || player.role == .spy:
            return living.filter { $0.id != actor && ![.assassin, .mercenary, .spy].contains($0.role) &&
                !($0.role == .deserter && deserterTeam == .traitors) }
                .filter { !(actor != 0 && testOptions.botsNeverKillHuman && $0.id == 0) }
                .map(\.id)
        case .mercenaryNight where player.role == .mercenary:
            return living.filter { $0.id != actor && !isSilenceOnCooldown($0.id) }.map(\.id)
        case .detectiveNight where player.role == .detective:
            return living.filter { $0.id != actor }.map(\.id)
        case .medicNight where player.role == .medic:
            return living.map(\.id) // Android allows self-protection and repeated protection.
        case .oracleNight where player.role == .oracle && !oracleUsed && round > 1:
            return players.filter { !$0.alive }.map(\.id)
        case .mayorTieBreak where player.role == .mayor:
            if actor == silencedPlayer { return [] }
            return tieCandidates.filter { $0 != actor }
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
        guard phaseIndex == expectedPhaseIndex, winner == nil,
              !deserterReconsiderationPending else { return false }
        let targets = legalTargets(for: 0)
        if phase != .oracleNight && !targets.isEmpty && !targets.contains(target ?? -1) { return false }
        switch phase {
        case .assignment:
            guard !needsInitialDeserterChoice else { return false }
            startNight()
        case .assassinNight, .mercenaryNight, .detectiveNight, .medicNight:
            if !targets.isEmpty {
                guard let target, targets.contains(target) else { return false }
                performNightAction(actor: 0, target: target)
            }
            advanceThroughPassiveNight()
        case .oracleNight:
            if let target {
                guard targets.contains(target) else { return false }
                inviteOracle(target)
            }
            transition(.dawn)
        case .mayorTieBreak:
            guard human.role == .mayor, human.alive, let target, targets.contains(target),
                  revealedMayorID == 0, silencedPlayer != 0 else { return false }
            resolveMayorTie(target)
        case .counterpoint:
            if let payador = living.first(where: { $0.role == .payador }), payador.id != 0 {
                guard let target = contrapuntoParticipants.sorted(by: {
                    if suspicion[$0, default: 0] != suspicion[$1, default: 0] { return suspicion[$0, default: 0] > suspicion[$1, default: 0] }
                    return $0 < $1
                }).first else { return false }
                finishContrapunto(target)
            } else {
                payadorPointedPlayer = nil
                transition(.voting)
                append("El Contrapunto terminó sin señalamiento.")
            }
        case .dawn:
            if let victim = nightTarget, victim != protectedPlayer {
                players[victim].alive = false
                append("\(name(victim)) murió durante la noche.")
            } else { append("Amanece sin víctimas.") }
            if let silenced = silencedPlayer {
                if silenced == protectedPlayer || !players[silenced].alive {
                    silencedPlayer = nil
                } else {
                    lastSilencedRounds[silenced] = round
                    append("\(name(silenced)) no puede hablar ni votar durante el día.")
                }
            }
            transition(.discussion)
            checkWinner()
            if winner == nil && !deserterReconsiderationPending {
                if let guest = oracleGuest { append("El Oráculo invocó a \(name(guest)). Hoy puede hablar, pero no votar ni usar habilidades.") }
                botDebate()
                botDayAbilities()
            }
        case .discussion:
            transition(.voting)
            append("Comienza la votación. No se permite votar por uno mismo.")
        case .voting, .tieVote:
            closeVoting(humanTarget: target)
        case .voteCount:
            if voteRound == 1 && tieCandidates.count > 1 {
                votes = [:]
                transition(.tieVote)
                append("Empate. Voten entre \(tieCandidates.map { name($0) }.joined(separator: ", ")).")
            } else if voteRound == 2 && tieCandidates.count > 1,
                      let mayor = players.first(where: { $0.role == .mayor }),
                      (!mayor.alive || mayor.id == silencedPlayer),
                      revealedMayorID != mayor.id && !(advanced.revealRolesOnDeath && !mayor.alive) {
                eliminationTarget = nil
                transition(.mayorTieBreak)
                append("El empate se repitió. El Alcalde puede decidir entre los empatados.")
            } else if voteRound == 2 && tieCandidates.count > 1,
                      let mayor = living.first(where: { $0.role == .mayor && $0.id != silencedPlayer }) {
                if tieCandidates.contains(mayor.id) { revealMayor(mayor.id) }
                tieCandidates.removeAll { $0 == mayor.id }
                if tieCandidates.count == 1 {
                    revealMayor(mayor.id)
                    resolveMayorTie(tieCandidates[0])
                } else if mayor.id != 0 {
                    revealMayor(mayor.id)
                    resolveMayorTie(pick(tieCandidates, actor: mayor.id, purpose: .voting)!)
                } else {
                    transition(.mayorTieBreak)
                    append("El empate se repitió. Revelá tu cargo y elegí quién será expulsado.")
                }
            } else {
                if tieCandidates.count > 1 { eliminationTarget = nil }
                transition(.result)
                append(eliminationTarget.map { "\(name($0)) será expulsado." }
                       ?? "Nadie será expulsado: no hubo una mayoría única.")
            }
        case .result:
            if let id = eliminationTarget, living.contains(where: { $0.id == id }) {
                players[id].alive = false
                append("\(name(id)) fue expulsado por el pueblo.")
                if players[id].role == .jester && !specialVictories.contains(where: { $0.playerID == id }) {
                    specialVictories.append(.init(playerID: id, reason: "bufon_expulsado", round: round))
                    append("\(name(id)) era el Bufón y ganó al ser expulsado por el pueblo. La partida sigue si ningún bando ganó.")
                }
            }
            checkWinner()
            if winner == nil && !deserterReconsiderationPending { round += 1; startNight() }
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

    /// The clock may close a ballot without a human vote. Bots still cast their
    /// votes; no choice is silently invented for the player.
    @discardableResult
    public mutating func expireVoting(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil,
              phase == .voting || phase == .tieVote else { return false }
        closeVoting(humanTarget: nil)
        return true
    }

    @discardableResult
    public mutating func expireMayorTie(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil, phase == .mayorTieBreak else { return false }
        eliminationTarget = nil; transition(.result)
        append("El Alcalde no decidió el empate. Nadie será expulsado.")
        return true
    }

    private mutating func closeVoting(humanTarget: Int?) {
        var ballot: [Int: Int] = [:]
        // Decisions use the same pre-ballot knowledge; bots cannot read the human's vote.
        for player in living {
            if player.id == 0 {
                if let humanTarget, legalTargets(for: 0).contains(humanTarget) {
                    ballot[0] = humanTarget
                }
            } else if let choice = botChoice(actor: player.id) {
                ballot[player.id] = choice
            }
        }
        recordVotes(forcedTieBallot(from: ballot) ?? ballot)
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
        for _ in 0..<5 {
            nextNightPhase()
            resolveBotNight()
            if !isNight || !legalTargets(for: 0).isEmpty { break }
        }
    }

    @discardableResult
    public mutating func accuse(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, canSpeak(0), winner == nil,
              !humanSpoke,
              living.contains(where: { $0.id == target && target != 0 }) else { return false }
        humanSpoke = true
        humanAccusation = target
        suspicion[target, default: 0] += 1
        append("Sospecho de \(name(target)). Quiero escuchar su versión.", speaker: 0)
        // This is a structured local debate, not a language-model chatbot.
        if canSpeak(target) { append("Una acusación no es una prueba. Miren también cómo votamos.", speaker: target) }
        return true
    }

    /// Adds a player's public message to the local debate. The response is
    /// deliberately deterministic and local; it never sends text to a server.
    @discardableResult
    public mutating func sendPublicMessage(_ text: String, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, canSpeak(0) else { return false }
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

        if let responder = (mentioned.map { canSpeak($0.id) } == true ? mentioned : nil)
            ?? players.first(where: { $0.id != 0 && canSpeak($0.id) }) {
            let answer = mentioned != nil
                ? (accusation ? "¿Qué prueba tenés contra mí? Escuchemos a los demás."
                   : "Te escucho. Comparemos lo que pasó esta noche.")
                : "Antes de votar, comparemos las versiones de todos."
            append(answer, speaker: responder.id)
        }
        return true
    }

    /// Private night chat is stored separately from public debate. Bot replies
    /// can be added later without ever exposing this channel to the town.
    @discardableResult
    public mutating func sendTraitorMessage(_ text: String, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, isNight, winner == nil,
              human.alive, [.assassin, .mercenary, .spy].contains(human.role) else { return false }
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(140))
        guard !clean.isEmpty else { return false }
        messageSequence += 1
        var channel = traitorMessages ?? []
        channel.append(.init(id: messageSequence, round: round, speaker: 0, text: clean))
        traitorMessages = Array(channel.suffix(100))
        return true
    }

    @discardableResult
    public mutating func shareInvestigation(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, canSpeak(0), !humanSharedRead,
              let read = humanInvestigations.last else { return false }
        humanSharedRead = true
        declare(read)
        return true
    }

    @discardableResult
    public mutating func revealMayor(expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil, human.alive, human.role == .mayor,
              silencedPlayer != 0, [.discussion, .voting, .tieVote, .mayorTieBreak].contains(phase),
              revealedMayorID == nil else { return false }
        revealMayor(0)
        return true
    }

    @discardableResult
    public mutating func chooseMayorTie(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phase == .mayorTieBreak else { return false }
        return advance(target: target, expectedPhaseIndex: expectedPhaseIndex)
    }

    @discardableResult
    public mutating func chooseDeserterTeam(_ team: RoleTeam, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, winner == nil, human.alive, human.role == .deserter,
              team != .neutral, (deserterTeam == nil && phase == .assignment) || deserterReconsiderationAvailable else { return false }
        if deserterTeam != nil { deserterReconsiderationUsed = true }
        deserterTeam = team
        deserterReconsiderationPending = false
        checkWinner()
        return true
    }

    @discardableResult
    public mutating func chooseContrapuntoPlayer(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .discussion, winner == nil,
              human.alive, human.role == .payador, map == .pampa, !payadorUsed,
              living.contains(where: { $0.id == target && target != 0 }),
              !contrapuntoParticipants.contains(target) else { return false }
        addContrapuntoPlayer(target)
        return true
    }

    @discardableResult
    public mutating func pointContrapuntoPlayer(_ target: Int, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .counterpoint, winner == nil,
              human.alive, human.role == .payador, map == .pampa,
              contrapuntoParticipants.contains(target) else { return false }
        finishContrapunto(target)
        return true
    }

    /// nil skips this night without consuming the once-per-match power.
    @discardableResult
    public mutating func invokeOracle(_ target: Int?, expectedPhaseIndex: Int) -> Bool {
        guard phaseIndex == expectedPhaseIndex, phase == .oracleNight, winner == nil,
              human.alive, human.role == .oracle, map == .greece, !oracleUsed, round > 1 else { return false }
        return advance(target: target, expectedPhaseIndex: expectedPhaseIndex)
    }

    private mutating func revealMayor(_ id: Int) {
        guard revealedMayorID == nil, players[id].alive, silencedPlayer != id else { return }
        revealedMayorID = id
        append("\(name(id)) se reveló como Alcalde. Su voto vale doble y puede decidir el segundo empate.")
    }

    private mutating func resolveMayorTie(_ target: Int) {
        eliminationTarget = target
        votes = [:]; tieCandidates = []; voteRound = 3
        transition(.voteCount)
        append("El Alcalde decidió: \(name(target)) será expulsado.")
    }

    private mutating func addContrapuntoPlayer(_ target: Int) {
        contrapuntoParticipants.append(target)
        if contrapuntoParticipants.count == 2 {
            payadorUsed = true
            transition(.counterpoint)
            append("Se abre un Contrapunto entre \(name(contrapuntoParticipants[0])) y \(name(target)). Solo ellos pueden hablar; el Payador escucha.")
            for id in contrapuntoParticipants where id != 0 && canSpeak(id) {
                append("Escuchá mi versión antes de señalarme. Mirá también cómo votamos.", speaker: id)
            }
        }
    }

    private mutating func finishContrapunto(_ target: Int) {
        payadorPointedPlayer = target
        transition(.voting)
        append("El Contrapunto terminó. \(name(target)) quedó señalado y recibe un voto adicional.")
    }

    private mutating func inviteOracle(_ target: Int) {
        oracleUsed = true
        oracleGuest = target
    }

    private mutating func botDayAbilities() {
        if round >= 2, let mayor = living.first(where: { $0.role == .mayor && $0.id != 0 }) { revealMayor(mayor.id) }
        if map == .pampa, !payadorUsed, let payador = living.first(where: { $0.role == .payador && $0.id != 0 }) {
            let pair = living.filter { $0.id != payador.id }.map(\.id).sorted {
                if suspicion[$0, default: 0] != suspicion[$1, default: 0] { return suspicion[$0, default: 0] > suspicion[$1, default: 0] }
                return $0 < $1
            }
            if pair.count >= 2 { addContrapuntoPlayer(pair[0]); addContrapuntoPlayer(pair[1]) }
        }
    }

    private mutating func botDeserterReconsider() {
        guard deserterReconsiderationAvailable,
              let deserter = living.first(where: { $0.role == .deserter }), deserter.id != 0 else { return }
        // A simple local policy based on public living counts, with no model or network.
        deserterTeam = living.count <= players.count / 2 ? .traitors : .town
        deserterReconsiderationUsed = true
    }

    internal mutating func recordVotes(_ ballot: [Int: Int]) {
        guard phase == .voting || phase == .tieVote else { return }
        votes = ballot.filter { legalTargets(for: $0.key).contains($0.value) }
        let totals = voteTotals
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
        botDeserterReconsider()
        let candidate = Self.winner(for: players, deserterTeam: deserterTeam)
        deserterReconsiderationPending = candidate == .traitors && human.alive &&
            human.role == .deserter && deserterReconsiderationAvailable
        winner = deserterReconsiderationPending ? nil : candidate
        if let winner { transition(.result); append("Victoria de \(winner.rawValue).") }
    }

    private mutating func startNight() {
        nightTarget = nil; protectedPlayer = nil; silencedPlayer = nil; eliminationTarget = nil
        oracleGuest = nil; contrapuntoParticipants = []; payadorPointedPlayer = nil
        votes = [:]; tieCandidates = []; voteRound = 0
        humanSpoke = false; humanSharedRead = false
        humanAccusation = nil
        suspicion = suspicion.mapValues { $0 / 2 }
        transition(.assassinNight)
        append("Noche \(round). \(map.title) duerme.")
        resolveBotNight()
        // A player with a later night role acts as soon as night falls, instead of
        // waiting on the bot-only phases before theirs. Without an action tonight
        // the night stays on the passive wait, so dawn never comes without the timer.
        if legalTargets(for: 0).isEmpty {
            var probe = self
            probe.advanceThroughPassiveNight()
            if probe.isNight { self = probe }
        }
    }

    private mutating func nextNightPhase() {
        switch phase {
        case .assassinNight:
            transition(living.contains(where: { $0.role == .mercenary }) ? .mercenaryNight : .detectiveNight)
        case .mercenaryNight: transition(.detectiveNight)
        case .detectiveNight: transition(.medicNight)
        case .medicNight:
            transition(map == .greece && round > 1 && !oracleUsed && players.contains { !$0.alive } && living.contains { $0.role == .oracle } ? .oracleNight : .dawn)
        case .oracleNight: transition(.dawn)
        default: break
        }
    }

    private mutating func resolveBotNight() {
        guard isNight else { return }
        if phase == .assassinNight {
            // Android collects a vote from every living killer. A human choice
            // coordinates the bot killers' votes in its local resolution path.
            if !living.contains(where: { $0.id == 0 && [.assassin, .spy].contains($0.role) }) {
                resolveKillVotes(humanTarget: nil)
            }
            return
        }
        let role: RoleKey = switch phase {
        case .mercenaryNight: .mercenary
        case .detectiveNight: .detective
        case .oracleNight: .oracle
        default: .medic
        }
        if let actor = living.first(where: { $0.role == role }), actor.id != 0,
           let target = botChoice(actor: actor.id) {
            performNightAction(actor: actor.id, target: target)
        }
    }

    private mutating func performNightAction(actor: Int, target: Int) {
        switch phase {
        case .assassinNight: resolveKillVotes(humanTarget: target)
        case .mercenaryNight: silencedPlayer = target
        case .oracleNight: inviteOracle(target)
        case .detectiveNight:
            investigations.append(.init(round: round, investigator: actor, target: target,
                                        suspicious: [.assassin, .mercenary].contains(players[target].role)))
        case .medicNight: protectedPlayer = target
        default: break
        }
    }

    private mutating func resolveKillVotes(humanTarget: Int?) {
        let killers = living.filter { [.assassin, .spy].contains($0.role) }
        let choices = killers.compactMap { killer -> Int? in
            if killer.id == 0 { return humanTarget }
            if let humanTarget, legalTargets(for: killer.id).contains(humanTarget) {
                return humanTarget
            }
            return botChoice(actor: killer.id)
        }
        let counts = Dictionary(grouping: choices, by: { $0 }).mapValues(\.count)
        let highest = counts.values.max() ?? 0
        let tied = counts.keys.filter { counts[$0] == highest }.sorted()
        nightTarget = tied.randomElement(using: &random)
    }

    private mutating func declare(_ read: Investigation) {
        if !declaredDetectives.contains(read.investigator) { declaredDetectives.append(read.investigator) }
        suspicion[read.target, default: 0] += read.suspicious ? 3 : -2
        append("Soy el Comisario. Investigué a \(name(read.target)): parece \(read.suspicious ? "sospechoso" : "inocente").",
               speaker: read.investigator)
    }

    private mutating func botDebate() {
        for actor in players where actor.id != 0 && canSpeak(actor.id) {
            if actor.role == .jester {
                append("Votame si querés, me banco toda la sospecha.", speaker: actor.id)
            } else if actor.alive, let read = investigations.last(where: { $0.investigator == actor.id && $0.round == round }) {
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
                let firstBase = (original[0] == first ? (revealedMayorID == 0 ? 2 : 1) : 0) + (payadorPointedPlayer == first ? 1 : 0)
                let secondBase = (original[0] == second ? (revealedMayorID == 0 ? 2 : 1) : 0) + (payadorPointedPlayer == second ? 1 : 0)
                if let result = tieAssignment(bots: bots, index: 0, first: first, second: second,
                                              firstVotes: firstBase, secondVotes: secondBase,
                                              otherVotes: (original[0].map { $0 != first && $0 != second ? (revealedMayorID == 0 ? 2 : 1) : 0 } ?? 0) +
                                                  (payadorPointedPlayer.map { $0 != first && $0 != second ? 1 : 0 } ?? 0),
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
        guard abs(firstVotes - secondVotes) <= remaining * 2,
              otherVotes <= 2 else { return nil }
        if index == bots.count {
            return firstVotes == secondVotes && firstVotes > otherVotes ? ballot : nil
        }
        let bot = bots[index]
        let weight = revealedMayorID == bot ? 2 : 1
        let legal = legalTargets(for: bot)
        for target in [first, second] where legal.contains(target) {
            var next = ballot
            next[bot] = target
            if let found = tieAssignment(bots: bots, index: index + 1, first: first, second: second,
                                         firstVotes: firstVotes + (target == first ? weight : 0),
                                         secondVotes: secondVotes + (target == second ? weight : 0),
                                         otherVotes: otherVotes, ballot: next) { return found }
        }
        if otherVotes == 0, let third = legal.first(where: { $0 != first && $0 != second }) {
            var next = ballot
            next[bot] = third
            return tieAssignment(bots: bots, index: index + 1, first: first, second: second,
                                 firstVotes: firstVotes, secondVotes: secondVotes,
                                 otherVotes: weight, ballot: next)
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
            // Assassins can sow doubt about the detective, using public claims only.
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
