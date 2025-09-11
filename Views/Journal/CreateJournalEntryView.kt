package com.love2love.journal

/*
 * Jetpack Compose port of the SwiftUI CreateJournalEntryView.
 *
 * ✅ Translations use Android's strings.xml. All user-facing strings are referenced via R.string.*
 *    Example: stringResource(R.string.memory_title_placeholder) or context.getString(R.string.save)
 *
 * 👉 Add these keys in your res/values/strings.xml (values are examples):
 *   <string name="memory_title_placeholder">Title</string>
 *   <string name="memory_description_placeholder">Describe your memory…</string>
 *   <string name="event_date_picker">Event date</string>
 *   <string name="choose_date">Choose a date</string>
 *   <string name="choose_time">Choose a time</string>
 *   <string name="cancel">Cancel</string>
 *   <string name="ok">OK</string>
 *   <string name="error">Error</string>
 *   <string name="authorization_required">Authorization required</string>
 *   <string name="open_settings_button">Open settings</string>
 *   <string name="saving">Saving…</string>
 *   <string name="save">Save</string>
 *   <string name="gallery_access_required">Photo gallery access is required.</string>
 *   <string name="gallery_access_error">Photo gallery access error.</string>
 *
 * Replace or localize values as needed (FR/EN, etc.).
 */

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.text.DateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

// --- App-level placeholders (adapt to your project DI) -----------------------------------------

data class JournalLocation(
    val displayName: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

sealed class JournalError(message: String) : Exception(message) {
    object FreemiumLimitReached : JournalError("freemium_limit_reached")
    data class Generic(val reason: String) : JournalError(reason)
}

interface JournalService {
    val currentUserEntriesCount: Int
    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Instant,
        imageUri: Uri?,
        location: JournalLocation?
    )
}

interface FreemiumManager {
    fun handleJournalEntryCreation(currentEntriesCount: Int)
}

data class AppState(
    val journalService: JournalService?,
    val freemiumManager: FreemiumManager?
)

// --- Colors -------------------------------------------------------------------------------------

private val Pink = Color(0xFFFD267A)

