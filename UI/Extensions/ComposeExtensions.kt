package com.love2loveapp.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Extensions Compose pour collecte lifecycle-aware
 * 
 * Remplace collectAsState() par collectAsStateWithLifecycle()
 * pour éviter les fuites mémoire et les collectes concurrentes
 */

/**
 * Collecte un StateFlow de manière lifecycle-aware
 * À utiliser PARTOUT au lieu de collectAsState()
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    return collectAsStateWithLifecycle()
}

/**
 * Collecte un Flow de manière lifecycle-aware avec valeur initiale
 */
@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> {
    return collectAsStateWithLifecycle(initialValue = initialValue)
}

/**
 * Collecte des événements one-shot (SharedFlow) de manière lifecycle-aware
 * Évite les replays d'événements après rotation/recomposition
 */
@Composable
fun <T> SharedFlow<T>.CollectAsEvents(
    onEvent: (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    LaunchedEffect(this, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@CollectAsEvents.collect { event ->
                onEvent(event)
            }
        }
    }
}

/**
 * Collecte des événements avec une clé pour éviter les re-collectes
 * inutiles lors des recompositions
 */
@Composable
fun <T> SharedFlow<T>.CollectAsEventsWithKey(
    key: Any,
    onEvent: (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    LaunchedEffect(key, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@CollectAsEventsWithKey.collect { event ->
                onEvent(event)
            }
        }
    }
}

/**
 * Hook pour mémoriser une lambda avec des dépendances
 * Évite les recompositions inutiles
 */
@Composable
fun <T> rememberEventHandler(
    vararg dependencies: Any?,
    handler: () -> T
): T {
    return remember(*dependencies) { handler() }
}
