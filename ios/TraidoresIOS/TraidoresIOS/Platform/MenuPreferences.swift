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

extension UserDefaults {
    /// UI tests use their own suite so they never touch the player's real menu, profile or options.
    @MainActor static let menuStore: UserDefaults = {
        guard ProcessInfo.processInfo.arguments.contains("-ui-testing") else { return .standard }
        return UserDefaults(suiteName: "com.traidores.juego.ios.ui-testing") ?? .standard
    }()
}

@MainActor @Observable
final class MenuPreferences {
    private let defaults: UserDefaults

    var gameplayActive = false

    var effectsEnabled: Bool {
        didSet { defaults.set(effectsEnabled, forKey: "menu.effectsEnabled") }
    }

    var effectsVolume: Double {
        didSet { defaults.set(effectsVolume, forKey: "menu.effectsVolume") }
    }

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
        effectsEnabled = true
        effectsVolume = 0.8
        textSize = .system
    }

    init(defaults: UserDefaults = .menuStore) {
        self.defaults = defaults
        effectsEnabled = defaults.object(forKey: "menu.effectsEnabled") as? Bool ?? true
        effectsVolume = min(max(defaults.object(forKey: "menu.effectsVolume") as? Double ?? 0.8, 0), 1)
        musicEnabled = defaults.object(forKey: "menu.musicEnabled") as? Bool ?? true
        musicVolume = min(max(defaults.object(forKey: "menu.musicVolume") as? Double ?? 0.8, 0), 1)
        textSize = MenuTextSize(rawValue: defaults.string(forKey: "menu.textSize") ?? "") ?? .system
    }
}
