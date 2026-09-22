import Foundation
import SwiftUI
import TraidoresCore

struct LocalModeView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            MenuBackground()
            VStack(spacing: 0) {
                Spacer()
                VStack(spacing: 0) {
                    Text("JUGAR vs IA")
                        .font(TraidoresTheme.title(24))
                        .foregroundStyle(TraidoresTheme.gold)
                        .padding(.bottom, 10)
                    Text("Elige una dificultad. Después irás al lobby para ajustar mapa, tiempos y cantidad de jugadores.")
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(TraidoresTheme.secondary)
                        .lineSpacing(3)
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 10))
                        .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
                        .padding(.bottom, 28)
                    difficultyLink(.normal, title: "NORMAL", prominent: true)
                    difficultyLink(.hard, title: "DIFICIL", prominent: false)
                }
                .padding(.horizontal, 32)
                .frame(maxWidth: 560)
                Spacer()
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

    private func difficultyLink(_ difficulty: BotDifficulty, title: String, prominent: Bool) -> some View {
        NavigationLink {
            LocalLobbyView(difficulty: difficulty)
        } label: {
            Text(title)
        }
        .buttonStyle(TraidoresButtonStyle(prominent: prominent))
        .accessibilityIdentifier(difficulty == .normal ? "difficulty.normal" : "difficulty.hard")
    }
}

@MainActor private enum LocalLobbyPreferences {
    static let store: UserDefaults = {
        guard ProcessInfo.processInfo.arguments.contains("-ui-testing") else { return .standard }
        return UserDefaults(suiteName: "com.traidores.juego.ios.ui-testing") ?? .standard
    }()
}

struct LocalLobbyView: View {
    let difficulty: BotDifficulty

    @Environment(\.dismiss) private var dismiss
    @State private var store: LocalGameStore
    @AppStorage("local.botNames", store: LocalLobbyPreferences.store) private var savedBotNames = ""
    @AppStorage("local.timing", store: LocalLobbyPreferences.store) private var savedTiming = ""
    @AppStorage("local.advanced", store: LocalLobbyPreferences.store) private var savedAdvanced = ""
    @State private var playing = false
    @State private var showingTiming = false
    @State private var showingAdvanced = false
    @State private var botNames = Array(ClassicGame.defaultBotNames.prefix(4))
    @State private var timing = GameTimingConfig.normal
    @State private var advanced = AdvancedGameConfig.standard
    @State private var editingBot: Int?
    @State private var editedName = ""

    init(difficulty: BotDifficulty) {
        self.difficulty = difficulty
        if ProcessInfo.processInfo.arguments.contains("-ui-testing") {
            let suiteName = "com.traidores.juego.ios.ui-testing"
            let defaults = UserDefaults(suiteName: suiteName) ?? .standard
            defaults.removePersistentDomain(forName: suiteName)
            _store = State(initialValue: LocalGameStore(defaults: defaults))
        } else {
            _store = State(initialValue: LocalGameStore())
        }
    }

