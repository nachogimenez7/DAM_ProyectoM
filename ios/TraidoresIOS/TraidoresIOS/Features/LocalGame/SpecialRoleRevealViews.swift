import SwiftUI
import TraidoresCore

// The special role announcements of Android's GameplayMockActivity: the Payador opening a
// Contrapunto, the Oráculo bringing a voice back and the Bufón's own victory. Colours,
// texts and sizes come from activity_gameplay_mock.xml and its bg_* drawables.

/// How long Android keeps the Payador and Oráculo panels (SPECIAL_ROLE_REVEAL_DURATION_MS).
private let specialRevealSeconds = 7.0

/// Shared entrance, countdown bar and exit; each panel only draws its content.
private struct SpecialRevealStage<Panel: View>: View {
    let scrim: Color
    let announcement: String
    let autoDismiss: Bool
    let onFinished: () -> Void
    @ViewBuilder let panel: (_ progress: Double, _ finish: @escaping () -> Void) -> Panel

    @Environment(MenuPreferences.self) private var preferences
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @State private var opacity = 0.0
    @State private var scale = 0.86
    @State private var progress = 1.0
    @State private var finishing = false

    var body: some View {
        ZStack {
            scrim.ignoresSafeArea().opacity(opacity)
            ViewThatFits(in: .vertical) {
                content
                ScrollView { content.padding(.vertical, 24) }.scrollBounceBehavior(.basedOnSize)
            }
        }
        .task { await run() }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("table.dawnAnnouncement")
    }

    private var content: some View {
        panel(progress, finish)
            // Texts wrap instead of truncating inside the fixed-width panel.
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: 360)
            .scaleEffect(scale)
            .opacity(opacity)
            .padding(.horizontal, 16)
    }

    @MainActor
    private func run() async {
        AccessibilityNotification.Announcement(announcement).post()
        withAnimation(.easeOut(duration: 0.26)) { opacity = 1 }
        withAnimation(preferences.reduceAnimations ? .easeOut(duration: 0.26) : .easeOut(duration: 0.62)) { scale = 1 }
        guard autoDismiss else { return }
        let total = RevealTiming.seconds(specialRevealSeconds)
        withAnimation(.linear(duration: Double(total.components.seconds)
                              + Double(total.components.attoseconds) / 1e18)) { progress = 0 }
        guard !voiceOver else { return }
        try? await Task.sleep(for: total)
        guard !Task.isCancelled else { return }
        finish()
    }

    private func finish() {
        guard !finishing else { return }
        finishing = true
        Task { @MainActor in
            withAnimation(.easeIn(duration: 0.24)) { opacity = 0; scale = 0.97 }
            try? await Task.sleep(for: RevealTiming.seconds(0.24))
            onFinished()
        }
    }
}

/// Android's 5 dp bar that empties while the panel is up.
private struct RevealProgressBar: View {
    let progress: Double
    let colors: [Color]

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color(hex: "#503C4045"))
                Capsule().fill(LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing))
                    .frame(width: proxy.size.width * progress)
            }
        }
        .frame(height: 5)
        .padding(.horizontal, 18)
        .accessibilityHidden(true)
    }
}

/// The scene with name plates over its bottom corners (`bg_reveal_inner_panel`).
private struct RevealScene<Plates: View>: View {
    let image: String
    let label: String
    @ViewBuilder let plates: Plates

    var body: some View {
        Color.clear
            .frame(height: 220)
            .overlay { Image(image).resizable().scaledToFill().accessibilityLabel(label) }
            .clipped()
            .overlay(alignment: .bottom) { plates.padding(7) }
            .padding(2)
            .background(Color(hex: "#EA08090D"), in: RoundedRectangle(cornerRadius: 4))
    }
}

private struct RevealContinueButton: View {
    let action: () -> Void
    var body: some View {
        Button("CONTINUAR", action: action)
            .buttonStyle(TraidoresButtonStyle(prominent: false))
            .padding(.top, 10)
            .accessibilityIdentifier("table.specialReveal.continue")
    }
}

// MARK: - Payador

struct ContrapuntoRevealView: View {
    let first: String
    let second: String
    let onFinished: () -> Void

