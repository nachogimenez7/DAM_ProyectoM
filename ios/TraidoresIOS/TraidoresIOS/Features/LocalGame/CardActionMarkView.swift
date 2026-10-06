import SwiftUI
import TraidoresCore

/// Android's card action marks (`CardActionMarks`, `CardActionAnimations`): the dagger,
/// magnifier, cross, rope, door and guitar stamped on the card a role acted on.
enum CardActionMarkKind: Equatable {
    case assassin, spy, detective, medic, mercenary, oracle, payador

    /// The mark of the player's own confirmed night action in this phase.
    init?(phase: GamePhase, role: RoleKey) {
        switch (phase, role) {
        case (.assassinNight, .assassin): self = .assassin
        case (.assassinNight, .spy): self = .spy
        case (.mercenaryNight, .mercenary): self = .mercenary
        case (.detectiveNight, .detective): self = .detective
        case (.medicNight, .medic): self = .medic
        case (.oracleNight, .oracle): self = .oracle
        default: return nil
        }
    }

    /// A teammate's traitor mark.
    init?(traitor role: RoleKey) {
        switch role {
        case .assassin: self = .assassin
        case .spy: self = .spy
        case .mercenary: self = .mercenary
        default: return nil
        }
    }

    var image: String {
        switch self {
        case .assassin: "action_mark_assassin"
        case .spy: "action_mark_spy"
        case .detective: "action_mark_detective"
        case .medic: "action_mark_medic"
        case .mercenary: "action_mark_mercenary"
        case .oracle: "action_mark_oracle"
        case .payador: "action_mark_payador"
        }
    }

    func description(target: String) -> String {
        switch self {
        case .assassin: "Elegiste eliminar a \(target)"
        case .spy: "Marcaste a \(target)"
        case .detective: "Investigación sobre \(target)"
        case .medic: "Protección sobre \(target)"
        case .mercenary: "Silencio sobre \(target)"
        case .oracle: "Invocación de \(target)"
        case .payador: "Contrapunto con \(target)"
        }
    }

    /// Each mark's entrance, from Android's `CardActionAnimations.forRole`.
    fileprivate var entrance: (scaleX: CGFloat, scaleY: CGFloat, rotation: Double,
                               offsetX: CGFloat, offsetY: CGFloat, duration: Double) {
        switch self {
        case .assassin, .spy: (1.28, 1.28, -18, 0, -0.34, 0.41)
        case .detective: (0.72, 0.72, -22, -0.42, 0, 0.56)
        case .medic: (0.48, 0.48, -5, 0, 0.22, 0.52)
        case .mercenary: (0.16, 0.92, 0, 0, 0, 0.61)
        case .oracle: (0.66, 0.32, 0, 0, 0.34, 0.65)
        case .payador: (0.88, 0.88, -15, -0.08, 0, 0.62)
        }
    }
}

/// The mark over a card, sized like Android: the rope covers the whole card, the rest
/// about three quarters of it. It springs in once when it appears.
struct CardActionMarkView: View {
    let kind: CardActionMarkKind
    let cardSize: CGSize
    /// Fractions of the card when several marks share it.
    var sizeOverride: CGSize?
    var restingRotation: Double = 0
    var delay: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var landed = false

    var body: some View {
        let rope = kind == .mercenary
        let entrance = kind.entrance
        let fraction = sizeOverride ?? (rope ? CGSize(width: 1, height: 1) : CGSize(width: 0.78, height: 0.72))
        Image(kind.image)
            .resizable().scaledToFit()
            .frame(width: cardSize.width * fraction.width, height: cardSize.height * fraction.height)
            .shadow(color: .black.opacity(0.55), radius: 3, y: 1)
            .scaleEffect(x: landed ? 1 : entrance.scaleX, y: landed ? 1 : entrance.scaleY)
            .rotationEffect(.degrees(restingRotation + (landed ? 0 : entrance.rotation)))
            .offset(x: landed ? 0 : cardSize.width * entrance.offsetX,
                    y: landed ? 0 : cardSize.height * entrance.offsetY)
            .opacity(landed ? 1 : 0)
            // The table disables inherited animations (`.transaction`), so the mark carries
            // its own: this modifier sits below that one and wins.
            .animation(reduceMotion ? .easeOut(duration: 0.2).delay(delay)
                       : .spring(duration: entrance.duration, bounce: 0.35).delay(delay), value: landed)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear { landed = true }
    }
}

/// The player's confirmed night action, kept on the card until the day begins.
struct StampedMark: Equatable {
    let target: Int
    let kind: CardActionMarkKind
    let round: Int
}

/// One mark on a card, with who made it when the table can know (a killer's partner).
struct CardActionMark: Hashable {
    let kind: CardActionMarkKind
    var actor: String?
    /// Seconds before it lands: a partner's dagger follows the player's, as if deciding.
    var delay: Double = 0
}

