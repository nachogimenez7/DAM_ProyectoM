import Foundation

// Quick chat, ported from Android's BotQuickReplies/GameplayChatController. It only
// builds ordinary chat text: choosing a message never votes or acts at night. The
// catalog is independent of the local engine so the same chips serve online rooms.

/// What the player meant, kept next to the text for bots (Android's HumanMessageIntent).
public enum QuickChatIntent: String, Codable, Sendable {
    case accuse, defend, roleQuestion, voteHelp, suspectHelp, roleClaim
    case actionClaim, refuseRole, doubt, casual
}

public struct QuickChatMessage: Hashable, Sendable {
    public let text: String
    public let intent: QuickChatIntent
    public init(_ text: String, _ intent: QuickChatIntent) {
        self.text = text
        self.intent = intent
    }
}

public struct QuickChatPlayer: Hashable, Sendable {
    public let id: Int
    public let name: String
    public init(id: Int, name: String) {
        self.id = id
        self.name = name
    }
}

public struct QuickChatRole: Hashable, Sendable {
    public let key: RoleKey
    /// Title on the current map ("Comisario" in Pampa).
    public let title: String
    public init(key: RoleKey, title: String) {
        self.key = key
        self.title = title
    }
}

/// A phrase that still needs a player, chosen by tapping that player's card.
public enum QuickChatTemplate: String, Hashable, Sendable {
    case suspect, defend, askRole, askVote, askExplanation, investigation, protection
    case voteFor, voteTogether, hearFirst
    case killProposal, doubtKill, silence, watchOut

    /// Shown while the cards are waiting for a tap.
    public var prompt: String {
        switch self {
        case .suspect: "¿De quién sospechás?"
        case .defend: "¿A quién defendés?"
        case .askRole, .askVote, .askExplanation: "¿A quién le preguntás?"
        case .investigation: "¿A quién investigaste?"
        case .protection: "¿A quién protegiste?"
        case .voteFor, .voteTogether: "¿A quién votarías?"
        case .hearFirst: "¿A quién querés escuchar?"
        case .killProposal: "¿A quién matamos?"
        case .doubtKill: "¿A quién no tocamos?"
        case .silence: "¿A quién silenciamos?"
        case .watchOut: "¿De quién nos cuidamos?"
        }
    }

    /// Short label printed on the selectable cards.
    public var cardLabel: String {
        switch self {
        case .suspect: "SOSPECHO"
        case .defend: "DEFIENDO"
        case .askRole, .askVote, .askExplanation: "PREGUNTAR"
        case .investigation: "INVESTIGUÉ"
        case .protection: "PROTEGÍ"
        case .voteFor, .voteTogether: "VOTARÍA"
        case .hearFirst: "ESCUCHAR"
        case .killProposal: "MATEMOS"
        case .doubtKill: "A ESE NO"
        case .silence: "SILENCIAR"
        case .watchOut: "CUIDADO"
        }
    }

    /// Plan phrases pick among non-allies; town phrases among every other living player.
    public var traitorPlan: Bool {
        [.killProposal, .doubtKill, .silence, .watchOut].contains(self)
    }

    /// An investigation also needs its result before it becomes a message.
    public var needsInvestigationResult: Bool { self == .investigation }

    public func message(target: String, suspicious: Bool = false) -> QuickChatMessage {
        switch self {
        case .suspect: QuickChat.suspect(target)
        case .defend: QuickChat.defend(target)
        case .askRole: .init("\(target), ¿qué rol sos?", .roleQuestion)
        case .askVote: .init("\(target), ¿a quién votarías?", .voteHelp)
        case .askExplanation: .init("\(target), ¿por qué sospechás de mí?", .suspectHelp)
        case .investigation:
            .init("Investigué a \(target) y me dio \(suspicious ? "sospechoso" : "inocente")", .actionClaim)
        case .protection: .init("Protegí a \(target) anoche", .actionClaim)
        case .voteFor: .init("Yo votaría a \(target)", .accuse)
        case .voteTogether: .init("Votemos a \(target)", .accuse)
        case .hearFirst: QuickChat.hearFirst(target)
        case .killProposal: .init("Matemos a \(target)", .accuse)
        case .doubtKill: .init("A \(target) no, mejor otro", .doubt)
        case .silence: .init("Silenciemos a \(target)", .accuse)
        case .watchOut: .init("Cuidado con \(target)", .doubt)
        }
    }
}

/// What a chip or menu entry does when tapped.
public indirect enum QuickChatAction: Hashable, Sendable {
    case send(QuickChatMessage)
    case pickPlayer(QuickChatTemplate)
    /// `alibi` lists only town roles and phrases it as tomorrow's cover story.
    case pickRole(alibi: Bool)
    case menu([QuickChatItem])
}

public struct QuickChatItem: Hashable, Sendable, Identifiable {
    public let title: String
    public let action: QuickChatAction
    public var id: String { title }
    public init(_ title: String, _ action: QuickChatAction) {
        self.title = title
        self.action = action
    }
}

