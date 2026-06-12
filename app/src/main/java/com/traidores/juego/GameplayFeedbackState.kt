package com.traidores.juego

internal class GameplayFeedbackState {
    var pending: GameplayFeedbackSpec? = null
        private set
    var privateVisible: Boolean = false
        private set

    fun restore(spec: GameplayFeedbackSpec?) {
        pending = spec?.takeIf { it.blocksGameplay }
        privateVisible = false
    }

    fun submit(spec: GameplayFeedbackSpec?): Presentation {
        if (spec == null) return Presentation.NONE
        return if (spec.blocksGameplay) {
            pending = spec
            Presentation.PRIVATE
        } else {
            Presentation.BANNER
        }
    }

    fun privateToPresent(): GameplayFeedbackSpec? {
        return pending?.takeIf { it.blocksGameplay && !privateVisible }
    }

    fun markPrivateVisible() {
        if (pending?.blocksGameplay == true) {
            privateVisible = true
        }
    }

    fun dismissPrivate() {
        pending = null
    }

    fun finishPrivateDismissal() {
        privateVisible = false
    }

    fun cancel(keepPending: Boolean) {
        privateVisible = false
        if (!keepPending) pending = null
    }

    fun blocksGameplay(): Boolean {
        return pending?.blocksGameplay == true || privateVisible
    }

    enum class Presentation {
        NONE,
        BANNER,
        PRIVATE
    }
}
