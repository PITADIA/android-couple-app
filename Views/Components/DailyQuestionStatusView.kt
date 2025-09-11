// DailyQuestionStatusView.kt
// Jetpack Compose rewrite of the provided SwiftUI component
// Localization: uses strings.xml via stringResource(...)

package com.love2loveapp.ui.daily

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.love2loveapp.R

// --- Minimal models to mirror the Swift code ---
// Replace these with your real AppState / AppUser from your project

data class AppUser(val partnerId: String?)

data class AppState(val currentUser: AppUser?)

@Composable
fun DailyQuestionStatusView(
    appState: AppState,
    onStatusTap: (showPartnerMessageOnly: Boolean) -> Unit
) {
    val context = LocalContext.current

    var hasNotificationPermission by remember { mutableStateOf(false) }
    var isCheckingPermission by remember { mutableStateOf(true) }

    // Load permission status once (onAppear equivalent)
    LaunchedEffect(Unit) {
        isCheckingPermission = true
        hasNotificationPermission = hasPostNotificationsPermission(context)
        isCheckingPermission = false
        Log.d("DailyQuestionStatusView", "🔔 permission = $hasNotificationPermission")
    }

    val currentUser = appState.currentUser

    // Whether we should show the partner message (placeholder logic like in Swift)
    val shouldShowPartnerMessage: Boolean = remember(currentUser, hasNotificationPermission) {
        val hasPartner = currentUser?.partnerId?.isNotEmpty() == true
        hasPartner && hasNotificationPermission // TODO: check partner's notification state via Firebase
    }

    // Whether to show the permission flow
    val shouldShowPermissionFlow: Boolean = remember(currentUser, hasNotificationPermission) {
        (hasNotificationPermission == false) && (currentUser?.partnerId != null)
    }

    // Map the status to a localized string
    val notificationStatusText = when {
        currentUser == null -> stringResource(R.string.notifications_status_unknown)
        currentUser.partnerId == null -> stringResource(R.string.notifications_status_no_partner)
        !hasNotificationPermission -> stringResource(R.string.notifications_status_user_needs)
        shouldShowPartnerMessage -> stringResource(R.string.notifications_status_partner_needs)
        else -> stringResource(R.string.notifications_status_ready)
    }

    // UI
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.05f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (hasNotificationPermission) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                contentDescription = null,
                tint = if (hasNotificationPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )

            // The tappable "pill"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.95f)
            ) {
                Text(
                    text = notificationStatusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), clip = false)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .clickable(enabled = shouldShowPermissionFlow || shouldShowPartnerMessage) {
                            // Only trigger when the flow is relevant
                            if (shouldShowPermissionFlow) {
                                onStatusTap(shouldShowPartnerMessage)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyQuestionStatusViewPreview_NoPartner() {
    MaterialTheme {
        DailyQuestionStatusView(
            appState = AppState(currentUser = AppUser(partnerId = null)),
            onStatusTap = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyQuestionStatusViewPreview_WithPartner_NoPermission() {
    MaterialTheme {
        DailyQuestionStatusView(
            appState = AppState(currentUser = AppUser(partnerId = "partner_123")),
            onStatusTap = { showPartnerMessageOnly ->
                Log.d("Preview", "Tapped. showPartnerMessageOnly=$showPartnerMessageOnly")
            }
        )
    }
}

/*
====================================
strings.xml — required keys
====================================

<resources>
    <string name="notifications_status_unknown">Notifications status unknown</string>
    <string name="notifications_status_no_partner">Connect with your partner to enable reminders</string>
    <string name="notifications_status_user_needs">Enable notifications to get daily questions</string>
    <string name="notifications_status_partner_needs">Your partner needs to enable notifications</string>
    <string name="notifications_status_ready">Notifications ready</string>
</resources>

// Add localized variants in values-fr/strings.xml, etc.
*/
