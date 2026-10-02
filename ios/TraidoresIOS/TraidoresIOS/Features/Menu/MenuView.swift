import SwiftUI

private enum MenuRoute: Hashable {
    case play, roles, help, options, profile, about, feedback
}

struct MenuView: View {
    @AppStorage("menu.localProfile.v1") private var localProfileData = Data()
    @Environment(MenuPreferences.self) private var preferences
    /// Already resolved by `GamePreferencesBridge` at the root.
    @Environment(\.dynamicTypeSize) private var textSize

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ZStack {
                    MenuBackground()
                    ScrollView {
                        VStack(spacing: 24) {
                            header
                            Spacer(minLength: 12)
                            title
                            VStack(spacing: 12) {
                                menuLink("JUGAR", route: .play, prominent: true)
                                menuLink("ROLES", route: .roles)
                                menuLink("AYUDA", route: .help)
                                menuLink("OPCIONES", route: .options)
                            }
                            .frame(maxWidth: 330)
                            Spacer(minLength: 12)
                            footer
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .frame(maxWidth: 520)
                        .frame(minHeight: geometry.size.height)
                        .frame(maxWidth: .infinity)
                    }
                    // Scrolled content fades under the status bar instead of colliding with it.
                    LinearGradient(colors: [.black.opacity(0.8), .clear], startPoint: .top, endPoint: .bottom)
                        .frame(height: geometry.safeAreaInsets.top + 20)
                        .frame(maxHeight: .infinity, alignment: .top)
                        .ignoresSafeArea(edges: .top)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MenuRoute.self) { route in
                Group {
                    switch route {
                    case .play: PlayModesView()
                    case .roles: RolesGuideView()
                    case .help: HelpView()
                    case .options: OptionsView()
                    case .profile:
                        ProfileView()
                    case .about:
                        AboutView()
                    case .feedback: SupportMessageView(feedback: true)
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            NavigationLink(value: MenuRoute.about) {
                HStack(spacing: 8) {
                    Image("bandido_menu_medallion").resizable().scaledToFit()
                        .frame(width: 38, height: 38).accessibilityHidden(true)
                    Text("Bandido Games").font(TraidoresTheme.title(16, relativeTo: .subheadline))
                        .lineLimit(1).minimumScaleFactor(0.8)
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                }
                .foregroundStyle(TraidoresTheme.gold)
                .frame(minHeight: 44)
            }
            .accessibilityLabel("Acerca de Bandido Games")
            .accessibilityShowsLargeContentViewer()
            .accessibilityIdentifier("menu.about")
            Spacer(minLength: 0)
            NavigationLink(value: MenuRoute.profile) {
                let profile = LocalMenuProfile.load(localProfileData)
                ProfilePortrait(image: profile.avatar, photoData: profile.photoData)
                    .frame(width: 44, height: 44)
                    .overlay(Circle().stroke(TraidoresTheme.gold, lineWidth: 1.5))
            }
            .accessibilityLabel("Abrir perfil")
            .accessibilityShowsLargeContentViewer()
            .accessibilityIdentifier("menu.profile")
        }
    }

    private var title: some View {
        VStack(spacing: 8) {
            // At accessibility sizes the emblem shrinks so JUGAR stays near the first screen.
            Image("logo_traidores_clean").resizable().scaledToFit()
                .frame(width: textSize.isAccessibilitySize ? 64 : 90,
                       height: textSize.isAccessibilitySize ? 72 : 100)
                .accessibilityHidden(true)
            // The wordmark is a logo: it may grow a little but never breaks mid-word.
            Text("TRAIDORES")
                .font(TraidoresTheme.title(42, relativeTo: .largeTitle))
                .foregroundStyle(TraidoresTheme.gold)
                .lineLimit(1).minimumScaleFactor(0.5)
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .shadow(color: .black, radius: 8, y: 4)
                .accessibilityAddTraits(.isHeader)
            Text("VERSIÓN EN DESARROLLO")
                .font(.caption2.weight(.semibold)).tracking(1.5)
                .foregroundStyle(TraidoresTheme.gold)
                .lineLimit(1).minimumScaleFactor(0.7)
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                .padding(.horizontal, 12).padding(.vertical, 5)
                .background(TraidoresTheme.panel, in: Capsule())
            Text("Desconfía de todos.")
                .font(.body).foregroundStyle(TraidoresTheme.secondary)
                .padding(.top, 4)
        }
        .padding(.bottom, 12)
    }

    private var footer: some View {
        HStack {
            Button {
                preferences.musicEnabled.toggle()
            } label: {
                Image(systemName: preferences.musicEnabled ? "speaker.wave.2" : "speaker.slash")
                    .font(.system(size: 20, weight: .medium)).frame(width: 48, height: 48)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityLabel(preferences.musicEnabled ? "Silenciar música" : "Activar música")
            .accessibilityValue(preferences.musicEnabled ? "Activada" : "Silenciada")
            .accessibilityShowsLargeContentViewer()
            .accessibilityIdentifier("menu.music")
            Spacer()
            NavigationLink(value: MenuRoute.feedback) {
                Text("COMENTARIOS / ERRORES")
                    .lineLimit(2).minimumScaleFactor(0.75).multilineTextAlignment(.trailing)
                    .padding(.horizontal, 8)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
                .font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                .frame(minHeight: 44)
                .accessibilityIdentifier("menu.feedback")
        }
    }

    private func menuLink(_ title: String, route: MenuRoute, prominent: Bool = false) -> some View {
        NavigationLink(title, value: route)
            .buttonStyle(TraidoresButtonStyle(prominent: prominent))
            .accessibilityIdentifier(title == "JUGAR" ? "menu.play" : "menu.\(title.lowercased())")
    }
}

#Preview { MenuView().environment(MenuPreferences()).preferredColorScheme(.dark) }
