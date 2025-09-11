package com.love2loveapp.ui.components

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kotlin/Compose rewrite of the SwiftUI CategoryCardView.
 *
 * ✅ Uses Android's standard localization via strings.xml.
 *    - Titles/Subtitles are provided as @StringRes ids and read with stringResource().
 * ✅ Mirrors the freemium gating + logging behavior.
 * ✅ Visuals: black rounded card with white stroke, emoji on top, centered texts, optional lock.
 */
@Composable
fun CategoryCardView(
    category: QuestionCategory,
    appState: AppState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
) {
    val titleText = stringResource(id = category.titleRes)
    val subtitleText = stringResource(id = category.subtitleRes)

    val isPremiumLocked = category.isPremium && (appState?.currentUser?.isSubscribed != true)
    val freemiumManager = appState?.freemiumManager

    val tag = "CategoryCardView"

    Surface(
        modifier = modifier
            .clickable {
                Log.d(tag, "🔥 CategoryCardView: Tap détecté sur $titleText")
                Log.d(tag, "🔥 Category tap: DEBUT GESTION")
                Log.d(tag, "🔥 Category tap: - Catégorie: $titleText")
                Log.d(tag, "🔥 Category tap: - FreemiumManager disponible: ${freemiumManager != null}")

                if (freemiumManager != null) {
                    Log.d(tag, "🔥 Category tap: APPEL handleCategoryTap")
                    Log.d(tag, "🔥 Category tap: FreemiumManager trouvé: $freemiumManager")
                    Log.d(tag, "🔥 Category tap: Avant appel handleCategoryTap")

                    freemiumManager.handleCategoryTap(category) {
                        Log.d(tag, "🔥 Category tap: CALLBACK EXECUTE - ACCES AUTORISE")
                        onClick()
                    }

                    Log.d(tag, "🔥 Category tap: Après appel handleCategoryTap")
                } else {
                    Log.d(tag, "🔥 Category tap: FREEMIUM MANAGER MANQUANT - FALLBACK")
                    Log.d(tag, "🔥 Category tap: appState.freemiumManager: ${appState?.freemiumManager}")
                    onClick()
                }
            },
        color = Color.Black,
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emoji
            Text(
                text = category.emoji,
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )

            // Title
            Text(
                text = titleText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White,
                lineHeight = 22.sp
            )

            // Subtitle + optional lock if premium and not subscribed
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFB0B0B0), // gray
                        lineHeight = 16.sp
                    )

                    if (isPremiumLocked) {
                        Text(
                            text = " 🔒",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// --- Minimal type contracts to align with your existing architecture ---
// Replace with your real implementations.

data class QuestionCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val emoji: String,
    val isPremium: Boolean
)

interface FreemiumManager {
    fun handleCategoryTap(category: QuestionCategory, onAuthorized: () -> Unit)
}

data class AppUser(val isSubscribed: Boolean)

data class AppState(
    val currentUser: AppUser? = null,
    val freemiumManager: FreemiumManager? = null
)

/*
strings.xml (example)
---------------------------------
<resources>
    <string name="category_fun_title">Moments de complicité</string>
    <string name="category_fun_subtitle">Riez et jouez ensemble</string>
</resources>

Usage example
---------------------------------
val sampleCategory = QuestionCategory(
    titleRes = R.string.category_fun_title,
    subtitleRes = R.string.category_fun_subtitle,
    emoji = "🎯",
    isPremium = true
)

CategoryCardView(
    category = sampleCategory,
    appState = AppState(currentUser = AppUser(isSubscribed = false), freemiumManager = object: FreemiumManager {
        override fun handleCategoryTap(category: QuestionCategory, onAuthorized: () -> Unit) { onAuthorized() }
    }),
    onClick = { /* navigate */ }
)
*/
