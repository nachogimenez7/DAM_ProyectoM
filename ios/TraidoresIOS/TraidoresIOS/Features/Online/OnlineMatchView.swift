import SwiftUI
import TraidoresCore

/// The online table of a server-authority match. Everything on screen comes from the server
/// projection; taps only send intentions. The countdown is the server deadline seen through
/// Firebase's clock offset, and at zero the table waits for the server («Resolviendo…»).
struct OnlineMatchView: View {
    let roomId: String
    let matchId: String
    let mapKey: String
    let uid: String
    let creatorId: String
    /// Lobby photos and avatars by UID; the match projection carries no profile data.
    let roster: [String: RoomPlayer]
    let close: () -> Void
    let returnToLobby: () -> Void

    @State private var session: ServerMatchSession
    @State private var targeting: ServerMatchActionOption?
    @State private var showingRole = false
    @State private var showingChat = false
    @State private var confirmingLeave = false
    @State private var leaveError: String?
    @State private var leaving = false
    @State private var rematching = false
    @State private var rematchError: String?
    @State private var deserterChoice: ServerMatchActionOption?
    @State private var eventQueue: [OnlineMatchPresentation] = []
    @State private var shownEvent: OnlineMatchPresentation?
    // Profile window of a player: long press, or tap on a card that is not a target right now.
    @State private var profileShown: PlayerProfileSnapshot?
    @State private var profileLongPressConsumedTap = false
    // Report flow (Android's PlayerModeration): reason + optional detail, one write to `reportes`.
    @State private var reportTarget: ReportTarget?
    @State private var reportReason: PlayerReportReason = .toxicidad
    @State private var reportDetail = ""
    @State private var submittingReport = false
    @State private var reportNotice: String?
    private struct ReportTarget: Equatable { let uid: String; let name: String }
    @AppStorage("menu.localProfile.v1") private var storedProfile = Data()
    @AppStorage("menu.profileTheme") private var profileTheme = "classic"
    @AppStorage("menu.profileEmotes") private var profileEmoteIDs = "griego_enojado,griego_triste,griego_contento,griego_sospechoso"
    @Environment(\.reduceAnimations) private var reduceMotion

    init(roomId: String, matchId: String, mapKey: String, uid: String, creatorId: String,
         roster: [String: RoomPlayer], close: @escaping () -> Void, returnToLobby: @escaping () -> Void) {
        self.roomId = roomId
        self.matchId = matchId
        self.mapKey = mapKey
        self.uid = uid
        self.creatorId = creatorId
        self.roster = roster
        self.close = close
        self.returnToLobby = returnToLobby
        _session = State(initialValue: ServerMatchSession(roomId: roomId, matchId: matchId, uid: uid))
    }

    private var map: GameMap { GameMap(onlineKey: mapKey) }

