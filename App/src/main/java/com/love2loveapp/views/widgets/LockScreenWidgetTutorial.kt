package com.love2loveapp.views.widgets

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import com.love2loveapp.R
import kotlinx.coroutines.delay

/**
 * 📱 LockScreenWidgetTutorial selon RAPPORT_WIDGETS_COMPLET.md
 * Tutoriel écran de verrouillage avec 4 étapes et images localisées
 * 
 * Images utilisées avec logique de localisation :
 * - etape1 : Swipe down (350x220dp, non localisé)
 * - etape2/etape2en : Tap customize (FR/EN, 350x220dp)
 * - etape3/etape3en : Select lock screen (FR/EN, 350x220dp)
 * - etape4 : Search Love2Love (350x220dp, non localisé)
 * 
 * Design : Fond sombre selon le rapport
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LockScreenWidgetTutorial(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    
    // 🎬 Animation entrance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // Fond blanc comme HomeScreen
    ) {
        // Header avec titre selon le rapport
        Column(
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = stringResource(R.string.lock_screen_widget),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 🎯 INDICATEURS PROGRESSION
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(600)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { step ->
                    val isActive = pagerState.currentPage >= step
                    
                    AnimatedVisibility(
                        visible = isActive,
                        enter = scaleIn(animationSpec = tween(400, delayMillis = step * 100))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = Color(0xFFFD267A),
                                    shape = CircleShape
                                )
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = !isActive,
                        enter = fadeIn(animationSpec = tween(400))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                    }
                    
                    if (step < 3) {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }
        }
        
        // 📱 CONTENU PRINCIPAL - HORIZONTAL PAGER
        // TabView avec TutorialStepView selon le rapport
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val languageCode = java.util.Locale.getDefault().language
            val etape2ImageName = if (languageCode == "fr") "etape2" else "etape2en"
            val etape3ImageName = if (languageCode == "fr") "etape3" else "etape3en"
            
            when (page) {
                0 -> LockTutorialStep(
                    title = stringResource(R.string.swipe_down),
                    description = stringResource(R.string.swipe_description),
                    imageName = "etape1" // Non localisé selon le rapport
                )
                1 -> LockTutorialStep(
                    title = stringResource(R.string.tap_customize),
                    description = stringResource(R.string.customize_description),
                    imageName = etape2ImageName // FR/EN selon le rapport
                )
                2 -> LockTutorialStep(
                    title = stringResource(R.string.select_lock_screen),
                    description = stringResource(R.string.lock_screen_description),
                    imageName = etape3ImageName // FR/EN selon le rapport
                )
                3 -> LockTutorialStep(
                    title = stringResource(R.string.search_love2love),
                    description = stringResource(R.string.search_description),
                    imageName = "etape4" // Non localisé selon le rapport
                )
            }
        }
        
        // Boutons de navigation selon le rapport
        LockNavigationButtons(
            currentStep = pagerState.currentPage,
            totalSteps = 4,
            onPrevious = {
                if (pagerState.currentPage > 0) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            },
            onNext = {
                if (pagerState.currentPage < 3) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onFinish()
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 40.dp)
        )
    }
}

// LockTutorialStep selon le rapport
@Composable
private fun LockTutorialStep(
    title: String,
    description: String,
    imageName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Image du tutoriel selon le rapport - Images etape1, etape2/etape2en, etape3/etape3en, etape4
        val imageResId = when(imageName) {
            "etape1" -> R.drawable.etape1
            "etape2" -> R.drawable.etape2
            "etape2en" -> R.drawable.etape2en
            "etape3" -> R.drawable.etape3
            "etape3en" -> R.drawable.etape3en
            "etape4" -> R.drawable.etape4
            else -> R.drawable.etape1 // Fallback
        }
        
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp), // maxHeight selon le rapport
            contentScale = ContentScale.Fit
        )
        
        // Texte explicatif selon le rapport
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = description,
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

// LockNavigationButtons selon le rapport
@Composable
private fun LockNavigationButtons(
    currentStep: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bouton Précédent (si currentStep > 0)
        if (currentStep > 0) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFD267A)
                ),
                border = BorderStroke(2.dp, Color(0xFFFD267A)),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.previous),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Bouton Continuer/Terminé
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFD267A)
            ),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text(
                text = if (currentStep < totalSteps - 1) {
                    stringResource(R.string.continue_button)
                } else {
                    stringResource(R.string.done)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
