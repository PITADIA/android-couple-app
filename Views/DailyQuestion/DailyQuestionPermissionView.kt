@file:Suppress("UnusedImport")

package com.love2love.dailyquestions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.love2love.R
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Écran "Permission notifications" pour les Questions du Jour (Android / Compose).
 *
 * - Localisation via strings.xml (stringResource()).
 * - Permission POST_NOTIFICATIONS (Android 13+).
 * - Fallback vers les réglages si refus.
 * - Carte d'exemple avec dégradés, bouton principal dégradé.
 */
@Composable
fun DailyQuestionPermissionScreen(
    onClose: () -> Unit,
    onPermissionGranted: () -> Unit,
    onContinueWithoutPermissions: () -> Unit
) {
    val context = LocalContext.current

    var isRequesting by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    // Lanceur de demande de permission pour Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequesting = false
        if (granted) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                FCMService.requestTokenAndSave(context)
                onPermissionGranted()
            } else {
                // Notifications globalement désactivées pour l’app
                showPermissionDeniedDialog = true
            }
        } else {
            showPermissionDeniedDialog = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.ScreenBg),
        color = AppColors.ScreenBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header avec bouton fermer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.padding(start = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Contenu principal
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Titre + sous-titre
                    Text(
                        text = stringResource(id = R.string.daily_question_permission_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.daily_question_permission_subtitle),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                    Spacer(Modifier.height(40.dp))

                    // Carte d'exemple
                    ExampleQuestionCard(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .height(300.dp)
                            .fillMaxWidth()
                    )

                    Spacer(Modifier.height(40.dp))

                    // Bénéfices
                    Text(
                        text = stringResource(id = R.string.daily_question_benefits_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.daily_question_benefits_description),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }

                // Bas d'écran : actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bouton principal (dégradé)
                    GradientButton(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isRequesting,
                        onClick = {
                            isRequesting = true
                            requestNotificationPermission(
                                context = context,
                                onAlreadyEnabled = {
                                    isRequesting = false
                                    FCMService.requestTokenAndSave(context)
                                    onPermissionGranted()
                                },
                                onNeedRequest = {
                                    // Android 13+ : lancer la demande
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onNeedOpenSettings = {
                                    isRequesting = false
                                    showPermissionDeniedDialog = true
                                }
                            )
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(end = 12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(end = 12.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.activate_notifications_button),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // "Continuer sans notifications"
                    TextButton(
                        onClick = {
                            onContinueWithoutPermissions()
                            onClose()
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.continue_without_notifications_button),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black.copy(alpha = 0.7f),
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Texte explicatif
                    Text(
                        text = stringResource(id = R.string.notification_permission_explanation),
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            }

            // Alerte permission refusée
            if (showPermissionDeniedDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDeniedDialog = false },
                    title = {
                        Text(text = stringResource(id = R.string.notification_permission_denied_title))
                    },
                    text = {
                        Text(text = stringResource(id = R.string.notification_permission_denied_message))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            openAppNotificationSettings(context)
                            showPermissionDeniedDialog = false
                        }) {
                            Text(text = stringResource(id = R.string.settings_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionDeniedDialog = false }) {
                            Text(text = stringResource(id = R.string.cancel_button))
                        }
                    }
                )
            }
        }
    }
}

/* ----------------------- UI components & helpers ----------------------- */

@Composable
private fun GradientButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    // Astuce : Button transparent + Box avec background dégradé
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AppColors.PinkStart, AppColors.OrangeEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

@Composable
private fun ExampleQuestionCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .shadow(8.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(AppColors.PinkStart, AppColors.OrangeEnd)
                    )
                )
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.daily_question_example_header),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Corps
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 0.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF331A26), // ~ (0.2, 0.1, 0.15)
                            Color(0xFF66334D), // ~ (0.4, 0.2, 0.3)
                            Color(0xFF994D33)  // ~ (0.6, 0.3, 0.2)
                        )
                    )
                )
                .padding(horizontal = 30.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.daily_question_example_text),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .padding(bottom = 30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Remplace par ton asset : R.drawable.leetchi2
                    Image(
                        painter = painterResource(id = R.drawable.leetchi2),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Love2Love",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private object AppColors {
    val ScreenBg = Color(0xFFF7F7FA) // ~ (0.97, 0.97, 0.98)
    val PinkStart = Color(0xFFFD267A)
    val OrangeEnd = Color(0xFFFF655B)
}

/* ----------------------- Permission & settings helpers ----------------------- */

private fun requestNotificationPermission(
    context: Context,
    onAlreadyEnabled: () -> Unit,
    onNeedRequest: () -> Unit,
    onNeedOpenSettings: () -> Unit
) {
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (!notificationsEnabled) {
        // Même si la permission Android 13+ est accordée, l’utilisateur peut avoir coupé les notifs pour l’app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (granted) {
                // Permission OK mais notifications désactivées pour l’app
                onNeedOpenSettings()
            } else {
                // Besoin de déclencher la demande système (Android 13+)
                onNeedRequest()
            }
        } else {
            // < Android 13 : pas de runtime permission, mais notifications coupées -> ouvrir réglages
            onNeedOpenSettings()
        }
    } else {
        // Notifications déjà actives
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                onAlreadyEnabled()
            } else {
                onNeedRequest()
            }
        } else {
            onAlreadyEnabled()
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback : page infos de l'application
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }
}

/* ----------------------- Firebase token helper ----------------------- */

private object FCMService {
    fun requestTokenAndSave(context: Context) {
        // Exemple simplifié : récupère le token FCM et log / envoie à ton backend
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result ?: return@addOnCompleteListener
                    // TODO: sauvegarder token côté serveur / Firestore
                    android.util.Log.d("FCMService", "FCM token = $token")
                } else {
                    android.util.Log.e("FCMService", "Erreur FCM token", task.exception)
                }
            }
    }
}

/* ----------------------- Preview ----------------------- */

@Preview(showBackground = true)
@Composable
private fun PreviewDailyQuestionPermissionScreen() {
    MaterialTheme {
        DailyQuestionPermissionScreen(
            onClose = {},
            onPermissionGranted = {},
            onContinueWithoutPermissions = {}
        )
    }
}
