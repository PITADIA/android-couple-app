package com.love2loveapp.models

/**
 * 🗺️ DailyContentRoute - Système de Navigation Questions du Jour
 * Équivalent iOS DailyContentRoute.swift
 * 
 * Gère la logique de route conditionnelle complexe :
 * 1. Vérification connexion partenaire (OBLIGATOIRE)
 * 2. Affichage intro si première fois
 * 3. Vérification freemium (3 jours gratuits)
 * 4. Route vers paywall après limite
 * 5. Interface chat principale
 */
abstract class DailyContentRoute {
    /**
     * 📖 Écran d'introduction
     * @param showConnect true si connexion partenaire requise
     */
    data class Intro(val showConnect: Boolean) : DailyContentRoute()

    /**
     * 💰 Écran paywall freemium
     * @param day jour actuel de la question pour contexte
     */
    data class Paywall(val day: Int) : DailyContentRoute()

    /**
     * 💬 Interface chat principale (question + réponses)
     */
    object Main : DailyContentRoute()

    /**
     * ❌ Écran d'erreur avec message
     */
    data class Error(val message: String) : DailyContentRoute()

    /**
     * ⏳ État de chargement
     */
    object Loading : DailyContentRoute()
}

/**
 * 🧮 Calculateur de Route - Logique Conditionnelle
 * Équivalent iOS DailyContentRouteCalculator.calculateRoute()
 * 
 * Implémente la logique métier complexe de navigation
 * basée sur l'état utilisateur et partenaire.
 */
object DailyContentRouteCalculator {

    /**
     * 🎯 Calcul de route principal
     * 
     * @param hasConnectedPartner L'utilisateur a-t-il un partenaire connecté ?
     * @param hasSeenIntro A-t-il vu l'intro Questions du Jour ?
     * @param shouldShowPaywall Doit-on afficher le paywall freemium ?
     * @param paywallDay Jour actuel pour contexte paywall
     * @param serviceHasError Y a-t-il une erreur service ?
     * @param serviceErrorMessage Message d'erreur si applicable
     * @param serviceIsLoading Service en cours de chargement ?
     * 
     * @return Route calculée selon la logique métier
     */
    fun calculateRoute(
        hasConnectedPartner: Boolean,
        hasSeenIntro: Boolean,
        shouldShowPaywall: Boolean,
        paywallDay: Int,
        serviceHasError: Boolean,
        serviceErrorMessage: String? = null,
        serviceIsLoading: Boolean = false
    ): DailyContentRoute {

        // 🔑 1. CONNEXION PARTENAIRE D'ABORD (OBLIGATOIRE)
        // Sans partenaire = pas d'accès aux Questions du Jour
        if (!hasConnectedPartner) {
            return DailyContentRoute.Intro(showConnect = true)
        }

        // 🔑 2. INTRO AVANT TOUT LOADING/CONTENU
        // Présentation fonctionnalité si première utilisation
        if (!hasSeenIntro) {
            return DailyContentRoute.Intro(showConnect = false)
        }

        // 🔑 3. ÉTATS TECHNIQUES (erreurs avant loading)
        if (serviceHasError) {
            val errorMessage = serviceErrorMessage ?: "Une erreur est survenue"
            return DailyContentRoute.Error(errorMessage)
        }

        // 🔑 4. LOADING STATE
        if (serviceIsLoading) {
            return DailyContentRoute.Loading
        }

        // 🔑 5. VÉRIFIER PAYWALL FREEMIUM (3 JOURS GRATUITS)
        if (shouldShowPaywall) {
            return DailyContentRoute.Paywall(day = paywallDay)
        }

        // 🔑 6. ÉTAT PAR DÉFAUT - INTERFACE CHAT PRINCIPALE
        return DailyContentRoute.Main
    }
}

/**
 * 📊 Configuration Settings Questions du Jour
 * Équivalent iOS DailyQuestionSettings
 */
data class DailyQuestionSettings(
    val coupleId: String,
    val startDate: com.google.firebase.Timestamp,    // Date de début du couple
    val currentDay: Int,                             // Jour actuel de question
    val timezone: String = "Europe/Paris",          // Fuseau horaire couple
    val isActive: Boolean = true,                    // Settings actifs
    val lastGeneratedDate: String? = null,          // Dernière génération "yyyy-MM-dd"
    val lastGeneratedDateTime: com.google.firebase.Timestamp? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null
) {
    
    companion object {
        /**
         * 🔥 Parsing depuis Firestore
         */
        fun fromFirestore(document: com.google.firebase.firestore.DocumentSnapshot): DailyQuestionSettings? {
            return try {
                val data = document.data ?: return null

                DailyQuestionSettings(
                    coupleId = document.id,
                    startDate = data["startDate"] as? com.google.firebase.Timestamp 
                        ?: com.google.firebase.Timestamp.now(),
                    currentDay = (data["currentDay"] as? Number)?.toInt() ?: 1,
                    timezone = data["timezone"] as? String ?: "Europe/Paris",
                    isActive = data["isActive"] as? Boolean ?: true,
                    lastGeneratedDate = data["lastGeneratedDate"] as? String,
                    lastGeneratedDateTime = data["lastGeneratedDateTime"] as? com.google.firebase.Timestamp,
                    createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
                    updatedAt = data["updatedAt"] as? com.google.firebase.Timestamp
                )
            } catch (e: Exception) {
                android.util.Log.e("DailyQuestionSettings", "❌ Erreur parsing: ${e.message}")
                null
            }
        }
    }

    /**
     * 📅 Calcul du jour attendu basé sur la date de début
     */
    fun calculateExpectedDay(): Int {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        
        val startOfDay = calendar.apply {
            time = startDate.toDate()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
        
        val startOfToday = calendar.apply {
            time = java.util.Date()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
        
        val daysSinceStart = ((startOfToday.time - startOfDay.time) / (24 * 60 * 60 * 1000)).toInt()
        return daysSinceStart + 1
    }

    /**
     * 🔥 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "startDate" to startDate,
            "currentDay" to currentDay,
            "timezone" to timezone,
            "isActive" to isActive,
            "lastGeneratedDate" to (lastGeneratedDate ?: ""),
            "lastGeneratedDateTime" to (lastGeneratedDateTime ?: com.google.firebase.Timestamp.now()),
            "createdAt" to (createdAt ?: com.google.firebase.Timestamp.now()),
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
    }
}
