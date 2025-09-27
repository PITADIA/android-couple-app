package com.love2loveapp.views.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.AppState
import com.love2loveapp.viewmodels.StatisticsViewModel
import com.love2loveapp.views.statistics.components.StatisticCard

/**
 * 📊 CoupleStatisticsSection - Section statistiques couple page principale
 * 
 * Équivalent Android exact de CoupleStatisticsView iOS :
 * - Titre "Vos statistiques de couple" (22sp semibold)  
 * - Grille 2x2 responsive avec spacing 16dp
 * - 4 cartes : Jours Ensemble, Questions %, Villes, Pays
 * - Couleurs thématiques et interactions analytics
 * - Architecture MVVM avec ViewModel simple
 */
@Composable
fun CoupleStatisticsSection(
    appState: AppState?,
    modifier: Modifier = Modifier
) {
    // Pour l'instant, utiliser la version simplifiée sans ViewModel
    // TODO: Intégrer ViewModel complet quand Hilt sera configuré
    CoupleStatisticsSectionSimple(appState, modifier)
}

/**
 * 📊 Version simplifiée sans Hilt pour usage direct
 * Utilisée quand ViewModel n'est pas disponible
 */
@Composable
fun CoupleStatisticsSectionSimple(
    appState: AppState?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Titre
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.couple_statistics),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Cartes statistiques mockées
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(4) { index ->
                when (index) {
                    0 -> StatisticCard(
                        title = stringResource(R.string.days_together),
                        value = "847", // TODO: Remplacer par vraies données
                        iconRes = R.drawable.jours,
                        iconColor = Color(0xFFfeb5c8),
                        backgroundColor = Color(0xFFfedce3),
                        textColor = Color(0xFFdb3556)
                    )
                    1 -> StatisticCard(
                        title = stringResource(R.string.questions_answered),
                        value = "67%", // TODO: Remplacer par vraies données
                        iconRes = R.drawable.qst,
                        iconColor = Color(0xFFfed397),
                        backgroundColor = Color(0xFFfde9cf),
                        textColor = Color(0xFFffa229)
                    )
                    2 -> StatisticCard(
                        title = stringResource(R.string.cities_visited),
                        value = "12", // TODO: Remplacer par vraies données
                        iconRes = R.drawable.ville,
                        iconColor = Color(0xFFb0d6fe),
                        backgroundColor = Color(0xFFdbecfd),
                        textColor = Color(0xFF0a85ff)
                    )
                    3 -> StatisticCard(
                        title = stringResource(R.string.countries_visited),
                        value = "3", // TODO: Remplacer par vraies données
                        iconRes = R.drawable.pays,
                        iconColor = Color(0xFFd1b3ff),
                        backgroundColor = Color(0xFFe8dcff),
                        textColor = Color(0xFF7c3aed)
                    )
                }
            }
        }
    }
}
