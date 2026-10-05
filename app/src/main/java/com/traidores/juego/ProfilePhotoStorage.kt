package com.traidores.juego

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import org.json.JSONObject
import java.security.MessageDigest

/** Gallery publication with a durable queue per account. No moderation API in the beta. */
object ProfilePhotoStorage {
    enum class PublicationStatus { IDLE, UPLOADING, FAILED }
    private const val OWNER = "profile_photo_owner"
    private const val URL = "profile_photo_url"
    private const val DIRTY = "profile_photo_dirty"
    private const val DIRTY_OWNER = "profile_photo_dirty_owner"
    private const val MAX_BYTES = 256 * 1024L
    private val waiting = mutableMapOf<String, MutableList<(Exception?) -> Unit>>()
    private fun prefs(context: Context) = context.getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private fun outbox(context: Context) = context.getSharedPreferences("ProfilePhotoOutbox", Context.MODE_PRIVATE)
    private fun uid() = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid.orEmpty()

    fun publicationStatus(context: Context): PublicationStatus {
        if (!BuildConfig.PROFILE_STORAGE_ENABLED) return PublicationStatus.IDLE
        val owner = uid()
        if (owner.isEmpty()) return PublicationStatus.IDLE
        if (waiting.containsKey(owner)) return PublicationStatus.UPLOADING
        return if (outbox(context).contains(owner)) PublicationStatus.FAILED else PublicationStatus.IDLE
    }
    fun forget(context: Context, owner: String) { outbox(context).edit().remove(owner).apply() }
    fun publishedUrl(context: Context): String {
        val owner = uid()
        val saved = prefs(context)
        return if (owner.isNotEmpty() && saved.getString(OWNER, "") == owner)
            PlayGamesProfileAvatar.normalize(saved.getString(URL, "").orEmpty()) else ""
    }
    fun ownsLocalPhoto(context: Context): Boolean {
        val owner = uid()
        val saved = prefs(context)
        return owner.isNotEmpty() && saved.getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false) &&
            saved.getString(DIRTY_OWNER, "") == owner && LocalProfilePhotoStore.hasSavedPhoto(context)
    }
    fun restorePublishedUrl(context: Context, url: String) {
        val owner = uid()
        if (owner.isEmpty() || outbox(context).contains(owner)) return
        val saved = prefs(context)
        if (saved.getBoolean(DIRTY, false) && saved.getString(DIRTY_OWNER, "") == owner) return
        LocalProfilePhotoStore.deleteSavedPhoto(context)
        saved.edit().putBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false).putString(OWNER, owner)
            .putString(URL, PlayGamesProfileAvatar.normalize(url)).putBoolean(DIRTY, false).apply()
    }
    fun markChanged(context: Context) {
        val owner = uid()
        if (owner.isEmpty()) return
        val saved = prefs(context)
        saved.edit().putBoolean(DIRTY, true).putString(DIRTY_OWNER, owner).apply()
        val usePhoto = saved.getBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false)
        val bytes = if (usePhoto) runCatching { LocalProfilePhotoStore.savedFile(context).readBytes() }.getOrNull() else null
        if (usePhoto && (bytes == null || bytes.size > MAX_BYTES)) return
        val revision = bytes?.let { MessageDigest.getInstance("SHA-256").digest(it)
            .joinToString("") { byte -> "%02x".format(byte) } }.orEmpty()
        if (!outbox(context).contains(owner) &&
            ((!usePhoto && publishedUrl(context).isEmpty()) ||
                (usePhoto && android.net.Uri.parse(publishedUrl(context)).getQueryParameter("v") == revision))) {
            saved.edit().putBoolean(DIRTY, false).apply()
            return
        }
        val edit = JSONObject().put("revision", revision).put("remove", !usePhoto)
        if (bytes != null) edit.put("jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
        outbox(context).edit().putString(owner, edit.toString()).apply()
    }
    fun sync(context: Context, onComplete: (Exception?) -> Unit = {}) {
        if (!BuildConfig.PROFILE_STORAGE_ENABLED) { onComplete(null); return }
        val owner = uid()
        if (owner.isEmpty()) { onComplete(null); return }
        val app = context.applicationContext
        val saved = prefs(app)
        val queue = outbox(app)
        if (!queue.contains(owner) && saved.getString(DIRTY_OWNER, "") == owner &&
            (saved.getBoolean(DIRTY, false) || (ownsLocalPhoto(app) && publishedUrl(app).isBlank()))) markChanged(app)
        waiting[owner]?.let { it.add(onComplete); return }
        val pending = queue.getString(owner, null) ?: run { onComplete(null); return }
        val edit = runCatching { JSONObject(pending) }.getOrElse {
            onComplete(it as? Exception ?: IllegalStateException(it)); return
        }
        val remove = edit.optBoolean("remove")
        val revision = edit.optString("revision")
        val bytes = if (remove) null else runCatching { Base64.decode(edit.getString("jpeg"), Base64.NO_WRAP) }.getOrNull()
        if (!remove && (bytes == null || bytes.size > MAX_BYTES || !revision.matches(Regex("[a-f0-9]{64}")))) {
            onComplete(IllegalStateException("La foto no está disponible o supera 256 KB.")); return
        }
        waiting[owner] = mutableListOf(onComplete)
        saved.edit().putString("profile_photo_status_owner", owner).putString("profile_photo_status", "UPLOADING").apply()
        val storage = FirebaseStorage.getInstance()
        fun current() = uid() == owner
        fun finish(error: Exception?) {
            val callbacks = waiting.remove(owner).orEmpty()
            if (current()) saved.edit().putString("profile_photo_status_owner", owner)
                .putString("profile_photo_status", if (error == null) "IDLE" else "FAILED").apply()
            if (error != null) OnlineDebugLog.e("profile_photo_publish_failure", error)
            if (error == null && current() && queue.contains(owner)) {
                sync(app) { nextError -> callbacks.forEach { it(nextError) } }
            } else callbacks.forEach { it(error) }
        }
        fun acknowledged() {
            if (queue.getString(owner, null) == pending) {
                queue.edit().remove(owner).apply()
                if (current()) saved.edit().putBoolean(DIRTY, false).apply()
            }
            finish(null)
        }
        fun publish(url: String) {
            if (!current()) { finish(IllegalStateException("La cuenta cambió durante la subida.")); return }
            FirebaseFirestore.getInstance().collection("perfiles_publicos").document(owner)
                .update(mapOf("fotoPerfil" to url, "fotoPlayGames" to "", "actualizadaEn" to FieldValue.serverTimestamp()))
                .addOnSuccessListener publishAck@{
                    if (!current()) { finish(IllegalStateException("La cuenta cambió durante la subida.")); return@publishAck }
                    saved.edit().putString(OWNER, owner).putString(URL, url).apply()
                    if (!remove) { acknowledged(); return@publishAck }
                    // Replacement retains versions used by active match rosters. Removal erases them.
                    storage.reference.child("profilePhotos/$owner").listAll().addOnSuccessListener listedAck@{ listed ->
                        if (!current()) { finish(IllegalStateException("La cuenta cambió.")); return@listedAck }
                        com.google.android.gms.tasks.Tasks.whenAll(listed.items.map { it.delete() })
                            .addOnSuccessListener { acknowledged() }.addOnFailureListener { finish(it) }
                    }.addOnFailureListener { finish(it) }
                }.addOnFailureListener { finish(it) }
        }
        if (bytes == null) publish("") else {
            val reference = storage.reference.child("profilePhotos/$owner/avatar_$revision.jpg")
            reference.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg")
                .setCacheControl("private,max-age=3600").build())
                .continueWithTask { uploaded ->
                    if (!uploaded.isSuccessful) throw uploaded.exception ?: IllegalStateException("Subida fallida")
                    reference.downloadUrl
                }.addOnSuccessListener { uri -> publish(uri.buildUpon().appendQueryParameter("v", revision).build().toString()) }
                .addOnFailureListener { finish(it) }
        }
    }
}
