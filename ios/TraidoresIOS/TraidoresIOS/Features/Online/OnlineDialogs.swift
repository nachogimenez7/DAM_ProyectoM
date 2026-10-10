import AuthenticationServices
import SwiftUI
import TraidoresCore

/// Android's `GameDialog`: a centered dark card with a gold border, Bree title, centered
/// message, custom content and CANCELAR / action buttons. Presented over a dimmed screen.
struct GameDialogCard<Content: View>: View {
    let title: String
    var message: String? = nil
    var negative: String? = "CANCELAR"
    var positive: String? = nil
    var positiveEnabled = true
    var onNegative: () -> Void = {}
    var onPositive: () -> Void = {}
    var identifier = "dialog"
    @ViewBuilder var content: () -> Content
    @Environment(\.reduceAnimations) private var reduceMotion
    @State private var shown = false

    var body: some View {
        ZStack {
            Color.black.opacity(shown ? 0.62 : 0.01).ignoresSafeArea()
                .contentShape(Rectangle())
                .accessibilityHidden(true)
            GeometryReader { geometry in
                // Centered while it fits; scrolls at the largest text sizes instead of shrinking.
                ScrollView {
                    card
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity, minHeight: geometry.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            .padding(.horizontal, 20)
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown || reduceMotion ? 1 : 0.95)
        }
        .onAppear { withAnimation(.easeOut(duration: 0.2)) { shown = true } }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier(identifier)
    }

    private var card: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(TraidoresTheme.title(21, relativeTo: .title3))
                .foregroundStyle(TraidoresTheme.gold)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            if let message {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
            }
            VStack(spacing: 10, content: content)
                .padding(.top, 12)
            HStack(spacing: 10) {
                if let negative {
                    Button(negative, action: onNegative).buttonStyle(GameDialogButtonStyle(strong: false))
                        .accessibilityIdentifier("\(identifier).negative")
                }
                if let positive {
                    Button(positive, action: onPositive).buttonStyle(GameDialogButtonStyle(strong: true))
                        .disabled(!positiveEnabled)
                        .accessibilityIdentifier("\(identifier).positive")
                }
            }
            .padding(.top, 14)
        }
        .padding(EdgeInsets(top: 18, leading: 18, bottom: 14, trailing: 18))
        .frame(maxWidth: 400)
        // Opaque: translucent cards read as low contrast in the accessibility audit.
        .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TraidoresTheme.gold, lineWidth: 1))
    }
}

struct GameDialogButtonStyle: ButtonStyle {
    let strong: Bool
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.bold())
            .tracking(0.6)
            .multilineTextAlignment(.center)
            .foregroundStyle(strong ? TraidoresTheme.ink : TraidoresTheme.text)
            .padding(.horizontal, 8)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(strong ? TraidoresTheme.gold : TraidoresTheme.panel)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 10).stroke(strong ? TraidoresTheme.gold : TraidoresTheme.border)
            }
            .opacity(!isEnabled ? 0.45 : configuration.isPressed ? 0.72 : 1)
            .contentShape(RoundedRectangle(cornerRadius: 10))
    }
}

extension View {
    /// Presents a `GameDialogCard` in its own full-screen presentation, without the slide (the
    /// card fades in itself). A separate presentation is what keeps the screen below out of
    /// VoiceOver; hiding it in place lets headers and fixed buttons escape.
    func gameDialog<Dialog: View>(isPresented: Binding<Bool>, onDismiss: (() -> Void)? = nil,
                                  @ViewBuilder dialog: @escaping () -> Dialog) -> some View {
        fullScreenCover(isPresented: isPresented, onDismiss: onDismiss) {
            dialog().presentationBackground(.clear)
        }
        .transaction(value: isPresented.wrappedValue) { $0.disablesAnimations = true }
    }
}

// MARK: - Account

/// Android's "Tu cuenta" (`ProfileActivity.showAccountDialog`): one CONTINUAR that links the
/// email to the current guest or, if that email already has an account, enters it and
/// recovers its number. There is no sign-out, as on Android.
struct OnlineAccountFlow: View {
    let onClose: () -> Void
    @Environment(OnlineServices.self) private var services
    @State private var email = ""
    @State private var password = ""
    @State private var working = false
    @State private var problem: String?
    @State private var outcome: Outcome?
    @FocusState private var focus: Field?

    private enum Field { case email, password }
    private enum Outcome { case linked, recovered(String?) }

