import SwiftUI
import UIKit
import TraidoresCore

/// Mirrors Android's winnerRevealPanel, WinnerResultsRenderer and WinnerRevealAnimator.
/// Dimensions below are the Android dp values; the original ceremony art is shared.
struct MatchResultView: View {
    let game: ClassicGame
    let winner: RoleTeam
    let durationLabel: String
    let onReturn: () -> Void

    @Environment(\.reduceAnimations) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.dynamicTypeSize) private var textSize
    @ScaledMetric(relativeTo: .body) private var textScale = 1.0
    @State private var showingChronicle = false
    @State private var panelShown = false
    @State private var headingsShown = false
    @State private var visibleCards: Set<Int> = []
    @State private var shine = 0.0
    @State private var footerHeight: CGFloat = 73

    private var accent: Color { Color(hex: winner == .town ? "#8FCB91" : "#8F2633") }
    private var winners: [ClassicPlayer] { game.players.filter { team(of: $0) == winner } }
    private var humanWon: Bool { team(of: game.human) == winner }
    private var title: String { winner == .town ? "VICTORIA DEL PUEBLO" : "VICTORIA DE LOS TRAIDORES" }
    private var subtitle: String {
        winner == .town ? "La plaza vuelve a respirar." : "Las sombras reclaman el pueblo."
    }
    private var metrics: WinnerCardMetrics { .init(count: winners.count) }
    private var columns: Int {
        guard !textSize.isAccessibilitySize else { return 1 }
        let rows = winners.count <= 2 ? 1 : winners.count <= 4 ? 2 : winners.count <= 8 ? 3 : winners.count <= 12 ? 4 : 5
        return max(1, Int(ceil(Double(winners.count) / Double(rows))))
    }
    private var rows: [[ClassicPlayer]] {
        stride(from: 0, to: winners.count, by: columns).map { Array(winners[$0..<min($0 + columns, winners.count)]) }
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.opacity(184.0 / 255).ignoresSafeArea()
                panel
                    .frame(width: max(1, geometry.size.width - 28), height: min(700, max(1, geometry.size.height - 32)))
                    .scaleEffect(panelShown || reduceMotion ? 1 : 0.72)
                    .opacity(panelShown ? 1 : 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .foregroundStyle(Color(hex: "#F7E8D0"))
        .environment(\.colorScheme, .dark)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("table.matchResult")
        .task { await animateEntrance() }
        .task {
            // Android's local winner ceremony returns after 45 seconds. Reading
            // with VoiceOver and UI verification retain explicit navigation.
            guard !voiceOver, !ProcessInfo.processInfo.arguments.contains("-ui-testing") else { return }
            try? await Task.sleep(for: .seconds(45))
            guard !Task.isCancelled else { return }
            onReturn()
        }
    }

    private var panel: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                LinearGradient(colors: [Color(hex: "#F01A1207"), Color(hex: "#F0161008"), Color(hex: "#F0120D06")],
                               startPoint: .top, endPoint: .bottom)
                Image("winner_ceremony_background")
                    .resizable().frame(width: geometry.size.width, height: geometry.size.height)
                    .accessibilityHidden(true)
                if !reduceMotion { WinnerAmbientParticles().allowsHitTesting(false).accessibilityHidden(true) }
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(spacing: 0) {
                            if showingChronicle { chronicle }
                            else {
                                heading
                                winnerCards.padding(.top, 8)
                                Text(ceremonySummary)
                                    .font(sans(12.5))
                                    .foregroundStyle(Color(hex: "#B9AD92"))
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .padding(.horizontal, 14).padding(.top, 6)
                                    .accessibilityIdentifier("table.result.personal")
                            }
                        }
                        .padding(.horizontal, 22).padding(.top, 45).padding(.bottom, 18)
                        .frame(maxWidth: .infinity, alignment: .top)
                        .id("result.top")
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .padding(.bottom, max(68, footerHeight - 5))
                    .onChange(of: showingChronicle) { _, _ in proxy.scrollTo("result.top", anchor: .top) }
                }
                Color(hex: "#55FFF1BD").opacity(shine).allowsHitTesting(false).accessibilityHidden(true)
                actions
                    .padding(.horizontal, 18).padding(.bottom, 11)
                    .background(GeometryReader { proxy in
                        Color.clear.preference(key: WinnerFooterHeightKey.self, value: proxy.size.height)
                    })
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "#B8923F"), lineWidth: 2))
            .onPreferenceChange(WinnerFooterHeightKey.self) { footerHeight = $0 }
        }
    }

    private var heading: some View {
        VStack(spacing: 5) {
            Text(title)
                .font(TraidoresTheme.title(27)).bold()
                .foregroundStyle(Color(hex: "#FFF0BC"))
                .shadow(color: .black.opacity(0.9), radius: 3, y: 1)
                .multilineTextAlignment(.center)
                .lineLimit(textSize.isAccessibilitySize ? nil : 2)
                .minimumScaleFactor(textSize.isAccessibilitySize ? 1 : 16.0 / 27)
                .fixedSize(horizontal: false, vertical: true)
                .offset(y: headingsShown || reduceMotion ? 0 : 10)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("table.result.winner")
            Text(subtitle)
                .font(sans(15, weight: .regular, italic: true))
                .foregroundStyle(Color(hex: "#F7E8D0"))
                .shadow(color: .black.opacity(0.94), radius: 2, y: 1)
                .multilineTextAlignment(.center)
                .lineLimit(textSize.isAccessibilitySize ? nil : 1)
                .minimumScaleFactor(textSize.isAccessibilitySize ? 1 : 11.0 / 15)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 10).padding(.top, 7).padding(.bottom, 8)
        .frame(maxWidth: .infinity)
        .background(LinearGradient(colors: [Color(hex: "#00130B06"), Color(hex: "#A30B0704"), Color(hex: "#00130B06")],
                                   startPoint: .leading, endPoint: .trailing))
        .opacity(headingsShown ? 1 : 0)
        .accessibilityHidden(!headingsShown)
    }

    private var winnerCards: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Rectangle().fill(accent.opacity(190.0 / 255)).frame(height: 1)
                Text("EQUIPO GANADOR")
                    .font(sans(12))
                    .foregroundStyle(accent)
                    .lineLimit(textSize.isAccessibilitySize ? nil : 1)
                    .fixedSize(horizontal: !textSize.isAccessibilitySize, vertical: true)
                    .layoutPriority(1)
                    .accessibilityAddTraits(.isHeader)
                Rectangle().fill(accent.opacity(190.0 / 255)).frame(height: 1)
            }
            .padding(.horizontal, 6).frame(minHeight: 28).padding(.bottom, 3)
            ForEach(rows.indices, id: \.self) { index in
                HStack(alignment: .top, spacing: 0) {
                    ForEach(rows[index]) { player in winnerCard(player) }
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 5)
            }
        }
    }

    private func winnerCard(_ player: ClassicPlayer) -> some View {
        let visible = visibleCards.contains(player.id)
        return VStack(spacing: 0) {
            Image(player.role.classicImage(on: game.map))
                .resizable().scaledToFit()
                .saturation(player.alive ? 1 : 0)
                .frame(width: metrics.imageWidth - 6, height: metrics.imageHeight - 6)
                .padding(3)
                .background(Color(hex: "#E6231810"), in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(player.alive ? accent : Color(hex: "#75695B"), lineWidth: player.alive ? 3 : 2))
                .shadow(color: .black.opacity(player.alive ? 0.3 : 0), radius: 4, y: 4)
                .accessibilityHidden(true)
            Text(player.name)
                .font(sans(fittedLabelSize(player.name, base: metrics.nameSize)))
                .foregroundStyle(Color(hex: "#FFF0C7"))
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: metrics.nameHeight * textScale)
            Text(player.role.classicTitle(on: game.map).uppercased())
                .font(sans(fittedLabelSize(player.role.classicTitle(on: game.map).uppercased(), base: metrics.roleSize)))
                .foregroundStyle(Color(hex: "#F3D488"))
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: metrics.roleHeight * textScale)
        }
        .padding(.horizontal, 4).padding(.vertical, 2)
        .frame(width: textSize.isAccessibilitySize ? 260 : metrics.width)
        .opacity(visible ? 1 : 0)
        .offset(y: visible || reduceMotion ? 0 : 16)
        .scaleEffect(visible || reduceMotion ? 1 : 0.9)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(player.name), \(player.role.classicTitle(on: game.map)), \(player.alive ? "en pie" : "eliminado")")
        .accessibilityIdentifier("table.result.player.\(player.id)")
        .accessibilityHidden(!visible)
    }

    private var ceremonySummary: String {
        let count = winners.filter(\.alive).count
        let survivors = count == 1 ? "1 superviviente" : "\(count) supervivientes"
        let rounds = game.round == 1 ? "1 ronda" : "\(game.round) rondas"
        return "\(survivors) · \(rounds) · \(humanWon ? "VICTORIA" : "DERROTA")"
    }

    private var actions: some View {
        HStack(spacing: 8) {
            Button {
                withAnimation(.easeOut(duration: reduceMotion ? 0 : 0.22)) { showingChronicle.toggle() }
            } label: {
                actionLabel(showingChronicle ? "CERRAR CRÓNICA" : "VER CRÓNICA")
            }
            .buttonStyle(WinnerActionStyle(primary: false))
            .accessibilityIdentifier("table.result.story")
            .accessibilityValue(showingChronicle ? "Abierta" : "Cerrada")
            Button(action: onReturn) { actionLabel("VOLVER AL LOBBY") }
                .buttonStyle(WinnerActionStyle(primary: true))
                .accessibilityIdentifier("table.result.return")
        }
        .padding(6).frame(minHeight: 62)
        .background(LinearGradient(colors: [Color(hex: "#E51E160E"), Color(hex: "#DC100C08")],
                                   startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 17))
        .overlay(RoundedRectangle(cornerRadius: 17).stroke(Color(hex: "#765526")))
    }

    private func actionLabel(_ text: String) -> some View {
        Text(text).font(TraidoresTheme.title(12, relativeTo: .headline)).bold().tracking(0.3)
            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4).padding(.vertical, textSize.isAccessibilitySize ? 10 : 0)
            .frame(maxWidth: .infinity, minHeight: 46)
            .contentShape(RoundedRectangle(cornerRadius: 13))
    }

    private var chronicle: some View {
        VStack(spacing: 7) {
            Text("CRÓNICA DE LA PARTIDA")
                .font(TraidoresTheme.title(15, relativeTo: .headline)).bold()
                .foregroundStyle(Color(hex: "#F3D488"))
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Group {
                if textSize.isAccessibilitySize {
                    VStack(spacing: 4) { stats }
                } else {
                    HStack(spacing: 0) { stats }.frame(minHeight: 52)
                }
            }
            summaryText(eliminatedDescription, size: 13.5)
            summaryText(timeline, size: 12.5).accessibilityIdentifier("table.result.timeline")
        }
        .padding(8).padding(.horizontal, 4).padding(.top, 12)
        .background(Color(hex: "#D90F0B06"), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "#6B5528")))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("table.result.chronicle")
    }

    @ViewBuilder private var stats: some View {
        stat("\(game.round)", label: "RONDAS")
        stat(durationLabel, label: "TIEMPO", identifier: "table.result.duration")
        stat("\(game.players.count - game.living.count)", label: "ELIM.")
    }

    private func stat(_ value: String, label: String, identifier: String = "") -> some View {
        VStack(spacing: 0) {
            Text(value).foregroundStyle(Color(hex: "#F3D488"))
                .font(sans(13))
            Text(label).foregroundStyle(Color(hex: "#B9AD92"))
                .font(sans(max(WinnerCardMetrics.minimumTextSize, 13 * 0.78)))
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, minHeight: 52 * textScale)
        .background(Color(hex: "#241809"), in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "#6B5528")))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(value) \(label.lowercased())")
        .accessibilityIdentifier(identifier)
    }

    private func summaryText(_ text: String, size: CGFloat) -> some View {
        Text(text).font(sans(size))
            .foregroundStyle(Color(hex: "#B9AD92"))
            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            .padding(.leading, 12).padding(.trailing, 9).padding(.vertical, 7)
            .frame(maxWidth: .infinity)
            .background(Color(hex: "#E60F0B06"), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "#332814")))
            .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 2).fill(Color(hex: "#6B5528")).frame(width: 3) }
    }

    private var eliminatedDescription: String {
        let names = game.players.filter { !$0.alive }.map { "\($0.name) (\($0.role.classicTitle(on: game.map)))" }
        return "ELIMINADOS: \(names.isEmpty ? "NINGUNO" : names.joined(separator: ", "))"
    }

    /// Android's public chronology, using the iOS engine's public events only.
    private var timeline: String {
        var days: [String] = []
        var moments: [String] = []
        for round in 1...max(1, game.round) {
            let messages = game.messages.filter { $0.round == round && $0.speaker == nil }.map(\.text)
            let killed = game.players.filter { player in messages.contains { $0.hasPrefix("\(player.name) murió durante la noche.") } }.map(\.name)
            let silenced = game.players.filter { player in messages.contains { $0.hasPrefix("\(player.name) no puede hablar ni votar") } }.map(\.name)
            let expelled = game.players.filter { player in messages.contains { $0.hasPrefix("\(player.name) fue expulsado") } }.map(\.name)
            let tie = messages.contains { $0.hasPrefix("Empate.") }
            var parts = [killed.isEmpty ? "no murió nadie" : "murió \(killed.joined(separator: ", "))",
                         silenced.isEmpty ? "nadie fue silenciado" : "se silenció a \(silenced.joined(separator: ", "))"]
            if !expelled.isEmpty { parts.append("se expulsó a \(expelled.joined(separator: ", "))") }
            else if messages.contains(where: { $0.hasPrefix("Nadie será expulsado") }) { parts.append("nadie fue expulsado") }
            if tie { parts.append("hubo empate") }
            days.append("Día \(round): \(parts.joined(separator: "; ")).")
            moments += killed.map { "Día \(round): murió \($0)." }
            if messages.contains("Amanece sin víctimas.") { moments.append("Día \(round): no murió nadie.") }
            moments += silenced.map { "Día \(round): \($0) fue silenciado." }
            if tie { moments.append("Día \(round): hubo empate en la votación.") }
            moments += expelled.map { "Día \(round): \($0) fue expulsado." }
        }
        if moments.isEmpty { moments = ["Día 1: no hubo eventos públicos decisivos."] }
        return "MOMENTOS CLAVE\n\(moments.suffix(7).map { "- \($0)" }.joined(separator: "\n"))\n\nRONDA POR RONDA\n\(days.joined(separator: "\n"))"
    }

    private func animateEntrance() async {
        withAnimation(.easeOut(duration: reduceMotion ? 0.42 : 0.52)) { panelShown = true }
        try? await Task.sleep(for: .milliseconds(520))
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.36)) { headingsShown = true }
        try? await Task.sleep(for: .milliseconds(360))
        for player in winners {
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.3)) { _ = visibleCards.insert(player.id) }
            try? await Task.sleep(for: .milliseconds(95))
        }
        try? await Task.sleep(for: .milliseconds(205))
        guard !Task.isCancelled else { return }
        if !reduceMotion {
            withAnimation(.easeInOut(duration: 0.36)) { shine = 0.34 }
            try? await Task.sleep(for: .milliseconds(360))
            withAnimation(.easeInOut(duration: 0.36)) { shine = 0 }
        }
        AccessibilityNotification.Announcement("\(title). \(humanWon ? "Victoria" : "Derrota").").post()
    }

    private func team(of player: ClassicPlayer) -> RoleTeam {
        RoleCatalog.all.first { $0.id == player.role }?.team ?? .town
    }

    private func sans(_ size: CGFloat, weight: UIFont.Weight = .bold, italic: Bool = false) -> Font {
        let name = italic ? "Roboto-Italic" : weight == .regular ? "Roboto-Regular" : "Roboto-Bold"
        return .custom(name, size: size, relativeTo: .body)
    }

    private func fittedLabelSize(_ text: String, base: CGFloat) -> CGFloat {
        guard !textSize.isAccessibilitySize else { return base }
        let width = (text as NSString).size(withAttributes: [.font: UIFont(name: "Roboto-Bold", size: base) ?? UIFont.systemFont(ofSize: base, weight: .bold)]).width
        // Android auto-sizes each one-line label down to 7sp. iOS stops at the
        // legibility floor and lets a longer label wrap instead.
        return min(base, max(WinnerCardMetrics.minimumTextSize, base * (metrics.width - 8) / max(1, width)))
    }
}

