import XCTest

/// Online screens against the in-memory services (`-ui-testing-online <scenario>`): no
/// Firebase, no network. Covers access, account, rooms, lobby, errors with retry and the
/// accessibility audit of every screen and dialog.
final class OnlineFlowUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSocialAccessButtonsAreSharedByProfileAndOnline() throws {
        let app = launchMenu("guest")
        app.buttons["menu.profile"].tap()
        let account = app.buttons["profile.account"]
        XCTAssertTrue(scrollTo(account, in: app))
        account.tap()
        XCTAssertTrue(element(app, "online.account.google").waitForExistence(timeout: 3))
        XCTAssertTrue(element(app, "online.account.apple").exists)
        XCTAssertFalse(element(app, "online.account.apple").isEnabled)
        XCTAssertTrue(app.staticTexts["Apple todavía no está habilitado en esta versión."].exists)
        try audit(app, "Accesos desde Perfil", dialog: true)
        attach(app, "profile-account-google-apple")
        app.buttons["accountDialog.negative"].tap()
        app.buttons["menu.back"].tap()
        app.buttons["menu.play"].tap()
        app.buttons["play.online"].tap()
        XCTAssertTrue(app.buttons["online.account"].waitForExistence(timeout: 5))
        app.buttons["online.account"].tap()
        XCTAssertTrue(element(app, "online.account.google").waitForExistence(timeout: 3))
        XCTAssertTrue(element(app, "online.account.apple").exists)
        attach(app, "online-account-google-apple")
    }

    /// Opt in while running local Auth/Firestore. This uses the real app adapters, not a
    /// fake scenario, and verifies that the saved profile is recovered after restarting.
    func testRealAccountAndProfileAgainstEmulators() throws {
        guard ProcessInfo.processInfo.environment["TRAIDORES_AUTH_EMULATOR_TEST"] == "1" else {
            throw XCTSkip("Requires local Auth/Firestore emulators and explicit opt-in")
        }
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-firebase-emulator-host", "127.0.0.1", "-firebase-ui-reset-auth", "YES"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        let account = app.buttons["profile.account"]
        XCTAssertTrue(scrollTo(account, in: app))
        account.tap()
        let google = element(app, "online.account.google")
        XCTAssertTrue(google.waitForExistence(timeout: 3))
        XCTAssertTrue(google.isEnabled, "normal emulator launch uses the real Google adapter")
        XCTAssertFalse(element(app, "online.account.apple").isEnabled)
        attach(app, "firebase-account-google-apple")
        let email = app.textFields["online.account.email"]
        email.tap(); email.typeText("ui-\(UUID().uuidString)@traidores.test")
        let password = app.secureTextFields["online.account.password"]
        password.tap(); password.typeText("test-password-123")
        app.buttons["accountDialog.positive"].tap()
        XCTAssertTrue(app.buttons["accountLinked.positive"].waitForExistence(timeout: 15))
        dismissPasswordPrompt(app)
        XCTAssertTrue(app.buttons["accountLinked.positive"].waitUntil(timeout: 5) { $0.isHittable })
        app.buttons["accountLinked.positive"].tap()
        // Autofill can present another save prompt when the account window closes.
        dismissPasswordPrompt(app)
        XCTAssertTrue(scrollTo(app.buttons["profile.edit"], in: app))
        app.buttons["profile.edit"].tap()
        let name = app.textFields["profile.name"]
        for _ in 0..<8 where !(name.exists && name.isHittable) { app.swipeDown() }
        XCTAssertTrue(name.exists && name.isHittable)
        replace(name, with: "Nombre Firebase")
        app.buttons["profile.edit"].tap()
        XCTAssertTrue(app.staticTexts["profile.displayName"].waitUntil { $0.label == "Nombre Firebase" })
        // The success state is also exposed in the account card after server confirmation.
        let state = app.staticTexts["profile.account.state"]
        XCTAssertTrue(scrollTo(state, in: app))
        XCTAssertTrue(state.waitUntil(timeout: 10) { $0.label.contains("Nombre Firebase #") })
        let savedState = state.label
        app.launchArguments = ["-ui-testing", "-firebase-emulator-host", "127.0.0.1"]
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        XCTAssertTrue(scrollTo(state, in: app))
        XCTAssertTrue(state.waitUntil(timeout: 10) { $0.label == savedState }, "server recovery preserves profile and number")
        attach(app, "firebase-account-recovered-after-restart")
    }

    func testGuestSearchesJoinsAndSeesTheRoster() throws {
        let app = launchOnline("guest")
        XCTAssertTrue(element(app, "online.identity").waitUntil { $0.label.hasSuffix("invitado") })
        try audit(app, "Online")
        attach(app, "online-hub-guest")

        app.buttons["online.search"].tap()
        let enter = app.buttons["browser.enter.SALA23"]
        XCTAssertTrue(enter.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["browser.enter.CUENTA"].exists, "public account-only rooms are listed")
        try audit(app, "Buscar")
        attach(app, "online-browser")
        enter.tap()

        XCTAssertTrue(app.staticTexts["lobby.online.code"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["lobby.online.count"].label, "4/6 jugadores · Pública")
        XCTAssertFalse(app.buttons["lobby.online.start"].exists, "only the host sees the start button")
        XCTAssertTrue(app.staticTexts["lobby.online.startStatus"].label.contains("todavía no está disponible"))
        // Same display name as another account, different UID: each keeps its own row.
        XCTAssertTrue(element(app, "lobby.online.player.fake-p3").exists)
        try audit(app, "Lobby")
        attach(app, "online-lobby-guest")

        let ready = app.buttons["lobby.online.ready"]
        XCTAssertEqual(ready.label, "ESTOY LISTO")
        ready.tap()
        XCTAssertTrue(app.buttons["YA NO ESTOY LISTO"].waitForExistence(timeout: 3))

        let greeceVote = app.buttons["lobby.online.vote.grecia"]
        greeceVote.tap()
        XCTAssertTrue(greeceVote.waitUntil { $0.label.hasPrefix("Grecia, 2 votos") }, greeceVote.label)

        app.buttons["lobby.online.leave"].tap()
        XCTAssertTrue(app.buttons["leaveDialog.positive"].waitForExistence(timeout: 3))
        try audit(app, "Salir", dialog: true)
        app.buttons["leaveDialog.positive"].tap()
        XCTAssertTrue(app.buttons["browser.refresh"].waitForExistence(timeout: 5), "leaving returns to the room list")
    }

    func testJoinByCodeValidatesAndExplainsEachRejection() throws {
        let app = launchOnline("guest")
        let create = app.buttons["online.create"]
        XCTAssertTrue(create.waitUntil { $0.isEnabled })
        create.tap()
        XCTAssertTrue(app.buttons["guestDialog.negative"].waitForExistence(timeout: 3), "guests are told why")
        try audit(app, "Solo con cuenta", dialog: true)
        app.buttons["guestDialog.negative"].tap()

        app.buttons["online.joinCode"].tap()
        let field = app.textFields["join.code"]
        XCTAssertTrue(field.waitForExistence(timeout: 3))
        try audit(app, "Código", dialog: true)
        attach(app, "online-join-dialog")
        let cases: [(String, String)] = [
            ("AB1", "El código debe tener 6 caracteres"),
            ("LLENA2", "La sala está llena."),
            ("JUGAN2", "La partida de esa sala ya empezó."),
            ("CUENTA", "Esta sala es solo para cuentas"),
            ("ZZZZ22", "No existe una sala con ese código.")
        ]
        for (code, message) in cases {
            replace(field, with: code)
            app.buttons["joinDialog.positive"].tap()
            let error = element(app, "online.inlineError")
            XCTAssertTrue(error.waitUntil { $0.label.contains(message) }, "\(code): \(error.label)")
        }
        replace(field, with: "sala23")
        XCTAssertEqual(field.value as? String, "SALA23", "codes are uppercased while typing")
        app.buttons["joinDialog.positive"].tap()
        XCTAssertTrue(app.staticTexts["lobby.online.code"].waitForExistence(timeout: 5))
    }

    func testLinkingAccountUnlocksCreatingAndHostingARoom() throws {
        let app = launchOnline("guest")
        let account = app.buttons["online.account"]
        XCTAssertTrue(account.waitForExistence(timeout: 5))
        account.tap()

        let email = app.textFields["online.account.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 3))
        try audit(app, "Tu cuenta", dialog: true)
        attach(app, "online-account-dialog")
        app.buttons["accountDialog.positive"].tap()
        XCTAssertTrue(app.staticTexts["online.account.error"].waitUntil { $0.label == "Escribí tu correo." })
        email.tap(); email.typeText("nuevo@traidores.test")
        let password = app.secureTextFields["online.account.password"]
        password.tap(); password.typeText("123")
        app.buttons["accountDialog.positive"].tap()
        XCTAssertTrue(app.staticTexts["online.account.error"].waitUntil { $0.label.contains("al menos 6 caracteres") })
        password.tap(); password.typeText("456789")
        app.buttons["accountDialog.positive"].tap()

        // Linking keeps the guest's UID and gives it a number.
        let linked = app.buttons["accountLinked.positive"]
        XCTAssertTrue(linked.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Tu perfil y tu número quedaron vinculados a tu cuenta."].exists)
        try audit(app, "Cuenta vinculada", dialog: true)
        dismissPasswordPrompt(app)
        linked.tap()
        XCTAssertTrue(linked.waitForNonExistence(timeout: 3))
        XCTAssertTrue(element(app, "online.identity").waitUntil { $0.label.hasSuffix("cuenta número 8") },
                      element(app, "online.identity").label)
        XCTAssertFalse(app.buttons["online.account"].exists, "an account no longer offers to create one")
        attach(app, "online-hub-account")

        app.buttons["online.create"].tap()
        XCTAssertTrue(app.buttons["createDialog.positive"].waitForExistence(timeout: 3))
        try audit(app, "Crear sala", dialog: true)
        attach(app, "online-create-dialog")
        app.buttons["Más jugadores"].tap()
        app.buttons["create.visibility.PRIVADA"].tap()
        app.buttons["createDialog.positive"].tap()

        let start = app.buttons["lobby.online.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 5), "the creator hosts the room")
        XCTAssertFalse(start.isEnabled, "real starts stay off until a gameplay authority exists")
        XCTAssertEqual(app.staticTexts["lobby.online.count"].label, "1/6 jugadores · Privada")
        let reveal = app.switches["lobby.online.rule.reveal"]
        XCTAssertEqual(reveal.value as? String, "0")
        reveal.switches.firstMatch.tap()
        XCTAssertTrue(app.staticTexts["lobby.online.rules"].waitUntil { $0.label.hasPrefix("Roles al morir: Sí") })
        try audit(app, "Lobby anfitrión")
        attach(app, "online-lobby-host")
    }

    func testProfileAccountRecoversAnExistingAccount() throws {
        let app = launchMenu("guest")
        app.buttons["menu.profile"].tap()
        let account = app.buttons["profile.account"]
        XCTAssertTrue(scrollTo(account, in: app))
        XCTAssertTrue(app.staticTexts["profile.account.state"].label.hasPrefix("Jugás como invitado"))
        try audit(app, "Perfil cuenta", scrolled: true)
        attach(app, "profile-account-guest")
        account.tap()

        let email = app.textFields["online.account.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 3))
        email.tap(); email.typeText("usado@traidores.test")
        let password = app.secureTextFields["online.account.password"]
        password.tap(); password.typeText("secreta1")
        app.buttons["accountDialog.positive"].tap()
        // The email already had an account: enter it and recover its number, as Android does.
        let done = app.buttons["accountLinked.positive"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        dismissPasswordPrompt(app)
        XCTAssertTrue(app.staticTexts["Tu cuenta quedó vinculada y recuperaste el perfil #3."].exists)
        done.tap()
        XCTAssertTrue(app.staticTexts["profile.account.state"].waitUntil { $0.label == "Cuenta: Lucía #3." })
        XCTAssertFalse(account.exists)
    }

    func testOfflineAccessRetriesAndErrorsOfferRetry() throws {
        let app = launchOnline("offline")
        // Access fails once and retries on its own, like Android's "Reintentando...".
        XCTAssertTrue(app.staticTexts["online.status"].waitUntil { $0.label.contains("Reintentando") })
        XCTAssertFalse(app.buttons["online.search"].isEnabled, "nothing is reachable before access")
        attach(app, "online-offline")
        XCTAssertTrue(app.buttons["online.search"].waitUntil(timeout: 8) { $0.isEnabled })

        app.buttons["online.search"].tap()
        XCTAssertTrue(app.buttons["online.status.retry"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["online.status.message"].label.contains("No hay conexión estable"))
        try audit(app, "Sin conexión")
        app.buttons["online.status.retry"].tap()
        let enter = app.buttons["browser.enter.SALA23"]
        XCTAssertTrue(enter.waitForExistence(timeout: 5))
        enter.tap()
        // The first join fails offline and keeps the player on the list with the reason.
        XCTAssertTrue(element(app, "online.inlineError").waitForExistence(timeout: 5))
        enter.tap()
        XCTAssertTrue(app.staticTexts["lobby.online.code"].waitForExistence(timeout: 5))

        let banner = element(app, "lobby.online.connection")
        XCTAssertTrue(banner.waitForExistence(timeout: 5), "reconnecting is announced")
        attach(app, "online-lobby-reconnecting")
        XCTAssertTrue(banner.waitForNonExistence(timeout: 5), "and cleared once live again")

        // A failed leave is not a confirmed exit: the player stays in the room.
        app.buttons["lobby.online.leave"].tap()
        app.buttons["leaveDialog.positive"].tap()
        XCTAssertTrue(element(app, "online.inlineError").waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["lobby.online.code"].exists)
    }

    func testRegisteredAccountShowsItsIdentity() throws {
        let app = launchOnline("registered")
        let identity = element(app, "online.identity")
        XCTAssertTrue(identity.waitUntil { $0.label == "Lucía, cuenta número 7" }, identity.label)
        XCTAssertFalse(app.buttons["online.account"].exists)
        try audit(app, "Online cuenta")
        attach(app, "online-hub-registered")
    }

    func testSuspendedAccountShowsTheReasonAndGoesBack() throws {
        let app = launchOnline("suspended")
        let back = app.buttons["suspendedDialog.positive"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Incumplimiento de las reglas de convivencia."].exists)
        try audit(app, "Suspendido", dialog: true)
        back.tap()
        XCTAssertTrue(app.buttons["play.online"].waitForExistence(timeout: 5))
    }

    func testEmptyBrowserExplainsThereAreNoRooms() throws {
        let app = launchOnline("empty")
        let search = app.buttons["online.search"]
        XCTAssertTrue(search.waitUntil { $0.isEnabled })
        search.tap()
        XCTAssertTrue(app.staticTexts["browser.empty"].waitForExistence(timeout: 5))
        try audit(app, "Sin salas")
    }

    func testLargestTextKeepsOnlineScreensUsable() throws {
        let app = launchOnline("guest", extra: ["-UIPreferredContentSizeCategoryName",
                                                "UICTContentSizeCategoryAccessibilityXXXL"])
        let join = app.buttons["online.joinCode"]
        XCTAssertTrue(join.waitUntil { $0.isEnabled })
        try audit(app, "Online AX5")
        attach(app, "online-hub-ax5")
        XCTAssertTrue(scrollTo(join, in: app))
        join.tap()
        let field = app.textFields["join.code"]
        XCTAssertTrue(field.waitForExistence(timeout: 3))
        try audit(app, "Código AX5", dialog: true)
        attach(app, "online-join-ax5")
        replace(field, with: "SALA23")
        let submit = app.buttons["joinDialog.positive"]
        XCTAssertTrue(scrollTo(submit, in: app))
        submit.tap()
        XCTAssertTrue(app.staticTexts["lobby.online.code"].waitForExistence(timeout: 5))
        try audit(app, "Lobby AX5")
        attach(app, "online-lobby-ax5")
        XCTAssertTrue(scrollTo(app.buttons["lobby.online.ready"], in: app))
    }

    // MARK: - Helpers

    private func launchMenu(_ scenario: String, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-online", scenario] + extra
        app.launch()
        XCTAssertTrue(app.buttons["menu.play"].waitForExistence(timeout: 5))
        return app
    }

    private func launchOnline(_ scenario: String, extra: [String] = []) -> XCUIApplication {
        let app = launchMenu(scenario, extra: extra)
        app.buttons["menu.play"].tap()
        let online = app.buttons["play.online"]
        XCTAssertTrue(online.waitForExistence(timeout: 3))
        online.tap()
        return app
    }

    /// iOS offers to save the new password in the keychain over the app; a player may accept
    /// it, the test declines it.
    /// While the offer is being prepared its window already takes the touches, so wait for it
    /// to show before going on.
    private func dismissPasswordPrompt(_ app: XCUIApplication) {
        let notNow = app.buttons["Ahora no"]
        guard notNow.waitForExistence(timeout: 6) else { return }
        // iOS can expose the button before its presentation animation accepts the tap.
        for _ in 0..<2 where notNow.exists {
            XCTAssertTrue(notNow.waitUntil(timeout: 3) { $0.isHittable })
            notNow.tap()
            if notNow.waitForNonExistence(timeout: 3) { return }
        }
        XCTAssertFalse(notNow.exists, "dismiss the system password prompt before continuing")
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func scrollTo(_ element: XCUIElement, in app: XCUIApplication) -> Bool {
        for _ in 0..<8 where !(element.exists && element.isHittable) {
            app.swipeUp()
        }
        return element.exists && element.isHittable
    }

    /// Replaces the text and waits until the field shows it (codes are uppercased as typed).
    private func replace(_ field: XCUIElement, with text: String) {
        field.tap()
        if let current = field.value as? String, !current.isEmpty, current != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
        // The simulator now and then types a wrong character; one clean retry tells that
        // apart from a field that really rewrites what the player typed.
        if !field.waitUntil(timeout: 2, { ($0.value as? String)?.uppercased() == text.uppercased() }),
           let current = field.value as? String, !current.isEmpty, current != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
            field.typeText(text)
        }
        XCTAssertTrue(field.waitUntil { ($0.value as? String)?.uppercased() == text.uppercased() },
                      "typed \(text), field shows \(field.value ?? "")")
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Deliberate audit exceptions, each with its reason.
    private let accepted: [(XCUIAccessibilityAuditType, String)] = []

    private func audit(_ app: XCUIApplication, _ screen: String, scrolled: Bool = false,
                       dialog: Bool = false) throws {
        // Let pushes, dialogs and loading states settle: mid-animation frames read as low contrast.
        sleep(1)
        // "Text clipped" is left out: across runs it flagged different, fully visible texts on
        // these screens (room names, button labels, two-line messages), never the same one
        // twice. Truncation is reviewed instead in the attached screenshots, including AX5.
        try app.performAccessibilityAudit(for: XCUIAccessibilityAuditType.all.subtracting(.textClipped)) { issue in
            let label = issue.element?.label ?? ""
            if self.accepted.contains(where: { $0.0 == issue.auditType && $0.1 == label }) { return true }
            // A scrolled page leaves text partly under the header title; that slice is not
            // what the player reads.
            // Behind a dialog the screen below shows dimmed through the scrim; VoiceOver rightly
            // skips it, which the audit reports as text it cannot reach.
            if dialog, issue.auditType == .elementDetection, issue.element == nil { return true }
            if scrolled, issue.auditType == .contrast, let frame = issue.element?.frame, frame.minY < 120 { return true }
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' [\($0.identifier)]" } ?? "—"
            print("ONLINE AUDIT[\(screen)] \(issue.auditType) · \(issue.compactDescription) · \(element)")
            return false
        }
    }
}

private extension XCUIElement {
    func waitUntil(timeout: TimeInterval = 5, _ condition: @escaping (XCUIElement) -> Bool) -> Bool {
        let predicate = NSPredicate { object, _ in
            guard let element = object as? XCUIElement, element.exists else { return false }
            return condition(element)
        }
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: self)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }
}
