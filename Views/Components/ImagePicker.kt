package com.love2love.ui.picker

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kotlin/Compose recode of the iOS ImagePicker + StandardImagePicker.
 *
 * Key Android adaptations:
 * - Uses Android Photo Picker via ActivityResultContracts.PickVisualMedia (no runtime storage permission required).
 * - Falls back automatically on older devices (the contract handles this under-the-hood).
 * - Strings use Android resources (strings.xml) via stringResource() / context.getString().
 * - Provides a simple preview card and callbacks for success/cancel.
 *
 * Usage example:
 *
 * var selectedUri by remember { mutableStateOf<Uri?>(null) }
 * ImagePickerCard(
 *   selectedImageUri = selectedUri,
 *   onPick = { uri, _ -> selectedUri = uri },
 *   onCancel = { /* no-op */ }
 * )
 */

private const val TAG = "ImagePicker"

@Composable
fun ImagePickerCard(
    modifier: Modifier = Modifier,
    previewHeight: Dp = 180.dp,
    selectedImageUri: Uri? = null,
    autoLaunch: Boolean = false,
    onPick: (uri: Uri, decodedBitmap: Bitmap?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var internalUri by remember { mutableStateOf(selectedImageUri) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val pickSinglePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "✅ Picked URI: $uri")
            internalUri = uri
            // Decode a preview bitmap (optional)
            val decoded = decodeBitmapFromUri(context, uri, maxSize = 2048)
            previewBitmap = decoded
            onPick(uri, decoded)
        } else {
            Log.d(TAG, "❌ Picker canceled by user")
            onCancel()
        }
    }

    LaunchedEffect(autoLaunch) {
        if (autoLaunch) {
            pickSinglePhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.photo_picker_title),
                style = MaterialTheme.typography.titleMedium
            )

            if (internalUri != null && previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.photo_picker_preview_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.photo_picker_placeholder),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    pickSinglePhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Filled.Photo, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.pick_photo))
            }
        }
    }
}

/**
 * Headless picker: exposes only an `openPicker()` lambda so you can plug it into any UI.
 */
@Composable
fun ImagePickerHost(
    onPick: (uri: Uri, decodedBitmap: Bitmap?) -> Unit,
    onCancel: () -> Unit,
    content: @Composable (openPicker: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "✅ Picked URI: $uri")
            onPick(uri, decodeBitmapFromUri(context, uri))
        } else onCancel()
    }

    content {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/**
 * Equivalent of the iOS `StandardImagePicker` – on Android the system picker closes itself,
 * so we just launch and receive the URI. Provided for API parity with your Swift code.
 */
@Composable
fun StandardImagePicker(
    onPick: (uri: Uri, decodedBitmap: Bitmap?) -> Unit,
    onCancel: () -> Unit
) {
    ImagePickerHost(onPick = onPick, onCancel = onCancel) { openPicker ->
        // Simple default button – you can replace with your own UI
        Button(onClick = openPicker) {
            Icon(Icons.Filled.Photo, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.pick_photo))
        }
    }
}

/**
 * Optional "Denied" card to mirror your iOS custom screen. In practice the Android Photo Picker
 * does **not** require storage permissions, so you usually won't need this. Keep it if you ever
 * switch to a legacy flow that reads MediaStore directly and the user denies access.
 */
@Composable
fun PhotoAccessDeniedCard(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📸",
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = stringResource(R.string.photo_access_required),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.photo_access_add_photos),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.open_settings_button))
            }
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/**
 * Decode a content Uri into a (possibly downscaled) Bitmap for previews/processing.
 * Uses ImageDecoder on API 28+ and MediaStore on older versions.
 */
fun decodeBitmapFromUri(
    context: Context,
    uri: Uri,
    maxSize: Int = 2048
): Bitmap? = try {
    val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    scaleDownIfNeeded(original, maxSize)
} catch (t: Throwable) {
    Log.e(TAG, "Failed to decode bitmap: $uri", t)
    null
}

private fun scaleDownIfNeeded(bmp: Bitmap, maxSize: Int): Bitmap {
    val w = bmp.width
    val h = bmp.height
    val largest = maxOf(w, h)
    if (largest <= maxSize) return bmp
    val ratio = maxSize.toFloat() / largest.toFloat()
    val newW = (w * ratio).toInt()
    val newH = (h * ratio).toInt()
    return Bitmap.createScaledBitmap(bmp, newW, newH, true)
}

/** Open the app settings screen (useful if you ever request legacy storage permissions). */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + context.packageName)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/*
==================== strings.xml (add these keys) ====================

<resources>
    <string name="photo_picker_title">%1$s</string>
    <!-- Replace %1$s via context.getString if you want a dynamic title; else set a literal like "Select a Photo". -->

    <string name="photo_picker_preview_cd">Selected photo preview</string>
    <string name="photo_picker_placeholder">No photo selected yet.</string>

    <string name="pick_photo">Pick a photo</string>

    <!-- Mirror of your iOS custom denied screen -->
    <string name="photo_access_required">Photo access required</string>
    <string name="photo_access_add_photos">To add a photo, allow access in Settings.</string>
    <string name="open_settings_button">Open Settings</string>
    <string name="cancel">Cancel</string>
</resources>

Notes:
- In Compose, prefer stringResource(R.string.key). In non-Compose Kotlin, use context.getString(R.string.key).
- If you want localized French/English, define these keys in `values/strings.xml` and `values-fr/strings.xml`.
*/
