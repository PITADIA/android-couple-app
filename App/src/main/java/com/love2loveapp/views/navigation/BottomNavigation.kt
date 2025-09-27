package com.love2loveapp.views.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R

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
 * 🔄 Destinations de navigation avec icônes personnalisées
 */
enum class NavigationDestination(
    val route: String,
    val title: String,
    @DrawableRes val selectedIcon: Int,
    @DrawableRes val unselectedIcon: Int
) {
    MAIN("main", "Accueil", R.drawable.home, R.drawable.home),
    DAILY_QUESTIONS("daily_questions", "Question du Jour", R.drawable.star, R.drawable.star),
    DAILY_CHALLENGES("daily_challenges", "Défi du Jour", R.drawable.miss, R.drawable.miss),
    JOURNAL("journal", "Journal", R.drawable.map, R.drawable.map),
    FAVORITES("favorites", "Favoris", R.drawable.heart, R.drawable.heart),
    PROFILE("profile", "Profil", R.drawable.profile, R.drawable.profile)
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
        contentColor = Color.Transparent, // Supprime le ripple par défaut
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
                    selectedIconColor = Color.Black, // Icône sélectionnée reste en noir
                    selectedTextColor = Color(0xFFFD267A), // Texte reste en rose
                    unselectedIconColor = Color.Gray.copy(alpha = 0.8f), // Gris du rapport
                    unselectedTextColor = Color.Gray.copy(alpha = 0.8f),
                    indicatorColor = Color.Transparent // Supprime la bulle rose complètement
                ),
                interactionSource = remember { MutableInteractionSource() } // Supprime l'effet ripple
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
            painter = painterResource(if (isSelected) destination.selectedIcon else destination.unselectedIcon),
            contentDescription = destination.title,
            modifier = Modifier.size(if (isSelected) 32.dp else 28.dp) // Tailles selon le rapport
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