    var body: some View {
        ZStack {
            if playing {
                LocalMatchFlow(store: store, onExit: { playing = false })
                    .transition(.opacity)
            } else {
                GeometryReader { geometry in
                    Image("mapa_pampa_vertical_dia")
                        .resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                        .overlay(.black.opacity(0.54))
                }
                .ignoresSafeArea().accessibilityHidden(true)

                VStack(spacing: 0) {
                    lobbyHeader
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                        .padding(.bottom, 10)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            configurationPanel
                            playersPanel
                        }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 16)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
        .alert("Editar nombre", isPresented: editAlertBinding) {
            TextField("Nombre del bot", text: $editedName)
            Button("Cancelar", role: .cancel) { editingBot = nil }
            Button("Guardar") { saveBotName() }
        } message: {
            Text("Usá hasta 18 caracteres.")
        }
        .onAppear(perform: restoreBotNames)
        .onChange(of: botNames) { _, names in
            guard let data = try? JSONEncoder().encode(names) else { return }
            savedBotNames = String(decoding: data, as: UTF8.self)
        }
        .onChange(of: timing) { _, value in saveTiming(value) }
        .onChange(of: advanced) { _, value in saveAdvanced(value) }
        .sheet(isPresented: $showingTiming) {
            TimingOptionsView(timing: $timing)
        }
        .sheet(isPresented: $showingAdvanced) {
            AdvancedOptionsView(config: $advanced)
        }
    }

    private var editAlertBinding: Binding<Bool> {
        Binding(get: { editingBot != nil }, set: { if !$0 { editingBot = nil } })
    }

    private var lobbyHeader: some View {
        HStack(spacing: 10) {
            Button(action: dismiss.callAsFunction) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .frame(width: 46, height: 46)
                    .background(TraidoresTheme.panel.opacity(0.97), in: Circle())
                    .overlay { Circle().stroke(TraidoresTheme.border) }
            }
            .foregroundStyle(TraidoresTheme.text)
            .accessibilityLabel("Volver")

            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("MODO LOCAL")
                        .font(TraidoresTheme.title(21))
                        .foregroundStyle(TraidoresTheme.gold)
                        .lineLimit(1)
                    Text("\(botNames.count + 1)/\(ClassicGame.maximumPlayers) jugadores · IA \(difficulty.rawValue.lowercased())")
                        .font(.caption)
                        .foregroundStyle(TraidoresTheme.secondary)
                        .lineLimit(1)
                        .accessibilityIdentifier("lobby.playerCount")
                }
                Spacer(minLength: 4)
                Button { showingAdvanced = true } label: {
                    Image(systemName: "gearshape.fill")
                        .font(.headline)
                        .frame(width: 44, height: 44)
                        .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 9))
                }
                .foregroundStyle(TraidoresTheme.gold)
                .accessibilityLabel("Opciones avanzadas")
            }
            .padding(.leading, 13)
            .padding(.trailing, 7)
            .padding(.vertical, 7)
            .background(TraidoresTheme.panel.opacity(0.97), in: RoundedRectangle(cornerRadius: 12))
            .overlay { RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border) }
        }
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
    }

    private var configurationPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button(action: start) {
                Text("INICIAR PARTIDA")
                    .contentShape(Rectangle())
            }
            .buttonStyle(TraidoresButtonStyle(prominent: true))
            .contentShape(Rectangle())
            .accessibilityIdentifier("local.startGame")

            Text(difficulty == .hard
                 ? "Modo difícil: la IA traidora coordina mejor sus votos."
                 : "Modo normal: elegí mapa, tiempos y participantes antes de iniciar.")
                .font(.caption).foregroundStyle(TraidoresTheme.gold)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)

            if let game = store.game, game.winner == nil {
                Button("CONTINUAR PARTIDA · DÍA \(game.round)") { playing = true }
                    .buttonStyle(TraidoresButtonStyle())
            }

            sectionLabel("CONFIGURACIÓN")
            mapCard
            Text("Pampa clásica · 1 Asesino, 1 Comisario, 1 Médico y \(botNames.count - 2) Aldeanos.")
                .font(.caption)
                .foregroundStyle(TraidoresTheme.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)
            optionsRow
            sectionLabel("EN LA SALA")
            playerControls
        }
        .padding(12)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    private var mapCard: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black.opacity(0.42)
                .allowsHitTesting(false)
            VStack(alignment: .leading, spacing: 3) {
                Text("PAMPA").font(TraidoresTheme.title(26)).foregroundStyle(TraidoresTheme.gold)
                Text("MAPA CLÁSICO")
                    .font(.caption.weight(.semibold))
            }
            .padding(14)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 126)
        .background {
            Image("mapa_pampa_vertical_dia")
                .resizable()
                .scaledToFill()
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    private var playerControls: some View {
        HStack(spacing: 8) {
            Button {
                guard botNames.count + 1 < ClassicGame.maximumPlayers else { return }
                botNames.append(ClassicGame.defaultBotNames[botNames.count])
            } label: {
                Label("AGREGAR", systemImage: "person.badge.plus")
            }
            .buttonStyle(TraidoresButtonStyle())
            .disabled(botNames.count + 1 >= ClassicGame.maximumPlayers)
            .accessibilityIdentifier("lobby.addPlayer")

            Button {
                guard botNames.count + 1 > ClassicGame.minimumPlayers else { return }
                botNames.removeLast()
            } label: {
                Label("QUITAR", systemImage: "person.badge.minus")
            }
            .buttonStyle(TraidoresButtonStyle())
            .disabled(botNames.count + 1 <= ClassicGame.minimumPlayers)
            .accessibilityIdentifier("lobby.removePlayer")
        }
    }

    private var optionsRow: some View {
        HStack(spacing: 8) {
            compactOptionButton("OPCIONES DE PARTIDA", systemImage: "timer") {
                showingTiming = true
            }
            compactOptionButton("OPCIONES AVANZADAS", systemImage: "slider.horizontal.3") {
                showingAdvanced = true
            }
        }
    }

    private func compactOptionButton(_ title: String, systemImage: String,
                                     action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .foregroundStyle(TraidoresTheme.gold)
                Text(title)
                    .font(.caption2.bold())
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.horizontal, 6)
            .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 8))
            .overlay { RoundedRectangle(cornerRadius: 8).stroke(TraidoresTheme.border) }
        }
        .buttonStyle(.plain)
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(.caption2.bold())
            .tracking(1.2)
            .foregroundStyle(TraidoresTheme.secondary)
            .padding(.top, 2)
    }

    private var playersPanel: some View {
        VStack(spacing: 8) {
            playerRow("Vos", human: true)
            ForEach(Array(botNames.enumerated()), id: \.offset) { index, botName in
                HStack(spacing: 10) {
                    Circle()
                        .fill(TraidoresTheme.border)
                        .frame(width: 34, height: 34)
                        .overlay {
                            Text(String(botName.prefix(1)).uppercased())
                                .font(.headline)
                                .foregroundStyle(TraidoresTheme.ink)
                        }
                    Button {
                        editingBot = index
                        editedName = botName
                    } label: {
                        HStack(spacing: 5) {
                            Text(botName)
                                .font(.headline)
                                .lineLimit(1)
                            Image(systemName: "pencil")
                                .font(.caption)
                                .foregroundStyle(TraidoresTheme.gold)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    Text("BOT")
                        .font(.caption2.bold())
                        .foregroundStyle(TraidoresTheme.secondary)
                    Button {
                        guard botNames.count + 1 > ClassicGame.minimumPlayers else { return }
                        botNames.remove(at: index)
                    } label: {
                        Image(systemName: "person.fill.xmark")
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.red.opacity(0.85))
                    .disabled(botNames.count + 1 <= ClassicGame.minimumPlayers)
                    .accessibilityLabel("Quitar a \(botName)")
                }
                .padding(.horizontal, 10)
                .frame(minHeight: 56)
                .background(TraidoresTheme.ink.opacity(0.72), in: RoundedRectangle(cornerRadius: 10))
            }
            Text("Tocá el nombre de un bot para editarlo.")
                .font(.caption2)
                .foregroundStyle(TraidoresTheme.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .padding(10)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    private func playerRow(_ playerName: String, human: Bool) -> some View {
        HStack(spacing: 11) {
            Circle().fill(human ? TraidoresTheme.gold : TraidoresTheme.border)
                .frame(width: 34, height: 34)
                .overlay(Text(String(playerName.prefix(1)).uppercased())
                    .font(.headline).foregroundStyle(TraidoresTheme.ink))
            VStack(alignment: .leading, spacing: 1) {
                Text(playerName).font(.headline)
                Text(human ? "ANFITRIÓN" : "BOT").font(.caption2.bold())
                    .foregroundStyle(TraidoresTheme.secondary)
            }
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green.opacity(0.8))
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 56)
        .background(TraidoresTheme.ink.opacity(0.72), in: RoundedRectangle(cornerRadius: 10))
    }

    private func start() {
        let arguments = ProcessInfo.processInfo.arguments
        let trainingRole: RoleKey? = if arguments.contains("-ui-testing-medic") {
            .medic
        } else if arguments.contains("-ui-testing-detective") {
            .detective
        } else {
            nil
        }
        store.start(name: "Vos", difficulty: difficulty, botNames: botNames,
                    timing: timing, advanced: advanced, trainingRole: trainingRole)
        playing = store.game != nil
    }

    private func saveBotName() {
        guard let editingBot else { return }
        let clean = String(editedName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(18))
        if !clean.isEmpty { botNames[editingBot] = clean }
        self.editingBot = nil
    }

    private func restoreBotNames() {
        guard let data = savedBotNames.data(using: .utf8),
              let names = try? JSONDecoder().decode([String].self, from: data),
              (ClassicGame.minimumPlayers - 1...ClassicGame.maximumPlayers - 1).contains(names.count)
        else {
            restoreTiming()
            return
        }
        botNames = names
        restoreTiming()
    }

    private func restoreTiming() {
        guard let data = savedTiming.data(using: .utf8),
              let value = try? JSONDecoder().decode(GameTimingConfig.self, from: data)
        else {
            restoreAdvanced()
            return
        }
        timing = value.normalized
        restoreAdvanced()
    }

    private func saveTiming(_ value: GameTimingConfig) {
        guard let data = try? JSONEncoder().encode(value.normalized) else { return }
        savedTiming = String(decoding: data, as: UTF8.self)
    }

    private func restoreAdvanced() {
        guard let data = savedAdvanced.data(using: .utf8),
              let value = try? JSONDecoder().decode(AdvancedGameConfig.self, from: data)
        else { return }
        advanced = value.normalized
    }

    private func saveAdvanced(_ value: AdvancedGameConfig) {
        guard let data = try? JSONEncoder().encode(value.normalized) else { return }
        savedAdvanced = String(decoding: data, as: UTF8.self)
    }
}

private struct AdvancedOptionsView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding private var config: AdvancedGameConfig
    @State private var draft: AdvancedGameConfig

    init(config: Binding<AdvancedGameConfig>) {
        _config = config
        _draft = State(initialValue: config.wrappedValue)
    }

    var body: some View {
        ZStack {
            MenuBackground()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("OPCIONES AVANZADAS")
                        .font(TraidoresTheme.title(25)).foregroundStyle(TraidoresTheme.gold)
                        .frame(maxWidth: .infinity, alignment: .center)
                    Text("REGLAS DE LA PARTIDA").font(.caption.bold()).tracking(1.2)
                        .foregroundStyle(TraidoresTheme.secondary)
                    optionToggle(title: "Mostrar roles al morir o al expulsar",
                                 detail: "Si se desactiva, las cartas eliminadas permanecen ocultas.",
                                 value: $draft.revealRolesOnDeath)
                    optionToggle(title: "Mostrar votos individuales",
                                 detail: "Si se desactiva, solamente se muestra el total recibido.",
                                 value: $draft.showIndividualVotes)
                    Text("LECTURA INICIAL DEL ROL").font(.caption.bold()).tracking(1.2)
                        .foregroundStyle(TraidoresTheme.secondary)
                    Text("Define cuándo aparece EMPEZAR después de recibir la carta.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    HStack(spacing: 8) {
                        readingButton("INMEDIATO", seconds: 0)
                        readingButton("6 S", seconds: 6)
                        readingButton("10 S", seconds: 10)
                    }
                    Button("APLICAR") {
                        config = draft.normalized
                        dismiss()
                    }
                    .buttonStyle(TraidoresButtonStyle(prominent: true))
                    Button("RESTABLECER") { draft = .standard }
                        .buttonStyle(TraidoresButtonStyle())
                    Button("CANCELAR") { dismiss() }
                        .foregroundStyle(TraidoresTheme.secondary)
                        .frame(maxWidth: .infinity)
                }
                .padding(20).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .presentationDetents([.large])
    }

    private func optionToggle(title: String, detail: String, value: Binding<Bool>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Toggle(title, isOn: value).font(.headline).tint(TraidoresTheme.gold)
            Text(detail).font(.footnote).foregroundStyle(TraidoresTheme.secondary)
        }
        .padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border))
    }

    private func readingButton(_ title: String, seconds: Int) -> some View {
        Button(title) { draft.roleReadingSeconds = seconds }
            .font(.caption.bold())
            .foregroundStyle(draft.roleReadingSeconds == seconds ? TraidoresTheme.ink : TraidoresTheme.text)
            .frame(maxWidth: .infinity, minHeight: 42)
            .background(draft.roleReadingSeconds == seconds ? TraidoresTheme.gold : TraidoresTheme.panel,
                        in: RoundedRectangle(cornerRadius: 9))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(TraidoresTheme.border))
    }
}

