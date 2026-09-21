import AVFoundation
import Observation
import OSLog

@MainActor @Observable
final class MenuAudio {
    private var player: AVAudioPlayer?
    private let logger = Logger(subsystem: "com.traidores.juego.ios", category: "audio")

    func setPlaying(_ shouldPlay: Bool) {
        guard shouldPlay else {
            player?.pause()
            return
        }
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
                newPlayer.volume = 0.35
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
