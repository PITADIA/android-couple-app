package com.love2loveapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.love2loveapp.R

/**
 * 📷 PermissionUtils - Gestion centralisée des permissions pour images et caméra
 * 
 * Implémente les meilleures pratiques Android :
 * - Demande de permissions au moment approprié
 * - Gestion des refus de permissions
 * - Messages d'explication clairs
 * - Compatible avec les API récentes (Scoped Storage, Photo Picker)
 */

/**
 * Enum pour les différents types de permissions nécessaires
 */
enum class PermissionType(val permission: String) {
    CAMERA(Manifest.permission.CAMERA),
    READ_MEDIA_IMAGES(Manifest.permission.READ_MEDIA_IMAGES),
    READ_EXTERNAL_STORAGE(Manifest.permission.READ_EXTERNAL_STORAGE),
    POST_NOTIFICATIONS(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * État des permissions (enum pour compatibilité R8 / Java 8)
 */
enum class PermissionState {
    Granted,
    Denied,
    PermanentlyDenied;

    companion object {
        fun from(isGranted: Boolean, showRationale: Boolean): PermissionState = when {
            isGranted -> Granted
            showRationale -> Denied
            else -> PermanentlyDenied
        }
    }
}

/**
 * 🎯 Composable principal pour gérer les permissions d'images et caméra
 * 
 * @param onCameraPermissionGranted Callback appelé quand la permission caméra est accordée
 * @param onCameraPermissionDenied Callback appelé quand la permission caméra est refusée
 * @param showRationaleDialog État pour afficher la dialogue d'explication
 * @param onShowRationaleDialog Callback pour contrôler l'affichage de la dialogue
 */
@Composable
fun CameraPermissionHandler(
    onCameraPermissionGranted: () -> Unit,
    onCameraPermissionDenied: () -> Unit = {},
    showRationaleDialog: Boolean = false,
    onShowRationaleDialog: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf(showRationaleDialog) }
    
    // Launcher pour demander la permission caméra
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted()
        } else {
            // Vérifier si c'est un refus permanent
            if (!shouldShowRequestPermissionRationale(context, PermissionType.CAMERA.permission)) {
                // Permission refusée de façon permanente
                onCameraPermissionDenied()
            } else {
                // Première fois ou refus temporaire - montrer l'explication
                showPermissionRationale = true
                onShowRationaleDialog(true)
            }
            onCameraPermissionDenied()
        }
    }
    
    // Fonction publique pour demander la permission caméra
    fun requestCameraPermission() {
        when (getCameraPermissionState(context)) {
            PermissionState.Granted -> {
                onCameraPermissionGranted()
            }
            PermissionState.Denied -> {
                if (shouldShowRequestPermissionRationale(context, PermissionType.CAMERA.permission)) {
                    // Montrer l'explication d'abord
                    showPermissionRationale = true
                    onShowRationaleDialog(true)
                } else {
                    // Demander directement la permission
                    cameraPermissionLauncher.launch(PermissionType.CAMERA.permission)
                }
            }
            PermissionState.PermanentlyDenied -> {
                onCameraPermissionDenied()
            }
        }
    }
    
    // Stocker la fonction dans une variable pour l'utiliser
    LaunchedEffect(Unit) {
        // Cette fonction sera disponible via le composable parent
    }
    
    // Dialogue d'explication pour la permission caméra
    if (showPermissionRationale) {
        CameraPermissionRationaleDialog(
            onGrantPermission = {
                showPermissionRationale = false
                onShowRationaleDialog(false)
                cameraPermissionLauncher.launch(PermissionType.CAMERA.permission)
            },
            onDeny = {
                showPermissionRationale = false
                onShowRationaleDialog(false)
                onCameraPermissionDenied()
            }
        )
    }
}

/**
 * 📸 Dialogue d'explication pour la permission caméra
 */
@Composable
fun CameraPermissionRationaleDialog(
    onGrantPermission: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                text = "Accès à la caméra",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = "Love2Love a besoin d'accéder à votre caméra pour prendre des photos et créer vos souvenirs. Vos photos restent privées et ne sont partagées qu'avec votre partenaire.",
                fontSize = 16.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Autoriser",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(
                    text = "Plus tard",
                    color = Color.Gray
                )
            }
        }
    )
}

/**
 * 📷 Hook composable pour utiliser les permissions caméra facilement
 * 
 * Retourne une fonction pour demander la permission caméra
 */
@Composable
fun rememberCameraPermissionLauncher(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    // Fonction pour lancer la demande de permission
    return remember {
        {
            when (getCameraPermissionState(context)) {
                PermissionState.Granted -> {
                    onPermissionGranted()
                }
                else -> {
                    permissionLauncher.launch(PermissionType.CAMERA.permission)
                }
            }
        }
    }
    
    // Dialogue d'explication si nécessaire
    if (showRationale) {
        CameraPermissionRationaleDialog(
            onGrantPermission = {
                showRationale = false
                permissionLauncher.launch(PermissionType.CAMERA.permission)
            },
            onDeny = {
                showRationale = false
                onPermissionDenied()
            }
        )
    }
}

/**
 * 🔍 Fonctions utilitaires pour vérifier l'état des permissions
 */
