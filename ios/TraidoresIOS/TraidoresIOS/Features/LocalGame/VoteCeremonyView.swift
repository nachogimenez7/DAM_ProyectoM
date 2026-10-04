import SwiftUI
import TraidoresCore

/// The vote recount and expulsion, ported from Android's VoteResultAnimator: sealed
/// votes land one by one on the accused cards, then the majority, the sentence with
/// the wax seal, the boot that kicks the card off the table and the outcome. It spans
/// the `voteCount` and `result` phases; every button advances the match.
struct VoteCeremonyView: View {
    let game: ClassicGame
    /// Card and title of the expelled player when the match reveals roles.
    let revealedRole: (image: String, title: String)?
    let playerColor: (Int) -> Color
    let onAdvance: () -> Void
    let onImpact: () -> Void

    // Like the day/night transition: only the game's "Reducir animaciones" changes these
    // reveals, as in Android. They keep their pacing either way; only motion is dropped.
    @Environment(MenuPreferences.self) private var preferences
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    private var reduceMotion: Bool { preferences.reduceAnimations }

    // Panel text and button.
    @State private var title = ""
    @State private var subtitle = ""
    @State private var notice = ""
    @State private var button: String?
    @State private var panelShown = false
    @State private var shake = 0
    @State private var flash = 0.0

    // Recount.
    @State private var landed: [Int: [Int]] = [:]
    @State private var pulsing: Int?

    // Expulsion.
    @State private var expelling = false
    @State private var cardShown = false
    @State private var roleShown = false
    @State private var sealShown = false
    @State private var cardTilt = false
    @State private var squash = CGSize(width: 1, height: 1)
    @State private var flight = 0.0
    @State private var flown = false
    @State private var boot = BootPose.hidden
    @State private var dust = false
    @State private var dustOpacity = 0.0

    private enum BootPose { case hidden, cocked, contact, recoil }

    var body: some View {
        ZStack {
            Color.black.opacity(0.88).ignoresSafeArea()
            ViewThatFits(in: .vertical) {
                panel
                ScrollView { panel.padding(.vertical, 24) }.scrollBounceBehavior(.basedOnSize)
            }
            Color(hex: "#FFE9C8").opacity(flash).ignoresSafeArea().allowsHitTesting(false)
        }
        .task(id: game.phaseIndex) { await run() }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("table.voteCeremony")
    }

    // MARK: Layout

