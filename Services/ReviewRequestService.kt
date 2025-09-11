package com.love2love.shared

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Android port of ReviewRequestService (Swift) → Kotlin.
 *
 * - Uses Google Play In‑App Review API (Play Core).
 * - Logs with Firebase Analytics.
 * - Stores state in SharedPreferences (instead of UserDefaults).
 * - Debounces within the current app session.
 *
 * IMPORTANT i18n note:
 * This class itself does not render UI strings. Wherever you need localized
 * challenge/question text (e.g. from a key like "daily_challenge_1"), use:
 *   val text = context.getString(R.string.daily_challenge_1)
 * and not any custom localization system.
 */
object ReviewRequestService {

    // --- SharedPreferences / Keys ---
    private const val PREFS_NAME = "review_request_prefs"
    private const val KEY_LAST_REVIEW_REQUEST = "lastReviewRequest"
    private const val KEY_MIGRATION_DATE = "daily_review_migration_date"
    private const val KEY_THRESHOLD_OVERRIDE = "review_threshold_override"

    // Legacy constants
    private const val VIEWED_QUESTIONS_THRESHOLD = 120
    private const val COOLDOWN_DAYS = 90

    // Default A/B (override-able via SharedPreferences)
    private const val DEFAULT_DAILY_THRESHOLD = 5

    // Session debounce
    @Volatile
    private var sessionHasRequestedReview: Boolean = false

    // Lazily cached app version (per process)
    @Volatile
    private var cachedVersionName: String? = null

    // --- Public API ---

    /**
     * Main entry point used after a successful Daily Question completion.
     *
     * @param completedDays The number of unique days the current user has answered.
     *                      Compute this with your DailyQuestionService analog (see helper below).
     */
    fun maybeRequestReviewAfterDailyCompletion(
        context: Context,
        activity: Activity,
        completedDays: Int,
        analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    ) {
        logI("ReviewRequestService", "Check after daily completion…")

        // Debounce (per session)
        if (sessionHasRequestedReview) {
            logI("ReviewRequestService", "Already requested in this session – skipping")
            return
        }

        val prefs = prefs(context)
        ensureMigrationDateInitialized(prefs)

        val threshold = prefs.getInt(KEY_THRESHOLD_OVERRIDE, DEFAULT_DAILY_THRESHOLD)
        logI("ReviewRequestService", "Days completed: $completedDays / $threshold")

        if (completedDays < threshold) {
            logAnalytics(
                analytics,
                "review_check",
                bundleOf(
                    "eligible" to false,
                    "reason" to "threshold_not_met",
                    "completed_days" to completedDays,
                    "context" to "daily"
                )
            )
            return
        }

        if (hasRequestedThisVersion(context, prefs)) {
            logAnalytics(
                analytics,
                "review_check",
                bundleOf(
                    "eligible" to false,
                    "reason" to "already_requested_version",
                    "completed_days" to completedDays,
                    "context" to "daily"
                )
            )
            return
        }

        if (!isOutOfCooldown(prefs)) {
            logAnalytics(
                analytics,
                "review_check",
                bundleOf(
                    "eligible" to false,
                    "reason" to "cooldown_active",
                    "completed_days" to completedDays,
                    "context" to "daily"
                )
            )
            return
        }

        if (!isActivityReady(activity)) {
            logAnalytics(
                analytics,
                "review_check",
                bundleOf(
                    "eligible" to false,
                    "reason" to "activity_not_ready",
                    "completed_days" to completedDays,
                    "context" to "daily"
                )
            )
            return
        }

        // All green → small human‑friendly delay then request
        sessionHasRequestedReview = true
        CoroutineScope(Dispatchers.Main).launch {
            delay(600)
            requestReviewWithActivity(context, activity, analytics, threshold, completedDays, "daily_completion")
        }
    }

