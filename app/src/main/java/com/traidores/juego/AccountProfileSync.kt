package com.traidores.juego

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject

/** UID-bound pending edits. A successful callback means Firebase acknowledged the latest edit. */
internal object AccountProfileSync {
    private val waiting = mutableMapOf<String, MutableList<(Exception?) -> Unit>>()
    private fun uid() = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid.orEmpty()
    private fun prefs(context: Context) = context.getSharedPreferences("AccountProfileOutbox", Context.MODE_PRIVATE)

    fun forget(context: Context, owner: String) {
        prefs(context).edit().remove(owner).remove("migrated:$owner").remove("confirmed:$owner").apply()
    }

    fun hasPending(context: Context) = uid().let { it.isNotEmpty() && prefs(context).contains(it) }

    fun markChanged(context: Context) {
        val owner = uid()
        val number = PlayerPublicIdentity.currentPublicId(context)
        if (owner.isEmpty() || number.isEmpty()) return
        val fields = metadataFields(PlayerPublicIdentity.publicProfileFields(context, number))
        val snapshot = JSONObject(fields).toString()
        val saved = prefs(context)
        // Keep the newest edit while a write is in flight, including a revert. Once
        // acknowledged, lifecycle callbacks must not publish the same data again.
        if (!saved.contains(owner) && saved.getString("confirmed:$owner", null) == snapshot) return
        saved.edit().putString(owner, snapshot).apply()
    }

    fun sync(context: Context, complete: (Exception?) -> Unit = {}) {
        val owner = uid()
        if (owner.isEmpty()) { complete(null); return }
        waiting[owner]?.let { it.add(complete); return }
        waiting[owner] = mutableListOf(complete)
        val app = context.applicationContext
        val saved = prefs(app)
        val ref = FirebaseFirestore.getInstance().collection("perfiles_publicos").document(owner)
        fun finish(error: Exception?) { waiting.remove(owner)?.forEach { it(error) } }
        fun current() = uid() == owner
        fun fields(json: JSONObject): Map<String, Any> = json.keys().asSequence().associateWith { key ->
            val value = json.get(key)
            if (value is JSONArray) (0 until value.length()).map { value.get(it) } else value
        }
        fun send() {
            if (!current()) { finish(IllegalStateException("La cuenta cambió.")); return }
            val pending = saved.getString(owner, null)
            if (pending == null) { finish(null); return }
            val payload = runCatching { metadataFields(fields(JSONObject(pending))) }.getOrElse {
                finish(IllegalStateException("No pudimos leer los cambios del perfil.", it)); return
            }
            ref.set(payload + mapOf("uidTemporal" to owner, "actualizadaEn" to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnSuccessListener {
                    val edit = saved.edit().putString("confirmed:$owner", pending)
                        .putBoolean("migrated:$owner", true)
                    if (saved.getString(owner, null) == pending) edit.remove(owner)
                    edit.apply()
                    if (current() && saved.contains(owner)) send() else finish(null)
                }.addOnFailureListener { finish(it) }
        }
        if (saved.contains(owner) || saved.getBoolean("migrated:$owner", false)) { send(); return }
        // Upgrade from the old local-only save: adopt it only after the server confirms its #.
        // New devices and other accounts cannot replace an existing profile with defaults.
        ref.get(Source.SERVER).addOnSuccessListener { remote ->
            if (!current()) { finish(IllegalStateException("La cuenta cambió.")); return@addOnSuccessListener }
            val localNumber = PlayerPublicIdentity.currentPublicId(app)
            val local = app.getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)
            if (localNumber.isNotEmpty() && remote.getString("publicId") == localNumber && local.contains("profile_name"))
                markChanged(app)
            saved.edit().putBoolean("migrated:$owner", true).apply()
            send()
        }.addOnFailureListener { finish(it) }
    }

    fun restoreConfirmed(context: Context, data: Map<String, Any>) {
        val owner = uid()
        if (owner.isEmpty() || prefs(context).contains(owner)) return
        val number = (data["publicId"] as? String).orEmpty()
        if (!PlayerPublicIdentity.isValidPublicId(number)) return
        PlayerPublicIdentity.savePublicId(context, number)
        PlayerProfileStore.saveRecoveredProfile(context,
            name = (data["nombrePerfil"] as? String).orEmpty(), bio = (data["bioPerfil"] as? String).orEmpty(),
            avatarKey = (data["avatarPerfil"] as? String).orEmpty(), bannerKey = (data["bannerPerfil"] as? String).orEmpty(),
            favoriteRoleKey = (data["rolFavoritoPerfil"] as? String).orEmpty(),
            playGamesAvatarUri = data["fotoPlayGames"] as? String,
            emoteIds = (data["emotesPerfil"] as? List<*>)?.filterIsInstance<String>(), profilePhotoUrl = data["fotoPerfil"] as? String)
        prefs(context).edit().putString("confirmed:$owner",
            JSONObject(metadataFields(PlayerPublicIdentity.publicProfileFields(context, number))).toString()).apply()
    }
    private fun metadataFields(fields: Map<String, Any>) = fields.filterKeys {
        it != "fotoPerfil" && (!BuildConfig.PROFILE_STORAGE_ENABLED || it != "fotoPlayGames")
    }
}
