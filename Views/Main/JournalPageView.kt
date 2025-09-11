// JournalPageScreen.kt
// package à adapter à ton projet
package your.package.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import your.package.R
import your.package.app.AppState // <- adapte l'import à l'emplacement réel de AppState

// Équivalent de @EnvironmentObject (SwiftUI)
val LocalAppState = staticCompositionLocalOf<AppState> {
    error("AppState non fourni.")
}

@Composable
fun JournalPageScreen(
    appState: AppState,
    modifier: Modifier = Modifier,
    bottomReservedSpace: Dp = 100.dp
) {
    // Fond gris clair identique à l'app (0.97, 0.97, 0.98) ≈ #F7F7FA
    val background = Color(0xFFF7F7FA)

    // Exemples d'accès aux traductions (strings.xml) :
    // 1) Via le contexte Android (comme demandé) :
    val context = LocalContext.current
    val journalTitleFromContext = context.getString(R.string.journal_title)
    // 2) Ou l’équivalent Compose :
    val journalTitle = stringResource(R.string.journal_title)

    // On laisse la vue occuper toute la surface (pas de TopAppBar => "navigationBarHidden(true)")
    CompositionLocalProvider(LocalAppState provides appState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(background)
        ) {
            // Optionnel : tenir compte des insets système pour ne pas chevaucher la nav bar
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (navBottom < bottomReservedSpace) bottomReservedSpace else navBottom

            // Appelle ton JournalView en Compose (implémenté ailleurs)
            JournalView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
            )
        }
    }
}

// Dans n'importe quel Composable enfant, tu peux récupérer l'état global comme en SwiftUI :
// val appState = LocalAppState.current
