import SwiftUI

enum TraidoresTheme {
    static let gold = Color(red: 212 / 255, green: 162 / 255, blue: 78 / 255)
    static let ink = Color(red: 26 / 255, green: 21 / 255, blue: 16 / 255)
    static let panel = Color(red: 42 / 255, green: 35 / 255, blue: 24 / 255)
    static let border = Color(red: 107 / 255, green: 79 / 255, blue: 42 / 255)
    static let text = Color(red: 240 / 255, green: 230 / 255, blue: 210 / 255)
    static let secondary = Color(red: 196 / 255, green: 182 / 255, blue: 156 / 255)

    static func title(_ size: CGFloat, relativeTo style: Font.TextStyle = .title) -> Font {
        .custom("BreeSerif-Regular", size: size, relativeTo: style)
    }
}

struct MenuBackground: View {
    var body: some View {
        GeometryReader { geometry in
            Image("fondo_menu")
                .resizable()
                .scaledToFill()
                .frame(width: geometry.size.width, height: geometry.size.height)
                .clipped()
                .overlay(.black.opacity(0.18))

        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

struct TraidoresButtonStyle: ButtonStyle {
    var prominent = false
    /// Profile styles pass their own colors so buttons do not fall back to the classic brown.
    var accent: Color? = nil
    var surface: Color? = nil

    func makeBody(configuration: Configuration) -> some View {
        TraidoresButtonBody(configuration: configuration, prominent: prominent, accent: accent, surface: surface)
    }
}

private struct TraidoresButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let prominent: Bool
    let accent: Color?
    let surface: Color?
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        let fill = surface ?? TraidoresTheme.panel
        configuration.label
            .font(TraidoresTheme.title(19, relativeTo: .headline))
            .tracking(1)
            .frame(maxWidth: .infinity, minHeight: 28)
            .padding(.vertical, 13)
            .padding(.horizontal, 18)
            .foregroundStyle(prominent ? TraidoresTheme.ink : TraidoresTheme.text)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(prominent
                          ? LinearGradient(colors: [Color(red: 232 / 255, green: 184 / 255, blue: 75 / 255), TraidoresTheme.gold], startPoint: .top, endPoint: .bottom)
                          : LinearGradient(colors: [fill, fill], startPoint: .top, endPoint: .bottom))
            }
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(prominent ? TraidoresTheme.gold : accent?.opacity(0.6) ?? TraidoresTheme.border, lineWidth: 1)
            }
            // A custom style must show the disabled state itself; SwiftUI only blocks the tap.
            .opacity(!isEnabled ? 0.45 : configuration.isPressed ? 0.72 : 1)
            .saturation(isEnabled ? 1 : 0.4)
    }
}

extension MenuTextSize {
    /// Normal follows iOS; Grande enlarges without ever shrinking an accessibility size;
    /// Compacto is one step smaller, as the player explicitly asked.
    func resolved(system: DynamicTypeSize) -> DynamicTypeSize {
        switch self {
        case .system: return system
        case .large: return max(system, .xLarge)
        case .compact:
            let sizes = DynamicTypeSize.allCases
            guard let index = sizes.firstIndex(of: system), index > 0 else { return system }
            return sizes[index - 1]
        }
    }
}

private struct ReduceAnimationsKey: EnvironmentKey { static let defaultValue = false }

extension EnvironmentValues {
    /// iOS Reduce Motion or the game's "Reducir animaciones" option. Read this instead of
    /// `accessibilityReduceMotion`, which cannot be overridden.
    var reduceAnimations: Bool {
        get { self[ReduceAnimationsKey.self] }
        set { self[ReduceAnimationsKey.self] = newValue }
    }
}

/// Applied once at the root: text size and reduced animations reach every screen,
/// sheets and the match.
struct GamePreferencesBridge: ViewModifier {
    let preferences: MenuPreferences
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.dynamicTypeSize) private var systemTextSize

    func body(content: Content) -> some View {
        content
            .environment(\.reduceAnimations, systemReduceMotion || preferences.reduceAnimations)
            // "Normal" passes the system size straight through: re-applying a value read from
            // the environment lags one frame behind every Dynamic Type change.
            .dynamicTypeSize(preferences.textSize == .system
                ? DynamicTypeSize.xSmall...DynamicTypeSize.accessibility5
                : preferences.textSize.resolved(system: systemTextSize)...preferences.textSize.resolved(system: systemTextSize))
    }
}

