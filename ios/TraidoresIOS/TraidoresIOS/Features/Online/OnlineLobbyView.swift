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
    // Profile window of the player whose row was tapped (public lobby data; yours adds your profile).
    @State private var profileShown: PlayerProfileSnapshot?
    @AppStorage("menu.localProfile.v1") private var storedProfile = Data()
    @AppStorage("menu.profileTheme") private var profileTheme = "classic"
    @AppStorage("menu.profileEmotes") private var profileEmoteIDs = "griego_enojado,griego_triste,griego_contento,griego_sospechoso"

    private var room: RoomSnapshot? { services.room.snapshot }
    private var myUid: String? {
        if case .ready(let identity) = services.account.access { identity.uid } else { nil }
    }
    private var me: RoomPlayer? { room?.players.first { $0.id == myUid } }
    private var isHost: Bool { room != nil && room?.hostId == myUid }

    // The match stays on screen until the player leaves it, even after the room is finished.
    @State private var activeMatch: String?

    // Roster and map captured when the match starts: the room document keeps changing after.
    @State private var matchRoster: [String: RoomPlayer] = [:]
    @State private var matchMap = "pampa"
    @State private var matchCreatorId = ""
    @State private var completedMatch: String?

    var body: some View {
        Group {
            if let matchId = activeMatch, let myUid {
                OnlineMatchView(roomId: roomId, matchId: matchId, mapKey: matchMap, uid: myUid, creatorId: matchCreatorId,
                                roster: matchRoster, close: { activeMatch = nil; dismiss() },
                                returnToLobby: {
                                    completedMatch = activeMatch
                                    activeMatch = nil
                                    matchRoster = [:]
                                    enterMatchIfStarted(room?.phase)
                                })
            } else {
                lobby
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await attach() }
        .onDisappear { services.room.detach() }
        .onChange(of: room?.phase) { _, phase in enterMatchIfStarted(phase) }
    }

    private func enterMatchIfStarted(_ phase: RoomPhase?) {
        guard activeMatch == nil, case .inGame(let matchId)? = phase, matchId != completedMatch, let room else { return }
        matchRoster = Dictionary(room.players.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        matchMap = room.mapKey
        matchCreatorId = room.hostId
        activeMatch = matchId
    }

    private var lobby: some View {
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
                .onlineScrollEdges()
            }
            if let profileShown {
                PlayerProfileCard(profile: profileShown) {
                    withAnimation(.easeOut(duration: 0.15)) { self.profileShown = nil }
                }
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
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
            } else if case .inGame = room.phase {
                inGameCard
            } else {
                startPanel(room)
                invitePanel(room)
                playersPanel(room)
                rulesPanel(room)
                mapPanel(room)
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
                    Button("INICIAR PARTIDA") {
                        perform { _ = try await services.room.start(hostTieBreakChoice: nil) }
                    }
                        .buttonStyle(TraidoresButtonStyle(prominent: true))
                        .disabled(working)
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
        .contentShape(RoundedRectangle(cornerRadius: 10))
        .onTapGesture { showProfile(of: player, isMe: isMe) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Abre su perfil")
        .accessibilityIdentifier("lobby.online.player.\(player.id)")
    }

    private func showProfile(of player: RoomPlayer, isMe: Bool) {
        var snapshot = PlayerProfileSnapshot(
            id: player.id, name: player.nombreSala, kind: isMe ? .me : .player, publicId: player.publicId,
            bio: player.bioPerfil, avatarKey: AnimalAvatarCatalog.normalize(player.avatarPerfil),
            photoURL: player.avatarURL, bannerKey: player.bannerPerfil, favoriteRoleKey: player.rolFavoritoPerfil,
            emoteIDs: player.emotesPerfil, matches: player.estadisticas?.matches, wins: player.estadisticas?.wins,
            styleRaw: player.temaCosmeticoPerfil)
        if isMe {
            snapshot = .own(name: player.nombreSala, storedProfile: storedProfile, theme: profileTheme,
                            emoteIDs: profileEmoteIDs, publicId: player.publicId)
        }
        withAnimation(.easeOut(duration: 0.15)) { profileShown = snapshot }
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

    /// The host picks the map, as on Android; the server ignores votes (`resolveMap`).
    /// Changing it clears everyone's «listo».
    private func mapPanel(_ room: RoomSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            OnlineSectionLabel("MAPA")
            HStack(spacing: 8) {
                ForEach(GameMap.allCases, id: \.rawValue) { map in
                    let selected = map.rawValue == room.mapKey
                    Button { perform { try await services.room.voteMap(map.rawValue) } } label: {
                        VStack(spacing: 4) {
                            Image(map.landscapeAsset).resizable().scaledToFill()
                                .frame(maxWidth: .infinity).frame(height: 56).clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 9))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 9)
                                        .stroke(selected ? TraidoresTheme.gold : TraidoresTheme.border, lineWidth: selected ? 2 : 1)
                                }
                                .opacity(selected || isHost ? 1 : 0.55)
                            Text(map.title).font(.caption.bold()).foregroundStyle(TraidoresTheme.text)
                        }
                    }
                    .buttonStyle(OnlineMapChoiceStyle())
                    .disabled(working || !isHost)
                    .accessibilityLabel(selected ? "\(map.title), mapa de la sala" : map.title)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                    .accessibilityIdentifier("lobby.online.map.\(map.rawValue)")
                }
            }
            Text(isHost ? "Tocá un mapa para cambiarlo. Todos vuelven a marcar LISTO." :
                    "El anfitrión elige el mapa: \(GameMap(onlineKey: room.mapKey).title).")
                .font(.caption).foregroundStyle(TraidoresTheme.secondary)
        }
        .onlinePanel()
    }

    private var inGameCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("PARTIDA EN CURSO").font(TraidoresTheme.title(21)).foregroundStyle(TraidoresTheme.gold)
            Text("El servidor ya repartió los roles. Sincronizando la mesa online…")
                .font(.body)
            Button("SALIR DE LA PARTIDA") { confirmingLeave = true }
                .buttonStyle(TraidoresButtonStyle())
                .accessibilityIdentifier("lobby.online.abandon")
        }
        .onlinePanel()
        .accessibilityIdentifier("lobby.online.inGame")
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

/// Disabled map choices remain readable; only the preview image is dimmed explicitly.
private struct OnlineMapChoiceStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
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
