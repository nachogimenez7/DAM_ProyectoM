import SwiftUI
import TraidoresCore

struct PlayModesView: View {
    var body: some View {
        MenuPage(title: "Jugar") {
            NavigationLink {
                LocalModeView()
            } label: {
                modeCard(title: "JUGAR CONTRA IA", image: "modo_juego_local_pampa_v3",
                         message: "Pampa clásica · Vos contra cuatro bots, sin conexión.")
            }.buttonStyle(.plain)
            modeCard(title: "JUGAR ONLINE", image: "modo_jugar_online",
                     message: "Próximamente · Compartí la mesa con tus amigos.")
            Text("El online y los roles especiales llegarán en las próximas etapas.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
        }
    }

    private func modeCard(title: String, image: String, message: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Image(image).resizable().scaledToFit().accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text(title).font(TraidoresTheme.title(23)).foregroundStyle(TraidoresTheme.gold)
                Text(message).font(.subheadline)
            }.padding(18)
        }
        .background(TraidoresTheme.panel)
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
