import SwiftUI
import TraidoresCore

struct LocalModeView: View {
    var body: some View {
        MenuPage(title: "Jugar vs IA") {
            Text("ELEGÍ UNA DIFICULTAD")
                .font(TraidoresTheme.title(26))
                .foregroundStyle(TraidoresTheme.gold)
            Text("Después irás al lobby para revisar el mapa y los participantes.")
                .foregroundStyle(TraidoresTheme.secondary)
            difficultyLink(.normal, title: "NORMAL",
                           detail: "Una mesa equilibrada para aprender el ritmo de la partida.")
            difficultyLink(.hard, title: "DIFÍCIL",
                           detail: "Los Traidores coordinan mejor sus decisiones y sus votos.")
        }
    }

    private func difficultyLink(_ difficulty: BotDifficulty, title: String, detail: String) -> some View {
        NavigationLink {
            LocalLobbyView(difficulty: difficulty)
        } label: {
            VStack(alignment: .leading, spacing: 7) {
                Text(title).font(TraidoresTheme.title(23)).foregroundStyle(TraidoresTheme.gold)
                Text(detail).font(.subheadline).foregroundStyle(TraidoresTheme.text)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
        }
        .buttonStyle(.plain)
    }
}

struct LocalLobbyView: View {
    let difficulty: BotDifficulty

    @State private var store = LocalGameStore()
    @AppStorage("local.playerName") private var name = ""
    @State private var playing = false
    @State private var replaceGame = false

    private let botNames = ["Thiago", "Mora", "Lautaro", "Valen"]

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                Image("mapa_pampa_vertical_dia")
                    .resizable().scaledToFill()
                    .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                    .overlay(.black.opacity(0.54))
            }
            .ignoresSafeArea().accessibilityHidden(true)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    lobbyHeader
                    startPanel
                    mapCard
                    playersPanel
                }
                .padding(16)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .navigationTitle("Lobby local")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(TraidoresTheme.ink, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .confirmationDialog("¿Reemplazar la partida guardada?", isPresented: $replaceGame,
                            titleVisibility: .visible) {
            Button("Comenzar otra partida", role: .destructive) { start() }
        }
        .fullScreenCover(isPresented: $playing) { LocalMatchFlow(store: store) }
    }

    private var lobbyHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("MODO LOCAL").font(TraidoresTheme.title(24)).foregroundStyle(TraidoresTheme.gold)
                Text("5/5 jugadores · IA \(difficulty.rawValue.lowercased())")
                    .font(.subheadline).foregroundStyle(TraidoresTheme.secondary)
            }
            Spacer()
            Image(systemName: "gearshape.fill")
                .foregroundStyle(TraidoresTheme.gold)
                .frame(width: 44, height: 44)
                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                .accessibilityHidden(true)
        }
        .padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
    }

    private var startPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("INICIAR PARTIDA") {
                if let game = store.game, game.winner == nil { replaceGame = true } else { start() }
            }
            .buttonStyle(TraidoresButtonStyle(prominent: true))

            Text(difficulty == .hard
                 ? "Modo difícil: la IA traidora coordina mejor sus votos."
                 : "Modo normal: una partida clásica para conocer la mesa.")
                .font(.subheadline).foregroundStyle(TraidoresTheme.gold)
                .frame(maxWidth: .infinity, alignment: .center)

            TextField("Tu nombre", text: $name)
                .textFieldStyle(.roundedBorder).autocorrectionDisabled()
                .onChange(of: name) { _, value in name = String(value.prefix(18)) }
                .accessibilityLabel("Tu nombre en la partida")

            if let game = store.game, game.winner == nil {
                Button("CONTINUAR PARTIDA · DÍA \(game.round)") { playing = true }
                    .buttonStyle(TraidoresButtonStyle())
            }
        }
        .padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
    }

    private var mapCard: some View {
        ZStack(alignment: .bottomLeading) {
            Image("mapa_pampa_vertical_dia")
                .resizable().scaledToFill().frame(height: 138).clipped().overlay(.black.opacity(0.42))
            VStack(alignment: .leading, spacing: 3) {
                Text("PAMPA").font(TraidoresTheme.title(26)).foregroundStyle(TraidoresTheme.gold)
                Text("Partida clásica · 1 Asesino, 1 Comisario, 1 Médico y 2 Aldeanos")
                    .font(.caption.weight(.semibold))
            }
            .padding(14)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
    }

    private var playersPanel: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("JUGADORES").font(.caption.weight(.bold)).tracking(1.2)
                .foregroundStyle(TraidoresTheme.secondary)
            playerRow(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Vos" : name,
                      human: true)
            ForEach(botNames, id: \.self) { playerRow($0, human: false) }
        }
        .padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
    }

    private func playerRow(_ playerName: String, human: Bool) -> some View {
        HStack(spacing: 11) {
            Circle().fill(human ? TraidoresTheme.gold : TraidoresTheme.border)
                .frame(width: 34, height: 34)
                .overlay(Text(String(playerName.prefix(1)).uppercased())
                    .font(.headline).foregroundStyle(TraidoresTheme.ink))
            VStack(alignment: .leading, spacing: 1) {
                Text(playerName).font(.headline)
                Text(human ? "Jugador local" : "Bot").font(.caption)
                    .foregroundStyle(TraidoresTheme.secondary)
            }
            Spacer()
            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green.opacity(0.8))
        }
        .padding(.vertical, 4)
    }

    private func start() {
        store.start(name: name, difficulty: difficulty)
        playing = true
    }
}

