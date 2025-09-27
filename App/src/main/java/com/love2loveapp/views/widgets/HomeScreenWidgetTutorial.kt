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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.launch
import com.love2loveapp.R
import kotlinx.coroutines.delay

/**
 * 📱 HomeScreenWidgetTutorial selon RAPPORT_WIDGETS_COMPLET.md
 * Tutoriel écran d'accueil avec 3 étapes et images spécifiques
 * 
 * Images utilisées :
 * - etape5 : Hold home screen (350x220dp)
 * - etape6 : Tap plus button (350x220dp) 
 * - etape7 : Search Love2Love home (350x220dp)
 * 
 * Design : Fond blanc complet selon le rapport
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreenWidgetTutorial(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    
    // 🎬 Animation entrance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200) // Petit délai pour animation entrée
        isVisible = true
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // Fond complètement blanc selon le rapport
    ) {
        // Header avec titre selon le rapport
        Column(
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = stringResource(R.string.home_screen_widget),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Indicateur de progression selon le rapport
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                Circle(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (index <= pagerState.currentPage) {
                                Color(0xFFFD267A)
                            } else {
                                Color.Gray.copy(alpha = 0.3f)
                            },
                            shape = CircleShape
                        )
                )
                if (index < 2) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        
        // 📱 CONTENU PRINCIPAL - HORIZONTAL PAGER
        // TabView avec TutorialStepView selon le rapport
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> HomeTutorialStep(
                    title = stringResource(R.string.hold_home_screen),
                    description = stringResource(R.string.hold_description),
                    imageName = "etape5" // Image 350x220dp selon le rapport
                )
                1 -> HomeTutorialStep(
                    title = stringResource(R.string.tap_plus_button),
                    description = stringResource(R.string.plus_description),
                    imageName = "etape6" // Image 350x220dp selon le rapport
                )
                2 -> HomeTutorialStep(
                    title = stringResource(R.string.search_love2love_home),
                    description = stringResource(R.string.search_home_description),
                    imageName = "etape7" // Image 350x220dp selon le rapport
                )
            }
        }
        
        // Boutons de navigation selon le rapport
        HomeNavigationButtons(
            currentStep = pagerState.currentPage,
            totalSteps = 3,
            onPrevious = {
                if (pagerState.currentPage > 0) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            },
            onNext = {
                if (pagerState.currentPage < 2) {
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

// TutorialStepView selon le rapport
@Composable
private fun HomeTutorialStep(
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
        
        // Image du tutoriel selon le rapport - Images etape5, etape6, etape7
        val imageResId = when(imageName) {
            "etape5" -> R.drawable.etape5
            "etape6" -> R.drawable.etape6
            "etape7" -> R.drawable.etape7
            else -> R.drawable.etape5 // Fallback
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

// HomeNavigationButtons selon le rapport
@Composable
private fun HomeNavigationButtons(
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

@Composable
private fun Circle(modifier: Modifier) {
    Box(modifier = modifier)
}
