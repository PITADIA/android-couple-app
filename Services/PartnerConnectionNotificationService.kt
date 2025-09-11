package com.love2love.core.partner

import android.util.Log
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.love2love.app.R

/**
 * =========================================================
 *  Partner Connection — Service + EventBus + UI Compose
 *  - Bus d’événements type-safe (Flow)
 *  - Service avec StateFlow consommables par l’UI
 *  - Composable prêt à l’emploi (Snackbar Host)
 *
 *  ⚠️ strings.xml doit contenir :
 *  <string name="connected_to_partner">Connecté à %1$s</string>
 * =========================================================
 */

/* ---------- Événements ---------- */
sealed class PartnerConnectionEvent {
    data class ConnectionSuccess(val partnerName: String) : PartnerConnectionEvent()
    data class RequestShowConnectionSuccess(val partnerName: String) : PartnerConnectionEvent()
}

/* ---------- Bus d’événements (remplace NotificationCenter) ---------- */
object PartnerEventsBus {
    private val _events: MutableSharedFlow<PartnerConnectionEvent> =
        MutableSharedFlow(extraBufferCapacity = 64)
    val events: SharedFlow<PartnerConnectionEvent> = _events.asSharedFlow()

    fun publish(event: PartnerConnectionEvent) {
        _events.tryEmit(event)
    }
}

/* ---------- Service : état réactif consommable par Compose ---------- */
object PartnerConnectionNotificationService {

    private const val TAG = "PartnerConnectionSvc"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _shouldShowConnectionSuccess = MutableStateFlow(false)
    val shouldShowConnectionSuccess: StateFlow<Boolean> =
        _shouldShowConnectionSuccess.asStateFlow()

    private val _connectedPartnerName = MutableStateFlow("")
    val connectedPartnerName: StateFlow<String> =
        _connectedPartnerName.asStateFlow()

    init {
        setupObservers()
    }

    private fun setupObservers() {
        scope.launch {
            PartnerEventsBus.events.collect { event ->
                when (event) {
                    is PartnerConnectionEvent.ConnectionSuccess -> {
                        showConnectionSuccess(event.partnerName)
                    }
                    is PartnerConnectionEvent.RequestShowConnectionSuccess -> {
                        showConnectionSuccess(event.partnerName)
                    }
                }
            }
        }
    }

    fun showConnectionSuccess(partnerName: String) {
        Log.d(TAG, "🎉 Affichage message pour: $partnerName")
        _connectedPartnerName.value = partnerName
        _shouldShowConnectionSuccess.value = true
    }

    fun dismissConnectionSuccess() {
        Log.d(TAG, "🎉 Fermeture message")
        _shouldShowConnectionSuccess.value = false
        _connectedPartnerName.value = ""
    }

    // Helpers pour émettre les événements depuis n’importe où dans l’app
    fun postConnectionSuccess(partnerName: String) {
        PartnerEventsBus.publish(PartnerConnectionEvent.ConnectionSuccess(partnerName))
    }

    fun requestShowConnectionSuccess(partnerName: String) {
        PartnerEventsBus.publish(PartnerConnectionEvent.RequestShowConnectionSuccess(partnerName))
    }
}

/* ---------- UI Compose : couche d’affichage (Snackbar) ---------- */
@Composable
fun PartnerConnectionSnackbarHost(
    modifier: Modifier = Modifier,
    service: PartnerConnectionNotificationService = PartnerConnectionNotificationService
) {
    val show by service.shouldShowConnectionSuccess.collectAsState()
    val partnerName by service.connectedPartnerName.collectAsState()
    val hostState = remember { SnackbarHostState() }

    LaunchedEffect(show, partnerName) {
        if (show) {
            hostState.showSnackbar(
                message = stringResource(R.string.connected_to_partner, partnerName),
                withDismissAction = true
            )
            service.dismissConnectionSuccess()
        }
    }

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    )
}

/* ---------- Exemple d’appel (à utiliser après connexion réussie) ----------
    PartnerConnectionNotificationService.postConnectionSuccess(partnerName = "Alex")
--------------------------------------------------------------------------- */
