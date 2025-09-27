package com.love2loveapp.views.components

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.User
import com.love2loveapp.models.UserLocation
import com.love2loveapp.views.UnifiedProfileImageView
import com.love2loveapp.views.ProfileImageType
import kotlinx.coroutines.delay
import java.util.*

/**
 * 🏗️ HeaderProfileDistance - Composant principal du header
 * 
 * Architecture équivalente à PartnerDistanceView iOS:
 * - Photos de profil utilisateur et partenaire (80dp)
 * - Distance intelligente avec cache 2 secondes
 * - Ligne courbe tirets design
 * - Callbacks pour navigation tutoriels
 * - Intégration Firebase Functions
 */
@Composable
fun HeaderProfileDistance(
    currentUser: User?,
    onPartnerAvatarTap: () -> Unit,
    onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Services depuis AppDelegate
    val partnerLocationService = AppDelegate.partnerLocationService
    val userCacheManager = AppDelegate.userCacheManager
    val locationSyncService = AppDelegate.locationSyncService
    
    // 🌍 Service unifié robuste (équivalent iOS LocationService)
    val unifiedLocationService = AppDelegate.unifiedLocationService
    
    // États pour cache distance
    var cachedDistance by remember { mutableStateOf("km ?") }
    var lastCalculationTime by remember { mutableLongStateOf(0L) }
    
    // Données partenaire
    val hasConnectedPartner = !currentUser?.partnerId.isNullOrEmpty()
    val partnerLocation by partnerLocationService?.partnerLocation?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerProfileImageURL by partnerLocationService?.partnerProfileImageURL?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerName by partnerLocationService?.partnerName?.collectAsState() ?: remember { mutableStateOf("") }
    
    // 🌍 Localisation utilisateur depuis service unifié (équivalent iOS @Published)
    val userLocationFromService by unifiedLocationService?.currentLocation?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // Calcul distance en temps réel (équivalent iOS partnerDistance computed property)
    LaunchedEffect(
        userLocationFromService,  // 🆕 Utiliser service unifié au lieu de currentUser
        partnerLocation
    ) {
        // Utiliser localisation du service unifié prioritairement (plus récente)
        val effectiveUserLocation = userLocationFromService ?: currentUser?.currentLocation
        
        cachedDistance = calculatePartnerDistance(
            context = context,
            currentLocation = effectiveUserLocation,
            partnerLocation = partnerLocation,
            cachedDistance = cachedDistance,
            lastCalculationTime = lastCalculationTime
        ) { newDistance, newTime ->
            cachedDistance = newDistance
            lastCalculationTime = newTime
        }
        
        // Log pour debugging
        Log.d("HeaderProfileDistance", "🔄 Recalcul distance:")
        Log.d("HeaderProfileDistance", "  - Service location: ${userLocationFromService?.displayName}")
        Log.d("HeaderProfileDistance", "  - AppState location: ${currentUser?.currentLocation?.displayName}")
        Log.d("HeaderProfileDistance", "  - Partner location: ${partnerLocation?.displayName}")
        Log.d("HeaderProfileDistance", "  - Distance: $cachedDistance")
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Photo profil utilisateur
        UserProfileImage(
            imageURL = currentUser?.profileImageURL,
            userName = currentUser?.name ?: "",
            size = 80.dp,
            userCacheManager = userCacheManager
        )
        
        // Distance avec ligne courbe
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Ligne courbe de fond
            CurvedDashedLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
            
            // Distance cliquable au centre (équivalent iOS avec réactivité temps réel)
            DistanceButton(
                distance = cachedDistance,
                onClick = { showPartnerMessageOnly ->
                    onDistanceTap(showPartnerMessageOnly)
                },
                // Calculer états
                showPartnerMessageOnly = shouldShowPartnerLocationMessage(
                    currentUser,
                    partnerLocation,
                    context
                ),
                isClickable = shouldShowLocationPermissionFlow(
                    currentUser,
                    partnerLocation,
                    context
                ) || shouldShowPartnerLocationMessage(
                    currentUser,
                    partnerLocation,
                    context
                )
            )
        }
        
        // 🎯 Photo profil partenaire UNIFIÉE (observe ProfileImageManager StateFlow)
        UnifiedProfileImageView(
            imageType = ProfileImageType.PARTNER,
            partnerName = partnerName ?: "",
            size = 80.dp,
            onClick = if (!hasConnectedPartner) onPartnerAvatarTap else null
        )
    }
}

/**
 * 📏 Calcul intelligent de la distance avec cache 2 secondes
 * Équivalent de la logique iOS partnerDistance
 */
