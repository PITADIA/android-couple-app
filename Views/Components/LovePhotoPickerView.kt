// LovePhotoPicker.kt
package com.love2loveapp.ui.picker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Equivalent Kotlin/Compose du LovePhotoPickerView Swift :
 * - UI modernisée en Compose
 * - Utilise le Photo Picker Android (aucune permission runtime)
 * - Retourne un Bitmap via onImagePicked
 * - Affiche un AlertDialog pour "Ouvrir les réglages" si nécessaire
 */
@Composable
fun LovePhotoPickerScreen(
    onImagePicked: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var showSettingsAlert by remember { mutableStateOf(false) }

    // Launcher du Photo Picker (1 image)
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            Log.d("LOVE2LOVE_PRIVACY", "[AUDIT] Sélection annulée par l'utilisateur")
            return@rememberLauncherForActivityResult
        }
        Log.d("LOVE2LOVE_PRIVACY", "[AUDIT] URI sélectionnée: $uri — choix explicite conforme")
        isLoading = true

        // Décodage du Bitmap en tâche de fond
        decodeBitmapAsync(
            context = context,
            uri = uri,
            onSuccess = { bmp ->
                isLoading = false
                Log.d("LOVE2LOVE_PRIVACY", "[AUDIT] Image décodée avec succès")
                onImagePicked(bmp)
                onDismiss()
            },
            onError = {
                isLoading = false
                Log.w("LOVE2LOVE_PRIVACY", "[AUDIT] Échec du décodage de l'image sélectionnée")
                // Dans de rares cas (politiques OEM), suggérer d’ouvrir les réglages
                showSettingsAlert = true
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Photo,
                    contentDescription = "Photo",
                    tint = LovePink,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .height(60.dp)
                )
                Text(
                    text = stringResource(id = R.string.choose_photo),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.photo_access_description),
                    fontSize = 13.sp,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center
                )
            }

            // Bouton principal
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        Log.d("LOVE2LOVE_PRIVACY", "[AUDIT] Demande d'accès photos initiée par l'utilisateur")
                        // Lancement du Photo Picker natif (Android 13+) ou fallback SAF (≤12)
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = stringResource(id = R.string.access_photos),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.cancel),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        // Alerte "ouvrir les réglages" (rare sur ce flux, gardé pour parité UX)
        if (showSettingsAlert) {
            AlertDialog(
                onDismissRequest = { showSettingsAlert = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSettingsAlert = false
                            openAppSettings(context)
                        }
                    ) { Text(text = stringResource(id = R.string.open_settings_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsAlert = false }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                },
                title = { Text(text = stringResource(id = R.string.photo_access_required)) },
                text = { Text(text = stringResource(id = R.string.photo_permission_message)) }
            )
        }
    }
}

// --- Helpers -----------------------------------------------------------------

private val LovePink = Color(0xFFFD267A)

private fun openAppSettings(context: Context) {
    Log.d("LOVE2LOVE_PRIVACY", "[AUDIT] Redirection vers les paramètres système")
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Décodage Bitmap sur thread I/O, compatible API 21+.
 */
private fun decodeBitmapAsync(
    context: Context,
    uri: Uri,
    onSuccess: (Bitmap) -> Unit,
    onError: () -> Unit
) {
    // Utilise un dispatcher I/O via withContext ; on garde une simple passerelle ici.
    // Tu peux basculer ceci en suspend si tu préfères.
    Thread {
        try {
            val bmp = decodeBitmapBlocking(context, uri)
            if (bmp != null) {
                runOnMain { onSuccess(bmp) }
            } else {
                runOnMain { onError() }
            }
        } catch (t: Throwable) {
            Log.e("LOVE2LOVE_PRIVACY", "Erreur décodage Bitmap", t)
            runOnMain { onError() }
        }
    }.start()
}

private fun decodeBitmapBlocking(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (t: Throwable) {
        null
    }
}

private fun runOnMain(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}

// --- Prévisualisation (facultatif) -------------------------------------------

/*
@Preview(showBackground = true)
@Composable
private fun LovePhotoPickerPreview() {
    LovePhotoPickerScreen(
        onImagePicked = { /* no-op */ },
        onDismiss = { /* no-op */ }
    )
}
*/
