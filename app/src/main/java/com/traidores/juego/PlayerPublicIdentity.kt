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
    const val FIELD_PROFILE_BIO = "bioPerfil"
    const val FIELD_PROFILE_AVATAR = "avatarPerfil"
    const val FIELD_PROFILE_BANNER = "bannerPerfil"
    const val FIELD_PROFILE_FAVORITE_ROLE = "rolFavoritoPerfil"

    private const val PREFS_NAME = "TraidoresPrefs"
    private const val PREF_PUBLIC_ID = "profile_public_id"
    private const val PUBLIC_ID_COUNTER_COLLECTION = "meta"
    private const val PUBLIC_ID_COUNTER_DOCUMENT = "public_ids"
    private const val PUBLIC_PROFILES_COLLECTION = "perfiles_publicos"
    private const val FIELD_NEXT_ID = "nextId"
    private const val FIELD_UID_TEMPORAL = "uidTemporal"
    private const val FIELD_UPDATED_AT = "actualizadaEn"
    private const val MAX_PUBLIC_BIO_LENGTH = 40

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
            publishPublicProfile(context, firestore, publicId)
            onReady(publicId)
        }.addOnFailureListener { error ->
            val fallback = localFallbackPublicId(uidTemporal)
            savePublicId(context, fallback)
            onReady(fallback)
            onFailure(error)
        }
    }

    /**
     * Borra el numero guardado en este dispositivo. Se usa al entrar con una cuenta que ya
     * existia: el `#` del invitado pertenece al uid viejo y quedaria duplicado.
     */
    fun clearPublicId(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_PUBLIC_ID)
            .apply()
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

    fun publicProfileFields(
        context: Context,
        publicId: String,
        visibleName: String = profileName(context)
    ): Map<String, Any> {
        return publicProfileFields(
            profile = PlayerProfileStore.loadHumanProfile(context),
            publicId = publicId,
            visibleName = visibleName
        )
    }

    fun publicProfileFields(
        profile: PlayerProfile,
        publicId: String,
        visibleName: String = profile.name
    ): Map<String, Any> {
        val safeName = OnlineRoomFirestore.normalizedPlayerName(
            visibleName.ifBlank { profile.name }
        )
        val safePublicId = publicId.takeIf(::isValidPublicId).orEmpty()
        return mapOf(
            FIELD_PUBLIC_ID to safePublicId,
            FIELD_PROFILE_NAME to safeName,
            FIELD_ROOM_NAME to RoomDisplayNames.withPublicId(safeName, safePublicId),
            FIELD_PROFILE_BIO to profile.bio.take(MAX_PUBLIC_BIO_LENGTH),
            FIELD_PROFILE_AVATAR to ProfileRoleCatalog.find(profile.avatarKey).key,
            FIELD_PROFILE_BANNER to ProfileCustomizationCatalog.normalizeBannerKey(profile.bannerKey),
            FIELD_PROFILE_FAVORITE_ROLE to ProfileRoleCatalog.find(profile.favoriteRoleKey).key
        )
    }

    private fun publishPublicProfile(
        context: Context,
        firestore: FirebaseFirestore,
        publicId: String
    ) {
        if (!isValidPublicId(publicId)) return
        val uidTemporal = OnlineTempIdentity.getOrCreate(context)
        val profileFields = publicProfileFields(context, publicId)
        firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .document(uidTemporal)
            .set(
                profileFields + mapOf(
                    FIELD_UID_TEMPORAL to uidTemporal,
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
