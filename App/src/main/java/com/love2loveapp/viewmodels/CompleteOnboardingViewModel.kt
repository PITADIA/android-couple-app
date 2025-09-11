package com.love2loveapp.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel complet pour l'onboarding Love2Love avec toutes les 17 étapes
 * Basé sur la structure iOS mais adapté pour Android avec Google Sign-In et Google Play Billing
 */
class CompleteOnboardingViewModel : ViewModel() {

    // Étapes d'onboarding dans l'ordre exact de l'iOS
    enum class OnboardingStep {
        RelationshipGoals,        // 1. Objectifs de couple
        RelationshipImprovement,  // 2. Points d'amélioration
        RelationshipDate,         // 3. Date de début de relation
        CommunicationEvaluation,  // 4. Évaluation communication
        DiscoveryTime,           // 5. Temps de découverte
        Listening,               // 6. Qualité d'écoute
        Confidence,              // 7. Niveau de confiance
        Complicity,              // 8. Complicité du couple
        Authentication,          // 9. Google Sign-In (remplace Apple)
        DisplayName,             // 10. Nom d'affichage
        ProfilePhoto,            // 11. Photo de profil
        Completion,              // 12. Récapitulatif
        Loading,                 // 13. Traitement des données
        PartnerCode,             // 14. Code de connexion partenaire
        QuestionsIntro,          // 15. Introduction aux questions
        CategoriesPreview,       // 16. Aperçu des catégories
        Subscription             // 17. Page de paiement Google Play
    }

    // États des données collectées
    private val _currentStep = MutableStateFlow(OnboardingStep.RelationshipGoals)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _selectedGoals = MutableStateFlow<List<String>>(emptyList())
    val selectedGoals: StateFlow<List<String>> = _selectedGoals.asStateFlow()

    private val _selectedImprovements = MutableStateFlow<List<String>>(emptyList())
    val selectedImprovements: StateFlow<List<String>> = _selectedImprovements.asStateFlow()

    private val _relationshipStartDate = MutableStateFlow<LocalDate?>(null)
    val relationshipStartDate: StateFlow<LocalDate?> = _relationshipStartDate.asStateFlow()

    private val _communicationRating = MutableStateFlow("")
    val communicationRating: StateFlow<String> = _communicationRating.asStateFlow()

    private val _discoveryTimeAnswer = MutableStateFlow("")
    val discoveryTimeAnswer: StateFlow<String> = _discoveryTimeAnswer.asStateFlow()

    private val _listeningAnswer = MutableStateFlow("")
    val listeningAnswer: StateFlow<String> = _listeningAnswer.asStateFlow()

    private val _confidenceAnswer = MutableStateFlow("")
    val confidenceAnswer: StateFlow<String> = _confidenceAnswer.asStateFlow()

    private val _complicityAnswer = MutableStateFlow("")
    val complicityAnswer: StateFlow<String> = _complicityAnswer.asStateFlow()

    private val _profileImage = MutableStateFlow<Bitmap?>(null)
    val profileImage: StateFlow<Bitmap?> = _profileImage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _shouldSkipSubscription = MutableStateFlow(false)
    val shouldSkipSubscription: StateFlow<Boolean> = _shouldSkipSubscription.asStateFlow()

    private val _shouldShowPartnerConnectionSuccess = MutableStateFlow(false)
    val shouldShowPartnerConnectionSuccess: StateFlow<Boolean> = _shouldShowPartnerConnectionSuccess.asStateFlow()

    private val _connectedPartnerName = MutableStateFlow("")
    val connectedPartnerName: StateFlow<String> = _connectedPartnerName.asStateFlow()

    private val analytics by lazy { Firebase.analytics }
    private var isCompletingSubscriptionGuard = false

    // Étapes qui n'affichent pas la barre de progression
    private val hiddenProgressSteps = setOf(
        OnboardingStep.Authentication,
        OnboardingStep.DisplayName,
        OnboardingStep.ProfilePhoto,
        OnboardingStep.Completion,
        OnboardingStep.Loading,
        OnboardingStep.Subscription
    )

