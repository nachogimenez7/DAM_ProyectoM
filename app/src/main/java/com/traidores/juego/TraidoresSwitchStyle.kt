package com.traidores.juego

import androidx.appcompat.widget.SwitchCompat

internal fun SwitchCompat.applyTraidoresSwitchStyle() {
    setShowText(false)
    splitTrack = false
    setThumbResource(R.drawable.switch_thumb_traidores)
    setTrackResource(R.drawable.switch_track_traidores)
}
