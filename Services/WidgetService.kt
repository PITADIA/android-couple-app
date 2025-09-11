package com.lyes.love2love.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.lyes.love2love.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.*

/**
 * === Data models équivalents ===
 */
data class WidgetPreviewData(
    val daysTogetherCount: Int,
    val userName: String?,
    val partnerName: String?,
    val distance: String?,
    val userImageFileName: String?,
    val partnerImageFileName: String?,
    val userCoordinates: Pair<Double, Double>?,
    val partnerCoordinates: Pair<Double, Double>?,
    val lastUpdate: Long // epoch seconds
) {
    companion object {
        fun placeholder(nowEpochSeconds: Long = Instant.now().epochSecond) = WidgetPreviewData(
            daysTogetherCount = 1,
            userName = "Alex",
            partnerName = "Morgan",
            distance = "3.2 km",
            userImageFileName = null,
            partnerImageFileName = null,
            userCoordinates = 48.8566 to 2.3522,
            partnerCoordinates = 43.6047 to 1.4442,
            lastUpdate = nowEpochSeconds
        )
    }
}

data class RelationshipStats(
    val startDate: LocalDate,
    val daysTotal: Int,
    val years: Int,
    val months: Int,
    val days: Int,
    val nextAnniversary: LocalDate,
    val daysToAnniversary: Int,
    val hoursToAnniversary: Int,
    val minutesToAnniversary: Int,
    val secondsToAnniversary: Int
) {
    fun formattedDuration(context: Context): String {
        return when {
            years > 0 && months > 0 -> {
                val y = context.getQuantityString(R.plurals.years, years, years)
                val m = context.getQuantityString(R.plurals.months, months, months)
                context.getString(R.string.duration_years_and_months, y, m)
            }
            years > 0 -> context.getQuantityString(R.plurals.years, years, years)
            months > 0 -> context.getQuantityString(R.plurals.months, months, months)
            else -> context.getQuantityString(R.plurals.days, days, days)
        }
    }

    fun countdownText(): String {
        return if (daysToAnniversary > 0) {
            "%d:%02d:%02d:%02d".format(
                daysToAnniversary,
                hoursToAnniversary,
                minutesToAnniversary,
                secondsToAnniversary
            )
        } else {
            "00:00:00:00"
        }
    }
}

