package com.love2loveapp.core.utils.connection

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.model.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrateur pour gérer les connexions partenaire avec thread safety et edge cases
 * - Remplace les appels de localisation par strings.xml (via StringProvider)
 * - Équivalent @MainActor + ObservableObject : ViewModel + StateFlow
 */
class ConnectionOrchestratorViewModel(
    private val partnerCodeService: PartnerCodeService,
    private val analyticsService: AnalyticsService,
    private val stringProvider: StringProvider,          // ex. { id, args -> context.getString(id, *args) }
    private val sharedPrefs: SharedPreferences? = null,   // optionnel, pour purge des caches
    private val connectionTimeoutMs: Long = AppConstants.Connection.DEFAULT_TIMEOUT_MS
) : ViewModel() {

    // --- State ---

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _canRetry = MutableStateFlow(true)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

    private val connectMutex = Mutex()

    // --- Types ---

    sealed class ConnectionState {
        data object Idle : ConnectionState()
        data object Connecting : ConnectionState()
        data class Success(val partnerName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        data object Timeout : ConnectionState()

        val isLoading: Boolean get() = this is Connecting
        val isError: Boolean get() = this is Error || this is Timeout
        val isSuccess: Boolean get() = this is Success
    }

    enum class ConnectionContext(val rawValue: String) {
        Onboarding("onboarding"),
        Settings("settings"),
        DeepLink("deep_link"),
        Unknown("unknown")
    }

    // --- API publique ---

    /**
     * Tente une connexion avec gestion des edge cases
     */
    fun attemptConnection(
        code: String,
        context: ConnectionContext,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            connectMutex.withLock {
                // Anti-réentrance
                if (_isConnecting.value) {
                    // équiv. "Connexion déjà en cours, ignorée"
                    return@withLock
                }
                if (!_canRetry.value) {
                    val msg = stringProvider.get(R.string.error_retry_wait)
                    _connectionState.value = ConnectionState.Error(msg)
                    onError(msg)
                    return@withLock
                }

                _isConnecting.value = true
                _connectionState.value = ConnectionState.Connecting
                _canRetry.value = false
            }

            // Log début (optionnel)
            analyticsService.track(
                AnalyticsEvent.ConnectStart(source = context.rawValue)
            )

            // Exécute la tentative avec timeout optionnel
            val result = withTimeoutOrNull(connectionTimeoutMs) {
                try {
                    partnerCodeService.connectWithPartnerCode(code, context)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // En cas d’erreur réseau ou autre
                    analyticsService.trackConnectionError(context, t, step = "network_request")
                    handleError(
                        message = stringProvider.get(
                            R.string.error_network_generic_with_msg,
                            t.localizedMessage ?: "unknown"
                        ),
                        onError = onError
                    )
                    return@withTimeoutOrNull null
                }
            }

            when (result) {
                null -> { // timeout
                    _connectionState.value = ConnectionState.Timeout
                    _isConnecting.value = false
                    onError(stringProvider.get(R.string.error_timeout))
                    allowRetryAfterDelay()
                }
                true -> {
                    val partnerName = partnerCodeService.partnerInfo?.name ?: stringProvider.get(R.string.partner_fallback_name)
                    _connectionState.value = ConnectionState.Success(partnerName)
                    _isConnecting.value = false
                    onSuccess(partnerName)

                    analyticsService.track(
                        AnalyticsEvent.ConnectSuccess(
                            inheritedSub = partnerCodeService.partnerInfo?.isSubscribed == true,
                            context = context.rawValue
                        )
                    )

                    // Reset après succès (délai court, pour laisser l’UI afficher l’état success)
                    resetConnectionState(delayMs = 2_000)
                }
                false -> {
                    val msg = partnerCodeService.errorMessage
                        ?: stringProvider.get(R.string.error_connection_default)
                    handleError(msg, onError)
                }
            }
        }
    }

    /** Force la réinitialisation de l’état */
    fun resetConnectionState(delayMs: Long = 0L) {
        viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            resetStateInternal()
        }
    }

    /** Permet un retry immédiat (bouton ‘Réessayer’) */
    fun enableRetry() {
        _canRetry.value = true
        if (_connectionState.value.isError) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    /** Gère la déconnexion d’un partenaire avec purge complète */
    fun handlePartnerDisconnection(appState: AppState) {
        // 1. Reset flags d’intro
        appState.resetIntroFlagsOnPartnerChange()

        // 2. Purge caches et services
        purgeCachesAndServices()

        // 3. Reset état connexion
        resetStateInternal()

        // 4. Analytics
        analyticsService.track(AnalyticsEvent.ConnectStart(source = "partner_disconnection"))
    }

    // --- Privé ---

    private fun resetStateInternal() {
        _connectionState.value = ConnectionState.Idle
        _isConnecting.value = false
        _canRetry.value = true
    }

    private fun handleError(message: String, onError: (String) -> Unit) {
        _connectionState.value = ConnectionState.Error(message)
        _isConnecting.value = false
        onError(message)
        allowRetryAfterDelay()
    }

    private fun allowRetryAfterDelay() {
        viewModelScope.launch {
            delay(AppConstants.Connection.RETRY_DELAY_MS)
            _canRetry.value = true
        }
    }

    private fun purgeCachesAndServices() {
        // Purge éventuelle des caches locaux (équivalents à DailyQuestionService / DailyChallengeService)
        runCatching {
            DailyQuestionService.clearCache()
        }.onFailure {
            // log si nécessaire
        }

        runCatching {
            DailyChallengeService.clearCache()
        }.onFailure {
            // log si nécessaire
        }

        // Purge SharedPreferences (équivalent UserDefaults)
        sharedPrefs?.let { prefs ->
            val keysToRemove = prefs.all.keys.filter { key ->
                key.contains("dailyQuestion", ignoreCase = true) ||
                key.contains("dailyChallenge", ignoreCase = true) ||
                key.contains("partnerCache", ignoreCase = true)
            }
            if (keysToRemove.isNotEmpty()) {
                prefs.edit().apply {
                    keysToRemove.forEach { remove(it) }
                }.apply()
            }
        }
    }
}