private struct TimingOptionsView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding private var timing: GameTimingConfig
    @State private var draft: GameTimingConfig

    init(timing: Binding<GameTimingConfig>) {
        _timing = timing
        _draft = State(initialValue: timing.wrappedValue)
    }

    var body: some View {
        ZStack {
            MenuBackground()
            ScrollView {
                VStack(spacing: 18) {
                    Text("OPCIONES DE PARTIDA")
                        .font(TraidoresTheme.title(25)).foregroundStyle(TraidoresTheme.gold)
                    Text("Estos son los mismos tiempos y límites de la versión Android.")
                        .foregroundStyle(TraidoresTheme.secondary).multilineTextAlignment(.center)
                    presetButtons
                    VStack(spacing: 10) {
                        ForEach(TimingField.allCases) { field in timingRow(field) }
                    }
                    .padding(14)
                    .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border))
                    Button("APLICAR") {
                        timing = draft.normalized
                        dismiss()
                    }
                    .buttonStyle(TraidoresButtonStyle(prominent: true))
                    Button("RESTABLECER") { draft = .normal }
                        .buttonStyle(TraidoresButtonStyle())
                    Button("CANCELAR") { dismiss() }
                        .foregroundStyle(TraidoresTheme.secondary)
                }
                .padding(20).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .presentationDetents([.large])
    }

    private var presetButtons: some View {
        HStack(spacing: 8) {
            preset("LENTO", value: .slow)
            preset("NORMAL", value: .normal)
            preset("RÁPIDO", value: .fast)
        }
    }

    private func preset(_ title: String, value: GameTimingConfig) -> some View {
        Button(title) { draft = value }
            .font(.caption.bold())
            .foregroundStyle(draft == value ? TraidoresTheme.ink : TraidoresTheme.text)
            .frame(maxWidth: .infinity, minHeight: 42)
            .background(draft == value ? TraidoresTheme.gold : TraidoresTheme.panel,
                        in: RoundedRectangle(cornerRadius: 9))
            .overlay(RoundedRectangle(cornerRadius: 9).stroke(TraidoresTheme.border))
    }

    private func timingRow(_ field: TimingField) -> some View {
        HStack {
            Text(field.title).font(.subheadline.weight(.semibold))
            Spacer()
            timingButton("minus") { field.change(&draft, by: -field.step) }
                .disabled(field.value(in: draft) <= field.range.lowerBound)
            Text("\(field.value(in: draft)) s")
                .font(.subheadline.monospacedDigit()).foregroundStyle(TraidoresTheme.gold)
                .frame(width: 58)
            timingButton("plus") { field.change(&draft, by: field.step) }
                .disabled(field.value(in: draft) >= field.range.upperBound)
        }
    }

    private func timingButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).frame(width: 36, height: 34)
                .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 7))
                .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.border))
        }
        .foregroundStyle(TraidoresTheme.text)
    }
}

