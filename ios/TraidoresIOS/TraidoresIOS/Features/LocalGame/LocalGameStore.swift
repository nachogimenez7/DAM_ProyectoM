import Foundation
import Observation
import TraidoresCore

@MainActor @Observable
final class LocalGameStore {
    private(set) var game: ClassicGame?
    var errorMessage: String?
    private let defaults: UserDefaults
    private let saveKey = "local.classic.save.v2"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard let data = defaults.data(forKey: saveKey) else { return }
        do { game = try ClassicSave.decode(data) }
        catch { errorMessage = "No se pudo recuperar la partida guardada. Podés comenzar una nueva." }
    }

    func start(name: String, map: GameMap = .pampa, difficulty: BotDifficulty, botNames: [String],
               timing: GameTimingConfig, advanced: AdvancedGameConfig,
               testOptions: LocalTestOptions = .standard, trainingRole: RoleKey? = nil) {
        game = ClassicGame(name: name, trainingRole: trainingRole, map: map, difficulty: difficulty,
                           timing: timing, advanced: advanced, testOptions: testOptions,
                           botNames: botNames)
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

    func shareRead(revision: Int) {
        guard var current = game, current.shareInvestigation(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func cancel() {
        game = nil
        errorMessage = nil
        defaults.removeObject(forKey: saveKey)
    }

    private func save() {
        guard let game else { return }
        do { defaults.set(try ClassicSave.encode(game), forKey: saveKey); errorMessage = nil }
        catch { errorMessage = "No se pudo guardar el progreso de esta partida." }
    }
}
