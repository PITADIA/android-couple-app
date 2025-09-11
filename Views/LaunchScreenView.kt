// LaunchScreenView.kt
package com.yourapp.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yourapp.R

@Composable
fun LaunchScreenView(modifier: Modifier = Modifier) {
    val tag = "LaunchScreenView"
    val nowSeconds = { System.currentTimeMillis() / 1000.0 }

    // onAppear
    LaunchedEffect(Unit) {
        Log.d(tag, "🚀 LaunchScreenView: Écran de chargement affiché [${nowSeconds()}]")
    }
    // onDisappear
    DisposableEffect(Unit) {
        onDispose {
            Log.d(tag, "🚀 LaunchScreenView: Écran de chargement masqué [${nowSeconds()}]")
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFD267A), // #FD267A
                        Color(0xFFFF655B)  // #FF655B
                    ),
                    // Diagonale équivalente à .topLeading -> .bottomTrailing
                    start = Offset(0f, 0f),
                    end = Offset(size.width.toFloat(), size.height.toFloat())
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.leetchi2),
            contentDescription = stringResource(R.string.leetchi_logo),
            modifier = Modifier.size(200.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LaunchScreenViewPreview() {
    LaunchScreenView()
}