struct MenuPage<Content: View>: View {
    let title: String
    var backgroundAsset: String? = nil
    var headerTint: Color = TraidoresTheme.text
    var headerSurface: Color = TraidoresTheme.panel
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ZStack {
            if let backgroundAsset {
                GeometryReader { geometry in
                    Image(backgroundAsset).resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        // Darker toward the bottom where most reading text sits.
                        .overlay(LinearGradient(colors: [.black.opacity(0.35), .black.opacity(0.6)],
                                                startPoint: .top, endPoint: .bottom))
                }.ignoresSafeArea().accessibilityHidden(true)
            } else {
                MenuBackground()
            }
            VStack(spacing: 0) {
                MenuHeader(title: title, back: dismiss.callAsFunction, tint: headerTint, surface: headerSurface)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18, content: content)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct MenuHeader: View {
    let title: String
    let back: () -> Void
    var tint: Color = TraidoresTheme.text
    var surface: Color = TraidoresTheme.panel

    var body: some View {
        ZStack {
            Text(title).font(TraidoresTheme.title(19)).foregroundStyle(tint)
                .lineLimit(2).minimumScaleFactor(0.7).multilineTextAlignment(.center)
                // Leave room for the 44 pt buttons on both sides.
                .padding(.horizontal, 52)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Button(action: back) {
                    // Like the system navigation bar, the icon keeps its size;
                    // the Large Content Viewer covers accessibility text sizes.
                    Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .background(surface.opacity(0.94), in: Circle())
                        .overlay(Circle().stroke(tint.opacity(0.45)))
                }
                .foregroundStyle(tint)
                .accessibilityLabel("Volver")
                .accessibilityShowsLargeContentViewer()
                .accessibilityIdentifier("menu.back")
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, minHeight: 44)
    }
}

struct InformationCard: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(TraidoresTheme.title(23)).foregroundStyle(TraidoresTheme.gold)
            Text(message).font(.body).foregroundStyle(TraidoresTheme.text)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border, lineWidth: 1))
    }
}

extension Color {
    /// `#RRGGBB` or `#AARRGGBB`, the formats used by the Android sources.
    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&value)
        let hasAlpha = hex.count > 7
        self.init(.sRGB,
                  red: Double((value >> 16) & 0xFF) / 255,
                  green: Double((value >> 8) & 0xFF) / 255,
                  blue: Double(value & 0xFF) / 255,
                  opacity: hasAlpha ? Double((value >> 24) & 0xFF) / 255 : 1)
    }
}

/// Profile styles, with the colors of Android's `CosmeticPilot` palettes.
enum ProfileStyle: String, CaseIterable, Identifiable {
    case classic, space, sea, fire
    var id: String { rawValue }

    var name: String {
        switch self { case .classic: "Clásico"; case .space: "Espacial"; case .sea: "Abismo Real"; case .fire: "Forja Infernal" }
    }
    /// Same wording as Android's style picker.
    var pickerTitle: String {
        switch self {
        case .classic: "CLÁSICO · sin decoración"
        case .space: "ESPACIAL · Órbita violeta"
        case .sea: "MAR · Abismo Real"
        case .fire: "LAVA · Forja Infernal"
        }
    }
    var primary: Color {
        switch self { case .classic: TraidoresTheme.gold; case .space: Color(hex: "#62E9FF"); case .sea: Color(hex: "#3DE6E0"); case .fire: Color(hex: "#FF6A32") }
    }
    var secondary: Color {
        switch self { case .classic: Color(hex: "#E8B84B"); case .space: Color(hex: "#965CFF"); case .sea: Color(hex: "#D6BD76"); case .fire: Color(hex: "#F2C15D") }
    }
    var text: Color {
        switch self { case .classic: TraidoresTheme.text; case .space: Color(hex: "#C9F7FF"); case .sea: Color(hex: "#C9FBF5"); case .fire: Color(hex: "#FFE0B2") }
    }
    /// Flat surface used by profile cards.
    var surface: Color {
        switch self {
        case .classic: TraidoresTheme.panel
        case .space: Color(red: 12 / 255, green: 19 / 255, blue: 43 / 255)
        case .sea: Color(red: 7 / 255, green: 26 / 255, blue: 36 / 255)
        case .fire: Color(red: 30 / 255, green: 10 / 255, blue: 7 / 255)
        }
    }
    /// Android `outer` frame gradient.
    var frame: [Color] {
        switch self {
        case .classic: [TraidoresTheme.gold, Color(hex: "#F3D58A"), TraidoresTheme.gold]
        case .space: [Color(hex: "#965CFF"), Color(hex: "#62E9FF"), Color(hex: "#965CFF")]
        case .sea: [Color(hex: "#3DE6E0"), Color(hex: "#D6BD76"), Color(hex: "#58AFC0")]
        case .fire: [Color(hex: "#B92A1D"), Color(hex: "#F2C15D"), Color(hex: "#FF6A32")]
        }
    }
    /// Android `surface` gradient.
    var fill: [Color] {
        switch self {
        case .classic: [Color(hex: "#3A2E1C"), TraidoresTheme.panel, Color(hex: "#30261A")]
        case .space: [Color(hex: "#E51B2343"), Color(hex: "#EB0C132B"), Color(hex: "#E526153E")]
        case .sea: [Color(hex: "#ED0B3440"), Color(hex: "#F0071A24"), Color(hex: "#ED092A35")]
        case .fire: [Color(hex: "#F035110D"), Color(hex: "#F00E0B0A"), Color(hex: "#ED250906")]
        }
    }
    var backgroundAsset: String? { self == .classic ? nil : "profile_background_\(rawValue)" }
}

