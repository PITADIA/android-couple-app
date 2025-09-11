package com.love2loveapp.model

import android.content.Context
import androidx.annotation.StringRes
import com.love2loveapp.R

/**
 * Machine d'états pour déterminer quelle vue afficher dans les flows Daily Question/Challenge
 */
sealed class DailyContentRoute {
    data class Intro(val showConnect: Boolean) : DailyContentRoute()
    data class Paywall(val day: Int) : DailyContentRoute()
    object Main : DailyContentRoute()
    data class Error(val message: String) : DailyContentRoute()
    object Loading : DailyContentRoute()

    // --- Computed properties équivalents ---

    val description: String
        get() = when (this) {
            is Intro -> "intro(showConnect: $showConnect)"
            is Paywall -> "paywall(day: $day)"
            is Main -> "main"
            is Error -> "error($message)"
            is Loading -> "loading"
        }

    val isIntro: Boolean get() = this is Intro
    val isPaywall: Boolean get() = this is Paywall
    val isMain: Boolean get() = this == Main
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this == Loading
}

/**
 * Helper pour calculer la route selon l'état de l'application
 */
object DailyContentRouteCalculator {

    fun calculateRoute(
        context: Context,
        contentType: ContentType,
        hasConnectedPartner: Boolean,
        hasSeenIntro: Boolean,
        shouldShowPaywall: Boolean,
        paywallDay: Int,
        serviceHasError: Boolean,
        serviceErrorMessage: String?,
        serviceIsLoading: Boolean
    ): DailyContentRoute {

        // 🚨 CORRECTION CRITIQUE: INTRO AVANT LOADING

        // 1. Connexion partenaire d'abord
        if (!hasConnectedPartner) {
            return DailyContentRoute.Intro(showConnect = true)
        }

        // 2. Intro avant tout loading/contenu
        if (!hasSeenIntro) {
            return DailyContentRoute.Intro(showConnect = false)
        }

        // 3. Puis états techniques (erreurs avant loading)
        if (serviceHasError) {
            val errorMessage = serviceErrorMessage ?: context.getString(R.string.error_generic)
            return DailyContentRoute.Error(errorMessage)
        }

        // ✅ CHANGEMENT CRITIQUE: .main au lieu de .loading
        if (serviceIsLoading) {
            return DailyContentRoute.Main
        }

        // 4. Vérifier paywall freemium
        if (shouldShowPaywall) {
            return DailyContentRoute.Paywall(paywallDay)
        }

        // 5. État par défaut - vue principale
        return DailyContentRoute.Main
    }

    enum class ContentType(@StringRes val labelRes: Int) {
        DAILY_QUESTION(R.string.daily_question_title),
        DAILY_CHALLENGE(R.string.daily_challenge_title);

        fun displayName(context: Context): String = context.getString(labelRes)
    }
}
