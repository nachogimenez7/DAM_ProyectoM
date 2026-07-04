package com.traidores.juego

import android.content.Context

enum class GameplayEffect {
    SELECT,
    CONFIRM,
    ERROR,
    PANEL,
    REVEAL,
    CHAT,
    COUNTDOWN,
    EMOTE
}

object GameplayEffects {
    fun play(context: Context, effect: GameplayEffect) {
        GameplayAudioDirector.feedback(context, cueFor(effect))
    }

    internal fun cueFor(effect: GameplayEffect): GameplayFeedbackCue = when (effect) {
        GameplayEffect.SELECT -> GameplayFeedbackCue.SELECT
        GameplayEffect.CONFIRM -> GameplayFeedbackCue.CONFIRM
        GameplayEffect.ERROR -> GameplayFeedbackCue.ERROR
        GameplayEffect.PANEL -> GameplayFeedbackCue.PANEL
        GameplayEffect.REVEAL -> GameplayFeedbackCue.REVEAL
        GameplayEffect.CHAT -> GameplayFeedbackCue.CHAT
        GameplayEffect.COUNTDOWN -> GameplayFeedbackCue.COUNTDOWN
        GameplayEffect.EMOTE -> GameplayFeedbackCue.EMOTE
    }
}
