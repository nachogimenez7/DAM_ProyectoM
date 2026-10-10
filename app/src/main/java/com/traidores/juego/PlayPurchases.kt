package com.traidores.juego

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.security.MessageDigest

/** What the shop shows for one product: Google's localized price, never a price of our own. */
internal data class StoreProduct(val id: String, val title: String, val price: String)

internal sealed class PurchaseOutcome {
    data class Granted(val items: Set<String>) : PurchaseOutcome()
    object Pending : PurchaseOutcome()
    object Cancelled : PurchaseOutcome()
    data class Failed(val message: String) : PurchaseOutcome()
}

/**
 * Google Play Billing for permanent products. Every purchase and every restore goes to
 * `validarCompraV1`, which checks it with Google, records it for this account and acknowledges
 * it. The phone only shows the result; it never decides what the player owns.
 */
internal object PlayPurchases {
    private var client: BillingClient? = null
    private val details = mutableMapOf<String, ProductDetails>()
    private var onOutcome: ((PurchaseOutcome) -> Unit)? = null
    private lateinit var appContext: Context

    private val updates = PurchasesUpdatedListener { result, purchases ->
        val callback = onOutcome
        onOutcome = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { handle(it, callback) }
            BillingClient.BillingResponseCode.USER_CANCELED -> callback?.invoke(PurchaseOutcome.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore { callback?.invoke(it) }
            else -> callback?.invoke(PurchaseOutcome.Failed("La tienda no respondió. Probá de nuevo."))
        }
    }

    private fun connected(context: Context, ready: (BillingClient?) -> Unit) {
        appContext = context.applicationContext
        val existing = client
        if (existing != null && existing.isReady) { ready(existing); return }
        val created = existing ?: BillingClient.newBuilder(appContext)
            .setListener(updates)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build().also { client = it }
        created.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                ready(created.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Localized names and prices from Play Console. Empty when the store is unavailable. */
    fun loadProducts(context: Context, onLoaded: (List<StoreProduct>) -> Unit) {
        connected(context) { billing ->
            if (billing == null) { onLoaded(emptyList()); return@connected }
            val params = QueryProductDetailsParams.newBuilder().setProductList(PurchaseCatalog.productIds.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP).build()
            }).build()
            billing.queryProductDetailsAsync(params) { result, found ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) found.productDetailsList else emptyList()
                list.forEach { details[it.productId] = it }
                val products = list.map { StoreProduct(it.productId, it.name, it.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()) }
                appContext.mainExecutor.execute { onLoaded(products) }
            }
        }
    }

    /** Opens Google's purchase sheet. Requires a registered account: guests cannot buy. */
    fun buy(activity: Activity, productId: String, onResult: (PurchaseOutcome) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) { onResult(PurchaseOutcome.Failed("Vinculá tu cuenta para comprar.")); return }
        val product = details[productId] ?: run { onResult(PurchaseOutcome.Failed("Ese producto no está disponible.")); return }
        connected(activity) { billing ->
            if (billing == null) { onResult(PurchaseOutcome.Failed("La tienda no está disponible.")); return@connected }
            onOutcome = onResult
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product).build()))
                .setObfuscatedAccountId(accountHash(user.uid)) // Binds the purchase to this account.
                .build()
            val launch = billing.launchBillingFlow(activity, params)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                onOutcome = null
                onResult(PurchaseOutcome.Failed("No pudimos abrir la tienda."))
            }
        }
    }

    /** «Restaurar compras»: sends every owned purchase to the server again (idempotent). */
    fun restore(context: Context = appContext, onResult: (PurchaseOutcome) -> Unit) {
        connected(context) { billing ->
            if (billing == null) { onResult(PurchaseOutcome.Failed("La tienda no está disponible.")); return@connected }
            billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (result.responseCode != BillingClient.BillingResponseCode.OK || owned.isEmpty()) {
                    appContext.mainExecutor.execute {
                        onResult(PurchaseOutcome.Granted(AccountEntitlements.items(appContext)))
                    }
                    return@queryPurchasesAsync
                }
                var remaining = owned.size
                var last: PurchaseOutcome = PurchaseOutcome.Granted(emptySet())
                owned.forEach { purchase ->
                    handle(purchase) { outcome ->
                        if (outcome !is PurchaseOutcome.Failed || last !is PurchaseOutcome.Granted) last = outcome
                        if (--remaining == 0) onResult(last)
                    }
                }
            }
        }
    }

    private fun handle(purchase: Purchase, callback: ((PurchaseOutcome) -> Unit)?) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            appContext.mainExecutor.execute { callback?.invoke(PurchaseOutcome.Pending) }
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        FirebaseFunctions.getInstance("southamerica-west1").getHttpsCallable("validarCompraV1")
            .call(mapOf("productId" to productId, "purchaseToken" to purchase.purchaseToken))
            .addOnSuccessListener { response ->
                val items = ((response.getData() as? Map<*, *>)?.get("items") as? List<*>)
                    .orEmpty().filterIsInstance<String>().toSet()
                AccountEntitlements.applyConfirmed(appContext, uid, items)
                callback?.invoke(PurchaseOutcome.Granted(items))
            }
            .addOnFailureListener { error ->
                OnlineDebugLog.e("purchase_validation_failed product=$productId", error)
                // The server explains refusals in Spanish (pending, other account, refunded).
                val serverMessage = (error as? FirebaseFunctionsException)?.message?.takeIf {
                    error.code != FirebaseFunctionsException.Code.INTERNAL && error.code != FirebaseFunctionsException.Code.UNAVAILABLE
                }
                callback?.invoke(PurchaseOutcome.Failed(serverMessage
                    ?: "No pudimos confirmar la compra. Tocá «Restaurar compras» en un rato."))
            }
    }

    /** Same value as accountHash() in purchaseService.js. */
    fun accountHash(uid: String): String =
        MessageDigest.getInstance("SHA-256").digest("traidores:$uid".toByteArray()).joinToString("") { "%02x".format(it) }
}
