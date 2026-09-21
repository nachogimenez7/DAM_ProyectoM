// Wire identifiers are preserved from Android, even when UI names differ.
public enum GameMap: String, CaseIterable, Codable, Sendable {
    case pampa, greece = "grecia", medieval

    public var title: String {
        switch self {
        case .pampa: "Pampa"
        case .greece: "Grecia"
        case .medieval: "Medieval"
        }
    }
}

public enum GamePhase: String, CaseIterable, Codable, Sendable {
    case assignment = "REPARTO"
    case assassinNight = "NOCHE_ASESINO"
    case mercenaryNight = "NOCHE_MERCENARIO"
    case detectiveNight = "NOCHE_POLICIA"
    case medicNight = "NOCHE_MEDICO"
    case oracleNight = "NOCHE_ORACULO"
    case dawn = "AMANECER"
    case discussion = "DIA_DEBATE"
    case counterpoint = "CONTRAPUNTO"
    case voting = "VOTACION"
    case voteCount = "RECUENTO_VOTOS"
    case tieVote = "DESEMPATE_VOTACION"
    case mayorTieBreak = "ALCALDE_DESEMPATE"
    case result = "RESULTADO"
}

public enum RoleKey: String, CaseIterable, Codable, Sendable {
    case villager = "aldeano", detective = "policia", medic = "medico"
    case mayor = "alcalde", assassin = "asesino", spy = "espia"
    case mercenary = "mercenario", deserter = "desertor"
    case payador, jester = "bufon", oracle = "oraculo"
}

public enum RoleTeam: String, Codable, Sendable {
    case town = "Pueblo", traitors = "Traidores", neutral = "Neutral"
}

public enum BotDifficulty: String, CaseIterable, Codable, Sendable {
    case normal = "NORMAL"
    case hard = "DIFICIL"
}

public struct RoleDefinition: Identifiable, Sendable {
    public let id: RoleKey
    public let title: String
    public let team: RoleTeam
    public let minimumPlayers: Int
    public let exclusiveMap: GameMap?
    public let instructions: String

    public init(
        id: RoleKey, title: String, team: RoleTeam, minimumPlayers: Int,
        exclusiveMap: GameMap? = nil, instructions: String
    ) {
        self.id = id
        self.title = title
        self.team = team
        self.minimumPlayers = minimumPlayers
        self.exclusiveMap = exclusiveMap
        self.instructions = instructions
    }
}

/// Reference material for the menu. This catalog does not implement gameplay.
public enum RoleCatalog {
    public static let all: [RoleDefinition] = [
        .init(id: .villager, title: "Aldeano", team: .town, minimumPlayers: 5,
              instructions: "No tiene habilidad especial. Participa en el debate y en las votaciones para descubrir a los traidores."),
        .init(id: .detective, title: "Detective", team: .town, minimumPlayers: 5,
              instructions: "Cada noche investiga a un jugador y recibe una pista sobre si parece inocente o sospechoso. En la Pampa es el Comisario."),
        .init(id: .medic, title: "Médico", team: .town, minimumPlayers: 5,
              instructions: "Cada noche protege a un jugador. Si ese jugador iba a morir, la eliminación se cancela."),
        .init(id: .mayor, title: "Alcalde", team: .town, minimumPlayers: 8,
              instructions: "Puede revelar su identidad durante el debate. Desde ese momento su voto vale doble y decide entre los dos participantes más votados si hay empate."),
        .init(id: .assassin, title: "Asesino", team: .traitors, minimumPlayers: 5,
              instructions: "Los asesinos eligen en conjunto una víctima durante la noche. Si queda uno solo, decide por su cuenta."),
        .init(id: .spy, title: "Espía", team: .traitors, minimumPlayers: 10,
              instructions: "Elige la víctima cada noche junto a los asesinos, pero ante la investigación aparece como inocente."),
        .init(id: .mercenary, title: "Mercenario", team: .traitors, minimumPlayers: 7,
              instructions: "Forma parte del bando traidor. Puede impedir que una víctima hable o vote durante el día siguiente."),
        .init(id: .deserter, title: "Desertor", team: .neutral, minimumPlayers: 14,
              instructions: "Elige un bando al comenzar. Puede reconsiderarlo una sola vez cuando quedan aproximadamente dos tercios de los jugadores iniciales y debe sobrevivir para ganar con su bando final."),
        .init(id: .payador, title: "Payador", team: .town, minimumPlayers: 8, exclusiveMap: .pampa,
              instructions: "Una vez por partida elige dos participantes para un Contrapunto. Solo esos dos pueden hablar; al terminar señala a uno, que recibe un voto adicional."),
        .init(id: .jester, title: "Bufón", team: .neutral, minimumPlayers: 8, exclusiveMap: .medieval,
              instructions: "Busca que el pueblo lo expulse durante la votación. No gana si muere de noche ni por otra causa."),
        .init(id: .oracle, title: "Oráculo", team: .town, minimumPlayers: 8, exclusiveMap: .greece,
              instructions: "Una vez por partida puede invocar a un jugador muerto para el debate del día siguiente. El invitado puede hablar, pero no votar ni usar habilidades.")
    ]
}