private struct WinnerCardMetrics {
    /// Android shrinks names and roles to 10–7sp in crowded grids. Text scaled relative
    /// to body stops shrinking below about 12 pt when the reader picks a smaller text
    /// size, which breaks Dynamic Type; 12 pt is the smallest size that keeps scaling.
    static let minimumTextSize: CGFloat = 12

    let width, imageWidth, imageHeight, nameSize, roleSize, nameHeight, roleHeight: CGFloat
    init(count: Int) {
        let values: [CGFloat]
        switch count {
        case 0...1: values = [176, 112, 150, 18, 13, 24, 19]
        case 2: values = [152, 96, 128, 16, 12, 22, 18]
        case 3...4: values = [132, 84, 112, 14, 10, 20, 15]
        case 5...8: values = [108, 68, 91, 12, 9, 17, 13]
        case 9...12: values = [90, 56, 75, 10, 8, 14, 12]
        default: values = [78, 48, 64, 9, 7, 13, 11]
        }
        let floor = Self.minimumTextSize
        (width, imageWidth, imageHeight, nameSize, roleSize, nameHeight, roleHeight) =
            (values[0], values[1], values[2], max(floor, values[3]), max(floor, values[4]),
             max(17, values[5]), max(15, values[6]))
    }
}

private struct WinnerActionStyle: ButtonStyle {
    let primary: Bool
    func makeBody(configuration: Configuration) -> some View {
        let colors = primary
            ? (configuration.isPressed ? ["#E8B544", "#A8751C"] : ["#F2C458", "#B77F21"])
            : (configuration.isPressed ? ["#302317", "#4A351D"] : ["#342719", "#241A11"])
        configuration.label
            .foregroundStyle(Color(hex: primary ? "#211407" : "#F3D488"))
            .background(LinearGradient(colors: colors.map { Color(hex: $0) }, startPoint: .top, endPoint: .bottom),
                        in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13)
                .stroke(Color(hex: primary ? (configuration.isPressed ? "#FFE4A0" : "#FFE8AD")
                              : (configuration.isPressed ? "#E0B85B" : "#9D7635")), lineWidth: 2))
    }
}

