import FirebaseAuth
import FirebaseDatabase
import Foundation
import Observation
import TraidoresCore

struct ServerChatMessage: Identifiable, Equatable {
    let id: String
    let uid: String
    let text: String
    let timestampMs: Int64
}

enum ServerChatChannel: String, CaseIterable, Identifiable {
    case town = "publico", traitors = "traidores", dead = "muertos"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .town: "PUEBLO"
        case .traitors: "TRAIDORES"
        case .dead: "ESPECTADORES"
        }
    }
}

/// One server-authority match on this device, like Android's `ServerGameRealtimeClient` +
/// `ServerGameActionSender` + `ServerGameChat`. It reads only the public projection, this
/// UID's private data and this UID's permissions; it sends intentions and never resolves,
/// deals or times a phase. `ClassicGame` is not involved.
@MainActor @Observable
final class ServerMatchSession {
    let roomId: String
    let matchId: String
    let uid: String

    private(set) var snapshot: ServerMatchSnapshot?
    private(set) var connected = true
    private(set) var synchronizing = true
    private(set) var failure: String?
    /// A different `matchId` was published: the room moved on (rematch).
    private(set) var nextMatchId: String?
    private(set) var pending: ServerMatchCommand?
    private(set) var actionError: String?
    /// Server events not yet shown on this device (each one once per `matchId + seq`).
    private(set) var freshEvents: [ServerMatchEvent] = []
    private(set) var phaseChanged = false
    private(set) var chatChannel: ServerChatChannel = .town
    private(set) var chatMessages: [ServerChatMessage] = []
    private(set) var now = Date()

    @ObservationIgnored private var serverOffsetMs: Double = 0
    @ObservationIgnored private var inbox: ServerMatchInbox
    @ObservationIgnored private var tracker: ServerMatchPresentationTracker
    @ObservationIgnored private var recovery: ServerMatchRecoveryPolicy
    @ObservationIgnored private var bindings: [(DatabaseReference, UInt)] = []
    @ObservationIgnored private var chatBinding: (DatabaseQuery, UInt)?
    @ObservationIgnored private var tick: Task<Void, Never>?
    @ObservationIgnored private var presenceArmed = false
    @ObservationIgnored private var started = false
    @ObservationIgnored private var recovering = false
    /// The server accepted `pending`; it stays locked until the projection that reflects it arrives.
    @ObservationIgnored private var receiptAt: Date?
    @ObservationIgnored private var accessRetries = 0
    @ObservationIgnored private var accessRetryTask: Task<Void, Never>?
    @ObservationIgnored private var lifecycle = 0
    @ObservationIgnored private var chatVisible = false
    @ObservationIgnored private var chatGeneration = 0

    private var root: DatabaseReference { Database.database().reference(withPath: "onlineV3/\(roomId)") }
    private static var cursorKey: String { "online.serverMatch.cursor" }

    init(roomId: String, matchId: String, uid: String) {
        self.roomId = roomId
        self.matchId = matchId
        self.uid = uid
        inbox = ServerMatchInbox(ownUid: uid, matchId: matchId)
        recovery = ServerMatchRecoveryPolicy(uid: uid)
        // Presentation survives app relaunches: a reconnection never replays seen events.
        let saved = UserDefaults.standard.data(forKey: Self.cursorKey)
            .flatMap { try? JSONDecoder().decode(ServerMatchPresentationCursor.self, from: $0) }
        tracker = ServerMatchPresentationTracker(restored: saved?.matchId == matchId ? saved : nil)
    }

    /// Server time: local clock plus Firebase's measured offset. Countdowns never write a clock.
    var nowMs: Int64 { Int64(now.timeIntervalSince1970 * 1000 + serverOffsetMs) }

    var remainingSeconds: Int? {
        guard let deadline = snapshot?.publicState.deadlineMs else { return nil }
        return max(0, Int((deadline - nowMs + 999) / 1000))
    }

    /// The countdown reached zero and the server has not published the next phase yet.
    var resolving: Bool {
        guard let state = snapshot?.publicState, state.winner == nil, let deadline = state.deadlineMs else { return false }
        return nowMs >= deadline
    }

    var options: [ServerMatchActionOption] {
        guard started, connected, !synchronizing, failure == nil, let snapshot, pending == nil else { return [] }
        return ServerMatchActionPolicy.options(snapshot, nowMs: nowMs)
    }

    var chatChannels: [ServerChatChannel] {
        guard let snapshot else { return [.town] }
        var channels: [ServerChatChannel] = [.town]
        if snapshot.me.alive && ServerMatchRules.traitorRoles.contains(snapshot.ownRole) { channels.append(.traitors) }
        if !snapshot.me.alive { channels.append(.dead) }
        return channels
    }

