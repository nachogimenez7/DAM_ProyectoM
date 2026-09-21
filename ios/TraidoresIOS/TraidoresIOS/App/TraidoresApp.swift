import SwiftUI

@main
struct TraidoresApp: App {
    @State private var preferences = MenuPreferences()
    @State private var audio = MenuAudio()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MenuView()
                .environment(preferences)
                .preferredColorScheme(.dark)
                .tint(TraidoresTheme.gold)
                .onChange(of: preferences.musicEnabled, initial: true) { _, _ in
                    updateAudio()
                }
                .onChange(of: preferences.gameplayActive) { _, _ in updateAudio() }
                .onChange(of: scenePhase) { _, _ in updateAudio() }
        }
    }

    private func updateAudio() {
        audio.setPlaying(preferences.musicEnabled && !preferences.gameplayActive && scenePhase == .active)
    }
}
