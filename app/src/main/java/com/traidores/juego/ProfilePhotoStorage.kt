package com.traidores.juego

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.security.MessageDigest

/** Gallery publication. Disabled until the bucket and rules are ready; no moderation API. */
object ProfilePhotoStorage {
    enum class PublicationStatus { IDLE, UPLOADING, FAILED }
    fun publicationStatus(context: Context): PublicationStatus {
        if (!BuildConfig.PROFILE_STORAGE_ENABLED) return PublicationStatus.IDLE
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return PublicationStatus.IDLE
        val saved = prefs(context)
        if (saved.getString("profile_photo_status_owner", "") != uid) return PublicationStatus.IDLE
        val status = PublicationStatus.entries.firstOrNull { it.name == saved.getString("profile_photo_status", "") }
            ?: PublicationStatus.IDLE
        return if (status == PublicationStatus.UPLOADING && !syncing) PublicationStatus.FAILED else status
    }
    private const val OWNER = "profile_photo_owner"
    private const val URL = "profile_photo_url"
    private const val DIRTY = "profile_photo_dirty"
    private const val DIRTY_OWNER = "profile_photo_dirty_owner"
    private const val MAX_BYTES = 256 * 1024L
    private var syncing = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)

    fun publishedUrl(context: Context): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        val saved = prefs(context)
        return if (saved.getString(OWNER, "") == uid)
            PlayGamesProfileAvatar.normalize(saved.getString(URL, "").orEmpty()) else ""
    }

    fun ownsLocalPhoto(context: Context): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val saved = prefs(context)
        return saved.getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false) &&
            saved.getString(DIRTY_OWNER, "") == uid && LocalProfilePhotoStore.hasSavedPhoto(context)
    }

    fun restorePublishedUrl(context: Context, url: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (prefs(context).getBoolean(DIRTY, false) && prefs(context).getString(DIRTY_OWNER, "") == uid) return
        LocalProfilePhotoStore.deleteSavedPhoto(context)
        prefs(context).edit().putBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false).putString(OWNER, uid)
            .putString(URL, PlayGamesProfileAvatar.normalize(url)).putBoolean(DIRTY, false).apply()
    }

    fun markChanged(context: Context) {
        prefs(context).edit().putBoolean(DIRTY, true)
            .putString(DIRTY_OWNER, FirebaseAuth.getInstance().currentUser?.uid.orEmpty()).apply()
    }

    fun sync(context: Context, onComplete: (Exception?) -> Unit = {}) {
        if (!BuildConfig.PROFILE_STORAGE_ENABLED || syncing) return
        if (publicationStatus(context) == PublicationStatus.FAILED) markChanged(context)
        if (!prefs(context).getBoolean(DIRTY, false)) {
            val localSelected = prefs(context).getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
            if (localSelected && LocalProfilePhotoStore.hasSavedPhoto(context) && publishedUrl(context).isBlank())
                markChanged(context)
            else return
        }
        val app = context.applicationContext
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        val uid = user.uid
        val saved = prefs(app)
        if (saved.getString(DIRTY_OWNER, uid) != uid) return
        val usePhoto = saved.getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
        val file = LocalProfilePhotoStore.savedFile(app)
        if (usePhoto && (!file.isFile || file.length() > MAX_BYTES)) {
            saved.edit().putString("profile_photo_status_owner", uid)
                .putString("profile_photo_status", PublicationStatus.FAILED.name).apply()
            onComplete(IllegalStateException("La foto no está disponible o supera 256 KB."))
            return
        }
        // Snapshot the compressed bytes: another selection cannot mutate an in-flight upload.
        val bytes = if (usePhoto) runCatching { file.readBytes() }.getOrElse {
            saved.edit().putString("profile_photo_status_owner", uid)
                .putString("profile_photo_status", PublicationStatus.FAILED.name).apply()
            onComplete(it as? Exception ?: IllegalStateException(it)); return
        } else null
        val revision = bytes?.let { data ->
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        }.orEmpty()
        val storage = FirebaseStorage.getInstance()
        val previousReference = publishedUrl(app).takeIf { it.isNotBlank() }?.let { url ->
            runCatching { storage.getReferenceFromUrl(url) }.getOrNull()?.takeIf { ref ->
                ref.bucket == storage.reference.bucket && ref.path.startsWith("profilePhotos/$uid/")
            }
        }
        val reference = storage.reference.child("profilePhotos/$uid/avatar_$revision.jpg")
        syncing = true
        saved.edit().putBoolean(DIRTY, false).putString("profile_photo_status_owner", uid)
            .putString("profile_photo_status", PublicationStatus.UPLOADING.name).apply()
        fun finish(error: Exception?) {
            syncing = false
            saved.edit().putString("profile_photo_status_owner", uid)
                .putString("profile_photo_status", if (error == null) PublicationStatus.IDLE.name else PublicationStatus.FAILED.name).apply()
            if (error != null) {
                saved.edit().putBoolean(DIRTY, true).apply()
                OnlineDebugLog.e("profile_photo_publish_failure", error)
            }
            onComplete(error)
            if (error == null && saved.getBoolean(DIRTY, false)) sync(app)
        }
        fun publish(url: String) {
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                finish(IllegalStateException("La cuenta cambió durante la subida."))
                return
            }
            val publicId = PlayerPublicIdentity.currentPublicId(app)
            if (!PlayerPublicIdentity.isValidPublicId(publicId)) {
                finish(IllegalStateException("El perfil todavía no tiene número público."))
                return
            }
            val profile = PlayerProfileStore.loadHumanProfile(app).copy(profilePhotoUrl = url)
            val fields = PlayerPublicIdentity.publicProfileFields(profile, publicId) + mapOf(
                "uidTemporal" to uid,
                "actualizadaEn" to FieldValue.serverTimestamp()
            )
            FirebaseFirestore.getInstance().collection("perfiles_publicos").document(uid)
                .set(fields, SetOptions.merge())
                .addOnSuccessListener {
                    if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                        saved.edit().putString(OWNER, uid).putString(URL, url).apply()
                    }
                    if (previousReference != null && (url.isBlank() || previousReference.path != reference.path)) {
                        previousReference.delete().addOnFailureListener { error ->
                            OnlineDebugLog.e("profile_photo_cleanup_failure", error)
                        }
                    }
                    finish(null)
                }.addOnFailureListener { finish(it) }
        }
        if (bytes == null) {
            publish("")
        } else {
            reference.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg")
                .setCacheControl("private,max-age=3600").build())
                .continueWithTask { upload ->
                    if (!upload.isSuccessful) throw upload.exception ?: IllegalStateException("Subida fallida")
                    reference.downloadUrl
                }.addOnSuccessListener { uri: Uri ->
                    publish(uri.buildUpon().appendQueryParameter("v", revision).build().toString())
                }.addOnFailureListener { finish(it) }
        }
    }
}