private enum TimingField: String, CaseIterable, Identifiable {
    case transition, night, discussion, voting
    var id: String { rawValue }
    var title: String {
        switch self {
        case .transition: "Transición"
        case .night: "Noche"
        case .discussion: "Debate"
        case .voting: "Votación"
        }
    }
    var range: ClosedRange<Int> {
        switch self {
        case .transition: 1...10
        case .night: 10...90
        case .discussion: 30...180
        case .voting: 10...60
        }
    }
    var step: Int {
        switch self {
        case .transition: 1
        case .night, .voting: 5
        case .discussion: 15
        }
    }
    func value(in config: GameTimingConfig) -> Int {
        switch self {
        case .transition: config.transitionSeconds
        case .night: config.nightSeconds
        case .discussion: config.discussionSeconds
        case .voting: config.votingSeconds
        }
    }
    func change(_ config: inout GameTimingConfig, by amount: Int) {
        let next = min(max(value(in: config) + amount, range.lowerBound), range.upperBound)
        switch self {
        case .transition: config.transitionSeconds = next
        case .night: config.nightSeconds = next
        case .discussion: config.discussionSeconds = next
        case .voting: config.votingSeconds = next
        }
    }
}

private struct LocalMatchFlow: View {
    @Bindable var store: LocalGameStore
    let onExit: () -> Void
    @Environment(MenuPreferences.self) private var preferences
    @State private var assignmentFinished = false

    init(store: LocalGameStore, onExit: @escaping () -> Void) {
        self.store = store
        self.onExit = onExit
        _assignmentFinished = State(initialValue: store.game?.phase != .assignment)
    }

    var body: some View {
        Group {
            if assignmentFinished {
                LocalTableView(store: store, dismissMatch: onExit)
            } else {
                LocalRoleAssignmentView(store: store) {
                    guard let game = store.game else { return }
                    store.advance(target: nil, revision: game.phaseIndex)
                    assignmentFinished = true
                } onExit: {
                    store.cancel()
                    onExit()
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

    @State private var stage = AssignmentStage.dealing
    @State private var tableOpacity = 0.0
    @State private var handsOpacity = 0.0
    @State private var handsOffset = 70.0
    @State private var cardOpacity = 0.0
    @State private var cardOffset = -120.0
    @State private var statusOpacity = 0.0
    @State private var remainingReading = 0
    @State private var showingExitConfirmation = false

    var body: some View {
        ZStack {
            Image("assigning_vertical_table").resizable().scaledToFill().ignoresSafeArea()
                .opacity(tableOpacity)
            Color.black.opacity(stage == .dealing ? 0.08 : 0.58).ignoresSafeArea()

            if stage == .dealing { dealingStage } else if let game = store.game { rolePreview(game.human.role) }

            Button { showingExitConfirmation = true } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 46, height: 46)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .foregroundStyle(TraidoresTheme.text).padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityIdentifier("assignment.back")
        }
        .alert("¿Salir de la partida?", isPresented: $showingExitConfirmation) {
            Button("SALIR", role: .destructive, action: onExit)
            Button("SEGUIR JUGANDO", role: .cancel) {}
        } message: {
            Text("Si salís ahora, se cancelará la partida y perderás su progreso.")
        }
        .task {
            withAnimation(.easeOut(duration: 0.42)) { tableOpacity = 1 }
            try? await Task.sleep(for: .seconds(0.42))
            withAnimation(.easeOut(duration: 0.48)) { handsOpacity = 1; handsOffset = 0 }
            try? await Task.sleep(for: .seconds(0.56))
            withAnimation(.spring(response: 0.62, dampingFraction: 0.72)) {
                cardOpacity = 1; cardOffset = 0
            }
            try? await Task.sleep(for: .seconds(0.9))
            withAnimation(.easeIn(duration: 0.35)) { statusOpacity = 1 }
            try? await Task.sleep(for: .seconds(1.35))
            withAnimation(.easeInOut(duration: 0.42)) { handsOpacity = 0; stage = .role }
            remainingReading = store.game?.advanced.roleReadingSeconds ?? 0
            while remainingReading > 0 {
                try? await Task.sleep(for: .seconds(1))
                remainingReading -= 1
            }
        }
    }

    private var dealingStage: some View {
        ZStack {
            Ellipse().fill(TraidoresTheme.gold.opacity(0.18)).blur(radius: 28)
                .frame(width: 230, height: 330).opacity(cardOpacity)
            Image("card_back_traidores").resizable().scaledToFit()
                .frame(width: 124, height: 196)
                .shadow(color: .black.opacity(0.85), radius: 14, y: 12)
                .offset(y: cardOffset).opacity(cardOpacity)
            Image("assigning_dealer_hands").resizable().scaledToFill().ignoresSafeArea()
                .offset(y: handsOffset).opacity(handsOpacity)
            Text("¡Buena suerte con tu rol!")
                .font(TraidoresTheme.title(22)).foregroundStyle(TraidoresTheme.gold)
                .padding(.horizontal, 22).padding(.vertical, 10)
                .background(TraidoresTheme.ink.opacity(0.9), in: Capsule())
                .overlay(Capsule().stroke(TraidoresTheme.border))
                .padding(.bottom, 18).opacity(statusOpacity)
                .frame(maxHeight: .infinity, alignment: .bottom)
                .accessibilityIdentifier("assignment.status")
        }
    }

    private func rolePreview(_ role: RoleKey) -> some View {
        VStack(spacing: 7) {
            Text("TU ROL").font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
            Text(role.classicTitle.uppercased()).font(TraidoresTheme.title(27)).foregroundStyle(TraidoresTheme.gold)
            Text(role == .assassin ? "TRAIDORES" : "PUEBLO")
                .font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
            Divider().overlay(TraidoresTheme.border)
            HStack(alignment: .top, spacing: 12) {
                Image(role.classicImage).resizable().scaledToFill()
                    .frame(width: 100, height: 150).clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 9))
                    .overlay(RoundedRectangle(cornerRadius: 9).stroke(TraidoresTheme.gold))
                VStack(alignment: .leading, spacing: 4) {
                    Text("QUÉ HACE").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                    Text(RoleCatalog.all.first { $0.id == role }?.instructions ?? "")
                        .font(.footnote).fixedSize(horizontal: false, vertical: true)
                    Text("CONSEJO").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                        .padding(.top, 4)
                    Text(role.classicAdvice).font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .layoutPriority(1)
            }
            if remainingReading == 0 {
                Button("EMPEZAR") { onStart() }
                    .buttonStyle(TraidoresButtonStyle(prominent: true)).frame(maxWidth: 190)
                    .accessibilityIdentifier("role.start")
            } else {
                Text("EMPEZAR (\(remainingReading))").font(.caption.bold())
                    .foregroundStyle(TraidoresTheme.secondary).frame(height: 48)
            }
        }
        .padding(14).frame(maxWidth: 390).foregroundStyle(TraidoresTheme.text)
        .background(TraidoresTheme.panel.opacity(0.97), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(TraidoresTheme.gold, lineWidth: 1.5))
        .padding(12).transition(.scale.combined(with: .opacity))
    }
}

private enum AssignmentStage { case dealing, role }

private struct PrivateActionFeedback: Equatable {
    let title: String
    let message: String
    let systemImage: String
}

private struct DayNightTransition: Equatable {
    enum Period { case day, night }

    let period: Period
    let round: Int

    var key: String { "\(period)-\(round)" }
    var title: String { "\(period == .night ? "NOCHE" : "DÍA") \(round)" }
    var artwork: String { period == .night ? "transition_moon" : "transition_sun" }
    var leavingArtwork: String { period == .night ? "transition_sun" : "transition_moon" }
    var background: String { period == .night ? "mapa_pampa_vertical_noche" : "mapa_pampa_vertical_dia" }
    var previousBackground: String {
        period == .night ? "mapa_pampa_vertical_dia" : "mapa_pampa_vertical_noche"
    }
}

private struct QuadraticTransitionMotion: AnimatableModifier {
    var progress: CGFloat
    let start: CGPoint
    let control: CGPoint
    let end: CGPoint

    nonisolated var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let remaining = 1 - progress
        let point = CGPoint(
            x: remaining * remaining * start.x
                + 2 * remaining * progress * control.x
                + progress * progress * end.x,
            y: remaining * remaining * start.y
                + 2 * remaining * progress * control.y
                + progress * progress * end.y
        )
        content.position(point)
    }
}

private struct DayNightTransitionView: View {
    let transition: DayNightTransition
    let duration: TimeInterval

