package com.traidores.juego

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Play products and what each one grants. Kept in sync with functions/src/purchaseService.js. */
internal object PurchaseCatalog {
    const val NO_ADS = "sin_anuncios"
    const val PACK = "pack_sello_pueblo"
    const val STYLE_SPACE = "estilo_espacial"
    const val STYLE_SEA = "estilo_abismo_real"
    const val STYLE_FIRE = "estilo_forja_infernal"
    const val STYLES_THREE = "estilos_tres"
    const val EMOTES_MEMES = "emotes_memes"
    val productIds = listOf(NO_ADS, PACK, STYLE_SPACE, STYLE_SEA, STYLE_FIRE, STYLES_THREE, EMOTES_MEMES)
}

/**
 * What the signed-in account owns, read from `derechos/{uid}`. Only the server writes that
 * document, after Google confirms a purchase; nothing on the phone can grant an item. The last
 * confirmed list is cached per account so an offline phone keeps what it already owned.
 */
internal object AccountEntitlements {
    private const val PREFS = "traidores_entitlements"
    private var listener: ListenerRegistration? = null
    private var listenedUid = ""
    private val observers = mutableSetOf<() -> Unit>()

    fun initialize(context: Context) {
        val app = context.applicationContext
        FirebaseAuth.getInstance().addAuthStateListener { auth -> follow(app, auth.currentUser?.takeUnless { it.isAnonymous }?.uid.orEmpty()) }
    }

    fun has(context: Context, item: String): Boolean = items(context).contains(item)

    fun items(context: Context): Set<String> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptySet()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(key(uid), emptySet()).orEmpty()
    }

    /** Called on the main thread whenever the confirmed list changes. */
    fun observe(observer: () -> Unit): () -> Unit {
        observers += observer
        return { observers -= observer }
    }

    /** The validation call answers with the new list; show it at once instead of waiting. */
    fun applyConfirmed(context: Context, uid: String, confirmed: Collection<String>) {
        if (uid.isBlank() || uid != FirebaseAuth.getInstance().currentUser?.uid) return
        save(context, uid, confirmed.toSet())
    }

    private fun follow(context: Context, uid: String) {
        if (uid == listenedUid) return
        listener?.remove(); listener = null
        listenedUid = uid
        if (uid.isBlank()) { notifyObservers(); return }
        listener = FirebaseFirestore.getInstance().document("derechos/$uid").addSnapshotListener { snapshot, error ->
            if (error != null) { OnlineDebugLog.w("entitlements_listen_failed ${error.code}"); return@addSnapshotListener }
            if (snapshot == null || snapshot.metadata.isFromCache || uid != listenedUid) return@addSnapshotListener
            val confirmed = (snapshot.get("items") as? List<*>).orEmpty().filterIsInstance<String>().toSet()
            save(context, uid, confirmed)
        }
    }

    private fun save(context: Context, uid: String, confirmed: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(key(uid), confirmed).apply()
        notifyObservers()
    }

    private fun notifyObservers() = observers.toList().forEach { it() }

    private fun key(uid: String) = "items_$uid"
}
