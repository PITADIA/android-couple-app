package com.love2loveapp.core.ui.views.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.R
import com.love2loveapp.core.ui.extensions.ChatText
import com.love2loveapp.core.viewmodels.AppState
import com.love2loveapp.core.viewmodels.WidgetsViewModel
import com.love2loveapp.model.AppConstants
import com.love2loveapp.model.RelationshipStats
import com.love2loveapp.model.DistanceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

/**
 * Vue principale des widgets - Équivalent Kotlin Compose de WidgetsView.swift
 * 
 * Fonctionnalités :
 * - Prévisualisation des widgets écran verrouillé et d'accueil
 * - Tutoriels d'installation
 * - Gestion freemium avec cadenas
 * - Rotation automatique des messages
 * - Analytics et navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsView(
    appState: AppState,
    onDismiss: () -> Unit,
    onShowSubscription: () -> Unit,
    onShowLockScreenTutorial: () -> Unit,
    onShowHomeScreenTutorial: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetsViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // États observables
    val relationshipStats by viewModel.relationshipStats.collectAsState()
    val distanceInfo by viewModel.distanceInfo.collectAsState()
    val hasSubscription by viewModel.hasSubscription.collectAsState()
    
    // État local pour rotation des messages
    var currentMessageIndex by remember { mutableStateOf(0) }
    
    // Configuration au premier affichage
    LaunchedEffect(Unit) {
        viewModel.configureServices(appState)
        viewModel.refreshData()
        
        // Démarrer la rotation des messages (équivalent Timer Swift)
        launch {
            while (true) {
                delay(3000) // 3 secondes
                val messages = distanceInfo?.messages
                if (!messages.isNullOrEmpty()) {
                    currentMessageIndex = (currentMessageIndex + 1) % messages.size
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Même fond que la page principale
    ) {
        // Dégradé de fond rose (équivalent LinearGradient Swift)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.3f),
                            Color(0xFFFD267A).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                // Header avec bouton retour
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Retour",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(20.dp)) }
            
            // Section Écran verrouillé
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.lock_screen),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, bottom = 16.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        item {
                            LockScreenWidgetPreview(
                                title = stringResource(R.string.widget_distance_title),
                                subtitle = stringResource(R.string.widget_distance_subtitle),
                                widgetType = LockScreenWidgetType.Distance,
                                relationshipStats = relationshipStats,
                                distanceInfo = distanceInfo,
                                appState = appState,
                                hasSubscription = hasSubscription,
                                currentMessageIndex = currentMessageIndex,
                                onPremiumTap = onShowSubscription
                            )
                        }
                        
                        item {
                            LockScreenWidgetPreview(
                                title = stringResource(R.string.widget_days_total_title),
                                subtitle = stringResource(R.string.widget_days_subtitle),
                                widgetType = LockScreenWidgetType.Days,
                                relationshipStats = relationshipStats,
                                distanceInfo = distanceInfo,
                                appState = appState,
                                hasSubscription = hasSubscription,
                                currentMessageIndex = currentMessageIndex,
                                onPremiumTap = onShowSubscription
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(30.dp)) }
            
            // Section Écran d'accueil
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.home_screen),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, bottom = 16.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        item {
                            HomeScreenWidgetPreview(
                                title = stringResource(R.string.widget_days_total_title),
                                subtitle = stringResource(R.string.widget_small_subtitle),
                                isMain = true,
                                relationshipStats = relationshipStats,
                                distanceInfo = distanceInfo,
                                appState = appState,
                                hasSubscription = hasSubscription,
                                onPremiumTap = onShowSubscription
                            )
                        }
                        
                        item {
                            HomeScreenWidgetPreview(
                                title = stringResource(R.string.widget_distance_title),
                                subtitle = stringResource(R.string.widget_small_subtitle),
                                isMain = false,
                                relationshipStats = relationshipStats,
                                distanceInfo = distanceInfo,
                                appState = appState,
                                hasSubscription = hasSubscription,
                                onPremiumTap = onShowSubscription
                            )
                        }
                        
                        item {
                            HomeScreenWidgetPreview(
                                title = stringResource(R.string.widget_complete_title),
                                subtitle = stringResource(R.string.widget_large_subtitle),
                                isMain = false,
                                relationshipStats = relationshipStats,
                                distanceInfo = distanceInfo,
                                appState = appState,
                                hasSubscription = hasSubscription,
                                onPremiumTap = onShowSubscription
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(30.dp)) }
            
            // Section Comment ajouter
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.how_to_add),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, bottom = 16.dp)
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        // Card pour widgets écran verrouillé
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onShowLockScreenTutorial()
                                    // Analytics
                                    viewModel.trackAnalyticsEvent("widget_configure", mapOf("type" to "lock"))
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.lock_screen_widget),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    
                                    Text(
                                        text = stringResource(R.string.complete_guide),
                                        fontSize = 14.sp,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        // Card pour widgets écran d'accueil
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onShowHomeScreenTutorial()
                                    // Analytics
                                    viewModel.trackAnalyticsEvent("widget_configure", mapOf("type" to "home"))
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_screen_widget),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    
                                    Text(
                                        text = stringResource(R.string.complete_guide),
                                        fontSize = 14.sp,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Types de widgets pour écran verrouillé
 */
enum class LockScreenWidgetType {
    Distance, Days
}

/**
 * Aperçu des widgets d'écran verrouillé
 */
