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
                    VStack(spacing: 14) {
                        difficultyLink(.normal, title: "NORMAL", prominent: true)
                        difficultyLink(.hard, title: "DIFICIL", prominent: false)
                    }
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
    @AppStorage("local.testOptions", store: LocalLobbyPreferences.store) private var savedTestOptions = ""
    @AppStorage("local.trainingRole", store: LocalLobbyPreferences.store) private var selectedTrainingRoleKey = ""
    @AppStorage("local.map", store: LocalLobbyPreferences.store) private var selectedMapKey = GameMap.pampa.rawValue
    @State private var playing = false
    @State private var showingTiming = false
    @State private var showingAdvanced = false
    @State private var botNames = Array(ClassicGame.defaultBotNames.prefix(4))
    @State private var timing = GameTimingConfig.normal
    @State private var advanced = AdvancedGameConfig.standard
    @State private var testOptions = LocalTestOptions.standard
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
                    Image(selectedMap.dayBackgroundAsset)
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
        .onChange(of: testOptions) { _, value in
            guard let data = try? JSONEncoder().encode(value) else { return }
            savedTestOptions = String(decoding: data, as: UTF8.self)
        }
        .sheet(isPresented: $showingTiming) {
            MatchOptionsView(config: $testOptions)
        }
        .sheet(isPresented: $showingAdvanced) {
            AdvancedOptionsView(config: $advanced, timing: $timing,
                                trainingRoleKey: $selectedTrainingRoleKey)
        }
    }

    private var editAlertBinding: Binding<Bool> {
        Binding(get: { editingBot != nil }, set: { if !$0 { editingBot = nil } })
    }

    private var selectedMap: GameMap {
        GameMap(rawValue: selectedMapKey) ?? .pampa
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
            mapSelector
            Text(selectedMap.lobbyDescription)
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
                Text(selectedMap.title.uppercased())
                    .font(TraidoresTheme.title(26)).foregroundStyle(TraidoresTheme.gold)
                    .accessibilityIdentifier("lobby.selectedMapName")
                Text("Rol exclusivo: \(selectedMap.exclusiveRoleTitle)")
                    .font(.caption.weight(.semibold))
            }
            .padding(14)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 126)
        .background {
            Image(selectedMap.landscapeAsset)
                .resizable()
                .scaledToFill()
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    private var mapSelector: some View {
        HStack(spacing: 8) {
            ForEach(GameMap.allCases, id: \.rawValue) { map in
                Button {
                    selectedMapKey = map.rawValue
                } label: {
                    Image(map.landscapeAsset)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .clipped()
                        .overlay {
                            Color.black.opacity(map == selectedMap ? 0 : 0.30)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                        .overlay {
                            RoundedRectangle(cornerRadius: 9)
                                .stroke(map == selectedMap ? TraidoresTheme.gold : TraidoresTheme.border,
                                        lineWidth: map == selectedMap ? 2 : 1)
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Mapa \(map.title)")
                .accessibilityAddTraits(map == selectedMap ? .isSelected : [])
                .accessibilityIdentifier("lobby.map.\(map.rawValue)")
            }
        }
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
            RoleKey(rawValue: selectedTrainingRoleKey)
        }
        #if DEBUG
        let appliedTestOptions = testOptions
        #else
        let appliedTestOptions = LocalTestOptions(quickMatch: testOptions.quickMatch)
        #endif
        store.start(name: "Vos", map: selectedMap, difficulty: difficulty, botNames: botNames,
                    timing: timing, advanced: advanced, testOptions: appliedTestOptions,
                    trainingRole: trainingRole)
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
        defer {
            if let data = savedTestOptions.data(using: .utf8),
               let value = try? JSONDecoder().decode(LocalTestOptions.self, from: data) {
                testOptions = value
            }
        }
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
    @Binding private var timing: GameTimingConfig
    @Binding private var trainingRoleKey: String
    @State private var draft: AdvancedGameConfig
    @State private var draftRoleKey: String
    @State private var showingTiming = false

    init(config: Binding<AdvancedGameConfig>, timing: Binding<GameTimingConfig>,
         trainingRoleKey: Binding<String>) {
        _config = config
        _timing = timing
        _trainingRoleKey = trainingRoleKey
        _draft = State(initialValue: config.wrappedValue)
        _draftRoleKey = State(initialValue: trainingRoleKey.wrappedValue)
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
                    Text("Elegí cuánto esperar antes de habilitar EMPEZAR. Al tocarlo comienza la primera noche.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    HStack(spacing: 8) {
                        readingButton("INMEDIATO", seconds: 0)
                        readingButton("6 S", seconds: 6)
                        readingButton("10 S", seconds: 10)
                    }
                    Text("TIEMPOS DE PARTIDA").font(.caption.bold()).tracking(1.2)
                        .foregroundStyle(TraidoresTheme.secondary)
                    Text("Cuánto dura cada etapa de la partida. Podés usar los valores normales o ajustarlos.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    Button {
                        showingTiming = true
                    } label: {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("CAMBIAR TIEMPOS")
                                    .font(TraidoresTheme.title(17))
                                Spacer()
                                Image(systemName: "chevron.right")
                            }
                            timingSummaryRow("Cambio día/noche", seconds: timing.transitionSeconds)
                            timingSummaryRow("Acción nocturna", seconds: timing.nightSeconds)
                            timingSummaryRow("Debate del pueblo", seconds: timing.discussionSeconds)
                            timingSummaryRow("Votación", seconds: timing.votingSeconds)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
                    }
                    .buttonStyle(.plain)
                    Text("MODO DE PRUEBA · PARTIDA LOCAL").font(.caption.bold()).tracking(1.2)
                        .foregroundStyle(TraidoresTheme.secondary)
                    Text("Elegí el rol que querés probar en la próxima partida. AZAR mantiene el reparto normal.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("TU ROL").font(.subheadline.bold())
                            Spacer()
                            Picker("Tu rol", selection: $draftRoleKey) {
                                Text("Al azar").tag("")
                                ForEach(ClassicGame.supportedTrainingRoles, id: \.self) { role in
                                    Text(role.classicTitle).tag(role.rawValue)
                                }
                            }
                            .labelsHidden()
                            .tint(TraidoresTheme.gold)
                        }
                        Text(trainingRoleExplanation)
                            .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(12)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
                    Button("APLICAR") {
                        config = draft.normalized
                        trainingRoleKey = draftRoleKey
                        dismiss()
                    }
                    .buttonStyle(TraidoresButtonStyle(prominent: true))
                    Button("RESTABLECER") { draft = .standard; draftRoleKey = "" }
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
        .sheet(isPresented: $showingTiming) { TimingOptionsView(timing: $timing) }
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

    private func timingSummaryRow(_ label: String, seconds: Int) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(TraidoresTheme.secondary)
            Spacer(minLength: 8)
            Text("\(seconds) s")
                .monospacedDigit()
                .foregroundStyle(TraidoresTheme.gold)
        }
        .font(.subheadline)
    }

    private var trainingRoleExplanation: String {
        guard let role = ClassicGame.supportedTrainingRoles.first(where: { $0.rawValue == draftRoleKey }) else {
            return "Se te asignará un rol al azar según la composición elegida."
        }
        return "Vas a jugar como \(role.classicTitle). Si no estaba en la composición, ocupará el lugar de un Aldeano; los demás roles se reparten normalmente."
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

private struct MatchOptionsView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding private var config: LocalTestOptions
    @State private var draft: LocalTestOptions

    init(config: Binding<LocalTestOptions>) {
        _config = config
        _draft = State(initialValue: config.wrappedValue)
    }

    var body: some View {
        ZStack {
            MenuBackground()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("OPCIONES DE PARTIDA")
                        .font(TraidoresTheme.title(25)).foregroundStyle(TraidoresTheme.gold)
                        .frame(maxWidth: .infinity)
                    Text("RITMO").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                    toggle("Partida rápida", detail: "Acorta los tiempos y salta automáticamente las fases nocturnas sin acción propia.",
                           value: $draft.quickMatch)
                    #if DEBUG
                    Text("HERRAMIENTAS DEBUG").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                    toggle("IA obedece votos del chat", detail: "Los bots siguen tu sospecha al votar.",
                           value: $draft.botsFollowAccusation)
                    toggle("Forzar empates", detail: "Prepara una votación empatada cuando hay votos suficientes.",
                           value: $draft.forceVoteTies)
                    toggle("Bots no te matan de noche", detail: nil,
                           value: $draft.botsNeverKillHuman)
                    toggle("Bots no te votan", detail: nil,
                           value: $draft.botsNeverVoteHuman)
                    #else
                    Text("Las herramientas debug solo aparecen en compilaciones de prueba.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    #endif
                    HStack(spacing: 10) {
                        Button("CANCELAR") { dismiss() }.buttonStyle(TraidoresButtonStyle())
                        Button("APLICAR") { config = draft; dismiss() }
                            .buttonStyle(TraidoresButtonStyle(prominent: true))
                    }
                }
                .padding(20).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .presentationDetents([.large])
    }

    private func toggle(_ title: String, detail: String?, value: Binding<Bool>) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Toggle(title, isOn: value).tint(TraidoresTheme.gold)
            if let detail { Text(detail).font(.footnote).foregroundStyle(TraidoresTheme.secondary) }
        }
        .padding(13)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
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
                ZStack {
                    // Keep the actual match table underneath the private role reveal,
                    // as Android does. The dealing animation itself remains full-screen.
                    LocalTableView(store: store, dismissMatch: onExit)
                        .allowsHitTesting(false)
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
    @State private var vignetteOpacity = 0.0
    @State private var candleOpacity = 0.0
    @State private var dealingOpacity = 1.0
    @State private var backOpacity = 0.0
    @State private var handsOpacity = 0.0
    @State private var handsOffset = 58.0
    @State private var leftHandOffset = -28.0
    @State private var rightHandOffset = 28.0
    @State private var handsScale = 0.91
    @State private var leftHandRotation = -5.0
    @State private var rightHandRotation = 5.0
    @State private var cardOpacity = 0.0
    @State private var cardOffset = -112.0
    @State private var cardScale = 0.78
    @State private var cardRotation = -3.0
    @State private var cardRotationX = 4.0
    @State private var cardRotationY = -11.0
    @State private var auraOpacity = 0.0
    @State private var auraScale = 0.74
    @State private var shadowOpacity = 0.0
    @State private var shadowScale = 0.88
    @State private var shadowOffset = 7.0
    @State private var shadowRotation = -3.0
    @State private var statusOpacity = 0.0
    @State private var viewportHeight = 0.0
    @State private var remainingReading = 0
    @State private var showingExitConfirmation = false
    @State private var showingTeammates = false

    var body: some View {
        ZStack {
            if stage == .dealing {
                Color.black.ignoresSafeArea()
                dealingStage.opacity(dealingOpacity)
            } else if let game = store.game {
                Color.black.opacity(0.58).ignoresSafeArea()
                if !showingTeammates {
                    rolePreview(game.human.role)
                }
            }

            Button { showingExitConfirmation = true } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 46, height: 46)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
            }
            .foregroundStyle(TraidoresTheme.text).padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .opacity(showingTeammates ? 0 : backOpacity)
            .disabled(showingTeammates || backOpacity < 0.7)
            .accessibilityIdentifier("assignment.back")

            if showingTeammates, let game = store.game {
                teammateReveal(game)
                    .zIndex(2)
            }
        }
        .alert("¿Salir de la partida?", isPresented: $showingExitConfirmation) {
            Button("SALIR", role: .destructive, action: onExit)
            Button("SEGUIR JUGANDO", role: .cancel) {}
        } message: {
            Text("Si salís ahora, se cancelará la partida y perderás su progreso.")
        }
        .task {
            await runDealingAnimation()
            remainingReading = store.game?.advanced.roleReadingSeconds ?? 0
            while remainingReading > 0 {
                try? await Task.sleep(for: .seconds(1))
                remainingReading -= 1
            }
        }
        .task(id: showingTeammates) {
            guard showingTeammates else { return }
            try? await Task.sleep(for: .seconds(6))
            guard !Task.isCancelled, showingTeammates else { return }
            showingTeammates = false
            onStart()
        }
    }

    @MainActor
    private func runDealingAnimation() async {
        let settleY = -max(viewportHeight, 700) * 0.02

        withAnimation(.easeOut(duration: 0.42)) {
            tableOpacity = 1
            vignetteOpacity = 0.78
            candleOpacity = 0.16
        }
        try? await Task.sleep(for: .seconds(0.42))
        guard !Task.isCancelled else { return }

        withAnimation(.easeOut(duration: 0.48)) {
            handsOpacity = 1
            handsOffset = 0
            leftHandOffset = -4
            rightHandOffset = 4
            handsScale = 0.94
            leftHandRotation = -1
            rightHandRotation = 1
        }
        withAnimation(.easeOut(duration: 0.22).delay(0.12)) { backOpacity = 0.76 }
        try? await Task.sleep(for: .seconds(0.48))
        guard !Task.isCancelled else { return }

        withAnimation(.easeOut(duration: 0.50)) {
            cardOpacity = 1
            cardOffset = settleY + 8
            cardScale = 1.04
            cardRotation = 0
            cardRotationX = -1
            cardRotationY = 2
            auraOpacity = 0.38
            auraScale = 1
            shadowOpacity = 0.44
            shadowScale = 1
            shadowOffset = 2
            shadowRotation = -1
            leftHandOffset = 10
            rightHandOffset = -10
            handsOffset = -4
            handsScale = 0.95
            leftHandRotation = 1.5
            rightHandRotation = -1.5
        }
        try? await Task.sleep(for: .seconds(0.50))
        guard !Task.isCancelled else { return }

        withAnimation(.easeInOut(duration: 0.13)) {
            cardOffset = settleY + 12
            cardScale = 1
        }
        try? await Task.sleep(for: .seconds(0.13))
        withAnimation(.easeInOut(duration: 0.13)) {
            cardOffset = settleY + 2
            cardScale = 1.06
            cardRotationX = 0
            cardRotationY = 0
            shadowOpacity = 0.58
            shadowScale = 0.94
            shadowOffset = 0
            shadowRotation = 0
            leftHandOffset = 3
            rightHandOffset = -3
            handsOffset = 2
            handsScale = 0.94
            leftHandRotation = 0
            rightHandRotation = 0
        }
        // The settle motion lasts 0.13 s; keep only a brief beat before the hands leave.
        try? await Task.sleep(for: .seconds(0.18))
        guard !Task.isCancelled else { return }

        withAnimation(.easeOut(duration: 0.62)) {
            handsOpacity = 0
            handsOffset = 82
            leftHandOffset = -42
            rightHandOffset = 42
            handsScale = 0.91
            leftHandRotation = -6
            rightHandRotation = 6
            cardScale = 1.13
            cardOffset = settleY - 2
            auraOpacity = 0.58
            shadowOpacity = 0.48
            statusOpacity = 1
        }
        try? await Task.sleep(for: .seconds(0.62))
        guard !Task.isCancelled else { return }

        withAnimation(.easeInOut(duration: 0.52)) {
            cardScale = 1.08
            cardOffset = settleY + 5
            statusOpacity = 0.72
            auraOpacity = 0.42
        }
        try? await Task.sleep(for: .seconds(0.52))
        withAnimation(.easeInOut(duration: 0.52)) {
            cardScale = 1.13
            cardOffset = settleY - 2
            statusOpacity = 1
            auraOpacity = 0.58
        }
        // Leave the dealt card on the table just a touch longer before revealing the role.
        try? await Task.sleep(for: .seconds(0.72))
        guard !Task.isCancelled else { return }

        withAnimation(.easeOut(duration: 0.48)) {
            dealingOpacity = 0
            backOpacity = 0
        }
        try? await Task.sleep(for: .seconds(0.48))
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.28)) {
            stage = .role
            backOpacity = 1
        }
    }

    private var dealingStage: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height
            let cardTop = height * 0.29

            ZStack(alignment: .topLeading) {
                Image("assigning_vertical_table")
                    .resizable()
                    .scaledToFill()
                    .frame(width: width, height: height)
                    .clipped()
                    .opacity(tableOpacity)

                RadialGradient(
                    colors: [Color(red: 1, green: 176 / 255, blue: 79 / 255).opacity(0.55),
                             Color(red: 232 / 255, green: 139 / 255, blue: 50 / 255).opacity(0.19),
                             .clear],
                    center: .center,
                    startRadius: 0,
                    endRadius: 115
                )
                .frame(width: 210, height: 250)
                .offset(x: -42, y: -22)
                .opacity(candleOpacity)

                RadialGradient(
                    colors: [.clear, .black.opacity(0.08), .black.opacity(0.64)],
                    center: .center,
                    startRadius: min(width, height) * 0.12,
                    endRadius: max(width, height) * 0.62
                )
                .frame(width: width, height: height)
                .opacity(vignetteOpacity)

                Ellipse()
                    .fill(RadialGradient(
                        colors: [TraidoresTheme.gold.opacity(0.65), TraidoresTheme.gold.opacity(0.26), .clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: 130
                    ))
                    .frame(width: 220, height: 312)
                    .scaleEffect(auraScale)
                    .opacity(auraOpacity)
                    .position(x: width / 2, y: cardTop + 98)

                Ellipse()
                    .fill(LinearGradient(
                        colors: [.clear, .black.opacity(0.27), .clear],
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                    .frame(width: 144, height: 216)
                    .scaleEffect(shadowScale)
                    .rotationEffect(.degrees(shadowRotation))
                    .opacity(shadowOpacity)
                    .position(x: width / 2 + shadowOffset, y: cardTop + 116)

                Image("card_back_traidores")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 124, height: 196)
                    .rotation3DEffect(.degrees(cardRotationX), axis: (x: 1, y: 0, z: 0), perspective: 0.35)
                    .rotation3DEffect(.degrees(cardRotationY), axis: (x: 0, y: 1, z: 0), perspective: 0.35)
                    .rotationEffect(.degrees(cardRotation))
                    .scaleEffect(cardScale)
                    .opacity(cardOpacity)
                    .position(x: width / 2, y: cardTop + 98 + cardOffset)

                dealerHands(width: width, height: height, leftHalf: true)
                    .scaleEffect(handsScale, anchor: UnitPoint(x: 0.18, y: 1))
                    .rotationEffect(.degrees(leftHandRotation), anchor: UnitPoint(x: 0.18, y: 1))
                    .offset(x: leftHandOffset, y: handsOffset)
                    .opacity(handsOpacity)

                dealerHands(width: width, height: height, leftHalf: false)
                    .scaleEffect(handsScale, anchor: UnitPoint(x: 0.82, y: 1))
                    .rotationEffect(.degrees(rightHandRotation), anchor: UnitPoint(x: 0.82, y: 1))
                    .offset(x: rightHandOffset, y: handsOffset)
                    .opacity(handsOpacity)

                Text("¡Buena suerte con tu rol!")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(TraidoresTheme.gold)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 9)
                    .background(TraidoresTheme.ink.opacity(0.92), in: Capsule())
                    .overlay(Capsule().stroke(TraidoresTheme.border))
                    .opacity(statusOpacity)
                    .position(x: width / 2, y: height - 108)
                    .accessibilityIdentifier("assignment.status")
            }
            .frame(width: width, height: height)
            .clipped()
            .onAppear { viewportHeight = height }
        }
        .ignoresSafeArea()
    }

    private func dealerHands(width: CGFloat, height: CGFloat, leftHalf: Bool) -> some View {
        Image("assigning_dealer_hands")
            .resizable()
            .scaledToFill()
            .frame(width: width, height: height)
            .clipped()
            .mask {
                HStack(spacing: 0) {
                    if leftHalf { Color.white } else { Color.clear }
                    if leftHalf { Color.clear } else { Color.white }
                }
            }
            .accessibilityHidden(true)
    }

    private func rolePreview(_ role: RoleKey) -> some View {
        VStack(spacing: 7) {
            Text("TU ROL").font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
            Text(role.classicTitle.uppercased()).font(TraidoresTheme.title(27)).foregroundStyle(TraidoresTheme.gold)
            Text([RoleKey.assassin, .mercenary, .spy].contains(role) ? "TRAIDORES" : "PUEBLO")
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
                Button("EMPEZAR") { beginMatch() }
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

    private func beginMatch() {
        guard let game = store.game else { return }
        let traitorRoles: Set<RoleKey> = [.assassin, .mercenary, .spy]
        let teammates = game.players.filter { $0.id != 0 && traitorRoles.contains($0.role) }
        if traitorRoles.contains(game.human.role), !teammates.isEmpty {
            showingTeammates = true
        } else {
            onStart()
        }
    }

    private func teammateReveal(_ game: ClassicGame) -> some View {
        let traitorRoles: Set<RoleKey> = [.assassin, .mercenary, .spy]
        let teammates = game.players.filter { $0.id != 0 && traitorRoles.contains($0.role) }
        return ZStack {
            Color.black.opacity(0.72).ignoresSafeArea()
            VStack(spacing: 10) {
                Text("TUS COMPAÑEROS TRAIDORES")
                    .font(TraidoresTheme.title(17)).foregroundStyle(TraidoresTheme.gold)
                    .multilineTextAlignment(.center)
                    .lineLimit(2).minimumScaleFactor(0.85)
                HStack(spacing: 12) {
                    ForEach(teammates) { teammate in
                        VStack(spacing: 3) {
                            Image(teammate.role.classicImage)
                                .resizable().scaledToFill()
                                .frame(width: teammates.count > 2 ? 68 : 92,
                                       height: teammates.count > 2 ? 96 : 124)
                                .clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(TraidoresTheme.gold))
                            Text(teammate.name).font(.caption.bold()).lineLimit(1)
                            Text(teammate.role.classicTitle.uppercased())
                                .font(.system(size: 10)).foregroundStyle(TraidoresTheme.secondary)
                        }
                    }
                }
                Text("Tocá para continuar · continúa solo en 6 s")
                    .font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: 260)
            .modifier(LocalEventCard(map: game.map))
            .padding(10)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            guard showingTeammates else { return }
            showingTeammates = false
            onStart()
        }
        .accessibilityIdentifier("assignment.teammates")
    }
}

