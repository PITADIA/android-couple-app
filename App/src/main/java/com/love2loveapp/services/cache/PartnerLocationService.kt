package com.love2loveapp.services.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * 🗺️ PartnerLocationService Android - Cache Temporel Sophistiqué
 * 
 * Architecture équivalente iOS PartnerLocationService:
 * - StateFlow → @Published iOS
 * - SharedPreferences → UserDefaults iOS  
 * - Cache temporel multi-niveau → lastFetchTime iOS
 * - Cloud Functions → Firebase Functions iOS
 * - Éviter spam API → cacheValidityInterval iOS
 * - Performance optimisée → 2min données / 5s localisation iOS
 * - Équivalent complet du PartnerLocationService iOS
 */
class PartnerLocationService private constructor(
    private val context: Context,
    private val functions: FirebaseFunctions
) {
    
    companion object {
        private const val TAG = "PartnerLocationService"
        private const val PREFS_NAME = "partner_location_cache"
        
        // Constantes cache temporel (équivalent iOS)
        private const val CACHE_VALIDITY_INTERVAL_MS = 2 * 60 * 1000L      // 2 minutes données
        private const val LOCATION_CACHE_INTERVAL_MS = 5 * 1000L           // 5 secondes position
        
        // Clés SharedPreferences
        private const val KEY_PARTNER_NAME = "partner_name"
        private const val KEY_PARTNER_DISTANCE = "partner_distance"
        private const val KEY_PARTNER_DISTANCE_UNIT = "partner_distance_unit"
        private const val KEY_PARTNER_LOCATION_LAT = "partner_location_lat"
        private const val KEY_PARTNER_LOCATION_LNG = "partner_location_lng"
        private const val KEY_PARTNER_DATA_TIMESTAMP = "partner_data_timestamp"
        private const val KEY_PARTNER_LOCATION_TIMESTAMP = "partner_location_timestamp"
        
        @Volatile
        private var instance: PartnerLocationService? = null
        
        fun getInstance(context: Context): PartnerLocationService {
            return instance ?: synchronized(this) {
                instance ?: PartnerLocationService(
                    context.applicationContext,
                    FirebaseFunctions.getInstance()
                ).also { instance = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // États observables (équivalent @Published iOS)
    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName: StateFlow<String?> = _partnerName.asStateFlow()
    
    private val _currentDistance = MutableStateFlow<String?>(null)
    val currentDistance: StateFlow<String?> = _currentDistance.asStateFlow()
    
    private val _partnerLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val partnerLocation: StateFlow<Pair<Double, Double>?> = _partnerLocation.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Cache timestamps pour contrôler fréquence appels
    private var lastFetchTime = 0L
    private var lastLocationFetchTime = 0L
    
    // Scope pour coroutines
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        Log.d(TAG, "✅ PartnerLocationService initialisé avec cache temporel")
        
        // Charger données cachées au démarrage
        loadCachedData()
    }
    
    // =======================
    // CACHE MULTI-NIVEAU INTELLIGENT (équivalent iOS)
    // =======================
    
    /**
     * Récupère données partenaire avec cache intelligent
     * Équivalent de fetchPartnerDataViaCloudFunction() iOS
     */
    fun fetchPartnerData(partnerId: String, forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        
        // Vérifier cache pour éviter appels trop fréquents
        if (!forceRefresh && 
            now - lastFetchTime < CACHE_VALIDITY_INTERVAL_MS && 
            _partnerName.value != null) {
            
            Log.d(TAG, "🌍 Données partenaire en cache - Récupération localisation uniquement")
            fetchPartnerLocation(partnerId)
            return
        }
        
        Log.d(TAG, "🔄 Récupération données partenaire complètes...")
        
        serviceScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                lastFetchTime = now
                
                // Cloud Function getPartnerInfo
                val result = functions.getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()
                
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any>
                val success = data?.get("success") as? Boolean ?: false
                
                if (success) {
                    @Suppress("UNCHECKED_CAST")
                    val partnerInfo = data?.get("partnerInfo") as? Map<String, Any>
                    if (partnerInfo != null) {
                        updatePartnerData(partnerInfo)
                        
                        // Récupérer localisation immédiatement après
                        fetchPartnerLocation(partnerId)
                    }
                } else {
                    val message = data?.get("message") as? String ?: "Erreur inconnue"
                    _errorMessage.value = "Erreur récupération partenaire: $message"
                    Log.e(TAG, "❌ Cloud Function getPartnerInfo échouée: $message")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur Cloud Function getPartnerInfo: ${e.message}", e)
                _errorMessage.value = "Erreur de connexion: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Récupère uniquement la localisation partenaire
     * Équivalent de fetchPartnerLocationViaCloudFunction() iOS
     */
    private fun fetchPartnerLocation(partnerId: String) {
        val now = System.currentTimeMillis()
        
        if (now - lastLocationFetchTime < LOCATION_CACHE_INTERVAL_MS) {
            Log.d(TAG, "🌍 Localisation récemment récupérée - Attente")
            return
        }
        
        serviceScope.launch {
            try {
                lastLocationFetchTime = now
                
                Log.d(TAG, "📍 Récupération localisation partenaire...")
                
                // Cloud Function getPartnerLocation
                val result = functions.getHttpsCallable("getPartnerLocation")
                    .call(mapOf("partnerId" to partnerId))
                    .await()
                
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any>
                val success = data?.get("success") as? Boolean ?: false
                
                if (success && data?.containsKey("location") == true) {
                    @Suppress("UNCHECKED_CAST")
                    val locationData = data?.get("location") as? Map<String, Any>
                    if (locationData != null) {
                        updatePartnerLocation(locationData)
                    }
                } else {
                    val message = data?.get("message") as? String
                    Log.w(TAG, "⚠️ Localisation partenaire indisponible: $message")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur récupération localisation: ${e.message}", e)
                // Ne pas mettre d'erreur pour localisation - pas critique
            }
        }
    }
    
    // =======================
    // MISE À JOUR DONNÉES CACHE (privé)
    // =======================
    
    /**
     * Met à jour les données générales du partenaire
     * Équivalent de updatePartnerData() iOS
     */
    private fun updatePartnerData(partnerInfo: Map<String, Any>) {
        try {
            val name = partnerInfo["name"] as? String
            val profileImageURL = partnerInfo["profileImageURL"] as? String
            
            Log.d(TAG, "👤 Mise à jour données partenaire: $name")
            
            // Mettre à jour StateFlow
            _partnerName.value = name
            
            // Sauvegarder en cache SharedPreferences
            sharedPrefs.edit()
                .putString(KEY_PARTNER_NAME, name)
                .putLong(KEY_PARTNER_DATA_TIMESTAMP, System.currentTimeMillis())
                .apply()
                
            Log.d(TAG, "✅ Données partenaire mises en cache")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour données partenaire: ${e.message}", e)
        }
    }
    
    /**
     * Met à jour la localisation du partenaire
     * Équivalent de updatePartnerLocation() iOS
     */
    private fun updatePartnerLocation(locationData: Map<String, Any>) {
        try {
            val distance = locationData["distance"] as? String
            val unit = locationData["unit"] as? String ?: "km"
            val latitude = locationData["latitude"] as? Double
            val longitude = locationData["longitude"] as? Double
            
            Log.d(TAG, "📍 Mise à jour localisation partenaire: $distance$unit")
            
            // Mettre à jour StateFlow
            val fullDistance = if (distance != null) "$distance $unit" else null
            _currentDistance.value = fullDistance
            
            if (latitude != null && longitude != null) {
                _partnerLocation.value = Pair(latitude, longitude)
            }
            
            // Sauvegarder en cache SharedPreferences
            val editor = sharedPrefs.edit()
            editor.putString(KEY_PARTNER_DISTANCE, distance)
            editor.putString(KEY_PARTNER_DISTANCE_UNIT, unit)
            
            if (latitude != null && longitude != null) {
                editor.putFloat(KEY_PARTNER_LOCATION_LAT, latitude.toFloat())
                editor.putFloat(KEY_PARTNER_LOCATION_LNG, longitude.toFloat())
            }
            
            editor.putLong(KEY_PARTNER_LOCATION_TIMESTAMP, System.currentTimeMillis())
            editor.apply()
            
            Log.d(TAG, "✅ Localisation partenaire mise en cache")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour localisation: ${e.message}", e)
        }
    }
    
    // =======================
    // GESTION CACHE PERSISTANT (équivalent iOS)
    // =======================
    
    /**
     * Charge les données cachées au démarrage
     * Équivalent de loadCachedData() iOS
     */
    private fun loadCachedData() {
        try {
            Log.d(TAG, "📂 Chargement données cachées...")
            
            // Charger données générales
            val cachedName = sharedPrefs.getString(KEY_PARTNER_NAME, null)
            if (cachedName != null) {
                _partnerName.value = cachedName
                Log.d(TAG, "👤 Nom partenaire depuis cache: $cachedName")
            }
            
            // Charger distance
            val cachedDistance = sharedPrefs.getString(KEY_PARTNER_DISTANCE, null)
            val cachedUnit = sharedPrefs.getString(KEY_PARTNER_DISTANCE_UNIT, "km")
            if (cachedDistance != null) {
                val fullDistance = "$cachedDistance $cachedUnit"
                _currentDistance.value = fullDistance
                Log.d(TAG, "📍 Distance partenaire depuis cache: $fullDistance")
            }
            
            // Charger coordonnées
            if (sharedPrefs.contains(KEY_PARTNER_LOCATION_LAT) && 
                sharedPrefs.contains(KEY_PARTNER_LOCATION_LNG)) {
                
                val lat = sharedPrefs.getFloat(KEY_PARTNER_LOCATION_LAT, 0f).toDouble()
                val lng = sharedPrefs.getFloat(KEY_PARTNER_LOCATION_LNG, 0f).toDouble()
                
                if (lat != 0.0 && lng != 0.0) {
                    _partnerLocation.value = Pair(lat, lng)
                    Log.d(TAG, "🗺️ Coordonnées partenaire depuis cache: ($lat, $lng)")
                }
            }
            
            // Vérifier âge du cache
            val dataAge = System.currentTimeMillis() - sharedPrefs.getLong(KEY_PARTNER_DATA_TIMESTAMP, 0)
            val locationAge = System.currentTimeMillis() - sharedPrefs.getLong(KEY_PARTNER_LOCATION_TIMESTAMP, 0)
            
            Log.d(TAG, "⏰ Âge cache: données ${dataAge / 1000}s, localisation ${locationAge / 1000}s")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement cache: ${e.message}", e)
        }
    }
    
    /**
     * Vérifie si les données en cache sont valides
     */
    fun isCacheValid(): Boolean {
        val dataTimestamp = sharedPrefs.getLong(KEY_PARTNER_DATA_TIMESTAMP, 0)
        val age = System.currentTimeMillis() - dataTimestamp
        return age < CACHE_VALIDITY_INTERVAL_MS && _partnerName.value != null
    }
    
    /**
     * Vérifie si la localisation en cache est valide
     */
    fun isLocationCacheValid(): Boolean {
        val locationTimestamp = sharedPrefs.getLong(KEY_PARTNER_LOCATION_TIMESTAMP, 0)
        val age = System.currentTimeMillis() - locationTimestamp
        return age < LOCATION_CACHE_INTERVAL_MS && _currentDistance.value != null
    }
    
    // =======================
    // MÉTHODES UTILITAIRES (équivalent iOS)
    // =======================
    
    /**
     * Calcule distance approximative entre deux points
     * Utilise formule haversine simplifiée
     */
    fun calculateDistance(
        userLat: Double, 
        userLng: Double, 
        partnerLat: Double, 
        partnerLng: Double
    ): String {
        return try {
            val earthRadius = 6371.0 // Rayon terrestre en km
            
            val dLat = Math.toRadians(partnerLat - userLat)
            val dLng = Math.toRadians(partnerLng - userLng)
            
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(partnerLat)) *
                    Math.sin(dLng / 2) * Math.sin(dLng / 2)
                    
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            val distance = earthRadius * c
            
            when {
                distance < 1.0 -> "${(distance * 1000).toInt()} m"
                distance < 10.0 -> "${"%.1f".format(distance)} km"
                else -> "${distance.toInt()} km"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur calcul distance: ${e.message}")
            "? km"
        }
    }
    
    /**
     * Force refresh des données
     */
    fun forceRefresh(partnerId: String) {
        Log.d(TAG, "🔄 Force refresh données partenaire")
        lastFetchTime = 0L
        lastLocationFetchTime = 0L
        fetchPartnerData(partnerId, forceRefresh = true)
    }
    
    /**
     * Efface les erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    // =======================
    // NETTOYAGE ET DEBUG (équivalent iOS)
    // =======================
    
    /**
     * Vide le cache partenaire
     * Équivalent de clearCache() iOS
     */
    fun clearCache() {
        Log.d(TAG, "🗑️ Nettoyage cache partenaire")
        
        // Vider StateFlow
        _partnerName.value = null
        _currentDistance.value = null
        _partnerLocation.value = null
        _errorMessage.value = null
        
        // Vider SharedPreferences
        sharedPrefs.edit().clear().apply()
        
        // Reset timestamps
        lastFetchTime = 0L
        lastLocationFetchTime = 0L
    }
    
    /**
     * Informations de debug complètes
     */
    fun getDebugInfo(): String {
        val dataAge = System.currentTimeMillis() - sharedPrefs.getLong(KEY_PARTNER_DATA_TIMESTAMP, 0)
        val locationAge = System.currentTimeMillis() - sharedPrefs.getLong(KEY_PARTNER_LOCATION_TIMESTAMP, 0)
        
        return """
            📊 DEBUG PartnerLocationService:
            - Nom partenaire: ${_partnerName.value ?: "Non défini"}
            - Distance: ${_currentDistance.value ?: "Non définie"}
            - Localisation: ${_partnerLocation.value?.let { "(${it.first}, ${it.second})" } ?: "Non définie"}
            - Âge cache données: ${dataAge / 1000}s (valide: ${isCacheValid()})
            - Âge cache localisation: ${locationAge / 1000}s (valide: ${isLocationCacheValid()})
            - En chargement: ${_isLoading.value}
            - Erreur: ${_errorMessage.value ?: "Aucune"}
            - Intervalles cache: ${CACHE_VALIDITY_INTERVAL_MS / 1000}s / ${LOCATION_CACHE_INTERVAL_MS / 1000}s
        """.trimIndent()
    }
    
    /**
     * Nettoyage ressources (destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage PartnerLocationService")
        serviceScope.cancel()
    }
}
