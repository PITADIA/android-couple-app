package com.love2loveapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.love2loveapp.models.AppScreen
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.services.*
import com.love2loveapp.views.LaunchScreenView
import com.love2loveapp.views.QuestionListScreen
import com.love2loveapp.views.QuestionDetailScreen
import com.love2loveapp.views.onboarding.CompleteOnboardingScreen
import com.love2loveapp.navigation.NavigationManager
import com.love2loveapp.navigation.NavigationDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("Love2Love", "MainActivity: Démarrage de Love2Love")
        
        setContent {
            MaterialTheme {
                Love2LoveApp()
            }
        }

        Log.i("Love2Love", "MainActivity: Interface chargée avec succès")
    }
}

@Composable
fun Love2LoveApp() {
    // Accès à l'AppState global
    val appState = AppDelegate.appState
    val currentScreen by appState.currentScreen.collectAsState()
    val currentUser by appState.currentUser.collectAsState()
    
    // Gestionnaire de navigation avec état local
    val navigationManager = NavigationManager.instance
    var currentDestination by remember { mutableStateOf(navigationManager.currentDestination) }
    
    // Navigation automatique depuis Launch Screen
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.Launch) {
            delay(2500) // Afficher le launch screen 2.5 secondes
            
            // Vérifier si l'utilisateur doit faire l'onboarding
            if (currentUser == null) {
                appState.startOnboarding()
            } else {
                appState.navigateToScreen(AppScreen.Main)
            }
        }
    }
    
    // Affichage basé sur l'écran actuel
    when (currentScreen) {
        AppScreen.Launch -> {
            LaunchScreenView()
        }
        
        AppScreen.Onboarding -> {
            // Utiliser le nouveau système d'onboarding complet avec 17 étapes
            CompleteOnboardingScreen(
                onComplete = { userData ->
                    Log.i("Love2Love", "Onboarding complet terminé avec:")
                    Log.i("Love2Love", "- Données utilisateur: $userData")
                    appState.navigateToScreen(AppScreen.Main)
                }
            )
        }
        
        AppScreen.Main -> {
            // Navigation interne dans l'écran principal
            when (navigationManager.currentDestination) {
                is NavigationDestination.Main -> {
                    Love2LoveMainInterface(
                        onCategoryClick = { category ->
                            navigationManager.navigateToCategory(category)
                            currentDestination = navigationManager.currentDestination
                        }
                    )
                }
                is NavigationDestination.QuestionList -> {
                    navigationManager.selectedCategory?.let { category ->
                        QuestionListScreen(category = category)
                    } ?: Love2LoveMainInterface(
                        onCategoryClick = { category ->
                            navigationManager.navigateToCategory(category)
                            currentDestination = navigationManager.currentDestination
                        }
                    )
                }
                is NavigationDestination.QuestionDetail -> {
                    val destination = navigationManager.currentDestination as NavigationDestination.QuestionDetail
                    QuestionDetailScreen(questionId = destination.questionId)
                }
                else -> {
                    Love2LoveMainInterface(
                        onCategoryClick = { category ->
                            navigationManager.navigateToCategory(category)
                            currentDestination = navigationManager.currentDestination
                        }
                    )
                }
            }
        }
        
        AppScreen.Authentication -> {
            Love2LoveAuthenticationScreen()
        }
    }
}


@Composable
fun Love2LoveMainInterface(
    onCategoryClick: ((QuestionCategory) -> Unit)? = null
) {
    val appState = AppDelegate.appState
    val currentUser by appState.currentUser.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // En-tête avec nom utilisateur
        Text(
            text = "❤️ Love2Love",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFFFD267A),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        currentUser?.let { user ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bonjour ${user.name} !",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Section partenaire
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (currentUser?.partnerId != null) "👫" else "💌",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (currentUser?.partnerId != null) "Partenaire connecté" else "Invitez votre partenaire",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentUser?.partnerId != null) 
                        "Vous pouvez maintenant partager vos réponses !" 
                    else "Partagez cette aventure à deux",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        Log.i("Love2Love", "Action partenaire cliquée")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (currentUser?.partnerId != null) "Voir le profil" else "Inviter mon partenaire")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Catégories de questions
        Text(
            text = "Catégories",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Liste des catégories avec les vraies données
        QuestionCategory.categories.forEach { category ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { 
                        Log.i("Love2Love", "Catégorie sélectionnée: ${category.title}")
                        onCategoryClick?.invoke(category)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (category.isPremium) 
                        Color(0xFFFFF3E0) else Color.White
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (category.isPremium) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PREMIUM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier
                                        .background(
                                            Color(0xFFFF9800).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFD267A)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun Love2LoveAuthenticationScreen() {
    // Écran d'authentification simple
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔐",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Authentification",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connectez-vous pour continuer",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}