import SwiftUI

private enum MenuRoute: Hashable {
    case play, roles, help, options, profile, about
}

struct MenuView: View {
    @Environment(MenuPreferences.self) private var preferences

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
                        MenuPage(title: "Perfil") {
                            InformationCard(title: "Tu identidad en la mesa", message: "El perfil llegará con las primeras partidas. Por ahora podés explorar los roles, las reglas y las opciones del menú.")
                        }
                    case .about:
                        MenuPage(title: "Bandido Games") {
                            Image("bandido_menu_medallion").resizable().scaledToFit()
                                .frame(height: 130).frame(maxWidth: .infinity).accessibilityHidden(true)
                            InformationCard(title: "Traidores", message: "Un juego de deducción social. Observá, escuchá y elegí en quién confiar.\n\nDesconfía de todos.")
                        }
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
                }
                .foregroundStyle(TraidoresTheme.gold)
                .frame(minHeight: 44)
            }
            .accessibilityLabel("Acerca de Bandido Games")
            Spacer(minLength: 0)
            NavigationLink(value: MenuRoute.profile) {
                Image(systemName: "person.crop.circle")
                    .font(.title2).frame(width: 44, height: 44)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityLabel("Abrir perfil")
        }
    }

    private var title: some View {
        VStack(spacing: 8) {
            Image("logo_traidores_clean").resizable().scaledToFit()
                .frame(width: 90, height: 100).accessibilityHidden(true)
            Text("TRAIDORES")
                .font(TraidoresTheme.title(42, relativeTo: .largeTitle))
                .foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .shadow(color: .black, radius: 8, y: 4)
                .accessibilityAddTraits(.isHeader)
            Text("VERSIÓN EN DESARROLLO")
                .font(.caption2.weight(.semibold)).tracking(1.5)
                .foregroundStyle(TraidoresTheme.gold)
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
                    .font(.title3).frame(width: 48, height: 48)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityLabel(preferences.musicEnabled ? "Silenciar música" : "Activar música")
            .accessibilityValue(preferences.musicEnabled ? "Activada" : "Silenciada")
            Spacer()
            Text("Bandido Games · iOS")
                .font(.caption).foregroundStyle(TraidoresTheme.secondary)
        }
    }

    private func menuLink(_ title: String, route: MenuRoute, prominent: Bool = false) -> some View {
        NavigationLink(title, value: route)
            .buttonStyle(TraidoresButtonStyle(prominent: prominent))
    }
}

#Preview { MenuView().environment(MenuPreferences()).preferredColorScheme(.dark) }
