package com.love2loveapp.views.journal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.JournalCluster
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.services.map.JournalClusteringService
import com.love2loveapp.views.journal.components.ClusterDetailView
import com.love2loveapp.views.journal.components.mapannotations.SmartJournalAnnotationView

/**
 * 🛡️ Créer bitmap de fallback pour les images qui ne chargent pas
 */
fun createFallbackBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Couleur rose Love2Love
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FD267A")
        isAntiAlias = true
    }
    
    // Dessiner rectangle rose
    canvas.drawRect(0f, 0f, 60f, 60f, paint)
    
    // Cœur blanc au centre
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 24f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("❤", 30f, 40f, paint)
    
    return bitmap
}

/**
 * 🗺️ JournalMapScreen - Système sophistiqué selon rapport iOS
 * Clustering adaptatif + Cache multi-niveaux + Interface élégante
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalMapScreen(
    onBackPressed: () -> Unit
) {
    val journalRepository = AppDelegate.journalRepository
    
    // États observables (identique iOS)
    val entries by journalRepository?.entries?.collectAsState(emptyList()) 
        ?: remember { mutableStateOf(emptyList<JournalEntry>()) }
    
    // 🔑 FILTRER ÉVÉNEMENTS AVEC LOCALISATION (identique iOS)
    val entriesWithLocation = remember(entries) {
        entries.filter { it.hasLocation }
    }
    
    // 🖼️ SYSTÈME DE PRÉ-CHARGEMENT DES IMAGES (solution au problème des marqueurs)
    var preloadedImages by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoadingImages by remember { mutableStateOf(false) }
    
    // 🚀 PRÉ-CHARGER TOUTES LES IMAGES AVANT AFFICHAGE MARQUEURS
    LaunchedEffect(entriesWithLocation) {
        Log.d("JournalMapScreen", "🚀 Début pré-chargement de ${entriesWithLocation.size} images")
        isLoadingImages = true
        
        val imageMap = mutableMapOf<String, Bitmap>()
        
        entriesWithLocation.forEach { entry ->
            if (!entry.imageURL.isNullOrEmpty()) {
                try {
                    Log.d("JournalMapScreen", "📡 Chargement: '${entry.title}' - [URL_MASKED]")
                    
                    val bitmap = withContext(Dispatchers.IO) {
                        // 🚀 URL FIREBASE RÉELLE (le pré-chargement fonctionne !)
                        val url = URL(entry.imageURL!!)
                        val connection = url.openConnection()
                        connection.connect()
                        val inputStream = connection.getInputStream()
                        BitmapFactory.decodeStream(inputStream)
                    }
                    
                    if (bitmap != null) {
                        imageMap[entry.imageURL!!] = bitmap
                        Log.d("JournalMapScreen", "✅ Image chargée: '${entry.title}' ${bitmap.width}x${bitmap.height}")
                    }
                    
                } catch (e: Exception) {
                    Log.e("JournalMapScreen", "❌ Erreur chargement '${entry.title}': ${e.message}")
                    // Créer image de fallback
                    imageMap[entry.imageURL!!] = createFallbackBitmap()
                }
            }
        }
        
        preloadedImages = imageMap
        isLoadingImages = false
        Log.d("JournalMapScreen", "🏁 Pré-chargement terminé: ${imageMap.size} images")
    }
    
    // 📊 CALCULER STATISTIQUES (identique iOS)
    val mapStats = remember(entriesWithLocation) {
        JournalClusteringService.calculateMapStatistics(entriesWithLocation)
    }
    
    // États de la carte
    val cameraPositionState = rememberCameraPositionState {
        // 🌍 RÉGION PAR DÉFAUT INTELLIGENTE (identique iOS)
        position = if (entriesWithLocation.isNotEmpty()) {
            // Centrer sur les événements existants
            val bounds = LatLngBounds.Builder()
            entriesWithLocation.forEach { entry ->
                bounds.include(entry.location!!.coordinate)
            }
            CameraPosition.fromLatLngZoom(bounds.build().center, 10f)
        } else {
            // Région par défaut (France)
            CameraPosition.fromLatLngZoom(
                LatLng(46.2276, 2.2137), 
                5f
            )
        }
    }
    
    // 🧠 CLUSTERING ADAPTATIF (identique iOS) - Force recalcul avec LaunchedEffect
    var clusters by remember { mutableStateOf<List<JournalCluster>>(emptyList()) }
    
    LaunchedEffect(entriesWithLocation, cameraPositionState.position.zoom) {
        clusters = JournalClusteringService.createStableClusters(
            entries = entriesWithLocation,
            zoomLevel = cameraPositionState.position.zoom
        )
    }
    
    // États d'interaction
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var selectedCluster by remember { mutableStateOf<JournalCluster?>(null) }
    
    // 🎨 INTERFACE SOPHISTIQUÉE (identique iOS)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoadingImages) {
            // 🔄 ÉTAT DE CHARGEMENT DES IMAGES
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                     // 🌀 SPINNER DE CHARGEMENT SEUL (selon demande utilisateur)
                     CircularProgressIndicator(
                         color = Color(0xFFFD267A),
                         modifier = Modifier
                             .size(48.dp)
                             .padding(24.dp),
                         strokeWidth = 4.dp
                     )
                }
            }
        } else {
            // 🗺️ CARTE AVEC CLUSTERING ADAPTATIF
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = false,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                // 🎯 ANNOTATIONS AVEC IMAGES PRÉ-CHARGÉES (solution au problème)
                clusters.forEach { cluster ->
                    MarkerComposable(
                        state = MarkerState(position = cluster.coordinate),
                        anchor = Offset(0.5f, 1.0f)
                    ) {
                        SmartJournalAnnotationView(
                            cluster = cluster,
                            preloadedImages = preloadedImages, // 🚀 PASS IMAGES PRÉ-CHARGÉES
                            onEntryTap = { entry ->
                                selectedEntry = entry
                            },
                            onClusterTap = { clusterTapped ->
                                selectedCluster = clusterTapped
                            }
                        )
                    }
                }
            }
        }
        
        // 📱 OVERLAY DE CONTRÔLES (identique iOS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🔙 BOUTON RETOUR (identique iOS)
                IconButton(
                    onClick = onBackPressed,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fermer",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // 📊 BULLE STATISTIQUES (identique iOS)
                if (entriesWithLocation.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.9f),
                                RoundedCornerShape(20.dp)
                            )
                            .shadow(4.dp, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Événements
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Events",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${mapStats.totalEvents}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                        
                        // Villes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationCity,
                                contentDescription = "Cities",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${mapStats.uniqueCities}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                        
                        // Pays
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Countries",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${mapStats.uniqueCountries}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
        
        // 🔍 ÉTAT VIDE ÉLÉGANT (identique iOS)
        if (entriesWithLocation.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Ajoutez des événements au journal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    
                    Text(
                        text = "Vos souvenirs avec géolocalisation apparaîtront sur cette carte",
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }
            }
        }
    }
    
    // 📋 MODAL DÉTAIL CLUSTER (identique iOS)
    selectedCluster?.let { cluster ->
        ClusterDetailView(
            cluster = cluster,
            onDismiss = { selectedCluster = null },
            onSelectEntry = { entry ->
                selectedEntry = entry
                selectedCluster = null
            }
        )
    }
    
    // TODO: 📝 MODAL DÉTAIL ÉVÉNEMENT (à implémenter)
    // selectedEntry?.let { entry ->
    //     JournalEntryDetailView(
    //         entry = entry,
    //         onDismiss = { selectedEntry = null }
    //     )
    // }
}