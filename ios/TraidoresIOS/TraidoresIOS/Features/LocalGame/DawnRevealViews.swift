import SwiftUI
import TraidoresCore

// Dawn announcements, ported from Android's DeathRevealAnimator, SilenceRevealAnimator
// and NoDeathRevealAnimator (timings and sizes in dp → pt), plus the stretchable event
// frame (`ui_frame_event_*.9.png`) every gameplay reveal panel uses.

// MARK: - Event frame

/// Android's event-frame 9-patch: two stretch bands per axis keep the centre
/// ornament intact. Values in source pixels (xxhdpi, 3 px per point) as printed by
/// `Scripts/export_nine_patch_frames.swift`.
struct RevealFrameArt {
    let asset: String
    let size: CGSize
    let stretchX: [ClosedRange<CGFloat>]
    let stretchY: [ClosedRange<CGFloat>]
    let padding: EdgeInsets
    let inner: Color
    let extraTop: CGFloat
    let extraBottom: CGFloat

    static func forMap(_ map: GameMap) -> RevealFrameArt {
        switch map {
        case .pampa:
            .init(asset: "ui_frame_event_pampa", size: .init(width: 935, height: 612),
                  stretchX: [243...317, 617...691], stretchY: [244...281, 367...403],
                  padding: .init(top: 86, leading: 93, bottom: 612 - 514, trailing: 935 - 837),
                  inner: Color(hex: "#EC120C07"), extraTop: 4, extraBottom: 4)
        case .greece:
            .init(asset: "ui_frame_event_grecia", size: .init(width: 917, height: 634),
                  stretchX: [238...311, 605...678], stretchY: [266...317, 443...494],
                  padding: .init(top: 117, leading: 108, bottom: 634 - 560, trailing: 917 - 810),
                  inner: Color(hex: "#EB080A10"), extraTop: 18, extraBottom: 12)
        case .medieval:
            .init(asset: "ui_frame_event_medieval", size: .init(width: 923, height: 612),
                  stretchX: [276...369, 553...646], stretchY: [195...244, 354...403],
                  padding: .init(top: 72, leading: 102, bottom: 612 - 534, trailing: 923 - 822),
                  inner: Color(hex: "#F0060708"), extraTop: 4, extraBottom: 4)
        }
    }

    static let pixelsPerPoint: CGFloat = 3

    /// Frame padding in points: where content may start.
    var pointPadding: EdgeInsets {
        .init(top: padding.top / Self.pixelsPerPoint, leading: padding.leading / Self.pixelsPerPoint,
              bottom: padding.bottom / Self.pixelsPerPoint, trailing: padding.trailing / Self.pixelsPerPoint)
    }

    /// Source and destination spans along one axis for a target length in points.
    static func slices(total: CGFloat, stretch: [ClosedRange<CGFloat>], target: CGFloat)
        -> [(source: ClosedRange<CGFloat>, length: CGFloat)] {
        var cuts: [(ClosedRange<CGFloat>, Bool)] = []
        var cursor: CGFloat = 0
        for band in stretch {
            if band.lowerBound > cursor { cuts.append((cursor...band.lowerBound, false)) }
            cuts.append((band, true))
            cursor = band.upperBound
        }
        if cursor < total { cuts.append((cursor...total, false)) }
        let stretchPixels = stretch.reduce(0) { $0 + $1.upperBound - $1.lowerBound }
        let fixedPoints = (total - stretchPixels) / pixelsPerPoint
        let extra = target - fixedPoints
        // Narrower than the fixed art: shrink everything evenly instead of overlapping.
        let fixedScale = extra < 0 ? target / max(fixedPoints, 1) : 1
        return cuts.map { range, stretches in
            let pixels = range.upperBound - range.lowerBound
            let length = stretches
                ? max(extra, 0) * pixels / max(stretchPixels, 1)
                : pixels / pixelsPerPoint * fixedScale
            return (range, length)
        }
    }
}

/// Draws a 9-patch with any number of stretch bands, like Android's NinePatchDrawable.
private struct NinePatchFrame: View {
    let art: RevealFrameArt

