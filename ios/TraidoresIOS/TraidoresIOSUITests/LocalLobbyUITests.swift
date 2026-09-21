import XCTest

final class LocalLobbyUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testStartGameOpensRoleAssignment() throws {
        let app = launchLobby()

        let startButton = app.buttons["local.startGame"]
        XCTAssertTrue(startButton.waitForExistence(timeout: 3))
        XCTAssertTrue(startButton.isHittable, "El botón INICIAR PARTIDA debe poder tocarse")
        startButton.tap()

        XCTAssertTrue(
            app.descendants(matching: .any)["assignment.root"].waitForExistence(timeout: 3),
            "Al tocar INICIAR PARTIDA debe abrirse el reparto de rol"
        )
    }

    func testLobbyAddsAndRemovesPlayers() throws {
        let app = launchLobby()
        let count = app.staticTexts["lobby.playerCount"]
        XCTAssertTrue(count.waitForExistence(timeout: 3))
        let initialCount = count.label

        let addButton = app.buttons["lobby.addPlayer"]
        XCTAssertTrue(addButton.isHittable)
        addButton.tap()
        XCTAssertNotEqual(count.label, initialCount)

        let removeButton = app.buttons["lobby.removePlayer"]
        XCTAssertTrue(removeButton.isHittable)
        removeButton.tap()
        XCTAssertEqual(count.label, initialCount)

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Lobby local clásico"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testModeAndDifficultyScreensMatchAndroidStructure() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()

        let playButton = app.buttons["menu.play"]
        XCTAssertTrue(playButton.waitForExistence(timeout: 3))
        playButton.tap()
        XCTAssertTrue(app.staticTexts["SELECCIONAR MODO"].waitForExistence(timeout: 3))

        let modeScreenshot = XCTAttachment(screenshot: app.screenshot())
        modeScreenshot.name = "Selección de modo"
        modeScreenshot.lifetime = .keepAlways
        add(modeScreenshot)

        let localMode = app.buttons["play.local"]
        XCTAssertTrue(localMode.isHittable)
        localMode.tap()
        XCTAssertTrue(app.staticTexts["JUGAR vs IA"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["difficulty.normal"].exists)
        XCTAssertTrue(app.buttons["difficulty.hard"].exists)

        let difficultyScreenshot = XCTAttachment(screenshot: app.screenshot())
        difficultyScreenshot.name = "Selección de dificultad"
        difficultyScreenshot.lifetime = .keepAlways
        add(difficultyScreenshot)
    }

    private func launchLobby() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
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
        return app
    }
}
