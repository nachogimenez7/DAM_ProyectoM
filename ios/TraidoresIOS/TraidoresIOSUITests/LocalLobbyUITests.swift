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
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("DEBATE"))
        let dawnAnnouncement = app.descendants(matching: .any)
            .matching(identifier: "table.dawnAnnouncement").firstMatch
        if dawnAnnouncement.exists {
            XCTAssertTrue(dawnAnnouncement.waitForNonExistence(timeout: 6))
        }

        primaryAction.tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("VOTACIÓN"))
        let target = try XCTUnwrap((1...4)
            .map { app.buttons["table.player.\($0)"] }
            .first { $0.exists && $0.isEnabled && $0.isHittable })
        target.tap()
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
        let dayTransition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if dayTransition.waitForExistence(timeout: 1) {
            XCTAssertTrue(dayTransition.waitForNonExistence(timeout: 3))
        }
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("DEBATE"))
    }

    func testPublicChatKeepsNewMessagesVisible() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()
        if !roleStart.waitForNonExistence(timeout: 2) {
            XCTAssertTrue(roleStart.isHittable)
            roleStart.tap()
            XCTAssertTrue(roleStart.waitForNonExistence(timeout: 3))
        }

        let nightTransition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if nightTransition.exists {
            XCTAssertTrue(nightTransition.waitForNonExistence(timeout: 3))
        }

        app.buttons["table.player.1"].tap()
        app.buttons["table.primaryAction"].tap()
        app.buttons["table.dismissPrivateFeedback"].tap()
        let transition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if transition.waitForExistence(timeout: 1) {
            XCTAssertTrue(transition.waitForNonExistence(timeout: 3))
        }
        let dawnAnnouncement = app.descendants(matching: .any)
            .matching(identifier: "table.dawnAnnouncement").firstMatch
        if dawnAnnouncement.exists {
            XCTAssertTrue(dawnAnnouncement.waitForNonExistence(timeout: 6))
        }
        let input = app.textFields["chat.input"]
        XCTAssertTrue(input.waitForExistence(timeout: 3))
        input.tap()
        input.typeText("Sospecho de Mora")
        XCTAssertEqual(input.value as? String, "Sospecho de Mora")
        let typingScreenshot = XCTAttachment(screenshot: app.screenshot())
        typingScreenshot.name = "Chat escritura completa"
        typingScreenshot.lifetime = .keepAlways
        add(typingScreenshot)
        app.buttons["chat.send"].tap()
        XCTAssertTrue(app.staticTexts["Sospecho de Mora"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["¿Qué prueba tenés contra mí? Escuchemos a los demás."].exists)
        let sentScreenshot = XCTAttachment(screenshot: app.screenshot())
        sentScreenshot.name = "Chat mensaje enviado"
        sentScreenshot.lifetime = .keepAlways
        add(sentScreenshot)
    }

    func testChatBackgroundPreviewForEveryMap() throws {
        for map in ["pampa", "grecia", "medieval"] {
            let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
            app.buttons["lobby.map.\(map)"].tap()
            app.buttons["local.startGame"].tap()

            let roleStart = app.buttons["role.start"]
            XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
            roleStart.tap()
            if !roleStart.waitForNonExistence(timeout: 2) {
                roleStart.tap()
                XCTAssertTrue(roleStart.waitForNonExistence(timeout: 3))
            }

            let nightTransition = app.descendants(matching: .any)
                .matching(identifier: "table.dayNightTransition").firstMatch
            if nightTransition.waitForExistence(timeout: 1) {
                XCTAssertTrue(nightTransition.waitForNonExistence(timeout: 3))
            }
            let night = XCTAttachment(screenshot: app.screenshot())
            night.name = "Chat \(map) noche"
            night.lifetime = .keepAlways
            add(night)

            app.buttons["table.player.1"].tap()
            app.buttons["table.primaryAction"].tap()
            app.buttons["table.dismissPrivateFeedback"].tap()
            let dayTransition = app.descendants(matching: .any)
                .matching(identifier: "table.dayNightTransition").firstMatch
            if dayTransition.waitForExistence(timeout: 1) {
                XCTAssertTrue(dayTransition.waitForNonExistence(timeout: 3))
            }
            XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("DEBATE"))
            let dawnAnnouncement = app.descendants(matching: .any)
                .matching(identifier: "table.dawnAnnouncement").firstMatch
            if dawnAnnouncement.exists {
                XCTAssertTrue(dawnAnnouncement.waitForNonExistence(timeout: 6))
            }
            let day = XCTAttachment(screenshot: app.screenshot())
            day.name = "Chat \(map) debate"
            day.lifetime = .keepAlways
            add(day)
            app.terminate()
        }
    }

    func testVotingReviewSnapshotAndRecount() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        roleStart.tap()
        if !roleStart.waitForNonExistence(timeout: 2) {
            roleStart.tap()
            XCTAssertTrue(roleStart.waitForNonExistence(timeout: 3))
        }
        let transition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if transition.exists { XCTAssertTrue(transition.waitForNonExistence(timeout: 3)) }
        app.buttons["table.player.1"].tap()
        app.buttons["table.primaryAction"].tap()
        app.buttons["table.dismissPrivateFeedback"].tap()
        if transition.waitForExistence(timeout: 1) {
            XCTAssertTrue(transition.waitForNonExistence(timeout: 3))
        }
        let announcement = app.descendants(matching: .any)
            .matching(identifier: "table.dawnAnnouncement").firstMatch
        if announcement.exists { XCTAssertTrue(announcement.waitForNonExistence(timeout: 6)) }
        XCTAssertTrue(app.staticTexts["table.phaseTimer"].exists)
        app.buttons["table.primaryAction"].tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("VOTACIÓN"))
        XCTAssertTrue(app.staticTexts["table.phaseTimer"].exists)
        let voting = XCTAttachment(screenshot: app.screenshot())
        voting.name = "Pampa votación"
        voting.lifetime = .keepAlways
        add(voting)
        XCTAssertFalse(app.buttons["table.primaryAction"].isEnabled)
        app.buttons["table.player.2"].tap()
        XCTAssertTrue(app.staticTexts["table.phaseTitle"].label.contains("RECUENTO"))
        let recount = XCTAttachment(screenshot: app.screenshot())
        recount.name = "Pampa recuento"
        recount.lifetime = .keepAlways
        add(recount)
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