    @State private var progress: CGFloat = 0
    @State private var revealBackground = false
    @State private var showTitle = false

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height
            let artworkSize = min(max(width * 0.40, 128), 190)
            let lowerY = height + artworkSize * 0.12
            let enteringStart = transition.period == .night
                ? CGPoint(x: -artworkSize, y: lowerY)
                : CGPoint(x: width + artworkSize * 0.15, y: lowerY)
            let enteringControl = transition.period == .night
                ? CGPoint(x: width * 0.05, y: height * 0.42)
                : CGPoint(x: width * 0.88, y: height * 0.42)
            let enteringEnd = CGPoint(
                x: width * (transition.period == .night ? 0.20 : 0.70),
                y: height * 0.14
            )
            let leavingStart = CGPoint(
                x: width * (transition.period == .night ? 0.70 : 0.20),
                y: height * 0.14
            )
            let leavingControl = CGPoint(
                x: width * (transition.period == .night ? 0.90 : 0.05),
                y: height * 0.48
            )
            let leavingEnd = transition.period == .night
                ? CGPoint(x: width + artworkSize * 0.15, y: lowerY)
                : CGPoint(x: -artworkSize, y: lowerY)

            ZStack {
                Image(transition.previousBackground)
                    .resizable().scaledToFill()
                    .frame(width: width, height: height).clipped()
                Image(transition.background)
                    .resizable().scaledToFill()
                    .frame(width: width, height: height).clipped()
                    .opacity(revealBackground ? 1 : 0)
                Color.black.opacity(transition.period == .night ? 0.48 : 0.26)

                Image(transition.leavingArtwork)
                    .resizable().scaledToFit()
                    .frame(width: artworkSize, height: artworkSize)
                    .shadow(color: .black.opacity(0.6), radius: 18, y: 8)
                    .modifier(QuadraticTransitionMotion(
                        progress: progress,
                        start: leavingStart,
                        control: leavingControl,
                        end: leavingEnd
                    ))
                    .opacity(1 - progress)

                Image(transition.artwork)
                    .resizable().scaledToFit()
                    .frame(width: artworkSize, height: artworkSize)
                    .shadow(color: .black.opacity(0.68), radius: 18, y: 8)
                    .modifier(QuadraticTransitionMotion(
                        progress: progress,
                        start: enteringStart,
                        control: enteringControl,
                        end: enteringEnd
                    ))
                    .opacity(progress)

                Text(transition.title)
                    .font(TraidoresTheme.title(38))
                    .tracking(2)
                    .foregroundStyle(TraidoresTheme.gold)
                    .shadow(color: .black, radius: 10, y: 4)
                    .scaleEffect(showTitle ? 1 : 0.86)
                    .opacity(showTitle ? 1 : 0)
            }
            .frame(width: width, height: height)
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(transition.title)
        .accessibilityIdentifier("table.dayNightTransition")
        .task {
            let movementDuration = max(duration * 0.82, 0.05)
            withAnimation(.easeInOut(duration: movementDuration)) { progress = 1 }
            withAnimation(.easeInOut(duration: max(duration * 0.66, 0.05))) {
                revealBackground = true
            }
            let titleDelay = UInt64(max(duration * 0.19, 0.01) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: titleDelay)
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: max(duration * 0.19, 0.05))) { showTitle = true }
            let titleHold = UInt64(max(duration * 0.48, 0.01) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: titleHold)
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: max(duration * 0.14, 0.05))) { showTitle = false }
        }
    }
}

private struct LocalTableView: View {
    @Bindable var store: LocalGameStore
    let dismissMatch: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var selected: Int?
    @State private var showingRole = false
    @State private var leaving = false
    @State private var privateFeedback: PrivateActionFeedback?
    @State private var pendingTransition: DayNightTransition?
    @State private var activeTransition: DayNightTransition?
    @State private var lastTransitionKey: String?
    @State private var transitionTask: Task<Void, Never>?