    private var panel: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.system(.title3, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .contentTransition(.opacity)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("table.voteCeremony.title")
            Text(subtitle)
                .font(.system(.footnote, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center).padding(.top, 2)
            // Once the kicked card has flown off, its space closes like Android's emptied grid.
            if !(expelling && flown) {
                Group {
                    if expelling { expulsionCard } else { recountGrid }
                }
                .padding(.top, 8)
            }
            if !notice.isEmpty {
                Text(notice)
                    .font(.system(.caption, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                    .multilineTextAlignment(.center).padding(.top, 6)
            }
            Button { advance() } label: {
                Text(button ?? "CONTINUAR")
                    .font(.system(.caption, weight: .bold))
                    .foregroundStyle(TraidoresTheme.text)
                    .frame(width: 230).frame(minHeight: 44)
                    .background(Color(hex: "#E7221A12"), in: RoundedRectangle(cornerRadius: 7))
                    .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.gold.opacity(0.54)))
                    .contentShape(Rectangle())
            }
                .buttonStyle(.plain)
                .padding(.top, 8)
                .opacity(button == nil ? 0 : 1)
                .disabled(button == nil)
                .accessibilityHidden(button == nil || !panelShown)
                .accessibilityIdentifier("table.voteContinue")
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .modifier(RevealPanel(map: game.map))
        .keyframeAnimator(initialValue: 0.0, trigger: shake) { content, offset in
            content.offset(x: offset)
        } keyframes: { _ in
            KeyframeTrack {
                CubicKeyframe(9.0, duration: 0.052)
                CubicKeyframe(-7.0, duration: 0.052)
                CubicKeyframe(5.0, duration: 0.052)
                CubicKeyframe(-3.0, duration: 0.052)
                CubicKeyframe(0.0, duration: 0.052)
            }
        }
        .scaleEffect(panelShown ? 1 : 0.96)
        .opacity(panelShown ? 1 : 0)
        .padding(.horizontal, 18)
    }

    private var candidates: [ClassicPlayer] {
        let voted = Set(game.votes.values)
        return game.players.filter { voted.contains($0.id) }
    }

    @ViewBuilder
    private var recountGrid: some View {
        let list = candidates
        if list.isEmpty {
            Text("NO SE EMITIERON VOTOS")
                .font(.system(.title3, weight: .bold)).foregroundStyle(TraidoresTheme.secondary)
                .multilineTextAlignment(.center).padding(6)
        } else {
            let columns = list.count <= 1 ? 1 : (list.count <= 4 ? 2 : 3)
            let size: CGSize = list.count <= 2 ? .init(width: 88, height: 132)
                : (list.count <= 4 ? .init(width: 82, height: 122) : .init(width: 60, height: 104))
            let dense = list.count > 4
            let margin: CGFloat = dense ? 3 : 5
            let rows = stride(from: 0, to: list.count, by: columns).map { Array(list[$0..<min($0 + columns, list.count)]) }
            VStack(spacing: margin * 2) {
                ForEach(rows.indices, id: \.self) { row in
                    // Like Android, an odd last card is centred across the row.
                    HStack(spacing: margin * 2) {
                        ForEach(rows[row]) { player in
                            voteCard(player, size: size, dense: dense)
                        }
                    }
                }
            }
        }
    }

    private func voteCard(_ player: ClassicPlayer, size: CGSize, dense: Bool) -> some View {
        let voters = landed[player.id] ?? []
        let count = voters.count
        return VStack(spacing: 0) {
            Image("card_back_traidores")
                .resizable().scaledToFit()
                .frame(width: dense ? 32 : 38, height: dense ? 44 : 52)
                .frame(width: dense ? 42 : 52, height: dense ? 43 : 54)
            HStack(spacing: 3) {
                InitialAvatar(name: player.name, isHuman: player.id == game.human.id, size: dense ? 15 : 20, fill: TraidoresTheme.gold)
                Text(player.name)
                    .font(.system(size: dense ? 10 : 12, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(height: dense ? 15 : 20)
            Text("\(count) \(count == 1 ? "VOTO" : "VOTOS")")
                .font(.system(size: dense ? 10 : 12, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .monospacedDigit()
                .contentTransition(.numericText(value: Double(count)))
                .frame(height: dense ? 15 : 18)
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(game.advanced.showIndividualVotes ? 10 : 12), spacing: 2),
                                     count: dense ? 4 : 5), spacing: 2) {
                ForEach(voters, id: \.self) { voter in
                    VoteToken(initial: game.advanced.showIndividualVotes ? String(game.name(voter).prefix(1)) : nil, isHuman: voter == game.human.id)
                        .transition(.scale(scale: 0.4).combined(with: .opacity))
                }
            }
            .frame(height: 24, alignment: .top)
            .padding(.top, 2)
        }
        .padding(.horizontal, dense ? 4 : 5).padding(.top, dense ? 4 : 5).padding(.bottom, dense ? 3 : 4)
        .frame(width: size.width, height: size.height)
        .background(VoteCardBackground())
        .scaleEffect(pulsing == player.id ? 1.04 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(player.name): \(count) \(count == 1 ? "voto" : "votos")"
            + (game.advanced.showIndividualVotes && !voters.isEmpty
               ? ", de \(voters.map { game.name($0) }.formatted(.list(type: .and)))" : ""))
    }

    private var expulsionCard: some View {
        let target = game.eliminationTarget.map { game.players[$0] }
        return VStack(spacing: 0) {
            ZStack {
                if roleShown, let revealedRole {
                    RevealCard(image: revealedRole.image)
                        .frame(width: 112, height: 150)
                        .transition(.scale(scale: 0.82).combined(with: .opacity))
                } else {
                    InitialAvatar(name: target?.name ?? "?", isHuman: target?.id == game.human.id, size: 76, fill: TraidoresTheme.gold)
                        .transition(.opacity)
                }
                Image("expulsion_seal")
                    .resizable().scaledToFit().frame(width: 42, height: 42)
                    .rotationEffect(.degrees(sealShown ? -6 : -18))
                    .scaleEffect(sealShown ? 1 : 2.1)
                    .opacity(sealShown ? 1 : 0)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(2)
            }
            .frame(width: 120, height: 154)
            Text(target?.name ?? "")
                .font(.system(.subheadline, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                .lineLimit(1).frame(height: 27)
            Text(roleShown ? (revealedRole?.title.uppercased() ?? "SERÁ EXPULSADO") : "SERÁ EXPULSADO")
                .font(.system(.caption2, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .frame(height: 21)
        }
        .padding(.horizontal, 8).padding(.top, 8).padding(.bottom, 6)
        .frame(width: 176, height: 218)
        .background(VoteCardBackground())
        .scaleEffect(x: squash.width, y: squash.height)
        .offset(x: cardTilt ? 10 : 0)
        .rotationEffect(.degrees(cardTilt ? -5 : 0))
        .modifier(ArcFlight(progress: flight))
        .opacity(cardShown && !flown ? 1 : 0)
        .scaleEffect(cardShown ? 1 : 0.86)
        // Dust bursts from the card's left side, where it flies off.
        .overlay(alignment: .topLeading) {
            Image("impact_dust")
                .resizable().scaledToFit().frame(width: 190, height: 190)
                .scaleEffect(dust ? 1.85 : 0.5)
                .opacity(dustOpacity)
                .position(x: 176 * 0.18, y: 218 * 0.52)
                .allowsHitTesting(false)
        }
        // The boot waits off to the right and strikes the card's right edge.
        .overlay(alignment: .leading) {
            Image(bootImage)
                .resizable().scaledToFit().frame(width: 150, height: 112)
                .rotationEffect(.degrees(bootRotation))
                .scaleEffect(boot == .contact ? 1.1 : (boot == .cocked ? 1.12 : 1))
                .offset(x: bootOffset)
                .opacity(boot == .hidden ? 0 : 1)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(target?.name ?? ""), \(roleShown ? revealedRole?.title ?? "" : "será expulsado")")
    }

    private var bootImage: String {
        switch boot {
        case .hidden, .cocked: "boot_windup"
        case .contact: "ic_kicking_boot"
        case .recoil: "boot_recoil"
        }
    }

    private var bootOffset: CGFloat {
        switch boot {
        case .hidden, .recoil: 460
        case .cocked: 176 + 88
        case .contact: 176 - 62
        }
    }

    private var bootRotation: Double {
        switch boot {
        case .hidden: -26
        case .cocked: -20
        case .contact: 6
        case .recoil: -30
        }
    }

    // MARK: Sequences

    @MainActor
    private func run() async {
        button = nil
        if game.phase == .voteCount {
            await runRecount()
        } else if game.phase == .result, game.eliminationTarget != nil {
            await runExpulsion()
        } else if game.phase == .result {
            await showNoExpulsion()
        }
    }

    @MainActor
    private func runRecount() async {
        expelling = false
        landed = [:]
        let tiedFirstRound = game.tieCandidates.count > 1 && game.voteRound == 1
        title = tiedFirstRound ? "EMPATE" : (game.voteRound == 2 ? "RECUENTO FINAL" : "RECUENTO DE VOTOS")
        subtitle = game.advanced.showIndividualVotes || tiedFirstRound
            ? "Cada sello muestra quién emitió el voto."
            : "La identidad de los votantes permanece oculta."
        notice = ""
        withAnimation(.easeOut(duration: 0.22)) { panelShown = true }

        // Same shuffle seed idea as Android: stable for this vote, different each round.
        var generator = SeededGenerator(seed: UInt64(game.round * 1_009 + game.voteRound * 97 + game.votes.count))
        let ballots = game.votes.sorted { $0.key < $1.key }.shuffled(using: &generator)
        if voiceOver {
            landed = Dictionary(grouping: ballots, by: \.value).mapValues { $0.map(\.key) }
        } else {
            // One seal at a time, a little slower than Android's 420 ms so each vote reads.
            try? await Task.sleep(for: RevealTiming.seconds(0.7))
            for (voter, target) in ballots {
                guard !Task.isCancelled else { return }
                withAnimation(.easeOut(duration: 0.22)) { landed[target, default: []].append(voter) }
                if !reduceMotion {
                    withAnimation(.easeOut(duration: 0.1)) { pulsing = target }
                    try? await Task.sleep(for: RevealTiming.seconds(0.1))
                    withAnimation(.easeOut(duration: 0.12)) { pulsing = nil }
                    try? await Task.sleep(for: RevealTiming.seconds(0.55))
                } else {
                    try? await Task.sleep(for: RevealTiming.seconds(0.65))
                }
            }
            try? await Task.sleep(for: RevealTiming.seconds(0.9))
        }
        guard !Task.isCancelled else { return }

        let tied = game.tieCandidates.count > 1
        if tied && game.voteRound == 1 {
            title = "EMPATE"
            notice = "SI EL EMPATE SE REPITE, NADIE SERÁ EXPULSADO."
            await ready("IR AL DESEMPATE")
        } else if tied {
            title = "EL EMPATE SE REPITIÓ"
            notice = "El pueblo no alcanzó una mayoría. Se resolverá el empate."
            await ready("RESOLVER EMPATE")
        } else if let target = game.eliminationTarget {
            title = "MAYORÍA ALCANZADA"
            notice = "\(game.name(target)) recibió la mayor cantidad de votos."
            await ready("VER EXPULSIÓN")
        } else {
            title = "SIN MAYORÍA"
            notice = "El pueblo no alcanzó una decisión."
            await ready("CONTINUAR")
        }
    }

    @MainActor
    private func showNoExpulsion() async {
        expelling = false
        withAnimation(.easeOut(duration: 0.22)) { panelShown = true }
        title = "EL PUEBLO NO LLEGÓ A UN ACUERDO"
        subtitle = "Nadie será expulsado esta jornada."
        notice = "La noche volverá a caer sobre el pueblo."
        await ready("CONTINUAR")
    }

    @MainActor
    private func runExpulsion() async {
        guard let target = game.eliminationTarget else { return }
        let name = game.name(target)
        panelShown = true
        expelling = true
        title = "EXPULSIÓN"
        subtitle = "El pueblo ha tomado su decisión."
        notice = revealedRole == nil
            ? "\(name) será expulsado del pueblo."
            : "\(name) será expulsado. Su carta se revelará primero."
        withAnimation(.spring(response: 0.32, dampingFraction: 0.55)) { cardShown = true }
        try? await Task.sleep(for: RevealTiming.seconds(0.32))

        if let revealedRole {
            title = "CARTA REVELADA"
            subtitle = "\(name) era \(revealedRole.title)."
            notice = "La identidad queda expuesta ante todo el pueblo."
            withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) { roleShown = true }
            try? await Task.sleep(for: RevealTiming.seconds(0.46 + 1.7 + 0.12))
        } else {
            try? await Task.sleep(for: RevealTiming.seconds(1.5 + 0.12))
        }
        guard !Task.isCancelled else { return }

        title = "SENTENCIA DEL PUEBLO"
        subtitle = revealedRole == nil ? "La carta de \(name) permanece oculta." : "\(name) fue revelado y expulsado."
        notice = "La sentencia está por cumplirse."
        withAnimation(.spring(response: 0.24, dampingFraction: 0.55)) { sealShown = true }
        try? await Task.sleep(for: RevealTiming.seconds(0.7))
        guard !Task.isCancelled else { return }

        if reduceMotion {
            impact()
            withAnimation(.easeOut(duration: 0.4)) { flown = true }
            try? await Task.sleep(for: RevealTiming.seconds(0.4))
        } else {
            // Beat 1: the boot comes in, cocked. Beat 2: the card braces. Beat 3: strike.
            withAnimation(.easeOut(duration: 0.46)) { boot = .cocked }
            try? await Task.sleep(for: RevealTiming.seconds(0.46))
            withAnimation(.easeOut(duration: 0.32)) { cardTilt = true }
            try? await Task.sleep(for: RevealTiming.seconds(0.32))
            withAnimation(.easeIn(duration: 0.24)) { boot = .contact }
            try? await Task.sleep(for: RevealTiming.seconds(0.24))
            guard !Task.isCancelled else { return }
            impact()
            withAnimation(.easeIn(duration: 0.3).delay(0.08)) { boot = .recoil }
            withAnimation(.easeOut(duration: 0.09)) { squash = CGSize(width: 1.32, height: 0.68) }
            try? await Task.sleep(for: RevealTiming.seconds(0.09))
            withAnimation(.spring(response: 0.12, dampingFraction: 0.45)) { squash = CGSize(width: 0.82, height: 1.2) }
            try? await Task.sleep(for: RevealTiming.seconds(0.09))
            withAnimation(.timingCurve(0.45, 0, 0.9, 0.6, duration: 1.05)) { flight = 1 }
            try? await Task.sleep(for: RevealTiming.seconds(1.05))
            withAnimation(.easeInOut(duration: 0.3)) { flown = true }
        }
        guard !Task.isCancelled else { return }
        title = "\(name.uppercased())\nFUE EXPULSADO"
        subtitle = revealedRole == nil ? "Su carta permanece oculta." : "Su carta ya fue revelada."
        notice = "El pueblo continúa con la partida."
        AccessibilityNotification.Announcement("\(name) fue expulsado.").post()
        try? await Task.sleep(for: RevealTiming.seconds(1.7))
        guard !Task.isCancelled else { return }
        await ready("CONTINUAR")
    }

    private func impact() {
        onImpact()
        withAnimation(.easeOut(duration: 0.06)) { flash = 0.22 }
        withAnimation(.easeIn(duration: 0.15).delay(0.06)) { flash = 0 }
        shake += 1
        dustOpacity = 0.95
        dust = false
        withAnimation(.easeOut(duration: 0.36)) { dust = true; dustOpacity = 0 }
    }

    /// Shows the button; like Android, the step continues on its own after 8 s.
    @MainActor
    private func ready(_ label: String) async {
        withAnimation(.easeOut(duration: 0.18)) { button = label }
        // UI tests tap every step themselves.
        guard !voiceOver, !RevealTiming.testing else { return }
        try? await Task.sleep(for: .seconds(game.testOptions.quickMatch ? 1.2 : 8))
        guard !Task.isCancelled, button == label else { return }
        advance()
    }

    private func advance() {
        guard button != nil else { return }
        button = nil
        onAdvance()
    }
}

// MARK: - Pieces

/// Android's `bg_vote_result_card`: warm vertical gradient, gold hairline and an inner bevel.
private struct VoteCardBackground: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(LinearGradient(colors: [Color(hex: "#EE15100C"), Color(hex: "#F11D1711"), Color(hex: "#F42B2117")],
                                 startPoint: .top, endPoint: .bottom))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "#D0D4A24E"), lineWidth: 1))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(hex: "#24F1D48B"), lineWidth: 1).padding(3))
    }
}