private struct LocalMatchFlow: View {
    @Bindable var store: LocalGameStore
    @Environment(\.dismiss) private var dismiss
    @Environment(MenuPreferences.self) private var preferences
    @State private var assignmentFinished = false

    init(store: LocalGameStore) {
        self.store = store
        _assignmentFinished = State(initialValue: store.game?.phase != .assignment)
    }

    var body: some View {
        Group {
            if assignmentFinished {
                LocalTableView(store: store, dismissMatch: dismiss)
            } else {
                LocalRoleAssignmentView(store: store) {
                    guard let game = store.game else { return }
                    store.advance(target: nil, revision: game.phaseIndex)
                    assignmentFinished = true
                } onExit: {
                    dismiss()
                }
            }
        }
        .onAppear { preferences.gameplayActive = true }
        .onDisappear { preferences.gameplayActive = false }
    }
}

private struct LocalRoleAssignmentView: View {
    @Bindable var store: LocalGameStore
    let onStart: () -> Void
    let onExit: () -> Void

    @State private var showingRole = false
    @State private var cardScale = 0.78
    @State private var cardRotation = -7.0

    var body: some View {
        ZStack {
            Image("mapa_pampa_vertical_noche").resizable().scaledToFill().ignoresSafeArea()
                .overlay(.black.opacity(0.56))

            VStack(spacing: 24) {
                Spacer()
                Text(showingRole ? "TU ROL SECRETO" : "REPARTIENDO ROLES")
                    .font(.caption.weight(.bold)).tracking(2)
                    .foregroundStyle(TraidoresTheme.secondary)
                if let game = store.game, showingRole {
                    roleCard(game.human.role)
                        .transition(.asymmetric(insertion: .scale.combined(with: .opacity), removal: .opacity))
                } else {
                    cardBack.scaleEffect(cardScale).rotationEffect(.degrees(cardRotation))
                }
                Spacer()
                if showingRole {
                    Button("EMPEZAR") { onStart() }
                        .buttonStyle(TraidoresButtonStyle(prominent: true))
                } else {
                    Text("Preparando tu carta...").foregroundStyle(TraidoresTheme.secondary)
                }
            }
            .padding(24).frame(maxWidth: 520)

            Button(action: onExit) {
                Image(systemName: "chevron.left").font(.headline).frame(width: 46, height: 46)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .foregroundStyle(TraidoresTheme.text).padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .task {
            withAnimation(.easeInOut(duration: 0.55).repeatCount(2, autoreverses: true)) {
                cardScale = 0.93
                cardRotation = 7
            }
            try? await Task.sleep(for: .seconds(1.35))
            withAnimation(.spring(response: 0.5, dampingFraction: 0.78)) { showingRole = true }
        }
    }

    private var cardBack: some View {
        RoundedRectangle(cornerRadius: 18)
            .fill(LinearGradient(colors: [.black, TraidoresTheme.panel, .black],
                                 startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: 190, height: 300)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(TraidoresTheme.gold, lineWidth: 3))
            .overlay { Image("logo_traidores_clean").resizable().scaledToFit().frame(width: 92, height: 120) }
            .shadow(color: .black.opacity(0.8), radius: 18, y: 12)
    }

    private func roleCard(_ role: RoleKey) -> some View {
        VStack(spacing: 13) {
            Image(role.classicImage).resizable().scaledToFit().frame(maxHeight: 245)
                .accessibilityHidden(true)
            Text(role.classicTitle).font(TraidoresTheme.title(32)).foregroundStyle(TraidoresTheme.gold)
            Text(role == .assassin ? "TRAIDORES" : "PUEBLO").font(.headline)
                .foregroundStyle(role == .assassin ? Color.red.opacity(0.78) : Color.green.opacity(0.78))
            Text(RoleCatalog.all.first { $0.id == role }?.instructions ?? "")
                .font(.subheadline).multilineTextAlignment(.center)
        }
        .padding(20).frame(maxWidth: 360).foregroundStyle(TraidoresTheme.text)
        .background(TraidoresTheme.panel.opacity(0.98), in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TraidoresTheme.gold, lineWidth: 2))
    }
}

private struct LocalTableView: View {
    @Bindable var store: LocalGameStore
    let dismissMatch: DismissAction

