package com.traidores.juego

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

data class OnlineRoomCreation(
    val roomReference: DocumentReference,
    val roomName: String,
    val roomCode: String,
    val playerName: String,
    val map: GameMap,
    val expectedPlayers: Int,
    val commitTask: Task<Void>
)

object OnlineRoomFirestore {
    const val ROOMS_COLLECTION = "partidas"
    const val PLAYERS_COLLECTION = "jugadores"
    const val STATE_WAITING = "esperando"
    const val STATE_IN_GAME = "en_juego"
    const val STATE_ABANDONED = "abandonada"
    const val STATE_FINISHED = "finalizada"

    const val FIELD_NAME = "nombre"
    const val FIELD_STATE = "estado"
    const val FIELD_TEST_MODE = "modoPrueba"
    const val FIELD_MAX_PLAYERS = "maxJugadores"
    const val FIELD_CURRENT_PLAYERS = "jugadoresActuales"
    const val FIELD_HOST_ID = "hostId"
    const val FIELD_HOST_NAME = "hostNombre"
    const val FIELD_EXPECTED_PLAYERS = "jugadoresEsperados"
    const val FIELD_ACTIVE_HOST_ID = "hostActivoId"
    const val FIELD_HOST_VERSION = "hostVersion"
    const val FIELD_INITIAL_MATCH_CREATED = "partidaInicialCreada"
    const val FIELD_ROOM_CODE = "codigoSala"
    const val FIELD_MAP_KEY = "mapa"
    const val FIELD_MAP_NAME = "mapaNombre"
    const val FIELD_IS_HOST = "esHost"
    const val FIELD_PLAYER_STATE = "estado"
    const val FIELD_PLAYER_ORDER = "orden"
    const val FIELD_ACTIVE_IN_MATCH = "activoEnPartida"
    const val FIELD_UPDATED_AT = "actualizadaEn"
    const val FIELD_CREATED_AT = "creadaEn"
    const val FIELD_JOINED_AT = "unidoEn"
    const val FIELD_LAST_SEEN_AT = "ultimaConexion"
    const val FIELD_LAST_SEEN_LOCAL = "ultimaConexionLocal"

    const val DEFAULT_EXPECTED_PLAYERS = LocalGameFactory.MIN_PLAYERS
    const val DEFAULT_MAX_PLAYERS = LocalGameFactory.MAX_PLAYERS
    const val ROOM_CODE_LENGTH = 6
    private const val DEFAULT_PLAYER_NAME = "Nacho"

    fun normalizedPlayerName(rawName: String): String {
        return rawName.trim()
            .take(18)
            .ifBlank { DEFAULT_PLAYER_NAME }
    }

    fun selectedMapFromKey(mapKey: String): GameMap {
        return LocalGameFactory.maps.firstOrNull { it.key == mapKey }
            ?: LocalGameFactory.maps.first()
    }

    fun createRoom(
        firestore: FirebaseFirestore,
        playerName: String,
        uidTemporal: String,
        publicId: String,
        profileFields: Map<String, Any> = emptyMap(),
        map: GameMap,
        origin: String,
        expectedPlayers: Int = DEFAULT_EXPECTED_PLAYERS,
        modePrueba: Boolean = true,
        roomCode: String = generateRoomCode()
    ): OnlineRoomCreation {
        val roomReference = firestore.collection(ROOMS_COLLECTION).document()
        val hostReference = roomReference.collection(PLAYERS_COLLECTION)
            .document(uidTemporal)
        val roomNumber = (System.currentTimeMillis() % 10000).toString().padStart(4, '0')
        val safePlayerName = normalizedPlayerName(playerName)
        val safeExpectedPlayers = expectedPlayers.coerceIn(
            LocalGameFactory.MIN_PLAYERS,
            LocalGameFactory.MAX_PLAYERS
        )
        val roomName = "Sala de $safePlayerName $roomNumber"
        val roomData = hashMapOf<String, Any>(
            FIELD_NAME to roomName,
            FIELD_ROOM_CODE to roomCode,
            FIELD_STATE to STATE_WAITING,
            FIELD_MAP_KEY to map.key,
            FIELD_MAP_NAME to map.name,
            FIELD_HOST_ID to uidTemporal,
            FIELD_HOST_NAME to safePlayerName,
            FIELD_ACTIVE_HOST_ID to uidTemporal,
            FIELD_HOST_VERSION to 0,
            FIELD_INITIAL_MATCH_CREATED to false,
            FIELD_EXPECTED_PLAYERS to safeExpectedPlayers,
            FIELD_MAX_PLAYERS to safeExpectedPlayers,
            FIELD_CURRENT_PLAYERS to 1,
            FIELD_TEST_MODE to modePrueba,
            "origen" to origin,
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        val hostData = hashMapOf<String, Any>(
            FIELD_NAME to safePlayerName,
            PlayerPublicIdentity.FIELD_PUBLIC_ID to publicId,
            PlayerPublicIdentity.FIELD_PROFILE_NAME to safePlayerName,
            PlayerPublicIdentity.FIELD_ROOM_NAME to RoomDisplayNames.withPublicId(safePlayerName, publicId),
            FIELD_IS_HOST to true,
            FIELD_PLAYER_STATE to "conectado",
            FIELD_PLAYER_ORDER to 0,
            FIELD_ACTIVE_IN_MATCH to true,
            "uidTemporal" to uidTemporal,
            FIELD_LAST_SEEN_LOCAL to System.currentTimeMillis(),
            FIELD_LAST_SEEN_AT to FieldValue.serverTimestamp(),
            FIELD_JOINED_AT to FieldValue.serverTimestamp()
        )
        hostData.putAll(profileFields)
        val task = firestore.batch()
            .set(roomReference, roomData)
            .set(hostReference, hostData)
            .commit()

        return OnlineRoomCreation(
            roomReference = roomReference,
            roomName = roomName,
            roomCode = roomCode,
            playerName = safePlayerName,
            map = map,
            expectedPlayers = safeExpectedPlayers,
            commitTask = task
        )
    }

    fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(ROOM_CODE_LENGTH) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }
}
