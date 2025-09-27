package com.love2loveapp.views.journal.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.love2loveapp.R
import com.love2loveapp.models.JournalLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 📍 LocationPickerView avec Pin Central Fixe
 * Adaptation complète du design iOS selon RAPPORT_CARTE_JOURNAL_COMPLET.md
 * 
 * Fonctionnalités :
 * - Pin central fixe au centre de l'écran (couleur Love2Love)
 * - Carte Google Maps native en plein écran
 * - Fond sombre sophistiqué RGB(25, 5, 13)
 * - Geocoding temps réel avec debouncing 0.5s
 * - Région par défaut intelligente selon locale/langue
 * - Bouton flottant de validation Love2Love
 * - Info localisation temps réel semi-transparente
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalLocationPicker(
    onLocationSelected: (JournalLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // États Google Maps
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var selectedCoordinate by remember { mutableStateOf<LatLng?>(null) }
    var currentLocationName by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }
    
    // Services
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { 
        if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) 
        else null 
    }
    
    // Debouncing pour geocoding
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    
    // Variable pour permissionLauncher - sera initialisée plus tard
    var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? by remember { mutableStateOf(null) }
    
    // Fonctions locales - DOIVENT être définies avant leur utilisation
    val performReverseGeocoding: (LatLng) -> Unit = { coordinate ->
        scope.launch {
            isGeocoding = true
            try {
                withContext(Dispatchers.IO) {
                    geocoder?.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                }?.firstOrNull()?.let { address ->
                    val components = listOfNotNull(
                        address.featureName,    // Nom lieu spécifique
                        address.locality,       // Ville
                        address.countryName     // Pays
                    )
                    
                    currentLocationName = if (components.isNotEmpty()) {
                        components.joinToString(", ")
                    } else {
                        context.getString(R.string.custom_location)
                    }
                } ?: run {
                    currentLocationName = context.getString(R.string.custom_location)
                }
            } catch (e: Exception) {
                Log.w("LocationPicker", "Erreur geocoding: ${e.message}")
                currentLocationName = context.getString(R.string.custom_location)
            } finally {
                isGeocoding = false
            }
        }
    }
    
    val performGeocodingWithDebounce: (LatLng) -> Unit = { coordinate ->
        // Éviter spam geocoding - Debouncing 0.5s selon rapport iOS
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500)
            performReverseGeocoding(coordinate)
        }
    }
    
    val requestCurrentLocationOnMap: () -> Unit = {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val hasPermission = permissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasPermission && googleMap != null) {
            scope.launch {
                try {
                    val location = fusedLocationClient.lastLocation.await()
                    location?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        
                        // Centrer la carte sur la position actuelle
                        googleMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                        )
                        
                        selectedCoordinate = latLng
                        performGeocodingWithDebounce(latLng)
                    }
                } catch (e: Exception) {
                    Log.e("LocationPicker", "Erreur localisation actuelle: ${e.message}")
                }
            }
        } else if (!hasPermission) {
            permissionLauncher?.launch(permissions)
        }
    }
    
    val confirmSelection: () -> Unit = {
        val coordinate = selectedCoordinate
        if (coordinate != null) {
            scope.launch {
                try {
                    val addresses = withContext(Dispatchers.IO) {
                        geocoder?.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                    }
                    
                    val address = addresses?.firstOrNull()
                    
                    val journalLocation = JournalLocation.fromCoordinate(
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        address = address?.getAddressLine(0),
                        city = address?.locality,
                        country = address?.countryName
                    )
                    
                    onLocationSelected(journalLocation)
                    
                } catch (e: Exception) {
                    // Fallback avec données actuelles
                    val journalLocation = JournalLocation.fromCoordinate(
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        address = currentLocationName,
                        city = null,
                        country = null
                    )
                    
                    onLocationSelected(journalLocation)
                }
            }
        }
    }
    
    // Permission launcher - défini après les fonctions locales
    permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                     permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (granted) {
            requestCurrentLocationOnMap()
        }
    }
    
    // 🎨 INTERFACE SELON RAPPORT iOS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.1f, green = 0.02f, blue = 0.05f)) // RGB(25, 5, 13) - Fond sombre sophistiqué selon rapport
    ) {
        
        // 🗺️ CARTE PLEIN ÉCRAN avec GoogleMap native
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    onCreate(null)
                    onResume()
                    
                    getMapAsync { map ->
                        googleMap = map
                        mapView = this
                        
                        // Configuration de la carte selon rapport iOS
                        map.apply {
                            uiSettings.apply {
                                isZoomControlsEnabled = true
                                isCompassEnabled = true
                                isMyLocationButtonEnabled = false
                                isMapToolbarEnabled = false
                            }
                            
                            mapType = GoogleMap.MAP_TYPE_NORMAL
                        }
                        
                        // 🌍 RÉGION PAR DÉFAUT INTELLIGENTE selon rapport iOS
                        val defaultRegion = getDefaultPickerRegion(context)
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                defaultRegion.center,
                                defaultRegion.zoom
                            )
                        )
                        
                        selectedCoordinate = defaultRegion.center
                        performGeocodingWithDebounce(defaultRegion.center)
                        
                        // Listener de mouvement caméra pour pin central
                        map.setOnCameraIdleListener {
                            val center = map.cameraPosition.target
                            selectedCoordinate = center
                            performGeocodingWithDebounce(center)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        )
        
        // 📍 PIN CENTRAL FIXE selon rapport iOS (30pt icon, 40pt background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape), // Background cercle blanc selon rapport
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Pin",
                    tint = Color(0xFFFD267A), // Rose Love2Love selon rapport
                    modifier = Modifier.size(30.dp) // 30pt selon rapport
                )
            }
        }
        
        // 📱 TOOLBAR NAVIGATION SOPHISTIQUÉE selon rapport iOS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .padding(top = 60.dp) // Safe area
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Annuler
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Titre centré
                Text(
                    text = stringResource(R.string.choose_location), // "Choisir une position"
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                
                // Bouton localisation actuelle
                IconButton(onClick = requestCurrentLocationOnMap) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Current location",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // 🏷️ INFO LOCALISATION TEMPS RÉEL selon rapport iOS
        if (currentLocationName.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 100.dp) // Au-dessus bouton selon rapport
                    .zIndex(3f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.8f), // Fond noir semi-transparent selon rapport
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selected_location), // "Position sélectionnée"
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isGeocoding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = currentLocationName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // 🚀 BOUTON FLOTTANT VALIDATION selon rapport iOS
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .zIndex(4f)
        ) {
            Button(
                onClick = confirmSelection,
                enabled = selectedCoordinate != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCoordinate != null) Color(0xFFFD267A) else Color.Gray.copy(alpha = 0.3f) // Love2Love selon rapport
                ),
                shape = RoundedCornerShape(28.dp), // Très arrondi selon rapport (56/2)
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp) // Hauteur tactile confortable selon rapport
            ) {
                Text(
                    text = stringResource(R.string.select), // "Sélectionner"
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 🌍 Région par Défaut Intelligente pour LocationPicker
 * Équivalent iOS getDefaultPickerRegion() selon RAPPORT_CARTE_JOURNAL_COMPLET.md
 */
private data class PickerRegion(
    val center: LatLng,
    val zoom: Float
)

private fun getDefaultPickerRegion(context: Context): PickerRegion {
    // TODO: 1️⃣ PRIORITÉ MAXIMALE: Localisation actuelle si disponible
    // if (LocationService.currentLocation != null) { ... }
    
    // 2️⃣ FALLBACK INTELLIGENT: Locale/région du téléphone selon rapport iOS
    val locale = Locale.getDefault()
    val language = locale.language
    val country = locale.country
    
    return when (language to country) {
        "fr" to null, "fr" to "FR" -> PickerRegion(
            center = LatLng(46.2276, 2.2137), // France centrale selon rapport
            zoom = 6f // Zoom adaptatif pour Europe selon rapport
        )
        
        "en" to "US" -> PickerRegion(
            center = LatLng(39.8283, -98.5795), // États-Unis selon rapport
            zoom = 4f // Zoom adaptatif pour grands pays selon rapport
        )
        
        "en" to "CA" -> PickerRegion(
            center = LatLng(56.1304, -106.3468), // Canada selon rapport
            zoom = 3f
        )
        
        "de" to "DE" -> PickerRegion(
            center = LatLng(51.1657, 10.4515), // Allemagne selon rapport
            zoom = 6f
        )
        
        "es" to "ES" -> PickerRegion(
            center = LatLng(40.4637, -3.7492), // Espagne selon rapport
            zoom = 6f
        )
        
        "it" to "IT" -> PickerRegion(
            center = LatLng(41.9028, 12.4964), // Italie
            zoom = 6f
        )
        
        "pt" to "PT" -> PickerRegion(
            center = LatLng(39.3999, -8.2245), // Portugal
            zoom = 7f
        )
        
        "pt" to "BR" -> PickerRegion(
            center = LatLng(-14.2350, -51.9253), // Brésil
            zoom = 4f
        )
        
        "nl" to "NL" -> PickerRegion(
            center = LatLng(52.1326, 5.2913), // Pays-Bas
            zoom = 7f
        )
        
        "ja" to "JP" -> PickerRegion(
            center = LatLng(36.2048, 138.2529), // Japon
            zoom = 5f
        )
        
        "zh" to "CN" -> PickerRegion(
            center = LatLng(35.8617, 104.1954), // Chine
            zoom = 4f
        )
        
        "ko" to "KR" -> PickerRegion(
            center = LatLng(35.9078, 127.7669), // Corée du Sud
            zoom = 7f
        )
        
        "ru" to "RU" -> PickerRegion(
            center = LatLng(61.5240, 105.3188), // Russie
            zoom = 3f
        )
        
        else -> PickerRegion(
            center = LatLng(20.0, 0.0), // Vue monde par défaut selon rapport
            zoom = 2f // Zoom très éloigné pour vue globale selon rapport
        )
    }
}