// --- Screen -------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJournalEntryScreen(
    appState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    // Store the selected instant as epochMillis to be saveable
    var eventMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedLocation by remember { mutableStateOf<JournalLocation?>(null) }

    var isCreating by remember { mutableStateOf(false) }

    var showFreemiumAlert by remember { mutableStateOf(false) }
    var freemiumErrorMessage by remember { mutableStateOf("") }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsMessage by remember { mutableStateOf("") }

    // Photo picker (Android Photo Picker – no runtime permission needed on modern devices)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            selectedImageUri = uri
        }
    )

    fun pickPhoto() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val canSave = title.trim().isNotEmpty()

    fun formattedEventDateTime(context: Context, millis: Long): String {
        val df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, Locale.getDefault())
        return df.format(Date(millis))
    }

    fun openSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    fun onSave() {
        if (!canSave || isCreating) return
        isCreating = true
        val js = appState.journalService
        if (js == null) {
            isCreating = false
            showFreemiumAlert = true
            freemiumErrorMessage = context.getString(R.string.error)
            return
        }

        val entryType = if (selectedImageUri != null) "photo" else "texte"

        // Launch save
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                js.createEntry(
                    title = title.trim(),
                    description = description.trim(),
                    eventDate = Instant.ofEpochMilli(eventMillis),
                    imageUri = selectedImageUri,
                    location = selectedLocation
                )

                // Analytics
                Firebase.analytics.logEvent("journal_evenement_ajoute") {
                    param("type", entryType)
                }

                onClose()
            } catch (e: JournalError) {
                isCreating = false
                when (e) {
                    is JournalError.FreemiumLimitReached -> {
                        // Close and let FreemiumManager display paywall
                        onClose()
                        appState.freemiumManager?.handleJournalEntryCreation(js.currentUserEntriesCount)
                    }
                    is JournalError.Generic -> {
                        freemiumErrorMessage = e.reason
                        showFreemiumAlert = true
                    }
                }
            } catch (t: Throwable) {
                isCreating = false
                freemiumErrorMessage = t.message ?: context.getString(R.string.error)
                showFreemiumAlert = true
            }
        }
    }

    // UI ------------------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp, bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clickable { onClose() },
                    tint = Color.Black
                )
                Spacer(Modifier.weight(1f))
            }

            // Content
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Title
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(text = stringResource(R.string.memory_title_placeholder)) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Date + (optional) Location
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedEventDateTime(context, eventMillis),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    if (selectedLocation != null) {
                        Text(
                            text = " • ${selectedLocation!!.displayName}",
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }

                // Description
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text(stringResource(R.string.memory_description_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                        maxLines = 10
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            // Bottom toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left icons
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Photo
                    if (selectedImageUri != null) {
                        Box(modifier = Modifier.size(60.dp)) {
                            val bm = remember(selectedImageUri) {
                                try {
                                    context.contentResolver.openInputStream(selectedImageUri!!)?.use { ins ->
                                        BitmapFactory.decodeStream(ins)?.asImageBitmap()
                                    }
                                } catch (t: Throwable) { null }
                            }
                            if (bm != null) {
                                Image(
                                    bitmap = bm,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Photo,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp).align(Alignment.Center)
                                )
                            }

                            // Clear button (X)
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp))
                                    .padding(2.dp)
                                    .clickable { selectedImageUri = null }
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Photo,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp).clickable { pickPhoto() }
                        )
                    }

                    // Calendar (date)
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                showDatePickerDialog(
                                    context = context,
                                    millis = eventMillis,
                                    onSelect = { date ->
                                        val current = Instant.ofEpochMilli(eventMillis).atZone(ZoneId.systemDefault())
                                        val newZdt = ZonedDateTime.of(date, current.toLocalTime(), ZoneId.systemDefault())
                                        eventMillis = newZdt.toInstant().toEpochMilli()
                                    }
                                )
                            }
                    )

                    // Time (hour/minute)
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                showTimePickerDialog(
                                    context = context,
                                    millis = eventMillis,
                                    onSelect = { time ->
                                        val current = Instant.ofEpochMilli(eventMillis).atZone(ZoneId.systemDefault())
                                        val newZdt = ZonedDateTime.of(current.toLocalDate(), time, ZoneId.systemDefault())
                                        eventMillis = newZdt.toInstant().toEpochMilli()
                                    }
                                )
                            }
                    )

                    // Location (open a simple picker sheet)
                    var showLocationSheet by remember { mutableStateOf(false) }
                    if (showLocationSheet) {
                        LocationPickerBottomSheet(
                            initial = selectedLocation,
                            onDismiss = { showLocationSheet = false },
                            onPicked = {
                                selectedLocation = it
                                showLocationSheet = false
                            }
                        )
                    }

                    Icon(
                        imageVector = if (selectedLocation != null) Icons.Outlined.LocationOn else Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp).clickable { showLocationSheet = true }
                    )
                }

                Spacer(Modifier.weight(1f))

                // Save button (pink rounded)
                val enabled = canSave && !isCreating
                Button(
                    onClick = { onSave() },
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Pink.copy(alpha = if (enabled) 1f else 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.saving), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(text = stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Alerts -------------------------------------------------------------------------------------------------
        if (showFreemiumAlert) {
            AlertDialog(
                onDismissRequest = { showFreemiumAlert = false },
                confirmButton = {
                    TextButton(onClick = { showFreemiumAlert = false }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(freemiumErrorMessage) }
            )
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        showSettingsDialog = false
                        openSettings()
                    }) { Text(stringResource(R.string.open_settings_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.authorization_required)) },
                text = { Text(settingsMessage) }
            )
        }
    }
}

// --- Helpers ------------------------------------------------------------------------------------

private fun showDatePickerDialog(
    context: Context,
    millis: Long,
    onSelect: (LocalDate) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val dialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onSelect(LocalDate.of(year, month + 1, dayOfMonth))
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dialog.show()
}

private fun showTimePickerDialog(
    context: Context,
    millis: Long,
    onSelect: (LocalTime) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val dialog = TimePickerDialog(
        context,
        { _, hour, minute -> onSelect(LocalTime.of(hour, minute)) },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        true
    )
    dialog.show()
}

// --- Simple location picker bottom sheet (placeholder) ------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPickerBottomSheet(
    initial: JournalLocation?,
    onDismiss: () -> Unit,
    onPicked: (JournalLocation?) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Location", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "(Replace with your actual LocationPickerView)")

            val examples = listOf(
                JournalLocation("Home"),
                JournalLocation("Paris, France", 48.8566, 2.3522),
                JournalLocation("New York, USA", 40.7128, -74.0060)
            )

            examples.forEach { loc ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onPicked(loc) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(loc.displayName)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { onPicked(null) }) { Text("Clear location") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
