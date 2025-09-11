import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoadingStepView(
    viewModel: OnboardingViewModel // même signature que sur iOS
) {
    // Fond gris clair (≈ 0.97, 0.97, 0.98)
    val background = Color(0xFFF7F7FA)

    // Libellés localisés via strings.xml (équivalent de context.getString)
    val messages = listOf(
        stringResource(R.string.loading_profile),
        stringResource(R.string.loading_preferences),
        stringResource(R.string.loading_experience)
    )

    var currentMessageIndex by remember { mutableIntStateOf(0) }

    // Logs d’apparition/disparition (équivalents des print en Swift)
    DisposableEffect(Unit) {
        Log.d("LoadingStepView", "🔥 Vue de chargement apparue")
        onDispose {
            Log.d("LoadingStepView", "🔥 Vue de chargement disparue")
        }
    }

    // Cycle des messages toutes les 5s, annulé automatiquement quand le composable quitte l’arbre
    LaunchedEffect(messages.size) {
        Log.d("LoadingStepView", "🔥 Début de la séquence de chargement")
        // SUPPRIMÉ: aucun auto-advance. L’avancement est géré manuellement (ex: viewModel.completeDataCollection()).
        while (true) {
            delay(5_000)
            currentMessageIndex = (currentMessageIndex + 1) % messages.size
            Log.d("LoadingStepView", "🔥 Changement de message: ${messages[currentMessageIndex]}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Espace entre barre de progression (éventuelle) et titre, harmonisé
            Spacer(modifier = Modifier.height(40.dp))

            // Premier Spacer pour centrer verticalement
            Spacer(modifier = Modifier.weight(1f))

            // Contenu principal centré
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color.Black,
                    strokeWidth = 4.dp
                )

                Crossfade(
                    targetState = currentMessageIndex,
                    animationSpec = tween(durationMillis = 500),
                    label = "messageCrossfade"
                ) { idx ->
                    Text(
                        text = messages[idx],
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Second Spacer pour centrer verticalement
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