@Composable
fun LockScreenWidgetPreview(
    title: String,
    subtitle: String,
    widgetType: LockScreenWidgetType,
    relationshipStats: RelationshipStats?,
    distanceInfo: DistanceInfo?,
    appState: AppState,
    hasSubscription: Boolean,
    currentMessageIndex: Int,
    onPremiumTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Déterminer si ce widget nécessite un abonnement premium
    val isPremium = widgetType == LockScreenWidgetType.Distance
    
    Card(
        modifier = modifier
            .size(
                width = if (widgetType == LockScreenWidgetType.Distance) 200.dp else 140.dp,
                height = 120.dp
            )
            .clickable(enabled = isPremium && !hasSubscription) {
                onPremiumTap()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (widgetType) {
                LockScreenWidgetType.Distance -> {
                    DistanceWidgetContent(
                        distanceInfo = distanceInfo,
                        appState = appState,
                        currentMessageIndex = currentMessageIndex
                    )
                }
                LockScreenWidgetType.Days -> {
                    DaysWidgetContent(
                        relationshipStats = relationshipStats
                    )
                }
            }
            
            // Cadenas pour les widgets premium
            if (isPremium && !hasSubscription) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Contenu du widget distance
 */
@Composable
private fun DistanceWidgetContent(
    distanceInfo: DistanceInfo?,
    appState: AppState,
    currentMessageIndex: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        val formattedDistance = distanceInfo?.formattedDistance ?: "? km"
        
        Text(
            text = "${stringResource(R.string.our_distance)} $formattedDistance",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cercle utilisateur
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appState.currentUser.value?.name?.firstOrNull()?.toString() ?: "Y",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
            
            Text(
                text = stringResource(R.string.dash_placeholder),
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(12.dp)
            )
            
            Text(
                text = stringResource(R.string.dash_placeholder),
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            // Cercle partenaire
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appState.partnerLocationService?.partnerName?.firstOrNull()?.toString() ?: "L",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Contenu du widget jours
 */
@Composable
private fun DaysWidgetContent(
    relationshipStats: RelationshipStats?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "💕",
            fontSize = 20.sp
        )
        
        Text(
            text = relationshipStats?.daysTotal?.toString() ?: "—",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Text(
            text = stringResource(R.string.days),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

/**
 * Aperçu des widgets d'écran d'accueil
 */
@Composable
fun HomeScreenWidgetPreview(
    title: String,
    subtitle: String,
    isMain: Boolean,
    relationshipStats: RelationshipStats?,
    distanceInfo: DistanceInfo?,
    appState: AppState,
    hasSubscription: Boolean,
    onPremiumTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Déterminer si ce widget nécessite un abonnement premium
    val isPremium = title == stringResource(R.string.widget_distance_title) || 
                   title == stringResource(R.string.widget_complete_title)
    
    Card(
        modifier = modifier
            .size(
                width = if (isMain) 160.dp else 140.dp,
                height = 120.dp
            )
            .clickable(enabled = isPremium && !hasSubscription) {
                onPremiumTap()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                title == stringResource(R.string.widget_days_total_title) -> {
                    HomeScreenDaysContent(relationshipStats, appState)
                }
                title == stringResource(R.string.widget_distance_title) -> {
                    HomeScreenDistanceContent(distanceInfo, appState)
                }
                else -> {
                    HomeScreenCompleteContent(relationshipStats, distanceInfo, appState)
                }
            }
            
            // Cadenas pour les widgets premium
            if (isPremium && !hasSubscription) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreenDaysContent(
    relationshipStats: RelationshipStats?,
    appState: AppState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = "💕",
            fontSize = 24.sp
        )
        
        val daysText = if (relationshipStats != null) {
            "${relationshipStats.daysTotal} ${stringResource(R.string.widget_days_text)}"
        } else {
            stringResource(R.string.widget_dash_jours)
        }
        
        Text(
            text = daysText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cercles utilisateur et partenaire
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = if (index == 0) {
                        appState.currentUser.value?.name?.firstOrNull()?.toString() ?: "Y"
                    } else {
                        appState.partnerLocationService?.partnerName?.firstOrNull()?.toString() ?: "L"
                    }
                    Text(
                        text = initial,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreenDistanceContent(
    distanceInfo: DistanceInfo?,
    appState: AppState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = if (index == 0) {
                        appState.currentUser.value?.name?.firstOrNull()?.toString() ?: "Y"
                    } else {
                        appState.partnerLocationService?.partnerName?.firstOrNull()?.toString() ?: "L"
                    }
                    Text(
                        text = initial,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
        
        Text(
            text = distanceInfo?.formattedDistance ?: stringResource(R.string.widget_dash_km),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
        
        Text(
            text = stringResource(R.string.widget_distance_text),
            fontSize = 12.sp,
            color = Color.Black.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun HomeScreenCompleteContent(
    relationshipStats: RelationshipStats?,
    distanceInfo: DistanceInfo?,
    appState: AppState
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        // Section jours
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = if (index == 0) {
                            appState.currentUser.value?.name?.firstOrNull()?.toString() ?: "Y"
                        } else {
                            appState.partnerLocationService?.partnerName?.firstOrNull()?.toString() ?: "L"
                        }
                        Text(
                            text = initial,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }
            }
            
            val daysText = if (relationshipStats != null) {
                "${relationshipStats.daysTotal} ${stringResource(R.string.widget_days_text)}"
            } else {
                stringResource(R.string.widget_dash_jours)
            }
            
            Text(
                text = daysText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            
            Text(
                text = stringResource(R.string.widget_together_text),
                fontSize = 10.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
        
        // Séparateur
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(50.dp)
                .background(Color.Gray.copy(alpha = 0.2f))
        )
        
        // Section distance
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = distanceInfo?.formattedDistance ?: stringResource(R.string.widget_dash_km),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            
            Text(
                text = stringResource(R.string.widget_distance_text),
                fontSize = 8.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}