    var body: some View {
        SpecialRevealStage(scrim: Color(hex: "#E6000000"),
                           announcement: "Comienza el Contrapunto entre \(first) y \(second).",
                           autoDismiss: true, onFinished: onFinished) { progress, finish in
            VStack(spacing: 0) {
                Text("¡COMIENZA EL CONTRAPUNTO!")
                    .font(TraidoresTheme.title(21)).foregroundStyle(Color(hex: "#F1C45D"))
                    .lineLimit(2).minimumScaleFactor(0.75)
                Text("El Payador ha elegido a dos voces")
                    .font(.footnote.bold()).foregroundStyle(Color(hex: "#CDBB95"))
                    .padding(.top, 2)
                RevealScene(image: "payador_contrapunto_scene", label: "El Payador abre el Contrapunto") {
                    HStack {
                        plate(first)
                        Spacer(minLength: 8)
                        plate(second)
                    }
                }
                .padding(.top, 11)
                Text("Solo ellos podrán hablar durante el contrapunto.")
                    .font(.subheadline.bold()).foregroundStyle(Color(hex: "#F0E3CA"))
                    .padding(.top, 12)
                Text("Al terminar, uno de los dos quedará señalado.")
                    .font(.caption).foregroundStyle(Color(hex: "#AA997C"))
                    .padding(.top, 4)
                RevealProgressBar(progress: progress,
                                  colors: [Color(hex: "#B87819"), Color(hex: "#F1C458"), Color(hex: "#FFF0A2")])
                    .padding(.top, 13)
                RevealContinueButton(action: finish)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 10).padding(.top, 14).padding(.bottom, 12)
            .background(LinearGradient(colors: [Color(hex: "#3B2314"), Color(hex: "#100A07"), Color(hex: "#2B180E")],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "#C08A32"), lineWidth: 2))
        }
    }

    private func plate(_ name: String) -> some View {
        Text(name.uppercased())
            .font(.footnote.bold()).foregroundStyle(Color(hex: "#F4CF7E"))
            .lineLimit(2).minimumScaleFactor(0.75)
            // The plate sits on the scene at a fixed size, as in Android.
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            .padding(.horizontal, 5)
            .frame(width: 112, height: 48)
            .background(Color(hex: "#ED160E09"), in: RoundedRectangle(cornerRadius: 9))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(Color(hex: "#D49A37"), lineWidth: 1))
    }
}

// MARK: - Oráculo

struct OracleRevealView: View {
    let guest: String
    let onFinished: () -> Void

    var body: some View {
        SpecialRevealStage(scrim: Color(hex: "#E600050B"),
                           announcement: "Una voz regresa: \(guest) puede hablar durante este día.",
                           autoDismiss: true, onFinished: onFinished) { progress, finish in
            VStack(spacing: 0) {
                Text("¡UNA VOZ REGRESA!")
                    .font(TraidoresTheme.title(24)).foregroundStyle(Color(hex: "#F0CF77"))
                    .lineLimit(2).minimumScaleFactor(0.75)
                Text("El fuego recuerda su nombre")
                    .font(.footnote.bold()).foregroundStyle(Color(hex: "#BDD8E7"))
                    .padding(.top, 2)
                RevealScene(image: "oracle_return_scene", label: "El Oráculo recupera una voz") {
                    HStack {
                        Spacer(minLength: 0)
                        Text("\(guest.uppercased())\nVOZ RECUPERADA")
                            .font(.subheadline.bold()).foregroundStyle(Color(hex: "#D8F5FF"))
                            .lineLimit(2).minimumScaleFactor(0.7)
                            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                            .padding(.horizontal, 7)
                            .frame(width: 124, height: 50)
                            .background(LinearGradient(colors: [Color(hex: "#B0123145"), Color(hex: "#B04B1721")],
                                                       startPoint: .leading, endPoint: .trailing),
                                        in: RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "#D9C477"), lineWidth: 1))
                    }
                }
                .padding(.top, 11)
                Text("Vuelve para hablar una vez más.")
                    .font(.callout.bold()).foregroundStyle(Color(hex: "#F1E8D1"))
                    .padding(.top, 12)
                Text("Puede hablar durante este día, pero no votar ni usar habilidades.")
                    .font(.caption).foregroundStyle(Color(hex: "#9FB1BD"))
                    .padding(.top, 4)
                RevealProgressBar(progress: progress,
                                  colors: [Color(hex: "#E8B958"), Color(hex: "#72D6FF"), Color(hex: "#D1F7FF")])
                    .padding(.top, 13)
                RevealContinueButton(action: finish)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 7).padding(.top, 14).padding(.bottom, 12)
            .padding(5)
            .background(Color(hex: "#10141B"), in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: "#B99542"), lineWidth: 3))
        }
    }
}

