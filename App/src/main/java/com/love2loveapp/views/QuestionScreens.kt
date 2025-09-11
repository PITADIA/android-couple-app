package com.love2loveapp.views

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.navigation.NavigationManager
import com.love2loveapp.services.FavoritesService

/**
 * Écran de liste des questions pour une catégorie
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    category: QuestionCategory,
    navigationManager: NavigationManager = NavigationManager.instance,
    favoritesService: FavoritesService = FavoritesService()
) {
    val favorites by favoritesService.favorites.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
    ) {
        // TopAppBar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "${category.emoji} ${category.title}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { navigationManager.goBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )
        
        // Contenu
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Générer des questions d'exemple pour la catégorie
            val sampleQuestions = generateSampleQuestions(category)
            
            items(sampleQuestions) { question ->
                QuestionCard(
                    question = question,
                    isFavorite = favorites.contains(question.id),
                    onQuestionClick = { 
                        navigationManager.navigateToQuestion(question.id)
                    },
                    onFavoriteClick = { 
                        if (favorites.contains(question.id)) {
                            favoritesService.removeFavorite(question.id)
                        } else {
                            favoritesService.addFavorite(question.id)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Carte d'une question individuelle
 */
@Composable
fun QuestionCard(
    question: Question,
    isFavorite: Boolean,
    onQuestionClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onQuestionClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (question.isPremium) {
                        Spacer(modifier = Modifier.height(8.dp))
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
                
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (isFavorite) Color(0xFFFD267A) else Color.Gray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Indicateur de difficulté
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Niveau: ${question.difficulty}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFD267A)
                )
            }
        }
    }
}

/**
 * Écran de détail d'une question
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    questionId: String,
    navigationManager: NavigationManager = NavigationManager.instance
) {
    // Simuler le chargement de la question
    val question = remember { generateQuestionById(questionId) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
    ) {
        // TopAppBar
        TopAppBar(
            title = { Text("Question") },
            navigationIcon = {
                IconButton(onClick = { navigationManager.goBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )
        
        // Contenu de la question
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Question principale
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (question.subtext.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = question.subtext,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { 
                        Log.i("Love2Love", "Répondre à la question: $questionId")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💬 Répondre ensemble")
                }
                
                OutlinedButton(
                    onClick = { 
                        Log.i("Love2Love", "Partager la question: $questionId")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 Partager")
                }
                
                TextButton(
                    onClick = { 
                        Log.i("Love2Love", "Question suivante demandée")
                        // Générer une nouvelle question
                        val nextQuestionId = "question_${System.currentTimeMillis()}"
                        navigationManager.navigateToQuestion(nextQuestionId)
                    }
                ) {
                    Text("⏭️ Question suivante")
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Conseils
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFD267A).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 Conseil",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFFD267A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prenez le temps d'écouter vraiment la réponse de votre partenaire. Il n'y a pas de bonne ou mauvaise réponse !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Modèle de données pour une question
 */
data class Question(
    val id: String,
    val text: String,
    val subtext: String = "",
    val categoryId: String,
    val difficulty: String,
    val isPremium: Boolean = false
)

/**
 * Générer des questions d'exemple pour une catégorie
 */
fun generateSampleQuestions(category: QuestionCategory): List<Question> {
    return when (category.id) {
        "daily" -> listOf(
            Question("q1", "Quel a été le moment le plus heureux de ta journée ?", "", category.id, "Facile"),
            Question("q2", "Si tu pouvais changer une chose dans ta journée, ce serait quoi ?", "", category.id, "Moyen"),
            Question("q3", "Qu'est-ce qui t'a fait sourire aujourd'hui ?", "", category.id, "Facile"),
            Question("q4", "Quel défi as-tu relevé aujourd'hui ?", "", category.id, "Moyen")
        )
        "challenges" -> listOf(
            Question("c1", "Préparez ensemble un plat que vous n'avez jamais cuisiné", "Choisissez une recette et amusez-vous !", category.id, "Moyen"),
            Question("c2", "Écrivez-vous une lettre d'amour à échanger ce soir", "", category.id, "Facile"),
            Question("c3", "Planifiez une sortie surprise pour votre partenaire", "", category.id, "Difficile")
        )
        "intimacy" -> listOf(
            Question("i1", "Qu'est-ce qui te fait te sentir le plus aimé(e) ?", "", category.id, "Moyen", true),
            Question("i2", "Quel est ton souvenir le plus tendre de nous deux ?", "", category.id, "Facile", true),
            Question("i3", "Comment aimerais-tu qu'on se montre plus d'affection ?", "", category.id, "Difficile", true)
        )
        "dreams" -> listOf(
            Question("d1", "Où aimerais-tu qu'on voyage ensemble dans 5 ans ?", "", category.id, "Moyen", true),
            Question("d2", "Quel projet commun te fait le plus rêver ?", "", category.id, "Difficile", true),
            Question("d3", "Comment vois-tu notre vie dans 10 ans ?", "", category.id, "Difficile", true)
        )
        "communication" -> listOf(
            Question("com1", "Comment préfères-tu qu'on résolve nos désaccords ?", "", category.id, "Difficile"),
            Question("com2", "Qu'est-ce que j'ai dit récemment qui t'a fait plaisir ?", "", category.id, "Moyen"),
            Question("com3", "Sur quoi aimerais-tu qu'on communique plus ?", "", category.id, "Difficile")
        )
        "fun" -> listOf(
            Question("f1", "Quel est le truc le plus drôle qu'on ait fait ensemble ?", "", category.id, "Facile"),
            Question("f2", "Quelle activité rigolote on devrait essayer ?", "", category.id, "Moyen"),
            Question("f3", "Raconte-moi une blague que tu adores", "", category.id, "Facile")
        )
        else -> listOf(
            Question("default", "Question par défaut pour la catégorie ${category.title}", "", category.id, "Moyen")
        )
    }
}

/**
 * Générer une question par son ID
 */
fun generateQuestionById(questionId: String): Question {
    return Question(
        id = questionId,
        text = "Qu'est-ce qui te rend le plus heureux dans notre relation ?",
        subtext = "Prenez le temps de vraiment réfléchir à cette question",
        categoryId = "daily",
        difficulty = "Moyen"
    )
}