    var body: some View {
        Canvas { context, size in
            let image = context.resolve(Image(art.asset))
            let columns = RevealFrameArt.slices(total: art.size.width, stretch: art.stretchX, target: size.width)
            let rows = RevealFrameArt.slices(total: art.size.height, stretch: art.stretchY, target: size.height)
            var y: CGFloat = 0
            for row in rows {
                var x: CGFloat = 0
                for column in columns {
                    let target = CGRect(x: x, y: y, width: column.length, height: row.length)
                    let sourceWidth = column.source.upperBound - column.source.lowerBound
                    let sourceHeight = row.source.upperBound - row.source.lowerBound
                    if target.width > 0, target.height > 0, sourceWidth > 0, sourceHeight > 0 {
                        // Map the whole image so this tile's source lands on its target.
                        let scaleX = target.width / sourceWidth
                        let scaleY = target.height / sourceHeight
                        let whole = CGRect(x: target.minX - column.source.lowerBound * scaleX,
                                           y: target.minY - row.source.lowerBound * scaleY,
                                           width: art.size.width * scaleX,
                                           height: art.size.height * scaleY)
                        context.drawLayer { tile in
                            // Half-point bleed hides seams between tiles.
                            tile.clip(to: Path(target.insetBy(dx: -0.25, dy: -0.25)))
                            tile.draw(image, in: whole)
                        }
                    }
                    x += column.length
                }
                y += row.length
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Android's reveal panel: dark inner surface tucked 10 dp under the frame's lip and
/// the map frame, both as the background (so a kicked card or the boot can cross the
/// frame, as in Android), with the content inside the frame's declared padding.
struct RevealPanel: ViewModifier {
    let map: GameMap
    var horizontalInset: CGFloat = 6
    var extraBottom: CGFloat = 0

    func body(content: Content) -> some View {
        let art = RevealFrameArt.forMap(map)
        let padding = art.pointPadding
        let overlap: CGFloat = 10
        content
            .padding(.top, padding.top + art.extraTop)
            .padding(.leading, padding.leading + horizontalInset)
            .padding(.bottom, padding.bottom + art.extraBottom + extraBottom)
            .padding(.trailing, padding.trailing + horizontalInset)
            .frame(minWidth: art.size.width / RevealFrameArt.pixelsPerPoint * 0.88)
            .background {
                ZStack {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(art.inner)
                        .padding(.top, max(padding.top - overlap, 0))
                        .padding(.leading, max(padding.leading - overlap, 0))
                        .padding(.bottom, max(padding.bottom - overlap, 0))
                        .padding(.trailing, max(padding.trailing - overlap, 0))
                    NinePatchFrame(art: art)
                }
            }
            .environment(\.colorScheme, .dark)
    }
}

// MARK: - Shared pieces

enum RevealTiming {
    static var testing: Bool { ProcessInfo.processInfo.arguments.contains("-ui-testing") }

    static func seconds(_ value: Double) -> Duration {
        .milliseconds(Int((testing ? min(value, 0.05) : value) * 1_000))
    }
}

/// Android's `bg_role_card`: dark card with a 3 dp gold border.
struct RevealCard: View {
    var image = "card_back_traidores"

    var body: some View {
        Image(image)
            .resizable().scaledToFit()
            .padding(4)
            .background(Color(hex: "#E6231810"), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(TraidoresTheme.gold, lineWidth: 3))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

/// Every dawn reveal sits on the same 72 % black scrim (`#B8000000`) over the table.
private struct RevealScrim<Content: View>: View {
    let opacity: Double
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.72).ignoresSafeArea().opacity(opacity)
            ViewThatFits(in: .vertical) {
                content
                ScrollView { content.padding(.vertical, 24) }
                    .scrollBounceBehavior(.basedOnSize)
            }
        }
    }
}

// MARK: - Death

struct DeathRevealView: View {
    let name: String
    /// The role card and title when the match reveals roles on death.
    let role: (image: String, title: String)?
    let map: GameMap
    let onFinished: () -> Void

    @Environment(\.reduceAnimations) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @State private var overlayOpacity = 0.0
    @State private var contentScale = 0.94
    @State private var flash = 0.0
    @State private var bloodOpacity = 0.0
    @State private var bloodLeftScale = 0.55
    @State private var bloodRightScale = 0.5
    @State private var shake = 0
    @State private var cardPulse = 1.0
    @State private var flipAngle = 0.0
    @State private var showsFront = false
    @State private var roleOpacity = 0.0
    @State private var continueVisible = false
    @State private var finishing = false

    var body: some View {
        RevealScrim(opacity: overlayOpacity) {
            panel
        }
        .overlay {
            Color(red: 160 / 255, green: 24 / 255, blue: 24 / 255).opacity(0.63 * flash)
                .ignoresSafeArea().allowsHitTesting(false)
        }
        .task { await run() }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier("table.dawnAnnouncement")
    }

    private var panel: some View {
        VStack(spacing: 0) {
            Text("AL AMANECER...")
                .font(.system(.callout, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            ZStack {
                RevealCard(image: showsFront ? (role?.image ?? "card_back_traidores") : "card_back_traidores")
                    .frame(width: 112, height: 150)
                    .rotation3DEffect(.degrees(flipAngle), axis: (x: 0, y: 1, z: 0), perspective: 0.4)
                    .scaleEffect(cardPulse)
                    .keyframeAnimator(initialValue: 0.0, trigger: shake) { card, offset in
                        card.offset(x: offset)
                    } keyframes: { _ in
                        KeyframeTrack {
                            CubicKeyframe(-6.0, duration: 0.084)
                            CubicKeyframe(6.0, duration: 0.084)
                            CubicKeyframe(-3.0, duration: 0.084)
                            CubicKeyframe(3.0, duration: 0.084)
                            CubicKeyframe(0.0, duration: 0.084)
                        }
                    }
                Image("death_blood_splatter_art")
                    .resizable().scaledToFit().frame(width: 96, height: 96)
                    .rotationEffect(.degrees(-18))
                    .scaleEffect(bloodLeftScale).opacity(bloodOpacity)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                Image("death_blood_splatter_art")
                    .resizable().scaledToFit().frame(width: 80, height: 80)
                    .rotationEffect(.degrees(24))
                    .scaleEffect(bloodRightScale).opacity(bloodOpacity)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
            .frame(width: 190, height: 190)
            .padding(.top, 4)
            .accessibilityHidden(true)
            Text(name.uppercased())
                .font(.system(.title3, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center).padding(.top, 8)
            Text(role.map { $0.title.uppercased() } ?? "ROL OCULTO")
                .font(.system(.footnote, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center).padding(.top, 2)
                .opacity(role == nil ? 1 : roleOpacity)
            Text("Murió durante la noche")
                .font(.system(.subheadline, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center).padding(.top, 8)
            Button("CONTINUAR") { finish() }
                .font(.system(.footnote, weight: .bold))
                .foregroundStyle(TraidoresTheme.text)
                .frame(width: 210).frame(minHeight: 34)
                .background(Color(hex: "#E7221A12"), in: RoundedRectangle(cornerRadius: 7))
                .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.gold.opacity(0.54)))
                .contentShape(Rectangle())
                .padding(.top, 8)
                .opacity(continueVisible ? 1 : 0)
                .disabled(!continueVisible)
                .accessibilityIdentifier("table.dawnContinue")
        }
        .frame(maxWidth: 300)
        .modifier(RevealPanel(map: map))
        .scaleEffect(contentScale)
        .opacity(overlayOpacity)
        .padding(.horizontal, 24)
    }

    @MainActor
    private func run() async {
        AccessibilityNotification.Announcement(
            "Al amanecer. \(name) murió durante la noche. \(role.map { "Era \($0.title)." } ?? "Rol oculto.")"
        ).post()
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.28)) {
                overlayOpacity = 1; contentScale = 1
                bloodOpacity = 1; bloodLeftScale = 1; bloodRightScale = 1
                showsFront = role != nil; roleOpacity = 1
            }
        } else {
            withAnimation(.easeOut(duration: 0.28)) { overlayOpacity = 1; contentScale = 1 }
            try? await Task.sleep(for: RevealTiming.seconds(0.28))
            // Impact: shake, red flash and the splatters bursting out.
            shake += 1
            withAnimation(.easeInOut(duration: 0.21)) { flash = 0.56 }
            withAnimation(.easeInOut(duration: 0.42)) {
                bloodOpacity = 1; bloodLeftScale = 1.16; bloodRightScale = 1.1
            }
            try? await Task.sleep(for: RevealTiming.seconds(0.21))
            withAnimation(.easeInOut(duration: 0.21)) { flash = 0 }
            try? await Task.sleep(for: RevealTiming.seconds(0.21))
            if role != nil {
                withAnimation(.easeIn(duration: 0.23)) { flipAngle = 90 }
                try? await Task.sleep(for: RevealTiming.seconds(0.23))
                showsFront = true
                flipAngle = -90
                withAnimation(.easeOut(duration: 0.26)) { flipAngle = 0; roleOpacity = 1 }
            } else {
                withAnimation(.easeOut(duration: 0.21)) { cardPulse = 1.05 }
                try? await Task.sleep(for: RevealTiming.seconds(0.21))
                withAnimation(.easeOut(duration: 0.21)) { cardPulse = 1 }
            }
        }
        // CONTINUAR shows once the death sound (2.9 s) has played, like Android.
        try? await Task.sleep(for: RevealTiming.seconds(reduceMotion ? 2.9 : 1.9))
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.18)) { continueVisible = true }
        guard !voiceOver else { return }
        try? await Task.sleep(for: RevealTiming.seconds(9))
        guard !Task.isCancelled else { return }
        finish()
    }

    private func finish() {
        guard !finishing else { return }
        finishing = true
        Task { @MainActor in
            withAnimation(.easeIn(duration: 0.26)) { overlayOpacity = 0; contentScale = 0.97 }
            try? await Task.sleep(for: RevealTiming.seconds(0.26))
            onFinished()
        }
    }
}

// MARK: - Silence

struct SilenceRevealView: View {
    let name: String
    let map: GameMap
    let onFinished: () -> Void

    @Environment(\.reduceAnimations) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @State private var overlayOpacity = 0.0
    @State private var contentScale = 0.95
    @State private var sidesOpacity = 0.0
    @State private var sidesOffset = 62.0
    @State private var doorOpacity = 0.0
    @State private var doorAngle = -72.0
    @State private var doorOffset = 28.0
    @State private var lockOpacity = 0.0
    @State private var lockScale = 1.65
    @State private var shake = 0
    @State private var continueVisible = false
    @State private var finishing = false

    var body: some View {
        RevealScrim(opacity: overlayOpacity) { panel }
            .task { await run() }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityIdentifier("table.dawnAnnouncement")
    }

    private var panel: some View {
        VStack(spacing: 0) {
            Text("UNA VOZ FUE SILENCIADA")
                .font(.system(.headline, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 14).padding(.vertical, 5).padding(.top, 8)
            ZStack {
                RevealCard()
                    .frame(width: 88, height: 117)
                    .keyframeAnimator(initialValue: 0.0, trigger: shake) { card, offset in
                        card.offset(x: offset)
                    } keyframes: { _ in
                        KeyframeTrack {
                            CubicKeyframe(-3.0, duration: 0.064)
                            CubicKeyframe(3.0, duration: 0.064)
                            CubicKeyframe(-2.0, duration: 0.064)
                            CubicKeyframe(2.0, duration: 0.064)
                            CubicKeyframe(0.0, duration: 0.064)
                        }
                    }
                CageSide()
                    .frame(width: 46, height: 128)
                    .offset(x: -sidesOffset).opacity(sidesOpacity)
                    .frame(maxWidth: .infinity, alignment: .leading)
                CageSide()
                    .frame(width: 46, height: 128)
                    .scaleEffect(x: -1)
                    .offset(x: sidesOffset).opacity(sidesOpacity)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                CageDoor()
                    .frame(width: 88, height: 122)
                    .rotation3DEffect(.degrees(doorAngle), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
                    .offset(x: doorOffset).opacity(doorOpacity)
                CageLock()
                    .frame(width: 26, height: 32)
                    .scaleEffect(lockScale).opacity(lockOpacity)
                    // Centred with a 17 dp top margin in Android: half of it shifts the lock.
                    .offset(y: 8.5)
            }
            .frame(width: 160, height: 151)
            .padding(.top, 8)
            .accessibilityHidden(true)
            Text(name.uppercased())
                .font(.system(.title3, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12).padding(.top, 5).padding(.bottom, 2)
            Text("No puede hablar ni votar durante el día")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12).padding(.top, 2).padding(.bottom, 6)
            if voiceOver {
                Button("CONTINUAR") { finish() }
                    .buttonStyle(TraidoresButtonStyle(prominent: false))
                    .opacity(continueVisible ? 1 : 0)
            }
        }
        .frame(maxWidth: 290)
        .modifier(RevealPanel(map: map, extraBottom: map == .pampa ? 20 : 0))
        .scaleEffect(contentScale)
        .opacity(overlayOpacity)
        .padding(.horizontal, 28)
    }

    @MainActor
    private func run() async {
        AccessibilityNotification.Announcement("Una voz fue silenciada. \(name) no puede hablar ni votar durante el día.").post()
        try? await Task.sleep(for: RevealTiming.seconds(0.3))
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.36)) {
                overlayOpacity = 1; contentScale = 1
                sidesOpacity = 1; sidesOffset = 0
                doorOpacity = 1; doorAngle = 0; doorOffset = 0
                lockOpacity = 1; lockScale = 1
            }
        } else {
            withAnimation(.easeOut(duration: 0.28)) { overlayOpacity = 1; contentScale = 1 }
            try? await Task.sleep(for: RevealTiming.seconds(0.28))
            withAnimation(.easeOut(duration: 0.46)) { sidesOpacity = 1; sidesOffset = 0 }
            try? await Task.sleep(for: RevealTiming.seconds(0.46))
            withAnimation(.easeOut(duration: 0.42)) { doorOpacity = 1; doorAngle = 0; doorOffset = 0 }
            try? await Task.sleep(for: RevealTiming.seconds(0.42))
            shake += 1
            withAnimation(.easeInOut(duration: 0.32)) { lockOpacity = 1; lockScale = 1 }
            try? await Task.sleep(for: RevealTiming.seconds(0.32))
        }
        withAnimation { continueVisible = true }
        guard !voiceOver else { return }
        try? await Task.sleep(for: RevealTiming.seconds(1.8))
        guard !Task.isCancelled else { return }
        finish()
    }

    private func finish() {
        guard !finishing else { return }
        finishing = true
        Task { @MainActor in
            withAnimation(.easeIn(duration: 0.3)) { overlayOpacity = 0 }
            try? await Task.sleep(for: RevealTiming.seconds(0.3))
            onFinished()
        }
    }
}

/// Android's `silence_cage_side` vector (54 × 150 viewport).
private struct CageSide: View {
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 54, y: size.height / 150)
            context.fill(rects([(4, 5, 8, 140), (46, 5, 6, 140), (4, 5, 48, 7), (4, 138, 48, 7)]), with: .color(Color(hex: "#4A3522")))
            context.fill(rects([(18, 10, 5, 130), (32, 10, 5, 130)]), with: .color(Color(hex: "#745432")))
            context.fill(rects([(6, 6, 2, 138), (19, 11, 2, 128), (33, 11, 2, 128), (47, 6, 2, 138), (6, 6, 44, 2), (6, 140, 44, 2)]),
                         with: .color(Color(hex: "#B58A52")))
            context.fill(rects([(10, 16, 2, 118), (44, 16, 2, 118)]), with: .color(Color(hex: "#271B12")))
        }
    }
}

