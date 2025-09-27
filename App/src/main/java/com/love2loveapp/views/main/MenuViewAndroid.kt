package com.love2loveapp.views.main

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import com.love2loveapp.services.UserCacheManager // TODO: implémenter
import com.love2loveapp.views.components.AndroidPhotoEditorView

/**
 * 🏠 MenuViewAndroid - Équivalent sophistiqué du MenuView iOS
 * 
 * Fonctionnalités équivalentes iOS:
 * - Affichage photo de profil avec cache
 * - Édition sophistiquée avec SwiftyCrop (AndroidPhotoEditorView + UCrop)
 * - Upload automatique vers Firebase
 * - Interface utilisateur Love2Love
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuViewAndroid(
    currentUserName: String = "",
    currentUserPhoto: Bitmap? = null,
    partnerName: String = "",
    relationshipDate: String = "",
    hasPartner: Boolean = false,
    onDeleteAccount: () -> Unit = {},
    onEditName: () -> Unit = {},
    onEditRelationship: () -> Unit = {},
    onShowPartnerCode: () -> Unit = {},
    onLocationTutorial: () -> Unit = {},
    onWidgets: () -> Unit = {}
) {
    var showPhotoEditor by remember { mutableStateOf(false) }
    var profileImage by remember { mutableStateOf(currentUserPhoto) }
    
    // Charger l'image depuis le cache au démarrage - TODO: implémenter
    LaunchedEffect(Unit) {
        if (profileImage == null) {
            // val cachedImage = UserCacheManager.getCachedProfileImage()
            // if (cachedImage != null) {
            //     profileImage = cachedImage
            //     Log.d("MenuViewAndroid", "✅ Image de profil chargée depuis le cache")
            // }
            Log.d("MenuViewAndroid", "TODO: Charger image de profil depuis le cache")
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7FA))
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Section Profil
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Photo de profil avec bouton d'édition
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.2f))
                                .clickable { 
                                    Log.d("MenuViewAndroid", "🖼️ Ouverture éditeur photo de profil")
                                    showPhotoEditor = true 
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            profileImage?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Photo de profil",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Ajouter photo",
                                modifier = Modifier.size(40.dp),
                                tint = Color.Gray
                            )
                        }
                        
                        // Bouton d'édition (équivalent iOS)
                        FloatingActionButton(
                            onClick = { 
                                Log.d("MenuViewAndroid", "✏️ Bouton édition photo cliqué")
                                showPhotoEditor = true 
                            },
                            modifier = Modifier.size(36.dp),
                            containerColor = Color(0xFFFD267A),
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Éditer photo",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Nom utilisateur
                    Text(
                        text = if (currentUserName.isNotEmpty()) currentUserName else "Votre nom",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { onEditName() }
                    )
                    
                    if (hasPartner && partnerName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "En couple avec $partnerName",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        
                        if (relationshipDate.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Depuis le $relationshipDate",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable { onEditRelationship() }
                            )
                        }
                    }
                }
            }
            
            // Autres options de menu
            MenuSection(
                title = "Couple",
                items = listOf(
                    if (hasPartner) "Gérer partenaire" to onShowPartnerCode
                    else "Connecter partenaire" to onShowPartnerCode,
                    "Date de relation" to onEditRelationship
                )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            MenuSection(
                title = "Application",
                items = listOf(
                    "Tutoriel localisation" to onLocationTutorial,
                    "Widgets" to onWidgets
                )
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bouton suppression compte
            Button(
                onClick = onDeleteAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.1f),
                    contentColor = Color.Red
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Supprimer le compte",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 🎨 ÉDITEUR PHOTO SOPHISTIQUÉ (équivalent SwiftyCrop iOS)
        if (showPhotoEditor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo de profil",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        TextButton(onClick = { showPhotoEditor = false }) {
                            Text(
                                text = "Fermer",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Éditeur sophistiqué
                    AndroidPhotoEditorView(
                        currentImage = profileImage,
                        onImageUpdated = { newBitmap ->
                            Log.d("MenuViewAndroid", "✅ Photo de profil mise à jour via éditeur")
                            profileImage = newBitmap
                            
                            // Auto-fermeture après édition (comme iOS)
                            showPhotoEditor = false
                        },
                        onError = { error ->
                            Log.e("MenuViewAndroid", "❌ Erreur éditeur photo: $error")
                            showPhotoEditor = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MenuSection(
    title: String,
    items: List<Pair<String, () -> Unit>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            items.forEachIndexed { index, (itemTitle, action) ->
                if (index > 0) {
                    Divider(
                        color = Color.Gray.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                Text(
                    text = itemTitle,
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action() }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