/// Decorated frame for anything the player picks (emotes, avatars, banners, styles).
/// Selected: tinted fill, gradient double border, small diamond ornaments and a soft glow.
struct SelectionFrame: ViewModifier {
    let selected: Bool
    var tone: Color = TraidoresTheme.gold
    var accent: Color = Color(hex: "#F3D58A")
    var cornerRadius: CGFloat = 12
    var fill: Color = TraidoresTheme.panel

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius)
        content
            .background {
                shape.fill(fill.opacity(0.94))
                    .overlay { if selected { shape.fill(tone.opacity(0.12)) } }
            }
            .overlay {
                if selected {
                    ZStack {
                        shape.strokeBorder(LinearGradient(colors: [tone, accent, tone],
                                                          startPoint: .topLeading, endPoint: .bottomTrailing),
                                           lineWidth: 2.5)
                        shape.inset(by: 4).strokeBorder(tone.opacity(0.45), lineWidth: 1)
                        VStack {
                            ornament
                            Spacer()
                            ornament
                        }
                        .padding(.vertical, -4)
                    }
                    .shadow(color: tone.opacity(0.55), radius: 6)
                } else {
                    shape.strokeBorder(TraidoresTheme.border, lineWidth: 1)
                }
            }
            .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var ornament: some View {
        Rectangle().fill(LinearGradient(colors: [accent, tone], startPoint: .top, endPoint: .bottom))
            .frame(width: 8, height: 8).rotationEffect(.degrees(45))
            .overlay(Rectangle().stroke(TraidoresTheme.ink, lineWidth: 1).rotationEffect(.degrees(45)))
            .accessibilityHidden(true)
    }
}

extension View {
    func selectionFrame(_ selected: Bool, tone: Color = TraidoresTheme.gold,
                        accent: Color = Color(hex: "#F3D58A"), cornerRadius: CGFloat = 12,
                        fill: Color = TraidoresTheme.panel) -> some View {
        modifier(SelectionFrame(selected: selected, tone: tone, accent: accent,
                                cornerRadius: cornerRadius, fill: fill))
    }

    /// Text that sits on illustrated backgrounds gets its own readable surface.
    func readableOnArtwork() -> some View {
        self.padding(.horizontal, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border.opacity(0.6)))
    }
}

/// Gold medallion with the pick order (Android `bg_emote_order_badge`) or a check mark.
struct SelectionBadge: View {
    var number: Int? = nil
    var tone: Color = TraidoresTheme.gold

    var body: some View {
        Group {
            if let number { Text("\(number)").font(.system(size: 13, weight: .heavy)).monospacedDigit() }
            else { Image(systemName: "checkmark").font(.system(size: 11, weight: .heavy)) }
        }
        .foregroundStyle(TraidoresTheme.ink)
        .frame(width: 24, height: 24)
        .background(Circle().fill(LinearGradient(colors: [Color(hex: "#F3D58A"), tone],
                                                 startPoint: .top, endPoint: .bottom)))
        .overlay(Circle().stroke(TraidoresTheme.ink, lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
        .accessibilityHidden(true)
    }
}
