package com.traidores.juego

import android.app.Activity
import android.app.PendingIntent
import android.net.Uri
import com.google.android.gms.games.FriendsResolutionRequiredException
import com.google.android.gms.games.PlayGames

data class PlayGamesFriend(
    val playerId: String,
    val displayName: String,
    val iconUri: Uri?
)

sealed class PlayGamesFriendsResult {
    data class Loaded(val friends: List<PlayGamesFriend>) : PlayGamesFriendsResult()
    data class PermissionRequired(val resolution: PendingIntent) : PlayGamesFriendsResult()
    data class Failed(val error: Exception) : PlayGamesFriendsResult()
}

object PlayGamesFriends {
    fun load(
        activity: Activity,
        forceReload: Boolean = false,
        onResult: (PlayGamesFriendsResult) -> Unit
    ) {
        if (!PlayGamesIdentity.isReady(activity)) {
            onResult(
                PlayGamesFriendsResult.Failed(
                    IllegalStateException("Play Games no está vinculado.")
                )
            )
            return
        }

        PlayGames.getPlayersClient(activity)
            .loadFriends(PAGE_SIZE, forceReload)
            .addOnSuccessListener { annotated ->
                val buffer = annotated.get()
                if (buffer == null) {
                    onResult(PlayGamesFriendsResult.Loaded(emptyList()))
                    return@addOnSuccessListener
                }
                val friends = buildList {
                    for (index in 0 until buffer.count) {
                        val player = buffer[index]
                        add(
                            PlayGamesFriend(
                                playerId = player.playerId,
                                displayName = player.displayName,
                                iconUri = player.iconImageUri
                            )
                        )
                    }
                }
                buffer.release()
                onResult(PlayGamesFriendsResult.Loaded(friends))
            }
            .addOnFailureListener { error ->
                if (error is FriendsResolutionRequiredException) {
                    onResult(PlayGamesFriendsResult.PermissionRequired(error.resolution))
                } else {
                    onResult(PlayGamesFriendsResult.Failed(error))
                }
            }
    }

    private const val PAGE_SIZE = 25
}
