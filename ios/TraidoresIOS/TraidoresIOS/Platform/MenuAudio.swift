import AVFoundation
import Observation
import OSLog

@MainActor @Observable
final class MenuAudio {
    private var player: AVAudioPlayer?
    private let logger = Logger(subsystem: "com.traidores.juego.ios", category: "audio")

    func setPlaying(_ shouldPlay: Bool, volume: Double = 0.8) {
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
