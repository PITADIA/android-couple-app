package com.love2love.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.lang.Integer.max
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

/**
 * Kotlin/Compose port of PartnerDistanceView (SwiftUI) with Android strings.xml localization.
 * - Replaces `.localized(tableName: ...)` with `stringResource(R.string.your_key)` inside Composables,
 *   and with `context.getString(R.string.your_key)` in non-Compose code.
 * - Caches last displayed distance in SharedPreferences (24h) with the same keys.
 * - Draws a curved dashed line and centers the distance chip over it.
 * - Handles placeholders ("km ?" / "? mi") and locale conversion (km↔mi for English locales).
 * - Uses Coil for image loading and a small in-memory cache for preloaded Bitmaps if desired.
 *
 * ⚠️ Wire this view to your own AppState / services by passing the proper values.
 */

// -------------------------------
// Models & Services (minimal stubs)
// -------------------------------

data class LatLng(val latitude: Double, val longitude: Double)

data class CurrentUser(
    val name: String = "",
    val profileImageUrl: String? = null,
    val currentLocation: LatLng? = null,
    val partnerId: String? = null
)

/**
 * Minimal contract for a PartnerLocationService you already have on iOS side.
 * Provide the current partner location / profile image URL / name from your repository (e.g., Firestore listener).
 */
interface PartnerLocationServiceContract {
    val partnerLocation: State<LatLng?>
    val partnerProfileImageURL: State<String?>
    val partnerName: State<String?>
    fun configureListener(partnerId: String)
}

// -------------------------------
// User cache (optional; Coil also caches aggressively)
// -------------------------------
object UserCacheManagerAndroid {
    private var userBitmap: Bitmap? = null
    private var partnerBitmap: Bitmap? = null
    private var partnerUrlCached: String? = null

    fun cacheUserBitmap(bm: Bitmap) { userBitmap = bm }
    fun getCachedUserBitmap(): Bitmap? = userBitmap

    fun cachePartnerBitmap(bm: Bitmap, url: String) { partnerBitmap = bm; partnerUrlCached = url }
    fun getCachedPartnerBitmap(): Bitmap? = partnerBitmap
    fun hasPartnerImageChanged(newURL: String): Boolean = partnerUrlCached?.equals(newURL, ignoreCase = true)?.not() ?: true
}

// -------------------------------
// Distance helpers
// -------------------------------
private fun haversineKm(a: LatLng, b: LatLng): Double {
    val R = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)

    val h = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
    val c = 2 * asin(min(1.0, sqrt(h)))
    return R * c
}

private fun isEnglishLocale(): Boolean = Locale.getDefault().language.lowercase(Locale.ROOT) == "en"

private fun formatDistanceBaseKm(context: Context, km: Double): String {
    // Mirrors iOS formatting: < 1 => together text; < 10 => one decimal; else integer
    if (km < 1.0) {
        val together = context.getString(R.string.widget_together_text)
        return together.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val text = if (km < 10.0) {
        // One decimal, locale-aware decimal separator
        val nf = NumberFormat.getNumberInstance()
        nf.maximumFractionDigits = 1
        nf.minimumFractionDigits = 1
        "${nf.format(km)} km"
    } else {
        "${km.roundToInt()} km"
    }
    return text
}

private fun convertForLocale(context: Context, baseKmText: String): String {
    // Accepts either "X km" or "? km" and converts to miles for English locales
    if (!isEnglishLocale()) return baseKmText // keep km for non-English

    val trimmed = baseKmText.trim()
    if (trimmed == "? km" || trimmed == "km ?") return "? mi"

    return try {
        val numberPart = trimmed.substringBefore(" ")
        val km = numberPart.replace(',', '.').toDouble()
        val miles = km * 0.621371
        val out = if (miles < 10.0) {
            val nf = NumberFormat.getNumberInstance(Locale.getDefault())
            nf.maximumFractionDigits = 1
            nf.minimumFractionDigits = 1
            "${nf.format(miles)} mi"
        } else {
            "${miles.roundToInt()} mi"
        }
        out
    } catch (_: Exception) {
        // Fallback untouched
        baseKmText
    }
}

private fun placeholderForLocale(): String = if (isEnglishLocale()) "? mi" else "km ?"
private fun unknownForLocale(): String = if (isEnglishLocale()) "? mi" else "? km"
private fun isPlaceholderDistance(text: String): Boolean = text.trim() == placeholderForLocale()

// -------------------------------
// SharedPreferences cache
// -------------------------------
private const val PREFS_NAME = "partner_distance_cache"
private const val KEY_LAST_DISTANCE = "last_known_partner_distance"
private const val KEY_LAST_TIME = "last_distance_update_time"

private fun loadLastKnownDistance(context: Context): Pair<String, Long>? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val dist = prefs.getString(KEY_LAST_DISTANCE, null) ?: return null
    val ts = prefs.getLong(KEY_LAST_TIME, 0L)
    return dist to ts
}

