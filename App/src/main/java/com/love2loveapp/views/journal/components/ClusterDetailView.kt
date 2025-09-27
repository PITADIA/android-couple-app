package com.love2loveapp.views.journal.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.love2loveapp.models.JournalCluster
import com.love2loveapp.models.JournalEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📋 ClusterDetailView - Vue détail d'un cluster (identique iOS)
 * Affiche la liste complète des événements dans un cluster avec design élégant
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterDetailView(
    cluster: JournalCluster,
    onDismiss: () -> Unit,
    onSelectEntry: (JournalEntry) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 📊 HEADER AVEC INFORMATIONS DU CLUSTER (identique iOS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${cluster.count} événements",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        // Localisation si disponible
                        cluster.entries.firstOrNull()?.location?.let { location ->
                            Text(
                                text = "${location.city ?: ""}, ${location.country ?: ""}",
                                fontSize = 16.sp,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Bouton fermer
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer",
                            tint = Color.Black
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 📜 LISTE DES ÉVÉNEMENTS (identique iOS)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(cluster.entries) { entry ->
                        ClusterEventItem(
                            entry = entry,
                            onTap = { onSelectEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🎯 Item d'événement dans la liste du cluster (identique iOS)
 * Design : Image/icône + informations + interaction
 */
@Composable
private fun ClusterEventItem(
    entry: JournalEntry,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🖼️ IMAGE OU ICÔNE (identique iOS)
            if (!entry.imageURL.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(entry.imageURL)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.title,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF267A)), // #FD267A identique iOS
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Love event",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // 📝 INFORMATIONS DE L'ÉVÉNEMENT
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(entry.eventDate),
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
            
            // 🔗 INDICATEUR D'INTERACTION
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Voir détail",
                tint = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
