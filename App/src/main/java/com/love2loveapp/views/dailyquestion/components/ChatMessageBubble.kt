package com.love2loveapp.views.dailyquestion.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.models.QuestionResponse

/**
 * 💬 ChatMessageBubble - Bulle de Message Chat
 * Équivalent iOS ChatMessageView dans DailyQuestionMainView
 * 
 * Affiche une bulle de message avec :
 * - Design différent utilisateur/partenaire
 * - Nom de l'expéditeur (si changement)
 * - Horodatage formaté
 * - Couleurs Love2Love
 * - Style Twitter/WhatsApp-like
 */
@Composable
fun ChatMessageBubble(
    response: QuestionResponse,
    isCurrentUser: Boolean,
    isPreviousSameSender: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isCurrentUser) Color(0xFFFF6B9D) else Color(0xFFE5E5E5)
    val textColor = if (isCurrentUser) Color.White else Color.Black
    val nameColor = if (isCurrentUser) Color(0xFFFF6B9D) else Color(0xFF666666)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            // 👤 Nom de l'expéditeur (seulement si changement d'expéditeur)
            if (!isPreviousSameSender) {
                Text(
                    text = response.userName,
                    fontSize = 12.sp,
                    color = nameColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = 16.dp, 
                        vertical = 4.dp
                    )
                )
            }

            // 💬 Bulle de message principale
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(
                        start = if (isCurrentUser) 40.dp else 0.dp,
                        end = if (isCurrentUser) 0.dp else 40.dp
                    ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isCurrentUser) 20.dp else 6.dp,
                    bottomEnd = if (isCurrentUser) 6.dp else 20.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Texte du message
                    Text(
                        text = response.text,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Horodatage et statut
                    Row(
                        modifier = Modifier.align(
                            if (isCurrentUser) Alignment.End else Alignment.Start
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = response.formattedTime,
                            fontSize = 11.sp,
                            color = if (isCurrentUser) Color.White.copy(alpha = 0.7f) else Color.Gray
                        )

                        // 📊 Indicateur de statut (pour messages temporaires)
                        if (response.isTemporary) {
                            Text(
                                text = "⏳",
                                fontSize = 10.sp
                            )
                        }

                        // ✅ Indicateur message récent
                        if (response.isRecent() && !response.isTemporary) {
                            Text(
                                text = "•",
                                fontSize = 8.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 💬 Bulle système pour messages d'information
 */
@Composable
fun SystemMessageBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF0F0F0)
        ) {
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * 📅 Séparateur de date pour le chat
 */
@Composable
fun DateSeparator(
    date: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFF6B9D).copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = date,
                fontSize = 11.sp,
                color = Color(0xFFFF6B9D),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 🔔 Bulle de notification nouveau message
 */
@Composable
fun NewMessageNotificationBubble(
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    if (count > 0) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFF6B9D),
                onClick = onClick,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "$count nouveau${if (count > 1) "x" else ""} message${if (count > 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * ⏳ Indicateur de frappe (typing indicator)
 */
@Composable
fun TypingIndicatorBubble(
    userName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .padding(end = 40.dp),
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE5E5E5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$userName écrit",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                // Animation points de frappe
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(3) { index ->
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B9D)
                        )
                    }
                }
            }
        }
    }
}
