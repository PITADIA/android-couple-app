package com.love2loveapp.views.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.views.journal.components.JournalEntryCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📔 JournalScreen selon RAPPORT_DESIGN_JOURNAL.md
 * Design complet iOS avec header navigation, état vide, et sections mensuelles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToCreateEntry: () -> Unit
) {
    val journalRepository = AppDelegate.journalRepository
    val appState = AppDelegate.appState
    val freemiumManager = appState.freemiumManager
    val currentUser by appState.currentUser.collectAsState()
    
    // États observables
    val entries by journalRepository?.entries?.collectAsState(emptyList()) 
        ?: remember { mutableStateOf(emptyList<JournalEntry>()) }
    val isLoading by journalRepository?.isLoading?.collectAsState(false) 
        ?: remember { mutableStateOf(false) }
    val errorMessage by journalRepository?.errorMessage?.collectAsState(null) 
        ?: remember { mutableStateOf(null as String?) }
    
    // États locaux
    val scope = rememberCoroutineScope()
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }
    
    // Initialiser le repository si nécessaire
    LaunchedEffect(currentUser) {
        if (currentUser != null && journalRepository != null) {
            journalRepository.initializeForUser(currentUser!!.id)
        }
    }
    
    // 🎨 Background identique à toute l'app selon le rapport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f)) // RGB(247, 247, 250)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            
            // 🏷️ Header Navigation selon le rapport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton carte (gauche) - 18pt selon le rapport
                IconButton(
                    onClick = onNavigateToMap
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = "View map",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // Titre centré selon le rapport
                Text(
                    text = stringResource(R.string.our_journal),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                // Bouton + (droite) - 20pt selon le rapport
                IconButton(
                    onClick = {
                        // Gestion freemium pour création d'entrées
                        val userEntriesCount = journalRepository?.getUserEntriesCount(currentUser?.id ?: "") ?: 0
                        val manager = freemiumManager.value
                        if (manager is com.love2loveapp.services.SimpleFreemiumManager) {
                            manager.handleJournalEntryCreation(userEntriesCount) {
                                onNavigateToCreateEntry()
                            }
                        } else {
                            onNavigateToCreateEntry()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add entry",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // 📋 Contenu principal selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                when {
                    // État chargement
                    isLoading -> {
                        LoadingJournalView()
                    }
                    
                    // État erreur
                    errorMessage != null -> {
                        ErrorJournalView(
                            errorMessage = errorMessage!!,
                            onRetry = {
                                currentUser?.let { user ->
                                    journalRepository?.initializeForUser(user.id)
                                }
                            }
                        )
                    }
                    
                    // 📔 État vide selon le rapport
                    entries.isEmpty() -> {
                        EmptyJournalView(
                            onCreateEntry = {
                                val userEntriesCount = journalRepository?.getUserEntriesCount(currentUser?.id ?: "") ?: 0
                                val manager = freemiumManager.value
                                if (manager is com.love2loveapp.services.SimpleFreemiumManager) {
                                    manager.handleJournalEntryCreation(userEntriesCount) {
                                        onNavigateToCreateEntry()
                                    }
                                } else {
                                    onNavigateToCreateEntry()
                                }
                            }
                        )
                    }
                    
                    // 📋 Liste avec sections mensuelles selon le rapport
                    else -> {
                        val groupedEntries = entries.sortedByDescending { it.eventDate }
                            .groupBy { entry ->
                                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(entry.eventDate)
                            }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            groupedEntries.forEach { (monthYear, entriesInMonth) ->
                                item {
                                    // 📅 Header de section (mois/année) selon le rapport
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 20.dp, bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = monthYear,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black
                                        )
                                        
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                
                                items(entriesInMonth) { entry ->
                                    JournalEntryCard(
                                        entry = entry,
                                        canEdit = entry.canBeEditedBy(currentUser?.id ?: ""),
                                        canDelete = entry.canBeDeletedBy(currentUser?.id ?: ""),
                                        onEdit = {
                                            // TODO: Navigation vers édition
                                        },
                                        onDelete = {
                                            entryToDelete = entry
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
        
    
    // 🗑️ DIALOG CONFIRMATION SUPPRESSION selon le rapport
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = {
                entryToDelete = null
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_memory_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            },
            text = {
                Column {
                    Text(
                        text = entry.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Créé le ${entry.formattedEventDate}",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.irreversible_action),
                        fontSize = 14.sp,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            journalRepository?.deleteEntry(entry)
                            entryToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text(
                        text = "Supprimer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFFD267A)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

// Vue de chargement selon le rapport
@Composable
private fun LoadingJournalView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFD267A), // Rose Love2Love
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.loading),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// 📔 État vide selon RAPPORT_DESIGN_JOURNAL.md
@Composable
private fun EmptyJournalView(
    onCreateEntry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 🖼️ Image principale - jou.png (240x240pt selon le rapport)
        Image(
            painter = painterResource(R.drawable.jou),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Titre selon le rapport (22sp Medium)
            Text(
                text = stringResource(R.string.empty_journal_message),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            
            // Description selon le rapport (16sp, opacity 0.7)
            Text(
                text = stringResource(R.string.journal_description),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // 🎨 Bouton Créer selon le rapport (hauteur 38dp, largeur adaptative, rose Love2Love, coins 19dp)
        Button(
            onClick = onCreateEntry,
            modifier = Modifier
                .wrapContentWidth()
                .height(38.dp)
                .padding(horizontal = 4.dp), // Marge pour éviter le collage
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFD267A) // Rose Love2Love
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) // Padding interne pour le texte
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = stringResource(R.string.create),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// Vue d'erreur selon le rapport
@Composable
private fun ErrorJournalView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        Text(
            text = stringResource(R.string.error),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Text(
            text = errorMessage,
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        OutlinedButton(
            onClick = onRetry,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFFD267A)
            ),
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                Color(0xFFFD267A)
            ),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text(
                text = stringResource(R.string.retry),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFD267A)
            )
        }
    }
}
