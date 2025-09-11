@file:Suppress("UnusedImport")

// JournalMapScreen.kt
// Jetpack Compose rewrite of the SwiftUI JournalMapView with clustering and Android strings.xml i18n.
// - Uses Google Maps Compose
// - Replaces .localized(...) with Android resources via stringResource(...) or context.getString(...)
// - Includes stable ID clustering with distance thresholds based on current zoom level
// - Shows bubbles for unique countries/cities, a back button, an empty-state CTA, and bottom sheets for
//   entry details and cluster details.
//
// Dependencies (Gradle):
// implementation("com.google.android.gms:play-services-maps:18.2.0")
// implementation("com.google.maps.android:maps-compose:4.4.1")
// implementation("io.coil-kt:coil-compose:2.6.0")
// implementation("androidx.compose.material3:material3:<latest>")
// implementation("androidx.activity:activity-compose:<latest>")
// implementation("androidx.lifecycle:lifecycle-runtime-compose:<latest>")
//
// Notes:
// • For localization, define keys in res/values/strings.xml, e.g. back, country, countries, city, cities,
//   events_count, add_journal_events, memories_appear_map, close.
// • Pass your data from a ViewModel (e.g., entries as StateFlow<List<JournalEntry>>). The composable below
//   accepts a List<JournalEntry> directly for simplicity.

package com.example.journalmap

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlin.math.*

// ---------------------------------------------------------
// Data models (adapt to your actual app models)
// ---------------------------------------------------------

data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val country: String? = null
) {
    val latLng: LatLng get() = LatLng(latitude, longitude)
}

data class JournalEntry(
    val id: String,
    val title: String,
    val description: String = "",
    val imageURL: String? = null,
    val eventDateEpochMillis: Long = System.currentTimeMillis(),
    val location: JournalLocation? = null
)

/** Stable cluster with ID derived from member entry IDs (sorted). */
data class JournalCluster(
    val id: String,
    val coordinate: LatLng,
    val entries: List<JournalEntry>
) {
    val count: Int get() = entries.size
    val isCluster: Boolean get() = count > 1
    val firstEntry: JournalEntry get() = entries.first()

    companion object {
        fun createId(from: List<JournalEntry>): String =
            from.map { it.id }.sorted().joinToString("-")
    }
}

// ---------------------------------------------------------
// Public Composable
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalMapScreen(
    entries: List<JournalEntry>,
    showBackButton: Boolean = true,
    onBack: () -> Unit = {},
    currentLocation: LatLng? = null,
    onCreateEntry: () -> Unit = {},
) {
    val context = LocalContext.current

    // Filter entries that have a location
    val entriesWithLocation = remember(entries) { entries.filter { it.location != null } }

    // Unique country/city counts
    val uniqueCountriesCount by remember(entriesWithLocation) {
        mutableStateOf(entriesWithLocation.mapNotNull { it.location?.country }.toSet().size)
    }
    val uniqueCitiesCount by remember(entriesWithLocation) {
        mutableStateOf(entriesWithLocation.mapNotNull { it.location?.city }.toSet().size)
    }

    val cameraPositionState = rememberCameraPositionState {
        // Initial region handled below in LaunchedEffect
        position = CameraPosition.fromLatLngZoom(LatLng(46.2276, 2.2137), 5f) // France as fallback
    }

    var hasInitializedCamera by remember { mutableStateOf(false) }

    // Selection state
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var selectedCluster by remember { mutableStateOf<JournalCluster?>(null) }

    // Compute clusters for the current zoom
    val zoom by derivedStateOf { cameraPositionState.position.zoom }
    val clusters by remember(entriesWithLocation, zoom) {
        mutableStateOf(createStableClusters(entriesWithLocation, zoom))
    }

    // Initialize the camera smartly (current location > entries > locale)
    LaunchedEffect(hasInitializedCamera, entriesWithLocation, currentLocation) {
        if (!hasInitializedCamera) {
            when {
                currentLocation != null -> {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLocation, 8f))
                }
                entriesWithLocation.size == 1 -> {
                    val loc = entriesWithLocation.first().location!!.latLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(loc, 14f))
                }
                entriesWithLocation.size > 1 -> {
                    val (center, zoomForBounds) = cameraForAllEntries(entriesWithLocation)
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(center, zoomForBounds))
                }
                else -> {
                    val (center, zoomFallback) = defaultMapRegionForLocale()
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(center, zoomFallback))
                }
            }
            hasInitializedCamera = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            properties = MapProperties(isMyLocationEnabled = false)
        ) {
            clusters.forEach { cluster ->
                if (cluster.isCluster) {
                    Marker(
                        state = MarkerState(cluster.coordinate),
                        title = "${cluster.count} ${stringResource(R.string.events_count)}",
                        onClick = {
                            selectedCluster = cluster
                            true
                        }
                    )
                } else {
                    val entry = cluster.firstEntry
                    val title = entry.title
                    Marker(
                        state = MarkerState(entry.location!!.latLng),
                        title = title,
                        snippet = entry.description.take(80),
                        onClick = {
                            selectedEntry = entry
                            true
                        }
                    )
                }
            }
        }

        // Top overlay: Back + info bubbles
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showBackButton) {
                    FilledTonalButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(text = stringResource(id = R.string.back), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (entriesWithLocation.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (uniqueCountriesCount > 0) {
                            LocationInfoBubble(
                                count = uniqueCountriesCount,
                                label = if (uniqueCountriesCount == 1)
                                    stringResource(R.string.country) else stringResource(R.string.countries)
                            )
                        }
                        if (uniqueCitiesCount > 0) {
                            LocationInfoBubble(
                                count = uniqueCitiesCount,
                                label = if (uniqueCitiesCount == 1)
                                    stringResource(R.string.city) else stringResource(R.string.cities)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Empty state CTA when no entries with a location
        if (entriesWithLocation.isEmpty()) {
            EmptyStateCard(
                title = stringResource(R.string.add_journal_events),
                subtitle = stringResource(R.string.memories_appear_map),
                onClick = onCreateEntry
            )
        }

        // Cluster detail sheet
        if (selectedCluster != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedCluster = null },
                sheetState = sheetState
            ) {
                ClusterDetailSheet(
                    cluster = selectedCluster!!,
                    onSelectEntry = { entry ->
                        selectedCluster = null
                        selectedEntry = entry
                    }
                )
            }
        }

        // Entry detail sheet
        if (selectedEntry != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedEntry = null },
                sheetState = sheetState
            ) {
                JournalEntryDetailSheet(entry = selectedEntry!!) { selectedEntry = null }
            }
        }
    }
}