private fun saveDistanceToCache(context: Context, distance: String) {
    if (distance == placeholderForLocale()) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LAST_DISTANCE, distance).putLong(KEY_LAST_TIME, System.currentTimeMillis()).apply()
}

// -------------------------------
// UI: PartnerDistanceView (Compose)
// -------------------------------
@Composable
fun PartnerDistanceView(
    appUser: CurrentUser?,
    partnerService: PartnerLocationServiceContract,
    onPartnerAvatarTap: (() -> Unit)? = null,
    onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit
) {
    val context = LocalContext.current

    // State mirrors
    val partnerLocation by partnerService.partnerLocation
    val partnerImageUrl by partnerService.partnerProfileImageURL
    val partnerName by partnerService.partnerName

    // Cached distance & timer logic
    var cachedDistance by remember { mutableStateOf(placeholderForLocale()) }
    var lastCalculationUnix by remember { mutableLongStateOf(0L) }

    // Load cached distance on first composition
    LaunchedEffect(Unit) {
        val pair = loadLastKnownDistance(context)
        val now = System.currentTimeMillis()
        if (pair != null) {
            val (saved, ts) = pair
            if (now - ts < 24 * 60 * 60 * 1000 && saved.isNotBlank()) {
                cachedDistance = saved
                lastCalculationUnix = ts
                Log.d("PartnerDistanceView", "Loaded distance from cache: $saved")
            }
        }
    }

    // Configure partner listener when partnerId appears / changes
    val partnerId = appUser?.partnerId
    LaunchedEffect(partnerId) {
        if (!partnerId.isNullOrEmpty()) {
            partnerService.configureListener(partnerId)
        }
    }

    // Compute coalesced current location (prefer user's saved, fallback to any service you have outside)
    val currentLocation = appUser?.currentLocation

    fun calculateDistanceNow(): String {
        // If user missing -> placeholder
        if (appUser == null) return placeholderForLocale()

        // No partner connected
        if (appUser.partnerId.isNullOrEmpty()) {
            return placeholderForLocale()
        }

        // No current user location
        if (currentLocation == null) {
            return placeholderForLocale()
        }

        // No partner location yet → show unknown in current unit
        if (partnerLocation == null) {
            return unknownForLocale()
        }

        val km = haversineKm(currentLocation, partnerLocation!!)
        val baseKmText = formatDistanceBaseKm(context, km)
        return convertForLocale(context, baseKmText)
    }

    fun forceUpdateDistance() {
        val newDistance = calculateDistanceNow()
        val now = System.currentTimeMillis()
        if (newDistance != cachedDistance || (now - lastCalculationUnix) >= 2000) {
            if (newDistance != cachedDistance) {
                Log.d("PartnerDistanceView", "Distance updated: $cachedDistance → $newDistance")
                saveDistanceToCache(context, newDistance)
            }
            cachedDistance = newDistance
            lastCalculationUnix = now
        }
    }

    // Initial immediate update
    LaunchedEffect(Unit) { forceUpdateDistance() }

    // React to location / partner changes
    LaunchedEffect(currentLocation) { forceUpdateDistance() }
    LaunchedEffect(partnerLocation) { forceUpdateDistance() }

    // Polling timer every 5 seconds (like the Swift Timer)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            // lightweight cache refresh
            val newDistance = calculateDistanceNow()
            val now = System.currentTimeMillis()
            if (newDistance != cachedDistance || (now - lastCalculationUnix) >= 2000) {
                cachedDistance = newDistance
                lastCalculationUnix = now
                saveDistanceToCache(context, newDistance)
            }
        }
    }

    val hasConnectedPartner = !appUser?.partnerId.isNullOrEmpty()

    // shouldShowLocationPermissionFlow → when placeholder is displayed
    val shouldShowLocationPermissionFlow = isPlaceholderDistance(cachedDistance)

    // shouldShowPartnerLocationMessage: partner connected + we have user location but still placeholder
    val shouldShowPartnerLocationMessage = hasConnectedPartner && currentLocation != null && isPlaceholderDistance(cachedDistance)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: current user profile
        UserProfileImage(
            imageURL = appUser?.profileImageUrl,
            userName = appUser?.name.orEmpty(),
            size = 80.dp
        )

        // Center: curved dashed line with distance chip
        Box(
            modifier = Modifier
                .height(40.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            CurvedDashedLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                strokeWidth = 3.dp,
                dashOn = 8.dp,
                dashOff = 4.dp,
                color = Color.White
            )

            // Distance chip
            DistanceChip(
                text = cachedDistance,
                enabled = shouldShowLocationPermissionFlow,
                onClick = {
                    if (shouldShowLocationPermissionFlow) {
                        onDistanceTap(shouldShowPartnerLocationMessage)
                    }
                }
            )
        }

        // Right: partner avatar (tap opens partner management if none)
        Box(modifier = Modifier
            .size(80.dp)
            .clickable(enabled = !hasConnectedPartner) {
                if (!hasConnectedPartner) onPartnerAvatarTap?.invoke()
            }
        ) {
            PartnerProfileImage(
                hasPartner = hasConnectedPartner,
                imageURL = partnerImageUrl,
                partnerName = partnerName.orEmpty(),
                size = 80.dp
            )
        }
    }
}

