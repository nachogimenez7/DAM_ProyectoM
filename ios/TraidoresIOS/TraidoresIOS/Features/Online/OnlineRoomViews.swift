import SwiftUI
import TraidoresCore

/// Android's `LobbyBrowserActivity`: public waiting rooms with refresh and empty state.
/// Entering pushes the lobby from here; leaving the room comes back to the list.
struct RoomBrowserView: View {
    @Environment(OnlineServices.self) private var services
    @State private var openRoom: OnlineRoomRoute?
    @State private var rooms: [RoomSummary] = []
    @State private var status: OnlineStatusCard.Status? = .loading("Buscando salas…")
    @State private var joiningRoom: String?
    @State private var joinError: OnlineError?

    var body: some View {
        MenuPage(title: "BUSCAR PARTIDA") {
            HStack {
                Text("Salas disponibles en Argentina")
                    .font(.callout).foregroundStyle(TraidoresTheme.secondary)
                Spacer()
                Button { Task { await load() } } label: {
                    Image(systemName: "arrow.clockwise").font(.headline)
                        .frame(width: 44, height: 44)
                        .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                        .overlay { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border) }
                }
                .foregroundStyle(TraidoresTheme.gold)
                .accessibilityLabel("Actualizar salas")
                .accessibilityShowsLargeContentViewer()
                .accessibilityIdentifier("browser.refresh")
                .disabled(status == .loading("Buscando salas…"))
            }
            if let joinError { OnlineInlineError(error: joinError) }
            if let status {
                OnlineStatusCard(status: status) { Task { await load() } }
            } else if rooms.isEmpty {
                Text("No hay salas disponibles por ahora.\nVolvé a intentar en un momento.")
                    .font(.body).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                    .onlinePanel()
                    .accessibilityIdentifier("browser.empty")
            } else {
                ForEach(rooms) { room in row(room) }
            }
        }
        .task { await load() }
        .navigationDestination(item: $openRoom) { route in
            OnlineLobbyView(roomId: route.id).environment(services)
        }
    }

    private func row(_ room: RoomSummary) -> some View {
        let map = GameMap(onlineKey: room.mapKey)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(map.landscapeAsset).resizable().scaledToFill()
                    .frame(width: 64, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(room.name).font(.headline)
                    Text("\(map.title) · \(room.hostName)").font(.callout).foregroundStyle(TraidoresTheme.secondary)
                    HStack(spacing: 6) {
                        Text("\(room.current)/\(room.expected) jugadores").font(.callout.bold())
                        if room.accountsOnly {
                            Text("SOLO CUENTAS").font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .overlay { Capsule().stroke(TraidoresTheme.gold.opacity(0.7)) }
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
            Button(joiningRoom == room.id ? "ENTRANDO…" : "ENTRAR") { join(room) }
                .buttonStyle(TraidoresButtonStyle(prominent: true))
                .disabled(joiningRoom != nil)
                .accessibilityLabel("Entrar a \(room.name)")
                .accessibilityIdentifier("browser.enter.\(room.code)")
        }
        .onlinePanel()
    }

    private func load() async {
        status = .loading("Buscando salas…")
        joinError = nil
        do {
            rooms = try await services.directory.publicRooms()
            status = nil
        } catch {
            status = .failed(error as? OnlineError ?? .server(nil))
        }
    }

    private func join(_ room: RoomSummary) {
        joiningRoom = room.id
        joinError = nil
        Task {
            defer { joiningRoom = nil }
            do {
                let roomId = try await services.directory.join(code: room.code)
                openRoom = OnlineRoomRoute(id: roomId)
            } catch {
                joinError = error as? OnlineError ?? .server(nil)
            }
        }
    }
}
