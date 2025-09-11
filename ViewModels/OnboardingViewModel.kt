package com.love2loveapp.core.viewmodels.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.model.AppConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Placeholders de ressources:
 * strings.xml doit contenir (exemples)
 *
 * <string name="goal_create_connection">Créer une connexion</string>
 * <string name="goal_talk_avoided_subjects">Parler des sujets évités</string>
 * <string name="goal_increase_passion">Augmenter la passion</string>
 * <string name="goal_share_more_laughs">Partager plus de rires</string>
 * <string name="goal_find_complicity">Trouver la complicité</string>
 *
 * <string name="improvement_create_strong_moment">Créer un moment fort</string>
 * <string name="improvement_revive_connection">Raviver la connexion</string>
 * <string name="improvement_break_routine">Casser la routine</string>
 * <string name="improvement_say_unsaid">Dire l’inavoué</string>
 */

// --- Événements globaux (remplacement simple de NotificationCenter) ---
object SubscriptionEvents {
    val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
object PartnerEvents {
    // émet le nom du partenaire connecté
    val connectionSuccess = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

// --- Types domaine “placeholder” à mapper sur tes vrais types ---
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdated: Long? = null
)

enum class RelationshipDuration {
    LessThanSixMonths, SixMonthsToOneYear, OneToThreeYears, ThreeToFiveYears, MoreThanFiveYears
}

data class AppUser(
    val id: String,
    val name: String,
    val isSubscribed: Boolean
)

interface AppState {
    var isOnboardingInProgress: Boolean
    fun updateUser(user: AppUser)
    fun completeOnboarding()
}

interface FirebaseService {
    /**
     * Doit créer/mettre à jour l’utilisateur en préservant les données partenaire si présentes.
     * Retourne (success, user)
     */
    suspend fun finalizeOnboardingWithPartnerData(
        name: String,
        relationshipGoals: List<String>,
        relationshipDuration: RelationshipDuration,
        relationshipImprovement: String?,
        questionMode: String?,
        isSubscribed: Boolean,
        relationshipStartDate: Date?,
        profileImage: Bitmap?,
        currentLocation: UserLocation?
    ): Pair<Boolean, AppUser?>

