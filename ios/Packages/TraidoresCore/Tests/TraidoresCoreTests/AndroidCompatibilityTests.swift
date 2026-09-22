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

    @Test func companionCardBreakpointsMatchAndroid() {
        let five = ClassicCompanionMetrics.androidCompatible(totalPlayers: 5, availableHeight: 0)
        #expect(five.columnWidth == 112)
        #expect(five.itemHeight == 106)
        #expect(five.cardWidth == 54)
        #expect(five.cardHeight == 86)

        let seven = ClassicCompanionMetrics.androidCompatible(totalPlayers: 7, availableHeight: 0)
        #expect(seven.columnWidth == 104)
        #expect(seven.itemHeight == 89)
        #expect(seven.cardWidth == 45)
        #expect(seven.cardHeight == 72)

        let nine = ClassicCompanionMetrics.androidCompatible(totalPlayers: 9, availableHeight: 0)
        #expect(nine.columnWidth == 94)
        #expect(nine.itemHeight == 74)
        #expect(nine.cardWidth == 36)
        #expect(nine.cardHeight == 58)

        let eleven = ClassicCompanionMetrics.androidCompatible(totalPlayers: 11, availableHeight: 0)
        #expect(eleven.columnWidth == 86)
        #expect(eleven.itemHeight == 65)
        #expect(eleven.cardWidth == 31)
        #expect(eleven.cardHeight == 50)

        let fifteen = ClassicCompanionMetrics.androidCompatible(totalPlayers: 15, availableHeight: 0)
        #expect(fifteen.columnWidth == 78)
        #expect(fifteen.itemHeight == 62)
        #expect(fifteen.cardWidth == 29)
        #expect(fifteen.cardHeight == 46)
        #expect(fifteen.scrollEnabled)
    }

    @Test func companionCardsFitAnIPhoneWidthAndOnlyScrollAtThirteenPlayers() {
        let five = ClassicCompanionMetrics.androidCompatible(
            totalPlayers: 5, availableHeight: 680, availableWidth: 78
        )
        let twelve = ClassicCompanionMetrics.androidCompatible(
            totalPlayers: 12, availableHeight: 680, availableWidth: 78
        )
        let thirteen = ClassicCompanionMetrics.androidCompatible(
            totalPlayers: 13, availableHeight: 680, availableWidth: 78
        )
        let fifteen = ClassicCompanionMetrics.androidCompatible(
            totalPlayers: 15, availableHeight: 680, availableWidth: 78
        )

        #expect(five.columnWidth == 78)
        #expect(twelve.columnWidth == 78)
        #expect(five.cardHeight > twelve.cardHeight)
        #expect(!twelve.scrollEnabled)
        #expect(thirteen.scrollEnabled)
        #expect(fifteen.scrollEnabled)
    }
}
