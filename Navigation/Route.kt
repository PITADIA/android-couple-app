package com.love2loveapp.navigation

/**
 * Routes typées de l'application
 * Utilisées pour calculer la navigation basée sur l'état
 */
sealed interface Route {
    /**
     * Écran de chargement initial
     */
    object Splash : Route
    
    /**
     * Processus d'onboarding
     */
    object Onboarding : Route
    
    /**
     * Écran principal de l'application
     */
    object Main : Route
    
    /**
     * Contenu quotidien avec paramètre jour
     */
    data class Daily(val day: Int) : Route
    
    /**
     * Écran de connexion partenaire
     */
    object PartnerConnection : Route
    
    /**
     * Paywall d'abonnement
     */
    object Paywall : Route
    
    /**
     * Paramètres de l'application
     */
    object Settings : Route
    
    /**
     * Journal du couple
     */
    object Journal : Route
}

/**
 * Extension pour convertir Route en string pour NavHost
 */
fun Route.toRouteString(): String = when (this) {
    Route.Splash -> "splash"
    Route.Onboarding -> "onboarding"
    Route.Main -> "main"
    is Route.Daily -> "daily/${day}"
    Route.PartnerConnection -> "partner_connection"
    Route.Paywall -> "paywall"
    Route.Settings -> "settings"
    Route.Journal -> "journal"
}