// MARK: - Bufón

struct JesterVictoryView: View {
    let name: String
    let roleTitle: String
    /// The player was the Bufón: they won and can keep watching or leave the match.
    let humanWon: Bool
    let onContinue: () -> Void
    let onLeave: () -> Void

    @Environment(MenuPreferences.self) private var preferences
    @State private var actionsVisible = false

    var body: some View {
        SpecialRevealStage(scrim: Color(hex: "#D9000000"),
                           announcement: "Victoria especial: \(name) era el \(roleTitle) y consiguió que lo expulsaran.",
                           autoDismiss: false, onFinished: onContinue) { _, finish in
            VStack(spacing: 0) {
                Text("VICTORIA ESPECIAL")
                    .font(.footnote.bold()).foregroundStyle(Color(hex: "#F7DEA0"))
                    .padding(.horizontal, 20).frame(height: 28)
                    .background(LinearGradient(colors: [Color(hex: "#32150F"), Color(hex: "#6A211C")],
                                               startPoint: .leading, endPoint: .trailing), in: Capsule())
                    .overlay(Capsule().stroke(Color(hex: "#E0B14A"), lineWidth: 1))
                Text("¡EL \(roleTitle.uppercased()) LOS ENGAÑÓ!")
                    .font(TraidoresTheme.title(27)).foregroundStyle(Color(hex: "#6A241B"))
                    .shadow(color: .white.opacity(0.33), radius: 1.5, y: 1)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .padding(.top, 2)
                LinearGradient(colors: [Color(hex: "#00D5A632"), Color(hex: "#8A2E25"), Color(hex: "#00D5A632")],
                               startPoint: .leading, endPoint: .trailing)
                    .frame(height: 2).padding(.horizontal, 48).padding(.top, 4)
                Color.clear.frame(height: 210)
                    .overlay { Image("jester_victory_scene").resizable().scaledToFill()
                        .accessibilityLabel("\(roleTitle) revelado") }
                    .clipped()
                    .padding(4)
                    .background(Color(hex: "#7A2B22"), in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "#E7B83F"), lineWidth: 2))
                    .padding(.top, 7)
                Text("\(name.uppercased()) ERA EL \(roleTitle.uppercased())")
                    .font(.title3.bold()).foregroundStyle(Color(hex: "#3D2415"))
                    .lineLimit(2).minimumScaleFactor(0.75)
                    .padding(.top, 9)
                Text("CONDICIÓN CUMPLIDA")
                    .font(.caption2.bold()).foregroundStyle(Color(hex: "#8A2E25"))
                    .padding(.top, 2)
                Text("Consiguió que el pueblo lo expulsara durante la votación.")
                    .font(.subheadline.bold()).foregroundStyle(Color(hex: "#4C321F"))
                    .padding(.top, 2)
                Text("La partida continúa, pero el \(roleTitle) ya consiguió su propia victoria.")
                    .font(.footnote.italic()).foregroundStyle(Color(hex: "#68472B"))
                    .padding(.top, 5)
                HStack(spacing: 10) {
                    Button(humanWon ? "SEGUIR MIRANDO" : "CONTINUAR PARTIDA", action: finish)
                        .buttonStyle(TraidoresButtonStyle(prominent: true))
                        .accessibilityIdentifier("table.specialReveal.continue")
                    if humanWon {
                        Button("VOLVER A LA SALA", action: onLeave)
                            .buttonStyle(TraidoresButtonStyle(prominent: false))
                            .accessibilityIdentifier("table.specialReveal.leave")
                    }
                }
                .padding(.top, 8)
                .opacity(actionsVisible ? 1 : 0)
                .disabled(!actionsVisible)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .background(LinearGradient(colors: [Color(hex: "#F2D99B"), Color(hex: "#D7AD5A")],
                                       startPoint: .top, endPoint: .bottom),
                        in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).stroke(Color(hex: "#D5A632"), lineWidth: 2))
            .padding(8)
            .background(LinearGradient(colors: [Color(hex: "#F5D777"), Color(hex: "#B77A24")],
                                       startPoint: .top, endPoint: .bottom),
                        in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "#6A241B"), lineWidth: 4))
            .overlay(alignment: .topLeading) { horn(mirrored: false) }
            .overlay(alignment: .topTrailing) { horn(mirrored: true) }
            .padding(.top, 24)
        }
        .overlay {
            if !preferences.reduceAnimations { JesterConfetti() }
        }
        .task {
            // Android shows the buttons once the panel has landed.
            try? await Task.sleep(for: RevealTiming.seconds(1.4))
            withAnimation(.easeOut(duration: 0.3)) { actionsVisible = true }
        }
    }

    private func horn(mirrored: Bool) -> some View {
        Image("jester_horn_illustrated").resizable().scaledToFit()
            .frame(width: 104, height: 62)
            .scaleEffect(x: mirrored ? -1 : 1)
            .rotationEffect(.degrees(mirrored ? 10 : -10))
            .offset(x: mirrored ? 10 : -10, y: -26)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Android's Bufón confetti (`JesterVictoryAnimator.launchConfetti`): 28 pieces thrown in
/// from both sides, spinning 540° and fading out, with random delays up to 1.8 s.
private struct JesterConfetti: View {
    private struct Piece {
        let fromLeft: Bool
        let size: CGFloat
        let oval: Bool
        let color: Color
        let startY: CGFloat, targetX: CGFloat, targetY: CGFloat
        let rotation: Double
        let delay: Double, duration: Double
    }

    @State private var start = Date()
    private let pieces: [Piece] = {
        var generator = SystemRandomNumberGenerator()
        let colors = ["#E7B83F", "#9C3029", "#26706A", "#F3D98C"].map { Color(hex: $0) }
        return (0..<28).map { index in
            let fromLeft = index % 2 == 0
            let startY = CGFloat.random(in: 0.2...0.8, using: &generator)
            return Piece(fromLeft: fromLeft, size: CGFloat.random(in: 6...12, using: &generator), oval: index % 3 == 0,
                         color: colors[index % colors.count], startY: startY,
                         targetX: fromLeft ? .random(in: 0.33...1, using: &generator) : .random(in: 0...0.67, using: &generator),
                         targetY: min(max(startY + .random(in: -0.25...0.33, using: &generator), 0), 1),
                         rotation: .random(in: 0...180, using: &generator),
                         delay: .random(in: 0...1.8, using: &generator), duration: .random(in: 1.8...3.2, using: &generator))
        }
    }()

    var body: some View {
        TimelineView(.animation) { timeline in
            let elapsed = timeline.date.timeIntervalSince(start)
            Canvas { context, size in
                for piece in pieces {
                    let raw = (elapsed - piece.delay) / piece.duration
                    guard raw > 0, raw < 1 else { continue }
                    let eased = 1 - pow(1 - raw, 2) // Android's DecelerateInterpolator
                    let fromX = piece.fromLeft ? -piece.size : size.width
                    let x = fromX + (piece.targetX * size.width - fromX) * eased
                    let y = (piece.startY + (piece.targetY - piece.startY) * eased) * size.height
                    let alpha = raw < 0.33 ? raw * 3 : raw > 0.66 ? (1 - raw) * 3 : 1
                    var layer = context
                    layer.opacity = alpha
                    layer.translateBy(x: x, y: y)
                    layer.rotate(by: .degrees(piece.rotation + 540 * eased))
                    let rect = CGRect(x: -piece.size / 2, y: -piece.size, width: piece.size, height: piece.size * 2)
                    let path = piece.oval ? Path(ellipseIn: rect) : Path(roundedRect: rect, cornerRadius: 2)
                    layer.fill(path, with: .color(piece.color))
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
