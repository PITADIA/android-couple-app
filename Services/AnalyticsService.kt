// AnalyticsService.kt
package com.yourapp.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Service d'analytics avancé pour la connexion partenaire (équivalent Swift).
 * Dépend de Firebase Analytics (lib: com.google.firebase:firebase-analytics-ktx).
 */
object AnalyticsService {

    private const val TAG = "AnalyticsService"

    // Récupère l'instance par défaut (FirebaseApp doit être initialisée dans ton Application)
    private val analytics: FirebaseAnalytics
        get() = Firebase.analytics

    /** Track un événement de connexion partenaire. */
    fun track(event: ConnectionConfig.AnalyticsEvent) {
        val eventName = event.eventName
        val params = event.parameters

        analytics.logEvent(eventName, params.toBundle())
        Log.d(TAG, "📊 Analytics: $eventName - $params")
    }

    /** Track spécifiquement pour les diagnostics de performance. */
    fun trackPerformanceDiagnostic(
        context: ConnectionConfig.ConnectionContext,
        duration: Double,
        isLoading: Boolean,
        isOptimizing: Boolean,
        hasCurrentQuestion: Boolean,
        networkConnected: Boolean
    ) {
        val diagnostics = mapOf(
            "context" to context.rawValue,
            "duration" to duration,
            "is_loading" to isLoading,
            "is_optimizing" to isOptimizing,
            "has_current_question" to hasCurrentQuestion,
            "network_connected" to networkConnected,
            "timestamp" to (System.currentTimeMillis() / 1000.0)
        )

        analytics.logEvent("performance_diagnostic", diagnostics.toBundle())
        Log.d(TAG, "🔍 Performance Diagnostic: $diagnostics")
    }

    /** Track les erreurs de connexion. */
    fun trackConnectionError(
        context: ConnectionConfig.ConnectionContext,
        error: Throwable,
        step: String
    ) {
        val params = mapOf(
            "context" to context.rawValue,
            "error_description" to (error.localizedMessage ?: error.message ?: error.toString()),
            "step" to step,
            "timestamp" to (System.currentTimeMillis() / 1000.0)
        )

        analytics.logEvent("connection_error", params.toBundle())
        Log.e(TAG, "❌ Connection Error: $params", error)
    }

    /** Track les timeouts avec contexte. */
    fun trackTimeout(
        context: ConnectionConfig.ConnectionContext,
        duration: Double,
        expectedDuration: Double
    ) {
        // Équivalent du track(.readyTimeout(...)) en Swift
        val readyTimeout = ConnectionConfig.AnalyticsEvent.ReadyTimeout(
            duration = duration,
            context = context.rawValue
        )
        track(readyTimeout)

        val timeoutParams = mapOf(
            "context" to context.rawValue,
            "actual_duration" to duration,
            "expected_duration" to expectedDuration,
            "timeout_ratio" to (if (expectedDuration != 0.0) duration / expectedDuration else 0.0)
        )
        analytics.logEvent("timeout_detailed", timeoutParams.toBundle())
    }
}

/* ========= Helpers ========= */

private fun Map<String, Any?>.toBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in this) {
        when (value) {
            null -> Unit
            is String -> bundle.putString(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is Float -> bundle.putDouble(key, value.toDouble())
            is Boolean -> bundle.putBoolean(key, value)
            is Enum<*> -> bundle.putString(key, value.name)
            else -> bundle.putString(key, value.toString())
        }
    }
    return bundle
}

/**
 * Si tu n’as pas encore porté ces types depuis Swift,
 * voici un squelette minimal pour compiler.
 * Supprime/ajuste cette section si tu as déjà tes propres définitions.
 */
object ConnectionConfig {

    enum class ConnectionContext(val rawValue: String) {
        Onboarding("onboarding"),
        Connect("connect"),
        Ready("ready"),
        Reconnect("reconnect")
    }

    sealed class AnalyticsEvent(open val eventName: String, open val parameters: Map<String, Any?>) {
        data class ReadyTimeout(val duration: Double, val context: String) : AnalyticsEvent(
            eventName = "ready_timeout",
            parameters = mapOf(
                "duration" to duration,
                "context" to context
            )
        )
        // Ajoute ici tes autres événements si besoin…
    }
}