/// Android's `silence_cage_door` vector (104 × 144 viewport).
private struct CageDoor: View {
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 104, y: size.height / 144)
            context.stroke(Path(CGRect(x: 7, y: 5, width: 90, height: 134)), with: .color(Color(hex: "#4A3522")),
                           style: StrokeStyle(lineWidth: 8, lineJoin: .round))
            context.fill(rects([(22, 8, 6, 128), (39, 8, 6, 128), (58, 8, 6, 128), (76, 8, 6, 128), (8, 45, 88, 7), (8, 94, 88, 7)]),
                         with: .color(Color(hex: "#5C4228")))
            context.fill(rects([(23, 10, 2, 124), (40, 10, 2, 124), (59, 10, 2, 124), (77, 10, 2, 124), (10, 46, 84, 2), (10, 95, 84, 2)]),
                         with: .color(Color(hex: "#B58A52")))
            context.fill(rects([(91, 8, 4, 128)]), with: .color(Color(hex: "#2A1C12")))
        }
    }
}

/// Android's `silence_cage_lock` vector (34 × 42 viewport).
private struct CageLock: View {
    var body: some View {
        Canvas { context, size in
            context.scaleBy(x: size.width / 34, y: size.height / 42)
            var shackle = Path()
            shackle.move(to: .init(x: 9, y: 19))
            shackle.addLine(to: .init(x: 9, y: 12))
            shackle.addCurve(to: .init(x: 17, y: 2), control1: .init(x: 9, y: 5), control2: .init(x: 13, y: 2))
            shackle.addCurve(to: .init(x: 26, y: 12), control1: .init(x: 22, y: 2), control2: .init(x: 26, y: 6))
            shackle.addLine(to: .init(x: 26, y: 19))
            context.stroke(shackle, with: .color(Color(hex: "#D8B45E")), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            context.fill(rects([(4, 17, 26, 22)]), with: .color(Color(hex: "#A87928")))
            context.fill(rects([(6, 19, 22, 4), (15, 26, 4, 8)]), with: .color(Color(hex: "#E3BD61")))
            context.fill(rects([(15, 25, 4, 5)]), with: .color(Color(hex: "#5A3A18")))
        }
    }
}

private func rects(_ values: [(CGFloat, CGFloat, CGFloat, CGFloat)]) -> Path {
    var path = Path()
    for (x, y, width, height) in values { path.addRect(CGRect(x: x, y: y, width: width, height: height)) }
    return path
}

// MARK: - Nobody died

struct NoDeathRevealView: View {
    let map: GameMap
    let onFinished: () -> Void