    var body: some View {
        ZStack {
            background
            if let failure = session.failure {
                VStack(spacing: 14) {
                    OnlineStatusCard(status: .failed(.server(failure))) { session.start() }
                    Button("VOLVER") { close() }.buttonStyle(TraidoresButtonStyle())
                }
                .padding(20)
            } else if let state = session.snapshot {
                VStack(spacing: 8) {
                    header(state)
                    if !session.connected { banner("Sin conexión. Reconectando…", systemImage: "wifi.slash") }
                    if session.synchronizing { banner("Sincronizando con el servidor…", systemImage: "arrow.triangle.2.circlepath") }
                    announcement(state)
                    ScrollView { playerGrid(state).padding(.bottom, 8) }
                    if state.publicState.winner == nil { controls(state) } else { resultPanel(state) }
                }
                .padding(.horizontal, 10)
                .padding(.top, 6)
                .padding(.bottom, 8)
                .frame(maxWidth: 620)
                .allowsHitTesting(shownEvent?.usesReveal != true)
                .accessibilityHidden(shownEvent?.usesReveal == true)
            } else {
                OnlineStatusCard(status: .loading("Sincronizando con el servidor…")).padding(20)
            }
            if let event = shownEvent { eventPresentation(event).id(event.id).zIndex(1) }
            if let profileShown {
                PlayerProfileCard(profile: profileShown, moderation: moderationActions(for: profileShown)) {
                    withAnimation(.easeOut(duration: 0.15)) { self.profileShown = nil }
                }
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .onAppear { session.start() }
        .onDisappear {
            // A sheet/cover can hide its presenting view. Keep V3 alive while the
            // player reads their role, chats or confirms a private/server action.
            if !showingRole && !showingChat && !confirmingLeave && deserterChoice == nil { session.stop() }
        }
        .onChange(of: session.freshEvents) { _, events in
            guard !events.isEmpty else { return }
            // «Noche N» is already the header and the announcement; it needs no extra card.
            if let state = session.snapshot?.publicState {
                for event in events where event.code != "NIGHT_START" {
                    eventQueue.append(.init(event: event, state: state))
                    // Silence has no separate V3 event. Present the public muted players
                    // once with the dawn's seq, without inferring any private role.
                    if ["NIGHT_DEATH", "DAWN_NO_VICTIMS"].contains(event.code) {
                        for player in state.players where player.alive && player.muted {
                            eventQueue.append(.init(event: event, state: state, silenced: player))
                        }
                    }
                }
            }
            session.eventsPresented()
            showNextEvent()
        }
        .onChange(of: session.snapshot?.publicState.phaseIndex) { _, _ in
            targeting = nil; deserterChoice = nil; profileShown = nil
        }
        .onChange(of: session.nextMatchId) { _, next in
            guard next != nil else { return }
            targeting = nil
            deserterChoice = nil
            showingRole = false
            showingChat = false
            eventQueue = []
            shownEvent = nil
            session.stop()
            returnToLobby()
        }
        .sheet(isPresented: $showingRole) { if let state = session.snapshot { roleSheet(state) } }
        .sheet(isPresented: $showingChat, onDismiss: { session.closeChat() }) {
            OnlineMatchChat(session: session, roster: roster).onAppear { session.openChat() }
        }
        .gameDialog(isPresented: Binding(get: { reportTarget != nil }, set: { if !$0 { reportTarget = nil } })) {
            if let target = reportTarget {
                GameDialogCard(title: "REPORTAR JUGADOR",
                               message: "¿Por qué querés reportar a \(target.name)?",
                               negative: "CANCELAR", positive: submittingReport ? "ENVIANDO…" : "ENVIAR",
                               positiveEnabled: !submittingReport,
                               onNegative: { reportTarget = nil },
                               onPositive: { submitReport(target) },
                               identifier: "matchReportDialog") { reportForm }
            }
        }
        .gameDialog(isPresented: Binding(get: { reportNotice != nil }, set: { if !$0 { reportNotice = nil } })) {
            GameDialogCard(title: "REPORTE", message: reportNotice ?? "", negative: nil, positive: "ENTENDIDO",
                           onPositive: { reportNotice = nil }, identifier: "matchReportNotice") { EmptyView() }
        }
        .gameDialog(isPresented: $confirmingLeave) {
            GameDialogCard(title: "¿Salir de la partida?",
                           message: session.snapshot?.publicState.winner == nil
                               ? "Si salís ahora, la partida cuenta como derrota y no podés volver a entrar."
                               : "Tu resultado ya quedó registrado.",
                           negative: "QUEDARME", positive: "SALIR",
                           onNegative: { confirmingLeave = false },
                           onPositive: { confirmingLeave = false; leave() },
                           identifier: "matchLeaveDialog") { EmptyView() }
        }
        .gameDialog(isPresented: Binding(get: { deserterChoice != nil }, set: { if !$0 { deserterChoice = nil } })) {
            if let choice = deserterChoice {
                GameDialogCard(title: choice.label.capitalized,
                               message: choice.action == "desertor_rethink"
                                   ? "Tanto mantener como cambiar el bando consume tu única revisión. Esta elección es privada."
                                   : "Esta elección es privada. Desde la ronda 4 vas a poder revisar tu bando una sola vez.",
                               negative: "VOLVER", positive: "CONFIRMAR",
                               positiveEnabled: session.options.contains(choice),
                               onNegative: { deserterChoice = nil },
                               onPositive: { session.send(choice); deserterChoice = nil },
                               identifier: "matchDeserterDialog") { EmptyView() }
            }
        }
    }

    // MARK: Pieces

    private var background: some View {
        GeometryReader { geometry in
            Image(session.snapshot?.publicState.phase.isNight == true ? map.nightBackgroundAsset : map.dayBackgroundAsset)
                .resizable().scaledToFill()
                .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                .overlay(.black.opacity(0.5))
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    private func header(_ state: ServerMatchSnapshot) -> some View {
        HStack(spacing: 10) {
            Button { if state.publicState.winner == nil { confirmingLeave = true } else { leave() } } label: {
                Image(systemName: "chevron.left").font(.headline)
                    .frame(width: 44, height: 44)
                    .background(TraidoresTheme.panel, in: Circle())
                    .overlay { Circle().stroke(TraidoresTheme.border) }
            }
            .accessibilityLabel("Salir de la partida")
            .accessibilityIdentifier("match.leave")
            .disabled(leaving || rematching)
            VStack(alignment: .leading, spacing: 1) {
                Text(state.publicState.phase.title.uppercased())
                    .font(TraidoresTheme.title(19)).foregroundStyle(TraidoresTheme.gold)
                Text(state.publicState.phase == .assignment ? map.title : "Ronda \(state.publicState.round) · \(map.title)")
                    .font(.caption).foregroundStyle(TraidoresTheme.secondary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            countdown(state)
            Button { showingChat = true } label: {
                Image(systemName: "bubble.left.and.bubble.right.fill").font(.headline)
                    .frame(width: 44, height: 44)
                    .background(TraidoresTheme.panel, in: Circle())
                    .overlay { Circle().stroke(TraidoresTheme.border) }
            }
            .accessibilityLabel("Chat")
            .accessibilityIdentifier("match.chat")
        }
        .padding(8)
        .background(TraidoresTheme.ink.opacity(0.85), in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    @ViewBuilder private func countdown(_ state: ServerMatchSnapshot) -> some View {
        if state.publicState.winner != nil {
            EmptyView()
        } else if session.resolving {
            HStack(spacing: 6) {
                ProgressView().tint(TraidoresTheme.gold)
                Text("Resolviendo…").font(.caption.bold())
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("match.resolving")
        } else if let seconds = session.remainingSeconds {
            Text(String(format: "%d:%02d", seconds / 60, seconds % 60))
                .font(.title3.monospacedDigit().bold())
                .foregroundStyle(seconds <= 5 ? Color.red : TraidoresTheme.gold)
                .accessibilityLabel("Quedan \(seconds) segundos")
                .accessibilityIdentifier("match.countdown")
        }
    }

    private func banner(_ text: String, systemImage: String) -> some View {
        Label(text, systemImage: systemImage)
            .font(.footnote.bold()).foregroundStyle(TraidoresTheme.ink)
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(TraidoresTheme.gold, in: RoundedRectangle(cornerRadius: 10))
    }

    @ViewBuilder private func announcement(_ state: ServerMatchSnapshot) -> some View {
        let text = state.publicState.phase == .deserterWindow && state.ownRole != .deserter
            ? "El Desertor decide su bando antes de cerrar la partida."
            : state.publicState.announcement
        if !text.isEmpty {
            Text(text)
                .font(.callout).multilineTextAlignment(.center)
                .frame(maxWidth: .infinity).padding(.horizontal, 12).padding(.vertical, 8)
                .background(TraidoresTheme.panel.opacity(0.92), in: RoundedRectangle(cornerRadius: 12))
                .overlay { RoundedRectangle(cornerRadius: 12).stroke(TraidoresTheme.border) }
                .accessibilityIdentifier("match.announcement")
        }
    }

    private func playerGrid(_ state: ServerMatchSnapshot) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
            ForEach(state.publicState.players) { player in
                playerCard(player, state: state)
            }
        }
    }

    private func playerCard(_ player: ServerMatchPlayer, state: ServerMatchSnapshot) -> some View {
        let role = state.role(of: player)
        let selectable = targeting?.targets.contains(player.uid) == true
        let profile = roster[player.uid]
        let tags = cardTags(player, state: state)
        return Button {
            // A long press that opened the profile must not also act when the finger lifts.
            if profileLongPressConsumedTap { profileLongPressConsumedTap = false; return }
            guard let option = targeting, selectable else { showProfile(of: player); return }
            guard session.pending == nil else { return }
            session.send(option, target: player.uid)
            if option.action != "votar" { targeting = nil }
        } label: {
            VStack(spacing: 5) {
                ZStack(alignment: .topTrailing) {
                    Group {
                        if let role {
                            Image(role.classicImage(on: map)).resizable().scaledToFill()
                        } else {
                            OnlinePortrait(photoURL: profile?.avatarURL, avatarKey: profile?.avatarPerfil ?? "aldeano", size: 84)
                        }
                    }
                    .frame(width: 84, height: 84)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .saturation(player.alive ? 1 : 0)
                    .opacity(player.alive ? 1 : 0.6)
                    if player.muted {
                        Image(systemName: "speaker.slash.fill").font(.caption.bold())
                            .padding(5).background(TraidoresTheme.ink, in: Circle()).foregroundStyle(TraidoresTheme.gold)
                    }
                }
                Text(player.uid == uid ? "\(player.name) (vos)" : player.name)
                    .font(.caption.bold()).lineLimit(2).multilineTextAlignment(.center)
                if let role { Text(role.classicTitle(on: map).uppercased()).font(.caption2.bold()).foregroundStyle(TraidoresTheme.gold) }
                ForEach(tags, id: \.self) { tag in
                    Text(tag).font(.caption2.bold()).foregroundStyle(TraidoresTheme.ink)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(TraidoresTheme.gold, in: Capsule())
                }
            }
            .padding(8)
            .frame(maxWidth: .infinity, minHeight: 150, alignment: .top)
            .background(TraidoresTheme.ink.opacity(0.88), in: RoundedRectangle(cornerRadius: 14))
            .overlay {
                RoundedRectangle(cornerRadius: 14)
                    .stroke(selectable ? TraidoresTheme.gold : TraidoresTheme.border, lineWidth: selectable ? 3 : 1)
            }
        }
        .buttonStyle(.plain)
        // Not `.disabled`: SwiftUI would fade every card that cannot be chosen right now.
        .simultaneousGesture(LongPressGesture(minimumDuration: 0.4).onEnded { _ in
            profileLongPressConsumedTap = true
            showProfile(of: player)
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(700))
                profileLongPressConsumedTap = false
            }
        })
        .accessibilityElement(children: .combine)
        .accessibilityHint(selectable ? (targeting?.label.capitalized ?? "") : "")
        .accessibilityAddTraits(selectable ? .isButton : [])
        .accessibilityIdentifier("match.player.\(player.uid)")
    }

    /// Public data only: the lobby entry (name, number, photo or animal). The match never sends
    /// a player's role, and none is shown here. Your own window adds your local profile.
    private func showProfile(of player: ServerMatchPlayer) {
        let entry = roster[player.uid]
        var snapshot = PlayerProfileSnapshot(
            id: player.uid, name: player.name, kind: player.uid == uid ? .me : .player,
            publicId: entry?.publicId, bio: entry?.bioPerfil, avatarKey: AnimalAvatarCatalog.normalize(entry?.avatarPerfil ?? "avatar_carpincho"),
            photoURL: entry?.avatarURL, bannerKey: entry?.bannerPerfil, favoriteRoleKey: entry?.rolFavoritoPerfil,
            emoteIDs: entry?.emotesPerfil ?? [], matches: entry?.estadisticas?.matches, wins: entry?.estadisticas?.wins,
            styleRaw: entry?.temaCosmeticoPerfil ?? "classic")
        if player.uid == uid {
            snapshot = .own(name: player.name, storedProfile: storedProfile, theme: profileTheme,
                            emoteIDs: profileEmoteIDs, publicId: entry?.publicId)
        } else {
            snapshot.isMuted = LocalMuteStore.isMuted(publicId: entry?.publicId, uid: player.uid)
        }
        withAnimation(.easeOut(duration: 0.15)) { profileShown = snapshot }
    }

    private func moderationActions(for profile: PlayerProfileSnapshot) -> ProfileModerationActions? {
        guard profile.kind == .player else { return nil }
        return ProfileModerationActions(
            toggleMute: {
                profileShown?.isMuted = LocalMuteStore.toggle(publicId: profile.publicId, uid: profile.id)
            },
            report: {
                withAnimation(.easeOut(duration: 0.15)) { profileShown = nil }
                reportReason = .toxicidad
                reportDetail = ""
                reportTarget = ReportTarget(uid: profile.id, name: profile.name)
            })
    }

    private var reportForm: some View {
        VStack(spacing: 8) {
            ForEach(PlayerReportReason.allCases) { reason in
                let selected = reportReason == reason
                Button { reportReason = reason } label: {
                    HStack(spacing: 10) {
                        Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(selected ? TraidoresTheme.gold : TraidoresTheme.secondary)
                        Text(reason.title).font(.subheadline).foregroundStyle(TraidoresTheme.text)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 10).frame(minHeight: 44)
                    .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10)
                        .stroke(selected ? TraidoresTheme.gold : TraidoresTheme.border))
                    .contentShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityIdentifier("matchReport.reason.\(reason.rawValue)")
            }
            TextField("Detalle opcional (hasta 140 caracteres)", text: $reportDetail)
                .textFieldStyle(OnlineTextFieldStyle())
                .onChange(of: reportDetail) { _, value in
                    if value.count > 140 { reportDetail = String(value.prefix(140)) }
                }
                .accessibilityIdentifier("matchReport.detail")
        }
    }

    private func submitReport(_ target: ReportTarget) {
        submittingReport = true
        let reason = reportReason
        let detail = reportDetail
        Task {
            let outcome = await PlayerReports.submit(roomId: roomId, matchId: matchId, reporterUid: uid,
                                                     reportedUid: target.uid, reportedName: target.name,
                                                     reason: reason, detail: detail)
            submittingReport = false
            reportTarget = nil
            // The second full-screen dialog waits for the first one to leave.
            try? await Task.sleep(for: .milliseconds(350))
            reportNotice = switch outcome {
            case .sent: "Gracias. Vamos a revisarlo."
            case .refused: "El servidor no aceptó el reporte. Si ya reportaste a este jugador en esta partida, ya lo tenemos."
            case .failed: "No pudimos enviar el reporte. Probá de nuevo cuando tengas conexión."
            case .invalid: "No se pudo preparar el reporte."
            }
        }
    }

    private func cardTags(_ player: ServerMatchPlayer, state: ServerMatchSnapshot) -> [String] {
        let p = state.publicState
        var tags: [String] = []
        if !player.alive {
            let cause = switch player.deathCause {
            case "VOTE": "EXPULSADO"
            case "AFK": "INACTIVO"
            case "ABANDONO": "ABANDONÓ"
            default: "MUERTO"
            }
            tags.append(cause)
        }
        if p.mayorUid == player.uid { tags.append("ALCALDE") }
        if p.oracleGuestUid == player.uid { tags.append("INVITADO") }
        if p.counterpointPlayers.contains(player.uid) && [.debate, .counterpoint].contains(p.phase) { tags.append("CONTRAPUNTO") }
        if p.tieCandidates.contains(player.uid) && [.tieVote, .mayorTie, .voteCount].contains(p.phase) { tags.append("EMPATE") }
        if let votes = p.voteTotals[player.order], votes > 0 { tags.append(votes == 1 ? "1 VOTO" : "\(votes) VOTOS") }
        if let mine = state.privateState.confirmed.first(where: { $0.targetUid == player.uid }) {
            tags.append(Self.confirmedTag(mine.action))
        }
        if let finding = state.privateState.investigations.last(where: { $0.targetUid == player.uid }) {
            tags.append(finding.traitor ? "SOSPECHOSO" : "INOCENTE")
        }
        if p.winner != nil, state.won(player) { tags.append("GANÓ") }
        return tags
    }

    private static func confirmedTag(_ action: String) -> String {
        switch action {
        case "votar": "TU VOTO"
        case "matar": "TU VÍCTIMA"
        case "silenciar": "SILENCIADO POR VOS"
        case "investigar": "INVESTIGADO"
        case "salvar": "PROTEGIDO"
        case "invitar_muerto": "INVITADO"
        case "decidir_empate": "TU DECISIÓN"
        default: "ELEGIDO"
        }
    }

    // MARK: Controls

    private func controls(_ state: ServerMatchSnapshot) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Button { showingRole = true } label: {
                    HStack(spacing: 8) {
                        Image(state.ownRole.classicImage(on: map)).resizable().scaledToFill()
                            .frame(width: 40, height: 40).clipShape(RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading, spacing: 0) {
                            Text("TU ROL").font(.caption2.bold()).foregroundStyle(TraidoresTheme.secondary)
                            Text(state.ownRole.classicTitle(on: map)).font(.subheadline.bold())
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("match.role")
                Spacer(minLength: 0)
                Text(status(state)).font(.caption).foregroundStyle(TraidoresTheme.secondary)
                    .multilineTextAlignment(.trailing).accessibilityIdentifier("match.status")
            }
            let options = session.options
            if state.ownRole == .deserter { deserterPanel(state, options: options) }
            if !options.isEmpty {
                ForEach(options.filter { !$0.action.hasPrefix("desertor_") }) { option in
                    Button {
                        if option.needsTarget {
                            targeting = targeting == option ? nil : option
                        } else {
                            session.send(option)
                        }
                    } label: {
                        Text(targeting == option ? "ELEGÍ EN LA MESA · CANCELAR" : option.label)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(TraidoresButtonStyle(prominent: targeting != option))
                    .accessibilityIdentifier("match.action.\(option.id)")
                }
            }
            if let error = session.actionError {
                Text(error).font(.footnote).foregroundStyle(OnlineInlineError.color).accessibilityIdentifier("match.actionError")
            }
            if let leaveError { Text(leaveError).font(.footnote).foregroundStyle(OnlineInlineError.color) }
        }
        .padding(10)
        .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border) }
    }

    private func status(_ state: ServerMatchSnapshot) -> String {
        if session.pending != nil { return "Enviando…" }
        if session.resolving { return "El servidor está resolviendo la fase." }
        if session.synchronizing { return "Sincronizando…" }
        if !state.me.alive { return state.me.abandoned ? "Saliste de la partida." : "Estás eliminado. Podés seguir mirando." }
        if state.me.muted && [.debate, .vote, .tieVote].contains(state.publicState.phase) { return "Estás silenciado: no podés hablar ni votar." }
        if let mine = state.privateState.confirmed.first(where: { ServerMatchRules.nightActions.contains($0.action) }) {
            return "Acción registrada: \(Self.confirmedTag(mine.action).lowercased())."
        }
        if state.privateState.confirmed.contains(where: { $0.action == "votar" }) { return "Acción registrada: tu voto." }
        if state.publicState.phase == .night && session.options.isEmpty { return "La noche sigue…" }
        if targeting != nil { return "Tocá una carta de la mesa." }
        if state.publicState.phase == .assignment, state.privateState.confirmed.contains(where: { $0.action == "role_ack" }) {
            return "Rol confirmado. Esperando al resto."
        }
        return ""
    }

    private func deserterPanel(_ state: ServerMatchSnapshot, options: [ServerMatchActionOption]) -> some View {
        let choices = options.filter { $0.action.hasPrefix("desertor_") }
        let team = state.privateState.deserterTeam
        return VStack(alignment: .leading, spacing: 8) {
            Text(team.map { "TU BANDO: \($0.uppercased())" } ?? "ELEGÍ TU BANDO")
                .font(.subheadline.bold()).foregroundStyle(TraidoresTheme.gold)
                .accessibilityIdentifier("match.deserter.team")
            Text(ServerMatchDeserterPresentation.explanation(state))
                .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
                .accessibilityIdentifier("match.deserter.explanation")
            ForEach(choices) { option in
                Button(option.label) { deserterChoice = option }
                    .buttonStyle(TraidoresButtonStyle(prominent: option.team != "mantener"))
                    .accessibilityIdentifier("match.action.\(option.id)")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("match.deserter")
    }

    private func resultPanel(_ state: ServerMatchSnapshot) -> some View {
        let winner = state.publicState.winner
        let title = switch winner {
        case "Pueblo": "VICTORIA DEL PUEBLO"
        case "Traidores": "VICTORIA DE LOS TRAIDORES"
        default: "PARTIDA CANCELADA"
        }
        return VStack(spacing: 8) {
            Text(title).font(TraidoresTheme.title(22)).foregroundStyle(TraidoresTheme.gold)
            if winner != "Cancelada" {
                Text(state.won(state.me) ? "¡Ganaste!" : "Perdiste esta vez.").font(.headline)
                ScrollView(.horizontal) {
                    HStack(alignment: .top, spacing: 16) {
                        ForEach(state.publicState.players.filter { state.won($0) }) { player in
                            VStack(spacing: 4) {
                                Text(player.name).font(.caption.bold()).lineLimit(1)
                                if let role = player.publicRole {
                                    Text(role.classicTitle(on: map)).font(.caption2).foregroundStyle(TraidoresTheme.gold)
                                }
                                OnlinePortrait(photoURL: roster[player.uid]?.avatarURL,
                                               avatarKey: roster[player.uid]?.avatarPerfil ?? "aldeano", size: 36)
                            }
                            .frame(width: 100)
                            .accessibilityElement(children: .combine)
                        }
                    }
                }
                .accessibilityIdentifier("match.winners")
            }
            if creatorId == uid {
                Button(rematching ? "PREPARANDO…" : "PREPARAR REVANCHA") { prepareRematch() }
                    .buttonStyle(TraidoresButtonStyle(prominent: true))
                    .disabled(rematching || leaving || !session.connected || session.synchronizing)
                    .accessibilityIdentifier("match.rematch")
            } else {
                Text("El creador puede preparar la revancha para volver a esta sala.")
                    .font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            Button("SALIR") { leave() }
                .buttonStyle(TraidoresButtonStyle())
                .disabled(leaving || rematching)
                .accessibilityIdentifier("match.exit")
            if let leaveError { Text(leaveError).font(.footnote).foregroundStyle(OnlineInlineError.color) }
            if let rematchError { Text(rematchError).font(.footnote).foregroundStyle(OnlineInlineError.color) }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(TraidoresTheme.panel, in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.gold) }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("match.result")
    }

    private func roleSheet(_ state: ServerMatchSnapshot) -> some View {
        let role = state.ownRole
        return ScrollView {
            VStack(spacing: 14) {
                Image(role.classicImage(on: map)).resizable().scaledToFit().frame(maxHeight: 260)
                    .clipShape(RoundedRectangle(cornerRadius: 16)).accessibilityHidden(true)
                Text(role.classicTitle(on: map)).font(TraidoresTheme.title(26)).foregroundStyle(TraidoresTheme.gold)
                Text(role.teamLabel).font(.caption.bold()).tracking(1.2).foregroundStyle(TraidoresTheme.secondary)
                if role == .deserter, let team = state.privateState.deserterTeam {
                    Text("Tu bando actual: \(team).").font(.headline)
                }
                Text(role.classicAdvice).font(.body).multilineTextAlignment(.center)
                let mates = state.privateState.visibleRoles.filter { $0.key != state.me.order }
                if !mates.isEmpty {
                    OnlineSectionLabel("TUS ALIADOS")
                    ForEach(mates.sorted { $0.key < $1.key }, id: \.key) { order, mate in
                        if let player = state.publicState.players.first(where: { $0.order == order }) {
                            Text("\(player.name) · \(mate.classicTitle(on: map))").font(.callout)
                        }
                    }
                }
                if !state.privateState.investigations.isEmpty {
                    OnlineSectionLabel("TUS INVESTIGACIONES")
                    ForEach(Array(state.privateState.investigations.enumerated()), id: \.offset) { _, finding in
                        let name = state.publicState.player(finding.targetUid)?.name ?? "?"
                        Text("Noche \(finding.round): \(name) es \(finding.traitor ? "sospechoso" : "inocente").").font(.callout)
                    }
                }
                Button("CERRAR") { showingRole = false }.buttonStyle(TraidoresButtonStyle(prominent: true))
            }
            .padding(20)
        }
        .foregroundStyle(TraidoresTheme.text)
        .presentationBackground(TraidoresTheme.ink)
        .presentationDetents([.large])
    }

    private func eventToast(_ event: ServerMatchEvent) -> some View {
        VStack {
            Text(event.text)
                .font(.headline).multilineTextAlignment(.center)
                .padding(.horizontal, 18).padding(.vertical, 14)
                .frame(maxWidth: 420)
                .background(TraidoresTheme.ink, in: RoundedRectangle(cornerRadius: 16))
                .overlay { RoundedRectangle(cornerRadius: 16).stroke(TraidoresTheme.gold, lineWidth: 2) }
                .padding(.top, 90).padding(.horizontal, 20)
            Spacer()
        }
        .transition(reduceMotion ? .opacity : .move(edge: .top).combined(with: .opacity))
        .onTapGesture { showNextEvent(skip: true) }
        .accessibilityAddTraits(.isStaticText)
        .accessibilityIdentifier("match.event")
    }

    /// Local reveal art is presentation only: finishing an animation never advances V3.
    /// Only public roles are passed to death reveals; private knowledge cannot reveal a role.
    @ViewBuilder private func eventPresentation(_ presentation: OnlineMatchPresentation) -> some View {
        let event = presentation.event
        if let player = presentation.silenced {
            SilenceRevealView(name: player.name, map: map, onFinished: { finishEvent(presentation) })
        } else {
            let first = presentation.state.player(event.players.first)
            switch event.code {
            case "NIGHT_DEATH":
                if let player = first {
                    DeathRevealView(name: player.name,
                                    role: player.publicRole.map { ($0.classicImage(on: map), $0.classicTitle(on: map)) },
                                    map: map, onFinished: { finishEvent(presentation) })
                } else { eventToast(event) }
            case "DAWN_NO_VICTIMS":
                NoDeathRevealView(map: map, onFinished: { finishEvent(presentation) })
            case "ORACLE_INVITATION":
                if let player = first { OracleRevealView(guest: player.name, onFinished: { finishEvent(presentation) }) }
                else { eventToast(event) }
            case "COUNTERPOINT_OPEN":
                if let player = first, event.players.count > 1, let second = presentation.state.player(event.players[1]) {
                    ContrapuntoRevealView(first: player.name, second: second.name, onFinished: { finishEvent(presentation) })
                } else { eventToast(event) }
            default: eventToast(event)
            }
        }
    }

    private func finishEvent(_ event: OnlineMatchPresentation) {
        if shownEvent?.id == event.id { showNextEvent(skip: true) }
    }

    /// Each server event once, in order; tapping skips to the next.
    private func showNextEvent(skip: Bool = false) {
        guard skip || shownEvent == nil else { return }
        withAnimation(reduceMotion ? nil : .easeOut(duration: 0.25)) {
            shownEvent = eventQueue.isEmpty ? nil : eventQueue.removeFirst()
        }
        guard let event = shownEvent else { return }
        // The reused reveal views own their animation duration and accessible CONTINUAR.
        if event.usesReveal { return }
        AccessibilityNotification.Announcement(event.event.text).post()
        Task {
            try? await Task.sleep(for: .seconds(3.2))
            if shownEvent == event { showNextEvent(skip: true) }
        }
    }

    private func leave() {
        guard !leaving, !rematching else { return }
        leaveError = nil
        // Leaving before the result is a server decision (a recorded defeat), alive or dead.
        guard let state = session.snapshot, !state.me.abandoned else {
            session.stop()
            close()
            return
        }
        leaving = true
        Task {
            defer { leaving = false }
            do {
                try await session.abandon()
                close()
            } catch {
                leaveError = "No se pudo salir. Probá otra vez."
            }
        }
    }

    private func prepareRematch() {
        guard !rematching, !leaving, uid == creatorId else { return }
        rematching = true
        rematchError = nil
        Task {
            do { try await session.prepareRematch() }
            catch {
                rematching = false
                rematchError = "No se pudo preparar la revancha. Esperá la publicación del resultado y probá otra vez."
            }
            if rematching {
                do { try await Task.sleep(for: .seconds(20)) } catch { return }
                if session.nextMatchId == nil {
                    rematching = false
                    rematchError = "Todavía no llegó la revancha. Revisá tu conexión y reintentá."
                }
            }
        }
    }
}

/// Capture the public projection that announced the event. A later final projection
/// cannot disclose a role in an older dawn reveal that originally hid it.
private struct OnlineMatchPresentation: Equatable, Identifiable {
    let event: ServerMatchEvent
    let state: ServerMatchPublic
    var silenced: ServerMatchPlayer? = nil
    var id: String { "\(event.seq):\(silenced?.uid ?? "event")" }
    var usesReveal: Bool {
        if silenced != nil || event.code == "DAWN_NO_VICTIMS" { return true }
        guard state.player(event.players.first) != nil else { return false }
        if ["NIGHT_DEATH", "ORACLE_INVITATION"].contains(event.code) { return true }
        return event.code == "COUNTERPOINT_OPEN" && event.players.count > 1 && state.player(event.players[1]) != nil
    }
}

/// Chat of the match: the channels this player may read, sending only where the server allows.
private struct OnlineMatchChat: View {
    let session: ServerMatchSession
    let roster: [String: RoomPlayer]
    @State private var draft = ""
    @State private var error: String?
    @State private var sending = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Text("CHAT").font(TraidoresTheme.title(20)).foregroundStyle(TraidoresTheme.gold)
                Spacer()
                Button("CERRAR") { dismiss() }.font(.footnote.bold()).foregroundStyle(TraidoresTheme.gold)
            }
            if session.chatChannels.count > 1 {
                Picker("Canal", selection: Binding(get: { session.chatChannel }, set: { session.selectChannel($0) })) {
                    ForEach(session.chatChannels) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
            }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(session.chatMessages.filter { !isMuted($0.uid) }) { message in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(name(message.uid)).font(.caption.bold()).foregroundStyle(TraidoresTheme.gold)
                                Text(message.text).font(.body)
                            }
                            .id(message.id)
                            .accessibilityElement(children: .combine)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: session.chatMessages.last?.id) { _, last in if let last { proxy.scrollTo(last, anchor: .bottom) } }
            }
            if session.canSend(on: session.chatChannel) {
                HStack {
                    TextField("Escribí un mensaje", text: $draft)
                        .textFieldStyle(OnlineTextFieldStyle())
                        .onSubmit(send)
                        .accessibilityIdentifier("match.chat.input")
                    Button("ENVIAR", action: send).font(.footnote.bold()).disabled(sending || draft.isEmpty)
                        .foregroundStyle(TraidoresTheme.gold)
                }
            } else {
                Text("Ahora no podés escribir en este canal.").font(.footnote).foregroundStyle(TraidoresTheme.secondary)
            }
            if let error { Text(error).font(.footnote).foregroundStyle(OnlineInlineError.color) }
        }
        .padding(16)
        .foregroundStyle(TraidoresTheme.text)
        .presentationBackground(TraidoresTheme.ink)
        .presentationDetents([.medium, .large])
    }

    private func name(_ uid: String) -> String {
        session.snapshot?.publicState.player(uid)?.name ?? roster[uid]?.nombreSala ?? "Jugador"
    }

    /// A local mute (Android's «Silenciar para mí») hides that player's chat on this device only.
    private func isMuted(_ uid: String) -> Bool {
        LocalMuteStore.isMuted(publicId: roster[uid]?.publicId, uid: uid)
    }

    private func send() {
        let text = draft
        sending = true
        error = nil
        Task {
            defer { sending = false }
            do {
                try await session.sendChat(text)
                draft = ""
            } catch let failure as OnlineError {
                if case .server(let message?) = failure { error = message } else { error = "No se pudo enviar." }
            } catch {
                self.error = "No se pudo enviar."
            }
        }
    }
}
