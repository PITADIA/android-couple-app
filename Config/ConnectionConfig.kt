package com.love2loveapp.config

/**
 * Fallback placement of ConnectionConfig to ensure it's compiled
 * Equivalent Kotlin version of ConnectionConfig.swift
 */
object ConnectionConfig {

    // --- Constantes temporelles ---
    const val PREPARING_MIN_DURATION = 1.5    // en secondes
    const val PREPARING_MAX_TIMEOUT = 8.0     // en secondes
    const val READINESS_CHECK_INTERVAL = 0.3  // en secondes
    const val UI_TRANSITION_DELAY = 0.1       // en secondes

    // --- Analytics events ---
    sealed class AnalyticsEvent {
        data class SuccessViewShown(val mode: String, val context: String) : AnalyticsEvent()
        data class SuccessViewContinue(val mode: String, val waitTime: Double) : AnalyticsEvent()
        data class ReadyTimeout(val duration: Double, val context: String) : AnalyticsEvent()
        data class ConnectStart(val source: String) : AnalyticsEvent()
        data class ConnectSuccess(val inheritedSub: Boolean, val context: String) : AnalyticsEvent()
        data class IntroShown(val screen: String) : AnalyticsEvent()
        data class IntroContinue(val screen: String) : AnalyticsEvent()

        val eventName: String
            get() = when (this) {
                is SuccessViewShown -> "success_view_shown"
                is SuccessViewContinue -> "success_view_continue"
                is ReadyTimeout -> "ready_timeout"
                is ConnectStart -> "connect_start"
                is ConnectSuccess -> "connect_success"
                is IntroShown -> "intro_shown"
                is IntroContinue -> "intro_continue"
            }

        val parameters: Map<String, Any>
            get() = when (this) {
                is SuccessViewShown -> mapOf("mode" to mode, "context" to context)
                is SuccessViewContinue -> mapOf("mode" to mode, "wait_time" to waitTime)
                is ReadyTimeout -> mapOf("duration" to duration, "context" to context)
                is ConnectStart -> mapOf("source" to source)
                is ConnectSuccess -> mapOf(
                    "inherited_subscription" to inheritedSub,
                    "context" to context
                )
                is IntroShown -> mapOf("screen" to screen)
                is IntroContinue -> mapOf("screen" to screen)
            }
    }

    // --- Utils ---
    fun introFlagsKey(coupleId: String): String {
        return "introFlags_$coupleId"
    }

    // --- Contextes possibles ---
    enum class ConnectionContext(val value: String) {
        ONBOARDING("onboarding"),
        MENU("menu"),
        PROFILE_PHOTO("profile_photo"),
        DAILY_QUESTION("daily_question"),
        DAILY_CHALLENGE("daily_challenge")
    }
}
