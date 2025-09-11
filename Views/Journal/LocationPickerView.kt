@file:Suppress("UnusedImport")

package your.package.name

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.MyLocation
import androidx.compose.material3.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.withContext
import java.util.Locale

// --- Data model Android équivalent à ton JournalLocation Swift ---
data class JournalLocation(
    val coordinate: LatLng,
    val address: String?,
    val city: String?,
    val country: String?
) {
    val displayName: String
        get() = listOfNotNull(address, city, country).joinToString(", ").ifBlank { "-" }
}

// --- Couleurs utilisées (approx. iOS) ---
private val Pink = Color(0xFFFD267A)
private val DeepBackground = Color(0xFF19050C) // ≈ (0.1, 0.02, 0.05)

/**
 * LocationPicker composable
 *
 * @param initialSelectedLocation localisation déjà choisie (optionnelle)
 * @param onLocationSelected callback renvoyant la localisation choisie
 * @param onDismiss fermeture de l’écran
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initialSelectedLocation: JournalLocation? = null,
    onLocationSelected: (JournalLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Permissions
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            // On peut tenter de centrer la carte sur la position actuelle
            centerOnCurrentLocation(context, fusedClient, cameraPositionState)
        }
    }

    // Camera state
    val (defaultLatLng, defaultZoom) = remember {
        if (initialSelectedLocation != null) {
            initialSelectedLocation.coordinate to 12f
        } else {
            defaultLatLngForLocale(context) // Pair<LatLng, Float>
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, defaultZoom)
    }

    // État sélection + nom affiché (reverse geocode)
    var selectedLatLng by remember { mutableStateOf<LatLng?>(initialSelectedLocation?.coordinate) }
    var currentLocationName by remember { mutableStateOf(initialSelectedLocation?.displayName ?: "") }

    // Si permission accordée à l’ouverture, tenter une recadrage doux sur la position actuelle
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && initialSelectedLocation == null) {
            centerOnCurrentLocation(context, fusedClient, cameraPositionState)
        }
    }

    // Debounce des déplacements de caméra -> reverse geocode
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.target }
            .distinctUntilChanged()
            .debounce(500) // ≈ Timer 0.5s côté iOS
            .collectLatest { target ->
                selectedLatLng = target
                val info = reverseGeocode(context, target)
                currentLocationName = info?.let {
                    listOfNotNull(it.address, it.city, it.country).joinToString(", ")
                }.takeUnless { it.isNullOrBlank() }
                    ?: context.getString(R.string.custom_location)
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.choose_location),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (hasLocationPermission) {
                                centerOnCurrentLocation(context, fusedClient, cameraPositionState)
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My Location",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBackground)
                .padding(padding)
        ) {
            // --- Google Map ---
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                ),
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission
                )
            )

            // --- Marqueur centré (overlay) ---
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Center Pin",
                        tint = Pink
                    )
                }
            }

            // --- Panneau d’info en bas (nom du lieu sélectionné) ---
            if (currentLocationName.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.selected_location),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = currentLocationName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // --- Bouton "Sélectionner" ---
                    val enabled = selectedLatLng != null
                    Button(
                        onClick = {
                            val latLng = selectedLatLng ?: return@Button
                            // Reverser une dernière fois pour remplir address/city/country
                            // (en cas de mise à jour tardive)
                            LaunchedEffect(latLng) {
                                val addr = reverseGeocode(context, latLng)
                                val jl = JournalLocation(
                                    coordinate = latLng,
                                    address = addr?.address ?: currentLocationName,
                                    city = addr?.city,
                                    country = addr?.country
                                )
                                onLocationSelected(jl)
                                onDismiss()
                            }
                        },
                        enabled = enabled,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (enabled) Pink else Color.Gray.copy(alpha = 0.3f),
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.select),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// --- Helpers ---

private fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

private fun defaultLatLngForLocale(context: Context): Pair<LatLng, Float> {
    val locale = Locale.getDefault()
    val lang = locale.language.lowercase(Locale.ROOT)
    val region = (locale.country ?: "").uppercase(Locale.ROOT)

    return when {
        // États-Unis
        lang == "en" && region == "US" -> LatLng(39.8283, -98.5795) to 3.5f
        // Canada
        (lang in listOf("en", "fr") && region == "CA") -> LatLng(56.1304, -106.3468) to 3.5f
        // Royaume-Uni
        lang == "en" && region == "GB" -> LatLng(55.3781, -3.4360) to 5.5f
        // Australie
        lang == "en" && region == "AU" -> LatLng(-25.2744, 133.7751) to 3.5f
        // France (toute variante fr-*)
        lang == "fr" -> LatLng(46.2276, 2.2137) to 5.5f
        // Espagne
        lang == "es" && region == "ES" -> LatLng(40.4637, -3.7492) to 5.5f
        // Allemagne
        lang == "de" && region == "DE" -> LatLng(51.1657, 10.4515) to 5.5f
        // Autres pays européens fréquents
        region in setOf("BE", "NL", "CH", "AT", "PT", "DK", "SE", "NO", "FI", "IT") ->
            LatLng(54.5260, 15.2551) to 4.0f // Europe
        // Vue monde par défaut
        else -> LatLng(20.0, 0.0) to 2.0f
    }
}

private suspend fun reverseGeocode(
    context: Context,
    latLng: LatLng
): AddressParts? = withContext(Dispatchers.IO) {
    runCatching {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addrs = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        val a = addrs?.firstOrNull() ?: return@withContext null
        AddressParts(
            address = a.thoroughfare ?: a.featureName, // nom de rue / point d’intérêt
            city = a.locality ?: a.subAdminArea ?: a.adminArea,
            country = a.countryName
        )
    }.getOrNull()
}

private data class AddressParts(
    val address: String?,
    val city: String?,
    val country: String?
)

@Composable
private fun centerOnCurrentLocation(
    context: Context,
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
    cameraPositionState: CameraPositionState
) {
    // Utilise lastLocation (rapide, non bloquant). Pour plus de précision, tu peux utiliser getCurrentLocation.
    if (!hasFineLocationPermission(context)) return
    fusedClient.lastLocation.addOnSuccessListener { loc ->
        loc?.let {
            val target = LatLng(it.latitude, it.longitude)
            // Animation douce
            cameraPositionState.move(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(target, 12f)
                )
            )
        }
    }
}
