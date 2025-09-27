package com.love2loveapp.views.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 📱 Bottom Navigation - Menu de navigation principal
 * 
 * Navigation entre :
 * - Vue principale (Questions/MainScreen)
 * - Vue favoris (FavoritesScreen)
 * 
 * N'apparaît PAS dans l'onboarding, seulement in-app après connexion
 * Design moderne Material 3 avec couleurs Love2Love
 */

/**
 * 🔄 Destinations de navigation
 */
enum class NavigationDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MAIN("main", "Questions", Icons.Filled.Home, Icons.Outlined.Home),
    DAILY_QUESTIONS("daily_questions", "Question du Jour", Icons.Filled.Email, Icons.Outlined.Email),
    DAILY_CHALLENGES("daily_challenges", "Défi du Jour", Icons.Filled.Settings, Icons.Outlined.Settings),
    JOURNAL("journal", "Journal", Icons.Filled.Create, Icons.Outlined.Create),
    FAVORITES("favorites", "Favoris", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE("profile", "Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * 📱 Composable BottomNavigation principal
 */
@Composable
fun Love2LoveBottomNavigation(
    currentDestination: NavigationDestination,
    favoritesCount: Int = 0,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.White,
        contentColor = Color(0xFFFF4081), // Rose Love2Love
        tonalElevation = 8.dp
    ) {
        NavigationDestination.values().forEach { destination ->
            NavigationBarItem(
                icon = {
                    NavigationItemIcon(
                        destination = destination,
                        isSelected = currentDestination == destination,
                        badgeCount = if (destination == NavigationDestination.FAVORITES) favoritesCount else 0
                    )
                },
                selected = currentDestination == destination,
                onClick = { onDestinationSelected(destination) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF4081),
                    selectedTextColor = Color(0xFFFF4081),
                    unselectedIconColor = Color.Black.copy(alpha = 0.6f),
                    unselectedTextColor = Color.Black.copy(alpha = 0.6f),
                    indicatorColor = Color(0xFFFF4081).copy(alpha = 0.1f)
                )
            )
        }
    }
}

/**
 * 🎨 Icône d'élément de navigation avec badge
 */
@Composable
private fun NavigationItemIcon(
    destination: NavigationDestination,
    isSelected: Boolean,
    badgeCount: Int = 0
) {
    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = Color(0xFFFF4081),
                    contentColor = Color.White
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.title,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 🎯 Extension pour obtenir la destination actuelle depuis une route
 */
fun String.toNavigationDestination(): NavigationDestination? {
    return NavigationDestination.values().find { it.route == this }
}

/**
 * 📱 Wrapper pour les écrans avec bottom navigation
 * Évite la duplication de code et gère l'état centralisé
 */
@Composable
fun ScreenWithBottomNavigation(
    currentDestination: NavigationDestination,
    favoritesCount: Int = 0,
    onDestinationSelected: (NavigationDestination) -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Contenu principal
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
        
        // Bottom Navigation
        Love2LoveBottomNavigation(
            currentDestination = currentDestination,
            favoritesCount = favoritesCount,
            onDestinationSelected = onDestinationSelected
        )
    }
}