private enum AssignmentStage { case dealing, role }

private struct EventSeal: View {
    let symbol: String
    let accent: Color

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 9)
                .fill(accent.opacity(0.22))
                .frame(width: 52, height: 52)
                .rotationEffect(.degrees(45))
            Circle().fill(TraidoresTheme.ink).frame(width: 66, height: 66)
            Circle().strokeBorder(accent.opacity(0.86), lineWidth: 1.5)
                .frame(width: 66, height: 66)
            Circle().strokeBorder(accent.opacity(0.34), lineWidth: 1)
                .frame(width: 55, height: 55)
            Image(systemName: symbol)
                .font(.system(size: 27, weight: .medium))
                .foregroundStyle(accent)
        }
        .frame(width: 82, height: 82)
        .accessibilityHidden(true)
    }
}

private struct LocalEventCard: ViewModifier {
    let map: GameMap

    func body(content: Content) -> some View {
        content
            // Keep the artwork square and the reading surface entirely inside it.
            .frame(width: 260, height: 260)
            .padding(45)
            .background {
                Rectangle()
                    // The frame has a transparent opening; an opaque reading
                    // surface prevents the underlying table from showing through.
                    .fill(Color(red: 0.075, green: 0.065, blue: 0.055))
                    .frame(width: 276, height: 276)
            }
            .overlay {
                Image(map.eventFrameAsset)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: 350, height: 350)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
    }
}