    @Environment(\.scenePhase) private var scenePhase
    @State private var selected: Int?
    @State private var showingRole = false
    @State private var leaving = false

    var body: some View {
        if let game = store.game {
            ZStack {
                tableBackground(game)
                VStack(spacing: 12) {
                    tableHeader(game)
                    ScrollView {
                        VStack(spacing: 14) {
                            playerGrid(game)
                            phasePanel(game)
                            if game.phase == .discussion { debatePanel(game) }
                            if game.phase == .voteCount { votePanel(game) }
                            if let winner = game.winner { resultPanel(game, winner: winner) }
                        }
                        .padding(.horizontal, 14).padding(.bottom, 10)
                    }
                    if game.winner == nil { actionDock(game) }
                }
                .padding(.top, 8)

                if scenePhase != .active {
                    TraidoresTheme.ink.ignoresSafeArea()
                    Text("TRAIDORES").font(TraidoresTheme.title(36)).foregroundStyle(TraidoresTheme.gold)
                }
            }
            .foregroundStyle(TraidoresTheme.text)
            .onChange(of: game.phaseIndex) { _, _ in selected = nil }
            .confirmationDialog("La partida queda guardada para continuar después.",
                                isPresented: $leaving, titleVisibility: .visible) {
                Button("Volver al menú") { dismissMatch() }
            }
            .sheet(isPresented: $showingRole) { roleSheet(game.human.role) }
        }
    }

    private func tableBackground(_ game: ClassicGame) -> some View {
        GeometryReader { geometry in
            Image(game.isNight ? "mapa_pampa_vertical_noche" : "mapa_pampa_vertical_dia")
                .resizable().scaledToFill()
                .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                .overlay(.black.opacity(0.62))
        }
        .ignoresSafeArea().accessibilityHidden(true)
    }

    private func tableHeader(_ game: ClassicGame) -> some View {
        HStack(spacing: 10) {
            Button { leaving = true } label: {
                Image(systemName: "chevron.left").frame(width: 42, height: 42)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(game.phase.classicTitle.uppercased())
                    .font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                Text("Ronda \(game.round) · Pampa").font(.caption).foregroundStyle(TraidoresTheme.secondary)
            }
            Spacer()
            Button { showingRole = true } label: {
                Image(systemName: "person.text.rectangle").frame(width: 42, height: 42)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .accessibilityLabel("Ver mi rol")
        }
        .padding(.horizontal, 14)
    }

    private func playerGrid(_ game: ClassicGame) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 9) {
            ForEach(game.players) { player in
                let actionable = game.legalTargets(for: 0).contains(player.id)
                Button {
                    if actionable { selected = player.id }
                } label: {
                    VStack(spacing: 6) {
                        ZStack {
                            Circle().fill(player.alive ? TraidoresTheme.border : Color.black.opacity(0.7))
                                .frame(width: 48, height: 48)
                            Text(String(player.name.prefix(1)).uppercased()).font(TraidoresTheme.title(22))
                                .foregroundStyle(player.alive ? TraidoresTheme.gold : TraidoresTheme.secondary)
                            if !player.alive { Image(systemName: "xmark").font(.title2.bold()) }
                        }
                        Text(player.name + (player.id == 0 ? " · VOS" : ""))
                            .font(.caption.weight(.bold)).lineLimit(1)
                        Text(player.alive ? "EN LA MESA" : "ELIMINADO").font(.caption2)
                            .foregroundStyle(TraidoresTheme.secondary)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(selected == player.id ? TraidoresTheme.border.opacity(0.95)
                                : TraidoresTheme.panel.opacity(0.94), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(actionable ? TraidoresTheme.gold.opacity(0.8) : TraidoresTheme.border.opacity(0.6)))
                }
                .buttonStyle(.plain).disabled(!actionable)
            }
        }
    }

    private func phasePanel(_ game: ClassicGame) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(game.phase.classicTitle).font(TraidoresTheme.title(23)).foregroundStyle(TraidoresTheme.gold)
            Text(instructions(game)).font(.subheadline)
            if !game.legalTargets(for: 0).isEmpty {
                Text(selected.map { "Objetivo: \(game.name($0))" } ?? "Elegí un jugador de la mesa")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(selected == nil ? TraidoresTheme.secondary : TraidoresTheme.gold)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
    }

