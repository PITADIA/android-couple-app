package com.love2loveapp.services.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.love2loveapp.models.AppState
import com.love2loveapp.models.UserLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * 🌍 UnifiedLocationService - Service unifié robuste comme iOS
 * 
 * Reproduit l'architecture iOS LocationService.swift avec :
 * - Gestion automatique des permissions
 * - Récupération GPS avec retry
 * - Géocodage inversé
 * - Sauvegarde Firebase automatique
 * - Synchronisation AppState temps réel
 * - Gestion d'erreurs robuste
 */
class UnifiedLocationService private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "UnifiedLocationService"
        private const val MIN_UPDATE_INTERVAL_MS = 30_000L // 30 secondes minimum
        private const val MIN_DISTANCE_METERS = 50f // 50 mètres minimum
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L // 5 secondes entre tentatives
        
        @Volatile
        private var INSTANCE: UnifiedLocationService? = null
        
        /**
         * 📦 Méthode getInstance pour le pattern Singleton (comme autres services)
         */
        fun getInstance(context: Context): UnifiedLocationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedLocationService(context.applicationContext).also { 
                    INSTANCE = it
                    Log.d(TAG, "🏭 Instance UnifiedLocationService créée")
                }
            }
        }
    }
    
    // Services Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val analytics = FirebaseAnalytics.getInstance(context)
    
    // Services Google Play
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    // Coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // État publique (comme iOS @Published)
    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()
    
    private val _isUpdatingLocation = MutableStateFlow(false)
    val isUpdatingLocation: StateFlow<Boolean> = _isUpdatingLocation.asStateFlow()
    
    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()
    
    // État interne
    private var locationCallback: LocationCallback? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var lastUpdateTime = 0L
    private var lastSavedLocation: Location? = null
    private var retryCount = 0
    private var isServiceActive = false
    
    // Référence AppState (sera injectée)
    private var appState: AppState? = null
    
    /**
     * 🔐 Injection AppState (appelé par AppDelegate après création)
     */
    fun setAppState(appState: AppState) {
        this.appState = appState
        Log.d(TAG, "✅ AppState injecté dans UnifiedLocationService")
    }
    
    /**
     * 🚀 Démarrage automatique robuste (équivalent iOS requestLocationUpdate)
     */
    fun startAutomatic() {
        Log.d(TAG, "🚀 DÉMARRAGE AUTOMATIQUE UnifiedLocationService")
        
        if (isServiceActive) {
            Log.d(TAG, "⚡ Service déjà actif, skip")
            return
        }
        
        // Marquer service comme actif
        isServiceActive = true
        retryCount = 0
        
        // Démarrer l'écoute Firebase Auth
        startAuthStateListener()
        
        // Tenter démarrage immédiat si conditions réunies
        attemptLocationUpdate()
    }
    
    /**
     * 🔐 Écoute état authentification Firebase (comme iOS auth state)
     */
    private fun startAuthStateListener() {
        Log.d(TAG, "🔐 Configuration listener Firebase Auth")
        
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            
            if (user != null) {
                Log.d(TAG, "✅ Utilisateur authentifié détecté: ${user.uid}")
                // Tenter démarrage GPS maintenant que l'auth est OK
                serviceScope.launch {
                    delay(1000) // Attendre stabilisation auth
                    attemptLocationUpdate()
                }
            } else {
                Log.d(TAG, "❌ Utilisateur non authentifié, arrêt service")
                stopLocationUpdates()
            }
        }
        
        auth.addAuthStateListener(authListener!!)
    }
    
    /**
     * 📍 Tentative récupération localisation (équivalent iOS startLocationUpdate)
     */
    private fun attemptLocationUpdate() {
        Log.d(TAG, "🔄 TENTATIVE récupération localisation")
        Log.d(TAG, "  - Auth état: ${if (auth.currentUser != null) "CONNECTÉ" else "NON CONNECTÉ"}")
        Log.d(TAG, "  - Permission: ${if (hasLocationPermission()) "ACCORDÉE" else "REFUSÉE"}")
        Log.d(TAG, "  - Retry count: $retryCount/$MAX_RETRY_ATTEMPTS")
        
        // Vérifier authentification
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "⚠️ Utilisateur non authentifié, attente listener auth...")
            return
        }
        
        // Vérifier permissions
        if (!hasLocationPermission()) {
            Log.w(TAG, "⚠️ Permission localisation non accordée")
            _locationError.value = "Permission localisation requise"
            
            // Programmer retry après délai
            scheduleRetry("Permission manquante")
            return
        }
        
        // Démarrer récupération GPS
        startLocationUpdates(currentUserId)
    }
    
    /**
     * 📡 Démarrage récupération GPS (équivalent iOS locationManager.requestLocation)
     */
    private fun startLocationUpdates(userId: String) {
        if (!hasLocationPermission()) {
            Log.e(TAG, "❌ Permission refusée au moment du démarrage GPS")
            return
        }
        
        Log.d(TAG, "📡 Démarrage récupération GPS pour: $userId")
        _isUpdatingLocation.value = true
        _locationError.value = null
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .setMaxUpdates(1) // Une seule mesure comme iOS
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                
                val location = locationResult.lastLocation ?: return
                Log.d(TAG, "📍 NOUVELLE LOCALISATION REÇUE!")
                
                _isUpdatingLocation.value = false
                retryCount = 0 // Reset retry count sur succès
                
                // Traiter la localisation (équivalent iOS didUpdateLocations)
                handleLocationSuccess(location, userId)
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.d(TAG, "❌ GPS non disponible")
                    _isUpdatingLocation.value = false
                    scheduleRetry("GPS non disponible")
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            // Également essayer lastLocation immédiatement
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { 
                    Log.d(TAG, "📍 Localisation depuis cache reçue")
                    handleLocationSuccess(it, userId)
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "❌ Erreur récupération lastLocation: ${error.message}")
                scheduleRetry("Erreur GPS: ${error.message}")
            }
            
            Log.d(TAG, "✅ Demande GPS envoyée")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException GPS: ${e.message}")
            _isUpdatingLocation.value = false
            scheduleRetry("Erreur sécurité GPS")
        }
    }
    
    /**
     * ✅ Traitement succès localisation (équivalent iOS handleLocationUpdate)
     */
    private fun handleLocationSuccess(location: Location, userId: String) {
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "🎉 TRAITEMENT LOCALISATION RÉUSSIE:")
        Log.d(TAG, "  - Latitude: ${location.latitude}")
        Log.d(TAG, "  - Longitude: ${location.longitude}")
        Log.d(TAG, "  - Précision: ${location.accuracy}m")
        
        // Éviter spam d'updates
        if (shouldSkipLocationUpdate(location, now)) {
            Log.d(TAG, "⏭️ Update ignorée (rate limiting)")
            return
        }
        
        // Arrêter les updates GPS (une seule mesure comme iOS)
        stopLocationUpdates()
        
        // Démarrer géocodage inversé (équivalent iOS reverseGeocodeLocation)
        startReverseGeocoding(location, userId)
    }
    
    /**
     * 🗺️ Géocodage inversé (équivalent iOS CLGeocoder.reverseGeocodeLocation)
     */
    private fun startReverseGeocoding(location: Location, userId: String) {
        Log.d(TAG, "🗺️ Démarrage géocodage inversé...")
        
        serviceScope.launch {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API moderne
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            continuation.resume(addresses) {}
                        }
                    }
                } else {
                    // API legacy
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                }
                
                handleGeocodingResult(location, addresses, userId)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur géocodage", e)
                handleGeocodingResult(location, emptyList(), userId)
            }
        }
    }
    
    /**
     * 📍 Traitement résultat géocodage (équivalent iOS handleGeocodingResult)
     */
    private fun handleGeocodingResult(location: Location, addresses: List<Address>?, userId: String) {
        val userLocation = if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                address = "${address.thoroughfare ?: ""} ${address.subThoroughfare ?: ""}".trim(),
                city = address.locality,
                country = address.countryName,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        Log.d(TAG, "📍 UserLocation créé: ${userLocation.displayName}")
        
        // 1. Mettre à jour StateFlow local (équivalent iOS @Published)
        _currentLocation.value = userLocation
        lastUpdateTime = System.currentTimeMillis()
        lastSavedLocation = location
        
        // 2. Mettre à jour AppState IMMÉDIATEMENT (équivalent iOS appState sync)
        mainScope.launch {
            updateAppStateLocation(userLocation)
        }
        
        // 3. Sauvegarder Firebase (équivalent iOS saveLocationToFirebase)
        saveLocationToFirebase(userLocation, userId)
        
        // 4. Analytics (équivalent iOS)
        analytics.logEvent("localisation_utilisee", null)
        
        Log.d(TAG, "✅ Localisation traitée avec succès - Chaîne complète!")
    }
    
    /**
     * 📱 Mise à jour AppState (équivalent iOS currentUser.currentLocation sync)
     */
    private fun updateAppStateLocation(userLocation: UserLocation) {
        appState?.let { state ->
            Log.d(TAG, "📱 Mise à jour AppState avec nouvelle localisation")
            state.updateUserLocation(userLocation)
        } ?: Log.w(TAG, "⚠️ AppState non disponible pour mise à jour")
    }
    
    /**
     * 💾 Sauvegarde Firebase (équivalent iOS FirebaseService.updateUserLocation)
     */
    private fun saveLocationToFirebase(userLocation: UserLocation, userId: String) {
        Log.d(TAG, "💾 SAUVEGARDE FIREBASE:")
        Log.d(TAG, "  - userId: $userId")
        Log.d(TAG, "  - location: ${userLocation.displayName}")
        
        serviceScope.launch {
            try {
                val locationData = mapOf(
                    "latitude" to userLocation.latitude,
                    "longitude" to userLocation.longitude,
                    "address" to userLocation.address,
                    "city" to userLocation.city,
                    "country" to userLocation.country,
                    "lastUpdated" to userLocation.lastUpdated
                )
                
                db.collection("users")
                    .document(userId)
                    .update("currentLocation", locationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Localisation sauvegardée dans Firebase")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Erreur sauvegarde Firebase: ${e.message}")
                    }
                    
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sauvegarde Firebase: ${e.message}", e)
            }
        }
    }
    
    /**
     * ⚡ Programmation retry automatique (robustesse)
     */
    private fun scheduleRetry(reason: String) {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "❌ Nombre maximum de tentatives atteint ($MAX_RETRY_ATTEMPTS)")
            _locationError.value = "Impossible de récupérer la localisation: $reason"
            return
        }
        
        retryCount++
        Log.d(TAG, "⏰ Programmation retry #$retryCount dans ${RETRY_DELAY_MS}ms - Raison: $reason")
        
        serviceScope.launch {
            delay(RETRY_DELAY_MS)
            attemptLocationUpdate()
        }
    }
    
    /**
     * 📍 Force une update immédiate (pour boutons UI)
     */
    fun requestImmediateUpdate() {
        Log.d(TAG, "🆘 Demande update immédiate")
        retryCount = 0 // Reset retry
        attemptLocationUpdate()
    }
    
    /**
     * ⏹️ Arrêt updates GPS
     */
    private fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
            Log.d(TAG, "⏹️ Updates GPS arrêtés")
        }
    }
    
    /**
     * 🛑 Arrêt complet service
     */
    fun stop() {
        Log.d(TAG, "🛑 Arrêt UnifiedLocationService")
        
        isServiceActive = false
        
        // Arrêter GPS
        stopLocationUpdates()
        
        // Arrêter listener auth
        authListener?.let { listener ->
            auth.removeAuthStateListener(listener)
            authListener = null
        }
        
        // Annuler coroutines
        serviceScope.cancel()
        mainScope.cancel()
        
        // Reset état
        _isUpdatingLocation.value = false
        _locationError.value = null
    }
    
    /**
     * ⚡ Détermine si on doit ignorer cette update
     */
    private fun shouldSkipLocationUpdate(location: Location, now: Long): Boolean {
        // Temps minimum écoulé
        if (now - lastUpdateTime < MIN_UPDATE_INTERVAL_MS) {
            return true
        }
        
        // Distance minimum parcourue
        lastSavedLocation?.let { lastLoc ->
            val distance = location.distanceTo(lastLoc)
            if (distance < MIN_DISTANCE_METERS) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 🔐 Vérification permissions localisation
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
