package com.love2loveapp.views.widgets

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.widgets.WidgetData
import com.love2loveapp.services.widgets.WidgetRepository

/**
 * 📱 WidgetsScreen selon RAPPORT_WIDGETS_COMPLET.md
 * Hub principal widgets avec design exact du rapport iOS
 * 
 * Architecture :
 * - Fond gris clair avec dégradé rose en haut (350dp)
 * - Header avec bouton retour circulaire
 * - Section Écran verrouillé avec previews
 * - Section Écran d'accueil avec previews
 * - Section Comment ajouter avec tutoriels
 */
@Composable
fun WidgetsScreen(
    onBack: () -> Unit,
    onNavigateToHomeScreenTutorial: () -> Unit = {},
    onNavigateToLockScreenTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val widgetRepository = remember { WidgetRepository.getInstance(context) }
    val widgetData by widgetRepository.widgetData.collectAsState()
    val isLoading by widgetRepository.isLoading.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Fond gris clair selon le rapport
    ) {
        // Dégradé rose en haut selon le rapport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.3f),
                            Color(0xFFFD267A).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp)
        ) {
            // Header selon le rapport - BOUTON RETOUR SUPPRIMÉ POUR LES SHEETS
            Spacer(modifier = Modifier.height(20.dp))
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Section Écran verrouillé selon le rapport
            LockScreenWidgetsSection(
                widgetData = widgetData,
                isLoading = isLoading,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Section Écran d'accueil selon le rapport
            HomeScreenWidgetsSection(
                widgetData = widgetData,
                isLoading = isLoading,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Section Comment ajouter selon le rapport
            AddWidgetHowToSection(
                onNavigateToLockScreenTutorial = onNavigateToLockScreenTutorial,
                onNavigateToHomeScreenTutorial = onNavigateToHomeScreenTutorial,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

// === SECTION ÉCRAN VERROUILLÉ SELON LE RAPPORT ===
@Composable 
private fun LockScreenWidgetsSection(
    widgetData: WidgetData,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val appState = AppDelegate.appState
    val currentUser by appState.currentUser.collectAsState()
    val hasSubscription = currentUser?.isSubscribed ?: false
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre section selon le rapport
        Text(
            text = stringResource(R.string.lock_screen),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        // ScrollView horizontal avec widgets selon le rapport
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            // Widget Distance selon le rapport
            item {
                LockScreenWidgetPreview(
                    widgetType = WidgetType.Distance,
                    widgetData = widgetData,
                    hasSubscription = hasSubscription,
                    isLoading = isLoading
                )
            }
            
            // Widget Jours Total selon le rapport
            item {
                LockScreenWidgetPreview(
                    widgetType = WidgetType.DaysTotal,
                    widgetData = widgetData,
                    hasSubscription = hasSubscription,
                    isLoading = isLoading
                )
            }
        }
    }
}

// === SECTION ÉCRAN D'ACCUEIL SELON LE RAPPORT ===
@Composable
private fun HomeScreenWidgetsSection(
    widgetData: WidgetData,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val appState = AppDelegate.appState
    val currentUser by appState.currentUser.collectAsState()
    val hasSubscription = currentUser?.isSubscribed ?: false
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre section selon le rapport
        Text(
            text = stringResource(R.string.home_screen),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        // ScrollView horizontal avec widgets selon le rapport
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            // Widget Principal Petit selon le rapport
            item {
                HomeScreenWidgetPreview(
                    title = stringResource(R.string.widget_small_subtitle),
                    widgetData = widgetData,
                    isMain = false,
                    hasSubscription = hasSubscription,
                    isLoading = isLoading
                )
            }
            
            // Widget Complet selon le rapport
            item {
                HomeScreenWidgetPreview(
                    title = stringResource(R.string.widget_complete_title),
                    widgetData = widgetData,
                    isMain = true,
                    hasSubscription = hasSubscription,
                    isLoading = isLoading
                )
            }
        }
    }
}

// === SECTION COMMENT AJOUTER SELON LE RAPPORT ===
@Composable
private fun AddWidgetHowToSection(
    onNavigateToLockScreenTutorial: () -> Unit,
    onNavigateToHomeScreenTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre section selon le rapport
        Text(
            text = stringResource(R.string.how_to_add), // Utilise clé de traduction
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card pour widgets écran verrouillé selon le rapport
            TutorialCard(
                title = stringResource(R.string.lock_screen_widget),
                description = stringResource(R.string.complete_guide),
                onClick = onNavigateToLockScreenTutorial
            )
            
            // Card pour widgets écran d'accueil selon le rapport
            TutorialCard(
                title = stringResource(R.string.home_screen_widget),
                description = stringResource(R.string.complete_guide),
                onClick = onNavigateToHomeScreenTutorial
            )
        }
    }
}

// === COMPOSANTS WIDGET PREVIEW SELON LE RAPPORT ===

// Lock Screen Widget Preview selon le rapport
@Composable
private fun LockScreenWidgetPreview(
    widgetType: WidgetType,
    widgetData: WidgetData,
    hasSubscription: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isPremium = widgetType.requiresPremium
    val width = if (widgetType == WidgetType.Distance) 200.dp else 140.dp
    
    Box(
        modifier = modifier
            .width(width)
            .height(120.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                when (widgetType) {
                    WidgetType.Distance -> {
                        if (isLoading) {
                            LoadingContent()
                        } else if (!hasSubscription && isPremium) {
                            PremiumLockedContent()
                        } else {
                            DistanceWidgetContent(widgetData)
                        }
                    }
                    WidgetType.DaysTotal -> {
                        if (isLoading) {
                            LoadingContent()
                        } else {
                            DaysTotalWidgetContent(widgetData)
                        }
                    }
                }
            }
        }
        
        // Cadenas premium si nécessaire selon le rapport
        if (isPremium && !hasSubscription) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = stringResource(R.string.locked_widget),
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

// Home Screen Widget Preview selon le rapport
@Composable
private fun HomeScreenWidgetPreview(
    title: String,
    widgetData: WidgetData,
    isMain: Boolean,
    hasSubscription: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isPremium = false // Tous les widgets sont maintenant gratuits
    val width = if (isMain) 160.dp else 140.dp
    
    Box(
        modifier = modifier
            .width(width)
            .height(120.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    LoadingContent()
                } else if (!hasSubscription && isPremium) {
                    PremiumLockedContent()
                } else {
                    if (isMain) {
                        CompleteWidgetContent(widgetData)
                    } else {
                        SmallWidgetContent(widgetData)
                    }
                }
            }
        }
        
        // Cadenas premium si nécessaire selon le rapport
        if (isPremium && !hasSubscription) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = stringResource(R.string.locked_widget),
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

// === COMPOSANTS CONTENU WIDGET SELON LE RAPPORT ===

@Composable
private fun LoadingContent() {
    CircularProgressIndicator(
        color = Color.White,
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.dp
    )
}

@Composable
private fun PremiumLockedContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.widget_premium_required),
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.widget_unlock_premium),
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DistanceWidgetContent(widgetData: WidgetData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = widgetData.distanceInfo?.formattedDistance ?: stringResource(R.string.widget_dash_km),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.widget_distance_label),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun DaysTotalWidgetContent(widgetData: WidgetData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = widgetData.relationshipStats?.daysTotal?.toString() ?: "0",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.widget_days_label),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = stringResource(R.string.widget_together_text),
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SmallWidgetContent(widgetData: WidgetData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Photos de profil simplifiées
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileCircle(name = widgetData.userName, size = 24.dp)
            ProfileCircle(name = widgetData.partnerName, size = 24.dp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${widgetData.relationshipStats?.daysTotal ?: 0}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.widget_days_label),
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun CompleteWidgetContent(widgetData: WidgetData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Section gauche : Compteur avec photos
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfileCircle(name = widgetData.userName, size = 20.dp)
                ProfileCircle(name = widgetData.partnerName, size = 20.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${widgetData.relationshipStats?.daysTotal ?: 0}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.widget_days_label),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Séparateur
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )
        
        // Section droite : Distance
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = widgetData.distanceInfo?.formattedDistance ?: stringResource(R.string.widget_dash_km),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileCircle(
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initial = name?.first()?.uppercase() ?: "?"
    
    Box(
        modifier = modifier
            .size(size)
            .background(
                Color.White.copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.4).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// TutorialCard selon le rapport
@Composable
private fun TutorialCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// Types de widgets selon le rapport
private enum class WidgetType(val requiresPremium: Boolean) {
    Distance(false),    // Gratuit - Tous les widgets sont maintenant gratuits
    DaysTotal(false)    // Gratuit selon le rapport
}