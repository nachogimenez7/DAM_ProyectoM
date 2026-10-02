import SwiftUI

@main
struct TraidoresApp: App {
    @State private var preferences = MenuPreferences()
    @State private var audio = MenuAudio()
    @State private var introFinished = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ZStack {
                MenuView()
                    .accessibilityHidden(!introFinished)
                if !introFinished {
                    BandidoIntroView(audio: audio) { introFinished = true }
                }
            }
                .environment(preferences)
                .defaultAppStorage(.menuStore)
                .preferredColorScheme(.dark)
                .tint(TraidoresTheme.gold)
                .statusBarHidden(!introFinished)
                .onChange(of: preferences.musicEnabled, initial: true) { _, _ in
                    updateAudio()
                }
                .onChange(of: preferences.gameplayActive) { _, _ in updateAudio() }
                .onChange(of: preferences.musicVolume) { _, _ in updateAudio() }
                .onChange(of: scenePhase) { _, _ in updateAudio() }
                .onChange(of: introFinished) { _, _ in updateAudio() }
        }
    }

    private func updateAudio() {
        if scenePhase != .active { audio.stopIntroBark() }
        audio.setPlaying(introFinished && preferences.musicEnabled && !preferences.gameplayActive && scenePhase == .active,
                         volume: preferences.musicVolume)
    }
}

private struct BandidoIntroView: View {
    let audio: MenuAudio
    let finished: () -> Void
    @Environment(MenuPreferences.self) private var preferences
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var frame = "idle"
    @State private var logoOpacity = 0.0
    @State private var scale = 0.88
    @State private var offset = 0.0
    @State private var rotation = 0.0
    @State private var pulse = 0.0
    @State private var opacity = 1.0

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 9/255, green: 9/255, blue: 9/255)
                // Keep all frames loaded so the mouth change cannot interrupt playback.
                ForEach(["idle", "bark_soft", "bark_open"], id: \.self) { image in
                    Image("bandido_games_intro_\(image)_portrait")
                        .resizable().scaledToFit()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .opacity(frame == image ? logoOpacity : 0)
                }
                .scaleEffect(reduceMotion ? 1 : scale)
                .offset(y: reduceMotion ? 0 : offset)
                .rotationEffect(.degrees(reduceMotion ? 0 : rotation))
                LinearGradient(colors: [.clear, TraidoresTheme.gold.opacity(0.13), .clear], startPoint: .bottom, endPoint: .top)
                    .opacity(pulse)
            }
        }
        .ignoresSafeArea().opacity(opacity)
        .contentShape(Rectangle()).onTapGesture {}
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Bandido Games")
        .accessibilityIdentifier("intro.bandido")
        .task {
            if preferences.effectsEnabled && preferences.effectsVolume > 0 { audio.prepareIntro() }
            do {
                let clock = ContinuousClock()
                let start = clock.now
                func wait(_ milliseconds: Int) async throws {
                    try await clock.sleep(until: start.advanced(by: .milliseconds(milliseconds)))
                }
                withAnimation(.spring(duration: 0.42, bounce: 0.12)) { logoOpacity = 1; scale = 1 }
                try await wait(620)
                if preferences.effectsEnabled && scenePhase == .active {
                    audio.playIntroBark(volume: preferences.effectsVolume)
                }
                withAnimation(.easeOut(duration: 0.115)) { scale = 1.045; offset = -24; rotation = -1.2 }
                try await wait(675); frame = "bark_soft"
                try await wait(735); withAnimation(.spring(duration: 0.165, bounce: 0.2)) { scale = 1.018; offset = -7; rotation = 0 }
                try await wait(740); frame = "bark_open"
                try await wait(930); frame = "bark_soft"
                try await wait(950); withAnimation(.easeOut(duration: 0.22)) { scale = 1; offset = 0; rotation = 0 }
                try await wait(1005); frame = "idle"
                try await wait(1170); withAnimation(.easeInOut(duration: 0.12)) { scale = 1.015 }
                try await wait(1260); withAnimation(.easeInOut(duration: 0.12)) { pulse = 1 }
                try await wait(1290); withAnimation(.easeInOut(duration: 0.14)) { scale = 1 }
                try await wait(1380); withAnimation(.easeOut(duration: 0.3)) { pulse = 0 }
                try await wait(1900); withAnimation(.easeOut(duration: 0.3)) { opacity = 0 }
                try await wait(2200)
                finished()
            } catch { audio.stopIntroBark() }
        }
        .onDisappear { audio.stopIntroBark() }
    }
}