/// Up to three marks on the same card, laid out like Android's `actionMarkLayoutParams`:
/// two killers cross their daggers, the Mercenario's rope keeps its own side, and each
/// partner's mark carries a small plate with their name.
struct CardActionMarksView: View {
    let marks: [CardActionMark]
    let cardSize: CGSize

    var body: some View {
        let visible = Array(marks.prefix(3))
        let hasRope = visible.contains { $0.kind == .mercenary }
        ZStack {
            ForEach(Array(visible.enumerated()), id: \.element) { index, mark in
                let layout = Self.layout(mark.kind, index: index, count: visible.count, hasRope: hasRope)
                CardActionMarkView(kind: mark.kind, cardSize: cardSize, sizeOverride: layout.size,
                                   restingRotation: layout.rotation, delay: max(mark.delay, Double(index) * 0.09))
                    .frame(width: cardSize.width, height: cardSize.height, alignment: layout.alignment)
                    .offset(x: cardSize.width * layout.offset.width, y: cardSize.height * layout.offset.height)
                    .overlay(alignment: layout.plateAlignment) {
                        if let actor = mark.actor {
                            DelayedAppearance(delay: mark.delay) { plate(actor, kind: mark.kind, bottom: layout.plateBottom) }
                        }
                    }
            }
        }
        .frame(width: cardSize.width, height: cardSize.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private struct Layout {
        var size: CGSize
        var alignment: Alignment = .center
        var offset: CGSize = .zero
        var rotation: Double = 0
        var plateAlignment: Alignment = .bottom
        var plateBottom: CGFloat = 15
    }

    private static func layout(_ kind: CardActionMarkKind, index: Int, count: Int, hasRope: Bool) -> Layout {
        let rope = kind == .mercenary
        let killer = kind == .assassin || kind == .spy
        if count == 3 {
            return rope
                ? .init(size: .init(width: 0.58, height: 0.9), alignment: .trailing, plateAlignment: .bottomTrailing, plateBottom: 2)
                : .init(size: .init(width: 0.62, height: 0.56), alignment: index == 0 ? .topLeading : .bottomLeading,
                        plateAlignment: .bottomLeading, plateBottom: index == 0 ? 14 : 2)
        }
        if count == 2 && hasRope {
            return .init(size: rope ? .init(width: 0.58, height: 0.9) : .init(width: 0.68, height: 0.72),
                         alignment: rope ? .trailing : .leading,
                         plateAlignment: rope ? .bottomTrailing : .bottomLeading, plateBottom: 2)
        }
        if count == 2 {
            let sign: CGFloat = index == 0 ? -1 : 1
            return .init(size: .init(width: 0.78, height: 0.72), offset: .init(width: sign / 9, height: sign / 12),
                         rotation: killer ? Double(sign) * 11 : 0, plateBottom: index == 0 ? 28 : 15)
        }
        return .init(size: rope ? .init(width: 1, height: 1) : .init(width: 0.78, height: 0.72))
    }

    private func plate(_ actor: String, kind: CardActionMarkKind, bottom: CGFloat) -> some View {
        let color = switch kind {
        case .spy: Color(hex: "#E36B159B")
        case .mercenary: Color(hex: "#E37B551F")
        default: Color(hex: "#E3A91419")
        }
        return Text(actor)
            .font(.system(size: 7, weight: .bold)).foregroundStyle(.white)
            .lineLimit(1).minimumScaleFactor(0.6)
            .padding(.horizontal, 2)
            .frame(maxWidth: cardSize.width - 2, minHeight: 12)
            .background(color, in: RoundedRectangle(cornerRadius: 3))
            .overlay(RoundedRectangle(cornerRadius: 3).stroke(Color(hex: "#F4D79B"), lineWidth: 1))
            .fixedSize(horizontal: true, vertical: false)
            .padding(.bottom, bottom)
    }
}

/// The partner seal on a fellow traitor's card: their mark in a small dark red coin.
struct TeammateSeal: View {
    let kind: CardActionMarkKind

    var body: some View {
        Image(kind.image).resizable().scaledToFit()
            .padding(3)
            .frame(width: 20, height: 20)
            .background(Color(hex: "#5A1418"), in: Circle())
            .overlay(Circle().stroke(Color(hex: "#F4D79B"), lineWidth: 1))
            .shadow(color: .black.opacity(0.6), radius: 2, y: 1)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Fades its content in after a delay, so a partner's name plate arrives with their mark.
private struct DelayedAppearance<Content: View>: View {
    let delay: Double
    @ViewBuilder let content: Content
    @State private var visible = false

    var body: some View {
        content
            .opacity(visible ? 1 : 0)
            .animation(.easeOut(duration: 0.2).delay(delay + 0.15), value: visible)
            .onAppear { visible = true }
    }
}