    var body: some View {
        if let outcome {
            GameDialogCard(title: "Cuenta vinculada exitosamente", message: successMessage(outcome),
                           negative: nil, positive: "ACEPTAR", onPositive: onClose,
                           identifier: "accountLinked") {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 44)).foregroundStyle(TraidoresTheme.gold)
                    .accessibilityHidden(true)
            }
        } else {
            GameDialogCard(title: "Tu cuenta",
                           message: "Vinculá una cuenta para guardar tu perfil y tu número, o entrá para recuperar los que ya tenés.",
                           positive: working ? "PROCESANDO…" : "CONTINUAR", positiveEnabled: !working,
                           onNegative: onClose, onPositive: submit, identifier: "accountDialog") {
                GoogleAccountButton {
                    run { try await services.account.continueWithGoogle() }
                }
                .frame(height: 48)
                .modifier(AccountProviderButtonFrame())
                .disabled(!services.account.googleSignInAvailable)
                .accessibilityIdentifier("online.account.google")
                if services.account.appleSignInAvailable {
                    SignInWithAppleButton(.continue) { request in
                        do { try services.account.prepareAppleRequest(request) } catch {
                            problem = (error as? OnlineError)?.message
                        }
                    } onCompletion: { result in
                        run { try await services.account.completeAppleSignIn(result) }
                    }
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 48)
                    .modifier(AccountProviderButtonFrame())
                    .accessibilityIdentifier("online.account.apple")
                } else {
                    UnavailableAppleAccountButton()
                        .frame(height: 48)
                        .modifier(AccountProviderButtonFrame())
                    Text("Apple todavía no está habilitado en esta versión.")
                        .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                }
                Text("O USÁ TU CORREO").font(.caption.bold()).tracking(1)
                    .foregroundStyle(TraidoresTheme.secondary)
                TextField("Correo", text: $email)
                    .textFieldStyle(OnlineTextFieldStyle())
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focus, equals: .email)
                    .submitLabel(.next)
                    .onSubmit { focus = .password }
                    .accessibilityIdentifier("online.account.email")
                SecureField("Contraseña", text: $password)
                    .textFieldStyle(OnlineTextFieldStyle())
                    .textContentType(.password)
                    .focused($focus, equals: .password)
                    .submitLabel(.go)
                    .onSubmit(submit)
                    .accessibilityIdentifier("online.account.password")
                if let problem {
                    Text(problem).font(.footnote).foregroundStyle(OnlineInlineError.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("online.account.error")
                        .onAppear { AccessibilityNotification.Announcement(problem).post() }
                }
            }
            .disabled(working)
        }
    }

    private func successMessage(_ outcome: Outcome) -> String {
        switch outcome {
        case .linked: "Tu perfil y tu número quedaron vinculados a tu cuenta."
        case .recovered(let number): number.map { "Tu cuenta quedó vinculada y recuperaste el perfil #\($0)." }
            ?? "Entraste con tu cuenta y recuperaste tu perfil."
        }
    }

    private func submit() {
        // Same local checks as Android's `AccountCredentials`, before asking the server.
        let address = email.trimmingCharacters(in: .whitespaces)
        problem = if address.isEmpty { "Escribí tu correo." }
            else if !address.contains("@") || !(address.split(separator: "@").last?.contains(".") ?? false) { "Ese correo no parece válido." }
            else if address.contains(" ") { "El correo no puede tener espacios." }
            else if password.isEmpty { "Escribí una contraseña." }
            else if password.count < 6 { "La contraseña necesita al menos 6 caracteres." }
            else { nil }
        guard problem == nil else { return }
        run { try await services.account.linkAccount(email: address, password: password) }
    }

    private func run(_ operation: @escaping () async throws -> Void) {
        problem = nil
        working = true
        focus = nil
        Task {
            defer { working = false }
            // Linking needs the guest identity that entering online creates.
            if case .signedOut = services.account.access { await services.account.enterAsGuest() }
            let before = currentUid
            do {
                try await operation()
                guard case .ready(let identity) = services.account.access else {
                    if case .failed(let error) = services.account.access { throw error }
                    if case .suspended(let reason) = services.account.access { throw OnlineError.suspended(reason) }
                    throw OnlineError.sessionExpired
                }
                outcome = identity.uid == before ? .linked : .recovered(identity.publicId)
            } catch {
                let failure = error as? OnlineError ?? .server(nil)
                if failure != .cancelled { problem = failure.message }
            }
        }
    }

    private var currentUid: String? {
        if case .ready(let identity) = services.account.access { identity.uid } else { nil }
    }
}

/// Decorate the space around the official provider artwork to match the dialog.
private struct AccountProviderButtonFrame: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(3)
            .background {
                RoundedRectangle(cornerRadius: 9)
                    .strokeBorder(TraidoresTheme.gold.opacity(0.35), lineWidth: 1)
            }
    }
}

