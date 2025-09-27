package com.love2loveapp.views.widgets.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.widgets.WidgetData

/**
 * 🎨 WidgetPreviewSection - Section preview widgets pour page principale
 * 
 * Équivalent Android du WidgetPreviewSection iOS :
 * - Affiche aperçus widgets disponibles
 * - Navigation vers hub widgets
 * - Design cartes élégant avec gradients
 * - Intégration données temps réel
 */
@Composable
fun WidgetPreviewSection(
    widgetData: WidgetData,
    isLoading: Boolean,
    hasSubscription: Boolean,
    onWidgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 📱 EN-TÊTE SECTION
        WidgetPreviewHeader(
            onAddWidgets = onWidgetClick
        )
        
        // 🎯 PREVIEWS WIDGETS
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(getWidgetPreviews()) { preview ->
                WidgetPreviewCard(
                    preview = preview,
                    widgetData = widgetData,
                    isLoading = isLoading,
                    hasSubscription = hasSubscription,
                    onClick = onWidgetClick
                )
            }
        }
    }
}

/**
 * 📱 WidgetPreviewHeader - En-tête section widgets
 */
@Composable
private fun WidgetPreviewHeader(
    onAddWidgets: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.widgets),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2C2E)
            )
            Text(
                text = stringResource(R.string.add_widgets),
                fontSize = 14.sp,
                color = Color(0xFF8E8E93)
            )
        }
        
        OutlinedButton(
            onClick = onAddWidgets,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF007AFF)
            ),
            border = BorderStroke(1.5.dp, Color(0xFF007AFF))
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.customize),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 🎨 WidgetPreviewCard - Carte preview widget individuelle
 */
@Composable
private fun WidgetPreviewCard(
    preview: WidgetPreview,
    widgetData: WidgetData,
    isLoading: Boolean,
    hasSubscription: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = preview.gradient,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // 🔒 Premium Lock Overlay
            if (preview.isPremium && !hasSubscription) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.premium_required),
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 📊 CONTENU WIDGET
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // En-tête
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = preview.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = stringResource(preview.title),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Contenu principal
                    when (preview.type) {
                        WidgetType.MAIN -> {
                            WidgetMainPreviewContent(
                                widgetData = widgetData,
                                isLoading = isLoading
                            )
                        }
                        WidgetType.DISTANCE -> {
                            WidgetDistancePreviewContent(
                                widgetData = widgetData,
                                isLoading = isLoading
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 📊 WidgetMainPreviewContent - Contenu preview widget principal
 */
@Composable
private fun WidgetMainPreviewContent(
    widgetData: WidgetData,
    isLoading: Boolean
) {
    Column {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        } else {
            Text(
                text = widgetData.relationshipStats?.daysTotal?.toString() ?: "0",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Text(
            text = stringResource(R.string.widget_days_label),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        } else {
            Text(
                text = "${widgetData.userName} & ${widgetData.partnerName}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 📍 WidgetDistancePreviewContent - Contenu preview widget distance
 */
@Composable
private fun WidgetDistancePreviewContent(
    widgetData: WidgetData,
    isLoading: Boolean
) {
    Column {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        } else {
            Text(
                text = widgetData.distanceInfo?.formattedDistance ?: stringResource(R.string.widget_no_location),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        } else {
            Text(
                text = widgetData.distanceInfo?.currentMessage ?: stringResource(R.string.widget_enable_location),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * ✨ Simple Shimmer Effect pour loading states (simplifié)
 */
@Composable
private fun Modifier.shimmerEffect(): Modifier = background(
    Color.Gray.copy(alpha = 0.2f)
)

/**
 * 🎯 WidgetPreview - Modèle preview widget
 */
private data class WidgetPreview(
    val type: WidgetType,
    val title: Int,
    val icon: ImageVector,
    val gradient: Brush,
    val isPremium: Boolean
)

/**
 * 📱 WidgetType - Types widgets disponibles
 */
private enum class WidgetType {
    MAIN,
    DISTANCE
}

/**
 * 🎨 getWidgetPreviews - Liste previews widgets
 */
private fun getWidgetPreviews(): List<WidgetPreview> = listOf(
    WidgetPreview(
        type = WidgetType.MAIN,
        title = R.string.main_widget,
        icon = Icons.Filled.Favorite,
        gradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFD267A),
                Color(0xFFFF655B)
            )
        ),
        isPremium = false
    ),
    WidgetPreview(
        type = WidgetType.DISTANCE,
        title = R.string.distance_widget,
        icon = Icons.Filled.LocationOn,
        gradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF667eea),
                Color(0xFF764ba2)
            )
        ),
        isPremium = true
    )
)
