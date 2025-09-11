// Package name can be adjusted to your project structure
package com.love2love.ui.packs

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2love.R

/**
 * Kotlin/Compose recode of the SwiftUI views:
 *  - PackCompletionView
 *  - NewPackRevealView
 *
 * IMPORTANT: All localized strings are pulled from Android's strings.xml via stringResource(R.string.*)
 * (equivalent to context.getString). See the string keys list at the bottom of this file.
 */

@Composable
fun PackCompletionView(
    packNumber: Int,
    onTapForSurprise: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Infinite scale animation for the flame emoji, from 1.0f to 1.2f
    val infiniteTransition = rememberInfiniteTransition(label = "flame-scale")
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame-scale-anim"
    )

    LaunchedEffect(Unit) {
        Log.d("PackCompletionView", "🔥 PackCompletionView: Pack $packNumber terminé !")
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Title block
            Column(
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.congratulations_pack),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.pack_completed),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.pack_name),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Animated flame emoji as a button
            Text(
                text = "🔥",
                fontSize = 120.sp,
                color = Color.White,
                modifier = Modifier
                    .scale(flameScale)
                    .clickable(onClick = onTapForSurprise)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Two-line mixed-weight message
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val line1 = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(stringResource(R.string.tap_on_me))
                        append(" ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.on_me))
                    }
                }
                Text(
                    text = line1,
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                val line2 = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(stringResource(R.string.for_surprise))
                        append(" ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.surprise))
                        append(" ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(stringResource(R.string.exclamation))
                    }
                }
                Text(
                    text = line2,
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun NewPackRevealView(
    packNumber: Int,
    questionsCount: Int = 32,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    val envelopeScale by animateFloatAsState(
        targetValue = if (showContent) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = spring().stiffness
        ),
        label = "envelope-scale"
    )

    val ctaAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "cta-alpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
        Log.d("NewPackRevealView", "🔥 NewPackRevealView: Nouveau pack $packNumber révélé !")
    }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFD267A), Color(0xFFFF655B)),
        start = Offset(0f, 0f),
        end = Offset.Infinite
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Envelope emoji
                Text(
                    text = "💌",
                    fontSize = 80.sp,
                    color = Color.White,
                    modifier = Modifier.scale(envelopeScale)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Main message
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.new_cards_added),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val cardsLine = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("$questionsCount ")
                            append(stringResource(R.string.new_cards))
                        }
                    }
                    Text(
                        text = cardsLine,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.enjoy_it),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // "Let's go" button
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF990033) // ~ Color(red = 0.6, green = 0.0, blue = 0.2)
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .alpha(ctaAlpha)
                ) {
                    Text(
                        text = stringResource(R.string.lets_go),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

// ----------------------
// Previews (require the string resources to exist)
// ----------------------
@Preview(showBackground = true)
@Composable
private fun PackCompletionViewPreview() {
    PackCompletionView(packNumber = 1, onTapForSurprise = { /* preview */ })
}

@Preview(showBackground = true)
@Composable
private fun NewPackRevealViewPreview() {
    NewPackRevealView(packNumber = 2, onContinue = { /* preview */ })
}

/*
 * strings.xml — keys used in this file (add to your res/values/strings.xml and localize as needed):
 *
 * <resources>
 *     <string name="congratulations_pack">Congratulations!</string>
 *     <string name="pack_completed">Pack completed</string>
 *     <string name="pack_name">Pack name</string>
 *
 *     <string name="tap_on_me">Tap</string>
 *     <string name="on_me">on me</string>
 *     <string name="for_surprise">for a</string>
 *     <string name="surprise">surprise</string>
 *     <string name="exclamation">!</string>
 *
 *     <string name="new_cards_added">New cards just landed</string>
 *     <string name="new_cards">new cards</string>
 *     <string name="enjoy_it">Enjoy!</string>
 *     <string name="lets_go">Let’s go!</string>
 * </resources>
 *
 * NOTE: In Composables we use stringResource(R.string.key) which is equivalent to context.getString(R.string.key).
 */