/// Google's current dark branding. Its iOS SDK's `.dark` preset still renders blue.
private struct GoogleAccountButton: View {
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image("google_sign_in_g")
                    .resizable().scaledToFit().frame(width: 20, height: 20)
                    .accessibilityHidden(true)
                Text("Iniciar sesión con Google")
                    .font(.custom("GoogleSans-Regular_Medium", size: 17, relativeTo: .body))
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 48)
        }
        .buttonStyle(GoogleAccountButtonStyle())
    }
}

private struct GoogleAccountButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Color(hex: "#E3E3E3"))
            .background(RoundedRectangle(cornerRadius: 6).fill(Color(hex: "#131314")))
            .overlay {
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(Color(hex: "#8E918F"), lineWidth: 1)
            }
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Apple's button currently omits UIAccessibilityTraitNotEnabled when disabled. Keep the
/// official UIKit button and override that trait for the unavailable state.
private struct UnavailableAppleAccountButton: UIViewRepresentable {
    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = DisabledAppleIDButton(type: .continue, style: .black)
        button.isEnabled = false
        button.accessibilityIdentifier = "online.account.apple"
        return button
    }
    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {
        button.isEnabled = false
    }
}

private final class DisabledAppleIDButton: ASAuthorizationAppleIDButton {
    override var accessibilityTraits: UIAccessibilityTraits {
        get { super.accessibilityTraits.union(.notEnabled) }
        set { super.accessibilityTraits = newValue }
    }
}

/// Android's profile card "CUENTA": state line and CREAR CUENTA O ENTRAR.
struct OnlineAccountCard: View {
    var accent: Color = TraidoresTheme.gold
    var surface: Color = TraidoresTheme.panel
    @Environment(OnlineServices.self) private var services
    @State private var presenting = false

    private var registered: OnlineIdentity? {
        if case .ready(let identity) = services.account.access, identity.isRegistered { identity } else { nil }
    }

    private var accountStatus: String {
        switch services.account.access {
        case .signedOut, .connecting: "Comprobando tu cuenta…"
        case .failed(let error): error.message
        case .suspended(let reason): reason
        case .ready(let identity):
            identity.isRegistered
                ? "Cuenta: \(services.profile.profile?.nombrePerfil ?? identity.displayName)\(identity.publicId.map { " #\($0)" } ?? "")."
                : "Jugás como invitado. Con una cuenta elegís tu nombre, personalizás el perfil y tenés tu número."
        }
    }

    var body: some View {
        VStack(spacing: 10) {
            Text(accountStatus)
                .font(.subheadline)
                .foregroundStyle(TraidoresTheme.text)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("profile.account.state")
            if registered == nil {
                Button("CREAR CUENTA O ENTRAR") { presenting = true }
                    .buttonStyle(TraidoresButtonStyle(accent: accent, surface: surface))
                    .accessibilityIdentifier("profile.account")
            }
            if case .failed = services.account.access {
                Button("REINTENTAR") { Task { await services.account.enterAsGuest() } }
                    .buttonStyle(TraidoresButtonStyle(accent: accent, surface: surface))
                    .accessibilityIdentifier("profile.account.retry")
            }
        }
        .padding(14)
        .background(surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(accent.opacity(0.5)))
        .gameDialog(isPresented: $presenting) {
            OnlineAccountFlow { presenting = false }.environment(services)
        }
        .task {
            if services.account.access == .signedOut { await services.account.enterAsGuest() }
        }
    }
}

// MARK: - Rooms

struct JoinRoomDialog: View {
    let onClose: () -> Void
    let onJoined: (String) -> Void
    @Environment(OnlineServices.self) private var services
    @State private var code = ""
    @State private var working = false
    @State private var error: OnlineError?

    var body: some View {
        GameDialogCard(title: "UNIRSE POR CÓDIGO", message: "Ingresá el código de 6 caracteres de la sala.",
                       positive: working ? "BUSCANDO…" : "UNIRSE", positiveEnabled: !working,
                       onNegative: onClose, onPositive: join, identifier: "joinDialog") {
            TextField("ABC234", text: $code)
                .textFieldStyle(OnlineTextFieldStyle())
                .font(.title2.monospaced().bold())
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .submitLabel(.join)
                .onSubmit(join)
                .accessibilityLabel("Código de sala")
                .accessibilityIdentifier("join.code")
            if let error { OnlineInlineError(error: error) }
        }
        .onChange(of: code) { _, value in
            let cleaned = String(value.uppercased().filter { $0.isLetter || $0.isNumber }.prefix(6))
            if cleaned != value { code = cleaned }
        }
    }

