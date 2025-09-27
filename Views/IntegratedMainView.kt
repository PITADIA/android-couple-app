package com.love2loveapp.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.core.common.Result
import com.love2loveapp.di.ServiceContainer
import com.love2loveapp.ui.components.IntegratedErrorView
import com.love2loveapp.ui.components.IntegratedLoadingView

/**
 * 🏠 IntegratedMainView - Vue Principale avec Tous les Services Intégrés
 * 
 * Responsabilités :
 * - Interface principale utilisant tous les Service Managers
 * - États réactifs depuis IntegratedAppState
 * - Navigation automatique et gestion des services
 * - Dashboard complet de l'application
 * 
 * Architecture : Compose + All Service Managers + Reactive UI
 */
@Composable
fun IntegratedMainView(
    integratedAppState: IntegratedAppState,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    // === États Réactifs depuis Service Managers ===
    
    // Partner States
    val hasConnectedPartner by integratedAppState.hasConnectedPartner.collectAsState()
    val partnerConnectionStatus by integratedAppState.partnerConnectionStatus.collectAsState()
    val partnerInfo by integratedAppState.partnerInfo.collectAsState()
    
    // Content States
    val currentDailyQuestion by integratedAppState.currentDailyQuestion.collectAsState()
    val currentDailyChallenge by integratedAppState.currentDailyChallenge.collectAsState()
    val favoriteQuestions by integratedAppState.favoriteQuestions.collectAsState()
    val journalEntries by integratedAppState.journalEntries.collectAsState()
    
    // System States
    val systemHealth by integratedAppState.systemHealth.collectAsState()
    val analyticsEnabled by integratedAppState.analyticsEnabled.collectAsState()
    val notificationsEnabled by integratedAppState.notificationsEnabled.collectAsState()
    val currentLanguage by integratedAppState.currentLanguage.collectAsState()
    
    // Navigation States
    val currentTab by integratedAppState.currentTab.collectAsState()
    val isSheetPresented by integratedAppState.isSheetPresented.collectAsState()
    
    // === UI Principale ===
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header avec statut système
        IntegratedSystemStatusHeader(
            systemHealth = systemHealth,
            currentLanguage = currentLanguage,
            analyticsEnabled = analyticsEnabled,
            onNavigateToSettings = onNavigateToSettings
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Contenu principal
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Partenaire
            item {
                IntegratedPartnerSection(
                    hasConnectedPartner = hasConnectedPartner,
                    partnerConnectionStatus = partnerConnectionStatus,
                    partnerInfo = partnerInfo,
                    onConnectPartner = {
                        // Navigation via Service Manager
                        integratedAppState.navigationManager.navigateToTab(
                            com.love2loveapp.core.services.navigation.TabRoute.PROFILE
                        )
                    }
                )
            }
            
            // Section Contenu Quotidien
            item {
                IntegratedDailyContentSection(
                    currentDailyQuestion = currentDailyQuestion,
                    currentDailyChallenge = currentDailyChallenge,
                    onNavigateToQuestion = {
                        integratedAppState.navigationManager.navigateToTab(
                            com.love2loveapp.core.services.navigation.TabRoute.DAILY_QUESTION
                        )
                    },
                    onNavigateToChallenge = {
                        integratedAppState.navigationManager.navigateToTab(
                            com.love2loveapp.core.services.navigation.TabRoute.DAILY_CHALLENGE
                        )
                    }
                )
            }
            
            // Section Contenu Sauvegardé
            item {
                IntegratedSavedContentSection(
                    favoriteQuestions = favoriteQuestions,
                    journalEntries = journalEntries,
                    onNavigateToFavorites = {
                        // Navigation vers favoris
                    },
                    onNavigateToJournal = {
                        integratedAppState.navigationManager.navigateToTab(
                            com.love2loveapp.core.services.navigation.TabRoute.JOURNAL
                        )
                    }
                )
            }
            
            // Section Actions Rapides
            item {
                IntegratedQuickActionsSection(
                    integratedAppState = integratedAppState,
                    onRefreshAll = {
                        // Actualiser tout via Service Managers
                        integratedAppState.contentServiceManager.refreshAllContent()
                    }
                )
            }
        }
    }
}

/**
 * Header avec statut système
 */
