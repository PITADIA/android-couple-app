package com.love2loveapp.views.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.NumberPicker
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import coil.compose.AsyncImage
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.UserProfile
import com.love2loveapp.views.profile.utils.ProfileExternalLinks
import com.love2loveapp.views.UnifiedProfileImageEditor
import com.love2loveapp.views.UnifiedProfileImageView
import com.love2loveapp.views.ProfileImageType
import kotlinx.coroutines.launch
import java.util.*
import android.graphics.Bitmap
import android.util.Log

/**
 * ⚙️ MenuScreen selon RAPPORT_DESIGN_MENU_SOPHISTIQUE.md
 * Menu avec design iOS Love2Love complet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToPartnerManagement: () -> Unit,
    onNavigateToLocationTutorial: () -> Unit,
    onNavigateToWidgets: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🔥 Repository et states
    val profileRepository = AppDelegate.profileRepository
    val currentUser by profileRepository?.currentUser?.collectAsState() ?: remember { mutableStateOf(null) }
    val isLoading by profileRepository?.isLoading?.collectAsState() ?: remember { mutableStateOf(false) }

    // 🎭 États UI locaux pour modales selon le rapport
    var showingNameEditor by remember { mutableStateOf(false) }
    var showingRelationshipDatePicker by remember { mutableStateOf(false) }
    var showingPhotoSelector by remember { mutableStateOf(false) }
    var showingDeleteConfirmation by remember { mutableStateOf(false) }

    // 🚀 Initialisation
    LaunchedEffect(Unit) {
        if (currentUser == null) {
            profileRepository?.refreshProfile()
        }
    }

    // ⭐ BACKGROUND PRINCIPAL selon le rapport - Gris très clair Love2Love
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))  // RGB(247, 247, 250)
    ) {
        // 📱 STRUCTURE SCROLLVIEW selon le rapport
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 🏷️ HEADER SECTION avec photo de profil sophistiquée selon le rapport
            ProfileHeaderSection(
                    user = currentUser,
                    isLoading = isLoading,
                onPhotoClick = { showingPhotoSelector = true }
            )

            // 👤 SECTION "À PROPOS DE MOI" selon le rapport
            AboutMeSection(
                user = currentUser,
                onEditName = { showingNameEditor = true },
                onEditRelationship = { showingRelationshipDatePicker = true },
                onNavigateToPartnerCode = onNavigateToPartnerManagement,
                onNavigateToLocationTutorial = onNavigateToLocationTutorial,
                onNavigateToWidgets = onNavigateToWidgets
            )

            // 📱 TRAIT DE SÉPARATION selon le rapport
            SeparatorLine()

            // 🔧 SECTION APPLICATION selon le rapport
            ApplicationSection(
                onDeleteAccount = { showingDeleteConfirmation = true }
            )

            // Spacer final selon le rapport
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // 📝 MODALES D'ÉDITION selon le rapport
    if (showingNameEditor) {
        EditNameDialog(
            currentName = currentUser?.name ?: "",
            onSave = { newName ->
                scope.launch {
                    profileRepository?.updateUserName(newName)
                }
                showingNameEditor = false
            },
            onDismiss = { showingNameEditor = false }
        )
    }

    if (showingRelationshipDatePicker) {
        EditRelationshipDateDialog(
            currentDate = currentUser?.relationshipStartDate ?: Date(),
            onSave = { newDate ->
                scope.launch {
                    profileRepository?.updateRelationshipStartDate(newDate)
                }
                showingRelationshipDatePicker = false
            },
            onDismiss = { showingRelationshipDatePicker = false }
        )
    }

    if (showingPhotoSelector) {
        // 🎯 ÉDITEUR UNIFIÉ - Nouveau système comme onboarding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo de profil",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    TextButton(onClick = { showingPhotoSelector = false }) {
                        Text(
                            text = "Fermer",
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 🎯 Éditeur unifié (même système que onboarding)
                UnifiedProfileImageEditor(
                    isOnboarding = false, // Mode profil - upload immédiat
                    onImageUpdated = { bitmap ->
                        Log.d("ProfileScreen", "✅ Photo de profil mise à jour via système unifié")
                        showingPhotoSelector = false
                    },
                    onError = { error ->
                        Log.e("ProfileScreen", "❌ Erreur éditeur photo: $error")
                        showingPhotoSelector = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showingDeleteConfirmation) {
        DeleteAccountDialog(
            onConfirm = {
                scope.launch {
                    val result = profileRepository?.deleteAccount()
                    result?.let {
                        if (it.isSuccess) {
                            AppDelegate.appState?.deleteAccount()
                            onBack()
                        }
                    }
                }
                showingDeleteConfirmation = false
            },
            onDismiss = { showingDeleteConfirmation = false }
        )
    }
}

// ========== COMPOSANTS MENU SELON LE RAPPORT ==========

// 🏷️ HEADER SECTION avec photo de profil sophistiquée selon le rapport
@Composable
fun ProfileHeaderSection(
    user: UserProfile?,
    isLoading: Boolean,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 120.dp, bottom = 50.dp),  // Grand espace selon le rapport
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 📍 PHOTO DE PROFIL AVEC EFFET SURBRILLANCE selon le rapport
        Box(
            contentAlignment = Alignment.Center
        ) {
            // ✨ Effet surbrillance identique à PartnerDistanceView selon le rapport
            Box(
                modifier = Modifier
                    .size(132.dp)  // 120 + 12 selon le rapport
                    .background(
                        Color.White.copy(alpha = 0.35f),  // Blanc semi-transparent selon le rapport
                        CircleShape
                    )
                    .blur(6.dp)  // Flou pour effet halo selon le rapport
            )

            // 🎯 PHOTO DE PROFIL UNIFIÉE - Observe ProfileImageManager StateFlow
            UnifiedProfileImageView(
                imageType = ProfileImageType.USER,
                size = 120.dp,
                userName = user?.name ?: "",
                onClick = { onPhotoClick() },
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )

            // 📍 BORDURE BLANCHE identique à PartnerDistanceView selon le rapport
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .border(3.dp, Color.White, CircleShape)
            )
        }
    }
}

// 👤 SECTION "À PROPOS DE MOI" selon le rapport
@Composable
fun AboutMeSection(
    user: UserProfile?,
    onEditName: () -> Unit,
    onEditRelationship: () -> Unit,
    onNavigateToPartnerCode: () -> Unit,
    onNavigateToLocationTutorial: () -> Unit,
    onNavigateToWidgets: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp)  // Espacement selon le rapport
    ) {
        // Titre section selon le rapport
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.about_me),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Lignes de profil selon le rapport
        ProfileRowView(
            title = stringResource(R.string.name),
            value = user?.name ?: "Non défini",
            showChevron = true,
            onClick = onEditName
        )

        ProfileRowView(
            title = stringResource(R.string.in_relationship_since),
            value = user?.formattedRelationshipDate ?: "Non définie",
            showChevron = true,
            onClick = onEditRelationship
        )

        ProfileRowView(
            title = stringResource(R.string.partner_code),
            value = "",
            showChevron = true,
            onClick = onNavigateToPartnerCode
        )

        ProfileRowView(
            title = stringResource(R.string.location_tutorial),
            value = "",
            showChevron = true,
            onClick = onNavigateToLocationTutorial
        )

        ProfileRowView(
            title = stringResource(R.string.widgets),
            value = "",
            showChevron = true,
            onClick = onNavigateToWidgets
        )

        ProfileRowView(
            title = stringResource(R.string.manage_subscription),
            value = "",
            showChevron = true,
            onClick = {
                ProfileExternalLinks.openSubscriptionSettings(context)
            }
        )
    }
}

// 📱 TRAIT DE SÉPARATION selon le rapport
@Composable
fun SeparatorLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 20.dp)
            .background(Color.Gray.copy(alpha = 0.3f))
            .padding(bottom = 30.dp)
    )
}

// 🔧 SECTION APPLICATION selon le rapport
@Composable
fun ApplicationSection(
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp)
    ) {
        // Titre section selon le rapport
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 20.dp)  // Ajout d'espace au-dessus du titre
        ) {
            Text(
                text = stringResource(R.string.application),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Lignes d'application selon le rapport
        ProfileRowView(
            title = stringResource(R.string.contact_us),
            value = "",
            showChevron = true,
            onClick = { ProfileExternalLinks.openSupportEmail(context) }
        )


        ProfileRowView(
            title = stringResource(R.string.privacy_policy),
            value = "",
            showChevron = true,
            onClick = { ProfileExternalLinks.openPrivacyPolicy(context) }
        )

        ProfileRowView(
            title = stringResource(R.string.delete_account),
            value = "",
            showChevron = false,  // Pas de chevron pour action destructive selon le rapport
            isDestructive = false,  // Design discret selon le rapport
            onClick = onDeleteAccount
        )
    }
}

// 📋 COMPOSANT PROFILEROWVIEW selon le rapport
@Composable
fun ProfileRowView(
    title: String,
    value: String,
    showChevron: Boolean = false,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),  // Hauteur tactile selon le rapport
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône optionnelle selon le rapport
        icon?.let { iconVector ->
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color(0xFFFD267A),  // Rose Love2Love selon le rapport
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
        }

        // Titre de la ligne selon le rapport
        Text(
            text = title,
            fontSize = 16.sp,
            color = if (isDestructive) Color.Red else Color.Black,
            modifier = Modifier.weight(1f)
        )

        // Valeur (si présente) selon le rapport
        if (value.isNotEmpty()) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color.Gray
            )
        }

        // Chevron de navigation selon le rapport
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// 🎨 UserInitialsView selon le rapport
@Composable
fun UserInitialsView(
    name: String,
    size: androidx.compose.ui.unit.Dp
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val backgroundColor = generateColorFromName(name)

    Box(
        modifier = Modifier
            .size(size)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.33).sp,  // 33% de la taille
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// Génération couleur depuis nom selon le rapport
fun generateColorFromName(name: String): Color {
    val colors = listOf(
        Color(0xFFFD267A),  // Rose Love2Love
        Color(0xFF3498DB),  // Bleu
        Color(0xFF2ECC71),  // Vert
        Color(0xFFF39C12),  // Orange
        Color(0xFF9B59B6),  // Violet
        Color(0xFFE74C3C)   // Rouge
    )
    val hash = name.hashCode()
    return colors[Math.abs(hash) % colors.size]
}

// ========== MODALES D'ÉDITION SELON LE RAPPORT ==========

// 📝 EditNameDialog selon le rapport
@Composable
fun EditNameDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.name),
                    fontWeight = FontWeight.Bold
                )
        },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("Votre nom") },  // Hardcodé selon le rapport
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFD267A),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(newName)
                    onDismiss()
                },
                enabled = newName.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

// 📅 EditRelationshipDateDialog - Carrousel multilingue selon iOS
@Composable
fun EditRelationshipDateDialog(
    currentDate: Date,
    onSave: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    // Convertir Date → LocalDate pour le carrousel
    val calendar = Calendar.getInstance()
    calendar.time = currentDate
    val initialDate = LocalDate.of(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    
    var selectedDate by remember { mutableStateOf(initialDate) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 📋 TITRE
                Text(
                    text = stringResource(R.string.edit_relationship_start),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                
                // 🎡 CARROUSEL DE DATE MULTILINGUE (comme iOS WheelDatePickerStyle)
                RelationshipDateCarousel(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    modifier = Modifier.height(200.dp)
                )
                
                // 🎨 BOUTONS ACTION (design iOS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bouton Annuler
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Bouton Enregistrer (dégradé iOS)
                    Button(
                        onClick = {
                            // Convertir LocalDate → Date
                            val calendar = Calendar.getInstance()
                            calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                            onSave(calendar.time)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFD267A), // Rose principal
                                            Color(0xFFFF655B)  // Orange dégradé
                                        )
                                    ),
                                    shape = RoundedCornerShape(25.dp)
                                )
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.save),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎡 Carrousel de date multilingue (reproduit iOS WheelDatePickerStyle)
 * Réutilise la logique de RelationshipDateStepView mais avec design modal
 */
