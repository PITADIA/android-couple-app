package com.love2love.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

/**
 * Portage Android/Kotlin de PartnerLocationService (Swift).
 * - Cloud Functions utilisées : getPartnerInfo, getPartnerLocation
 * - Caching anti-rafale : 15s (infos), 5s (localisation)
 * - État exposé via StateFlow pour intégration Compose/ViewModel
 * - Localisation : utilisez strings.xml (ex: R.string.widget_together_text)
 *
 * ⚠️ Dépendances recommandées :
 * implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<version>")
 * implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:<version>")
 * implementation("com.google.firebase:firebase-functions-ktx:<version>")
 * implementation("com.google.firebase:firebase-firestore-ktx:<version>")
 */
class PartnerLocationService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        // Équivalent de `static let shared`
        val shared: PartnerLocationService by lazy { PartnerLocationService() }
        private const val TAG = "PartnerLocationService"
    }

    // --- State exposé (équivalent @Published) ---
    private val _partnerLocation = MutableStateFlow<UserLocation?>(null)
    val partnerLocation: StateFlow<UserLocation?> = _partnerLocation.asStateFlow()

    private val _partnerProfileImageURL = MutableStateFlow<String?>(null)
    val partnerProfileImageURL: StateFlow<String?> = _partnerProfileImageURL.asStateFlow()

    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName: StateFlow<String?> = _partnerName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- Internes ---
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var partnerListener: ListenerRegistration? = null // réservé si vous souhaitez écouter Firestore

    private var lastFetchMillis: Long = 0L
    private var lastLocationFetchMillis: Long = 0L
    private val cacheValidityMs: Long = 15_000L   // 15s (infos)
    private val locationCacheMs: Long = 5_000L    // 5s (location)

    private var partnerId: String? = null

    fun close() {
        partnerListener?.remove()
        partnerListener = null
        scope.cancel()
    }

    // ----------------------------------
    // Config / Entrée publique
    // ----------------------------------
    fun configureListener(partnerId: String?) {
        Log.i(TAG, "Configurer listener pour partenaire: ${maskId(partnerId)}")

        if (partnerId.isNullOrBlank()) {
            Log.i(TAG, "Aucun partenaire → reset état")
            resetPartnerData()
            return
        }

        // Si même partenaire déjà configuré et que le nom est connu → ne rafraîchir que la localisation
        if (this.partnerId == partnerId && _partnerName.value != null) {
            Log.i(TAG, "Même partenaire détecté → fetch location uniquement")
            fetchPartnerLocationViaCloudFunction(partnerId)
            return
        }

        this.partnerId = partnerId
        fetchPartnerDataViaCloudFunction(partnerId)
    }

    // ----------------------------------
    // Cloud Functions
    // ----------------------------------
    private fun fetchPartnerDataViaCloudFunction(partnerId: String) {
        val now = System.currentTimeMillis()
        if (now - lastFetchMillis < cacheValidityMs && _partnerName.value != null) {
            Log.i(TAG, "Données partenaire en cache → fetch location seulement")
            fetchPartnerLocationViaCloudFunction(partnerId)
            return
        }

        _isLoading.value = true
        lastFetchMillis = now

        scope.launch {
            try {
                val result = functions
                    .getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                val success = data?.get("success") as? Boolean ?: false
                if (!success) {
                    Log.e(TAG, "Réponse invalide pour getPartnerInfo")
                    return@launch
                }

                val partnerInfo = data["partnerInfo"] as? Map<String, Any?>
                if (partnerInfo == null) {
                    Log.e(TAG, "partnerInfo manquant dans la réponse")
                    return@launch
                }

                updatePartnerDataFromCloudFunction(partnerInfo)
                // Récupérer la localisation immédiatement après
                fetchPartnerLocationViaCloudFunction(partnerId)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur getPartnerInfo: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchPartnerLocationViaCloudFunction(partnerId: String) {
        val now = System.currentTimeMillis()
        if (now - lastLocationFetchMillis < locationCacheMs) {
            Log.i(TAG, "Localisation récemment récupérée → skip")
            return
        }
        lastLocationFetchMillis = now

        scope.launch {
            try {
                val result = functions
                    .getHttpsCallable("getPartnerLocation")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                val success = data?.get("success") as? Boolean ?: false
                if (!success) {
                    val reason = data?.get("reason") as? String ?: "unknown"
                    Log.w(TAG, "Localisation non disponible. Raison=$reason")
                    _partnerLocation.value = null
                    return@launch
                }

                val location = data["location"] as? Map<String, Any?>
                if (location != null) {
                    Log.i(TAG, "Localisation partenaire récupérée ✅")
                    updatePartnerLocationFromCloudFunction(location)
                }
            } catch (e: FirebaseFunctionsException) {
                Log.e(TAG, "Erreur getPartnerLocation (code=${e.code}): ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur getPartnerLocation: ${e.message}", e)
            }
        }
    }

    // ----------------------------------
    // Mises à jour locales à partir des réponses CF
    // ----------------------------------
    private fun updatePartnerLocationFromCloudFunction(locationData: Map<String, Any?>) {
        val latitude = (locationData["latitude"] as? Number)?.toDouble() ?: 0.0
        val longitude = (locationData["longitude"] as? Number)?.toDouble() ?: 0.0
        val address = locationData["address"] as? String
        val city = locationData["city"] as? String
        val country = locationData["country"] as? String

        _partnerLocation.value = UserLocation(
            latitude = latitude,
            longitude = longitude,
            address = address,
            city = city,
            country = country,
        )
        Log.i(TAG, "Localisation partenaire configurée: ${city ?: "(ville inconnue)"} ✅ (coordonnées masquées)")
    }

    private fun updatePartnerDataFromCloudFunction(partnerInfo: Map<String, Any?>) {
        Log.i(TAG, "Mise à jour des données partenaire depuis CF")

        _partnerName.value = partnerInfo["name"] as? String

        val newProfileUrl = partnerInfo["profileImageURL"] as? String
        if (!newProfileUrl.isNullOrBlank()) {
            Log.i(TAG, "Photo profil partenaire détectée")
            if (UserCacheManager.hasPartnerImageChanged(newProfileUrl)) {
                Log.i(TAG, "URL image partenaire changée → téléchargement en arrière-plan…")
                downloadAndCachePartnerImage(newProfileUrl)
            }
        } else {
            Log.i(TAG, "Pas de photo profil partenaire")
            if (UserCacheManager.hasCachedPartnerImage()) {
                UserCacheManager.clearCachedPartnerImage()
                Log.i(TAG, "Cache image partenaire nettoyé (plus d'URL)")
            }
        }
        _partnerProfileImageURL.value = newProfileUrl

        // Si la CF renvoie aussi la localisation (optionnel)
        val location = partnerInfo["currentLocation"] as? Map<String, Any?>
        if (location != null) {
            Log.i(TAG, "Localisation partenaire trouvée dans partnerInfo")
            updatePartnerLocationFromCloudFunction(location)
        } else {
            Log.w(TAG, "Aucune localisation partenaire dans partnerInfo")
            _partnerLocation.value = null
        }

        Log.i(TAG, "Données partenaire mises à jour: ${_partnerName.value ?: "inconnu"}")
    }

    // ----------------------------------
    // Utilitaires
    // ----------------------------------
    fun calculateDistance(context: Context, from: UserLocation): String {
        val partner = _partnerLocation.value ?: return "? km" // Option: context.getString(R.string.unknown_distance)

        val distanceKm = from.distanceTo(partner)

        // < 1 km → "ensemble / together"
        if (distanceKm < 1.0) {
            // strings.xml → <string name="widget_together_text">Ensemble</string>
            val together = context.getString(R.string.widget_together_text)
            return together.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        val formatted = if (distanceKm < 10.0) {
            nf.minimumFractionDigits = 1
            nf.maximumFractionDigits = 1
            nf.format(distanceKm)
        } else {
            nf.maximumFractionDigits = 0
            nf.format(distanceKm)
        }

        // Idéalement via strings.xml: <string name="distance_km_format">%1$s km</string>
        // return context.getString(R.string.distance_km_format, formatted)
        return "$formatted km"
    }

    fun clearPartnerData() {
        Log.i(TAG, "Nettoyage des données partenaire")
        partnerListener?.remove()
        partnerListener = null
        _partnerLocation.value = null
        _partnerProfileImageURL.value = null
        _partnerName.value = null
        _isLoading.value = false
        partnerId = null
        lastFetchMillis = 0L
        lastLocationFetchMillis = 0L
    }

    private fun resetPartnerData() {
        _partnerName.value = null
        _partnerLocation.value = null
        _partnerProfileImageURL.value = null
        partnerId = null
        _isLoading.value = false
        lastFetchMillis = 0L
        lastLocationFetchMillis = 0L
    }

    private fun maskId(id: String?): String = if (id.isNullOrBlank()) "null" else "[ID_MASQUÉ:${id.take(3)}…]"

    // Téléchargement + cache image partenaire (simplifié)
    private fun downloadAndCachePartnerImage(url: String) {
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val stream = URL(url).openStream()
                    stream.use { BitmapFactory.decodeStream(it) }
                }
                if (bitmap != null) {
                    UserCacheManager.cachePartnerImage(bitmap, url)
                    Log.i(TAG, "Image partenaire mise en cache ✅")
                } else {
                    Log.w(TAG, "Impossible de décoder l'image partenaire")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur téléchargement image partenaire: ${e.message}", e)
            }
        }
    }
}

// ----------------------------------
// Modèles & helpers
// ----------------------------------

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
) {
    val displayName: String
        get() = city ?: address ?: String.format(Locale.US, "%.4f,%.4f", latitude, longitude)

    /** Distance en kilomètres vers un autre point (haversine via Android Location). */
    fun distanceTo(other: UserLocation): Double {
        val results = FloatArray(1)
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, results)
        val meters = results.firstOrNull() ?: 0f
        return meters / 1000.0
    }
}

/**
 * Stub simple pour remplacer le gestionnaire de cache iOS.
 * Remplacez par votre implémentation Android (Coil/Glide/disk cache, etc.).
 */
object UserCacheManager {
    private var lastUrl: String? = null
    private var lastBitmap: Bitmap? = null

    fun hasPartnerImageChanged(newURL: String): Boolean = newURL != lastUrl
    fun hasCachedPartnerImage(): Boolean = lastBitmap != null
    fun clearCachedPartnerImage() { lastBitmap = null; lastUrl = null }
    fun cachePartnerImage(bitmap: Bitmap, url: String) { lastBitmap = bitmap; lastUrl = url }
}

/*
=====================
Exemple strings.xml
=====================

<resources>
    <!-- Affiché quand les 2 partenaires sont à moins d'1 km -->
    <string name="widget_together_text">Ensemble</string>

    <!-- Optionnel, pour formater la distance proprement dans toutes les langues -->
    <!-- <string name="distance_km_format">%1$s km</string> -->
    <!-- <string name="unknown_distance">? km</string> -->
</resources>
*/