@Composable
private fun IntegratedSystemStatusHeader(
    systemHealth: com.love2loveapp.core.services.managers.SystemHealthStatus,
    currentLanguage: String,
    analyticsEnabled: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Love2Love",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicateur santé système
                    val healthColor = when (systemHealth) {
                        com.love2loveapp.core.services.managers.SystemHealthStatus.HEALTHY -> MaterialTheme.colorScheme.primary
                        com.love2loveapp.core.services.managers.SystemHealthStatus.WARNING -> MaterialTheme.colorScheme.tertiary
                        com.love2loveapp.core.services.managers.SystemHealthStatus.CRITICAL -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Circle,
                        contentDescription = "System Health",
                        tint = healthColor,
                        modifier = Modifier.size(12.dp)
                    )
                    
                    Text(
                        text = "Lang: ${currentLanguage.uppercase()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (analyticsEnabled) {
                        Text(
                            text = "📊",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    }
}

/**
 * Section partenaire
 */
@Composable
private fun IntegratedPartnerSection(
    hasConnectedPartner: Boolean,
    partnerConnectionStatus: com.love2loveapp.core.services.managers.PartnerConnectionStatus,
    partnerInfo: Result<com.love2loveapp.model.User?>,
    onConnectPartner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "👥 Partenaire",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            when {
                hasConnectedPartner -> {
                    when (partnerInfo) {
                        is Result.Success -> {
                            val partner = partnerInfo.data
                            if (partner != null) {
                                Text(
                                    text = "Connecté avec ${partner.firstName}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                Text(
                                    text = "Statut: ${partnerConnectionStatus.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text("Partenaire connecté mais informations indisponibles")
                            }
                        }
                        is Result.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Chargement informations partenaire...")
                            }
                        }
                        is Result.Error -> {
                            Text(
                                text = "Erreur chargement partenaire",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                partnerConnectionStatus == com.love2loveapp.core.services.managers.PartnerConnectionStatus.CONNECTING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Connexion en cours...")
                    }
                }
                
                else -> {
                    Text(
                        text = "Aucun partenaire connecté",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = onConnectPartner,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connecter mon partenaire")
                    }
                }
            }
        }
    }
}

/**
 * Section contenu quotidien
 */
@Composable
private fun IntegratedDailyContentSection(
    currentDailyQuestion: Result<com.love2loveapp.model.DailyQuestion?>,
    currentDailyChallenge: Result<com.love2loveapp.model.DailyChallenge?>,
    onNavigateToQuestion: () -> Unit,
    onNavigateToChallenge: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📅 Contenu du Jour",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Daily Question
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Question du Jour",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    when (currentDailyQuestion) {
                        is Result.Success -> {
                            val question = currentDailyQuestion.data
                            Text(
                                text = question?.text?.take(50) + "..." ?: "Aucune question",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        is Result.Loading -> {
                            Text("Chargement...", style = MaterialTheme.typography.bodySmall)
                        }
                        is Result.Error -> {
                            Text("Erreur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                
                TextButton(onClick = onNavigateToQuestion) {
                    Text("Voir")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Daily Challenge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Défi du Jour",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    when (currentDailyChallenge) {
                        is Result.Success -> {
                            val challenge = currentDailyChallenge.data
                            Text(
                                text = challenge?.title?.take(50) + "..." ?: "Aucun défi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        is Result.Loading -> {
                            Text("Chargement...", style = MaterialTheme.typography.bodySmall)
                        }
                        is Result.Error -> {
                            Text("Erreur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                
                TextButton(onClick = onNavigateToChallenge) {
                    Text("Voir")
                }
            }
        }
    }
}

/**
 * Section contenu sauvegardé
 */
@Composable
private fun IntegratedSavedContentSection(
    favoriteQuestions: Result<List<com.love2loveapp.model.FavoriteQuestion>>,
    journalEntries: Result<List<com.love2loveapp.model.JournalEntry>>,
    onNavigateToFavorites: () -> Unit,
    onNavigateToJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "💾 Contenu Sauvegardé",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Favoris
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val favoritesCount = when (favoriteQuestions) {
                        is Result.Success -> favoriteQuestions.data.size
                        else -> 0
                    }
                    
                    Text(
                        text = "$favoritesCount",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Favoris",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onNavigateToFavorites) {
                        Text("Voir")
                    }
                }
                
                // Journal
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val journalCount = when (journalEntries) {
                        is Result.Success -> journalEntries.data.size
                        else -> 0
                    }
                    
                    Text(
                        text = "$journalCount",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Journal",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onNavigateToJournal) {
                        Text("Voir")
                    }
                }
            }
        }
    }
}

/**
 * Section actions rapides
 */
@Composable
private fun IntegratedQuickActionsSection(
    integratedAppState: IntegratedAppState,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "⚡ Actions Rapides",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Actualiser tout
                OutlinedButton(
                    onClick = onRefreshAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔄 Actualiser")
                }
                
                // Analytics toggle
                OutlinedButton(
                    onClick = {
                        val current = integratedAppState.analyticsEnabled.value
                        integratedAppState.systemServiceManager.setAnalyticsEnabled(!current)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    val analyticsText = if (integratedAppState.analyticsEnabled.value) "📊 ON" else "📊 OFF"
                    Text(analyticsText)
                }
            }
        }
    }
}
