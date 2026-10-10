package com.traidores.juego

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When a video between matches is due. Decided on 10/10/2026: one every 2 finished matches,
 * at least 4 minutes apart, never during a player's first 2 matches, never for ad-free accounts.
 */
internal object InterstitialAdPolicy {
    const val MATCHES_PER_AD = 2
    const val FREE_FIRST_MATCHES = 2
    const val MIN_GAP_MS = 4 * 60_000L

    data class State(val lifetimeMatches: Int = 0, val matchesSinceAd: Int = 0, val lastAdAtMs: Long = 0L, val adsShown: Int = 0)

    fun afterMatch(state: State): State =
        state.copy(lifetimeMatches = state.lifetimeMatches + 1, matchesSinceAd = state.matchesSinceAd + 1)

    fun isDue(state: State, nowMs: Long, adFree: Boolean, demo: Boolean = false): Boolean =
        if (demo) !adFree && state.matchesSinceAd >= 1 else
        !adFree &&
            state.lifetimeMatches > FREE_FIRST_MATCHES &&
            state.matchesSinceAd >= MATCHES_PER_AD &&
            nowMs - state.lastAdAtMs >= MIN_GAP_MS

    /** The "¿Cansado de los videos?" strip follows every 2nd video, with no daily limit. */
    const val ADS_PER_STRIP = 2
    fun stripDue(adsShown: Int): Boolean = adsShown > 0 && adsShown % ADS_PER_STRIP == 0

    fun afterAd(state: State, nowMs: Long): State =
        state.copy(matchesSinceAd = 0, lastAdAtMs = nowMs, adsShown = state.adsShown + 1)
}

/**
 * Shows the video only at safe points. The host-authority online hands the table to another
 * player when the host's screen stops, so an ad must never open inside a room: callers are the
 * vs IA exit, the main menu and the online menu, all outside any room.
 */
internal object InterstitialAds {
    private const val PREFS = "traidores_ads"
    private val started = AtomicBoolean(false)
    private var ad: InterstitialAd? = null
    private var loading = false
    private var showing = false

    private val enabled: Boolean get() = BuildConfig.ADS_ENABLED

    /** Consent first (Google's form only appears where the law requires it), then the SDK. */
    fun start(activity: Activity) {
        if (!enabled || !started.compareAndSet(false, true)) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) OnlineDebugLog.w("ads_consent_form_error ${error.errorCode}")
                if (consent.canRequestAds()) initialize(activity)
            }
        }, { error ->
            OnlineDebugLog.w("ads_consent_update_error ${error.errorCode}")
            if (consent.canRequestAds()) initialize(activity)
        })
        if (consent.canRequestAds()) initialize(activity) // Consent from a previous session.
    }

    private val initialized = AtomicBoolean(false)
    private fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext) { preload(context.applicationContext) }
    }

    private var leftRoom = false

    /** A player who leaves an online room sees a due video once, back in the online menu. */
    fun onLeftOnlineRoom() { leftRoom = true }

    fun maybeShowAfterLeavingRoom(activity: Activity) {
        if (!leftRoom) return
        leftRoom = false
        maybeShow(activity)
    }

    fun onMatchCompleted(context: Context) {
        if (!enabled) return
        save(context, InterstitialAdPolicy.afterMatch(load(context)))
        preload(context.applicationContext)
    }

    /** Shows the video if one is due and ready; [onDone] always runs, after the ad or at once. */
    fun maybeShow(activity: Activity, onDone: () -> Unit = {}) {
        val state = load(activity)
        val ready = ad
        if (!enabled || showing || ready == null ||
            !InterstitialAdPolicy.isDue(state, System.currentTimeMillis(), AdFreeEntitlement.isAdFree(activity), BuildConfig.ADS_DEMO)) {
            onDone(); return
        }
        showing = true
        ad = null
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                val after = InterstitialAdPolicy.afterAd(load(activity), System.currentTimeMillis())
                save(activity, after)
                if (InterstitialAdPolicy.stripDue(after.adsShown)) NoAdsStrip.markPending(activity)
            }
            override fun onAdDismissedFullScreenContent() { finish() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                OnlineDebugLog.w("ads_show_failed ${error.code}"); finish()
            }
            private fun finish() {
                showing = false
                preload(activity.applicationContext)
                onDone()
            }
        }
        ready.show(activity)
    }

    private fun preload(context: Context) {
        if (!enabled || !initialized.get() || ad != null || loading || AdFreeEntitlement.isAdFree(context)) return
        loading = true
        InterstitialAd.load(context, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) { loading = false; ad = loaded }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false; OnlineDebugLog.w("ads_load_failed ${error.code}")
                }
            })
    }

    private fun load(context: Context): InterstitialAdPolicy.State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return InterstitialAdPolicy.State(p.getInt("lifetime", 0), p.getInt("since", 0), p.getLong("last", 0L), p.getInt("shown", 0))
    }

    private fun save(context: Context, state: InterstitialAdPolicy.State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("lifetime", state.lifetimeMatches).putInt("since", state.matchesSinceAd)
            .putLong("last", state.lastAdAtMs).putInt("shown", state.adsShown).apply()
    }
}

/**
 * «Sin anuncios» and the support pack remove the videos, once the server confirmed the purchase.
 */
internal object AdFreeEntitlement {
    fun isAdFree(context: Context): Boolean = AccountEntitlements.has(context, PurchaseCatalog.NO_ADS)
}
