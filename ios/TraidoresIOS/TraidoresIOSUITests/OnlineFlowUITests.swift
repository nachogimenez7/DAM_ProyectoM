import XCTest

/// Screen tests use explicit in-memory scenarios. Opt-in testServer* cases instead use
/// the actual Firebase SDK, rules and V3 callables against isolated local emulators.
final class OnlineFlowUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // Real Firebase SDK + rules + callable tests on the isolated local emulator set.
    // The loopback controller only creates/advances fixtures; app actions go to V3.
    func testServerChatVoteAndSingleNightAnnouncement() throws {
        let (app, fixture) = try launchServerMatch()
        _ = try serverFixture("phase", ["phase": "NOCHE", "role": "aldeano"])
        XCTAssertTrue(app.staticTexts["match.announcement"].waitUntil(timeout: 10) { $0.label == "Noche 1" })
        XCTAssertFalse(element(app, "match.event").exists, "NIGHT_START has no duplicate toast")
        XCTAssertFalse(app.staticTexts["Ya no tenés acceso a esta partida."].exists)
        _ = try serverFixture("phase", ["phase": "DIA_DEBATE", "role": "aldeano"])
        app.buttons["match.chat"].tap()
        let input = app.textFields["match.chat.input"]
        XCTAssertTrue(input.waitForExistence(timeout: 10))
        input.tap(); input.typeText("Mensaje real del debate")
        app.buttons["ENVIAR"].tap()
        XCTAssertTrue(app.staticTexts["Mensaje real del debate"].waitForExistence(timeout: 10))
        let chat = try serverFixture("status")["chat"] as? [String: Any]
        XCTAssertNotNil(chat?["publico"], "message persisted in RTDB through rules")
        app.buttons["CERRAR"].tap()
        _ = try serverFixture("phase", ["phase": "VOTACION", "role": "aldeano"])
        let vote = app.buttons["match.action.votar:"]
        XCTAssertTrue(vote.waitForExistence(timeout: 10)); vote.tap()
        let uid = fixture["uid"] as! String
        let other = String(uid.dropLast()) + "1"
        app.buttons["match.player.\(other)"].tap()
        XCTAssertTrue(app.staticTexts["match.status"].waitUntil(timeout: 15) { $0.label.contains("Acción registrada: tu voto") })
        let actions = try serverFixture("status")["confirmed"] as? [[String: Any]]
        XCTAssertEqual(actions?.first?["action"] as? String, "votar")
        XCTAssertEqual(actions?.first?["targetUid"] as? String, other)
        attach(app, "server-v3-vote-confirmed")
    }

    func testServerDeserterInitialRoundFourAndFinalWindow() throws {
        let (app, _) = try launchServerMatch()
        _ = try serverFixture("phase", ["phase": "REPARTO", "role": "desertor"])
        let initial = app.buttons["match.action.desertor_initial:Pueblo"]
        XCTAssertTrue(initial.waitForExistence(timeout: 10), app.debugDescription); initial.tap()
        app.buttons["matchDeserterDialog.positive"].tap()
        XCTAssertTrue(app.staticTexts["match.deserter.team"].waitUntil(timeout: 15) { $0.label.contains("PUEBLO") })
        XCTAssertEqual(try serverFixture("status")["team"] as? String, "Pueblo")
        _ = try serverFixture("phase", ["phase": "DIA_DEBATE", "role": "desertor", "team": "Pueblo", "round": 3])
        XCTAssertTrue(app.staticTexts["match.announcement"].waitUntil(timeout: 10) { $0.label.contains("DIA_DEBATE") })
        XCTAssertFalse(app.buttons["match.action.desertor_rethink:mantener"].exists)
        _ = try serverFixture("phase", ["phase": "DIA_DEBATE", "role": "desertor", "team": "Pueblo", "round": 4, "muted": true])
        let keep = app.buttons["match.action.desertor_rethink:mantener"]
        XCTAssertTrue(keep.waitForExistence(timeout: 10)); keep.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "consume tu única revisión")).firstMatch.exists)
        app.buttons["matchDeserterDialog.positive"].tap()
        XCTAssertTrue(keep.waitForNonExistence(timeout: 15))
        XCTAssertEqual(try serverFixture("status")["used"] as? Bool, true)
        _ = try serverFixture("phase", ["phase": "DESERTOR_RECONSIDERACION", "role": "desertor", "team": "Pueblo", "round": 4])
        let change = app.buttons["match.action.desertor_rethink:Traidores"]
        XCTAssertTrue(change.waitForExistence(timeout: 10)); change.tap()
        app.buttons["matchDeserterDialog.positive"].tap()
        XCTAssertTrue(element(app, "match.result").waitForExistence(timeout: 15))
        XCTAssertEqual(try serverFixture("status")["team"] as? String, "Traidores")
        attach(app, "server-v3-deserter-final")
    }

    func testServerPrivateChatsAndDeadAbandonment() throws {
        let (app, _) = try launchServerMatch()
        _ = try serverFixture("phase", ["phase": "NOCHE", "role": "asesino"])
        app.buttons["match.chat"].tap()
        let traitors = app.segmentedControls.buttons["TRAIDORES"]
        XCTAssertTrue(traitors.waitForExistence(timeout: 10)); traitors.tap()
        let input = app.textFields["match.chat.input"]
        XCTAssertTrue(input.waitForExistence(timeout: 10), app.debugDescription); input.tap(); input.typeText("Canal traidor")
        app.buttons["ENVIAR"].tap()
        XCTAssertTrue(app.staticTexts["Canal traidor"].waitForExistence(timeout: 10))
        app.buttons["CERRAR"].tap()
        _ = try serverFixture("phase", ["phase": "DIA_DEBATE", "role": "aldeano", "dead": true])
        app.buttons["match.chat"].tap()
        let dead = app.segmentedControls.buttons["ESPECTADORES"]
        XCTAssertTrue(dead.waitForExistence(timeout: 10)); dead.tap()
        XCTAssertTrue(input.waitForExistence(timeout: 10)); input.tap(); input.typeText("Canal de muertos")
        app.buttons["ENVIAR"].tap()
        XCTAssertTrue(app.staticTexts["Canal de muertos"].waitForExistence(timeout: 10))
        app.buttons["CERRAR"].tap()
        app.buttons["match.leave"].tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "cuenta como derrota")).firstMatch.exists)
        app.buttons["matchLeaveDialog.positive"].tap()
        XCTAssertTrue(app.buttons["online.joinCode"].waitForExistence(timeout: 15))
        let result = try serverFixture("status")
        XCTAssertEqual(result["active"] as? Bool, false)
        XCTAssertEqual(result["deathCause"] as? String, "ABANDONO")
    }

    func testServerRematchAndReentryAfterResult() throws {
        let (app, _) = try launchServerMatch()
        _ = try serverFixture("phase", ["phase": "FINALIZADA", "role": "aldeano"])
        XCTAssertTrue(element(app, "match.result").waitForExistence(timeout: 15))
        let previous = try serverFixture("status")["matchId"] as? String
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["menu.play"].waitForExistence(timeout: 10)); app.buttons["menu.play"].tap()
        XCTAssertTrue(app.buttons["play.online"].waitForExistence(timeout: 5)); app.buttons["play.online"].tap()
        let recover = app.buttons["online.recover"]
        XCTAssertTrue(recover.waitForExistence(timeout: 15)); recover.tap()
        XCTAssertTrue(element(app, "match.result").waitForExistence(timeout: 15))
        app.buttons["match.rematch"].tap()
        XCTAssertTrue(app.staticTexts["lobby.online.code"].waitForExistence(timeout: 20), "same room after clean LOBBY publication")
        XCTAssertFalse(app.buttons["match.role"].exists, "previous private role no longer on screen")
        XCTAssertEqual(try serverFixture("status")["phase"] as? String, "LOBBY")
        XCTAssertNotEqual(try serverFixture("status")["matchId"] as? String, previous)
        attach(app, "server-v3-rematch-lobby")
    }

    func testServerDawnUsesPublicRoleAndEventsDoNotReplay() throws {
        let (app, _) = try launchServerMatch(realTimeReveals: true)
        _ = try serverFixture("phase", ["phase": "AMANECER", "role": "aldeano", "event": "NIGHT_DEATH", "revealRole": false])
        let reveal = element(app, "table.dawnAnnouncement")
        XCTAssertTrue(reveal.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["ROL OCULTO"].exists, "reveal must not use a private role")
        let next = app.buttons["table.dawnContinue"]
        XCTAssertTrue(next.waitUntil(timeout: 10) { $0.exists && $0.isEnabled })
        attach(app, "server-v3-public-dawn-reveal")
        next.tap()
        XCTAssertTrue(reveal.waitForNonExistence(timeout: 5), app.debugDescription)
        XCTAssertEqual(try serverFixture("status")["phase"] as? String, "AMANECER", "animation completion does not arbitrate")
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["menu.play"].waitForExistence(timeout: 10)); app.buttons["menu.play"].tap()
        XCTAssertTrue(app.buttons["play.online"].waitForExistence(timeout: 5)); app.buttons["play.online"].tap()
        let recover = app.buttons["online.recover"]
        XCTAssertTrue(recover.waitForExistence(timeout: 15)); recover.tap()
        XCTAssertTrue(app.buttons["match.role"].waitForExistence(timeout: 15))
        XCTAssertFalse(reveal.exists, "persisted matchId + seq prevents replay after relaunch")
        _ = try serverFixture("phase", ["phase": "DIA_DEBATE", "role": "aldeano", "event": "ORACLE_INVITATION"])
        XCTAssertTrue(reveal.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["¡UNA VOZ REGRESA!"].exists)
        app.buttons["table.specialReveal.continue"].tap()
        XCTAssertTrue(reveal.waitForNonExistence(timeout: 5))
        XCTAssertEqual(try serverFixture("status")["phase"] as? String, "DIA_DEBATE")
    }

    private func serverFixture(_ operation: String, _ fields: [String: Any] = [:]) throws -> [String: Any] {
        guard ProcessInfo.processInfo.environment["TRAIDORES_IOS_V3_NATIVE_TEST"] == "1" else {
            throw XCTSkip("Requires isolated emulators and ios/Scripts/server_v3_native_harness.cjs")
        }
        var request = URLRequest(url: URL(string: "http://127.0.0.1:29888/\(operation)")!)
        request.httpMethod = "POST"; request.httpBody = try JSONSerialization.data(withJSONObject: fields)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let done = expectation(description: "Fixture \(operation)")
        var result: Result<[String: Any], Error>?
        URLSession.shared.dataTask(with: request) { data, response, error in
            defer { done.fulfill() }
            do {
                if let error { throw error }
                let value = try JSONSerialization.jsonObject(with: data ?? Data()) as! [String: Any]
                guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                    throw NSError(domain: "V3NativeFixture", code: 1, userInfo: [NSLocalizedDescriptionKey: value.description])
                }
                result = .success(value)
            } catch { result = .failure(error) }
        }.resume()
        wait(for: [done], timeout: 30)
        return try XCTUnwrap(result).get()
    }

    private func launchServerMatch(realTimeReveals: Bool = false) throws -> (XCUIApplication, [String: Any]) {
        let fixture = try serverFixture("setup")
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-firebase-emulator-host", "127.0.0.1",
            "-firebase-emulator-ports", "29099,28081,25001,29000", "-firebase-ui-reset-auth", "YES",
            "-firebase-emulator-email", fixture["email"] as! String, "-firebase-emulator-password", fixture["password"] as! String]
        if realTimeReveals { app.launchArguments.append("-ui-testing-real-time") }
        app.launch()
        XCTAssertTrue(app.buttons["menu.play"].waitForExistence(timeout: 10)); app.buttons["menu.play"].tap()
        app.buttons["play.online"].tap()
        let join = app.buttons["online.joinCode"]
        XCTAssertTrue(join.waitUntil(timeout: 15) { $0.isEnabled }); join.tap()
        let code = app.textFields["join.code"]
        XCTAssertTrue(code.waitForExistence(timeout: 5)); code.tap(); code.typeText(fixture["code"] as! String)
        app.buttons["joinDialog.positive"].tap()
        let ready = app.buttons["lobby.online.ready"]
        XCTAssertTrue(ready.waitForExistence(timeout: 15)); ready.tap()
        let start = app.buttons["lobby.online.start"]
        XCTAssertTrue(start.waitUntil(timeout: 15) { $0.isEnabled }); start.tap()
        XCTAssertTrue(app.buttons["match.role"].waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertFalse(app.staticTexts["Ya no tenés acceso a esta partida."].exists)
        return (app, fixture)
    }

    func testNativeHistoryAndPhotosAgainstEmulators() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let email = environment["TRAIDORES_MEDIA_EMAIL"], let other = environment["TRAIDORES_MEDIA_OTHER_EMAIL"],
              let password = environment["TRAIDORES_MEDIA_PASSWORD"] else {
            throw XCTSkip("Requires the two accounts from seed-media-emulators.cjs and all local emulators")
        }
        let app = XCUIApplication()
        let base = ["-ui-testing", "-firebase-emulator-host", "127.0.0.1",
                    "-firebase-media-email", email, "-firebase-media-other-email", other, "-firebase-media-password", password]
        app.launchArguments = base + ["-firebase-media-smoke", "prepare"]
        app.launch()
        let report = app.staticTexts["firebase.media.result"]
        XCTAssertTrue(report.waitForExistence(timeout: 10))
        XCTAssertTrue(report.waitUntil(timeout: 120) { $0.label == "MEDIA PREPARED" || $0.label.hasPrefix("MEDIA FAIL") }, report.label)
        XCTAssertEqual(report.label, "MEDIA PREPARED")
        app.terminate()
        app.launchArguments = base + ["-firebase-media-smoke", "resume"]
        app.launch()
        XCTAssertTrue(report.waitForExistence(timeout: 10))
        XCTAssertTrue(report.waitUntil(timeout: 40) { $0.label == "MEDIA PASS" || $0.label.hasPrefix("MEDIA FAIL") }, report.label)
        XCTAssertEqual(report.label, "MEDIA PASS")
        app.terminate()
        // Normal services, no fake scenario and no test driver. Verify the Profile renders
        // the same backend result and the published photo after another process launch.
        app.launchArguments = ["-ui-testing", "-firebase-emulator-host", "127.0.0.1"]
        app.launch()
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        app.buttons["menu.profile"].tap()
        let stats = element(app, "profile.stats.Partidas")
        XCTAssertTrue(scrollTo(stats, in: app))
        XCTAssertTrue(stats.waitUntil(timeout: 15) { $0.label == "Partidas: 1" }, stats.label)
        XCTAssertEqual(element(app, "profile.stats.Victorias").label, "Victorias: 1")
        attach(app, "native-firebase-profile-history-photo")
        let history = app.buttons["profile.history"]
        XCTAssertTrue(scrollTo(history, in: app))
        history.tap()
        XCTAssertTrue(app.staticTexts["HISTORIAL"].waitForExistence(timeout: 5))
        try audit(app, "Historial real de cuenta")
        attach(app, "native-firebase-account-history")
    }

    func testNativeLocalResultsReachFirebase() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let email = environment["TRAIDORES_MEDIA_EMAIL"], let other = environment["TRAIDORES_MEDIA_OTHER_EMAIL"],
              let password = environment["TRAIDORES_MEDIA_PASSWORD"] else {
            throw XCTSkip("Requires native media fixtures and the Functions emulator")
        }
        let app = XCUIApplication()
        app.launchArguments = ["-firebase-emulator-host", "127.0.0.1", "-firebase-media-smoke", "local",
                               "-firebase-media-email", email, "-firebase-media-other-email", other, "-firebase-media-password", password]
        app.launch()
        let report = app.staticTexts["firebase.media.result"]
        XCTAssertTrue(report.waitForExistence(timeout: 10))
        XCTAssertTrue(report.waitUntil(timeout: 100) { $0.label == "MEDIA LOCAL PASS" || $0.label.hasPrefix("MEDIA FAIL") }, report.label)
        XCTAssertEqual(report.label, "MEDIA LOCAL PASS")
        app.terminate()
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

        // Only the host picks the map.
        XCTAssertFalse(app.buttons["lobby.online.map.grecia"].isEnabled)

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
        let greece = app.buttons["lobby.online.map.grecia"]
        greece.tap()
        XCTAssertTrue(greece.waitUntil { $0.label == "Grecia, mapa de la sala" }, greece.label)
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
        // At AX5 the identity card fills the viewport. Audit the visible card and then
        // check the controls after scrolling them into view, instead of black offscreen crops.
        try audit(app, "Online AX5", scrolled: true)
        attach(app, "online-hub-ax5")
        XCTAssertTrue(scrollTo(join, in: app, fullyVisible: true))
        // A second audit on this same scrolled screen reuses the first audit's
        // element rectangles/crops on iOS 27. Review this viewport in its screenshot.
        attach(app, "online-hub-controls-ax5")
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

    private func scrollTo(_ element: XCUIElement, in app: XCUIApplication, fullyVisible: Bool = false) -> Bool {
        func visible() -> Bool {
            guard element.exists, element.isHittable else { return false }
            return !fullyVisible || (element.frame.minY >= 120 && element.frame.maxY <= app.frame.maxY - 44)
        }
        for _ in 0..<8 where !visible() {
            app.swipeUp()
        }
        return visible()
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
        sleep(scrolled ? 2 : 1)
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
            // iOS also audits black crops of controls outside the viewport at AX5.
            // The AX5 test checks their visibility and captures them after scrolling.
            if issue.auditType == .contrast, let frame = issue.element?.frame {
                let visible = frame.intersection(app.frame)
                if visible.isNull || visible.height < min(20, frame.height * 0.25) { return true }
            }
            if scrolled, issue.auditType == .contrast, let frame = issue.element?.frame, frame.minY < 120 { return true }
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' [\($0.identifier)]" } ?? "—"
            print("ONLINE AUDIT[\(screen)] \(issue.auditType) · \(issue.compactDescription) · \(element) \(String(describing: issue.element?.frame))")
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
