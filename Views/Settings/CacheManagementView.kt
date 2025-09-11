package com.love2love.settings.cache

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import com.love2love.R

/**
 * Modèle des statistiques de cache.
 */
data class CacheStats(
    val totalQuestions: Int,
    val categories: Int,
    val cacheSize: String
)

/**
 * Interface à raccorder à ton implémentation Android.
 * Tu peux brancher ça sur Firestore/Room/Storage selon ton architecture.
 */
interface QuestionCacheManager {
    val isLoading: StateFlow<Boolean>
    suspend fun preloadAllCategories()
    fun clearCache()
    suspend fun getCacheStatistics(): CacheStats
}

/**
 * Écran de gestion du cache (équivalent SwiftUI CacheManagementView).
 */
@Composable
fun CacheManagementScreen(
    manager: QuestionCacheManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isLoading by manager.isLoading.collectAsState(initial = false)

    var stats by remember {
        mutableStateOf(CacheStats(totalQuestions = 0, categories = 0, cacheSize = "0 B"))
    }
    var showClearAlert by remember { mutableStateOf(false) }

    // Charger les stats au montage
    LaunchedEffect(Unit) {
        stats = manager.getCacheStatistics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.cache_management_title)) },
                actions = {
                    TextButton(onClick = onClose) {
                        Text(text = stringResource(id = R.string.close))
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Statistiques
            SectionCard(
                title = stringResource(id = R.string.cache_section_stats)
            ) {
                StatRow(
                    label = stringResource(id = R.string.questions_cached),
                    value = stats.totalQuestions.toString(),
                    emphasized = true
                )
                Divider()
                StatRow(
                    label = stringResource(id = R.string.categories),
                    value = stats.categories.toString()
                )
                Divider()
                StatRow(
                    label = stringResource(id = R.string.cache_size),
                    value = stats.cacheSize
                )
            }

            // Section Actions
            SectionCard(
                title = stringResource(id = R.string.cache_section_actions)
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.reload_cache))
                    },
                    leadingContent = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                scope.launch {
                                    manager.preloadAllCategories()
                                    stats = manager.getCacheStatistics()
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text(text = stringResource(id = R.string.reload_cache))
                        }
                    }
                )

                Divider()

                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(id = R.string.clear_cache),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent = {
                        Button(
                            onClick = { showClearAlert = true }
                        ) {
                            Text(text = stringResource(id = R.string.clear_cache))
                        }
                    }
                )
            }

            // Loading indicator (équivalent à la Section "loading_simple")
            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(text = stringResource(id = R.string.loading_simple))
                    }
                }
            }
        }
    }

    // Alerte de confirmation pour vider le cache
    if (showClearAlert) {
        AlertDialog(
            onDismissRequest = { showClearAlert = false },
            title = { Text(text = stringResource(id = R.string.cache_clear_title)) },
            text = { Text(text = stringResource(id = R.string.cache_clear_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAlert = false
                        manager.clearCache()
                        // Recalculer les stats après nettoyage
                        scope.launch {
                            stats = manager.getCacheStatistics()
                        }
                    }
                ) {
                    Text(text = stringResource(id = R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAlert = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

/* ---------- UI helpers ---------- */

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    emphasized: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (emphasized) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyMedium
            }
        )
    }
}
