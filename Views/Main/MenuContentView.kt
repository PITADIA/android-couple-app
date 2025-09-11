// MenuContentView.kt
package com.yourapp.ui.menu

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.CardDefaults.outlinedCardColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yourapp.R

/**
 * Équivalent Kotlin/Compose de MenuContentView.swift
 *
 * - Fond gris clair (#F7F7FA) sur toute la vue
 * - ScrollView verticale
 * - Dégradé rose doux en haut (comme HomeContentView)
 * - "Sheet" (ModalBottomSheet) pour LocationPermissionFlow et WidgetsView
 *
 * Remplacement localisation:
 *   - Swift: challengeKey.localized(tableName: "DailyChallenges")
 *   - Android: stringResource(R.string.challenge_key) dans un @Composable
 *               ou LocalContext.current.getString(R.string.challenge_key) / context.getString(...) hors Compose
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuContentScreen(
    // Passe ton AppState/VM si nécessaire
    // appState: AppState,
) {
    val scrollState = rememberScrollState()
    var activeSheet by rememberSaveable { mutableStateOf<SheetType?>(null) }

    // Couleurs identiques à SwiftUI (Color(red: 0.97, green: 0.97, blue: 0.98))
    val backgroundGray = Color(0xFFF7F7FA)
    val pink = Color(0xFFFD267A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGray) // Fond gris clair identique
    ) {
        // Dégradé rose très doux en arrière-plan (en haut, ~350dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            pink.copy(alpha = 0.30f),
                            pink.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        )

        // Contenu scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Espace bas comme le padding(.bottom, 100) pour laisser la place à un éventuel menu
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuView(
                onLocationTutorialTap = { activeSheet = SheetType.LocationTutorial },
                onWidgetsTap = { activeSheet = SheetType.Widgets }
            )
        }

        // "Sheet" (équivalent .sheet(item:))
        if (activeSheet != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = sheetState
            ) {
                when (activeSheet) {
                    SheetType.LocationTutorial -> {
                        LaunchedEffect(Unit) {
                            Log.d("MenuContentView", "📍 MenuContentView: LocationPermissionFlow apparue depuis le menu")
                        }
                        // Intègre ici ta vraie vue Android équivalente
                        LocationPermissionFlow(
                            onClose = { activeSheet = null }
                        )
                    }

                    SheetType.Widgets -> {
                        LaunchedEffect(Unit) {
                            Log.d("MenuContentView", "📱 MenuContentView: WidgetsView apparue depuis le menu")
                        }
                        // Intègre ici ta vraie vue Android équivalente
                        WidgetsView(
                            onClose = { activeSheet = null }
                        )
                    }

                    null -> Unit
                }
            }
        }
    }
}

private enum class SheetType { LocationTutorial, Widgets }

/**
 * Équivalent de MenuView(...) côté SwiftUI.
 * Ici, je fournis une version simple en cartes. Remplace-la par ta grille/list si tu as déjà un design.
 */
@Composable
private fun MenuView(
    onLocationTutorialTap: () -> Unit,
    onWidgetsTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MenuCard(
            title = stringResource(R.string.menu_location_tutorial_title),
            subtitle = stringResource(R.string.menu_location_tutorial_subtitle),
            onClick = onLocationTutorialTap
        )
        MenuCard(
            title = stringResource(R.string.menu_widgets_title),
            subtitle = stringResource(R.string.menu_widgets_subtitle),
            onClick = onWidgetsTap
        )
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        elevation = cardElevation(defaultElevation = 2.dp),
        colors = outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Placeholders pour les écrans appelés en sheet.
 * Remplace par tes vraies implémentations Android dès que tu les as.
 */
@Composable
private fun LocationPermissionFlow(
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    // Exemple d’usage context.getString(...) hors Composable UI pur :
    // val someText = ctx.getString(R.string.some_key)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.menu_location_tutorial_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.menu_location_tutorial_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Text(text = stringResource(R.string.close))
        }
    }
}

@Composable
private fun WidgetsView(
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.menu_widgets_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.menu_widgets_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Text(text = stringResource(R.string.close))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMenuContentScreen() {
    MaterialTheme {
        MenuContentScreen()
    }
}