    /**
     * Legacy fallback for users without partner connection, based on total viewed questions.
     */
    fun checkForReviewRequestLegacy(
        context: Context,
        activity: Activity,
        totalViewedQuestions: Int,
        analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    ) {
        logI("ReviewRequestService", "Legacy check… (viewed=$totalViewedQuestions)")

        // Same rule of thumb as Swift: if user has a partner, the new flow should be used.
        if (hasPartnerConnected()) {
            logI("ReviewRequestService", "User has partner – legacy flow ignored")
            return
        }

        if (totalViewedQuestions < VIEWED_QUESTIONS_THRESHOLD) return

        val prefs = prefs(context)
        ensureMigrationDateInitialized(prefs)

        if (hasRequestedThisVersion(context, prefs)) return
        if (!isOutOfCooldown(prefs)) return

        // Request immediately (on main)
        Handler(Looper.getMainLooper()).post {
            requestReviewWithActivity(
                context = context,
                activity = activity,
                analytics = analytics,
                threshold = VIEWED_QUESTIONS_THRESHOLD,
                completedDays = -1, // Not applicable in legacy
                contextName = "legacy"
            )
        }
    }

    /** Resets all counters for testing. */
    fun resetForTesting(context: Context) {
        prefs(context).edit().clear().apply()
        sessionHasRequestedReview = false
        logI("ReviewRequestService", "All counters reset for testing")
    }

    /** Returns a human‑readable debug status (useful for diagnostics). */
    fun getDebugStatus(
        context: Context,
        completedDays: Int,
        totalViewedQuestions: Int
    ): String {
        val p = prefs(context)
        val hasRequested = hasRequestedThisVersion(context, p)
        val outOfCooldown = isOutOfCooldown(p)
        val safeToRequest = isSafeToRequestReview(p)
        val threshold = p.getInt(KEY_THRESHOLD_OVERRIDE, DEFAULT_DAILY_THRESHOLD)

        return buildString {
            appendLine("🌟 ReviewRequestService Status (NEW):")
            appendLine("- Days completed: $completedDays/$threshold ${if (completedDays >= threshold) "✅" else "❌"}")
            appendLine("- Version not requested: ${!hasRequested} ${if (!hasRequested) "✅" else "❌"}")
            appendLine("- Cooldown respected: $outOfCooldown ${if (outOfCooldown) "✅" else "❌"}")
            appendLine("- Migration safe: $safeToRequest ${if (safeToRequest) "✅" else "❌"}")
            appendLine("- Session not requested: ${!sessionHasRequestedReview} ${if (!sessionHasRequestedReview) "✅" else "❌"}")
            appendLine()
            appendLine("📊 Legacy (comparison):")
            appendLine("- Questions viewed: $totalViewedQuestions/$VIEWED_QUESTIONS_THRESHOLD ${if (totalViewedQuestions >= VIEWED_QUESTIONS_THRESHOLD) "✅" else "❌"}")
        }
    }

    // --- Internals ---

    private fun requestReviewWithActivity(
        context: Context,
        activity: Activity,
        analytics: FirebaseAnalytics,
        threshold: Int,
        completedDays: Int,
        contextName: String
    ) {
        logI("ReviewRequestService", "🎉 Requesting in‑app review ($contextName)…")

        logAnalytics(
            analytics,
            "review_requested",
            bundleOf(
                "context" to contextName,
                "threshold" to threshold,
                "completed_days" to completedDays.takeIf { it >= 0 }
            )
        )

        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()

        request.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                logE("ReviewRequestService", "requestReviewFlow failed: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val reviewInfo = task.result
            val flow = manager.launchReviewFlow(activity, reviewInfo)
            flow.addOnCompleteListener { _ ->
                // Regardless of the result, consider the flow done per Google guidelines.
                markReviewRequestedThisVersion(context)
                markLastReviewDateNow(context)
                logI("ReviewRequestService", "✅ Review flow completed (version ${appVersion(context)})")
            }
        }
    }

    private fun isActivityReady(activity: Activity): Boolean {
        return !(activity.isFinishing || activity.isDestroyed)
    }

