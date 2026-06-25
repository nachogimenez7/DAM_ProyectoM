package com.traidores.juego

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.abs

object PlayerPublicIdentity {
    const val FIELD_PUBLIC_ID = "publicId"
    const val FIELD_PROFILE_NAME = "nombrePerfil"
    const val FIELD_ROOM_NAME = "nombreSala"

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_PUBLIC_ID = "profile_public_id"
    private const val PUBLIC_ID_COUNTER_COLLECTION = "meta"
    private const val PUBLIC_ID_COUNTER_DOCUMENT = "public_ids"
    private const val PUBLIC_PROFILES_COLLECTION = "perfiles_publicos"
    private const val FIELD_NEXT_ID = "nextId"
    private const val FIELD_UID_TEMPORAL = "uidTemporal"
    private const val FIELD_UPDATED_AT = "actualizadaEn"

    fun currentPublicId(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_PUBLIC_ID, "")
            .orEmpty()
        return stored.takeIf(::isValidPublicId).orEmpty()
    }

    fun displayPublicId(context: Context): String {
        return currentPublicId(context).takeIf { it.isNotBlank() }?.let { "#$it" }
            ?: "#SIN ID"
    }

    fun profileName(context: Context): String {
        return OnlineRoomFirestore.normalizedPlayerName(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(OpcionesActivity.PREF_PLAYER_NAME, "")
                .orEmpty()
        )
    }

    fun ensurePublicId(
        context: Context,
        firestore: FirebaseFirestore,
        onReady: (String) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val existing = currentPublicId(context)
        if (existing.isNotBlank()) {
            publishPublicProfile(context, firestore, existing)
            onReady(existing)
            return
        }

        val uidTemporal = OnlineTempIdentity.getOrCreate(context)
        val counterReference = firestore.collection(PUBLIC_ID_COUNTER_COLLECTION)
            .document(PUBLIC_ID_COUNTER_DOCUMENT)
        val profileReference = firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .document(uidTemporal)
        firestore.runTransaction { transaction ->
            val profile = transaction.get(profileReference)
            val alreadyAssigned = profile.getString(FIELD_PUBLIC_ID)
                ?.takeIf(::isValidPublicId)
            if (alreadyAssigned != null) {
                return@runTransaction alreadyAssigned
            }

            val counter = transaction.get(counterReference)
            val nextId = (counter.getLong(FIELD_NEXT_ID) ?: 1L).coerceAtLeast(1L)
            val publicId = nextId.toString()
            transaction.set(
                counterReference,
                mapOf(
                    FIELD_NEXT_ID to nextId + 1L,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                profileReference,
                mapOf(
                    FIELD_UID_TEMPORAL to uidTemporal,
                    FIELD_PUBLIC_ID to publicId,
                    FIELD_PROFILE_NAME to profileName(context),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            publicId
        }.addOnSuccessListener { publicId ->
            savePublicId(context, publicId)
            onReady(publicId)
        }.addOnFailureListener { error ->
            val fallback = localFallbackPublicId(uidTemporal)
            onReady(fallback)
            onFailure(error)
        }
    }

    fun savePublicId(context: Context, publicId: String) {
        if (!isValidPublicId(publicId)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PUBLIC_ID, publicId)
            .apply()
    }

    fun isValidPublicId(publicId: String): Boolean {
        return publicId.matches(Regex("^[0-9]{1,12}$")) && publicId.toLongOrNull() != null
    }

    private fun publishPublicProfile(
        context: Context,
        firestore: FirebaseFirestore,
        publicId: String
    ) {
        if (!isValidPublicId(publicId)) return
        val uidTemporal = OnlineTempIdentity.getOrCreate(context)
        firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .document(uidTemporal)
            .set(
                mapOf(
                    FIELD_UID_TEMPORAL to uidTemporal,
                    FIELD_PUBLIC_ID to publicId,
                    FIELD_PROFILE_NAME to profileName(context),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
    }

    private fun localFallbackPublicId(uidTemporal: String): String {
        val hash = abs(uidTemporal.hashCode().toLong())
        return (900_000L + (hash % 100_000L)).toString()
    }
}
