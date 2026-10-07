import SwiftUI
import TraidoresCore

/// The online waiting room, following Android's lobby: invite code, players with their
/// public photo, ready state, map vote and the rules chosen by the host. Starting stays
/// disabled while `startAvailability` says the online match is not available.
struct OnlineLobbyView: View {
    let roomId: String
    @Environment(OnlineServices.self) private var services
    @Environment(\.dismiss) private var dismiss
    @State private var loadError: OnlineError?
    @State private var actionError: OnlineError?
    @State private var working = false
    @State private var confirmingLeave = false
    @State private var copied = false

    private var room: RoomSnapshot? { services.room.snapshot }
    private var myUid: String? {
        if case .ready(let identity) = services.account.access { identity.uid } else { nil }
    }
    private var me: RoomPlayer? { room?.players.first { $0.id == myUid } }
    private var isHost: Bool { room != nil && room?.hostId == myUid }

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                Image(GameMap(onlineKey: room?.mapKey ?? "pampa").dayBackgroundAsset)
                    .resizable().scaledToFill()
                    .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                    .overlay(.black.opacity(0.58))
            }
            .ignoresSafeArea().accessibilityHidden(true)

            VStack(spacing: 0) {
                header.padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 8)
                connectionBanner
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) { content }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 20)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
        .task { await attach() }
        .onDisappear { services.room.detach() }
        .gameDialog(isPresented: $confirmingLeave) {
            GameDialogCard(title: "¿Salir de la sala?",
                           message: isHost ? "Sos el anfitrión: la sala pasa a otro jugador con cuenta." : "Vas a dejar tu lugar en la sala.",
                           negative: "QUEDARME", positive: "SALIR",
                           onNegative: { confirmingLeave = false },
                           onPositive: { confirmingLeave = false; leave() },
                           identifier: "leaveDialog") { EmptyView() }
        }
    }

    @ViewBuilder private var content: some View {
        if let loadError {
            OnlineStatusCard(status: .failed(loadError)) { Task { await attach() } }
            Button("VOLVER") { dismiss() }.buttonStyle(TraidoresButtonStyle())
        } else if let room {
            if case .closed = room.phase {
                closedCard
            } else {
                startPanel(room)
                invitePanel(room)
                playersPanel(room)
                rulesPanel(room)
                mapVotePanel(room)
            }
        } else {
            OnlineStatusCard(status: .loading("Entrando a la sala…"))
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Button { confirmingLeave = room != nil && loadError == nil; if !confirmingLeave { dismiss() } } label: {
                Image(systemName: "chevron.left").font(.headline)
                    .frame(width: 46, height: 46)
                    .background(TraidoresTheme.panel, in: Circle())
                    .overlay { Circle().stroke(TraidoresTheme.border) }
            }
            .foregroundStyle(TraidoresTheme.text)
            .accessibilityLabel("Salir de la sala")
            .accessibilityShowsLargeContentViewer()
            .accessibilityIdentifier("lobby.online.leave")

            VStack(alignment: .leading, spacing: 1) {
                Text(room?.name ?? "SALA ONLINE")
                    .font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                    .lineLimit(2).minimumScaleFactor(0.8)
                if let room {
                    Text("\(room.players.count)/\(room.expected) jugadores · \(room.isPublic ? "Pública" : "Privada")")
                        .font(.caption).foregroundStyle(TraidoresTheme.secondary)
                        .accessibilityIdentifier("lobby.online.count")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 13).padding(.vertical, 7)
            .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 12))
            .overlay { RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border) }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("lobby.online.header")
        }
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder private var connectionBanner: some View {
        let text: String? = switch services.room.connection {
        case .reconnecting: "Se cortó la conexión. Reconectando…"
        case .lost: "Sin conexión con la sala. Tus cambios se aplicarán al volver."
        default: nil
        }
        if let text {
            Label(text, systemImage: "wifi.exclamationmark")
                .font(.callout.bold())
                .foregroundStyle(TraidoresTheme.ink)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(TraidoresTheme.gold)
                .accessibilityIdentifier("lobby.online.connection")
                .onAppear { AccessibilityNotification.Announcement(text).post() }
        }
    }

    private func startPanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if isHost {
                if services.room.startAvailability == .ready {
                    Button("INICIAR PARTIDA") {}
                        .buttonStyle(TraidoresButtonStyle(prominent: true))
                        .accessibilityIdentifier("lobby.online.start")
                } else {
                    // Locked, not faded: the reason below explains it and the label stays readable.
                    Button {} label: { Label("INICIAR PARTIDA", systemImage: "lock.fill") }
                        .buttonStyle(LockedButtonStyle())
                        .disabled(true)
                        .accessibilityIdentifier("lobby.online.start")
                }
            }
            Text(startMessage(room))
                .font(.callout).foregroundStyle(TraidoresTheme.gold)
                .frame(maxWidth: .infinity, alignment: .center).multilineTextAlignment(.center)
                .accessibilityIdentifier("lobby.online.startStatus")
            if let me {
                Button(me.isReady ? "YA NO ESTOY LISTO" : "ESTOY LISTO") {
                    perform { try await services.room.setReady(!me.isReady) }
                }
                .buttonStyle(TraidoresButtonStyle(prominent: !me.isReady && !isHost))
                .disabled(working)
                .accessibilityIdentifier("lobby.online.ready")
            }
            if let actionError { OnlineInlineError(error: actionError) }
        }
        .onlinePanel()
    }

    private func startMessage(_ room: RoomSnapshot) -> String {
        let ready = room.players.filter(\.isReady).count
        let waiting = "\(ready) de \(room.expected) jugadores listos."
        switch services.room.startAvailability {
        case .unavailable(let feature): return "\(waiting) \(feature.unavailableMessage)"
        case .waitingForPlayers: return isHost ? waiting : "\(waiting) Esperando a que el anfitrión inicie."
        case .ready: return isHost ? "Todos listos. Ya podés iniciar." : "Todos listos. Esperando al anfitrión."
        }
    }

    private func invitePanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            OnlineSectionLabel("INVITAR")
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("CÓDIGO DE SALA").font(.caption.bold()).foregroundStyle(TraidoresTheme.secondary)
                    Text(room.code).font(.title.monospaced().bold()).foregroundStyle(TraidoresTheme.gold)
                        .accessibilityLabel(room.code.map(String.init).joined(separator: " "))
                        .accessibilityIdentifier("lobby.online.code")
                }
                Spacer(minLength: 0)
                Button {
                    UIPasteboard.general.string = room.code
                    copied = true
                    AccessibilityNotification.Announcement("Código copiado").post()
                } label: {
                    Label(copied ? "COPIADO" : "COPIAR", systemImage: copied ? "checkmark" : "doc.on.doc")
                        .font(.caption.bold())
                }
                .buttonStyle(.bordered).tint(TraidoresTheme.gold)
                .accessibilityIdentifier("lobby.online.copy")
                ShareLink(item: "Sumate a mi sala de Traidores con el código \(room.code).") {
                    Label("COMPARTIR", systemImage: "square.and.arrow.up").font(.caption.bold())
                }
                .buttonStyle(.bordered).tint(TraidoresTheme.gold)
            }
        }
        .onlinePanel()
    }

    private func playersPanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            OnlineSectionLabel("EN LA SALA")
            ForEach(room.players.sorted { $0.order < $1.order }) { player in
                playerRow(player, room: room)
            }
            let missing = max(0, room.expected - room.players.count)
            if missing > 0 {
                Text(missing == 1 ? "Falta 1 jugador." : "Faltan \(missing) jugadores.")
                    .font(.callout).foregroundStyle(TraidoresTheme.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .onlinePanel()
    }

    private func playerRow(_ player: RoomPlayer, room: RoomSnapshot) -> some View {
        let isMe = player.id == myUid
        let status = [player.id == room.hostId ? "ANFITRIÓN" : nil,
                      !player.isConnected ? "DESCONECTADO" : player.isReady ? "LISTO" : "ESPERANDO"]
            .compactMap { $0 }.joined(separator: " · ")
        return HStack(spacing: 11) {
            // Identity is the UID: a player who shares a name never borrows another's photo.
            OnlinePortrait(photoURL: player.avatarURL, avatarKey: player.avatarPerfil, size: 42,
                           localPhoto: isMe ? services.profile.pendingPhotoData : nil)
                .opacity(player.isConnected ? 1 : 0.5)
            VStack(alignment: .leading, spacing: 1) {
                Text(isMe ? "\(player.nombreSala) (vos)" : player.nombreSala).font(.headline)
                Text(status).font(.caption.bold())
                    .foregroundStyle(player.isReady ? .green.opacity(0.9) : TraidoresTheme.secondary)
            }
            Spacer(minLength: 0)
            Image(systemName: !player.isConnected ? "wifi.slash" : player.isReady ? "checkmark.circle.fill" : "hourglass")
                .foregroundStyle(player.isReady && player.isConnected ? .green.opacity(0.85) : TraidoresTheme.secondary)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 58)
        .background(TraidoresTheme.ink.opacity(isMe ? 0.92 : 0.72), in: RoundedRectangle(cornerRadius: 10))
        .overlay { if isMe { RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.gold.opacity(0.6)) } }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("lobby.online.player.\(player.id)")
    }

    private func rulesPanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            OnlineSectionLabel("CONFIGURACIÓN")
            Text("Roles al morir: \(room.config.revelarRolesAlMorir ? "Sí" : "No") · Votos: \(room.config.votosIndividuales ? "Individuales" : "Totales") · Debate: \(room.config.discusionSeg) s")
                .font(.callout)
                .accessibilityIdentifier("lobby.online.rules")
            if isHost {
                ruleToggle("Mostrar el rol al morir", isOn: room.config.revelarRolesAlMorir, id: "reveal") { value in
                    var config = room.config; config.revelarRolesAlMorir = value; return config
                }
                ruleToggle("Mostrar quién votó a quién", isOn: room.config.votosIndividuales, id: "votes") { value in
                    var config = room.config; config.votosIndividuales = value; return config
                }
            } else {
                Text("Solo el anfitrión cambia las reglas.")
                    .font(.caption).foregroundStyle(TraidoresTheme.secondary)
            }
        }
        .onlinePanel()
    }

    private func ruleToggle(_ title: String, isOn: Bool, id: String,
                            change: @escaping (Bool) -> LobbyConfig) -> some View {
        Toggle(title, isOn: Binding(get: { isOn }, set: { value in
            perform { try await services.room.updateConfig(change(value)) }
        }))
        .tint(TraidoresTheme.gold)
        .disabled(working)
        .accessibilityIdentifier("lobby.online.rule.\(id)")
    }

    private func mapVotePanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            OnlineSectionLabel("VOTACIÓN DE MAPA")
            HStack(spacing: 8) {
                ForEach(GameMap.allCases, id: \.rawValue) { map in
                    let votes = room.players.filter { $0.mapVote == map.rawValue }.count
                    let mine = me?.mapVote == map.rawValue
                    Button { perform { try await services.room.voteMap(map.rawValue) } } label: {
                        VStack(spacing: 4) {
                            Image(map.landscapeAsset).resizable().scaledToFill()
                                .frame(maxWidth: .infinity).frame(height: 56).clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 9))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 9)
                                        .stroke(mine ? TraidoresTheme.gold : TraidoresTheme.border, lineWidth: mine ? 2 : 1)
                                }
                            Text(map.title).font(.caption.bold())
                            Text(votes == 1 ? "1 voto" : "\(votes) votos").font(.caption)
                                .foregroundStyle(TraidoresTheme.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(working)
                    .accessibilityLabel("\(map.title), \(votes == 1 ? "1 voto" : "\(votes) votos")\(map.rawValue == room.mapKey ? ", mapa de la sala" : "")")
                    .accessibilityAddTraits(mine ? .isSelected : [])
                    .accessibilityIdentifier("lobby.online.vote.\(map.rawValue)")
                }
            }
            Text("Mapa de la sala: \(GameMap(onlineKey: room.mapKey).title). Tocá un mapa para votar.")
                .font(.caption).foregroundStyle(TraidoresTheme.secondary)
        }
        .onlinePanel()
    }

    private var closedCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("LA SALA SE CERRÓ").font(TraidoresTheme.title(21)).foregroundStyle(TraidoresTheme.gold)
            Text("El anfitrión cerró la sala o quedó abandonada.").font(.body)
            Button("VOLVER") { dismiss() }.buttonStyle(TraidoresButtonStyle(prominent: true))
        }
        .onlinePanel()
        .accessibilityIdentifier("lobby.online.closed")
    }

    private func attach() async {
        loadError = nil
        do {
            try await services.room.attach(roomId: roomId)
        } catch {
            loadError = error as? OnlineError ?? .server(nil)
        }
    }

    private func leave() {
        perform {
            // A network error is not a confirmed exit: stay in the room and show it.
            try await services.room.leave()
            dismiss()
        }
    }

    private func perform(_ operation: @escaping () async throws -> Void) {
        actionError = nil
        working = true
        Task {
            defer { working = false }
            do { try await operation() } catch { actionError = error as? OnlineError ?? .server(nil) }
        }
    }
}

/// A button that cannot be used yet, drawn at full contrast instead of faded.
private struct LockedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(TraidoresTheme.title(19, relativeTo: .headline))
            .tracking(1)
            .foregroundStyle(TraidoresTheme.text)
            .frame(maxWidth: .infinity, minHeight: 28)
            .padding(.vertical, 13)
            .padding(.horizontal, 18)
            .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(TraidoresTheme.border, style: StrokeStyle(lineWidth: 1, dash: [5, 4])))
    }
}
