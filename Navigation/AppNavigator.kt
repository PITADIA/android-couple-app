package com.love2loveapp.navigation

import com.love2loveapp.core.viewmodels.IntegratedAppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppNavigator - Traduit l'état global en commandes de navigation
 * 
 * Responsabilités:
 * - Observe l'état global (IntegratedAppState)
 * - Calcule la route appropriée basée sur l'état
 * - Émet des NavCommand via NavigationManager
 * - Évite les boucles de navigation
 */
@Singleton
class AppNavigator @Inject constructor(
    private val navigationManager: NavigationManager
) {
    
    private var isInitialized = false
    
    /**
     * Lie l'AppNavigator à l'état global
     * À appeler une seule fois au démarrage
     */
    fun bind(
        appState: IntegratedAppState, 
        scope: CoroutineScope
    ) {
        if (isInitialized) return
        isInitialized = true
        
        scope.launch {
            appState.currentRoute
                .distinctUntilChanged()
                .collect { route ->
                    handleRouteChange(route)
                }
        }
    }
    
    /**
     * Gère les changements de route calculés par l'état global
     */
    private suspend fun handleRouteChange(route: Route) {
        val navCommand = when (route) {
            Route.Splash -> {
                // Pas de navigation pour splash, on attend l'état suivant
                return
            }
            Route.Onboarding -> {
                NavCommand.To("onboarding", popUpToRoot = true)
            }
            Route.Main -> {
                NavCommand.To("main", popUpToRoot = true)
            }
            is Route.Daily -> {
                NavCommand.To("daily/${route.day}")
            }
            Route.PartnerConnection -> {
                NavCommand.To("partner_connection", popUpToRoot = true)
            }
            Route.Paywall -> {
                NavCommand.To("paywall")
            }
            Route.Settings -> {
                NavCommand.To("settings")
            }
            Route.Journal -> {
                NavCommand.To("journal")
            }
        }
        
        navigationManager.go(navCommand)
    }
}
