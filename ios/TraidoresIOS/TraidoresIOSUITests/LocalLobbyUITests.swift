import XCTest

final class LocalLobbyUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testStartGameOpensRoleAssignment() throws {
        let app = XCUIApplication()
        app.launch()

        let playButton = app.buttons["menu.play"]
        XCTAssertTrue(playButton.waitForExistence(timeout: 3))
        playButton.tap()

        let localMode = app.buttons["play.local"]
        XCTAssertTrue(localMode.waitForExistence(timeout: 3))
        localMode.tap()

        let normalDifficulty = app.buttons["difficulty.normal"]
        XCTAssertTrue(normalDifficulty.waitForExistence(timeout: 3))
        normalDifficulty.tap()

        let startButton = app.buttons["local.startGame"]
        XCTAssertTrue(startButton.waitForExistence(timeout: 3))
        XCTAssertTrue(startButton.isHittable, "El botón INICIAR PARTIDA debe poder tocarse")
        startButton.tap()

        XCTAssertTrue(
            app.descendants(matching: .any)["assignment.root"].waitForExistence(timeout: 3),
            "Al tocar INICIAR PARTIDA debe abrirse el reparto de rol"
        )
    }
}