    @Environment(\.reduceAnimations) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @State private var overlayOpacity = 0.0
    @State private var contentScale = 0.95
    @State private var contentOffset = 4.0
    @State private var sunOpacity = 0.0
    @State private var sunScale = 0.45
    @State private var continueVisible = false
    @State private var finishing = false

    var body: some View {
        RevealScrim(opacity: overlayOpacity) { panel }
            .task { await run() }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityIdentifier("table.dawnAnnouncement")
    }

    private var panel: some View {
        VStack(spacing: 0) {
            Text("AL AMANECER...")
                .font(.system(.callout, weight: .bold)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12).padding(.vertical, 6).padding(.top, 8)
            Image("no_death_sun_rural")
                .resizable().scaledToFit()
                .frame(width: 136, height: 136)
                .scaleEffect(sunScale).opacity(sunOpacity)
                .frame(width: 152, height: 122)
                .padding(.top, 9)
                .accessibilityHidden(true)
            VStack(spacing: 4) {
                Text("EL PUEBLO RESPIRA")
                    .font(.system(.title3, weight: .bold)).foregroundStyle(TraidoresTheme.text)
                Text("Nadie murió esta noche.")
                    .font(.system(.footnote, weight: .semibold)).foregroundStyle(TraidoresTheme.gold)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 14).padding(.vertical, 8).padding(.top, 9)
            if voiceOver {
                Button("CONTINUAR") { finish() }
                    .buttonStyle(TraidoresButtonStyle(prominent: false))
                    .opacity(continueVisible ? 1 : 0)
            }
        }
        .frame(maxWidth: 280)
        .modifier(RevealPanel(map: map))
        .scaleEffect(contentScale)
        .offset(y: contentOffset)
        .opacity(overlayOpacity)
        .padding(.horizontal, 34)
    }

