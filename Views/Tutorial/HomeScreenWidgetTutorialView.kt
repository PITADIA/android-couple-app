package com.yourapp.ui.tutorial

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch

private val PinkPrimary = Color(0xFFFD267A)
private val PinkSecondary = Color(0xFFFF6B9D)

/**
 * Modèle d'étape du tutoriel.
 */
data class TutorialStep(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val imageRes: Int
)

/**
 * Écran principal du tutoriel "Home Screen Widget".
 *
 * @param onClose callback appelé quand l’utilisateur termine (équivaut à dismiss()).
 */
@Composable
fun HomeScreenWidgetTutorialScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Déclare les étapes (utilise strings.xml + drawables)
    val steps = remember {
        listOf(
            TutorialStep(
                titleRes = R.string.hold_home_screen,
                descriptionRes = R.string.hold_description,
                imageRes = R.drawable.etape5
            ),
            TutorialStep(
                titleRes = R.string.tap_plus_button,
                descriptionRes = R.string.plus_description,
                imageRes = R.drawable.etape6
            ),
            TutorialStep(
                titleRes = R.string.search_love2love_home,
                descriptionRes = R.string.search_home_description,
                imageRes = R.drawable.etape7
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (titre centré)
            Text(
                text = stringResource(id = R.string.home_screen_widget),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
            )

            // Indicateur de progression (points)
            DotsIndicator(
                totalDots = steps.size,
                selectedIndex = pagerState.currentPage,
                modifier = Modifier
                    .padding(top = 30.dp)
            )

            // Contenu principal (pager horizontal)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                TutorialStepCard(step = steps[page])
            }

            // Boutons de navigation
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                pagerState.animateScrollToPage(prev)
                            }
                        },
                        border = BorderStroke(2.dp, PinkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = PinkPrimary
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .height(50.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.previous),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val isLast = pagerState.currentPage >= steps.size - 1
                GradientButton(
                    text = if (isLast)
                        stringResource(id = R.string.done)
                    else
                        stringResource(id = R.string.continue_label),
                    onClick = {
                        scope.launch {
                            if (!isLast) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                onClose()
                            }
                        }
                    },
                    modifier = Modifier
                        .height(50.dp)
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Carte d’une étape : image + titre + description.
 */
@Composable
private fun TutorialStepCard(step: TutorialStep, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Image
        Image(
            painter = painterResource(id = step.imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 16.dp)
                .heightIn(min = 200.dp, max = 400.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Titre
        Text(
            text = stringResource(id = step.titleRes),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = stringResource(id = step.descriptionRes),
            fontSize = 16.sp,
            color = Color(0xFF5F5F5F),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * Indicateur de progression « …●●○… » animé.
 */
@Composable
private fun DotsIndicator(
    totalDots: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(totalDots) { index ->
            val targetColor =
                if (index <= selectedIndex) PinkPrimary
                else Color.LightGray.copy(alpha = 0.3f)

            val color by animateColorAsState(targetValue = targetColor, label = "dotColor")

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Bouton principal avec fond en dégradé (rose -> rose clair).
 * Utilise stringResource en appelant ce composable depuis une @Composable.
 */
@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brush = Brush.horizontalGradient(listOf(PinkPrimary, PinkSecondary))
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(),
        modifier = modifier
            .clip(CircleShape)
            .background(brush)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
