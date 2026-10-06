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

    func testReopeningLobbyResetsPlayersAndAdvancedOptions() throws {
        let app = launchLobby()
        let count = app.staticTexts["lobby.playerCount"]
        XCTAssertTrue(count.waitForExistence(timeout: 3))
        XCTAssertTrue(count.label.hasPrefix("5/"))
        app.buttons["lobby.addPlayer"].tap()
        XCTAssertTrue(count.label.hasPrefix("6/"))
        app.buttons["lobby.map.grecia"].tap()

        app.buttons["lobby.advanced"].tap()
        let revealRoles = app.switches["Mostrar roles al morir o al expulsar"]
        XCTAssertTrue(revealRoles.waitForExistence(timeout: 3))
        XCTAssertEqual(revealRoles.value as? String, "0")
        revealRoles.tap()
        XCTAssertEqual(revealRoles.value as? String, "1")
        let apply = app.buttons["APLICAR"]
        for _ in 0..<5 where !apply.isHittable { app.swipeUp() }
        XCTAssertTrue(apply.isHittable)
        apply.tap()
        XCTAssertTrue(apply.waitForNonExistence(timeout: 3))

        // Check that the change was applied before leaving the lobby.
        app.buttons["lobby.advanced"].tap()
        XCTAssertTrue(revealRoles.waitForExistence(timeout: 3))
        XCTAssertEqual(revealRoles.value as? String, "1")
        let cancel = app.buttons["CANCELAR"]
        for _ in 0..<5 where !cancel.isHittable { app.swipeUp() }
        XCTAssertTrue(cancel.isHittable)
        cancel.tap()
        XCTAssertTrue(cancel.waitForNonExistence(timeout: 3))
        app.buttons["Volver"].tap()
        XCTAssertTrue(app.buttons["difficulty.normal"].waitForExistence(timeout: 3))
        app.buttons["Volver"].tap()
        XCTAssertTrue(app.buttons["play.local"].waitForExistence(timeout: 3))
        app.buttons["Volver"].tap()
        XCTAssertTrue(app.buttons["menu.play"].waitForExistence(timeout: 3))

        app.buttons["menu.play"].tap()
        app.buttons["play.local"].tap()
        app.buttons["difficulty.normal"].tap()
        XCTAssertTrue(count.waitForExistence(timeout: 3))
        XCTAssertTrue(count.label.hasPrefix("5/"))
        XCTAssertEqual(app.staticTexts["lobby.selectedMapName"].label, "GRECIA")
        app.buttons["lobby.advanced"].tap()
        XCTAssertTrue(revealRoles.waitForExistence(timeout: 3))
        XCTAssertEqual(revealRoles.value as? String, "0")
        XCTAssertEqual(app.switches["Mostrar votos individuales"].value as? String, "1")
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

    func testTableOptionsChangeTextSizeAndExit() throws {
        let app = launchLobby()
        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        startMatch(app, roleStart)

        let options = app.buttons["table.options"]
        XCTAssertTrue(options.waitForExistence(timeout: 3))
        options.tap()
        XCTAssertTrue(app.staticTexts["ACCESIBILIDAD"].waitForExistence(timeout: 3))
        // At the default text size every option fits without scrolling.
        let large = app.buttons["table.options.textSize.large"]
        let exit = app.buttons["table.options.exit"]
        XCTAssertTrue(waitUntilHittable(large, timeout: 3))
        XCTAssertTrue(app.buttons["table.options.report"].isHittable)
        XCTAssertTrue(exit.isHittable)
        XCTAssertTrue(app.buttons["table.options.close"].isHittable)
        // Let the entrance fade finish: mid-animation frames produce false contrast failures.
        sleep(1)
        let panelShot = XCTAttachment(screenshot: app.screenshot())
        panelShot.name = "Opciones de partida"; panelShot.lifetime = .keepAlways; add(panelShot)
        try app.performAccessibilityAudit(for: [.dynamicType, .textClipped, .hitRegion, .contrast]) { issue in
            // Same prominent gold button as JUGAR in MenuAccessibilityUITests: dark ink on the
            // gold gradient measures about 7.8:1, but the audit misreads the gradient.
            if issue.auditType == .contrast, issue.element?.label == "CERRAR" { return true }
            // "May be clipped at larger sizes" on full-width buttons: their labels wrap (checked
            // at AX5); the heuristic only sees the button's fixed minimum height.
            if issue.auditType == .textClipped,
               ["REPORTAR UN PROBLEMA", "SALIR DE LA PARTIDA"].contains(issue.element?.label ?? "") { return true }
            print("OPTIONS AUDIT: \(issue.compactDescription), \(issue.element?.label ?? "-") [\(issue.element?.identifier ?? "")]")
            return false
        }
        large.tap()
        XCTAssertEqual(app.staticTexts["table.options.textSizeLabel"].label, "Tamaño de texto: Grande")
        exit.tap()
        let confirmation = app.buttons["Volver al menú"]
        XCTAssertTrue(confirmation.waitForExistence(timeout: 3))
        confirmation.tap()
        XCTAssertTrue(app.buttons["local.startGame"].waitForExistence(timeout: 3))
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
        // The Detective acts as soon as night falls, without a wait first.
        let primary = app.buttons["table.primaryAction"]
        XCTAssertTrue(primary.waitForExistence(timeout: 8))
        let target = app.buttons["table.player.1"]
        XCTAssertTrue(waitUntilHittable(target, timeout: 6))
        XCTAssertNotEqual(primary.label, "ESPERAR")
        target.tap()
        primary.tap()
        // The magnifier is stamped on the card before the private answer.
        // It stays for a moment before the action resolves, so poll quickly.
        var marked = false
        for _ in 0..<20 where !marked {
            marked = target.label.contains("Investigación sobre")
            if !marked { Thread.sleep(forTimeInterval: 0.05) }
        }
        XCTAssertTrue(marked, "La lupa debe quedar sobre la carta investigada")
        attach(app, "Detective · marca de investigación")

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

    /// Quick chat: phrases come from chips or «MÁS», players are chosen by tapping their
    /// card, roles by tapping a role card. The killers' night chat has its own plan phrases.
    func testQuickChatChoosesPlayersAndRolesByTheirCards() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
        app.buttons["local.startGame"].tap()
        let roleStart = app.buttons["role.start"]
        XCTAssertTrue(roleStart.waitForExistence(timeout: 8))
        startMatch(app, roleStart)
        let nightTransition = app.descendants(matching: .any)
            .matching(identifier: "table.dayNightTransition").firstMatch
        if nightTransition.exists {
            XCTAssertTrue(nightTransition.waitForNonExistence(timeout: 3))
        }

        // Night: the killers' plan.
        let firstCard = app.buttons["table.player.1"]
        XCTAssertTrue(waitUntilHittable(firstCard, timeout: 6))
        let firstName = String(firstCard.label.split(separator: ",").first ?? "")
        quickChip(app, "Matemos a…")
        XCTAssertTrue(app.otherElements["chat.quickPick"].waitForExistence(timeout: 2))
        attach(app, "Mensajes rápidos · elegir carta de noche")
        firstCard.tap()
        XCTAssertTrue(app.staticTexts["Matemos a \(firstName)"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.otherElements["chat.quickPick"].exists)

        firstCard.tap()
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

        // Day: a chip that needs a player.
        XCTAssertTrue(waitUntilHittable(app.buttons["chat.quickMore"], timeout: 6))
        attach(app, "Mensajes rápidos · debate")
        quickChip(app, "Sospecho de…")
        let card = app.buttons["table.player.2"]
        XCTAssertTrue(waitUntilHittable(card, timeout: 3))
        attach(app, "Mensajes rápidos · sospecho de")
        let name = String(card.label.split(separator: ",").first ?? "")
        card.tap()
        XCTAssertTrue(app.staticTexts["Sospecho de \(name)"].waitForExistence(timeout: 3))

        // «Soy…» opens the role cards.
        quickChip(app, "Soy…")
        let villager = app.buttons["chat.quickRole.aldeano"]
        XCTAssertTrue(villager.waitForExistence(timeout: 2))
        attach(app, "Mensajes rápidos · decir mi rol")
        villager.tap()
        let claim = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Soy '")).firstMatch
        XCTAssertTrue(claim.waitForExistence(timeout: 3))

        // «MÁS» → Informar una acción → Investigación → card → result.
        app.buttons["chat.quickMore"].tap()
        attach(app, "Mensajes rápidos · más")
        let report = app.buttons["Informar una acción…"]
        // With accessibility text the menu is taller than the screen.
        for _ in 0..<4 where !(report.exists && report.isHittable) { app.swipeUp() }
        report.tap()
        app.buttons["Investigación…"].tap()
        XCTAssertTrue(waitUntilHittable(card, timeout: 3))
        card.tap()
        app.buttons["INOCENTE"].tap()
        XCTAssertTrue(app.staticTexts["Investigué a \(name) y me dio inocente"].waitForExistence(timeout: 3))

        // Cancelling leaves the table as it was.
        quickChip(app, "Sospecho de…")
        app.buttons["chat.quickCancel"].tap()
        XCTAssertFalse(app.otherElements["chat.quickPick"].exists)
        attach(app, "Mensajes rápidos · enviados")
    }

    /// The header gear opens the general options (sound, text size, reports), like Android's
    /// lobby; match settings stay under OPCIONES AVANZADAS.
    func testLobbyGearOpensGeneralOptions() throws {
        let app = launchLobby()
        app.buttons["lobby.options"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["table.options.panel"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["table.options.report"].exists)
        XCTAssertFalse(app.buttons["table.options.exit"].exists)
        attach(app, "Lobby · opciones generales")
        app.buttons["table.options.close"].tap()
        XCTAssertTrue(app.buttons["lobby.advanced"].waitForExistence(timeout: 3))
    }

    /// A practice role needs its map and minimum players, with the reason in the picker
    /// and a clear notice instead of silently dealing another role.
    func testPracticeRoleRequiresItsMapAndPlayers() throws {
        let app = launchLobby()
        app.buttons["lobby.map.pampa"].tap()
        // With five players the Alcalde is locked and Medieval's Bufón is not offered in Pampa.
        openPracticeRoles(app)
        let mayor = app.buttons["practice.role.alcalde"]
        XCTAssertTrue(mayor.waitForExistence(timeout: 3))
        XCTAssertFalse(mayor.isEnabled)
        XCTAssertTrue(mayor.label.contains("Desde 8 jugadores"))
        XCTAssertFalse(app.buttons["practice.role.bufon"].exists)
        XCTAssertTrue(app.buttons["practice.role.payador"].exists)
        attach(app, "Rol de práctica · cartas")
        app.buttons["practice.role.random"].tap()
        applyAdvanced(app)

        // With the map and enough players the role is dealt.
        app.buttons["lobby.map.medieval"].tap()
        for _ in 0..<3 { app.buttons["lobby.addPlayer"].tap() }
        openPracticeRoles(app)
        let jester = app.buttons["practice.role.bufon"]
        XCTAssertTrue(jester.waitForExistence(timeout: 3))
        for _ in 0..<4 where !jester.isHittable { app.swipeUp() }
        XCTAssertTrue(jester.isEnabled)
        jester.tap()
        applyAdvanced(app)

        // Removing a player afterwards blocks the start with the reason.
        app.buttons["lobby.removePlayer"].tap()
        app.buttons["local.startGame"].tap()
        XCTAssertTrue(app.staticTexts["No se puede iniciar"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'necesita al menos 8 jugadores'")).firstMatch.exists)
        app.buttons["ENTENDIDO"].tap()

        app.buttons["lobby.addPlayer"].tap()
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        if start.waitForExistence(timeout: 8), !start.isHittable {
            for _ in 0..<4 where !start.isHittable { app.swipeUp() }
        }
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label ==[c] 'BUFÓN'")).firstMatch.waitForExistence(timeout: 3))
    }

    private func openPracticeRoles(_ app: XCUIApplication) {
        app.buttons["lobby.advanced"].tap()
        let card = app.buttons["practice.role"]
        XCTAssertTrue(card.waitForExistence(timeout: 3))
        for _ in 0..<4 where !card.isHittable { app.swipeUp() }
        card.tap()
    }

    private func applyAdvanced(_ app: XCUIApplication) {
        let apply = app.buttons["APLICAR"]
        XCTAssertTrue(apply.waitForExistence(timeout: 3))
        for _ in 0..<5 where !apply.isHittable { app.swipeUp() }
        apply.tap()
        XCTAssertTrue(apply.waitForNonExistence(timeout: 3))
    }

    /// Emotes: only in the day's public phases, no limit for the player against the AI,
    /// and the bots react too.
    func testEmotesInTheDebateWithLimits() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=aldeano", "-ui-testing-emotes"])
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 8))
        startMatch(app, start)
        let transition = app.descendants(matching: .any).matching(identifier: "table.dayNightTransition").firstMatch
        if transition.exists { XCTAssertTrue(transition.waitForNonExistence(timeout: 4)) }
        let emotes = app.buttons["table.emotes"]
        XCTAssertTrue(waitUntilHittable(emotes, timeout: 6))
        emotes.tap()
        XCTAssertTrue(app.staticTexts["Los emotes se usan durante el debate y la votación."].waitForExistence(timeout: 2))

        skipToHumanNightTurn(app)
        for _ in 0..<6 where !waitForPhase(app, containing: "DEBATE", timeout: 2) {
            let announcement = app.descendants(matching: .any).matching(identifier: "table.dawnAnnouncement").firstMatch
            if announcement.exists { _ = announcement.waitForNonExistence(timeout: 8) }
        }
        XCTAssertTrue(waitUntilHittable(emotes, timeout: 6))
        emotes.tap()
        let palette = app.descendants(matching: .any)["table.emotePalette"]
        XCTAssertTrue(palette.waitForExistence(timeout: 2))
        attach(app, "Emotes · paleta")
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'table.emote.'")).firstMatch.tap()
        XCTAssertFalse(palette.exists)
        attach(app, "Emotes · burbuja propia")
        // Against the AI the player has no cooldown.
        emotes.tap()
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'table.emote.'")).firstMatch.tap()
        let cooldown = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Esperá'")).firstMatch
        XCTAssertFalse(cooldown.waitForExistence(timeout: 1))
        // The own card explains the role.
        app.buttons["table.player.0"].tap()
        XCTAssertTrue(app.staticTexts["QUÉ HACE"].waitForExistence(timeout: 2))
        attach(app, "Mi rol desde mi carta")
        app.buttons["CERRAR"].tap()
        // Bots react every 5–10 seconds.
        sleep(9)
        attach(app, "Emotes · bots")
    }

    /// The Desertor picks a side before EMPEZAR appears.
    func testDeserterChoosesASideBeforeStarting() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=desertor"])
        app.buttons["local.startGame"].tap()
        let town = app.buttons["role.deserter.town"]
        XCTAssertTrue(town.waitForExistence(timeout: 8))
        XCTAssertFalse(app.buttons["role.start"].exists)
        attach(app, "Desertor · elegir bando")
        town.tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 3))
        startMatch(app, start)
    }

    /// The Alcalde reveals himself in the debate after confirming.
    func testMayorRevealsDuringTheDebate() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=alcalde", "-ui-testing-protected"])
        startAndReachDay(app)
        let reveal = app.buttons["ability.mayor"]
        XCTAssertTrue(waitUntilHittable(reveal, timeout: 6))
        reveal.tap()
        app.buttons["REVELARME"].tap()
        let announced = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'se reveló como Alcalde'")).firstMatch
        XCTAssertTrue(announced.waitForExistence(timeout: 3))
        XCTAssertFalse(reveal.exists)
        attach(app, "Alcalde revelado")
    }

    /// The Payador opens a Contrapunto by tapping two cards and then points at one.
    func testPayadorOpensAndClosesAContrapunto() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=payador"])
        app.buttons["lobby.map.pampa"].tap()
        startAndReachDay(app)
        let open = app.buttons["ability.contrapunto"]
        XCTAssertTrue(waitUntilHittable(open, timeout: 6))
        open.tap()
        let first = app.buttons["table.player.1"], second = app.buttons["table.player.2"]
        XCTAssertTrue(waitUntilHittable(first, timeout: 3))
        attach(app, "Payador · elegir contrapunto")
        first.tap()
        second.tap()
        XCTAssertTrue(waitForPhase(app, containing: "CONTRAPUNTO"))
        attach(app, "Payador · contrapunto abierto")
        let pointed = String(first.label.split(separator: ",").first ?? "")
        first.tap()
        XCTAssertTrue(waitForPhase(app, containing: "VOTACIÓN"))
        let marked = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "\(pointed) quedó señalado")).firstMatch
        XCTAssertTrue(marked.exists || app.descendants(matching: .any)["table.votingPrompt"].exists)
    }

    /// The Oráculo, from the second night, invokes a dead player into the next debate.
    func testOracleInvokesADeadPlayerOnTheSecondNight() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=oraculo", "-ui-testing-finish-match"])
        app.buttons["lobby.map.grecia"].tap()
        // 13 players deal two Asesinos and a Espía: one expulsion cannot end the match
        // before the second night.
        for _ in 0..<8 { app.buttons["lobby.addPlayer"].tap() }
        startAndReachDay(app)
        let primary = app.buttons["table.primaryAction"]
        let targetCard = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                         "table.player.", "Objetivo disponible")).firstMatch
        for _ in 0..<40 {
            if primary.exists, primary.label == "GUARDAR PODER" { break }
            let transition = app.descendants(matching: .any).matching(identifier: "table.dayNightTransition").firstMatch
            if transition.exists { _ = transition.waitForNonExistence(timeout: 6); continue }
            let feedback = app.buttons["table.dismissPrivateFeedback"]
            if feedback.exists { feedback.tap(); continue }
            let announcement = app.descendants(matching: .any).matching(identifier: "table.dawnAnnouncement").firstMatch
            if announcement.exists { _ = announcement.waitForNonExistence(timeout: 8); continue }
            let next = app.buttons["table.voteContinue"]
            if next.exists, next.label != "", tapCeremonyButton(next) { continue }
            guard primary.exists, primary.isHittable else { sleep(1); continue }
            if primary.label.hasPrefix("TU VOTO") { sleep(1); continue }
            if (primary.label.hasPrefix("LISTOS") || primary.label == "SALTAR NOCHE"), primary.isEnabled {
                primary.tap(); continue
            }
            if app.staticTexts["table.phaseTitle"].label.contains("VOTACIÓN"), targetCard.exists, targetCard.isHittable {
                targetCard.tap(); continue
            }
            sleep(1)
        }
        XCTAssertEqual(primary.label, "GUARDAR PODER")
        XCTAssertTrue(waitUntilHittable(targetCard, timeout: 3))
        attach(app, "Oráculo · invocar")
        let guestCard = app.buttons[targetCard.identifier]
        targetCard.tap()
        XCTAssertEqual(primary.label, "INVOCAR")
        primary.tap()
        XCTAssertTrue(app.staticTexts["INVOCACIÓN REGISTRADA"].waitForExistence(timeout: 3))
        app.buttons["table.dismissPrivateFeedback"].tap()
        // The invoked player's card carries the public mark during the debate.
        let invoked = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS 'invocado'"),
                                                object: guestCard)
        XCTAssertEqual(XCTWaiter.wait(for: [invoked], timeout: 20), .completed)
        XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
        attach(app, "Oráculo · invitado en el debate")
    }

    /// By day an Asesino rereads the night plan, read-only, and goes back to the town chat.
    func testTraitorReviewsTheNightPlanDuringTheDay() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin"])
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 8))
        startMatch(app, start)
        skipToHumanNightTurn(app)
        let target = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                     "table.player.", "Objetivo disponible")).firstMatch
        XCTAssertTrue(waitUntilHittable(target, timeout: 4))
        target.tap()
        app.buttons["table.primaryAction"].tap()
        for _ in 0..<8 where !waitForPhase(app, containing: "DEBATE", timeout: 2) {
            let feedback = app.buttons["table.dismissPrivateFeedback"]
            if feedback.exists { feedback.tap() }
        }
        XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
        let plan = app.buttons["chat.channel.plan"]
        XCTAssertTrue(waitUntilHittable(plan, timeout: 8))
        plan.tap()
        XCTAssertTrue(app.staticTexts["PLAN NOCTURNO"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["De día el plan de los asesinos es solo de lectura."].exists)
        attach(app, "Plan de los asesinos de día")
        app.buttons["chat.channel.town"].tap()
        XCTAssertTrue(app.staticTexts["CHAT DEL PUEBLO"].waitForExistence(timeout: 2))
    }

    /// The Payador, Oráculo and Bufón announcements, shown on demand for a screenshot.
    func testSpecialRoleAnnouncementsLayout() throws {
        for reveal in ["contrapunto", "oracle", "jester"] {
            let app = launchLobby(extraArguments: ["-ui-testing-preview-reveal=\(reveal)", "-ui-testing-real-time"])
            app.buttons["local.startGame"].tap()
            let start = app.buttons["role.start"]
            XCTAssertTrue(start.waitForExistence(timeout: 8))
            startMatch(app, start)
            let continueButton = app.buttons["table.specialReveal.continue"]
            XCTAssertTrue(continueButton.waitForExistence(timeout: 8))
            Thread.sleep(forTimeInterval: 1.8)
            attach(app, "Anuncio especial · \(reveal)")
            // With large text the panel scrolls; bring the button into view.
            for _ in 0..<5 where !continueButton.isHittable { app.swipeUp() }
            XCTAssertTrue(waitUntilHittable(continueButton, timeout: 3))
            continueButton.tap()
            XCTAssertTrue(continueButton.waitForNonExistence(timeout: 3))
            app.terminate()
        }
    }

    private func startAndReachDay(_ app: XCUIApplication) {
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 8))
        startMatch(app, start)
        let transition = app.descendants(matching: .any).matching(identifier: "table.dayNightTransition").firstMatch
        if transition.exists { XCTAssertTrue(transition.waitForNonExistence(timeout: 4)) }
        skipToHumanNightTurn(app)
        for _ in 0..<6 where !waitForPhase(app, containing: "DEBATE", timeout: 2) {
            if transition.exists { _ = transition.waitForNonExistence(timeout: 4) }
            let announcement = app.descendants(matching: .any).matching(identifier: "table.dawnAnnouncement").firstMatch
            if announcement.exists { _ = announcement.waitForNonExistence(timeout: 8) }
            let feedback = app.buttons["table.dismissPrivateFeedback"]
            if feedback.exists { feedback.tap() }
        }
        XCTAssertTrue(waitForPhase(app, containing: "DEBATE"))
    }

    /// Taps the vote ceremony button. A full-screen system "Toolbar" element sometimes makes
    /// XCTest report it as covered although real touches reach it, so fall back to its
    /// on-screen coordinate once it has a valid frame.
    @discardableResult
    private func tapCeremonyButton(_ button: XCUIElement, timeout: TimeInterval = 1) -> Bool {
        if waitUntilHittable(button, timeout: timeout) { button.tap(); return true }
        guard button.exists, !button.frame.isEmpty, button.frame.minY > 0 else { return false }
        button.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        return true
    }

    /// Chips are visible at regular sizes; with accessibility text they live in the menu.
    private func quickChip(_ app: XCUIApplication, _ title: String) {
        let chip = app.buttons[title].firstMatch
        if !(chip.exists && chip.isHittable) { app.buttons["chat.quickMore"].tap() }
        XCTAssertTrue(chip.waitForExistence(timeout: 2))
        chip.tap()
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
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

        // The recount ceremony covers the table and waits for each step's button.
        let ceremony = app.descendants(matching: .any).matching(identifier: "table.voteCeremony").firstMatch
        XCTAssertTrue(ceremony.waitForExistence(timeout: 3))
        let next = app.buttons["table.voteContinue"]
        XCTAssertTrue(waitUntilHittable(next, timeout: 5))
        let recount = XCTAttachment(screenshot: app.screenshot())
        recount.name = "Pampa recuento"
        recount.lifetime = .keepAlways
        add(recount)
        let outcome = next.label
        next.tap()
        if outcome == "IR AL DESEMPATE" {
            XCTAssertTrue(waitForPhase(app, containing: "DESEMPATE"))
            return
        }
        XCTAssertTrue(waitUntilHittable(next, timeout: 8))
        XCTAssertEqual(next.label, "CONTINUAR")
        let sentence = XCTAttachment(screenshot: app.screenshot())
        sentence.name = "Pampa expulsión"
        sentence.lifetime = .keepAlways
        add(sentence)
        next.tap()
        XCTAssertTrue(ceremony.waitForNonExistence(timeout: 5))
    }

    /// Android's tie-break window: after a tie the player votes again between the tied cards.
    func testTieVoteWindowVotesAgainBetweenTheTied() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-role=aldeano", "-ui-testing-force-ties"])
        startAndReachDay(app)
        let primary = app.buttons["table.primaryAction"]
        let unlocked = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: primary)
        XCTAssertEqual(XCTWaiter.wait(for: [unlocked], timeout: 14), .completed)
        primary.tap()
        XCTAssertTrue(waitForPhase(app, containing: "VOTACIÓN"))
        let target = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                     "table.player.", "Objetivo disponible")).firstMatch
        XCTAssertTrue(waitUntilHittable(target, timeout: 4))
        target.tap()
        let next = app.buttons["table.voteContinue"]
        for _ in 0..<4 {
            guard tapCeremonyButton(next, timeout: 8) else { break }
            if app.descendants(matching: .any)["tieVote.window"].waitForExistence(timeout: 1) { break }
        }
        let window = app.descendants(matching: .any)["tieVote.window"]
        XCTAssertTrue(window.waitForExistence(timeout: 6), "Tras el empate se abre la ventana de desempate")
        XCTAssertTrue(app.staticTexts["DESEMPATE"].exists)
        attach(app, "Ventana de desempate")
        // CHAT steps the window aside for the town chat; closing the chat brings it back.
        app.buttons["tieVote.chat"].tap()
        XCTAssertTrue(window.waitForNonExistence(timeout: 2))
        let input = app.textFields["chat.input"]
        XCTAssertTrue(input.waitForExistence(timeout: 2))
        input.typeText("Voto a Thiago")
        app.buttons["chat.send"].tap()
        attach(app, "Chat durante el desempate")
        app.buttons["table.closeChat"].tap()
        XCTAssertTrue(window.waitForExistence(timeout: 3))
        let tied = window.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                      "table.player.", "Objetivo disponible")).firstMatch
        XCTAssertTrue(waitUntilHittable(tied, timeout: 3))
        tied.tap()
        let notice = app.staticTexts["tieVote.notice"]
        XCTAssertTrue(notice.label.hasPrefix("Votaste a"))
        attach(app, "Ventana de desempate · voto")
        // The ballot closes on its own and the window gives way to the recount.
        XCTAssertTrue(window.waitForNonExistence(timeout: 8))
    }

    /// With a partner killer, the player's dagger lands first and the partner's follows.
    func testPartnerDaggerFollowsThePlayersChoice() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin", "-ui-testing-protected"])
        // Ten players deal an Espía next to the Asesino.
        for _ in 0..<5 { app.buttons["lobby.addPlayer"].tap() }
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 8))
        startMatch(app, start)
        skipToHumanNightTurn(app)
        let target = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                     "table.player.", "Objetivo disponible")).firstMatch
        XCTAssertTrue(waitUntilHittable(target, timeout: 4))
        let id = target.identifier
        target.tap()
        app.buttons["table.primaryAction"].tap()
        // The player's dagger lands first; the partner's follows before the private result.
        let card = app.buttons[id]
        XCTAssertTrue(card.waitForExistence(timeout: 1))
        XCTAssertTrue(card.label.contains(" eligió a "), "La daga del compañero lleva su nombre")
        Thread.sleep(forTimeInterval: 1.1)
        attach(app, "Dagas del Asesino y su compañero")
        XCTAssertTrue(app.staticTexts["VÍCTIMA ELEGIDA"].waitForExistence(timeout: 4))
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

    func testCompleteMatchShowsTraitorVictoryAndReturnsToLobby() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-assassin", "-ui-testing-finish-match", "-ui-testing-seed=7"])
        try completeMatch(app, town: false)
        XCTAssertTrue(app.staticTexts["table.result.winner"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["table.result.winner"].label, "VICTORIA DE LOS TRAIDORES")
        XCTAssertTrue(app.staticTexts["table.result.personal"].label.hasSuffix("VICTORIA"))
        verifyResultAndReturn(app, name: "Pampa victoria de Traidores")
    }

    func testCompleteMatchShowsTownVictoryAndRevealsTheRoles() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-detective", "-ui-testing-finish-match", "-ui-testing-seed=7"])
        try completeMatch(app, town: true)
        XCTAssertTrue(app.staticTexts["table.result.winner"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["table.result.winner"].label, "VICTORIA DEL PUEBLO")
        XCTAssertTrue(app.staticTexts["table.result.personal"].label.hasSuffix("VICTORIA"))
        Thread.sleep(forTimeInterval: 2.8)
        try app.performAccessibilityAudit(for: [.dynamicType, .textClipped, .hitRegion, .elementDetection]) { issue in
            // Same heuristic as the options buttons: the label wraps; only the fixed height is seen.
            if issue.auditType == .textClipped, issue.element?.identifier == "table.result.return" { return true }
            print("RESULT AUDIT: \(issue.compactDescription), \(issue.element?.label ?? "sin elemento"), \(issue.element?.identifier ?? "")")
            return false
        }
        verifyResultAndReturn(app, name: "Pampa victoria del Pueblo")
    }

    func testCompleteMatchWithRealAnimationTimingAndSilence() throws {
        let app = launchLobby(extraArguments: ["-ui-testing-mercenary", "-ui-testing-finish-match",
                                               "-ui-testing-seed=7", "-ui-testing-real-time"])
        // A Mercenary and Assassin are dealt naturally at seven players.
        app.buttons["lobby.addPlayer"].tap()
        app.buttons["lobby.addPlayer"].tap()
        try completeMatch(app, town: false, realTime: true)
        XCTAssertTrue(app.staticTexts["table.result.winner"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["table.result.winner"].label, "VICTORIA DEL PUEBLO")
        XCTAssertTrue(app.staticTexts["table.result.personal"].label.hasSuffix("DERROTA"))
        verifyResultAndReturn(app, name: "Pampa derrota - animaciones con tiempos reales")
    }

    /// Plays through the real UI and engine; the test seed only makes the role deal repeatable.
    private func completeMatch(_ app: XCUIApplication, town: Bool, realTime: Bool = false) throws {
        app.buttons["local.startGame"].tap()
        let start = app.buttons["role.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 8))
        startMatch(app, start)
        let result = app.descendants(matching: .any).matching(identifier: "table.matchResult").firstMatch
        var accused = false
        for _ in 0..<30 {
            if result.exists { return }
            let transition = app.descendants(matching: .any).matching(identifier: "table.dayNightTransition").firstMatch
            if transition.exists { XCTAssertTrue(transition.waitForNonExistence(timeout: 6)) }
            let feedback = app.buttons["table.dismissPrivateFeedback"]
            if feedback.exists { feedback.tap(); continue }
            let announcement = app.descendants(matching: .any).matching(identifier: "table.dawnAnnouncement").firstMatch
            if announcement.exists { XCTAssertTrue(announcement.waitForNonExistence(timeout: realTime ? 24 : 6)); continue }
            if result.exists { return }
            let next = app.buttons["table.voteContinue"]
            let ceremony = app.descendants(matching: .any).matching(identifier: "table.voteCeremony").firstMatch
            if ceremony.exists {
                XCTAssertTrue(next.waitForExistence(timeout: realTime ? 14 : 8))
                let label = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label != ''"), object: next)
                _ = XCTWaiter.wait(for: [label], timeout: realTime ? 14 : 8)
                if realTime && app.staticTexts["table.voteCeremony.title"].label.contains("FUE EXPULSADO") {
                    let shot = XCTAttachment(screenshot: app.screenshot())
                    shot.name = "Expulsión completa con tiempos reales"
                    shot.lifetime = .keepAlways
                    add(shot)
                }
                XCTAssertTrue(tapCeremonyButton(next, timeout: realTime ? 14 : 8))
                continue
            }
            let primary = app.buttons["table.primaryAction"]
            if primary.exists && primary.label.hasPrefix("TU VOTO") {
                // A cast vote closes the voting on its own a few seconds later.
                XCTAssertTrue(ceremony.waitForExistence(timeout: 8))
                continue
            }
            XCTAssertTrue(waitUntilHittable(primary, timeout: 8))
            if primary.label == "ESPERAR" {
                let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true AND label != %@", "ESPERAR"), object: primary)
                XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 8), .completed)
            }
            if primary.label.hasPrefix("VOTAR ANTES") {
                // The debate can only be skipped after its first 10 seconds.
                let unlocked = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: primary)
                XCTAssertEqual(XCTWaiter.wait(for: [unlocked], timeout: 14), .completed)
            }
            if town && primary.label.hasPrefix("LISTOS PARA VOTAR") && !accused {
                // Seed 7 deals the Assassin to Thiago. Coordinate a public accusation before voting.
                let input = app.textFields["chat.input"]
                XCTAssertTrue(input.waitForExistence(timeout: 3))
                input.tap(); input.typeText("Sospecho de Thiago")
                app.buttons["chat.send"].tap()
                accused = true
            }
            let targets = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND value == %@",
                                                            "table.player.", "Objetivo disponible"))
                .allElementsBoundByIndex.filter { $0.isHittable }
            if let target = (town ? targets.first(where: { $0.identifier == "table.player.1" }) : nil) ?? targets.first {
                let voting = app.staticTexts["table.phaseTitle"].label.contains("VOTACIÓN")
                    || app.staticTexts["table.phaseTitle"].label.contains("DESEMPATE")
                target.tap()
                if !voting {
                    let night = ["MATAR", "SILENCIAR", "INVESTIGAR", "SALVAR", "SALVARME", "INVOCAR"].contains(primary.label)
                    primary.tap()
                    // The night mark lands first; the private result follows a moment later.
                    if night { _ = app.buttons["table.dismissPrivateFeedback"].waitForExistence(timeout: 3) }
                }
            } else {
                primary.tap()
            }
        }
        XCTFail("La partida completa no llegó a la pantalla de ganadores")
    }

    private func verifyResultAndReturn(_ app: XCUIApplication, name: String) {
        // Capture the finished entrance, rather than a translucent frame during the fade.
        Thread.sleep(forTimeInterval: 2.8)
        let snapshot = XCTAttachment(screenshot: app.screenshot())
        snapshot.name = name; snapshot.lifetime = .keepAlways; add(snapshot)
        let story = app.buttons["table.result.story"]
        let back = app.buttons["table.result.return"]
        XCTAssertTrue(story.isHittable)
        story.tap()
        XCTAssertTrue(app.staticTexts["CRÓNICA DE LA PARTIDA"].waitForExistence(timeout: 3))
        let timeline = app.descendants(matching: .any).matching(identifier: "table.result.timeline").firstMatch
        XCTAssertTrue(timeline.waitForExistence(timeout: 5))
        XCTAssertTrue(timeline.label.contains("RONDA POR RONDA"))
        let duration = app.descendants(matching: .any).matching(identifier: "table.result.duration").firstMatch.label
        XCTAssertTrue(duration.contains(":"))
        XCTAssertFalse(app.staticTexts["table.result.winner"].exists)
        let revealed = XCTAttachment(screenshot: app.screenshot())
        revealed.name = name + " - crónica"; revealed.lifetime = .keepAlways; add(revealed)
        story.tap()
        XCTAssertTrue(app.staticTexts["table.result.winner"].waitForExistence(timeout: 3))
        XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(app.buttons["local.startGame"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["local.startGame"].label, "INICIAR PARTIDA")
        XCTAssertFalse(app.buttons["local.lastResult"].exists)
        XCTAssertFalse(app.buttons["local.resumeGame"].exists)
        XCTAssertTrue(app.staticTexts["lobby.playerCount"].label.hasPrefix("5/"))
    }

    /// Taps EMPEZAR once it accepts touches and checks that the match really left the
    /// role reveal; retries once if SwiftUI swallowed the tap during the panel transition.
    private func startMatch(_ app: XCUIApplication, _ roleStart: XCUIElement) {
        // With large text the role panel scrolls; bring EMPEZAR into view first.
        if roleStart.waitForExistence(timeout: 8), !roleStart.isHittable {
            for _ in 0..<6 where !roleStart.isHittable { app.swipeUp() }
        }
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