    var body: some View {
        if let game = store.game {
            ZStack {
                tableBackground(game)
                GeometryReader { geometry in
                    let availableSideWidth = min(
                        max(Int((geometry.size.width - 8 - 8 - 220) / 2), 54),
                        78
                    )
                    let availableSideHeight = max(Int(geometry.size.height) - 154, 1)
                    let androidMetrics = ClassicCompanionMetrics.androidCompatible(
                        totalPlayers: game.players.count,
                        availableHeight: availableSideHeight,
                        availableWidth: availableSideWidth
                    )
                    let metrics = game.players.count <= 9
                        ? androidMetrics.cappedCardWidth(60)
                        : androidMetrics
                    let sides = sidePlayers(game)

                    HStack(alignment: .top, spacing: 4) {
                        playerColumn(sides.left, game: game, metrics: metrics)
                        VStack(spacing: 4) {
                            tableHeader(game)
                            tableCenter(game)
                            if game.winner == nil { humanPanel(game) }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        playerColumn(sides.right, game: game, metrics: metrics)
                    }
                    .padding(.horizontal, 4).padding(.vertical, 8)
                }

                if showingRole {
                    roleOverlay(game.human.role)
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                        .zIndex(2)
                }

                if let privateFeedback {
                    privateFeedbackOverlay(privateFeedback)
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                        .zIndex(3)
                }

                if let activeTransition {
                    dayNightTransitionOverlay(activeTransition, game: game)
                        .transition(.opacity)
                        .zIndex(4)
                }

                if scenePhase != .active {
                    TraidoresTheme.ink.ignoresSafeArea()
                    Text("TRAIDORES").font(TraidoresTheme.title(36)).foregroundStyle(TraidoresTheme.gold)
                }
            }
            .foregroundStyle(TraidoresTheme.text)
            .onChange(of: game.phaseIndex) { _, _ in selected = nil }
            .onChange(of: transitionSpec(for: game).key) { _, _ in
                queueTransition(for: game)
            }
            .onChange(of: privateFeedback) { _, feedback in
                if feedback == nil { presentPendingTransition(using: game) }
            }
            .onAppear { queueTransition(for: game) }
            .onDisappear { transitionTask?.cancel() }
            .confirmationDialog("La partida queda guardada para continuar después.",
                                isPresented: $leaving, titleVisibility: .visible) {
                Button("Volver al menú") { dismissMatch() }
            }
            .animation(.easeInOut(duration: 0.18), value: showingRole)
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

    private func transitionSpec(for game: ClassicGame) -> DayNightTransition {
        .init(period: game.isNight ? .night : .day, round: game.round)
    }

    private func queueTransition(for game: ClassicGame) {
        let spec = transitionSpec(for: game)
        guard spec.key != lastTransitionKey, spec != activeTransition else { return }
        pendingTransition = spec
        presentPendingTransition(using: game)
    }

    private func presentPendingTransition(using game: ClassicGame) {
        guard privateFeedback == nil, activeTransition == nil, let spec = pendingTransition else { return }
        pendingTransition = nil
        lastTransitionKey = spec.key
        withAnimation(.easeInOut(duration: 0.24)) { activeTransition = spec }
        transitionTask?.cancel()
        let nanoseconds = UInt64(transitionDuration(for: game) * 1_000_000_000)
        transitionTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.32)) { activeTransition = nil }
            try? await Task.sleep(nanoseconds: 340_000_000)
            guard !Task.isCancelled else { return }
            presentPendingTransition(using: game)
        }
    }

    private func transitionDuration(for game: ClassicGame) -> TimeInterval {
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-ui-testing-transition") { return 1.5 }
        if arguments.contains("-ui-testing") { return 0.08 }
        return TimeInterval(game.timing.transitionSeconds)
    }

    private func dayNightTransitionOverlay(
        _ transition: DayNightTransition,
        game: ClassicGame
    ) -> some View {
        DayNightTransitionView(
            transition: transition,
            duration: transitionDuration(for: game)
        )
    }

