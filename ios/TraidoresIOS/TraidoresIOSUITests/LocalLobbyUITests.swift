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
            app.staticTexts["assignment.status"].waitForExistence(timeout: 3),
            "Al tocar INICIAR PARTIDA debe abrirse el reparto de rol"
        )

        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        let roleScreenshot = XCTAttachment(screenshot: app.screenshot())
        roleScreenshot.name = "Lectura inicial del rol"
        roleScreenshot.lifetime = .keepAlways
        add(roleScreenshot)

        roleStart.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].waitForExistence(timeout: 3))
        let tableScreenshot = XCTAttachment(screenshot: app.screenshot())
        tableScreenshot.name = "Primera fase de juego"
        tableScreenshot.lifetime = .keepAlways
        add(tableScreenshot)
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

    func testLobbySelectsThreeMapsAndPassesTheChoiceToTheMatch() throws {
        let app = launchLobby()
        let selectedMapName = app.staticTexts["lobby.selectedMapName"]
        XCTAssertTrue(selectedMapName.waitForExistence(timeout: 3))
        XCTAssertEqual(selectedMapName.label, "PAMPA")

        let medieval = app.buttons["lobby.map.medieval"]
        XCTAssertTrue(medieval.isHittable)
        medieval.tap()
        XCTAssertEqual(selectedMapName.label, "MEDIEVAL")

        let greece = app.buttons["lobby.map.grecia"]
        XCTAssertTrue(greece.isHittable)
        greece.tap()
        XCTAssertEqual(selectedMapName.label, "GRECIA")

        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()

        let tableMapName = app.staticTexts["table.mapName"]
        XCTAssertTrue(tableMapName.waitForExistence(timeout: 3))
        XCTAssertTrue(tableMapName.label.contains("Grecia"))
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

    func testLeavingRoleAssignmentCancelsTheNewMatch() throws {
        let app = launchLobby()
        app.buttons["local.startGame"].tap()

        let backButton = app.buttons["assignment.back"]
        XCTAssertTrue(backButton.waitForExistence(timeout: 3))
        backButton.tap()
        XCTAssertTrue(app.alerts["¿Salir de la partida?"].waitForExistence(timeout: 3))
        app.alerts.buttons["SALIR"].tap()

        XCTAssertTrue(app.buttons["local.startGame"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'CONTINUAR PARTIDA'"))
            .firstMatch.exists)
    }

    func testMedicCanProtectThemselfAndAdvanceTheNight() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-medic"])
        app.buttons["local.startGame"].tap()

        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()

        let ownCard = app.buttons["table.player.0"]
        XCTAssertTrue(ownCard.waitForExistence(timeout: 3))
        XCTAssertTrue(ownCard.isHittable, "El Médico debe poder elegirse a sí mismo como en Android")
        ownCard.tap()

        let primaryAction = app.buttons["table.primaryAction"]
        XCTAssertTrue(primaryAction.isEnabled)
        XCTAssertEqual(primaryAction.label, "SALVARME")
        primaryAction.tap()
        XCTAssertTrue(app.staticTexts["PROTECCIÓN REGISTRADA"].waitForExistence(timeout: 3))
        app.buttons["table.dismissPrivateFeedback"].tap()
        let dayTransition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if dayTransition.waitForExistence(timeout: 1) {
            XCTAssertTrue(dayTransition.waitForNonExistence(timeout: 2))
        }
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("AMANECE"))

        primaryAction.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("DEBATE"))

        primaryAction.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("VOTACIÓN"))
        let target = try XCTUnwrap((1...4)
            .map { app.buttons["table.player.\($0)"] }
            .first { $0.exists && $0.isEnabled && $0.isHittable })
        target.tap()
        primaryAction.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("RECUENTO"))
    }

    func testDetectiveReceivesPrivateInvestigationResult() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-detective"])
        app.buttons["local.startGame"].tap()

        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()

        let target = app.buttons["table.player.1"]
        XCTAssertTrue(target.waitForExistence(timeout: 3))
        target.tap()
        app.buttons["table.primaryAction"].tap()

        XCTAssertTrue(app.staticTexts["RESPUESTA PRIVADA"].waitForExistence(timeout: 3))
        let dismiss = app.buttons["table.dismissPrivateFeedback"]
        XCTAssertTrue(dismiss.isHittable)
        dismiss.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("AMANECE"))
    }

    func testNightTransitionAppearsBeforeTheInteractiveTable() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-medic", "-ui-testing-transition"])
        app.buttons["local.startGame"].tap()

        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()

        let transition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        XCTAssertTrue(transition.waitForExistence(timeout: 2))
        XCTAssertEqual(transition.label, "NOCHE 1")
        XCTAssertTrue(transition.waitForNonExistence(timeout: 3))
        XCTAssertTrue(app.buttons["table.player.0"].isHittable)
    }

    func testFifteenPlayerTableKeepsEveryCompanionVisibleAndUniform() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-medic"])
        let count = app.staticTexts["lobby.playerCount"]
        XCTAssertTrue(count.waitForExistence(timeout: 3))

        let addButton = app.buttons["lobby.addPlayer"]
        for _ in 0..<ClassicGameMaximum.additionalPlayersFromMinimum {
            guard !count.label.hasPrefix("15/") else { break }
            XCTAssertTrue(addButton.isEnabled)
            addButton.tap()
        }
        XCTAssertTrue(count.label.hasPrefix("15/"))

        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()

        var frames: [CGRect] = []
        for id in 1...14 {
            let card = app.buttons["table.player.\(id)"]
            XCTAssertTrue(card.waitForExistence(timeout: 3), "Falta la carta del jugador \(id)")
            frames.append(card.frame)
        }
        let heights = frames.map(\.height)
        XCTAssertLessThanOrEqual((heights.max() ?? 0) - (heights.min() ?? 0), 1)

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Mesa responsive de 15 jugadores"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    private func launchLobby(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"] + extraArguments
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

private enum ClassicGameMaximum {
    static let additionalPlayersFromMinimum = 10
}