private struct WinnerFooterHeightKey: PreferenceKey {
    static var defaultValue: CGFloat { 73 }
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Same twelve gold particles and deterministic seeds as AmbientParticlesView.Mode.VICTORY.
private struct WinnerAmbientParticles: View {
    @State private var startedAt = Date()
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { timeline in
            Canvas { context, size in
                let seconds = timeline.date.timeIntervalSince(startedAt)
                for index in 0..<12 {
                    let seed = index + 39
                    let x = 0.08 + Double((seed * 37) % 83) / 100
                    let y = Double((seed * 29) % 100) / 100
                    let speed = 0.018 + Double((seed * 11) % 17) / 1000
                    let travel = (y + seconds * speed).truncatingRemainder(dividingBy: 1.12)
                    let drift = sin(seconds * 0.72 + Double(seed) * 0.91) * size.width * 0.028
                    let radius = 0.7 + Double(seed % 4) * 0.35
                    let point = CGPoint(x: size.width * x + drift, y: size.height * (1.06 - travel))
                    context.fill(Path(ellipseIn: CGRect(x: point.x - radius, y: point.y - radius,
                                                       width: radius * 2, height: radius * 2)),
                                 with: .color(Color(hex: "#F4D680").opacity(Double(38 + (seed % 4) * 16) / 255)))
                }
            }
        }
    }
}
