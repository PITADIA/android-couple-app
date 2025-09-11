@file:Suppress("unused")

package com.yourapp.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourapp.R

/**
 * Kotlin/Compose conversion of SwiftUI `HomeContentView`.
 *
 * Notes:
 * - iOS `EnvironmentObject` -> explicit parameter [appState].
 * - iOS `@Binding var activeSheet: SheetType?` -> [activeSheet] as a hoisted [MutableState].
 * - `NSLocalizedString(...)` -> Compose `stringResource(R.string.key)` (or Context.getString in helpers).
 * - Layout: ZStack -> Box, ScrollView -> Column + verticalScroll.
 * - Gradient header reproduced with a top overlay Box.
 */
@Composable
fun HomeContentView(
    appState: AppState,
    activeSheet: MutableState<SheetType?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val hasConnectedPartner by remember(appState.currentUser?.partnerId) {
        val partnerId = appState.currentUser?.partnerId.orEmpty()
        androidx.compose.runtime.mutableStateOf(partnerId.isNotEmpty())
    }

    Box(
        modifier
            .fillMaxSize()
            // Swift: Color(red: 0.97, green: 0.97, blue: 0.98)
            .background(Color(0xFFF7F7FA))
    ) {
        // --- Pink top gradient background (height 350dp) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.30f),
                            Color(0xFFFD267A).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // --- Scrollable content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // Space for bottom navigation
        ) {
            // Partner distance section (replaces logo)
            PartnerDistanceView(
                onPartnerAvatarTap = {
                    activeSheet.value = SheetType.PartnerManagement
                },
                onDistanceTap = { showPartnerMessageOnly ->
                    activeSheet.value = if (showPartnerMessageOnly) {
                        SheetType.PartnerLocationMessage
                    } else {
                        SheetType.LocationPermission
                    }
                }
            )
                .then(Modifier.padding(top = 100.dp)) // More space from status bar

            // Partner invite section if not connected
            if (!hasConnectedPartner) {
                PartnerInviteView(onInviteTap = {
                    activeSheet.value = SheetType.PartnerManagement
                })
                    .then(Modifier.padding(top =  (-15).dp)) // pull closer to distance card
            }

            // Category list (rectangular style). Freemium access handled elsewhere.
            Column(Modifier.padding(horizontal = 20.dp)) {
                QuestionCategory.categories.forEachIndexed { index, category ->
                    CategoryListCardView(
                        category = category,
                        onClick = {
                            println("🔥 HomeContentView: Catégorie sélectionnée: ${'$'}{category.title}")
                            activeSheet.value = SheetType.Questions(category)
                        }
                    )
                    if (index < QuestionCategory.categories.lastIndex) {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // Widgets section
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = R.string.widgets),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.weight(1f))
                }

                WidgetPreviewSection(onWidgetTap = {
                    println("📱 HomeContentView: Carte widget tappée, ouverture de la page widgets")
                    activeSheet.value = SheetType.Widgets
                })
            }

            // Couple statistics
            CoupleStatisticsView(appState = appState)
                .then(Modifier.padding(top = 30.dp))
        }
    }
}

// --- Helper: Premium categories subtitle (kept for parity with Swift) ---
fun getPremiumCategoriesSubtitle(context: Context, appState: AppState): String? {
    val isSubscribed = appState.currentUser?.isSubscribed ?: false
    return if (isSubscribed) {
        context.getString(R.string.premium_categories_subscribed)
    } else {
        context.getString(R.string.premium_categories_not_subscribed)
    }
}

// -----------------------------
// Models & placeholders
// -----------------------------

/** Minimal user + app state to match usage in this screen */
data class User(
    val id: String? = null,
    val partnerId: String? = null,
    val isSubscribed: Boolean = false,
)

class AppState(
    val currentUser: User? = null,
    // Add other services as needed (journalService, favoritesService, etc.)
)

/** Categories used on the Home screen */
@Suppress("MemberVisibilityCanBePrivate")
data class QuestionCategory(
    val id: String,
    val title: String, // You can switch to @StringRes if you store titles in strings.xml
) {
    companion object {
        // Provide your real categories here
        val categories: List<QuestionCategory> = emptyList()
    }
}

/** Hoisted destination/sheet types (parity with Swift `SheetType`) */
sealed class SheetType {
    data class Questions(val category: QuestionCategory) : SheetType()
    object PartnerManagement : SheetType()
    object PartnerLocationMessage : SheetType()
    object LocationPermission : SheetType()
    object Widgets : SheetType()
}

// -----------------------------
// UI child placeholders (replace with your real implementations)
// -----------------------------

@Composable
fun PartnerDistanceView(
    onPartnerAvatarTap: () -> Unit,
    onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with your real composable
    Spacer(modifier.height(1.dp))
}

@Composable
fun PartnerInviteView(
    onInviteTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with your real composable
    Spacer(modifier.height(1.dp))
}

@Composable
fun CategoryListCardView(
    category: QuestionCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with your real composable
    Spacer(modifier.height(120.dp))
}

@Composable
fun WidgetPreviewSection(
    onWidgetTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with your real composable
    Spacer(modifier.height(140.dp))
}

@Composable
fun CoupleStatisticsView(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with your real composable
    Spacer(modifier.height(100.dp))
}