// ---------------------------------------------------------
// UI Pieces
// ---------------------------------------------------------

@Composable
private fun LocationInfoBubble(count: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.width(6.dp))
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(text = subtitle, fontSize = 16.sp, color = Color.Black.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ClusterDetailSheet(cluster: JournalCluster, onSelectEntry: (JournalEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${cluster.count} ${stringResource(R.string.events_count)}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        val firstLoc = cluster.entries.firstOrNull()?.location
        if (firstLoc != null) {
            Text(
                text = listOfNotNull(firstLoc.city, firstLoc.country).joinToString(", "),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }

        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cluster.entries) { entry ->
                Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp, modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectEntry(entry) }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!entry.imageURL.isNullOrBlank()) {
                            AsyncImage(
                                model = entry.imageURL,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Pink),
                                contentAlignment = Alignment.Center
                            ) {
                                // Placeholder heart
                                Text("❤", fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (entry.description.isNotBlank()) {
                                Text(entry.description, fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f), maxLines = 2)
                            }
                            Text(
                                text = formatEpochMillis(entry.eventDateEpochMillis),
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { onSelectEntry(entry) }) {
                            Icon(
                                painter = painterResource(android.R.drawable.arrow_forward),
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun JournalEntryDetailSheet(entry: JournalEntry, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.title,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        if (!entry.imageURL.isNullOrBlank()) {
            AsyncImage(
                model = entry.imageURL,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(12.dp))
        }
        if (entry.description.isNotBlank()) {
            Text(
                text = entry.description,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
        entry.location?.let { loc ->
            Text(
                text = listOfNotNull(loc.city, loc.country).joinToString(", "),
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
        Text(
            text = formatEpochMillis(entry.eventDateEpochMillis),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 12.sp,
            color = Color.Black.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(text = stringResource(R.string.close)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------
// Clustering & Camera helpers
// ---------------------------------------------------------

private fun createStableClusters(entries: List<JournalEntry>, zoom: Float): List<JournalCluster> {
    if (entries.isEmpty()) return emptyList()

    val clusterDistanceKm = when {
        zoom < 3f -> 1000.0
        zoom < 5f -> 500.0
        zoom < 7f -> 200.0
        zoom < 9f -> 100.0
        zoom < 11f -> 50.0
        zoom < 13f -> 25.0
        zoom < 15f -> 10.0
        zoom < 17f -> 5.0
        else -> 1.0
    }

    val remaining = entries.toMutableList()
    val result = mutableListOf<JournalCluster>()

    while (remaining.isNotEmpty()) {
        val seed = remaining.removeFirst()
        val seedLoc = seed.location?.latLng ?: continue
        val bucket = mutableListOf(seed)

        val iterator = remaining.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val loc = e.location?.latLng ?: continue
            if (distanceKm(seedLoc, loc) < clusterDistanceKm) {
                bucket += e
                iterator.remove()
            }
        }

        // center coordinate and stable ID
        val center = averageLatLng(bucket.mapNotNull { it.location?.latLng }) ?: seedLoc
        val stableId = JournalCluster.createId(bucket)
        val sorted = bucket.sortedByDescending { it.eventDateEpochMillis }
        result += JournalCluster(id = stableId, coordinate = center, entries = sorted)
    }

    return result
}

private fun averageLatLng(points: List<LatLng>): LatLng? {
    if (points.isEmpty()) return null
    var lat = 0.0
    var lng = 0.0
    for (p in points) {
        lat += p.latitude
        lng += p.longitude
    }
    val n = points.size.toDouble()
    return LatLng(lat / n, lng / n)
}

private fun distanceKm(a: LatLng, b: LatLng): Double {
    val R = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val s1 = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2.0)
    val c = 2 * atan2(sqrt(s1), sqrt(1 - s1))
    return R * c
}

private fun cameraForAllEntries(entries: List<JournalEntry>): Pair<LatLng, Float> {
    val latitudes = entries.mapNotNull { it.location?.latitude }
    val longitudes = entries.mapNotNull { it.location?.longitude }
    if (latitudes.isEmpty() || longitudes.isEmpty()) return LatLng(46.2276, 2.2137) to 5f

    val minLat = latitudes.min()
    val maxLat = latitudes.max()
    val minLng = longitudes.min()
    val maxLng = longitudes.max()
    val center = LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)

    val spanLat = max(0.01, (maxLat - minLat) * 1.3)
    // heuristic zoom based on latitude span
    val zoom = when {
        spanLat > 40 -> 3f
        spanLat > 20 -> 4f
        spanLat > 10 -> 5f
        spanLat > 5 -> 6f
        spanLat > 2 -> 7f
        spanLat > 1 -> 8f
        spanLat > 0.5 -> 10f
        spanLat > 0.2 -> 12f
        else -> 14f
    }
    return center to zoom
}

private fun defaultMapRegionForLocale(): Pair<LatLng, Float> {
    val locale = java.util.Locale.getDefault()
    val language = locale.language.lowercase()
    val country = (locale.country ?: "").uppercase()

    return when {
        // United States
        language == "en" && country == "US" -> LatLng(39.8283, -98.5795) to 4.5f
        // Canada
        (language == "en" || language == "fr") && country == "CA" -> LatLng(56.1304, -106.3468) to 3.8f
        // United Kingdom
        language == "en" && country == "GB" -> LatLng(55.3781, -3.4360) to 6.5f
        // Australia
        language == "en" && country == "AU" -> LatLng(-25.2744, 133.7751) to 3.5f
        // France (default for fr-*)
        language == "fr" -> LatLng(46.2276, 2.2137) to 6.8f
        // Spain
        language == "es" && country == "ES" -> LatLng(40.4637, -3.7492) to 6.8f
        // Germany
        language == "de" && country == "DE" -> LatLng(51.1657, 10.4515) to 6.8f
        // Italy
        language == "it" && country == "IT" -> LatLng(41.8719, 12.5674) to 6.5f
        // Japan
        language == "ja" && country == "JP" -> LatLng(36.2048, 138.2529) to 5.8f
        // Brazil (pt-BR)
        language == "pt" && country == "BR" -> LatLng(-14.2350, -51.9253) to 4.0f
        // EU-ish fallback
        country in setOf("BE", "NL", "CH", "AT", "PT", "DK", "SE", "NO", "FI") -> LatLng(54.5260, 15.2551) to 4.5f
        // World view fallback
        else -> LatLng(20.0, 0.0) to 2.5f
    }
}

private fun formatEpochMillis(epochMillis: Long): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val zone = java.time.ZoneId.systemDefault()
    return java.time.ZonedDateTime.ofInstant(instant, zone).format(formatter)
}

private val Pink = Color(0xFFFD267A)

// ---------------------------------------------------------
// Preview
// ---------------------------------------------------------

@Preview(showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreviewJournalMapScreen() {
    val sample = listOf(
        JournalEntry(
            id = "1",
            title = "Dîner romantique",
            description = "Un super moment à Paris",
            imageURL = null,
            eventDateEpochMillis = System.currentTimeMillis() - 86_400_000L,
            location = JournalLocation(48.8566, 2.3522, city = "Paris", country = "France")
        ),
        JournalEntry(
            id = "2",
            title = "Plage",
            description = "Coucher de soleil",
            imageURL = null,
            eventDateEpochMillis = System.currentTimeMillis() - 172_800_000L,
            location = JournalLocation(43.2965, 5.3698, city = "Marseille", country = "France")
        ),
        JournalEntry(
            id = "3",
            title = "Randonnée",
            description = "Souvenirs Alpins",
            imageURL = null,
            eventDateEpochMillis = System.currentTimeMillis() - 259_200_000L,
            location = JournalLocation(45.7640, 4.8357, city = "Lyon", country = "France")
        )
    )

    JournalMapScreen(entries = sample, showBackButton = true)
}
