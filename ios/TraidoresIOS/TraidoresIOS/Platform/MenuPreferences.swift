import Foundation
import Observation

/// Android's "Tamaño del texto": Compacto, Normal (default) and Grande.
/// `normal` keeps the stored raw value "system" from earlier versions.
enum MenuTextSize: String, CaseIterable, Identifiable {
    case compact, system, large
    var id: String { rawValue }
    var title: String {
        switch self {
        case .compact: "Compacto"
        case .system: "Normal"
        case .large: "Grande"
        }
    }
    init?(stored: String) {
        if stored == "extraLarge" { self = .large } else { self.init(rawValue: stored) }
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

    /// Android: "Vibración al interactuar", off by default.
    var vibrationEnabled: Bool {
        didSet { defaults.set(vibrationEnabled, forKey: "menu.vibrationEnabled") }
    }

    /// Android: "Reducir animaciones". Combined with the system's Reduce Motion.
    var reduceAnimations: Bool {
        didSet { defaults.set(reduceAnimations, forKey: "menu.reduceAnimations") }
    }

    func resetMenuOptions() {
        musicEnabled = true
        musicVolume = 0.8
        effectsEnabled = true
        effectsVolume = 0.8
        textSize = .system
        vibrationEnabled = false
        reduceAnimations = false
    }

    init(defaults: UserDefaults = .menuStore) {
        self.defaults = defaults
        effectsEnabled = defaults.object(forKey: "menu.effectsEnabled") as? Bool ?? true
        effectsVolume = min(max(defaults.object(forKey: "menu.effectsVolume") as? Double ?? 0.8, 0), 1)
        musicEnabled = defaults.object(forKey: "menu.musicEnabled") as? Bool ?? true
        musicVolume = min(max(defaults.object(forKey: "menu.musicVolume") as? Double ?? 0.8, 0), 1)
        textSize = MenuTextSize(stored: defaults.string(forKey: "menu.textSize") ?? "") ?? .system
        vibrationEnabled = defaults.object(forKey: "menu.vibrationEnabled") as? Bool ?? false
        reduceAnimations = defaults.object(forKey: "menu.reduceAnimations") as? Bool ?? false
    }
}
