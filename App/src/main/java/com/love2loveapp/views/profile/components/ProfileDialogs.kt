package com.love2loveapp.views.profile.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.love2loveapp.models.UserProfile
import com.love2loveapp.views.UnifiedProfileImageEditor
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🎭 ProfileDialogs - Composable regroupant tous les dialogs profil
 * 
 * Gère l'affichage de tous les dialogs et modals du système profil :
 * - Édition nom avec validation
 * - Sélecteur date relation avec DatePicker Material3
 * - Éditeur photo avec AndroidPhotoEditorView (réutilisation onboarding)
 * - Confirmation suppression compte
 */
@Composable
fun ProfileDialogs(
    showingNameEditor: Boolean,
    showingRelationshipDatePicker: Boolean,
    showingPhotoSelector: Boolean,
    showingDeleteConfirmation: Boolean,
    currentUser: UserProfile?,
    onDismissNameEditor: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDismissPhotoSelector: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onConfirmNameChange: (String) -> Unit,
    onConfirmDateChange: (java.util.Date) -> Unit,
    onConfirmPhotoChange: (android.graphics.Bitmap) -> Unit,
    onConfirmDelete: () -> Unit
) {
    // 📝 Dialog édition nom
    if (showingNameEditor) {
        ProfileEditNameDialog(
            currentName = currentUser?.name ?: "",
            onDismiss = onDismissNameEditor,
            onConfirm = onConfirmNameChange
        )
    }
    
    // 📅 Dialog sélection date relation
    if (showingRelationshipDatePicker) {
        ProfileDatePickerDialog(
            currentDate = currentUser?.relationshipStartDate,
            onDismiss = onDismissDatePicker,
            onConfirm = onConfirmDateChange
        )
    }
    
    // 📸 Dialog sélection/édition photo
    if (showingPhotoSelector) {
        ProfilePhotoEditorDialog(
            currentImage = null, // TODO: Charger image actuelle si nécessaire
            onDismiss = onDismissPhotoSelector,
            onPhotoSelected = onConfirmPhotoChange
        )
    }
    
    // ⚠️ Dialog confirmation suppression
    if (showingDeleteConfirmation) {
        ProfileDeleteConfirmationDialog(
            userName = currentUser?.name ?: "",
            onDismiss = onDismissDeleteConfirmation,
            onConfirm = onConfirmDelete
        )
    }
}

/**
 * 📝 ProfileEditNameDialog - Dialog édition nom utilisateur
 * 
 * Équivalent Android de EditNameView iOS :
 * - TextField Material3 avec validation
 * - Boutons Annuler/Enregistrer
 * - Validation nom non vide
 */
@Composable
private fun ProfileEditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Modifier le nom", // TODO: utiliser stringResource(R.string.profile_edit_name_title)
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            ) 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Entrez votre nouveau nom d'affichage",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        isError = it.trim().isEmpty()
                    },
                    label = { Text("Nom") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Le nom ne peut pas être vide") }
                    } else null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6B9D),
                        focusedLabelColor = Color(0xFFFF6B9D),
                        cursorColor = Color(0xFFFF6B9D)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (name.trim().isNotEmpty()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFFF6B9D)
                )
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text("Annuler")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 📅 ProfileDatePickerDialog - Dialog sélection date relation
 * 
 * Utilise Material3 DatePicker avec :
 * - Sélection intuitive
 * - Validation date passée uniquement
 * - Formatage français
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDatePickerDialog(
    currentDate: Date?,
    onDismiss: () -> Unit,
    onConfirm: (Date) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate?.time ?: System.currentTimeMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Seules les dates passées sont sélectionnables
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "En couple depuis",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            ) 
        },
        text = {
            Column {
                Text(
                    text = "Sélectionnez la date de début de votre relation",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = Color(0xFFFF6B9D),
                        selectedDayContentColor = Color.White,
                        todayDateBorderColor = Color(0xFFFF6B9D),
                        dayContentColor = Color.Black
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Date(millis))
                    }
                },
                enabled = datePickerState.selectedDateMillis != null,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFFF6B9D)
                )
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text("Annuler")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 📸 ProfilePhotoEditorDialog - Dialog édition photo de profil
 * 
 * Réutilise AndroidPhotoEditorView de l'onboarding pour :
 * - Sélection galerie/caméra
 * - Crop circulaire avec CropImage
 * - Interface cohérente avec onboarding
 */
@Composable
private fun ProfilePhotoEditorDialog(
    currentImage: android.graphics.Bitmap? = null,
    onDismiss: () -> Unit,
    onPhotoSelected: (android.graphics.Bitmap) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF7F7FA)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Titre
                Text(
                    text = "Changer la photo de profil",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Sélectionnez une nouvelle photo et ajustez-la",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // 🎯 ÉDITEUR UNIFIÉ - Nouveau système comme onboarding
                UnifiedProfileImageEditor(
                    currentImage = currentImage,
                    isOnboarding = false, // Mode profil - upload immédiat
                    onImageUpdated = { bitmap ->
                        Log.d("ProfileDialogs", "✅ Photo de profil mise à jour via système unifié")
                        onPhotoSelected(bitmap)
                        onDismiss()
                    },
                    onError = { error ->
                        Log.e("ProfileDialogs", "❌ Erreur éditeur photo: $error")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bouton fermer
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF666666)
                    )
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

/**
 * ⚠️ ProfileDeleteConfirmationDialog - Dialog confirmation suppression compte
 * 
 * Dialog de sécurité avec :
 * - Avertissement clair sur l'irréversibilité
 * - Nom utilisateur pour personnalisation
 * - Boutons rouge danger + gris annuler
 */
@Composable
private fun ProfileDeleteConfirmationDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Supprimer le compte",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Red
            ) 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (userName.isNotEmpty()) {
                        "Êtes-vous sûr(e) de vouloir supprimer définitivement le compte de $userName ?"
                    } else {
                        "Êtes-vous sûr(e) de vouloir supprimer définitivement votre compte ?"
                    },
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Cette action est irréversible. Toutes vos données seront définitivement supprimées :",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("• Profil et photo", fontSize = 13.sp, color = Color(0xFF888888))
                    Text("• Messages et conversations", fontSize = 13.sp, color = Color(0xFF888888))
                    Text("• Questions favorites", fontSize = 13.sp, color = Color(0xFF888888))
                    Text("• Connexion partenaire", fontSize = 13.sp, color = Color(0xFF888888))
                    Text("• Journal et souvenirs", fontSize = 13.sp, color = Color(0xFF888888))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Red
                )
            ) {
                Text(
                    text = "Supprimer définitivement",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF666666)
                )
            ) {
                Text("Annuler")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 🎨 Formatage date français pour affichage
 */
private fun formatDateFrench(date: Date): String {
    return SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(date)
}
