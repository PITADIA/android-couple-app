// LocationService.kt
@file:Suppress("MissingPermission")

package com.love2loveapp.core.services.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.love2loveapp.model.AppConstants

/**
 * Équivalent Android du LocationService Swift.
 * - Démarre/arrête les mises à jour si l'utilisateur est connecté + permissions OK
 * - Reverse geocoding (adresse/ville/pays)
 * - Seuil d'ignore < 100 m
 * - Sauvegarde Firestore: users/{uid}.currentLocation
 * - Analytics: "localisation_utilisee"
 */
class LocationService private constructor(
    private val appContext: Context
) {

    companion object {
        private const val TAG = "LocationService"
        private const val MANUAL_UPDATE_INTERVAL_MS = AppConstants.Location.UPDATE_INTERVAL_MS
        private const val IGNORE_DISTANCE_METERS = AppConstants.Location.IGNORE_DISTANCE_METERS

        @Volatile private var INSTANCE: LocationService? = null
        fun getInstance(context: Context): LocationService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationService(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ---- États observables (à brancher sur ViewModel si besoin) ----
    @Volatile var currentLocation: UserLocation? = null
        private set

    @Volatile var authorizationStatus: AuthorizationStatus = AuthorizationStatus.NOT_DETERMINED
        private set

    @Volatile var isUpdatingLocation: Boolean = false
        private set

    // ---- Firebase ----
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val analytics = Firebase.analytics

    // ---- Google Location Services ----
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    private val locationRequest: LocationRequest =
        LocationRequest.Builder(MANUAL_UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(AppConstants.Location.MIN_UPDATE_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY) // ~100m
            .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }
    }

    // ---- Timer 30 min pour déclencher une demande active (comme iOS) ----
    private val handler = Handler(Looper.getMainLooper())
    private val timerActive = AtomicBoolean(false)
    private val tickRunnable = object : Runnable {
        override fun run() {
            requestOneShotLocation()
            if (timerActive.get()) handler.postDelayed(this, MANUAL_UPDATE_INTERVAL_MS)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastSavedLocation: UserLocation? = null

    init {
        // 👤 Observateur Auth: start/stop selon connexion
        auth.addAuthStateListener {
            if (auth.currentUser != null) {
                startLocationUpdatesIfAuthorized()
            } else {
                stopLocationUpdates()
            }
        }
        // Init du statut permission
        authorizationStatus = computeAuthorizationStatus()
        Log.i(TAG, "Service initialisé - Statut: ${statusDescription(authorizationStatus)}")
        Log.i(TAG, "Config Android : PRIORITY_BALANCED_POWER_ACCURACY (~100m)")
    }

    // ---------- Permissions ----------
    fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

    fun requestLocationPermission(activity: Activity, requestCode: Int = AppConstants.Location.LOCATION_REQUEST_CODE) {
        Log.i(TAG, "Demande de permission de localisation")
        val toAsk = buildList {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (toAsk.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, toAsk.toTypedArray(), requestCode)
        }
    }

    // ---------- Public API ----------
    fun startLocationUpdatesIfAuthorized() {
        authorizationStatus = computeAuthorizationStatus()

        if (!hasForegroundPermission()) {
            Log.w(TAG, "Permission non accordée - Statut: ${statusDescription(authorizationStatus)}")
            return
        }
        if (auth.currentUser == null) {
            Log.w(TAG, "Aucun utilisateur connecté - Arrêt des mises à jour")
            return
        }

        if (!isUpdatingLocation) {
            Log.i(TAG, "Démarrage des mises à jour de localisation")
            isUpdatingLocation = true
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            startTimer()
        }
    }

    fun stopLocationUpdates() {
        Log.i(TAG, "Arrêt des mises à jour de localisation")
        isUpdatingLocation = false
        fusedClient.removeLocationUpdates(locationCallback)
        stopTimer()
    }

    // ---------- Interne ----------
    private fun startTimer() {
        if (timerActive.compareAndSet(false, true)) {
            handler.postDelayed(tickRunnable, MANUAL_UPDATE_INTERVAL_MS)
        }
    }

    private fun stopTimer() {
        timerActive.set(false)
        handler.removeCallbacks(tickRunnable)
    }

    private fun requestOneShotLocation() {
        if (!hasForegroundPermission()) return
        Log.i(TAG, "Demande de mise à jour manuelle de localisation")
        val cts = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) handleNewLocation(loc)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur getCurrentLocation: ${e.message}")
                isUpdatingLocation = false
            }
    }

    private fun handleNewLocation(location: Location) {
        val deviceModel = Build.MODEL ?: "Unknown"
        Log.i(TAG, "Nouvelle localisation reçue")
        Log.i(TAG, "Appareil: $deviceModel, Android: ${Build.VERSION.RELEASE}")
        Log.i(TAG, "Précision: ${location.accuracy}m, Âge: ${ageSeconds(location)}s")

        scope.launch {
            val userLocation = resolveAddress(location)
            analytics.logEvent("localisation_utilisee", null)
            Log.i(TAG, "Événement Firebase: localisation_utilisee")
            saveLocationToFirebase(userLocation)
        }
    }

    private fun ageSeconds(l: Location): Long {
        val now = System.currentTimeMillis()
        val t = if (Build.VERSION.SDK_INT >= 17) l.elapsedRealtimeNanos / 1_000_000 else l.time
        return kotlin.math.abs((now - t) / 1000L)
    }

    private suspend fun resolveAddress(loc: Location): UserLocation = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                Log.w(TAG, "Geocoder non présent - coordonnées uniquement")
                return@withContext UserLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )
            }
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val list = if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            }
            val a = list?.firstOrNull()
            if (a != null) {
                val addressLine = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
                Log.i(TAG, "Adresse résolue avec succès")
                UserLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    address = addressLine.ifBlank { null },
                    city = a.locality,
                    country = a.countryName
                )
            } else {
                Log.i(TAG, "Adresse non résolue - coordonnées uniquement")
                UserLocation(latitude = loc.latitude, longitude = loc.longitude)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding échoué: ${e.message}")
            UserLocation(latitude = loc.latitude, longitude = loc.longitude)
        }
    }

    private fun computeAuthorizationStatus(): AuthorizationStatus {
        val fg = hasForegroundPermission()
        val bg = hasBackgroundPermission()
        return when {
            !fg -> AuthorizationStatus.DENIED_OR_RESTRICTED
            fg && bg -> AuthorizationStatus.GRANTED_BACKGROUND
            fg -> AuthorizationStatus.GRANTED_FOREGROUND
            else -> AuthorizationStatus.UNKNOWN
        }
    }

    private fun statusDescription(status: AuthorizationStatus): String {
        return when (status) {
            AuthorizationStatus.NOT_DETERMINED ->
                appContext.getString(R.string.location_status_not_determined)
            AuthorizationStatus.DENIED_OR_RESTRICTED ->
                appContext.getString(R.string.location_status_restricted)
            AuthorizationStatus.GRANTED_FOREGROUND ->
                appContext.getString(R.string.location_status_when_in_use_authorized)
            AuthorizationStatus.GRANTED_BACKGROUND ->
                appContext.getString(R.string.location_status_always_authorized)
            AuthorizationStatus.UNKNOWN ->
                appContext.getString(R.string.location_status_unknown)
        }
    }

    private fun shouldIgnore(newLoc: UserLocation): Boolean {
        val last = lastSavedLocation ?: return false
        val d = last.distanceToMeters(newLoc)
        if (d < IGNORE_DISTANCE_METERS) {
            Log.i(TAG, "Localisation similaire ignorée (distance: ${d.toInt()}m)")
            return true
        }
        return false
    }

    private fun saveLocationToFirebase(location: UserLocation) {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "Aucun utilisateur connecté pour sauvegarder la localisation")
            return
        }
        if (shouldIgnore(location)) return

        isUpdatingLocation = true
        Log.i(TAG, "Sauvegarde nouvelle localisation en Firebase")

        val payload = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "address" to location.address,
            "city" to location.city,
            "country" to location.country,
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        val userRef = db.collection("users").document(user.uid)
        userRef.set(mapOf("currentLocation" to payload), SetOptions.merge())
            .addOnSuccessListener {
                isUpdatingLocation = false
                currentLocation = location
                lastSavedLocation = location
                Log.i(TAG, "Localisation sauvegardée avec succès")
            }
            .addOnFailureListener { e ->
                isUpdatingLocation = false
                Log.e(TAG, "Échec de la sauvegarde de localisation: ${e.message}")
            }
    }
}

// --------- Modèles & utilitaires ---------

enum class AuthorizationStatus {
    NOT_DETERMINED,
    DENIED_OR_RESTRICTED,
    GRANTED_FOREGROUND,
    GRANTED_BACKGROUND,
    UNKNOWN
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
) {
    /** Distance en mètres jusqu’à [other] (utilise l’API Android Location). */
    fun distanceToMeters(other: UserLocation): Double {
        val a = FloatArray(1)
        android.location.Location.distanceBetween(
            latitude, longitude,
            other.latitude, other.longitude,
            a
        )
        return a[0].toDouble()
    }
}