    @MainActor
    private func run() async {
        AccessibilityNotification.Announcement("Al amanecer. El pueblo respira: nadie murió esta noche.").post()
        try? await Task.sleep(for: RevealTiming.seconds(0.3))
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.36)) {
                overlayOpacity = 1; contentScale = 1; contentOffset = 0; sunOpacity = 1; sunScale = 1
            }
            try? await Task.sleep(for: RevealTiming.seconds(0.36))
        } else {
            withAnimation(.easeOut(duration: 0.36)) {
                overlayOpacity = 1; contentScale = 1; sunOpacity = 1; sunScale = 1
            }
            try? await Task.sleep(for: RevealTiming.seconds(0.36))
            withAnimation(.easeOut(duration: 0.26)) { contentOffset = 0 }
            try? await Task.sleep(for: RevealTiming.seconds(0.26))
            // The sun breathes once.
            withAnimation(.easeOut(duration: 0.45)) { sunScale = 1.08 }
            try? await Task.sleep(for: RevealTiming.seconds(0.45))
            withAnimation(.easeOut(duration: 0.45)) { sunScale = 1 }
            try? await Task.sleep(for: RevealTiming.seconds(0.45))
        }
        withAnimation { continueVisible = true }
        guard !voiceOver else { return }
        try? await Task.sleep(for: RevealTiming.seconds(1.6))
        guard !Task.isCancelled else { return }
        finish()
    }

    private func finish() {
        guard !finishing else { return }
        finishing = true
        Task { @MainActor in
            withAnimation(.easeIn(duration: 0.3)) { overlayOpacity = 0; contentScale = 0.97 }
            try? await Task.sleep(for: RevealTiming.seconds(0.3))
            onFinished()
        }
    }
}