@Composable
fun RelationshipDateCarousel(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 🌍 Mois localisés depuis strings.xml (comme iOS automatic locale)
    val localizedMonths = listOf(
        stringResource(R.string.month_january),
        stringResource(R.string.month_february),
        stringResource(R.string.month_march),
        stringResource(R.string.month_april),
        stringResource(R.string.month_may),
        stringResource(R.string.month_june),
        stringResource(R.string.month_july),
        stringResource(R.string.month_august),
        stringResource(R.string.month_september),
        stringResource(R.string.month_october),
        stringResource(R.string.month_november),
        stringResource(R.string.month_december)
    )
    
    // Plage d'années : actuelle jusqu'à 50 ans en arrière
    val currentYear = LocalDate.now().year
    val yearsDesc = (currentYear downTo (currentYear - 50)).toList()
    
    var day by rememberSaveable { mutableStateOf(selectedDate.dayOfMonth) }
    var month by rememberSaveable { mutableStateOf(selectedDate.monthValue) }
    var year by rememberSaveable { mutableStateOf(selectedDate.year) }

    val daysInMonth = remember(month, year) { YearMonth.of(year, month).lengthOfMonth() }

    // Validation automatique des jours
    LaunchedEffect(month, year) {
        if (day > daysInMonth) day = daysInMonth
    }

    // Propagation vers parent
    LaunchedEffect(day, month, year) {
        onDateChange(LocalDate.of(year, month, day))
    }

    // 🎡 CARROUSEL HORIZONTAL (3 colonnes comme iOS)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // JOUR (1-31) - LOGIQUE ONBOARDING APPLIQUÉE
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                NumberPicker(context).apply {
                    wrapSelectorWheel = false // 🚫 Pas de défilement circulaire
                    minValue = 1
                    maxValue = daysInMonth
                    value = day
                    setOnValueChangedListener { _, _, newVal -> day = newVal }
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { picker ->
                if (picker.maxValue != daysInMonth) {
                    picker.maxValue = daysInMonth
                    if (picker.value > daysInMonth) {
                        picker.value = daysInMonth
                    }
                }
            }
        )
        
        // MOIS (localisé) - LOGIQUE ONBOARDING APPLIQUÉE
        AndroidView(
            modifier = Modifier.weight(2f),
            factory = { context ->
                NumberPicker(context).apply {
                    wrapSelectorWheel = false // 🚫 Pas de défilement circulaire 
                    minValue = 0
                    maxValue = 11
                    value = month - 1
                    displayedValues = localizedMonths.toTypedArray()
                    setOnValueChangedListener { _, _, newVal -> month = newVal + 1 }
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                }
            }
        )
        
        // ANNÉE (descendante) - LOGIQUE ONBOARDING APPLIQUÉE
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                NumberPicker(context).apply {
                    wrapSelectorWheel = false // 🚫 Désactiver défilement circulaire (pas d'années futures)
                    minValue = 0
                    maxValue = yearsDesc.lastIndex
                    displayedValues = yearsDesc.map { it.toString() }.toTypedArray()
                    value = yearsDesc.indexOf(year).coerceAtLeast(0)
                    setOnValueChangedListener { _, _, newIndex ->
                        val newYear = yearsDesc.getOrNull(newIndex) ?: year
                        // 🛡️ S'assurer qu'on ne dépasse pas l'année actuelle
                        if (newYear <= currentYear) {
                            year = newYear
                        }
                    }
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { picker ->
                // 🔄 Mise à jour sécurisée (logique onboarding)
                picker.displayedValues = null
                picker.minValue = 0
                picker.maxValue = yearsDesc.lastIndex
                picker.displayedValues = yearsDesc.map { it.toString() }.toTypedArray()
                val idx = yearsDesc.indexOf(year).coerceAtLeast(0)
                if (picker.value != idx) picker.value = idx
            }
        )
    }
}


// 🗑️ DeleteAccountDialog selon le rapport
@Composable
fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_account),
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_account_confirmation)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.remove),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
