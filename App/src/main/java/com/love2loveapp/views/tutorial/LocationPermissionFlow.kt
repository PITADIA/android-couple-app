package com.love2loveapp.views.tutorial

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.love2loveapp.R

/**
 * 📍 LocationPermissionFlow - Tutoriel permission localisation
 * 
 * Équivalent de LocationPermissionFlow iOS:
 * - Explication importance localisation pour distance partenaire
 * - Boutons d'action pour activer ou skiper
 * - Design cohérent avec thème Love2Love
 * - Navigation via callbacks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPermissionFlow(
    onPermissionGranted: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isFromMenu: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Vérification permissions en temps réel
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Launcher pour demander les permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            Log.d("LocationPermissionFlow", "✅ Permissions localisation accordées")
            hasLocationPermission = true
            onPermissionGranted()
        } else {
            Log.w("LocationPermissionFlow", "❌ Permissions localisation refusées")
        }
    }
    
    // Si permission déjà accordée, fermer automatiquement
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            onPermissionGranted()
        }
    }
    
    // Background identique à PartnerLocationMessageScreen: gris clair + gradient rose doux
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F7F8), // Gris clair iOS
            Color(0xFFFDEEF4)  // Rose doux
        )
    )
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp) // spacing: 30 comme dans le document
            ) {
                Spacer(modifier = Modifier.height(60.dp)) // Top spacing
                
                // Titre selon les clés du document
                Text(
                    text = stringResource(R.string.widget_enable_your_location),
                    fontSize = 28.sp, // .font(.system(size: 28, weight: .bold))
                    fontWeight = FontWeight.Bold,
                    color = Color.Black, // .foregroundColor(.black)
                    textAlign = TextAlign.Center
                )
                
                // Description (peut rester en dur car pas de clé spécifique dans le document)
                Text(
                    text = "Pour calculer la distance qui vous sépare de votre partenaire, nous avons besoin d'accéder à votre localisation.\n\nCette information reste privée et n'est partagée qu'avec votre partenaire connecté.",
                    fontSize = 16.sp, // .font(.system(size: 16))
                    color = Color.Black.copy(alpha = 0.7f), // .foregroundColor(.black.opacity(0.7))
                    textAlign = TextAlign.Center, // .multilineTextAlignment(.center)
                    modifier = Modifier.padding(horizontal = 30.dp) // .padding(.horizontal, 30)
                )
                
                // Icône localisation comme dans le document
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp), // .font(.system(size: 80, weight: .medium))
                    tint = Color.Black // .foregroundColor(.black)
                )
                
                Spacer(modifier = Modifier.weight(1f)) // Push button to bottom
                
                // Bouton principal avec clé du document
                Button(
                    onClick = {
                        Log.d("LocationPermissionFlow", "🔄 Demande permissions localisation système...")
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A) // Color(hex: "#FD267A")
                    ),
                    shape = RoundedCornerShape(28.dp), // .clipShape(RoundedRectangle(cornerRadius: 28))
                    modifier = Modifier
                        .fillMaxWidth() // .frame(maxWidth: .infinity, height: 56)
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.continue_button), // Clé du document
                        fontSize = 18.sp, // .font(.system(size: 18, weight: .semibold))
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White // .foregroundColor(.white)
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp)) // Bottom spacing
            }
        }
    }
}