    private func join() {
        error = nil
        // Validate locally first: a malformed code never reaches the server.
        guard let normalized = try? OnlineContract.roomCode(code) else {
            error = .invalidCode
            return
        }
        working = true
        Task {
            defer { working = false }
            do { onJoined(try await services.directory.join(code: normalized)) } catch {
                self.error = error as? OnlineError ?? .server(nil)
            }
        }
    }
}

/// Android's "CREAR SALA ONLINE": players, optional name and visibility, fixed at creation.
struct CreateRoomDialog: View {
    let onClose: () -> Void
    let onCreated: (String) -> Void
    @Environment(OnlineServices.self) private var services
    @AppStorage("local.map") private var lastMap = GameMap.pampa.rawValue
    @State private var expected = 5
    @State private var name = ""
    @State private var isPublic = true
    @State private var working = false
    @State private var error: OnlineError?

    private var hostName: String {
        guard case .ready(let identity) = services.account.access else { return "Jugador" }
        return services.profile.profile?.nombrePerfil ?? identity.displayName
    }

    var body: some View {
        GameDialogCard(title: "CREAR SALA ONLINE",
                       message: "Elegí cuántas personas van a jugar. La partida comienza cuando todos estén listos.",
                       positive: working ? "CREANDO…" : "CREAR", positiveEnabled: !working,
                       onNegative: onClose, onPositive: create, identifier: "createDialog") {
            playerCount
            Text("NOMBRE DE LA SALA").font(.caption.bold()).tracking(1)
                .foregroundStyle(TraidoresTheme.secondary).frame(maxWidth: .infinity, alignment: .leading)
            TextField("Nombre opcional", text: $name)
                .textFieldStyle(OnlineTextFieldStyle())
                .accessibilityLabel("Nombre de la sala, opcional")
                .accessibilityIdentifier("create.name")
            Text("VISIBILIDAD").font(.caption.bold()).tracking(1)
                .foregroundStyle(TraidoresTheme.secondary).frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 8) {
                visibilityChip("PÚBLICA", selected: isPublic) { isPublic = true }
                visibilityChip("PRIVADA", selected: !isPublic) { isPublic = false }
            }
            Text("Pública: aparece en Buscar partida. Privada: no aparece en la lista y se entra con el código.")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .multilineTextAlignment(.center)
            if let error { OnlineInlineError(error: error) }
        }
        .onChange(of: name) { _, value in if value.count > 60 { name = String(value.prefix(60)) } }
    }

    private var playerCount: some View {
        HStack(spacing: 12) {
            stepButton("minus", label: "Menos jugadores", enabled: expected > 5) { expected -= 1 }
            // Number and caption as separate lines that always keep their height: inside the
            // dialog a single two-line Text was clipped to one line ("5…").
            VStack(spacing: 0) {
                Text("\(expected)").font(TraidoresTheme.title(24))
                Text("JUGADORES").font(.caption.bold()).tracking(1).foregroundStyle(TraidoresTheme.secondary)
            }
            .foregroundStyle(TraidoresTheme.text)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("\(expected) jugadores")
            .accessibilityIdentifier("create.expected")
            stepButton("plus", label: "Más jugadores", enabled: expected < 15) { expected += 1 }
        }
    }

    private func stepButton(_ symbol: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.title3.bold())
                .frame(width: 48, height: 48)
                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
        }
        .foregroundStyle(TraidoresTheme.gold)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
        .accessibilityLabel(label)
        .accessibilityValue("\(expected) jugadores")
        .accessibilityShowsLargeContentViewer()
    }

    private func visibilityChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.subheadline.bold())
                .foregroundStyle(selected ? TraidoresTheme.ink : TraidoresTheme.text)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(selected ? TraidoresTheme.gold : TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                .overlay { RoundedRectangle(cornerRadius: 10).stroke(selected ? TraidoresTheme.gold : TraidoresTheme.border) }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("create.visibility.\(title)")
    }

    private func create() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        // Like Android, the room starts on the last map chosen; the host changes it in the lobby.
        let draft = RoomDraft(name: trimmed.isEmpty ? "Sala de \(hostName)" : trimmed,
                              mapKey: GameMap(onlineKey: lastMap).rawValue,
                              expected: expected, isPublic: isPublic, accountsOnly: false)
        error = nil
        working = true
        Task {
            defer { working = false }
            do { onCreated(try await services.directory.create(draft)) } catch {
                self.error = error as? OnlineError ?? .server(nil)
            }
        }
    }
}