    func canSend(on channel: ServerChatChannel) -> Bool {
        guard started, connected, !synchronizing, failure == nil, let snapshot, !resolving else { return false }
        switch channel {
        case .town: return snapshot.permissions.publicChat
        case .traitors: return snapshot.permissions.traitorChat
        case .dead: return snapshot.permissions.deadChat
        }
    }

    // MARK: Lifecycle

    func start() {
        guard !started, FirebaseSetup.configureIfNeeded() else { return }
        started = true
        lifecycle += 1
        let generation = lifecycle
        failure = nil
        synchronizing = true
        listen(root.child("snapshot/public")) { [weak self] value in
            guard let self else { return }
            let state = try ServerMatchParser.parsePublic(value)
            if state.matchId != self.matchId { self.nextMatchId = state.matchId; return }
            self.inbox.accept(state)
            self.deliver()
        }
        listen(root.child("snapshot/private/\(uid)")) { [weak self] value in
            guard let self else { return }
            let own = try ServerMatchParser.parsePrivate(value)
            guard own.matchId == self.matchId else { return }
            self.inbox.accept(own)
            self.deliver()
        }
        listen(root.child("snapshot/permissions/\(uid)")) { [weak self] value in
            guard let self else { return }
            let access = try ServerMatchParser.parsePermissions(value)
            guard access.matchId == self.matchId else { return }
            guard access.member else { self.fail("Ya no pertenecés a esta partida."); return }
            self.inbox.accept(access)
            self.deliver()
            self.armPresence()
            self.refreshChat()
        }
        let info = Database.database().reference(withPath: ".info")
        let offset = info.child("serverTimeOffset")
        let offsetHandle = offset.observe(.value) { [weak self] snapshot in
            let value = (snapshot.value as? NSNumber)?.doubleValue ?? 0
            Task { @MainActor in
                guard let self, self.started, self.lifecycle == generation else { return }
                self.serverOffsetMs = value
            }
        }
        bindings.append((offset, offsetHandle))
        let connection = info.child("connected")
        let connectionHandle = connection.observe(.value) { [weak self] snapshot in
            let online = snapshot.value as? Bool == true
            Task { @MainActor in
                guard let self, self.started, self.lifecycle == generation else { return }
                self.connected = online
                if online { self.armPresence() } else { self.presenceArmed = false }
            }
        }
        bindings.append((connection, connectionHandle))
        tick = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(500))
                guard let self else { return }
                self.now = Date()
                if let receipt = self.receiptAt, self.now.timeIntervalSince(receipt) > 3 {
                    self.pending = nil
                    self.receiptAt = nil
                }
                self.recoverIfNeeded()
            }
        }
    }

    func stop() {
        lifecycle += 1
        accessRetryTask?.cancel()
        accessRetryTask = nil
        guard started else { return }
        started = false
        tick?.cancel()
        tick = nil
        pending = nil
        receiptAt = nil
        for (reference, handle) in bindings { reference.removeObserver(withHandle: handle) }
        bindings.removeAll()
        stopChat()
        if presenceArmed {
            root.child("presence/\(uid)").setValue(["estado": "desconectado", "actualizadaEn": ServerValue.timestamp()])
        }
        presenceArmed = false
    }

    private func listen(_ reference: DatabaseReference, receive: @escaping @MainActor (Any?) throws -> Void) {
        let generation = lifecycle
        let handle = reference.observe(.value, with: { snapshot in
            let exists = snapshot.exists()
            let value = snapshot.value
            Task { @MainActor [weak self] in
                guard let self, self.started, self.lifecycle == generation else { return }
                guard Auth.auth().currentUser?.uid == self.uid else { self.fail("La cuenta cambió."); return }
                guard exists else { self.synchronizing = true; return }
                do { try receive(value) } catch { self.fail(Self.message(for: error)) }
            }
        }, withCancel: { error in
            Task { @MainActor [weak self] in
                guard let self, self.started, self.lifecycle == generation else { return }
                // Right after the start the server may not have published this player's
                // permissions yet: the first reads are denied. Keep synchronizing and retry;
                // only a denial after having had access, or a long wait, means access is gone.
                if (error as NSError).code == 1, self.snapshot == nil, self.accessRetries < 20 {
                    self.accessRetries += 1
                    self.stop()
                    self.accessRetryTask = Task { [weak self] in
                        do { try await Task.sleep(for: .milliseconds(1500)) } catch { return }
                        guard let self, !Task.isCancelled, self.failure == nil else { return }
                        self.accessRetryTask = nil
                        self.start()
                    }
                    return
                }
                self.fail((error as NSError).code == 1 ? "Ya no tenés acceso a esta partida." : "Se perdió la conexión con la partida.")
            }
        })
        bindings.append((reference, handle))
    }

    private func deliver() {
        guard let next = inbox.snapshot else { synchronizing = true; return }
        synchronizing = false
        if next != snapshot {
            let update = tracker.accept(next.publicState)
            if !update.events.isEmpty || update.phaseChanged {
                freshEvents = update.events
                phaseChanged = update.phaseChanged
                if let cursor = tracker.cursor, let data = try? JSONEncoder().encode(cursor) {
                    UserDefaults.standard.set(data, forKey: Self.cursorKey)
                }
            }
            snapshot = next
            if let command = pending, receiptAt != nil || command.phaseIndex != next.publicState.phaseIndex
                || next.publicState.winner != nil {
                pending = nil
                receiptAt = nil
            }
            if !chatChannels.contains(chatChannel) { selectChannel(.town) }
        }
    }

    /// The view consumed `freshEvents`; they are never shown again on this device.
    func eventsPresented() {
        freshEvents = []
        phaseChanged = false
    }

    private func fail(_ message: String) {
        failure = message
        stop()
    }

    private func armPresence() {
        guard started, connected, !presenceArmed, inbox.permissions?.member == true else { return }
        presenceArmed = true
        let generation = lifecycle
        let own = root.child("presence/\(uid)")
        own.onDisconnectSetValue(["estado": "desconectado", "actualizadaEn": ServerValue.timestamp()]) { [weak self] error, _ in
            Task { @MainActor in
                guard let self, self.started, self.lifecycle == generation else { return }
                if error != nil { self.presenceArmed = false; return }
                own.setValue(["estado": "conectado", "actualizadaEn": ServerValue.timestamp()])
            }
        }
    }

    // MARK: Intentions

    /// Sends one intention; the same `requestId` is reused when a network failure is retried.
    func send(_ option: ServerMatchActionOption, target: String? = nil) {
        guard let state = snapshot?.publicState, options.contains(option),
              option.needsTarget ? option.targets.contains(target ?? "") : target == nil else { return }
        let command = ServerMatchCommand(roomId: roomId, state: state, action: option.action, targetUid: target, team: option.team)
        pending = command
        receiptAt = nil
        actionError = nil
        Task { await deliver(command, attempt: 1) }
    }

    private func deliver(_ command: ServerMatchCommand, attempt: Int) async {
        guard started, pending == command else { return }
        do {
            let response = try await ServerMatchCalls.call("accionPartidaV3", command.payload)
            guard pending == command else { return }
            let receipt = response as? [String: Any]
            if receipt?["accepted"] as? Bool != true || receipt?["matchId"] as? String != command.matchId {
                actionError = "El servidor no confirmó la acción."
            }
            // The confirmation shows up in the next projection; keep the controls locked until then.
            receiptAt = Date()
        } catch let error as OnlineError {
            guard pending == command else { return }
            if error == .offline, attempt < 3, snapshot?.publicState.phaseIndex == command.phaseIndex {
                try? await Task.sleep(for: .seconds(Double(1 << (attempt - 1))))
                await deliver(command, attempt: attempt + 1)
                return
            }
            pending = nil
            actionError = Self.actionMessage(error)
        } catch {
            pending = nil
            actionError = "No se pudo enviar la acción."
        }
    }

    private func recoverIfNeeded() {
        guard started, connected, !synchronizing, failure == nil, !recovering,
              let state = snapshot?.publicState, recovery.due(state, nowMs: nowMs) else { return }
        recovering = true
        let now = nowMs
        Task {
            defer { recovering = false }
            do {
                let response = try await ServerMatchCalls.call("recuperarFaseV3", ["roomId": roomId, "matchId": state.matchId,
                                                                                    "phaseIndex": state.phaseIndex])
                let retryAfter = ((response as? [String: Any])?["retryAfterMs"] as? NSNumber)?.int64Value ?? 0
                recovery.attempted(nowMs: now, retryAfterMs: retryAfter)
            } catch {
                recovery.attempted(nowMs: now)
            }
        }
    }

    /// Voluntary exit: the server eliminates this player (a recorded defeat) and revokes access.
    func abandon() async throws {
        _ = try await ServerMatchCalls.call("abandonarPartidaV3", ["roomId": roomId, "matchId": matchId])
        UserDefaults.standard.removeObject(forKey: Self.cursorKey)
        stop()
    }

    /// Only the creator is offered this action. The server archives the result and
    /// publishes clean membership before the view returns to the lobby.
    func prepareRematch() async throws {
        guard started, connected, !synchronizing, snapshot?.publicState.winner != nil else {
            throw OnlineError.roomChanged
        }
        _ = try await ServerMatchCalls.call("prepararRevanchaV3", ["roomId": roomId, "matchId": matchId])
        // Do not navigate from the receipt: nextMatchId comes from the RTDB publication.
    }

    // MARK: Chat

    /// No message downloads while the chat sheet is closed.
    func openChat() {
        chatVisible = true
        if !started { start() }
        refreshChat()
    }

    func closeChat() {
        chatVisible = false
        stopChat()
    }

    func selectChannel(_ channel: ServerChatChannel) {
        guard chatChannels.contains(channel) else { return }
        chatChannel = channel
        stopChat()
        refreshChat()
    }

    private func refreshChat() {
        guard started, chatVisible, chatBinding == nil, inbox.permissions?.member == true else { return }
        let channel = chatChannel
        let generation = chatGeneration
        let query = root.child("chat/\(channel.rawValue)").queryOrdered(byChild: "ts").queryLimited(toLast: 60)
        let handle = query.observe(.value) { [weak self] snapshot in
            let children = snapshot.children.allObjects.compactMap { $0 as? DataSnapshot }.map { child in
                (child.key, child.value as? [String: Any] ?? [:])
            }
            Task { @MainActor in
                guard let self, self.started, self.chatVisible, self.chatGeneration == generation,
                      self.chatChannel == channel else { return }
                self.chatMessages = children.compactMap { key, data -> ServerChatMessage? in
                    guard data["matchId"] as? String == self.matchId, let actor = data["actorUid"] as? String,
                          let text = data["text"] as? String, (1...300).contains(text.count),
                          let ts = (data["ts"] as? NSNumber)?.int64Value else { return nil }
                    return ServerChatMessage(id: key, uid: actor, text: text, timestampMs: ts)
                }.sorted { $0.timestampMs < $1.timestampMs }
            }
        } withCancel: { [weak self] _ in
            Task { @MainActor in
                guard let self, self.chatGeneration == generation else { return }
                self.chatMessages = []
            }
        }
        chatBinding = (query, handle)
    }

    private func stopChat() {
        chatGeneration += 1
        if let (query, handle) = chatBinding { query.removeObserver(withHandle: handle) }
        chatBinding = nil
        chatMessages = []
    }

    /// A bounded ring of 16 slots per player and channel, two seconds apart (RTDB rules).
    func sendChat(_ raw: String) async throws {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard started, connected, !synchronizing, (1...300).contains(text.count), let state = snapshot?.publicState,
              canSend(on: chatChannel) else {
            throw OnlineError.server("No podés escribir en este momento.")
        }
        let channel = chatChannel
        let rate = try await root.child("chatRate/\(uid)").getData()
        let previous = rate.value as? [String: Any]
        if let ts = (previous?["ts"] as? NSNumber)?.int64Value, nowMs - ts < 2000 {
            throw OnlineError.server("Esperá dos segundos entre mensajes.")
        }
        let slot = (((previous?["slot"] as? NSNumber)?.intValue ?? -1) + 1) % 16
        let id = "\(uid)_\(slot)"
        do {
            try await root.updateChildValues([
                "chat/\(channel.rawValue)/\(id)": ["actorUid": uid, "matchId": state.matchId, "phaseIndex": state.phaseIndex,
                                                   "slot": slot, "text": text, "ts": ServerValue.timestamp()],
                "chatRate/\(uid)": ["messageId": id, "channel": channel.rawValue, "slot": slot, "ts": ServerValue.timestamp()]
            ])
        } catch {
            throw OnlineError.server("No se pudo enviar el mensaje.")
        }
    }

    // MARK: Messages

    private static func message(for error: Error) -> String {
        switch error as? ServerMatchError {
        case .otherProtocol?: "La sala usa otro protocolo online."
        case .lostAuthority?: "La sala perdió la autoridad del servidor."
        case .unknownRole?: "El servidor envió un rol que esta versión no conoce. Actualizá la app."
        case .unknownPhase?: "El servidor envió una fase que esta versión no conoce. Actualizá la app."
        default: "El estado de la partida no es válido."
        }
    }

    private static func actionMessage(_ error: OnlineError) -> String {
        switch error {
        case .roomChanged: "La fase terminó antes de que llegara tu acción."
        case .offline: "Sin conexión. Tu acción no se envió."
        case .server(let message?): message
        default: "El servidor rechazó la acción."
        }
    }
}