private struct PrivateActionFeedback: Equatable {
    let title: String
    let message: String
    let systemImage: String
}

private enum DawnAnnouncement: Equatable {
    case death(String)
    case silence(String)
    case peaceful
}

private extension GameMap {
    var landscapeAsset: String { "mapa_\(rawValue)" }
    var dayBackgroundAsset: String { "mapa_\(rawValue)_vertical_dia" }
    var nightBackgroundAsset: String { "mapa_\(rawValue)_vertical_noche" }
    var eventFrameAsset: String {
        switch self {
        case .pampa: "event_frame_pampa_modal"
        case .greece: "event_frame_greece_modal"
        case .medieval: "event_frame_medieval_modal"
        }
    }

    var exclusiveRoleTitle: String {
        switch self {
        case .pampa: "Payador"
        case .greece: "Oráculo"
        case .medieval: "Bufón"
        }
    }

    var lobbyDescription: String {
        switch self {
        case .pampa: "Sospechas en la pampa, el polvo del pueblo y la estación abandonada."
        case .greece: "Intriga entre templos, plazas y discursos que esconden traiciones."
        case .medieval: "Secretos entre murallas, castillos y un feudo que desconfía de todos."
        }
    }
}

private struct DayNightTransition: Equatable {
    enum Period { case day, night }