/// Everything the catalog needs to know, from a local match or an online room.
public struct QuickChatContext: Sendable {
    /// Night chat of the killers (Asesinos, Mercenario, Espía).
    public var traitorChannel: Bool
    public var humanName: String
    public var humanRole: QuickChatRole
    /// Living players other than the human.
    public var others: [QuickChatPlayer]
    /// Living players outside the traitor team (the plan's possible targets).
    public var planTargets: [QuickChatPlayer]
    /// Roles dealt in this match, with their titles on the current map.
    public var rolesInPlay: [QuickChatRole]
    /// Recent public (or, for traitorChannel, plan) chat as (speaker name, text), oldest first.
    public var recentMessages: [(speaker: String, text: String)]
    public var jesterInPlay: Bool

    public init(traitorChannel: Bool, humanName: String, humanRole: QuickChatRole,
                others: [QuickChatPlayer], planTargets: [QuickChatPlayer],
                rolesInPlay: [QuickChatRole], recentMessages: [(speaker: String, text: String)],
                jesterInPlay: Bool) {
        self.traitorChannel = traitorChannel
        self.humanName = humanName
        self.humanRole = humanRole
        self.others = others
        self.planTargets = planTargets
        self.rolesInPlay = rolesInPlay
        self.recentMessages = recentMessages
        self.jesterInPlay = jesterInPlay
    }
}

public enum QuickChat {
    static func suspect(_ target: String) -> QuickChatMessage { .init("Sospecho de \(target)", .accuse) }
    static func defend(_ target: String) -> QuickChatMessage { .init("Yo no votaría a \(target) todavía", .defend) }
    static func hearFirst(_ target: String) -> QuickChatMessage { .init("Quiero escuchar a \(target) primero", .suspectHelp) }

    public static func claim(_ role: QuickChatRole, alibi: Bool = false) -> QuickChatMessage {
        alibi ? .init("Mañana digo que soy \(role.title.lowercased())", .roleClaim)
              : .init("Soy \(role.title.lowercased())", .roleClaim)
    }

    /// Up to three contextual chips; the view adds «MÁS» after them.
    public static func chips(_ context: QuickChatContext) -> [QuickChatItem] {
        let chips = context.traitorChannel ? traitorChips(context) : townChips(context)
        var seen = Set<String>()
        return Array(chips.filter { seen.insert($0.title).inserted }.prefix(3))
    }

    /// The «MÁS» menu, grouped like Android's quick message categories.
    public static func menu(_ context: QuickChatContext) -> [QuickChatItem] {
        if context.traitorChannel {
            return [
                .init("Matemos a…", .pickPlayer(.killProposal)),
                .init("A ese no…", .pickPlayer(.doubtKill)),
                .init("Silenciemos a…", .pickPlayer(.silence)),
                .init("Cuidado con…", .pickPlayer(.watchOut)),
                .init("Cúbranme…", .menu([
                    .init("Me están marcando, cúbranme", .send(.init("Me están marcando, cúbranme", .suspectHelp))),
                    .init("Mañana digo que soy…", .pickRole(alibi: true)),
                    .init("Estoy limpio, hablo yo", .send(.init("Yo estoy limpio, hablo yo", .defend))),
                    .init("Mañana no nos crucemos", .send(.init("Mañana no nos crucemos", .voteHelp))),
                    .init("Vamos tranquilos, sin regalarnos", .send(.init("Vamos tranquilos, sin regalarnos", .voteHelp))),
                ])),
                .init("Cerrado, quedamos así", .send(.init("Cerrado, quedamos así", .casual))),
            ]
        }
        return [
            .init("Sospechar de…", .pickPlayer(.suspect)),
            .init("Defender a…", .pickPlayer(.defend)),
            .init("Preguntar…", .menu([
                .init("¿Qué rol sos?", .pickPlayer(.askRole)),
                .init("¿A quién votarías?", .pickPlayer(.askVote)),
                .init("¿Por qué sospechás de mí?", .pickPlayer(.askExplanation)),
            ])),
            .init("Decir mi rol…", .pickRole(alibi: false)),
            .init("Informar una acción…", .menu([
                .init("Investigación…", .pickPlayer(.investigation)),
                .init("Protección…", .pickPlayer(.protection)),
            ])),
            .init("Estrategia de voto…", .menu([
                .init("Votaría a…", .pickPlayer(.voteFor)),
                .init("No votemos apurados", .send(.init("No votemos apurados todavía", .voteHelp))),
                .init("Quiero escuchar a…", .pickPlayer(.hearFirst)),
            ])),
        ]
    }

    /// Roles offered by «Soy…»: every role dealt on this map, or only town roles as an alibi.
    public static func claimableRoles(_ context: QuickChatContext, alibi: Bool) -> [QuickChatRole] {
        var seen = Set<RoleKey>()
        return context.rolesInPlay.filter { role in
            guard seen.insert(role.key).inserted else { return false }
            return !alibi || RoleCatalog.all.first { $0.id == role.key }?.team == .town
        }
    }