// -------------------------------
// UI Pieces
// -------------------------------
@Composable
private fun DistanceChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .heightIn(min = 40.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.White.copy(alpha = 0.95f),
        contentColor = Color.Black
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun UserProfileImage(
    imageURL: String?,
    userName: String,
    size: Dp
) {
    Box(contentAlignment = Alignment.Center) {
        // subtle halo
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
        )

        val cached = remember { UserCacheManagerAndroid.getCachedUserBitmap() }
        if (cached != null) {
            Image(
                bitmap = androidx.compose.ui.graphics.asImageBitmap(cached),
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else if (!imageURL.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageURL)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else if (userName.isNotBlank()) {
            UserInitialsView(name = userName, size = size)
        } else {
            // Fallback icon
            Box(
                modifier = Modifier.size(size).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                // Replace with your own placeholder painter if desired
                Text("\uD83D\uDC64", fontSize = (size.value * 0.4f).sp)
            }
        }

        // white border
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Transparent)
                .borderStroke(3.dp, Color.White, CircleShape)
        )
    }
}

@Composable
fun PartnerProfileImage(
    hasPartner: Boolean,
    imageURL: String?,
    partnerName: String,
    size: Dp
) {
    Box(contentAlignment = Alignment.Center) {
        // subtle halo
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (hasPartner) 0.35f else 0.2f))
        )

        if (hasPartner) {
            val cached = remember { UserCacheManagerAndroid.getCachedPartnerBitmap() }
            if (cached != null) {
                Image(
                    bitmap = androidx.compose.ui.graphics.asImageBitmap(cached),
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape)
                )
            } else if (!imageURL.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageURL)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape),
                    onSuccess = { result ->
                        val bm = (result.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bm != null) UserCacheManagerAndroid.cachePartnerBitmap(bm, imageURL)
                    }
                )
            } else if (partnerName.isNotBlank()) {
                UserInitialsView(name = partnerName, size = size)
            } else {
                EmptyPersonCircle(size)
            }
        } else {
            EmptyPersonCircle(size)
        }

        // border
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Transparent)
                .borderStroke(3.dp, if (hasPartner) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
private fun EmptyPersonCircle(size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text("\uD83D\uDC64", fontSize = (size.value * 0.4f).sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun UserInitialsView(name: String, size: Dp) {
    val initials = remember(name) {
        name.split(" ", "-", ignoreCase = true)
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = (size.value * 0.36f).sp, color = Color(0xFF424242), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CurvedDashedLine(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    dashOn: Dp = 8.dp,
    dashOff: Dp = 4.dp,
    color: Color = Color.White
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val curveHeight = min(widthPx * 0.03f, 15f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val start = Offset(0f, heightPx / 2f + curveHeight)
            val end = Offset(widthPx, heightPx / 2f + curveHeight)
            val control = Offset(widthPx / 2f, heightPx / 2f - curveHeight)

            val path = Path().apply {
                moveTo(start.x, start.y)
                quadraticBezierTo(control.x, control.y, end.x, end.y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashOn.toPx(), dashOff.toPx()),
                        0f
                    )
                )
            )
        }
    }
}

// -------------------------------
// Small helpers
// -------------------------------
private fun Modifier.borderStroke(width: Dp, color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(
        Modifier
            .shadow(0.dp, shape) // ensure clip + border overlay nicely
            .drawBehind {
                val strokeWidthPx = width.toPx()
                val half = strokeWidthPx / 2
                drawPath(
                    path = Path().apply { addRoundRect(androidx.compose.ui.graphics.RoundRect(0f + half, 0f + half, size.width - half, size.height - half, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension / 2, size.minDimension / 2))) },
                    color = color,
                    style = Stroke(width = strokeWidthPx)
                )
            }
    )

/* -------------------------------------------------------------
   strings.xml — add the keys you use in this file, for example:

   <resources>
       <string name="widget_together_text">Ensemble</string>
       <!-- Any other keys you may need -->
   </resources>

   Usage inside Compose: stringResource(R.string.widget_together_text)
   Usage outside Compose: context.getString(R.string.widget_together_text)
   ------------------------------------------------------------- */
