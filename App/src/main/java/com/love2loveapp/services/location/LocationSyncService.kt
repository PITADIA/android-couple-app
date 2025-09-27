package com.love2loveapp.services.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.love2loveapp.models.UserLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 📍 LocationSyncService - Synchronisation localisation utilisateur avec Firebase
 * 
 * Fonctionnalités:
 * - Récupération localisation GPS via FusedLocationProvider
 * - Sauvegarde automatique dans Firestore
 * - Gestion permissions et erreurs
 * - Cache intelligent anti-spam
 */
class LocationSyncService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationSyncService"
        private const val USERS_COLLECTION = "users"
        private const val MIN_UPDATE_INTERVAL_MS = 30_000L // 30 secondes minimum entre updates
        private const val MIN_DISTANCE_METERS = 50f // 50 mètres minimum
        
        @Volatile
        private var instance: LocationSyncService? = null
        
        fun getInstance(context: Context): LocationSyncService {
            return instance ?: synchronized(this) {
                instance ?: LocationSyncService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Services
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // État
    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()
    
    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()
    
    // Location callback
    private var locationCallback: LocationCallback? = null
    private var lastUpdateTime = 0L
    private var lastSavedLocation: Location? = null
    
    /**
     * 🚀 Démarre la synchronisation localisation
     */
    fun startLocationSync() {
        Log.d(TAG, "🔄 TENTATIVE démarrage LocationSync...")
        Log.d(TAG, "  - Auth état: ${if (auth.currentUser != null) "CONNECTÉ" else "NON CONNECTÉ"}")
        Log.d(TAG, "  - Permission: ${if (hasLocationPermission()) "ACCORDÉE" else "REFUSÉE"}")
        
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "⚠️ PROBLÈME: Utilisateur non connecté, tentative avec listener Auth...")
            
            // 🆕 SOLUTION: Écouter l'authentification Firebase
            startAuthListener()
            return
        }
        
        if (!hasLocationPermission()) {
            Log.w(TAG, "⚠️ Permission localisation non accordée")
            _isLocationEnabled.value = false
            return
        }
        
        // Utilisateur connecté et permissions OK - démarrer immédiatement
        Log.d(TAG, "✅ Utilisateur DÉJÀ connecté - démarrage immédiat")
        startLocationSyncWithAuth(currentUserId)
    }
    
    /**
     * 📍 Traite une nouvelle localisation
     */
    private fun handleLocationUpdate(location: Location, userId: String) {
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "🆕 NOUVELLE LOCALISATION REÇUE:")
        Log.d(TAG, "  - userId: $userId")
        Log.d(TAG, "  - latitude: ${location.latitude}")
        Log.d(TAG, "  - longitude: ${location.longitude}")
        Log.d(TAG, "  - précision: ${location.accuracy}m")
        
        // Éviter le spam d'updates
        if (shouldSkipLocationUpdate(location, now)) {
            Log.d(TAG, "  ⏭️ Update ignorée (rate limiting ou distance insuffisante)")
            return
        }
        
        val userLocation = UserLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            address = null, // TODO: Geocoding si nécessaire
            city = null,
            country = null,
            lastUpdated = now
        )
        
        Log.d(TAG, "  ✅ UserLocation créé: ${userLocation.displayName}")
        
        // Mettre à jour le StateFlow local
        _currentLocation.value = userLocation
        lastUpdateTime = now
        lastSavedLocation = location
        
        Log.d(TAG, "📍 Nouvelle localisation: ${location.latitude}, ${location.longitude}")
        
        // Sauvegarder dans Firestore
        saveLocationToFirestore(userLocation, userId)
    }
    
    /**
     * 💾 Sauvegarde la localisation dans Firestore
     */
    private fun saveLocationToFirestore(userLocation: UserLocation, userId: String) {
        Log.d(TAG, "💾 SAUVEGARDE FIRESTORE:")
        Log.d(TAG, "  - userId: $userId")
        Log.d(TAG, "  - userLocation: ${userLocation.displayName}")
        
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
                
                Log.d(TAG, "  📤 Envoi vers Firestore en cours...")
                
                db.collection(USERS_COLLECTION)
                    .document(userId)
                    .update("currentLocation", locationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Localisation sauvegardée dans Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Erreur sauvegarde localisation: ${e.message}")
                    }
                    
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sauvegarde localisation: ${e.message}", e)
            }
        }
    }
    
    /**
     * ⚡ Détermine si on doit ignorer cette update de localisation
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
     * 🔐 Vérifie les permissions localisation
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
    
    /**
     * ⏹️ Arrête la synchronisation localisation
     */
    fun stopLocationSync() {
        Log.d(TAG, "⏹️ Arrêt synchronisation localisation")
        
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        _isLocationEnabled.value = false
    }
    
    /**
     * 📍 Force une update localisation immédiate
     */
    fun requestImmediateLocationUpdate() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "⚠️ Permission localisation nécessaire")
            return
        }
        
        val currentUserId = auth.currentUser?.uid ?: return
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { 
                    handleLocationUpdate(it, currentUserId)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Erreur récupération localisation immédiate: ${e.message}")
        }
    }
    
    // Listener Firebase Auth pour démarrer le service quand l'utilisateur se connecte
    private var authListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null
    
    /**
     * 🔐 Démarre l'écoute de l'authentification Firebase
     */
    private fun startAuthListener() {
        Log.d(TAG, "🔐 DÉMARRAGE listener Firebase Auth...")
        
        if (authListener == null) {
            authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                
                if (user != null) {
                    Log.d(TAG, "✅ AUTH LISTENER: Utilisateur connecté détecté: ${user.uid}")
                    Log.d(TAG, "🚀 RELANCE LocationSync maintenant que l'auth est OK...")
                    
                    // Arrêter le listener et relancer le service
                    stopAuthListener()
                    
                    // Petite attente pour être sûr que l'auth est stable
                    serviceScope.launch {
                        kotlinx.coroutines.delay(500) // 500ms de sécurité
                        startLocationSyncWithAuth(user.uid)
                    }
                } else {
                    Log.d(TAG, "❌ AUTH LISTENER: Aucun utilisateur connecté")
                }
            }
            
            auth.addAuthStateListener(authListener!!)
            Log.d(TAG, "👂 Firebase Auth listener ajouté")
        }
    }
    
    /**
     * 🛑 Arrête l'écoute de l'authentification Firebase
     */
    private fun stopAuthListener() {
        authListener?.let { listener ->
            auth.removeAuthStateListener(listener)
            authListener = null
            Log.d(TAG, "🛑 Firebase Auth listener supprimé")
        }
    }
    
    /**
     * 🚀 Démarre la synchronisation avec utilisateur authentifié
     */
    private fun startLocationSyncWithAuth(userId: String) {
        Log.d(TAG, "🚀 DÉMARRAGE AVEC AUTH: $userId")
        
        if (!hasLocationPermission()) {
            Log.w(TAG, "⚠️ Permission localisation toujours non accordée")
            _isLocationEnabled.value = false
            return
        }
        
        Log.d(TAG, "✅ Permissions OK - Démarrage effectif de la sync localisation")
        
        val locationRequest = LocationRequest.create().apply {
            interval = 60000 // 1 minute
            fastestInterval = MIN_UPDATE_INTERVAL_MS
            smallestDisplacement = MIN_DISTANCE_METERS
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location, userId)
                }
            }
            
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                _isLocationEnabled.value = locationAvailability.isLocationAvailable
                Log.d(TAG, "📍 Disponibilité localisation: ${locationAvailability.isLocationAvailable}")
            }
        }
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "❌ Permissions localisation manquantes au final")
                return
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            // Récupérer localisation actuelle immédiatement
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { 
                    handleLocationUpdate(it, userId)
                }
            }
            
            _isLocationEnabled.value = true
            Log.d(TAG, "✅ Synchronisation localisation EFFECTIVEMENT démarrée!")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Erreur permissions localisation: ${e.message}")
            _isLocationEnabled.value = false
        }
    }
    
    /**
     * 🧹 Nettoyage des ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage LocationSyncService")
        stopAuthListener() // ← 🆕 Arrêter le listener auth aussi
        stopLocationSync()
        serviceScope.cancel()
    }
}
