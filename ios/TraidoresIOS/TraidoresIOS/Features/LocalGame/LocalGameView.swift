import SwiftUI
import TraidoresCore

struct LocalLobbyView: View {
    @State private var store = LocalGameStore()
    @AppStorage("local.playerName") private var name = ""
    @State private var trainingRole: RoleKey?
    @State private var playing = false
    @State private var replaceGame = false

    var body: some View {
        MenuPage(title: "Contra IA") {
            InformationCard(title: "Una mesa en la Pampa", message: "Vos y cuatro bots. Una partida clásica sin conexión, con un Asesino, un Comisario, un Médico y dos Aldeanos.")
            VStack(alignment: .leading, spacing: 14) {
                TextField("Tu nombre", text: $name)
                    .textFieldStyle(.roundedBorder).autocorrectionDisabled()
                    .onChange(of: name) { _, value in name = String(value.prefix(18)) }
                    .accessibilityLabel("Tu nombre en la partida")
                Picker("Tu rol", selection: $trainingRole) {
                    Text("Al azar").tag(nil as RoleKey?)
                    ForEach([RoleKey.villager, .assassin, .detective, .medic], id: \.self) { role in
                        Text(role.classicTitle).tag(Optional(role))
                    }
                }.tint(TraidoresTheme.gold)
                Text("Elegir un rol te permite practicarlo. Los demás roles se reparten al azar. Podés jugar a tu ritmo y retomar la partida más tarde.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }.padding().background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
            if let game = store.game, game.winner == nil {
                Button("CONTINUAR · DÍA \(game.round)") { playing = true }
                    .buttonStyle(TraidoresButtonStyle(prominent: true))
            }
            Button("NUEVA PARTIDA") {
                if let game = store.game, game.winner == nil { replaceGame = true }
                else { start() }
            }.buttonStyle(TraidoresButtonStyle(prominent: store.game == nil))
            if let error = store.errorMessage { Text(error).font(.footnote).foregroundStyle(.orange) }
        }
        .confirmationDialog("¿Reemplazar la partida guardada?", isPresented: $replaceGame, titleVisibility: .visible) {
            Button("Comenzar otra partida", role: .destructive) { start() }
        }
        .fullScreenCover(isPresented: $playing) { LocalGameView(store: store) }
    }

    private func start() { store.start(name: name, trainingRole: trainingRole); playing = true }
}

private struct LocalGameView: View {
    @Bindable var store: LocalGameStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(MenuPreferences.self) private var preferences
    @State private var selected: Int?
    @State private var showingRole = false
    @State private var leaving = false

    var body: some View {
        NavigationStack {
            if let game = store.game {
                ZStack {
                    GeometryReader { geometry in
                        Image(game.isNight || game.phase == .assignment ? "mapa_pampa_vertical_noche" : "mapa_pampa_vertical_dia")
                            .resizable().scaledToFill()
                            .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                            .overlay(.black.opacity(0.66))
                    }.ignoresSafeArea().accessibilityHidden(true)
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            if let winner = game.winner { results(game, winner: winner) }
                            else {
                                phaseCard(game)
                                if game.phase == .assignment { roleCard(game.human.role) }
                                else {
                                    players(game)
                                    if game.phase == .discussion { debate(game) }
                                    if game.phase == .voteCount { voteCount(game) }
                                    if !game.humanInvestigations.isEmpty {
                                        DisclosureGroup("Tus investigaciones privadas") {
                                            ForEach(Array(game.humanInvestigations.enumerated()), id: \.offset) { _, read in
                                                Text("Noche \(read.round) · \(game.name(read.target)): \(read.suspicious ? "sospechoso" : "inocente")")
                                                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                                            }
                                        }.padding().background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                                    }
                                }
                                actionButton(game)
                                if game.phase != .assignment { history(game) }
                            }
                            if let error = store.errorMessage { Text(error).foregroundStyle(.orange) }
                        }.padding(20).frame(maxWidth: 560).frame(maxWidth: .infinity)
                    }
                    if scenePhase != .active {
                        TraidoresTheme.ink.ignoresSafeArea()
                        Text("TRAIDORES").font(TraidoresTheme.title(36)).foregroundStyle(TraidoresTheme.gold)
                    }
                }
                .foregroundStyle(TraidoresTheme.text)
                .navigationTitle("Pampa · Día \(game.round)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(TraidoresTheme.ink, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Salir") { leaving = true }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showingRole = true } label: { Image(systemName: "person.text.rectangle") }
                            .accessibilityLabel("Ver mi rol")
                    }
                }
                .onChange(of: game.phaseIndex) { _, _ in selected = nil }
                .confirmationDialog("La partida queda guardada para continuar después.", isPresented: $leaving, titleVisibility: .visible) {
                    Button("Volver al menú") { dismiss() }
                }
                .sheet(isPresented: $showingRole) {
                    ScrollView { roleCard(game.human.role).padding(24) }
                        .background(TraidoresTheme.ink)
                        .presentationDetents([.large])
                        .overlay(alignment: .topTrailing) { Button("Cerrar") { showingRole = false }.padding() }
                }
            }
        }
        .onAppear { preferences.gameplayActive = true }
        .onDisappear { preferences.gameplayActive = false }
    }

    private func phaseCard(_ game: ClassicGame) -> some View {
        InformationCard(title: game.phase.classicTitle, message: instructions(game))
    }

    private func roleCard(_ role: RoleKey) -> some View {
        VStack(spacing: 16) {
            Text("TU ROL SECRETO").font(.caption.weight(.bold)).tracking(2)
            Image(role.classicImage).resizable().scaledToFit().frame(maxHeight: 270).accessibilityHidden(true)
            Text(role.classicTitle).font(TraidoresTheme.title(30)).foregroundStyle(TraidoresTheme.gold)
            Text(role == .assassin ? "Traidores" : "Pueblo").font(.headline)
            Text(RoleCatalog.all.first { $0.id == role }?.instructions ?? "")
                .multilineTextAlignment(.center)
        }.padding(20).frame(maxWidth: .infinity)
            .foregroundStyle(TraidoresTheme.text)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 16))
    }

    private func players(_ game: ClassicGame) -> some View {
        VStack(spacing: 8) {
            ForEach(game.players) { player in
                let actionable = game.legalTargets(for: 0).contains(player.id)
                Button { selected = player.id } label: {
                    HStack(spacing: 12) {
                        Image(systemName: player.alive ? "person.crop.circle.fill" : "person.crop.circle.badge.xmark")
                            .font(.title2).foregroundStyle(player.alive ? TraidoresTheme.gold : TraidoresTheme.secondary)
                        VStack(alignment: .leading) {
                            Text(player.name + (player.id == 0 ? " (vos)" : "")).font(.headline)
                            Text(player.alive ? "En la mesa" : "Eliminado").font(.caption)
                        }
                        Spacer()
                        if actionable { Image(systemName: selected == player.id ? "checkmark.circle.fill" : "circle") }
                    }.padding(14).frame(maxWidth: .infinity, minHeight: 48)
                        .background(selected == player.id ? TraidoresTheme.border : TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
                }.buttonStyle(.plain).disabled(!actionable)
                    .accessibilityAddTraits(selected == player.id ? [.isSelected] : [])
            }
        }
    }

    private func debate(_ game: ClassicGame) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("La mesa habla").font(TraidoresTheme.title(24)).foregroundStyle(TraidoresTheme.gold)
            ForEach(game.messages.filter { $0.round == game.round && $0.speaker != nil }) { message in
                VStack(alignment: .leading, spacing: 5) {
                    Text(game.name(message.speaker ?? 0)).font(.headline).foregroundStyle(TraidoresTheme.gold)
                    Text(message.text)
                }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            if game.human.alive {
                if !game.humanSpoke {
                    Menu("Señalar una sospecha") {
                        ForEach(game.living.filter { $0.id != 0 }) { player in
                            Button(player.name) { store.accuse(player.id, revision: game.phaseIndex) }
                        }
                    }.buttonStyle(TraidoresButtonStyle())
                }
                if !game.humanInvestigations.isEmpty && !game.humanSharedRead {
                    Button("Compartir mi última investigación") { store.shareRead(revision: game.phaseIndex) }
                        .buttonStyle(TraidoresButtonStyle())
                    Text("Al compartirla revelás tu cargo de Comisario.").font(.footnote)
                }
            }
        }
    }

    private func voteCount(_ game: ClassicGame) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(game.votes.keys.sorted(), id: \.self) { voter in
                Text("\(game.name(voter)) → \(game.name(game.votes[voter] ?? voter))")
            }
            if !game.tieCandidates.isEmpty { Text("Empate entre \(game.tieCandidates.map { game.name($0) }.joined(separator: ", ")).").bold() }
            else if let target = game.eliminationTarget { Text("Mayoría para \(game.name(target)).").bold() }
        }.padding().frame(maxWidth: .infinity, alignment: .leading)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
    }

    private func actionButton(_ game: ClassicGame) -> some View {
        let targets = game.legalTargets(for: 0)
        return Button(actionTitle(game)) { store.advance(target: selected, revision: game.phaseIndex) }
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            .disabled(!targets.isEmpty && !targets.contains(selected ?? -1))
            .opacity(!targets.isEmpty && selected == nil ? 0.5 : 1)
    }

    private func history(_ game: ClassicGame) -> some View {
        DisclosureGroup("Crónica de la partida") {
            ForEach(game.messages.filter { $0.speaker == nil }.reversed()) { message in
                Text("Día \(message.round) · \(message.text)")
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 5)
            }
        }.padding().background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
    }

    private func results(_ game: ClassicGame, winner: RoleTeam) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            InformationCard(title: "Victoria de \(winner.rawValue)", message: winner == .town ? "La mesa eliminó al Asesino." : "Los Traidores alcanzaron la paridad con el Pueblo.")
            ForEach(game.players) { player in
                HStack {
                    Text(player.name + (player.id == 0 ? " (vos)" : ""))
                    Spacer()
                    Text(player.role.classicTitle).foregroundStyle(TraidoresTheme.gold)
                }.padding().background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
            }
            Button("VOLVER A JUGAR") { dismiss() }.buttonStyle(TraidoresButtonStyle(prominent: true))
            history(game)
        }
    }

    private func instructions(_ game: ClassicGame) -> String {
        if !game.human.alive { return "Fuiste eliminado. Podés seguir la partida como espectador; las identidades se revelan al final." }
        switch game.phase {
        case .assignment: return "Leé tu carta antes de comenzar. Tu identidad permanece oculta para la mesa."
        case .assassinNight: return "Elegí a quién atacar esta noche y confirmá tu decisión."
        case .detectiveNight: return "Elegí a quién investigar. Solo vos recibirás el resultado."
        case .medicNight: return "Elegí a quién proteger. También podés protegerte a vos."
        case .dawn: return "La noche terminó. Continuá para descubrir qué ocurrió."
        case .discussion: return game.messages.last(where: { $0.speaker == nil })?.text ?? "Escuchá las sospechas de la mesa."
        case .voting: return "Elegí a quién expulsar. Tu voto se confirma con el botón de abajo."
        case .tieVote: return "Votá entre los empatados. Si el empate se repite, nadie será expulsado."
        case .voteCount: return "Cada jugador vivo tiene un voto. Revisá quién votó por quién."
        case .result: return game.messages.last?.text ?? "La mesa decidió."
        default: return ""
        }
    }

    private func actionTitle(_ game: ClassicGame) -> String {
        if !game.legalTargets(for: 0).isEmpty { return game.isNight ? "CONFIRMAR ACCIÓN" : "CONFIRMAR VOTO" }
        switch game.phase {
        case .assignment: return "COMENZAR LA NOCHE"
        case .discussion: return "IR A VOTAR"
        case .dawn: return "VER AMANECER"
        case .result: return "RESOLVER LA JORNADA"
        default: return "CONTINUAR"
        }
    }
}

private extension RoleKey {
    var classicTitle: String {
        switch self {
        case .detective: "Comisario"
        case .assassin: "Asesino"
        case .medic: "Médico"
        default: "Aldeano"
        }
    }
    var classicImage: String {
        switch self {
        case .detective: "rol_detective_gaucho"
        case .assassin: "rol_asesino_gaucho"
        case .medic: "rol_medico_gaucho"
        default: "rol_aldeano_gaucho"
        }
    }
}

private extension GamePhase {
    var classicTitle: String {
        switch self {
        case .assignment: "Tu identidad"
        case .assassinNight, .detectiveNight, .medicNight: "La noche"
        case .dawn: "Amanece en la Pampa"
        case .discussion: "El debate"
        case .voting: "La votación"
        case .tieVote: "Desempate"
        case .voteCount: "Recuento de votos"
        case .result: "La decisión del pueblo"
        default: "Pampa"
        }
    }
}