    private fun hasPartnerConnected(): Boolean {
        // Simple check – refine if you later store explicit partner link state.
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun hasRequestedThisVersion(context: Context, prefs: SharedPreferences): Boolean {
        val key = hasRequestedReviewKey(appVersion(context))
        return prefs.getBoolean(key, false)
    }

    private fun markReviewRequestedThisVersion(context: Context) {
        val p = prefs(context)
        val key = hasRequestedReviewKey(appVersion(context))
        p.edit().putBoolean(key, true).apply()
    }

    private fun markLastReviewDateNow(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        p.edit().putLong(KEY_LAST_REVIEW_REQUEST, now).apply()
    }

    private fun isOutOfCooldown(prefs: SharedPreferences): Boolean {
        val last = prefs.getLong(KEY_LAST_REVIEW_REQUEST, -1L)
        if (last <= 0L) return true
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last)
        return elapsedDays >= COOLDOWN_DAYS
    }

    private fun isSafeToRequestReview(prefs: SharedPreferences): Boolean {
        val migration = prefs.getLong(KEY_MIGRATION_DATE, -1L)
        if (migration <= 0L) return true
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - migration)
        return elapsedDays >= 4
    }

    private fun ensureMigrationDateInitialized(prefs: SharedPreferences) {
        if (!prefs.contains(KEY_MIGRATION_DATE)) {
            prefs.edit().putLong(KEY_MIGRATION_DATE, System.currentTimeMillis()).apply()
            logI("ReviewRequestService", "Migration date initialized: ${System.currentTimeMillis()}")
        }
    }

    private fun hasRequestedReviewKey(versionName: String): String = "hasRequestedReview_${versionName}"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun appVersion(context: Context): String {
        cachedVersionName?.let { return it }
        val v = try {
            val pm = context.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            @Suppress("DEPRECATION")
            pInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        cachedVersionName = v
        return v
    }

    // --- Analytics helpers ---

    private fun bundleOf(vararg pairs: Pair<String, Any?>): android.os.Bundle {
        val b = android.os.Bundle()
        for ((k, v) in pairs) {
            when (v) {
                null -> Unit
                is String -> b.putString(k, v)
                is Int -> b.putInt(k, v)
                is Long -> b.putLong(k, v)
                is Boolean -> b.putBoolean(k, v)
                is Double -> b.putDouble(k, v)
                is Float -> b.putFloat(k, v)
                else -> b.putString(k, v.toString())
            }
        }
        return b
    }

    private fun logAnalytics(analytics: FirebaseAnalytics, name: String, params: android.os.Bundle) {
        analytics.logEvent(name, params)
    }

    // --- Logging helpers ---

    private fun logI(tag: String, msg: String) = Log.i(tag, msg)
    private fun logE(tag: String, msg: String) = Log.e(tag, msg)
}

// =============================
// Optional helper for computing completed daily days
// =============================

/**
 * Provide your own implementation backed by Firestore/Room/etc.
 * The Swift code computed unique days where the *current* user posted a response.
 */
interface DailyProgressProvider {
    /** Return the count of DISTINCT scheduled dates where [currentUserId] has responded. */
    fun completedDailyQuestionDays(currentUserId: String): Int
}

// Example data models you may already have in your Android layer
// Adapt them or delete if you have real ones in place.

data class DailyQuestion(
    val scheduledDate: String, // e.g., "2025-09-10"
    val responses: List<DailyResponse>
)

data class DailyResponse(
    val userId: String,
    val text: String,
    val respondedAt: Long
)

// =============================
// Usage notes
// =============================
/*
// 1) After a successful daily completion:
val completed = myProvider.completedDailyQuestionDays(FirebaseAuth.getInstance().currentUser?.uid ?: return)
ReviewRequestService.maybeRequestReviewAfterDailyCompletion(context, activity, completed)

// 2) Legacy fallback for users without partner:
val totalViewed = /* compute from your CategoryProgressService analog */
ReviewRequestService.checkForReviewRequestLegacy(context, activity, totalViewed)

// 3) strings.xml usage example (i18n):
// <string name="daily_challenge_1">Faisons une liste de 3 choses…</string>
// val text = context.getString(R.string.daily_challenge_1)
*/