    let period: Period
    let round: Int
    let map: GameMap

    var key: String { "\(map.rawValue)-\(period)-\(round)" }
    var title: String { "\(period == .night ? "NOCHE" : "DÍA") \(round)" }
    var artwork: String { period == .night ? "transition_moon" : "transition_sun" }
    var leavingArtwork: String { period == .night ? "transition_sun" : "transition_moon" }
    var background: String { period == .night ? map.nightBackgroundAsset : map.dayBackgroundAsset }
    var previousBackground: String {
        period == .night ? map.dayBackgroundAsset : map.nightBackgroundAsset
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
    @State private var humanCardRevealed = false
    @State private var leaving = false
    @State private var privateFeedback: PrivateActionFeedback?
    @State private var dawnAnnouncements: [DawnAnnouncement] = []
    @State private var pendingTransition: DayNightTransition?
    @State private var activeTransition: DayNightTransition?
    @State private var lastTransitionKey: String?
    @State private var transitionTask: Task<Void, Never>?
    @State private var nightCountdownTask: Task<Void, Never>?
    @State private var nightSkipTask: Task<Void, Never>?
    @State private var remainingNightSeconds: Int?
    @State private var nightSkipReady = false

    var body: some View {
        if let game = store.game {
            ZStack {
                tableBackground(game)
                GeometryReader { geometry in
                    let availableSideWidth = min(
                        max(Int((geometry.size.width - 8 - 8 - 220) / 2), 54),
                        78
                    )
                    let footerInset: CGFloat = game.winner == nil ? 148 : 8
                    // Leave a visible lane between the last side card and the
                    // full-width player panel, especially at 13–15 players.
                    let availableSideHeight = max(
                        Int(geometry.size.height) - Int(footerInset) - 36,
                        1
                    )
                    let androidMetrics = ClassicCompanionMetrics.androidCompatible(
                        totalPlayers: game.players.count,
                        availableHeight: availableSideHeight,
                        availableWidth: availableSideWidth
                    )
                    let metrics = androidMetrics
                    let sides = sidePlayers(game)
                    let footerWidth = min(max(geometry.size.width - 24, 244), 372)

                    ZStack(alignment: .bottom) {
                        HStack(alignment: .top, spacing: 4) {
                            playerColumn(sides.left, game: game, metrics: metrics)
                            VStack(spacing: 4) {
                                tableHeader(game)
                                tableCenter(game)
                            }
                            .padding(4)
                            .background(TraidoresTheme.ink.opacity(0.42), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border, lineWidth: 1))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            // The table's old rounded panel is wider than the
                            // illustrated event frame. Keep it out of sight
                            // while an event is presented.
                            .opacity(privateFeedback != nil || !dawnAnnouncements.isEmpty ? 0 : 1)
                            playerColumn(sides.right, game: game, metrics: metrics)
                        }
                        .padding(.horizontal, 4)
                        .padding(.top, 8)
                        .padding(.bottom, footerInset)

                        if game.winner == nil {
                            humanPanel(game)
                                .frame(width: footerWidth)
                                .padding(.bottom, 8)
                                .zIndex(1)
                        }
                    }
                }

                if showingRole {
                    roleOverlay(game.human.role)
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                        .zIndex(2)
                }

                if let privateFeedback {
                    privateFeedbackOverlay(privateFeedback, map: game.map)
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                        .zIndex(3)
                }

                if let announcement = dawnAnnouncements.first {
                    dawnAnnouncementOverlay(announcement, map: game.map)
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
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
            .onChange(of: game.phaseIndex) { _, _ in
                selected = nil
                nightCountdownTask?.cancel()
                nightSkipTask?.cancel()
                remainingNightSeconds = nil
                nightSkipReady = false
                Task { @MainActor in
                    await Task.yield()
                    if let current = store.game { armNightPhaseIfReady(current) }
                }
            }
            .onChange(of: transitionSpec(for: game).key) { _, _ in
                queueTransition(for: game)
            }
            .onChange(of: privateFeedback) { _, feedback in
                if feedback == nil {
                    presentPendingTransition(using: game)
                    armNightPhaseIfReady(game)
                }
            }
            .onAppear { queueTransition(for: game) }
            .onDisappear {
                transitionTask?.cancel()
                nightCountdownTask?.cancel()
                nightSkipTask?.cancel()
            }
            .confirmationDialog("La partida queda guardada para continuar después.",
                                isPresented: $leaving, titleVisibility: .visible) {
                Button("Volver al menú") { dismissMatch() }
            }
            .animation(.easeInOut(duration: 0.18), value: showingRole)
        }
    }

    private func tableBackground(_ game: ClassicGame) -> some View {
        let spec = transitionSpec(for: game)
        let transitionUnfinished = game.phase != .assignment && (
            lastTransitionKey != spec.key || pendingTransition == spec || activeTransition == spec
        )
        // Keep the previous map visible until the transition finishes. This
        // also covers the frame between a night action and queuing the dawn
        // transition, including actions that auto-resolve bot-only phases.
        let showsNight = transitionUnfinished ? spec.period == .day : game.isNight
        return GeometryReader { geometry in
            Image(showsNight ? game.map.nightBackgroundAsset : game.map.dayBackgroundAsset)
                .resizable().scaledToFill()
                .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                // Android leaves the map legible around the table; darkness belongs on
                // the central play surface, not over the whole screen.
                .overlay(alignment: .top) {
                    LinearGradient(colors: [.black.opacity(0.22), .clear], startPoint: .top, endPoint: .bottom)
                        .frame(height: 90)
                }
        }
        .ignoresSafeArea().accessibilityHidden(true)
    }

    private func transitionSpec(for game: ClassicGame) -> DayNightTransition {
        .init(period: game.isNight ? .night : .day, round: game.round, map: game.map)
    }

    private func queueTransition(for game: ClassicGame) {
        // The assignment layer shows the table as a backdrop, but the first real
        // transition must wait until the player taps EMPEZAR and night begins.
        guard game.phase != .assignment else { return }
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
            if let current = store.game, current.isNight {
                startNightCountdown(for: current)
            }
            presentPendingTransition(using: game)
        }
    }

    private func startNightCountdown(for game: ClassicGame) {
        nightCountdownTask?.cancel()
        nightSkipTask?.cancel()
        nightSkipReady = false
        let phaseIndex = game.phaseIndex
        let duration = game.effectiveTiming.nightSeconds
        remainingNightSeconds = duration
        if game.legalTargets(for: 0).isEmpty {
            nightSkipTask = Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(game.testOptions.quickMatch ? 1_200 : 3_500))
                guard !Task.isCancelled, let current = store.game,
                      current.phaseIndex == phaseIndex, current.isNight,
                      current.legalTargets(for: 0).isEmpty else { return }
                if current.testOptions.quickMatch {
                    store.skipPassiveNight(revision: phaseIndex)
                } else {
                    nightSkipReady = true
                }
            }
        }
        nightCountdownTask = Task { @MainActor in
            for remaining in stride(from: duration - 1, through: 0, by: -1) {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled, let current = store.game,
                      current.phaseIndex == phaseIndex else { return }
                remainingNightSeconds = remaining
            }
            store.expireNight(revision: phaseIndex)
        }
    }

    private func armNightPhaseIfReady(_ game: ClassicGame) {
        guard game.isNight, activeTransition == nil, pendingTransition == nil,
              privateFeedback == nil,
              lastTransitionKey == transitionSpec(for: game).key,
              remainingNightSeconds == nil else { return }
        startNightCountdown(for: game)
    }

    private func transitionDuration(for game: ClassicGame) -> TimeInterval {
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-ui-testing-transition") { return 1.5 }
        if arguments.contains("-ui-testing") { return 0.08 }
        return TimeInterval(game.effectiveTiming.transitionSeconds)
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
        let roomy = game.players.count <= 8
        let relaxed = game.players.count <= 10
        let headerHeight: CGFloat = roomy ? 90 : (relaxed ? 82 : 76)
        let rowHeight: CGFloat = roomy ? 44 : 38
        let subtitleHeight = headerHeight - rowHeight - 5

        return VStack(spacing: 0) {
            HStack(spacing: 6) {
                Button { leaving = true } label: {
                    Image(systemName: "chevron.left").font(.caption.bold()).frame(width: 30, height: 30)
                        .background(TraidoresTheme.ink.opacity(0.84), in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.gold.opacity(0.62)))
                }
                .accessibilityLabel("Salir de la partida")
                VStack(alignment: .leading, spacing: 1) {
                    Text(phaseTitle(for: game).uppercased())
                        .font(TraidoresTheme.title(16)).foregroundStyle(TraidoresTheme.gold)
                        .lineLimit(1).minimumScaleFactor(0.7)
                        .accessibilityIdentifier("table.phaseTitle")
                    Text("Ronda \(game.round) · \(game.map.title)")
                        .font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                        .accessibilityIdentifier("table.mapName")
                }
                Spacer(minLength: 2)
                if game.isNight, let remainingNightSeconds {
                    Text("\(remainingNightSeconds)")
                        .font(.caption.bold()).monospacedDigit()
                        .frame(minWidth: 30, minHeight: 30)
                        .background(TraidoresTheme.ink.opacity(0.84),
                                    in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.gold.opacity(0.62)))
                }
                Button { showingRole = true } label: {
                    Image(systemName: "person.text.rectangle").font(.caption).frame(width: 30, height: 30)
                        .background(TraidoresTheme.ink.opacity(0.84), in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.gold.opacity(0.62)))
                }
                .accessibilityLabel("Ver mi rol")
            }
            .frame(height: rowHeight).padding(.horizontal, 6)
            Rectangle().fill(TraidoresTheme.border.opacity(0.82)).frame(height: 1)
            Text(instructions(game))
                .font(.caption2.weight(.semibold)).lineLimit(2).minimumScaleFactor(0.82)
                .frame(maxWidth: .infinity, minHeight: subtitleHeight, alignment: .leading)
                .padding(.horizontal, 7)
        }
        .frame(height: headerHeight)
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
        GeometryReader { columnGeometry in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: CGFloat(metrics.itemGap)) {
                    ForEach(players) { player in
                        sidePlayerCard(player, game: game, metrics: metrics)
                    }
                }
                .frame(
                    maxWidth: .infinity,
                    minHeight: metrics.scrollEnabled ? 0 : columnGeometry.size.height,
                    alignment: metrics.scrollEnabled ? .top : .center
                )
            }
            .scrollDisabled(!metrics.scrollEnabled)
        }
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
                        .overlay {
                            if actionable && game.isNight {
                                RoundedRectangle(cornerRadius: 5)
                                    .stroke(phaseAccent(game.phase).opacity(0.9), lineWidth: 1.5)
                            }
                        }
                        .overlay(alignment: .bottom) {
                            if actionable && game.isNight {
                                Text(game.phase == .assassinNight ? "MATAR" :
                                     game.phase == .mercenaryNight ? "SILENCIAR" :
                                     game.phase == .medicNight ? "SALVAR" : "INVESTIGAR")
                                    .font(.system(size: 8, weight: .heavy))
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 2)
                                    .background(phaseAccent(game.phase).opacity(0.9))
                                    .padding(.horizontal, 2).padding(.bottom, 2)
                            }
                        }
                        .overlay(alignment: .top) {
                            if !game.isNight, game.silencedPlayer == player.id, player.alive {
                                Text("MUDO")
                                    .font(.system(size: 8, weight: .heavy))
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .background(TraidoresTheme.ink.opacity(0.9))
                            }
                        }
                        .overlay {
                            if !player.alive {
                                Image("death_blood_splatter_art")
                                    .resizable().scaledToFit()
                                    .frame(width: CGFloat(metrics.cardWidth) * 0.85,
                                           height: CGFloat(metrics.cardHeight) * 0.8)
                            }
                        }
                }
                Text(player.name).font(.system(size: metrics.nameTextSize, weight: .bold))
                    .foregroundStyle(player.alive ? playerNameColor(player.id) : playerNameColor(player.id).opacity(0.62))
                    .strikethrough(!player.alive)
                    .lineLimit(1)
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
        .opacity(player.alive ? 1 : 0.72)
        .accessibilityIdentifier("table.player.\(player.id)")
    }

    private func publicCardImage(_ player: ClassicPlayer, game: ClassicGame) -> String {
        !player.alive && game.advanced.revealRolesOnDeath ? player.role.classicImage : "card_back_traidores"
    }

    private func playerNameColor(_ id: Int) -> Color {
        let palette: [(Double, Double, Double)] = [
            (0.831, 0.635, 0.306), (0.788, 0.431, 0.290), (0.561, 0.722, 0.416),
            (0.416, 0.651, 0.722), (0.722, 0.541, 0.847), (0.847, 0.537, 0.537),
            (0.784, 0.710, 0.416), (0.541, 0.690, 0.847)
        ]
        let color = palette[(max(id, 0)) % palette.count]
        return Color(red: color.0, green: color.1, blue: color.2)
    }

    private func roleCountName(_ role: RoleKey, count: Int) -> String {
        guard count > 1 else { return role.classicTitle }
        return switch role {
        case .villager: "Aldeanos"
        case .detective: "Detectives"
        case .medic: "Médicos"
        case .mayor: "Alcaldes"
        case .assassin: "Asesinos"
        case .mercenary: "Mercenarios"
        case .spy: "Espías"
        case .deserter: "Desertores"
        case .payador: "Payadores"
        case .jester: "Bufones"
        case .oracle: "Oráculos"
        }
    }

    @ViewBuilder
    private func tableCenter(_ game: ClassicGame) -> some View {
        GeometryReader { centerGeometry in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 8) {
                    if let winner = game.winner {
                        resultPanel(game, winner: winner)
                    } else {
                        compositionPanel(game)
                        phaseSummaryPanel(game)
                        if game.isNight {
                            nightPanel(game)
                                .frame(minHeight: max(285, centerGeometry.size.height - 115))
                        } else if game.phase == .discussion {
                            debatePanel(game)
                        } else if game.phase == .voteCount {
                            votePanel(game)
                        } else if game.phase == .result {
                            publicResultPanel(game)
                        } else if !game.legalTargets(for: 0).isEmpty {
                            targetPrompt(game)
                        } else {
                            phasePanel(game)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 5)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func compositionPanel(_ game: ClassicGame) -> some View {
        let counts = Dictionary(grouping: game.players, by: \.role).mapValues(\.count)
        let ordered: [RoleKey] = [.villager, .detective, .medic, .mayor, .assassin, .mercenary, .spy, .deserter, .payador, .jester, .oracle]
        let summary = ordered.compactMap { role -> String? in
            guard let count = counts[role], count > 0 else { return nil }
            return "\(count) \(roleCountName(role, count: count))"
        }.joined(separator: " · ")
        return Text("PARTIDA · \(summary)")
            .font(.system(size: 9, weight: .bold)).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity).padding(.horizontal, 5).padding(.vertical, 6)
            .background(TraidoresTheme.panel.opacity(0.95), in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(TraidoresTheme.border))
    }

    private func phaseSummaryPanel(_ game: ClassicGame) -> some View {
        let events = game.messages.filter { $0.round == game.round && $0.speaker == nil }
        let event = game.phase == .discussion
            ? events.first(where: { $0.text.contains("murió durante") || $0.text.contains("Amanece sin") })?.text
                ?? events.last?.text
            : events.last?.text
        return VStack(spacing: 3) {
            Text(game.isNight ? "NOCHE \(game.round)" : "DÍA \(game.round) · \(game.phase.classicTitle.uppercased())")
                .font(TraidoresTheme.title(15)).foregroundStyle(TraidoresTheme.gold)
                .lineLimit(1).minimumScaleFactor(0.75)
            Text(event ?? instructions(game))
                .font(.system(size: 10, weight: .semibold)).multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 7).padding(.horizontal, 6)
        .background(game.isNight ? TraidoresTheme.ink.opacity(0.96)
                                 : TraidoresTheme.panel.opacity(0.95),
                    in: RoundedRectangle(cornerRadius: 7))
        .overlay(RoundedRectangle(cornerRadius: 7).stroke(
            game.isNight ? phaseAccent(game.phase).opacity(0.75) : TraidoresTheme.border))
    }

    private func nightPanel(_ game: ClassicGame) -> some View {
        let traitorChat = [.assassin, .mercenary, .spy].contains(game.human.role)
        return VStack(spacing: 10) {
            Text(traitorChat ? "CHAT DE LOS ASESINOS" : "LA NOCHE")
                .font(TraidoresTheme.title(12))
                .foregroundStyle(phaseAccent(game.phase))
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 5) {
                Rectangle().fill(phaseAccent(game.phase).opacity(0.5)).frame(height: 1)
                Text("◆").font(.system(size: 8)).foregroundStyle(phaseAccent(game.phase))
                Rectangle().fill(phaseAccent(game.phase).opacity(0.5)).frame(height: 1)
            }
            Label("Noche \(game.round): comienza la noche.", systemImage: "moon.stars.fill")
                .font(.system(size: 10, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(8)
                .background(phaseAccent(game.phase).opacity(0.16),
                            in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(phaseAccent(game.phase).opacity(0.35)))
            Spacer(minLength: 28)
            Label(traitorChat ? "Hablen bajo. El pueblo no debe oírlos." :
                  "El pueblo duerme. Esperá el amanecer.",
                  systemImage: traitorChat ? "person.wave.2.fill" : "moon.fill")
                .font(.system(size: 10))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(8)
                .background(TraidoresTheme.ink.opacity(0.75),
                            in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(TraidoresTheme.border))
        }
        .padding(9)
        .frame(maxWidth: .infinity, minHeight: 285, alignment: .top)
        .background(TraidoresTheme.panel.opacity(0.95),
                    in: RoundedRectangle(cornerRadius: 9))
        .overlay(RoundedRectangle(cornerRadius: 9).stroke(phaseAccent(game.phase).opacity(0.72)))
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
        let canAdvance = game.isNight && targets.isEmpty
            ? nightSkipReady : (targets.isEmpty || targets.contains(selected ?? -1))
        return VStack(spacing: 5) {
            HStack(spacing: 8) {
                Button { humanCardRevealed.toggle() } label: {
                    Image(humanCardRevealed ? game.human.role.classicImage : "card_back_traidores")
                        .resizable().scaledToFill()
                        .frame(width: 48, height: 76).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(TraidoresTheme.border))
                }
                .buttonStyle(.plain)
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(game.human.name).font(.subheadline.bold()).foregroundStyle(TraidoresTheme.gold)
                            .lineLimit(1)
                        Text(humanCardRevealed ? game.human.role.classicTitle.uppercased() : "CARTA OCULTA")
                            .font(.caption2.bold()).foregroundStyle(TraidoresTheme.secondary)
                        Text(humanHint(game, canChooseSelf: canChooseSelf))
                            .font(.caption2).foregroundStyle(TraidoresTheme.secondary)
                            .lineLimit(2).minimumScaleFactor(0.82)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            HStack(spacing: 5) {
                Button(humanCardRevealed ? "OCULTAR CARTA" : "VER CARTA") { humanCardRevealed.toggle() }
                    .font(.system(size: 10, weight: .bold)).tracking(0.5)
                    .foregroundStyle(TraidoresTheme.text)
                    .frame(maxWidth: .infinity, minHeight: 32)
                    .background(TraidoresTheme.ink.opacity(0.88), in: RoundedRectangle(cornerRadius: 7))
                    .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.border))
                Button(actionTitle(game)) {
                    performPrimaryAction(game)
                }
                .font(.system(size: 10, weight: .heavy)).tracking(0.45)
                .foregroundStyle(game.isNight && !targets.isEmpty ? Color.white : TraidoresTheme.ink)
                .lineLimit(1).minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity, minHeight: 32)
                .background(game.isNight && !targets.isEmpty ? phaseAccent(game.phase) : TraidoresTheme.gold,
                            in: RoundedRectangle(cornerRadius: 7))
                .disabled(!canAdvance).opacity(canAdvance ? 1 : 0.52)
                .accessibilityIdentifier("table.primaryAction")
            }
        }
                .padding(6).frame(height: 132)
        .background(TraidoresTheme.panel.opacity(0.97), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border))
    }

    private func performPrimaryAction(_ game: ClassicGame) {
        if game.isNight && game.legalTargets(for: 0).isEmpty {
            guard nightSkipReady else { return }
            nightSkipReady = false
            store.skipPassiveNight(revision: game.phaseIndex)
            return
        }
        let target = selected
        let targetPlayer = target.flatMap { target in game.players.first { $0.id == target } }
        let feedback: PrivateActionFeedback? = switch (game.phase, targetPlayer) {
        case (.assassinNight, let player?):
            .init(
                title: "VÍCTIMA ELEGIDA",
                message: "Elegiste a \(player.name). El resultado se anunciará al amanecer.",
                systemImage: "moon.fill"
            )
        case (.mercenaryNight, let player?):
            .init(
                title: "SILENCIO REGISTRADO",
                message: "\(player.name) no podrá hablar ni votar durante el día.",
                systemImage: "speaker.slash.fill"
            )
        case (.detectiveNight, let player?):
            .init(
                title: "RESPUESTA PRIVADA",
                message: "\(player.name) parece \([RoleKey.assassin, .mercenary].contains(player.role) ? "SOSPECHOSO" : "INOCENTE").",
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
        if game.phase == .dawn, let updated = store.game, updated.phaseIndex != game.phaseIndex {
            if let victim = game.nightTarget,
               victim != game.protectedPlayer,
               !updated.players[victim].alive {
                dawnAnnouncements = [.death(updated.name(victim))]
            } else {
                dawnAnnouncements = [.peaceful]
            }
            if let silenced = game.silencedPlayer, updated.players[silenced].alive {
                dawnAnnouncements.append(.silence(updated.name(silenced)))
            }
        }
    }

    private func privateFeedbackOverlay(_ feedback: PrivateActionFeedback, map: GameMap) -> some View {
        let accent = eventAccent(for: feedback.systemImage)
        return ZStack {
            Color.black.opacity(0.72).ignoresSafeArea()
            VStack(spacing: 12) {
                EventSeal(symbol: feedback.systemImage, accent: accent)
                Text(feedback.title)
                    .font(TraidoresTheme.title(19)).foregroundStyle(accent)
                    .multilineTextAlignment(.center)
                Text(feedback.message)
                    .font(.subheadline.weight(.semibold)).multilineTextAlignment(.center)
                Button("CONTINUAR") { privateFeedback = nil }
                    .buttonStyle(TraidoresButtonStyle(prominent: false)).frame(maxWidth: .infinity)
                    .accessibilityIdentifier("table.dismissPrivateFeedback")
            }
            .frame(maxWidth: 260)
            .modifier(LocalEventCard(map: map))
            .padding(10)
        }
    }

    private func dawnAnnouncementOverlay(_ announcement: DawnAnnouncement, map: GameMap) -> some View {
        let accent = announcementAccent(announcement)
        return ZStack {
            Color.black.opacity(0.72).ignoresSafeArea()
            VStack(spacing: 8) {
                if case .death = announcement {
                    ZStack {
                        Image("card_back_traidores")
                            .resizable().scaledToFit().frame(width: 63, height: 86)
                            .rotationEffect(.degrees(-4))
                        Image("death_blood_splatter_art")
                            .resizable().scaledToFit().frame(width: 80, height: 80)
                    }
                    .frame(height: 90)
                } else {
                    EventSeal(symbol: announcementSymbol(announcement), accent: accent)
                }
                Text(announcementTitle(announcement))
                    .font(TraidoresTheme.title(17)).foregroundStyle(accent)
                    .multilineTextAlignment(.center)
                    .lineLimit(2).minimumScaleFactor(0.85)
                Text(announcementName(announcement))
                    .font(TraidoresTheme.title(19)).foregroundStyle(TraidoresTheme.text)
                    .multilineTextAlignment(.center)
                    .lineLimit(2).minimumScaleFactor(0.85)
                Text(announcementMessage(announcement))
                    .font(.subheadline).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
                Button("CONTINUAR") { dismissDawnAnnouncement() }
                    .buttonStyle(TraidoresButtonStyle()).frame(maxWidth: .infinity)
            }
            .frame(maxWidth: 260)
            .modifier(LocalEventCard(map: map))
            .padding(10)
        }
        .task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled, dawnAnnouncements.first == announcement else { return }
            dismissDawnAnnouncement()
        }
        .accessibilityIdentifier("table.dawnAnnouncement")
    }

    private func dismissDawnAnnouncement() {
        guard !dawnAnnouncements.isEmpty else { return }
        dawnAnnouncements.removeFirst()
    }

    private func announcementSymbol(_ announcement: DawnAnnouncement) -> String {
        switch announcement {
        case .death: "moon.fill"
        case .silence: "speaker.slash.fill"
        case .peaceful: "sunrise.fill"
        }
    }

    private func announcementAccent(_ announcement: DawnAnnouncement) -> Color {
        switch announcement {
        case .death: Color(red: 0.73, green: 0.20, blue: 0.25)
        case .silence: Color(red: 0.52, green: 0.24, blue: 0.33)
        case .peaceful: TraidoresTheme.gold
        }
    }

    private func eventAccent(for symbol: String) -> Color {
        switch symbol {
        case "cross.case.fill": phaseAccent(.medicNight)
        case "eye.fill": phaseAccent(.detectiveNight)
        case "speaker.slash.fill": phaseAccent(.mercenaryNight)
        default: phaseAccent(.assassinNight)
        }
    }

    private func announcementTitle(_ announcement: DawnAnnouncement) -> String {
        switch announcement {
        case .death: "AL AMANECER..."
        case .silence: "UNA VOZ FUE SILENCIADA"
        case .peaceful: "AL AMANECER..."
        }
    }

    private func announcementName(_ announcement: DawnAnnouncement) -> String {
        switch announcement {
        case .death(let name), .silence(let name): name.uppercased()
        case .peaceful: "NADIE MURIÓ"
        }
    }

    private func announcementMessage(_ announcement: DawnAnnouncement) -> String {
        switch announcement {
        case .death: "Murió durante la noche"
        case .silence: "No puede hablar ni votar durante el día"
        case .peaceful: "El pueblo despierta sin víctimas"
        }
    }

    private func humanHint(_ game: ClassicGame, canChooseSelf: Bool) -> String {
        if !game.human.alive { return "Estás eliminado. Podés observar la mesa." }
        if game.isNight && game.legalTargets(for: 0).isEmpty {
            return "No actuás ahora. Esperá o saltá hasta tu turno o el amanecer."
        }
        if game.silencedPlayer == 0, !game.isNight { return "Hoy no podés hablar ni votar." }
        if game.phase == .assassinNight && selected == nil {
            return "Los Traidores se mueven en silencio."
        }
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
            HStack(spacing: 5) {
                Rectangle().fill(TraidoresTheme.border).frame(height: 1)
                Text("◆").font(.system(size: 8)).foregroundStyle(TraidoresTheme.gold)
                Rectangle().fill(TraidoresTheme.border).frame(height: 1)
            }
            Text("CHAT DEL PUEBLO").font(.caption.weight(.bold)).tracking(1.2)
                .foregroundStyle(TraidoresTheme.gold)
            ForEach(game.messages.filter { $0.round == game.round }.suffix(6)) { message in
                if let speaker = message.speaker {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(game.name(speaker)).font(.caption.bold()).foregroundStyle(playerNameColor(speaker))
                        Text(message.text).font(.system(size: 11))
                    }
                    .padding(8).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.black.opacity(0.20), in: RoundedRectangle(cornerRadius: 7))
                } else {
                    Label(message.text, systemImage: "moon.stars.fill")
                        .font(.system(size: 10, weight: .semibold)).foregroundStyle(TraidoresTheme.text)
                        .padding(8).frame(maxWidth: .infinity, alignment: .leading)
                        .background(TraidoresTheme.ink.opacity(0.85), in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).stroke(TraidoresTheme.border))
                }
            }
            if game.human.alive && game.silencedPlayer != 0 && !game.humanSpoke {
                Menu("SEÑALAR UNA SOSPECHA") {
                    ForEach(game.living.filter { $0.id != 0 }) { player in
                        Button(player.name) { store.accuse(player.id, revision: game.phaseIndex) }
                    }
                }
                .buttonStyle(TraidoresButtonStyle())
            }
            if game.human.alive && game.silencedPlayer != 0 &&
                !game.humanInvestigations.isEmpty && !game.humanSharedRead {
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
                Text([RoleKey.assassin, .mercenary, .spy].contains(role) ? "TRAIDORES" : "PUEBLO")
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
        if game.isNight && game.legalTargets(for: 0).isEmpty {
            return "No actuás en esta fase. Podés esperar o saltarla en unos segundos."
        }
        if game.silencedPlayer == 0, !game.isNight {
            return "Una voz fue silenciada: hoy no podés hablar ni votar."
        }
        let allies = game.players.filter { $0.role == .mercenary && $0.id != 0 }.map(\.name)
        return switch game.phase {
        case .assassinNight:
            allies.isEmpty ? "Elegí una víctima y confirmá MATAR." :
                "Elegí una víctima y confirmá MATAR. Aliados: \(allies.joined(separator: ", "))."
        case .mercenaryNight: "Elegí a quién silenciar. No podrá hablar ni votar durante el día."
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

    private func phaseTitle(for game: ClassicGame) -> String {
        if game.isNight { return "Noche \(game.round)" }
        if game.phase == .dawn { return "Amanece en \(game.map.title)" }
        return game.phase.classicTitle
    }

    private func playerStatus(_ player: ClassicPlayer, game: ClassicGame) -> String {
        if player.alive { return "EN LA MESA" }
        return game.advanced.revealRolesOnDeath
            ? "ELIMINADO · \(player.role.classicTitle.uppercased())"
            : "ELIMINADO"
    }

    private func actionTitle(_ game: ClassicGame) -> String {
        if game.isNight && game.legalTargets(for: 0).isEmpty {
            return nightSkipReady ? "SALTAR NOCHE" : "ESPERAR"
        }
        if !game.legalTargets(for: 0).isEmpty {
            return switch game.phase {
            case .assassinNight: selected == nil ? "ELEGIR OBJETIVO" : "MATAR"
            case .mercenaryNight: selected == nil ? "ELEGIR OBJETIVO" : "SILENCIAR"
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

    private func phaseAccent(_ phase: GamePhase) -> Color {
        switch phase {
        case .assassinNight: Color(red: 0.73, green: 0.20, blue: 0.25)
        case .mercenaryNight: Color(red: 0.52, green: 0.24, blue: 0.33)
        case .detectiveNight: Color(red: 0.29, green: 0.50, blue: 0.71)
        case .medicNight: Color(red: 0.35, green: 0.56, blue: 0.27)
        case .oracleNight: Color(red: 0.54, green: 0.37, blue: 0.75)
        default: TraidoresTheme.gold
        }
    }
}

private extension RoleKey {
    var classicTitle: String {
        switch self {
        case .detective: "Comisario"
        case .assassin: "Asesino"
        case .mercenary: "Mercenario"
        case .medic: "Médico"
        default: "Aldeano"
        }
    }

    var classicImage: String {
        switch self {
        case .detective: "rol_detective_gaucho"
        case .assassin: "rol_asesino_gaucho"
        case .mercenary: "rol_mercenario_gaucho"
        case .medic: "rol_medico_gaucho"
        default: "rol_aldeano_gaucho"
        }
    }

    var classicAdvice: String {
        switch self {
        case .assassin: "Mezclate con el Pueblo. Acusá con cuidado y evitá que tus votos revelen un patrón."
        case .mercenary: "Elegí bien a quién silenciar: durante el día no podrá hablar ni votar."
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
        case .assassinNight, .mercenaryNight, .detectiveNight, .medicNight: "La noche"
        case .dawn: "Amanece"
        case .discussion: "Debate del pueblo"
        case .voting: "Votación"
        case .tieVote: "Desempate"
        case .voteCount: "Recuento de votos"
        case .result: "Resultado"
        default: "Partida"
        }
    }
}
