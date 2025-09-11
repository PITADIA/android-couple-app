package com.love2loveapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.love2loveapp.navigation.NavCommand
import com.love2loveapp.navigation.NavigationManager
import com.love2loveapp.navigation.AppNavigator
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.ui.screens.SplashScreen
import com.love2loveapp.ui.screens.OnboardingScreen
import com.love2loveapp.ui.screens.MainScreen
import com.love2loveapp.ui.screens.PartnerConnectionScreen
import com.love2loveapp.ui.screens.PaywallScreen
import com.love2loveapp.ui.screens.SettingsScreen
import com.love2loveapp.ui.screens.JournalScreen
import com.love2loveapp.ui.screens.DailyContentScreen

/**
 * AppRoot - Point d'entrée unique pour la navigation
 * 
 * Responsabilités:
 * - Consomme les événements NavigationManager (une seule fois)
 * - Exécute la navigation via NavHost
 * - Lie AppNavigator à IntegratedAppState
 * - Gère le backstack et les deep links
 */
@Composable
fun AppRoot(
    appState: IntegratedAppState,
    navigationManager: NavigationManager,
    appNavigator: AppNavigator,
    navController: NavHostController = rememberNavController()
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Lie AppNavigator à l'état global (une seule fois)
    LaunchedEffect(appState, appNavigator) {
        appNavigator.bind(appState, coroutineScope)
    }
    
    // Consomme les événements de navigation (une seule fois)
    LaunchedEffect(navigationManager) {
        navigationManager.events.collect { navCommand ->
            when (navCommand) {
                is NavCommand.To -> {
                    navController.navigate(navCommand.route) {
                        if (navCommand.popUpToRoot) {
                            popUpTo(0) { inclusive = true }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                is NavCommand.Back -> {
                    navController.popBackStack()
                }
                is NavCommand.Replace -> {
                    navController.navigate(navCommand.route) {
                        popUpTo(navController.currentDestination?.id ?: 0) { 
                            inclusive = true 
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    
    // NavHost - Seul exécuteur de navigation
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(appState = appState)
        }
        
        composable("onboarding") {
            OnboardingScreen(appState = appState)
        }
        
        composable("main") {
            MainScreen(appState = appState)
        }
        
        composable("partner_connection") {
            PartnerConnectionScreen(appState = appState)
        }
        
        composable("paywall") {
            PaywallScreen(appState = appState)
        }
        
        composable("settings") {
            SettingsScreen(appState = appState)
        }
        
        composable("journal") {
            JournalScreen(appState = appState)
        }
        
        composable("daily/{day}") { backStackEntry ->
            val day = backStackEntry.arguments?.getString("day")?.toIntOrNull() ?: 1
            DailyContentScreen(
                appState = appState,
                day = day
            )
        }
    }
}
