// LockScreenWidgetTutorialView.kt
package com.yourapp.ui.tutorial

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Immutable
data class TutorialStep(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val imageResId: Int
)

@Composable
fun LockScreenWidgetTutorialScreen(
    onDone: () -> Unit = {} // Appelle navController.popBackStack() ou finish() depuis l'appelant
) {
    val context = LocalContext.current
    val isFrench = remember {
        // Même logique que Swift: choisir l’asset selon la langue
        val lang = try {
            context.resources.configuration.locales[0]?.language
        } catch (_: Throwable) { null } ?: Locale.getDefault().language
        lang.startsWith("fr", ignoreCase = true)
    }

    val steps = remember(isFrench) {
        listOf(
            TutorialStep(
                titleRes = R.string.swipe_down,
                descriptionRes = R.string.swipe_description,
                imageResId = R.drawable.etape1
            ),
            TutorialStep(
                titleRes = R.string.tap_customize,
                descriptionRes = R.string.customize_description,
                imageResId = if (isFrench) R.drawable.etape2 else R.drawable.etape2en
            ),
            TutorialStep(
                titleRes = R.string.select_lock_screen,
                descriptionRes = R.string.lock_screen_description,
                imageResId = if (isFrench) R.drawable.etape3 else R.drawable.etape3en
            ),
            TutorialStep(
                titleRes = R.string.search_love2love,
                descriptionRes = R.string.search_description,
                imageResId = R.drawable.etape4
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val currentStep by remember { derivedStateOf { pagerState.currentPage } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Titre (équivalent du header sans bouton retour)
            Text(
                text = stringResource(R.string.lock_screen_widget),
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 40.dp)
                    .padding(horizontal = 20.dp)
            )

            // Indicateur de progression (pastilles)
            Row(
                modifier = Modifier.padding(top = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(steps.size) { index ->
                    val color by animateColorAsState(
                        targetValue = if (index <= currentStep) Color(0xFFFD267A) else Color.Black.copy(alpha = 0.3f),
                        animationSpec = tween(300),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                }
            }

            // Contenu principal (pager façon TabView/PageTabViewStyle)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                userScrollEnabled = true
            ) { page ->
                TutorialStepView(step = steps[page])
            }

            // Boutons de navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(currentStep - 1) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFFD267A)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.previous),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(currentStep + 1) }
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(25.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFD267A), Color(0xFFFF6B9D))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentStep < steps.size - 1)
                                stringResource(R.string.action_continue)
                            else
                                stringResource(R.string.done),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepView(step: TutorialStep) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Image(
            painter = painterResource(id = step.imageResId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(9f / 16f) // ajuste si besoin selon tes assets
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(id = step.titleRes),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(id = step.descriptionRes),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenWidgetTutorialPreview() {
    LockScreenWidgetTutorialScreen()
}
