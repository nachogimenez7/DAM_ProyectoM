package com.traidores.juego

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native SDK test driver. Not present in Release and refuses any non-emulator build. */
class ProfileMediaSmokeActivity : Activity() {
    private lateinit var label: TextView
    private fun <T> await(task: Task<T>): T = Tasks.await(task, 25, TimeUnit.SECONDS)
    private fun waitForObject(reference: com.google.firebase.storage.StorageReference) {
        repeat(100) {
            try { await(reference.metadata); return }
            catch (error: java.util.concurrent.ExecutionException) {
                if ((error.cause as? StorageException)?.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
                Thread.sleep(50)
            }
        }
        error("Timed out waiting for uploaded JPEG")
    }
    private fun report(text: String) {
        File(cacheDir, "media_smoke_result.txt").writeText(text)
        android.util.Log.i("TRAIDORES_MEDIA_QA", text)
        runOnUiThread { label.text = text }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        label = TextView(this).apply { text = "ANDROID MEDIA RUNNING"; textSize = 20f }
        setContentView(label)
        if (!BuildConfig.USE_ONLINE_AUTHORITY_EMULATOR || !BuildConfig.PROFILE_STORAGE_ENABLED) {
            report("ANDROID MEDIA BLOCKED: requires Auth/Firestore/Storage emulators"); return
        }
        Thread {
            try { runCheck() } catch (error: Exception) { report("ANDROID MEDIA FAIL: ${error.stackTraceToString()}") }
        }.start()
    }
    private fun runCheck() {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        val email = requireNotNull(intent.getStringExtra("email"))
        val otherEmail = requireNotNull(intent.getStringExtra("other"))
        val password = requireNotNull(intent.getStringExtra("password"))
        val prefs = getSharedPreferences(ProfileActivity.PREFS_NAME, MODE_PRIVATE)
        fun signIn(value: String): String {
            val uid = requireNotNull(await(auth.signInWithEmailAndPassword(value, password)).user).uid
            val profile = await(db.document("perfiles_publicos/$uid").get(Source.SERVER))
            PlayerPublicIdentity.savePublicId(this, requireNotNull(profile.getString("publicId")))
            ProfilePhotoStorage.restorePublishedUrl(this, profile.getString("fotoPerfil").orEmpty())
            return uid
        }
        fun sync(): Exception? {
            val completed = CountDownLatch(1)
            var failure: Exception? = null
            runOnUiThread { ProfilePhotoStorage.sync(this) { failure = it; completed.countDown() } }
            check(completed.await(25, TimeUnit.SECONDS)) { "Timed out publishing native photo" }
            return failure
        }
        fun select(color: Int): String {
            val file = File(cacheDir, "media_input.jpg")
            val bitmap = Bitmap.createBitmap(700, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            android.media.ExifInterface(file.path).apply {
                setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, "1/1,1/1,1/1"); saveAttributes()
            }
            LocalProfilePhotoStore.importPending(this, Uri.fromFile(file)).getOrThrow()
            check(LocalProfilePhotoStore.commitPending(this))
            val saved = LocalProfilePhotoStore.savedFile(this)
            check(saved.length() <= 256 * 1024)
            check(android.media.ExifInterface(saved.path).getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE) == null)
            prefs.edit().putBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, true).apply()
            ProfilePhotoStorage.markChanged(this)
            return MessageDigest.getInstance("SHA-256").digest(saved.readBytes()).joinToString("") { "%02x".format(it) }
        }
        val owner = signIn(email)
        if (intent.getStringExtra("stage") == "resume") {
            val expected = getSharedPreferences("MediaSmokeState", MODE_PRIVATE).getString("revision", "")
            check(sync() == null)
            val profile = await(db.document("perfiles_publicos/$owner").get(Source.SERVER))
            check(Uri.parse(profile.getString("fotoPerfil")).getQueryParameter("v") == expected)
            check(await(db.document("cuentas/$owner").get(Source.SERVER)).getLong("partidas") == 1L)
            prefs.edit().putBoolean(ProfileActivity.PREF_LOCAL_PHOTO_ENABLED, false).apply()
            ProfilePhotoStorage.markChanged(this)
            check(sync() == null)
            check(await(storage.reference.child("profilePhotos/$owner").listAll()).items.isEmpty())
            check(await(db.document("perfiles_publicos/$owner").get(Source.SERVER)).getString("fotoPerfil").isNullOrEmpty())
            report("ANDROID MEDIA PASS"); return
        }
        check(await(db.document("cuentas/$owner").get(Source.SERVER)).getLong("victorias") == 1L)
        val first = select(Color.RED)
        check(sync() == null)
        val firstRef = storage.reference.child("profilePhotos/$owner/avatar_$first.jpg")
        val original = await(firstRef.getBytes(256 * 1024))
        check(await(firstRef.metadata).contentType == "image/jpeg")
        await(db.disableNetwork())
        val blue = select(Color.BLUE)
        val done = CountDownLatch(1)
        var failed: Exception? = null
        runOnUiThread { ProfilePhotoStorage.sync(this) { failed = it; done.countDown() } }
        waitForObject(storage.reference.child("profilePhotos/$owner/avatar_$blue.jpg"))
        val latest = select(Color.GREEN)
        await(db.enableNetwork())
        check(done.await(25, TimeUnit.SECONDS) && failed == null)
        check(Uri.parse(await(db.document("perfiles_publicos/$owner").get(Source.SERVER)).getString("fotoPerfil"))
            .getQueryParameter("v") == latest)
        check(await(firstRef.getBytes(256 * 1024)).contentEquals(original))
        val other = signIn(otherEmail)
        check(ProfilePhotoStorage.publishedUrl(this).isEmpty())
        check(await(firstRef.getBytes(256 * 1024)).contentEquals(original))
        try {
            await(firstRef.putBytes(original)); error("Other UID overwrote a photo")
        } catch (error: java.util.concurrent.ExecutionException) {
            check((error.cause as? StorageException)?.errorCode == StorageException.ERROR_NOT_AUTHORIZED)
        }
        try { await(db.document("cuentas/$owner").get(Source.SERVER)); error("Other UID read history") }
        catch (error: java.util.concurrent.ExecutionException) {
            check((error.cause as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        check(await(db.document("cuentas/$other").get(Source.SERVER)).getLong("victorias") == 0L)
        signIn(email)
        await(db.disableNetwork())
        val abandoned = select(Color.MAGENTA)
        val switched = CountDownLatch(1)
        runOnUiThread { ProfilePhotoStorage.sync(this) { switched.countDown() } }
        waitForObject(storage.reference.child("profilePhotos/$owner/avatar_$abandoned.jpg"))
        await(auth.signInWithEmailAndPassword(otherEmail, password))
        await(db.enableNetwork())
        check(switched.await(25, TimeUnit.SECONDS))
        check(await(db.document("perfiles_publicos/$other").get(Source.SERVER)).getString("fotoPerfil").isNullOrEmpty())
        signIn(email)
        check(sync() == null)
        val restart = select(Color.YELLOW) // Durable pending bytes; deliberately do not send.
        getSharedPreferences("MediaSmokeState", MODE_PRIVATE).edit().putString("revision", restart).apply()
        report("ANDROID MEDIA PREPARED")
    }
}
