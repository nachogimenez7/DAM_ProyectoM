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

    func start(name: String, difficulty: BotDifficulty, botNames: [String],
               timing: GameTimingConfig, trainingRole: RoleKey? = nil) {
        game = ClassicGame(name: name, trainingRole: trainingRole, difficulty: difficulty,
                           timing: timing, botNames: botNames)
        save()
    }

    func advance(target: Int?, revision: Int) {
        guard var current = game, current.advance(target: target, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func accuse(_ target: Int, revision: Int) {
        guard var current = game, current.accuse(target, expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    func shareRead(revision: Int) {
        guard var current = game, current.shareInvestigation(expectedPhaseIndex: revision) else { return }
        game = current
        save()
    }

    private func save() {
        guard let game else { return }
        do { defaults.set(try ClassicSave.encode(game), forKey: saveKey); errorMessage = nil }
        catch { errorMessage = "No se pudo guardar el progreso de esta partida." }
    }
}
