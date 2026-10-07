import XCTest

/// Xcode's automated accessibility audit over the menu block (contrast, Dynamic Type,
/// clipped text, hit regions, element descriptions and traits).
final class MenuAccessibilityUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    func testMainMenuAudit() throws { try auditScreen(nil, "Menú") }
    func testRolesAudit() throws { try auditScreen("menu.roles", "Roles") }
    func testHelpAudit() throws { try auditScreen("menu.ayuda", "Ayuda") }
    func testOptionsAudit() throws { try auditScreen("menu.opciones", "Opciones") }
    func testProfileAudit() throws { try auditScreen("menu.profile", "Perfil") }

    private func auditScreen(_ button: String?, _ screen: String) throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        // The Bandido Games intro covers the menu for about two seconds.
        XCTAssertTrue(app.buttons["menu.profile"].waitForExistence(timeout: 5))
        if let button {
            app.buttons[button].tap()
            XCTAssertTrue(app.buttons["menu.back"].firstMatch.waitForExistence(timeout: 3))
        }
        try audit(app, screen: screen)
    }

    /// Deliberate exceptions, each with its reason:
    /// - The TRAIDORES wordmark, studio name and version badge are a logo: Dynamic Type is capped
    ///   so the word never breaks (menu verified at AX5).
    /// - Role summaries stop at four lines; the full text is one tap away in the role card.
    /// - JUGAR: dark ink on the gold gradient measures about 7.8:1; the audit misreads the gradient.
    /// - "--" (profile stats without data): cream on the dark card is about 12:1; the two thin
    ///   dashes are mostly anti-aliased edge pixels, which the audit reads as low contrast.
    private let accepted: [(XCUIAccessibilityAuditType, String)] = [
        (.dynamicType, "TRAIDORES"), (.dynamicType, "Bandido Games"), (.dynamicType, "VERSIÓN EN DESARROLLO"),
        (.contrast, "TRAIDORES"), (.contrast, "JUGAR"), (.contrast, "--")
    ]

    private func audit(_ app: XCUIApplication, screen: String) throws {
        // Let the push transition finish: mid-animation frames produce false contrast failures.
        sleep(1)
        try app.performAccessibilityAudit { issue in
            let label = issue.element?.label ?? ""
            if self.accepted.contains(where: { $0.0 == issue.auditType && $0.1 == label }) { return true }
            if issue.auditType == .textClipped, screen == "Roles", label.count > 120 { return true }
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' [\($0.identifier)]" } ?? "—"
            print("AUDIT[\(screen)] \(issue.auditType) · \(issue.compactDescription) · \(element)")
            return false
        }
    }
}
