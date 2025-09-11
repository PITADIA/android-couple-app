package com.love2loveapp.core.ui.views.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.R
import com.love2loveapp.core.viewmodels.PartnerConnectionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vue de succès de connexion partenaire
 * Équivalent Kotlin Compose de PartnerConnectionSuccessView.swift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerConnectionSuccessView(
    partnerName: String,
    mode: PartnerConnectionMode = PartnerConnectionMode.WaitForServices,
    context: ConnectionContext = ConnectionContext.Onboarding,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PartnerConnectionViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    
    // États d'animation
    val titleAlpha = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(0f) }
    val featuresAlpha = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }
    
    // États observables du ViewModel
    val isWaiting by viewModel.isWaiting.collectAsState()
    
    // Configuration au premier affichage
    LaunchedEffect(Unit) {
        println("🎉 PartnerConnectionSuccessView: Vue apparue pour partenaire: $partnerName")
        
        viewModel.configureConnection(partnerName, mode, context)
        viewModel.trackSuccessViewShown()
        
        // Démarrer les animations en séquence (équivalent Swift)
        launch {
            delay(500)
            titleAlpha.animateTo(1f, animationSpec = tween(1000))
        }
        launch {
            delay(1200)
            cardAlpha.animateTo(1f, animationSpec = tween(1000))
        }
        launch {
            delay(1500)
            featuresAlpha.animateTo(1f, animationSpec = tween(1000))
        }
        launch {
            delay(2000)
            buttonAlpha.animateTo(1f, animationSpec = tween(1000))
        }
    }
    
    // Annuler l'attente si la vue disparaît
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelWaiting()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Fond gris clair identique à l'app
    ) {
        // Dégradé rose très doux en arrière-plan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.03f),
                            Color(0xFFFF655B).copy(alpha = 0.02f)
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Titre avec animation
            Text(
                text = stringResource(R.string.connection_successful),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(titleAlpha.value)
            )
            
            Spacer(modifier = Modifier.height(66.dp))
            
            // Carte avec connexion partenaire
            PartnerConnectionCard(
                partnerName = partnerName,
                alpha = cardAlpha.value
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Fonctionnalités débloquées
            NewFeaturesSection(
                alpha = featuresAlpha.value
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bouton Continuer
            ContinueButton(
                mode = mode,
                isWaiting = isWaiting,
                alpha = buttonAlpha.value,
                onClick = {
                    coroutineScope.launch {
                        viewModel.handleContinue(onContinue)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

enum class PartnerConnectionMode(val displayName: String) {
    SimpleDismiss("simple"),
    WaitForServices("wait_services")
}

enum class ConnectionContext(val rawValue: String) {
    Onboarding("onboarding"),
    Menu("menu"),
    Profile("profile"),
    Questions("questions"),
    Challenges("challenges")
}

/**
 * Carte de connexion partenaire
 */
@Composable
private fun PartnerConnectionCard(
    partnerName: String,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icône cœur
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFD267A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Texte de connexion
            Text(
                text = stringResource(R.string.connected_with, partnerName),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            
            // Icône de validation
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.Green,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Section des nouvelles fonctionnalités
 */
@Composable
private fun NewFeaturesSection(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.new_features_unlocked),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        FeatureItem(
            emoji = "💕",
            text = stringResource(R.string.shared_favorite_questions)
        )
        
        FeatureItem(
            emoji = "📍",
            text = stringResource(R.string.real_time_distance)
        )
        
        FeatureItem(
            emoji = "⭐",
            text = stringResource(R.string.shared_journal)
        )
        
        FeatureItem(
            emoji = "📱",
            text = stringResource(R.string.widgets_available)
        )
        
        FeatureItem(
            emoji = "💬",
            text = stringResource(R.string.daily_questions_available)
        )
    }
}

/**
 * Composant pour afficher un élément de fonctionnalité
 */
@Composable
private fun FeatureItem(
    emoji: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
        
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.8f)
        )
    }
}

/**
 * Bouton Continuer avec états
 */
@Composable
private fun ContinueButton(
    mode: PartnerConnectionMode,
    isWaiting: Boolean,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(alpha),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFD267A).copy(
                alpha = if (isWaiting && mode == PartnerConnectionMode.WaitForServices) 0.7f else 1f
            )
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWaiting && mode == PartnerConnectionMode.WaitForServices) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = getButtonText(mode, isWaiting),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * Obtient le texte du bouton selon le mode et l'état
 */
private fun getButtonText(mode: PartnerConnectionMode, isWaiting: Boolean): String {
    return when (mode) {
        PartnerConnectionMode.SimpleDismiss -> "Continuer" // stringResource(R.string.continue_text)
        PartnerConnectionMode.WaitForServices -> {
            if (isWaiting) {
                "Préparation en cours..." // stringResource(R.string.preparation_in_progress)
            } else {
                "Continuer" // stringResource(R.string.continue_text)
            }
        }
    }
}