data class DistanceInfo(
    val distanceKm: Double,
    val currentUserLocation: UserLocation,
    val partnerLocation: UserLocation,
    val messages: List<String>,
    val lastUpdatedEpochMillis: Long
) {
    fun formattedDistance(context: Context): String {
        // < 1km : "together"
        if (distanceKm < 1.0) {
            val together = context.getString(R.string.widget_together_text)
            return together.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val isImperial = usesImperial(Locale.getDefault())
        return if (isImperial) {
            val miles = distanceKm * 0.621371
            if (miles < 10) String.format(Locale.getDefault(), "%.1f mi", miles)
            else "${miles.toInt()} mi"
        } else {
            if (distanceKm < 10) String.format(Locale.getDefault(), "%.1f km", distanceKm)
            else "${distanceKm.toInt()} km"
        }
    }

    fun randomMessage(context: Context): String {
        return messages.randomOrNull() ?: context.getString(R.string.default_love_message)
    }

    private fun usesImperial(locale: Locale): Boolean {
        // US, UK, LR, MM utilisent miles; ajuste si besoin
        val c = locale.country.uppercase(Locale.US)
        return c in setOf("US", "GB", "LR", "MM")
    }
}

/**
 * === Placeholders minimaux à relier à ton code existant ===
 * Tu as probablement déjà ces classes/services — garde leurs signatures à toi.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdatedEpochMillis: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = listOfNotNull(city, country).joinToString(", ").ifBlank { "—" }
}

data class AppUser(
    val id: String,
    val name: String?,
    val partnerId: String?,
    val relationshipStartDate: LocalDate?,
    val profileImageURL: String?,
    val isSubscribed: Boolean,
    val currentLocation: UserLocation?
)

interface FirebaseService {
    val currentUserFlow: StateFlow<AppUser?>
    suspend fun updateUserLocation(location: UserLocation): Boolean

    companion object {
        // TODO branche sur ton implémentation
        fun getInstance(context: Context): FirebaseService = error("Provide your FirebaseService")
    }
}

interface LocationService {
    val currentLocationFlow: StateFlow<UserLocation?>

    companion object {
        // TODO branche sur ton implémentation
        fun getInstance(context: Context): LocationService = error("Provide your LocationService")
    }
}

/**
 * === WidgetService Android ===
 * - Coroutines/Flow (remplace Combine)
 * - SharedPreferences pour partage widget
 * - Firebase Functions (getPartnerInfo / getPartnerLocation)
 * - Cache d’images local (filesDir/ImageCache)
 */
class WidgetService(
    private val context: Context,
    private val firebaseService: FirebaseService = FirebaseService.getInstance(context),
    private val locationService: LocationService = LocationService.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    private val TAG = "WidgetService"

    private val _relationshipStats = MutableStateFlow<RelationshipStats?>(null)
    val relationshipStats: StateFlow<RelationshipStats?> = _relationshipStats

    private val _distanceInfo = MutableStateFlow<DistanceInfo?>(null)
    val distanceInfo: StateFlow<DistanceInfo?> = _distanceInfo

    private val _isLocationUpdateInProgress = MutableStateFlow(false)
    val isLocationUpdateInProgress: StateFlow<Boolean> = _isLocationUpdateInProgress

    private val _lastUpdateTimeEpochSeconds = MutableStateFlow(Instant.now().epochSecond)
    val lastUpdateTimeEpochSeconds: StateFlow<Long> = _lastUpdateTimeEpochSeconds

    private var currentUser: AppUser? = null
    private var partnerUser: AppUser? = null

    // Shared prefs pour l’app + widget
    private val prefs by lazy {
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    }

    init {
        setupObservers()
    }

    private fun setupObservers() {
        // Observe l'utilisateur courant
        scope.launch {
            firebaseService.currentUserFlow.collectLatest { user ->
                currentUser = user
                updateRelationshipStats()
                fetchPartnerInfo() // enchaîne localisation & distance
            }
        }

        // Observe la localisation utilisateur
        scope.launch {
            locationService.currentLocationFlow.collectLatest { loc ->
                if (loc != null && currentUser != null) {
                    Log.d(TAG, "🔄 Localisation utilisateur mise à jour")
                    currentUser = currentUser!!.copy(currentLocation = loc)
                    updateDistanceInfo()
                }
            }
        }
    }

    // === Relationship Stats ===
    private fun updateRelationshipStats() {
        Log.d(TAG, "🔄 updateRelationshipStats()")

        val user = currentUser
        if (user == null) {
            Log.w(TAG, "❌ Aucun utilisateur")
            _relationshipStats.value = null
            saveWidgetData()
            return
        }

        val startDate = user.relationshipStartDate
        if (startDate == null) {
            Log.w(TAG, "❌ relationshipStartDate manquante")
            _relationshipStats.value = null
            saveWidgetData()
            return
        }

        val nowDate = LocalDate.now()
        val daysTogether = max(0, ChronoUnit.DAYS.between(startDate, nowDate).toInt())
        val period = Period.between(startDate, nowDate)
        val years = period.years
        val months = period.months
        val days = period.days

        // Prochain anniversaire (même mois/jour, année courante sinon +1)
        var nextAnniv = LocalDate.of(nowDate.year, startDate.month, startDate.dayOfMonth)
        if (nextAnniv.isBefore(nowDate)) nextAnniv = nextAnniv.plusYears(1)

        val nowDateTime = LocalDateTime.now()
        val nextAnnivDateTime = nextAnniv.atStartOfDay()
        val duration = Duration.between(nowDateTime, nextAnnivDateTime).coerceAtLeast(Duration.ZERO)

        val totalSeconds = duration.seconds
        val d = (totalSeconds / (24 * 3600)).toInt()
        val h = ((totalSeconds % (24 * 3600)) / 3600).toInt()
        val m = ((totalSeconds % 3600) / 60).toInt()
        val s = (totalSeconds % 60).toInt()

        val stats = RelationshipStats(
            startDate = startDate,
            daysTotal = daysTogether,
            years = years,
            months = months,
            days = days,
            nextAnniversary = nextAnniv,
            daysToAnniversary = d,
            hoursToAnniversary = h,
            minutesToAnniversary = m,
            secondsToAnniversary = s
        )

        _relationshipStats.value = stats
        Log.d(TAG, "✅ Stats relation calculées: days=$daysTogether; countdown=${stats.countdownText()}")

        saveWidgetData()
    }

    // === Partner Info & Distance ===
    private fun fetchPartnerInfo() {
        val user = currentUser
        val partnerId = user?.partnerId.orEmpty()
        if (partnerId.isBlank()) {
            Log.d(TAG, "🔄 Pas de partenaire — nettoyage")
            partnerUser = null
            _distanceInfo.value = null
            saveWidgetData()
            return
        }

        Log.d(TAG, "🔄 Appel Cloud Function getPartnerInfo")
        scope.launch(Dispatchers.IO) {
            try {
                val result = Firebase.functions
                    .getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()
                val success = data["success"] as? Boolean ?: false
                val partnerInfo = data["partnerInfo"] as? Map<String, Any?>

                if (!success || partnerInfo == null) {
                    Log.e(TAG, "❌ getPartnerInfo: réponse invalide")
                    withContext(Dispatchers.Main) {
                        partnerUser = null
                        _distanceInfo.value = null
                        saveWidgetData()
                    }
                    return@launch
                }

                val defaultPartnerName = if (Locale.getDefault().language.startsWith("fr"))
                    context.getString(R.string.partner_generic_fr)
                else
                    context.getString(R.string.partner_generic_en)

                val p = AppUser(
                    id = partnerId,
                    name = partnerInfo["name"] as? String ?: defaultPartnerName,
                    partnerId = null,
                    relationshipStartDate = null,
                    profileImageURL = partnerInfo["profileImageURL"] as? String,
                    isSubscribed = partnerInfo["isSubscribed"] as? Boolean ?: false,
                    currentLocation = null // rempli par fetchPartnerLocation
                )

                Log.d(TAG, "✅ getPartnerInfo OK : ${p.name} ; photo=${p.profileImageURL != null}")

                withContext(Dispatchers.Main) {
                    partnerUser = p
                }

                fetchPartnerLocation(partnerId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ getPartnerInfo error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    partnerUser = null
                    _distanceInfo.value = null
                    saveWidgetData()
                }
            }
        }
    }

    private fun fetchPartnerLocation(partnerId: String) {
        Log.d(TAG, "🌍 Appel Cloud Function getPartnerLocation")
        scope.launch(Dispatchers.IO) {
            try {
                val result = Firebase.functions
                    .getHttpsCallable("getPartnerLocation")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?> ?: emptyMap()
                val success = data["success"] as? Boolean ?: false
                val loc = data["location"] as? Map<String, Any?>

                if (!success || loc == null) {
                    Log.w(TAG, "❌ Aucune localisation partenaire — on continue sans")
                    withContext(Dispatchers.Main) { updateDistanceInfo() }
                    return@launch
                }

                val partnerLocation = UserLocation(
                    latitude = (loc["latitude"] as? Number)?.toDouble() ?: .0,
                    longitude = (loc["longitude"] as? Number)?.toDouble() ?: .0,
                    address = loc["address"] as? String,
                    city = loc["city"] as? String,
                    country = loc["country"] as? String
                )

                Log.d(TAG, "✅ Localisation partenaire: ${partnerLocation.city ?: "inconnue"}")

                withContext(Dispatchers.Main) {
                    partnerUser = partnerUser?.copy(currentLocation = partnerLocation)
                    updateDistanceInfo()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ getPartnerLocation error: ${e.message}", e)
                withContext(Dispatchers.Main) { updateDistanceInfo() }
            }
        }
    }

    private fun updateDistanceInfo() {
        Log.d(TAG, "🔄 updateDistanceInfo()")
        val cu = currentUser
        val pu = partnerUser
        val cuLoc = cu?.currentLocation
        val puLoc = pu?.currentLocation

        if (cu == null || cuLoc == null || pu == null || puLoc == null) {
            Log.w(TAG, "❌ Distance: données manquantes (user/partner/location)")
            _distanceInfo.value = null
            saveWidgetData()
            return
        }

        val distanceKm = haversineKm(
            cuLoc.latitude, cuLoc.longitude,
            puLoc.latitude, puLoc.longitude
        )

        val messages = generateDistanceMessages(distanceKm)

        val info = DistanceInfo(
            distanceKm = distanceKm,
            currentUserLocation = cuLoc,
            partnerLocation = puLoc,
            messages = messages,
            lastUpdatedEpochMillis = min(cuLoc.lastUpdatedEpochMillis, puLoc.lastUpdatedEpochMillis)
        )

        _distanceInfo.value = info

        Log.d(TAG, "✅ Distance calculée: ${info.formattedDistance(context)}")
        Log.d(TAG, "✅ User: ${cuLoc.displayName} | Partner: ${puLoc.displayName}")

        saveWidgetData()
    }

    private fun generateDistanceMessages(distanceKm: Double): List<String> {
        return when {
            distanceKm < 0.1 -> listOf("🥰 Tu me manques", "💕 Je pense à toi")
            distanceKm < 1   -> listOf("😘 Tu me manques", "💖 Je pense à toi")
            distanceKm < 10  -> listOf("🤗 Tu me manques", "💝 Je pense à toi")
            distanceKm < 100 -> listOf("😍 Tu me manques", "💓 Je pense à toi")
            distanceKm < 500 -> listOf("🥺 Tu me manques", "💞 Je pense à toi")
            else             -> listOf("😢 Tu me manques tellement", "💔 J'ai hâte de te revoir")
        }
    }

    // === Location updates ===
    fun updateCurrentLocation(location: UserLocation) {
        val user = currentUser ?: return
        _isLocationUpdateInProgress.value = true

        scope.launch(Dispatchers.IO) {
            try {
                val ok = firebaseService.updateUserLocation(location)
                withContext(Dispatchers.Main) {
                    _isLocationUpdateInProgress.value = false
                    if (ok) {
                        currentUser = user.copy(currentLocation = location)
                        updateDistanceInfo()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateUserLocation error: ${e.message}", e)
                withContext(Dispatchers.Main) { _isLocationUpdateInProgress.value = false }
            }
        }
    }

    // === Public API ===
    fun refreshData() {
        Log.d(TAG, "🔄 refreshData()")
        updateRelationshipStats()
        fetchPartnerInfo()
        _lastUpdateTimeEpochSeconds.value = Instant.now().epochSecond
    }

    fun startLocationUpdates() {
        // Ici tu peux brancher un vrai start updates; pour l’instant on rafraîchit
        refreshData()
    }

    fun forceRefreshProfileImages() {
        Log.d(TAG, "🔄 Force refresh profile images")
        currentUser?.profileImageURL?.let { url ->
            downloadAndCacheImage(url, prefKey = "widget_user_image_url", fileName = "user_profile_image.jpg")
        } ?: run {
            prefs.edit { remove("widget_user_image_url") }
            clearCachedImage("user_profile_image.jpg")
        }

        partnerUser?.profileImageURL?.let { url ->
            downloadAndCacheImage(url, prefKey = "widget_partner_image_url", fileName = "partner_profile_image.jpg")
        } ?: run {
            prefs.edit { remove("widget_partner_image_url") }
            clearCachedImage("partner_profile_image.jpg")
        }
    }

    fun debugSharedPrefs() {
        val keys = listOf(
            "widget_days_total", "widget_duration", "widget_days_to_anniversary", "widget_start_date",
            "widget_distance", "widget_message", "widget_user_name", "widget_partner_name",
            "widget_user_image_url", "widget_partner_image_url",
            "widget_user_latitude", "widget_user_longitude", "widget_partner_latitude", "widget_partner_longitude",
            "widget_has_subscription", "widget_last_update"
        )
        Log.d(TAG, "🔍 Debug prefs:")
        keys.forEach { k ->
            Log.d(TAG, "  - $k: ${prefs.all[k]}")
        }
        val cacheDir = File(context.filesDir, "ImageCache")
        Log.d(TAG, "🔍 ImageCache: ${cacheDir.absolutePath}")
        cacheDir.listFiles()?.forEach { f ->
            Log.d(TAG, "   - ${f.name}: ${f.length()} bytes")
        }
    }

    // === Widget data sharing ===
    private fun saveWidgetData() {
        Log.d(TAG, "🔄 Sauvegarde données widget…")
        cleanPrefsIfNeeded()

        prefs.edit {
            // Relationship stats
            val stats = _relationshipStats.value
            if (stats != null) {
                putInt("widget_days_total", stats.daysTotal)
                putString("widget_duration", stats.formattedDuration(context))
                putInt("widget_days_to_anniversary", stats.daysToAnniversary)
                // stocker startDate en epoch seconds (minuit local)
                val epochStart = stats.startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                putLong("widget_start_date", epochStart)
            } else {
                remove("widget_days_total")
                remove("widget_duration")
                remove("widget_days_to_anniversary")
                remove("widget_start_date")
            }

            // Distance
            val dist = _distanceInfo.value
            if (dist != null) {
                putString("widget_distance", dist.formattedDistance(context))
                putString("widget_message", dist.randomMessage(context))
            } else {
                remove("widget_distance")
                remove("widget_message")
            }

            // User
            currentUser?.let { cu ->
                putString("widget_user_name", cu.name)
                putBoolean("widget_has_subscription", cu.isSubscribed)
                cu.currentLocation?.let { loc ->
                    putString("widget_user_latitude", loc.latitude.toString())
                    putString("widget_user_longitude", loc.longitude.toString())
                } ?: run {
                    remove("widget_user_latitude"); remove("widget_user_longitude")
                }
            }

            // Partner
            partnerUser?.let { pu ->
                putString("widget_partner_name", pu.name)
                pu.currentLocation?.let { loc ->
                    putString("widget_partner_latitude", loc.latitude.toString())
                    putString("widget_partner_longitude", loc.longitude.toString())
                } ?: run {
                    remove("widget_partner_latitude"); remove("widget_partner_longitude")
                }
            } ?: run {
                remove("widget_partner_name")
                remove("widget_partner_image_url")
                remove("widget_partner_latitude")
                remove("widget_partner_longitude")
            }

            putLong("widget_last_update", Instant.now().epochSecond)
        }

        notifyPreviewsUpdated()
    }

    private fun cleanPrefsIfNeeded() {
        // supprime les strings vides
        val keys = listOf(
            "widget_days_total", "widget_duration", "widget_days_to_anniversary", "widget_start_date",
            "widget_distance", "widget_message", "widget_user_name", "widget_partner_name",
            "widget_user_image_url", "widget_partner_image_url",
            "widget_user_latitude", "widget_user_longitude", "widget_partner_latitude", "widget_partner_longitude",
            "widget_has_subscription", "widget_last_update"
        )
        prefs.edit {
            keys.forEach { k ->
                val v = prefs.all[k]
                if (v is String && v.isBlank()) remove(k)
            }
        }
    }

    // === Image caching ===
    private fun downloadAndCacheImage(urlString: String, prefKey: String, fileName: String) {
        Log.d(TAG, "🔄 downloadAndCacheImage(key=$prefKey, file=$fileName) [URL masquée]")
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = URL(urlString).openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
                    Log.e(TAG, "❌ decode image failed")
                    return@launch
                }
                val resized = Bitmap.createScaledBitmap(bmp, 150, 150, true)

                val cacheDir = File(context.filesDir, "ImageCache").apply { mkdirs() }
                val outFile = File(cacheDir, fileName)
                FileOutputStream(outFile).use { fos ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                withContext(Dispatchers.Main) {
                    prefs.edit { putString(prefKey, fileName) }
                    Log.d(TAG, "✅ Image cached & pref updated: $prefKey=$fileName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ downloadAndCacheImage error: ${e.message}", e)
            }
        }
    }

    private fun clearCachedImage(fileName: String) {
        val f = File(File(context.filesDir, "ImageCache"), fileName)
        if (f.exists()) f.delete()
    }

    // === Widget data access (si besoin côté UI) ===
    fun getWidgetData(): WidgetPreviewData? {
        Log.d(TAG, "🔍 Chargement données prefs widget…")
        val days = prefs.getInt("widget_days_total", 0)
        val userName = prefs.getString("widget_user_name", null)
        val partnerName = prefs.getString("widget_partner_name", null)
        val distance = prefs.getString("widget_distance", null)
        val userImage = prefs.getString("widget_user_image_url", null)
        val partnerImage = prefs.getString("widget_partner_image_url", null)
        val userLat = prefs.getString("widget_user_latitude", null)?.toDoubleOrNull()
        val userLon = prefs.getString("widget_user_longitude", null)?.toDoubleOrNull()
        val partnerLat = prefs.getString("widget_partner_latitude", null)?.toDoubleOrNull()
        val partnerLon = prefs.getString("widget_partner_longitude", null)?.toDoubleOrNull()
        val lastUpdate = prefs.getLong("widget_last_update", Instant.now().epochSecond)

        return WidgetPreviewData(
            daysTogetherCount = days,
            userName = userName,
            partnerName = partnerName,
            distance = distance,
            userImageFileName = userImage,
            partnerImageFileName = partnerImage,
            userCoordinates = if (userLat != null && userLon != null) userLat to userLon else null,
            partnerCoordinates = if (partnerLat != null && partnerLon != null) partnerLat to partnerLon else null,
            lastUpdate = lastUpdate
        )
    }

    private fun notifyPreviewsUpdated() {
        Log.d(TAG, "📢 Notification WidgetDataUpdated")
        // Broadcast interne simple (ton widget peut l’écouter pour se rafraîchir)
        val intent = Intent("WidgetDataUpdated")
        context.sendBroadcast(intent)
    }
}

/**
 * === Utils ===
 */

// Haversine en km
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

/**
 * Résoudre une clé dynamique (ex: "daily_challenge_12") → string res
 * Remplace les appels Swift: `challengeKey.localized(tableName: "DailyChallenges")`
 *
 * Usage:
 *   val text = "daily_challenge_12".localizeByName(context)
 *   // ou si tu as une constante connue à la compile: context.getString(R.string.daily_challenge_12)
 */
fun String.localizeByName(context: Context): String {
    val resId = context.resources.getIdentifier(this, "string", context.packageName)
    return if (resId != 0) context.getString(resId) else this
}

// Helpers pour pluriels
fun Context.getQuantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    return resources.getQuantityString(id, quantity, *formatArgs)
}

// Helper pour strings (optionnel)
fun Context.t(@StringRes id: Int, vararg args: Any): String = getString(id, *args)
