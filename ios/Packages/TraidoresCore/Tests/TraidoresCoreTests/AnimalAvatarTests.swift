import Foundation
import Testing
@testable import TraidoresCore

struct AnimalAvatarTests {
    @Test func rosterKeepsAnimalsAcrossNamesRolesAndSaveRestore() throws {
        let original = ClassicGame(name: "Vos", seed: 1, botNames: ClassicGame.defaultBotNames)
        let renamed = ClassicGame(name: "Vos", seed: 987, botNames: Array(repeating: "Nombre nuevo", count: 14))
        #expect(original.players.dropFirst().map(\.avatarKey) == renamed.players.dropFirst().map(\.avatarKey))
        #expect(Set(original.players.dropFirst().compactMap(\.avatarKey)).count == 14)
        let restored = try JSONDecoder().decode(ClassicGame.self, from: JSONEncoder().encode(renamed))
        #expect(restored.players.map(\.avatarKey) == renamed.players.map(\.avatarKey))
    }

    @Test func removingAnotherBotDoesNotChangeExplicitIdentity() {
        let game = ClassicGame(name: "Vos", seed: 1, botNames: ["Lucas", "Mora", "Lautaro", "Valen"],
            botAvatarKeys: ["avatar_hornero", "avatar_buho", "avatar_cuervo", "avatar_lobo"])
        #expect(game.players[1].avatarKey == "avatar_hornero")
        #expect(game.players[2].avatarKey == "avatar_buho")
    }

    @Test func oldPlayerSavesWithoutAvatarRemainReadable() throws {
        let player = try JSONDecoder().decode(ClassicPlayer.self,
            from: Data(#"{"id":1,"name":"Thiago","role":"aldeano","alive":true}"#.utf8))
        #expect(player.avatarKey == nil)
    }
}
