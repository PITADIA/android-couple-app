package com.love2loveapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.max

@Composable
fun DaysTogetherCard(
    relationshipStartDate: LocalDate?,   // passe LocalDate (ou utilise la surcharge Date ci-dessous)
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFFD267A)
    val background = accent.copy(alpha = 0.10f)

    val daysTogether = remember(relationshipStartDate) {
        if (relationshipStartDate == null) 0L
        else max(0L, ChronoUnit.DAYS.between(relationshipStartDate, LocalDate.now()))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                // Nombre de jours
                Text(
                    text = daysTogether.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )

                // "Jours" / "Ensemble" via strings.xml
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(id = R.string.days),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(id = R.string.together),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Cœur à droite
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = accent.copy(alpha = 0.8f)
            )
        }
    }
}

/** Surcharge pratique si ton modèle expose encore java.util.Date. */
@Composable
fun DaysTogetherCard(
    relationshipStartDate: Date?,
    modifier: Modifier = Modifier
) {
    val localDate = relationshipStartDate?.toLocalDate()
    DaysTogetherCard(relationshipStartDate = localDate, modifier = modifier)
}

private fun Date.toLocalDate(): LocalDate =
    toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

@Preview(showBackground = true)
@Composable
private fun DaysTogetherCardPreview() {
    DaysTogetherCard(
        relationshipStartDate = LocalDate.now().minusDays(123)
    )
}
