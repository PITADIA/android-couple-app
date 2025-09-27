package com.love2loveapp.views.journal.components.mapannotations

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.BitmapFactory
import com.love2loveapp.models.JournalCluster
import com.love2loveapp.models.JournalEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🧪 Créer bitmap de test pour diagnostiquer l'affichage
 */
fun createTestBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Couleur rose comme vos annotations iOS
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FD267A")
        isAntiAlias = true
    }
    
    // Dessiner rectangle rose + texte "TEST"
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    
    // Texte blanc "TEST" au centre
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 20f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("TEST", width / 2f, height / 2f + 7f, paint)
    
    return bitmap
}

/**
 * 🚀 CachedMapImageView - Équivalent iOS pour chargement manuel d'images
 * Reproduit exactement le comportement de CachedMapImageView iOS
 */
@Composable
fun CachedMapImageView(
    imageUrl: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // ⚡ ÉTAT comme iOS CachedMapImageView
    var image by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }
    
    when {
        // ✅ SUCCESS: Image chargée (comme iOS)
        image != null -> {
            Log.d("MapAnnotation", "✅ Affichage image réussie [URL_MASKED]")
            Image(
                bitmap = image!!.asImageBitmap(),
                contentDescription = "Journal Image",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        
        // ⏳ LOADING: Chargement en cours (comme iOS ProgressView)
        isLoading -> {
            Log.d("MapAnnotation", "⏳ Affichage loading [URL_MASKED]")
            Box(
                modifier = modifier.background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFFFD267A),
                    strokeWidth = 2.dp
                )
            }
        }
        
        // ❌ ERROR: Erreur de chargement (comme iOS icône photo)
        else -> {
            Log.d("MapAnnotation", "❌ Affichage erreur [URL_MASKED]")
            Box(
                modifier = modifier.background(Color(0xFFFD267A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    
    // 🔄 CHARGEMENT SYNCHRONE dans init() (comme iOS exactement)
    LaunchedEffect(imageUrl) {
        Log.d("MapAnnotation", "🚀 TENTATIVE LaunchedEffect: [URL_MASKED]")
        
        // 🧪 TEST : Forcer une image factice immédiatement
        image = createTestBitmap(60, 60)
        isLoading = false
        
        Log.d("MapAnnotation", "✅ Image factice créée 60x60")
    }
}

/**
 * 🎯 Composant d'événement unique sur la carte (identique iOS)
 * Design sophistiqué : Image 60x60 avec bordure blanche + badge date
 */
@Composable
fun JournalMapAnnotationView(
    entry: JournalEntry,
    preloadedImages: Map<String, Bitmap>, // 🚀 IMAGES PRÉ-CHARGÉES
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Button(
        onClick = onTap,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 🖼️ IMAGE OU ICÔNE SELON DISPONIBILITÉ (identique iOS)
            if (!entry.imageURL.isNullOrEmpty()) {
                Log.d("MapAnnotation", "🖼️ Rendu image pour: '${entry.title}'")
                Log.d("MapAnnotation", "   imageURL: '${entry.imageURL}'")
                
                // 🚀 AFFICHAGE DIRECT D'IMAGE PRÉ-CHARGÉE (solution au problème !)
                val preloadedBitmap = preloadedImages[entry.imageURL]
                
                if (preloadedBitmap != null) {
                    Log.d("MapAnnotation", "✅ Image pré-chargée trouvée: ${preloadedBitmap.width}x${preloadedBitmap.height}")
                    Image(
                        bitmap = preloadedBitmap.asImageBitmap(),
                        contentDescription = entry.title,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Log.w("MapAnnotation", "⚠️ Image pré-chargée manquante pour: '${entry.title}'")
                    // Fallback : Icône cœur
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFD267A))
                            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Love event",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Log.d("MapAnnotation", "❤️ Pas d'image pour: '${entry.title}' → Affichage icône cœur")
                // Événement sans image - icône cœur (identique iOS)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF267A)) // #FD267A identique iOS
                        .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Love event",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // 📝 BADGE TITRE (identique iOS - CORRECTION!)
            Text(
                text = entry.title,  // ✅ TITRE au lieu de la date !
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 2,  // Max 2 lignes comme iOS
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .shadow(2.dp, RoundedCornerShape(6.dp))
                    .widthIn(max = 100.dp)  // Max 100dp comme iOS
            )
        }
    }
}

/**
 * 🎯 Composant de cluster sur la carte (identique iOS)
 * Design sophistiqué : Images empilées avec offset + badge compteur
 */
@Composable
fun JournalClusterAnnotationView(
    cluster: JournalCluster,
    preloadedImages: Map<String, Bitmap>, // 🚀 IMAGES PRÉ-CHARGÉES
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Button(
        onClick = onTap,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Box {
            // 📚 IMAGES EMPILÉES DES ÉVÉNEMENTS (identique iOS - max 2)
            cluster.entries.take(2).forEachIndexed { index, entry ->
                val offsetX = (index * 8).dp
                val offsetY = (-index * 8).dp
                
                if (!entry.imageURL.isNullOrEmpty()) {
                    // 🚀 AFFICHAGE DIRECT D'IMAGE PRÉ-CHARGÉE + offset pour effet d'empilement
                    val preloadedBitmap = preloadedImages[entry.imageURL]
                    
                    if (preloadedBitmap != null) {
                        Image(
                            bitmap = preloadedBitmap.asImageBitmap(),
                            contentDescription = entry.title,
                            modifier = Modifier
                                .offset(x = offsetX, y = offsetY)
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                .shadow(2.dp, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback : Rectangle rose
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX, y = offsetY)
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF267A))
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                .shadow(2.dp, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Love event",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    // Rectangle rose pour événement sans image
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF267A)) // #FD267A identique iOS
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            .shadow(2.dp, RoundedCornerShape(8.dp)),
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
            }
            
            // 🏷️ BADGE AVEC NOMBRE D'ÉVÉNEMENTS (identique iOS)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
            ) {
                Text(
                    text = cluster.count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            Color(0xFFFF267A), // #FD267A identique iOS
                            CircleShape
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .shadow(2.dp, CircleShape)
                )
            }
        }
    }
}

/**
 * 🎨 Composant d'annotation générique (switch automatique)
 * Utilise le bon composant selon si c'est un cluster ou événement unique
 */
@Composable
fun SmartJournalAnnotationView(
    cluster: JournalCluster,
    preloadedImages: Map<String, Bitmap>, // 🚀 IMAGES PRÉ-CHARGÉES
    onEntryTap: (JournalEntry) -> Unit,
    onClusterTap: (JournalCluster) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cluster.isCluster) {
        JournalClusterAnnotationView(
            cluster = cluster,
            preloadedImages = preloadedImages, // 🚀 PASS AUX CLUSTERS
            onTap = { onClusterTap(cluster) },
            modifier = modifier
        )
    } else {
        JournalMapAnnotationView(
            entry = cluster.firstEntry,
            preloadedImages = preloadedImages, // 🚀 PASS AUX ÉVÉNEMENTS
            onTap = { onEntryTap(cluster.firstEntry) },
            modifier = modifier
        )
    }
}
