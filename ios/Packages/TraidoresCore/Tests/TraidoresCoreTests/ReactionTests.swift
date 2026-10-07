import Testing
@testable import TraidoresCore

struct ReactionTests {
    // GameplayReactionLimiter: two per round, ten seconds apart, reset each round.
    @Test func limiterAllowsTwoPerRoundWithCooldown() {
        var limiter = ReactionLimiter()
        #expect(limiter.record(player: 0, round: 1, now: 0) == .none)
        #expect(limiter.record(player: 0, round: 1, now: 3) == .cooldown(seconds: 7))
        #expect(limiter.record(player: 1, round: 1, now: 3) == .none)
        #expect(limiter.record(player: 0, round: 1, now: 10) == .none)
        #expect(limiter.check(player: 0, round: 1, now: 30) == .roundLimit)
        #expect(limiter.record(player: 0, round: 2, now: 30) == .none)
    }

    @Test func publicPhasesAreTheDayOnes() {
        #expect(ReactionRules.isPublicPhase(.discussion))
        #expect(ReactionRules.isPublicPhase(.counterpoint))
        #expect(ReactionRules.isPublicPhase(.tieVote))
        #expect(!ReactionRules.isPublicPhase(.assassinNight))
        #expect(!ReactionRules.isPublicPhase(.assignment))
    }

    @Test func botsUseTheirThemeAndThePhaseMood() {
        #expect(ReactionRules.botEmoteIDs(map: .medieval, role: .assassin).first == "medieval_contento")
        #expect(ReactionRules.botEmoteIDs(map: .pampa, role: .detective).first == "gaucho_contento")
        #expect(ReactionRules.botEmoteIDs(map: .greece, role: .assassin).first == "griego_contento")
        for seed in 0..<20 {
            let id = ReactionRules.botEmoteID(map: .greece, role: .villager, phase: .voting, seed: seed)
            #expect(id == "griego_sospechoso" || id == "griego_enojado")
        }
        #expect(ReactionRules.botEmoteID(map: .pampa, role: .payador, phase: .discussion, seed: 0) == "griego_contento")
    }

    @Test func soundsMatchAndroidEmotionKeys() {
        #expect(ReactionRules.soundKey(forEmoteID: "gaucho_triste") == "sad")
        #expect(ReactionRules.soundKey(forEmoteID: "medieval_enojado") == "angry")
        #expect(ReactionRules.soundKey(forEmoteID: "premium_mate") == "premium_mate")
    }
}
