import Foundation
import Observation

enum MenuTextSize: String, CaseIterable, Identifiable {
    case system, large, extraLarge
    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: "Según el iPhone"
        case .large: "Grande"
        case .extraLarge: "Muy grande"
        }
    }
}

@MainActor @Observable
final class MenuPreferences {
    private let defaults: UserDefaults

    var gameplayActive = false

    var musicEnabled: Bool {
        didSet { defaults.set(musicEnabled, forKey: "menu.musicEnabled") }
    }

    var musicVolume: Double {
        didSet { defaults.set(musicVolume, forKey: "menu.musicVolume") }
    }

    var textSize: MenuTextSize {
        didSet { defaults.set(textSize.rawValue, forKey: "menu.textSize") }
    }

    func resetMenuOptions() {
        musicEnabled = true
        musicVolume = 0.8
        textSize = .system
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        musicEnabled = defaults.object(forKey: "menu.musicEnabled") as? Bool ?? true
        musicVolume = min(max(defaults.object(forKey: "menu.musicVolume") as? Double ?? 0.8, 0), 1)
        textSize = MenuTextSize(rawValue: defaults.string(forKey: "menu.textSize") ?? "") ?? .system
    }
}
