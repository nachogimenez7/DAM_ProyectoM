import AVFoundation
import Observation
import OSLog
import TraidoresCore

/// Match audio, mirroring Android's `MusicManager` and `GameplayAudioDirector`: one
/// looping track (day music per map, night music), the victory music once, and short
/// effects for the key moments. Files live in the `GameAudio` folder of the bundle.
@MainActor @Observable
final class GameAudio {
    enum Music: Equatable {
        case day(GameMap), night, victory(townWon: Bool)

        fileprivate var resource: String {
            switch self {
            case .day(.greece): "day_music_greece"
            case .day(.medieval): "day_music_medieval"
            case .day: "day_music_pampa"
            case .night: "night_phase_music"
            case .victory(let townWon): townWon ? "victory_music_town" : "victory_music_traitors"
            }
        }

        fileprivate var loops: Bool {
            if case .victory = self { return false }
            return true
        }
    }

    /// Android's `GameSound`, with the same files and relative volumes.
    enum Effect: CaseIterable {
        case cardDeal, nightFall, dawn, elimination, expulsion, silence, noDeath, voteCast, tieBreak

        fileprivate var resource: (name: String, ext: String) {
            switch self {
            case .cardDeal: ("sfx_card_deal", "mp3")
            case .nightFall: ("sfx_night_fall", "mp3")
            case .dawn: ("sfx_dawn", "mp3")
            case .elimination: ("sfx_death_elevenlabs", "mp3")
            case .expulsion: ("sfx_expulsion", "mp3")
            case .silence: ("sfx_elimination", "mp3")
            case .noDeath: ("sfx_no_death", "wav")
            case .voteCast: ("sfx_vote_cast", "mp3")
            case .tieBreak: ("sfx_tie_break", "mp3")
            }
        }

        /// Every file is already levelled to the same loudness (`scripts/audio_levels.py`),
        /// so no per-effect multiplier is needed.
        fileprivate var relativeVolume: Float { 1 }
    }

    @ObservationIgnored private let playback = GameAudioPlayback()

    /// `nil` pauses the music, keeping its position (Android pauses it for transitions
    /// and announcements, then resumes the track of the current period).
    func setMusic(_ music: Music?, volume: Double) {
        playback.setMusic(music, volume: Float(min(max(volume, 0), 1)))
    }

    func play(_ effect: Effect, volume: Double, scale: Float = 1, rate: Float = 1) {
        guard volume > 0 else { return }
        playback.play(effect, volume: Float(min(max(volume, 0), 1)) * scale, rate: rate)
    }

    func stop() { playback.stop() }
}

// AVAudioSession and player setup can block on the audio device, so everything
// runs on one serial queue (like the menu audio) and never stalls an animation.
private final class GameAudioPlayback: @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.traidores.juego.ios.game-audio")
    private let logger = Logger(subsystem: "com.traidores.juego.ios", category: "game-audio")
    private var music: GameAudio.Music?
    private var musicPlayer: AVAudioPlayer?
    private var musicGeneration = 0
    private var effectData: [String: Data] = [:]
    private var effectPlayers: [AVAudioPlayer] = []

    func setMusic(_ requested: GameAudio.Music?, volume: Float) {
        queue.async { [self] in setMusicOnQueue(requested, volume: volume) }
    }

    func play(_ effect: GameAudio.Effect, volume: Float, rate: Float) {
        queue.async { [self] in playOnQueue(effect, volume: volume, rate: rate) }
    }

    func stop() {
        queue.async { [self] in
            musicGeneration += 1
            musicPlayer?.stop()
            musicPlayer = nil
            music = nil
            effectPlayers.forEach { $0.stop() }
            effectPlayers.removeAll()
        }
    }

    private func setMusicOnQueue(_ requested: GameAudio.Music?, volume: Float) {
        musicGeneration += 1
        let generation = musicGeneration
        guard let requested, volume > 0 else {
            fadeOutAndPause(generation: generation)
            return
        }
        if requested == music, let player = musicPlayer {
            if !player.isPlaying {
                // A finished victory track stays finished; looping tracks resume.
                guard requested.loops || player.currentTime > 0 else { return }
                activate()
                player.volume = 0
                player.play()
            }
            player.setVolume(volume, fadeDuration: 0.6)
            return
        }
        musicPlayer?.stop()
        musicPlayer = nil
        music = requested
        guard let url = Self.url(requested.resource, "mp3") else {
            logger.error("Falta la música \(requested.resource, privacy: .public).")
            return
        }
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.numberOfLoops = requested.loops ? -1 : 0
            player.volume = 0
            player.prepareToPlay()
            activate()
            player.play()
            player.setVolume(volume, fadeDuration: 0.8)
            musicPlayer = player
        } catch {
            logger.error("No se pudo reproducir la música: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func fadeOutAndPause(generation: Int) {
        guard let player = musicPlayer, player.isPlaying else { return }
        player.setVolume(0, fadeDuration: 0.3)
        queue.asyncAfter(deadline: .now() + 0.32) { [self] in
            guard generation == musicGeneration else { return }
            player.pause()
        }
    }

    private func playOnQueue(_ effect: GameAudio.Effect, volume: Float, rate: Float) {
        let (name, ext) = effect.resource
        effectPlayers.removeAll { !$0.isPlaying }
        do {
            let data: Data
            if let cached = effectData[name] {
                data = cached
            } else {
                guard let url = Self.url(name, ext) else {
                    logger.error("Falta el efecto \(name, privacy: .public).")
                    return
                }
                data = try Data(contentsOf: url)
                effectData[name] = data
            }
            let player = try AVAudioPlayer(data: data)
            player.volume = min(volume * effect.relativeVolume, 1)
            if rate != 1 {
                player.enableRate = true
                player.rate = rate
            }
            player.prepareToPlay()
            activate()
            player.play()
            effectPlayers.append(player)
        } catch {
            logger.error("No se pudo reproducir \(name, privacy: .public): \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Same session as the menu: media volume, plays with the silent switch on and
    /// mixes with other apps; it stops when the app leaves the foreground.
    private func activate() {
        do {
            let session = AVAudioSession.sharedInstance()
            if session.category != .playback {
                try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            }
            try session.setActive(true)
        } catch {
            logger.error("No se pudo activar el audio: \(error.localizedDescription, privacy: .public)")
        }
    }

    private static func url(_ name: String, _ ext: String) -> URL? {
        Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "GameAudio")
    }
}
