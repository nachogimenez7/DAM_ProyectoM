import XCTest

final class LocalLobbyUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testProfileStylesEmotesAndAchievementCatalog() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        app.buttons["profile.edit"].tap()
        let style = app.buttons["profile.style"]
        for _ in 0..<5 where !style.isHittable { app.swipeUp() }
        style.tap()
        XCTAssertTrue(app.buttons["profile.style.sea"].waitForExistence(timeout: 3))
        app.buttons["profile.style.sea"].tap()
        app.buttons["menu.back"].firstMatch.tap()
        let styleChanged = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", "ABISMO REAL"), object: app.buttons["profile.style"])
        XCTAssertEqual(XCTWaiter.wait(for: [styleChanged], timeout: 3), .completed)
        let editEmotes = app.buttons["profile.emotes"]
        for _ in 0..<5 where !editEmotes.isHittable { app.swipeUp() }
        editEmotes.tap()
        XCTAssertTrue(app.staticTexts["profile.emotes.count"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["profile.emotes.count"].label.contains("4/4"))
        app.buttons["profile.emote.griego_enojado"].tap()
        XCTAssertTrue(app.staticTexts["profile.emotes.count"].label.contains("3/4"))
        app.buttons["profile.emote.griego_enojado"].tap()
        app.buttons["menu.back"].firstMatch.tap()
        let achievements = app.buttons["profile.achievements"]
        for _ in 0..<5 where !achievements.isHittable { app.swipeUp() }
        achievements.tap()
        let first = app.buttons["profile.achievement.profile_created"]
        XCTAssertTrue(first.waitForExistence(timeout: 3))
        first.tap()
        XCTAssertTrue(app.alerts["Te agradezco infinitamente"].waitForExistence(timeout: 3))
        app.alerts.buttons["ENTENDIDO"].tap()
        app.buttons["menu.back"].firstMatch.tap()
        app.buttons["profile.edit"].tap()
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = "Perfil completo - Abismo Real"
        snapshot.lifetime = .keepAlways
        add(snapshot)
    }

    func testLocalProfileSavesNameAvatarBannerAndFavorite() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        app.buttons["profile.edit"].tap()
        let name = app.textFields["profile.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        let current = name.value as? String ?? ""
        let testName = "Ignacio " + String(UUID().uuidString.prefix(4))
        name.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count) + testName + "\n")
        let avatar = app.buttons["profile.avatar"]
        for _ in 0..<4 where !avatar.isHittable { app.swipeUp() }
        avatar.tap()
        XCTAssertTrue(app.buttons["profile.map.medieval"].waitForExistence(timeout: 3))
        app.buttons["profile.map.medieval"].tap()
        XCTAssertTrue(app.buttons["profile.choice.medieval.aldeano"].waitForExistence(timeout: 3))
        app.buttons["profile.choice.medieval.aldeano"].tap()
        app.buttons["profile.banner"].tap()
        app.buttons["profile.banner.grecia"].tap()
        app.buttons["profile.favorite"].tap()
        app.buttons["profile.map.medieval"].tap()
        app.buttons["profile.choice.medieval.policia"].tap()
        // Leave while still editing, without any save/confirmation action.
        app.buttons["menu.back"].firstMatch.tap()
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        XCTAssertEqual(app.staticTexts["profile.displayName"].label, testName)
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = "Perfil local con banner griego"
        snapshot.lifetime = .keepAlways
        add(snapshot)
        app.buttons["profile.edit"].tap()
        XCTAssertFalse(app.buttons["profile.save"].exists)
        for _ in 0..<5 where !app.buttons["profile.avatar"].isHittable { app.swipeDown() }
        app.buttons["profile.avatar"].tap()
        XCTAssertEqual(app.buttons["profile.choice.medieval.aldeano"].value as? String, "Seleccionado")
        app.buttons["profile.choice.medieval.aldeano"].tap()
        app.buttons["profile.banner"].tap()
        XCTAssertEqual(app.buttons["profile.banner.grecia"].value as? String, "Seleccionado")
        app.buttons["profile.banner.grecia"].tap()
        app.buttons["profile.favorite"].tap()
        XCTAssertEqual(app.buttons["profile.choice.medieval.policia"].value as? String, "Seleccionado")
    }

    func testEmptyNameKeepsLastValidProfileWhenLeaving() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        let previous = app.staticTexts["profile.displayName"].label
        app.buttons["profile.edit"].tap()
        let name = app.textFields["profile.name"]
        name.tap()
        name.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.count))
        name.typeText("\n")
        app.buttons["menu.back"].firstMatch.tap()
        app.buttons["menu.profile"].tap()
        XCTAssertEqual(app.staticTexts["profile.displayName"].label, previous)
    }

    func testMenuVolumePersistsAndResetRestoresDefaults() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.opciones"].waitForExistence(timeout: 5))
        app.buttons["menu.opciones"].tap()
        let slider = app.sliders["options.volume"]
        XCTAssertTrue(slider.waitForExistence(timeout: 3))
        if !slider.isEnabled { app.switches["options.music"].tap() }
        slider.adjust(toNormalizedSliderPosition: 0.25)
        let selectedVolume = app.staticTexts["options.volumeLabel"].label
        XCTAssertNotEqual(selectedVolume, "Música: 80%")
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["menu.opciones"].waitForExistence(timeout: 5))
        app.buttons["menu.opciones"].tap()
        XCTAssertEqual(app.staticTexts["options.volumeLabel"].label, selectedVolume)
        let reset = app.buttons["options.reset"]
        for _ in 0..<4 where !reset.isHittable { app.swipeUp() }
        reset.tap()
        XCTAssertTrue(app.staticTexts["options.resetDone"].waitForExistence(timeout: 2))
        app.swipeDown()
        XCTAssertEqual(app.staticTexts["options.volumeLabel"].label, "Música: 80%")
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = "Opciones del menú"
        snapshot.lifetime = .keepAlways
        add(snapshot)
    }

    func testAboutAndFeedbackDraft() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        // The Bandido Games intro covers the menu for about two seconds.
        XCTAssertTrue(app.buttons["menu.about"].waitForExistence(timeout: 5))
        app.buttons["menu.about"].tap()
        XCTAssertTrue(app.staticTexts["about.version"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.descendants(matching: .any)["about.privacy"].exists)
        app.buttons["menu.back"].tap()
        app.buttons["menu.feedback"].tap()
        let editor = app.textViews["support.message"]
        XCTAssertTrue(editor.waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["support.openMail"].isEnabled)
        editor.tap()
        editor.typeText("Prueba del menu")
        XCTAssertEqual(editor.value as? String, "Prueba del menu")
        XCTAssertTrue(app.buttons["support.openMail"].isEnabled)
    }

    func testRoleCatalogChangesMapAndOpensFullCard() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        // The Bandido Games intro covers the menu for about two seconds.
        XCTAssertTrue(app.buttons["menu.roles"].waitForExistence(timeout: 5))
        app.buttons["menu.roles"].tap()
        XCTAssertTrue(app.staticTexts["roles.mapTitle"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["roles.mapTitle"].label, "Feudo de Hierro")
        app.buttons["roles.card.aldeano"].tap()
        XCTAssertTrue(app.staticTexts["Su historia"].waitForExistence(timeout: 3))
        app.swipeUp()
        app.buttons["roles.close"].tap()
        let pampa = app.buttons["roles.map.pampa"]
        if !pampa.isHittable { app.buttons["roles.map.grecia"].swipeLeft() }
        pampa.tap()
        XCTAssertEqual(app.staticTexts["roles.mapTitle"].label, "Pueblo del Interior - 1915")
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = "Catálogo por mapa - Pampa"
        snapshot.lifetime = .keepAlways
        add(snapshot)
    }

    func testHelpAccordionAndRepeatableTutorial() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        // The Bandido Games intro covers the menu for about two seconds.
        XCTAssertTrue(app.buttons["menu.ayuda"].waitForExistence(timeout: 5))
        app.buttons["menu.ayuda"].tap()
        XCTAssertTrue(app.buttons["help.tutorial"].waitForExistence(timeout: 3))
        app.buttons["help.section.About"].tap()
        XCTAssertTrue(app.staticTexts["help.body.About"].exists)
        if !app.buttons["help.section.How"].isHittable { app.swipeUp() }
        app.buttons["help.section.How"].tap()
        XCTAssertTrue(app.staticTexts["help.body.How"].exists)
        XCTAssertFalse(app.staticTexts["help.body.About"].exists)
        app.buttons["help.section.How"].tap()
        if !app.buttons["help.tutorial"].isHittable { app.swipeDown() }
        app.buttons["help.tutorial"].tap()
        XCTAssertTrue(app.staticTexts["tutorial.progress"].waitForExistence(timeout: 3))
        for step in 1...4 {
            XCTAssertEqual(app.staticTexts["tutorial.progress"].label, "\(step) DE 4")
            app.buttons["tutorial.next"].tap()
        }
        XCTAssertTrue(app.buttons["help.tutorial"].waitForExistence(timeout: 3))
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = "Ayuda desplegable"
        snapshot.lifetime = .keepAlways
        add(snapshot)
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

        startMatch(app, roleStart)
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
        let roleCard = XCTAttachment(screenshot: app.screenshot())
        roleCard.name = "Carta del rol en Grecia"
        roleCard.lifetime = .keepAlways
        add(roleCard)
        startMatch(app, roleStart)

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
        startMatch(app, roleStart)
        skipToHumanNightTurn(app)

        let ownCard = app.buttons["table.player.0"]
        // The table ignores touches while the NOCHE 1 transition covers it.
        XCTAssertTrue(waitUntilHittable(ownCard, timeout: 6), "El Médico debe poder elegirse a sí mismo como en Android")
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
        XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
        let dawnAnnouncement = app.descendants(matching: .any)
            .matching(identifier: "table.dawnAnnouncement").firstMatch
        if dawnAnnouncement.exists {
            XCTAssertTrue(dawnAnnouncement.waitForNonExistence(timeout: 6))
        }

        primaryAction.tap()
        XCTAssertTrue(waitForPhase(app, containing: "VOTACIÓN"))
        let target = try XCTUnwrap((1...4)
            .map { app.buttons["table.player.\($0)"] }
            .first { $0.exists && $0.isEnabled && $0.isHittable })
        target.tap()
        XCTAssertTrue(waitForPhase(app, containing: "RECUENTO"))
    }

    func testDetectiveReceivesPrivateInvestigationResult() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-detective"])
        app.buttons["local.startGame"].tap()

        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        startMatch(app, roleStart)
        skipToHumanNightTurn(app)

        let target = app.buttons["table.player.1"]
        XCTAssertTrue(waitUntilHittable(target, timeout: 6))
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
        XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
    }

    func testPublicChatKeepsNewMessagesVisible() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        startMatch(app, roleStart)
        if !roleStart.waitForNonExistence(timeout: 2) {
            XCTAssertTrue(roleStart.isHittable)
            startMatch(app, roleStart)
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
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 2))
        // A card tap while typing dismisses the keyboard without losing the draft.
        app.buttons["table.player.1"].tap()
        XCTAssertTrue(keyboard.waitForNonExistence(timeout: 3))
        XCTAssertEqual(input.value as? String, "Sospecho de Mora")
        input.tap()
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
            startMatch(app, roleStart)
            if !roleStart.waitForNonExistence(timeout: 2) {
                startMatch(app, roleStart)
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
            XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
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
        startMatch(app, roleStart)
        if !roleStart.waitForNonExistence(timeout: 2) {
            startMatch(app, roleStart)
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
        XCTAssertTrue(waitForPhase(app, containing: "VOTACIÓN"))
        XCTAssertTrue(app.staticTexts["table.phaseTimer"].exists)
        let voting = XCTAttachment(screenshot: app.screenshot())
        voting.name = "Pampa votación"
        voting.lifetime = .keepAlways
        add(voting)
        XCTAssertFalse(app.buttons["table.primaryAction"].isEnabled)
        app.buttons["table.player.2"].tap()
        XCTAssertTrue(waitForPhase(app, containing: "RECUENTO"))
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
        startMatch(app, roleStart)

        let transition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        XCTAssertTrue(transition.waitForExistence(timeout: 4))
        XCTAssertEqual(transition.label, "NOCHE 1")
        XCTAssertTrue(transition.waitForNonExistence(timeout: 5))
        XCTAssertTrue(waitUntilHittable(app.buttons["table.player.0"], timeout: 3))
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
        startMatch(app, roleStart)

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

    /// Night 1 opens on the Assassin's turn. Like Android, other roles wait ("ESPERAR")
    /// and can skip after a few seconds ("SALTAR NOCHE"), which advances to their own turn.
    private func skipToHumanNightTurn(_ app: XCUIApplication) {
        // Depending on the deal the human's turn can also arrive directly.
        let primary = app.buttons["table.primaryAction"]
        let ready = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "hittable == true AND (label == 'SALTAR NOCHE' AND enabled == true OR NOT (label IN {'ESPERAR', 'CONTINUAR', ''}))"),
            object: primary)
        if XCTWaiter.wait(for: [ready], timeout: 12) != .completed {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.name = "Noche sin turno propio"; shot.lifetime = .keepAlways; add(shot)
            XCTFail("La noche debe llegar al turno propio; botón: '\(primary.label)' habilitado=\(primary.isEnabled) tocable=\(primary.isHittable), fase: '\(app.staticTexts["table.phaseTitle"].label)'")
        }
        if primary.label == "SALTAR NOCHE" { primary.tap() }
    }

    /// Taps EMPEZAR once it accepts touches and checks that the match really left the
    /// role reveal; retries once if SwiftUI swallowed the tap during the panel transition.
    private func startMatch(_ app: XCUIApplication, _ roleStart: XCUIElement) {
        XCTAssertTrue(waitUntilHittable(roleStart, timeout: 8))
        for _ in 0..<2 {
            roleStart.tap()
            if roleStart.waitForNonExistence(timeout: 3) { return }
        }
        XCTFail("EMPEZAR no inició la partida")
    }

    /// Phase changes can land a moment after the tap (animations, bot resolution).
    private func waitForPhase(_ app: XCUIApplication, containing text: String, timeout: TimeInterval = 5) -> Bool {
        let phase = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS %@", text),
                                              object: app.staticTexts["table.phaseTitle"])
        return XCTWaiter.wait(for: [phase], timeout: timeout) == .completed
    }

    /// Existence is not enough on the table: cards stay in the hierarchy while a
    /// transition hides them, so wait until they can actually receive a tap.
    private func waitUntilHittable(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let hittable = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND hittable == true"),
                                                 object: element)
        return XCTWaiter.wait(for: [hittable], timeout: timeout) == .completed
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