fun getCameraPermissionState(context: Context): PermissionState {
    val granted = ContextCompat.checkSelfPermission(
        context,
        PermissionType.CAMERA.permission
    ) == PackageManager.PERMISSION_GRANTED
    val rationale = shouldShowRequestPermissionRationale(context, PermissionType.CAMERA.permission)
    return PermissionState.from(granted, rationale)
}

fun getStoragePermissionState(context: Context): PermissionState {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionType.READ_MEDIA_IMAGES.permission
    } else {
        PermissionType.READ_EXTERNAL_STORAGE.permission
    }
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val rationale = shouldShowRequestPermissionRationale(context, permission)
    return PermissionState.from(granted, rationale)
}

/**
 * 🤔 Vérifie si on doit montrer une explication pour la permission
 */
private fun shouldShowRequestPermissionRationale(context: Context, permission: String): Boolean {
    return if (context is androidx.activity.ComponentActivity) {
        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
    } else {
        false
    }
}

/**
 * 📱 Vérifie si l'appareil supporte la caméra
 */
fun hasCameraFeature(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

/**
 * 🆕 Vérifie si l'appareil supporte le photo picker moderne (Android 13+)
 */
fun hasPhotoPickerFeature(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * 🔔 Fonctions pour les permissions de notifications
 */

/**
 * Hook composable pour demander la permission de notifications push
 * 
 * @param onPermissionGranted Callback appelé quand la permission est accordée
 * @param onPermissionDenied Callback appelé quand la permission est refusée
 */
@Composable
fun rememberNotificationPermissionLauncher(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    // Fonction pour lancer la demande de permission
    return remember {
        {
            // Vérifier si on est sur Android 13+ (seule version qui nécessite cette permission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (getNotificationPermissionState(context)) {
                    PermissionState.Granted -> {
                        onPermissionGranted()
                    }
                    else -> {
                        permissionLauncher.launch(PermissionType.POST_NOTIFICATIONS.permission)
                    }
                }
            } else {
                // Android < 13, pas besoin de permission
                onPermissionGranted()
            }
        }
    }
}

/**
 * 🔔 Dialogue d'explication pour les notifications push
 */
@Composable
fun NotificationPermissionRationaleDialog(
    onGrantPermission: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                text = "Notifications de messages",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = "Love2Love souhaite vous envoyer des notifications pour vous alerter quand votre partenaire vous envoie un message. Vous pouvez désactiver cela à tout moment dans les paramètres.",
                fontSize = 16.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Autoriser",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(
                    text = "Plus tard",
                    color = Color.Gray
                )
            }
        }
    )
}

/**
 * Vérifie l'état de la permission notifications
 */
fun getNotificationPermissionState(context: Context): PermissionState {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context, 
            PermissionType.POST_NOTIFICATIONS.permission
        ) == PackageManager.PERMISSION_GRANTED
        val rationale = shouldShowRequestPermissionRationale(context, PermissionType.POST_NOTIFICATIONS.permission)
        PermissionState.from(granted, rationale)
    } else {
        // Android < 13, toujours accordée
        PermissionState.Granted
    }
}

/**
 * Vérifie si les notifications sont supportées et nécessaires
 */
fun isNotificationPermissionRequired(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * 🎯 Composable tout-en-un pour la demande de permission notifications
 * Utilisation : Déclencher après le premier message envoyé par l'utilisateur
 */
@Composable
fun HandleFirstMessageNotificationPermission(
    shouldTrigger: Boolean,
    onPermissionRequestCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // État pour contrôler l'affichage du dialogue
    var showRationaleDialog by remember { mutableStateOf(false) }
    
    // Launcher pour demander la permission
    val requestNotificationPermission = rememberNotificationPermissionLauncher(
        onPermissionGranted = {
            Log.d("NotificationPermission", "🔔 Permission notifications accordée")
            onPermissionRequestCompleted()
        },
        onPermissionDenied = {
            Log.d("NotificationPermission", "❌ Permission notifications refusée")
            onPermissionRequestCompleted()
        }
    )
    
    // Déclencher la demande quand shouldTrigger passe à true
    LaunchedEffect(shouldTrigger) {
        if (shouldTrigger && isNotificationPermissionRequired()) {
            when (getNotificationPermissionState(context)) {
                PermissionState.Granted -> {
                    // Déjà accordée, rien à faire
                    onPermissionRequestCompleted()
                }
                PermissionState.Denied -> {
                    // Montrer le dialogue d'explication d'abord
                    showRationaleDialog = true
                }
                PermissionState.PermanentlyDenied -> {
                    // Refusée de façon permanente, pas de nouvelle demande
                    Log.d("NotificationPermission", "⚠️ Permission notifications refusée définitivement")
                    onPermissionRequestCompleted()
                }
            }
        } else if (shouldTrigger) {
            // Android < 13, pas de permission nécessaire
            onPermissionRequestCompleted()
        }
    }
    
    // Dialogue d'explication
    if (showRationaleDialog) {
        NotificationPermissionRationaleDialog(
            onGrantPermission = {
                showRationaleDialog = false
                requestNotificationPermission()
            },
            onDeny = {
                showRationaleDialog = false
                onPermissionRequestCompleted()
            }
        )
    }
}