/// Gold disc with the player's initial (Android's GameplayAvatarView fallback).
private struct InitialAvatar: View {
    let name: String
    var isHuman = false
    let size: CGFloat
    let fill: Color

    var body: some View {
        GamePlayerAvatar(name: name, isHuman: isHuman, size: size, fill: fill)
    }
}

/// A voter's seal: their initial, or an anonymous mark when votes are secret.
private struct VoteToken: View {
    let initial: String?
    var isHuman = false

    var body: some View {
        if let initial {
            GamePlayerAvatar(name: initial, isHuman: isHuman, size: 10)
                .overlay(Circle().stroke(Color(hex: "#FFF0C4"), lineWidth: 1))
        } else {
            Circle()
                .fill(Color(hex: "#5E4722"))
                .overlay(Circle().stroke(TraidoresTheme.gold, lineWidth: 1.5))
                .overlay(Circle().fill(TraidoresTheme.gold).padding(3.5))
                .frame(width: 12, height: 12)
        }
    }
}

/// The kicked card's flight: a wide arc up and out to the left, spinning clockwise,
/// shrinking and fading (Android's quadTo path with an accelerate interpolator).
private struct ArcFlight: AnimatableModifier {
    var progress: Double

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let t = progress
        let width: Double = 430
        let end = CGPoint(x: -width - 80, y: -30)
        let control = CGPoint(x: -width * 0.28, y: -120)
        let x = 2 * (1 - t) * t * control.x + t * t * end.x
        let y = 2 * (1 - t) * t * control.y + t * t * end.y
        return content
            .rotationEffect(.degrees(210 * t))
            .scaleEffect(1 - 0.45 * t)
            .offset(x: x, y: y)
            // Android fades the card with the same accelerating curve: solid at first.
            .opacity(1 - t * t)
    }
}

/// Deterministic shuffle so a recount replays identically (SplitMix64).
private struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E37_79B9_7F4A_7C15 }
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