    private func debatePanel(_ game: ClassicGame) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("LA MESA HABLA").font(.caption.weight(.bold)).tracking(1.2)
                .foregroundStyle(TraidoresTheme.secondary)
            ForEach(game.messages.filter { $0.round == game.round && $0.speaker != nil }.suffix(5)) { message in
                VStack(alignment: .leading, spacing: 2) {
                    Text(game.name(message.speaker ?? 0)).font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                    Text(message.text).font(.subheadline)
                }
                .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 9))
            }
            if game.human.alive && !game.humanSpoke {
                Menu("SEÑALAR UNA SOSPECHA") {
                    ForEach(game.living.filter { $0.id != 0 }) { player in
                        Button(player.name) { store.accuse(player.id, revision: game.phaseIndex) }
                    }
                }
                .buttonStyle(TraidoresButtonStyle())
            }
            if game.human.alive && !game.humanInvestigations.isEmpty && !game.humanSharedRead {
                Button("COMPARTIR INVESTIGACIÓN") { store.shareRead(revision: game.phaseIndex) }
                    .buttonStyle(TraidoresButtonStyle())
            }
        }
        .padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 12))
    }

    private func votePanel(_ game: ClassicGame) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("RECUENTO").font(.caption.weight(.bold)).tracking(1.2)
                .foregroundStyle(TraidoresTheme.secondary)
            ForEach(game.votes.keys.sorted(), id: \.self) { voter in
                Text("\(game.name(voter)) → \(game.name(game.votes[voter] ?? voter))").font(.subheadline)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 12))
    }

    private func resultPanel(_ game: ClassicGame, winner: RoleTeam) -> some View {
        VStack(spacing: 12) {
            Text("VICTORIA DE \(winner.rawValue.uppercased())")
                .font(TraidoresTheme.title(28)).foregroundStyle(TraidoresTheme.gold)
            ForEach(game.players) { player in
                HStack {
                    Text(player.name + (player.id == 0 ? " · VOS" : ""))
                    Spacer()
                    Text(player.role.classicTitle).foregroundStyle(TraidoresTheme.gold)
                }
            }
            Button("VOLVER AL LOBBY") { dismissMatch() }
                .buttonStyle(TraidoresButtonStyle(prominent: true))
        }
        .padding(16)
        .background(TraidoresTheme.panel.opacity(0.98), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.gold))
    }

    private func actionDock(_ game: ClassicGame) -> some View {
        let targets = game.legalTargets(for: 0)
        return Button(actionTitle(game)) { store.advance(target: selected, revision: game.phaseIndex) }
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            .disabled(!targets.isEmpty && !targets.contains(selected ?? -1))
            .opacity(!targets.isEmpty && selected == nil ? 0.58 : 1)
            .padding(.horizontal, 14).padding(.bottom, 8)
            .background(TraidoresTheme.ink.opacity(0.94))
    }

    private func roleSheet(_ role: RoleKey) -> some View {
        ScrollView {
            VStack(spacing: 15) {
                Image(role.classicImage).resizable().scaledToFit().frame(maxHeight: 300)
                Text(role.classicTitle).font(TraidoresTheme.title(32)).foregroundStyle(TraidoresTheme.gold)
                Text(RoleCatalog.all.first { $0.id == role }?.instructions ?? "").multilineTextAlignment(.center)
                Button("CERRAR") { showingRole = false }.buttonStyle(TraidoresButtonStyle(prominent: true))
            }
            .padding(24)
        }
        .foregroundStyle(TraidoresTheme.text).background(TraidoresTheme.ink)
    }

    private func instructions(_ game: ClassicGame) -> String {
        if !game.human.alive { return "Fuiste eliminado. Podés observar la mesa hasta que termine la partida." }
        return switch game.phase {
        case .assassinNight: "Elegí a quién atacar esta noche."
        case .detectiveNight: "Elegí a quién investigar. El resultado será privado."
        case .medicNight: "Elegí a quién proteger. También podés protegerte."
        case .dawn: "La noche terminó. Continuá para conocer lo ocurrido."
        case .discussion: "Escuchá a la mesa, compartí información o marcá una sospecha."
        case .voting: "Elegí a quién expulsar. No podés votarte a vos mismo."
        case .tieVote: "Votá entre los jugadores empatados."
        case .voteCount: "Revisá cómo votó cada participante."
        case .result: game.messages.last?.text ?? "La mesa tomó una decisión."
        default: "La partida continúa."
        }
    }

    private func actionTitle(_ game: ClassicGame) -> String {
        if !game.legalTargets(for: 0).isEmpty { return game.isNight ? "CONFIRMAR ACCIÓN" : "CONFIRMAR VOTO" }
        return switch game.phase {
        case .discussion: "IR A VOTAR"
        case .dawn: "VER AMANECER"
        case .voteCount: "CONTINUAR"
        case .result: "RESOLVER LA JORNADA"
        default: "CONTINUAR"
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
        case .discussion: "Debate del pueblo"
        case .voting: "Votación"
        case .tieVote: "Desempate"
        case .voteCount: "Recuento de votos"
        case .result: "Resultado"
        default: "Pampa"
        }
    }
}
