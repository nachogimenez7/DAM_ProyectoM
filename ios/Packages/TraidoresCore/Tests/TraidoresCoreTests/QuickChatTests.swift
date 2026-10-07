import Testing
@testable import TraidoresCore

struct QuickChatTests {
    private let ana = QuickChatPlayer(id: 1, name: "Ana")
    private let beto = QuickChatPlayer(id: 2, name: "Beto")
    private let anaMaria = QuickChatPlayer(id: 3, name: "Ana María")

    private func context(traitor: Bool = false, role: RoleKey = .villager,
                         messages: [(speaker: String, text: String)] = [],
                         jester: Bool = false) -> QuickChatContext {
        let titles: [RoleKey: String] = [.villager: "Aldeano", .detective: "Comisario", .medic: "Médico",
                                         .assassin: "Asesino", .jester: "Bufón"]
        return QuickChatContext(
            traitorChannel: traitor, humanName: "Vos",
            humanRole: QuickChatRole(key: role, title: titles[role] ?? role.rawValue),
            others: [ana, beto, anaMaria], planTargets: [beto, anaMaria],
            rolesInPlay: [.villager, .detective, .medic, .assassin, .villager].map {
                QuickChatRole(key: $0, title: titles[$0]!)
            },
            recentMessages: messages, jesterInPlay: jester)
    }

    // BotQuickReplies.generalReplies: «Sospecho de», «Soy», «Votemos a».
    @Test func generalChipsBeforeAnyoneTalks() {
        let chips = QuickChat.chips(context())
        #expect(chips.map(\.title) == ["Sospecho de…", "Soy…", "Votemos a…"])
        #expect(chips[0].action == .pickPlayer(.suspect))
        #expect(chips[1].action == .pickRole(alibi: false))
    }

    @Test func answersARoleQuestionWithOwnRoleADecoyAndARefusal() {
        let chips = QuickChat.chips(context(role: .medic, messages: [("Ana", "Vos, ¿qué rol sos?")]))
        #expect(chips.map(\.title) == ["Soy médico", "Soy aldeano", "No voy a decir mi rol todavía"])
        let villager = QuickChat.chips(context(messages: [("Ana", "¿Cuál es tu rol?")]))
        #expect(villager.map(\.title).prefix(2) == ["Soy aldeano", "Soy comisario"])
    }

    @Test func claimedDetectiveIsAskedForTheInvestigation() {
        let chips = QuickChat.chips(context(role: .detective, messages: [
            ("Vos", "Soy comisario"), ("Beto", "¿A quién investigaste anoche?"),
        ]))
        #expect(chips.first?.action == .pickPlayer(.investigation))
        #expect(chips.last?.title == "Mentí, no soy comisario")
    }

    @Test func mentionedPlayerRepliesPreferTheLongestName() {
        let chips = QuickChat.chips(context(messages: [("Vos", "Hola"), ("Beto", "Yo miraría a Ana María")]))
        #expect(chips.map(\.title) == ["Sospecho de Ana María", "Quiero escuchar a Ana María primero",
                                        "Yo no votaría a Ana María todavía"])
        let jester = QuickChat.chips(context(messages: [("Vos", "Hola"), ("Beto", "Ana miente")], jester: true))
        #expect(jester.last?.title == "Ojo, Ana puede ser el bufón")
    }

    @Test func playerMessagesBeforeTheHumanSpeaksKeepGeneralChips() {
        let chips = QuickChat.chips(context(messages: [("Beto", "Ana está rara")]))
        #expect(chips.first?.title == "Sospecho de…")
    }

    @Test func traitorChipsAnswerAnAllyPlan() {
        let plain = QuickChat.chips(context(traitor: true, role: .assassin))
        #expect(plain.map(\.action) == [.pickPlayer(.killProposal), .pickPlayer(.silence), .pickPlayer(.watchOut)])
        let plan = QuickChat.chips(context(traitor: true, role: .assassin, messages: [("Ana", "Esta noche vamos por Beto")]))
        #expect(plan.map(\.title) == ["Dale, matemos a Beto", "A Beto no, mejor otro", "Silenciemos a…"])
    }

    @Test func templatesBuildSpanishTextWithIntent() {
        #expect(QuickChatTemplate.investigation.message(target: "Ana", suspicious: true)
            == QuickChatMessage("Investigué a Ana y me dio sospechoso", .actionClaim))
        #expect(QuickChatTemplate.askRole.message(target: "Beto").text == "Beto, ¿qué rol sos?")
        #expect(QuickChat.claim(QuickChatRole(key: .detective, title: "Comisario"), alibi: true).text
            == "Mañana digo que soy comisario")
        #expect(QuickChatTemplate.silence.traitorPlan)
        #expect(!QuickChatTemplate.suspect.traitorPlan)
    }

    @Test func claimableRolesAreUniqueAndAlibisOnlyTown() {
        let all = QuickChat.claimableRoles(context(), alibi: false).map(\.key)
        #expect(all == [.villager, .detective, .medic, .assassin])
        #expect(QuickChat.claimableRoles(context(), alibi: true).map(\.key) == [.villager, .detective, .medic])
    }

    @Test func menusMatchAndroidCategories() {
        #expect(QuickChat.menu(context()).map(\.title) == ["Sospechar de…", "Defender a…", "Preguntar…",
            "Decir mi rol…", "Informar una acción…", "Estrategia de voto…"])
        #expect(QuickChat.menu(context(traitor: true)).map(\.title) == ["Matemos a…", "A ese no…",
            "Silenciemos a…", "Cuidado con…", "Cúbranme…", "Cerrado, quedamos así"])
    }
}
