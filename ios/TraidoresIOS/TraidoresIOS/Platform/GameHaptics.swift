import UIKit

/// Android's `HapticLevel` and `GameplayFeedbackCue`: a light tap, a medium one and the
/// strong double pulse (60 ms, pause, 80 ms) of deaths and expulsions. Only with
/// "Vibración al interactuar" on, independent of the sound volume, like Android.
enum GameHaptic {
    case light, medium, strong

    @MainActor
    func play() {
        switch self {
        case .light: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .medium: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .strong:
            let generator = UIImpactFeedbackGenerator(style: .heavy)
            generator.impactOccurred()
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(100))
                generator.impactOccurred(intensity: 1)
            }
        }
    }
}

extension GameAudio.Effect {
    /// The vibration Android's `GameSound` pairs with each sound.
    var haptic: GameHaptic {
        switch self {
        case .elimination, .expulsion: .strong
        case .silence, .tieBreak, .oracle, .payador, .jester: .medium
        case .cardDeal, .nightFall, .dawn, .noDeath, .voteCast: .light
        }
    }
}
