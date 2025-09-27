package com.love2loveapp.views.journal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.JournalLocation
import com.love2loveapp.utils.rememberCameraPermissionLauncher
import com.love2loveapp.utils.saveBitmapToTempUri
import com.love2loveapp.views.journal.components.JournalDatePicker
import com.love2loveapp.views.journal.components.JournalTimePicker
import com.love2loveapp.views.journal.components.JournalLocationPicker
import com.love2loveapp.views.journal.components.JournalDateTimeFormatters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✍️ CreateJournalEntryScreen selon RAPPORT_DESIGN_JOURNAL.md
 * Design iOS avec header simple, formulaire épuré et barre d'outils interactive
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJournalEntryScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journalRepository = AppDelegate.journalRepository
    val focusManager = LocalFocusManager.current
    
    // États du formulaire
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(Date()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedLocation by remember { mutableStateOf<JournalLocation?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    
    // États UI
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    
    // Validation selon le rapport (seul le titre est obligatoire)
    val canSave = title.trim().isNotEmpty() && !isCreating
    
    // Photo picker (galerie)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val uri = saveBitmapToTempUri(context, it)
            selectedImageUri = uri
        }
    }
    
    // Gestion des permissions caméra
    val requestCameraPermission = rememberCameraPermissionLauncher(
        onPermissionGranted = {
            cameraLauncher.launch(null)
        },
        onPermissionDenied = {
            // Continuer sans caméra, juste avec la galerie
        }
    )
    
    // 📡 États pour le sélecteur d'image
    var showImageSelector by remember { mutableStateOf(false) }
    
    // Fonction de sauvegarde
    fun saveEntry() {
        if (!canSave) return
        
        isCreating = true
        scope.launch {
            try {
                journalRepository?.createEntry(
                    title = title.trim(),
                    description = description.trim(),
                    eventDate = eventDate,
                    imageUri = selectedImageUri,
                    location = selectedLocation
                )?.let { result ->
                    if (result.isSuccess) {
                        onDismiss()
                    } else {
                        // TODO: Afficher erreur
                        isCreating = false
                    }
                }
            } catch (e: Exception) {
                // TODO: Afficher erreur
                isCreating = false
            }
        }
    }
    
    // 🎨 Background identique à toute l'app selon le rapport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f)) // RGB(247, 247, 250)
            .clickable(
                indication = null, // Pas d'effet visuel
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Fermer le clavier quand on tape en dehors des champs
                focusManager.clearFocus()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            
            // 🏷️ Header Simple selon le rapport (juste bouton retour)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp, bottom = 30.dp) // Safe area + espace selon rapport
            ) {
                // Bouton retour uniquement selon le rapport
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp) // 20pt selon le rapport
                    )
                }

                Spacer(modifier = Modifier.weight(1f)) // Pas de titre dans le header selon le rapport
            }
        
            
            // ✍️ Formulaire Principal selon le rapport
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 📝 Titre (champ principal) - 24pt Medium selon le rapport
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.memory_title_placeholder),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFFFD267A)
                    )
                )
                
                // 📅 Informations contextuelles (date + lieu) selon le rapport
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Date formatée selon le rapport (16sp, opacity 0.6)
                    Text(
                        text = JournalDateTimeFormatters.formatDate(eventDate),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    
                    // Localisation si sélectionnée selon le rapport
                    selectedLocation?.let { location ->
                        Text(
                            text = " • ${location.displayName}",
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // 📄 Description (champ secondaire) - 16sp selon le rapport
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.memory_description_placeholder),
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp), // Expansion selon le rapport (5...10 lignes)
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFFFD267A)
                    ),
                    maxLines = 10
                )
                
                Spacer(modifier = Modifier.weight(1f)) // Pousser la barre d'outils vers le bas selon le rapport
            }
            
            // 🛠️ Barre d'Outils Interactive selon le rapport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icônes à gauche (fonctionnalités) - 24pt selon le rapport
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 📷 Icône photo avec preview selon le rapport
                    if (selectedImageUri != null) {
                        // Affichage image sélectionnée (60x60 selon le rapport)
                        Box {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Image sélectionnée",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Bouton X supprimer selon le rapport
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove image",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        // Icône photo normale avec menu de sélection selon le rapport
                        IconButton(onClick = { showImageSelector = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add photo",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // 📅 Icône calendrier (date) selon le rapport
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Select date",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // ⏰ Icône horloge (heure) selon le rapport
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = "Select time",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // 📍 Icône localisation (état adaptatif) selon le rapport
                    IconButton(onClick = { showLocationPicker = true }) {
                        Icon(
                            imageVector = if (selectedLocation != null) Icons.Filled.LocationOn else Icons.Default.LocationOn,
                            contentDescription = "Select location",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Bouton Enregistrer (style Love2Love) selon le rapport
                val buttonText = if (isCreating) stringResource(R.string.saving) else stringResource(R.string.save)
                Button(
                    onClick = { saveEntry() },
                    enabled = canSave && !isCreating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSave && !isCreating) Color(0xFFFD267A) else Color(0xFFFD267A).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = buttonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
    
    // 📅 Date Picker
    if (showDatePicker) {
        JournalDatePicker(
            selectedDate = eventDate,
            onDateSelected = { newDate ->
                eventDate = newDate
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    
    // ⏰ Time Picker
    if (showTimePicker) {
        JournalTimePicker(
            selectedTime = eventDate,
            onTimeSelected = { newTime ->
                eventDate = newTime
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
    
    // 📍 Location Picker
    if (showLocationPicker) {
        JournalLocationPicker(
            onLocationSelected = { location ->
                selectedLocation = location
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }
    
    // 🖼️ Sélecteur d'image (Galerie vs Caméra)
    if (showImageSelector) {
        ImageSelectorDialog(
            onGallerySelected = {
                showImageSelector = false
                photoPickerLauncher.launch("image/*")
            },
            onCameraSelected = {
                showImageSelector = false
                requestCameraPermission()
            },
            onDismiss = { showImageSelector = false }
        )
    }
}

/**
 * 🖼️ Dialog pour choisir entre galerie et caméra
 */
@Composable
fun ImageSelectorDialog(
    onGallerySelected: () -> Unit,
    onCameraSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ajouter une photo",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text("Choisissez comment ajouter votre photo")
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bouton Galerie
                Button(
                    onClick = onGallerySelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Galerie",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Bouton Caméra
                Button(
                    onClick = onCameraSelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Caméra",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.Gray
                )
            }
        }
    )
}
