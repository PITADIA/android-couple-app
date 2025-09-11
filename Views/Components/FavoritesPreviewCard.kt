package com.yourapp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.R

// --- Modèle équivalent à "Favorite" côté Swift ---
data class Favorite(
    val id: String,
    val emoji: String,
    val questionText: String
)

// --- Service simple pour remplacer EnvironmentObject ---
class FavoritesService {
    private val favorites = mutableStateListOf<Favorite>()

    fun getAllFavorites(): List<Favorite> = favorites
    fun getRecentFavorites(limit: Int): List<Favorite> = favorites.take(limit)
    fun getFavoritesCount(): Int = favorites.size

    // utilitaires pour la démo / preview
    fun setAll(items: List<Favorite>) {
        favorites.clear()
        favorites.addAll(items)
    }
}

@Composable
fun FavoritesPreviewCard(
    favoritesService: FavoritesService,
    onTapViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allFavorites = favoritesService.getAllFavorites()
    val recent = favoritesService.getRecentFavorites(limit = 3)
    val totalCount = favoritesService.getFavoritesCount()

    val shape = RoundedCornerShape(12.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.10f),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.80f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.my_favorites),
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.my_favorites),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = onTapViewAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFFD267A)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.view_all),
                        fontSize = 16.sp
                    )
                }
            }

            // Contenu
            if (allFavorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("❤️", fontSize = 24.sp)
                    Text(
                        text = stringResource(R.string.no_favorites_yet),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.60f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { favorite ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = favorite.emoji,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = favorite.questionText,
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.90f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val remaining = totalCount - 3
                    if (remaining > 0) {
                        Text(
                            text = pluralStringResource(
                                id = R.plurals.others_count,
                                count = remaining,
                                remaining
                            ),
                            fontSize = 11.sp,
                            color = Color.Black.copy(alpha = 0.60f)
                        )
                    }
                }
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true)
@Composable
private fun FavoritesPreviewCardPreview() {
    val service = FavoritesService().apply {
        setAll(
            listOf(
                Favorite("1", "✨", "Quelle est la petite habitude de l’autre que tu aimes ?"),
                Favorite("2", "💬", "Quel message d’encouragement t’a le plus touché récemment ?"),
                Favorite("3", "🎯", "Quel objectif commun souhaites-tu lancer ce mois-ci ?"),
                Favorite("4", "🧩", "Quel trait de caractère de ton/ta partenaire t’inspire ?")
            )
        )
    }

    Surface(color = Color(0xFF9C27B0).copy(alpha = 0.15f)) {
        FavoritesPreviewCard(
            favoritesService = service,
            onTapViewAll = { /* navigation */ },
            modifier = Modifier.padding(16.dp)
        )
    }
}