    private static func townChips(_ context: QuickChatContext) -> [QuickChatItem] {
        let general: [QuickChatItem] = [
            .init("Sospecho de…", .pickPlayer(.suspect)),
            .init("Soy…", .pickRole(alibi: false)),
            .init("Votemos a…", .pickPlayer(.voteTogether)),
        ]
        let human = context.humanName
        guard let last = context.recentMessages.last(where: { $0.speaker != human }) else { return general }
        let text = normalized(last.text)
        let humanSpoke = context.recentMessages.contains { $0.speaker == human }
        let claimed = lastClaim(context)
        let asksRole = ["que rol", "tu rol", "sos medico", "sos detective", "sos comisario", "sos aldeano"]
            .contains { text.contains($0) }
        let asksInvestigation = ["a quien investig", "a quien miraste", "que te dio", "nombre que investigaste"]
            .contains { text.contains($0) }
        let asksProtection = ["a quien cuid", "a quien proteg", "a quien salvaste"].contains { text.contains($0) }
        guard humanSpoke || asksRole || (claimed == .detective && asksInvestigation)
                || (claimed == .medic && asksProtection) else { return general }

        if claimed == .detective && asksInvestigation {
            return [
                .init("Investigué a…", .pickPlayer(.investigation)),
                .init("No lo voy a decir todavía", .send(.init("No lo voy a decir todavía", .refuseRole))),
                .init("Mentí, no soy \(title(.detective, context))", .send(.init("Mentí, no soy \(title(.detective, context))", .roleClaim))),
            ]
        }
        if claimed == .medic && asksProtection {
            return [
                .init("Protegí a…", .pickPlayer(.protection)),
                .init("Prefiero no decir a quién", .send(.init("Prefiero no decir a quién", .refuseRole))),
                .init("Mentí, no soy \(title(.medic, context))", .send(.init("Mentí, no soy \(title(.medic, context))", .roleClaim))),
            ]
        }
        if asksRole {
            let own = context.humanRole
            let decoy = own.key == .villager
                ? QuickChatRole(key: .detective, title: title(.detective, context))
                : QuickChatRole(key: .villager, title: title(.villager, context))
            return [own, decoy].map { .init(claim($0).text, .send(claim($0))) } + [
                .init("No voy a decir mi rol todavía", .send(.init("No voy a decir mi rol todavía", .refuseRole))),
            ]
        }
        if let target = mentioned(in: last.text, among: context.others) {
            var replies = [QuickChatItem(suspect(target.name).text, .send(suspect(target.name))),
                           QuickChatItem(hearFirst(target.name).text, .send(hearFirst(target.name)))]
            let third = context.jesterInPlay
                ? QuickChatMessage("Ojo, \(target.name) puede ser el \(title(.jester, context).lowercased())", .voteHelp)
                : defend(target.name)
            replies.append(.init(third.text, .send(third)))
            return replies
        }
        return general
    }

    /// If an ally just named tonight's target, answer that plan; otherwise offer the plan pickers.
    private static func traitorChips(_ context: QuickChatContext) -> [QuickChatItem] {
        if let last = context.recentMessages.last, last.speaker != context.humanName,
           let target = mentioned(in: last.text, among: context.planTargets) {
            let agree = QuickChatMessage("Dale, matemos a \(target.name)", .accuse)
            let doubt = QuickChatTemplate.doubtKill.message(target: target.name)
            return [.init(agree.text, .send(agree)), .init(doubt.text, .send(doubt)),
                    .init("Silenciemos a…", .pickPlayer(.silence))]
        }
        return [.init("Matemos a…", .pickPlayer(.killProposal)),
                .init("Silenciemos a…", .pickPlayer(.silence)),
                .init("Cuidado con…", .pickPlayer(.watchOut))]
    }

    private static func lastClaim(_ context: QuickChatContext) -> RoleKey? {
        for message in context.recentMessages.reversed() where message.speaker == context.humanName {
            let text = normalized(message.text)
            guard text.hasPrefix("soy ") else { continue }
            return context.rolesInPlay.first { text == "soy \(normalized($0.title))" }?.key
                ?? RoleCatalog.all.first { text == "soy \(normalized($0.title))" }?.id
        }
        return nil
    }

    private static func title(_ key: RoleKey, _ context: QuickChatContext) -> String {
        (context.rolesInPlay.first { $0.key == key }?.title
            ?? RoleCatalog.all.first { $0.id == key }?.title ?? key.rawValue).lowercased()
    }

    /// Longest name first, so «Ana María» wins over «Ana».
    static func mentioned(in text: String, among players: [QuickChatPlayer]) -> QuickChatPlayer? {
        let folded = normalized(text)
        return players.sorted { $0.name.count > $1.name.count }
            .first { folded.contains(normalized($0.name)) }
    }

    static func normalized(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "es"))
            .replacingOccurrences(of: "¿", with: "").replacingOccurrences(of: "?", with: "")
    }
}
