package com.love2loveapp.views.journal.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.love2loveapp.models.JournalEntry

/**
 * 🃏 JournalEntryCard selon RAPPORT_DESIGN_JOURNAL.md
 * Carte événement avec double ombres sophistiquées et couleurs Love2Love
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryCard(
    entry: JournalEntry,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigation vers détail */ },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 20.dp, // Ombre principale selon le rapport
            hoveredElevation = 6.dp    // Ombre secondaire selon le rapport
        ),
        shape = RoundedCornerShape(20.dp), // Coins arrondis 20pt selon le rapport
        colors = CardDefaults.cardColors(
            containerColor = Color.White // Fond blanc pur selon le rapport
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp) // Padding généreux interne selon le rapport
        ) {
            
            // 📅 HEADER AVEC DATE ET ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Date événement avec couleur Love2Love selon le rapport
                    Text(
                        text = entry.shortFormattedDate,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFD267A) // Rose Love2Love selon le rapport
                    )
                    
                    // Heure avec couleur métadonnée selon le rapport
                    Text(
                        text = entry.formattedTime,
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.6f) // Métadonnées selon le rapport
                    )
                }
                
                // Actions si autorisées
                if (canEdit || canDelete) {
                    Row {
                        if (canEdit) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Modifier",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Gray
                                )
                            }
                        }

                        if (canDelete) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Supprimer",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Red.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 📝 TITRE
            Text(
                text = entry.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 📄 DESCRIPTION avec couleur selon le rapport
            if (entry.description.isNotEmpty()) {
                Text(
                    text = entry.description,
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.8f), // Texte description selon le rapport
                    lineHeight = 22.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 🖼️ IMAGE SI PRÉSENTE
            if (entry.hasImage) {
                AsyncImage(
                    model = entry.imageURL,
                    contentDescription = "Image événement",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 📍 LOCALISATION SI PRÉSENTE avec couleur Love2Love selon le rapport
            if (entry.hasLocation) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFD267A) // Rose Love2Love selon le rapport
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = entry.location?.displayName ?: "Localisation",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.6f), // Métadonnées selon le rapport
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 👤 AUTEUR (si différent de l'utilisateur actuel) avec couleur selon le rapport
            if (entry.authorName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Black.copy(alpha = 0.6f) // Métadonnées selon le rapport
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Par ${entry.authorName}", // TODO: Utiliser stringResource si disponible
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.6f) // Métadonnées selon le rapport
                    )
                }
            }
        }
    }
}
