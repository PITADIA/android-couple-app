package com.love2love.paywall

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.love2love.paywall.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionPaywallScreen(
    questionDay: Int,
    onOpenSubscription: (() -> Unit)? = null // si tu veux brancher ta logique Billing
) {
    val ctx = LocalContext.current
    val analytics = Firebase.analytics

    var showSubscriptionSheet by remember { mutableStateOf(false) }

    // == Analytics: Paywall vu ==
    LaunchedEffect(questionDay) {
        analytics.logEvent("paywall_viewed") {
            param("source", "daily_question_freemium")
            param("question_day", questionDay.toLong())
        }
        Log.d("Analytics", "📊 paywall_viewed - source=daily_question_freemium, day=$questionDay")
    }

    // Fond général gris clair (≈ 0.97 / 0.97 / 0.98)
    val pageBackground = Color(0xFFF7F7FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== Header =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 40.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.paywall_page_title_questions),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // ===== Contenu principal =====
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Titre principal
                Text(
                    text = stringResource(id = R.string.paywall_questions_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Carte de question floutée
                Box(modifier = Modifier.fillMaxWidth()) {
                    // ---- Carte ----
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        elevation = cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Header dégradé (Love2Love)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(red = 1f, green = 0.4f, blue = 0.6f),
                                                Color(red = 1f, green = 0.6f, blue = 0.8f)
                                            )
                                        )
                                    )
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Love2Love",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Corps de carte dégradé + texte flouté
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(red = 0.2f, green = 0.1f, blue = 0.15f),
                                                Color(red = 0.4f, green = 0.2f, blue = 0.3f),
                                                Color(red = 0.6f, green = 0.3f, blue = 0.2f)
                                            )
                                        )
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Quelle est la chose que tu apprécies le plus chez ton partenaire et que tu aimerais lui dire plus souvent ? Comment penses-tu que cela pourrait renforcer votre relation ?",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 28.sp,
                                        modifier = Modifier
                                            .padding(horizontal = 30.dp)
                                            .blur(8.dp) // flou appliqué au texte
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }

                    // ---- Overlay flou + cœur (cadenas visuel) ----
                    Column(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Spacer(modifier = Modifier.height(60.dp)) // laisser le header visible non flouté
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF332633).copy(alpha = 0.95f),
                                            Color(0xFF6A3A60).copy(alpha = 0.95f),
                                            Color(0xFF995B33).copy(alpha = 0.95f)
                                        )
                                    )
                                )
                                .blur(15.dp) // vrai effet de flou du fond
                        ) {
                            // Effet glassmorphism simple
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.18f))
                            )
                            Text(
                                text = "💕",
                                fontSize = 60.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Sous-titre
                Text(
                    text = stringResource(id = R.string.paywall_questions_subtitle),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ===== Bouton principal =====
            Button(
                onClick = {
                    showSubscriptionSheet = true
                    analytics.logEvent("cta_premium_clicked") {
                        param("source", "daily_question_paywall")
                        param("question_day", questionDay.toLong())
                    }
                },
                modifier = Modifier
                    .padding(bottom = 160.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.paywall_continue_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ===== BottomSheet de souscription =====
    if (showSubscriptionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSubscriptionSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            SubscriptionSheetContent(
                onClose = { showSubscriptionSheet = false },
                onContinue = {
                    onOpenSubscription?.invoke()
                    // tu peux déclencher ici ton flux Google Play Billing
                }
            )
        }
    }
}

@Composable
private fun SubscriptionSheetContent(
    onClose: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Love2Love Premium",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Débloque toutes les questions quotidiennes, sans limite, et soutiens notre travail 💕",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFD267A),
                contentColor = Color.White
            )
        ) {
            Text("Continuer")
        }
        TextButton(onClick = onClose) {
            Text("Plus tard")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyQuestionPaywallScreenPreview() {
    MaterialTheme {
        DailyQuestionPaywallScreen(questionDay = 4)
    }
}
