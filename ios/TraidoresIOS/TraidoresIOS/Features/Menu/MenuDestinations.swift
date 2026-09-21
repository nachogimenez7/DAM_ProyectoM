import SwiftUI
import TraidoresCore

struct PlayModesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            MenuBackground()

            VStack(spacing: 0) {
                Text("SELECCIONAR MODO")
                    .font(TraidoresTheme.title(23))
                    .foregroundStyle(TraidoresTheme.gold)
                    .padding(.top, 28)
                    .padding(.bottom, 12)

                Spacer(minLength: 8)

                VStack(spacing: 40) {
                    NavigationLink {
                        LocalModeView()
                    } label: {
                        modeCard(title: "JUGAR CONTRA IA", image: "modo_juego_local_pampa_v3")
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("play.local")

                    modeCard(title: "JUGAR ONLINE", image: "modo_jugar_online", badge: "PRÓXIMAMENTE")
                        .accessibilityElement(children: .combine)
                }
                .frame(maxWidth: 560)
                .padding(.horizontal, 16)

                Spacer(minLength: 24)
            }

            Button(action: dismiss.callAsFunction) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 10))
                    .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
            }
            .foregroundStyle(TraidoresTheme.secondary)
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityLabel("Volver")
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func modeCard(title: String, image: String, badge: String? = nil) -> some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: [.clear, .black.opacity(0.88)],
                           startPoint: .center, endPoint: .bottom)
                .allowsHitTesting(false)
            Text(title)
                .font(TraidoresTheme.title(22))
                .foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center)
                .shadow(color: .black, radius: 3, y: 2)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)

            if let badge {
                Text(badge)
                    .font(.caption2.bold())
                    .tracking(1)
                    .foregroundStyle(TraidoresTheme.gold)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(TraidoresTheme.ink.opacity(0.92), in: Capsule())
                    .padding(10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
        }
        .aspectRatio(3 / 2, contentMode: .fit)
        .background {
            Image(image)
                .resizable()
                .scaledToFill()
                .accessibilityHidden(true)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

struct RolesGuideView: View {
    var body: some View {
        MenuPage(title: "Roles") {
            Text("Cada carta esconde una intención.")
                .font(TraidoresTheme.title(26)).foregroundStyle(TraidoresTheme.gold)
            ForEach(RoleCatalog.all) { role in
                VStack(alignment: .leading, spacing: 8) {
                    Text(role.title).font(TraidoresTheme.title(24)).foregroundStyle(TraidoresTheme.gold)
                    Text(role.team.rawValue.uppercased())
                        .font(.caption.weight(.bold)).tracking(1)
                    Text(role.instructions)
                    Text("\(role.exclusiveMap?.title ?? "Todos los mapas") · Desde \(role.minimumPlayers) jugadores")
                        .font(.caption).foregroundStyle(TraidoresTheme.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }
}

struct HelpView: View {
    var body: some View {
        MenuPage(title: "Cómo jugar") {
            InformationCard(title: "Una identidad secreta", message: "Al comenzar recibís un rol. Leé tu carta y descubrí a qué bando pertenecés. Los demás pueden estar mintiendo.")
            InformationCard(title: "La noche", message: "Los roles con habilidades actúan en secreto: matar, investigar, proteger o silenciar. Al amanecer se conocen las consecuencias.")
            InformationCard(title: "El debate", message: "Escuchá las versiones, compartí tus sospechas y elegí en quién confiar. Cuidá lo que revelás sobre tu identidad.")
            InformationCard(title: "La votación", message: "La mesa decide a quién expulsar. Los empates y las habilidades especiales pueden cambiar el resultado.")
            InformationCard(title: "La victoria", message: "El Pueblo busca eliminar a todos los asesinos y espías. Los Traidores buscan alcanzar la paridad con el Pueblo. Los roles neutrales tienen objetivos propios.")
        }
    }
}

struct OptionsView: View {
    @Environment(MenuPreferences.self) private var preferences

    var body: some View {
        @Bindable var preferences = preferences
        MenuPage(title: "Opciones") {
            VStack(alignment: .leading, spacing: 12) {
                Toggle("Música del menú", isOn: $preferences.musicEnabled)
                    .font(.headline).tint(TraidoresTheme.gold)
                Text("La música respeta el modo silencio del iPhone y se pausa al salir de la app.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            .padding(20)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
        }
    }
}