    // Progression calculée
    val progress: StateFlow<Float> = currentStep
        .map { step ->
            val visibleSteps = OnboardingStep.values().filterNot { it in hiddenProgressSteps }
            val currentIndex = visibleSteps.indexOf(step)
            if (currentIndex >= 0) (currentIndex + 1f) / visibleSteps.size else 0f
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    init {
        Log.d("CompleteOnboardingVM", "🔥 Initialisation ViewModel complet")
    }

    // Navigation entre les étapes
    fun nextStep() {
        val currentStepValue = _currentStep.value
        val stepIndex = OnboardingStep.values().indexOf(currentStepValue)
        
        analytics.logEvent("onboarding_etape") { 
            param("etape", stepIndex.toLong()) 
        }
        
        Log.d("CompleteOnboardingVM", "📊 Firebase event: onboarding_etape - étape: $stepIndex")

        when (currentStepValue) {
            OnboardingStep.RelationshipGoals -> _currentStep.value = OnboardingStep.RelationshipImprovement
            OnboardingStep.RelationshipImprovement -> _currentStep.value = OnboardingStep.RelationshipDate
            OnboardingStep.RelationshipDate -> _currentStep.value = OnboardingStep.CommunicationEvaluation
            OnboardingStep.CommunicationEvaluation -> _currentStep.value = OnboardingStep.DiscoveryTime
            OnboardingStep.DiscoveryTime -> _currentStep.value = OnboardingStep.Listening
            OnboardingStep.Listening -> _currentStep.value = OnboardingStep.Confidence
            OnboardingStep.Confidence -> _currentStep.value = OnboardingStep.Complicity
            OnboardingStep.Complicity -> _currentStep.value = OnboardingStep.Authentication
            OnboardingStep.Authentication -> {
                // Vérifier si Google a fourni un nom d'affichage
                val googleName = FirebaseAuth.getInstance().currentUser?.displayName
                if (!googleName.isNullOrBlank()) {
                    _userName.value = googleName
                    _currentStep.value = OnboardingStep.ProfilePhoto
                } else {
                    _currentStep.value = OnboardingStep.DisplayName
                }
            }
            OnboardingStep.DisplayName -> _currentStep.value = OnboardingStep.ProfilePhoto
            OnboardingStep.ProfilePhoto -> _currentStep.value = OnboardingStep.Completion
            OnboardingStep.Completion -> {
                _currentStep.value = OnboardingStep.Loading
                completeDataCollection()
            }
            OnboardingStep.Loading -> {
                if (_shouldSkipSubscription.value) {
                    finalizeOnboarding(withSubscription = true)
                } else {
                    _currentStep.value = OnboardingStep.PartnerCode
                }
            }
            OnboardingStep.PartnerCode -> _currentStep.value = OnboardingStep.QuestionsIntro
            OnboardingStep.QuestionsIntro -> _currentStep.value = OnboardingStep.CategoriesPreview
            OnboardingStep.CategoriesPreview -> _currentStep.value = OnboardingStep.Subscription
            OnboardingStep.Subscription -> {
                // Finalisation via completeSubscription()
            }
        }
    }

    fun previousStep() {
        val currentStepValue = _currentStep.value
        Log.d("CompleteOnboardingVM", "🔥 Retour depuis $currentStepValue")
        
        _currentStep.value = when (currentStepValue) {
            OnboardingStep.RelationshipGoals -> OnboardingStep.RelationshipGoals
            OnboardingStep.RelationshipImprovement -> OnboardingStep.RelationshipGoals
            OnboardingStep.RelationshipDate -> OnboardingStep.RelationshipImprovement
            OnboardingStep.CommunicationEvaluation -> OnboardingStep.RelationshipDate
            OnboardingStep.DiscoveryTime -> OnboardingStep.CommunicationEvaluation
            OnboardingStep.Listening -> OnboardingStep.DiscoveryTime
            OnboardingStep.Confidence -> OnboardingStep.Listening
            OnboardingStep.Complicity -> OnboardingStep.Confidence
            OnboardingStep.Authentication -> OnboardingStep.Complicity
            OnboardingStep.DisplayName -> OnboardingStep.Authentication
            OnboardingStep.ProfilePhoto -> {
                val googleName = FirebaseAuth.getInstance().currentUser?.displayName
                if (!googleName.isNullOrBlank()) OnboardingStep.Authentication else OnboardingStep.DisplayName
            }
            OnboardingStep.Completion -> OnboardingStep.ProfilePhoto
            OnboardingStep.Loading -> OnboardingStep.Completion
            OnboardingStep.PartnerCode -> OnboardingStep.Loading
            OnboardingStep.QuestionsIntro -> OnboardingStep.PartnerCode
            OnboardingStep.CategoriesPreview -> OnboardingStep.QuestionsIntro
            OnboardingStep.Subscription -> OnboardingStep.CategoriesPreview
        }
    }

    // Méthodes pour mettre à jour les données
    fun toggleGoal(goal: String) {
        val current = _selectedGoals.value.toMutableList()
        if (current.contains(goal)) {
            current.remove(goal)
        } else {
            current.add(goal)
        }
        _selectedGoals.value = current
        Log.d("CompleteOnboardingVM", "🎯 Objectifs sélectionnés: $current")
    }

    fun toggleImprovement(improvement: String) {
        val current = _selectedImprovements.value.toMutableList()
        if (current.contains(improvement)) {
            current.remove(improvement)
        } else {
            current.add(improvement)
        }
        _selectedImprovements.value = current
        Log.d("CompleteOnboardingVM", "🔧 Améliorations sélectionnées: $current")
    }

    fun updateRelationshipStartDate(date: LocalDate) {
        _relationshipStartDate.value = date
        Log.d("CompleteOnboardingVM", "📅 Date de relation: $date")
    }

    fun updateCommunicationRating(rating: String) {
        _communicationRating.value = rating
        Log.d("CompleteOnboardingVM", "📊 Évaluation communication: $rating")
    }

    fun updateDiscoveryTimeAnswer(answer: String) {
        _discoveryTimeAnswer.value = answer
        Log.d("CompleteOnboardingVM", "⏰ Réponse discovery time: $answer")
    }

    fun updateListeningAnswer(answer: String) {
        _listeningAnswer.value = answer
        Log.d("CompleteOnboardingVM", "👂 Réponse listening: $answer")
    }

    fun updateConfidenceAnswer(answer: String) {
        _confidenceAnswer.value = answer
        Log.d("CompleteOnboardingVM", "💪 Réponse confidence: $answer")
    }

    fun updateComplicityAnswer(answer: String) {
        _complicityAnswer.value = answer
        Log.d("CompleteOnboardingVM", "🤝 Réponse complicity: $answer")
    }

    fun updateUserName(name: String) {
        _userName.value = name
        Log.d("CompleteOnboardingVM", "👤 Nom utilisateur: $name")
    }

    fun updateProfileImage(bitmap: Bitmap) {
        _profileImage.value = bitmap
        Log.d("CompleteOnboardingVM", "📷 Image de profil mise à jour")
    }

    // Authentification Google
    fun completeAuthentication() {
        Log.d("CompleteOnboardingVM", "🔐 Authentification Google terminée")
        viewModelScope.launch {
            checkGoogleNameAndProceed()
        }
    }

    private fun checkGoogleNameAndProceed() {
        val googleDisplayName = FirebaseAuth.getInstance().currentUser?.displayName
        Log.d("CompleteOnboardingVM", "🔐 googleUserDisplayName='${googleDisplayName ?: "null"}'")

        if (!googleDisplayName.isNullOrBlank()) {
            Log.d("CompleteOnboardingVM", "✅ Nom Google fourni ($googleDisplayName) - Skip DisplayName step")
            _userName.value = googleDisplayName
            _currentStep.value = OnboardingStep.ProfilePhoto
        } else {
            Log.d("CompleteOnboardingVM", "❌ Aucun nom Google - Aller vers DisplayName")
            _currentStep.value = OnboardingStep.DisplayName
        }
    }

    // Gestion des données et finalisation
    private fun completeDataCollection() {
        Log.d("CompleteOnboardingVM", "🔥 Début finalisation collecte")
        viewModelScope.launch {
            _isLoading.value = true
            delay(3000) // Simulation du traitement
            _isLoading.value = false
            _currentStep.value = OnboardingStep.PartnerCode
            Log.d("CompleteOnboardingVM", "🔥 Fin du chargement simulé -> partnerCode")
        }
    }

    fun skipSubscription() {
        Log.d("CompleteOnboardingVM", "🔥 Abonnement ignoré -> finalisation")
        finalizeOnboarding(withSubscription = false)
    }

    fun completeSubscription() {
        if (isCompletingSubscriptionGuard) {
            Log.d("CompleteOnboardingVM", "🔥 Appel ignoré, finalisation déjà en cours")
            return
        }
        isCompletingSubscriptionGuard = true
        Log.d("CompleteOnboardingVM", "🔥 Abonnement terminé -> finalisation")
        finalizeOnboarding(withSubscription = true)
    }

    fun skipSubscriptionDueToInheritance() {
        Log.d("CompleteOnboardingVM", "🔥 Abonnement hérité du partenaire -> skip subscription")
        _shouldSkipSubscription.value = true
    }

    fun showPartnerConnectionSuccess(partnerName: String) {
        Log.d("CompleteOnboardingVM", "🎉 Connexion partenaire: $partnerName")
        _connectedPartnerName.value = partnerName
        _shouldShowPartnerConnectionSuccess.value = true
    }

    fun dismissPartnerConnectionSuccess() {
        Log.d("CompleteOnboardingVM", "🎉 Fermeture message connexion")
        _shouldShowPartnerConnectionSuccess.value = false
        _connectedPartnerName.value = ""
    }

    // Vérification si la barre de progression doit être affichée
    fun shouldShowProgressBar(): Boolean {
        return _currentStep.value !in hiddenProgressSteps
    }

    private fun finalizeOnboarding(withSubscription: Boolean) {
        Log.d("CompleteOnboardingVM", "🔥 Finalisation complète de l'onboarding")
        
        // Désactiver l'overlay de connexion partenaire
        if (_shouldShowPartnerConnectionSuccess.value) {
            Log.d("CompleteOnboardingVM", "🔥 Désactivation overlay partenaire")
            _shouldShowPartnerConnectionSuccess.value = false
        }

        analytics.logEvent("onboarding_complete") {
            param("with_subscription", if (withSubscription) 1L else 0L)
        }
        Log.d("CompleteOnboardingVM", "📊 Firebase event: onboarding_complete")

        viewModelScope.launch {
            // TODO: Intégrer avec votre FirebaseService pour sauvegarder les données
            // val improvementString = _selectedImprovements.value.joinToString(", ").ifBlank { null }
            // firebaseService.finalizeOnboardingWithPartnerData(...)
            
            Log.d("CompleteOnboardingVM", "✅ Onboarding finalisé avec succès")
            isCompletingSubscriptionGuard = false
            
            // TODO: Notifier AppState que l'onboarding est terminé
        }
    }

    // Méthodes utilitaires pour la localisation
    fun getRelationshipGoals(context: Context): List<String> {
        return listOf(
            context.getString(com.love2loveapp.R.string.goal_create_connection),
            context.getString(com.love2loveapp.R.string.goal_talk_avoided_subjects),
            context.getString(com.love2loveapp.R.string.goal_increase_passion),
            context.getString(com.love2loveapp.R.string.goal_share_more_laughs),
            context.getString(com.love2loveapp.R.string.goal_find_complicity)
        )
    }

    fun getRelationshipImprovements(context: Context): List<String> {
        return listOf(
            context.getString(com.love2loveapp.R.string.improvement_create_strong_moment),
            context.getString(com.love2loveapp.R.string.improvement_revive_connection),
            context.getString(com.love2loveapp.R.string.improvement_break_routine),
            context.getString(com.love2loveapp.R.string.improvement_say_unsaid)
        )
    }
}

