import SwiftUI
import TraidoresCore

/// Android's tie-break window (`tieVoteOverlay`): when the first ballot ties, a panel over
/// the table asks to vote again between the tied players, with the closing countdown, CHAT
/// to go back to the debate and REVELAR for a hidden Alcalde. Voting is direct, as on the
/// table: tapping a card casts the vote and it can change until the close.
struct TieVoteWindow: View {
    let candidates: [ClassicPlayer]
    let map: GameMap
    /// Cards the player may vote for; the rest (their own, or all when silenced) are dimmed.
    let votable: Set<Int>
    let votedTarget: Int?
    let remainingSeconds: Int?
    let subtitle: String
    let watchNotice: String?
    let onVote: (Int) -> Void
    let onChat: (() -> Void)?
    let onRevealMayor: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var risen = false

    var body: some View {
        ZStack {
            Color(hex: "#B8000000").ignoresSafeArea()
            ViewThatFits(in: .vertical) {
                panel
                ScrollView { panel.padding(.vertical, 24) }.scrollBounceBehavior(.basedOnSize)
            }
        }
        .opacity(risen ? 1 : 0)
        .onAppear {
            // The backdrop fades and the panel rises with a small bounce, like Android.
            withAnimation(reduceMotion ? .easeOut(duration: 0.18) : .spring(duration: 0.42, bounce: 0.3)) { risen = true }
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("tieVote.window")
    }

    private var panel: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text("DESEMPATE")
                    .font(TraidoresTheme.title(24)).foregroundStyle(TraidoresTheme.gold)
                    .lineLimit(1).minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
                if let remainingSeconds {
                    Text("\(max(remainingSeconds, 0))")
                        .font(.system(size: 16, weight: .bold, design: .rounded)).monospacedDigit()
                        .foregroundStyle(TraidoresTheme.gold)
                        .frame(width: 46, height: 34)
                        .background(Color(hex: "#E616110D"), in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color(hex: "#7FD4A24E"), lineWidth: 1))
                        .accessibilityLabel("Cierra en \(max(remainingSeconds, 0)) segundos")
                        .accessibilityIdentifier("tieVote.countdown")
                }
            }
            Text(subtitle)
                .font(.subheadline.bold()).foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center)
                .padding(.top, 1)
            ScrollView(.vertical, showsIndicators: candidates.count > 4) {
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(118), spacing: 10),
                                         count: min(candidates.count, 2)), spacing: 8) {
                    ForEach(candidates, id: \.id) { card($0) }
                }
                .padding(.vertical, 2)
            }
            .scrollBounceBehavior(.basedOnSize)
            .frame(maxHeight: candidates.count > 4 ? 300 : nil)
            .fixedSize(horizontal: false, vertical: candidates.count <= 4)
            .padding(.top, 14)
            Text(notice)
                .font(.footnote.bold()).foregroundStyle(Color(hex: "#F1D48B"))
                .multilineTextAlignment(.center)
                .padding(.top, 10)
                .accessibilityIdentifier("tieVote.notice")
            if onChat != nil || onRevealMayor != nil {
                HStack(spacing: 8) {
                    if let onChat { button("CHAT", id: "tieVote.chat", action: onChat) }
                    if let onRevealMayor { button("REVELAR", id: "tieVote.revealMayor", action: onRevealMayor) }
                }
                .frame(minHeight: 48)
                .padding(.top, 14)
            }
        }
        .padding(.horizontal, 8)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: 330)
        .modifier(RevealPanel(map: map))
        .offset(y: risen || reduceMotion ? 0 : 40)
        .padding(.horizontal, 16)
    }

    private var notice: String {
        if let votedTarget, let name = candidates.first(where: { $0.id == votedTarget })?.name {
            return "Votaste a \(name). Podés cambiar hasta el cierre."
        }
        return watchNotice ?? "SI EL EMPATE SE REPITE, NADIE SERÁ EXPULSADO."
    }

    private func card(_ player: ClassicPlayer) -> some View {
        let selected = votedTarget == player.id
        let enabled = votable.contains(player.id)
        let isHuman = player.id == 0
        return Button { onVote(player.id) } label: {
            VStack(spacing: 0) {
                Image("card_back_traidores").resizable().scaledToFit()
                    .frame(width: 52, height: 70)
                    .overlay(alignment: .top) {
                        GamePlayerAvatar(name: player.name, isHuman: isHuman, size: 30, avatarKey: player.avatarKey ?? AnimalAvatarCatalog.keys[max(0, player.id - 1) % 14]).padding(.top, 8)
                    }
                    .frame(width: 58, height: 74)
                Text(player.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(selected ? TraidoresTheme.gold : TraidoresTheme.text)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .frame(height: 22)
                Text(isHuman ? "TU CARTA" : selected ? "TU VOTO ✓" : "VOTAR")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(selected ? TraidoresTheme.gold : TraidoresTheme.secondary)
                    .lineLimit(1).minimumScaleFactor(0.7)
                    .frame(height: 18)
            }
            .padding(.horizontal, 6).padding(.top, 7).padding(.bottom, 5)
            .frame(maxWidth: .infinity)
            .background(Color(hex: selected ? "#F03B2A18" : "#E51B150F"), in: RoundedRectangle(cornerRadius: 6))
            // Android's panel is opaque: nothing of the table shows through the cards.
            .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(
                selected ? TraidoresTheme.gold : enabled ? Color(hex: "#6B4F2A") : Color(hex: "#8A7A62"),
                lineWidth: selected ? 2 : 1))
            .opacity(enabled || selected ? 1 : 0.5)
            .contentShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        // Same identifiers as the table cards: the window replaces them while it is open.
        .accessibilityLabel(isHuman ? "\(player.name), tu carta empatada"
                            : selected ? "Tu voto actual es por \(player.name)"
                            : enabled ? "\(player.name), tocar para votar" : "\(player.name), no disponible")
        .accessibilityValue(enabled ? "Objetivo disponible" : "")
        .accessibilityIdentifier("table.player.\(player.id)")
    }

    private func button(_ title: String, id: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .heavy)).tracking(0.8)
                .foregroundStyle(TraidoresTheme.text)
                .lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(Color(hex: "#E7221A12"), in: RoundedRectangle(cornerRadius: 7))
                .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color(hex: "#8AD4A24E"), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}