private suspend fun calculatePartnerDistance(
    context: Context,
    currentLocation: UserLocation?,
    partnerLocation: UserLocation?,
    cachedDistance: String,
    lastCalculationTime: Long,
    onUpdate: (String, Long) -> Unit
): String {
    val now = System.currentTimeMillis()
    
    // Cache ultra-rapide - ne recalculer que toutes les 2 secondes
    if (now - lastCalculationTime < 2000 &&
        cachedDistance != "km ?" && cachedDistance != "? mi") {
        return cachedDistance
    }
    
    // Vérifier données nécessaires
    if (currentLocation == null || partnerLocation == null) {
        return "km ?"
    }
    
    // Calcul distance avec Android Location
    val results = FloatArray(1)
    Location.distanceBetween(
        currentLocation.latitude,
        currentLocation.longitude,
        partnerLocation.latitude,
        partnerLocation.longitude,
        results
    )
    
    val distance = results[0]
    
    // Formatage selon localisation
    val locale = Locale.getDefault()
    val formattedDistance = if (distance < 1000) { // Moins de 1 km
        if (locale.language == "en") {
            val feet = (distance * 3.28084f).toInt()
            "$feet ft"
        } else {
            "${distance.toInt()} m"
        }
    } else { // Plus de 1 km
        if (locale.language == "en") {
            val miles = distance / 1609.34f
            String.format("%.1f mi", miles)
        } else {
            val kilometers = distance / 1000f
            String.format("%.1f km", kilometers)
        }
    }
    
    // Mise à jour cache et persistance
    onUpdate(formattedDistance, now)
    
    // Sauvegarder dans SharedPreferences
    val sharedPrefs = context.getSharedPreferences("distance_cache", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .putString("last_distance", formattedDistance)
        .putLong("last_update_time", now)
        .apply()
    
    Log.d("HeaderProfileDistance", "🌍 Distance calculée: $formattedDistance")
    
    return formattedDistance
}

/**
 * 🎯 Logique de clic distance - permission utilisateur nécessaire
 * Équivalent de shouldShowLocationPermissionFlow iOS
 */
private fun shouldShowLocationPermissionFlow(
    currentUser: User?,
    partnerLocation: UserLocation?,
    context: android.content.Context
): Boolean {
    val userLocation = currentUser?.currentLocation
    val partnerId = currentUser?.partnerId
    val hasPartner = !partnerId.isNullOrEmpty()
    
    // Vérifier les permissions Android directement
    val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context, 
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, 
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    
    android.util.Log.d("HeaderProfileDistance", "🔍 shouldShowLocationPermissionFlow:")
    android.util.Log.d("HeaderProfileDistance", "  - currentUser.name: ${currentUser?.name}")
    android.util.Log.d("HeaderProfileDistance", "  - userLocation: ${userLocation?.displayName}")
    android.util.Log.d("HeaderProfileDistance", "  - hasLocationPermission: $hasLocationPermission")
    android.util.Log.d("HeaderProfileDistance", "  - partnerId: $partnerId")
    android.util.Log.d("HeaderProfileDistance", "  - hasPartner: $hasPartner")
    android.util.Log.d("HeaderProfileDistance", "  - partnerLocation: ${partnerLocation?.displayName}")
    
    // Si l'utilisateur n'a pas accordé la permission → demander permission
    if (!hasLocationPermission) {
        android.util.Log.d("HeaderProfileDistance", "  ✅ PERMISSION REQUISE: Utilisateur sans permission localisation")
        return true
    }
    
    android.util.Log.d("HeaderProfileDistance", "  ❌ PERMISSION OK: Utilisateur a la permission")
    return false
}

/**
 * 💬 Logique message partenaire - utilisateur a localisation mais pas partenaire
 * Équivalent de shouldShowPartnerLocationMessage iOS
 */
private fun shouldShowPartnerLocationMessage(
    currentUser: User?,
    partnerLocation: UserLocation?,
    context: android.content.Context
): Boolean {
    val userLocation = currentUser?.currentLocation
    val partnerId = currentUser?.partnerId
    val hasPartner = !partnerId.isNullOrEmpty()
    
    // Vérifier les permissions Android directement
    val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context, 
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, 
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    
    android.util.Log.d("HeaderProfileDistance", "💬 shouldShowPartnerLocationMessage:")
    android.util.Log.d("HeaderProfileDistance", "  - currentUser.name: ${currentUser?.name}")
    android.util.Log.d("HeaderProfileDistance", "  - userLocation: ${userLocation?.displayName}")
    android.util.Log.d("HeaderProfileDistance", "  - hasLocationPermission: $hasLocationPermission")
    android.util.Log.d("HeaderProfileDistance", "  - partnerId: $partnerId")
    android.util.Log.d("HeaderProfileDistance", "  - hasPartner: $hasPartner")
    android.util.Log.d("HeaderProfileDistance", "  - partnerLocation: ${partnerLocation?.displayName}")
    
    // Si utilisateur n'a pas la permission → ne pas afficher message partenaire
    if (!hasLocationPermission) {
        android.util.Log.d("HeaderProfileDistance", "  ❌ PAS DE MESSAGE: Utilisateur sans permission")
        return false
    }
    
    // Si utilisateur a localisation mais pas de partenaire → afficher message d'invitation
    if (!hasPartner) {
        android.util.Log.d("HeaderProfileDistance", "  ✅ AFFICHER MESSAGE PARTENAIRE: Utilisateur a localisation mais pas de partenaire")
        return true
    }
    
    // Si utilisateur a localisation + partenaire sans localisation → afficher message partenaire
    if (hasPartner && partnerLocation == null) {
        android.util.Log.d("HeaderProfileDistance", "  ✅ AFFICHER MESSAGE PARTENAIRE: Utilisateur a localisation, partenaire sans localisation")
        return true
    }
    
    android.util.Log.d("HeaderProfileDistance", "  ❌ PAS DE MESSAGE: Partenaire a sa localisation")
    return false
}
