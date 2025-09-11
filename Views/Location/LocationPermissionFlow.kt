package com.yourapp.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourapp.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocationOn

/**
 * Android/Compose rewrite of the SwiftUI `LocationPermissionFlow`.
 *
 * Notes on i18n: all strings come from `strings.xml` via stringResource(R.string.*).
 * If you ever need a string outside of a @Composable, use
 * `val text = context.getString(R.string.key)`.
 */
@Composable
fun LocationPermissionFlow(
    onFinished: () -> Unit = {}
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F9))
    ) {
        // Subtle pink gradient background (top-left -> bottom-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.03f),
                            Color(0xFFFF655B).copy(alpha = 0.02f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(1.dp))

            when (currentStep) {
                0 -> LocationServiceExplanationView(onContinue = { currentStep = 1 })
                1 -> LocationPermissionView(onContinue = { currentStep = 2 })
                2 -> LocationPartnerExplanationView(onContinue = onFinished)
            }

            Spacer(Modifier.height(1.dp))
        }
    }
}

// ————————————————————————————————————————————————————————————————————————
// Step 1 — Service explanation
// ————————————————————————————————————————————————————————————————————————
@Composable
private fun LocationServiceExplanationView(
    onContinue: () -> Unit
) {
    val title = stringResource(id = R.string.location_permission)
    val subtitle = stringResource(id = R.string.location_services_needed)
    val cardTitle = stringResource(id = R.string.location_status)
    val continueText = stringResource(id = R.string.continue_button)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(50.dp))

        InfoCard(
            leadingIcon = Icons.Filled.Handshake,
            leadingTint = Color.White,
            leadingBg = Color(0xFF2196F3), // blue
            title = cardTitle,
            statusIcon = Icons.Filled.CheckCircle,
            statusTint = Color(0xFF2E7D32)
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = continueText, onClick = onContinue)

        Spacer(Modifier.height(40.dp))
    }
}

// ————————————————————————————————————————————————————————————————————————
// Step 2 — Permission request
// ————————————————————————————————————————————————————————————————————————
@Composable
private fun LocationPermissionView(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val permissionFine = Manifest.permission.ACCESS_FINE_LOCATION
    val permissionCoarse = Manifest.permission.ACCESS_COARSE_LOCATION

    var hasRequested by rememberSaveable { mutableStateOf(false) }

    val requestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasRequested = true
        val granted = result.values.any { it }
        // Mirror iOS logic: proceed if granted OR the request was at least shown.
        if (granted || hasRequested) onContinue()
    }

    val granted by remember {
        derivedStateOf {
            isGranted(context, permissionFine) || isGranted(context, permissionCoarse)
        }
    }

    val servicesEnabled by remember {
        derivedStateOf { isLocationServicesEnabled(context) }
    }

    val permanentlyDenied by remember {
        derivedStateOf {
            !granted && hasRequested && activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, permissionFine) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(it, permissionCoarse)
            } == true
        }
    }

    val title = stringResource(id = R.string.permission)
    val subtitle = stringResource(id = R.string.location_services_needed)
    val cardTitle = stringResource(id = R.string.location_access_permission)
    val instructionsTitle = stringResource(id = R.string.how_to_enable_location)
    val step1 = stringResource(id = R.string.tap_authorize)
    val step2 = stringResource(id = R.string.select_allow_when_active)

    val buttonText = when {
        granted -> stringResource(id = R.string.continue_button)
        permanentlyDenied -> stringResource(id = R.string.open_settings_button)
        else -> stringResource(id = R.string.authorize_button)
    }

    val buttonColor = when {
        permanentlyDenied -> Color(0xFFFF9800) // orange
        else -> Color(0xFFFD267A) // pink
    }

    val (statusIcon, statusTint) = when {
        granted -> Icons.Filled.CheckCircle to Color(0xFF2E7D32) // green
        !granted && !hasRequested -> Icons.Outlined.Help to Color(0xFFFFA000) // orange
        else -> Icons.Filled.Close to Color(0xFFD32F2F) // red (denied)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(50.dp))

        InfoCard(
            leadingIcon = Icons.Filled.LocationOn,
            leadingTint = Color.White,
            leadingBg = Color(0xFF2196F3),
            title = cardTitle,
            statusIcon = statusIcon,
            statusTint = statusTint
        )

        Spacer(Modifier.height(40.dp))

        // Instructions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = instructionsTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Spacer(Modifier.height(12.dp))

            InstructionRow(iconTint = Color(0xFFFFA000), text = step1)
            Spacer(Modifier.height(8.dp))
            InstructionRow(iconTint = Color(0xFF2E7D32), text = step2)
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = buttonText,
            containerColor = buttonColor,
            onClick = {
                when {
                    granted -> onContinue()
                    permanentlyDenied -> {
                        // If location services are off, route to location settings first, else app settings
                        if (!servicesEnabled) openLocationSourceSettings(context) else openAppSettings(context)
                    }
                    else -> {
                        hasRequested = true
                        requestLauncher.launch(arrayOf(permissionFine, permissionCoarse))
                    }
                }
            }
        )

        Spacer(Modifier.height(40.dp))
    }
}

// ————————————————————————————————————————————————————————————————————————
// Step 3 — Partner explanation
// ————————————————————————————————————————————————————————————————————————
@Composable
private fun LocationPartnerExplanationView(
    onContinue: () -> Unit
) {
    val title = stringResource(id = R.string.partner_turn)
    val subtitle = stringResource(id = R.string.partner_location_request)
    val continueText = stringResource(id = R.string.continue_button)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(50.dp))

        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Black
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = continueText, onClick = onContinue)

        Spacer(Modifier.height(40.dp))
    }
}

// ————————————————————————————————————————————————————————————————————————
// Small UI building blocks
// ————————————————————————————————————————————————————————————————————————
@Composable
private fun InfoCard(
    leadingIcon: ImageVector,
    leadingTint: Color,
    leadingBg: Color,
    title: String,
    statusIcon: ImageVector,
    statusTint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(leadingBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(leadingIcon, contentDescription = null, tint = leadingTint)
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun InstructionRow(
    iconTint: Color,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            // Use a simple dot with tint to avoid missing icon variants
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(iconTint)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    containerColor: Color = Color(0xFFFD267A),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

// ————————————————————————————————————————————————————————————————————————
// Helpers
// ————————————————————————————————————————————————————————————————————————
private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openLocationSourceSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// ————————————————————————————————————————————————————————————————————————
// Preview
// ————————————————————————————————————————————————————————————————————————
@Preview(showBackground = true)
@Composable
private fun PreviewLocationPermissionFlow() {
    MaterialTheme {
        LocationPermissionFlow()
    }
}
