import SwiftUI
import TraidoresCore

/// Android's `OnlineModeActivity`: centered gold title, an access status line and the four
/// actions, plus a compact identity card. Entering checks access as a guest; a failed check
/// retries on its own, like Android's "El servidor no responde. Reintentando...".
struct OnlineModeView: View {
    @Environment(OnlineServices.self) private var services
    @Environment(\.dismiss) private var dismiss
    @State private var recoverable: RoomSummary?
    @State private var openRoom: OnlineRoomRoute?
    @State private var dialog: Dialog?
    @State private var showingDialog = false
    // A presentation requested while the previous dialog is still closing would be dropped,
    // so it waits for `onDismiss`.
    @State private var closingDialog = false
    @State private var nextDialog: Dialog?

    private enum Dialog { case join, create, guestCannotCreate, account, suspended(String) }

    private var identity: OnlineIdentity? {
        if case .ready(let identity) = services.account.access { identity } else { nil }
    }

    var body: some View {
        ZStack {
            MenuBackground()
            GeometryReader { geometry in
                ScrollView {
                    content
                        .padding(.horizontal, 32)
                        .padding(.vertical, 64)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity, minHeight: geometry.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            backButton
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
        .task { await enterUntilAnswered() }
        .task(id: services.account.access) {
            switch services.account.access {
            case .ready: recoverable = try? await services.directory.recoverableRoom()
            case .suspended(let reason): present(.suspended(reason))
            default: break
            }
        }
        .navigationDestination(item: $openRoom) { route in
            OnlineLobbyView(roomId: route.id)
        }
        .gameDialog(isPresented: $showingDialog, onDismiss: {
            closingDialog = false
            if let next = nextDialog {
                nextDialog = nil
                present(next)
            }
        }) { dialogView.environment(services) }
    }

    private var content: some View {
        VStack(spacing: 0) {
            Text("JUGAR EN LÍNEA")
                .font(TraidoresTheme.title(24))
                .foregroundStyle(TraidoresTheme.gold)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, 20)
            if let identity {
                OnlineIdentityCard(identity: identity) { present(.account) }
                    .padding(.bottom, 16)
            }
            if let status {
                Text(status)
                    .font(.subheadline)
                    .foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 12)
                    .accessibilityIdentifier("online.status")
            }
            VStack(spacing: 12) {
                if let recoverable {
                    Button("REINGRESAR \(recoverable.code)") { openRoom = OnlineRoomRoute(id: recoverable.id) }
                        .buttonStyle(TraidoresButtonStyle(prominent: true))
                        .accessibilityIdentifier("online.recover")
                }
                NavigationLink {
                    RoomBrowserView().environment(services)
                } label: {
                    Text("BUSCAR PARTIDA")
                }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("online.search")
                Button("UNIRSE POR CÓDIGO") { present(.join) }
                    .buttonStyle(TraidoresButtonStyle())
                    .accessibilityIdentifier("online.joinCode")
                Button("CREAR PARTIDA") { present(identity?.isRegistered == true ? .create : .guestCannotCreate) }
                    .buttonStyle(TraidoresButtonStyle())
                    .accessibilityIdentifier("online.create")
            }
            // As on Android, nothing online is reachable until access is confirmed.
            .disabled(identity == nil || !services.roomsAvailable)
            if !services.roomsAvailable {
                Text("Las partidas online de iOS siguen en preparación. Ya podés vincular o recuperar tu cuenta.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center).padding(.top, 12)
            }
            if identity == nil {
                Button("CREAR CUENTA O ENTRAR") { present(.account) }
                    .buttonStyle(TraidoresButtonStyle())
                    .accessibilityIdentifier("online.account")
                    .padding(.top, 12)
            }
        }
    }

    private var status: String? {
        switch services.account.access {
        case .signedOut, .connecting: "Conectando con el servidor…"
        case .failed(let error): error == .offline ? "El servidor no responde. Reintentando…" : error.message
        default: nil
        }
    }

    private var backButton: some View {
        Button(action: dismiss.callAsFunction) {
            Image(systemName: "chevron.left").font(.headline)
                .frame(width: 44, height: 44)
                .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
        }
        .foregroundStyle(TraidoresTheme.secondary)
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityLabel("Volver")
        .accessibilityShowsLargeContentViewer()
        .accessibilityIdentifier("menu.back")
    }

    @ViewBuilder private var dialogView: some View {
        switch dialog {
        case .join:
            JoinRoomDialog(onClose: closeDialog) { roomId in closeDialog(); openRoom = OnlineRoomRoute(id: roomId) }
        case .create:
            CreateRoomDialog(onClose: closeDialog) { roomId in closeDialog(); openRoom = OnlineRoomRoute(id: roomId) }
        case .guestCannotCreate:
            GameDialogCard(title: "Solo con cuenta",
                           message: "Crear salas es para cuentas. Como invitado podés buscar salas y entrar por código.\n\nCreá tu cuenta para armar tus propias salas y ser el anfitrión.",
                           negative: "AHORA NO", positive: "CREAR CUENTA",
                           onNegative: closeDialog, onPositive: { switchDialog(to: .account) },
                           identifier: "guestDialog") { EmptyView() }
        case .account:
            OnlineAccountFlow(onClose: closeDialog)
        case .suspended(let reason):
            GameDialogCard(title: "Acceso online suspendido", message: reason, negative: nil, positive: "VOLVER",
                           onPositive: { closeDialog(); dismiss() }, identifier: "suspendedDialog") { EmptyView() }
        case nil:
            EmptyView()
        }
    }

    private func present(_ next: Dialog) {
        if closingDialog {
            nextDialog = next
            return
        }
        dialog = next
        showingDialog = true
    }

    /// Replaces the open dialog's content without closing the presentation.
    private func switchDialog(to next: Dialog) {
        dialog = next
    }

    private func closeDialog() {
        guard showingDialog else { return }
        closingDialog = true
        showingDialog = false
    }

    private func enterUntilAnswered() async {
        guard services.account.access == .signedOut else { return }
        await services.account.enterAsGuest()
        while case .failed(let error) = services.account.access, error.isTransient, !Task.isCancelled {
            try? await Task.sleep(for: .seconds(ProcessInfo.processInfo.arguments.contains("-ui-testing") ? 2.5 : 3))
            await services.account.enterAsGuest()
        }
    }
}

struct OnlineRoomRoute: Identifiable, Hashable {
    let id: String
}

/// Who is playing online: portrait, name and number, or the guest alias with the way to
/// create an account. Photo publication is reported here when it is pending or failed.
private struct OnlineIdentityCard: View {
    let identity: OnlineIdentity
    let manageAccount: () -> Void
    @Environment(OnlineServices.self) private var services
    @Environment(\.dynamicTypeSize) private var textSize

    private var profile: PublicProfile? { identity.isRegistered ? services.profile.profile : nil }
    private var bio: String { profile?.bioPerfil.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
    private var removingPhoto: Bool { services.profile.pendingPhoto == .removal }

    var body: some View {
        VStack(spacing: 12) {
            if let profile {
                Image("profile_banner_\(profile.bannerPerfil)")
                    .resizable().scaledToFit()
                    .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .accessibilityHidden(true)
            }
            // At accessibility sizes the portrait goes above the name instead of squeezing it.
            let layout = textSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(spacing: 10)) : AnyLayout(HStackLayout(spacing: 14))
            layout {
                OnlinePortrait(photoURL: removingPhoto ? nil : profile?.avatarURL,
                               avatarKey: profile?.avatarPerfil ?? "pampa_aldeano", size: 58,
                               localPhoto: identity.isRegistered ? services.profile.pendingPhotoData : nil)
                    .overlay(Circle().stroke(TraidoresTheme.gold, lineWidth: 2))
                VStack(alignment: textSize.isAccessibilitySize ? .center : .leading, spacing: 4) {
                    Text(profile?.nombrePerfil ?? identity.displayName)
                        .font(TraidoresTheme.title(21, relativeTo: .title3))
                        .foregroundStyle(TraidoresTheme.text)
                        .multilineTextAlignment(textSize.isAccessibilitySize ? .center : .leading)
                    HStack(spacing: 6) {
                        Text(identity.isRegistered ? "CUENTA" : "INVITADO")
                            .font(.caption.bold()).tracking(1)
                            .foregroundStyle(identity.isRegistered ? TraidoresTheme.ink : TraidoresTheme.gold)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(identity.isRegistered ? TraidoresTheme.gold : .clear, in: Capsule())
                            .overlay { Capsule().stroke(TraidoresTheme.gold) }
                        if let number = identity.publicId {
                            Text("#\(number)").font(.subheadline.bold()).foregroundStyle(TraidoresTheme.gold)
                        }
                    }
                }
                if !textSize.isAccessibilitySize { Spacer(minLength: 0) }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityText)
            .accessibilityIdentifier("online.identity")

            if !bio.isEmpty {
                Text(bio)
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(textSize.isAccessibilitySize ? .center : .leading)
                    .frame(maxWidth: .infinity, alignment: textSize.isAccessibilitySize ? .center : .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("online.identity.bio")
            }

            photoStatus

            if !identity.isRegistered {
                Divider().overlay(TraidoresTheme.border)
                Text("Con una cuenta elegís tu nombre, creás salas y tenés tu número.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.center)
                Button(action: manageAccount) {
                    Text("CREAR CUENTA O ENTRAR").font(.subheadline.bold()).tracking(0.6)
                        .foregroundStyle(TraidoresTheme.gold)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.gold.opacity(0.8)) }
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("online.account")
            }
        }
        .padding(14)
        .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.gold.opacity(0.55)))
    }

    private var accessibilityText: String {
        let name = profile?.nombrePerfil ?? identity.displayName
        guard identity.isRegistered else { return "\(name), invitado" }
        return identity.publicId.map { "\(name), cuenta número \($0)" } ?? "\(name), cuenta"
    }

    @ViewBuilder private var photoStatus: some View {
        switch services.profile.photoSync {
        case .pending, .uploading:
            Label(removingPhoto ? "Quitando tu foto…" : "Publicando tu foto…", systemImage: "arrow.up.circle")
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("online.profile.photoStatus")
        case .failed:
            HStack(spacing: 10) {
                Text(removingPhoto ? "No se pudo quitar la foto." : "No se pudo publicar tu foto.")
                    .font(.footnote).foregroundStyle(OnlineInlineError.color)
                Spacer(minLength: 0)
                Button("REINTENTAR") { Task { try? await services.profile.retryPhotoSync() } }
                    .font(.footnote.bold()).foregroundStyle(TraidoresTheme.gold)
                    .frame(minHeight: 44)
                    .accessibilityIdentifier("online.profile.retryPhoto")
            }
            .accessibilityIdentifier("online.profile.photoStatus")
        default:
            EmptyView()
        }
    }
}

@MainActor
enum OnlineBootstrap {
    /// One real account/profile session for Profile and Online. Fakes require an explicit
    /// UI-test launch argument and never appear in normal Debug or Release builds.
    static func services() -> OnlineServices? {
        #if DEBUG
        if let scenario = FakeOnlineScenario.requested { return scenario.makeServices() }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing"), FirebaseSetup.emulatorHost == nil { return nil }
        #endif
        guard FirebaseSetup.options != nil else { return nil }
        let profile = FirebasePublicProfileService()
        let rooms = UnavailableIOSRooms()
        return OnlineServices(account: FirebaseAccountService(profiles: profile), profile: profile,
                              directory: rooms, room: rooms, roomsAvailable: false, history: FirebaseAccountHistory())
    }
}
