// QuestionsIntroStepView.kt
package your.package.name

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuestionsIntroStepView(
    viewModel: OnboardingViewModel
) = QuestionsIntroStepView(
    onContinue = {
        Log.d("QuestionsIntroStepView", "Bouton continuer pressé")
        viewModel.nextStep()
    }
)

@Composable
fun QuestionsIntroStepView(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val background = Color(0xFFF7F7FA) // équiv. Color(red:0.97, green:0.97, blue:0.98)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.questions_intro_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Image (équiv. Image("mima").resizable().aspectFit...)
            Image(
                painter = painterResource(id = R.drawable.mima),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            // Sous-titre style paywall
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(top = 30.dp)
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.questions_intro_subtitle),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Zone blanche collée en bas avec ombre
            Surface(
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp)
                ) {
                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFD267A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(horizontal = 30.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_continue),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Logs "onAppear"
        LaunchedEffect(Unit) {
            Log.d("QuestionsIntroStepView", "Vue apparue - Affichage de l'image: mima")
            Log.d(
                "QuestionsIntroStepView",
                "Titre de la page: \"${context.getString(R.string.questions_intro_title)}\""
            )
        }
    }
}