    fun completeOnboardingProcess()
}

// --- ViewModel ---
class OnboardingViewModel(
    private val firebaseService: FirebaseService,
    var appState: AppState? = null
) : ViewModel() {

    // Étapes (ordre identique à Swift)
    enum class OnboardingStep {
        relationshipGoals,
        relationshipImprovement,
        relationshipDate,
        communicationEvaluation,
        discoveryTime,
        listening,
        confidence,
        complicity,
        authentication,
        displayName,
        profilePhoto,
        completion,
        loading,
        partnerCode,
        questionsIntro,
        categoriesPreview,
        subscription
    }

    // --- State (équivalents @Published) ---
    val currentStep = MutableStateFlow(OnboardingStep.relationshipGoals)
    val userName = MutableStateFlow("")
    val birthDate = MutableStateFlow(Date())
    val selectedGoals = MutableStateFlow<List<String>>(emptyList())
    val relationshipDuration = MutableStateFlow(RelationshipDuration.OneToThreeYears)
    val relationshipImprovement = MutableStateFlow("")
    val selectedImprovements = MutableStateFlow<List<String>>(emptyList())
    val questionMode = MutableStateFlow("🔄 Questions variées")
    val isLoading = MutableStateFlow(false)
    val shouldSkipSubscription = MutableStateFlow(false)
    val shouldShowPartnerConnectionSuccess = MutableStateFlow(false)
    val connectedPartnerName = MutableStateFlow("")
    val relationshipStartDate = MutableStateFlow<Date?>(null)
    val profileImage = MutableStateFlow<Bitmap?>(null)
    val currentLocation = MutableStateFlow<UserLocation?>(null)

    // Évaluation du couple
    val communicationRating = MutableStateFlow("")
    val discoveryTimeAnswer = MutableStateFlow("")
    val listeningAnswer = MutableStateFlow("")
    val confidenceAnswer = MutableStateFlow("")
    val complicityAnswer = MutableStateFlow("")

    private val analytics = Firebase.analytics
    private var isCompletingSubscriptionGuard = false

    // --- Localisation via strings.xml : listes de @StringRes puis helpers de résolution ---
    private val relationshipGoalsRes: List<Int> = listOf(
        R.string.goal_create_connection,
        R.string.goal_talk_avoided_subjects,
        R.string.goal_increase_passion,
        R.string.goal_share_more_laughs,
        R.string.goal_find_complicity
    )

    private val relationshipImprovementsRes: List<Int> = listOf(
        R.string.improvement_create_strong_moment,
        R.string.improvement_revive_connection,
        R.string.improvement_break_routine,
        R.string.improvement_say_unsaid
    )

    /** Récupère la liste localisée des objectifs depuis un Context */
    fun relationshipGoals(context: Context): List<String> =
        relationshipGoalsRes.map(context::getString)

    /** Récupère la liste localisée des améliorations depuis un Context */
    fun relationshipImprovements(context: Context): List<String> =
        relationshipImprovementsRes.map(context::getString)

    // Modes de questions (texte direct ici ; tu peux aussi passer par strings.xml si tu préfères)
    val questionModes: List<String> = listOf(
        "🎯 Sérieux",
        "🎉 Fun",
        "🌶️ Hot et Sensuel",
        "💫 Profond"
    )

    /** Progression 0..1 (réactive) */
    val progress: StateFlow<Float> = currentStep
        .map { step ->
            val total = OnboardingStep.values().size.toFloat()
            val index = OnboardingStep.values().indexOf(step).toFloat()
            (index + 1f) / total
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f / OnboardingStep.values().size)

    init {
        Log.d("OnboardingVM", "🔥 Initialisation ViewModel")
        // Observateurs équivalents à NotificationCenter
        SubscriptionEvents.updates
            .onEach { handleSubscriptionUpdate() }
            .launchIn(viewModelScope)

        PartnerEvents.connectionSuccess
            .onEach { showPartnerConnectionSuccess(it) }
            .launchIn(viewModelScope)
    }

    fun updateAppState(newAppState: AppState) {
        Log.d("OnboardingVM", "🔥 Mise à jour AppState")
        this.appState = newAppState
    }

    fun nextStep() {
        val stepIndex = OnboardingStep.values().indexOf(currentStep.value)
        analytics.logEvent("onboarding_etape") { param("etape", stepIndex.toLong()) }
        Log.d("OnboardingVM", "📊 Firebase event: onboarding_etape - étape: $stepIndex")

        when (currentStep.value) {
            OnboardingStep.relationshipGoals -> currentStep.value = OnboardingStep.relationshipImprovement
            OnboardingStep.relationshipImprovement -> currentStep.value = OnboardingStep.relationshipDate
            OnboardingStep.relationshipDate -> currentStep.value = OnboardingStep.communicationEvaluation
            OnboardingStep.communicationEvaluation -> currentStep.value = OnboardingStep.discoveryTime
            OnboardingStep.discoveryTime -> currentStep.value = OnboardingStep.listening
            OnboardingStep.listening -> currentStep.value = OnboardingStep.confidence
            OnboardingStep.confidence -> currentStep.value = OnboardingStep.complicity
            OnboardingStep.complicity -> currentStep.value = OnboardingStep.authentication
            OnboardingStep.authentication -> currentStep.value = OnboardingStep.displayName
            OnboardingStep.displayName -> {
                Log.d("OnboardingVM", "🔥 displayName -> profilePhoto (nom: '${userName.value}')")
                currentStep.value = OnboardingStep.profilePhoto
            }
            OnboardingStep.profilePhoto -> currentStep.value = OnboardingStep.completion
            OnboardingStep.completion -> {
                currentStep.value = OnboardingStep.loading
                completeDataCollection()
            }
            OnboardingStep.loading -> {
                if (shouldSkipSubscription.value) {
                    finalizeOnboarding(withSubscription = true)
                } else {
                    currentStep.value = OnboardingStep.partnerCode
                }
            }
            OnboardingStep.partnerCode -> currentStep.value = OnboardingStep.questionsIntro
            OnboardingStep.questionsIntro -> currentStep.value = OnboardingStep.categoriesPreview
            OnboardingStep.categoriesPreview -> currentStep.value = OnboardingStep.subscription
            OnboardingStep.subscription -> {
                // finalisation via skipSubscription() ou completeSubscription()
            }
        }
    }

    fun previousStep() {
        Log.d("OnboardingVM", "🔥 Retour depuis ${currentStep.value}")
        currentStep.value = when (currentStep.value) {
            OnboardingStep.relationshipGoals -> OnboardingStep.relationshipGoals
            OnboardingStep.relationshipImprovement -> OnboardingStep.relationshipGoals
            OnboardingStep.relationshipDate -> OnboardingStep.relationshipImprovement
            OnboardingStep.communicationEvaluation -> OnboardingStep.relationshipDate
            OnboardingStep.discoveryTime -> OnboardingStep.communicationEvaluation
            OnboardingStep.listening -> OnboardingStep.discoveryTime
            OnboardingStep.confidence -> OnboardingStep.listening
            OnboardingStep.complicity -> OnboardingStep.confidence
            OnboardingStep.authentication -> OnboardingStep.complicity
            OnboardingStep.displayName -> OnboardingStep.authentication
            OnboardingStep.profilePhoto -> {
                // Google Only : si un displayName Google existe on peut estimer revenir à auth, sinon displayName
                val googleName = FirebaseAuth.getInstance().currentUser?.displayName
                if (!googleName.isNullOrBlank()) OnboardingStep.authentication else OnboardingStep.displayName
            }
            OnboardingStep.completion -> OnboardingStep.profilePhoto
            OnboardingStep.loading -> OnboardingStep.completion
            OnboardingStep.partnerCode -> OnboardingStep.loading
            OnboardingStep.questionsIntro -> OnboardingStep.partnerCode
            OnboardingStep.categoriesPreview -> OnboardingStep.questionsIntro
            OnboardingStep.subscription -> {
                Log.d("OnboardingVM", "🔥 Impossible de revenir depuis subscription")
                OnboardingStep.subscription
            }
        }
        Log.d("OnboardingVM", "🔥 Nouvelle étape: ${currentStep.value}")
    }

    fun toggleGoal(goal: String) {
        val current = selectedGoals.value.toMutableList()
        if (current.contains(goal)) current.remove(goal) else current.add(goal)
        selectedGoals.value = current
    }

    private fun completeDataCollection() {
        Log.d("OnboardingVM", "🔥 Début finalisation collecte")
        viewModelScope.launch {
            isLoading.value = true
            delay(AppConstants.Onboarding.DATA_COLLECTION_DELAY_MS)
            isLoading.value = false
            currentStep.value = OnboardingStep.partnerCode
            Log.d("OnboardingVM", "🔥 Fin du chargement simulé -> partnerCode")
        }
    }

    /** Google Sign-In uniquement */
    fun completeAuthentication() {
        Log.d("OnboardingVM", "🔥 Authentification terminée (Google)")
        viewModelScope.launch {
            // Lecture du displayName Google si dispo
            checkGoogleNameAndProceed()
        }
    }

    private fun checkGoogleNameAndProceed() {
        val googleDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
        Log.d("OnboardingVM", "🔥 googleUserDisplayName='${googleDisplayName ?: "null"}'")

        if (!googleDisplayName.isNullOrBlank()) {
            Log.d("OnboardingVM", "✅ Nom Google fourni ($googleDisplayName) - Skip DisplayName step")
            userName.value = googleDisplayName
            currentStep.value = OnboardingStep.profilePhoto
        } else {
            Log.d("OnboardingVM", "❌ Aucun nom Google - Aller vers DisplayName")
            currentStep.value = OnboardingStep.displayName
        }
    }

    fun skipSubscription() {
        Log.d("OnboardingVM", "🔥 Abonnement ignoré -> finalisation")
        finalizeOnboarding(withSubscription = false)
    }

    fun completeSubscription() {
        if (isCompletingSubscriptionGuard) {
            Log.d("OnboardingVM", "🔥 Appel ignoré, finalisation déjà en cours")
            return
        }
        isCompletingSubscriptionGuard = true
        Log.d("OnboardingVM", "🔥 Abonnement terminé -> finalisation")
        finalizeOnboarding(withSubscription = true)
    }
    
    // === Result<T> based operations ===
    
    /**
     * Valide les données d'onboarding avec Result<T>
     */
    fun validateOnboardingData(): Result<Boolean> = try {
        when {
            userName.value.isBlank() -> Result.error(AppException.Data.ValidationError("user_name"))
            selectedGoals.value.isEmpty() -> Result.error(AppException.Data.ValidationError("relationship_goals"))
            relationshipStartDate.value == null -> Result.error(AppException.Data.ValidationError("relationship_date"))
            else -> Result.success(true)
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }
    
    /**
     * État de progression avec Result<T>
     */
    fun getProgressResult(): Result<Float> = try {
        val progressValue = progress.value
        if (progressValue in 0f..1f) {
            Result.success(progressValue)
        } else {
            Result.error(AppException.Generic("Invalid progress value: $progressValue"))
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }
    
    /**
     * Finalisation avec Result<T>
     */
    suspend fun finalizeOnboardingResult(withSubscription: Boolean): Result<Unit> = try {
        val validation = validateOnboardingData()
        if (validation is Result.Error) {
            return validation
        }
        
        val improvementString = selectedImprovements.value.joinToString(", ").ifBlank { null }
        
        val (success, user) = firebaseService.finalizeOnboardingWithPartnerData(
            name = userName.value,
            relationshipGoals = selectedGoals.value,
            relationshipDuration = relationshipDuration.value,
            relationshipImprovement = improvementString,
            questionMode = questionMode.value.ifBlank { null },
            isSubscribed = withSubscription,
            relationshipStartDate = relationshipStartDate.value,
            profileImage = profileImage.value,
            currentLocation = currentLocation.value
        )
        
        if (success && user != null) {
            firebaseService.completeOnboardingProcess()
            appState?.let { state ->
                state.isOnboardingInProgress = false
                state.updateUser(user)
                state.completeOnboarding()
            }
            Result.success(Unit)
        } else {
            Result.error(AppException.Generic("Onboarding finalization failed"))
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }

    fun skipSubscriptionDueToInheritance() {
        Log.d("OnboardingVM", "🔥 Abonnement hérité du partenaire -> skip subscription")
        shouldSkipSubscription.value = true
    }

    fun resetSubscriptionSkip() {
        Log.d("OnboardingVM", "🔥 Reset skip subscription")
        shouldSkipSubscription.value = false
    }

    fun handleSubscriptionUpdate() {
        Log.d("OnboardingVM", "🔥 Mise à jour d’abonnement détectée")
        // À implémenter si besoin (sync UI/serveur)
    }

    fun showPartnerConnectionSuccess(partnerName: String) {
        Log.d("OnboardingVM", "🎉 Connexion partenaire: $partnerName")
        connectedPartnerName.value = partnerName
        shouldShowPartnerConnectionSuccess.value = true
    }

    fun dismissPartnerConnectionSuccess() {
        Log.d("OnboardingVM", "🎉 Fermeture message connexion")
        shouldShowPartnerConnectionSuccess.value = false
        connectedPartnerName.value = ""
    }

    private fun finalizeOnboarding(withSubscription: Boolean) {
        Log.d("OnboardingVM", "🔥 Finalisation complète de l’onboarding")
        // Désactiver l’overlay de connexion partenaire : l’écran principal prendra le relais
        if (shouldShowPartnerConnectionSuccess.value) {
            Log.d("OnboardingVM", "🔥 Désactivation overlay partenaire (MainView prend le relais)")
            shouldShowPartnerConnectionSuccess.value = false
        }

        analytics.logEvent("onboarding_complete") {}
        Log.d("OnboardingVM", "📊 Firebase event: onboarding_complete")

        viewModelScope.launch {
            val improvementString = selectedImprovements.value.joinToString(", ").ifBlank { null }

            val (success, user) = firebaseService.finalizeOnboardingWithPartnerData(
                name = userName.value,
                relationshipGoals = selectedGoals.value,
                relationshipDuration = relationshipDuration.value,
                relationshipImprovement = improvementString,
                questionMode = questionMode.value.ifBlank { null },
                isSubscribed = withSubscription,
                relationshipStartDate = relationshipStartDate.value,
                profileImage = profileImage.value,
                currentLocation = currentLocation.value
            )

            if (success && user != null) {
                Log.d("OnboardingVM", "✅ Utilisateur créé avec succès (préservation partenaire OK)")
                firebaseService.completeOnboardingProcess()

                appState?.let { state ->
                    state.isOnboardingInProgress = false
                    state.updateUser(user)
                    state.completeOnboarding()
                } ?: run {
                    Log.e("OnboardingVM", "❌ AppState manquant")
                }
            } else {
                Log.e("OnboardingVM", "❌ Erreur lors de la finalisation")
            }

            isCompletingSubscriptionGuard = false
        }
    }
}
