import Foundation
import Observation
import TraidoresCore

@MainActor @Observable
final class LocalGameStore {
    private(set) var game: ClassicGame?
    var errorMessage: String?
    private let defaults: UserDefaults
    private let saveKey = "local.classic.save.v2"
    private let startedKey = "local.classic.startedAt"
    private let finishedKey = "local.classic.finishedAt"
    private(set) var startedAt: Date?
    private(set) var finishedAt: Date?

    var durationLabel: String {
        guard let startedAt else { return "—" }
        let seconds = max(0, Int((finishedAt ?? Date()).timeIntervalSince(startedAt)))
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard let data = defaults.data(forKey: saveKey) else { return }
        do {
            game = try ClassicSave.decode(data)
            startedAt = defaults.object(forKey: startedKey) as? Date
            finishedAt = defaults.object(forKey: finishedKey) as? Date
        }
        catch { errorMessage = "No se pudo recuperar la partida guardada. Podés comenzar una nueva." }
    }

    func start(name: String, map: GameMap = .pampa, difficulty: BotDifficulty, botNames: [String],
               timing: GameTimingConfig, advanced: AdvancedGameConfig,
               testOptions: LocalTestOptions = .standard, trainingRole: RoleKey? = nil,
               seed: UInt64 = .random(in: .min ... .max)) {
        game = ClassicGame(name: name, seed: seed, trainingRole: trainingRole, map: map, difficulty: difficulty,
                           timing: timing, advanced: advanced, testOptions: testOptions,
                           botNames: botNames)
        startedAt = Date()
        finishedAt = nil
        save()
    }

    func advance(target: Int?, revision: Int) {
        guard var current = game, current.advance(target: target, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func expireNight(revision: Int) {
        guard var current = game, current.expireNight(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func skipPassiveNight(revision: Int) {
        guard var current = game, current.skipPassiveNight(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func expireVoting(revision: Int) {
        guard var current = game, current.expireVoting(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func accuse(_ target: Int, revision: Int) {
        guard var current = game, current.accuse(target, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func sendPublicMessage(_ text: String, revision: Int) {
        guard var current = game,
              current.sendPublicMessage(text, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func sendTraitorMessage(_ text: String, revision: Int) {
        guard var current = game,
              current.sendTraitorMessage(text, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func shareRead(revision: Int) {
        guard var current = game, current.shareInvestigation(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func cancel() {
        game = nil
        errorMessage = nil
        defaults.removeObject(forKey: saveKey)
        startedAt = nil
        finishedAt = nil
        defaults.removeObject(forKey: startedKey)
        defaults.removeObject(forKey: finishedKey)
    }

    private func save() {
        guard let game else { return }
        if game.winner != nil, finishedAt == nil, startedAt != nil { finishedAt = Date() }
        do {
            defaults.set(try ClassicSave.encode(game), forKey: saveKey)
            defaults.set(startedAt, forKey: startedKey)
            defaults.set(finishedAt, forKey: finishedKey)
            errorMessage = nil
        }
        catch { errorMessage = "No se pudo guardar el progreso de esta partida." }
    }
}
