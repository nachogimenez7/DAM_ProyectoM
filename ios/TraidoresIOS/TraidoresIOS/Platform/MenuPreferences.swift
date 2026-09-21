import Foundation
import Observation

@MainActor @Observable
final class MenuPreferences {
    private let defaults: UserDefaults

    var gameplayActive = false

    var musicEnabled: Bool {
        didSet { defaults.set(musicEnabled, forKey: "menu.musicEnabled") }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        musicEnabled = defaults.object(forKey: "menu.musicEnabled") as? Bool ?? true
    }
}