    private func tableHeader(_ game: ClassicGame) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
            Button { leaving = true } label: {
                    Image(systemName: "chevron.left").font(.caption.bold()).frame(width: 30, height: 30)
                        .background(TraidoresTheme.ink.opacity(0.84), in: RoundedRectangle(cornerRadius: 7))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(game.phase.classicTitle.uppercased())
                        .font(TraidoresTheme.title(16)).foregroundStyle(TraidoresTheme.gold)
                        .lineLimit(1).minimumScaleFactor(0.7)
                    .accessibilityIdentifier("table.phaseTitle")
                    Text("Ronda \(game.round) · Pampa").font(.caption2).foregroundStyle(TraidoresTheme.secondary)
            }
            Spacer()
            Button { showingRole = true } label: {
                    Image(systemName: "person.text.rectangle").font(.caption).frame(width: 30, height: 30)
                        .background(TraidoresTheme.ink.opacity(0.84), in: RoundedRectangle(cornerRadius: 7))
            }
            .accessibilityLabel("Ver mi rol")
            }
            .frame(height: 42).padding(.horizontal, 6)
            Divider().overlay(TraidoresTheme.border)
            Text(instructions(game))
                .font(.caption2.weight(.semibold)).lineLimit(2).minimumScaleFactor(0.82)
                .frame(maxWidth: .infinity, minHeight: 35, alignment: .leading)
                .padding(.horizontal, 7)
        }
        .frame(height: 80)
        .background(TraidoresTheme.panel.opacity(0.95), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
    }

    private func sidePlayers(_ game: ClassicGame) -> (left: [ClassicPlayer], right: [ClassicPlayer]) {
        let companions = game.players.filter { $0.id != 0 }
        let leftCount = (companions.count + 1) / 2
        return (Array(companions.prefix(leftCount)), Array(companions.dropFirst(leftCount)))
    }

    private func playerColumn(
        _ players: [ClassicPlayer],
        game: ClassicGame,
        metrics: ClassicCompanionMetrics
    ) -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: CGFloat(metrics.itemGap)) {
                ForEach(players) { player in
                    sidePlayerCard(player, game: game, metrics: metrics)
                }
            }
            .frame(maxWidth: .infinity, minHeight: metrics.scrollEnabled ? 0 : nil)
        }
        .scrollDisabled(!metrics.scrollEnabled)
        .frame(width: CGFloat(metrics.columnWidth))
    }

    private func sidePlayerCard(
        _ player: ClassicPlayer,
        game: ClassicGame,
        metrics: ClassicCompanionMetrics
    ) -> some View {
        let actionable = game.legalTargets(for: 0).contains(player.id)
        return Button {
            if actionable { selected = player.id }
        } label: {
            VStack(spacing: 0) {
                ZStack {
                    Image(publicCardImage(player, game: game)).resizable().scaledToFit()
                        .frame(width: CGFloat(metrics.cardWidth), height: CGFloat(metrics.cardHeight))
                        .clipShape(RoundedRectangle(cornerRadius: 5))
                    ZStack {
                        Circle().fill(TraidoresTheme.ink.opacity(0.88))
                            .frame(width: CGFloat(metrics.avatarSize), height: CGFloat(metrics.avatarSize))
                        Text(String(player.name.prefix(1)).uppercased())
                            .font(TraidoresTheme.title(max(CGFloat(metrics.avatarSize) * 0.5, 7)))
                                .foregroundStyle(player.alive ? TraidoresTheme.gold : TraidoresTheme.secondary)
                        if !player.alive { Image(systemName: "xmark").font(.caption.bold()) }
                    }
                }
                Text(player.name).font(.system(size: metrics.nameTextSize, weight: .bold)).lineLimit(1)
                    .minimumScaleFactor(0.65)
                    .frame(height: CGFloat(metrics.nameHeight))
            }
            .frame(
                minWidth: CGFloat(metrics.minimumCardWidth),
                maxWidth: CGFloat(metrics.minimumCardWidth),
                minHeight: CGFloat(metrics.itemHeight),
                alignment: .top
            )
            .contentShape(Rectangle())
            .overlay {
                if selected == player.id {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(TraidoresTheme.gold, lineWidth: 2)
                        .frame(width: CGFloat(metrics.cardWidth + 4), height: CGFloat(metrics.cardHeight + 4))
                        .frame(maxHeight: .infinity, alignment: .top)
                }
            }
        }
        .buttonStyle(.plain).disabled(!actionable)
        .opacity(player.alive ? 1 : 0.62)
        .accessibilityIdentifier("table.player.\(player.id)")
    }

    private func publicCardImage(_ player: ClassicPlayer, game: ClassicGame) -> String {
        !player.alive && game.advanced.revealRolesOnDeath ? player.role.classicImage : "card_back_traidores"
    }

    @ViewBuilder
    private func tableCenter(_ game: ClassicGame) -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 8) {
                if let winner = game.winner {
                    resultPanel(game, winner: winner)
                } else if game.phase == .discussion {
                    debatePanel(game)
                } else if game.phase == .voteCount {
                    votePanel(game)
                } else if game.phase == .result {
                    publicResultPanel(game)
                } else if !game.legalTargets(for: 0).isEmpty {
                    targetPrompt(game)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 5)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func targetPrompt(_ game: ClassicGame) -> some View {
        VStack(spacing: 7) {
            Image(systemName: selected == nil ? "hand.tap" : "checkmark.circle.fill")
                .font(.title2).foregroundStyle(TraidoresTheme.gold)
            Text(selected.map { game.name($0).uppercased() } ?? "ELEGÍ UN OBJETIVO")
                .font(.caption.bold()).tracking(0.8).multilineTextAlignment(.center)
            Text(selected == nil ? "Tocá una carta de la mesa." : "Objetivo preparado. Confirmá abajo.")
                .font(.caption2).foregroundStyle(TraidoresTheme.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(12)
        .background(TraidoresTheme.panel.opacity(0.93), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
    }

    private func publicResultPanel(_ game: ClassicGame) -> some View {
        VStack(spacing: 8) {
            Text("DECISIÓN DE LA MESA")
                .font(.caption2.bold()).tracking(1).foregroundStyle(TraidoresTheme.ink)
                .padding(.horizontal, 11).padding(.vertical, 5)
                .background(TraidoresTheme.gold, in: Capsule())
            Text(game.eliminationTarget == nil ? "SIN EXPULSIÓN" : "EL PUEBLO DECIDIÓ")
                .font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
            Divider().overlay(TraidoresTheme.border)
            Text(game.messages.last?.text ?? "La mesa tomó una decisión.")
                .font(.subheadline.weight(.semibold)).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(14)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 11))
        .overlay(RoundedRectangle(cornerRadius: 11).stroke(TraidoresTheme.gold, lineWidth: 1.2))
    }

    private func humanPanel(_ game: ClassicGame) -> some View {
        let targets = game.legalTargets(for: 0)
        let canChooseSelf = targets.contains(0)
        let canAdvance = targets.isEmpty || targets.contains(selected ?? -1)
        return VStack(spacing: 5) {
            Button {
                if canChooseSelf { selected = 0 }
            } label: {
                HStack(spacing: 8) {
                    Image(game.human.role.classicImage).resizable().scaledToFill()
                        .frame(width: 48, height: 76).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(
                            selected == 0 ? TraidoresTheme.gold : TraidoresTheme.border,
                            lineWidth: selected == 0 ? 2 : 1
                        ))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(game.human.name).font(.subheadline.bold()).foregroundStyle(TraidoresTheme.gold)
                            .lineLimit(1)
                        Text(game.human.role.classicTitle.uppercased())
                            .font(.caption2.bold()).foregroundStyle(TraidoresTheme.secondary)
                        Text(humanHint(game, canChooseSelf: canChooseSelf))
                            .font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                            .lineLimit(2).minimumScaleFactor(0.82)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .buttonStyle(.plain).disabled(!canChooseSelf)
            .accessibilityIdentifier("table.player.0")

            HStack(spacing: 5) {
                Button("VER CARTA") { showingRole = true }
                    .font(.system(size: 10, weight: .bold)).tracking(0.5)
                    .foregroundStyle(TraidoresTheme.text)
                    .frame(maxWidth: .infinity, minHeight: 32)
                    .background(TraidoresTheme.ink.opacity(0.88), in: RoundedRectangle(cornerRadius: 7))
                    .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.border))
                Button(actionTitle(game)) {
                    performPrimaryAction(game)
                }
                .font(.system(size: 10, weight: .heavy)).tracking(0.45)
                .foregroundStyle(TraidoresTheme.ink)
                .lineLimit(1).minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity, minHeight: 32)
                .background(TraidoresTheme.gold, in: RoundedRectangle(cornerRadius: 7))
                .disabled(!canAdvance).opacity(canAdvance ? 1 : 0.52)
                .accessibilityIdentifier("table.primaryAction")
            }
        }
        .padding(7).frame(height: 142)
        .background(TraidoresTheme.panel.opacity(0.97), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
    }

    private func performPrimaryAction(_ game: ClassicGame) {
        let target = selected
        let targetPlayer = target.flatMap { target in game.players.first { $0.id == target } }
        let feedback: PrivateActionFeedback? = switch (game.phase, targetPlayer) {
        case (.assassinNight, let player?):
            .init(
                title: "VÍCTIMA ELEGIDA",
                message: "Elegiste a \(player.name). El resultado se anunciará al amanecer.",
                systemImage: "moon.fill"
            )
        case (.detectiveNight, let player?):
            .init(
                title: "RESPUESTA PRIVADA",
                message: "\(player.name) parece \(player.role == .assassin ? "SOSPECHOSO" : "INOCENTE").",
                systemImage: "eye.fill"
            )
        case (.medicNight, let player?):
            .init(
                title: "PROTECCIÓN REGISTRADA",
                message: player.id == 0
                    ? "Te protegiste durante esta noche."
                    : "Protegiste a \(player.name) durante esta noche.",
                systemImage: "cross.case.fill"
            )
        default:
            nil
        }
        store.advance(target: target, revision: game.phaseIndex)
        privateFeedback = feedback
    }

    private func privateFeedbackOverlay(_ feedback: PrivateActionFeedback) -> some View {
        ZStack {
            Color.black.opacity(0.82).ignoresSafeArea()
            VStack(spacing: 13) {
                Image(systemName: feedback.systemImage)
                    .font(.title2).foregroundStyle(TraidoresTheme.gold)
                Text(feedback.title)
                    .font(TraidoresTheme.title(21)).foregroundStyle(TraidoresTheme.gold)
                Text(feedback.message)
                    .font(.headline).multilineTextAlignment(.center)
                Text("Solo vos recibís esta información.")
                    .font(.caption).foregroundStyle(TraidoresTheme.secondary)
                Button("CONTINUAR") { privateFeedback = nil }
                    .buttonStyle(TraidoresButtonStyle(prominent: true)).frame(maxWidth: 210)
                    .accessibilityIdentifier("table.dismissPrivateFeedback")
            }
            .padding(20).frame(maxWidth: 350)
            .background(TraidoresTheme.panel.opacity(0.98), in: RoundedRectangle(cornerRadius: 15))
            .overlay(RoundedRectangle(cornerRadius: 15).stroke(TraidoresTheme.gold, lineWidth: 1.5))
            .padding(18)
        }
    }

    private func humanHint(_ game: ClassicGame, canChooseSelf: Bool) -> String {
        if !game.human.alive { return "Estás eliminado. Podés observar la mesa." }
        if selected == 0 { return "Te elegiste como objetivo." }
        if canChooseSelf { return "Tocá tu carta para elegirte." }
        if let selected { return "Objetivo: \(game.name(selected))" }
        return instructions(game)
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
            if game.advanced.showIndividualVotes {
                ForEach(game.votes.keys.sorted(), id: \.self) { voter in
                    Text("\(game.name(voter)) → \(game.name(game.votes[voter] ?? voter))")
                        .font(.subheadline)
                }
            } else {
                let totals = Dictionary(grouping: game.votes.values, by: { $0 }).mapValues(\.count)
                ForEach(totals.keys.sorted(), id: \.self) { target in
                    Text("\(game.name(target)): \(totals[target] ?? 0) votos").font(.subheadline)
                }
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

    private func roleOverlay(_ role: RoleKey) -> some View {
        ZStack {
            Color.black.opacity(0.82).ignoresSafeArea()
            VStack(spacing: 7) {
                Text("TU ROL").font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
                Text(role.classicTitle.uppercased())
                    .font(TraidoresTheme.title(27)).foregroundStyle(TraidoresTheme.gold)
                Text(role == .assassin ? "TRAIDORES" : "PUEBLO")
                    .font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
                Divider().overlay(TraidoresTheme.border)
                HStack(alignment: .top, spacing: 11) {
                    Image(role.classicImage).resizable().scaledToFill()
                        .frame(width: 92, height: 138).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(TraidoresTheme.gold))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("QUÉ HACE").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                        Text(RoleCatalog.all.first { $0.id == role }?.instructions ?? "")
                            .font(.caption).fixedSize(horizontal: false, vertical: true)
                        Text("CONSEJO").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                            .padding(.top, 3)
                        Text(role.classicAdvice).font(.caption).foregroundStyle(TraidoresTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).layoutPriority(1)
                }
                Button("CERRAR") { showingRole = false }
                    .buttonStyle(TraidoresButtonStyle(prominent: true)).frame(maxWidth: 190)
            }
            .padding(14).frame(maxWidth: 370)
            .foregroundStyle(TraidoresTheme.text)
            .background(TraidoresTheme.panel.opacity(0.98), in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(TraidoresTheme.gold, lineWidth: 1.5))
            .padding(14)
        }
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
        case .voteCount: game.advanced.showIndividualVotes
            ? "Revisá cómo votó cada participante."
            : "Revisá el total de votos recibido por cada participante."
        case .result: game.messages.last?.text ?? "La mesa tomó una decisión."
        default: "La partida continúa."
        }
    }

    private func playerStatus(_ player: ClassicPlayer, game: ClassicGame) -> String {
        if player.alive { return "EN LA MESA" }
        return game.advanced.revealRolesOnDeath
            ? "ELIMINADO · \(player.role.classicTitle.uppercased())"
            : "ELIMINADO"
    }

    private func actionTitle(_ game: ClassicGame) -> String {
        if !game.legalTargets(for: 0).isEmpty {
            return switch game.phase {
            case .assassinNight: "MATAR"
            case .detectiveNight: "INVESTIGAR"
            case .medicNight: selected == 0 ? "SALVARME" : "SALVAR"
            case .voting, .tieVote: "VOTAR"
            default: "CONFIRMAR"
            }
        }
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

    var classicAdvice: String {
        switch self {
        case .assassin: "Mezclate con el Pueblo. Acusá con cuidado y evitá que tus votos revelen un patrón."
        case .detective: "Protegé tus investigaciones. Revelarte demasiado pronto puede convertirte en el próximo objetivo."
        case .medic: "Buscá a los roles valiosos y variá tus protecciones para que los Traidores no puedan anticiparte."
        default: "Escuchá las contradicciones y observá los votos. Tu información se construye durante el debate."
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
