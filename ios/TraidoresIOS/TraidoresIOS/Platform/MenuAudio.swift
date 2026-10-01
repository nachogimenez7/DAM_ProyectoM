import AVFoundation
import Observation
import OSLog

@MainActor @Observable
final class MenuAudio {
    private let playback = MenuAudioPlayback()

    func prepareIntro() { playback.prepareIntro() }
    func playIntroBark(volume: Double) { playback.playIntroBark(volume: volume) }
    func stopIntroBark() { playback.stopIntroBark() }
    func setPlaying(_ shouldPlay: Bool, volume: Double = 0.8) {
        playback.setPlaying(shouldPlay, volume: volume)
    }
}

// AVAudioSession activation can wait on the audio device. Confine players and
// their session to one queue so startup animations never wait for that work.
private final class MenuAudioPlayback: @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.traidores.juego.ios.menu-audio")
    private var player: AVAudioPlayer?
    private var barkPlayer: AVAudioPlayer?
    private let logger = Logger(subsystem: "com.traidores.juego.ios", category: "audio")

    func prepareIntro() {
        queue.async { [self] in prepareIntroOnQueue() }
    }

    private func prepareIntroOnQueue() {
        do {
            guard let url = Bundle.main.url(forResource: "sfx_bandido_bark", withExtension: "wav") else { return }
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            barkPlayer = try AVAudioPlayer(contentsOf: url)
            barkPlayer?.prepareToPlay()
        } catch {
            logger.error("No se pudo preparar el ladrido: \(error.localizedDescription, privacy: .public)")
        }
    }

    func playIntroBark(volume: Double) {
        let deadline = DispatchTime.now() + .milliseconds(150)
        queue.async { [self] in
            // Do not play a delayed bark over the menu if the audio device was slow.
            guard DispatchTime.now() <= deadline else { return }
            playIntroBarkOnQueue(volume: volume)
        }
    }

    private func playIntroBarkOnQueue(volume: Double) {
        guard volume > 0 else { return }
        do {
            try AVAudioSession.sharedInstance().setActive(true)
            barkPlayer?.volume = Float(min(max(volume, 0), 1))
            barkPlayer?.play()
        } catch {
            logger.error("No se pudo reproducir el ladrido: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stopIntroBark() { queue.async { [self] in barkPlayer?.stop() } }

    func setPlaying(_ shouldPlay: Bool, volume: Double = 0.8) {
        queue.async { [self] in setPlayingOnQueue(shouldPlay, volume: volume) }
    }

    private func setPlayingOnQueue(_ shouldPlay: Bool, volume: Double) {
        player?.volume = Float(min(max(volume, 0), 1))
        guard shouldPlay else {
            player?.pause()
            return
        }
        // Moving the volume slider must not reactivate the audio session each time.
        if player?.isPlaying == true { return }
        do {
            if player == nil {
                guard let url = Bundle.main.url(forResource: "menu_music", withExtension: "mp3") else {
                    logger.error("No se encontró la música del menú.")
                    return
                }
                // Respect silent mode and other apps' audio; no background audio entitlement.
                try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
                let newPlayer = try AVAudioPlayer(contentsOf: url)
                newPlayer.numberOfLoops = -1
                newPlayer.volume = Float(min(max(volume, 0), 1))
                newPlayer.prepareToPlay()
                player = newPlayer
            }
            try AVAudioSession.sharedInstance().setActive(true)
            player?.play()
        } catch {
            logger.error("No se pudo reproducir la música: \(error.localizedDescription, privacy: .public)")
        }
    }
}