/* ------------------------- Interfaces & stubs ------------------------- */

/** Fournisseur de chaînes localisées depuis strings.xml */
fun interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

/** Service de connexion par code partenaire (à implémenter avec Firebase Functions) */
interface PartnerCodeService {
    val partnerInfo: PartnerInfo?
    val errorMessage: String?
    suspend fun connectWithPartnerCode(code: String, context: ConnectionOrchestratorViewModel.ConnectionContext): Boolean
}

data class PartnerInfo(
    val name: String? = null,
    val isSubscribed: Boolean = false
)

/** Service analytics minimal utilisé par l’orchestrateur */
interface AnalyticsService {
    fun track(event: AnalyticsEvent)
    fun trackConnectionError(
        context: ConnectionOrchestratorViewModel.ConnectionContext,
        error: Throwable,
        step: String
    )
}

sealed interface AnalyticsEvent {
    data class ConnectStart(val source: String) : AnalyticsEvent
    data class ConnectSuccess(val inheritedSub: Boolean, val context: String) : AnalyticsEvent
}

/** État global de l’app (stub) */
interface AppState {
    fun resetIntroFlagsOnPartnerChange()
}

/** Stubs de services de cache (si présents côté Android) */
object DailyQuestionService {
    fun clearCache() { /* no-op or actual implementation */ }
}

object DailyChallengeService {
    fun clearCache() { /* no-op or actual implementation */ }
}

/* ------------------------- Utilisation côté UI -------------------------

// 1) Fournir un StringProvider basé sur context.getString(...)
val sp = StringProvider { id, args -> context.getString(id, *args) }

// 2) Instancier le ViewModel (via DI ou factory), en lui passant partnerCodeService, analyticsService, sp, sharedPrefs

// 3) Dans Compose, observer connectionState/isConnecting/canRetry (collectAsState())

-------------------------------------------------------------------------- */
