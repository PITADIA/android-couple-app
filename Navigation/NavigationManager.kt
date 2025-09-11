package com.love2loveapp.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NavigationManager - Source unique de vérité pour la navigation
 * 
 * Responsabilités:
 * - Émet des événements de navigation one-shot (replay=0)
 * - Évite les doubles navigations et les replays après recomposition
 * - Centralise toute la logique de navigation
 */
@Singleton
class NavigationManager @Inject constructor() {
    
    private val _events = MutableSharedFlow<NavCommand>(
        replay = 0, // Pas de replay pour éviter les navigations répétées
        extraBufferCapacity = 1 // Buffer pour éviter les pertes d'événements
    )
    
    /**
     * Flow des événements de navigation
     * Consommé uniquement par AppRoot/NavHost
     */
    val events: SharedFlow<NavCommand> = _events.asSharedFlow()
    
    /**
     * Émission d'une commande de navigation
     * @param cmd La commande à exécuter
     */
    suspend fun go(cmd: NavCommand) {
        _events.emit(cmd)
    }
    
    /**
     * Navigation vers une route avec options
     */
    suspend fun navigateTo(
        route: String, 
        popUpToRoot: Boolean = false
    ) {
        go(NavCommand.To(route, popUpToRoot))
    }
    
    /**
     * Retour en arrière
     */
    suspend fun goBack() {
        go(NavCommand.Back)
    }
    
    /**
     * Remplacement de la route actuelle
     */
    suspend fun replaceCurrent(route: String) {
        go(NavCommand.Replace(route))
    }
}
