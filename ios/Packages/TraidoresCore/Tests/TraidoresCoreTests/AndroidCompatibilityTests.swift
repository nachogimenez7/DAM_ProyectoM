import Foundation
import Testing
@testable import TraidoresCore

struct AndroidCompatibilityTests {
    private struct Fixture: Decodable {
        let maps: [String]
        let phases: [String]
        let roles: [Role]

        struct Role: Decodable {
            let key: String
            let team: String
            let minimumPlayers: Int
            let exclusiveMap: String?
        }
    }

    private func fixture() throws -> Fixture {
        let url = try #require(Bundle.module.url(forResource: "android-catalog", withExtension: "json", subdirectory: "Fixtures"))
        return try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: url))
    }

    @Test func everyAndroidPhaseDecodesAndReencodesWithoutRenaming() throws {
        let expected = try fixture().phases
        #expect(GamePhase.allCases.map(\.rawValue) == expected)
        for name in expected {
            let data = try JSONEncoder().encode(name)
            let phase = try JSONDecoder().decode(GamePhase.self, from: data)
            let encodedName = try JSONDecoder().decode(String.self, from: JSONEncoder().encode(phase))
            #expect(encodedName == name)
        }
    }

    @Test func roleTeamsAvailabilityAndMapRestrictionsMatchKotlin() throws {
        let expected = try fixture()
        #expect(Set(GameMap.allCases.map(\.rawValue)) == Set(expected.maps))
        #expect(RoleCatalog.all.map { $0.id.rawValue } == expected.roles.map(\.key))
        #expect(Set(RoleKey.allCases.map(\.rawValue)) == Set(expected.roles.map(\.key)))
        for reference in expected.roles {
            let actual = try #require(RoleCatalog.all.first { $0.id.rawValue == reference.key })
            #expect(actual.team.rawValue == reference.team)
            #expect(actual.minimumPlayers == reference.minimumPlayers)
            #expect(actual.exclusiveMap?.rawValue == reference.exclusiveMap)
        }
    }

    @Test func unknownWireValuesAreRejectedInsteadOfInventingRolesOrPhases() throws {
        let unknown = Data("\"future_value\"".utf8)
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(RoleKey.self, from: unknown) }
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(GamePhase.self, from: unknown) }
        #expect(throws: DecodingError.self) { try JSONDecoder().decode(GameMap.self, from: unknown) }
    }
}
