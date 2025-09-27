package com.love2loveapp.navigation

/**
 * Commandes de navigation one-shot
 * Utilisées pour éviter les doubles navigations et les replays
 */
sealed interface NavCommand {
    /**
     * Navigation vers une route spécifique
     * @param route La route de destination
     * @param popUpToRoot Si true, vide le backstack jusqu'à la racine
     */
    data class To(
        val route: String, 
        val popUpToRoot: Boolean = false
    ) : NavCommand
    
    /**
     * Retour en arrière dans le backstack
     */
    object Back : NavCommand
    
    /**
     * Navigation avec remplacement de la route actuelle
     * @param route La nouvelle route
     */
    data class Replace(val route: String) : NavCommand
}
