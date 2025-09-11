package com.love2love.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2love.R
import kotlin.math.min

/**
 * Android/Compose équivalent de `ProfilePhotoStepView` (SwiftUI).
 *
 * Points clés:
 * - Utilise le Photo Picker Android (ActivityResultContracts.PickVisualMedia) → pas de permission requise.
 * - Centre-crop automatique en carré, puis affichage en cercle (équivalent visuel au masque rond).
 * - Localisation via `stringResource(R.string.key)` comme demandé.
 * - Zone d'action collée en bas avec "Continuer" / "Passer".
 *
 * Si tu veux un recadrage manuel interactif (zoom/déplacement comme SwiftyCrop),
 * intègre une lib de crop (p.ex. uCrop ou CanHub Image Cropper) et remplace `onPhotoPicked(uri)`
 * par un lancement d'activité de crop; le reste du flux ne change pas.
 */
@Composable
fun ProfilePhotoStepScreen(
    modifier: Modifier = Modifier,
    onImageSelected: (Bitmap) -> Unit, // appelé dès que l'image recadrée est prête
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Photo Picker (Android 13+ natif, rétroporté via Google Play Services sur beaucoup d'appareils)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoPicked(context, uri) { bitmap ->
                croppedBitmap = bitmap
                onImageSelected(bitmap)
            }
        }
    }

    // Fallback très large pour anciens appareils : ACTION_GET_CONTENT (sans permission)
    val legacyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onPhotoPicked(context, uri) { bitmap ->
                croppedBitmap = bitmap
                onImageSelected(bitmap)
            }
        }
    }

    fun launchPicker() {
        // Préfère le Photo Picker moderne quand disponible
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        try {
            photoPickerLauncher.launch(request)
        } catch (_: Throwable) {
            // Très rare : si l'implémentation n'est pas dispo sur l'appareil, on tombe sur un GetContent classique
            legacyPickerLauncher.launch("image/*")
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // ≈ (0.97, 0.97, 0.98)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(modifier = Modifier.padding(horizontal = 30.dp)) {
                Text(
                    text = stringResource(R.string.add_profile_photo),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp),
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Avatar cliquable
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { launchPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = croppedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.add_photo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Zone d'actions collée en bas
            Surface(color = Color.White) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            onContinue()
                        },
                        modifier = Modifier
                            .padding(horizontal = 30.dp)
                            .height(56.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD267A))
                    ) {
                        Text(
                            text = stringResource(R.string.continue_key),
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(15.dp))

                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.skip_step),
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text(text = stringResource(R.string.authorization_required)) },
                text = { Text(text = stringResource(R.string.photo_access_denied_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSettingsDialog = false
                        openAppSettings(context)
                    }) {
                        Text(text = stringResource(R.string.open_settings_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun onPhotoPicked(
    context: Context,
    uri: Uri,
    onReady: (Bitmap) -> Unit
) {
    // Décodage → centre-crop carré → (optionnel) redimensionnement soft
    val decoded = decodeUriToBitmap(context, uri) ?: return
    val square = cropToSquare(decoded)
    onReady(square)
}

@Suppress("DEPRECATION")
private fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (_: Throwable) { null }

private fun cropToSquare(bmp: Bitmap): Bitmap {
    val size = min(bmp.width, bmp.height)
    val x = (bmp.width - size) / 2
    val y = (bmp.height - size) / 2
    return Bitmap.createBitmap(bmp, x, y, size, size)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + context.packageName)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
